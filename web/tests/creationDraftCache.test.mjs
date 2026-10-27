import assert from 'node:assert/strict';
import test from 'node:test';
import { CreationDraftCache, DRAFT_TTL_MS, changedDraftFields, draftKey, parseCachedDraft, rememberDraftAccount, recalledDraftAccount } from '../src/features/delivery/creationDraftCache.ts';

const scope = { tenantId:'tenant', userId:'user', accountId:'account', awemeId:'aweme', planId:'draft' };
const inputs = { productIds:['123'], name:'测试计划', budget:'300', roi:'1.5', mode:'CUSTOM', duration:'long', start:'2026-10-17', end:'2026-10-17', step:2 };
const baseline = { revision:7, inputs };
function storage() {
  const map = new Map();
  return { map, getItem:key=>map.get(key) ?? null, setItem:(key,value)=>map.set(key,value), removeItem:key=>map.delete(key) };
}

test('预算、ROI 的未完成输入以及自定义日期可恢复，读取不修改存储', () => {
  const disk=storage(), cache=new CreationDraftCache(disk,'writer-a'); cache.open(scope,baseline);
  const edited={...inputs,budget:'',roi:'2.',duration:'custom',start:'2026-10-20',end:'2026-11-10'};
  assert.equal(cache.save(edited),'saved'); const raw=disk.getItem(draftKey(scope));
  const other=new CreationDraftCache(disk,'writer-b'); const recovered=other.open(scope,baseline);
  assert.deepEqual(recovered.inputs,edited);assert.equal(recovered.baseline.revision,7);
  assert.equal(disk.getItem(draftKey(scope)),raw);
});
test('用户、企业、广告账户、抖音号、草稿五个维度独立隔离', () => {
  const disk=storage(), cache=new CreationDraftCache(disk,'writer');cache.open(scope,baseline);cache.save({...inputs,budget:'777'});
  for(const field of ['tenantId','userId','accountId','awemeId','planId']) {
    const otherScope={...scope,[field]:'other'};
    assert.notEqual(draftKey(scope),draftKey(otherScope));
    assert.equal(new CreationDraftCache(disk,'other').open(otherScope,baseline),undefined);
    assert.equal(parseCachedDraft(disk.getItem(draftKey(scope)),otherScope),undefined);
  }
  assert.throws(()=>draftKey({...scope,userId:''}));
});
test('未创建服务端草稿的选品可保存，清空选择也能恢复', () => {
  const disk=storage(), cache=new CreationDraftCache(disk,'writer');const fresh={...scope,planId:null};
  const empty={...inputs,productIds:[],name:'',budget:'',roi:'',step:1};
  cache.open(fresh,{revision:null,inputs:empty});assert.equal(cache.save({...empty,productIds:['123','456']}),'saved');
  assert.deepEqual(new CreationDraftCache(disk,'reader').open(fresh,{revision:null,inputs:empty}).inputs.productIds,['123','456']);
  const saved=new CreationDraftCache(disk,'writer');saved.open(scope,baseline);saved.save({...inputs,productIds:[]});
  assert.deepEqual(parseCachedDraft(disk.getItem(draftKey(scope)),scope).inputs.productIds,[]);
});
test('只保存输入白名单，不保存凭证、商品链接和分析结果', () => {
  const disk=storage(), cache=new CreationDraftCache(disk,'writer');
  cache.open({...scope,token:'SECRET_SCOPE'}, {revision:7,inputs:{...inputs,apiKey:'SECRET_BASE'}});
  cache.save({...inputs,budget:'777',token:'SECRET_TOKEN',imageUrl:'https://private.test/signed',analysis:{secret:'SECRET_AI'}});
  const raw=disk.getItem(draftKey(scope));
  for(const value of ['SECRET_', 'private.test', 'analysis', 'apiKey', 'imageUrl']) assert.equal(raw.includes(value),false);
});
test('另一个窗口保存后旧窗口停止写入，也不能删除新内容', () => {
  const disk=storage(), a=new CreationDraftCache(disk,'a'), b=new CreationDraftCache(disk,'b');
  a.open(scope,baseline);b.open(scope,baseline);
  assert.equal(a.save({...inputs,budget:'777'}),'saved');
  assert.equal(b.save({...inputs,budget:'888'}),'conflict');assert.equal(b.discard(),'conflict');
  assert.equal(parseCachedDraft(disk.getItem(draftKey(scope)),scope).inputs.budget,'777');
});
test('服务端版本变化时保留原始基线，显式核对后才能切换到新基线', () => {
  const disk=storage(), cache=new CreationDraftCache(disk,'a');cache.open(scope,baseline);cache.save({...inputs,budget:'777'});
  const reopened=new CreationDraftCache(disk,'b');const local=reopened.open(scope,{revision:8,inputs:{...inputs,budget:'999'}});
  assert.equal(local.baseline.revision,7);assert.equal(reopened.baseline.revision,8);
  // A normal recovery uses the original revision; explicit review keeps the latest server baseline.
  reopened.recover(local);assert.equal(reopened.baseline.revision,7);
  reopened.open(scope,{revision:8,inputs:{...inputs,budget:'999'}});reopened.recover(local,true);
  assert.equal(reopened.baseline.revision,8);assert.equal(reopened.baseline.inputs.budget,'999');
});
test('本窗口保存步骤或选品后更新版本，其他未提交字段继续保留', () => {
  const disk=storage(), cache=new CreationDraftCache(disk,'a');cache.open(scope,baseline);
  const edited={...inputs,budget:'777',step:3};
  cache.acknowledge(8,edited,['step']);cache.save(edited);
  const local=parseCachedDraft(disk.getItem(draftKey(scope)),scope);
  assert.equal(local.baseline.revision,8);assert.equal(local.baseline.inputs.step,3);
  assert.equal(local.baseline.inputs.budget,'300');assert.equal(local.inputs.budget,'777');
  cache.acknowledge(9,edited,['budget']);assert.equal(cache.save(edited),'clean');assert.equal(disk.getItem(draftKey(scope)),null);
});
test('等价金额格式不会被误判为未提交修改', () => {
  assert.deepEqual(changedDraftFields({...inputs,budget:'00300.00',roi:'1.50'},inputs),[]);
  assert.deepEqual(changedDraftFields({...inputs,budget:'300.'},inputs),['budget']);
});
test('损坏、超量、过期、越权以及异常结构缓存不会进入恢复流程', () => {
  const now=Date.now();const record={schema:1,scope,baseline,inputs:{...inputs,budget:'777'},savedAt:now,writer:'a'};
  const invalid=[null,'{', 'x'.repeat(17000),JSON.stringify({...record,schema:2}),
    JSON.stringify({...record,savedAt:now-DRAFT_TTL_MS-1}),JSON.stringify({...record,savedAt:now+300001}),
    JSON.stringify({...record,scope:{...scope,userId:'other'}}),JSON.stringify({...record,baseline:{...baseline,revision:null}}),
    ...[{productIds:['123','123']},{productIds:['not-an-id']},{productIds:Array.from({length:11},(_,i)=>String(i+1))},
      {step:4},{budget:777},{mode:'BAD'},{start:'invalid'}].map(change=>JSON.stringify({...record,inputs:{...record.inputs,...change}}))];
  for(const raw of invalid)assert.equal(parseCachedDraft(raw,scope,now),undefined);
  assert.equal(parseCachedDraft(JSON.stringify({...record,savedAt:now-DRAFT_TTL_MS}),scope,now).schema,1);
});
test('禁用存储或容量不足时返回明确失败，不冒充保存成功', () => {
  const disk=storage(), cache=new CreationDraftCache(disk,'writer');cache.open(scope,baseline);
  disk.setItem=()=>{throw new Error('quota')};assert.equal(cache.save({...inputs,budget:'777'}),'unavailable');
  const blocked={getItem:()=>{throw new Error('blocked')},setItem:()=>{},removeItem:()=>{}};
  const unreadable=new CreationDraftCache(blocked,'a');
  assert.throws(()=>unreadable.open(scope,baseline));
  // The baseline survives even a blocked read, allowing the page to warn before leaving.
  assert.deepEqual(unreadable.scope,scope);assert.deepEqual(unreadable.baseline,baseline);
  assert.deepEqual(changedDraftFields({...inputs,budget:'777'},unreadable.baseline.inputs),['budget']);
  assert.equal(unreadable.save({...inputs,budget:'777'}),'unavailable');
});
test('记忆最近账户按用户和企业隔离，并有有效期', () => {
  const disk=storage();rememberDraftAccount(disk,scope);
  assert.equal(recalledDraftAccount(disk,'tenant','user'),'account:aweme');
  assert.equal(recalledDraftAccount(disk,'tenant','other'),undefined);
  assert.equal(recalledDraftAccount(disk,'other','user'),undefined);
  assert.equal(recalledDraftAccount(disk,'tenant','user',Date.now()+DRAFT_TTL_MS+1000),undefined);
});

