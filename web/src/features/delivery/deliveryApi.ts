import { apiGet, apiPost, apiPut } from "@/api/http";

export interface PlanVo {
  planId: string;
  name: string;
  status: string;
  source: string;
  qcPlanId?: string;
  smartBidType?: string;
  budget?: number;
  awemeId?: string;
  rejectReason?: string;
  lingtou: boolean;
  guarantee?: string;
  coverUrl?: string;
  costYuan?: number;
  gmvYuan?: number;
  roi?: number;
}

export interface PlanListVo {
  planId: string;
  name: string;
  status: string;
  source: string;
  qcPlanId?: string;
  lingtou: boolean;
  coverUrl?: string;
  guarantee?: string;
  costYuan?: number;
  gmvYuan?: number;
  roi?: number;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export interface PlanDraftRequest {
  accountId: string;
  awemeId: string;
  name: string;
  smartBidType: string;
  dailyBudget: number;
  roi2Goal?: number;
  productIds: string[];
  packageId?: string;
}

export function createDraft(body: PlanDraftRequest) {
  return apiPost<{ plan: PlanVo; suggestBudget?: number; suggestRoi?: number }>("/api/v1/delivery/plans", body);
}

export function updateDraft(id: string, body: PlanDraftRequest) {
  return apiPut<PlanVo>(`/api/v1/delivery/plans/${id}`, body);
}

export function listPlans(pageNum = 1, pageSize = 20) {
  return apiGet<PageResult<PlanListVo>>("/api/v1/delivery/plans", { params: { pageNum, pageSize } });
}

export function getPlan(id: string) {
  return apiGet<PlanVo>(`/api/v1/delivery/plans/${id}`);
}

export function publishPlan(id: string, idempotencyKey: string) {
  return apiPost<{ qcPlanId: string; status: string }>(
    `/api/v1/delivery/plans/${id}/publish`,
    { idempotencyKey },
    { headers: { idempotencyKey } },
  );
}

export function actPlan(id: string, action: "ENABLE" | "DISABLE" | "FINISH") {
  return apiPost<PlanVo>(`/api/v1/delivery/plans/${id}/actions`, { action });
}
