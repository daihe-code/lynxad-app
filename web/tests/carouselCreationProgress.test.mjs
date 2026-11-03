import test from 'node:test';
import assert from 'node:assert/strict';
import { carouselCreationProgress } from '../src/features/creative/carouselCreationProgress.ts';

const asset = (id, status = 'APPROVED') => ({ id, storageStatus: 'STORED', aiReview: { status, reasons: [] }, syncs: [{ accountId: 'current', status: 'UPLOADED', reviewStatus: 'PASS' }] });
const bundle = () => ({ id: 'bundle', accountIds: ['current'], aiReview: { status: 'APPROVED', reasons: [] }, parts: [
  { role: 'IMAGE', asset: asset('one') }, { role: 'IMAGE', asset: asset('two') }, { role: 'AUDIO', asset: asset('audio') }
] });
const row = (progress, key) => progress.rows.find(r => r.key === key);

test('initial checks do not invent passed images, audio or optional description', () => {
  const progress = carouselCreationProgress(undefined, 'current', false, 3);
  assert.equal(row(progress, 'name').status, 'PASS');
  assert.equal(row(progress, 'description').status, 'SKIP');
  assert.equal(row(progress, 'images').text, '0/3张检测已完成');
  assert.equal(row(progress, 'audio').status, 'WAITING');
});

test('image counts and review failures come from the returned parts', () => {
  const value = bundle();
  assert.equal(carouselCreationProgress(value, 'current', true, 2).ready, false);
  value.parts[1].asset.aiReview.status = 'RUNNING';
  let progress = carouselCreationProgress(value, 'current', true, 3);
  assert.equal(row(progress, 'description').status, 'WAITING');
  assert.equal(row(progress, 'images').text, '1/2张检测已完成');
  assert.equal(progress.ready, false);
  value.parts[1].asset.aiReview.status = 'REJECTED';
  progress = carouselCreationProgress(value, 'current', false, 3);
  assert.equal(progress.blocked, true);
  assert.equal(row(progress, 'images').status, 'FAIL');
  assert.equal(progress.label, '整套图文检查未通过');
});

test('the target account sync and actual plan-write receipt are distinct steps', () => {
  const value = bundle(); value.parts[0].asset.syncs[0].accountId = 'other';
  assert.equal(carouselCreationProgress(value, 'current', false, 2).label, '整套图文同步中');
  value.parts[0].asset.syncs[0].accountId = 'current';
  assert.equal(carouselCreationProgress(value, 'current', false, 2).label, '整套图文添加中');
  assert.equal(carouselCreationProgress(value, 'current', false, 2, false, 'offline').label, '整套图文添加未完成');
  const complete = carouselCreationProgress(value, 'current', false, 2, true);
  assert.equal(complete.label, '整套图文已添加');
  assert.equal(row(complete, 'sync').status, 'PASS');
});

test('sync failures and composite review rejection cannot appear as successful additions', () => {
  const value = bundle(); value.parts[0].asset.syncs[0].status = 'UNCERTAIN';
  assert.equal(carouselCreationProgress(value, 'current', false, 2).label, '整套图文同步未完成');
  value.parts[0].asset.syncs[0].status = 'UPLOADED'; value.aiReview.status = 'REJECTED';
  assert.equal(carouselCreationProgress(value, 'current', false, 2).blocked, true);
});
