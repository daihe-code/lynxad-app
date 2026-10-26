import type { InboxMessage } from '../tenant/inboxApi';
import type { CreationState } from './creationApi';

export function generationNoticeTarget(state: CreationState, message: InboxMessage) {
  const result = message.result;
  if (result ? result.creationPlanId !== state.planId : ![state.planId, state.contentEdit?.targetPlanId].includes(message.bizId)) return null;
  const operation = state.generationRetry?.requestId || state.regeneration?.requestId || 'batch';
  const current = !!result?.jobId && result.jobId === state.engineJobId && (!result.operationId || result.operationId === operation);
  const material = result?.materialId ? state.materials.find(m => m.id === result.materialId) : current ? state.materials.find(m => m.engineJobId === result.jobId) : undefined;
  const issue = current ? state.generationIssues?.[0] : undefined;
  const reasons = current ? [...new Set([...(state.generationIssues || []).map(i => i.message), ...state.materials.filter(m => m.status === 'FAILED' && m.engineJobId === result?.jobId).map(m => m.error).filter((value): value is string => !!value)])] : [];
  const exactMaterial = !!result?.materialId && !!material;
  return { materialId: material?.id, productId: exactMaterial ? material.productId : issue?.productId || material?.productId,
    type: exactMaterial ? material.type : issue?.type || material?.type || result?.materialType,
    reasons, note: !result?.jobId ? '这条历史消息未记录具体任务，以下展示本计划当前素材结果。'
      : !current && !material ? '此消息对应较早的生成任务，当前计划已有更新；以下展示当前素材，原任务结果以本条消息为准。'
      : result.outcome === 'FAILED' && !reasons.length ? '当前未保存具体失败原因，请核查原生成任务；原有素材保持不变。'
      : '生成结果与登记、检查和千川同步分别处理，素材当前状态请在下方查看。' };
}
