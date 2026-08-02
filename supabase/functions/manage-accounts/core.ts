import { isValidPin, UUID_PATTERN } from "../_shared/pin.ts";
import { isWeakPin } from "../manage-users/core.ts";

export type AccountAdminAction = "disable_user" | "enable_user" | "reset_pin";

export interface AccountAdminRequest {
  action: AccountAdminAction;
  request_id: string;
  target_user_id: string;
  reauth_pin: string;
  new_pin: string | null;
}

const BASE_FIELDS = new Set([
  "action",
  "request_id",
  "target_user_id",
  "reauth_pin",
]);

export function parseAccountAdminRequest(
  value: unknown,
): AccountAdminRequest | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const record = value as Record<string, unknown>;
  if (
    record.action !== "disable_user" &&
    record.action !== "enable_user" &&
    record.action !== "reset_pin"
  ) return null;
  const allowed = record.action === "reset_pin"
    ? new Set([...BASE_FIELDS, "new_pin"])
    : BASE_FIELDS;
  if (Object.keys(record).some((key) => !allowed.has(key))) return null;
  if (
    typeof record.request_id !== "string" ||
    typeof record.target_user_id !== "string" ||
    typeof record.reauth_pin !== "string" ||
    !UUID_PATTERN.test(record.request_id) ||
    !UUID_PATTERN.test(record.target_user_id) ||
    !isValidPin(record.reauth_pin)
  ) return null;

  let newPin: string | null = null;
  if (record.action === "reset_pin") {
    if (
      typeof record.new_pin !== "string" ||
      !isValidPin(record.new_pin) ||
      isWeakPin(record.new_pin)
    ) return null;
    newPin = record.new_pin;
  } else if (record.new_pin !== undefined) return null;

  return {
    action: record.action,
    request_id: record.request_id.toLowerCase(),
    target_user_id: record.target_user_id.toLowerCase(),
    reauth_pin: record.reauth_pin,
    new_pin: newPin,
  };
}
