import {
  createPinHash,
  decodeBase64Secret,
  hmacSha256,
  verifyPinHash,
} from "../_shared/pin.ts";
import { internalEmail } from "../manage-users/core.ts";
import {
  AccountAdminRequest,
  AccountManagementRequest,
  parseAccountManagementRequest,
  ShopDeleteRequest,
} from "./core.ts";
import {
  correlationIdFor,
  operationalFailure,
  operationalHeaders,
} from "../_shared/operational.ts";

interface AuthUser {
  id?: string;
}
interface PreparationRow {
  reservation_status: "reserved" | "complete";
  actor_pin_hash: string | null;
  actor_pepper_version: number | null;
  target_disabled: boolean;
  resulting_disabled: boolean;
}
interface ApplicationRow {
  target_user_id: string;
  action: string;
  disabled: boolean;
}
interface ManagedAuthUser {
  user_id: string;
  provisioning_request_id: string;
}
interface ShopDeletionPreparationRow {
  reservation_status: "reserved" | "complete";
  actor_pin_hash: string | null;
  actor_pepper_version: number | null;
  target_shop_id: string;
  target_slug: string;
  target_display_name: string;
  managed_auth_users: unknown;
  auth_cleanup_pending: boolean;
}
interface ShopDeletionApplicationRow {
  target_shop_id: string;
  target_slug: string;
  target_display_name: string;
  managed_auth_users: unknown;
  auth_cleanup_pending: boolean;
}
interface AdminAuthUser {
  id?: string;
  email?: string;
  app_metadata?: Record<string, unknown>;
}

const UPSTREAM_TIMEOUT_MS = 10_000;
const validatedPublishableKeys = new Set<string>();

