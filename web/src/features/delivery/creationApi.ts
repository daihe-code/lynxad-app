import { apiDelete, apiGet, apiPost, apiPut } from '@/api/http';
import type { PlatformProductPrice } from '@/features/catalog/productPrice';
import type { ProductSelectionAnalysis } from './productSelectionAnalysis';

export interface CreationProduct {
  productId: string; qcProductId: string; name: string; price: number | null; userPrice: number | null;
  imageUrl?: string; category?: string; stock?: number; sales?: number | null; tags: string[]; unavailableReasons: string[];
  priceInfo?: PlatformProductPrice | null; channelId?: string | null;
  draft?: { planId: string; planName: string } | null;
}
export interface ProductSuggestions {
  accountId: string; awemeId: string; sourceProductIds: string[];
  items: { product: CreationProduct; reasons: string[]; matchedProductIds: string[] }[];
  scannedCount: number; catalogTotal: number; partial: boolean; cached: boolean;
  fetchedAt: string; method: string; message: string;
}
export interface CreationMaterial {
  engineJobId?: string;
  id: string; productId: string; type: 'VIDEO' | 'IMAGE' | 'CAROUSEL'; title: string; text?: string; displayName?: string;
  status: string; error?: string; previewUrl?: string; posterUrl?: string; engineMaterialId?: string;
  titles: string[]; imageUrls?: string[];
    width?: number | null; height?: number | null; durationSec?: number | null;
  generationRequestId?: string; generationDescription?: string;
  creativeProfile?: Record<string, unknown>; creativeProfileStatus?: string;
  deliverySource?: 'product_reference' | 'ai_generated';
  deliveryMode?: 'full' | 'simplified' | 'image_only'; deliveryFamilyId?: string;
  typographyStatus?: 'complete' | 'core_only' | 'pending';
  processing?: MaterialProcessing;
  source?: 'LYNX' | 'UPLOAD';
  addedVia?: 'LIBRARY' | 'UPLOAD' | null;
}
export interface MaterialProcessing {
  generationStatus: string; storageStatus: string; reviewStatus: string; syncStatus: string;
  stage: string; message?: string; canRetrySync: boolean;
  components: { component: string; storageStatus: string; reviewStatus: string; syncStatus: string; message?: string }[];
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
  estimated_at?: string | null;
  source?: string;
  assumptions?: string[];
  scenario?: { average_order_value: number; click_cost_range: [number, number]; conversion_rate_range: [number, number]; formula: string; basis: 'assumed' };
  missing_reason?: string | null;
  evidence_ids?: string[];
  evidence?: { platform?: string; scope?: string };
}
export interface ProductAnalysis {
  selection_analysis?: ProductSelectionAnalysis;
  analysis_input?: {
    sources: { source_id: string; source_type: string; provider: string; retrieved_at: string; verified: boolean; url: string | null; scope?: { product_ids?: string[] } }[];
    evidence: { evidence_id: string; source_id: string; metric_definition: string; observed_period: string | null; field?: string; value?: unknown; statement?: string }[];
  };
  preparation?: {
    version: string; summary: string;
    products: { product_id: string; name: string; source: string; demand_scene: string; material_direction: string; confirmations: string[] }[];
    group_suggestion: { groups: { name: string; product_ids: string[]; reason: string }[]; needs_confirmation: boolean };
    market_reference: { status: string; message: string; records?:{knowledge_id:string;label:string;value:number;unit:string;platform:string;scope:string;observed_at:string;title:string;source:string;url?:string|null}[] }; disclaimer: string;
  };
  overall_conclusion?: string;
  dimensions?: Partial<Record<ProductAnalysisDimensionKey, ProductAnalysisDimension>>;
  recommendations?: string[];
  public_sources?: { id: string; title: string; url: string; published_at?: string | null }[];
  data_quality?: { status: 'complete' | 'partial' | 'insufficient'; available_dimensions: number; total_dimensions: number; retryable?: boolean; product_estimated_dimensions?: ProductAnalysisDimensionKey[] };
  data_freshness?: { searched_at?: string; notes?: string };
  processing?: { web_search_triggered?: boolean; web_search_executed?: boolean; web_search_used?: boolean; web_search_error?: string | null; inference_attempted?: boolean; inference_status?: 'SUCCEEDED' | 'FAILED'; inference_error?: string | null };
  snapshot?: { created_at?: string; cache_hit?: boolean; version?: number };
}
export interface ProductBasicIssue {
  productId: string | null; productName: string | null;
  severity: 'ERROR' | 'WARNING'; code: string; message: string;
}
export interface ProductBasicAnalysis {
  status: 'PASSED' | 'BLOCKED'; canContinue: boolean; checkedAt: string; fingerprint: string;
  products: { productId: string; productName: string; issues: ProductBasicIssue[] }[];
  issues: ProductBasicIssue[];
}
export interface CreationState {
  materialOutcome?: { ready:number; recovering:number; failed:number; generatedFailure:boolean };
  materialVersions?: { sourceId: string; material: CreationMaterial; decision?: string }[];
  materialRevision?: { requestId: string; sourceId: string; materialId?: string; status: string } | null;
  engineJobId?: string;
  regeneration?: { requestId?: string; resultMaterialId?: string };
  generationRetry?: { requestId?: string };
  groupingDecision?: 'KEEP_CURRENT';
  publishRecovery?: 'REFRESH_ESTIMATE' | 'PRODUCTS' | 'GOAL' | 'MATERIALS' | 'ACCOUNTS' | 'UNKNOWN';
  titlePools?: Record<string,ProductTitlesPool>;
  titleGeneration?: TitleGeneration;
  planId: string; accountId: string; awemeId: string; name: string; revision: number; step: number;
  reachedStep?: number;
  packageVersion: number; confirmedVersion?: number; smartBidType: 'CUSTOM' | 'CONSERVATIVE';
  budget?: number; roi2Goal?: number; scheduleType: string; startDate?: string; endDate?: string; hideInAweme: boolean;
  products: CreationProduct[]; analysisStatus: string; analysisError?: string;
  analysis?: ProductAnalysis;
  basicAnalysis?: ProductBasicAnalysis; analysisStartedAt?: number;
  recommendations?: { budget?: Record<string, unknown>; roi?: Record<string, unknown>; budgetError?: string; roiError?: string; fetchedAt?: string; accountId?: string; awemeId?: string; productIds?: string[]; budgetFromCache?: boolean; budgetFetchedAt?: string; budgetStatus?: string; roiStatus?: string; budgetCode?: number; roiCode?: number; budgetRequestId?: string; roiRequestId?: string };
  generationStatus: string; generationMessage?: string; progress: number; generationProductIds: string[];
  generationSelection?: string[] | null;
  generationPendingProductIds?: string[];
  selectionChange?: { addedNames: string[]; removedNames: string[]; retainedMaterials: number; removedMaterials: number };
  regenerationRetryAt?: number;
  materialEdit?: { status: string; message: string } | null;
  generationStage?: string;
  generationResultStatus?: string;
  generationCounts?: { type: 'VIDEO' | 'IMAGE' | 'CAROUSEL'; completed: number; total: number | null; failed?: number }[];
  generationIssues?: { productId: string; productName: string; message: string; type?: 'VIDEO' | 'IMAGE' | 'CAROUSEL' | 'TITLE' | null; materialId?: string | null; title?: string | null; slotId?: string | null }[];
  productSelectionLocked?:boolean;
  contentEdit?: {targetPlanId:string;status:string;message?:string;planStatus?:string}; contentEditDraftId?:string;
  materials: CreationMaterial[]; publishStatus: string; publishKey?: string; qcPlanId?: string; publishError?: string;
  generationAdditions?: { id: string; productId: string; type: string; description: string; status: string; createdAt: string; materialId?: string; error?: string }[];
}
export interface CreationAdditionBody { productId: string; type: 'VIDEO' | 'IMAGE' | 'CAROUSEL'; description: string; requestId: string }
export type GenerationIssue = NonNullable<CreationState['generationIssues']>[number];
export interface ProductQuery { page: number; pageSize: number; keyword: string; tab: string; unpromoted: boolean; cursor?: string }
export interface ProductPage { items: CreationProduct[]; total: number; nextCursor?: string | null; hasMore?: boolean }
const root = '/api/v1/delivery/creation';
const quiet = { headers: { 'X-Quiet-Request': 'true' } };
const slow = { timeout: 120000 };
export const creationRestoreProducts = (accountId: string, awemeId: string, productIds: string[], draftId?: string) => apiGet<CreationProduct[]>(`${root}/products/restore`, {
  ...quiet, timeout: 60000, params: { accountId, awemeId, productIds: productIds.join(','), draftId },
});
export const creationProducts = (accountId: string, awemeId: string, query: ProductQuery, refreshPrices = false, signal?: AbortSignal, draftId?: string) => apiGet<ProductPage>(`${root}/products`, { ...quiet, signal, timeout: 60000, params: { accountId, awemeId, ...query, refreshPrices, draftId } });
export const creationProductSuggestions = (accountId: string, awemeId: string, productIds: string[], draftId?: string, refresh = false, signal?: AbortSignal, excludeProductIds?: string[]) => apiGet<ProductSuggestions>(`${root}/products/suggestions`, {
  ...quiet, timeout: 90000, readRetry: false, signal, params: { accountId, awemeId, productIds: productIds.join(','), excludeProductIds:excludeProductIds?.join(','), draftId, refresh },
});
export const creationGet = (id: string, signal?: AbortSignal) => apiGet<CreationState>(`${root}/${id}`, {...quiet,signal});
export const beginContentEdit = (id:string) => apiPost<CreationState>(`${root}/${id}/content-edit`,{},slow);
export const saveContentEdit = (s:CreationState) => apiPost<CreationState>(`${root}/${s.planId}/content-edit/save`,{revision:s.revision},slow);
export const verifyContentEdit = (s:CreationState) => apiPost<CreationState>(`${root}/${s.planId}/content-edit/verify`,{},slow);
export const creationSelect = (body: { planId?: string; revision?: number; accountId: string; awemeId: string; productIds: string[] }) => apiPost<CreationState>(root, body, slow);
export const creationAnalyze = (s: CreationState, refresh = false) => apiPost<CreationState>(`${root}/${s.planId}/analysis`, { revision: s.revision, refresh });
export const creationKeepGroup = (s:CreationState) => apiPut<CreationState>(`${root}/${s.planId}/groups/keep`, {revision:s.revision});
export const creationRecommendations = (id: string, refresh = false) => apiGet<CreationState>(`${root}/${id}/recommendations`, { ...slow, params: { refresh } });
export const creationGoal = (s: CreationState, body: { name: string; smartBidType: string; budget: number; roi2Goal: number | null; scheduleType: string; startDate: string | null; endDate: string | null }) => apiPut<CreationState>(`${root}/${s.planId}/goal`, { revision: s.revision, ...body });
export const creationStep = (s: CreationState, step: number) => apiPut<CreationState>(`${root}/${s.planId}/step`, { revision: s.revision, step });
export const creationGenerate = (s: CreationState, productIds: string[]) => apiPost<CreationState>(`${root}/${s.planId}/generation`, { revision: s.revision, productIds });
export const creationRetryGeneration = (s: CreationState, type: 'VIDEO'|'IMAGE'|'CAROUSEL', issue?: GenerationIssue) => apiPost<CreationState>(`${root}/${s.planId}/generation/retry`, { revision:s.revision,type, ...(issue ? {productId:issue.productId,slotId:issue.slotId} : {}) }, slow);
export const creationDeleteGenerationFailure = (s: CreationState, issue: GenerationIssue) => apiDelete<CreationState>(`${root}/${s.planId}/generation/failures`, { data: {revision:s.revision,productId:issue.productId,type:issue.type,slotId:issue.slotId} });
export const creationAddition = (s: CreationState, body: CreationAdditionBody) => apiPost<CreationState>(`${root}/${s.planId}/generation/additions`, { revision: s.revision, ...body });
export const creationMaterialContext = (s: CreationState, id: string) => apiPost<CreationState>(`${root}/${s.planId}/materials/${id}/context`);
export type VisibilitySetting = Pick<CreationState,'planId'|'revision'|'packageVersion'|'confirmedVersion'|'hideInAweme'>;
export const creationVisibility = (s: CreationState, hideInAweme: boolean) => apiPut<VisibilitySetting>(`${root}/${s.planId}/visibility/setting`, { revision: s.revision, hideInAweme }, {headers:{'X-Quiet-Request':'true'}});
export const creationAdd = (s: CreationState, body: { productId: string; type: string; videoAssetId?: string; coverAssetId?: string; imageAssetIds?: string[]; carouselId?: string; titles?: string[]; addedVia?: 'LIBRARY' | 'UPLOAD' }) => apiPost<CreationState>(`${root}/${s.planId}/materials`, { revision: s.revision, ...body }, slow);
export const creationFeedback = (s: CreationState, id: string, action: string, body: { reasons: string[]; description: string }) => action === 'delete'
  ? apiDelete<CreationState>(`${root}/${s.planId}/materials/${id}`, { data: { revision: s.revision, ...body } })
  : apiPost<CreationState>(`${root}/${s.planId}/materials/${id}/regenerate`, { revision: s.revision, ...body }, slow);
