const DEFAULT_CUSTOM_DAYS = 14;

export function creationScheduleDefaults(today: string, startDate?: string | null, endDate?: string | null) {
  const start = startDate || today;
  if (endDate) return { start, end: endDate };
  const end = new Date(`${start}T00:00:00Z`);
  // Both boundary dates are delivery days: a fourteen-day range ends thirteen days after it starts.
  end.setUTCDate(end.getUTCDate() + DEFAULT_CUSTOM_DAYS - 1);
  return { start, end: end.toISOString().slice(0, 10) };
}
