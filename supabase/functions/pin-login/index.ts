import {
  decodeBase64Secret,
  diagnosticFailureDetails,
  diagnosticSuccessDetails,
  parseGeneratedLinkToken,
  parseLoginRequest,
  parseSafeAuthErrorCode,
  PIN_PEPPER_VERSION,
  secretsEqual,
  sourceFingerprint,
  verifyPinHash,
} from "./core.ts";

interface PreparationRow {
  source_limited: boolean;
  account_locked: boolean;
  user_id: string | null;
  auth_email: string | null;
  pin_hash: string | null;
  pepper_version: number | null;
}

interface AuthTokenResponse {
  access_token?: string;
  refresh_token?: string;
  expires_in?: number;
  expires_at?: number;
  token_type?: string;
  user?: { id?: string };
}

interface EstablishedSession {
  session: AuthTokenResponse;
  singleUseVerified: boolean;
}

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
  "content-security-policy": "default-src 'none'; frame-ancestors 'none'",
  "referrer-policy": "no-referrer",
};
const UPSTREAM_TIMEOUT_MS = 10_000;
const DUMMY_USER_ID = "00000000-0000-4000-8000-000000000000";
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
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  });
  if (!response.ok) return false;
  validatedPublishableKeys.add(key);
  return true;
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

async function prepareLogin(
  projectUrl: string,
  serviceKey: string,
  loginId: string,
  fingerprint: string,
  requestTime: string,
): Promise<PreparationRow> {
  const response = await servicePost(
    projectUrl,
    serviceKey,
    "/rest/v1/rpc/pin_login_prepare",
    {
      normalized_login_id: loginId,
      source_fingerprint: fingerprint,
      request_time: requestTime,
    },
  );
  if (!response.ok) {
    throw new Error(`login preparation failed with status ${response.status}`);
  }
  const rows = await response.json() as PreparationRow[];
  if (!Array.isArray(rows) || rows.length !== 1) {
    throw new Error("invalid preparation result");
  }
  return rows[0];
}

async function completeLogin(
  projectUrl: string,
  serviceKey: string,
  userId: string | null,
  successful: boolean,
  requestTime: string,
): Promise<void> {
  const response = await servicePost(
    projectUrl,
    serviceKey,
    "/rest/v1/rpc/pin_login_complete",
    {
      target_user_id: userId,
      was_successful: successful,
      request_time: requestTime,
    },
  );
  if (!response.ok) throw new Error("login completion failed");
}

