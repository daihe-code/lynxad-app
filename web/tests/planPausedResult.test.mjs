import test from 'node:test';
import assert from 'node:assert/strict';
import { hasPlanPausedResult, currentPause, pauseHistory, pauseElapsed, pauseStatisticsRange, pauseTimestamp, pausedMetricCards, automaticPauseActions, automaticPauseAdvice, loadPauseAutomation } from '../src/features/delivery/planPausedResult.ts';
const plan = {planId:'p1',qcPlanId:'9007199254740993',status:'PAUSED',startDate:'2026-10-19',createdAt:'2026-10-19T02:00:00Z',smartBidType:'SMART_BID_CUSTOM',roi2Goal:6};
const now = Date.parse('2026-10-27T04:12:00Z');
const action = (overrides={})=>({id:'a1',planId:'p1',type:'MANUAL_DISABLE',summary:'暂停投放：净成交 ROI 低于预期',snapshotTs:'2026-10-27T01:00:00Z',open:false,...overrides});
test('只有已发布的暂停计划进入四模块结果页',()=>{
  assert.equal(hasPlanPausedResult(plan),true);
  assert.equal(hasPlanPausedResult({...plan,qcPlanId:undefined}),false);
  for(const status of ['DRAFT','DELIVERING','PENDING_AUDIT','REJECTED','FINISHED'])assert.equal(hasPlanPausedResult({...plan,status}),false);
});
test('同一计划最新一次暂停原因来自保存记录，不取其他计划或旧轮次原因',()=>{
  const old=action({id:'old',summary:'暂停投放：预算消耗过快',snapshotTs:'2026-10-26T01:00:00Z'});
  const records=[action(),action({id:'other',planId:'p2',summary:'暂停投放：其他原因'}),old,action({id:'resume',type:'MANUAL_ENABLE',snapshotTs:'2026-10-26T02:00:00Z'})];
  const current=currentPause(plan,records,now);assert.equal(current.selected,'净成交 ROI 低于预期');assert.equal(current.reason,'净成交 ROI 低于预期');
  assert.equal(currentPause(plan,[action(),action({id:'resume',type:'MANUAL_ENABLE',snapshotTs:'2026-10-27T02:00:00Z'})],now),undefined);
});
test('状态记录按时间排序并去重，只包含当前计划状态操作',()=>{
  const rows=pauseHistory(plan,[action(),action(),action({id:'alert',type:'ROI_DROP'}),action({id:'other',planId:'p2'}),action({id:'earlier',type:'MANUAL_ENABLE',snapshotTs:'2026-10-26T02:00:00Z'})]);
  assert.deepEqual(rows.map(row=>row.id),['earlier','a1']);
});
test('历史缺失原因和异常暂停不默认选中其他原因，也不编造暂停时间',()=>{
  const legacy=currentPause(plan,[action({summary:'MANUAL_DISABLE',snapshotTs:''})],now);assert.equal(legacy.reason,'');assert.equal(legacy.selected,undefined);assert.equal(pauseElapsed(legacy.timestamp,now),'');
  const system=currentPause(plan,[action(),action({id:'system',type:'QC_ABNORMAL_PAUSE',summary:'账户余额异常',snapshotTs:'2026-10-27T02:00:00Z',open:true})],now);
  assert.equal(system.manual,false);assert.equal(system.reason,'账户余额异常');
  assert.equal(currentPause(plan,[action({type:'QC_ABNORMAL_PAUSE',open:false})],now),undefined);
  assert.equal(pauseElapsed(now+1000,now),'');assert.equal(currentPause(plan,[action({snapshotTs:'2026-10-28T01:00:00Z'})],now),undefined);
});
test('暂停时长和截止日期按北京时间处理，不能使用机器时区或今天冒充暂停日期',()=>{
  assert.equal(pauseElapsed(pauseTimestamp('2026-10-27 09:00:00'),now),'已暂停 3 小时 12 分');
  const at=pauseTimestamp('2026-10-26T16:30:00Z');assert.deepEqual(pauseStatisticsRange(plan,at,now).range,['2026-10-19','2026-10-27']);
  assert.equal(pauseStatisticsRange(plan,NaN,now),undefined);
});
test('统计不能超过180天，开始日期缺失时只展示并标注暂停当日',()=>{
  const long=pauseStatisticsRange({...plan,startDate:'2025-01-01'},pauseTimestamp(action().snapshotTs),now);assert.equal(long.clipped,true);assert.equal(long.range[1],'2026-10-27');assert.equal((Date.parse(long.range[1])-Date.parse(long.range[0]))/86400000,179);
  const unknown=pauseStatisticsRange({...plan,startDate:null,createdAt:null},pauseTimestamp(action().snapshotTs),now);assert.deepEqual(unknown.range,['2026-10-27','2026-10-27']);assert.equal(unknown.startKnown,false);
});
test('暂停表现使用所选区间数据，零值保留，缺失净成交ROI不以支付ROI补齐，也不计算每日预算剩余',()=>{
  const cards=pausedMetricCards(plan,{cost:386.2,netRoi:4.6,shows:158640,orders:84});assert.equal(cards[0].value,'¥386.20');assert.doesNotMatch(cards[0].note,/预算剩余|已消耗/);assert.equal(cards[1].warning,true);
  const missing=pausedMetricCards(plan,{cost:0,roi:9,orders:0});assert.deepEqual(missing.map(card=>card.value),['¥0.00','—','—','0']);
});

