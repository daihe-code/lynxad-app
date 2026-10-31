import { scheduleInputError } from './inputValidation.ts';
import type { CreationState } from './creationApi';

const DEFAULT_CUSTOM_DAYS = 14;

export function creationScheduleDefaults(today: string, startDate?: string | null, endDate?: string | null) {
  const start = startDate || today;
  if (endDate) return { start, end: endDate };
  const end = new Date(`${start}T00:00:00Z`);
  // Both boundary dates are delivery days: a fourteen-day range ends thirteen days after it starts.
  end.setUTCDate(end.getUTCDate() + DEFAULT_CUSTOM_DAYS - 1);
  return { start, end: end.toISOString().slice(0, 10) };
}

export function canRefreshCreationSchedule(state?: Pick<CreationState,'qcPlanId'|'publishStatus'|'contentEdit'>) {
  return !state || !state.qcPlanId && !state.contentEdit && ['NONE','FAILED'].includes(state.publishStatus);
}

export function resumeCreationSchedule(today: string, startDate?: string | null, endDate?: string | null, enabled = true) {
  const saved = creationScheduleDefaults(today, startDate, endDate);
  if (!enabled || saved.start >= today || scheduleInputError('custom', saved.start, saved.end, saved.start)) return saved;
  if (saved.end >= today) return { start: today, end: saved.end };
  const days = (Date.parse(`${saved.end}T00:00:00Z`) - Date.parse(`${saved.start}T00:00:00Z`)) / 86400000;
  const end = new Date(`${today}T00:00:00Z`); end.setUTCDate(end.getUTCDate() + days);
  return { start: today, end: end.toISOString().slice(0,10) };
}

export function resumeDraftDates<T extends { duration: string; start: string; end: string }>(inputs: T, today: string, enabled = true): T {
  return inputs.duration === 'custom' && enabled && !scheduleInputError('custom',inputs.start,inputs.end,inputs.start)
    ? { ...inputs, ...resumeCreationSchedule(today, inputs.start, inputs.end) } : inputs;
}

/** Offer an explicit repair for an unpublished draft without extending its end date. */
export function creationScheduleRecovery(duration: string, start: string, end: string, today: string) {
  if (duration !== 'custom' || start >= today
    || scheduleInputError('custom', start, end, start)
    || scheduleInputError('custom', today, end, today)) return;
  return { start: today, end };
}
