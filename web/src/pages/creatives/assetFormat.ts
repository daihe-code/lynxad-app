import type { LibraryItemVo } from "@/features/creative/creativeApi";
import { reviewLabel } from '@/features/creative/assetUploads';

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
export function assetDay(iso: string) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : `${d.getMonth() + 1}月${d.getDate()}日`;
}
export function sourceLabel(item: LibraryItemVo) {
  return item.source === "UPLOAD" ? "本地上传" : item.source === "LYNX" ? "灵投原创" : "千川素材";
}
export function assetStatus(item: LibraryItemVo) {
  if (item.carouselId) {
    const status = item.aiReview?.status;
    return { cls: status === 'APPROVED' ? 'ac-use--on' : ['REJECTED', 'FAILED', 'MANUAL_REVIEW'].includes(status || '') ? 'ac-use--bad' : 'ac-use--wait',
      label: status === 'APPROVED' ? '图文质检合格' : status === 'FAILED' ? '图文质检失败' : ['REJECTED', 'MANUAL_REVIEW'].includes(status || '') ? '图文不合格' : '图文质检中' };
  }
  if (item.aiReview && !['APPROVED','NOT_REQUIRED'].includes(item.aiReview.status)) return { cls: ['REJECTED', 'FAILED'].includes(item.aiReview.status) ? 'ac-use--bad' : 'ac-use--wait', label: reviewLabel(item.aiReview.status) };
  if (item.reviewBadge === "REVIEWING") return { cls: "ac-use--wait", label: "审核中" };
  if (item.reviewBadge === "REJECTED") return { cls: "ac-use--bad", label: "审核未通过" };
  if (item.assetId) {
    if (item.type === 'TEXT') return { cls: 'ac-use--off', label: '已入库，可用于文案' };
    const syncs = item.syncs || [];
    if (syncs.some(s => ['RUNNING', 'QUEUED'].includes(s.status))) return { cls: 'ac-use--wait', label: '已入库 · 千川同步中' };
    if (syncs.some(s => s.status === 'UNCERTAIN')) return { cls: 'ac-use--wait', label: '已入库 · 千川结果待确认' };
    if (syncs.some(s => s.status === 'WAITING_AUTH')) return { cls: 'ac-use--wait', label: '已入库 · 等待授权' };
    if (syncs.some(s => s.status === 'FAILED')) return { cls: 'ac-use--bad', label: '已入库 · 千川同步失败' };
    if (syncs.some(s => s.status === 'PAUSED')) return { cls: 'ac-use--wait', label: '已入库 · 千川同步暂停' };
    if (!syncs.length) return { cls: 'ac-use--off', label: '已入库' };
    if (!item.used) return { cls: 'ac-use--off', label: '千川已接收' };
  }
  return item.used ? { cls: "ac-use--on", label: "使用中" } : { cls: "ac-use--off", label: "未使用" };
}
export function assetRatio(item: LibraryItemVo) {
  if (item.width && item.height) {
    const gcd = (a: number, b: number): number => b ? gcd(b, a % b) : a;
    const divisor = gcd(item.width, item.height);
    return `${item.width / divisor}:${item.height / divisor}`;
  }
  const mode = item.imageMode || "";
  return mode.includes("VERTICAL") ? "9:16" : mode.includes("HORIZONTAL") ? "16:9" : mode.includes("SQUARE") ? "1:1" : "—";
}
export function assetDimensions(item: LibraryItemVo) {
  return item.width && item.height ? `${item.width}×${item.height}` : "—";
}
