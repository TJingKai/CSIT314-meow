/** Application controller: navigation, event delegation and API-backed mutations. */
import { api } from "./api.js";
import {
  $,
  escapeHtml,
  field,
  footer,
  formError,
  header,
  identifier,
  modal,
  navigate,
  route,
  toast,
} from "./ui.js";
import * as pages from "./pages.js";
import * as management from "./management.js";

let currentUser = null;
let renderVersion = 0;
let accountQuery = "";
const home = {
  customer: "discover",
  designer: "portfolio",
  admin: "accounts",
  manager: "reports",
};
const permissions = {
  discover: ["customer"],
  project: ["customer", "designer", "manager"],
  studios: ["customer"],
  studio: ["customer", "designer"],
  favourites: ["customer"],
  portfolio: ["designer"],
  analytics: ["designer"],
  company: ["designer"],
  accounts: ["admin"],
  verification: ["admin"],
  reports: ["manager"],
  categories: ["manager"],
  moderation: ["manager"],
  profile: Object.keys(home),
};

async function render() {
  const version = ++renderVersion;
  const path = route();
  $("#app").innerHTML =
    `${header(currentUser)}<main class="shell"><div class="loading">Loading your space…</div></main>`;
  try {
    let content;
    if (!currentUser) {
      content = pages.authPage(path === "register");
    } else if (path === "login" || path === "register") {
      navigate(home[currentUser.role]);
      return;
    } else if (!permissions[path]?.includes(currentUser.role)) {
      content = `<div class="page"><div class="empty"><h3>This page is unavailable for your role.</h3><a class="btn" href="#${home[currentUser.role]}">Go to your workspace</a></div></div>`;
    } else {
      const routes = {
        discover: pages.discoverPage,
        project: () => pages.projectPage(identifier(), currentUser),
        favourites: pages.favouritesPage,
        studios: pages.studiosPage,
        studio: () => pages.studioPage(identifier(), currentUser),
        portfolio: () => pages.portfolioPage(currentUser),
        company: pages.companyPage,
        profile: () => pages.profilePage(currentUser),
        analytics: management.analyticsPage,
        accounts: () => management.accountsPage(false, accountQuery),
        verification: () => management.accountsPage(true, accountQuery),
        categories: management.categoriesPage,
        reports: management.reportsPage,
        moderation: management.moderationPage,
      };
      content = await routes[path]();
    }
    if (version !== renderVersion) return;
    $("#app").innerHTML =
      `${header(currentUser)}<main class="shell">${content}${footer()}</main>`;
  } catch (error) {
    if (version !== renderVersion) return;
    if (error.status === 401) {
      currentUser = null;
      toast("Your session has ended. Please log in again.");
      navigate("login");
    } else {
      $("#app").innerHTML =
        `${header(currentUser)}<main class="shell"><div class="page empty"><h3>We could not load this page.</h3><p>${escapeHtml(error.message)}</p><button class="btn" data-action="reload">Try again</button></div></main>`;
    }
  }
}

async function refreshSession() {
  currentUser = await api.session();
}

async function mutate(path, method, body, message, { close = false } = {}) {
  const result = await api.request(path, { method, body });
  if (close) $("#modal").close();
  if (message) toast(message);
  // Avoid recording another project view just because the save button changed.
  if (route() !== "project") await render();
  return result;
}

function confirmAction(title, message, action, id) {
  modal(
    title,
    `<p class="note">${message}</p><button class="btn primary" data-action="${action}" data-id="${id}">Confirm</button>`,
  );
}

