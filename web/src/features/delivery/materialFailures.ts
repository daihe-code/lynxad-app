import type { CreationMaterial, CreationState } from './creationApi';

export function materialFailed(material: CreationMaterial): boolean {
  if (material.status === 'READY' && (!material.processing || material.processing.stage === 'READY')) return false;
  const p = material.processing;
  return material.status === 'FAILED' || !!p && [p.generationStatus, p.storageStatus, p.reviewStatus, p.syncStatus].some(s => ['FAILED', 'REJECTED'].includes(s));
}

export function materialFailureReason(material: CreationMaterial, state: CreationState): string {
  const issue = state.generationIssues?.find(i => i.materialId === material.id);
  return material.error || issue?.message || material.processing?.message || (materialFailed(material) ? '素材处理未完成，暂未返回具体原因，可核查处理记录。' : '');
}

export function productHasFailure(state: CreationState, productId: string, type?: string): boolean {
  return activeGenerationIssues(state).some(i => i.productId === productId && (!type || i.type === type))
    || state.materials.some(m => m.productId === productId && (!type || m.type === type) && materialFailed(m));
}

export function generationFailureRows(state: CreationState, productId: string, type: string) {
  return activeGenerationIssues(state).filter(i => i.productId === productId && i.type === type
    && !state.materials.some(m => m.id === i.materialId));
}

export function activeGenerationIssues(state: CreationState) {
  return (state.generationIssues || []).filter(i => !i.materialId || !state.materials.some(m => m.id === i.materialId
    && m.status === 'READY' && (!m.processing || m.processing.stage === 'READY')));
}
