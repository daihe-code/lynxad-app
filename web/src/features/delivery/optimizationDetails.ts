import type { OptimizationDetails } from './optimizationApi';
export function optimizationDetailState(data: OptimizationDetails, planStatus?: string, accountStatus?: string, runningTasks = 0) {
  if (accountStatus && accountStatus !== 'ACTIVE') return { label: '授权失效', tone: 'warning', reason: '广告账户授权不可用，无法确认最新投放与消耗状态。' };
  if (data.pending.length) return { label: '正在核实', tone: 'warning', reason: '已提交的操作仍在核实，结果未明前不会重复提交。' };
  if (!data.view.policy.enabled) return { label: '已关闭', tone: 'muted', reason: '已停止新的经营判断，历史记录保留。' };
  if (planStatus === 'PAUSED') return runningTasks ? { label: '部分生效', tone: 'warning', reason: '主计划已暂停，独立调控任务仍需核对。' } : { label: '随计划暂停', tone: 'muted', reason: '计划已暂停，不会自动恢复投放。' };
  if (['FINISHED', 'COMPLETED', 'TERMINATED', 'DELETED'].includes(planStatus || '')) return { label: '已结束', tone: 'muted', reason: '计划已结束，历史工作记录仍可查看。' };
  if (data.view.holdReason) return { label: '需核对规则', tone: 'warning', reason: data.view.holdReason };
  if (data.view.error) return { label: '检查受限', tone: 'warning', reason: data.view.error };
  if (!data.view.result && !data.view.lastSuccess) return { label: '等待数据', tone: 'muted', reason: '规则已开启，正在积累可用于判断的数据。' };
  if (data.view.stale) return { label: '检查受限', tone: 'warning', reason: '等待最新投放数据，历史结果不作为实时判断。' };
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
