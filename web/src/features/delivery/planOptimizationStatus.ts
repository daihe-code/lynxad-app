import type { PlanOptimizationStatus } from './optimizationApi';
import { planStatusDisplay } from './planStatus.ts';

export function planOptimizationPresentation(plan: { qcPlanId?: string | null; status: string; qcStatus?: string | null }, state: PlanOptimizationStatus | undefined, loading = false, failed = false) {
  const status = planStatusDisplay(plan.status, plan.qcStatus);
  if (!plan.qcPlanId || ['DRAFT', 'DELETED'].includes(status.group)) return { label: '—', tone: 'muted', action: '' };
  if (loading) return { label: '加载中', tone: 'muted', action: '' };
  if (failed) return { label: '加载失败', tone: 'warning', action: '重试' };
  const action = state ? '查看盯盘详情' : '设置盯盘规则';
  if (['FINISHED', 'COMPLETED', 'TERMINATED'].includes(status.group)) return { label: '已结束', tone: 'muted', action: state ? action : '' };
  if (!state) return { label: '待配置', tone: 'warning', action };
  if (!state.enabled) return { label: '已关闭', tone: 'muted', action };
  if (state.failed) return { label: '检测受限', tone: 'warning', action };
  if (state.holdReason) return { label: '需核对配置', tone: 'warning', action };
  if (status.detail === 'TIME_NO_REACH' || ['PENDING_AUDIT', 'AUDIT', 'REAUDIT'].includes(status.group) || plan.qcStatus === 'PRE_ONLINE') return { label: '待投放生效', tone: 'muted', action };
  if (status.group === 'PAUSED') return { label: '观察暂停', tone: 'muted', action };
  if (status.group !== 'DELIVERING') return { label: '投放受限', tone: 'warning', action };
  if (state.stale || state.state !== 'READY') return { label: '等待数据', tone: 'muted', action };
  return { label: state.mode === 'AUTO_LIMITED' ? '优化中' : '观察中', tone: 'success', action };
}
