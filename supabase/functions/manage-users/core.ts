import { isValidPin, normalizeLoginId, UUID_PATTERN } from "../_shared/pin.ts";

export type ProvisionAction =
  | "bootstrap_super_admin"
  | "create_owner"
  | "create_salesman";

export interface ProvisionRequest {
  action: ProvisionAction;
  request_id: string;
  login_id: string;
  display_name: string;
  pin: string;
  shop_id: string | null;
}

const ALLOWED_FIELDS = new Set([
  "action",
  "request_id",
  "login_id",
  "display_name",
  "pin",
  "shop_id",
]);

export function isWeakPin(pin: string): boolean {
  if (/^(\d)\1+$/.test(pin)) return true;
  const common = new Set([
    "123456",
    "1234567",
    "12345678",
    "654321",
    "7654321",
    "87654321",
    "121212",
    "112233",
  ]);
  return common.has(pin);
}

export function parseProvisionRequest(value: unknown): ProvisionRequest | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const record = value as Record<string, unknown>;
  if (Object.keys(record).some((key) => !ALLOWED_FIELDS.has(key))) return null;
  if (
    record.action !== "bootstrap_super_admin" &&
    record.action !== "create_owner" &&
    record.action !== "create_salesman"
  ) return null;
  if (
    typeof record.request_id !== "string" ||
    typeof record.login_id !== "string" ||
    typeof record.display_name !== "string" ||
    typeof record.pin !== "string"
  ) return null;

  const loginId = normalizeLoginId(record.login_id);
  const displayName = record.display_name.trim();
  const shopId = record.shop_id === undefined ? null : record.shop_id;
  if (
    !UUID_PATTERN.test(record.request_id) ||
    loginId === null ||
    displayName.length < 1 ||
    displayName.length > 120 ||
    !isValidPin(record.pin) ||
    isWeakPin(record.pin)
  ) return null;

  if (record.action === "bootstrap_super_admin" && shopId !== null) return null;
  if (
    (record.action === "create_owner" || record.action === "create_salesman") &&
    (typeof shopId !== "string" || !UUID_PATTERN.test(shopId))
  ) return null;

  return {
    action: record.action,
    request_id: record.request_id.toLowerCase(),
    login_id: loginId,
    display_name: displayName,
    pin: record.pin,
    shop_id: typeof shopId === "string" ? shopId.toLowerCase() : null,
  };
}

export function internalEmail(userId: string): string {
  return `acct.${userId.replaceAll("-", "")}@auth.gdad.invalid`;
}

export function clientRole(action: ProvisionAction): string {
  switch (action) {
    case "bootstrap_super_admin":
      return "super_admin";
    case "create_owner":
      return "owner";
    case "create_salesman":
      return "salesman";
  }
}

export function operatorFailureDetails(
  trustedBootstrap: boolean,
  stage: string,
): Record<string, string> {
  return trustedBootstrap ? { stage } : {};
}

export interface ProvisioningReserveFailure {
  status: number;
  code:
    | "ACCOUNT_CONFLICT"
    | "INVALID_REQUEST"
    | "UNAUTHORIZED"
    | "OPERATION_FAILED";
}

/**
 * Converts only PostgreSQL SQLSTATE values into client-safe responses. Database
 * messages and details must never cross the Edge Function boundary.
 */
export function classifyProvisioningReserveFailure(
  databaseCode: string | null,
): ProvisioningReserveFailure {
  switch (databaseCode) {
    case "23505":
      return { status: 409, code: "ACCOUNT_CONFLICT" };
    case "22023":
      return { status: 400, code: "INVALID_REQUEST" };
    case "42501":
      return { status: 403, code: "UNAUTHORIZED" };
    default:
      return { status: 503, code: "OPERATION_FAILED" };
  }
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
