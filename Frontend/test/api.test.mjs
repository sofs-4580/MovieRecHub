import test from "node:test";
import assert from "node:assert/strict";
import {
  ApiError, registerAccount, loginAccount, changeAccountPassword
} from "../src/api.js";

test("sends the three account requests with same-origin session cookies", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => { globalThis.fetch = originalFetch; });
  const calls = [];
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options });
    if (path === "/api/users/me/password") {
      return new Response(null, { status: 204 });
    }
    return new Response(JSON.stringify({ userId: 34, username: "cine_fan" }), {
      status: path.endsWith("/register") ? 201 : 200,
      headers: { "Content-Type": "application/json" }
    });
  };

  await registerAccount({
    username: "cine_fan", password: "password-testing-123", favoriteGenres: ["DRAMA"]
  });
  await loginAccount({ username: "cine_fan", password: "password-testing-123" });
  assert.equal(await changeAccountPassword({
    currentPassword: "password-testing-123", newPassword: "new-password-123"
  }), null);

  assert.deepEqual(calls.map((call) => [call.path, call.options.method]), [
    ["/api/auth/register", "POST"],
    ["/api/auth/login", "POST"],
    ["/api/users/me/password", "PATCH"]
  ]);
  assert.ok(calls.every((call) => call.options.credentials === "same-origin"));
  assert.deepEqual(JSON.parse(calls[0].options.body).favoriteGenres, ["DRAMA"]);
  assert.deepEqual(Object.keys(JSON.parse(calls[2].options.body)).sort(), [
    "currentPassword", "newPassword"
  ]);
});

test("keeps backend error codes for the interface", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => { globalThis.fetch = originalFetch; });
  globalThis.fetch = async () => new Response(
    JSON.stringify({ code: "INVALID_CURRENT_PASSWORD", message: "Wrong password" }),
    { status: 403, headers: { "Content-Type": "application/json" } }
  );
  await assert.rejects(
    changeAccountPassword({ currentPassword: "wrong", newPassword: "new-password-123" }),
    (error) => error instanceof ApiError
      && error.status === 403 && error.code === "INVALID_CURRENT_PASSWORD"
  );
});

