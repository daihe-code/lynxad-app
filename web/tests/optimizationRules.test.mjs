import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultOptimizationRules, validateOptimizationRules, numericOptimizationRules } from '../src/features/delivery/optimizationRules.ts';
import { isPausedPlanResume, manualPlanPauseLog, monitoringDuration, optimizationDetailState } from '../src/features/delivery/optimizationDetails.ts';

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
test('incomplete or temporarily unavailable monitoring data shows a neutral waiting state', () => {
  for (const change of [
    { result: { state: 'WAITING_DATA', complete: true, daily_spend: '0', daily_net_revenue: null, strategy_approved: false, business_ready: false } },
    { result: { ...fixture().view.result, complete: false } },
    { result: { ...fixture().view.result, business_ready: false } },
    { result: undefined },
    { stale: true },
    { error: '暂时无法获取最新投放数据，系统会自动重试。' },
  ]) {
    const data = fixture();
    Object.assign(data.view, change);
    const state = optimizationDetailState(data, 'DELIVERING', 'ACTIVE');
    assert.equal(state.label, '等待数据');
    assert.equal(state.tone, 'muted');
    assert.equal(optimizationDetailState(data, 'DELIVERING', 'EXPIRED').label, '授权失效');
  }
});
test('ready results show active monitoring even while the strategy is being calibrated', () => {
  const data = fixture();
  assert.equal(optimizationDetailState(data, 'DELIVERING', 'ACTIVE').label, '盯盘中');
  data.view.result.strategy_approved = false;
  assert.equal(optimizationDetailState(data, 'DELIVERING', 'ACTIVE').label, '盯盘中');
  data.view.result.state = 'NO_DELIVERY';
  assert.equal(optimizationDetailState(data, 'DELIVERING', 'ACTIVE').label, '持续无曝光');
});
test('paused plans with running independent tasks are not presented as fully handled', () => {
  assert.equal(optimizationDetailState(fixture(), 'PAUSED', 'ACTIVE', 1).label, '部分生效');
  assert.equal(optimizationDetailState(fixture(), 'PAUSED', 'ACTIVE', 0).label, '等待投放');
});
test('only a paused plan uses the waiting-for-delivery confirmation', () => {
  assert.equal(isPausedPlanResume({ planStatus: 'PAUSED', qcStatus: 'DISABLE', outcome: 'PLAN_NOT_DELIVERING' }), true);
  assert.equal(isPausedPlanResume({ planStatus: 'PENDING_AUDIT', outcome: 'PLAN_NOT_DELIVERING' }), false);
  assert.equal(isPausedPlanResume({ planStatus: 'DELIVERING', outcome: 'LIMIT_PAUSE' }), false);
});
test('pause details use the latest explicit plan operation observed at the time of verification', () => {
  const pause = { operation: '暂停投放', time: '2026-11-08 14:00:00', actor: '店主' };
  const resume = { operation: '恢复投放', time: '2026-11-08 14:10:00', actor: '店主' };
  const task = { operation: '暂停素材追投', time: '2026-11-08 14:15:00', actor: '运营' };
  const checked = '2026-11-08T06:20:00Z';
  assert.equal(manualPlanPauseLog([task, pause], checked), pause);
  assert.equal(manualPlanPauseLog([task, pause, resume], checked), undefined);
  assert.equal(manualPlanPauseLog([pause, { ...resume, time: '2026-11-08 14:21:00' }], checked), undefined);
});
test('monitoring duration uses observed seconds and never invents historical time', () => {
  assert.equal(monitoringDuration(0), '—'); assert.equal(monitoringDuration(90), '1分钟'); assert.equal(monitoringDuration(194400), '2天6小时');
});
