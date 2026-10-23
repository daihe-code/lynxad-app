import test from 'node:test';
import assert from 'node:assert/strict';
import { publishedSchedule, publishedScheduleError } from '../src/features/delivery/publishedSchedule.ts';
import { changedPlanInputs, rebasePlanInputs, planEditComparison } from '../src/features/delivery/planEditConflict.ts';

test('已发布计划按平台日期类型回显，长期投放不使用平台的远期占位结束日',()=>{
  assert.deepEqual(publishedSchedule({scheduleType:'FROM_NOW',endDate:'2036-10-22'},'2026-10-23'),{duration:'long',end:'2026-11-05'});
  assert.deepEqual(publishedSchedule({scheduleType:'DATE_RANGE',endDate:'2026-12-01'},'2026-10-23'),{duration:'custom',end:'2026-12-01'});
});
test('历史开始日期可以保留，结束日期校验今天、原开始日期和真实日期',()=>{
  assert.equal(publishedScheduleError('custom','2026-10-19','2026-10-23','2026-10-23'),'');
  for(const end of ['','2026-10-22','2026-02-30','invalid']) assert.ok(publishedScheduleError('custom','2026-10-19',end,'2026-10-23'));
  assert.ok(publishedScheduleError('custom','2026-11-01','2026-10-30','2026-10-23'));
  assert.equal(publishedScheduleError('long','','','2026-10-23'),'');
});
test('日期修改参与版本冲突核对，保留用户改过的日期并采用其他字段的新值',()=>{
  const base={planId:'p',qcPlanId:'456',accountId:'a',awemeId:'w',smartBidType:'SMART_BID_CUSTOM',name:'原计划',status:'PAUSED',budget:300,roi2Goal:2,scheduleType:'FROM_NOW',endDate:null,settingsEdit:{version:'0.before',status:'IDLE'}};
  const mine={name:'原计划',budget:'300',roi:'2',duration:'custom',end:'2026-12-01'};
  const latest={...base,budget:400,scheduleType:'DATE_RANGE',endDate:'2026-11-15',settingsEdit:{version:'1.after',status:'IDLE'}};
  assert.equal(changedPlanInputs(base,mine).schedule,true);
  assert.deepEqual(rebasePlanInputs(base,latest,mine),{...mine,budget:'400'});
  assert.equal(planEditComparison(base,latest,mine).find(row=>row.key==='schedule').latest,'自定日期，结束于 2026-11-15');
  assert.equal(rebasePlanInputs(base,latest,{...mine,duration:'long'}).end,'2026-11-15');
});