test('当前预算保存到服务器后，另一种投放方式的本机预算仍可恢复', () => {
  const disk=storage(),cache=new CreationDraftCache(disk,'writer');cache.open(scope,baseline);
  const edited={...inputs,modeBudgets:{CUSTOM:'300',CONSERVATIVE:'100'}};
  cache.acknowledge(8,edited,['budget','mode']);
  assert.equal(cache.save(edited),'saved');
  const reopened=new CreationDraftCache(disk,'reader').open(scope,{revision:8,inputs});
  assert.deepEqual(reopened.inputs.modeBudgets,{CUSTOM:'300',CONSERVATIVE:'100'});
  assert.equal(reopened.inputs.budget,'300');
  assert.equal(reopened.baseline.revision,8);
});

test('旧草稿只恢复当前模式预算，新增预算记忆不产生虚假的输入修改', () => {
  assert.deepEqual(changedDraftFields({...inputs,modeBudgets:{CUSTOM:'300'}},inputs),[]);
  assert.deepEqual(changedDraftFields({...inputs,budget:'00300.00',modeBudgets:{CUSTOM:'00300.00'}},inputs),[]);
  assert.deepEqual(changedDraftFields({...inputs,modeBudgets:{CUSTOM:'300',CONSERVATIVE:'100'}},inputs),['modeBudgets']);
  const disk=storage(),cache=new CreationDraftCache(disk,'writer');cache.open(scope,baseline);cache.save({...inputs,budget:'400'});
  const old=parseCachedDraft(disk.getItem(draftKey(scope)),scope);
  assert.equal(old.inputs.modeBudgets,undefined);
});

test('预算记忆保留显式空值，拒绝异常结构并清理白名单外字段', () => {
  const disk=storage(),cache=new CreationDraftCache(disk,'writer');cache.open(scope,baseline);
  assert.equal(cache.save({...inputs,modeBudgets:{CUSTOM:'300',CONSERVATIVE:'',token:'SECRET'}}),'saved');
  const raw=disk.getItem(draftKey(scope)),restored=parseCachedDraft(raw,scope);
  assert.deepEqual(restored.inputs.modeBudgets,{CUSTOM:'300',CONSERVATIVE:''});assert.equal(raw.includes('SECRET'),false);
  for (const modeBudgets of [null,[],{CUSTOM:300},{CONSERVATIVE:'1'.repeat(65)}]) {
    assert.equal(cache.save({...inputs,modeBudgets}),'unavailable');
    const record=JSON.parse(raw);record.inputs.modeBudgets=modeBudgets;
    assert.equal(parseCachedDraft(JSON.stringify(record),scope),undefined);
  }
});
