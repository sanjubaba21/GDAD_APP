import { argon2id, argon2Verify } from "hash-wasm";

export const PIN_PEPPER_VERSION = 1;
export const LOGIN_ID_PATTERN = /^[a-z0-9][a-z0-9._-]{2,63}$/;
export const PIN_PATTERN = /^\d{6,8}$/;
export const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const ARGON2_MEMORY_KIB = 19_456;
const ARGON2_ITERATIONS = 2;
const ARGON2_PARALLELISM = 1;
const ARGON2_HASH_LENGTH = 32;
const ARGON2_SALT_LENGTH = 16;

export function normalizeLoginId(value: string): string | null {
  const normalized = value.trim().toLowerCase();
  return LOGIN_ID_PATTERN.test(normalized) ? normalized : null;
}

export function isValidPin(pin: string): boolean {
  return PIN_PATTERN.test(pin);
}

export function bytesToBase64Unpadded(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=+$/, "");
}

export function decodeBase64Secret(value: string): Uint8Array {
  const normalized = value.trim().replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  const binary = atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

export async function hmacSha256(
  key: Uint8Array,
  value: string,
): Promise<Uint8Array> {
  const keyData = new ArrayBuffer(key.byteLength);
  new Uint8Array(keyData).set(key);
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyData,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return new Uint8Array(
    await crypto.subtle.sign(
      "HMAC",
      cryptoKey,
      new TextEncoder().encode(value),
    ),
  );
}

export async function pinMaterial(
  pepper: Uint8Array,
  userId: string,
  pin: string,
  pepperVersion = PIN_PEPPER_VERSION,
): Promise<string> {
  if (pepperVersion !== PIN_PEPPER_VERSION) {
    throw new Error("unsupported PIN pepper version");
  }
  if (pepper.byteLength < 32) throw new Error("PIN pepper is too short");
  if (!UUID_PATTERN.test(userId) || !isValidPin(pin)) {
    throw new Error("invalid PIN material input");
  }
  return bytesToBase64Unpadded(
    await hmacSha256(pepper, `gdad-pin-v1\0${userId}\0${pin}`),
  );
}

export async function createPinHash(
  pepper: Uint8Array,
  userId: string,
  pin: string,
  options: { pepperVersion?: number; salt?: Uint8Array } = {},
): Promise<string> {
  const pepperVersion = options.pepperVersion ?? PIN_PEPPER_VERSION;
  const material = await pinMaterial(pepper, userId, pin, pepperVersion);
  const salt = options.salt ?? crypto.getRandomValues(
    new Uint8Array(ARGON2_SALT_LENGTH),
  );
  if (salt.byteLength !== ARGON2_SALT_LENGTH) {
    throw new Error("invalid Argon2id salt length");
  }
  return await argon2id({
    password: material,
    salt,
    parallelism: ARGON2_PARALLELISM,
    iterations: ARGON2_ITERATIONS,
    memorySize: ARGON2_MEMORY_KIB,
    hashLength: ARGON2_HASH_LENGTH,
    outputType: "encoded",
  });
}

export async function verifyPinHash(
  pepper: Uint8Array,
  userId: string,
  pin: string,
  hash: string,
  pepperVersion = PIN_PEPPER_VERSION,
): Promise<boolean> {
  if (pepperVersion !== PIN_PEPPER_VERSION) return false;
  let material: string;
  try {
    material = await pinMaterial(pepper, userId, pin, pepperVersion);
  } catch {
    return false;
  }
  try {
    return await argon2Verify({ password: material, hash });
  } catch {
    return false;
  }
}
