import { assert, assertEquals } from "@std/assert";
import {
  parseAccountAdminRequest,
  parseAccountManagementRequest,
} from "../../manage-accounts/core.ts";

const REQUEST_ID = "550e8400-e29b-41d4-a716-446655440000";
const TARGET_ID = "650e8400-e29b-41d4-a716-446655440000";

Deno.test("parses exact disable and enable requests", () => {
  for (const action of ["disable_user", "enable_user"] as const) {
    assertEquals(
      parseAccountAdminRequest({
        action,
        request_id: REQUEST_ID.toUpperCase(),
        target_user_id: TARGET_ID.toUpperCase(),
        reauth_pin: "473829",
      }),
      {
        action,
        request_id: REQUEST_ID,
        target_user_id: TARGET_ID,
        reauth_pin: "473829",
        new_pin: null,
      },
    );
  }
});

Deno.test("reset requires a strong new PIN", () => {
  assert(parseAccountAdminRequest({
    action: "reset_pin",
    request_id: REQUEST_ID,
    target_user_id: TARGET_ID,
    reauth_pin: "473829",
    new_pin: "592847",
  }));
  assertEquals(
    parseAccountAdminRequest({
      action: "reset_pin",
      request_id: REQUEST_ID,
      target_user_id: TARGET_ID,
      reauth_pin: "473829",
      new_pin: "123456",
    }),
    null,
  );
  assertEquals(
    parseAccountAdminRequest({
      action: "reset_pin",
      request_id: REQUEST_ID,
      target_user_id: TARGET_ID,
      reauth_pin: "473829",
      new_pin: "4826",
    }),
    null,
  );
});

Deno.test("legacy existing PIN can reauthenticate account administration", () => {
  assert(
    parseAccountAdminRequest({
      action: "disable_user",
      request_id: REQUEST_ID,
      target_user_id: TARGET_ID,
      reauth_pin: "4826",
    }),
  );
});

Deno.test("rejects malformed and expanded administration bodies", () => {
  const valid = {
    action: "disable_user",
    request_id: REQUEST_ID,
    target_user_id: TARGET_ID,
    reauth_pin: "473829",
  };
  assertEquals(parseAccountAdminRequest({ ...valid, new_pin: "592847" }), null);
  assertEquals(parseAccountAdminRequest({ ...valid, role: "owner" }), null);
  assertEquals(
    parseAccountAdminRequest({ ...valid, target_user_id: "bad" }),
    null,
  );
});

Deno.test("parses the exact destructive shop deletion request", () => {
  assertEquals(
    parseAccountManagementRequest({
      action: "delete_shop",
      request_id: REQUEST_ID.toUpperCase(),
      target_shop_id: TARGET_ID.toUpperCase(),
      confirmation_slug: "test-shop-1",
      reason: "  Controlled test shop cleanup  ",
      reauth_pin: "473829",
    }),
    {
      action: "delete_shop",
      request_id: REQUEST_ID,
      target_shop_id: TARGET_ID,
      confirmation_slug: "test-shop-1",
      reason: "Controlled test shop cleanup",
      reauth_pin: "473829",
    },
  );
});

Deno.test("shop deletion accepts legacy reauthentication PIN", () => {
  assertEquals(
    parseAccountManagementRequest({
      action: "delete_shop",
      request_id: REQUEST_ID,
      target_shop_id: TARGET_ID,
      confirmation_slug: "test-shop-1",
      reason: "Controlled test shop cleanup",
      reauth_pin: "4826",
    })?.reauth_pin,
    "4826",
  );
});

Deno.test("shop deletion rejects weak confirmation and expanded bodies", () => {
  const valid = {
    action: "delete_shop",
    request_id: REQUEST_ID,
    target_shop_id: TARGET_ID,
    confirmation_slug: "test-shop-1",
    reason: "Controlled test shop cleanup",
    reauth_pin: "473829",
  };
  assertEquals(
    parseAccountManagementRequest({ ...valid, confirmation_slug: "Test-Shop" }),
    null,
  );
  assertEquals(
    parseAccountManagementRequest({ ...valid, reason: "short" }),
    null,
  );
  assertEquals(
    parseAccountManagementRequest({ ...valid, delete_users: true }),
    null,
  );
  assertEquals(
    parseAccountManagementRequest({ ...valid, reauth_pin: "123" }),
    null,
  );
});
