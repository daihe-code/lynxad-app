export type StatisticsRange = [string, string];
const DAY = 86400000;
export function shanghaiToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const part = (name: string) => parts.find(p => p.type === name)!.value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}
export function shiftDay(day: string, offset: number): string {
  return new Date(Date.parse(`${day}T00:00:00Z`) + offset * DAY).toISOString().slice(0, 10);
}
export function rangeDays(range: StatisticsRange): number {
  return Math.round((Date.parse(`${range[1]}T00:00:00Z`) - Date.parse(`${range[0]}T00:00:00Z`)) / DAY) + 1;
}
export function recentRange(days: number): StatisticsRange {
  const today = shanghaiToday();
  return [shiftDay(today, 1 - days), today];
}
export function rangeError(range: StatisticsRange): string | null {
  const days = rangeDays(range);
  if (!Number.isFinite(days) || days < 1) return '请选择有效的统计起止日期';
  if (days > 180) return '统计时间区间最长为180天（包含起止日期）';
  if (range[1] > shanghaiToday()) return '统计日期不能晚于今天';
  return null;
}
