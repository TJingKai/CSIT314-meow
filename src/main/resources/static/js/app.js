import { api } from "./api.js";
import {
  $,
  escapeHtml as e,
  field,
  footer,
  header,
  modal,
  toast,
  formError,
  navigate,
  route,
  identifier,
  options,
} from "./ui.js";

let user = null;
let allowedScreens = [];
let revision = 0;
let keyword = "";
let searched = false;
let accountsQuery = "";
let reportState = {
  period: "daily",
  date: new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Singapore",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date()),
  valid: false,
};
const homes = {
  customer: "discover",
  designer: "portfolio",
  admin: "accounts",
  manager: "reports",
};
const permissions = {
  discover: ["customer"],
  favourites: ["customer"],
  project: ["customer", "designer"],
  portfolio: ["designer"],
  company: ["designer"],
  analytics: ["designer"],
  accounts: ["admin"],
  verification: ["admin"],
  categories: ["manager"],
  reports: ["manager"],
  "project-company": ["customer"],
  studio: ["customer", "designer", "admin", "manager"],
};
const message = (result) => {
  const messages = result.messages || [];
  const visible = messages.filter((item) => item.kind !== "log");
  if (visible.length) toast(visible.map((item) => item.text).join(" · "));
};
const empty = (title, description = "") =>
  `<div class="empty"><h3>${e(title)}</h3><p>${e(description)}</p></div>`;
const textarea = (label, name, value = "") =>
  `<label class="label" for="${name}">${label}</label><textarea id="${name}" name="${name}" required maxlength="5000">${e(value)}</textarea>`;
const bar = (value, max) =>
  `<span class="bar"><i style="width:${max ? Math.round((value / max) * 100) : 0}%"></i></span>`;
const statusSelect = (value = "Current") =>
  `<label class="label" for="status">Project status</label><select id="status" name="status">${options(["Current", "Past"], value)}</select>`;
