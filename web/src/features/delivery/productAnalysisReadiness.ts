import type { CreationProduct, CreationState, ProductBasicAnalysis } from './creationApi';

export function productAnalysisInput(products: CreationProduct[]): string {
  return JSON.stringify(products.map(p => [p.qcProductId, p.name, p.category || '', p.price ?? null, p.userPrice ?? null,
    p.priceInfo?.minPrice ?? null, p.priceInfo?.maxPrice ?? null, p.stock ?? null, p.imageUrl || '', p.unavailableReasons || []]));
}

export function isCurrentCreationPoll(current: Pick<CreationState, 'planId' | 'revision'> | undefined,
  incoming: Pick<CreationState, 'planId' | 'revision'>, requestedPlanId: string): boolean {
  return !!current && current.planId === requestedPlanId && incoming.planId === requestedPlanId
    && incoming.revision >= current.revision;
}

export function canContinueProductAnalysis(basic: ProductBasicAnalysis | undefined, aiStatus: string): boolean {
  // Older API deployments have no basic checks; retain their existing success requirement.
  return basic ? basic.canContinue && basic.status === 'PASSED' : aiStatus === 'SUCCEEDED';
}

export function productAnalysisProgress(basic: ProductBasicAnalysis | undefined, status: string, elapsedMs = 0): string | undefined {
  if (!basic) return;
  if (!basic.canContinue) return '商品基础检查未通过，请先处理下方问题';
  if (status === 'QUEUED' || status === 'RUNNING') return elapsedMs >= 15000
    ? 'AI 分析耗时较长，商品基础检查已通过，可先设置投放目标，结果将后台补充'
    : '商品基础检查已通过，AI 正在后台分析，可先设置投放目标';
  if (status === 'FAILED') return 'AI 分析暂时不可用，商品基础检查已通过，可继续设置投放目标或稍后重新分析';
}
