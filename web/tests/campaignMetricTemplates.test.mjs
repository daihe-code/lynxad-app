import test from 'node:test';
import assert from 'node:assert/strict';
import { metricTemplates, metricText, metricRowValues, metricDelta, metricDeltaDisplay, templatePreferenceKey } from '../src/features/delivery/campaignMetricTemplates.ts';

const row = { costYuan: 100, convert: 12, gmvYuan: 1000, roi: 10, netGmvYuan: 810, netRoi: 8.1, refundRate: 19 };
test('payment and net templates export their own figures in displayed order', () => {
  assert.deepEqual(metricRowValues(row, 'PAYMENT'), [100, 12, 1000, 10]);
  assert.deepEqual(metricRowValues(row, 'NET'), [100, 8.1, 810, 19]);
  assert.deepEqual(metricTemplates.NET.columns.map(c => c.label), ['整体消耗(元)', '净成交ROI', '净成交金额(元)', '1小时退款率']);
});
test('missing net figures never fall back to payment figures and zero is real data', () => {
  assert.deepEqual(metricRowValues({ costYuan: 10, gmvYuan: 100, roi: 10 }, 'NET'), [10, null, null, null]);
  assert.deepEqual(metricRowValues({ netGmvYuan: 0, netRoi: 0, refundRate: 0 }, 'NET'), [null, 0, 0, 0]);
  assert.equal(metricText(null, 'rate'), '—'); assert.equal(metricText(0, 'rate'), '0.00%');
  assert.equal(metricText(12.5, 'rate'), '12.50%'); assert.equal(metricText(Infinity, 'roi'), '—');
});
test('new metric comparisons use net values and preserve a zero or missing baseline', () => {
  const net = metricTemplates.NET.columns[1];
  const r = { comparison: { current: { netRoi: 8 }, previous: { netRoi: 4 }, roiChangePct: -20, basis: 'PREVIOUS_PERIOD' } };
  assert.equal(metricDelta(r, net).value, 100);
  r.comparison.previous.netRoi = 0;
  assert.equal(metricDelta(r, net).value, null); assert.equal(metricDelta(r, net).previous, 0);
  delete r.comparison.previous.netRoi;
  assert.equal(metricDelta(r, net).value, null);
});
test('template preferences are scoped to user and tenant', () => {
  assert.notEqual(templatePreferenceKey('a', 'u'), templatePreferenceKey('b', 'u'));
  assert.notEqual(templatePreferenceKey('a', 'u'), templatePreferenceKey('a', 'v'));
  assert.equal(templatePreferenceKey(undefined, 'u'), null);
});

test('zero to zero remains a visible flat comparison across both metric templates', () => {
  for (const template of Object.values(metricTemplates)) for (const column of template.columns) {
    const comparison = { current: { [column.key]: 0 }, previous: { [column.key]: 0 } };
    assert.deepEqual(metricDeltaDisplay(metricDelta({ comparison }, column)), { value: 0, added: false });
    comparison.current[column.key] = 60.38;
    assert.deepEqual(metricDeltaDisplay(metricDelta({ comparison }, column)), { value: null, added: true });
  }
});

test('new activity is distinguished from missing data without inventing a percentage', () => {
  assert.deepEqual(metricDeltaDisplay({ value: 100, current: 10, previous: 0 }), { value: null, added: true });
  for (const current of [null, undefined, NaN, Infinity]) {
    assert.deepEqual(metricDeltaDisplay({ value: 0, current, previous: 0 }), { value: null, added: false });
  }
  for (const [value, current] of [[50, 30], [-100, 0], [0, 20]]) {
    assert.deepEqual(metricDeltaDisplay({ value, current, previous: 20 }), { value, added: false });
  }
  assert.deepEqual(metricDeltaDisplay({ current: 10 }), { value: null, added: false });
});
