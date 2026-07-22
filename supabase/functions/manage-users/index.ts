import {
  createPinHash,
  decodeBase64Secret,
  UUID_PATTERN,
} from "../_shared/pin.ts";
import {
  clientRole,
  internalEmail,
  operatorFailureDetails,
  parseProvisionRequest,
  ProvisionRequest,
  secretsEqual,
} from "./core.ts";

interface AuthUser {
  id?: string;
  email?: string;
  app_metadata?: Record<string, unknown>;
}

interface ReservationRow {
  reservation_status: "reserved" | "complete";
  auth_user_id: string | null;
}

interface FinalizationRow {
  auth_user_id: string;
  login_id: string;
  platform_role: "standard" | "super_admin";
  shop_id: string | null;
  shop_role: "owner" | "salesman" | null;
}

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
};
const validatedPublishableKeys = new Set<string>();

function json(
  status: number,
  code: string,
  extra: Record<string, unknown> = {},
): Response {
  return new Response(JSON.stringify({ code, ...extra }), {
    status,
    headers: JSON_HEADERS,
  });
}

function requiredEnvironment(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`missing ${name}`);
  return value;
}

async function isValidPublishableKey(
  projectUrl: string,
  key: string,
): Promise<boolean> {
  if (!key.startsWith("sb_publishable_") || key.length > 256) return false;
  if (validatedPublishableKeys.has(key)) return true;
  const response = await fetch(`${projectUrl}/auth/v1/settings`, {
    headers: { apikey: key },
  });
  if (!response.ok) return false;
  validatedPublishableKeys.add(key);
  return true;
}

async function authenticatedSubject(
  projectUrl: string,
  publishableKey: string,
  authorization: string | null,
): Promise<string | null> {
  if (!authorization?.startsWith("Bearer ") || authorization.length > 4096) {
    return null;
  }
  const response = await fetch(`${projectUrl}/auth/v1/user`, {
    headers: { apikey: publishableKey, authorization },
  });
  if (!response.ok) return null;
  const user = await response.json() as AuthUser;
  return typeof user.id === "string" ? user.id : null;
}

async function serviceFetch(
  projectUrl: string,
  serviceKey: string,
  path: string,
  init: RequestInit,
): Promise<Response> {
  return await fetch(`${projectUrl}${path}`, {
    ...init,
    headers: {
      apikey: serviceKey,
      authorization: `Bearer ${serviceKey}`,
      "content-type": "application/json",
      ...init.headers,
    },
  });
}

async function rpc<T>(
  projectUrl: string,
  serviceKey: string,
  name: string,
  body: Record<string, unknown>,
): Promise<T> {
  const response = await serviceFetch(
    projectUrl,
    serviceKey,
    `/rest/v1/rpc/${name}`,
    { method: "POST", body: JSON.stringify(body) },
  );
  if (!response.ok) throw new Error(`rpc ${name} status ${response.status}`);
  return await response.json() as T;
}

async function rpcNoResult(
  projectUrl: string,
  serviceKey: string,
  name: string,
  body: Record<string, unknown>,
): Promise<void> {
  const response = await serviceFetch(
    projectUrl,
    serviceKey,
    `/rest/v1/rpc/${name}`,
    { method: "POST", body: JSON.stringify(body) },
  );
  if (!response.ok) throw new Error(`rpc ${name} status ${response.status}`);
}

async function startProvisioning(
  projectUrl: string,
  serviceKey: string,
  request: ProvisionRequest,
  actorUserId: string | null,
): Promise<ReservationRow> {
  const rows = await rpc<ReservationRow[]>(
    projectUrl,
    serviceKey,
    "account_provision_start",
    {
      p_request_id: request.request_id,
      p_operation: request.action,
      p_actor_user_id: actorUserId,
      p_login_id: request.login_id,
      p_display_name: request.display_name,
      p_shop_id: request.shop_id,
    },
  );
  if (!Array.isArray(rows) || rows.length !== 1) {
    throw new Error("invalid provisioning reservation result");
  }
  return rows[0];
}

function unwrapAuthUser(payload: unknown): AuthUser | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  const candidate = record.user && typeof record.user === "object"
    ? record.user
    : record;
  return candidate as AuthUser;
}