async function establishSession(
  projectUrl: string,
  serviceKey: string,
  publishableKey: string,
  email: string,
  expectedUserId: string,
  proveSingleUse: boolean,
  setStage: (stage: string) => void,
): Promise<EstablishedSession> {
  setStage("auth-link-generation");
  const linkResponse = await servicePost(
    projectUrl,
    serviceKey,
    "/auth/v1/admin/generate_link",
    { type: "magiclink", email },
  );
  if (!linkResponse.ok) {
    const authCode = await linkResponse.json()
      .then(parseSafeAuthErrorCode)
      .catch(() => null);
    setStage(
      `auth-link-generation-${linkResponse.status}${
        authCode ? `-${authCode}` : ""
      }`,
    );
    throw new Error("session link generation failed");
  }

  setStage("auth-link-result");
  const tokenHash = parseGeneratedLinkToken(await linkResponse.json());
  if (!tokenHash) {
    throw new Error("invalid session link result");
  }

  setStage("auth-token-exchange");
  const verifyResponse = await fetch(`${projectUrl}/auth/v1/verify`, {
    method: "POST",
    headers: { apikey: publishableKey, "content-type": "application/json" },
    body: JSON.stringify({ token_hash: tokenHash, type: "email" }),
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  });
  if (!verifyResponse.ok) {
    setStage(`auth-token-exchange-${verifyResponse.status}`);
    throw new Error("session token exchange failed");
  }

  setStage("auth-session-validation");
  const session = await verifyResponse.json() as AuthTokenResponse;
  if (
    session.user?.id !== expectedUserId ||
    !session.access_token ||
    !session.refresh_token ||
    !session.token_type ||
    typeof session.expires_in !== "number"
  ) throw new Error("invalid session subject or token result");

  let singleUseVerified = false;
  if (proveSingleUse) {
    setStage("auth-token-single-use-check");
    const reuseResponse = await fetch(`${projectUrl}/auth/v1/verify`, {
      method: "POST",
      headers: { apikey: publishableKey, "content-type": "application/json" },
      body: JSON.stringify({ token_hash: tokenHash, type: "email" }),
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    });
    if (reuseResponse.ok) {
      setStage("auth-token-reuse-accepted");
      throw new Error("single-use Auth token was accepted twice");
    }
    singleUseVerified = true;
  }
  return { session, singleUseVerified };
}

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method !== "POST") {
    return new Response(null, {
      status: 405,
      headers: { ...JSON_HEADERS, allow: "POST" },
    });
  }

  let stage = "request-validation";
  let trustedDiagnostic = false;
  try {
    const contentType = request.headers.get("content-type")?.toLowerCase() ??
      "";
    const declaredLength = Number(request.headers.get("content-length") ?? "0");
    if (!contentType.startsWith("application/json") || declaredLength > 2048) {
      return json(400, "INVALID_REQUEST");
    }

    const bodyText = await request.text();
    if (new TextEncoder().encode(bodyText).byteLength > 2048) {
      return json(400, "INVALID_REQUEST");
    }
    let body: unknown;
    try {
      body = JSON.parse(bodyText);
    } catch {
      return json(400, "INVALID_REQUEST");
    }
    const login = parseLoginRequest(body);
    if (!login) return json(400, "INVALID_REQUEST");

    stage = "environment";
    const projectUrl = requiredEnvironment("SUPABASE_URL").replace(/\/$/, "");
    const serviceKey = requiredEnvironment("SUPABASE_SERVICE_ROLE_KEY");
    const expectedDiagnostic = Deno.env.get("GDAD_LOGIN_DIAGNOSTIC_TOKEN")
      ?.trim();
    const suppliedDiagnostic = request.headers.get("x-gdad-diagnostic-token")
      ?.trim();
    trustedDiagnostic = Boolean(
      expectedDiagnostic &&
        suppliedDiagnostic &&
        await secretsEqual(suppliedDiagnostic!, expectedDiagnostic!),
    );
    const publishableKey = request.headers.get("apikey")?.trim() ?? "";
    stage = "project-key-validation";
    if (!await isValidPublishableKey(projectUrl, publishableKey)) {
      return json(401, "INVALID_PROJECT_KEY");
    }

    stage = "login-secret-validation";
    const pinPepper = decodeBase64Secret(
      requiredEnvironment("GDAD_PIN_PEPPER_V1"),
    );
    const ratePepper = decodeBase64Secret(
      requiredEnvironment("GDAD_RATE_LIMIT_PEPPER_V1"),
    );
    const dummyHash = requiredEnvironment("GDAD_DUMMY_PIN_HASH_V1");
    if (pinPepper.byteLength < 32 || ratePepper.byteLength < 32) {
      throw new Error("invalid secret length");
    }

    stage = "source-fingerprint";
    const requestTime = new Date().toISOString();
    const fingerprint = await sourceFingerprint(
      ratePepper,
      request.headers.get("x-forwarded-for"),
    );
    stage = "login-preparation";
    const prepared = await prepareLogin(
      projectUrl,
      serviceKey,
      login.login_id,
      fingerprint,
      requestTime,
    );

    const expectedUserId = prepared.user_id ?? DUMMY_USER_ID;
    const verifier = prepared.pin_hash ?? dummyHash;
    stage = "pin-verification";
    const pinMatches = await verifyPinHash(
      pinPepper,
      expectedUserId,
      login.pin,
      verifier,
      prepared.pepper_version ?? PIN_PEPPER_VERSION,
    );

    if (prepared.source_limited) return json(429, "TRY_AGAIN_LATER");
    if (prepared.account_locked) return json(401, "INVALID_CREDENTIALS");

    const validIdentity = Boolean(
      prepared.user_id &&
        prepared.auth_email &&
        prepared.pin_hash &&
        prepared.pepper_version === PIN_PEPPER_VERSION,
    );
    if (!pinMatches || !validIdentity) {
      stage = "failed-login-completion";
      await completeLogin(
        projectUrl,
        serviceKey,
        prepared.user_id,
        false,
        requestTime,
      );
      return json(401, "INVALID_CREDENTIALS");
    }

    const established = await establishSession(
      projectUrl,
      serviceKey,
      publishableKey,
      prepared.auth_email!,
      prepared.user_id!,
      trustedDiagnostic,
      (nextStage) => stage = nextStage,
    );
    stage = "successful-login-completion";
    await completeLogin(
      projectUrl,
      serviceKey,
      prepared.user_id,
      true,
      requestTime,
    );

    return new Response(
      JSON.stringify({
        access_token: established.session.access_token,
        refresh_token: established.session.refresh_token,
        expires_in: established.session.expires_in,
        expires_at: established.session.expires_at,
        token_type: established.session.token_type,
        ...diagnosticSuccessDetails(
          trustedDiagnostic,
          established.singleUseVerified,
        ),
      }),
      { status: 200, headers: JSON_HEADERS },
    );
  } catch {
    console.error("pin-login internal failure", stage);
    return json(
      503,
      "SERVICE_UNAVAILABLE",
      diagnosticFailureDetails(trustedDiagnostic, stage),
    );
  }
});
