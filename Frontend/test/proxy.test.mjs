import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { once } from "node:events";
import { createFrontendServer } from "../server.mjs";

async function listen(server) {
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  return "http://127.0.0.1:" + server.address().port;
}

async function close(server) {
  await new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
}

test("serves the account screens and limits the proxy to UserService routes", async (t) => {
  const seen = [];
  const userService = createServer(async (request, response) => {
    let body = "";
    for await (const chunk of request) body += chunk;
    seen.push({ method: request.method, path: request.url, cookie: request.headers.cookie, body });

    if (request.url === "/api/auth/login") {
      response.writeHead(200, {
        "Content-Type": "application/json",
        "Set-Cookie": "JSESSIONID=test-session; Path=/; HttpOnly; SameSite=Strict"
      });
      response.end(JSON.stringify({ userId: 34, username: "cine_fan" }));
    } else if (request.url === "/api/users/me/password") {
      response.writeHead(204);
      response.end();
    } else {
      response.writeHead(201, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ userId: 34, username: "cine_fan" }));
    }
  });
  const backend = await listen(userService);
  t.after(() => close(userService));

  const frontend = createFrontendServer({ userServiceUrl: backend });
  const site = await listen(frontend);
  t.after(() => close(frontend));

  const home = await fetch(site);
  assert.equal(home.status, 200);
  const html = await home.text();
  assert.match(html, /id="register-form"/);
  assert.match(html, /id="login-form"/);
  assert.match(html, /id="password-form"/);
  assert.equal((await fetch(site + "/styles.css")).status, 200);
  assert.equal((await fetch(site + "/src/app.js")).status, 200);

  const login = await fetch(site + "/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "cine_fan", password: "password-testing-123" })
  });
  assert.equal(login.status, 200);
  assert.match(login.headers.get("set-cookie"), /JSESSIONID=test-session/);
  assert.equal(seen[0].path, "/api/auth/login");
  assert.equal(JSON.parse(seen[0].body).username, "cine_fan");

  const update = await fetch(site + "/api/users/me/password", {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Cookie: "JSESSIONID=test-session" },
    body: JSON.stringify({ currentPassword: "old-pass", newPassword: "new-password-123" })
  });
  assert.equal(update.status, 204);
  assert.equal(seen[1].cookie, "JSESSIONID=test-session");

  assert.equal((await fetch(site + "/api/auth/login")).status, 405);
  assert.equal((await fetch(site + "/api/unknown")).status, 404);
  assert.equal((await fetch(site + "/internal/auth/session")).status, 404);
  assert.equal((await fetch(site + "/test/proxy.test.mjs")).status, 404);
  assert.equal(seen.length, 2);
});

test("returns a clear error when UserService cannot be reached", async (t) => {
  const frontend = createFrontendServer({ userServiceUrl: "http://127.0.0.1:1" });
  const site = await listen(frontend);
  t.after(() => close(frontend));

  const response = await fetch(site + "/api/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: "{}"
  });
  assert.equal(response.status, 502);
  assert.equal((await response.json()).code, "USER_SERVICE_UNAVAILABLE");
});

