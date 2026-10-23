/** A missing receipt is not proof that the platform rejected an upload. */
export function canRetryAssetSync(status: string): boolean {
  return ['WAITING_AUTH', 'FAILED', 'PAUSED'].includes(status);
}
export function canVerifyAssetSync(status: string, checkedAt?: string, now = Date.now()): boolean {
  if (status !== 'UNCERTAIN') return false;
  const checked = checkedAt ? Date.parse(checkedAt) : NaN;
  return !Number.isFinite(checked) || now - checked >= 30000;
}
