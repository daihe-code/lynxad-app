import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultOptimizationRules, validateOptimizationRules, numericOptimizationRules } from '../src/features/delivery/optimizationRules.ts';
import { monitoringDuration, optimizationDetailState } from '../src/features/delivery/optimizationDetails.ts';

test('initial rule amounts follow this plan and never default to automatic pause', () => {
  const rules = defaultOptimizationRules(4.9, 300);
  assert.equal(rules.roiFloor, 4.9); assert.equal(rules.spendReminder, 300); assert.equal(rules.spendProtection, 360);
  assert.equal(rules.protectionAction, 'CONFIRM'); assert.deepEqual(validateOptimizationRules(rules), {});
});
test('rule validation catches amount relationships, incomplete values and fractional minutes', () => {
  const rules = defaultOptimizationRules();
  for (const [key, value] of [['roiFloor', ''], ['roiFloor', 101], ['spendProtection', 399], ['runawayMinutes', 1.1], ['runawaySpend', 'NaN'], ['maxAddition', 201], ['dailyAddition', '1.001'], ['protectionAction', 'AUTO_ALL']]) {
    const errors = validateOptimizationRules({ ...rules, [key]: value }); assert.ok(Object.keys(errors).length, key);
  }
  assert.equal(numericOptimizationRules({ ...rules, spendProtection: '480.50' }).spendProtection, 480.5);
});
const fixture = () => ({ view: { policy: { enabled: true }, stale: false, result: { state: 'READY', daily_spend: '120', daily_net_revenue: '600' } }, pending: [], effectiveSeconds: 0, summary: { found: 0, completed: 0, protectedCount: 0 }, history: { records: [], total: 0 } });
test('verification never appears as confirmed, even when monitoring was closed', () => {
  const data = fixture(); data.pending = [{ status: 'VERIFY_PENDING' }]; data.view.policy.enabled = false;
  assert.equal(optimizationDetailState(data, 'DELIVERING', 'ACTIVE').label, '正在核实');
});
test('waiting, missing net revenue, stale and authorization loss remain distinct', () => {
  const data = fixture(); assert.equal(optimizationDetailState(data, 'DELIVERING', 'ACTIVE').label, '盯盘中');
  data.view.result.daily_net_revenue = null; assert.equal(optimizationDetailState(data, 'DELIVERING', 'ACTIVE').label, '部分受限');
  data.view.stale = true; assert.equal(optimizationDetailState(data, 'DELIVERING', 'ACTIVE').label, '检查受限');
  assert.equal(optimizationDetailState(data, 'DELIVERING', 'EXPIRED').label, '授权失效');
  data.view.result = undefined; assert.equal(optimizationDetailState(data, 'DELIVERING', 'ACTIVE').label, '等待数据');
});
test('paused plans with running independent tasks are not presented as fully handled', () => {
  assert.equal(optimizationDetailState(fixture(), 'PAUSED', 'ACTIVE', 1).label, '部分生效');
  assert.equal(optimizationDetailState(fixture(), 'PAUSED', 'ACTIVE', 0).label, '随计划暂停');
});
test('monitoring duration uses observed seconds and never invents historical time', () => {
  assert.equal(monitoringDuration(0), '—'); assert.equal(monitoringDuration(90), '1分钟'); assert.equal(monitoringDuration(194400), '2天6小时');
});
