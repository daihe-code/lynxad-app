import assert from 'node:assert/strict';
import test from 'node:test';
import { conflictingSettings, savePlanSettingsWithRecovery } from '../src/features/delivery/planSettingsSave.ts';

const base = {planId:'p',qcPlanId:'456',accountId:'a',awemeId:'w',smartBidType:'SMART_BID_CUSTOM',name:'原计划',status:'DELIVERING',budget:300,roi2Goal:4.6,scheduleType:'FROM_NOW',endDate:null,settingsEdit:{version:'1.before',status:'IDLE'}};
const inputs = {name:'原计划',budget:'400',roi:'4.6',duration:'long',end:'2026-12-01'};
const versionConflict = () => Object.assign(new Error('stale'),{code:1404});
const updated = (patch = {}) => ({...base,...patch,settingsEdit:{version:'2.latest',status:'IDLE'}});

test('后台刷新导致旧版本失效：重新读取并保留用户预算，一次保存即可完成', async () => {
  const latest=updated({roi2Goal:4.8});const writes=[];let reads=0,rebase;
  const result=await savePlanSettingsWithRecovery(base,inputs,{
    read:async()=>{reads++;return latest;},
    write:async patch=>{writes.push(patch);if(writes.length===1)throw versionConflict();return {...latest,budget:patch.budget};},
    rebased:(plan,values)=>{rebase={plan,values};},
  });
  assert.equal(result.status,'SAVED');assert.equal(result.plan.budget,400);
  assert.equal(reads,1);assert.equal(writes.length,2);
  assert.deepEqual(writes[1],{expectedVersion:'2.latest',budget:400});
  assert.equal(rebase.values.budget,'400');assert.equal(rebase.values.roi,'4.8');
});
test('其他窗口同时修改同一字段：保留输入和最新版本，核对后仍能保存', async () => {
  const latest=updated({budget:500});let writes=0,rebase;
  const result=await savePlanSettingsWithRecovery(base,inputs,{
    read:async()=>latest,write:async()=>{writes++;throw versionConflict();},rebased:(plan,values)=>{rebase={plan,values};},
  });
  assert.equal(result.status,'REVIEW');assert.deepEqual(result.conflicts,['budget']);assert.equal(writes,1);
  assert.equal(rebase.plan.settingsEdit.version,'2.latest');assert.equal(rebase.values.budget,'400');
  const next=await savePlanSettingsWithRecovery(rebase.plan,rebase.values,{
    read:async()=>assert.fail('无需再次读取'),write:async patch=>{assert.equal(patch.expectedVersion,'2.latest');return {...latest,budget:400};},rebased:()=>assert.fail(),
  });
  assert.equal(next.status,'SAVED');
});
test('同一目标值已被保存：只读确认即可，不重复调用更新接口', async () => {
  const latest=updated({budget:400});let writes=0;
  const result=await savePlanSettingsWithRecovery(base,inputs,{
    read:async()=>latest,write:async()=>{writes++;throw versionConflict();},rebased:()=>{},
  });
  assert.equal(result.status,'SAVED');assert.equal(writes,1);
});

