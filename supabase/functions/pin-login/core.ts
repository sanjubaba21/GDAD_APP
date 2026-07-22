export {
  bytesToBase64Unpadded,
  createPinHash,
  decodeBase64Secret,
  hmacSha256,
  isValidPin,
  LOGIN_ID_PATTERN,
  normalizeLoginId,
  PIN_PATTERN,
  PIN_PEPPER_VERSION,
  pinMaterial,
  UUID_PATTERN,
  verifyPinHash,
} from "../_shared/pin.ts";
import {
  hmacSha256,
  isValidPin,
  normalizeLoginId,
  UUID_PATTERN,
} from "../_shared/pin.ts";

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

  const loginId = normalizeLoginId(record.login_id);
  if (
    loginId === null ||
    !isValidPin(record.pin) ||
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
