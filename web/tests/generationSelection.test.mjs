import assert from 'node:assert/strict';
import test from 'node:test';
import { hasGenerationDecision, shouldPromptGeneration, selectedGenerationProducts, productSelectionLocked } from '../src/features/delivery/generationSelection.ts';

const draft = () => ({ products: [{ qcProductId: '123' }, { qcProductId: '456' }], generationStatus: 'NONE', generationProductIds: [], materials: [] });
test('生成任务的全部执行阶段均锁定商品，旧的允许调整标记不能解除限制', () => {
  for (const generationStatus of ['QUEUED','SUBMITTING','RUNNING','IMPORTING','REVISING','UNCERTAIN'])
    assert.equal(productSelectionLocked({ ...draft(), generationStatus, canAdjustProductsDuringGeneration: true }), true);
  assert.equal(productSelectionLocked(undefined),false);
  assert.equal(productSelectionLocked({ ...draft(), generationStatus:'SUCCEEDED' }),false);
});
test('只要还有单条素材或追加生成未结束就保持锁定，全部结束后解除', () => {
  assert.equal(productSelectionLocked({ ...draft(), generationStatus:'FAILED',materials:[{status:'PROCESSING'}] }),true);
  assert.equal(productSelectionLocked({ ...draft(), generationStatus:'FAILED',generationAdditions:[{status:'RUNNING'}] }),true);
  assert.equal(productSelectionLocked({ ...draft(), productSelectionLocked:true }),true);
  assert.equal(productSelectionLocked({ ...draft(), generationStatus:'FAILED',materials:[{status:'FAILED'}],generationAdditions:[{status:'FAILED'}] }),false);
});

test('首次到素材页需要选择，明确不智能生成后重新进入和刷新都不再询问', () => {
  assert.equal(shouldPromptGeneration(draft()), true);
  const saved = JSON.parse(JSON.stringify({ ...draft(), generationSelection: [] }));
  assert.equal(hasGenerationDecision(saved), true);
  assert.equal(shouldPromptGeneration(saved), false);
  assert.deepEqual(selectedGenerationProducts(saved), []);
});

test('从素材编辑位置恢复旧草稿不强制重选，未确认的新商品组合仍需选择', () => {
  assert.equal(shouldPromptGeneration(draft(), true), false);
  assert.equal(shouldPromptGeneration(draft(), false), true);
  assert.equal(shouldPromptGeneration({ ...draft(), materials: [{}] }), false);
  for (const generationStatus of ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'UNCERTAIN']) {
    assert.equal(shouldPromptGeneration({ ...draft(), generationStatus }), false);
  }
});

test('保留所选商品子集，不能把空选择或历史选择重置为全选', () => {
  assert.deepEqual(selectedGenerationProducts(draft()), ['123', '456']);
  assert.deepEqual(selectedGenerationProducts({ ...draft(), generationSelection: ['456', '789'] }), ['456']);
  assert.deepEqual(selectedGenerationProducts({ ...draft(), generationProductIds: ['123'] }), ['123']);
});
