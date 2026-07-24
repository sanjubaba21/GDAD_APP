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

export function parseGeneratedLinkToken(value: unknown): string | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const response = value as Record<string, unknown>;
  const direct = response.hashed_token;
  if (typeof direct === "string" && direct.length > 0) return direct;

  const properties = response.properties;
  if (
    !properties || typeof properties !== "object" || Array.isArray(properties)
  ) {
    return null;
  }
  const nested = (properties as Record<string, unknown>).hashed_token;
  return typeof nested === "string" && nested.length > 0 ? nested : null;
}

export function diagnosticFailureDetails(
  trustedDiagnostic: boolean,
  stage: string,
): Record<string, string> {
  return trustedDiagnostic ? { stage } : {};
}

export function diagnosticSuccessDetails(
  trustedDiagnostic: boolean,
  singleUseVerified: boolean,
): Record<string, boolean> {
  return trustedDiagnostic ? { single_use_verified: singleUseVerified } : {};
}

export async function secretsEqual(
  left: string,
  right: string,
): Promise<boolean> {
  const encoder = new TextEncoder();
  const [leftDigest, rightDigest] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(left)),
    crypto.subtle.digest("SHA-256", encoder.encode(right)),
  ]);
  const leftBytes = new Uint8Array(leftDigest);
  const rightBytes = new Uint8Array(rightDigest);
  let difference = 0;
  for (let index = 0; index < leftBytes.length; index++) {
    difference |= leftBytes[index] ^ rightBytes[index];
  }
  return difference === 0;
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
