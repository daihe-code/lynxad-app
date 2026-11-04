import test from 'node:test';
import assert from 'node:assert/strict';
import { imageLibraryFileError, imageLibraryDescription } from '../src/features/creative/imageLibraryUpload.ts';

const metadata = (width, height) => ({ width, height, summary: '' });
const file = mb => ({ size: mb * 1024 ** 2 });

test('图片库支持五种画幅，宽高上下限同时生效', () => {
  for (const [width, height] of [[1280, 720], [2560, 1440], [1080, 1920], [2160, 3840], [456, 300], [1368, 900], [600, 600], [3000, 3000], [600, 800], [3000, 4000]]) {
    assert.equal(imageLibraryFileError(file(5), metadata(width, height)), '', `${width}×${height}`);
  }
  for (const [width, height] of [[640, 360], [3840, 2160], [720, 1280], [2700, 4800], [228, 150], [1520, 1000], [599, 599], [3001, 3001], [300, 400], [3003, 4004], [1200, 800]]) {
    assert.ok(imageLibraryFileError(file(1), metadata(width, height)), `${width}×${height}`);
  }
  assert.ok(imageLibraryFileError(file(1)));
});

test('商品卡竖图单张上限5M，其余画幅为10M', () => {
  assert.equal(imageLibraryFileError(file(5), metadata(900, 1200)), '');
  assert.equal(imageLibraryFileError(file(5.01), metadata(900, 1200)), '商品卡竖图大小不得超过5M');
  for (const [width, height] of [[1920, 1080], [1080, 1920], [912, 600], [1200, 1200]]) {
    assert.equal(imageLibraryFileError(file(10), metadata(width, height)), '');
    assert.match(imageLibraryFileError(file(10.01), metadata(width, height)), /大小不得超过10M/);
  }
});

test('文件卡片按实际图片尺寸识别画幅，未知尺寸不伪造分类', () => {
  for (const [width, height, label] of [[1920, 1080, '横版大图16:9'], [1080, 1920, '竖版大图9:16'], [912, 600, '横版小图1.52'], [1200, 1200, '商品卡方图1:1'], [900, 1200, '商品卡竖图3:4']]) {
    assert.equal(imageLibraryDescription(metadata(width, height)), label);
  }
  assert.equal(imageLibraryDescription(), '');
  assert.equal(imageLibraryDescription(metadata(1200, 800)), '');
});
