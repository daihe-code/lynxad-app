import type { CreationState } from './creationApi';

type ProgressState = Pick<CreationState, 'step' | 'reachedStep' | 'budget' | 'generationSelection' | 'generationStatus' | 'materials' | 'qcPlanId' | 'publishStatus'>;
export function creationReachedStep(state?: ProgressState, currentStep = 1): number {
  if (!state) return currentStep;
  if (state.qcPlanId || state.publishStatus === 'CREATED') return 4;
  if (state.reachedStep != null) return Math.max(1, Math.min(4, state.reachedStep));
  return Math.max(state.step, state.budget != null || state.generationSelection != null || state.generationStatus !== 'NONE' || state.materials.length ? 3 : 1);
}

export function validReachedStep(reached: number, productsChanged = false, goalChanged = false): number {
  return productsChanged ? 1 : goalChanged ? Math.min(reached, 2) : reached;
}

interface GoalInputs { mode: string; budget: string; roi: string; duration: string; start: string; end: string }
type GoalState = Pick<CreationState, 'smartBidType' | 'budget' | 'roi2Goal' | 'scheduleType' | 'startDate' | 'endDate'>;
const sameNumber = (input: string, value?: number) => value == null ? !input.trim() : !!input.trim() && Number(input) === value;
export function creationGoalChanged(state: GoalState | undefined, input: GoalInputs): boolean {
  if (!state) return false;
  return state.smartBidType !== input.mode || !sameNumber(input.budget, state.budget)
    || input.mode === 'CUSTOM' && !sameNumber(input.roi, state.roi2Goal)
    || (state.scheduleType === 'DATE_RANGE' ? 'custom' : 'long') !== input.duration
    || input.duration === 'custom' && (input.start !== state.startDate || input.end !== state.endDate);
}
