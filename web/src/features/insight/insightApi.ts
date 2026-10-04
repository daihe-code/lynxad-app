import { apiGet, apiPost } from "@/api/http";

export interface ReportRowVo {
  id: string;
  name: string;
  cost: number;
  gmv: number;
  roi: number;
  impression: number;
  click: number;
  convert: number;
  snapshotTs?: string;
}

export function listInsightReports(dim: "plan" | "account" | "PLAN" | "ACCOUNT" = "plan", adIds?: string[]) {
  const params: Record<string, string | string[]> = { dim };
  if (adIds && adIds.length > 0) {
    params.adIds = adIds;
  }
  return apiGet<ReportRowVo[]>("/api/v1/insight/reports", { params });
}

export function exportInsightReports() {
  return apiPost<void>("/api/v1/insight/reports/export", {});
}

export function sumAccountRows(rows: ReportRowVo[]) {
  const cost = rows.reduce((s, r) => s + Number(r.cost || 0), 0);
  const gmv = rows.reduce((s, r) => s + Number(r.gmv || 0), 0);
  return { cost, gmv, roi: cost > 0 ? gmv / cost : 0 };
}
