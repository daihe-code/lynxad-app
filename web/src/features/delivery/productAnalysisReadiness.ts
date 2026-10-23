import type { CreationProduct, CreationState, ProductAnalysis, ProductBasicAnalysis } from './creationApi';

export function productAnalysisPresentation(analysis: ProductAnalysis | undefined, status: string, products: CreationProduct[]): 'ready' | 'pending' | 'failed' | 'outdated' | 'empty' {
  if (status === 'QUEUED' || status === 'RUNNING') return 'pending';
  if (status === 'FAILED' || analysis?.processing?.inference_error || analysis?.processing?.inference_status === 'FAILED') return 'failed';
  if (!analysis || status !== 'SUCCEEDED') return 'empty';
  const report = analysis.selection_analysis;
  if (report?.schema_version === '1.0' && report.product_analyses.length === products.length
    && products.every(product => report.product_analyses.some(row => row.product_id === product.qcProductId))) return 'ready';
  // Preserve real model output from the former preparation contract, but do not
  // disguise old dimension-only reports or fallback rows as the new report.
  const rows = analysis.preparation?.products;
  if (rows?.length === products.length && products.every(product => rows.some(row => row.product_id === product.qcProductId && row.source === 'ai_suggestion'))) return 'ready';
  return 'outdated';
}

export function productAnalysisInput(products: CreationProduct[]): string {
  return JSON.stringify(products.map(p => [p.qcProductId, p.name, p.category || '', p.price ?? null, p.userPrice ?? null,
    p.priceInfo?.minPrice ?? null, p.priceInfo?.maxPrice ?? null, p.stock ?? null, p.imageUrl || '', p.unavailableReasons || []]));
}

export function isCurrentCreationPoll(current: Pick<CreationState, 'planId' | 'revision'> | undefined,
  incoming: Pick<CreationState, 'planId' | 'revision'>, requestedPlanId: string): boolean {
  return !!current && current.planId === requestedPlanId && incoming.planId === requestedPlanId
    && incoming.revision >= current.revision;
}

export function canContinueProductAnalysis(basic: ProductBasicAnalysis | undefined, aiStatus: string,
  analysis: ProductAnalysis | undefined, products: CreationProduct[]): boolean {
  return products.length > 0 && (!basic || basic.canContinue && basic.status === 'PASSED')
    && productAnalysisPresentation(analysis, aiStatus, products) === 'ready';
}
