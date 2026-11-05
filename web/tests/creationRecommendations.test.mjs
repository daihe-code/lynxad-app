import test from 'node:test';
import assert from 'node:assert/strict';
import { budgetChoices, budgetEstimateHints, budgetEstimateState, budgetOptions, recommendedBudget, recommendationHints, recommendationsNeedRefresh } from '../src/features/delivery/creationRecommendations.ts';

test('放量保障要求当前日预算有真实ROI预估，不能借用控成本推荐或其他档位', () => {
  const empty = {budget:{budget:50,estimate_trans_cost_list:[]},budgetStatus:'NO_ESTIMATE',roi:{ecp_roi2_goal:4.91}};
  assert.equal(budgetEstimateState(empty,'50'),'empty');
  assert.equal(budgetEstimateState({budgetStatus:'EMPTY',budget:{}},'50'),'empty');
  const available = {budget:{budget:50,estimate_trans_cost_list:[{budget:50,min_estimate_roi:0,max_estimate_roi:1,min_estimate_convert:0,max_estimate_convert:2}]}};
  assert.equal(budgetEstimateState(available,'50.00'),'available');
  assert.equal(budgetEstimateState(available,'80'),'unmatched');
  assert.equal(budgetEstimateState(available,''),'unmatched');
  assert.equal(budgetEstimateState({budget:{budget:50,estimate_trans_cost_list:[{budget:50,estimate_convert:8}]}},'50'),'empty');
  assert.equal(budgetEstimateState({budget:{budget:50,estimate_trans_cost_list:[{budget:50,estimate_roi:'0.72'}]}},'50'),'available');
});

test('请求失败、加载中和旧预算缓存不误判为竞争力弱，也不展示保障', () => {
  const available = {budget:{budget:50,estimate_trans_cost_list:[{budget:50,estimate_roi:1}]}};
  assert.equal(budgetEstimateState(available,'50',true),'loading');
  assert.equal(budgetEstimateState(undefined,'50'),'unavailable');
  for (const failure of [{budgetError:'SocketTimeoutException'}, {budgetStatus:'AUTH_REQUIRED'}, {budgetStatus:'RATE_LIMITED'}, {budgetCode:500}, {budgetFromCache:true}]) {
    assert.equal(budgetEstimateState({...available,...failure},'50'),'unavailable');
  }
  assert.equal(budgetEstimateState({...available,roiError:'cost mode ROI unavailable'},'50'),'available');
});

test('千川仅返回推荐预算时保留该档位，并明确预估缺失，不能借用控成本ROI', () => {
  const data = { budget: { budget: 50, is_update: false, estimate_trans_cost_list: [] }, roi: { ecp_roi2_goal: 4.82 } };
  assert.equal(recommendedBudget(data), 50);
  assert.deepEqual(budgetChoices(data), [50]);
  assert.deepEqual(budgetOptions(data), [{ budget: 50, roi: '--', orders: '--', badge: '推荐档位' }]);
  assert.deepEqual(budgetEstimateHints(data, '50'), {
    roi: '--', orders: '--', message: '千川已返回推荐日预算，暂未提供该商品组合的 ROI 和订单预估。',
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
    roi: '--', orders: '--', message: '当前日预算未对应平台预估档位，预估暂不展示。',
  });
});

test('接口失败和没有推荐预算时不给出虚构的推荐档位', () => {
  const failure = { budgetError: '预算推荐获取失败：平台暂不可用' };
  assert.deepEqual(budgetChoices(failure), []);
  assert.deepEqual(budgetOptions(failure), []);
  assert.deepEqual(budgetEstimateHints(failure, '50'), {
    roi: '--', orders: '--', message: '千川预算预估暂不可用，可手动填写日预算。',
  });
  assert.equal(budgetEstimateHints(undefined, '').message, '填写日预算后查看平台提供的预估。');
});

test('完整推荐在五分钟内复用，空预估或失败在三十秒后可重新获取', () => {
  const now=Date.now(), state={accountId:'a',awemeId:'w',products:[{qcProductId:'123'}],recommendations:{accountId:'a',awemeId:'w',productIds:['123'],fetchedAt:new Date(now).toISOString(),budget:{budget:50,estimate_trans_cost_list:[{budget:50,estimate_roi:0.38,estimate_convert:8}]},roi:{ecp_roi2_goal:4.82}}};
  assert.equal(recommendationsNeedRefresh(state,now+299999),false);assert.equal(recommendationsNeedRefresh(state,now+300000),true);
  state.recommendations.budget.estimate_trans_cost_list=[];assert.equal(recommendationsNeedRefresh(state,now+29999),false);assert.equal(recommendationsNeedRefresh(state,now+30000),true);
  state.recommendations.budgetError='请求频率受限';assert.equal(recommendationsNeedRefresh(state,now+29999),false);assert.equal(recommendationsNeedRefresh(state,now+30000),true);
});

test('旧推荐缺少时间或账户、抖音号、商品不一致时必须刷新', () => {
  const now=Date.now(),state={accountId:'a',awemeId:'w',products:[{qcProductId:'123'}],recommendations:{accountId:'a',awemeId:'w',productIds:['123'],fetchedAt:new Date(now).toISOString(),budget:{budget:50},roi:{ecp_roi2_goal:4.82}}};
  for(const edit of [s=>s.recommendations.accountId='other',s=>s.recommendations.awemeId='other',s=>s.recommendations.productIds=['456'],s=>delete s.recommendations.fetchedAt,s=>s.recommendations.fetchedAt=new Date(now+90000).toISOString()]){const s=structuredClone(state);edit(s);assert.equal(recommendationsNeedRefresh(s,now),true);}
});

test('失败时保留的预算标注为上次推荐，不能当作本次平台预估', () => {
  const data={budget:{budget:50},budgetFromCache:true,budgetError:'请求频率受限'};
  assert.deepEqual(budgetOptions(data),[{budget:50,roi:'--',orders:'--',badge:'上次推荐'}]);
  assert.equal(budgetEstimateHints(data,'50').message,'千川本次预算推荐获取失败，已保留上次推荐金额；ROI 和订单预估暂不可用。');
});

test('预算和ROI推荐失败不向用户透传英文异常或接口地址', () => {
  const error = 'java.net.SocketTimeoutException: request failed https://example.invalid/internal?token=private';
  const hints = recommendationHints({budget:{budget:50},roi:{ecp_roi2_goal:4.82},budgetError:error,roiError:error});
  assert.deepEqual(hints, {budget:'平台未返回推荐预算，可手动填写',roi:'平台未返回推荐ROI，可根据经营目标填写'});
  assert.deepEqual(recommendationHints(undefined),hints);
  assert.equal(recommendationHints({budget:{budget:50}}).budget,'自动获取巨量千川推荐预算，可调整');
  assert.equal(recommendationHints({roi:{ecp_roi2_goal:4.82}}).roi,'千川推荐净成交ROI 4.82，可参考实际经营目标调整');
  assert.deepEqual(recommendationHints({budgetError:error,roiError:error},true),{budget:'正在获取千川推荐预算…',roi:'正在获取千川推荐ROI…'});
});

test('推荐预算读取失败时，已保留的有效预算仍使用缓存提示', () => {
  const data={budget:{budget:50},budgetFromCache:true,budgetError:'upstream error'};
  assert.equal(recommendationHints(data).budget,'千川本次推荐获取失败，已保留上次推荐预算，可手动调整');
  assert.equal(recommendationHints({...data,budgetStatus:'RATE_LIMITED'}).budget,'千川请求频率受限，已保留上次推荐预算，可手动调整');
});
