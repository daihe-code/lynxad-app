import test from 'node:test';
import assert from 'node:assert/strict';
import { planOptimizationPresentation as present } from '../src/features/delivery/planOptimizationStatus.ts';
import { campaignTableColumns } from '../src/features/delivery/campaignTableColumns.ts';

const plan = { qcPlanId: '1880818347348088', status: 'DELIVERING' };
const state = { planId: 'p', enabled: true, mode: 'AUTO_LIMITED', stale: false, failed: false, state: 'READY' };
test('monitoring column remains available with either metric template and without control tools', () => {
  for (const template of ['PAYMENT', 'NET']) for (const tools of [true, false]) {
    const keys = campaignTableColumns(template, tools).map(c => c.key);
    assert.ok(keys.includes('optimization'));
    assert.equal(keys.includes('tools'), tools);
    assert.ok(keys.indexOf('optimization') < keys.indexOf('actions'));
  }
});
test('unconfigured, disabled and observing plans have distinct truthful entries', () => {
  assert.deepEqual(present(plan), { label: '待配置', tone: 'warning', action: '设置盯盘规则' });
  assert.equal(present(plan, { ...state, enabled: false }).label, '已关闭');
  assert.equal(present(plan, { ...state, mode: 'OBSERVE' }).label, '观察中');
  assert.equal(present(plan, state).label, '优化中');
});
test('loading and failed requests never appear as unconfigured or healthy', () => {
  assert.equal(present(plan, undefined, true).label, '加载中');
  assert.equal(present(plan, undefined, false, true).action, '重试');
  assert.equal(present(plan, { ...state, stale: true }).label, '等待数据');
  assert.equal(present(plan, { ...state, failed: true }).label, '检测受限');
  assert.equal(present(plan, { ...state, holdReason: 'manual' }).label, '需核对配置');
});
test('plan lifecycle and platform restrictions override historical ready results', () => {
  assert.equal(present({ ...plan, status: 'PAUSED' }, state).label, '观察暂停');
  for (const qcStatus of ['AUDIT', 'REAUDIT', 'TIME_NO_REACH', 'PRE_ONLINE']) assert.equal(present({ ...plan, qcStatus }, state).label, '待投放生效');
  assert.equal(present({ ...plan, qcStatus: 'OFFLINE_AUDIT' }, state).label, '投放受限');
  assert.equal(present({ ...plan, qcStatus: 'TIME_DONE' }, state).label, '已结束');
  assert.equal(present({ status: 'DRAFT' }, state).action, '');
  assert.equal(present({ ...plan, status: 'DELETED' }, state).action, '');
});
