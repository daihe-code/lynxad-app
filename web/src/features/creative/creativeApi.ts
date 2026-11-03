import { apiDelete, apiGet, apiPost, apiPostForm, type ApiRequestConfig } from "@/api/http";

export type MaterialType = 'VIDEO' | 'IMAGE';
export interface MaterialFeedback { reasons: string[]; description: string }
export function regenerateMaterial(packageId: string, productId: string, type: MaterialType, feedback?: MaterialFeedback) {
  return apiPost<{ taskId: string }>(`/api/v1/creative/packages/${encodeURIComponent(packageId)}/items/${encodeURIComponent(productId)}/regenerate`, { type, ...feedback });
}
export function deleteMaterial(packageId: string, productId: string, type: MaterialType, feedback?: MaterialFeedback) {
  return apiDelete(`/api/v1/creative/packages/${encodeURIComponent(packageId)}/items/${encodeURIComponent(productId)}?type=${type}`, { data: feedback });
}

export interface PackageItemVo {
  productId: string;
  playUrl?: string;
  posterUrl?: string;
  titles?: string[];
}

export interface TaskVo {
  taskId: string;
  status: string;
  steps: string[];
  packageId?: string;
  errorMsg?: string;
  items?: PackageItemVo[];
}

export interface LibraryVideoVo {
  videoId: string;
  productId: string;
  coverImageId?: string;
}

export interface LibraryItemVo {
  id: string;
  type: "VIDEO" | "IMAGE" | "CAROUSEL" | "AUDIO" | "TEXT";
  source: "LYNX" | "EXTERNAL" | "UPLOAD";
  filename: string;
  sizeBytes: number;
  imageMode?: string;
  durationSec?: number | null;
  category: string;
  createdAt: string | null;
  used: boolean;
  usageKnown?: boolean;
  references?: { planId: string; name: string; status: string }[];
  reviewBadge: "NONE" | "REVIEWING" | "REJECTED";
  materialId?: string | null;
  objectId: string;
  posterUrl: string;
  playUrl: string;
  width?: number | null;
  height?: number | null;
  assetId?: string;
  carouselId?: string;
  storageStatus?: string;
  syncs?: AssetSync[];
  aiReview?: AssetReview | null;
}

export interface LibraryResponse {
  usagePending?: boolean;
  qianchuanBound: boolean;
  tabCounts: { video: number; image: number; carousel: number; audio: number; text: number };
  statusCounts: { all: number; used: number; unused: number; reviewing?: number; rejected?: number };
  pageInfo: { page: number; pageSize: number; total: number };
  items: LibraryItemVo[];
  platformWarning?: string;
  relatedCount?: number;
  productScope?: 'RELATED' | 'ALL';
}

