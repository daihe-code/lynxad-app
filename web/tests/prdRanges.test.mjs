import assert from 'node:assert/strict';
import test from 'node:test';
import { creationBudgetError } from '../src/features/delivery/budgetValidation.ts';
import * as deliveryRules from '../src/features/delivery/budgetValidation.ts';

test('PRD控成本预算接受100000.01至999999999.99，而不扩大低预算许可', () => {
  for (const mode of ['CUSTOM', 'SMART_BID_CUSTOM']) {
    for (const amount of ['100000.01', '999999999.99']) assert.equal(creationBudgetError(amount, mode), '', amount);
    for (const amount of ['299.99', '1000000000', '300.001', '1e8']) assert.notEqual(creationBudgetError(amount, mode), '', amount);
  }
});

test('创建ROI按新走查规范允许0.01–10000并阻止超界和多余小数', () => {
  assert.equal(typeof deliveryRules.creationRoiError, 'function');
  for (const value of ['0.01', '0.1', '50', '100', '100.01', '10000']) assert.equal(deliveryRules.creationRoiError(value, 'CUSTOM'), '');
  for (const value of ['', '0', '10000.01', '0.001']) assert.notEqual(deliveryRules.creationRoiError(value, 'CUSTOM'), '');
  assert.equal(deliveryRules.creationRoiError('', 'CONSERVATIVE'), '');
});

test('日志默认日期采用北京时间并包含当天共7天，跨月计算正确', async () => {
  const rules = await import('../src/features/audit/auditDateRange.ts').catch(() => ({}));
  assert.equal(typeof rules.auditDateDefaults, 'function');
  assert.deepEqual(rules.auditDateDefaults(new Date('2026-10-18T16:30:00Z')), { from: '2026-10-13', to: '2026-10-19' });
  assert.deepEqual(rules.auditDateDefaults(new Date('2026-03-01T10:00:00Z')), { from: '2026-02-23', to: '2026-03-01' });
});
