import type { CreationMaterial } from './creationApi';

export const isGeneratedMaterial = (m: CreationMaterial) => m.source === 'LYNX' || !!m.engineMaterialId;
export const materialSourceLabel = (m: CreationMaterial) => m.deliverySource === 'product_reference' ? '商品原图版' : isGeneratedMaterial(m) ? '智能生成' : '人工上传';

export function materialSelectionAccepted(submitted: boolean, busy: boolean, error?: string) {
  // A successful add may reuse the existing row and keep the same revision.
  return submitted && !busy && !error;
}
