import test from 'node:test';
import assert from 'node:assert/strict';
import { hasPlanCompletedResult,completedStatisticsRange,completedMetricCards,bestCompletedMaterial,completedAutomationCounts } from '../src/features/delivery/planCompletedResult.ts';
const plan={planId:'p1',status:'FINISHED',qcStatus:'TIME_DONE',qcPlanId:'9007199254740993',startDate:'2026-10-10',endDate:'2026-10-24',createdAt:'2026-10-09T01:00:00Z',smartBidType:'SMART_BID_CUSTOM',roi2Goal:6};
const now=Date.parse('2026-10-27T04:00:00Z'),range=['2026-10-10','2026-10-24'];
const material=(id,orders,overrides={})=>({id,type:'VIDEO',name:`素材 ${id}`,metrics:{orders},...overrides});
const event=(id,kind='BUDGET',overrides={})=>({id,planId:'p1',kind,status:'SUCCEEDED',finishedAt:'2026-10-24T12:00:00Z',createdAt:'2026-10-24T11:00:00Z',proposal:{kind,cause:'TEST'},actual:{qcId:plan.qcPlanId},report:{plan_id:plan.qcPlanId},...overrides});
test('只有确认完成的已发布计划进入完成结果，不把终止或结束类型未知当完成',()=>{
  assert.equal(hasPlanCompletedResult(plan),true);assert.equal(hasPlanCompletedResult({...plan,status:'COMPLETED',qcStatus:null}),true);
  assert.equal(hasPlanCompletedResult({...plan,qcPlanId:undefined}),false);assert.equal(hasPlanCompletedResult({...plan,status:'DELETED'}),false);
  for(const qcStatus of ['FROZEN','FINISHED','DELETE','DELIVERY_OK','AUDIT',null])assert.equal(hasPlanCompletedResult({...plan,qcStatus}),false);
});
test('最终统计截至有效结束日期，未来占位日期、无效日期和起止倒置不能冒充投放结束日期',()=>{
  const period=completedStatisticsRange(plan,now);assert.deepEqual(period.range,range);assert.equal(period.cumulative,true);assert.equal(period.endKnown,true);
  for(const endDate of ['2099-12-31','2026-02-30','bad','2026-10-01',null]){
    const unknown=completedStatisticsRange({...plan,endDate},now);assert.equal(unknown.endKnown,false);assert.equal(unknown.range[1],'2026-10-27');assert.equal(unknown.cumulative,false);
  }
  const clipped=completedStatisticsRange({...plan,startDate:'2025-01-01'},now);assert.equal(clipped.clipped,true);assert.equal(clipped.cumulative,false);assert.equal((Date.parse(clipped.range[1])-Date.parse(clipped.range[0]))/86400000,179);
});
test('六项汇总只使用净成交口径和区间报表，不用当日预算计算历史剩余',()=>{
  const cards=completedMetricCards(plan,{netGmv:18304,netRoi:6.4,cost:2860,shows:1286400,clicks:43738,orders:588},completedStatisticsRange(plan,now),[material('v1',192)]);
  assert.deepEqual(cards.map(c=>c.value),['¥18,304.00','6.4','¥2,860.00','1,286,400','588','素材 v1']);assert.match(cards[1].note,/超出 6.7%/);assert.equal(cards[1].positive,true);
  assert.match(cards[2].note,/15 个统计日/);assert.doesNotMatch(cards[2].note,/预算剩余|接近日预算/);assert.match(cards[5].note,/192 单/);
  const missing=completedMetricCards(plan,{gmv:999,roi:9,cost:0,orders:0},completedStatisticsRange(plan,now));
  assert.deepEqual(missing.slice(0,3).map(c=>c.value),['—','—','¥0.00']);assert.equal(missing[4].value,'0');assert.equal(missing[5].value,'素材数据待同步');
});
test('区间不足全程时不称总量，放量计划不比较遗留ROI目标',()=>{
  const period=completedStatisticsRange({...plan,startDate:'2025-01-01'},now);
  const cards=completedMetricCards({...plan,smartBidType:'SMART_BID_CONSERVATIVE'},{netRoi:4,cost:0},period,[]);
  assert.equal(cards[2].label,'区间消耗');assert.equal(cards[3].label,'区间曝光量');assert.equal(cards[4].label,'区间成交量');assert.doesNotMatch(cards[1].note,/目标/);assert.equal(cards[5].value,'暂无成交素材');
});
test('最好素材按实际成交数比较，缺失或零成交不随意选中，重名和重复记录保留ID区分',()=>{
  const rows=[material('a',0),material('b',8),material('c',25),material('c',25),material('d',undefined),material('e',-1)];
  assert.equal(bestCompletedMaterial(rows).id,'c');assert.equal(bestCompletedMaterial([]),undefined);assert.equal(bestCompletedMaterial([material('a',0)]),undefined);assert.equal(bestCompletedMaterial(),undefined);
  assert.equal(bestCompletedMaterial([material('b',25),material('a',25)]).id,'a');
});
test('自动操作仅统计本计划本千川ID、本区间、回查成功的四类操作，并按事件ID去重',()=>{
  const rows=[event('b'),event('b'),event('r','ROI'),event('f','MATERIAL_FOLLOW'),event('a','MATERIAL_ADD'),event('g','MATERIAL_GENERATE'),event('boost','BOOST'),event('other','BUDGET',{planId:'p2'}),event('qc','BUDGET',{actual:{qcId:'old'}}),event('report','BUDGET',{report:{plan_id:'old'}}),event('kind','BUDGET',{proposal:{kind:'ROI'}}),event('before','BUDGET',{finishedAt:'2026-10-09T15:59:59Z'}),event('after','BUDGET',{finishedAt:'2026-10-24T16:00:00Z'})];
  for(const status of ['RECOMMENDED','READY','EXECUTING','VERIFY_PENDING','UNKNOWN','ACKNOWLEDGED','SKIPPED','FAILED','QUALIFIED'])rows.push(event(status,'BUDGET',{status}));
  assert.deepEqual(completedAutomationCounts(plan,rows,range,now),{counts:{BUDGET:1,ROI:1,MATERIAL_FOLLOW:1,MATERIAL_ADD:1},timeIncomplete:false});
});
test('成功时间确定区间归属，建议创建时间不能代替生效时间',()=>{
  const rows=[event('before-created','ROI',{createdAt:'2026-10-01T00:00:00Z',finishedAt:'2026-10-10T00:00:00Z'}),event('after-finished','ROI',{createdAt:'2026-10-10T00:00:00Z',finishedAt:'2026-10-25T01:00:00Z'})];
  assert.equal(completedAutomationCounts(plan,rows,range,now).counts.ROI,1);
});
test('未完整取得自动记录或记录缺失生效时间时显示未知，完整空记录才显示零次',()=>{
  assert.equal(completedAutomationCounts(plan,undefined,range,now).counts,undefined);
  assert.deepEqual(completedAutomationCounts(plan,[],range,now).counts,{BUDGET:0,ROI:0,MATERIAL_FOLLOW:0,MATERIAL_ADD:0});
  const missing=completedAutomationCounts(plan,[event('a','ROI',{finishedAt:undefined})],range,now);assert.equal(missing.counts,undefined);assert.equal(missing.timeIncomplete,true);
});
