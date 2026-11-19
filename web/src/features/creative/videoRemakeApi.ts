import { apiGet, apiPost, apiPut } from '@/api/http';

const root = '/api/v1/creative/video-remakes';
const quiet = { headers: { 'X-Quiet-Request': 'true' } };

export interface RemakeCapability {
  ready: boolean;
  subscribed: boolean;
  balanceStatus?: 'SUFFICIENT' | 'LOW' | 'EMPTY';
  availablePoints?: number;
  pointsPerSecond?: Record<'720p' | '1080p', number>;
  kling4Available?: boolean;
  sourceMatchingAvailable?: boolean;
  sourceMatchingHd?: boolean;
}

export interface RemakeVideoSpec {
  width: number;
  height: number;
  duration_seconds: number;
}

export interface RemakeShot {
  role: string;
  duration: number;
  visual_prompt: string;
  narration: string;
  caption: string;
}

export interface RemakeTask {
  id: string;
  product: string;
  phase: 'CREATING' | 'PLANNING' | 'REVIEW' | 'CONFIRMING' | 'SENDING' | 'GENERATING' | 'SUCCEEDED' | 'FAILED' | 'NEEDS_ATTENTION';
  stage?: string;
  error?: string;
  createdAt: string;
  assetId?: string;
  plan?: { title: string; shots: RemakeShot[] };
  videoUrl?: string;
  coverUrl?: string;
  adNotices?: string[];
  outputSpec?: RemakeVideoSpec;
  quote?: { points: number; pointsPerSecond?: number };
}

export interface RemakeInput {
  requestId: string;
  bindingId: string;
  awemeId: string;
  productId: string;
  productImageId?: string;
  referenceId: string;
  facts: string[];
  portraitId?: string;
  portraitName?: string;
  portraitImageId?: string;
  rightsConfirmed: boolean;
  portraitTermsConfirmed: boolean;
  followSource: boolean;
  adNotices?: string[];
}

export interface RemakeUpload {
  file: File;
  requestId: string;
  sha256?: string;
  uploadId?: string;
  nextChunk: number;
  assetId?: string;
}

export const getRemakeCapability = () => apiGet<RemakeCapability>(root + '/capability', quiet);
export const listRemakes = () => apiGet<RemakeTask[]>(root, quiet);
export const getRemake = (id: string) => apiGet<RemakeTask>(root + '/' + encodeURIComponent(id), quiet);
export const createRemake = (body: RemakeInput) => apiPost<RemakeTask>(root, body, { ...quiet, timeout: 60000 });
export const confirmRemake = (id: string, title: string, shots: RemakeShot[], points: number) =>
  apiPost<RemakeTask>(root + '/' + encodeURIComponent(id) + '/confirm', {
    title, shots: shots.map(shot => ({ visualPrompt: shot.visual_prompt, narration: shot.narration, caption: shot.caption })),
    points, accepted: true,
  }, quiet);
export const saveRemake = (id: string) =>
  apiPost<{ assetId: string }>(root + '/' + encodeURIComponent(id) + '/save', null, { ...quiet, timeout: 90000 });

export async function uploadRemakeMedia(session: RemakeUpload, kind: 'video' | 'image', bindingId: string,
                                        progress: (percent: number) => void) {
  if (session.assetId) return session.assetId;
  const { file, requestId } = session;
  const config = { ...quiet, params: { bindingId }, timeout: 30000 };
  if (!session.sha256) {
    const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
    session.sha256 = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  }
  const { id, chunkBytes } = await apiPost<{ id: string; chunkBytes: number }>(root + '/uploads', {
    requestId, mediaType: kind, filename: file.name, size: file.size, sha256: session.sha256,
  }, config);
  if (session.uploadId !== id) session.nextChunk = 0;
  session.uploadId = id;
  if (!Number.isSafeInteger(chunkBytes) || chunkBytes <= 0) throw new Error('素材分片配置无效');
  const chunks = Math.ceil(file.size / chunkBytes);
  for (let index = session.nextChunk; index < chunks; index++) {
    const block = file.slice(index * chunkBytes, (index + 1) * chunkBytes);
    await apiPut(root + '/uploads/' + encodeURIComponent(id) + '/chunks/' + index, block, {
      ...config, headers: { ...quiet.headers, 'Content-Type': 'application/octet-stream' },
    });
    session.nextChunk = index + 1;
    progress(Math.floor(session.nextChunk * 100 / chunks));
  }
  const result = await apiPost<{ assetId: string }>(root + '/uploads/' + encodeURIComponent(id) + '/finish',
    null, { ...config, timeout: 160000 });
  session.assetId = result.assetId;
  return result.assetId;
}
