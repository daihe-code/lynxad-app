import type { CarouselDetail, UploadedAsset } from './creativeApi';
import { carouselReady } from './materialSources.ts';

export type CreationCheck = 'PASS' | 'WAITING' | 'FAIL' | 'SKIP';
export interface CreationCheckRow { key: string; label: string; status: CreationCheck; text: string }
const approved = (value?: string) => value === 'APPROVED' || value === 'NOT_REQUIRED';
const failedReview = (value?: string) => ['REJECTED', 'MANUAL_REVIEW', 'FAILED'].includes(value || '');
function review(assets: UploadedAsset[], expected: number): CreationCheck {
  if (assets.some(a => a.storageStatus === 'FAILED' || failedReview(a.aiReview?.status))) return 'FAIL';
  return assets.length === expected && expected > 0 && assets.every(a => a.storageStatus === 'STORED' && approved(a.aiReview?.status)) ? 'PASS' : 'WAITING';
}

/** Completion comes from the plan-write receipt, not merely the component checks. */
export function carouselCreationProgress(value: CarouselDetail | undefined, accountId: string, hasDescription: boolean, selectedImages: number, added = false, attachError = '') {
  const assets = (role: 'TEXT' | 'IMAGE' | 'AUDIO') => value?.parts.filter(p => p.role === role).map(p => p.asset) || [];
  const pictures = assets('IMAGE'), imageCount = pictures.length || selectedImages;
  const checkedImages = pictures.filter(a => review([a], 1) === 'PASS').length;
  const description = hasDescription ? review(assets('TEXT'), 1) : 'SKIP';
  const imageStatus = review(pictures, imageCount), audio = review(assets('AUDIO'), 1);
  const rejected = !!value && failedReview(value.aiReview.status);
  const checksFailed = rejected || [description, imageStatus, audio].includes('FAIL');
  const syncFailed = !!value?.parts.some(p => p.role !== 'TEXT' && p.asset.syncs.some(s => s.accountId === accountId && (['FAILED', 'WAITING_AUTH', 'UNCERTAIN', 'PAUSED'].includes(s.status) || s.reviewStatus === 'REJECTED')));
  const checksPassed = !!value && approved(value.aiReview.status) && [description, imageStatus, audio].every(s => s === 'PASS' || s === 'SKIP');
  const ready = checksPassed && carouselReady(value, accountId);
  const label = added ? '整套图文已添加' : attachError ? '整套图文添加未完成' : checksFailed ? '整套图文检查未通过' : syncFailed ? '整套图文同步未完成' : ready ? '整套图文添加中' : checksPassed ? '整套图文同步中' : '整套图文检查中';
  const rows: CreationCheckRow[] = [
    { key: 'name', label: '图文名称', status: 'PASS', text: '校验已完成' },
    { key: 'description', label: '图文作品描述（选填）', status: description, text: description === 'SKIP' ? '未填写' : description === 'PASS' ? '检测已完成' : description === 'FAIL' ? '检测未通过' : '检测中' },
    { key: 'images', label: '图片', status: imageStatus, text: imageStatus === 'FAIL' ? `${checkedImages}/${imageCount}张检测已完成，部分未通过` : `${checkedImages}/${imageCount}张检测已完成` },
    { key: 'audio', label: '音频', status: audio, text: audio === 'PASS' ? '检测已完成' : audio === 'FAIL' ? '检测未通过' : '检测中' },
    { key: 'sync', label, status: added ? 'PASS' : attachError || checksFailed || syncFailed ? 'FAIL' : 'WAITING', text: '' }
  ];
  return { rows, imageCount, blocked: checksFailed || syncFailed, ready, label };
}
