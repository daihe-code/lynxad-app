export const COST_DAILY_BUDGET_MINIMUM = 300;
export const COST_DAILY_BUDGET_HINT = `控成本日预算不能低于 ${COST_DAILY_BUDGET_MINIMUM} 元（含 ${COST_DAILY_BUDGET_MINIMUM} 元），最多两位小数`;

export function creationBudgetError(value: string, mode: string): string {
  const text = value.trim();
  const cost = mode === 'CUSTOM' || mode === 'SMART_BID_CUSTOM';
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return '请输入正确的日预算，最多两位小数';
  const amount = Number(text);
  if (cost && amount < COST_DAILY_BUDGET_MINIMUM) return COST_DAILY_BUDGET_HINT;
  if (amount <= 0 || amount > 100000) return '日预算须大于 0、不超过 100000 元，最多两位小数';
  return '';
}
