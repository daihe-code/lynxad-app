import type { InsightMetrics } from './planInsightsApi';

interface DeliveryPlan { status?: string; qcPlanId?: string; budget?: number | null; smartBidType?: string | null; roi2Goal?: number | null }
export function hasPlanDeliveryResult(plan?: DeliveryPlan | null): boolean {
  return !!plan?.qcPlanId && ['DELIVERING', 'SCHEDULED'].includes(plan.status || '');
}
const known = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value >= 0;
const number = (value: unknown, digits = 2) => known(value) ? value.toLocaleString('zh-CN', { maximumFractionDigits: digits }) : '—';
const money = (value: unknown) => known(value) ? `¥${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—';

export function deliveryMetricCards(plan: DeliveryPlan, metrics: InsightMetrics = {}) {
  const { cost, netRoi, shows, clicks, orders } = metrics;
  const budgetKnown = known(plan.budget) && plan.budget > 0;
  const custom = ['CUSTOM', 'SMART_BID_CUSTOM'].includes(plan.smartBidType || '');
  const goalKnown = custom && known(plan.roi2Goal) && plan.roi2Goal > 0;
  const belowGoal = goalKnown && known(netRoi) && netRoi < plan.roi2Goal!;
  return [
    { label: '今日消耗', icon: 'coins', value: money(cost), unit: '', warning: false,
      note: budgetKnown && known(cost) ? `预算剩余 ${money(Math.max(0, plan.budget! - cost))}（${number(cost / plan.budget! * 100, 1)}% 已消耗）` : '今日平台消耗，未取得时显示 —' },
    { label: '净成交 ROI', icon: 'target', value: number(netRoi), unit: '', warning: belowGoal,
      note: goalKnown ? known(netRoi) ? `${belowGoal ? '低于' : '已达到'}目标 ${number(plan.roi2Goal)}` : `目标 ${number(plan.roi2Goal)}，数据待同步` : '净成交金额 / 广告消耗' },
    { label: '曝光量', icon: 'eye', value: number(shows, 0), unit: '', warning: false,
      note: known(shows) && shows > 0 && known(clicks) ? `点击率 ${number(clicks / shows * 100, 2)}%` : '平台尚未提供有效点击率' },
    { label: '成交订单数', icon: 'cart', value: number(orders, 0), unit: known(orders) ? '单' : '', warning: false,
      note: known(cost) && known(orders) && orders > 0 ? `平均成交成本 ${money(cost / orders)}` : '整体成交订单口径' },
  ];
}