export const creationResolveVersion = (s: CreationState, id: string, decision: 'REPLACE' | 'KEEP_BOTH' | 'DISCARD') => apiPost<CreationState>(`${root}/${s.planId}/materials/${id}/version-decision`, { revision: s.revision, decision }, slow);
export const creationConfirm = (s: CreationState) => apiPost<CreationState>(`${root}/${s.planId}/confirm`, { revision: s.revision }, slow);
export const creationPublish = (s: CreationState, requestKey: string) => apiPost<CreationState>(`${root}/${s.planId}/publish`, { revision: s.revision, packageVersion: s.packageVersion, requestKey }, slow);
export const creationDiscard = (id: string) => apiDelete(`${root}/${id}`);

export const creationTitles = (s: CreationState, id: string, titles: string[]) => apiPut<CreationState>(`${root}/${s.planId}/materials/${id}/titles`, { revision: s.revision, titles }, slow);
export const creationProductTitles = (s: CreationState, product: string, kind: 'CUSTOM'|'CARD', titles: string[], quiet=false) => apiPut<CreationState>(`${root}/${s.planId}/products/${encodeURIComponent(product)}/titles`, { revision:s.revision,kind,titles }, quiet?{...slow,headers:{'X-Quiet-Request':'true'}}:slow);
export interface ProductTitlesPool {customTitles:string[];cardTitles:string[];customGeneratedTitles?:string[];cardGeneratedTitles?:string[]}
export interface TitleGeneration {status:string;message?:string;requestId?:string;productId?:string;kind?:'CUSTOM'|'CARD';addition?:boolean}
export const creationRegenerateTitle = (s: CreationState, product: string, kind: 'CUSTOM'|'CARD', index:number, original:string) => apiPost<CreationState>(`${root}/${s.planId}/products/${encodeURIComponent(product)}/titles/regenerate`, {revision:s.revision,kind,index,original},slow);
export const creationGenerateTitle = (s: CreationState, product: string, kind: 'CUSTOM'|'CARD', requestId: string) => apiPost<CreationState>(`${root}/${s.planId}/products/${encodeURIComponent(product)}/titles/generate`, {revision:s.revision,kind,requestId},{...slow,headers:{'X-Quiet-Request':'true'}});
export const creationRecheck = (s: CreationState, id: string) => apiPost<CreationState>(`${root}/${s.planId}/materials/${id}/recheck`, { revision: s.revision });
export const creationRetrySync = (s: CreationState, id: string) => apiPost<CreationState>(`${root}/${s.planId}/materials/${id}/sync/retry`, { revision:s.revision });
export interface MaterialSyncVerification { materialId: string; revision: number; state: string; message: string; processing: MaterialProcessing }
export const creationVerifySync = (s: CreationState, id: string) => apiPost<MaterialSyncVerification>(`${root}/${s.planId}/materials/${id}/sync/reconcile`, { revision:s.revision }, { ...slow, ...quiet });
export const creationTypography = (s: CreationState, id: string) => apiPost<CreationState>(`${root}/${s.planId}/materials/${id}/typography`, { revision: s.revision }, slow);

export const creationReconcile = (s: CreationState, qcPlanId: string) => apiPost<CreationState>(`${root}/${s.planId}/reconcile`, { qcPlanId }, slow);
