import { reactive } from 'vue';
import { cancelAssetUpload, completeAssetUpload, getUploadedAsset, getUploadPolicy, initAssetUpload, uploadAssetFile, type UploadedAsset } from './creativeApi';
import { preflight, targetError, type FileMetadata } from './assetPreflight';
import { libraryFileError, type UploadLibrary } from './assetUploadTypes';

export interface UploadRow { key: string; file: File; library?: UploadLibrary; scope?: string; isAigc?: boolean; progress: number; stage: string; busy: boolean; valid: boolean; stopped?: boolean; uploadAssetId?: string; metadata?: FileMetadata; error: string; result?: UploadedAsset }
/** In-memory transfers outlive a dialog; server-side sync tasks also outlive a browser session. */
export const uploadRows = reactive<UploadRow[]>([]);
let ownerToken = localStorage.getItem('lynx.token');
export function ensureUploadSession() {
  const token = localStorage.getItem('lynx.token');
  if (ownerToken !== token) { uploadRows.splice(0); ownerToken = token; }
}
export async function addUploadFiles(files: FileList | File[] | undefined | null, library?: UploadLibrary, scope?: string, signal?: AbortSignal) {
  ensureUploadSession();
  const errors: string[] = [];
  const candidates = Array.from(files || []).filter(file => {
    const error = libraryFileError(file.name, library);
    if (error) errors.push(error);
    return !error;
  });
  if (!candidates.length) return errors;
  const session = ownerToken;
  let policy;
  try { policy = await getUploadPolicy(); }
  catch { return ['无法加载上传规则，请检查网络后重新选择文件']; }
  ensureUploadSession(); if (session !== ownerToken) return errors;
  const maxFiles = library === 'CAROUSEL' ? Math.max(policy.maxFiles, (policy.carouselMaxImages || 18) + 2) : policy.maxFiles;
  for (const file of candidates) {
    if (signal?.aborted) break;
    if (session !== localStorage.getItem('lynx.token')) break;
    if (uploadRows.filter(r => r.library === library && r.scope === scope).length >= maxFiles) { errors.push(`一次最多选择 ${maxFiles} 个文件`); break; }
    if (uploadRows.some(r => r.library === library && r.scope === scope && r.file.name === file.name && r.file.size === file.size && r.file.lastModified === file.lastModified)) continue;
    const key = crypto.randomUUID();
    uploadRows.push({ key, file, library, scope, progress: 0, stage: '正在本地预检', busy: true, valid: false, error: '' });
    const row = uploadRows.find(r => r.key === key)!;
    try { row.metadata = await preflight(file, policy); row.valid = true; row.stage = '待上传'; }
    catch (error) { row.error = errorMessage(error); row.stage = '预检未通过'; }
    finally { row.busy = false; }
  }
  return errors;
}
export function syncLabel(status: string, reviewStatus = 'UNKNOWN') {
  if (status === 'WAITING_REVIEW') return 'AI 审核通过前暂停同步';
  if (status === 'UPLOADED' && ['REVIEWING', 'REJECTED', 'PASS'].includes(reviewStatus)) return `千川已接收 · ${{ REVIEWING: '审核中', REJECTED: '审核未通过', PASS: '审核已通过' }[reviewStatus as 'REVIEWING' | 'REJECTED' | 'PASS']}`;
  return ({ WAITING_STORAGE: '等待文件入库', QUEUED: '等待千川同步', RUNNING: '正在上传千川', UPLOADED: '千川已接收 · 审核状态待查询', WAITING_AUTH: '等待授权更新', FAILED: '千川同步失败', UNCERTAIN: '千川结果待确认', PAUSED: '已入库 · 千川同步暂停' } as Record<string, string>)[status] || status;
}
export function uploadStage(asset: UploadedAsset) {
  const stored = asset.storageMode === 'oss' ? '已上传平台 OSS' : '已保存到平台';
  if (!asset.aiReview || ['QUEUED', 'RUNNING', 'PENDING'].includes(asset.aiReview.status)) return `${stored} · AI 审核中`;
  if (!['APPROVED','NOT_REQUIRED'].includes(asset.aiReview.status)) return `${stored} · ${reviewLabel(asset.aiReview.status)}`;
  if (asset.kind === 'TEXT') return '已入库，可用于文案';
  if (!asset.syncs.length) return '已入库，未选择千川同步账户';
  if (asset.syncs.every(s => s.status === 'UPLOADED')) return '平台与千川均已上传';
  if (asset.syncs.some(s => ['QUEUED', 'RUNNING'].includes(s.status))) return '已入库，正在同步千川';
  return '已入库，请查看千川同步状态';
}
export function reviewLabel(status?: string) {
  return ({ QUEUED: '等待 AI 审核', RUNNING: 'AI 审核中', PENDING: 'AI 审核中', APPROVED: 'AI 审核已通过', NOT_REQUIRED: 'AI 引擎生成 · 无需上传审核', REJECTED: 'AI 审核未通过', MANUAL_REVIEW: 'AI 建议人工复核 · 暂停同步', FAILED: 'AI 审核失败 · 暂停同步' } as Record<string, string>)[status || 'QUEUED'] || '等待 AI 审核';
}
export function reviewPending(status?: string) { return !!status && ['QUEUED', 'RUNNING', 'PENDING'].includes(status); }
function errorMessage(error: unknown) { const value = error as { message?: string; response?: { data?: { message?: string } } }; return value.response?.data?.message || value.message || '上传失败，请重试'; }
function putPart(url: string, part: Blob, progress: (bytes: number) => void, signal?: AbortSignal): Promise<string> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(new DOMException('已停止上传', 'AbortError')); return; }
    const xhr = new XMLHttpRequest(); xhr.open('PUT', url); xhr.timeout = 10 * 60 * 1000;
    const abort = () => { xhr.abort(); reject(new DOMException('已停止上传', 'AbortError')); };
    signal?.addEventListener('abort', abort, { once: true });
    xhr.onloadend = () => signal?.removeEventListener('abort', abort);
    xhr.upload.onprogress = e => progress(e.loaded);
    xhr.onload = () => { const etag = xhr.getResponseHeader('ETag'); if (xhr.status >= 200 && xhr.status < 300 && etag) resolve(etag.replace(/"/g, '')); else reject(new Error('OSS 上传失败，或跨域配置未暴露 ETag')); };
    xhr.onerror = () => reject(new Error('OSS 连接失败，请检查网络及存储跨域配置'));
    xhr.ontimeout = () => reject(new Error('OSS 分片上传超时，请重试'));
    xhr.send(part);
  });
}
let running = false;
const transfers = new Map<string, AbortController>();
export async function stopUploadRows(keys: string[]) {
  const targets = uploadRows.filter(row => keys.includes(row.key));
  for (const row of targets) { row.stopped = true; row.stage = '已停止'; row.error = ''; transfers.get(row.key)?.abort(); }
  // Stored assets may be shared elsewhere. Stop this batch's consumption of them;
  // only cancel unfinished storage sessions on the server.
  await Promise.allSettled(targets.filter(row => row.uploadAssetId && !row.result).map(row => cancelAssetUpload(row.uploadAssetId!)));
}
/** A reopened dialog shows current work, not results from previous batches. */
export function beginUploadDialog() {
  ensureUploadSession();
  for (let index = uploadRows.length - 1; index >= 0; index--) {
    const row = uploadRows[index]!;
    const pending = !row.stopped && (row.busy || (running && row.valid && !row.result)
      || reviewPending(row.result?.aiReview?.status)
      || row.result?.syncs.some(s => ['QUEUED', 'RUNNING', 'WAITING_STORAGE'].includes(s.status)));
    if (!pending) uploadRows.splice(index, 1);
  }
}
export async function startUploads(accountIds: string[], aigc: boolean, library?: UploadLibrary, keys?: readonly string[]) {
  ensureUploadSession();
  if (running) throw new Error('其他素材正在上传，请稍后重试');
  running = true;
  const session = ownerToken;
  function checkSession() { ensureUploadSession(); if (session !== ownerToken || !session) throw new Error('登录账户已切换，文件传输已停止'); }
  const pending = uploadRows.filter(r => !r.stopped && (keys ? keys.includes(r.key) : !r.scope) && (r.library !== 'CAROUSEL' || library === 'CAROUSEL') && r.valid && !r.result && !r.busy && !libraryFileError(r.file.name, library) && !targetError(r.file, accountIds.length > 0));
  let index = 0;
  async function work() {
    while (index < pending.length) {
      if (session !== localStorage.getItem('lynx.token')) break;
      const row = pending[index++]!; row.busy = true; row.error = ''; row.progress = 0; row.stage = '准备上传';
      const controller = new AbortController(); transfers.set(row.key, controller);
      const check = () => { checkSession(); if (row.stopped) throw new DOMException('已停止上传', 'AbortError'); };
      try {
        check();
        const problem = libraryFileError(row.file.name, library) || targetError(row.file, accountIds.length > 0);
        if (problem) throw new Error(problem);
        // New session after a failed attempt; unfinished server/OSS sessions expire and are cleaned up.
        const init = await initAssetUpload(row.file, crypto.randomUUID(), accountIds, row.isAigc ?? aigc, library, controller.signal);
        row.uploadAssetId = init.assetId;
        check();
        let asset: UploadedAsset;
        if (init.uploadMode === 'NONE') asset = await getUploadedAsset(init.assetId);
        else if (init.uploadMode === 'OSS_MULTIPART' && init.ticket) {
          const { urls, partSize } = init.ticket, parts: { partNumber: number; etag: string }[] = [];
          row.stage = '上传到平台 OSS';
          for (let n = 0; n < urls.length; n++) {
            check();
            const start = n * partSize, blob = row.file.slice(start, Math.min(start + partSize, row.file.size));
            let etag = '';
            for (let attempt = 0; attempt < 3; attempt++) {
              try { check(); etag = await putPart(urls[n]!, blob, loaded => row.progress = Math.min(99, Math.round((start + loaded) / row.file.size * 100)), controller.signal); break; }
              catch (error) { if (row.stopped || attempt === 2) throw error; }
            }
            parts.push({ partNumber: n + 1, etag });
          }
          row.progress = 100; row.stage = '正在合并分片并校验文件';
          check();
          asset = await completeAssetUpload(init.assetId, parts, controller.signal);
        } else {
          row.stage = '上传到平台存储';
          asset = await uploadAssetFile(init.assetId, row.file, percent => { if (row.stopped) return; row.progress = Math.min(100, percent); if (percent >= 100) row.stage = '正在校验文件并入库'; }, controller.signal);
        }
        check(); row.result = asset; row.progress = 100; row.stage = uploadStage(asset);
      } catch (error) { if (!row.stopped) { row.error = errorMessage(error); row.stage = '平台上传失败'; } }
      finally { row.busy = false; transfers.delete(row.key); }
    }
  }
  try { await Promise.all([work(), work()]); } finally { running = false; }
}
let polling = false;
setInterval(async () => {
  ensureUploadSession();
  if (polling) return;
  const rows = uploadRows.filter(r => !r.stopped && r.result && (reviewPending(r.result.aiReview?.status) || r.result.syncs.some(s => ['QUEUED', 'RUNNING', 'WAITING_STORAGE'].includes(s.status))));
  if (!rows.length) return;
  polling = true;
  try { await Promise.all(rows.map(async row => { try { const result = await getUploadedAsset(row.result!.id); if (!row.stopped) { row.result = result; row.stage = uploadStage(result); } } catch { /* Keep the last known state when offline. */ } })); }
  finally { polling = false; }
}, 4000);
