import type { AgentActionVo } from '../agentops/agentopsApi';
import type { InsightMetrics } from './planInsightsApi';
import type { HistoryPage, OptimizationEvent } from './optimizationApi';
import { deliveryMetricCards } from './planDeliveryResult.ts';
import { PLAN_PAUSE_REASONS } from './planPause.ts';
import { shiftDay } from './statisticsRange.ts';

type PausePlan = { planId: string; status: string; qcPlanId?: string; createdAt?: string | null; startDate?: string | null; smartBidType?: string | null; roi2Goal?: number | null };
export type PauseAction = AgentActionVo & { automation?: { eventId: string; cause: string; evidence: string } };
const controlTypes: Record<string,string> = { MANUAL_DISABLE:'手动暂停投放', MANUAL_ENABLE:'恢复投放', MANUAL_FINISH:'结束计划', QC_ABNORMAL_PAUSE:'平台异常暂停', AUTOMATIC_DISABLE:'系统自动暂停投放', AUTOMATIC_ENABLE:'系统自动恢复投放' };

// Read all confirmed executions before choosing the latest control action. A partial
// history cannot establish whether a later pause or restore superseded an old one.
export async function loadPauseAutomation(planId: string, readPage: (params: Record<string,unknown>, signal?: AbortSignal) => Promise<HistoryPage>, signal?: AbortSignal) {
  const records = new Map<string,OptimizationEvent>();
  let total: number | undefined;
  for (let pageNum = 1; pageNum <= 5; pageNum++) {
    signal?.throwIfAborted();
    const page = await readPage({ planId, status:'SUCCEEDED', pageNum, pageSize:100 },signal);
    signal?.throwIfAborted();
    if (!Array.isArray(page.records) || !Number.isSafeInteger(page.total) || page.total < 0) throw new Error('暂停执行记录响应不完整');
    if (total !== undefined && total !== page.total) return { records:[...records.values()], complete:false };
    total = page.total;
    for (const event of page.records) records.set(event.id,event);
    if (records.size === total) return { records:[...records.values()], complete:true };
    if (records.size > total) break;
    if (page.records.length < 100) break;
  }
  return { records:[...records.values()], complete:false };
}
export function automaticPauseActions(plan: PausePlan, events: readonly OptimizationEvent[]): PauseAction[] {
  return events.flatMap(event => {
    if (event.planId !== plan.planId || event.status !== 'SUCCEEDED' || !['PAUSE','ENABLE'].includes(event.kind || '')) return [];
    if (event.proposal?.kind && event.proposal.kind !== event.kind) return [];
    const qcIds = [event.actual?.qcId,event.report?.plan_id].filter(value=>value !== undefined && value !== null && value !== '');
    if (qcIds.some(id=>String(id) !== plan.qcPlanId)) return [];
    const pause = event.kind === 'PAUSE';
    if (event.actual?.status && event.actual.status !== (pause ? 'PAUSED' : 'DELIVERING')) return [];
    const cause = event.proposal?.cause || '';
    const fallback = pause ? cause === 'DAILY_LIMIT' ? '当日消耗达到授权限额' : cause === 'LOSS' ? '估算亏损达到授权止损限额' : '系统自动暂停投放' : '系统自动恢复投放';
    return [{ id:`optimization:${event.id}`, planId:event.planId, type:pause ? 'AUTOMATIC_DISABLE' : 'AUTOMATIC_ENABLE', summary:event.title?.trim() || fallback,
      snapshotTs:event.finishedAt || '', open:false, automation:{ eventId:event.id, cause, evidence:event.evidence?.trim() || '' } }];
  });
}
export function automaticPauseAdvice(cause: string) {
  if (cause === 'DAILY_LIMIT') return '建议先核对当日消耗和授权限额，再决定是否恢复；恢复后若仍达到限额，可能再次触发自动暂停。';
  if (cause === 'LOSS') return '建议先核对商品成本、退款预留和投放表现，调整投放目标或素材后，再决定是否恢复。';
  return '建议先查看自动调控记录，核对触发原因和投放设置后，再决定是否恢复。';
}
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
export function pauseHistory(plan: PausePlan, actions: readonly PauseAction[]) {
  const unique = new Map<string,PauseAction>();
  for (const action of actions) if (action?.planId === plan.planId && controlTypes[action.type] && !unique.has(action.id)) unique.set(action.id,action);
  return [...unique.values()].sort((a,b)=>(pauseTimestamp(a.snapshotTs)||0)-(pauseTimestamp(b.snapshotTs)||0));
}
export function currentPause(plan: PausePlan, actions: readonly PauseAction[], now = Date.now()) {
  const history = pauseHistory(plan,actions);
  if (history.length > 1 && history.some(action=>!Number.isFinite(pauseTimestamp(action.snapshotTs)))) return undefined;
  const current = history.at(-1);
  if (!current || !['MANUAL_DISABLE','QC_ABNORMAL_PAUSE','AUTOMATIC_DISABLE'].includes(current.type)) return undefined;
  if (current.type === 'QC_ABNORMAL_PAUSE' && !current.open) return undefined;
  const timestamp = pauseTimestamp(current.snapshotTs);
  if (Number.isFinite(timestamp) && timestamp > now) return undefined;
  const text = current.summary?.trim();
  const reason = text && text !== current.type ? text.replace(/^(暂停投放|暂停原因)[：:]\s*/, '').trim() : '';
  return { action:current, timestamp, reason, manual:current.type==='MANUAL_DISABLE', automatic:current.type==='AUTOMATIC_DISABLE', selected:current.type==='MANUAL_DISABLE' ? PLAN_PAUSE_REASONS.find(option=>option === reason) : undefined };
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
