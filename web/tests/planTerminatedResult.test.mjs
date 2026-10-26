import test from 'node:test';
import assert from 'node:assert/strict';
import { hasPlanTerminatedResult,terminationRecord,terminatedStatisticsRange,terminatedMetricValues,terminatedPauseDuration } from '../src/features/delivery/planTerminatedResult.ts';
import { automaticPauseActions } from '../src/features/delivery/planPausedResult.ts';
const plan = {planId:'p1',status:'FINISHED',qcStatus:'FROZEN',qcPlanId:'9007199254740993',createdAt:'2026-10-19T01:00:00Z',startDate:'2026-10-19'};
const now = Date.parse('2026-10-27T04:00:00Z');
const action = (overrides={})=>({id:'finish',planId:'p1',type:'MANUAL_FINISH',summary:'终止原因：净成交 ROI 持续低于目标，转为其他计划投放',snapshotTs:'2026-10-26T06:20:35Z',open:false,...overrides});

test('千川FROZEN进入已终止页面，不把自然完成、删除或结束类型未知当终止',()=>{
  assert.equal(hasPlanTerminatedResult(plan),true);
  assert.equal(hasPlanTerminatedResult({...plan,status:'TERMINATED',qcStatus:null}),true);
  assert.equal(hasPlanTerminatedResult({...plan,qcPlanId:undefined}),false);
  assert.equal(hasPlanTerminatedResult({...plan,status:'DELETED'}),false);
  for (const qcStatus of ['TIME_DONE','FINISHED','DELETE','DELIVERY_OK',null,'FUTURE_STATE']) assert.equal(hasPlanTerminatedResult({...plan,qcStatus}),false);
});
test('终止原因和时间只来自本计划最终结束记录，暂停原因不能冒充终止原因',()=>{
  const record=terminationRecord(plan,[action({id:'pause',type:'MANUAL_DISABLE',summary:'暂停投放：预算消耗过快',snapshotTs:'2026-10-26T01:12:35Z'}),action(),action({planId:'p2',summary:'其他计划'})],now);
  assert.equal(record.reason,'净成交 ROI 持续低于目标，转为其他计划投放');assert.equal(record.timestamp,Date.parse(action().snapshotTs));assert.equal(record.pausedMinutes,308);
  assert.equal(terminationRecord(plan,[action({type:'MANUAL_DISABLE'})],now),undefined);
  assert.equal(terminationRecord(plan,[action(),action({id:'enable',type:'MANUAL_ENABLE',snapshotTs:'2026-10-27T01:00:00Z'})],now),undefined);
});
test('没有终止记录或只有占位原因时保持缺失，不把结束计划或今天作为具体原因和时间',()=>{
  assert.equal(terminationRecord(plan,[],now),undefined);
  const record=terminationRecord(plan,[action({summary:'MANUAL_FINISH',snapshotTs:''})],now);
  assert.equal(record.reason,'');assert.ok(Number.isNaN(record.timestamp));assert.equal(record.pausedMinutes,undefined);
  assert.equal(terminationRecord(plan,[action({snapshotTs:'2026-10-28T01:00:00Z'})],now),undefined);
});
test('终止前暂停时长只计算最近一轮确认暂停到终止，不累加已恢复的旧暂停',()=>{
  const old=action({id:'old',type:'MANUAL_DISABLE',snapshotTs:'2026-10-25T01:00:00Z'}),restore=action({id:'restore',type:'MANUAL_ENABLE',snapshotTs:'2026-10-26T01:00:00Z'});
  assert.equal(terminationRecord(plan,[old,restore,action()],now).pausedMinutes,undefined);
  const auto=automaticPauseActions(plan,[{id:'auto',planId:'p1',status:'SUCCEEDED',kind:'PAUSE',title:'亏损达到止损线',finishedAt:'2026-10-26T05:20:35Z',report:{plan_id:plan.qcPlanId},actual:{qcId:plan.qcPlanId,status:'PAUSED'},proposal:{kind:'PAUSE',cause:'LOSS'}}]);
  assert.equal(terminationRecord(plan,[old,restore,...auto,action()],now).pausedMinutes,60);
  assert.equal(terminatedPauseDuration(308),'终止前已暂停 5 小时 8 分。');assert.equal(terminatedPauseDuration(),'');assert.equal(terminatedPauseDuration(-1),'');
});
test('历史统计截止真实终止当日并按北京时间处理，日期范围不超过180天',()=>{
  const range=terminatedStatisticsRange(plan,Date.parse('2026-10-25T16:30:00Z'),now);
  assert.deepEqual(range.range,['2026-10-19','2026-10-26']);assert.equal(range.endKnown,true);assert.equal(range.cumulative,true);
  const clipped=terminatedStatisticsRange({...plan,startDate:'2025-01-01'},Date.parse(action().snapshotTs),now);
  assert.equal(clipped.clipped,true);assert.equal(clipped.cumulative,false);assert.equal((Date.parse(clipped.range[1])-Date.parse(clipped.range[0]))/86400000,179);
});
test('终止时间缺失时查询日期与终止日期分开，起始日期缺失时不冒充全程累计',()=>{
  const current=terminatedStatisticsRange(plan,NaN,now);assert.deepEqual(current.range,['2026-10-19','2026-10-27']);assert.equal(current.endKnown,false);
  const unknown=terminatedStatisticsRange({...plan,startDate:null,createdAt:null},NaN,now);assert.equal(unknown.cumulative,false);assert.equal(unknown.startKnown,false);assert.equal((Date.parse(unknown.range[1])-Date.parse(unknown.range[0]))/86400000,179);
});
test('终止消耗和成交只使用查询区间的实际指标，零值保留，无数据不回填当日快照',()=>{
  assert.deepEqual(terminatedMetricValues({cost:386.2,orders:84}),{cost:'¥386.20',orders:'84'});
  assert.deepEqual(terminatedMetricValues({cost:0,orders:0}),{cost:'¥0.00',orders:'0'});
  assert.deepEqual(terminatedMetricValues(),{cost:'—',orders:'—'});assert.deepEqual(terminatedMetricValues({cost:-1,orders:NaN}),{cost:'—',orders:'—'});
});
