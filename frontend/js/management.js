/** Administrative and reporting screens. API endpoints independently enforce access. */
import { api } from "./api.js";
import { escapeHtml as e, field, roles, stat } from "./ui.js";

export async function accountsPage(pending = false, query = "") {
  const search = new URLSearchParams({
    q: query,
    ...(pending ? { status: "pending" } : {}),
  });
  const { data: users } = await api.request(`/users?${search}`);
  return `<div class="page"><div class="section-head"><div><span class="eyebrow">Administration</span><h1>${pending ? "Designer verification" : "User accounts"}</h1><p class="muted">Manage access and review business registrations.</p></div>
    <button class="btn primary" data-action="create-account">＋ Create account</button></div>
    <form id="accountSearch" class="search"><input name="q" value="${e(query)}" placeholder="Search name, email or role" aria-label="Search accounts"><button class="btn">Search</button></form>
    <div class="panel table-wrap"><table><thead><tr><th scope="col">Name / Email</th><th scope="col">Role</th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead><tbody>
      ${
        users
          .map(
            (
              user,
            ) => `<tr><td><strong>${e(user.name)}</strong><br><span class="muted">${e(user.email)}</span></td><td>${roles[user.role]}</td><td><span class="badge">${e(user.status)}</span></td>
        <td><div class="row-actions">${user.role === "designer" && ["pending", "rejected"].includes(user.status) ? `<button class="btn small" data-action="review" data-id="${user.id}">Review application</button>` : `<button class="btn small" data-action="user-status" data-id="${user.id}" data-status="${user.status}">${user.status === "active" ? "Suspend" : "Reactivate"}</button>`}
          <button class="btn small" data-action="user-role" data-id="${user.id}" data-role="${user.role}">Change role</button>
          <button class="btn small" data-action="delete-user" data-id="${user.id}">Delete</button></div></td></tr>`,
          )
          .join("") || '<tr><td colspan="4">No matching accounts.</td></tr>'
      }
    </tbody></table></div></div>`;
}

export function accountForm(id = "", currentRole = "") {
  return `<form id="accountForm" class="form" data-id="${id}">${id ? "" : field("Full name", "name") + field("Email", "email", "", "email") + field("Initial password", "password", "", "password")}
    <label class="label" for="role">Role</label><select id="role" name="role">${Object.entries(
      roles,
    )
      .map(
        ([value, name]) =>
          `<option value="${value}" ${currentRole === value ? "selected" : ""}>${name}</option>`,
      )
      .join("")}</select>
    <p class="note">New designers require company details and business proof before approval. Role changes revoke existing login sessions.</p><button class="btn primary">Save account</button><div class="error" role="alert"></div></form>`;
}

export async function verificationForm(id) {
  const { data } = await api.request(`/users/${id}/verification`);
  const company = data.company;
  return `<p><strong>${e(data.user.name)}</strong> · ${e(data.user.email)}</p><div class="panel"><h3>${e(company?.name || "Company profile not yet provided")}</h3>
    <p class="note">UEN: ${e(company?.uen)} · Established: ${e(company?.year)}</p><p class="note">${e(company?.description)}</p>
    ${company?.proofUrl ? `<a class="btn" href="${e(company.proofUrl)}" download>Download business proof</a>` : '<p class="notice">No business proof uploaded. Approval is blocked until proof is provided.</p>'}</div>
    <button class="btn primary" data-action="verify-user" data-id="${id}" data-decision="approve" ${company?.proofUrl ? "" : "disabled"}>Approve designer</button>
    <button class="btn" data-action="verify-user" data-id="${id}" data-decision="reject">Reject application</button>`;
}

export async function categoriesPage() {
  const { data: categories } = await api.request("/categories");
  return `<div class="page"><div class="section-head"><div><span class="eyebrow">Platform management</span><h1>Keep inspiration organised.</h1><p class="muted">Active categories become available to designers.</p></div><button class="btn primary" data-action="create-category">＋ New category</button></div>
    <div class="panel">${categories
      .map(
        (
          category,
        ) => `<div class="category-line"><div><strong>${e(category.name)}</strong> <span class="badge">${category.active ? "Active" : "Inactive"}</span></div>
      <div class="row-actions"><button class="btn small" data-action="edit-category" data-id="${category.id}" data-name="${e(category.name)}">Edit</button>
      <button class="btn small" data-action="category-status" data-id="${category.id}" data-active="${category.active}">${category.active ? "Deactivate" : "Activate"}</button></div></div>`,
      )
      .join("")}</div></div>`;
}

