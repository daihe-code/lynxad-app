import type { FileMetadata } from './assetPreflight';

/** Carousel placement limits differ from the product-card image limits. */
export function carouselImageError(file: Pick<File, 'size'>, metadata?: Pick<FileMetadata, 'width' | 'height'>) {
  if (file.size <= 0 || file.size > 10 * 1024 ** 2) return '单张图片不能为空，大小不得超过10M';
  const { width, height } = metadata || {};
  if (!width || !height) return '无法读取图片尺寸，请重新选择';
  if (width < 360 || width > 2560) return '图片宽度须为360—2560px';
  return width / height < 9 / 16 || width / height > 16 / 9 ? '图片宽高比须为9:16—16:9' : '';
}
