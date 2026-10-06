import axios, { type AxiosRequestConfig } from "axios";
import { ElMessage } from "element-plus";

export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || "/lynx-ad",
  timeout: 20000,
});

http.interceptors.request.use((config) => {
  const url = config.url || "";
  const publicAuth = /\/tenant\/auth\/(sms|password)\//.test(url);
  const token = localStorage.getItem("lynx.token");
  if (token && !publicAuth) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  const idem = (config.headers["Idempotency-Key"] || config.headers.idempotencyKey) as string | undefined;
  if (idem) {
    config.headers.idempotencyKey = idem;
  }
  return config;
});

http.interceptors.response.use(
  (res) => {
    const body = res.data as ApiEnvelope<unknown>;
    if (body && typeof body.code === "number" && body.code !== 0) {
      if (body.code === 401) {
        localStorage.removeItem("lynx.token");
        if (!location.pathname.startsWith("/login")) {
          location.href = "/login";
        }
      }
      if (res.config.headers['X-Quiet-Request'] !== 'true') ElMessage.error(body.message || "请求失败");
      return Promise.reject(body);
    }
    return res;
  },
  (err) => {
    const status = err?.response?.status;
    if (err?.config?.headers?.['X-Quiet-Request'] === 'true') return Promise.reject(err);
    if (!err?.response) {
      ElMessage.error("无法连接服务，请确认后端已启动");
    } else if (status >= 500) {
      ElMessage.error("服务暂时不可用，请稍后重试");
    } else {
      ElMessage.error(err?.message || "网络异常");
    }
    return Promise.reject(err);
  },
);

export async function apiGet<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.get<ApiEnvelope<T>>(url, config);
  return res.data.data;
}

export async function apiPost<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.post<ApiEnvelope<T>>(url, data, config);
  return res.data.data;
}

export async function apiPostForm<T>(url: string, data: FormData, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.post<ApiEnvelope<T>>(url, data, {
    ...config,
    headers: { ...(config?.headers || {}), "Content-Type": "multipart/form-data" },
  });
  return res.data.data;
}

export async function apiPatch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.patch<ApiEnvelope<T>>(url, data, config);
  return res.data.data;
}

export async function apiPut<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.put<ApiEnvelope<T>>(url, data, config);
  return res.data.data;
}

export async function apiDelete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.delete<ApiEnvelope<T>>(url, config);
  return res.data.data;
}

export default http;
