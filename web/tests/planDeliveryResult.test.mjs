import test from 'node:test';
import assert from 'node:assert/strict';
import { deliveryMetricCards, hasPlanDeliveryResult } from '../src/features/delivery/planDeliveryResult.ts';
const plan = { status:'DELIVERING', qcPlanId:'9007199254740993', budget:200, smartBidType:'SMART_BID_CUSTOM', roi2Goal:6 };

test('审核通过的已发布计划使用数据结果页，提交中和拒审不进入', () => {
  assert.equal(hasPlanDeliveryResult(plan), true);
  assert.equal(hasPlanDeliveryResult({...plan,status:'SCHEDULED'}), true);
  for (const status of ['DRAFT','SUBMITTING','PENDING_AUDIT','REJECTED','PAUSED','FINISHED']) assert.equal(hasPlanDeliveryResult({...plan,status}), false);
  assert.equal(hasPlanDeliveryResult({...plan,qcPlanId:undefined}), false);
});
test('按同一天报表计算剩余预算、点击率、成交成本，ROI只使用净成交口径', () => {
  const cards = deliveryMetricCards(plan, { cost:126.4, netRoi:5.8, roi:9, shows:48320, clicks:1546, orders:26 });
  assert.equal(cards[0].value,'¥126.40'); assert.match(cards[0].note,/¥73.60.*63.2%/);
  assert.equal(cards[1].value,'5.8'); assert.equal(cards[1].warning,true); assert.equal(cards[1].note,'低于目标 6');
  assert.equal(cards[2].value,'48,320'); assert.equal(cards[2].note,'点击率 3.2%');
  assert.equal(cards[3].note,'平均成交成本 ¥4.86');
});
test('实际零值保留，缺失指标不能用支付ROI或历史快照补成净成交ROI', () => {
  const zero = deliveryMetricCards(plan,{cost:0,netRoi:0,shows:0,clicks:0,orders:0});
  assert.deepEqual(zero.map(c=>c.value),['¥0.00','0','0','0']);
  assert.doesNotMatch(zero[2].note,/NaN|Infinity|0%/); assert.doesNotMatch(zero[3].note,/NaN|Infinity|¥/);
  const missing = deliveryMetricCards(plan,{roi:9});
  assert.deepEqual(missing.map(c=>c.value),['—','—','—','—']); assert.equal(missing[1].warning,false);
});
test('放量投放不比较旧ROI目标，预算超支保留比例，未知或非法数值不进行计算', () => {
  const volume = deliveryMetricCards({...plan,smartBidType:'SMART_BID_CONSERVATIVE'},{cost:250,netRoi:1});
  assert.equal(volume[1].warning,false); assert.doesNotMatch(volume[1].note,/目标/);
  assert.match(volume[0].note,/¥0.00.*125%/);
  const invalid = deliveryMetricCards({...plan,budget:0},{cost:NaN,netRoi:Infinity,shows:-1,orders:null});
  assert.deepEqual(invalid.map(c=>c.value),['—','—','—','—']);
  assert.doesNotMatch(invalid.map(c=>c.note).join(''),/NaN|Infinity/);
});
