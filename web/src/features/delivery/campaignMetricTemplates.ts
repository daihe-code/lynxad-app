import type { PlanListVo, PlanMetricTemplate, PlanMetricValues } from './deliveryApi';

export type MetricField = 'costYuan' | 'convert' | 'gmvYuan' | 'roi' | 'netGmvYuan' | 'netRoi' | 'refundRate';
export interface MetricColumn { field: MetricField; key: keyof PlanMetricValues; label: string; kind: 'money' | 'count' | 'roi' | 'rate'; hint: string }
const scopeHint = '与列表采用相同数据范围';
const cost: MetricColumn = { field: 'costYuan', key: 'cost', label: '整体消耗(元)', kind: 'money', hint: scopeHint };
export const metricTemplates: Record<PlanMetricTemplate, { label: string; columns: MetricColumn[] }> = {
  PAYMENT: { label: '整体支付ROI目标', columns: [cost,
    { field: 'convert', key: 'orders', label: '整体成交订单数', kind: 'count', hint: scopeHint },
    { field: 'gmvYuan', key: 'gmv', label: '整体成交金额(元)', kind: 'money', hint: scopeHint },
    { field: 'roi', key: 'roi', label: '整体支付ROI', kind: 'roi', hint: '汇总成交金额 / 汇总消耗' }] },
  NET: { label: '净成交ROI目标', columns: [cost,
    { field: 'netRoi', key: 'netRoi', label: '净成交ROI', kind: 'roi', hint: '汇总净成交金额 / 汇总消耗' },
    { field: 'netGmvYuan', key: 'netGmv', label: '净成交金额(元)', kind: 'money', hint: scopeHint },
    { field: 'refundRate', key: 'refundRate', label: '1小时退款率', kind: 'rate', hint: '按整体成交金额加权汇总' }] },
};
export function metricText(value: number | null | undefined, kind: MetricColumn['kind'], currency = false) {
  if (value == null || !Number.isFinite(Number(value))) return '—';
  const text = Number(value).toLocaleString('zh-CN', { minimumFractionDigits: kind === 'count' ? 0 : 2, maximumFractionDigits: kind === 'count' ? 0 : 2 });
  return kind === 'rate' ? `${text}%` : currency && kind === 'money' ? `¥${text}` : text;
}
export function metricRowValues(row: PlanListVo, template: PlanMetricTemplate) {
  return metricTemplates[template].columns.map(column => row[column.field] ?? null);
}
export function metricDelta(row: PlanListVo, column: MetricColumn) {
  const comparison = row.comparison, key = column.key;
  const current = comparison?.current?.[key], previous = comparison?.previous?.[key];
  const legacyField = { cost: 'costChangePct', orders: 'convertChangePct', gmv: 'gmvChangePct', roi: 'roiChangePct' } as const;
  const value = key in legacyField ? comparison?.[legacyField[key as keyof typeof legacyField]]
    : current != null && previous != null && current >= 0 && previous > 0 ? (current - previous) / previous * 100
    : current === 0 && previous === 0 ? 0 : null;
  return { value, current, previous, percent: true, lowerIsBetter: column.kind === 'rate', unit: column.kind === 'count' ? '单' : column.kind === 'money' ? '元' : column.kind === 'rate' ? '%' : '',
    basis: comparison?.basis, previousTs: comparison?.previousSnapshotTs, previousStartDate: comparison?.previousStartDate,
    previousEndDate: comparison?.previousEndDate, unavailableReason: comparison?.unavailableReason };
}
export function templatePreferenceKey(tenant?: string, user?: string) { return tenant && user ? `lynx.campaign.metricTemplate:${tenant}:${user}` : null; }
