import assert from 'node:assert/strict';
import test from 'node:test';
import { canContinueProductAnalysis, productAnalysisProgress, isCurrentCreationPoll, productAnalysisInput } from '../src/features/delivery/productAnalysisReadiness.ts';

const passed = { status: 'PASSED', canContinue: true };
const blocked = { status: 'BLOCKED', canContinue: false };

test('商品分析输入变化包括价格口径、图片、库存和可投状态，刷新时间和草稿装饰不使结果失效', () => {
  const p = { qcProductId:'123',name:'商品',category:'类目',price:100,userPrice:null,stock:10,imageUrl:'https://assets.test/a.jpg',unavailableReasons:[],priceInfo:{minPrice:100,maxPrice:200,fetchedAt:'old'} };
  const original=productAnalysisInput([p]);
  for (const change of [{price:99},{stock:0},{imageUrl:'https://assets.test/new.jpg'},{unavailableReasons:['审核中']},{priceInfo:{minPrice:100,maxPrice:300}}])
    assert.notEqual(productAnalysisInput([{...p,...change}]),original);
  assert.equal(productAnalysisInput([{...p, draft:{planId:'draft'},priceInfo:{...p.priceInfo,fetchedAt:'new'}}]),original);
});
test('基础检查通过后，AI 排队、执行和失败均可进入目标设置', () => {
  for (const status of ['NONE', 'QUEUED', 'RUNNING', 'FAILED', 'SUCCEEDED']) assert.equal(canContinueProductAnalysis(passed, status), true);
});
test('AI 成功不能绕过不可投商品；旧接口继续要求 AI 成功', () => {
  for (const status of ['NONE', 'RUNNING', 'FAILED', 'SUCCEEDED']) assert.equal(canContinueProductAnalysis(blocked, status), false);
  assert.equal(canContinueProductAnalysis(undefined, 'RUNNING'), false);
  assert.equal(canContinueProductAnalysis(undefined, 'SUCCEEDED'), true);
});
test('15 秒后明确说明后台分析和继续入口，不制造指标', () => {
  assert.match(productAnalysisProgress(passed, 'RUNNING', 14999), /可先设置投放目标/);
  assert.match(productAnalysisProgress(passed, 'RUNNING', 15000), /耗时较长.*后台补充/);
  assert.match(productAnalysisProgress(passed, 'FAILED'), /暂时不可用.*可继续/);
  assert.match(productAnalysisProgress(blocked, 'SUCCEEDED'), /未通过/);
  assert.equal(productAnalysisProgress(passed, 'SUCCEEDED'), undefined);
});
test('旧轮询不能覆盖刚保存的目标或切换后的草稿，同版本 AI 更新仍可回显', () => {
  const current = { planId: 'new', revision: 3 };
  assert.equal(isCurrentCreationPoll(current, { planId: 'new', revision: 2 }, 'new'), false);
  assert.equal(isCurrentCreationPoll(current, { planId: 'old', revision: 3 }, 'old'), false);
  assert.equal(isCurrentCreationPoll(undefined, current, 'new'), false);
  assert.equal(isCurrentCreationPoll(current, { planId: 'wrong', revision: 4 }, 'new'), false);
  assert.equal(isCurrentCreationPoll(current, { planId: 'new', revision: 3 }, 'new'), true);
  assert.equal(isCurrentCreationPoll(current, { planId: 'new', revision: 4 }, 'new'), true);
});
