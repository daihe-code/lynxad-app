import type { AssetSync, CarouselDetail, LibraryItemVo, UploadedAsset } from './creativeApi';
import { assetGeometry } from './assetGeometry.ts';

const approved = (value?: string) => value === 'APPROVED' || value === 'NOT_REQUIRED';
const accepted = (sync: AssetSync) => sync.status === 'UPLOADED' && sync.reviewStatus !== 'REJECTED';
export function uploadedReady(asset: UploadedAsset | undefined, accountId: string) {
  return !!asset && asset.storageStatus === 'STORED' && approved(asset.aiReview?.status)
    && asset.syncs.some(sync => sync.accountId === accountId && accepted(sync));
}
export function carouselReady(value: CarouselDetail | undefined, accountId: string) {
  return !!value && approved(value.aiReview.status) && value.accountIds.includes(accountId)
    && value.parts.length > 0 && value.parts.every(part => part.asset.storageStatus === 'STORED' && approved(part.asset.aiReview?.status)
      && (part.role === 'TEXT' || uploadedReady(part.asset, accountId)));
}
export function syncRecoveryPending(sync: AssetSync, until = 0): boolean {
  return sync.reviewStatus !== 'REJECTED' && (sync.status === 'FAILED' && (sync.attempts ?? 3) < 3
    || ['UNCERTAIN','WAITING_AUTH'].includes(sync.status) && until > Date.now());
}
export function uploadPhase(asset: UploadedAsset | undefined, accountId: string, busy = false, error = '', recoveryUntil = 0) {
  if (error) return '上传失败';
  if (!asset) return busy ? '上传中' : '待上传';
  if (asset.storageStatus !== 'STORED') return asset.storageStatus === 'FAILED' ? '上传失败' : '上传中';
  if (!approved(asset.aiReview?.status)) return ['FAILED', 'REJECTED', 'MANUAL_REVIEW'].includes(asset.aiReview?.status || '') ? '检查未通过' : '检查中';
  const sync = asset.syncs.find(s => s.accountId === accountId);
  if (uploadedReady(asset, accountId)) return '已就绪';
  if (sync && syncRecoveryPending(sync, recoveryUntil)) return '同步中';
  if (sync && ['FAILED', 'WAITING_AUTH', 'UNCERTAIN', 'PAUSED'].includes(sync.status) || sync?.reviewStatus === 'REJECTED') return '同步未完成';
  return '同步中';
}
export function uploadedItem(asset: UploadedAsset): LibraryItemVo {
  return { id: `UPLOAD:${asset.id}`, assetId: asset.id, type: asset.kind as LibraryItemVo['type'], source: 'UPLOAD',
    filename: asset.filename, sizeBytes: asset.sizeBytes, durationSec: asset.storageMetadata?.durationMs ? asset.storageMetadata.durationMs / 1000 : undefined,
    width: asset.width, height: asset.height, category: '本地上传', createdAt: asset.createdAt, used: false, reviewBadge: 'NONE',
    objectId: asset.id, posterUrl: asset.kind === 'IMAGE' ? asset.url : '', playUrl: asset.kind === 'VIDEO' ? asset.url : '',
    storageStatus: asset.storageStatus, syncs: asset.syncs, aiReview: asset.aiReview };
}
export function carouselItem(value: CarouselDetail): LibraryItemVo {
  const image = value.parts.find(p => p.role === 'IMAGE')?.asset;
  return { id: `CAROUSEL:${value.id}`, carouselId: value.id, type: 'CAROUSEL', source: 'UPLOAD', filename: value.name,
    sizeBytes: value.parts.reduce((n, p) => n + p.asset.sizeBytes, 0), category: `${value.parts.filter(p => p.role === 'IMAGE').length} 张图片 · 图文配音`,
    createdAt: value.createdAt, used: false, reviewBadge: 'NONE', objectId: value.id, posterUrl: image?.url || '', playUrl: '',
    storageStatus: 'STORED', aiReview: value.aiReview };
}
export function materialMeta(item: LibraryItemVo) {
  if (item.type !== 'VIDEO') return item.type === 'CAROUSEL' ? item.category || '图文' : item.width && item.height ? `${item.width} × ${item.height}` : '图片';
  const duration = item.durationSec && item.durationSec > 0 ? `${Math.round(item.durationSec * 10) / 10} 秒` : '时长待获取';
  return `${duration} · ${assetGeometry(item).label}`;
}
