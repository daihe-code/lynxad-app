interface GenerationDraft {
  products: { qcProductId: string }[];
  generationSelection?: string[] | null;
  generationPendingProductIds?: string[];
  generationStatus: string;
  generationProductIds: string[];
  materials: { productId?: string; status?: string }[];
  generationAdditions?: { productId: string; status: string }[];
}

interface SelectionState {
  productSelectionLocked?: boolean;
  generationStatus: string;
  materials: { status: string }[];
  generationAdditions?: { status: string }[];
  materialEdit?: { status: string } | null;
  generationCounts?: unknown[];
}
const activeGeneration = new Set(['QUEUED','SUBMITTING','SENDING','RUNNING','IMPORTING','REVISING','UNCERTAIN']);
export function activeGenerationAddition<T extends { status: string }>(state: { generationAdditions?: T[] }): T | undefined {
  return state.generationAdditions?.slice().reverse().find(addition => activeGeneration.has(addition.status));
}
export function batchGenerationRunning(state: SelectionState, submitting = false): boolean {
  return !!activeGenerationAddition(state) || (submitting || activeGeneration.has(state.generationStatus)) && state.generationStatus !== 'REVISING'
    && !(state.generationCounts?.length === 1 && state.materials.length > 0);
}
export function productSelectionLocked(state?: SelectionState): boolean {
  return !!state && (state.productSelectionLocked === true || activeGeneration.has(state.generationStatus)
    || state.materials.some(material => material.status === 'PROCESSING')
    || !!state.generationAdditions?.some(addition => activeGeneration.has(addition.status))
    || !!state.materialEdit && !['SUCCEEDED','FAILED'].includes(state.materialEdit.status));
}

export function hasGenerationDecision(state: GenerationDraft): boolean {
  if (state.generationPendingProductIds?.length) return false;
  return Array.isArray(state.generationSelection) || state.generationStatus !== 'NONE'
    || state.generationProductIds.length > 0 || state.materials.length > 0;
}

/** A previous visit or an empty generation choice is not evidence of material preparation. */
export function generationPromptProductIds(state: GenerationDraft): string[] {
  const prepared = new Set([
    ...state.materials.flatMap(m => m.productId ? [m.productId] : []),
    ...(state.generationSelection || []), ...state.generationProductIds,
    ...(state.generationAdditions || []).map(a => a.productId),
  ]);
  return state.products.filter(p => !prepared.has(p.qcProductId)).map(p => p.qcProductId);
}

export function shouldPromptGeneration(state: GenerationDraft): boolean {
  if(activeGeneration.has(state.generationStatus) || activeGenerationAddition(state)
    || state.materials.some(m => m.status === 'PROCESSING'))return false;
  return generationPromptProductIds(state).length > 0;
}

export function selectedGenerationProducts(state: GenerationDraft): string[] {
  return generationPromptProductIds(state);
}
