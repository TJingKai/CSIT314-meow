export const $ = (selector) => document.querySelector(selector);
export const roles = {
  customer: "Customer",
  designer: "Interior Designer",
  admin: "User Admin",
  manager: "Platform Manager",
};
export const escapeHtml = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (character) =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      })[character],
  );
export const money = (value) => `S$${Number(value).toLocaleString("en-SG")}`;
export const route = () => location.hash.slice(1).split("/")[0] || "discover";
export const identifier = () => location.hash.slice(1).split("/")[1];
export const navigate = (path) => {
  location.hash = path;
};
let toastTimeout;

export function toast(message) {
  $("#toast").textContent = message;
  $("#toast").style.display = "block";
  clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => {
    $("#toast").style.display = "none";
  }, 4000);
}

export function field(label, name, value = "", type = "text", required = true) {
  return `<label class="label" for="${name}">${label}</label>
    <input id="${name}" name="${name}" type="${type}" value="${escapeHtml(value)}"
      ${required ? "required" : ""} ${type === "password" ? 'minlength="10" maxlength="128"' : 'maxlength="300"'}>`;
}

export function options(values, selected = "") {
  return values
    .map(
      (value) =>
        `<option ${value === selected ? "selected" : ""}>${escapeHtml(value)}</option>`,
    )
    .join("");
}

export function select(label, name, values, selected) {
  return `<label class="label" for="${name}">${label}</label>
    <select id="${name}" name="${name}">${options(values, selected)}</select>`;
}

export function stat(title, value, caption) {
  return `<div class="stat"><span>${title}</span><strong>${value}</strong><span>${caption}</span></div>`;
}

export function modal(title, content) {
  const dialog = $("#modal");
  dialog.innerHTML = `<div class="dialog-head"><h2 id="dialogTitle">${title}</h2>
    <button class="close" data-action="close-modal" aria-label="Close">×</button></div>${content}`;
  dialog.showModal();
}

export function formError(form, error) {
  let element = form.querySelector(".error");
  if (!element) {
    element = document.createElement("div");
    element.className = "error";
    element.setAttribute("role", "alert");
    form.append(element);
  }
  element.textContent = error.message || String(error);
}

export function header(user, allowedScreens = []) {
  const navigation = {
    customer: [
      ["discover", "Discover projects"],
      
      ["favourites", "My favourites ♡"],
    ],
    designer: [
      ["portfolio", "My projects"],
      ["analytics", "Analytics"],
      ["company", "Company profile"],
    ],
    admin: [
      ["accounts", "User accounts"],
      ["verification", "Designer verification"],
    ],
    manager: [
      ["reports", "Activity reports"],
      ["categories", "Categories"],
      
    ],
  };
  return `<header class="top"><a class="brand" href="#${{ customer: "discover", designer: "portfolio", admin: "accounts", manager: "reports" }[user?.role] || "login"}"><img class="brand-logo" src="/assets/meow-logo.svg" alt="" width="44" height="44">MEOW</a>
    <nav aria-label="Main navigation">${(navigation[user?.role] || []).filter(([path]) => allowedScreens.includes(path))
      .map(
        ([path, label]) =>
          `<a href="#${path}" class="${route() === path ? "active" : ""}">${label}</a>`,
      )
      .join("")}</nav>
    <div class="actions">${
      user
        ? `<span class="username">${escapeHtml(user.name)}</span>
      <span class="avatar" aria-label="Account">${escapeHtml(
        user.name
          .split(" ")
          .map((name) => name[0])
          .join(""),
       )}</span>
      <button class="btn text" data-action="logout">Log out </button>`
        : '<a class="btn" href="#login">Log in</a><a class="btn primary" href="#register">Join MEOW</a>'
    }</div></header>`;
}

export function footer() {
  return `<footer><span>© ${new Date().getFullYear()} MEOW. Thoughtful spaces, meaningful connections.</span>
    <span>CSIT314 · Interior Design Platform</span></footer>`;
}

