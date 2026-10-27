import assert from 'node:assert/strict';
import test from 'node:test';
import { creationBudgetError, COST_DAILY_BUDGET_HINT } from '../src/features/delivery/budgetValidation.ts';

test('控成本预算低于 300 元时提示，恰好 300 元可通过', () => {
  for (const mode of ['CUSTOM', 'SMART_BID_CUSTOM']) {
    for (const budget of ['0', '50', '99.99', '100', '100.01', '299.99']) assert.equal(creationBudgetError(budget, mode), COST_DAILY_BUDGET_HINT);
    for (const budget of ['300', '300.00', '300.01', '100000']) assert.equal(creationBudgetError(budget, mode), '');
  }
});
test('金额格式、两位小数和新的上限仍然有效', () => {
  for (const budget of ['', '-100', '100.001', '1e2', 'NaN', '1000000000']) assert.notEqual(creationBudgetError(budget, 'CUSTOM'), '');
});
test('同一金额按当前模式分别校验，不修改输入值', () => {
  const budget = '50';
  assert.equal(creationBudgetError(budget, 'CONSERVATIVE'), '');
  assert.equal(creationBudgetError(budget, 'SMART_BID_CUSTOM'), COST_DAILY_BUDGET_HINT);
  assert.equal(budget, '50');
});
