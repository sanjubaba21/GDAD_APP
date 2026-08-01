import { assertEquals, assertMatch } from "@std/assert";
import {
  correlationIdFor,
  operationalFailure,
  operationalHeaders,
} from "../../_shared/operational.ts";

const REQUEST_ID = "550e8400-e29b-41d4-a716-446655440000";

Deno.test("uses a validated request UUID as the cross-system correlation ID", () => {
  assertEquals(correlationIdFor(REQUEST_ID.toUpperCase()), REQUEST_ID);
  assertMatch(correlationIdFor("not-a-request-id"), /^[0-9a-f-]{36}$/);
});

Deno.test("response headers expose correlation without weakening security headers", () => {
  const headers = operationalHeaders(REQUEST_ID, { allow: "POST" });
  assertEquals(headers["x-gdad-correlation-id"], REQUEST_ID);
  assertEquals(headers["cache-control"], "no-store");
  assertEquals(
    headers["content-security-policy"],
    "default-src 'none'; frame-ancestors 'none'",
  );
  assertEquals(headers.allow, "POST");
});

Deno.test("failure event contains only safe operational fields", () => {
  const event = JSON.parse(
    operationalFailure("pin-login", "auth-token-exchange-503", REQUEST_ID),
  );
  assertEquals(event, {
    event: "edge_operation_failed",
    function: "pin-login",
    stage: "auth-token-exchange-503",
    correlation_id: REQUEST_ID,
  });
  assertEquals(Object.keys(event).sort(), [
    "correlation_id",
    "event",
    "function",
    "stage",
  ]);
});

Deno.test("unsafe labels are replaced instead of entering logs", () => {
  const serialized = operationalFailure(
    "pin login body=secret",
    "stage token=secret",
    REQUEST_ID,
  );
  assertEquals(serialized.includes("secret"), false);
  assertEquals(JSON.parse(serialized).function, "edge-function");
  assertEquals(JSON.parse(serialized).stage, "unknown");
});
