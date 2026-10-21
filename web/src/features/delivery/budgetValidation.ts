import { budgetInputError, roiInputError, deliveryInputRules } from './inputValidation.ts';
export const COST_DAILY_BUDGET_MINIMUM = deliveryInputRules.costBudgetMinimum;
export const COST_DAILY_BUDGET_HINT = `控成本日预算不能低于 ${COST_DAILY_BUDGET_MINIMUM} 元（含 ${COST_DAILY_BUDGET_MINIMUM} 元），最多两位小数`;

export function creationBudgetError(value: string, mode: string): string {
  return budgetInputError(value, mode);
}

export function creationRoiError(value: string, mode: string): string {
  return roiInputError(value, mode);
}