async function clickAction(button) {
  const { action, id } = button.dataset;
  switch (action) {
    case "reload":
      await render();
      break;
    case "close-modal":
      $("#modal").close();
      break;
    case "logout":
      await api.logout();
      currentUser = null;
      navigate("login");
      await render();
      break;
    case "fill-demo": {
      const addresses = {
        customer: "customer",
        designer: "designer",
        admin: "admin",
        manager: "manager",
      };
      $("#loginForm [name=email]").value =
        `${addresses[button.dataset.role]}@meow.demo`;
      $("#loginForm [name=password]").value = "MEOWDemo!2026";
      break;
    }
    case "filter-category":
      pages.filters.category = id;
      pages.filters.page = 1;
      await render();
      break;
    case "apply-location":
      pages.filters.location = $("#locationFilter").value.trim();
      pages.filters.page = 1;
      await render();
      break;
    case "reset-filters":
      Object.keys(pages.filters).forEach((key) => {
        pages.filters[key] =
          key === "page" ? 1 : key === "sort" ? "newest" : "";
      });
      await render();
      break;
    case "page":
      pages.filters.page = Number(button.dataset.page);
      await render();
      break;
    case "gallery":
      $("#galleryMain").src = button.dataset.src;
      break;
    case "save-favourite": {
      const result = await api.request(`/me/favourites/${id}`, {
        method: "PUT",
      });
      toast(result.data.message);
      button.textContent = "♥ Saved to favourites";
      break;
    }
    case "toggle-favourite":
      await mutate(
        `/me/favourites/${id}`,
        button.dataset.saved === "true" ? "DELETE" : "PUT",
        undefined,
        button.dataset.saved === "true"
          ? "Removed from favourites"
          : "Saved to favourites",
      );
      break;
    case "create-project":
    case "edit-project":
      modal(
        id ? "Edit project" : "Create project",
        await pages.projectForm(id),
      );
      break;
    case "publish-project":
      await mutate(
        `/projects/${id}/publication`,
        "PUT",
        { published: button.dataset.published !== "true" },
        "Publication updated",
      );
      break;
    case "archive-project":
      await mutate(
        `/projects/${id}/archive`,
        "PUT",
        { archived: button.dataset.archived !== "true" },
        "Archive status updated",
      );
      break;
    case "delete-project":
      confirmAction(
        "Delete project?",
        "The project and its favourites will be removed. Activity totals remain in historical reports.",
        "confirm-delete-project",
        id,
      );
      break;
    case "confirm-delete-project":
      await mutate(`/projects/${id}`, "DELETE", undefined, "Project deleted", {
        close: true,
      });
      break;
    case "create-account":
      modal("Create user account", management.accountForm());
      break;
    case "user-role":
      modal("Change role", management.accountForm(id, button.dataset.role));
      break;
    case "user-status":
      await mutate(
        `/users/${id}`,
        "PATCH",
        { status: button.dataset.status === "active" ? "suspended" : "active" },
        "Account status updated",
      );
      break;
    case "delete-user":
      confirmAction(
        "Delete account?",
        "The account, its company and projects will be removed. This cannot be undone.",
        "confirm-delete-user",
        id,
      );
      break;
    case "confirm-delete-user":
      await mutate(`/users/${id}`, "DELETE", undefined, "Account deleted", {
        close: true,
      });
      break;
    case "review":
      modal(
        "Review designer application",
        await management.verificationForm(id),
      );
      break;
    case "verify-user":
      await mutate(
        `/users/${id}/verification`,
        "PUT",
        { decision: button.dataset.decision },
        "Verification updated",
        { close: true },
      );
      break;
    case "create-category":
      modal("Create category", management.categoryForm());
      break;
    case "edit-category":
      modal("Edit category", management.categoryForm(id, button.dataset.name));
      break;
    case "category-status":
      await mutate(
        `/categories/${id}`,
        "PATCH",
        { active: button.dataset.active !== "true" },
        "Category updated",
      );
      break;
    case "take-down":
      modal(
        "Take down project",
        `<form id="moderationForm" class="form" data-id="${id}">${field("Reason for removal", "reason")}<button class="btn primary">Confirm take down</button></form>`,
      );
      break;
    default:
      break;
  }
}

