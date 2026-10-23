interface ScheduleSource { scheduleType?: string | null; startDate?: string | null; endDate?: string | null }
const shanghaiToday = () => new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
export function publishedSchedule(plan: ScheduleSource, today = shanghaiToday()) {
  const duration = plan.scheduleType === 'FROM_NOW' ? 'long' : plan.scheduleType === 'DATE_RANGE' || plan.endDate ? 'custom' : 'long';
  const defaultEnd = new Date(`${today}T00:00:00Z`);defaultEnd.setUTCDate(defaultEnd.getUTCDate()+9);
  return {duration:duration as 'long' | 'custom',end:duration === 'custom' && plan.endDate ? plan.endDate : defaultEnd.toISOString().slice(0,10)};
}
export function publishedScheduleError(duration: string, start: string, end: string, today = shanghaiToday()) {
  if (duration !== 'custom') return '';
  const valid = /^\d{4}-\d{2}-\d{2}$/.test(end) && Number.isFinite(Date.parse(`${end}T00:00:00Z`)) && new Date(`${end}T00:00:00Z`).toISOString().slice(0,10) === end;
  return !valid || end < today || !!start && end < start ? '结束日期不得早于今天或原计划开始日期' : '';
}
