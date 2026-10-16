import { apiDelete, apiGet, apiPost, apiPut } from '@/api/http';
import type { PlatformProductPrice } from '@/features/catalog/productPrice';

export interface CreationProduct {
  productId: string; qcProductId: string; name: string; price: number | null; userPrice: number | null;
  imageUrl?: string; category?: string; stock?: number; tags: string[]; unavailableReasons: string[];
  priceInfo?: PlatformProductPrice | null; channelId?: string | null;
}
export interface CreationMaterial {
  id: string; productId: string; type: 'VIDEO' | 'IMAGE' | 'CAROUSEL'; title: string; text?: string;
  status: string; error?: string; previewUrl?: string; posterUrl?: string; engineMaterialId?: string;
  titles: string[]; imageUrls?: string[];
  generationRequestId?: string; generationDescription?: string;
  creativeProfile?: Record<string, unknown>; creativeProfileStatus?: string;
}
export type ProductAnalysisDimensionKey = 'competitor_count' | 'search_heat_7d' | 'audience_match' | 'estimated_roi';
export interface ProductAnalysisDimension {
  value?: number | string | null;
  value_range?: [number, number] | null;
  unit?: string;
  conclusion?: string;
  method?: string;
  is_estimate?: boolean;
  confidence?: number;
  observed_at?: string | null;
  source?: string;
  missing_reason?: string | null;
  evidence_ids?: string[];
  evidence?: { platform?: string; scope?: string };
}
export interface ProductAnalysis {
  overall_conclusion?: string;
  dimensions?: Partial<Record<ProductAnalysisDimensionKey, ProductAnalysisDimension>>;
  recommendations?: string[];
  public_sources?: { id: string; title: string; url: string; published_at?: string | null }[];
  data_quality?: { status: 'complete' | 'partial' | 'insufficient'; available_dimensions: number; total_dimensions: number; retryable?: boolean };
  data_freshness?: { searched_at?: string; notes?: string };
  processing?: { web_search_triggered?: boolean; web_search_executed?: boolean; web_search_used?: boolean; web_search_error?: string | null };
  snapshot?: { created_at?: string; cache_hit?: boolean; version?: number };
}
export interface CreationState {
  planId: string; accountId: string; awemeId: string; name: string; revision: number; step: number;
  packageVersion: number; confirmedVersion?: number; smartBidType: 'CUSTOM' | 'CONSERVATIVE';
  budget?: number; roi2Goal?: number; scheduleType: string; startDate?: string; endDate?: string; hideInAweme: boolean;
  products: CreationProduct[]; analysisStatus: string; analysisError?: string;
  analysis?: ProductAnalysis;
  recommendations?: { budget?: Record<string, unknown>; roi?: Record<string, unknown>; budgetError?: string; roiError?: string };
  generationStatus: string; generationMessage?: string; progress: number; generationProductIds: string[];
  generationStage?: string;
  generationCounts?: { type: 'VIDEO' | 'IMAGE' | 'CAROUSEL'; completed: number; total: number | null }[];
  generationIssues?: { productId: string; productName: string; message: string }[];
  materials: CreationMaterial[]; publishStatus: string; publishKey?: string; qcPlanId?: string; publishError?: string;
  generationAdditions?: { id: string; productId: string; type: string; description: string; status: string; createdAt: string; materialId?: string; error?: string }[];
}
export interface CreationAdditionBody { productId: string; type: 'VIDEO' | 'IMAGE' | 'CAROUSEL'; description: string; requestId: string }
export interface ProductQuery { page: number; pageSize: number; keyword: string; tab: string; unpromoted: boolean }
const root = '/api/v1/delivery/creation';
const quiet = { headers: { 'X-Quiet-Request': 'true' } };
const slow = { timeout: 120000 };
export const creationProducts = (accountId: string, awemeId: string, query: ProductQuery, refreshPrices = false) => apiGet<{ items: CreationProduct[]; total: number }>(`${root}/products`, { timeout: 60000, params: { accountId, awemeId, ...query, refreshPrices } });
export const creationGet = (id: string) => apiGet<CreationState>(`${root}/${id}`, quiet);
export const creationSelect = (body: { planId?: string; revision?: number; accountId: string; awemeId: string; productIds: string[] }) => apiPost<CreationState>(root, body, slow);
export const creationAnalyze = (s: CreationState, refresh = false) => apiPost<CreationState>(`${root}/${s.planId}/analysis`, { revision: s.revision, refresh });
export const creationRecommendations = (id: string) => apiGet<CreationState>(`${root}/${id}/recommendations`, slow);
export const creationGoal = (s: CreationState, body: { name: string; smartBidType: string; budget: number; roi2Goal: number | null; scheduleType: string; startDate: string | null; endDate: string | null }) => apiPut<CreationState>(`${root}/${s.planId}/goal`, { revision: s.revision, ...body });
export const creationStep = (s: CreationState, step: number) => apiPut<CreationState>(`${root}/${s.planId}/step`, { revision: s.revision, step });
export const creationGenerate = (s: CreationState, productIds: string[]) => apiPost<CreationState>(`${root}/${s.planId}/generation`, { revision: s.revision, productIds });
export const creationAddition = (s: CreationState, body: CreationAdditionBody) => apiPost<CreationState>(`${root}/${s.planId}/generation/additions`, { revision: s.revision, ...body });
export const creationMaterialContext = (s: CreationState, id: string) => apiPost<CreationState>(`${root}/${s.planId}/materials/${id}/context`);
export const creationVisibility = (s: CreationState, hideInAweme: boolean) => apiPut<CreationState>(`${root}/${s.planId}/visibility`, { revision: s.revision, hideInAweme });
export const creationAdd = (s: CreationState, body: { productId: string; type: string; videoAssetId?: string; coverAssetId?: string; imageAssetIds?: string[]; carouselId?: string; titles: string[] }) => apiPost<CreationState>(`${root}/${s.planId}/materials`, { revision: s.revision, ...body }, slow);
export const creationFeedback = (s: CreationState, id: string, action: string, body: { reasons: string[]; description: string }) => action === 'delete'
  ? apiDelete<CreationState>(`${root}/${s.planId}/materials/${id}`, { data: { revision: s.revision, ...body } })
  : apiPost<CreationState>(`${root}/${s.planId}/materials/${id}/regenerate`, { revision: s.revision, ...body }, slow);
export const creationConfirm = (s: CreationState) => apiPost<CreationState>(`${root}/${s.planId}/confirm`, { revision: s.revision }, slow);
export const creationPublish = (s: CreationState, requestKey: string) => apiPost<CreationState>(`${root}/${s.planId}/publish`, { revision: s.revision, packageVersion: s.packageVersion, requestKey }, slow);
export const creationDiscard = (id: string) => apiDelete(`${root}/${id}`);

export const creationTitles = (s: CreationState, id: string, titles: string[]) => apiPut<CreationState>(`${root}/${s.planId}/materials/${id}/titles`, { revision: s.revision, titles }, slow);
export const creationRecheck = (s: CreationState, id: string) => apiPost<CreationState>(`${root}/${s.planId}/materials/${id}/recheck`, { revision: s.revision });

export const creationReconcile = (s: CreationState, qcPlanId: string) => apiPost<CreationState>(`${root}/${s.planId}/reconcile`, { qcPlanId }, slow);
