import { planStatusDisplay } from './planStatus.ts';

interface PlanActionState {
  status: string;
  qcStatus?: string | null;
  scheduleType?: string | null;
  endDate?: string | null;
}

export function restoreUnavailableReason(plan: PlanActionState, today: string): string {
  const state = planStatusDisplay(plan.status, plan.qcStatus);
  if ((plan.scheduleType !== 'FROM_NOW' && plan.endDate && plan.endDate.slice(0, 10) < today) || state.group === 'COMPLETED') {
    return '该计划投放排期已结束，请先调整投放时间，再恢复投放。';
  }
  if (state.group !== 'PAUSED') return `该计划当前为${state.label}，请刷新列表后再操作。`;
  return '';
}

export function restoreResultFeedback(plan: PlanActionState): { type: 'success' | 'warning'; message: string } {
  const state = planStatusDisplay(plan.status, plan.qcStatus);
  if (plan.qcStatus && state.group === 'DELIVERING') return { type: 'success', message: '千川已确认，计划已恢复投放' };
  if (!plan.qcStatus) return { type: 'warning', message: '恢复指令已提交，暂未获取千川最新状态，请刷新核查。' };
  return { type: 'warning', message: `恢复指令已提交，千川当前状态为“${state.label}”，请核查投放结果。` };
}
