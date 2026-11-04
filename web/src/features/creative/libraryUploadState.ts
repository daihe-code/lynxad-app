import type { UploadRow } from './assetUploads';

/** Storage confirmation is separate from later review/sync receipts. */
export function libraryUploadState(row: Pick<UploadRow, 'stopped' | 'error' | 'result' | 'progress'>) {
  if (row.stopped) return '已停止';
  if (row.error || row.result?.storageStatus === 'FAILED') return '上传失败';
  if (row.result?.storageStatus === 'STORED') return '已入库';
  if (row.progress >= 100) return '上传完成';
  return '上传中';
}
