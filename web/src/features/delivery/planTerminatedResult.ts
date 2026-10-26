import type { InsightMetrics } from './planInsightsApi';
import { planStatusDisplay } from './planStatus.ts';
import { currentPause, pauseHistory, pauseTimestamp, pauseStatisticsRange, type PauseAction } from './planPausedResult.ts';
import { shiftDay } from './statisticsRange.ts';

type TerminatedPlan = { planId:string; status:string; qcPlanId?:string; qcStatus?:string|null; startDate?:string|null; createdAt?:string|null };
export function hasPlanTerminatedResult(plan?: Pick<TerminatedPlan,'status'|'qcPlanId'|'qcStatus'> | null) {
  if (!plan?.qcPlanId || plan.status === 'DELETED') return false;
  return planStatusDisplay(plan.status,plan.qcStatus).group === 'TERMINATED' || plan.status === 'TERMINATED' && !plan.qcStatus;
}
export function terminationRecord(plan: TerminatedPlan, actions: readonly PauseAction[], now = Date.now()) {
  const history = pauseHistory(plan,actions);
  if (history.length > 1 && history.some(action=>!Number.isFinite(pauseTimestamp(action.snapshotTs)))) return undefined;
  const action = history.at(-1);
  if (action?.type !== 'MANUAL_FINISH') return undefined;
  const timestamp = pauseTimestamp(action.snapshotTs);
  if (timestamp > now) return undefined;
  const summary = action.summary?.trim();
  const reason = summary && !['MANUAL_FINISH','FINISH'].includes(summary) ? summary.replace(/^(终止原因|结束原因|终止计划|结束计划)[：:]\s*/,'').trim() : '';
  const before = history.filter(row=>row.id !== action.id && pauseTimestamp(row.snapshotTs) < timestamp);
  const pause = Number.isFinite(timestamp) ? currentPause(plan,before,timestamp) : undefined;
  return { action,timestamp,reason,pausedMinutes:pause && Number.isFinite(pause.timestamp) ? Math.floor((timestamp-pause.timestamp)/60000) : undefined };
}
// Missing termination time affects the time label, not the ability to read history.
// The query date remains explicitly separate from the unknown termination date.
export function terminatedStatisticsRange(plan: TerminatedPlan, timestamp: number, now = Date.now()) {
  const endKnown = Number.isFinite(timestamp) && timestamp <= now;
  const period = pauseStatisticsRange(plan,endKnown ? timestamp : now,now)!;
  return { ...period, range:period.startKnown ? period.range : [shiftDay(period.range[1],-179),period.range[1]] as [string,string], endKnown, cumulative:period.startKnown && !period.clipped };
}
export function terminatedMetricValues(metrics: InsightMetrics = {}) {
  const valid = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value >= 0;
  return { cost:valid(metrics.cost) ? `¥${metrics.cost.toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})}` : '—', orders:valid(metrics.orders) ? metrics.orders.toLocaleString('zh-CN') : '—' };
}
export function terminatedPauseDuration(minutes?: number) {
  if (minutes === undefined || !Number.isFinite(minutes) || minutes < 0) return '';
  if (minutes < 1) return '终止前已暂停不足1分钟。';
  const days = Math.floor(minutes/1440),hours = Math.floor(minutes%1440/60),rest = minutes%60;
  return `终止前已暂停 ${days ? `${days} 天 ` : ''}${hours ? `${hours} 小时 ` : ''}${rest || !hours ? `${rest} 分` : ''}。`;
}
