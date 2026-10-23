import { ApiRequestError, requestFailure, type RequestContext } from './apiErrors.ts';

/** Only verified queries may replay; some GET endpoints create OAuth sessions or update recommendations. */
export function isSafeRead(context: RequestContext): boolean {
  if ((context.method || '').toUpperCase() !== 'GET') return false;
  const path = (context.url || '').split('?')[0];
  return path === '/api/v1/identity/oauth/accounts'
    || /^\/api\/v1\/delivery\/creation\/(products(?:\/restore)?|[A-Za-z0-9_-]+)$/.test(path)
    || /^\/api\/v1\/delivery\/plans\/[A-Za-z0-9_-]+$/.test(path);
}
export interface ReadRetryOptions extends RequestContext {
  signal?: AbortSignal; enabled?: boolean;
  /** Capture session identity once; do not replay a request under a newly logged-in user. */
  identity?: () => string;
}
interface RetryRuntime {
  now: () => number; random: () => number;
  wait: (ms: number, signal?: AbortSignal) => Promise<void>;
  online: () => boolean;
}
const cancelled = () => new ApiRequestError('请求已取消', {kind:'CANCELLED',action:'NONE',method:'GET'});
function wait(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve,reject) => {
    if (signal?.aborted) { reject(cancelled()); return; }
    const abort = () => { clearTimeout(timer); signal?.removeEventListener('abort',abort); reject(cancelled()); };
    const timer = setTimeout(() => { signal?.removeEventListener('abort',abort); resolve(); },ms);
    signal?.addEventListener('abort',abort,{once:true});
  });
}
const defaults: RetryRuntime = { now:Date.now, random:Math.random, wait,
  online:() => typeof navigator === 'undefined' || navigator.onLine !== false };

export function pollRecoveryDelay(failure: ApiRequestError, consecutiveFailures: number): number | undefined {
  if (!['NETWORK','SERVICE','RATE_LIMIT'].includes(failure.kind) || (failure.retryAfterMs || 0) > 300000) return;
  return Math.max(Math.min(60000,15000 * 2**Math.min(2,Math.max(0,consecutiveFailures-1))),failure.retryAfterMs || 0);
}

export async function withReadRetry<T>(request: (retryBudgetMs?: number) => Promise<T>, options: ReadRetryOptions,
  runtime: RetryRuntime = defaults): Promise<T> {
  const start = runtime.now(), identity = options.identity?.();
  for (let attempt=0;;attempt++) {
    if (options.signal?.aborted) throw cancelled();
    try { return await request(attempt > 0 ? Math.max(1,10000-(runtime.now()-start)) : undefined); }
    catch (error) {
      const failure = requestFailure(error,options); failure.attempts = attempt+1;
      const delay = Math.max(600 + Math.floor(runtime.random()*200),failure.retryAfterMs || 0);
      if (attempt >= 1 || options.enabled === false || !isSafeRead(options) || !failure.automaticRetry
        || !runtime.online() || delay > 5000 || runtime.now()-start+delay > 10000) throw failure;
      await runtime.wait(delay,options.signal);
      if (identity !== options.identity?.()) throw cancelled();
      if (!runtime.online() || runtime.now()-start >= 10000) throw failure;
    }
  }
}
