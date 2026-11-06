/** Route views consume API resources; no database or permission logic lives here. */
import { api } from "./api.js";
import {
  cards,
  escapeHtml as e,
  field,
  money,
  options,
  roles,
  select,
  stat,
} from "./ui.js";

export const filters = {
  q: "",
  category: "",
  style: "",
  maxBudget: "",
  property: "",
  location: "",
  sort: "newest",
  page: 1,
};
const photo =
  "https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=1200&q=85";

export function authPage(register = false) {
  return `<div class="page auth"><span class="eyebrow">Welcome to MEOW</span>
    <h1>${register ? "Make room for inspiration." : "Welcome home."}</h1>
    <p class="note">${register ? "Find your next space or showcase your design work." : "Log in to explore spaces, save inspiration and manage your work."}</p>
    <form class="panel form" id="${register ? "registerForm" : "loginForm"}">
      ${register ? select("Register as", "role", ["customer", "designer"]) + field("Full name", "name") : ""}
      ${field("Email address", "email", "", "email")}${field("Password (10–128 characters)", "password", "", "password")}
      ${
        register
          ? field("Confirm password", "confirmPassword", "", "password") +
            `<section id="businessFields" hidden>
        ${field("Company name", "company", "", "text", false)}${field("Business registration / UEN", "uen", "", "text", false)}
        ${field("Year established", "year", "", "number", false)}
        <label class="label" for="proof">Proof of business (PDF or image, up to 2MB)</label>
        <input id="proof" name="proof" type="file" accept="application/pdf,image/png,image/jpeg,image/webp">
        <p class="note">Your designer account will be pending until an admin reviews your business proof.</p></section>`
          : ""
      }
      <button class="btn primary" type="submit" style="width:100%">${register ? "Create account" : "Log in"}</button>
      <div class="error" role="alert"></div>
    </form>
    <p class="note">${register ? 'Already registered? <a href="#login">Log in →</a>' : 'New to MEOW? <a href="#register">Create an account →</a>'}</p>
    ${
      register
        ? ""
        : `<details class="panel"><summary>Local demo accounts</summary><p class="note">Available after starting with --demo. These buttons fill sample credentials; authentication still uses the server.</p>
      <div class="demo-buttons">${Object.entries(roles)
        .map(
          ([role, label]) =>
            `<button class="btn small" data-action="fill-demo" data-role="${role}">${label}</button>`,
        )
        .join("")}</div>
      <p class="note">Password: MEOWDemo!2026</p></details>`
    }
  </div>`;
}

