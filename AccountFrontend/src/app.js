import { ApiError, registerAccount, loginAccount, changeAccountPassword } from "./api.js";

const views = new Set(["login", "register", "account", "password"]);
const usernamePattern = /^[a-z0-9_]{3,30}$/;
const state = { account: null, flash: null };

const notice = document.querySelector("#notice");
const loginForm = document.querySelector("#login-form");
const registerForm = document.querySelector("#register-form");
const passwordForm = document.querySelector("#password-form");

function canonicalUsername(value) {
  return value.trim().toLowerCase();
}

function clearNotice() {
  notice.hidden = true;
  notice.textContent = "";
  notice.classList.remove("error");
}

function showNotice(message, error = false) {
  notice.textContent = message;
  notice.classList.toggle("error", error);
  notice.hidden = false;
}

function routeTo(view, message, error = false) {
  state.flash = message ? { message, error } : null;
  if (window.location.hash === "#" + view) {
    render();
  } else {
    window.location.hash = view;
  }
}

function render() {
  const requested = window.location.hash.slice(1);
  const selected = views.has(requested) ? requested : "login";
  const needsLogin = !state.account && (selected === "account" || selected === "password");
  const view = needsLogin ? "login" : selected;

  for (const section of document.querySelectorAll("[data-view]")) {
    section.hidden = section.dataset.view !== view;
  }
  for (const link of document.querySelectorAll("[data-guest-nav]")) {
    link.hidden = Boolean(state.account);
  }
  for (const link of document.querySelectorAll("[data-account-nav]")) {
    link.hidden = !state.account;
  }
  for (const link of document.querySelectorAll(".topnav a")) {
    if (link.getAttribute("href") === "#" + view) {
      link.setAttribute("aria-current", "page");
    } else {
      link.removeAttribute("aria-current");
    }
  }

  document.querySelector(".brand").href = state.account ? "#account" : "#login";
  document.querySelector("#account-username").textContent = state.account?.username || "";
  document.title = document.querySelector("[data-view='" + view + "'] h2").textContent
    + " — MovieRecHub";

  if (state.flash) {
    showNotice(state.flash.message, state.flash.error);
    state.flash = null;
  } else if (needsLogin) {
    showNotice("Inicia sessão para aceder à tua conta.", true);
  } else {
    clearNotice();
  }
}

function messageFor(error) {
  if (!(error instanceof ApiError)) {
    return "Ocorreu um erro inesperado. Tenta novamente.";
  }
  const messages = {
    USERNAME_TAKEN: "Esse username já está em uso. Escolhe outro.",
    INVALID_CREDENTIALS: "Username ou password incorretos.",
    INVALID_CURRENT_PASSWORD: "A password atual não está correta.",
    UNAUTHORIZED: "A tua sessão expirou. Entra novamente.",
    VALIDATION_ERROR: "Revê os dados do formulário e tenta novamente.",
    INVALID_REQUEST: "O pedido não pôde ser processado.",
    USER_SERVICE_UNAVAILABLE: "O UserService está temporariamente indisponível.",
    NETWORK_ERROR: "Não foi possível contactar o servidor. Tenta novamente."
  };
  return messages[error.code] || "O pedido não foi concluído. Tenta novamente.";
}

function validPassword(value, minimum) {
  return value.trim().length > 0 && value.length >= minimum && value.length <= 128;
}

async function submit(form, operation) {
  if (form.getAttribute("aria-busy") === "true") return;
  const button = form.querySelector("button[type='submit']");
  form.setAttribute("aria-busy", "true");
  button.disabled = true;
  try {
    await operation();
  } catch (error) {
    if (error instanceof ApiError && error.code === "UNAUTHORIZED" && form === passwordForm) {
      state.account = null;
      passwordForm.reset();
      routeTo("login", messageFor(error), true);
    } else {
      showNotice(messageFor(error), true);
    }
  } finally {
    form.removeAttribute("aria-busy");
    button.disabled = false;
  }
}

registerForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearNotice();
  const usernameInput = registerForm.elements.username;
  const passwordInput = registerForm.elements.password;
  const confirmInput = registerForm.elements.confirmPassword;
  const username = canonicalUsername(usernameInput.value);
  const password = passwordInput.value;
  const genres = new FormData(registerForm).getAll("favoriteGenres");

  if (!usernamePattern.test(username)) {
    showNotice("O username deve ter 3 a 30 letras minúsculas, números ou _.", true);
    usernameInput.focus();
    return;
  }
  if (!validPassword(password, 12)) {
    showNotice("A password deve ter entre 12 e 128 caracteres.", true);
    passwordInput.focus();
    return;
  }
  if (password !== confirmInput.value) {
    showNotice("As passwords não coincidem.", true);
    confirmInput.focus();
    return;
  }
  if (genres.length === 0) {
    showNotice("Escolhe pelo menos um género favorito.", true);
    registerForm.querySelector("input[name='favoriteGenres']").focus();
    return;
  }

  submit(registerForm, async () => {
    await registerAccount({ username, password, favoriteGenres: genres });
    registerForm.reset();
    loginForm.elements.username.value = username;
    routeTo("login", "Conta criada. Já podes entrar com as tuas credenciais.");
  });
});

loginForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearNotice();
  const usernameInput = loginForm.elements.username;
  const passwordInput = loginForm.elements.password;
  const username = canonicalUsername(usernameInput.value);
  const password = passwordInput.value;

  if (!usernamePattern.test(username)) {
    showNotice("Introduz um username válido.", true);
    usernameInput.focus();
    return;
  }
  if (!validPassword(password, 1)) {
    showNotice("Introduz a tua password.", true);
    passwordInput.focus();
    return;
  }

  submit(loginForm, async () => {
    const account = await loginAccount({ username, password });
    state.account = { userId: account.userId, username: account.username };
    loginForm.reset();
    routeTo("account", "Sessão iniciada com sucesso.");
  });
});

passwordForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearNotice();
  const currentInput = passwordForm.elements.currentPassword;
  const newInput = passwordForm.elements.newPassword;
  const confirmInput = passwordForm.elements.confirmPassword;

  if (!validPassword(currentInput.value, 1)) {
    showNotice("Introduz a tua password atual.", true);
    currentInput.focus();
    return;
  }
  if (!validPassword(newInput.value, 12)) {
    showNotice("A nova password deve ter entre 12 e 128 caracteres.", true);
    newInput.focus();
    return;
  }
  if (newInput.value !== confirmInput.value) {
    showNotice("As novas passwords não coincidem.", true);
    confirmInput.focus();
    return;
  }

  submit(passwordForm, async () => {
    await changeAccountPassword({
      currentPassword: currentInput.value,
      newPassword: newInput.value
    });
    passwordForm.reset();
    routeTo("account", "Password alterada com sucesso.");
  });
});

for (const link of document.querySelectorAll("a[data-nav]")) {
  link.addEventListener("click", () => {
    if (link.getAttribute("href") === window.location.hash) {
      render();
    }
  });
}

window.addEventListener("hashchange", render);
render();