function cards(projects, manage = false) {
  if (!projects.length)
    return empty(
      manage
        ? "You have not created any IDPs yet"
        : "No projects match your search",
    );
  return `<div class="grid">${projects.map((p) => `<article class="card"><div class="photo"><a href="#project/${p.id}" aria-label="View ${e(p.title)}"><img src="${e(p.photos?.[p.cover] || "/assets/meow-logo.svg")}" alt="${e(p.title)}" loading="lazy"></a><span class="badge ${manage ? (p.published ? "status-active" : "status-off") : ""}">${manage ? (p.published ? "Published" : "Unpublished") : e(p.stage)}</span>${!manage ? `<button class="heart ${p.saved ? "saved" : ""}" data-action="save-favourite" data-id="${p.id}" aria-label="Save to favourites">${p.saved ? "♥" : "♡"}</button>` : ""}</div><div class="card-body"><h3><a href="#project/${p.id}">${e(p.title)}</a></h3><span class="studio">${e(p.company?.name || "Your portfolio")}</span><p class="note">${e(p.description)}</p><div class="card-foot"><strong>${e(p.stage)} project</strong><span>${p.views} views · ${p.shortlists} shortlists</span></div>${manage ? `<div class="row-actions" style="margin-top:15px"><button class="btn small" data-action="edit-project" data-id="${p.id}">Edit</button><button class="btn small" data-action="photos" data-id="${p.id}">Photos & cover</button><button class="btn small" data-action="project-categories" data-id="${p.id}">Categories</button><button class="btn small" data-action="publication" data-id="${p.id}" data-published="${p.published}">${p.published ? "Unpublish" : "Publish"}</button></div>` : ""}</div></article>`).join("")}</div>`;
}
function auth(register = false) {
  return `<div class="page auth"><span class="eyebrow">Welcome to MEOW</span><h1>${register ? "Make room for inspiration." : "Welcome home."}</h1><p class="note">${register ? "Create a customer account or apply as an interior designer." : "Log in to your interior design workspace."}</p><form id="${register ? "registerForm" : "loginForm"}" class="panel form">${register ? '<label class="label" for="registerRole">Register as</label><select id="registerRole" name="role"><option value="customer">Customer</option><option value="designer">Interior Designer</option></select>' : ""}${field("Email", "email", "", "email")}${field("Password (10-128 characters)", "password", "", "password")}${register ? field("Confirm password", "confirmPassword", "", "password") + `<section id="businessFields" hidden>${field("Company name", "company", "", "text", false)}${field("Business registration number / UEN", "uen", "", "text", false)}<label class="label" for="proof">Proof of business (PDF, PNG, JPEG or WebP; up to 2MB)</label><input id="proof" name="proof" type="file" accept="application/pdf,image/png,image/jpeg,image/webp"></section><p class="note" id="validationNotice">Enter matching credentials to enable submission.</p>` : ""}<button id="authSubmit" class="btn primary" ${register ? "disabled" : ""}>${register ? "Create account" : "Log in"}</button><div class="error" role="alert"></div></form><p class="note">${register ? 'Already registered? <a href="#login">Log in</a>' : 'New here? <a href="#register">Create an account</a>'}</p>${
    register
      ? ""
      : `<details class="panel"><summary>Demo accounts</summary><p class="note">Password: MEOWDemo!2026</p><div class="row-actions">${Object.keys(
          homes,
        )
          .map(
            (role) =>
              `<button class="btn small" data-action="demo" data-role="${role}">${role}</button>`,
          )
          .join("")}</div></details>`
  }</div>`;
}
async function discover() {
  const initial = await api.request(
    `/projects?q=${encodeURIComponent(keyword)}`,
  );
  const result = searched
    ? await api.request(`/search-results?q=${encodeURIComponent(keyword)}`)
    : initial;
  const projects = result.data.idps;
  const featured = projects.find((p) => p.photos?.length) || projects[0];
  return `<section class="hero"><div><span class="eyebrow">Thoughtful spaces. Happy homes.</span><h1>Good design.<br><em>Great places to live.</em></h1><p>Discover interior design projects, meet the company behind them and keep your favourite ideas together.</p><div class="trust">Verified designers · MEOW interior design platform</div></div><a class="hero-image" href="${featured ? "#project/" + featured.id : "#discover"}"><img src="${e(featured?.photos?.[featured.cover] || "/assets/meow-logo.svg")}" alt="Interior design inspiration"><div class="hero-caption"><strong>A little inspiration for your everyday.</strong>${e(featured?.title || "Find your next space")}</div></a></section><div class="section-head"><div><h2>Find your inspiration</h2><span class="muted">Search titles, descriptions and project details.</span></div></div><form id="searchForm" class="toolbar filter-row"><div class="search"><input aria-label="Search IDPs" name="keyword" value="${e(keyword)}" placeholder="Search published projects..."><button class="btn primary">Search</button></div></form><div class="results-head"><b>${projects.length} published projects</b></div>${cards(projects)}`;
}
async function portfolio() {
  const result = await api.request("/projects?mine=true");
  return `<div class="page"><div class="section-head"><div><span class="eyebrow">Designer workspace</span><h1>My projects</h1><p class="muted">Create your IDP, then manage photos, categories and publication separately.</p></div><button class="btn primary" data-action="create-project" ${user.status !== "active" ? "disabled" : ""}>+ Create IDP</button></div>${user.status !== "active" ? '<div class="notice">Account is pending verification. Publishing restricted. Project creation requires approval.</div>' : ""}${cards(result.data.idps, true)}</div>`;
}
async function project(id) {
  const result = await api.request(`/projects/${id}`);
  const p = result.data.idp;
  const gallery = result.data.gallery;
  const counted = await api.request(`/projects/${id}/views`, {
    method: "POST",
  });
  const views = counted.data.views ?? p.views;
  return `<div class="page"><a class="muted" href="#${homes[user.role]}">Back to projects</a><h1>${e(p.title)}</h1><p class="muted">${e(p.stage)} project · ${p.published ? "Published" : "Unpublished"}</p><img id="galleryMain" class="detail-hero" src="${e(gallery[p.cover]?.url || "/assets/meow-logo.svg")}" alt="${e(p.title)}"><div class="gallery">${gallery.map((photo, i) => `<button class="btn" style="padding:0" data-action="gallery" data-src="${e(photo.url)}" aria-label="View photo ${i + 1}"><img src="${e(photo.url)}" alt="Project photo ${i + 1}"></button>`).join("")}</div><div class="detail-layout"><section><h2>About this project</h2><p>${e(p.description)}</p><h3>Project details</h3><p>${e(p.projectDetails || "")}</p></section><aside class="panel"><span class="eyebrow">Designed by</span><h2>${e(p.company?.name)}</h2>${user.role === "customer" ? `<a class="btn" href="#project-company/${id}">View company profile</a><button class="btn primary" style="margin-top:18px" data-action="save-favourite" data-id="${id}">${p.saved ? "♥ Saved to favourites" : "♡ Save to favourites"}</button>` : `<a class="btn" href="#company">My company profile</a>`}<p class="muted"><span id="viewCount">${views}</span> views · <span id="shortlistCount">${p.shortlists}</span> shortlists</p></aside></div></div>`;
}
function companyView(company) {
  return `<div class="page"><span class="badge">Verified company</span><h1>${e(company.name)}</h1><p class="note">${e(company.description)}</p><div class="panel"><div class="specs"><div>UEN<strong>${e(company.uen)}</strong></div><div>Year established<strong>${e(company.year || "Not provided")}</strong></div></div></div></div>`;
}
async function companyEdit() {
  const result = await api.request("/me/company");
  const c = result.data.company || {};
  return `<div class="page"><h1>Your company profile</h1><p class="muted">Keep your company details accurate and your UEN unique.</p><form id="companyForm" class="panel form" style="max-width:750px">${field("Company name", "name", c.name)}${textarea("Description", "description", c.description)}${field("UEN", "uen", c.uen)}${field("Year established", "year", c.year, "number")}<button class="btn primary">Save profile</button><div class="error" role="alert"></div></form>${c.id ? `<a class="btn" href="#studio/${c.id}">View public profile</a>` : ""}</div>`;
}
async function analytics() {
  const result = await api.request("/me/analytics/totals");
  const data = result.data;
  return `<div class="page"><span class="eyebrow">Portfolio performance</span><h1>Your projects, in numbers.</h1>${(result.messages || []).map((m) => `<p class="notice">${e(m.text)}</p>`).join("")}<div class="stats"><div class="stat"><span>Total views</span><strong>${data.totalViews}</strong></div><div class="stat"><span>Total shortlists</span><strong>${data.totalShortlists}</strong></div></div><button class="btn primary" data-action="per-idp-stats">View by IDP</button><div id="projectStats"></div></div>`;
}
function statsTable(projects) {
  const maxViews = Math.max(0, ...projects.map((p) => p.views));
  const maxShortlists = Math.max(0, ...projects.map((p) => p.shortlists));
  return `<div class="panel table-wrap"><table class="chart-table"><thead><tr><th>Project</th><th>Views</th><th>Shortlists</th></tr></thead><tbody>${projects.map((p) => `<tr><td>${e(p.title)}</td><td><div class="bar-cell">${bar(p.views, maxViews)}<b>${p.views}</b></div></td><td><div class="bar-cell">${bar(p.shortlists, maxShortlists)}<b>${p.shortlists}</b></div></td></tr>`).join("") || '<tr><td colspan="3">No IDPs found</td></tr>'}</tbody></table></div>`;
}
async function accounts(pending = false) {
  const result = await api.request(
    "/users?q=" + encodeURIComponent(accountsQuery),
  );
  const list = pending
    ? result.data.accounts.filter(
        (u) => u.role === "designer" && u.status === "pending",
      )
    : result.data.accounts;
  return `<div class="page"><div class="section-head"><div><span class="eyebrow">User Admin</span><h1>${pending ? "Designer verification" : "User accounts"}</h1></div>${pending ? "" : '<button class="btn primary" data-action="create-account">+ Create account</button>'}</div><form id="accountSearch" class="search"><input name="q" value="${e(accountsQuery)}" aria-label="Search accounts" placeholder="Search name, email or role"><button class="btn">Search</button></form><div class="panel table-wrap"><table><thead><tr><th>Account</th><th>Role</th><th>Status</th><th>Actions</th></tr></thead><tbody>${list.map((u) => `<tr><td><strong>${e(u.name)}</strong><br><span class="muted">${e(u.email)}</span></td><td>${e(u.role)}</td><td><span class="badge status-${e(u.status)}">${e(u.status)}</span></td><td><div class="row-actions">${u.id === user.id ? '<span class="muted">Your account</span>' : `${u.role === "designer" && ["pending", "rejected"].includes(u.status) ? `<button class="btn small" data-action="review" data-id="${u.id}">Review</button>` : `<button class="btn small" data-action="user-status" data-id="${u.id}" data-status="${u.status}">${u.status === "active" ? "Suspend" : "Reactivate"}</button>`}<button class="btn small" data-action="delete-user" data-id="${u.id}">Delete</button>`}</div></td></tr>`).join("") || '<tr><td colspan="4">No accounts found</td></tr>'}</tbody></table></div></div>`;
}
async function categories() {
  const result = await api.request("/categories?flow=availability");
  return `<div class="page"><div class="section-head"><div><span class="eyebrow">Platform Manager</span><h1>Keep projects organised.</h1><p class="muted">Manage category names, descriptions and availability.</p></div><button class="btn primary" data-action="create-category">+ Create Category</button></div><div class="panel">${result.data.categories.map((c) => `<div class="category-line"><div><strong>${e(c.name)}</strong><p class="note">${e(c.description)}</p><span class="badge ${c.active ? "status-active" : "status-off"}">${c.active ? "Available for IDP creation" : "Unavailable"}</span></div><div class="row-actions"><button class="btn small" data-action="edit-category" data-id="${c.id}">Edit</button><button class="btn small" data-action="availability" data-id="${c.id}" data-available="${c.active}">${c.active ? "Make unavailable" : "Make available"}</button></div></div>`).join("")}</div></div>`;
}
async function reports() {
  await api.request("/reports/options");
  return `<div class="page"><span class="eyebrow">Platform Manager</span><h1>Platform activity reports</h1><p class="muted">Select a period and date, then generate totals or view one row per date with a Total row.</p><form id="periodForm" class="panel filter-row"><select name="period" aria-label="Report period">${["daily", "weekly", "monthly"].map((p) => `<option ${p === reportState.period ? "selected" : ""}>${p}</option>`).join("")}</select><input name="date" type="date" value="${reportState.date}" aria-label="Report date"><button class="btn primary">Select period</button><div class="error" role="alert"></div></form><p id="periodLabel" class="notice">Select a period before generating a report.</p><div class="row-actions"><button class="btn" id="generateReport" data-action="generate-report" disabled>Generate totals</button><button class="btn" id="tableReport" data-action="report-table" disabled>View as Table</button></div><div id="reportResult"></div></div>`;
}
// keep: redraw the current page in place (no loading screen, same scroll position)
// highlight: id of the row or card that just changed
async function render({ keep = false, highlight } = {}) {
  const token = ++revision;
  const inPlace = keep && $("#app main");
  const scroll = window.scrollY;
  if (!inPlace)
    $("#app").innerHTML =
      header(user, allowedScreens) +
      '<main class="shell"><div class="loading">Loading your space...</div></main>';
  try {
    const path = route();
    let html;
    if (!user && !["studio"].includes(path)) {
      html = auth(path === "register");
      await api.request(
        path === "register"
          ? "/registration-form?role=customer"
          : "/login-form",
      );
    } else if (["login", "register"].includes(path) && user) {
      navigate(homes[user.role]);
      return;
    } else if (path === "studio") {
      html = companyView(
        (await api.request("/companies/" + identifier())).data.company,
      );
    } else if (!permissions[path]?.includes(user.role)) {
      toast("Access Denied: You do not have permission to view this page");
      navigate(homes[user.role]);
      return;
    } else {
      const views = {
        discover,
        portfolio,
        analytics,
        accounts: () => accounts(false),
        verification: () => accounts(true),
        categories,
        reports,
        company: companyEdit,
        project: () => project(identifier()),
        favourites: async () =>
          `<div class="page"><h1>My favourites</h1>${cards((await api.request("/me/favourites")).data.idps)}</div>`,
        "project-company": async () =>
          companyView(
            (await api.request(`/projects/${identifier()}/company`)).data
              .company,
          ),
      };
      html = await views[path]();
    }
    if (token !== revision) return;
    $("#app").innerHTML =
      header(user, allowedScreens) +
      `<main class="shell">${html}${footer()}</main>`;
    if (inPlace) window.scrollTo(0, scroll);
    if (highlight) flash(highlight);
  } catch (error) {
    if (token !== revision) return;
    if (error.status === 401) {
      user = null;
      navigate("login");
    } else
      $("#app").innerHTML =
        header(user, allowedScreens) +
        `<main class="shell">${empty(error.message)}<button class="btn" data-action="reload">Try again</button></main>`;
  }
}
function flash(id) {
  const target = document
    .querySelector(`[data-id="${CSS.escape(id)}"]`)
    ?.closest("tr, .category-line, .card");
  if (!target) return;
  target.classList.add("updated");
  setTimeout(() => target.classList.remove("updated"), 2500);
}
async function mutate(
  path,
  method,
  body,
  { close = true, refresh = true, highlight } = {},
) {
  const result = await api.request(path, { method, body });
  message(result);
  if (close && $("#modal").open) $("#modal").close();
  if (refresh) await render({ keep: true, highlight });
  return result;
}
async function projectForm(id) {
  let p = {};
  if (id) p = (await api.request("/projects/" + id)).data.idp;
  modal(
    id ? "Edit IDP" : "Create IDP",
    `<form class="form" id="projectForm" data-id="${id || ""}">${field("Title", "title", p.title)}${textarea("Description", "description", p.description)}${textarea("Project details", "projectDetails", p.projectDetails)}${statusSelect(p.stage)}<p class="note">Save the IDP first. Photos, cover image, categories and publication each have their own action.</p><button class="btn primary">Save IDP</button><div class="error" role="alert"></div></form>`,
  );
}
async function photosForm(id) {
  const result = await api.request("/projects/" + id);
  const p = result.data.idp;
  const photos = result.data.gallery;
  modal(
    "IDP photos and cover",
    `<form class="form" id="photosForm" data-id="${id}" data-count="${photos.length}"><div class="gallery">${photos.map((photo, i) => `<div><img src="${e(photo.url)}" alt="Existing photo ${i + 1}"><span class="muted">Photo ${i + 1}</span></div>`).join("")}</div><label class="label" for="photos">Add photos (PNG, JPEG or WebP; 2MB each, 20 total)</label><input id="photos" name="photos" type="file" accept="image/png,image/jpeg,image/webp" multiple ${photos.length ? "" : "required"}><label class="label" for="coverIndex">Choose cover image</label><select id="coverIndex" name="coverIndex">${photos.map((_, i) => `<option value="${i}" ${i === p.cover ? "selected" : ""}>Photo ${i + 1}</option>`).join("")}</select><button class="btn primary">Upload Photos</button><div class="error" role="alert"></div></form>`,
  );
}
async function categoriesForm(id) {
  const [p, c] = await Promise.all([
    api.request("/projects/" + id),
    api.request("/categories/available"),
  ]);
  modal(
    "Assign IDP categories",
    `<form class="form" id="projectCategories" data-id="${id}">${c.data.map((category) => `<label class="check"><input type="checkbox" name="categories" value="${category.id}" ${p.data.idp.categories.includes(category.id) ? "checked" : ""}>${e(category.name)}</label>`).join("") || '<p class="note">No categories are available. A Platform Manager must make a category available first.</p>'}<button class="btn primary">Save Categories</button><div class="error" role="alert"></div></form>`,
  );
}
async function categoryForm(id) {
  let c = {};
  if (id) {
    await api.request("/categories");
    c = (await api.request(`/categories/${id}/edit-form`)).data.category;
  } else await api.request("/categories/form");
  modal(
    id ? "Edit category" : "Create category",
    `<form id="categoryForm" class="form" data-id="${id || ""}">${field("Category name", "name", c.name, "text", false)}${textarea("Description", "description", c.description)}<button class="btn primary">${id ? "Save" : "Submit"}</button><div class="error" role="alert"></div></form>`,
  );
}
async function action(button) {
  const { action, id } = button.dataset;
  switch (action) {
    case "close-modal":
      $("#modal").close();
      break;
    case "reload":
      await render();
      break;
    case "logout":
      await api.logout();
      user = null;
      navigate("login");
      await render();
      break;
    case "demo":
      $("#email").value = button.dataset.role + "@meow.demo";
      $("#password").value = "MEOWDemo!2026";
      break;
    case "gallery":
      $("#galleryMain").src = button.dataset.src;
      break;
    case "create-project":
      await projectForm();
      break;
    case "edit-project":
      await projectForm(id);
      break;
    case "photos":
      await photosForm(id);
      break;
    case "project-categories":
      await categoriesForm(id);
      break;
    case "publication":
      await mutate(
        `/projects/${id}/publication`,
        "PUT",
        {
          action: button.dataset.published === "true" ? "Unpublish" : "Publish",
        },
        { highlight: id },
      );
      break;
    case "save-favourite": {
      const saved = await api.request(`/me/favourites/${id}`, {
        method: "PUT",
      });
      message(saved);
      const shortlisted = await api.request(`/projects/${id}/shortlists`, {
        method: "POST",
      });
      if ($("#shortlistCount") && shortlisted.data.shortlists !== undefined)
        $("#shortlistCount").textContent = shortlisted.data.shortlists;
      if (route() !== "project") await render({ keep: true, highlight: id });
      else button.textContent = "♥ Saved to favourites";
      break;
    }
    case "per-idp-stats": {
      const result = await api.request("/me/analytics/projects");
      message(result);
      $("#projectStats").innerHTML = statsTable(result.data.projects || []);
      break;
    }
    case "create-account":
      await api.request("/users/form");
      modal(
        "Create user account",
        `<form id="createAccountForm" class="form">${field("Email", "email", "", "email")}${field("Password", "password", "", "password")}${field("Confirm password", "confirmPassword", "", "password")}<label class="label" for="role">Role</label><select name="role" id="role">${["customer", "designer", "admin", "manager"].map((r) => `<option>${r}</option>`).join("")}</select><button class="btn primary">Create Account</button><div class="error" role="alert"></div></form>`,
      );
      break;
    case "user-status":
      await mutate(
        `/users/${id}`,
        "PATCH",
        { status: button.dataset.status === "active" ? "suspended" : "active" },
        { highlight: id },
      );
      break;
    case "delete-user":
      await api.request(`/users/${id}/deletion-confirmation`);
      modal(
        "Confirm account deletion",
        `<p class="note">Delete this account permanently?</p><div class="row-actions"><button class="btn primary" data-action="confirm-delete-user" data-id="${id}">Confirm Delete</button><button class="btn" data-action="cancel-delete-user" data-id="${id}">Cancel</button></div>`,
      );
      break;
    case "confirm-delete-user":
      await mutate(`/users/${id}`, "DELETE");
      break;
    case "cancel-delete-user":
      await mutate(`/users/${id}/deletion-cancellations`, "POST", undefined, {
        highlight: id,
      });
      break;
    case "review": {
      const result = await api.request(`/users/${id}/verification`);
      const c = result.data.company;
      modal(
        "Review designer application",
        `<h3>${e(result.data.user.email)}</h3><div class="panel"><h3>${e(c?.name || "Company not provided")}</h3><p class="note">UEN: ${e(c?.uen)}</p>${c?.proofUrl ? `<a class="btn" href="${e(c.proofUrl)}" download>Download business proof</a>` : '<p class="notice">No business proof is attached.</p>'}</div><div class="row-actions"><button class="btn primary" data-action="verify" data-id="${id}" data-decision="approve" ${c?.proofUrl ? "" : "disabled"}>Approve</button><button class="btn" data-action="verify" data-id="${id}" data-decision="reject">Reject</button></div>`,
      );
      break;
    }
    case "verify":
      await mutate(
        `/users/${id}/verification`,
        "PUT",
        { decision: button.dataset.decision },
        { highlight: id },
      );
      break;
    case "create-category":
      await categoryForm();
      break;
    case "edit-category":
      await categoryForm(id);
      break;
    case "availability":
      await mutate(
        `/categories/${id}/availability`,
        "PUT",
        { available: button.dataset.available !== "true" },
        { highlight: id },
      );
      break;
    case "generate-report": {
      const result = await api.request(
        "/reports/activity?" +
          new URLSearchParams({
            period: reportState.period,
            date: reportState.date,
          }),
      );
      message(result);
      const t = result.data.totals;
      $("#reportResult").innerHTML =
        `<div class="stats report-stats"><div class="stat"><span>New users</span><strong>${t.registration}</strong></div><div class="stat"><span>New IDPs</span><strong>${t.project}</strong></div><div class="stat"><span>Views</span><strong>${t.view}</strong></div><div class="stat"><span>Shortlists</span><strong>${t.shortlist}</strong></div></div><div class="panel table-wrap"><h3>Activity totals</h3><table><thead><tr><th>New Users</th><th>New IDPs</th><th>Views</th><th>Shortlists</th></tr></thead><tbody><tr><td>${t.registration}</td><td>${t.project}</td><td>${t.view}</td><td>${t.shortlist}</td></tr></tbody></table></div>`;
      break;
    }
    case "report-table": {
      const result = await api.request(
        "/reports/table?" +
          new URLSearchParams({
            period: reportState.period,
            date: reportState.date,
          }),
      );
      message(result);
      $("#reportResult").innerHTML =
        `<div class="panel table-wrap"><h3>Activity by date</h3><table class="chart-table"><thead><tr><th>Date</th><th>New Users</th><th>New IDPs</th><th>Views</th><th>Shortlists</th></tr></thead><tbody>${(() => {
          const rows = result.data.rows;
          const days = rows.filter((row) => row.date !== "Total");
          const max = (key) => Math.max(0, ...days.map((row) => row[key]));
          const cell = (row, key) =>
            row.date === "Total"
              ? `<td><b>${row[key]}</b></td>`
              : `<td><div class="bar-cell">${bar(row[key], max(key))}<b>${row[key]}</b></div></td>`;
          return rows
            .map(
              (row) =>
                `<tr class="${row.date === "Total" ? "total-row" : ""}"><td><strong>${e(row.date)}</strong></td>${cell(row, "newUsers")}${cell(row, "newIDPs")}${cell(row, "views")}${cell(row, "shortlists")}</tr>`,
            )
            .join("");
        })() || '<tr><td colspan="5">No activity in this period</td></tr>'}</tbody></table></div>`;
      break;
    }
    default:
      break;
  }
}
async function submit(form) {
  const data = new FormData(form),
    body = Object.fromEntries(data),
    id = form.dataset.id;
  switch (form.id) {
    case "loginForm":
      user = await api.login(body.email, body.password);
      allowedScreens = (await api.request("/navigation")).data.screens;
      navigate(homes[user.role]);
      await render();
      break;
    case "registerForm": {
      let result;
      if (body.role === "designer") {
        if (data.get("proof").size > 2 * 1024 * 1024)
          throw new Error("Business proof must be at most 2MB.");
        result = await api.request("/registrations", {
          method: "POST",
          body: data,
        });
      } else
        result = await api.request("/registrations", {
          method: "POST",
          body: { ...body },
        });
      message(result);
      navigate("login");
      break;
    }
    case "searchForm":
      keyword = body.keyword;
      searched = true;
      await render({ keep: true });
      break;
    case "projectForm":
      await mutate(
        id ? `/projects/${id}` : "/projects",
        id ? "PATCH" : "POST",
        body,
        { highlight: id },
      );
      break;
    case "photosForm": {
      const files = data.getAll("photos").filter((file) => file.size);
      if (files.some((file) => file.size > 2 * 1024 * 1024))
        throw new Error("Each photo must be at most 2MB.");
      const upload = new FormData();
      files.forEach((file) => upload.append("photos", file));
      upload.append("coverIndex", body.coverIndex);
      await mutate(`/projects/${id}/photos`, "POST", upload, { highlight: id });
      break;
    }
    case "projectCategories":
      await mutate(
        `/projects/${id}/categories`,
        "PUT",
        { categories: data.getAll("categories") },
        { highlight: id },
      );
      break;
    case "companyForm":
      await mutate("/me/company", "PUT", body, { close: false });
      break;
    case "accountSearch":
      accountsQuery = body.q;
      await render({ keep: true });
      break;
    case "createAccountForm":
      await mutate("/users", "POST", body);
      break;
    case "categoryForm":
      await mutate(
        id ? `/categories/${id}` : "/categories",
        id ? "PATCH" : "POST",
        body,
        { highlight: id },
      );
      break;
    case "periodForm": {
      reportState = { ...reportState, ...body, valid: false };
      $("#generateReport").disabled = true;
      $("#tableReport").disabled = true;
      const result = await api.request("/reports/period", {
        method: "POST",
        body,
      });
      message(result);
      reportState.valid = true;
      $("#periodLabel").textContent =
        `Selected period: ${result.data.from} to ${result.data.to} · Singapore time`;
      $("#generateReport").disabled = false;
      $("#tableReport").disabled = false;
      $("#reportResult").innerHTML = "";
      break;
    }
    default:
      break;
  }
}
document.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button || button.disabled) return;
  event.preventDefault();
  button.disabled = true;
  try {
    await action(button);
  } catch (error) {
    toast(error.message, "error");
  } finally {
    if (button.isConnected) button.disabled = false;
  }
});
document.addEventListener("submit", async (event) => {
  if (!(event.target instanceof HTMLFormElement)) return;
  event.preventDefault();
  const form = event.target,
    button = form.querySelector("button:not([data-action])");
  if (button?.disabled) return;
  if (button) button.disabled = true;
  form.querySelector(".error")?.replaceChildren();
  try {
    await submit(form);
  } catch (error) {
    formError(form, error);
  } finally {
    if (button?.isConnected) button.disabled = false;
  }
});
let validationTimer,
  validationVersion = 0;
