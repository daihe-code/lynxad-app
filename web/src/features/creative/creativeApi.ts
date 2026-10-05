import { apiGet, apiPost, apiPostForm } from "@/api/http";

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
  type: "VIDEO" | "IMAGE" | "CAROUSEL";
  source: "LYNX" | "EXTERNAL";
  filename: string;
  sizeBytes: number;
  imageMode?: string;
  durationSec?: number | null;
  category: string;
  createdAt: string;
  used: boolean;
  reviewBadge: "NONE" | "REVIEWING" | "REJECTED";
  materialId?: string | null;
  objectId: string;
  posterUrl: string;
  playUrl: string;
  width?: number | null;
  height?: number | null;
}

export interface LibraryResponse {
  qianchuanBound: boolean;
  tabCounts: { video: number; image: number; carousel: number };
  statusCounts: { all: number; used: number; unused: number; reviewing?: number; rejected?: number };
  pageInfo: { page: number; pageSize: number; total: number };
  items: LibraryItemVo[];
}

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
}) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== "") q.set(k, String(v));
  });
  const suffix = q.toString() ? `?${q.toString()}` : "";
  return apiGet<LibraryResponse>(`/api/v1/creative/library${suffix}`);
}

export function replaceItem(packageId: string, productId: string, file: File) {
  const body = new FormData();
  body.append("file", file);
  return apiPostForm<{ packageId: string; confirmed: boolean }>(
    `/api/v1/creative/packages/${packageId}/items/${productId}/replace`,
    body,
  );
}
