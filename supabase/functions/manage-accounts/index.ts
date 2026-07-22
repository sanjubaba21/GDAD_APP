import {
  createPinHash,
  decodeBase64Secret,
  hmacSha256,
  verifyPinHash,
} from "../_shared/pin.ts";
import { AccountAdminRequest, parseAccountAdminRequest } from "./core.ts";

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
  request: AccountAdminRequest,
  row: ApplicationRow | PreparationRow,
): Response {
  return json(200, "ACCOUNT_UPDATED", {
    status: "updated",
    request_id: request.request_id,
    target_user_id: request.target_user_id,
    action: request.action,
    disabled: "disabled" in row ? row.disabled : row.resulting_disabled,
  });
}

Deno.serve(async (incoming: Request): Promise<Response> => {
  if (incoming.method !== "POST") {
    return new Response(null, {
      status: 405,
      headers: { ...JSON_HEADERS, allow: "POST" },
    });
  }
  let request: AccountAdminRequest | null = null;
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
      return json(400, "INVALID_REQUEST");
    }
    const bodyText = await incoming.text();
    if (new TextEncoder().encode(bodyText).byteLength > 2048) {
      return json(400, "INVALID_REQUEST");
    }
    let body: unknown;
    try {
      body = JSON.parse(bodyText);
    } catch {
      return json(400, "INVALID_REQUEST");
    }
    request = parseAccountAdminRequest(body);
    if (!request) return json(400, "INVALID_REQUEST");

    projectUrl = requiredEnvironment("SUPABASE_URL").replace(/\/$/, "");
    serviceKey = requiredEnvironment("SUPABASE_SERVICE_ROLE_KEY");
    const publishableKey = incoming.headers.get("apikey")?.trim() ?? "";
    if (!await isValidPublishableKey(projectUrl, publishableKey)) {
      return json(401, "INVALID_PROJECT_KEY");
    }
    stage = "authenticate";
    const actorUserId = await authenticatedSubject(
      projectUrl,
      publishableKey,
      incoming.headers.get("authorization"),
    );
    if (!actorUserId) return json(401, "UNAUTHORIZED");

    const ratePepper = decodeBase64Secret(
      requiredEnvironment("GDAD_RATE_LIMIT_PEPPER_V1"),
    );
    const fingerprint = await sourceFingerprint(
      ratePepper,
      incoming.headers.get("x-forwarded-for"),
    );
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
      return safeResult(request, rows[0]);
    }
    if (!rows[0].actor_pin_hash || rows[0].actor_pepper_version === null) {
      throw new Error("missing actor verifier");
    }

    stage = "reauthenticate";
    const pinPepper = decodeBase64Secret(
      requiredEnvironment("GDAD_PIN_PEPPER_V1"),
    );
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
      return json(403, "OPERATION_DENIED");
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
    return safeResult(request, applied[0]);
  } catch {
    console.error("manage-accounts internal failure", stage);
    return json(
      stage === "prepare" ? 403 : 503,
      stage === "prepare" ? "OPERATION_DENIED" : "OPERATION_FAILED",
    );
  }
});
