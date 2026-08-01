const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SAFE_LABEL = /^[a-z0-9][a-z0-9_-]{0,95}$/;

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
  "content-security-policy": "default-src 'none'; frame-ancestors 'none'",
  "referrer-policy": "no-referrer",
};

/** Uses a validated idempotency UUID when available and otherwise creates an opaque request UUID. */
export function correlationIdFor(requestId?: string | null): string {
  const candidate = requestId?.trim().toLowerCase() ?? "";
  return UUID_PATTERN.test(candidate) ? candidate : crypto.randomUUID();
}

export function operationalHeaders(
  correlationId: string,
  additional: Record<string, string> = {},
): Record<string, string> {
  if (!UUID_PATTERN.test(correlationId)) {
    throw new Error("invalid correlation identifier");
  }
  return {
    ...JSON_HEADERS,
    "x-gdad-correlation-id": correlationId.toLowerCase(),
    ...additional,
  };
}

/** Serializes an allow-listed operational event without exception messages or request data. */
export function operationalFailure(
  functionName: string,
  stage: string,
  correlationId: string,
): string {
  const safeFunction = SAFE_LABEL.test(functionName)
    ? functionName
    : "edge-function";
  const safeStage = SAFE_LABEL.test(stage) ? stage : "unknown";
  return JSON.stringify({
    event: "edge_operation_failed",
    function: safeFunction,
    stage: safeStage,
    correlation_id: correlationIdFor(correlationId),
  });
}
