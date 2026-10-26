import assert from 'node:assert/strict';
import test from 'node:test';
import { assetDate, assetDateTime, assetDay } from '../src/features/creative/assetDates.ts';

test('素材卡片、详情和选材日期按千川原始时间展示', () => {
  const original = '2026-10-19T12:31:36Z';
  assert.equal(assetDay(original), '10月19日');
  assert.equal(assetDate(original), '2026-10-19');
  assert.equal(assetDateTime(original), '2026-10-19 20:31:36');
});

test('跨日时间统一按北京时间展示，不受电脑时区影响', () => {
  const previous = process.env.TZ;
  process.env.TZ = 'America/Los_Angeles';
  try {
    assert.equal(assetDay('2026-10-20T16:05:00Z'), '10月21日');
    assert.equal(assetDateTime('2026-10-21T00:05:00+08:00'), '2026-10-21 00:05:00');
  } finally {
    if (previous === undefined) delete process.env.TZ;
    else process.env.TZ = previous;
  }
});

test('缺失或无效时间不显示今天或1970年', () => {
  for (const value of [null, undefined, '', 'not-a-date', '1970-01-01T00:00:00Z']) {
    assert.equal(assetDay(value), '日期未知');
    assert.equal(assetDate(value), '日期未知');
    assert.equal(assetDateTime(value), '日期未知');
  }
});
