import axios from "axios";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

// Must stay ABOVE the backend's OLLAMA_CHAT_TIMEOUT_SECONDS (600s by default),
// or the browser aborts a request the backend is still legitimately working on
// and the user sees a network error for a run that would have succeeded.
const API_TIMEOUT_MS = Number(
  import.meta.env.VITE_API_TIMEOUT_MS || 900000
);

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  // An analysis makes two model round trips (embedding + generation) on top of
  // parsing. A local Ollama model on CPU is far slower than a hosted API, and
  // the first request also pays the model load cost, so budget generously.
  timeout: API_TIMEOUT_MS,
  // Deliberately no default Content-Type here.
  //
  // A blanket "application/json" default is applied to *every* request,
  // including the FormData resume upload, where it is actively wrong: the
  // multipart body then arrives labelled as JSON, the backend cannot find the
  // file part, and the upload fails before the analysis ever starts. axios sets
  // JSON for plain-object bodies itself, and lets the browser generate the
  // multipart boundary when the Content-Type is left unset - which is exactly
  // the behaviour each of those two cases needs.
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("hirelens_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const status = error.response.status;
      const data = error.response.data;
      const hasBody = Boolean(data?.error || data?.message);

      // A rejected session: 401 from the JWT filter, or a bodyless 403 which is
      // what the same condition used to surface as (see SupabaseJwtFilter).
      // Recovering is the only useful response - leaving the user on a page whose
      // every request is rejected, with no way forward, is a dead end.
      if (status === 401 || (status === 403 && !hasBody)) {
        localStorage.removeItem("hirelens_token");
        localStorage.removeItem("hirelens_user");
        if (window.location.pathname !== "/login") {
          window.location.href = "/login";
        }
        return Promise.reject(
          new Error("Your session has expired. Please sign in again.")
        );
      }

      const message =
        data?.error ||
        data?.message ||
        `Request failed with status ${status}`;
      return Promise.reject(new Error(message));
    }

    if (error.code === "ECONNABORTED" || error.code === "ETIMEDOUT") {
      return Promise.reject(
        new Error("Request timed out. Please try again.")
      );
    }

    // A request the caller cancelled on purpose (closing the assistant panel,
    // switching analysis) is not a network failure. It must propagate as-is,
    // otherwise every in-flight turn appears as "could not reach the backend".
    if (axios.isCancel(error) || error.code === "ERR_CANCELED") {
      return Promise.reject(error);
    }

    // A blocked cross-origin request and a genuinely dead backend look identical
    // to axios: the browser refuses to hand the response to JS, so there is no
    // `error.response`. The browser console has the real reason (a CORS error
    // names the blocked origin), so point at it instead of saying "check your
    // connection" and sending people to debug Wi-Fi.
    if (error.code === "ERR_NETWORK" || !error.response) {
      return Promise.reject(
        new Error(
          `Could not reach the backend at ${API_BASE_URL}. Either it is not ` +
            `running, or it rejected this page's origin as a CORS origin — ` +
            `check the browser console for the exact reason.`
        )
      );
    }

    return Promise.reject(
      new Error("Network error. Please check your connection.")
    );
  }
);

export default apiClient;
export { API_BASE_URL };
