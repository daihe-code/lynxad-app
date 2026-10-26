import test from 'node:test';
import assert from 'node:assert/strict';
import { hasPlanPausedResult, currentPause, pauseHistory, pauseElapsed, pauseStatisticsRange, pauseTimestamp, pausedMetricCards } from '../src/features/delivery/planPausedResult.ts';
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
