import {
  assert,
  assertEquals,
  assertNotEquals,
  assertRejects,
} from "@std/assert";
import { argon2id } from "hash-wasm";
import {
  createPinHash,
  isValidPin,
  isValidPinForVerification,
  normalizeLoginId,
  PIN_PEPPER_VERSION,
  verificationPinMaterial,
  verifyPinHash,
} from "../../_shared/pin.ts";

const USER_ID = "550e8400-e29b-41d4-a716-446655440000";
const PEPPER = new Uint8Array(32).fill(23);

Deno.test("shared login and PIN normalization matches the contract", () => {
  assertEquals(normalizeLoginId("  Owner.Kathmandu  "), "owner.kathmandu");
  assertEquals(normalizeLoginId("bad login"), null);
  assert(isValidPin("123456"));
  assert(isValidPin("12345678"));
  assertEquals(isValidPin("12345"), false);
  assertEquals(isValidPin("12345x"), false);
  assert(isValidPinForVerification("1234"));
  assert(isValidPinForVerification("12345"));
  assert(isValidPinForVerification("12345678"));
  assertEquals(isValidPinForVerification("123"), false);
  assertEquals(isValidPinForVerification("123456789"), false);
  assertEquals(isValidPinForVerification("123x"), false);
});

Deno.test("legacy PINs verify but cannot be used for new credentials", async () => {
  const legacyPin = "4826";
  const material = await verificationPinMaterial(PEPPER, USER_ID, legacyPin);
  const legacyHash = await argon2id({
    password: material,
    salt: new Uint8Array(16).fill(19),
    parallelism: 1,
    iterations: 2,
    memorySize: 19_456,
    hashLength: 32,
    outputType: "encoded",
  });

  assert(await verifyPinHash(PEPPER, USER_ID, legacyPin, legacyHash));
  assertEquals(await verifyPinHash(PEPPER, USER_ID, "4827", legacyHash), false);
  await assertRejects(
    () => createPinHash(PEPPER, USER_ID, legacyPin),
    Error,
    "invalid PIN material input",
  );
});

Deno.test("shared verifier accepts the correct PIN and rejects the wrong PIN", async () => {
  const hash = await createPinHash(PEPPER, USER_ID, "473829", {
    salt: new Uint8Array(16).fill(31),
  });
  assert(await verifyPinHash(PEPPER, USER_ID, "473829", hash));
  assertEquals(await verifyPinHash(PEPPER, USER_ID, "473820", hash), false);
});

Deno.test("identical PINs use independent Argon2id salts", async () => {
  const first = await createPinHash(PEPPER, USER_ID, "473829");
  const second = await createPinHash(PEPPER, USER_ID, "473829");
  assertNotEquals(first, second);
  assert(await verifyPinHash(PEPPER, USER_ID, "473829", first));
  assert(await verifyPinHash(PEPPER, USER_ID, "473829", second));
});

Deno.test("unsupported pepper versions cannot create or verify credentials", async () => {
  await assertRejects(
    () => createPinHash(PEPPER, USER_ID, "473829", { pepperVersion: 2 }),
    Error,
    "unsupported PIN pepper version",
  );
  const hash = await createPinHash(PEPPER, USER_ID, "473829");
  assertEquals(await verifyPinHash(PEPPER, USER_ID, "473829", hash, 2), false);
  assertEquals(PIN_PEPPER_VERSION, 1);
});

Deno.test("malformed PHC strings fail closed without escaping an error", async () => {
  assertEquals(
    await verifyPinHash(PEPPER, USER_ID, "473829", "not-an-argon2-phc"),
    false,
  );
});

Deno.test("dummy and real hashes use the same shared verification function", async () => {
  const realHash = await createPinHash(PEPPER, USER_ID, "473829", {
    salt: new Uint8Array(16).fill(41),
  });
  const dummyHash = await createPinHash(
    PEPPER,
    "00000000-0000-4000-8000-000000000000",
    "000000",
    { salt: new Uint8Array(16).fill(42) },
  );
  assert(await verifyPinHash(PEPPER, USER_ID, "473829", realHash));
  assertEquals(
    await verifyPinHash(
      PEPPER,
      "00000000-0000-4000-8000-000000000000",
      "473829",
      dummyHash,
    ),
    false,
  );
});
