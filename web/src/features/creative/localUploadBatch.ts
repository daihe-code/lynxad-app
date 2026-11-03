export interface UploadBatchState {
  count: number; readyCount: number; addedCount: number; stoppedCount: number;
  processing: boolean; readyIds: string[];
}
export interface UploadBatchReceipt { added: (id: string) => void; done: (error?: string) => void }

export interface LocalUploadBatch { productId: string; type: 'IMAGE' | 'VIDEO' | 'CAROUSEL'; assetIds: string[] }
export function localUploadSelection(type: LocalUploadBatch['type'], id: string) {
  return type === 'VIDEO' ? { videoAssetId: id } : type === 'CAROUSEL' ? { carouselId: id } : { imageAssetIds: [id] };
}

/** Each file is a separate material. Chain returned revisions and keep successful receipts. */
export async function persistUploadBatch<T>(initial: T, ids: string[], write: (state: T, id: string) => Promise<T>, accepted: (id: string, state: T) => void) {
  let state = initial;
  for (const id of new Set(ids)) { state = await write(state, id); accepted(id, state); }
  return state;
}
