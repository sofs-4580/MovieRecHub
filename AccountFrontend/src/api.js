export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

async function send(path, method, body) {
  let response;
  try {
    response = await fetch(path, {
      method,
      credentials: "same-origin",
      cache: "no-store",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "Não foi possível contactar o servidor");
  }

  let payload = null;
  if (response.headers.get("content-type")?.includes("application/json")) {
    try {
      payload = await response.json();
    } catch {
      // A resposta HTTP continua a ser tratada pelo seu estado.
    }
  }

  if (!response.ok) {
    throw new ApiError(
      response.status,
      payload?.code || "HTTP_" + response.status,
      payload?.message || "Pedido não concluído"
    );
  }
  return payload;
}

export function registerAccount({ username, password, favoriteGenres }) {
  return send("/api/auth/register", "POST", { username, password, favoriteGenres });
}

export function loginAccount({ username, password }) {
  return send("/api/auth/login", "POST", { username, password });
}

export function changeAccountPassword({ currentPassword, newPassword }) {
  return send("/api/users/me/password", "PATCH", { currentPassword, newPassword });
}
