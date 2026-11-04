import test from 'node:test';
import assert from 'node:assert/strict';
import { materialMetricValue, reportingToday, shiftReportDate, statisticsRangeError, materialDailyPoints } from '../src/features/creative/materialStatistics.ts';

test('素材报表区分真实零值与未提供的指标，百分数不重复乘100', () => {
  assert.equal(materialMetricValue('cost', 0), '0.00');
  assert.equal(materialMetricValue('cost', null), '—');
  assert.equal(materialMetricValue('roi', undefined), '—');
  assert.equal(materialMetricValue('ctr', 8.11), '8.11%');
  assert.equal(materialMetricValue('cvr', 0), '0.00%');
  assert.equal(materialMetricValue('cost', 12345.67), '12,345.67');
  assert.equal(materialMetricValue('cost', NaN), '—');
});
test('统计日期采用北京时间，并处理跨月和闰日', () => {
  assert.equal(reportingToday(new Date('2026-11-04T17:30:00Z')), '2026-11-05');
  assert.equal(shiftReportDate('2026-11-05', -6), '2026-10-30');
  assert.equal(shiftReportDate('2024-03-01', -1), '2024-02-29');
});
test('统计日期字段校验阻止缺失、反向、未来和超出查询窗口的请求', () => {
  const today = '2026-11-05';
  assert.equal(statisticsRangeError('2026-10-30', today, today), '');
  for (const [from, to] of [['', today], ['2026-02-30', today], [today, '2026-11-04'], [today, '2026-11-06'], ['2026-05-09', today]]) assert.ok(statisticsRangeError(from, to, today));
  assert.equal(statisticsRangeError(shiftReportDate(today, -179), today, today), '');
});
test('缺失的每日数据保留为空，不补成零或跨缺失日期连接', () => {
  const points = materialDailyPoints('2026-11-01', '2026-11-03', [{ date: '2026-11-01', metrics: { cost: 0 } }, { date: '2026-11-03', metrics: { cost: 20, ctr: 8.11 } }]);
  assert.equal(points.length, 3); assert.equal(points[0].metrics.cost, 0);
  assert.equal(points[1].metrics.cost, undefined); assert.equal(points[2].metrics.ctr, 8.11);
});
