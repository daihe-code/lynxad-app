import { reactive } from 'vue';
import { apiGet } from '@/api/http';
import type { LibraryItemVo } from './creativeApi';
export type PreviewItem = Pick<LibraryItemVo, 'id' | 'type' | 'assetId' | 'carouselId' | 'posterUrl' | 'playUrl'>;

interface Preview { posterUrl: string; playUrl: string; source: string; message: string }
interface Entry { result?: Preview; pending?: Promise<boolean>; request?: symbol; expires: number }
const entries = reactive(new Map<string, Entry>());
let remoteActive = 0;
const remoteWaiters: Array<() => void> = [];
async function remoteSlot<T>(work: () => Promise<T>): Promise<T> {
  if (remoteActive >= 3 || remoteWaiters.length) await new Promise<void>(resolve => remoteWaiters.push(resolve));
  else remoteActive++;
  try { return await work(); }
  finally {
    const next = remoteWaiters.shift();
    if (next) next(); // Transfer this slot without allowing a fourth concurrent request.
    else remoteActive--;
  }
}
let owner = localStorage.getItem('lynx.token');
function session() {
  const token = localStorage.getItem('lynx.token');
  if (token !== owner) { entries.clear(); owner = token; }
  return token;
}
const key = (item: PreviewItem) => `${item.type}:${item.carouselId || item.assetId || item.id}`;
export function previewNeedsRefresh(item: PreviewItem) {
  session();
  const entry = entries.get(key(item));
  return item.id.startsWith('EXT:') && (!entry?.result || entry.expires <= Date.now());
}
export function assetPreview(item: PreviewItem) {
  session();
  const entry = entries.get(key(item));
  // Keep an already-rendered preview until a fresh one replaces it. A failed URL triggers refresh.
  const result = entry && (entry.expires > Date.now() || item.id.startsWith('EXT:')) ? entry.result : undefined;
  return result || { posterUrl: item.posterUrl, playUrl: item.playUrl, source: 'ORIGINAL', message: '' };
}
export function clearAssetPreviews(items: PreviewItem[]) {
  session();
  for (const item of items) entries.delete(key(item));
}

/** Resolve only the same asset by its local ID or its owned provider catalog identity. */
export async function resolveAssetPreview(item: PreviewItem, refresh = false): Promise<boolean> {
  const token = session(), remote = item.id.startsWith('EXT:'), id = remote ? item.id : item.carouselId || item.assetId;
  if (!id || !['VIDEO', 'IMAGE', 'CAROUSEL'].includes(item.type)) return false;
  const k = key(item), previous = entries.get(k);
  if (previous?.pending) return previous.pending;
  if (!refresh && previous?.result && previous.expires > Date.now()) return previous.result.source === 'QIANCHUAN';
  if (entries.size > 256) entries.clear();
  // Preserve the visible thumbnail while a new short-lived provider link is fetched.
  const request = Symbol(k), entry: Entry = { result: previous?.result, request, expires: Date.now() + 30_000 };
  entries.set(k, entry);
  const pending = (async () => {
    let result: Preview;
    try {
      result = await (remote
        ? remoteSlot(() => apiGet<Preview>(`/api/v1/creative/library/preview?id=${encodeURIComponent(id)}&refresh=${refresh}`, {
          headers: { 'X-Quiet-Request': 'true' },
        }))
        : apiGet<Preview>(`/api/v1/creative/${item.carouselId ? 'carousels' : 'assets'}/${encodeURIComponent(id)}/preview`, {
          headers: { 'X-Quiet-Request': 'true' },
        }));
    } catch {
      result = { posterUrl: '', playUrl: '', source: 'UNAVAILABLE', message: '原文件和千川副本暂不可预览，请检查存储服务及千川授权后刷新' };
    }
    // A late response cannot enter another user's session or overwrite an explicit refresh.
    if (session() !== token || entries.get(k)?.request !== request) return false;
    // Stay below the server cache lifetime. Failed lookups are retried soon, and a
    // provider URL that expires earlier is refreshed immediately by the image error handler.
    const lifetime = result.source === 'QIANCHUAN'
      ? item.type === 'VIDEO' ? 40 * 60_000 : 4 * 60_000
      : 30_000;
    entries.set(k, { result, expires: Date.now() + lifetime });
    return result.source === 'QIANCHUAN';
  })();
  entries.set(k, { ...entry, pending });
  return pending;
}
