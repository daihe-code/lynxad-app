import test from 'node:test';
import assert from 'node:assert/strict';
import { campaignTableColumns, readColumnWidths, columnWidthsPreferenceKey } from '../src/features/delivery/campaignTableColumns.ts';

test('saved widths follow metric identity when templates change and tools are hidden', () => {
  const saved = readColumnWidths(JSON.stringify({ name: 400, costYuan: 160, convert: 180, netRoi: 130, tools: 150 }));
  const widths = template => campaignTableColumns(template, false).map(c => [c.key, saved[c.key] ?? c.width]);
  const payment = new Map(widths('PAYMENT')), net = new Map(widths('NET'));
  assert.equal(payment.get('name'), net.get('name'));
  assert.equal(payment.get('costYuan'), net.get('costYuan'));
  assert.equal(payment.get('convert'), 180);
  assert.equal(net.get('netRoi'), 130);
  assert.equal(net.has('convert'), false);
  assert.equal(payment.has('tools'), false);
  assert.equal(saved.tools, 150);
  assert.equal(campaignTableColumns('PAYMENT', true).length, payment.size + 1);
});

test('corrupt or outdated stored widths cannot break table layout', () => {
  for (const value of [null, '', '{', 'null', 'true', '[]', '"wide"']) assert.deepEqual(readColumnWidths(value), {});
  const saved = readColumnWidths('{"name":-10,"costYuan":999999,"actions":"100","netRoi":123.6,"unknown":200,"roi":1e999}');
  assert.equal(saved.name, campaignTableColumns('PAYMENT', true)[0].minWidth);
  assert.equal(saved.costYuan, 1600);
  assert.equal(saved.netRoi, 124);
  assert.equal(saved.actions, undefined);
  assert.equal(saved.unknown, undefined);
  assert.equal(saved.roi, undefined);
});

test('column width preferences do not cross users or tenants', () => {
  assert.notEqual(columnWidthsPreferenceKey('a', 'u'), columnWidthsPreferenceKey('b', 'u'));
  assert.notEqual(columnWidthsPreferenceKey('a', 'u'), columnWidthsPreferenceKey('a', 'v'));
  assert.equal(columnWidthsPreferenceKey(undefined, 'u'), null);
  assert.equal(columnWidthsPreferenceKey('a', undefined), null);
});
