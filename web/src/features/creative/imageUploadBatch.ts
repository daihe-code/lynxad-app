export interface ImageBatchState {
  count: number; readyCount: number; addedCount: number; stoppedCount: number;
  processing: boolean; readyIds: string[];
}
export interface ImageBatchReceipt { added: (id: string) => void; done: (error?: string) => void }

/** Each square image is a separate product card. Chain returned revisions and keep successful receipts. */
export async function persistImageBatch<T>(initial: T, ids: string[], write: (state: T, id: string) => Promise<T>, accepted: (id: string, state: T) => void) {
  let state = initial;
  for (const id of new Set(ids)) { state = await write(state, id); accepted(id, state); }
  return state;
}
