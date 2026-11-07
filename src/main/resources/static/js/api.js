/** Same-origin REST client. Authentication uses HttpOnly session cookies. */
export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

class ApiClient {
  #csrfToken = null;

  async request(path, { method = "GET", body } = {}) {
    const headers = { Accept: "application/json" };
    if (this.#csrfToken && method !== "GET") {
      headers["X-CSRF-Token"] = this.#csrfToken;
    }
    const isForm = body instanceof FormData;
    if (body !== undefined && !isForm)
      headers["Content-Type"] = "application/json";
    const response = await fetch(`/api/v1${path}`, {
      method,
      headers,
      credentials: "same-origin",
      body:
        body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
    if (response.status === 204) return null;
    const result = await response.json();
    if (!response.ok) {
      throw new ApiError(
        result.error?.message || result.messages?.find(item => item.kind === "error")?.text || result.messages?.[0]?.text || "Request failed.",
        response.status,
        result.error?.code,
      );
    }
    return result;
  }

  async session() {
    const result = await this.request("/auth/session");
    this.#csrfToken = result.data.csrfToken;
    return result.data.user;
  }

  async login(email, password) {
    await this.request("/auth/session", {
      method: "POST",
      body: { email, password },
    });
    return this.session();
  }

  async logout() {
    await this.request("/auth/session", { method: "DELETE" });
    this.#csrfToken = null;
  }

  async upload(file) {
    if (file.size > 2 * 1024 * 1024)
      throw new Error("Each upload must be at most 2MB.");
    const body = new FormData();
    body.append("file", file);
    const result = await this.request("/uploads", { method: "POST", body });
    return result.data.id;
  }
}

export const api = new ApiClient();
