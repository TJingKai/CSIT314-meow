/** Presentation helpers. All user-supplied strings are escaped before HTML insertion. */
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

export function header(user) {
  const navigation = {
    customer: [
      ["discover", "Discover projects"],
      ["studios", "Design studios"],
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
      ["moderation", "Project moderation"],
    ],
  };
  return `<header class="top"><a class="brand" href="#${{ customer: "discover", designer: "portfolio", admin: "accounts", manager: "reports" }[user?.role] || "login"}"><img class="brand-logo" src="/assets/meow-logo.svg" alt="" width="44" height="44">MEOW</a>
    <nav aria-label="Main navigation">${(navigation[user?.role] || [])
      .map(
        ([path, label]) =>
          `<a href="#${path}" class="${route() === path ? "active" : ""}">${label}</a>`,
      )
      .join("")}</nav>
    <div class="actions">${
      user
        ? `<a href="#profile" class="username">${escapeHtml(user.name)}</a>
      <a href="#profile" class="avatar" aria-label="My profile">${escapeHtml(
        user.name
          .split(" ")
          .map((name) => name[0])
          .join(""),
      )}</a>
      <button class="btn text" data-action="logout">Log out ↪</button>`
        : '<a class="btn" href="#login">Log in</a><a class="btn primary" href="#register">Join MEOW</a>'
    }</div></header>`;
}

export function footer() {
  return `<footer><span>© ${new Date().getFullYear()} MEOW. Thoughtful spaces, meaningful connections.</span>
    <span>CSIT314 · Interior Design Platform</span></footer>`;
}

export function cards(
  projects,
  {
    manage = false,
    allowSave = true,
    emptyTitle = "No projects match your search",
  } = {},
) {
  if (!projects.length)
    return `<div class="empty"><h3>${emptyTitle}</h3><p>Try another search or add your first project.</p></div>`;
  return `<div class="grid">${projects
    .map(
      (project) => `<article class="card"><div class="photo">
    <a href="#project/${project.id}" aria-label="View ${escapeHtml(project.title)}">
      <img src="${escapeHtml(project.photos[project.cover])}" alt="${escapeHtml(project.title)}" loading="lazy"></a>
    <span class="badge">${manage ? (project.takenDown ? "Taken down" : project.archived ? "Archived" : project.published ? "Published" : "Draft") : escapeHtml(project.style)}</span>
    ${
      manage || !allowSave
        ? ""
        : `<button class="heart ${project.saved ? "saved" : ""}" data-action="toggle-favourite"
      data-id="${project.id}" data-saved="${project.saved}" aria-label="${project.saved ? "Remove from" : "Save to"} favourites">${project.saved ? "♥" : "♡"}</button>`
    }
    </div><div class="card-body"><h3><a href="#project/${project.id}">${escapeHtml(project.title)}</a></h3>
    <a class="studio" href="#studio/${project.company?.id}">${escapeHtml(project.company?.name || "Design studio")} ✦</a>
    <div class="meta">${escapeHtml(project.property)} · ${project.size} m² · ${escapeHtml(project.location)}</div>
    <div class="card-foot"><strong>${money(project.budget)}</strong><span>${escapeHtml(project.stage)} project ↗</span></div>
    ${
      manage
        ? `<div class="row-actions" style="margin-top:15px">
      <button class="btn small" data-action="edit-project" data-id="${project.id}">Edit</button>
      <button class="btn small" data-action="publish-project" data-id="${project.id}" data-published="${project.published}" ${project.takenDown ? "disabled" : ""}>${project.published ? "Unpublish" : "Publish"}</button>
      <button class="btn small" data-action="archive-project" data-id="${project.id}" data-archived="${project.archived}">${project.archived ? "Restore" : "Archive"}</button>
      <button class="btn small" data-action="delete-project" data-id="${project.id}">Delete</button></div>`
        : ""
    }
    </div></article>`,
    )
    .join("")}</div>`;
}