export function categoryForm(id = "", name = "") {
  return `<form id="categoryForm" class="form" data-id="${id}">${field("Category name", "name", name)}<button class="btn primary">Save category</button><div class="error" role="alert"></div></form>`;
}

export async function analyticsData(from = "", to = "") {
  const { data } = await api.request(
    `/me/analytics?${new URLSearchParams({ from, to })}`,
  );
  return `<div class="stats">${stat("Total views", data.totalViews, "Published project visits")}${stat("Total shortlists", data.totalShortlists, "Successful save events")}${stat("Projects", data.projects.length, "In your portfolio")}</div>
    <div class="panel table-wrap"><table><thead><tr><th scope="col">Project</th><th scope="col">Views</th><th scope="col">Shortlists</th></tr></thead><tbody>
    ${data.projects.map((project) => `<tr><td>${e(project.title)}</td><td>${project.views}</td><td>${project.shortlists}</td></tr>`).join("") || '<tr><td colspan="3">No projects yet.</td></tr>'}</tbody></table></div>`;
}

export async function analyticsPage() {
  return `<div class="page"><span class="eyebrow">Portfolio insights</span><h1>A closer look at your impact.</h1><p class="muted">Activity dates follow Singapore time. Shortlists count successful saves; removing a favourite keeps the historical event.</p>
    <form id="analyticsForm" class="panel filter-row"><label>From <input name="from" type="date"></label><label>To <input name="to" type="date"></label><button class="btn">Apply date range</button></form>
    <div id="analyticsData">${await analyticsData()}</div></div>`;
}

export function todaySingapore() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Singapore",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

export async function reportData(period = "daily", date = todaySingapore()) {
  const { data } = await api.request(
    `/reports/activity?${new URLSearchParams({ period, date })}`,
  );
  const totals = [
    ["Project views", data.totals.view],
    ["Shortlists", data.totals.shortlist],
    ["New accounts", data.totals.registration],
    ["Projects created", data.totals.project],
  ];
  return `<p class="muted">${e(data.period)} · ${data.from} to ${data.to} · Asia/Singapore · Weekly reports start Monday</p>
    <div class="stats">${stat("Project views", data.totals.view, "Recorded visits")}${stat("Shortlists", data.totals.shortlist, "Successful saves")}${stat("New accounts", data.totals.registration, "Registrations")}</div>
    <div class="panel table-wrap"><h3>Activity totals</h3><table><thead><tr><th scope="col">Metric</th><th scope="col">Total</th></tr></thead><tbody>${totals.map(([title, total]) => `<tr><td>${title}</td><td>${total}</td></tr>`).join("")}</tbody></table></div>
    ${[
      ["Top viewed projects", data.topViewed],
      ["Top shortlisted projects", data.topShortlisted],
    ]
      .map(
        ([title, projects]) =>
          `<div class="panel table-wrap"><h3>${title}</h3><table><thead><tr><th scope="col">Project</th><th scope="col">Total</th></tr></thead><tbody>${projects.map((project) => `<tr><td>${e(project.title)}</td><td>${project.total}</td></tr>`).join("") || '<tr><td colspan="2">No activity in this period.</td></tr>'}</tbody></table></div>`,
      )
      .join("")}`;
}

export async function reportsPage() {
  return `<div class="page"><span class="eyebrow">Platform overview</span><h1>Understand what’s happening.</h1><p class="muted">Activity is calculated from shared server records.</p>
    <form id="reportForm" class="panel filter-row"><select name="period" aria-label="Report period"><option value="daily">Daily</option><option value="weekly">Weekly</option><option value="monthly">Monthly</option></select>
      <input name="date" type="date" value="${todaySingapore()}" aria-label="Report date" required><button class="btn primary">Generate report</button></form><div id="reportData">${await reportData()}</div></div>`;
}

export async function moderationPage() {
  const { data: projects } = await api.request("/moderation/projects");
  return `<div class="page"><h1>Project moderation</h1><p class="muted">Take down projects that break platform guidelines.</p><div class="panel table-wrap"><table><thead><tr><th scope="col">Project</th><th scope="col">Studio</th><th scope="col">Status</th><th scope="col">Action</th></tr></thead><tbody>
    ${projects
      .map(
        (
          project,
        ) => `<tr><td>${e(project.title)}</td><td>${e(project.company)}</td><td>${project.takedown_reason ? `Taken down: ${e(project.takedown_reason)}` : project.archived ? "Archived" : project.published ? "Published" : "Draft"}</td>
    <td><button class="btn small" data-action="take-down" data-id="${project.id}" ${project.takedown_reason ? "disabled" : ""}>Take down</button></td></tr>`,
      )
      .join("")}</tbody></table></div></div>`;
}
