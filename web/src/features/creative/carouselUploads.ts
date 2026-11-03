import { reactive } from 'vue';
import { createCarousel, getCarousel, type CarouselDetail } from './creativeApi';
import { addUploadFiles, ensureUploadSession, reviewPending, startUploads, uploadRows } from './assetUploads';
import { targetError } from './assetPreflight';
import { assetFileRules, carouselAudioError } from './assetUploadTypes';

export const carouselDraft = reactive({ name: '', text: '', images: [] as File[], audio: undefined as File | undefined,
  textAigc: undefined as boolean | undefined, fileAigc: {} as Record<string, boolean>,
  accounts: [] as string[], aigc: false, busy: false, error: '', keys: [] as string[], requestId: '', result: undefined as CarouselDetail | undefined });
let owner = localStorage.getItem('lynx.token');
let textFile: File | undefined, fileText = '', bodySignature = '', textRevision = Date.now();
function reset() {
  textFile = undefined; fileText = ''; bodySignature = '';
  Object.assign(carouselDraft, { name: '', text: '', images: [], audio: undefined, textAigc: undefined, fileAigc: {}, accounts: [], aigc: false, busy: false, error: '', keys: [], requestId: '', result: undefined });
}
function session() {
  ensureUploadSession();
  const token = localStorage.getItem('lynx.token');
  if (token !== owner) { owner = token; reset(); }
  return token;
}
export function beginCarouselDialog() {
  session();
  if (!carouselDraft.busy && (!carouselDraft.result || !carouselPending(carouselDraft.result))) {
    for (let i = uploadRows.length - 1; i >= 0; i--) if (carouselDraft.keys.includes(uploadRows[i]!.key)) uploadRows.splice(i, 1);
    reset();
  }
}
export function carouselPending(value: CarouselDetail) {
  return value.parts.some(p => reviewPending(p.asset.aiReview?.status) || p.asset.syncs.some(s => value.accountIds.includes(s.accountId) && ['QUEUED', 'RUNNING', 'WAITING_REVIEW'].includes(s.status)))
    || value.aiReview.status === 'APPROVED' && value.parts.some(p => p.role !== 'TEXT' && value.accountIds.some(id => !p.asset.syncs.some(s => s.accountId === id)));
}
function message(error: unknown) {
  const e = error as { message?: string; response?: { data?: { message?: string } } };
  return e.response?.data?.message || e.message || '图文上传失败，请重试';
}
export function fileAigcKey(file: File) { return JSON.stringify([file.name, file.size, file.lastModified]); }
/** All components use existing OSS/preflight/review flow; the composite owns platform targets. */
export async function uploadCarousel() {
  const token = session(); if (carouselDraft.busy || carouselDraft.result) return;
  carouselDraft.busy = true; carouselDraft.error = '';
  const check = () => { if (!token || token !== localStorage.getItem('lynx.token')) throw new Error('登录账户已切换，上传已停止'); };
  try {
    check();
    if (!carouselDraft.text.trim()) throw new Error('请输入图文文本');
    if (!carouselDraft.images.length || carouselDraft.images.length > assetFileRules.carouselMaxImages) throw new Error(`请选择 1-${assetFileRules.carouselMaxImages} 张图片`);
    if (!carouselDraft.audio) throw new Error('请选择一份音频');
    const audioError = carouselAudioError(carouselDraft.audio); if (audioError) throw new Error(audioError);
    const problem = targetError(carouselDraft.audio, !!carouselDraft.accounts.length); if (problem) throw new Error(problem);
    if (!textFile || fileText !== carouselDraft.text) { fileText = carouselDraft.text; textFile = new File([fileText], '图文文本.txt', { type: 'text/plain', lastModified: ++textRevision }); }
    const files = [textFile, ...carouselDraft.images, carouselDraft.audio];
    const isAigc = (file: File) => file === textFile ? carouselDraft.textAigc ?? carouselDraft.aigc : carouselDraft.fileAigc[fileAigcKey(file)] ?? carouselDraft.aigc;
    for (let i = uploadRows.length - 1; i >= 0; i--) {
      const row = uploadRows[i]!;
      if (row.library === 'CAROUSEL' && !row.busy && (row.result && row.result.isAigc !== isAigc(row.file) || !files.some(f => f.name === row.file.name && f.size === row.file.size && f.lastModified === row.file.lastModified))) uploadRows.splice(i, 1);
    }
    // Validate every part before any transfer. Keep successful rows when a transfer or registration is retried.
    const errors = await addUploadFiles(files, 'CAROUSEL'); check();
    if (errors.length) throw new Error(errors.join('；'));
    const rows = files.map(f => uploadRows.find(r => r.library === 'CAROUSEL' && r.file.name === f.name && r.file.size === f.size && r.file.lastModified === f.lastModified));
    if (rows.some(r => !r?.valid)) throw new Error(rows.find(r => !r?.valid)?.error || '图文组件预检未通过');
    rows.forEach((row, index) => { row!.isAigc = isAigc(files[index]!); });
    carouselDraft.keys = rows.map(r => r!.key);
    await startUploads([], carouselDraft.aigc, 'CAROUSEL', carouselDraft.keys); check();
    if (rows.some(r => !r?.result)) throw new Error(rows.find(r => !r?.result)?.error || '图文组件未全部上传，请重试');
    const images = [...new Set(rows.slice(1, -1).map(r => r!.result!.id))];
    const body = { name: carouselDraft.name.trim() || carouselDraft.images[0]!.name.replace(/\.[^.]+$/, '').slice(0, 100),
      textAssetId: rows[0]!.result!.id, imageAssetIds: images, audioAssetId: rows.at(-1)!.result!.id, accountIds: [...carouselDraft.accounts] };
    const signature = JSON.stringify(body);
    if (signature !== bodySignature) { bodySignature = signature; carouselDraft.requestId = crypto.randomUUID(); }
    carouselDraft.result = await createCarousel({ requestId: carouselDraft.requestId, ...body });
  } catch (error) { if (token === localStorage.getItem('lynx.token')) carouselDraft.error = message(error); }
  finally { if (token === localStorage.getItem('lynx.token')) carouselDraft.busy = false; }
}
let polling = false;
setInterval(async () => {
  const token = session(), result = carouselDraft.result;
  if (polling || !result || !carouselPending(result)) return;
  polling = true;
  try { const updated = await getCarousel(result.id); if (token === localStorage.getItem('lynx.token') && carouselDraft.result?.id === result.id) carouselDraft.result = updated; }
  catch { /* Offline: preserve the last result and resume on the next poll. */ }
  finally { polling = false; }
}, 4000);
