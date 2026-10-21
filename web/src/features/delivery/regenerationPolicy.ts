export function regenerationWait(retryAt: number | undefined, now = Date.now()): number {
  return retryAt && Number.isFinite(retryAt) ? Math.max(0, Math.ceil((retryAt - now) / 1000)) : 0;
}
export function regenerationFeedbackError(reasons: string[], description: string): string {
  if (!reasons.length || reasons.length > 10 || reasons.some(r => !r.trim() || [...r].length > 100)) return '请选择重新生成原因';
  if ([...description.trim()].length > 50) return '重新生成说明最多50字';
  const instruction = [...new Set(reasons.map(r => r.trim()))].join('、') + (description.trim() ? `；${description.trim()}` : '');
  return [...instruction].length > 200 ? '重新生成原因与说明合计最多200字，请精简后提交' : '';
}