test('同一编辑会话连续保存预算、ROI、名称，沿用每次返回的最新版本和已保存值', async () => {
  let current=structuredClone(base);const writes=[];
  const actions={
    read:async()=>assert.fail('保存成功后无需重新加载编辑会话'),
    write:async patch=>{
      assert.equal(patch.expectedVersion,current.settingsEdit.version);
      writes.push(patch);
      current={...current,...patch,settingsEdit:{version:`${writes.length + 1}.saved`,status:'IDLE'}};
      return current;
    },
    rebased:()=>assert.fail('正常连续保存不应触发冲突恢复'),
  };
  let result=await savePlanSettingsWithRecovery(current,inputs,actions);
  result=await savePlanSettingsWithRecovery(result.plan,{...inputs,roi:'4.9'},actions);
  result=await savePlanSettingsWithRecovery(result.plan,{...inputs,roi:'4.9',name:'继续编辑的计划'},actions);
  assert.deepEqual(writes,[
    {expectedVersion:'1.before',budget:400},
    {expectedVersion:'2.saved',roi2Goal:4.9},
    {expectedVersion:'3.saved',name:'继续编辑的计划'},
  ]);
  assert.equal(result.plan.budget,400);assert.equal(result.plan.roi2Goal,4.9);
  assert.equal(result.plan.name,'继续编辑的计划');assert.equal(result.status,'SAVED');
});
test('保存结果不确定、超时或业务校验错误，不自动重发写请求', async () => {
  for (const error of [{code:1406},{code:1523},{code:'ERR_NETWORK'},{code:1402},{status:409}]) {
    let reads=0,writes=0;
    await assert.rejects(savePlanSettingsWithRecovery(base,inputs,{
      read:async()=>{reads++;return updated();},write:async()=>{writes++;throw error;},rebased:()=>assert.fail(),
    }),e=>e===error);
    assert.equal(reads,0);assert.equal(writes,1);
  }
});
test('读取到另一请求仍在处理时，仅进入结果核查，不再次提交', async () => {
  let writes=0;
  const latest={...updated(),settingsEdit:{version:'2.pending',status:'UNCERTAIN'}};
  const result=await savePlanSettingsWithRecovery(base,inputs,{
    read:async()=>latest,write:async()=>{writes++;throw versionConflict();},rebased:()=>assert.fail(),
  });
  assert.equal(result.status,'PENDING');assert.equal(writes,1);
});
test('进入修改时已有未确认写入，不发送新的修改请求', async () => {
  const result=await savePlanSettingsWithRecovery({...base,settingsEdit:{version:'1.pending',status:'SENDING'}},inputs,{
    read:async()=>assert.fail('由只读核查流程接管'),write:async()=>assert.fail('不能重发写入'),rebased:()=>assert.fail(),
  });
  assert.equal(result.status,'PENDING');
});
test('物理计划、账号、投放方式或可编辑状态变化，不向新目标重发旧修改', async () => {
  for (const patch of [{qcPlanId:'789'},{accountId:'other'},{awemeId:'other'},{smartBidType:'SMART_BID_CONSERVATIVE'},{status:'FINISHED'}]) {
    let writes=0;
    const result=await savePlanSettingsWithRecovery(base,inputs,{
      read:async()=>updated(patch),write:async()=>{writes++;throw versionConflict();},rebased:()=>assert.fail(),
    });
    assert.equal(result.status,'RELOAD');assert.equal(writes,1);
  }
});
test('短时间连续版本变化，自动恢复有上限，采用最新版本后保持可再次保存', async () => {
  let reads=0,writes=0,adopted;
  await assert.rejects(savePlanSettingsWithRecovery(base,inputs,{
    read:async()=>{reads++;return updated();},write:async()=>{writes++;throw versionConflict();},rebased:plan=>{adopted=plan;},
  }),{code:1404});
  assert.equal(reads,1);assert.equal(writes,2);assert.equal(adopted.settingsEdit.version,'2.latest');
});
test('缺少版本先读取；读取失败保留用户输入，不绕过版本检查', async () => {
  let writes=0;
  const noVersion={...base,settingsEdit:null};
  const result=await savePlanSettingsWithRecovery(noVersion,inputs,{
    read:async()=>updated(),write:async patch=>{writes++;assert.equal(patch.expectedVersion,'2.latest');return updated({budget:400});},rebased:()=>{},
  });
  assert.equal(result.status,'SAVED');assert.equal(writes,1);
  await assert.rejects(savePlanSettingsWithRecovery(base,inputs,{
    read:async()=>{throw new Error('read failed');},write:async()=>{throw versionConflict();},rebased:()=>assert.fail(),
  }),/read failed/);
  assert.equal(inputs.budget,'400');
});
test('字段冲突只检查实际修改项，长期投放平台截止日期规范化不阻塞预算', () => {
  assert.deepEqual(conflictingSettings({...base,endDate:'2036-11-01'},updated({roi2Goal:4.9}),inputs),[]);
  assert.deepEqual(conflictingSettings(base,updated({name:'新名称',roi2Goal:4.9}),{...inputs,name:'我的名称',roi:'5'}),['name','roi']);
  const custom={...base,scheduleType:'DATE_RANGE',endDate:'2026-12-01'};
  assert.deepEqual(conflictingSettings(custom,{...updated(),scheduleType:'DATE_RANGE',endDate:'2026-12-02'},{...inputs,duration:'custom',end:'2026-12-05'}),['schedule']);
});
