import assert from 'node:assert/strict';
import test from 'node:test';
import { activeGenerationAddition, batchGenerationRunning, hasGenerationDecision, shouldPromptGeneration, selectedGenerationProducts, generationPromptProductIds, productSelectionLocked } from '../src/features/delivery/generationSelection.ts';

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

test('未产出素材时，首次进入及关闭后重新进入均提示，空选择不代表已有素材', () => {
  assert.equal(shouldPromptGeneration(draft()), true);
  const saved = JSON.parse(JSON.stringify({ ...draft(), generationSelection: [] }));
  assert.equal(hasGenerationDecision(saved), true);
  assert.equal(shouldPromptGeneration(saved), true);
  assert.deepEqual(selectedGenerationProducts(saved), ['123','456']);
});

test('到过第三步的草稿仍逐商品核对，没有任务的商品才进入生成选择', () => {
  assert.equal(shouldPromptGeneration({...draft(),step:2,reachedStep:3}), true);
  for (const generationStatus of ['QUEUED','SUBMITTING','SENDING','RUNNING','IMPORTING','REVISING','UNCERTAIN']) {
    assert.equal(shouldPromptGeneration({ ...draft(), generationStatus }),false);
  }
  assert.equal(shouldPromptGeneration({...draft(),generationStatus:'SUCCEEDED',generationProductIds:['123','456']}),false);
  assert.equal(shouldPromptGeneration({...draft(),generationStatus:'FAILED',generationProductIds:['123','456']}),false);
});

test('默认仅勾选未准备素材的商品，不重复选择已发起生成的历史商品', () => {
  assert.deepEqual(selectedGenerationProducts(draft()), ['123', '456']);
  assert.deepEqual(selectedGenerationProducts({ ...draft(), generationSelection: ['456', '789'] }), ['123']);
  assert.deepEqual(selectedGenerationProducts({ ...draft(), generationProductIds: ['123'] }), ['456']);
});

test('智能生成、素材库选择和本地上传的现有素材均排除，不能只按计划是否有素材判断', () => {
  for(const origin of [{source:'LYNX'},{source:'LYNX',addedVia:'LIBRARY'},{source:'UPLOAD',addedVia:'UPLOAD'}]) {
    const state={...draft(),materials:[{productId:'123',status:'READY',...origin}]};
    assert.equal(shouldPromptGeneration(state),true);
    assert.deepEqual(generationPromptProductIds(state),['456']);
    assert.deepEqual(selectedGenerationProducts(state),['456']);
    assert.equal(shouldPromptGeneration({...state,materials:[...state.materials,{productId:'456',status:'READY'}]}),false);
  }
});

test('调整商品后同时提示保留商品与新增商品中未准备素材的部分', () => {
  const state={...draft(),products:[...draft().products,{qcProductId:'789'}],generationSelection:[],generationPendingProductIds:['789'],materials:[{productId:'123',status:'READY'}]};
  assert.deepEqual(selectedGenerationProducts(JSON.parse(JSON.stringify(state))),['456','789']);
  assert.equal(shouldPromptGeneration(state),true);
  assert.deepEqual(selectedGenerationProducts({...state,materials:[...state.materials,{productId:'789',status:'READY'}]}),['456']);
});

test('追加生成或素材上传处理未结束时不弹出新一轮生成选择', () => {
  const state={...draft(),generationStatus:'SUCCEEDED',generationAdditions:[{productId:'123',status:'RUNNING'}]};
  assert.equal(shouldPromptGeneration(state),false);
  assert.deepEqual(generationPromptProductIds(state),['456']);
  assert.equal(shouldPromptGeneration({...draft(),materials:[{productId:'123',status:'PROCESSING'}]}),false);
});

test('失败素材与失败生成保留原重试入口，不自动重新选择已处理商品', () => {
  assert.deepEqual(generationPromptProductIds({...draft(),materials:[{productId:'123',status:'FAILED'}],generationSelection:['456']}),[]);
  assert.equal(shouldPromptGeneration({...draft(),generationStatus:'FAILED',generationSelection:['123','456']}),false);
});
