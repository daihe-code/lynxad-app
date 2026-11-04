import { apiDelete, apiGet, apiPost, apiPut } from "@/api/http";
import type { PlatformProductPrice } from '@/features/catalog/productPrice';
import type { PlanReviewProgress } from './planReview';
import type { PlanPauseReason } from './planPause';

export interface PlanVo {
  review?: PlanReviewProgress | null;
  settingsEdit?: { version: string; status: 'IDLE' | 'PREPARING' | 'SENDING' | 'VERIFYING' | 'UNCERTAIN'; message?: string | null } | null;
  statisticsDate?: string | null;
  historicalMetrics?: boolean;
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
  scheduleType?: 'FROM_NOW' | 'DATE_RANGE' | null;
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
  awemeId?: string | null;
  awemeName?: string | null;
  awemeAvatarUrl?: string | null;
  materialCoverUrl?: string | null;
  review?: PlanReviewProgress | null;
  statisticsDate?: string | null;
  historicalMetrics?: boolean;
  roiEstimate?: { value?: number | null; min?: number | null; max?: number | null } | null;
  accountId?: string;
  qcStatus?: string | null;
  canEdit?: boolean;
  canControl?: boolean;
  canTransfer?: boolean;
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
  netGmvYuan?: number | null;
  netRoi?: number | null;
  refundRate?: number | null;
  smartBidType?: string | null;
  budget?: number | null;
  roi2Goal?: number | null;
  scheduleType?: 'FROM_NOW' | 'DATE_RANGE' | null;
  startDate?: string | null;
  endDate?: string | null;
  convert?: number | null;
  snapshotTs?: string | null;
  createdAt?: string | null;
  deliveryPlatform?: 'ALL' | 'STANDARD' | null;
  products?: { id: string; name: string; imageUrl?: string | null; priceInfo?: PlatformProductPrice | null; stock?: number | null; sales?: number | null }[];
  comparison?: {
    costChangePct?: number | null;
    convertChangePct?: number | null;
    roiChange?: number | null;
    roiChangePct?: number | null;
    gmvChangePct?: number | null;
    current?: PlanMetricValues | null;
    previous?: PlanMetricValues | null;
    previousStartDate?: string | null;
    previousEndDate?: string | null;
    unavailableReason?: string | null;
    previousSnapshotTs?: string;
    basis?: 'PREVIOUS_SNAPSHOT' | 'YESTERDAY_FULL_DAY' | 'PREVIOUS_DAY' | 'PREVIOUS_PERIOD';
  } | null;
}

export type PlanMetricTemplate = 'PAYMENT' | 'NET';
export interface PlanMetricValues { cost?: number | null; gmv?: number | null; roi?: number | null; orders?: number | null; netGmv?: number | null; netRoi?: number | null; refundRate?: number | null }

