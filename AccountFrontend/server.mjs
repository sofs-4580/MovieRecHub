import { createServer, request as httpRequest } from "node:http";
import { request as httpsRequest } from "node:https";
import { readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));

const apiMethods = new Map([
  ["/api/auth/register", "POST"],
  ["/api/auth/login", "POST"],
  ["/api/users/me/password", "PATCH"]
]);

const assets = new Map([
  ["/", ["index.html", "text/html; charset=utf-8"]],
  ["/index.html", ["index.html", "text/html; charset=utf-8"]],
  ["/styles.css", ["styles.css", "text/css; charset=utf-8"]],
  ["/src/app.js", ["src/app.js", "text/javascript; charset=utf-8"]],
  ["/src/api.js", ["src/api.js", "text/javascript; charset=utf-8"]]
]);

function jsonError(response, status, code, message) {
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  response.end(JSON.stringify({ code, message }));
}

function proxyRequest(incoming, outgoing, upstream, parsedUrl) {
  const transport = upstream.protocol === "https:" ? httpsRequest : httpRequest;
  const headers = { ...incoming.headers, host: upstream.host };
  delete headers.connection;
  delete headers["proxy-connection"];

  const request = transport({
    protocol: upstream.protocol,
    hostname: upstream.hostname,
    port: upstream.port || (upstream.protocol === "https:" ? 443 : 80),
    method: incoming.method,
    path: parsedUrl.pathname + parsedUrl.search,
    headers
  }, (response) => {
    outgoing.writeHead(response.statusCode || 502, response.headers);
    response.pipe(outgoing);
  });

  request.setTimeout(15000, () => request.destroy(new Error("UserService timeout")));
  request.on("error", () => {
    if (!outgoing.headersSent) {
      jsonError(outgoing, 502, "USER_SERVICE_UNAVAILABLE", "UserService indisponível");
    } else {
      outgoing.destroy();
    }
  });
  incoming.pipe(request);
}

export function createFrontendServer({
  userServiceUrl = process.env.USER_SERVICE_URL || "http://127.0.0.1:8081"
} = {}) {
  const upstream = new URL(userServiceUrl);
  if (!["http:", "https:"].includes(upstream.protocol)
      || upstream.pathname !== "/" || upstream.search || upstream.hash
      || upstream.username || upstream.password) {
    throw new Error("USER_SERVICE_URL must be an HTTP(S) origin without a path");
  }

  return createServer((incoming, outgoing) => {
    let parsedUrl;
    try {
      parsedUrl = new URL(incoming.url, "http://frontend.local");
    } catch {
      jsonError(outgoing, 400, "INVALID_URL", "Endereço inválido");
      return;
    }

    if (parsedUrl.pathname.startsWith("/api/")) {
      const expectedMethod = apiMethods.get(parsedUrl.pathname);
      if (!expectedMethod) {
        jsonError(outgoing, 404, "NOT_FOUND", "Endpoint não encontrado");
      } else if (incoming.method !== expectedMethod) {
        jsonError(outgoing, 405, "METHOD_NOT_ALLOWED", "Método não permitido");
      } else {
        proxyRequest(incoming, outgoing, upstream, parsedUrl);
      }
      return;
    }

    const asset = assets.get(parsedUrl.pathname);
    if (incoming.method !== "GET" || !asset) {
      outgoing.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      outgoing.end("Página não encontrada");
      return;
    }

    readFile(join(root, asset[0])).then((contents) => {
      outgoing.writeHead(200, {
        "Content-Type": asset[1],
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
        "Referrer-Policy": "no-referrer",
        "Content-Security-Policy": "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; frame-ancestors 'none'"
      });
      outgoing.end(contents);
    }).catch(() => {
      outgoing.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
      outgoing.end("Erro ao carregar a aplicação");
    });
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.FRONTEND_PORT || 3000);
  const host = process.env.FRONTEND_HOST || "127.0.0.1";
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("FRONTEND_PORT must be between 1 and 65535");
  }
  createFrontendServer().listen(port, host, () => {
    console.log("Frontend disponível em http://" + host + ":" + port);
  });
}
