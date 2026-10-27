import type { CreationMaterial, CreationProduct } from './creationApi';
import type { PlanVo } from './deliveryApi';
import type { PlanMaterial } from './planInsightsApi';
import type { StatisticsRange } from './statisticsRange';
import { shanghaiToday, shiftDay } from './statisticsRange.ts';

const date = (value?: string | null): value is string => !!value && /^\d{4}-\d{2}-\d{2}$/.test(value)
  && Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0,10) === value;

/** Include historical delivery days, rather than querying only today for completed plans. */
export function planMaterialRange(plan: Pick<PlanVo,'startDate'|'endDate'|'createdAt'>, today = shanghaiToday()): StatisticsRange {
  const end = date(plan.endDate) && plan.endDate <= today ? plan.endDate : today;
  const earliest = shiftDay(end,-179);
  const start = date(plan.startDate) ? plan.startDate : date(plan.createdAt?.slice(0,10)) ? plan.createdAt!.slice(0,10) : earliest;
  return [start <= end ? start < earliest ? earliest : start : earliest,end];
}

export function visiblePlanMaterials(materials: readonly PlanMaterial[], productId = ''): PlanMaterial[] {
  const unique = new Map<string,PlanMaterial>();
  for (const material of materials) {
    if (material.deleted || !material.id || productId && !material.products.some(product => product.id === productId)) continue;
    unique.set(`${material.type}:${material.id}`,material);
  }
  return [...unique.values()];
}

/** A preview projection only: no engine IDs, generation receipts or editable creation state. */
export function planMaterialPreview(material?: PlanMaterial): CreationMaterial | undefined {
  if (!material || !['VIDEO','IMAGE','CAROUSEL'].includes(material.type)) return undefined;
  return { id: `${material.type}:${material.id}`, productId: material.products[0]?.id || '',
    type: material.type as CreationMaterial['type'],title: material.name,displayName:material.name,
    text:material.text,status:'READY',previewUrl:material.type === 'CAROUSEL' ? material.musicUrl : material.previewUrl,
    posterUrl:material.posterUrl,imageUrls:material.imageUrls,titles:material.text ? [material.text] : [] };
}

export function planMaterialProduct(material: PlanMaterial | undefined, products: readonly CreationProduct[], selected = ''): CreationProduct | undefined {
  if (!material) return undefined;
  const associated = material.products.find(product => product.id === selected) || material.products[0];
  if (!associated) return undefined;
  return products.find(product => product.qcProductId === associated.id) || {
    productId:associated.id,qcProductId:associated.id,name:associated.name,imageUrl:associated.imageUrl,
    price:null,userPrice:null,tags:[],unavailableReasons:[],
  };
}
