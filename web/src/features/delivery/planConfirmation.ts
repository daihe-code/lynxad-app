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

type ActivationVerb = '恢复' | '转投';

export function restoreResultFeedback(plan: PlanActionState, verb: ActivationVerb = '恢复'): { type: 'success' | 'warning'; message: string } {
  const state = planStatusDisplay(plan.status, plan.qcStatus);
  if (verb === '转投') {
    if (plan.qcStatus && state.group === 'DELIVERING') return { type: 'success', message: '转投成功，关联计划已暂停' };
    return { type: 'warning', message: '转投指令已提交，请核查最新投放状态。' };
  }
  if (plan.qcStatus && state.group === 'DELIVERING') return { type: 'success', message: '千川已确认，计划已恢复投放' };
  if (!plan.qcStatus) return { type: 'warning', message: '恢复指令已提交，暂未获取千川最新状态，请刷新核查。' };
  return { type: 'warning', message: `恢复指令已提交，千川当前状态为“${state.label}”，请核查投放结果。` };
}

export function transferredRestoreFeedback(plan: PlanActionState, result?: { status: string; message?: string | null }, verb: ActivationVerb = '恢复') {
  if (result?.status === 'RUNNING' || result?.status === 'UNCERTAIN') {
    return { pending: true, type: 'warning' as const, message: result.message || `${verb}结果待确认，请稍后查询。` };
  }
  if (result?.status === 'FAILED') return { pending: false, type: 'warning' as const, message: result.message || `计划${verb}失败。` };
  if (plan.qcStatus === 'TRANSFER_ARCHIVED') return { pending: false, type: 'warning' as const, message: `未查到已完成的${verb}，计划仍为暂停。` };
  return { pending: false, ...restoreResultFeedback(plan, verb) };
}
