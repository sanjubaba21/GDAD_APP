import { assert, assertEquals } from "@std/assert";
import {
  clientRole,
  internalEmail,
  isWeakPin,
  parseProvisionRequest,
  secretsEqual,
} from "../../manage-users/core.ts";

const REQUEST_ID = "550e8400-e29b-41d4-a716-446655440000";
const SHOP_ID = "11111111-1111-4111-8111-111111111111";

Deno.test("parses normalized Owner and Salesman provisioning requests", () => {
  for (const action of ["create_owner", "create_salesman"] as const) {
    assertEquals(
      parseProvisionRequest({
        action,
        request_id: REQUEST_ID.toUpperCase(),
        login_id: "  Managed.User  ",
        display_name: "  Managed User  ",
        pin: "473829",
        shop_id: SHOP_ID.toUpperCase(),
      }),
      {
        action,
        request_id: REQUEST_ID,
        login_id: "managed.user",
        display_name: "Managed User",
        pin: "473829",
        shop_id: SHOP_ID,
      },
    );
  }
});

Deno.test("bootstrap requires no shop while managed roles require a shop", () => {
  assert(parseProvisionRequest({
    action: "bootstrap_super_admin",
    request_id: REQUEST_ID,
    login_id: "admin.primary",
    display_name: "Primary Admin",
    pin: "473829",
    shop_id: null,
  }));
  assertEquals(
    parseProvisionRequest({
      action: "bootstrap_super_admin",
      request_id: REQUEST_ID,
      login_id: "admin.primary",
      display_name: "Primary Admin",
      pin: "473829",
      shop_id: SHOP_ID,
    }),
    null,
  );
  assertEquals(
    parseProvisionRequest({
      action: "create_salesman",
      request_id: REQUEST_ID,
      login_id: "sales.person",
      display_name: "Sales Person",
      pin: "473829",
      shop_id: null,
    }),
    null,
  );
});

Deno.test("rejects weak PINs, expanded bodies, and malformed identifiers", () => {
  assert(isWeakPin("123456"));
  assert(isWeakPin("777777"));
  const valid = {
    action: "create_owner",
    request_id: REQUEST_ID,
    login_id: "owner.one",
    display_name: "Owner One",
    pin: "473829",
    shop_id: SHOP_ID,
  };
  assertEquals(parseProvisionRequest({ ...valid, pin: "123456" }), null);
  assertEquals(parseProvisionRequest({ ...valid, unexpected: true }), null);
  assertEquals(
    parseProvisionRequest({ ...valid, request_id: "not-a-uuid" }),
    null,
  );
});

Deno.test("derives only deterministic client-safe identity labels", () => {
  assertEquals(
    internalEmail(REQUEST_ID),
    "acct.550e8400e29b41d4a716446655440000@auth.gdad.invalid",
  );
  assertEquals(clientRole("bootstrap_super_admin"), "super_admin");
  assertEquals(clientRole("create_owner"), "owner");
  assertEquals(clientRole("create_salesman"), "salesman");
});

Deno.test("bootstrap secret comparison accepts equality and rejects differences", async () => {
  assert(await secretsEqual("fixed-test-secret", "fixed-test-secret"));
  assertEquals(
    await secretsEqual("fixed-test-secret", "other-test-secret"),
    false,
  );
});
