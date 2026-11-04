import type { FileMetadata } from './assetPreflight';

export const imageLibraryRules = [
  { label: '横版大图', ratio: '16:9', numerator: 16, denominator: 9, maxMb: 10, minWidth: 1280, minHeight: 720, maxWidth: 2560, maxHeight: 1440 },
  { label: '竖版大图', ratio: '9:16', numerator: 9, denominator: 16, maxMb: 10, minWidth: 1080, minHeight: 1920, maxWidth: 2160, maxHeight: 3840 },
  { label: '横版小图', ratio: '1.52', numerator: 38, denominator: 25, maxMb: 10, minWidth: 456, minHeight: 300, maxWidth: 1368, maxHeight: 900 },
  { label: '商品卡方图', ratio: '1:1', numerator: 1, denominator: 1, maxMb: 10, minWidth: 600, minHeight: 600, maxWidth: 3000, maxHeight: 3000 },
  { label: '商品卡竖图', ratio: '3:4', numerator: 3, denominator: 4, maxMb: 5, minWidth: 600, minHeight: 800, maxWidth: 3000, maxHeight: 4000 },
] as const;

function imageLibraryRule(metadata?: Pick<FileMetadata, 'width' | 'height'>) {
  if (!metadata?.width || !metadata.height) return undefined;
  const { width, height } = metadata;
  // Allow the one-pixel rounding used when exporting a fixed aspect ratio.
  return imageLibraryRules.find(rule => Math.abs(width * rule.denominator - height * rule.numerator) <= Math.max(rule.numerator, rule.denominator));
}

export function imageLibraryFileError(file: Pick<File, 'size'>, metadata?: FileMetadata) {
  if (!metadata?.width || !metadata.height) return '无法读取图片尺寸，请重新选择文件';
  const rule = imageLibraryRule(metadata);
  if (!rule) return '图片宽高比须为16:9、9:16、1.52、1:1或3:4';
  if (metadata.width < rule.minWidth || metadata.width > rule.maxWidth || metadata.height < rule.minHeight || metadata.height > rule.maxHeight) {
    return `${rule.label}尺寸须为${rule.minWidth}×${rule.minHeight}至${rule.maxWidth}×${rule.maxHeight}`;
  }
  if (file.size > rule.maxMb * 1024 ** 2) return `${rule.label}大小不得超过${rule.maxMb}M`;
  return '';
}

export function imageLibraryDescription(metadata?: Pick<FileMetadata, 'width' | 'height'>) {
  const rule = imageLibraryRule(metadata);
  return rule ? `${rule.label}${rule.ratio}` : '';
}