export async function discoverPage() {
  const query = new URLSearchParams(
    Object.entries(filters).filter(([, value]) => value !== ""),
  );
  const [result, categories] = await Promise.all([
    api.request(`/projects?${query}`),
    api.request("/categories"),
  ]);
  const featured = result.data[0];
  return `<section class="hero"><div><div class="eyebrow">Your next chapter starts at home</div>
    <h1>Good design.<br><em>Great places to live.</em></h1>
    <p>Find spaces that feel like you. Explore inspiring projects from Singapore’s interior design studios.</p>
    <div class="trust"><b>✦</b> Verified designers · Real spaces · Endless possibilities</div></div>
    <a class="hero-image" href="${featured ? `#project/${featured.id}` : "#studios"}"><img src="${e(featured?.photos[featured.cover] || photo)}" alt="A thoughtfully designed living space">
      <div class="hero-caption"><strong>A little inspiration for your everyday.</strong>${e(featured?.title || "Discover design studios")} ↗</div></a></section>
    <div class="section-head"><div><h2>Find your inspiration</h2><span class="muted">Spaces to love. Ideas to make your own.</span></div><span class="eyebrow">Curated for living</span></div>
    <section class="toolbar"><form id="searchForm" class="filter-row"><div class="search"><span>⌕</span>
      <input name="q" aria-label="Search projects" value="${e(filters.q)}" placeholder="Search projects, styles, or designers…"><button class="btn primary small">Search</button></div>
      <select name="style" aria-label="Design style"><option value="">All styles</option>${options(["Japandi", "Minimalist", "Contemporary", "Scandinavian"], filters.style)}</select>
      <select name="maxBudget" aria-label="Maximum budget"><option value="">Any budget</option>${[60000, 80000, 100000, 150000].map((n) => `<option value="${n}" ${String(n) === filters.maxBudget ? "selected" : ""}>Up to ${money(n)}</option>`).join("")}</select>
      <select name="property" aria-label="Property type"><option value="">All properties</option>${options(["HDB", "Condominium", "Landed", "Commercial"], filters.property)}</select>
    </form><div class="chips"><button class="chip ${!filters.category ? "active" : ""}" data-action="filter-category" data-id="">All projects</button>
      ${categories.data
        .filter((category) => category.active)
        .map(
          (category) =>
            `<button class="chip ${category.id === filters.category ? "active" : ""}" data-action="filter-category" data-id="${category.id}">${e(category.name)}</button>`,
        )
        .join("")}
      <input id="locationFilter" value="${e(filters.location)}" placeholder="Location" aria-label="Filter location" style="width:140px">
      <button class="btn text" data-action="apply-location">Apply</button><button class="btn text" data-action="reset-filters">Reset ↺</button></div></section>
    <div class="results-head"><span><b>${result.meta.total} projects</b> to inspire your next space</span>
      <label>Sort by <select id="sortFilter" class="sort" aria-label="Sort projects"><option value="newest">Newest first</option>
        <option value="views" ${filters.sort === "views" ? "selected" : ""}>Most viewed</option><option value="saves" ${filters.sort === "saves" ? "selected" : ""}>Most saved</option></select></label></div>
    ${cards(result.data)}
    <div class="pager"><button class="btn small" data-action="page" data-page="${filters.page - 1}" ${filters.page <= 1 ? "disabled" : ""}>Previous</button>
      <span class="muted">Page ${filters.page} of ${Math.max(1, Math.ceil(result.meta.total / result.meta.limit))}</span>
      <button class="btn small" data-action="page" data-page="${filters.page + 1}" ${filters.page * result.meta.limit >= result.meta.total ? "disabled" : ""}>Next</button></div>`;
}

export async function projectPage(id, user) {
  let { data: project } = await api.request(`/projects/${id}`);
  // GET is read-only; opening the detail view records a separate POST event.
  if (project.published && !project.archived && !project.takenDown) {
    try {
      const result = await api.request(`/projects/${id}/views`, {
        method: "POST",
      });
      project.views = result.data.views;
    } catch (error) {
      if (error.status !== 409) throw error;
    }
  }
  return `<div class="page"><a class="muted" href="#discover">← Back to projects</a><h1>${e(project.title)}</h1>
    <p class="muted">${e(project.location)} · ${e(project.property)} · ${e(project.style)}</p>
    <img class="detail-hero" id="galleryMain" src="${e(project.photos[project.cover])}" alt="${e(project.title)}">
    <div class="gallery">${project.photos.map((url, index) => `<button class="btn" style="padding:0" data-action="gallery" data-src="${e(url)}" aria-label="View photo ${index + 1}"><img src="${e(url)}" alt="Project photo ${index + 1}"></button>`).join("")}</div>
    <div class="detail-layout"><section><h2>About this space</h2><p>${e(project.description)}</p>
      <div class="specs">${[
        ["Property", project.property],
        ["Floor area", `${project.size} m²`],
        ["Budget", money(project.budget)],
        ["Style", project.style],
        ["Duration", project.duration],
        ["Status", project.stage],
      ]
        .map(([key, value]) => `<div>${key}<strong>${e(value)}</strong></div>`)
        .join("")}</div></section>
      <aside class="panel"><span class="eyebrow">Designed by</span><h2>${e(project.company?.name)}</h2><p class="muted">Interior design studio</p>
        <a class="btn" href="#studio/${project.company?.id}">View company profile ↗</a>
        ${user.role === "customer" ? `<button class="btn primary" style="display:block;margin-top:20px" data-action="save-favourite" data-id="${id}">${project.saved ? "♥ Saved to favourites" : "♡ Save to favourites"}</button>` : ""}
        <p class="muted">${project.views} views · ${project.shortlists} shortlists</p></aside></div></div>`;
}

export async function favouritesPage() {
  const result = await api.request("/projects?favourites=true&limit=100");
  return `<div class="page"><span class="eyebrow">Your collection</span><h1>Spaces you keep coming back to.</h1><p class="muted">Your saved inspiration, all in one place.</p>${cards(result.data, { emptyTitle: "Your inspiration collection starts here." })}${result.meta.total > 100 ? '<p class="note">Showing your first 100 favourites.</p>' : ""}</div>`;
}

export async function studiosPage() {
  const result = await api.request("/companies");
  return `<div class="page"><h1>People behind the spaces.</h1><p class="muted">Meet our verified interior design studios.</p>
    ${result.data
      .map(
        (
          company,
        ) => `<div class="panel">${company.logoUrl ? `<img class="company-logo" src="${e(company.logoUrl)}" alt="${e(company.name)} logo">` : ""}<span class="badge">Verified studio ✦</span>
      <h2>${e(company.name)}</h2><p class="note">${e(company.description)}</p><a class="btn" href="#studio/${company.id}">Explore studio ↗</a></div>`,
      )
      .join("")}</div>`;
}

export async function studioPage(id, user) {
  const [companyResult, projects] = await Promise.all([
    api.request(`/companies/${id}`),
    api.request(`/projects?company=${id}&limit=100`),
  ]);
  const company = companyResult.data;
  return `<div class="page">${company.bannerUrl ? `<img class="company-banner" src="${e(company.bannerUrl)}" alt="${e(company.name)} banner">` : ""}
    ${company.logoUrl ? `<img class="company-logo" src="${e(company.logoUrl)}" alt="Company logo">` : ""}<h1>${e(company.name)}</h1><p class="note">${e(company.description)}</p>
    <div class="panel"><div class="specs">${[
      ["UEN", company.uen],
      ["Established", company.year],
      ["Styles", company.styles],
      ["Contact", company.contact],
      ["Hours", company.hours],
      ["Areas", company.areas],
      ["Certifications & awards", company.awards],
    ]
      .map(
        ([key, value]) =>
          `<div>${key}<strong>${e(value || "Not provided")}</strong></div>`,
      )
      .join(
        "",
      )}</div></div><h2>Selected work</h2>${cards(projects.data, { allowSave: user.role === "customer" })}</div>`;
}

export async function portfolioPage(user) {
  const result = await api.request("/projects?mine=true&limit=100");
  const projects = result.data;
  return `<div class="page"><div class="section-head"><div><span class="eyebrow">Designer workspace</span><h1>My projects</h1><p class="muted">Your work, thoughtfully presented.</p></div>
    <button class="btn primary" data-action="create-project" ${user.status !== "active" ? "disabled" : ""}>＋ Create project</button></div>
    ${user.status === "pending" ? '<div class="notice">Your account is pending verification. Company details can be edited; project creation and publication require approval.</div>' : ""}
    <div class="stats">${stat("Portfolio projects", result.meta.total, "Current and past work")}${stat("Published on this page", projects.filter((project) => project.published && !project.archived && !project.takenDown).length, "Visible to customers")}${stat("Hidden on this page", projects.filter((project) => !project.published || project.archived || project.takenDown).length, "Drafts, archived or taken down")}</div>
    ${cards(projects, { manage: true, emptyTitle: "Your next project starts here." })}${result.meta.total > 100 ? '<p class="note">Showing first 100 projects; additional pages are available through the API.</p>' : ""}</div>`;
}

export async function projectForm(id) {
  const [categoryResult, projectResult] = await Promise.all([
    api.request("/categories"),
    id ? api.request(`/projects/${id}`) : Promise.resolve({ data: {} }),
  ]);
  const project = projectResult.data;
  return `<form id="projectForm" class="form" data-id="${id || ""}">
    ${field("Project title", "title", project.title)}<label class="label" for="description">Description</label>
    <textarea id="description" name="description" required maxlength="5000">${e(project.description)}</textarea>
    <div class="pair"><div>${select("Property type", "property", ["HDB", "Condominium", "Landed", "Commercial"], project.property)}</div><div>${select("Project status", "stage", ["Current", "Past"], project.stage)}</div></div>
    <div class="pair"><div>${field("Location", "location", project.location)}</div><div>${field("Floor area (m²)", "size", project.size, "number")}</div></div>
    <div class="pair"><div>${field("Budget (S$)", "budget", project.budget, "number")}</div><div>${field("Duration", "duration", project.duration)}</div></div>
    ${select("Style", "style", ["Japandi", "Minimalist", "Contemporary", "Scandinavian"], project.style)}
    <label class="label">Categories — select at least one</label>${categoryResult.data
      .filter((category) => category.active)
      .map(
        (category) =>
          `<label class="check"><input name="categories" type="checkbox" value="${category.id}" ${project.categories?.includes(category.id) ? "checked" : ""}>${e(category.name)}</label>`,
      )
      .join("")}
    <label class="label" for="photos">${id ? "Add more photos" : "Upload photos"} (up to 20, 2MB each)</label><input id="photos" name="photos" type="file" accept="image/png,image/jpeg,image/webp" multiple ${id ? "" : "required"}>
    ${id ? `<label class="label" for="coverPhotoId">Cover photo</label><select name="coverPhotoId" id="coverPhotoId">${project.photoIds.map((photoId, index) => `<option value="${photoId}" ${index === project.cover ? "selected" : ""}>Photo ${index + 1}</option>`).join("")}</select>` : '<p class="note">First photo becomes the cover. Edit after saving to choose another.</p>'}
    <button class="btn primary">${id ? "Save changes" : "Save as draft"}</button><div class="error" role="alert"></div></form>`;
}

export async function companyPage() {
  const { data: result } = await api.request("/me/company");
  const company = result || {};
  return `<div class="page"><h1>Your studio, your story.</h1><p class="muted">Keep your company details accurate and ready to discover.</p>
    <form id="companyForm" class="panel form" style="max-width:800px">${field("Company name", "name", company.name)}
    <label class="label" for="description">Description</label><textarea id="description" name="description" maxlength="5000">${e(company.description)}</textarea>
    <div class="pair"><div>${field("UEN", "uen", company.uen)}</div><div>${field("Year established", "year", company.year, "number")}</div></div>
    ${field("Styles and specialisations", "styles", company.styles, "text", false)}${field("Contact details", "contact", company.contact, "text", false)}
    ${field("Business hours", "hours", company.hours, "text", false)}${field("Service areas", "areas", company.areas, "text", false)}${field("Certifications, awards and memberships", "awards", company.awards, "text", false)}
    <label class="label" for="logo">Company logo (optional)</label><input id="logo" name="logo" type="file" accept="image/png,image/jpeg,image/webp">
    <label class="label" for="banner">Company banner (optional)</label><input id="banner" name="banner" type="file" accept="image/png,image/jpeg,image/webp">
    <label class="label" for="businessProof">New business proof (optional, replacing proof requires approval again)</label><input id="businessProof" name="businessProof" type="file" accept="application/pdf,image/png,image/jpeg,image/webp">
    <button class="btn primary">Save company profile</button><div class="error" role="alert"></div></form></div>`;
}

export function profilePage(user) {
  return `<div class="page"><h1>Your profile</h1><p class="muted">${roles[user.role]} · ${e(user.status)}</p>
    <form id="profileForm" class="panel form" style="max-width:650px">${field("Full name", "name", user.name)}${field("Contact number", "contact", user.contact, "tel", false)}${field("Design preferences", "preferences", user.preferences, "text", false)}<button class="btn primary">Save profile</button></form>
    <form id="passwordForm" class="panel form" style="max-width:650px"><h2>Change password</h2>${field("Current password", "currentPassword", "", "password")}${field("New password", "password", "", "password")}${field("Confirm new password", "confirmPassword", "", "password")}<button class="btn primary">Change password and log out</button><div class="error" role="alert"></div></form></div>`;
}