export interface PageResult<T> {
  metricTemplate?: PlanMetricTemplate;
  statisticsRange?: { startDate: string; endDate: string } | null;
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  summary?: PlanMetricValues & { covered: number; plans: number; snapshotTs: string | null; comparison?: PlanListVo['comparison'] };
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

export interface PlanFilters { keyword?: string; status?: string; platform?: string; bidType?: string; createdWithin?: number | null; accountId?: string; sortField?: string; descending?: boolean; startDate?: string; endDate?: string; refreshStatistics?: boolean; metricTemplate?: PlanMetricTemplate }
export function listPlans(pageNum = 1, pageSize = 20, filters: PlanFilters = {}) {
  return apiGet<PageResult<PlanListVo>>("/api/v1/delivery/plans", { params: { pageNum, pageSize, ...filters }, timeout: 120000 });
}
export interface CampaignStatisticsTrend {
  points: { time: string; metrics: PlanMetricValues }[];
  timeDimension: 'stat_time_hour' | 'stat_time_day';
  statisticsRange: { startDate: string; endDate: string };
  metricTemplate: PlanMetricTemplate;
  fetchedAt: string;
  warning?: string | null;
}
export function getCampaignTrend(filters: PlanFilters) {
  return apiGet<CampaignStatisticsTrend>('/api/v1/delivery/plans/statistics/trend', { params: filters, timeout: 60000, headers: { 'X-Quiet-Request': 'true' } });
}

export function syncPlans(accountId?: string) {
  return apiPost<{ accounts: { accountId: string; plans: number; warnings: string[] }[]; completedAt: string }>("/api/v1/delivery/plans/sync", undefined, { params: { accountId }, timeout: 120000 });
}
export function syncPlanDetail(id: string, quiet = false) { return apiPost<PlanVo>(`/api/v1/delivery/plans/${id}/sync-detail`, undefined, { timeout: 60000, ...(quiet ? { headers: { 'X-Quiet-Request': 'true' } } : {}) }); }
export function refreshPlanAudit(id:string){return apiPost<PlanVo>(`/api/v1/delivery/plans/${id}/audit-refresh`,undefined,{timeout:60000,headers:{'X-Quiet-Request':'true'}});}
export function updatePlanSettings(id: string, body: { name?: string; budget?: number; roi2Goal?: number; scheduleType?: 'FROM_NOW' | 'DATE_RANGE'; endDate?: string; expectedVersion: string }) {
  return apiPut<PlanVo>(`/api/v1/delivery/plans/${id}/settings`, body, { timeout: 60000,headers:{'X-Quiet-Request':'true'} });
}
export function refreshPlanSettings(id: string) {
  return apiPost<PlanVo>(`/api/v1/delivery/plans/${id}/sync-detail`,undefined,{timeout:60000,headers:{'X-Quiet-Request':'true'}});
}
export interface PlanSettingsResult {plan: PlanVo; status:'CONFIRMED'|'ACCEPTED_CURRENT'|'UNCERTAIN';message:string;canAcceptCurrent:boolean}
export function verifyPlanSettings(id: string, expectedVersion: string, acceptCurrent=false) {
  return apiPost<PlanSettingsResult>(`/api/v1/delivery/plans/${id}/settings/reconcile`,{expectedVersion,acceptCurrent},{timeout:60000,headers:{'X-Quiet-Request':'true'}});
}
export interface PlanTransferContext {
  planId: string; currentQcPlanId: string; targetMode: string; budget: number; roi2Goal?: number | null;
  reuse: boolean; counterpartQcPlanId?: string | null; available: boolean; reason?: string | null;
  pendingKey?: string | null; pendingStatus?: string | null; pendingError?: string | null;
}
export interface PlanTransferRequest { requestKey: string; expectedQcPlanId: string; smartBidType: string; budget: number; roi2Goal?: number; name?: string; scheduleType?: 'FROM_NOW'|'DATE_RANGE'; endDate?: string }
export interface PlanTransferResult { planId: string; qcPlanId?: string | null; status: 'SUCCEEDED' | 'FAILED' | 'UNCERTAIN' | 'RUNNING' | 'IDLE'; message: string }
export function getPlanTransfer(id: string) { return apiGet<PlanTransferContext>(`/api/v1/delivery/plans/${id}/transfer`); }
export function transferPlan(id: string, body: PlanTransferRequest) { return apiPost<PlanTransferResult>(`/api/v1/delivery/plans/${id}/transfer`, body, { timeout: 180000 }); }
export function switchTransferredPlan(id: string, body: { requestKey: string; expectedQcPlanId: string }) {
  return apiPost<PlanTransferResult>(`/api/v1/delivery/plans/${id}/transfer/switch`, body, { timeout: 180000 });
}
export function reconcilePlanTransfer(id: string) { return apiPost<PlanTransferResult>(`/api/v1/delivery/plans/${id}/transfer/reconcile`, undefined, { timeout: 120000 }); }
export interface ControlMaterial { id: string; name: string; coverUrl?: string | null; auditStatus: string; materialStatus: string; eligible: boolean; reason?: string | null; impressions?: number | null }
export interface ControlTaskMaterial { material_id?: string | null; aweme_item_id?: string | null; title?: string | null; cover_image?: { image_url?: string | null; url?: string | null } | null; video_duration?: number | null }
export interface ControlTask { id: string; name?: string; scene?: string; task_status?: string; budget?: number | null; duration?: number | null; create_time?: string; material_list?: ControlTaskMaterial[] | null; metrics?: Record<string, number | null> | null }
export function listControlMaterials(id: string) { return apiGet<ControlMaterial[]>(`/api/v1/delivery/plans/${id}/materials`, { timeout: 120000 }); }
export function listControlTasks(id: string, kind?: string, range?: import('./statisticsRange').StatisticsRange) { return apiGet<ControlTask[]>(`/api/v1/delivery/plans/${id}/control-tasks`, { timeout: 120000, params: { kind, startDate: range?.[0], endDate: range?.[1] }, headers: { 'X-Quiet-Request': 'true' } }); }
export type ControlTaskAction = 'PAUSE' | 'RESUME' | 'END' | 'DELETE';
export interface ControlTaskReport { task: ControlTask; summary: Record<string, number | null>; trend: { time: string; metrics: Record<string, number | null> }[]; granularity: 'HOUR' | 'DAY'; fetchedAt: string; warning?: string | null }
export function actControlTask(id: string, taskId: string, body: { kind: string; action: ControlTaskAction; expectedStatus: string }) {
  return apiPost<{ taskId: string; status: string }>(`/api/v1/delivery/plans/${encodeURIComponent(id)}/control-tasks/${encodeURIComponent(taskId)}/actions`, body, { timeout: 60000, headers: { 'X-Quiet-Request': 'true' } });
}
export function getControlTaskReport(id: string, taskId: string, kind: string, range: import('./statisticsRange').StatisticsRange, granularity: 'HOUR' | 'DAY') {
  return apiGet<ControlTaskReport>(`/api/v1/delivery/plans/${encodeURIComponent(id)}/control-tasks/${encodeURIComponent(taskId)}/report`, { timeout: 120000, params: { kind, startDate: range[0], endDate: range[1], granularity }, headers: { 'X-Quiet-Request': 'true' } });
}
export function createControlTask(id: string, body: { kind: string; name: string; budget: number; hours?: number; roi2Goal?: number; materialIds: string[]; requestKey: string }) {
  return apiPost<{ taskId: string; status: string }>(`/api/v1/delivery/plans/${id}/control-tasks`, body, { timeout: 60000 });
}

export function getPlan(id: string, options?: { quiet?: boolean; signal?: AbortSignal }) {
  return apiGet<PlanVo>(`/api/v1/delivery/plans/${id}`, {
    signal: options?.signal,
    ...(options?.quiet ? { headers: { 'X-Quiet-Request': 'true' } } : {}),
  });
}

export function publishPlan(id: string, idempotencyKey: string) {
  return apiPost<{ qcPlanId: string; status: string }>(
    `/api/v1/delivery/plans/${id}/publish`,
    { idempotencyKey },
    { headers: { idempotencyKey } },
  );
}

export function actPlan(id: string, action: "ENABLE" | "DISABLE" | "FINISH", pauseReason?: PlanPauseReason) {
  return apiPost<PlanVo>(`/api/v1/delivery/plans/${id}/actions`, { action, ...(action === 'DISABLE' ? { pauseReason } : {}) });
}

export function deletePlan(id: string) {
  return apiDelete<void>(`/api/v1/delivery/plans/${encodeURIComponent(id)}`, { timeout: 60000 });
}
