import assert from 'node:assert/strict';
import test from 'node:test';
import { creationScheduleDefaults } from '../src/features/delivery/creationSchedule.ts';

test('自定日期默认含开始日在内十天，跨月、跨年和闰日均按日历计算', () => {
  for (const [start, end] of [
    ['2026-10-22', '2026-10-31'], ['2026-10-30', '2026-11-08'],
    ['2026-12-28', '2027-01-06'], ['2028-02-25', '2028-03-05'],
    ['2027-02-25', '2027-03-06'],
  ]) assert.deepEqual(creationScheduleDefaults(start), { start, end });
});

test('保留已保存的自定日期，包括用户指定的单日投放', () => {
  assert.deepEqual(creationScheduleDefaults('2026-10-22', '2026-10-25', '2026-11-15'), { start: '2026-10-25', end: '2026-11-15' });
  assert.deepEqual(creationScheduleDefaults('2026-10-22', '2026-10-25', '2026-10-25'), { start: '2026-10-25', end: '2026-10-25' });
});

test('缺少结束日期时，以已有开始日期补齐十天', () => {
  assert.deepEqual(creationScheduleDefaults('2026-10-22', '2026-10-25', null), { start: '2026-10-25', end: '2026-11-03' });
});
