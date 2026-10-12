import type { CreationState } from './creationApi';

type Recommendations = CreationState['recommendations'];
const number = (v: unknown): number | undefined => {
  if (typeof v !== 'number' && (typeof v !== 'string' || !v.trim())) return undefined;
  const n = Number(v);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
};
const positive = (v: unknown) => { const n = number(v); return n && n > 0 ? n : undefined; };
const rows = (data: Recommendations): Record<string, unknown>[] => {
  const value = data?.budget?.estimate_trans_cost_list;
  return Array.isArray(value) ? value.filter(v => v && typeof v === 'object') : [];
};
export const recommendedBudget = (data: Recommendations) => positive(data?.budget?.budget) || positive(data?.budget?.suggested_budget);
export const recommendedRoi = (data: Recommendations) => positive(data?.roi?.ecp_roi2_goal);
export function budgetChoices(data: Recommendations): number[] {
  const suggested = data?.budget?.suggested_budget_list;
  return [...new Set([...(Array.isArray(suggested) ? suggested : []), ...rows(data).map(r => r.budget)].map(positive).filter((v): v is number => v !== undefined))];
}
export interface BudgetOption { budget: number; roi: string; orders: string; badge?: string }
export function budgetOptions(data: Recommendations): BudgetOption[] {
  const choices = budgetChoices(data), recommended = recommendedBudget(data);
  return choices.map(value => {
    const row = rows(data).find(r => positive(r.budget) === value);
    const hints = budgetEstimateHints(data, String(value)), roi = number(row?.estimate_roi);
    return { budget: value, roi: roi !== undefined ? String(roi) : hints.roi, orders: hints.orders,
      badge: value === recommended ? '推荐档位' : choices.length > 1 && value === Math.min(...choices) ? '低价尝鲜' : choices.length > 1 && value === Math.max(...choices) ? '进阶带量' : undefined };
  });
}
export function budgetEstimateHints(data: Recommendations, budget: string): { roi: string; orders: string } {
  const missing = data?.budgetError ? '预算预估获取失败，请重新获取' : !budget.trim() ? '填写日预算后查看预估' : '该预算暂无平台预估，可选择推荐档位';
  const selected = positive(budget);
  const row = selected === undefined ? undefined : rows(data).find(r => positive(r.budget) === selected);
  if (!row) return { roi: missing, orders: missing };
  const lo = number(row.min_estimate_roi), hi = number(row.max_estimate_roi), roi = number(row.estimate_roi);
  const min = number(row.min_estimate_convert), max = number(row.max_estimate_convert), orders = number(row.estimate_convert);
  return {
    roi: lo !== undefined && hi !== undefined ? `${lo} ～ ${hi}` : lo !== undefined ? `不低于 ${lo}` : roi !== undefined ? String(roi) : '平台未返回ROI预估',
    orders: min !== undefined && max !== undefined ? `${min} ～ ${max}单` : orders !== undefined ? `${orders}单` : '平台未返回订单预估',
  };
}
