import axios, { AxiosHeaders, type AxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';
import { ApiRequestError, requestFailure } from './apiErrors';
import { withReadRetry } from './readRetry';

export interface ApiEnvelope<T> { code: number; message: string; data: T }
export interface ApiRequestConfig extends AxiosRequestConfig { readRetry?: boolean }
const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/lynx-ad', timeout:20000 });
const publicAuth = (url: string) => /\/tenant\/auth\/(sms|password)\//.test(url);
function sessionIdentity(): string { try { return localStorage.getItem('lynx.token') || ''; } catch { return ''; } }

http.interceptors.request.use(config => {
  const token = sessionIdentity();
  if (token && !publicAuth(config.url || '')) config.headers.Authorization = `Bearer ${token}`;
  const idem = config.headers['Idempotency-Key'] || config.headers.idempotencyKey;
  if (idem) config.headers.idempotencyKey = idem;
  return config;
});

// Notify only after the bounded query retry finishes. Quiet polling still rejects structured errors.
http.interceptors.response.use(res => {
  const body = res.data as ApiEnvelope<unknown>;
  if (!body || typeof body !== 'object' || typeof body.code !== 'number')
    return Promise.reject(new ApiRequestError('服务返回内容不完整，请重新查询当前状态。',
      {kind:res.config.method === 'get' ? 'SERVICE' : 'UNCERTAIN',action:res.config.method === 'get' ? 'RETRY_READ' : 'VERIFY',method:res.config.method || 'GET'}));
  if (body.code !== 0) return Promise.reject(requestFailure({...body,config:res.config}));
  return res;
}, error => Promise.reject(requestFailure(error)));

async function perform<T>(method: string, url: string, data?: unknown, config?: ApiRequestConfig): Promise<T> {
  let identity = sessionIdentity();
  const request = async (retryBudgetMs?: number) => {
    const response = await http.request<ApiEnvelope<T>>({...config,method,url,data,
      timeout:retryBudgetMs === undefined ? config?.timeout : Math.min(config?.timeout || http.defaults.timeout || 20000,retryBudgetMs)});
    const replacement = response.headers['x-auth-token'];
    if (identity && identity === sessionIdentity() && !publicAuth(url) && typeof replacement === 'string' && replacement.trim()) {
      localStorage.setItem('lynx.token', replacement);
      identity = replacement;
      window.dispatchEvent(new Event('lynx:session-replaced'));
    }
    return response.data.data;
  };
  try {
    const result = method === 'GET' ? await withReadRetry(request,{method,url,enabled:config?.readRetry,
      signal:config?.signal as AbortSignal | undefined,identity:sessionIdentity}) : await request();
    if (identity !== sessionIdentity()) throw new ApiRequestError('请求已取消',{kind:'CANCELLED',action:'NONE',method});
    return result;
  } catch (error) {
    if (identity !== sessionIdentity()) throw new ApiRequestError('请求已取消',{kind:'CANCELLED',action:'NONE',method});
    const failure = requestFailure(error,{method,url});
    if (failure.kind === 'LOGIN' && !publicAuth(url) && identity === sessionIdentity()) {
      try { localStorage.removeItem('lynx.token'); } catch { /* Browser storage may be disabled. */ }
      if (!location.pathname.startsWith('/login')) location.href = '/login';
    }
    if (failure.kind !== 'CANCELLED' && AxiosHeaders.from(config?.headers as AxiosHeaders | undefined).get('X-Quiet-Request') !== 'true' && !failure.notified) {
      failure.notified = true; ElMessage.error(failure.message);
    }
    throw failure;
  }
}
export const apiGet = <T>(url: string, config?: ApiRequestConfig) => perform<T>('GET',url,undefined,config);
export const apiPost = <T>(url: string, data?: unknown, config?: ApiRequestConfig) => perform<T>('POST',url,data,config);
export const apiPut = <T>(url: string, data?: unknown, config?: ApiRequestConfig) => perform<T>('PUT',url,data,config);
export const apiPatch = <T>(url: string, data?: unknown, config?: ApiRequestConfig) => perform<T>('PATCH',url,data,config);
export const apiDelete = <T>(url: string, config?: ApiRequestConfig) => perform<T>('DELETE',url,config?.data,config);
export const apiPostForm = <T>(url: string, data: FormData, config?: ApiRequestConfig) =>
  perform<T>('POST',url,data,{...config,headers:{...config?.headers,'Content-Type':'multipart/form-data'}});
export default http;
