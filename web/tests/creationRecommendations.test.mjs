import test from 'node:test';
import assert from 'node:assert/strict';
import { budgetChoices, budgetEstimateHints, budgetOptions, recommendedBudget } from '../src/features/delivery/creationRecommendations.ts';

test('千川仅返回推荐预算时保留该档位，并明确预估缺失，不能借用控成本ROI', () => {
  const data = { budget: { budget: 50, is_update: false, estimate_trans_cost_list: [] }, roi: { ecp_roi2_goal: 4.82 } };
  assert.equal(recommendedBudget(data), 50);
  assert.deepEqual(budgetChoices(data), [50]);
  assert.deepEqual(budgetOptions(data), [{ budget: 50, roi: '暂未提供', orders: '暂未提供', badge: '推荐档位' }]);
  assert.deepEqual(budgetEstimateHints(data, '50'), {
    roi: '暂未提供', orders: '暂未提供', message: '千川已返回推荐日预算，暂未提供该商品组合的 ROI 和订单预估。',
  });
});

test('完整预估按当前预算精确匹配，零值仍正常显示', () => {
  const data = { budget: { budget: 50, suggested_budget_list: [30, 50, 100], estimate_trans_cost_list: [
    { budget: 50, min_estimate_roi: 0, max_estimate_roi: 0.01, min_estimate_convert: 0, max_estimate_convert: 2 },
    { budget: 100, estimate_roi: 0.3, estimate_convert: 0 },
  ] } };
  assert.deepEqual(budgetChoices(data), [30, 50, 100]);
  assert.deepEqual(budgetEstimateHints(data, '50.00'), { roi: '0 ～ 0.01', orders: '0 ～ 2单' });
  assert.deepEqual(budgetEstimateHints(data, '100'), { roi: '0.3', orders: '0单' });
  assert.deepEqual(budgetEstimateHints(data, '80'), {
    roi: '暂未提供', orders: '暂未提供', message: '当前日预算未对应平台预估档位，预估暂不展示。',
  });
});

test('接口失败和没有推荐预算时不给出虚构的推荐档位', () => {
  const failure = { budgetError: '预算推荐获取失败：平台暂不可用' };
  assert.deepEqual(budgetChoices(failure), []);
  assert.deepEqual(budgetOptions(failure), []);
  assert.deepEqual(budgetEstimateHints(failure, '50'), {
    roi: '暂未提供', orders: '暂未提供', message: '千川预算预估暂不可用，可手动填写日预算。',
  });
  assert.equal(budgetEstimateHints(undefined, '').message, '填写日预算后查看平台提供的预估。');
});
