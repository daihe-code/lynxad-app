import type { CreationMaterial, CreationProduct } from './creationApi';
import type { PlanVo } from './deliveryApi';
import type { PlanMaterial } from './planInsightsApi';
import type { StatisticsRange } from './statisticsRange';
import { shanghaiToday, shiftDay } from './statisticsRange.ts';

const date = (value?: string | null): value is string => !!value && /^\d{4}-\d{2}-\d{2}$/.test(value)
  && Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0,10) === value;

/** Include historical delivery days, rather than querying only today for completed plans. */
export function planMaterialRange(plan: Pick<PlanVo,'startDate'|'endDate'|'createdAt'> & {source?:string}, today = shanghaiToday()): StatisticsRange {
  const end = date(plan.endDate) && plan.endDate <= today ? plan.endDate : today;
  const earliest = shiftDay(end,-179);
  // Imported rows record the import time as createdAt, not the actual delivery start.
  const created = plan.source === 'EXTERNAL' ? undefined : plan.createdAt?.slice(0,10);
  const start = date(plan.startDate) ? plan.startDate : date(created) ? created : earliest;
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
  const titles=(material.titles || []).filter(readableTitle);
  if (!titles.length && readableTitle(material.text)) titles.push(material.text!);
  const name=planMaterialName(material);
  return { id: `${material.type}:${material.id}`, productId: material.products[0]?.id || '',
    type: material.type as CreationMaterial['type'],title:name,displayName:name,
    text:material.text,status:'READY',previewUrl:material.type === 'CAROUSEL' ? material.musicUrl : material.previewUrl,
    posterUrl:material.posterUrl,imageUrls:material.imageUrls,titles };
}

const readableTitle = (text?: string): text is string => !!text?.trim() && !/\.(mp4|mov|webm|m4v|avi|jpe?g|png|webp|gif|mp3|wav|m4a|aac)$/i.test(text.trim());
export function planMaterialName(material: PlanMaterial): string {
  return (readableTitle(material.name) ? material.name : '') || material.titles?.find(readableTitle)
    || (readableTitle(material.text) ? material.text.split('\n')[0] : '') || material.products[0]?.name || `素材 ${material.id}`;
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
