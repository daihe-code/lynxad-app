import { metricTemplates, type MetricColumn } from './campaignMetricTemplates.ts';
import type { PlanMetricTemplate } from './deliveryApi';

export interface CampaignTableColumn {
  key: string;
  label: string;
  width: number;
  minWidth: number;
  metric?: MetricColumn;
}

export const maximumColumnWidth = 1600;

export function campaignTableColumns(template: PlanMetricTemplate, showTools: boolean): CampaignTableColumn[] {
  return [
    { key: 'name', label: '计划名称', width: 320, minWidth: 180 },
    { key: 'status', label: '计划状态', width: 160, minWidth: 96 },
    { key: 'settings', label: '投放设置', width: 192, minWidth: 156 },
    { key: 'schedule', label: '投放时间', width: 192, minWidth: 168 },
    { key: 'optimization', label: '盯盘优化', width: 148, minWidth: 128 },
    ...(showTools ? [{ key: 'tools', label: '调控工具', width: 136, minWidth: 116 }] : []),
    { key: 'actions', label: '操作', width: 80, minWidth: 80 },
    ...metricTemplates[template].columns.map((metric, index) => ({
      key: metric.field, label: metric.label, width: [108, 92, 122, 106][index]!, minWidth: 72, metric,
    })),
  ];
}
export function boundedColumnWidth(column: CampaignTableColumn, value: number) {
  return Math.round(Math.max(column.minWidth, Math.min(maximumColumnWidth, value)));
}

export function readColumnWidths(value: string | null): Record<string, number> {
  if (!value) return {};
  try {
    const saved: unknown = JSON.parse(value);
    if (!saved || typeof saved !== 'object' || Array.isArray(saved)) return {};
    const result: Record<string, number> = {};
    for (const column of [...campaignTableColumns('PAYMENT', true), ...campaignTableColumns('NET', true)]) {
      const width = (saved as Record<string, unknown>)[column.key];
      if (typeof width === 'number' && Number.isFinite(width)) result[column.key] = boundedColumnWidth(column, width);
    }
    return result;
  } catch { return {}; }
}

export const columnWidthsPreferenceKey = (tenant?: string, user?: string) =>
  tenant && user ? `lynx.campaign.columnWidths:${tenant}:${user}` : null;
