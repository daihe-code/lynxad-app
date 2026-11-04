import test from 'node:test';
import assert from 'node:assert/strict';
import { carouselImageError } from '../src/features/creative/carouselLibraryUpload.ts';

test('carousel upload accepts its width, aspect-ratio and size boundaries', () => {
  for (const [width, height] of [[360, 640], [2560, 1440], [1200, 1200]]) assert.equal(carouselImageError({ size: 10 * 1024 ** 2 }, { width, height }), '');
});
test('carousel image upload rejects invalid files before sending them to storage', () => {
  assert.match(carouselImageError({ size: 0 }, { width: 1200, height: 1200 }), /不能为空/);
  assert.match(carouselImageError({ size: 10 * 1024 ** 2 + 1 }, { width: 1200, height: 1200 }), /10M/);
  assert.match(carouselImageError({ size: 100 }), /读取图片尺寸/);
  for (const width of [359, 2561]) assert.match(carouselImageError({ size: 100 }, { width, height: 1200 }), /宽度/);
  for (const [width, height] of [[360, 641], [2560, 1439]]) assert.match(carouselImageError({ size: 100 }, { width, height }), /宽高比/);
});
