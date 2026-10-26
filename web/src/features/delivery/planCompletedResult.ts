import type { InsightMetrics,PlanMaterial } from './planInsightsApi';
import type { OptimizationEvent } from './optimizationApi';
import type { StatisticsRange } from './statisticsRange';
import { shiftDay,rangeDays } from './statisticsRange.ts';
import { planStatusDisplay } from './planStatus.ts';
import { pauseTimestamp } from './planPausedResult.ts';
import { terminatedStatisticsRange } from './planTerminatedResult.ts';
import { deliveryMetricCards } from './planDeliveryResult.ts';

type CompletedPlan = {planId:string;status:string;qcStatus?:string|null;qcPlanId?:string;startDate?:string|null;endDate?:string|null;createdAt?:string|null;smartBidType?:string|null;roi2Goal?:number|null};
export function hasPlanCompletedResult(plan?: Pick<CompletedPlan,'status'|'qcStatus'|'qcPlanId'> | null) {
  if (!plan?.qcPlanId || plan.status === 'DELETED') return false;
  return planStatusDisplay(plan.status,plan.qcStatus).group === 'COMPLETED' || plan.status === 'COMPLETED' && !plan.qcStatus;
}
const validDate = (value?: string|null) => !!value && /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && shiftDay(value,0) === value;
export function completedStatisticsRange(plan: CompletedPlan,now = Date.now()) {
  const end = validDate(plan.endDate) ? pauseTimestamp(`${plan.endDate}T00:00:00+08:00`) : NaN;
  const endKnown = Number.isFinite(end) && end <= now && (!validDate(plan.startDate) || plan.startDate! <= plan.endDate!);
  const period = terminatedStatisticsRange(plan,endKnown ? end : NaN,now);
  return {...period,endKnown,cumulative:period.cumulative && endKnown};
}
const known = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value >= 0;
const money = (value: unknown) => known(value) ? `¥${value.toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})}` : '—';
const number = (value: number,digits = 2) => value.toLocaleString('zh-CN',{maximumFractionDigits:digits});
export function bestCompletedMaterial(materials?: readonly PlanMaterial[]) {
  if (!materials) return undefined;
  const unique = new Map<string,PlanMaterial>();
  for (const material of materials) if (material?.id && known(material.metrics?.orders) && material.metrics.orders > 0) unique.set(`${material.type}:${material.id}`,material);
  return [...unique.values()].sort((a,b)=>b.metrics.orders!-a.metrics.orders! || `${a.type}:${a.id}`.localeCompare(`${b.type}:${b.id}`))[0];
}
export function completedMetricCards(plan: CompletedPlan,metrics: InsightMetrics,period: {range:StatisticsRange;cumulative:boolean},materials?: readonly PlanMaterial[]) {
  const base = deliveryMetricCards(plan,metrics),best = bestCompletedMaterial(materials),days = rangeDays(period.range);
  const goal = ['CUSTOM','SMART_BID_CUSTOM'].includes(plan.smartBidType || '') && known(plan.roi2Goal) && plan.roi2Goal > 0 ? plan.roi2Goal : undefined;
  const above = goal !== undefined && known(metrics.netRoi) && metrics.netRoi > goal;
  return [
    {label:'净成交金额',icon:'chart-up',value:money(metrics.netGmv),unit:'',note:known(metrics.netRoi) ? `每 1 元广告消耗带来 ${number(metrics.netRoi)} 元净成交额` : '平台净成交金额口径',positive:known(metrics.netGmv) && metrics.netGmv > 0,warning:false,material:false},
    {...base[1],note:above ? `超出目标 ${number(goal!)}，超出 ${number((metrics.netRoi!-goal!)/goal!*100,1)}%` : base[1].note,positive:above,material:false},
    {...base[0],label:period.cumulative ? '总消耗' : '区间消耗',note:known(metrics.cost) ? `日均 ${money(metrics.cost/days)}（按 ${days} 个统计日）` : '平台消耗数据待同步',positive:false,material:false},
    {...base[2],label:period.cumulative ? '总曝光量' : '区间曝光量',positive:false,material:false},
    {...base[3],label:period.cumulative ? '总成交量' : '区间成交量',positive:false,material:false},
    {label:'表现最好素材',icon:'bulb',value:best ? best.name?.trim() || `素材 ${best.id}` : materials ? '暂无成交素材' : '素材数据待同步',unit:'',note:best ? `成交 ${number(best.metrics.orders!,0)} 单；按可查询素材的成交数排序` : materials ? '可查询素材暂无有效成交数据' : '素材效果数据尚未完整取得',positive:false,warning:false,material:true},
  ];
}
export const completedAutomationKinds = [
  {kind:'BUDGET',label:'预算节奏优化',note:'调整计划日预算'},
  {kind:'ROI',label:'ROI 目标调整',note:'调整计划净成交 ROI 目标'},
  {kind:'MATERIAL_FOLLOW',label:'素材追投',note:'追加已选素材的投放'},
  {kind:'MATERIAL_ADD',label:'素材补充',note:'补充新素材并确认投放生效'},
] as const;
export function completedAutomationCounts(plan: CompletedPlan,events: readonly OptimizationEvent[] | undefined,range: StatisticsRange,now = Date.now()) {
  if (!events) return {counts:undefined,timeIncomplete:false};
  const start = pauseTimestamp(`${range[0]}T00:00:00+08:00`),end = pauseTimestamp(`${shiftDay(range[1],1)}T00:00:00+08:00`);
  const counts:Record<string,number> = Object.fromEntries(completedAutomationKinds.map(item=>[item.kind,0]));
  const unique = new Set<string>();let timeIncomplete = false;
  for (const event of events) {
    if (event.planId !== plan.planId || event.status !== 'SUCCEEDED' || !completedAutomationKinds.some(item=>item.kind === event.kind) || event.proposal?.kind && event.proposal.kind !== event.kind) continue;
    const qcIds = [event.actual?.qcId,event.report?.plan_id].filter(id=>id !== undefined && id !== null && id !== '');
    if (qcIds.some(id=>String(id) !== plan.qcPlanId) || unique.has(event.id)) continue;
    unique.add(event.id);
    const timestamp = pauseTimestamp(event.finishedAt);
    if (!Number.isFinite(timestamp) || timestamp > now) {timeIncomplete = true;continue;}
    if (timestamp >= start && timestamp < end) counts[event.kind!]++;
  }
  return {counts:timeIncomplete ? undefined : counts,timeIncomplete};
}