export interface AssetReview {
  status: 'QUEUED' | 'RUNNING' | 'PENDING' | 'APPROVED' | 'NOT_REQUIRED' | 'REJECTED' | 'MANUAL_REVIEW' | 'FAILED';
  reviewId?: string; decision?: string; summary?: string; reasons: string[]; errorMessage?: string;
}
export interface AssetSync {
  id: string; accountId: string; advertiserId?: string; status: 'WAITING_STORAGE' | 'WAITING_REVIEW' | 'QUEUED' | 'RUNNING' | 'UPLOADED' | 'WAITING_AUTH' | 'FAILED' | 'UNCERTAIN' | 'PAUSED';
  objectId?: string; materialId?: string; requestId?: string; reviewStatus: string; attempts: number; errorMessage?: string;
  verification?: { state: string; checkedAt?: string; message?: string };
}
export interface AssetStorageMetadata {
  bucket?: string; objectKey?: string; endpoint?: string; pathVersion: string;
  origin: 'LOCAL_UPLOAD' | 'AI_GENERATED'; durationMs?: number; etag?: string; previewAssetId?: string;
}
export interface UploadedAsset {
  id: string; kind: string; filename: string; sizeBytes: number; storageStatus: string; storageMode: string;
  isAigc: boolean; md5?: string; width?: number; height?: number; textContent?: string; url: string; errorMessage?: string; createdAt: string; syncs: AssetSync[]; aiReview?: AssetReview | null; storageMetadata?: AssetStorageMetadata;
}
export interface UploadInit { assetId: string; storageStatus: string; uploadMode: 'NONE' | 'PROXY' | 'OSS_MULTIPART'; ticket?: { uploadId: string; partSize: number; urls: string[] } }
export interface CarouselDetail {
  id: string; name: string; createdAt: string; accountIds: string[]; aiReview: AssetReview;
  parts: { role: 'TEXT' | 'IMAGE' | 'AUDIO'; position: number; asset: UploadedAsset }[];
}
export function createCarousel(body: { requestId: string; name: string; textAssetId: string; imageAssetIds: string[]; audioAssetId: string; accountIds: string[] }) {
  return apiPost<CarouselDetail>('/api/v1/creative/carousels', body);
}
export function getCarousel(id: string) {
  return apiGet<CarouselDetail>(`/api/v1/creative/carousels/${encodeURIComponent(id)}`, { headers: { 'X-Quiet-Request': 'true' } });
}
export interface UploadPolicy {
  files: { extension: string; kind: string; maxBytes: number; qianchuanSupported: boolean }[];
  maxPixels: number; maxTextChars: number; maxFiles: number; videoCodec: string; m4aCodec: string; carouselMaxImages?: number;
}
export function getUploadPolicy() { return apiGet<UploadPolicy>('/api/v1/creative/assets/upload-policy', { headers: { 'X-Quiet-Request': 'true' } }); }
export function initAssetUpload(file: File, requestId: string, accountIds: string[], isAigc: boolean, libraryType?: LibraryItemVo['type']) {
  return apiPost<UploadInit>('/api/v1/creative/assets/uploads/init', { requestId, filename: file.name, sizeBytes: file.size, accountIds, isAigc, libraryType }, { headers: { 'X-Quiet-Request': 'true' } });
}
export function uploadAssetFile(id: string, file: File, progress: (percent: number) => void) {
  const body = new FormData(); body.append('file', file);
  return apiPostForm<UploadedAsset>(`/api/v1/creative/assets/${id}/file`, body, { timeout: 15 * 60 * 1000, headers: { 'X-Quiet-Request': 'true' }, onUploadProgress: e => progress(Math.round(e.loaded / (e.total || file.size) * 100)) });
}
export function completeAssetUpload(id: string, parts: { partNumber: number; etag: string }[]) {
  return apiPost<UploadedAsset>(`/api/v1/creative/assets/${id}/upload-complete`, { parts }, { timeout: 15 * 60 * 1000, headers: { 'X-Quiet-Request': 'true' } });
}
export function getUploadedAsset(id: string) { return apiGet<UploadedAsset>(`/api/v1/creative/assets/${id}`, { headers: { 'X-Quiet-Request': 'true' } }); }
export function retryAssetReview(id: string) {
  return apiPost<UploadedAsset>(`/api/v1/creative/assets/${id}/review/retry`, {}, { timeout: 15 * 60 * 1000 });
}
export function retryAssetSync(id: string, accountId?: string) { return apiPost<UploadedAsset>(`/api/v1/creative/assets/${id}/sync/retry`, { accountId }); }
export interface AssetSyncVerification { state: string; message: string; asset: UploadedAsset }
export function reconcileAssetSync(id: string, accountId: string) { return apiPost<AssetSyncVerification>(`/api/v1/creative/assets/${id}/sync/reconcile`, { accountId }, { timeout: 120000, headers: { 'X-Quiet-Request': 'true' } }); }

export function generateCreatives(planId: string | undefined, productIds: string[]) {
  return apiPost<{ taskId: string }>("/api/v1/creative/creatives/generate", {
    planId,
    productIds,
    mode: "STANDARD",
  });
}

export function getCreativeTask(taskId: string) {
  return apiGet<TaskVo>(`/api/v1/creative/creatives/tasks/${taskId}`);
}

export function confirmPackage(id: string) {
  return apiPost<{ packageId: string; confirmed: boolean }>(`/api/v1/creative/packages/${id}/confirm`);
}

export function listLibrary() {
  return apiGet<LibraryVideoVo[]>("/api/v1/creative/library/videos");
}

export function listLibraryFull(params: {
  type?: string;
  platform?: string;
  from?: string;
  to?: string;
  status?: string;
  q?: string;
  page?: number;
  pageSize?: number;
  selectable?: boolean;
  accountId?: string;
  productId?: string;
  productScope?: 'AUTO' | 'RELATED' | 'ALL';
}, config?: ApiRequestConfig) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== "") q.set(k, String(v));
  });
  const suffix = q.toString() ? `?${q.toString()}` : "";
  return apiGet<LibraryResponse>(`/api/v1/creative/library${suffix}`, config);
}

export function replaceItem(packageId: string, productId: string, file: File) {
  const body = new FormData();
  body.append("file", file);
  return apiPostForm<{ packageId: string; confirmed: boolean }>(
    `/api/v1/creative/packages/${packageId}/items/${productId}/replace`,
    body,
  );
}
