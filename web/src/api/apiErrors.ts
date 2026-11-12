export type FailureKind = 'LOGIN' | 'AUTHORIZATION' | 'PERMISSION' | 'VALIDATION' | 'CONFLICT'
  | 'STATE' | 'RATE_LIMIT' | 'NETWORK' | 'SERVICE' | 'UNCERTAIN' | 'CANCELLED' | 'UNKNOWN';
export type RecoveryAction = 'LOGIN' | 'ACCOUNTS' | 'EDIT' | 'REFRESH' | 'RETRY_READ' | 'VERIFY' | 'NONE';
interface FailureResponse { status?: number; data?: unknown; headers?: unknown }
interface FailureInput {
  code?: unknown; message?: unknown; response?: FailureResponse; name?: string;
  config?: { method?: string; url?: string };
}
export interface RequestContext { method?: string; url?: string }
const object = (value: unknown): Record<string, unknown> => value && typeof value === 'object' ? value as Record<string, unknown> : {};
const numericCode = (value: unknown) => typeof value === 'number' && Number.isInteger(value) ? value : undefined;

export function safeErrorMessage(value: unknown): string {
  if (typeof value !== 'string') return '';
  return value.replace(/Bearer\s+[^\s,;"']+/gi, 'Bearer [已隐藏]')
    .replace(/((?:access[_-]?token|refresh[_-]?token|api[_-]?key|secret|password|authorization)["'\s]*[:=]["'\s]*)[^\s,;}\]"']+/gi, '$1[已隐藏]')
    .replace(/https?:\/\/[^\s<>]+/gi, '[服务地址]')
    .replace(/[\u0000-\u001f]/g, ' ').trim().slice(0, 400);
}

/** Stable error contract for pages. A platform authorization error never expires the app session. */
export class ApiRequestError extends Error {
  kind: FailureKind; action: RecoveryAction; code?: number; status?: number; method: string;
  retryable: boolean; automaticRetry: boolean; notified = false; attempts = 1;
  response?: FailureResponse; retryAfterMs?: number;
  constructor(message: string, details: { kind: FailureKind; action: RecoveryAction; method: string;
    code?: number; status?: number; retryable?: boolean; automaticRetry?: boolean; response?: FailureResponse; retryAfterMs?: number }) {
    super(message); this.name = 'ApiRequestError';
    this.kind = details.kind; this.action = details.action; this.method = details.method;
    this.code = details.code; this.status = details.status; this.response = details.response;
    this.retryable = !!details.retryable; this.automaticRetry = !!details.automaticRetry;
    this.retryAfterMs = details.retryAfterMs;
  }
}

function retryAfter(headers: unknown): number | undefined {
  const h = object(headers);
  const raw = h['retry-after'] ?? h['Retry-After'];
  if (typeof raw !== 'string' && typeof raw !== 'number') return;
  const seconds = Number(raw);
  if (Number.isFinite(seconds) && seconds >= 0) return seconds * 1000;
  const date = Date.parse(String(raw));
  if (Number.isFinite(date)) return Math.max(0, date - Date.now());
}

export function requestFailure(error: unknown, context: RequestContext = {}): ApiRequestError {
  if (error instanceof ApiRequestError) return error;
  const source = object(error) as FailureInput, body = object(source.response?.data);
  const parsedCode = numericCode(body.code) ?? numericCode(source.code), code = parsedCode === 0 ? undefined : parsedCode;
  const status = source.response?.status;
  const method = (context.method || source.config?.method || 'GET').toUpperCase(), read = method === 'GET';
  const business = code !== undefined;
  const transport = !!source.response || !!source.config || ['ERR_NETWORK','ECONNABORTED','ETIMEDOUT','ERR_BAD_RESPONSE','ERR_BAD_REQUEST'].includes(String(source.code));
  const detail = safeErrorMessage(body.message ?? source.message);
  const make = (kind: FailureKind, action: RecoveryAction, fallback: string, retryable=false, automaticRetry=false) =>
    new ApiRequestError(fallback, { kind, action, method, code, status, retryable, automaticRetry,
      response: source.response ? {...source.response,data:{...body,message:fallback}} : undefined,
      retryAfterMs: retryAfter(source.response?.headers) });
  if (source.code === 'ERR_CANCELED' || source.name === 'AbortError') return make('CANCELLED','NONE','请求已取消');
  if (code === 401 || (!business && status === 401)) return make('LOGIN','LOGIN','坤境登录已失效，请重新登录。');
  if ([1211,40102,40105].includes(code!)) return make('AUTHORIZATION','ACCOUNTS','千川授权已失效，请在账户管理中重新授权；当前填写内容已保留。');
  if (code === 403 || status === 403 || [1201,1205].includes(code!)) return make('PERMISSION','ACCOUNTS',detail || '当前账户没有此操作权限，请检查授权账户或联系管理员。');
  if (code === 1404 || code === 409 || status === 409) return make('CONFLICT','REFRESH',detail || '内容已在其他位置更新，请重新读取后核对，当前输入已保留。');
  if ([1523,8003].includes(code!) || (!read && (code === 1406 || code === 9999 || [500,502,503,504].includes(code!)))
    || (!read && !business && transport && (status === undefined || status >= 500)))
    return make('UNCERTAIN','VERIFY','请求结果暂未确认，请先查询任务或平台状态，勿重复提交。');
  if (code === 429 || code === 40100 || status === 429)
    return make('RATE_LIMIT',read ? 'RETRY_READ' : 'NONE','请求过于频繁，请稍后再试。',read);
  if ([400,422,1001,1302,1402,1503].includes(code!) || status === 400 || status === 422)
    return make('VALIDATION','EDIT',detail || '提交内容不符合要求，请检查填写项后再提交。');
  if ([404,1403,1405].includes(code!) || status === 404)
    return make('STATE','REFRESH',detail || '内容已变化或不存在，请重新查询当前状态。');
  if (!business && transport && status === undefined) return make('NETWORK',read ? 'RETRY_READ' : 'VERIFY',
    source.code === 'ECONNABORTED' || source.code === 'ETIMEDOUT' ? '查询超时，当前填写内容已保留，可重新查询。' : '暂时无法连接服务，请检查网络后重新查询。',read,read);
  if ((status !== undefined && status >= 500) || [500,502,503,504].includes(code!))
    return make('SERVICE',read ? 'RETRY_READ' : 'NONE','服务暂时不可用，请稍后重新查询。',read,
      read && !business && [502,503,504].includes(status!));
  return make('UNKNOWN',read ? 'RETRY_READ' : 'NONE',detail || '请求未完成，请稍后核对当前状态。');
}

export function recoveryLabel(failure: ApiRequestError): string {
  return ({ LOGIN:'重新登录', ACCOUNTS:'前往账户管理', EDIT:'检查填写项', REFRESH:'重新读取并核对',
    RETRY_READ:'重新查询', VERIFY:'核查当前状态', NONE:'' })[failure.action];
}
