import type { OptimizationDetails, OptimizationResumePreview } from './optimizationApi';
import type { InsightLog } from './planInsightsApi';
import { pauseTimestamp } from './planPausedResult.ts';
export function isOptimizationPlanEnded(group?: string) {
  return ['FINISHED', 'COMPLETED', 'TERMINATED', 'DELETED'].includes(group || '');
}

export function optimizationDetailState(data: OptimizationDetails, planStatus?: string, accountStatus?: string, runningTasks = 0) {
  if (isOptimizationPlanEnded(planStatus) || data.view.holdReason === 'PLAN_ENDED') return { label: '已结束', tone: 'muted', reason: '计划已结束，历史工作记录仍可查看。' };
  if (!data.view.policy.enabled && data.view.policy.rules) return { label: '已暂停', tone: 'warning', reason: '盯盘优化已暂停，消耗保护当前不生效。' };
  if (accountStatus && accountStatus !== 'ACTIVE') return { label: '授权失效', tone: 'warning', reason: '广告账户授权不可用，无法确认最新投放与消耗状态。' };
  if (data.pending.length) return { label: '正在核实', tone: 'warning', reason: '已提交的操作仍在核实，结果未明前不会重复提交。' };
  if (!data.view.policy.enabled) return { label: '已关闭', tone: 'muted', reason: '已停止新的经营判断，历史记录保留。' };
  if (data.view.holdReason === 'AUTHORIZATION_REQUIRED') return { label: '待核对授权', tone: 'warning', reason: '请确认广告账户授权，再核对并恢复本计划的规则。' };
  if (data.view.holdReason === 'SERVICE_EXPIRED') return { label: '服务到期', tone: 'warning', reason: '恢复服务资格后，请核对并重新启用本计划的规则。' };
  if (data.view.holdReason === 'REVISION_REQUIRED' || data.view.pendingRules) return { label: '规则待生效', tone: 'warning', reason: '已保存新规则，核对当天消耗后生效。' };
  if (planStatus === 'PAUSED') return runningTasks ? { label: '部分生效', tone: 'warning', reason: '主计划已暂停，独立调控任务仍需核对。' } : { label: '等待投放', tone: 'muted', reason: '规则已开启，广告恢复投放后继续经营判断。' };
  if (data.view.holdReason) return { label: '需核对规则', tone: 'warning', reason: data.view.holdReason };
  if (data.view.error) return { label: '检查受限', tone: 'warning', reason: data.view.error };
  if (!data.view.result && !data.view.lastSuccess) return { label: '等待数据', tone: 'muted', reason: '规则已开启，正在积累可用于判断的数据。' };
  if (data.view.stale) return { label: '检查受限', tone: 'warning', reason: '等待最新投放数据，历史结果不作为实时判断。' };
  if (data.view.result?.state === 'NO_DELIVERY') return { label: '持续无曝光', tone: 'warning', reason: '' };
  if (data.view.result?.state === 'READY' && data.view.result.complete === false) return { label: '部分受限', tone: 'warning', reason: '当天消耗待核实，暂缓消耗保护与新增投入判断。' };
  if (data.view.result?.daily_spend != null && data.view.result.daily_net_revenue == null) return { label: '部分受限', tone: 'warning', reason: '净成交数据未齐，基于可用的消耗数据继续检查。' };
  if (data.view.result?.strategy_approved === false) return { label: '部分受限', tone: 'warning', reason: '消耗保护继续运行，经营判断策略仍在校准。' };
  if (data.view.result?.state === 'READY' && data.view.result?.business_ready === false) return { label: '部分受限', tone: 'warning', reason: '消耗检查继续运行，部分经营判断的数据仍待核实。' };
  if (data.view.result?.state !== 'READY') return { label: '等待数据', tone: 'muted', reason: '正在积累完整小时和成交回补数据，暂不贸然调整。' };
  return { label: '盯盘中', tone: 'success', reason: '' };
}
export function monitoringDuration(seconds: number) {
  if (!seconds) return '—';
  const minutes = Math.floor(seconds / 60), hours = Math.floor(minutes / 60), days = Math.floor(hours / 24);
  return days ? `${days}天${hours % 24}小时` : hours ? `${hours}小时${minutes % 60}分` : `${minutes}分钟`;
}

export function isPausedPlanResume(preview?: OptimizationResumePreview) {
  return preview?.planStatus === 'PAUSED' && preview.outcome === 'PLAN_NOT_DELIVERING';
}

const pauseOperations = ['暂停投放', '暂停计划', '暂停广告计划', '手动暂停', '手动暂停投放'];
const resumeOperations = ['恢复投放', '恢复计划', '启用计划', '启用广告计划', '手动恢复投放'];

export function manualPlanPauseLog(records: InsightLog[], checkedAt: string) {
  const cutoff = pauseTimestamp(checkedAt);
  if (!Number.isFinite(cutoff)) return undefined;
  const changes = records.flatMap(record => {
    const operation = record.operation.trim();
    const paused = pauseOperations.includes(operation);
    return paused || resumeOperations.includes(operation) ? [{ record, paused, timestamp: pauseTimestamp(record.time) }] : [];
  });
  if (changes.some(change => !Number.isFinite(change.timestamp) || change.timestamp > cutoff)) return undefined;
  changes.sort((a, b) => b.timestamp - a.timestamp || Number(a.paused) - Number(b.paused));
  return changes[0]?.paused ? changes[0].record : undefined;
}
