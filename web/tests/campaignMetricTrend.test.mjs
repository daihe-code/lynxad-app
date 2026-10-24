import test from 'node:test';
import assert from 'node:assert/strict';
import { campaignTrendScope, metricPlot } from '../src/features/delivery/campaignMetricTrend.ts';

test('trend scope follows dates, template and all business filters but ignores sorting', () => {
  const f = { startDate: '2026-10-09', endDate: '2026-10-09', metricTemplate: 'NET', accountId: 'a', keyword: '商品', status: 'PAUSED', platform: 'ALL', bidType: 'CUSTOM', createdWithin: 7 };
  assert.equal(campaignTrendScope(f), campaignTrendScope({ ...f, sortField: 'netRoi', descending: true }));
  for (const [key, value] of Object.entries(f)) assert.notEqual(campaignTrendScope(f), campaignTrendScope({ ...f, [key]: typeof value === 'number' ? value + 1 : value + 'x' }));
});
test('missing buckets break a line while true zero remains a plotted value', () => {
  const plot = metricPlot([{ time: '2026-10-09 00:00:00', metrics: { cost: 0 } }, { time: '2026-10-09 01:00:00', metrics: { cost: null } }, { time: '2026-10-09 02:00:00', metrics: { cost: 20 } }], 'cost');
  assert.equal(plot.hasData, true); assert.equal(plot.values[0].y, 208); assert.equal(plot.values[1].y, null);
  assert.equal((plot.path.match(/M/g) || []).length, 2); assert.equal(plot.path.includes('L'), false);
  assert.equal(plot.values[0].x, 64); assert.equal(plot.values[2].x, 600);
});
test('unavailable net data is empty even when payment figures exist', () => {
  const plot = metricPlot([{ time: '2026-10-09', metrics: { gmv: 1000, roi: 10 } }], 'netGmv');
  assert.equal(plot.hasData, false); assert.equal(plot.path.trim(), ''); assert.equal(plot.values[0].value, null);
});
test('single and all-zero series remain visible with a nonzero axis range', () => {
  const plot = metricPlot([{ time: '2026-10-09', metrics: { refundRate: 0 } }], 'refundRate');
  assert.equal(plot.hasData, true); assert.equal(plot.values[0].x, 332); assert.equal(plot.values[0].y, 208);
  assert.equal(plot.ticks.at(-1).value, 1); assert.equal(plot.path.includes('NaN'), false);
});
