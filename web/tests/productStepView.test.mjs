import test from 'node:test';
import assert from 'node:assert/strict';
import { readProductStepView, rememberProductStepView } from '../src/features/delivery/productStepView.ts';
import { DRAFT_TTL_MS } from '../src/features/delivery/creationDraftCache.ts';

const scope = { tenantId:'tenant', userId:'user', accountId:'account', awemeId:'aweme', planId:'draft' };
const storage = () => {
  const records = new Map();
  return { records, getItem:key=>records.get(key) ?? null, setItem:(key,value)=>records.set(key,value) };
};

test('调整商品后退出再进入仍回到选择页，再次分析后恢复分析页', () => {
  const disk = storage();
  assert.equal(readProductStepView(disk,scope),undefined);
  rememberProductStepView(disk,scope,'SELECTION');
  assert.equal(readProductStepView(disk,scope),'SELECTION');
  rememberProductStepView(disk,scope,'ANALYSIS');
  assert.equal(readProductStepView(disk,scope),'ANALYSIS');
  assert.deepEqual(Object.keys(JSON.parse([...disk.records.values()][0])).sort(),['savedAt','view']);
});

test('页面状态按租户、用户、广告账户、抖音号、草稿隔离，不写入未创建的计划', () => {
  const disk = storage(); rememberProductStepView(disk,scope,'SELECTION');
  for (const field of Object.keys(scope)) assert.equal(readProductStepView(disk,{...scope,[field]:'other'}),undefined);
  rememberProductStepView(disk,{...scope,planId:null},'SELECTION');
  assert.equal(readProductStepView(disk,{...scope,planId:null}),undefined);
  assert.equal(disk.records.size,1);
});

test('损坏、过期缓存和存储不可用不会影响页面操作', () => {
  const disk = storage(), now = Date.now(); rememberProductStepView(disk,scope,'SELECTION',now);
  assert.equal(readProductStepView(disk,scope,now+DRAFT_TTL_MS),'SELECTION');
  assert.equal(readProductStepView(disk,scope,now+DRAFT_TTL_MS+1),undefined);
  assert.equal(readProductStepView(disk,scope,now-300001),undefined);
  const [key] = disk.records.keys();
  for (const raw of ['{','null',JSON.stringify({view:'INVALID',savedAt:now}),JSON.stringify({view:'SELECTION',savedAt:'today'})]) {
    disk.setItem(key,raw);assert.equal(readProductStepView(disk,scope,now),undefined);
  }
  const blocked = { getItem:()=>{throw new Error('blocked');}, setItem:()=>{throw new Error('blocked');} };
  assert.equal(readProductStepView(blocked,scope),undefined);
  assert.doesNotThrow(()=>rememberProductStepView(blocked,scope,'SELECTION'));
});
