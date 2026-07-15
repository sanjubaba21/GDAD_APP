export const LOGIN_ID_PATTERN = /^[a-z0-9][a-z0-9._-]{2,63}$/;
export const PIN_PATTERN = /^\d{6,8}$/;
export const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export interface LoginRequest {
  login_id: string;
  pin: string;
  request_id: string;
  device_id: string;
}

const ALLOWED_FIELDS = new Set(["login_id", "pin", "request_id", "device_id"]);

export function parseLoginRequest(value: unknown): LoginRequest | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;

  const record = value as Record<string, unknown>;
  if (Object.keys(record).some((key) => !ALLOWED_FIELDS.has(key))) return null;
  if (
    typeof record.login_id !== "string" ||
    typeof record.pin !== "string" ||
    typeof record.request_id !== "string" ||
    typeof record.device_id !== "string"
  ) return null;

  const loginId = record.login_id.trim().toLowerCase();
  if (
    !LOGIN_ID_PATTERN.test(loginId) ||
    !PIN_PATTERN.test(record.pin) ||
    !UUID_PATTERN.test(record.request_id) ||
    record.device_id.length < 16 ||
    record.device_id.length > 128
  ) return null;

  return {
    login_id: loginId,
    pin: record.pin,
    request_id: record.request_id.toLowerCase(),
    device_id: record.device_id,
  };
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
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    key,
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
): Promise<string> {
  return bytesToBase64Unpadded(
    await hmacSha256(pepper, `gdad-pin-v1\0${userId}\0${pin}`),
  );
}

export async function sourceFingerprint(
  pepper: Uint8Array,
  forwardedFor: string | null,
): Promise<string> {
  const boundedSource = (forwardedFor ?? "unavailable").trim().slice(0, 256);
  const digest = await hmacSha256(pepper, `gdad-rate-v1\0${boundedSource}`);
  return Array.from(digest, (byte) => byte.toString(16).padStart(2, "0")).join(
    "",
  );
}
