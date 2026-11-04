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
/** Application refresh policy: completed results for five minutes, errors/empty estimates for thirty seconds. */
export function recommendationsNeedRefresh(state: CreationState, now = Date.now()): boolean {
  const data=state.recommendations, ids=new Set(data?.productIds || []);
  if (!data || data.accountId!==state.accountId || data.awemeId!==state.awemeId || ids.size!==state.products.length || state.products.some(p=>!ids.has(p.qcProductId))) return true;
  const fetched=Date.parse(data.fetchedAt || ''), age=now-fetched;
  if (!Number.isFinite(fetched) || age < -60000) return true;
  const complete=!data.budgetError && !data.roiError && !!recommendedBudget(data) && rows(data).length>0 && !!recommendedRoi(data);
  return age >= (complete ? 300000 : 30000);
}
export function budgetChoices(data: Recommendations): number[] {
  const suggested = data?.budget?.suggested_budget_list;
  return [...new Set([...(Array.isArray(suggested) ? suggested : []), ...rows(data).map(r => r.budget), recommendedBudget(data)].map(positive).filter((v): v is number => v !== undefined))];
}
export interface BudgetOption { budget: number; roi: string; orders: string; badge?: string }
export function budgetOptions(data: Recommendations): BudgetOption[] {
  const choices = budgetChoices(data), recommended = recommendedBudget(data);
  return choices.map(value => {
    const row = rows(data).find(r => positive(r.budget) === value);
    const hints = budgetEstimateHints(data, String(value)), roi = number(row?.estimate_roi);
    return { budget: value, roi: roi !== undefined ? String(roi) : hints.roi, orders: hints.orders,
      badge: value === recommended ? data?.budgetFromCache ? '上次推荐' : '推荐档位' : choices.length > 1 && value === Math.min(...choices) ? '低价尝鲜' : choices.length > 1 && value === Math.max(...choices) ? '进阶带量' : undefined };
  });
}
export function budgetEstimateHints(data: Recommendations, budget: string): { roi: string; orders: string; message?: string } {
  const selected = positive(budget);
  const row = selected === undefined ? undefined : rows(data).find(r => positive(r.budget) === selected);
  if (!row) {
    const message = data?.budgetFromCache ? '千川本次预算推荐获取失败，已保留上次推荐金额；ROI 和订单预估暂不可用。'
      : data?.budgetError ? '千川预算预估暂不可用，可手动填写日预算。'
      : !budget.trim() ? '填写日预算后查看平台提供的预估。'
      : rows(data).length ? '当前日预算未对应平台预估档位，预估暂不展示。'
      : recommendedBudget(data) ? '千川已返回推荐日预算，暂未提供该商品组合的 ROI 和订单预估。'
      : '千川暂未返回预算预估，可手动填写日预算。';
    return { roi: '--', orders: '--', message };
  }
  const lo = number(row.min_estimate_roi), hi = number(row.max_estimate_roi), roi = number(row.estimate_roi);
  const min = number(row.min_estimate_convert), max = number(row.max_estimate_convert), orders = number(row.estimate_convert);
  return {
    roi: lo !== undefined && hi !== undefined ? `${lo} ～ ${hi}` : lo !== undefined ? `不低于 ${lo}` : roi !== undefined ? String(roi) : '--',
    orders: min !== undefined && max !== undefined ? `${min} ～ ${max}单` : orders !== undefined ? `${orders}单` : '--',
  };
}