function json(
  correlationId: string,
  status: number,
  code: string,
  extra: Record<string, unknown> = {},
): Response {
  return new Response(JSON.stringify({ code, ...extra }), {
    status,
    headers: operationalHeaders(correlationId),
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
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
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
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  });
  if (!response.ok) return null;
  const user = await response.json() as AuthUser;
  return typeof user.id === "string" ? user.id : null;
}
async function servicePost(
  projectUrl: string,
  serviceKey: string,
  path: string,
  body: Record<string, unknown>,
): Promise<Response> {
  return await fetch(`${projectUrl}${path}`, {
    method: "POST",
    headers: {
      apikey: serviceKey,
      authorization: `Bearer ${serviceKey}`,
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  });
}
async function rpc<T>(
  projectUrl: string,
  serviceKey: string,
  name: string,
  body: Record<string, unknown>,
): Promise<T> {
  const response = await servicePost(
    projectUrl,
    serviceKey,
    `/rest/v1/rpc/${name}`,
    body,
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
  const response = await servicePost(
    projectUrl,
    serviceKey,
    `/rest/v1/rpc/${name}`,
    body,
  );
  if (!response.ok) throw new Error(`rpc ${name} status ${response.status}`);
}
async function sourceFingerprint(
  pepper: Uint8Array,
  source: string | null,
): Promise<string> {
  const bounded = (source ?? "unavailable").trim().slice(0, 256);
  const digest = await hmacSha256(pepper, `gdad-admin-rate-v1\0${bounded}`);
  return Array.from(digest, (byte) => byte.toString(16).padStart(2, "0")).join(
    "",
  );
}
function safeResult(
  correlationId: string,
  request: AccountAdminRequest,
  row: ApplicationRow | PreparationRow,
): Response {
  return json(correlationId, 200, "ACCOUNT_UPDATED", {
    status: "updated",
    request_id: request.request_id,
    target_user_id: request.target_user_id,
    action: request.action,
    disabled: "disabled" in row ? row.disabled : row.resulting_disabled,
  });
}

function managedAuthUsers(value: unknown): ManagedAuthUser[] | null {
  if (!Array.isArray(value)) return null;
  const result: ManagedAuthUser[] = [];
  for (const item of value) {
    if (!item || typeof item !== "object" || Array.isArray(item)) return null;
    const record = item as Record<string, unknown>;
    if (
      Object.keys(record).some((key) =>
        key !== "user_id" && key !== "provisioning_request_id"
      ) ||
      typeof record.user_id !== "string" ||
      typeof record.provisioning_request_id !== "string" ||
      record.user_id !== record.provisioning_request_id
    ) return null;
    result.push({
      user_id: record.user_id,
      provisioning_request_id: record.provisioning_request_id,
    });
  }
  return result;
}

async function deleteManagedAuthUser(
  projectUrl: string,
  serviceKey: string,
  expected: ManagedAuthUser,
): Promise<void> {
  const path = `/auth/v1/admin/users/${expected.user_id}`;
  const lookup = await fetch(`${projectUrl}${path}`, {
    headers: {
      apikey: serviceKey,
      authorization: `Bearer ${serviceKey}`,
    },
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  });
  if (lookup.status === 404) return;
  if (!lookup.ok) throw new Error(`auth lookup status ${lookup.status}`);
  const user = await lookup.json() as AdminAuthUser;
  if (
    user.id !== expected.user_id ||
    user.email !== internalEmail(expected.user_id) ||
    user.app_metadata?.managed_by !== "gdad_pin_v1" ||
    user.app_metadata?.provisioning_request_id !==
      expected.provisioning_request_id
  ) throw new Error("managed Auth identity mismatch");

  const removal = await fetch(`${projectUrl}${path}`, {
    method: "DELETE",
    headers: {
      apikey: serviceKey,
      authorization: `Bearer ${serviceKey}`,
    },
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  });
  if (!removal.ok && removal.status !== 404) {
    throw new Error(`auth deletion status ${removal.status}`);
  }
}

async function executeShopDeletion(
  request: ShopDeleteRequest,
  actorUserId: string,
  fingerprint: string,
  pinPepper: Uint8Array,
  projectUrl: string,
  serviceKey: string,
  correlationId: string,
  setStage: (stage: string) => void,
): Promise<Response> {
  setStage("prepare");
  const prepared = await rpc<ShopDeletionPreparationRow[]>(
    projectUrl,
    serviceKey,
    "shop_delete_prepare",
    {
      p_request_id: request.request_id,
      p_actor_user_id: actorUserId,
      p_target_shop_id: request.target_shop_id,
      p_confirmation_slug: request.confirmation_slug,
      p_reason: request.reason,
      p_source_fingerprint: fingerprint,
      p_request_time: new Date().toISOString(),
    },
  );
  if (
    !Array.isArray(prepared) || prepared.length !== 1 ||
    prepared[0].target_shop_id !== request.target_shop_id ||
    prepared[0].target_slug !== request.confirmation_slug
  ) throw new Error("invalid shop deletion preparation result");

  let result: ShopDeletionApplicationRow;
  if (prepared[0].reservation_status === "complete") {
    result = prepared[0];
  } else {
    if (
      !prepared[0].actor_pin_hash ||
      prepared[0].actor_pepper_version === null
    ) throw new Error("missing actor verifier");
    setStage("reauthenticate");
    const verified = await verifyPinHash(
      pinPepper,
      actorUserId,
      request.reauth_pin,
      prepared[0].actor_pin_hash,
      prepared[0].actor_pepper_version,
    );
    if (!verified) {
      await rpcNoResult(projectUrl, serviceKey, "shop_delete_fail", {
        p_request_id: request.request_id,
        p_failure_code: "REAUTH_FAILED",
      });
      return json(correlationId, 403, "OPERATION_DENIED");
    }
    setStage("apply");
    const applied = await rpc<ShopDeletionApplicationRow[]>(
      projectUrl,
      serviceKey,
      "shop_delete_apply",
      { p_request_id: request.request_id },
    );
    if (
      !Array.isArray(applied) || applied.length !== 1 ||
      applied[0].target_shop_id !== request.target_shop_id ||
      applied[0].target_slug !== request.confirmation_slug
    ) throw new Error("invalid shop deletion application result");
    result = applied[0];
  }

  const users = managedAuthUsers(result.managed_auth_users);
  if (!users || (result.auth_cleanup_pending && users.length === 0)) {
    throw new Error("invalid managed Auth cleanup state");
  }
  if (result.auth_cleanup_pending) {
    setStage("auth-cleanup");
    for (const user of users) {
      await deleteManagedAuthUser(projectUrl, serviceKey, user);
    }
    await rpcNoResult(
      projectUrl,
      serviceKey,
      "shop_delete_mark_auth_cleanup",
      { p_request_id: request.request_id },
    );
  }

  return json(correlationId, 200, "SHOP_DELETED", {
    status: "deleted",
    request_id: request.request_id,
    target_shop_id: request.target_shop_id,
    auth_cleanup_pending: false,
  });
}

Deno.serve(async (incoming: Request): Promise<Response> => {
  let correlationId = correlationIdFor();
  const respond = (
    status: number,
    code: string,
    extra: Record<string, unknown> = {},
  ) => json(correlationId, status, code, extra);
  if (incoming.method !== "POST") {
    return new Response(null, {
      status: 405,
      headers: operationalHeaders(correlationId, { allow: "POST" }),
    });
  }
  let request: AccountManagementRequest | null = null;
  let stage = "request";
  let projectUrl = "";
  let serviceKey = "";
  try {
    const contentType = incoming.headers.get("content-type")?.toLowerCase() ??
      "";
    const declaredLength = Number(
      incoming.headers.get("content-length") ?? "0",
    );
    if (!contentType.startsWith("application/json") || declaredLength > 2048) {
      return respond(400, "INVALID_REQUEST");
    }
    const bodyText = await incoming.text();
    if (new TextEncoder().encode(bodyText).byteLength > 2048) {
      return respond(400, "INVALID_REQUEST");
    }
    let body: unknown;
    try {
      body = JSON.parse(bodyText);
    } catch {
      return respond(400, "INVALID_REQUEST");
    }
    request = parseAccountManagementRequest(body);
    if (!request) return respond(400, "INVALID_REQUEST");
    correlationId = correlationIdFor(request.request_id);

    projectUrl = requiredEnvironment("SUPABASE_URL").replace(/\/$/, "");
    serviceKey = requiredEnvironment("SUPABASE_SERVICE_ROLE_KEY");
    const publishableKey = incoming.headers.get("apikey")?.trim() ?? "";
    if (!await isValidPublishableKey(projectUrl, publishableKey)) {
      return respond(401, "INVALID_PROJECT_KEY");
    }
    stage = "authenticate";
    const actorUserId = await authenticatedSubject(
      projectUrl,
      publishableKey,
      incoming.headers.get("authorization"),
    );
    if (!actorUserId) return respond(401, "UNAUTHORIZED");

    const ratePepper = decodeBase64Secret(
      requiredEnvironment("GDAD_RATE_LIMIT_PEPPER_V1"),
    );
    if (ratePepper.byteLength < 32) {
      throw new Error("invalid rate-limit secret length");
    }
    const fingerprint = await sourceFingerprint(
      ratePepper,
      incoming.headers.get("x-forwarded-for"),
    );
    const pinPepper = decodeBase64Secret(
      requiredEnvironment("GDAD_PIN_PEPPER_V1"),
    );
    if (pinPepper.byteLength < 32) {
      throw new Error("invalid PIN secret length");
    }
    if (request.action === "delete_shop") {
      return await executeShopDeletion(
        request,
        actorUserId,
        fingerprint,
        pinPepper,
        projectUrl,
        serviceKey,
        correlationId,
        (nextStage) => stage = nextStage,
      );
    }
    stage = "prepare";
    const rows = await rpc<PreparationRow[]>(
      projectUrl,
      serviceKey,
      "account_admin_prepare",
      {
        p_request_id: request.request_id,
        p_action: request.action,
        p_actor_user_id: actorUserId,
        p_target_user_id: request.target_user_id,
        p_source_fingerprint: fingerprint,
        p_request_time: new Date().toISOString(),
      },
    );
    if (!Array.isArray(rows) || rows.length !== 1) {
      throw new Error("invalid preparation result");
    }
    if (rows[0].reservation_status === "complete") {
      return safeResult(correlationId, request, rows[0]);
    }
    if (!rows[0].actor_pin_hash || rows[0].actor_pepper_version === null) {
      throw new Error("missing actor verifier");
    }

    stage = "reauthenticate";
    const verified = await verifyPinHash(
      pinPepper,
      actorUserId,
      request.reauth_pin,
      rows[0].actor_pin_hash,
      rows[0].actor_pepper_version,
    );
    if (!verified) {
      await rpcNoResult(projectUrl, serviceKey, "account_admin_fail", {
        p_request_id: request.request_id,
        p_failure_code: "REAUTH_FAILED",
      });
      return respond(403, "OPERATION_DENIED");
    }

    stage = "hash-target-pin";
    const pinHash = request.action === "reset_pin"
      ? await createPinHash(pinPepper, request.target_user_id, request.new_pin!)
      : null;
    stage = "apply";
    const applied = await rpc<ApplicationRow[]>(
      projectUrl,
      serviceKey,
      "account_admin_apply",
      {
        p_request_id: request.request_id,
        p_pin_hash: pinHash,
      },
    );
    if (
      !Array.isArray(applied) || applied.length !== 1 ||
      applied[0].target_user_id !== request.target_user_id ||
      applied[0].action !== request.action
    ) throw new Error("invalid application result");
    return safeResult(correlationId, request, applied[0]);
  } catch {
    console.error(operationalFailure("manage-accounts", stage, correlationId));
    return respond(
      stage === "prepare" ? 403 : 503,
      stage === "prepare" ? "OPERATION_DENIED" : "OPERATION_FAILED",
    );
  }
});