function isExpectedManagedUser(
  user: AuthUser | null,
  id: string,
  email: string,
  requestId: string,
): boolean {
  return user?.id === id && user.email === email &&
    user.app_metadata?.managed_by === "gdad_pin_v1" &&
    user.app_metadata?.provisioning_request_id === requestId;
}

async function loadAuthUser(
  projectUrl: string,
  serviceKey: string,
  userId: string,
): Promise<AuthUser | null> {
  const response = await serviceFetch(
    projectUrl,
    serviceKey,
    `/auth/v1/admin/users/${userId}`,
    { method: "GET" },
  );
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Auth lookup status ${response.status}`);
  return unwrapAuthUser(await response.json());
}

async function ensureAuthUser(
  projectUrl: string,
  serviceKey: string,
  requestId: string,
  existingUserId: string | null,
  setStage: (stage: string) => void,
): Promise<string> {
  const email = internalEmail(requestId);
  if (existingUserId) {
    setStage("auth-user-lookup");
    const existing = await loadAuthUser(projectUrl, serviceKey, existingUserId);
    if (!isExpectedManagedUser(existing, existingUserId, email, requestId)) {
      throw new Error("managed Auth identity conflict");
    }
    return existingUserId;
  }

  setStage("auth-user-create");
  const response = await serviceFetch(
    projectUrl,
    serviceKey,
    "/auth/v1/admin/users",
    {
      method: "POST",
      body: JSON.stringify({
        email,
        email_confirm: true,
        app_metadata: {
          managed_by: "gdad_pin_v1",
          provisioning_request_id: requestId,
        },
      }),
    },
  );
  if (!response.ok) {
    setStage(`auth-user-create-${response.status}`);
    throw new Error(`Auth creation status ${response.status}`);
  }
  const created = unwrapAuthUser(await response.json());
  if (
    !created?.id || !UUID_PATTERN.test(created.id) ||
    !isExpectedManagedUser(created, created.id, email, requestId)
  ) {
    setStage("auth-user-create-invalid-result");
    throw new Error("invalid managed Auth creation result");
  }
  return created.id;
}

async function deleteAuthUser(
  projectUrl: string,
  serviceKey: string,
  requestId: string,
  userId: string,
): Promise<void> {
  const existing = await loadAuthUser(projectUrl, serviceKey, userId);
  if (!existing) return;
  if (
    !isExpectedManagedUser(
      existing,
      userId,
      internalEmail(requestId),
      requestId,
    )
  ) {
    throw new Error("refusing to delete an unrelated Auth identity");
  }
  const response = await serviceFetch(
    projectUrl,
    serviceKey,
    `/auth/v1/admin/users/${userId}`,
    { method: "DELETE" },
  );
  if (!response.ok && response.status !== 404) {
    throw new Error(`Auth compensation status ${response.status}`);
  }
}

function safeResult(
  request: ProvisionRequest,
  userId: string,
  repeated: boolean,
): Response {
  return json(repeated ? 200 : 201, "ACCOUNT_PROVISIONED", {
    status: repeated ? "existing" : "created",
    user_id: userId,
    login_id: request.login_id,
    role: clientRole(request.action),
    shop_id: request.shop_id,
  });
}

Deno.serve(async (incoming: Request): Promise<Response> => {
  if (incoming.method !== "POST") {
    return new Response(null, {
      status: 405,
      headers: { ...JSON_HEADERS, allow: "POST" },
    });
  }

  let stage = "request";
  let request: ProvisionRequest | null = null;
  let actorUserId: string | null = null;
  let projectUrl = "";
  let serviceKey = "";
  let reservedUserId: string | null = null;
  let trustedBootstrap = false;
  try {
    const contentType = incoming.headers.get("content-type")?.toLowerCase() ??
      "";
    const declaredLength = Number(
      incoming.headers.get("content-length") ?? "0",
    );
    if (!contentType.startsWith("application/json") || declaredLength > 4096) {
      return json(400, "INVALID_REQUEST");
    }
    const bodyText = await incoming.text();
    if (new TextEncoder().encode(bodyText).byteLength > 4096) {
      return json(400, "INVALID_REQUEST");
    }
    let body: unknown;
    try {
      body = JSON.parse(bodyText);
    } catch {
      return json(400, "INVALID_REQUEST");
    }
    request = parseProvisionRequest(body);
    if (!request) return json(400, "INVALID_REQUEST");

    projectUrl = requiredEnvironment("SUPABASE_URL").replace(/\/$/, "");
    serviceKey = requiredEnvironment("SUPABASE_SERVICE_ROLE_KEY");
    const publishableKey = incoming.headers.get("apikey")?.trim() ?? "";
    if (!await isValidPublishableKey(projectUrl, publishableKey)) {
      return json(401, "INVALID_PROJECT_KEY");
    }

    stage = "authorize";
    if (request.action === "bootstrap_super_admin") {
      const supplied = incoming.headers.get("x-gdad-bootstrap-token") ?? "";
      const expected = requiredEnvironment("GDAD_BOOTSTRAP_TOKEN");
      if (!supplied || !await secretsEqual(supplied, expected)) {
        return json(401, "UNAUTHORIZED");
      }
      trustedBootstrap = true;
    } else {
      actorUserId = await authenticatedSubject(
        projectUrl,
        publishableKey,
        incoming.headers.get("authorization"),
      );
      if (!actorUserId) return json(401, "UNAUTHORIZED");
    }

    stage = "reserve";
    const reservation = await startProvisioning(
      projectUrl,
      serviceKey,
      request,
      actorUserId,
    );
    if (reservation.reservation_status === "complete") {
      if (!reservation.auth_user_id) {
        throw new Error("completed reservation missing Auth subject");
      }
      return safeResult(request, reservation.auth_user_id, true);
    }

    stage = "auth-user";
    const authUserId = await ensureAuthUser(
      projectUrl,
      serviceKey,
      request.request_id,
      reservation.auth_user_id,
      (nextStage) => stage = nextStage,
    );
    reservedUserId = authUserId;

    stage = "attach-auth-user";
    const attachedUserId = await rpc<string>(
      projectUrl,
      serviceKey,
      "account_provision_attach_auth",
      {
        p_request_id: request.request_id,
        p_auth_user_id: authUserId,
      },
    );
    if (attachedUserId !== authUserId) {
      throw new Error("invalid Auth attachment result");
    }

    stage = "pin-hash";
    const pepper = decodeBase64Secret(
      requiredEnvironment("GDAD_PIN_PEPPER_V1"),
    );
    const pinHash = await createPinHash(
      pepper,
      authUserId,
      request.pin,
    );

    stage = "finalize";
    const rows = await rpc<FinalizationRow[]>(
      projectUrl,
      serviceKey,
      "account_provision_finalize",
      { p_request_id: request.request_id, p_pin_hash: pinHash },
    );
    if (
      !Array.isArray(rows) || rows.length !== 1 ||
      rows[0].auth_user_id !== authUserId ||
      rows[0].login_id !== request.login_id
    ) {
      throw new Error("invalid provisioning finalization result");
    }
    return safeResult(request, authUserId, false);
  } catch {
    console.error("manage-users internal failure", stage);
    if (request && projectUrl && serviceKey) {
      try {
        const reconciliation = await startProvisioning(
          projectUrl,
          serviceKey,
          request,
          actorUserId,
        );
        if (reconciliation.reservation_status === "complete") {
          if (!reconciliation.auth_user_id) {
            throw new Error("completed reconciliation missing Auth subject");
          }
          return safeResult(request, reconciliation.auth_user_id, true);
        }
        const compensationUserId = reservedUserId ??
          reconciliation.auth_user_id;
        if (compensationUserId) {
          await deleteAuthUser(
            projectUrl,
            serviceKey,
            request.request_id,
            compensationUserId,
          );
        }
        await rpcNoResult(
          projectUrl,
          serviceKey,
          "account_provision_fail",
          {
            p_request_id: request.request_id,
            p_failure_code: "EDGE_OPERATION_FAILED",
          },
        );
      } catch {
        console.error("manage-users compensation failure");
      }
    }
    return json(
      stage === "reserve" ? 403 : 503,
      "OPERATION_FAILED",
      operatorFailureDetails(trustedBootstrap, stage),
    );
  }
});
