import { apiGet, apiPost, apiPut } from '@/api/http';
import { requestInboxRefresh } from '@/features/tenant/inboxRefresh';

export interface ProductEconomics {
  productId: string;
  contributionMarginRate: number;
  refundReserveRate: number;
  verified: boolean;
}
export interface OptimizationPolicy {
  rules?: OptimizationRules | null;
  enabled: boolean;
  mode: 'OBSERVE' | 'AUTO_LIMITED';
  automation: Automation;
  tactics: Tactics;
  creativeGeneration: CreativeGeneration;
  dailySpendLimit: number;
  maxLoss: number;
  minSpend: number;
  minOrders: number;
  persistenceWindows: number;
  conversionDelayMinutes: number;
  economics: ProductEconomics[];
}
export interface OptimizationRules { roiFloor: number; spendReminder: number; spendProtection: number; protectionAction: 'CONFIRM' | 'AUTO_PAUSE'; runawayMinutes: number; runawaySpend: number; maxAddition: number; dailyAddition: number }
export interface Automation { budget: boolean; roi: boolean; pause: boolean; resumeNextDay: boolean; minBudget: number; maxBudget: number; minRoi: number; maxRoi: number; maxStepPercent: number; cooldownMinutes: number; maxActionsPerDay: number; resumeHour: number }
export const defaultAutomation = (): Automation => ({ budget: true, roi: false, pause: true, resumeNextDay: false, minBudget: 50, maxBudget: 500, minRoi: .01, maxRoi: 100, maxStepPercent: 10, cooldownMinutes: 120, maxActionsPerDay: 3, resumeHour: 9 });
export interface Tactics { boost: boolean; materialFollow: boolean; materialAdd: boolean; materialRemove: boolean; productAdd: false; productRemove: boolean; taskBudget: number; taskHours: number; extraBudgetPerDay: number; entityDays: number; retainProducts: number; retainMaterials: number; candidatePlanIds: string[] }
export const defaultTactics = (): Tactics => ({ boost: false, materialFollow: false, materialAdd: false, materialRemove: false, productAdd: false, productRemove: false, taskBudget: 100, taskHours: 1, extraBudgetPerDay: 300, entityDays: 2, retainProducts: 1, retainMaterials: 1, candidatePlanIds: [] });
export interface CreativeGeneration { enabled: boolean; autoPublish: boolean; formats: string[]; minMaterials: number; maxPending: number; dailyCostLimit: number; perAssetCostLimit: number; trialLossLimit: number; minImpressions: number; minClicks: number; brandStyle: string; forbiddenClaims: string; authorizedBy?: string }
export const defaultCreativeGeneration = (): CreativeGeneration => ({ enabled: false, autoPublish: false, formats: ['video', 'image_text', 'product_card'], minMaterials: 2, maxPending: 2, dailyCostLimit: 100, perAssetCostLimit: 50, trialLossLimit: 50, minImpressions: 1000, minClicks: 50, brandStyle: '', forbiddenClaims: '' });
export interface CreativeStrategy { scene_strategy?: { mode: string; avoid_scenes: string[]; avoid_styles: string[]; avoid_angles: string[] }; product_id: string; target: string; trigger: string; hypothesis: string; visual_style?: string; evidence_summary: string; reserved_cost: string; cost_note: string; experiment_mode: string; test_dimension?: string; baseline_material_id?: string; warnings: string[] }
export interface ProposedAction { kind: 'BUDGET' | 'ROI' | 'PAUSE' | 'ENABLE' | 'BOOST' | 'MATERIAL_FOLLOW' | 'MATERIAL_GENERATE' | 'MATERIAL_ADD' | 'MATERIAL_REMOVE' | 'PRODUCT_ADD' | 'PRODUCT_REMOVE'; expected?: string; target?: string; cause: string; budget?: string; duration?: string; entity_id?: string; product_id?: string; material_type?: string; source_plan_id?: string; generation_id?: string; strategy?: CreativeStrategy }
export interface MonitoringRecommendation { type: string; title: string; evidence: string; requires_confirmation: boolean; execution_allowed?: boolean; action?: ProposedAction; workflow?: string; object_id?: string; object_type?: string }
export interface OptimizationReview { id: string; eventId: string; workflow: string; status: string; createdAt: string; actionAt?: string; result?: { reason?: string; before_roi?: string; after_roi?: string; roi_change?: string; before_spend?: string; after_spend?: string; before_window?: { window_start: string; window_end: string }; after_window?: { window_start: string; window_end: string } } }
export interface DecisionPreview { eventId: string; version: number; hash: string; facts: Record<string, unknown>; action: ProposedAction; addition: number; committed: number; expiresAt: string }
export interface MonitoringReport {
  strategy_approved?: boolean; diagnostics_ready?: boolean; business_ready?: boolean; coverage_gaps?: string[];
  rules?: OptimizationRules;
  actual_net_roi?: string | null;
  mature_spend?: string | null;
  mature_net_revenue?: string | null;
  daily_net_revenue?: string | null;
  complete?: boolean;
  roi_windows?: { window_start: string; window_end: string; metrics: { spend: string | null; net_revenue: string | null } }[];
  plan_id?: string;
  plan_status?: string;
  optimization_restarted_at?: string;
  state: 'READY' | 'WAITING_DATA' | 'PAUSED';
  observed_at: string;
  source: string;
  rule_version: string;
  estimated_contribution_profit: string | null;
  break_even_net_roi: string | null;
  daily_spend: string | null;
  spend: string | null;
  net_revenue: string | null;
  orders: string | null;
  window_start: string | null;
  window_end: string | null;
  window_count: number;
  profit_note: string;
  reasons: string[];
  tactics_issues?: string[];
  creative_strategies?: CreativeStrategy[];
  creative_direction_assessments?: { context_id?: string; entity_id: string; product_id: string; format: string; status: string; estimated_contribution_profit: string | null; spend: string | null; mature_days: number; creative_profile: { source?: string; confidence?: number; scenes?: string[]; visual_style?: string; creative_angle?: string } }[];
  recommendations: MonitoringRecommendation[];
}
export interface OptimizationView {
  planName?: string; accountId?: string; planStatus?: string; qcStatus?: string;
  policy: OptimizationPolicy;
  version: number;
  productIds: string[];
  available: boolean;
  unavailableReason?: string;
  lastCheck?: string;
  lastSuccess?: string;
  error?: string;
  stale: boolean;
  result?: MonitoringReport;
  holdReason?: string;
  lastAction?: string;
  bidType?: string;
}
const path = (id: string) => `/api/v1/delivery/plans/${encodeURIComponent(id)}/optimization`;
export const getOptimization = (id: string) => apiGet<OptimizationView>(path(id), { headers: { 'X-Quiet-Request': 'true' } });
export const getOptimizationRules = (id: string) => apiGet<OptimizationView>(`${path(id)}/rules`, { headers: { 'X-Quiet-Request': 'true' } });
export async function saveOptimizationRules(id: string, rules: OptimizationRules, enabled: boolean, expectedVersion: number) {
  const view = await apiPut<OptimizationView>(`${path(id)}/rules`, { rules, enabled, expectedVersion }, { headers: { 'X-Quiet-Request': 'true' } });
  requestInboxRefresh();
  return view;
}
export interface OptimizationResumePreview {
  version: number;
  day: string;
  planStatus: string;
  qcStatus?: string | null;
  outcome: 'BELOW_LIMIT' | 'LIMIT_PAUSE' | 'LIMIT_REMINDER' | 'PLAN_NOT_DELIVERING';
  spend: number;
  fetchedAt: string;
  committed: number;
  rules: OptimizationRules;
}
export const getOptimizationResumePreview = (id: string) => apiGet<OptimizationResumePreview>(
  `${path(id)}/rules/resume-preview`, { headers: { 'X-Quiet-Request': 'true' } },
);
export async function confirmOptimizationResume(id: string, { version, day, planStatus, outcome }: OptimizationResumePreview) {
  const result = await apiPost<{ pauseStatus?: string | null }>(`${path(id)}/rules/resume`,
    { version, day, planStatus, outcome }, { timeout: 60000, headers: { 'X-Quiet-Request': 'true' } });
  requestInboxRefresh();
  return result;
}
export interface CandidatePlan { id: string; name: string; qcId: string }
export const getOptimizationCandidates = (id: string) => apiGet<CandidatePlan[]>(`${path(id)}/candidates`, { headers: { 'X-Quiet-Request': 'true' } });
export const saveOptimization = (id: string, policy: OptimizationPolicy, expectedVersion: number) => apiPut<OptimizationView>(path(id), { policy, expectedVersion });
export const checkOptimization = (id: string) => apiPost<OptimizationView>(`${path(id)}/check`, undefined, { timeout: 120000 });
export interface CreativeJob { id: string; productId: string; status: string; reservedCost: number; createdAt: string; publishedAt?: string; body: { strategy: CreativeStrategy; directionPlan?: { creative_profile: { scenes: string[]; visual_style: string; creative_angle: string }; rationale: string; selection_mode?: 'MODEL' | 'FALLBACK'; preflight_status?: 'APPROVED' | 'ADVISORY'; warnings?: string[] }; error?: string; material?: { type: string; title: string; text: string; previewUrl?: string; posterUrl?: string; imageUrls?: string[] }; trial_summary?: { spend: number; revenue: number; profit: number; impressions: number; clicks: number; orders: number; days: number }; trial?: { audit?: string; complete?: boolean }; publication_event_id?: string } }
export const getCreativeJobs = (id: string) => apiGet<CreativeJob[]>(`${path(id)}/creative-jobs`, { headers: { 'X-Quiet-Request': 'true' } });
export const reviewCreativeJob = (id: string, job: string, approved: boolean) => apiPost(`${path(id)}/creative-jobs/${encodeURIComponent(job)}/review`, { approved });
const centerPath = '/api/v1/optimization';
export interface PlanOptimizationStatus { planId: string; enabled: boolean; mode: string; holdReason?: string; stale: boolean; failed: boolean; state?: string; ruleBased?: boolean }
export const getOptimizationStatuses = (planIds: string[]) => apiGet<PlanOptimizationStatus[]>(`${centerPath}/statuses`, { params: { planIds: planIds.join(',') }, headers: { 'X-Quiet-Request': 'true' } });
export interface CenterRow { planId: string; qcId: string; planName: string; accountId: string; accountName: string; planStatus: string; enabled: boolean; mode: string; version: number; holdReason?: string; lastSuccess?: string; lastAction?: string; error?: string; stale: boolean; result?: MonitoringReport }
export interface CenterAccount { accountId: string; name: string; budgetLimit?: number }
export interface CenterView { records: CenterRow[]; total: number; watched: number; automatic: number; held: number; accounts: CenterAccount[]; pageNum: number; pageSize: number }
export interface OptimizationEvent { id: string; tenant: string; planId: string; accountId: string; planName: string; version: number; kind?: string; title: string; evidence: string; proposal?: ProposedAction; report: MonitoringReport; status: string; error?: string; actual?: Record<string, unknown>; createdAt: string; dispatchedAt?: string; finishedAt?: string }
export interface HistoryPage { records: OptimizationEvent[]; total: number; pageNum: number; pageSize: number }
export interface OptimizationDetails {
  actions?: OptimizationEvent[];
  reviews?: OptimizationReview[];
  view: OptimizationView;
  effectiveSeconds: number;
  summary: { found: number; completed: number; protectedCount: number };
  pending: OptimizationEvent[];
  history: HistoryPage;
  accountStatus?: string;
  accountName?: string;
  pause?: { pausedAt: string; snapshot?: { dailySpend?: string | number | null; dataAt?: string | null } | null } | null;
}
export const getOptimizationDetails = (id: string, params: Record<string, unknown>) => apiGet<OptimizationDetails>(`${path(id)}/details`, { params, headers: { 'X-Quiet-Request': 'true' } });
export const getMonitoringCenter = (params: Record<string, unknown>) => apiGet<CenterView>(`${centerPath}/center`, { params, headers: { 'X-Quiet-Request': 'true' } });
export const getOptimizationHistory = (params: Record<string, unknown>, signal?: AbortSignal) => apiGet<HistoryPage>(`${centerPath}/history`, { params, signal, headers: { 'X-Quiet-Request': 'true' } });
export const setAccountBudgetLimit = (id: string, budgetLimit: number) => apiPut(`${centerPath}/accounts/${encodeURIComponent(id)}/limit`, { budgetLimit });
export const holdAllOptimization = () => apiPost(`${centerPath}/hold-all`);
export const resumeOptimization = (id: string, expectedVersion: number) => apiPost(`${centerPath}/plans/${encodeURIComponent(id)}/resume`, { expectedVersion });
export const recheckOptimization = (id: string) => apiPost(`${centerPath}/history/${encodeURIComponent(id)}/recheck`);
export const acknowledgeOptimization = (id: string) => apiPost(`${centerPath}/history/${encodeURIComponent(id)}/acknowledge`);

export const previewOptimizationDecision = (id: string, event: string) => apiGet<DecisionPreview>(`${path(id)}/decisions/${encodeURIComponent(event)}/preview`, { headers: { 'X-Quiet-Request': 'true' } });
export const confirmOptimizationDecision = (id: string, event: string, expectedVersion: number, previewHash: string) => apiPost<OptimizationEvent>(`${path(id)}/decisions/${encodeURIComponent(event)}/confirm`, { expectedVersion, previewHash }, { timeout: 120000, headers: { 'X-Quiet-Request': 'true' } });
export const startOptimizationReview = (id: string, event: string, workflow: string) => apiPost<OptimizationReview>(`${path(id)}/decisions/${encodeURIComponent(event)}/track`, { workflow }, { timeout: 120000, headers: { 'X-Quiet-Request': 'true' } });
