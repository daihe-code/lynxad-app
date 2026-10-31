import assert from 'node:assert/strict';
import test from 'node:test';
import { activeGenerationAddition, batchGenerationRunning, hasGenerationDecision, shouldPromptGeneration, selectedGenerationProducts, productSelectionLocked } from '../src/features/delivery/generationSelection.ts';

const draft = () => ({ products: [{ qcProductId: '123' }, { qcProductId: '456' }], generationStatus: 'NONE', generationProductIds: [], materials: [] });

test('图片先就绪时继续展示本次整批进度，等待视频图文完成后再进入确认页', () => {
  const initial={...draft(),generationStatus:'RUNNING',generationCounts:[{},{},{}]};
  assert.equal(batchGenerationRunning(initial),true);
  assert.equal(batchGenerationRunning({...initial,materials:[{type:'IMAGE',status:'READY'}]}),true);
  assert.equal(batchGenerationRunning({...initial,generationStatus:'IMPORTING',materials:[{status:'PROCESSING'}]}),true);
  for(const generationStatus of ['SUCCEEDED','FAILED','CANCELLED'])assert.equal(batchGenerationRunning({...initial,generationStatus}),false);
});

test('新增素材进入生成页，已有素材的版本修订继续使用原来的修订界面', () => {
  const existing={...draft(),generationStatus:'RUNNING',materials:[{status:'READY'}]};
  assert.equal(batchGenerationRunning({...existing,generationAdditions:[{status:'RUNNING'}]}),true);
  assert.equal(batchGenerationRunning({...existing,generationStatus:'REVISING'}),false);
  assert.equal(batchGenerationRunning({...existing,generationCounts:[{}]}),false);
  assert.equal(batchGenerationRunning(draft(),true),true);
});
test('新增素材从排队到同步完成前一直显示生成页，重新进入也恢复本次进度', () => {
  const existing={...draft(),generationStatus:'SUCCEEDED',generationCounts:[{type:'VIDEO',completed:0,total:1}],materials:[{status:'READY'}]};
  for(const status of ['QUEUED','SUBMITTING','SENDING','RUNNING','IMPORTING','UNCERTAIN']) {
    const state={...existing,generationAdditions:[{id:'old',status:'SUCCEEDED',type:'IMAGE'},{id:'current',status,type:'VIDEO'}]};
    assert.equal(batchGenerationRunning(state),true);
    assert.equal(batchGenerationRunning(JSON.parse(JSON.stringify(state))),true);
    assert.equal(activeGenerationAddition(state).id,'current');
  }
  for(const status of ['SUCCEEDED','FAILED','CANCELLED']) {
    const state={...existing,generationStatus:status,generationAdditions:[{id:'current',status,type:'VIDEO'}]};
    assert.equal(batchGenerationRunning(state),false);
    assert.equal(activeGenerationAddition(state),undefined);
  }
});
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
