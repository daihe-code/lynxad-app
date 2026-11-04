import type { UploadRow } from './assetUploads';
import type { FileMetadata } from './assetPreflight';

export { libraryUploadState as videoLibraryUploadState } from './libraryUploadState.ts';

export function videoLibraryFileError(metadata?: FileMetadata) {
  if (!metadata?.width || !metadata.height || !Number.isFinite(metadata.duration)) return '无法读取视频信息，请重新选择文件';
  if (metadata.duration! < 4 || metadata.duration! > 300) return '视频时长须为4—300秒';
  const { width, height } = metadata;
  const horizontal = Math.abs(width * 9 - height * 16) <= 16;
  const vertical = Math.abs(width * 16 - height * 9) <= 16;
  if (!horizontal && !vertical) return '视频宽高比须为16:9或9:16';
  if (horizontal && (width < 1280 || width > 2560 || height < 720 || height > 1440)) return '横版视频分辨率须为1280×720至2560×1440';
  if (vertical && (width < 720 || width > 1440 || height < 1280 || height > 2560)) return '竖版视频分辨率须为720×1280至1440×2560';
  return '';
}

export function videoLibraryDescription(row: Pick<UploadRow, 'metadata' | 'result'>) {
  const width = row.metadata?.width || row.result?.width, height = row.metadata?.height || row.result?.height;
  if (!width || !height) return '';
  return width >= height ? '横版16:9' : '竖版9:16';
}
