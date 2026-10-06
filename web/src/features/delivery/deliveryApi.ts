import { apiDelete, apiGet, apiPost, apiPut } from "@/api/http";

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
  accountId?: string;
  roi2Goal?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  convert?: number | null;
  snapshotTs?: string | null;
  products?: PlanListVo['products'];
  comparison?: PlanListVo['comparison'];
  createdAt?: string | null;
  deliveryPlatform?: PlanListVo['deliveryPlatform'];
  qcStatus?: string | null;
  platformDetail?: Record<string, unknown> | null;
}

export interface PlanListVo {
  accountId?: string;
  qcStatus?: string | null;
  canEdit?: boolean;
  canControl?: boolean;
  planId: string;
  name: string;
  status: string;
  source: string;
  qcPlanId?: string;
  lingtou: boolean;
  coverUrl?: string;
  guarantee?: string;
  costYuan?: number | null;
  gmvYuan?: number | null;
  roi?: number | null;
  smartBidType?: string | null;
  budget?: number | null;
  roi2Goal?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  convert?: number | null;
  snapshotTs?: string | null;
  createdAt?: string | null;
  deliveryPlatform?: 'ALL' | 'STANDARD' | null;
  products?: { id: string; name: string; imageUrl?: string | null }[];
  comparison?: {
    costChangePct?: number | null;
    convertChangePct?: number | null;
    roiChange?: number | null;
    roiChangePct?: number | null;
    previousSnapshotTs?: string;
    basis?: 'PREVIOUS_SNAPSHOT' | 'YESTERDAY_FULL_DAY';
  } | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  summary?: { cost: number | null; gmv: number | null; orders: number | null; roi: number | null; covered: number; plans: number; snapshotTs: string | null };
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

export function discardDraft(id: string) {
  return apiDelete(`/api/v1/delivery/plans/${encodeURIComponent(id)}/draft`);
}

export interface PlanFilters { keyword?: string; status?: string; platform?: string; bidType?: string; createdWithin?: number | null; accountId?: string; sortField?: string; descending?: boolean }
export function listPlans(pageNum = 1, pageSize = 20, filters: PlanFilters = {}) {
  return apiGet<PageResult<PlanListVo>>("/api/v1/delivery/plans", { params: { pageNum, pageSize, ...filters } });
}

export function syncPlans(accountId?: string) {
  return apiPost<{ accounts: { accountId: string; plans: number; warnings: string[] }[]; completedAt: string }>("/api/v1/delivery/plans/sync", undefined, { params: { accountId }, timeout: 120000 });
}
export function syncPlanDetail(id: string) { return apiPost<PlanVo>(`/api/v1/delivery/plans/${id}/sync-detail`, undefined, { timeout: 60000 }); }
export function updatePlanSettings(id: string, body: { name?: string; budget?: number; roi2Goal?: number }) {
  return apiPut<PlanVo>(`/api/v1/delivery/plans/${id}/settings`, body, { timeout: 60000 });
}
export interface ControlMaterial { id: string; name: string; coverUrl?: string | null; auditStatus: string; materialStatus: string; eligible: boolean; reason?: string | null; impressions?: number | null }
export interface ControlTask { id: string; name?: string; scene?: string; task_status?: string; budget?: number; duration?: number }
export function listControlMaterials(id: string) { return apiGet<ControlMaterial[]>(`/api/v1/delivery/plans/${id}/materials`, { timeout: 120000 }); }
export function listControlTasks(id: string) { return apiGet<ControlTask[]>(`/api/v1/delivery/plans/${id}/control-tasks`, { timeout: 120000 }); }
export function createControlTask(id: string, body: { kind: string; name: string; budget: number; hours?: number; roi2Goal?: number; materialIds: string[]; requestKey: string }) {
  return apiPost<{ taskId: string; status: string }>(`/api/v1/delivery/plans/${id}/control-tasks`, body, { timeout: 60000 });
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
