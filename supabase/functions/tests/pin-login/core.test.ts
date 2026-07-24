import { assert, assertEquals, assertNotEquals } from "@std/assert";
import {
  createPinHash,
  decodeBase64Secret,
  diagnosticFailureDetails,
  diagnosticSuccessDetails,
  parseGeneratedLinkToken,
  parseLoginRequest,
  pinMaterial,
  secretsEqual,
  sourceFingerprint,
  verifyPinHash,
} from "../../pin-login/core.ts";

Deno.test("extracts raw and client-wrapped generated-link token hashes", () => {
  assertEquals(
    parseGeneratedLinkToken({ hashed_token: "raw-token" }),
    "raw-token",
  );
  assertEquals(
    parseGeneratedLinkToken({ properties: { hashed_token: "wrapped-token" } }),
    "wrapped-token",
  );
  assertEquals(parseGeneratedLinkToken({ hashed_token: "" }), null);
  assertEquals(parseGeneratedLinkToken({ properties: {} }), null);
});

Deno.test("only a trusted operator diagnostic receives a safe failure stage", () => {
  assertEquals(diagnosticFailureDetails(false, "auth-token-exchange-400"), {});
  assertEquals(diagnosticFailureDetails(true, "auth-token-exchange-400"), {
    stage: "auth-token-exchange-400",
  });
});

Deno.test("only a trusted diagnostic receives single-use success evidence", () => {
  assertEquals(diagnosticSuccessDetails(false, true), {});
  assertEquals(diagnosticSuccessDetails(true, true), {
    single_use_verified: true,
  });
});

Deno.test("diagnostic secret comparison accepts equality and rejects differences", async () => {
  assert(await secretsEqual("one-time-secret", "one-time-secret"));
  assertEquals(await secretsEqual("one-time-secret", "other-secret"), false);
});

Deno.test("normalizes and validates the exact login request contract", () => {
  assertEquals(
    parseLoginRequest({
      login_id: "  Owner.Kathmandu  ",
      pin: "123456",
      request_id: "550e8400-e29b-41d4-a716-446655440000",
      device_id: "installation-identifier-01",
    }),
    {
      login_id: "owner.kathmandu",
      pin: "123456",
      request_id: "550e8400-e29b-41d4-a716-446655440000",
      device_id: "installation-identifier-01",
    },
  );
});

Deno.test("rejects malformed or expanded request bodies", () => {
  assertEquals(
    parseLoginRequest({
      login_id: "owner.kathmandu",
      pin: "12345",
      request_id: "550e8400-e29b-41d4-a716-446655440000",
      device_id: "installation-identifier-01",
    }),
    null,
  );
  assertEquals(
    parseLoginRequest({
      login_id: "owner.kathmandu",
      pin: "123456",
      request_id: "550e8400-e29b-41d4-a716-446655440000",
      device_id: "installation-identifier-01",
      unexpected: true,
    }),
    null,
  );
});

Deno.test("PIN material is deterministic for one identity and isolated between identities", async () => {
  const pepper = new Uint8Array(32).fill(7);
  const first = await pinMaterial(
    pepper,
    "550e8400-e29b-41d4-a716-446655440000",
    "123456",
  );
  const repeated = await pinMaterial(
    pepper,
    "550e8400-e29b-41d4-a716-446655440000",
    "123456",
  );
  const otherUser = await pinMaterial(
    pepper,
    "550e8400-e29b-41d4-a716-446655440001",
    "123456",
  );
  assertEquals(first, repeated);
  assertNotEquals(first, otherUser);
  assert(!first.includes("="));
});

Deno.test("Argon2id verifies only the correct peppered material", async () => {
  const pepper = new Uint8Array(32).fill(11);
  const userId = "550e8400-e29b-41d4-a716-446655440000";
  const hash = await createPinHash(pepper, userId, "876543", {
    salt: new Uint8Array(16).fill(13),
  });
  assert(await verifyPinHash(pepper, userId, "876543", hash));
  assertEquals(await verifyPinHash(pepper, userId, "876544", hash), false);
});

Deno.test("source fingerprint is a stable non-plaintext SHA-256 HMAC", async () => {
  const pepper = decodeBase64Secret(
    "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc=",
  );
  const first = await sourceFingerprint(pepper, "203.0.113.4");
  const repeated = await sourceFingerprint(pepper, "203.0.113.4");
  assertEquals(first, repeated);
  assertEquals(first.length, 64);
  assert(!first.includes("203.0.113.4"));
});
