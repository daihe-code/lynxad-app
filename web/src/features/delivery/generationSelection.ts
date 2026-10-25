interface GenerationDraft {
  products: { qcProductId: string }[];
  generationSelection?: string[] | null;
  generationStatus: string;
  generationProductIds: string[];
  materials: unknown[];
}

export function hasGenerationDecision(state: GenerationDraft): boolean {
  return Array.isArray(state.generationSelection) || state.generationStatus !== 'NONE'
    || state.generationProductIds.length > 0 || state.materials.length > 0;
}

export function shouldPromptGeneration(state: GenerationDraft, resumingMaterials = false): boolean {
  return !resumingMaterials && !hasGenerationDecision(state);
}

export function selectedGenerationProducts(state: GenerationDraft): string[] {
  const selected = state.generationSelection ?? (state.generationProductIds.length ? state.generationProductIds : state.products.map(p => p.qcProductId));
  return state.products.filter(p => selected.includes(p.qcProductId)).map(p => p.qcProductId);
}
