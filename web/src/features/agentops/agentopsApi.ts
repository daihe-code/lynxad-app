import { apiGet } from "@/api/http";

export interface AgentActionVo {
  id: string;
  planId: string;
  type: string;
  summary: string;
  snapshotTs: string;
  recoveredAt?: string | null;
  open: boolean;
}

export function listActions() {
  return apiGet<AgentActionVo[]>("/api/v1/agentops/actions");
}

export const WATCHER_TYPES = ["ROI_DROP", "BUDGET_EXHAUST", "QC_ABNORMAL_PAUSE"] as const;

export function ruleLabel(type: string): string {
  if (type === "ROI_DROP") return "ROI 下降";
  if (type === "BUDGET_EXHAUST") return "预算耗尽";
  if (type === "QC_ABNORMAL_PAUSE") return "千川异常暂停";
  return type;
}
