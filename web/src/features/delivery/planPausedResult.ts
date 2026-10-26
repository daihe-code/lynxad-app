import type { AgentActionVo } from '../agentops/agentopsApi';
import type { InsightMetrics } from './planInsightsApi';
import { deliveryMetricCards } from './planDeliveryResult.ts';
import { PLAN_PAUSE_REASONS } from './planPause.ts';
import { shiftDay } from './statisticsRange.ts';

type PausePlan = { planId: string; status: string; qcPlanId?: string; createdAt?: string | null; startDate?: string | null; smartBidType?: string | null; roi2Goal?: number | null };
const controlTypes: Record<string,string> = { MANUAL_DISABLE:'手动暂停投放', MANUAL_ENABLE:'恢复投放', MANUAL_FINISH:'结束计划', QC_ABNORMAL_PAUSE:'平台异常暂停' };
export function hasPlanPausedResult(plan?: Pick<PausePlan,'status'|'qcPlanId'> | null) { return !!plan?.qcPlanId && plan.status === 'PAUSED'; }
export function pauseTimestamp(value?: string | null) {
  if (!value) return NaN;
  return Date.parse(/^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(value) ? `${value.replace(' ', 'T')}+08:00` : value);
}
function day(timestamp: number) {
  if (!Number.isFinite(timestamp)) return undefined;
  const parts = new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(timestamp));
  return ['year','month','day'].map(key=>parts.find(part=>part.type===key)!.value).join('-');
}
export function pauseHistory(plan: PausePlan, actions: readonly AgentActionVo[]) {
  const unique = new Map<string,AgentActionVo>();
  for (const action of actions) if (action?.planId === plan.planId && controlTypes[action.type] && !unique.has(action.id)) unique.set(action.id,action);
  return [...unique.values()].sort((a,b)=>(pauseTimestamp(a.snapshotTs)||0)-(pauseTimestamp(b.snapshotTs)||0));
}
export function currentPause(plan: PausePlan, actions: readonly AgentActionVo[], now = Date.now()) {
  const history = pauseHistory(plan,actions);
  const current = history.at(-1);
  if (!current || !['MANUAL_DISABLE','QC_ABNORMAL_PAUSE'].includes(current.type)) return undefined;
  if (current.type === 'QC_ABNORMAL_PAUSE' && !current.open) return undefined;
  const timestamp = pauseTimestamp(current.snapshotTs);
  if (Number.isFinite(timestamp) && timestamp > now) return undefined;
  const text = current.summary?.trim();
  const reason = text && text !== current.type ? text.replace(/^(暂停投放|暂停原因)[：:]\s*/, '').trim() : '';
  return { action:current, timestamp, reason, manual:current.type==='MANUAL_DISABLE', selected:PLAN_PAUSE_REASONS.find(option=>option === reason) };
}
export function pauseElapsed(timestamp: number, now = Date.now()) {
  if (!Number.isFinite(timestamp) || timestamp > now) return '';
  const minutes = Math.floor((now-timestamp)/60000);
  if (!minutes) return '已暂停不足1分钟';
  const days = Math.floor(minutes/1440), hours = Math.floor(minutes%1440/60), rest = minutes%60;
  return `已暂停 ${days ? `${days} 天 ` : ''}${hours ? `${hours} 小时 ` : ''}${rest || !hours ? `${rest} 分` : ''}`.trim();
}
export function pauseStatisticsRange(plan: PausePlan, timestamp: number, now = Date.now()) {
  const end = day(timestamp);
  if (!end || timestamp > now) return undefined;
  const rawStart = plan.startDate && /^\d{4}-\d{2}-\d{2}$/.test(plan.startDate) && day(Date.parse(`${plan.startDate}T00:00:00+08:00`)) === plan.startDate ? plan.startDate : day(pauseTimestamp(plan.createdAt));
  const startKnown = !!rawStart && rawStart <= end;
  const first = startKnown ? rawStart! : end, earliest = shiftDay(end,-179), clipped = first < earliest;
  return { range:[clipped ? earliest : first,end] as [string,string], clipped, startKnown };
}
export function pausedMetricCards(plan: PausePlan, metrics: InsightMetrics = {}) {
  return deliveryMetricCards(plan,metrics).map((card,index)=>index === 0 ? {...card,label:'暂停前消耗',note:'所示统计区间内的广告消耗'} : card);
}
export function pauseActionLabel(type: string) { return controlTypes[type] || '操作记录'; }
