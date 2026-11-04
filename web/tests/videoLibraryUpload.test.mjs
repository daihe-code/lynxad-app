import test from 'node:test';
import assert from 'node:assert/strict';
import { videoLibraryUploadState, videoLibraryFileError, videoLibraryDescription } from '../src/features/creative/videoLibraryUpload.ts';

test('视频上传状态以真实传输及存储回执为准，百分百不等于已入库', () => {
  const row = { progress: 0, error: '' };
  assert.equal(videoLibraryUploadState(row), '上传中');
  assert.equal(videoLibraryUploadState({ ...row, progress: 65 }), '上传中');
  assert.equal(videoLibraryUploadState({ ...row, progress: 100 }), '上传完成');
  assert.equal(videoLibraryUploadState({ ...row, progress: 100, result: { storageStatus: 'VALIDATING' } }), '上传完成');
  assert.equal(videoLibraryUploadState({ ...row, progress: 100, result: { storageStatus: 'STORED', aiReview: { status: 'PENDING' }, syncs: [{ status: 'QUEUED' }] } }), '已入库');
  assert.equal(videoLibraryUploadState({ ...row, progress: 100, error: '存储失败' }), '上传失败');
  assert.equal(videoLibraryUploadState({ ...row, result: { storageStatus: 'FAILED' } }), '上传失败');
  assert.equal(videoLibraryUploadState({ ...row, stopped: true }), '已停止');
});
test('视频上传要求同时校验宽、高、画幅和时长，并包含上下限', () => {
  const meta = (width, height, duration) => ({ width, height, duration, summary: '' });
  for (const value of [meta(1280, 720, 4), meta(2560, 1440, 300), meta(720, 1280, 4), meta(1440, 2560, 300)]) assert.equal(videoLibraryFileError(value), '');
  for (const value of [meta(960, 540, 10), meta(540, 960, 10), meta(3840, 2160, 10), meta(1200, 1200, 10), meta(1280, 720, 3.99), meta(1280, 720, 300.01), meta(1280, 720, NaN), undefined]) assert.ok(videoLibraryFileError(value));
});
test('文件卡片使用本地读取的画幅，在服务端已有宽高时也能回显', () => {
  assert.equal(videoLibraryDescription({ metadata: { width: 1920, height: 1080 } }), '横版16:9');
  assert.equal(videoLibraryDescription({ result: { width: 720, height: 1280 } }), '竖版9:16');
  assert.equal(videoLibraryDescription({}), '');
});
