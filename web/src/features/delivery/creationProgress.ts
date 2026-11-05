import type { CreationState } from './creationApi';

type ProgressState = Pick<CreationState, 'step' | 'reachedStep' | 'budget' | 'generationSelection' | 'generationStatus' | 'materials' | 'qcPlanId' | 'publishStatus'>;
export function creationReachedStep(state?: ProgressState, currentStep = 1): number {
  if (!state) return currentStep;
  if (state.qcPlanId || state.publishStatus === 'CREATED') return 4;
  if (state.reachedStep != null) return Math.max(1, Math.min(4, state.reachedStep));
  return Math.max(state.step, state.budget != null || state.generationSelection != null || state.generationStatus !== 'NONE' || state.materials.length ? 3 : 1);
}

/** Reached steps describe navigation history; goal inputs are validated separately. */
export function validReachedStep(reached: number, productsChanged = false): number {
  return productsChanged ? 1 : reached;
}
