import { assert, assertEquals } from "@std/assert";
import { parseAccountAdminRequest } from "../../manage-accounts/core.ts";

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
