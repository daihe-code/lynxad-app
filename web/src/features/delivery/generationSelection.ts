interface GenerationDraft {
  products: { qcProductId: string }[];
  generationSelection?: string[] | null;
  generationPendingProductIds?: string[];
  generationStatus: string;
  generationProductIds: string[];
  materials: unknown[];
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
export function batchGenerationRunning(state: SelectionState, submitting = false): boolean {
  return (submitting || activeGeneration.has(state.generationStatus)) && state.generationStatus !== 'REVISING'
    && !state.generationAdditions?.some(addition => activeGeneration.has(addition.status))
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

export function shouldPromptGeneration(state: GenerationDraft, resumingMaterials = false): boolean {
  if(['QUEUED','SUBMITTING','RUNNING','IMPORTING','REVISING','UNCERTAIN'].includes(state.generationStatus))return false;
  return !!state.generationPendingProductIds?.length || !resumingMaterials && !hasGenerationDecision(state);
}

export function selectedGenerationProducts(state: GenerationDraft): string[] {
  const selected = state.generationPendingProductIds?.length ? state.generationPendingProductIds : state.generationSelection ?? (state.generationProductIds.length ? state.generationProductIds : state.products.map(p => p.qcProductId));
  return state.products.filter(p => selected.includes(p.qcProductId)).map(p => p.qcProductId);
}