const automatic=(overrides={})=>({id:'auto1',planId:'p1',kind:'PAUSE',title:'当日消耗达到授权限额',evidence:'当日广告消耗 500 元，已达到授权限额。',proposal:{kind:'PAUSE',cause:'DAILY_LIMIT'},report:{plan_id:plan.qcPlanId},actual:{qcId:plan.qcPlanId,status:'PAUSED'},status:'SUCCEEDED',createdAt:'2026-10-27T00:55:00Z',dispatchedAt:'2026-10-27T00:58:00Z',finishedAt:'2026-10-27T01:00:00Z',...overrides});
test('自动暂停只认当前计划和千川ID的回查成功记录，建议和未确认执行不会冒充已暂停',()=>{
  const otherStatuses=['RECOMMENDED','READY','EXECUTING','VERIFY_PENDING','UNKNOWN','FAILED','SKIPPED','ACKNOWLEDGED'];
  const rows=automaticPauseActions(plan,[automatic(),...otherStatuses.map(status=>automatic({id:status,status})),automatic({id:'other',planId:'p2'}),automatic({id:'old-qc',actual:{qcId:'old',status:'PAUSED'}}),automatic({id:'old-report',report:{plan_id:'old'}}),automatic({id:'mismatch',actual:{qcId:plan.qcPlanId,status:'DELIVERING'}}),automatic({id:'kind',proposal:{kind:'BUDGET'}})]);
  assert.equal(rows.length,1);const result=currentPause(plan,rows,now);
  assert.equal(result.automatic,true);assert.equal(result.manual,false);assert.equal(result.selected,undefined);
  assert.equal(result.reason,'当日消耗达到授权限额');assert.equal(result.action.automation.evidence,automatic().evidence);
  assert.equal(result.timestamp,pauseTimestamp(automatic().finishedAt));assert.notEqual(result.timestamp,pauseTimestamp(automatic().createdAt));
});
test('手动与自动启停合并排序，后续恢复或手动暂停不能沿用旧自动暂停原因',()=>{
  const auto=automaticPauseActions(plan,[automatic({id:'old-auto',finishedAt:'2026-10-26T01:00:00Z'}),automatic({id:'auto-resume',kind:'ENABLE',proposal:{kind:'ENABLE',cause:'NEXT_DAY'},actual:{qcId:plan.qcPlanId,status:'DELIVERING'},finishedAt:'2026-10-27T00:00:00Z'})]);
  assert.equal(currentPause(plan,auto,now),undefined);
  const latest=currentPause(plan,[action(),...auto],now);assert.equal(latest.manual,true);assert.equal(latest.selected,'净成交 ROI 低于预期');
  assert.deepEqual(pauseHistory(plan,[action(),...auto]).map(row=>row.type),['AUTOMATIC_DISABLE','AUTOMATIC_ENABLE','MANUAL_DISABLE']);
  const newAuto=automaticPauseActions(plan,[automatic({finishedAt:'2026-10-27T02:00:00Z'})]);assert.equal(currentPause(plan,[action(),...newAuto],now).automatic,true);
  assert.equal(currentPause(plan,[...newAuto,action({type:'MANUAL_ENABLE',snapshotTs:'2026-10-27T03:00:00Z'})],now),undefined);
});
test('自动暂停确认时间缺失或将来时间不以建议时间填充，也不借用历史手动原因',()=>{
  const missing=automaticPauseActions(plan,[automatic({finishedAt:undefined})]);
  assert.equal(missing[0].snapshotTs,'');assert.equal(pauseElapsed(currentPause(plan,missing,now).timestamp,now),'');
  assert.equal(currentPause(plan,[action(),...missing],now),undefined);
  assert.equal(currentPause(plan,automaticPauseActions(plan,[automatic({finishedAt:'2026-10-28T01:00:00Z'})]),now),undefined);
});
test('自动暂停按真实触发原因给出建议，不硬编码示意图阈值，不把平台异常当自动执行',()=>{
  const loss=automaticPauseActions(plan,[automatic({title:'',proposal:{kind:'PAUSE',cause:'LOSS'}})]);
  assert.equal(currentPause(plan,loss,now).reason,'估算亏损达到授权止损限额');
  assert.match(automaticPauseAdvice('DAILY_LIMIT'),/当日消耗和授权限额/);assert.match(automaticPauseAdvice('LOSS'),/商品成本/);
  assert.match(automaticPauseAdvice('future-rule'),/查看自动调控记录/);
  assert.doesNotMatch(automaticPauseAdvice('LOSS'),/80%|2 小时/);
  assert.equal(currentPause(plan,[action({type:'QC_ABNORMAL_PAUSE',open:true})],now).automatic,false);
});
test('自动执行分页完整读取后才确认当前原因，按计划与成功状态限定请求',async()=>{
  const calls=[],signal=new AbortController().signal;
  const result=await loadPauseAutomation('p1',async(params,receivedSignal)=>{
    assert.equal(receivedSignal,signal);calls.push(params);
    return {records:params.pageNum===1?Array.from({length:100},(_,i)=>automatic({id:String(i)})):[automatic({id:'100'})],total:101};
  },signal);
  assert.equal(result.complete,true);assert.equal(result.records.length,101);assert.equal(calls.length,2);
  assert.deepEqual(calls[1],{planId:'p1',status:'SUCCEEDED',pageNum:2,pageSize:100});
  assert.equal((await loadPauseAutomation('p1',async()=>({records:[],total:0}))).complete,true);
});
test('分页遗漏、重复、数量变动或超出上限不能把旧自动暂停当当前原因',async()=>{
  assert.equal((await loadPauseAutomation('p1',async()=>({records:[automatic()],total:101}))).complete,false);
  const duplicate=await loadPauseAutomation('p1',async()=>({records:Array.from({length:100},(_,i)=>automatic({id:String(i)})),total:101}));assert.equal(duplicate.complete,false);
  let calls=0;const changed=await loadPauseAutomation('p1',async()=>({records:Array.from({length:100},(_,i)=>automatic({id:String(i)})),total:++calls===1?101:102}));assert.equal(changed.complete,false);
  calls=0;const capped=await loadPauseAutomation('p1',async()=>({records:Array.from({length:100},(_,i)=>automatic({id:`${calls}-${i}`})),total:600,pageNum:++calls}));assert.equal(capped.complete,false);assert.equal(calls,5);
});
test('自动暂停记录请求失败或取消时不返回部分记录冒充完整结果',async()=>{
  await assert.rejects(loadPauseAutomation('p1',async()=>{throw new Error('读取失败');}),/读取失败/);
  await assert.rejects(loadPauseAutomation('p1',async()=>({records:undefined,total:0})),/不完整/);
  const controller=new AbortController();controller.abort();let called=false;
  await assert.rejects(loadPauseAutomation('p1',async()=>{called=true;return {records:[],total:0};},controller.signal),{name:'AbortError'});assert.equal(called,false);
});
