import { assert, assertEquals, assertNotEquals } from "@std/assert";
import { argon2id, argon2Verify } from "hash-wasm";
import {
  decodeBase64Secret,
  parseLoginRequest,
  pinMaterial,
  sourceFingerprint,
} from "../../pin-login/core.ts";

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
  const material = await pinMaterial(
    new Uint8Array(32).fill(11),
    "550e8400-e29b-41d4-a716-446655440000",
    "876543",
  );
  const hash = await argon2id({
    password: material,
    salt: new Uint8Array(16).fill(13),
    parallelism: 1,
    iterations: 2,
    memorySize: 19456,
    hashLength: 32,
    outputType: "encoded",
  });
  assert(await argon2Verify({ password: material, hash }));
  assertEquals(await argon2Verify({ password: `${material}x`, hash }), false);
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
