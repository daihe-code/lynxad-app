import { reactive } from 'vue';
import { apiGet } from '@/api/http';
import type { LibraryItemVo } from './creativeApi';
export type PreviewItem = Pick<LibraryItemVo, 'id' | 'type' | 'assetId' | 'carouselId' | 'posterUrl' | 'playUrl'>;

interface Preview { posterUrl: string; playUrl: string; source: string; message: string }
interface Entry { result?: Preview; pending?: Promise<boolean>; request?: symbol; expires: number }
const entries = reactive(new Map<string, Entry>());
let owner = localStorage.getItem('lynx.token');
function session() {
  const token = localStorage.getItem('lynx.token');
  if (token !== owner) { entries.clear(); owner = token; }
  return token;
}
const key = (item: PreviewItem) => `${item.type}:${item.carouselId || item.assetId || item.id}`;
export function assetPreview(item: PreviewItem) {
  session();
  const entry = entries.get(key(item));
  const result = entry && entry.expires > Date.now() ? entry.result : undefined;
  return result || { posterUrl: item.posterUrl, playUrl: item.playUrl, source: 'ORIGINAL', message: '' };
}
export function clearAssetPreviews(items: PreviewItem[]) {
  session();
  for (const item of items) entries.delete(key(item));
}

/** Resolve only after the original media fails; never substitute a different asset. */
export async function resolveAssetPreview(item: PreviewItem): Promise<boolean> {
  const token = session(), id = item.carouselId || item.assetId;
  if (!id || !['VIDEO', 'IMAGE', 'CAROUSEL'].includes(item.type)) return false;
  const k = key(item), previous = entries.get(k);
  if (previous?.pending) return previous.pending;
  if (previous?.result && previous.expires > Date.now()) return previous.result.source === 'QIANCHUAN';
  if (entries.size > 256) entries.clear();
  const request = Symbol(k), entry: Entry = { request, expires: Date.now() + 30_000 };
  entries.set(k, entry);
  const pending = (async () => {
    let result: Preview;
    try {
      result = await apiGet<Preview>(`/api/v1/creative/${item.carouselId ? 'carousels' : 'assets'}/${encodeURIComponent(id)}/preview`, {
        headers: { 'X-Quiet-Request': 'true' },
      });
    } catch {
      result = { posterUrl: '', playUrl: '', source: 'UNAVAILABLE', message: '原文件和千川副本暂不可预览，请检查存储服务及千川授权后刷新' };
    }
    // A late response cannot enter another user's session or overwrite an explicit refresh.
    if (session() !== token || entries.get(k)?.request !== request) return false;
    entries.set(k, { result, expires: Date.now() + 30_000 });
    return result.source === 'QIANCHUAN';
  })();
  entries.set(k, { ...entry, pending });
  return pending;
}
