import assert from 'node:assert/strict';
import test from 'node:test';
import { restoreUnavailableReason, restoreResultFeedback } from '../src/features/delivery/planConfirmation.ts';

test('恢复前核对排期，结束当天仍可恢复，结束后必须先处理排期', () => {
  const plan = { status: 'PAUSED', qcStatus: 'DISABLE', scheduleType: 'DATE_RANGE', endDate: '2026-10-25' };
  assert.equal(restoreUnavailableReason(plan, '2026-10-25'), '');
  assert.match(restoreUnavailableReason(plan, '2026-10-26'), /排期已结束/);
  assert.equal(restoreUnavailableReason({ ...plan, endDate: null }, '2026-10-26'), '');
  assert.equal(restoreUnavailableReason({ ...plan, scheduleType: 'FROM_NOW' }, '2026-10-26'), '');
  assert.match(restoreUnavailableReason({ ...plan, endDate: null, qcStatus: 'TIME_DONE' }, '2026-10-25'), /排期已结束/);
});

test('平台已完成、已终止或当前已投放的计划不能沿用暂停状态再次恢复', () => {
  for (const plan of [{ status: 'DELIVERING', qcStatus: 'DELIVERY_OK' }, { status: 'PAUSED', qcStatus: 'FROZEN' }, { status: 'DELETED', qcStatus: 'DELETE' }]) {
    assert.notEqual(restoreUnavailableReason(plan, '2026-10-25'), '');
  }
});

test('恢复成功以核查后的千川状态为准，仍暂停或状态缺失时不显示已恢复', () => {
  assert.equal(restoreResultFeedback({ status: 'DELIVERING', qcStatus: 'DELIVERY_OK' }).type, 'success');
  for (const plan of [{ status: 'PAUSED', qcStatus: 'OFFLINE_BUDGET' }, { status: 'FINISHED', qcStatus: 'TIME_DONE' }, { status: 'DELIVERING' }, { status: 'PAUSED', qcStatus: 'NEW_PLATFORM_STATE' }]) {
    const feedback = restoreResultFeedback(plan);
    assert.equal(feedback.type, 'warning');
    assert.doesNotMatch(feedback.message, /已恢复投放/);
  }
});
