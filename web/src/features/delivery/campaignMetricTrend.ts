import type { CampaignStatisticsTrend, PlanFilters, PlanMetricValues } from './deliveryApi';

// Sorting and pagination change row order, not the overview's statistical scope.
export function campaignTrendScope(filters: PlanFilters) {
  return JSON.stringify([filters.startDate, filters.endDate, filters.metricTemplate || 'PAYMENT', filters.keyword || '', filters.status || '', filters.platform || '', filters.bidType || '', filters.createdWithin ?? null, filters.accountId || '']);
}
/** Monotone cubic interpolation: rounds corners without inventing peaks or bridging missing values. */
export function smoothMetricPath(values: { x: number; y: number | null }[]): string {
  const segments: { x: number; y: number }[][] = [];
  for (const value of values) {
    if (value.y == null) { segments.push([]); continue; }
    let segment = segments.at(-1);
    if (!segment || segment.length && value.x <= segment.at(-1)!.x) { segment = []; segments.push(segment); }
    segment.push({ x:value.x, y:value.y });
  }
  const number = (value: number) => String(Number(value.toFixed(3)));
  const point = (x: number, y: number) => `${number(x)},${number(y)}`;
  return segments.filter(segment => segment.length).map(segment => {
    const slopes = segment.slice(1).map((p,i) => (p.y-segment[i]!.y)/(p.x-segment[i]!.x));
    const tangents = segment.map((_p,i) => {
      if (i===0) return slopes[0] ?? 0;
      if (i===segment.length-1) return slopes[i-1]!;
      const before=slopes[i-1]!,after=slopes[i]!;
      if (before*after<=0) return 0;
      const left=segment[i]!.x-segment[i-1]!.x,right=segment[i+1]!.x-segment[i]!.x;
      const a=2*right+left,b=right+2*left;
      return (a+b)/(a/before+b/after);
    });
    for (let i=0;i<slopes.length;i++) {
      const slope=slopes[i]!;
      if (slope===0) { tangents[i]=tangents[i+1]=0; continue; }
      const scale=Math.hypot(tangents[i]!/slope,tangents[i+1]!/slope);
      if (scale>3) { tangents[i]=tangents[i]!*3/scale; tangents[i+1]=tangents[i+1]!*3/scale; }
    }
    return `M${point(segment[0]!.x,segment[0]!.y)}`+segment.slice(1).map((p,i) => {
      const previous=segment[i]!,third=(p.x-previous.x)/3;
      return ` C${point(previous.x+third,previous.y+third*tangents[i]!)} ${point(p.x-third,p.y-third*tangents[i+1]!)} ${point(p.x,p.y)}`;
    }).join('');
  }).join(' ');
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
  const path = smoothMetricPath(values);
  return { values:values.map((p,i) => ({ ...p, isolated:p.value!=null && values[i-1]?.value==null && values[i+1]?.value==null })), path, hasData: known.length > 0, ticks: Array.from({ length: 5 }, (_, i) => ({ y: 208 - i * 45, value: lower + (upper - lower) * i / 4 })) };
}
