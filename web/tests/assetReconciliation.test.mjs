import assert from 'node:assert/strict';
import test from 'node:test';
import { canRetryAssetSync, canVerifyAssetSync } from '../src/features/creative/assetReconciliation.ts';
test('待确认不能作为失败重传，只有明确失败、授权拒绝或暂停可以重试',()=>{
  for(const status of ['UNCERTAIN','RUNNING','UPLOADED','QUEUED','WAITING_REVIEW'])assert.equal(canRetryAssetSync(status),false);
  for(const status of ['FAILED','WAITING_AUTH','PAUSED'])assert.equal(canRetryAssetSync(status),true);
});
test('只读核查限于待确认，30秒冷却后可以继续核查',()=>{
  const now=Date.parse('2026-10-17T10:00:00Z');
  assert.equal(canVerifyAssetSync('FAILED',undefined,now),false);
  assert.equal(canVerifyAssetSync('UNCERTAIN',undefined,now),true);
  assert.equal(canVerifyAssetSync('UNCERTAIN','2026-10-17T09:59:40Z',now),false);
  assert.equal(canVerifyAssetSync('UNCERTAIN','2026-10-17T09:59:30Z',now),true);
  assert.equal(canVerifyAssetSync('UNCERTAIN','2026-10-17T10:00:20Z',now),false);
});
