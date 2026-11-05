import { DRAFT_TTL_MS, draftKey, type DraftScope, type DraftStorage } from './creationDraftCache.ts';

export type ProductStepView = 'SELECTION' | 'ANALYSIS';
const key = (scope: DraftScope) => 'lynx.creation.product-view.v1:' + draftKey(scope);

/** Presentation only; restoring this preference must not change plan inputs or analysis. */
export function readProductStepView(storage: Pick<DraftStorage, 'getItem'>, scope: DraftScope, now = Date.now()): ProductStepView | undefined {
  if (!scope.planId) return;
  try {
    const value = JSON.parse(storage.getItem(key(scope)) || 'null');
    if (value && ['SELECTION', 'ANALYSIS'].includes(value.view) && Number.isFinite(value.savedAt)
      && value.savedAt <= now + 300000 && now - value.savedAt <= DRAFT_TTL_MS) return value.view;
  } catch { /* Browser storage is optional for presentation recovery. */ }
}

export function rememberProductStepView(storage: Pick<DraftStorage, 'setItem'>, scope: DraftScope, view: ProductStepView, now = Date.now()): void {
  if (!scope.planId) return;
  try { storage.setItem(key(scope), JSON.stringify({ view, savedAt: now })); }
  catch { /* Keep the current view usable when browser storage is unavailable. */ }
}
