export const materialMetrics = [
  { key: 'cost', label: '整体消耗(元)' },
  { key: 'ctr', label: '整体点击率' },
  { key: 'cpm', label: '整体千次展现费用(元)' },
  { key: 'cvr', label: '整体转化率' },
  { key: 'payment', label: '用户实际支付金额(元)' },
  { key: 'orderCost', label: '整体成交订单成本(元)' },
  { key: 'roi', label: '整体支付ROI' },
] as const;
export function materialMetricValue(key: string, value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  // The native Value is already expressed in percentage points (8.11 means 8.11%).
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + (['ctr', 'cvr'].includes(key) ? '%' : '');
}
export function reportingToday(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  return ['year', 'month', 'day'].map(type => parts.find(part => part.type === type)!.value).join('-');
}
export function shiftReportDate(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`); value.setUTCDate(value.getUTCDate() + days); return value.toISOString().slice(0, 10);
}
export function statisticsRangeError(from: string, to: string, today: string): string {
  const valid = (date: string) => /^\d{4}-\d{2}-\d{2}$/.test(date) && !Number.isNaN(Date.parse(date)) && new Date(date).toISOString().slice(0, 10) === date;
  if (!valid(from) || !valid(to)) return '请选择完整的统计日期';
  if (from > to) return '开始日期不能晚于结束日期';
  if (to > today) return '统计日期不能晚于今天';
  if (from < shiftReportDate(today, -179)) return '请选择最近180天内的日期';
  return '';
}
export function materialDailyPoints(from: string, to: string, rows: { date: string; metrics: Record<string, number | null> }[]) {
  const values = new Map(rows.map(row => [row.date, row.metrics]));
  const points: { date: string; metrics: Record<string, number | null> }[] = [];
  for (let date = from; date <= to && points.length < 180; date = shiftReportDate(date, 1)) points.push({ date, metrics: values.get(date) || {} });
  return points;
}
