import assert from 'node:assert/strict';
import test from 'node:test';
import { changedPlanInputs, canRebasePlan, rebasePlanInputs, planEditComparison } from '../src/features/delivery/planEditConflict.ts';
const base={planId:'p',qcPlanId:'456',accountId:'a',awemeId:'w',smartBidType:'SMART_BID_CUSTOM',name:'原计划',status:'PAUSED',budget:300,roi2Goal:2,settingsEdit:{version:'0.before',status:'IDLE'}};
test('旧窗口保留自己的修改，未改字段采用另一窗口保存的新值',()=>{
  const latest={...base,name:'其他窗口改名',budget:500,roi2Goal:3,settingsEdit:{version:'1.after',status:'IDLE'}};
  const mine={name:'原计划',budget:'400',roi:'2.00'};
  assert.deepEqual(changedPlanInputs(base,mine),{name:false,budget:true,roi:false});
  assert.deepEqual(rebasePlanInputs(base,latest,mine),{name:'其他窗口改名',budget:'400',roi:'3'});
  assert.deepEqual(mine,{name:'原计划',budget:'400',roi:'2.00'});
  assert.equal(planEditComparison(base,latest,mine).find(r=>r.key==='budget').latest,'500');
});
test('相同值的小数格式不算修改，不会把平台新值覆盖回旧值',()=>{
  const latest={...base,budget:450,roi2Goal:2.5};
  assert.deepEqual(rebasePlanInputs(base,latest,{name:'原计划',budget:'300.00',roi:'2.0'}),{name:'原计划',budget:'450',roi:'2.5'});
});
test('归属、物理计划、投放方式、状态或未确认占用变化时不能沿用旧输入重提',()=>{
  for(const patch of [{qcPlanId:'789'},{accountId:'other'},{awemeId:'other'},{smartBidType:'SMART_BID_CONSERVATIVE'},{status:'REJECTED'},{settingsEdit:{version:'1.next',status:'UNCERTAIN'}},{settingsEdit:null}]) {
    assert.equal(canRebasePlan(base,{...base,...patch}),false);
    assert.throws(()=>rebasePlanInputs(base,{...base,...patch},{name:'我的改名',budget:'400',roi:'2'}));
  }
  const rows=planEditComparison(base,{...base,smartBidType:'SMART_BID_CONSERVATIVE',status:'REJECTED'},{name:'原计划',budget:'400',roi:'2'});
  assert.equal(rows.find(r=>r.key==='mode').latest,'放量投放');assert.equal(rows.find(r=>r.key==='status').latest,'审核未通过');
});
