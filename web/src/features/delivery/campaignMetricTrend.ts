import type { CampaignStatisticsTrend, PlanFilters, PlanMetricValues } from './deliveryApi';

// Sorting and pagination change row order, not the overview's statistical scope.
export function campaignTrendScope(filters: PlanFilters) {
  return JSON.stringify([filters.startDate, filters.endDate, filters.metricTemplate || 'PAYMENT', filters.keyword || '', filters.status || '', filters.platform || '', filters.bidType || '', filters.createdWithin ?? null, filters.accountId || '']);
}
export function metricPlot(points: CampaignStatisticsTrend['points'], key: keyof PlanMetricValues) {
  const known = points.map(p => p.metrics[key]).filter((v): v is number => v != null && Number.isFinite(Number(v))).map(Number);
  const nice = (v: number) => { const power = 10 ** Math.floor(Math.log10(v)); return Math.ceil(v / power) * power; };
  const min = Math.min(0, ...known), max = Math.max(0, ...known);
  const lower = min < 0 ? -nice(-min) : 0, upper = max > 0 ? nice(max) : min < 0 ? 0 : 1;
  const timestamps = points.map(p => Date.parse(p.time.length === 10 ? `${p.time}T00:00:00+08:00` : `${p.time.replace(' ', 'T')}+08:00`));
  const first = timestamps[0] ?? 0, last = timestamps.at(-1) ?? 0;
  const values = points.map((p, index) => {
    const raw = p.metrics[key], value = raw != null && Number.isFinite(Number(raw)) ? Number(raw) : null;
    return { time: p.time, value, x: first === last ? 332 : 64 + (timestamps[index]! - first) / (last - first) * 536,
      y: value == null ? null : 208 - (value - lower) / (upper - lower) * 180 };
  });
  let joined = false;
  const path = values.map(p => { if (p.value == null) { joined = false; return ''; } const point = `${joined ? 'L' : 'M'}${p.x},${p.y}`; joined = true; return point; }).join(' ');
  return { values, path, hasData: known.length > 0, ticks: Array.from({ length: 5 }, (_, i) => ({ y: 208 - i * 45, value: lower + (upper - lower) * i / 4 })) };
}