document.addEventListener("input", (event) => {
  if (
    !event.target.closest("#registerForm") ||
    $("#registerRole").value !== "customer"
  )
    return;
  clearTimeout(validationTimer);
  $("#authSubmit").disabled = true;
  const version = ++validationVersion;
  validationTimer = setTimeout(async () => {
    try {
      const data = Object.fromEntries(new FormData($("#registerForm")));
      const result = await api.request("/registration-validations", {
        method: "POST",
        body: {
          email: data.email,
          password: data.password,
          confirmPassword: data.confirmPassword,
        },
      });
      if (version !== validationVersion) return;
      $("#authSubmit").disabled =
        !result.data.submitEnabled || data.password.length < 10;
      $("#validationNotice").textContent =
        data.password.length < 10
          ? "Use a password with at least 10 characters."
          : "Credentials match. You can submit.";
    } catch (error) {
      if (version === validationVersion && $("#validationNotice"))
        $("#validationNotice").textContent = error.message;
    }
  }, 350);
});
document.addEventListener("change", async (event) => {
  const el = event.target;
  try {
    if (el.id === "registerRole") {
      clearTimeout(validationTimer);
      validationVersion++;
      const designer = el.value === "designer";
      $("#businessFields").hidden = !designer;
      $("#businessFields")
        .querySelectorAll("input")
        .forEach((input) => (input.required = designer));
      $("#authSubmit").disabled = !designer;
      $("#authSubmit").textContent = designer
        ? "Submit Application"
        : "Create account";
      await api.request("/registration-form?role=" + el.value);
    } else if (el.id === "photos") {
      const count = Number($("#photosForm").dataset.count),
        cover = $("#coverIndex");
      const selected = cover.value;
      cover.innerHTML = Array.from(
        { length: count + el.files.length },
        (_, i) =>
          `<option value="${i}" ${String(i) === selected ? "selected" : ""}>${i < count ? "Existing photo " + (i + 1) : "New photo " + (i - count + 1)}</option>`,
      ).join("");
    } else if (el.closest("#periodForm")) {
      reportState.valid = false;
      $("#generateReport").disabled = true;
      $("#tableReport").disabled = true;
      $("#periodLabel").textContent =
        "Select this period before generating a report.";
    }
  } catch (error) {
    toast(error.message);
  }
});
// photos that cannot load (e.g. no internet for the seed images) fall back to the logo
document.addEventListener(
  "error",
  (event) => {
    const img = event.target;
    if (img.tagName !== "IMG" || img.dataset.fallback) return;
    img.dataset.fallback = "1";
    img.classList.add("missing-photo");
    img.src = "/assets/meow-logo.svg";
  },
  true,
);
window.addEventListener("hashchange", () => {
  revision++;
  clearTimeout(validationTimer);
  validationVersion++;
  if ($("#modal").open) $("#modal").close();
  render();
  window.scrollTo(0, 0);
});
try {
  user = await api.session();
  if (user) {
    allowedScreens = (await api.request("/navigation")).data.screens;
    if (!location.hash) navigate(homes[user.role]);
  }
  await render();
} catch (error) {
  $("#app").innerHTML =
    `<main class="shell">${empty("MEOW could not reach the Java server", error.message)}</main>`;
}
