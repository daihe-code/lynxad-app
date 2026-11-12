import type { LibraryItemVo } from "@/features/creative/creativeApi";
import { reviewLabel, syncLabel } from '@/features/creative/assetUploads';
import { assetGeometry } from '@/features/creative/assetGeometry';
export { assetDay, assetDateTime } from '@/features/creative/assetDates';

export function assetUrl(url: string) {
  if (!url || /^(https?:|blob:|data:)/i.test(url)) return url;
  return (import.meta.env.VITE_API_BASE || "/lynx-ad") + url.replace(/^\/lynx-ad/, "");
}
export function duration(seconds: number | null | undefined) {
  if (seconds == null) return "—";
  const n = Math.max(0, Math.round(seconds));
  return `${Math.floor(n / 60)}:${String(n % 60).padStart(2, "0")}`;
}
export function fileSize(bytes: number) {
  if (!bytes) return "—";
  if (bytes < 1024) return `${bytes}B`;
  return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)}KB` : `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}
export function sourceLabel(item: LibraryItemVo) {
  return item.source === "UPLOAD" ? "本地上传" : item.source === "LYNX" ? "坤境原创" : "千川素材";
}
export function assetStatus(item: LibraryItemVo) {
  if (item.carouselId) {
    const status = item.aiReview?.status;
    if (!status || status === 'NOT_REQUIRED') return { cls: 'ac-use--off', label: '' };
    return { cls: status === 'APPROVED' ? 'ac-use--on' : ['REJECTED', 'FAILED', 'MANUAL_REVIEW'].includes(status || '') ? 'ac-use--bad' : 'ac-use--wait',
      label: status === 'APPROVED' ? '图文质检合格' : status === 'FAILED' ? '图文质检失败' : ['REJECTED', 'MANUAL_REVIEW'].includes(status || '') ? '图文不合格' : '图文质检中' };
  }
  if (item.aiReview && !['APPROVED','NOT_REQUIRED'].includes(item.aiReview.status)) return { cls: ['REJECTED', 'FAILED'].includes(item.aiReview.status) ? 'ac-use--bad' : 'ac-use--wait', label: reviewLabel(item.aiReview.status) };
  if (item.reviewBadge === "REVIEWING") return { cls: "ac-use--wait", label: "审核中" };
  if (item.reviewBadge === "REJECTED") return { cls: "ac-use--bad", label: "审核未通过" };
  if (item.used) return { cls: "ac-use--on", label: "已使用" };
  if (item.usageKnown === false) return { cls: "ac-use--wait", label: "使用情况待同步" };
  if (item.assetId) {
    if (item.type === 'TEXT') return { cls: 'ac-use--off', label: '已入库，可用于文案' };
    const syncs = item.syncs || [];
    if (syncs.some(s => ['RUNNING', 'QUEUED'].includes(s.status))) return { cls: 'ac-use--wait', label: '已入库 · 千川同步中' };
    if (syncs.some(s => s.status === 'UNCERTAIN')) return { cls: 'ac-use--wait', label: '已入库 · 千川结果待确认' };
    if (syncs.some(s => s.status === 'WAITING_AUTH')) return { cls: 'ac-use--wait', label: '已入库 · 等待授权' };
    if (syncs.some(s => s.status === 'FAILED')) return { cls: 'ac-use--bad', label: '已入库 · 千川同步失败' };
    if (syncs.some(s => s.status === 'PAUSED')) return { cls: 'ac-use--wait', label: '已入库 · 千川同步暂停' };
    if (!syncs.length) return { cls: 'ac-use--off', label: '已入库' };
    if (!item.used) return { cls: 'ac-use--off', label: '' };
  }
  return item.used ? { cls: "ac-use--on", label: "已使用" } : { cls: "ac-use--off", label: "未使用" };
}

export function assetIdentifier(item: LibraryItemVo) {
  return item.materialId || item.objectId || item.id;
}

export function assetSyncLabel(status: string, reviewStatus = 'UNKNOWN') {
  if (status === 'UPLOADED') return ({ REVIEWING: '审核中', REJECTED: '审核未通过', PASS: '审核已通过' } as Record<string, string>)[reviewStatus] || '审核状态待查询';
  return syncLabel(status, reviewStatus);
}
export function assetRatio(item: LibraryItemVo) {
  return assetGeometry(item).ratioLabel || '—';
}
export function assetDimensions(item: LibraryItemVo) {
  const {width,height} = assetGeometry(item);
  return width && height ? `${width}×${height}` : "—";
}
