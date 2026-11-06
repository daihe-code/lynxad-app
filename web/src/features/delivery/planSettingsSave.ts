import { canRebasePlan, changedPlanInputs, rebasePlanInputs, type PlanEditInputs, type PlanEditSnapshot } from './planEditConflict.ts';

export interface PlanSettingsPatch {
  name?: string; budget?: number; roi2Goal?: number;
  scheduleType?: 'FROM_NOW' | 'DATE_RANGE'; endDate?: string; expectedVersion: string;
}
export type SettingsField = 'name' | 'budget' | 'roi' | 'schedule';
export function planSettingsPatch(base: PlanEditSnapshot, inputs: PlanEditInputs): PlanSettingsPatch {
  const changed = changedPlanInputs(base, inputs);
  return {
    expectedVersion: base.settingsEdit?.version || '',
    ...(changed.name ? {name: inputs.name.trim()} : {}),
    ...(changed.budget ? {budget: Number(inputs.budget)} : {}),
    ...(changed.roi ? {roi2Goal: Number(inputs.roi)} : {}),
    ...(changed.schedule ? {scheduleType: inputs.duration === 'long' ? 'FROM_NOW' : 'DATE_RANGE',
      ...(inputs.duration === 'custom' ? {endDate: inputs.end} : {})} : {}),
  };
}
const hasChanges = (patch: PlanSettingsPatch) => Object.keys(patch).some(key => key !== 'expectedVersion');
const sameNumber = (a?: number | null, b?: number | null) => a == null ? b == null : b != null && Number(a) === Number(b);
const sameSchedule = (a: PlanEditSnapshot, b: PlanEditSnapshot) => {
  const duration = (p: PlanEditSnapshot) => p.scheduleType === 'FROM_NOW' || !p.scheduleType && !p.endDate ? 'long' : 'custom';
  return duration(a) === duration(b) && (duration(a) === 'long' || a.endDate === b.endDate);
};
export function conflictingSettings(base: PlanEditSnapshot, latest: PlanEditSnapshot, inputs: PlanEditInputs): SettingsField[] {
  const before = planSettingsPatch(base, inputs), after = planSettingsPatch(latest, inputs);
  const fields: SettingsField[] = [];
  if (before.name != null && after.name != null && base.name !== latest.name) fields.push('name');
  if (before.budget != null && after.budget != null && !sameNumber(base.budget, latest.budget)) fields.push('budget');
  if (before.roi2Goal != null && after.roi2Goal != null && !sameNumber(base.roi2Goal, latest.roi2Goal)) fields.push('roi');
  if (before.scheduleType && after.scheduleType && !sameSchedule(base, latest)) fields.push('schedule');
  return fields;
}

/** Only a rejected version check proves that no platform write was sent. Never replay an uncertain write. */
export async function savePlanSettingsWithRecovery<T extends PlanEditSnapshot>(base: T, inputs: PlanEditInputs, actions: {
  read: () => Promise<T>;
  write: (patch: PlanSettingsPatch) => Promise<T>;
  rebased: (latest: T, inputs: PlanEditInputs) => void;
}): Promise<{status: 'SAVED' | 'REVIEW' | 'PENDING' | 'RELOAD'; plan: T; conflicts: SettingsField[]}> {
  if (base.settingsEdit && base.settingsEdit.status !== 'IDLE') return {status: 'PENDING', plan: base, conflicts: []};
  try {
    const patch = planSettingsPatch(base, inputs);
    if (!hasChanges(patch)) return {status: 'SAVED', plan: base, conflicts: []};
    if (base.settingsEdit?.version) return {status: 'SAVED', plan: await actions.write(patch), conflicts: []};
  } catch (error) {
    if (Number((error as {code?: number})?.code) !== 1404) throw error;
  }
  const latest = await actions.read();
  if (latest.settingsEdit && latest.settingsEdit.status !== 'IDLE') return {status: 'PENDING', plan: latest, conflicts: []};
  if (!canRebasePlan(base, latest)) return {status: 'RELOAD', plan: latest, conflicts: []};
  const conflicts = conflictingSettings(base, latest, inputs);
  const rebased = rebasePlanInputs(base, latest, inputs);
  actions.rebased(latest, rebased);
  if (conflicts.length) return {status: 'REVIEW', plan: latest, conflicts};
  const patch = planSettingsPatch(latest, rebased);
  // The first attempt may have been completed by another window. Do not send the same value again.
  return {status: 'SAVED', plan: hasChanges(patch) ? await actions.write(patch) : latest, conflicts: []};
}
