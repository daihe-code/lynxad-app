/** A review id is correlation only; the original edit flow still owns validation and confirmation. */
export function monitoringReviewHeader(pathname: string, search: string, method: string, requestUrl: string) {
  if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase())) return undefined;
  const plan = /^\/campaigns\/([^/]+)\/edit$/.exec(pathname)?.[1];
  const target = /^\/api\/v1\/delivery\/plans\/([^/?]+)(\/[^?]*)?(?:\?.*)?$/.exec(requestUrl);
  const id = new URLSearchParams(search).get('monitoringReview');
  if (!plan || !id || !/^[a-f\d]{8}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{12}$/i.test(id)) return undefined;
  // The server resolves this work draft to the original plan before accepting correlation.
  if (method.toUpperCase() === 'POST' && /^\/api\/v1\/delivery\/creation\/[^/?]+\/content-edit\/save$/.test(requestUrl)) return id;
  if (!target || target[1] !== plan) return undefined;
  if (/^\/(optimization|sync-detail|audit-refresh|transfer)(\/|$)/.test(target[2] || '') || target[2] === '/settings/reconcile') return undefined;
  return id;
}
export function reviewStatus(status: string) {
  return ({ TRACKING: '等待实际修改', OBSERVING: '观察中', WAITING_DATA: '等待成熟数据', COMPARED: '已完成可比复评', INCOMPARABLE: '不可单独比较', NOT_APPLIED: '未核实修改', INSUFFICIENT: '可比样本不足' } as Record<string, string>)[status] || '待核对';
}
