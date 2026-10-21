import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiRequestError, requestFailure, recoveryLabel, safeErrorMessage } from '../src/api/apiErrors.ts';
import { isSafeRead, withReadRetry, pollRecoveryDelay } from '../src/api/readRetry.ts';

const query={method:'GET',url:'/api/v1/delivery/creation/draft-123'};
const network=()=>({code:'ERR_NETWORK',message:'Network Error',config:query});
const response=(status,code,message)=>({response:{status,data:code===undefined?{message}:{code,message}},config:query});
function runtime(extra={}) { return {now:()=>0,random:()=>0,online:()=>true,wait:async()=>{},...extra}; }

test('灵投登录、千川授权和权限分开恢复，平台问题不归类为登录失效',()=>{
  assert.equal(requestFailure({code:401,message:'未登录'}).action,'LOGIN');
  assert.equal(requestFailure(response(401,undefined,'expired')).kind,'LOGIN');
  for(const code of [1211,40102,40105]) {
    const failure=requestFailure({code,message:'授权失效'});
    assert.equal(failure.kind,'AUTHORIZATION');assert.equal(failure.action,'ACCOUNTS');assert.equal(failure.automaticRetry,false);
  }
  assert.equal(requestFailure({code:1201,message:'抖音号不属于账户'}).kind,'PERMISSION');
  assert.equal(requestFailure(response(403,undefined,'禁止访问')).action,'ACCOUNTS');
});
test('参数、版本冲突和待确认写入有不同操作，不自动重发写请求',()=>{
  assert.equal(requestFailure({code:1402,message:'日预算不能低于300元'}).message,'日预算不能低于300元');
  assert.equal(requestFailure({code:1402,message:'不合法'}).action,'EDIT');
  assert.equal(requestFailure({code:1404,message:'草稿已更新'}).action,'REFRESH');
  for(const code of [1523,8003]) { const f=requestFailure({code});assert.equal(f.kind,'UNCERTAIN');assert.equal(f.action,'VERIFY'); }
  for(const method of ['POST','PUT','PATCH','DELETE']) {
    const f=requestFailure(network(),{method});assert.equal(f.kind,'UNCERTAIN');assert.equal(f.automaticRetry,false);
    assert.equal(requestFailure(response(503),{method}).action,'VERIFY');
    assert.equal(requestFailure({code:9999,message:'系统繁忙'},{method}).action,'VERIFY');
  }
  assert.equal(requestFailure(new Error('请先选择商品'),{method:'POST'}).message,'请先选择商品');
});
test('网络、超时和上游繁忙分类；业务失败不叠加后台已执行的退避',()=>{
  assert.equal(requestFailure(network()).automaticRetry,true);
  assert.match(requestFailure({code:'ECONNABORTED',config:query}).message,/超时/);
  assert.equal(requestFailure(response(503)).automaticRetry,true);
  for(const code of [429,40100,503,9999,1406]) assert.equal(requestFailure({code,message:'失败'},query).automaticRetry,false);
  assert.equal(requestFailure({code:'ERR_CANCELED'}).kind,'CANCELLED');
  const same=requestFailure(network());assert.equal(requestFailure(same),same);assert.equal(recoveryLabel(same),'重新查询');
});
test('错误提示隐藏凭证及服务URL并限制长度',()=>{
  const text=safeErrorMessage('访问失败 access_token=abc-secret Authorization: Bearer private-key https://private.test/?secret=xyz\n'+ '错'.repeat(500));
  for(const secret of ['abc-secret','private-key','private.test','xyz','\n'])assert.equal(text.includes(secret),false);
  assert.ok(text.length<=400);
  assert.equal(requestFailure(response(400,undefined,'access_token=not-for-display')).response.data.message.includes('not-for-display'),false);
});
test('只重试核验过的查询，不按GET一概重试，也不重试任何写请求',()=>{
  for(const url of ['/api/v1/identity/oauth/accounts','/api/v1/delivery/creation/products','/api/v1/delivery/creation/products/restore',query.url,'/api/v1/delivery/plans/plan-123']) assert.equal(isSafeRead({...query,url}),true);
  for(const url of ['/api/v1/identity/oauth/url',query.url+'/recommendations',query.url+'/generation','https://other.test'+query.url]) assert.equal(isSafeRead({...query,url}),false);
  for(const method of ['POST','PUT','PATCH','DELETE'])assert.equal(isSafeRead({...query,method}),false);
});
test('瞬态查询第一次失败后重试一次，成功时不抛错',async()=>{
  let calls=0;const waits=[];
  const value=await withReadRetry(async()=>{if(++calls===1)throw network();return 'latest';},query,runtime({wait:async ms=>waits.push(ms)}));
  assert.equal(value,'latest');assert.equal(calls,2);assert.deepEqual(waits,[600]);
});
test('重试次数、总耗时和Retry-After有上限',async()=>{
  let calls=0;
  await assert.rejects(withReadRetry(async()=>{calls++;throw response(503);},query,runtime()),f=>f instanceof ApiRequestError&&f.attempts===2);
  assert.equal(calls,2);calls=0;
  await assert.rejects(withReadRetry(async()=>{calls++;throw {...response(503),response:{status:503,headers:{'retry-after':'20'}}};},query,runtime()));
  assert.equal(calls,1);
  let clock=0;calls=0;
  await assert.rejects(withReadRetry(async()=>{calls++;clock=10000;throw network();},query,runtime({now:()=>clock})));
  assert.equal(calls,1);
});
test('取消、离线、切换登录账户会停止重试，原调用可手动关闭重试',async()=>{
  for(const enabled of [false,undefined]) {
    let calls=0;
    await assert.rejects(withReadRetry(async()=>{calls++;throw network();},{...query,enabled},runtime({online:()=>false})));
    assert.equal(calls,1);
  }
  let identity='first',calls=0;
  await assert.rejects(withReadRetry(async()=>{calls++;throw network();},{...query,identity:()=>identity},runtime({wait:async()=>{identity='second';}})),f=>f.kind==='CANCELLED');
  assert.equal(calls,1);
  const controller=new AbortController();controller.abort();calls=0;
  await assert.rejects(withReadRetry(async()=>{calls++;return 'no';},{...query,signal:controller.signal},runtime()),f=>f.kind==='CANCELLED');
  assert.equal(calls,0);
  calls=0;await assert.rejects(withReadRetry(async()=>{calls++;throw network();},{...query,enabled:false},runtime()));assert.equal(calls,1);
});
test('业务错误和非白名单查询不会重放',async()=>{
  for(const [failure,options] of [[{code:1402,message:'预算错误'},query],[network(),{...query,url:query.url+'/recommendations'}],[network(),{...query,method:'POST'}],[response(429),query]]) {
    let calls=0;await assert.rejects(withReadRetry(async()=>{calls++;throw failure;},options,runtime()));assert.equal(calls,1);
  }
});
test('重试请求只使用剩余时间预算，等待期间取消会停止发出请求',async()=>{
  let calls=0,clock=0;const budgets=[];
  await withReadRetry(async budget=>{budgets.push(budget);if(++calls===1)throw network();return true;},query,runtime({now:()=>clock,wait:async()=>{clock=800;}}));
  assert.deepEqual(budgets,[undefined,9200]);
  const controller=new AbortController();calls=0;
  const pending=withReadRetry(async()=>{calls++;throw network();},{...query,signal:controller.signal});
  setImmediate(()=>controller.abort());
  await assert.rejects(pending,f=>f.kind==='CANCELLED');assert.equal(calls,1);
});
test('轮询故障逐步退避；授权错误停止轮询，限流等待不会早于平台要求',()=>{
  const failure=requestFailure(network());
  assert.deepEqual([1,2,3,9].map(n=>pollRecoveryDelay(failure,n)),[15000,30000,60000,60000]);
  for(const code of [1211,1402,1403,1404])assert.equal(pollRecoveryDelay(requestFailure({code}),1),undefined);
  failure.retryAfterMs=120000;assert.equal(pollRecoveryDelay(failure,1),120000);
  failure.retryAfterMs=3600000;assert.equal(pollRecoveryDelay(failure,1),undefined);
});

test('提交后1406按结果未确认处理，查询1406不假定发生了写入',()=>{
  assert.equal(requestFailure({code:1406,message:'未确认结果'},{method:'PUT'}).kind,'UNCERTAIN');
  assert.notEqual(requestFailure({code:1406,message:'查询失败'},{method:'GET'}).kind,'UNCERTAIN');
});