async function submitForm(form) {
  const data = new FormData(form);
  const value = Object.fromEntries(data);
  const id = form.dataset.id;
  switch (form.id) {
    case "loginForm":
      currentUser = await api.login(value.email, value.password);
      navigate(home[currentUser.role]);
      await render();
      toast("Logged in successfully");
      break;
    case "registerForm":
      if (value.password !== value.confirmPassword)
        throw new Error("Passwords do not match.");
      await api.request("/registrations", { method: "POST", body: data });
      navigate("login");
      toast(
        value.role === "designer"
          ? "Application submitted. Please log in; your account is pending verification."
          : "Account created. You can now log in.",
      );
      break;
    case "searchForm":
      Object.assign(pages.filters, value, { page: 1 });
      await render();
      break;
    case "projectForm": {
      const categories = data.getAll("categories");
      if (!categories.length) throw new Error("Select at least one category.");
      const photoFiles = data.getAll("photos").filter((file) => file.size);
      if (!id && !photoFiles.length)
        throw new Error("Upload at least one photo.");
      if (photoFiles.length > 20) throw new Error("Use at most 20 photos.");
      const uploadIds = [];
      for (const file of photoFiles) uploadIds.push(await api.upload(file));
      const body = {
        title: value.title,
        description: value.description,
        property: value.property,
        location: value.location,
        style: value.style,
        stage: value.stage,
        duration: value.duration,
        budget: Number(value.budget),
        size: Number(value.size),
        categories,
        photoUploadIds: uploadIds,
      };
      if (value.coverPhotoId) body.coverPhotoId = value.coverPhotoId;
      await mutate(
        id ? `/projects/${id}` : "/projects",
        id ? "PATCH" : "POST",
        body,
        "Project saved",
        { close: true },
      );
      break;
    }
    case "companyForm": {
      const body = Object.fromEntries(
        [...data].filter(([, item]) => typeof item === "string"),
      );
      for (const name of ["logo", "banner"]) {
        const file = data.get(name);
        if (file?.size) body[`${name}_id`] = await api.upload(file);
      }
      await api.request("/me/company", { method: "PUT", body });
      const proof = data.get("businessProof");
      if (proof?.size) {
        const proofBody = new FormData();
        proofBody.append("file", proof);
        await api.request("/me/company/proof", {
          method: "POST",
          body: proofBody,
        });
        await refreshSession();
      }
      toast(
        proof?.size
          ? "Company saved. New proof is pending verification."
          : "Company profile saved",
      );
      await render();
      break;
    }
    case "profileForm":
      await mutate("/me", "PATCH", value, "Profile updated");
      await refreshSession();
      await render();
      break;
    case "passwordForm":
      await api.request("/me/password", { method: "PUT", body: value });
      currentUser = null;
      navigate("login");
      await render();
      toast("Password changed. Log in again.");
      break;
    case "accountSearch":
      accountQuery = value.q;
      await render();
      break;
    case "accountForm":
      await mutate(
        id ? `/users/${id}` : "/users",
        id ? "PATCH" : "POST",
        value,
        "Account saved",
        { close: true },
      );
      break;
    case "categoryForm":
      await mutate(
        id ? `/categories/${id}` : "/categories",
        id ? "PATCH" : "POST",
        { name: value.name },
        "Category saved",
        { close: true },
      );
      break;
    case "analyticsForm":
      $("#analyticsData").innerHTML = await management.analyticsData(
        value.from,
        value.to,
      );
      break;
    case "reportForm":
      $("#reportData").innerHTML = await management.reportData(
        value.period,
        value.date,
      );
      break;
    case "moderationForm":
      await mutate(
        `/projects/${id}/moderation`,
        "PUT",
        value,
        "Project taken down",
        { close: true },
      );
      break;
    default:
      break;
  }
}

// One delegated listener per event type works across route and modal replacements.
document.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button || button.disabled) return;
  event.preventDefault();
  button.disabled = true;
  try {
    await clickAction(button);
  } catch (error) {
    toast(error.message);
  } finally {
    if (button.isConnected) button.disabled = false;
  }
});

document.addEventListener("submit", async (event) => {
  const form = event.target;
  if (!(form instanceof HTMLFormElement)) return;
  event.preventDefault();
  const button = form.querySelector("button[type=submit], button:not([type])");
  if (button?.disabled) return;
  if (button) button.disabled = true;
  form.querySelector(".error")?.replaceChildren();
  try {
    await submitForm(form);
  } catch (error) {
    formError(form, error);
  } finally {
    if (button?.isConnected) button.disabled = false;
  }
});

document.addEventListener("change", async (event) => {
  const element = event.target;
  try {
    if (element.id === "role" && $("#businessFields")) {
      const business = $("#businessFields");
      business.hidden = element.value !== "designer";
      business.querySelectorAll("input").forEach((input) => {
        input.required = !business.hidden;
      });
    } else if (element.id === "sortFilter") {
      pages.filters.sort = element.value;
      pages.filters.page = 1;
      await render();
    } else if (element.closest("#searchForm") && element.tagName === "SELECT") {
      Object.assign(
        pages.filters,
        Object.fromEntries(new FormData($("#searchForm"))),
        { page: 1 },
      );
      await render();
    }
  } catch (error) {
    toast(error.message);
  }
});

window.addEventListener("hashchange", () => {
  if ($("#modal").open) $("#modal").close();
  render();
  window.scrollTo(0, 0);
});

try {
  await refreshSession();
  if (currentUser && !location.hash) navigate(home[currentUser.role]);
  await render();
} catch (error) {
  $("#app").innerHTML =
    `<main class="shell"><div class="empty"><h1>MEOW could not reach the server.</h1><p>${escapeHtml(error.message)}</p><p>Start the application and open http://127.0.0.1:8000.</p></div></main>`;
}
