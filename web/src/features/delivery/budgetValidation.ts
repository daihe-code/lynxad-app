import { budgetInputError, deliveryInputRules } from './inputValidation.ts';
export const COST_DAILY_BUDGET_MINIMUM = deliveryInputRules.costBudgetMinimum;
export const COST_DAILY_BUDGET_HINT = `控成本日预算不能低于 ${COST_DAILY_BUDGET_MINIMUM} 元（含 ${COST_DAILY_BUDGET_MINIMUM} 元），最多两位小数`;

export function creationBudgetError(value: string, mode: string): string {
  return budgetInputError(value, mode);
}

export function creationRoiError(value: string, mode: string): string {
  if (mode !== 'CUSTOM' && mode !== 'SMART_BID_CUSTOM') return '';
  const text = value.trim(), amount = Number(text);
  return !/^\d+(\.\d{1,2})?$/.test(text) || amount < .01 || amount > 100
    ? '净成交ROI目标须为 0.01–100，最多两位小数' : '';
}
