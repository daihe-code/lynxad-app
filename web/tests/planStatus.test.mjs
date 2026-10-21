import assert from 'node:assert/strict';
import test from 'node:test';
import cases from '../../lynx-ad-app/src/test/resources/plan-status-cases.json' with {type:'json'};
import {planStatusDisplay,matchesPlanStatus,planStatusOptions} from '../src/features/delivery/planStatus.ts';

for(const row of cases) test(`投放状态：${row.name}`,()=>{
  assert.equal(planStatusDisplay(row.status,row.qcStatus).group,row.group);
  assert.equal(planStatusDisplay(row.status,row.qcStatus).label,row.label);
  assert.equal(matchesPlanStatus(row.status,row.qcStatus),row.group!=='DELETED');
  assert.equal(matchesPlanStatus(row.status,row.qcStatus,'ALL_INCLUDE_DELETED'),true);
  for(const filter of row.include)assert.equal(matchesPlanStatus(row.status,row.qcStatus,filter),true,filter);
  for(const filter of row.exclude)assert.equal(matchesPlanStatus(row.status,row.qcStatus,filter),false,filter);
});
test('已暂停提供全部九个子选项且各分类可以单独选择',()=>{
  const paused=planStatusOptions.find(row=>row.value==='PAUSED');
  assert.deepEqual(paused.children.map(row=>row.label),['手动暂停','计划预算不足','账户预算已花光','账户余额不足','系统暂停','不在投放时段','未到投放时间','在投计划配额超限','商品暂不可用']);
  assert.ok(planStatusOptions.some(row=>row.value==='ALL_INCLUDE_DELETED'));
  assert.ok(planStatusOptions.some(row=>row.value==='COMPLETED'));
  assert.ok(planStatusOptions.some(row=>row.value==='TERMINATED'));
});
