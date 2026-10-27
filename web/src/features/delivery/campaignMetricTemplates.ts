import type { PlanListVo, PlanMetricTemplate, PlanMetricValues } from './deliveryApi';

export type MetricField = 'costYuan' | 'convert' | 'gmvYuan' | 'roi' | 'netGmvYuan' | 'netRoi' | 'refundRate';
export interface MetricColumn { field: MetricField; key: keyof PlanMetricValues; label: string; kind: 'money' | 'count' | 'roi' | 'rate'; hint: string; description: string }
const scopeHint = '与列表采用相同数据范围';
const cost: MetricColumn = { field: 'costYuan', key: 'cost', label: '整体消耗(元)', kind: 'money', hint: scopeHint, description: '所选统计时间内，投放期间产生的消耗。' };
export const metricTemplates: Record<PlanMetricTemplate, { label: string; columns: MetricColumn[] }> = {
  PAYMENT: { label: '整体支付ROI目标', columns: [cost,
    { field: 'convert', key: 'orders', label: '整体成交订单数', kind: 'count', hint: scopeHint, description: '所选统计时间内，投放带来的整体成交订单数量。' },
    { field: 'gmvYuan', key: 'gmv', label: '整体成交金额(元)', kind: 'money', hint: scopeHint, description: '所选统计时间内，投放带来的整体成交金额，包含平台计入的优惠及补贴金额。' },
    { field: 'roi', key: 'roi', label: '整体支付ROI', kind: 'roi', hint: '汇总成交金额 / 汇总消耗', description: '每消耗1元带来的整体成交金额。计算公式：整体支付ROI = 整体成交金额 ÷ 整体消耗。' }] },
  NET: { label: '净成交ROI目标', columns: [cost,
    { field: 'netRoi', key: 'netRoi', label: '净成交ROI', kind: 'roi', hint: '汇总净成交金额 / 汇总消耗', description: '每消耗1元带来的净成交金额。计算公式：净成交ROI = 净成交金额 ÷ 整体消耗。' },
    { field: 'netGmvYuan', key: 'netGmv', label: '净成交金额(元)', kind: 'money', hint: scopeHint, description: '整体成交金额扣除支付后1小时内发生退款的金额后，得到的成交金额。' },
    { field: 'refundRate', key: 'refundRate', label: '1小时退款率', kind: 'rate', hint: '按整体成交金额加权汇总', description: '支付后1小时内退款金额占整体成交金额的比例，用于了解成交后的短时退款情况。' }] },
};
export function metricText(value: number | null | undefined, kind: MetricColumn['kind'], currency = false) {
  if (value == null || !Number.isFinite(Number(value))) return '—';
  const text = Number(value).toLocaleString('zh-CN', { minimumFractionDigits: kind === 'count' ? 0 : 2, maximumFractionDigits: kind === 'count' ? 0 : 2 });
  return kind === 'rate' ? `${text}%` : currency && kind === 'money' ? `¥${text}` : text;
}
export function metricRowValues(row: PlanListVo, template: PlanMetricTemplate) {
  return metricTemplates[template].columns.map(column => row[column.field] ?? null);
}
export function metricDelta(row: Pick<PlanListVo, 'comparison'>, column: MetricColumn) {
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
