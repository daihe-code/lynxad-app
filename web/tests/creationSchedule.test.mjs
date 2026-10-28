import assert from 'node:assert/strict';
import test from 'node:test';
import { creationScheduleDefaults, creationScheduleRecovery } from '../src/features/delivery/creationSchedule.ts';
import { scheduleInputError } from '../src/features/delivery/inputValidation.ts';

test('自定日期默认含开始日在内十四天，跨月、跨年和闰日均按日历计算', () => {
  for (const [start, end] of [
    ['2026-10-22', '2026-11-04'], ['2026-10-23', '2026-11-05'],
    ['2026-10-30', '2026-11-12'], ['2026-12-28', '2027-01-10'],
    ['2028-02-25', '2028-03-09'], ['2027-02-25', '2027-03-10'],
  ]) assert.deepEqual(creationScheduleDefaults(start), { start, end });
});

test('保留已保存的自定日期，包括用户指定的单日投放', () => {
  assert.deepEqual(creationScheduleDefaults('2026-10-22', '2026-10-25', '2026-11-15'), { start: '2026-10-25', end: '2026-11-15' });
  assert.deepEqual(creationScheduleDefaults('2026-10-22', '2026-10-25', '2026-10-25'), { start: '2026-10-25', end: '2026-10-25' });
});

test('缺少结束日期时，以已有开始日期补齐十四天', () => {
  assert.deepEqual(creationScheduleDefaults('2026-10-22', '2026-10-25', null), { start: '2026-10-25', end: '2026-11-07' });
});

test('过期草稿可显式改为今天开始，保留未来结束日期并通过原有日期校验', () => {
  const today = '2026-10-28', saved = { start: '2026-10-23', end: '2026-11-01' };
  assert.ok(scheduleInputError('custom', saved.start, saved.end, today));
  const recovery = creationScheduleRecovery('custom', saved.start, saved.end, today);
  assert.deepEqual(recovery, { start: today, end: saved.end });
  assert.equal(scheduleInputError('custom', recovery.start, recovery.end, today), '');
  assert.equal(saved.start, '2026-10-23');
  assert.deepEqual(creationScheduleRecovery('custom', saved.start, today, today), { start: today, end: today });
});

test('结束日期已过期或原日期无效时，不能自动延长投放时间', () => {
  for (const [start, end] of [['2026-10-21','2026-10-27'], ['2026-10-23','2026-10-22'], ['2026-02-30','2026-11-01'], ['','2026-11-01'], ['2026-10-23','']]) {
    assert.equal(creationScheduleRecovery('custom', start, end, '2026-10-28'), undefined);
  }
  assert.equal(creationScheduleRecovery('long', '2026-10-23', '2026-11-01', '2026-10-28'), undefined);
  assert.equal(creationScheduleRecovery('custom', '2026-10-28', '2026-11-01', '2026-10-28'), undefined);
  assert.equal(creationScheduleRecovery('custom', '2026-10-29', '2026-11-01', '2026-10-28'), undefined);
});
