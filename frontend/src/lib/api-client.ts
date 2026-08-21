import axios from "axios";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    "Content-Type": "application/json",
  },
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

      if (status === 401) {
        localStorage.removeItem("hirelens_token");
        if (window.location.pathname !== "/login") {
          window.location.href = "/login";
        }
      }

      const message =
        data?.error ||
        data?.message ||
        `Request failed with status ${status}`;
      return Promise.reject(new Error(message));
    }

    if (error.code === "ECONNABORTED") {
      return Promise.reject(
        new Error("Request timed out. Please try again.")
      );
    }

    return Promise.reject(
      new Error("Network error. Please check your connection.")
    );
  }
);

export default apiClient;
export { API_BASE_URL };
