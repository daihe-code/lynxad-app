import assert from 'node:assert/strict';
import test from 'node:test';
import { canContinueProductAnalysis, isCurrentCreationPoll, productAnalysisInput, productAnalysisPresentation } from '../src/features/delivery/productAnalysisReadiness.ts';

const passed = { status: 'PASSED', canContinue: true };
const blocked = { status: 'BLOCKED', canContinue: false };
const products = [{ qcProductId: '123' }];
const report = { selection_analysis: { schema_version: '1.0', product_analyses: [{ product_id: '123' }] } };

test('模型失败的基础备用报告不能当作智能分析成功展示，旧版报告明确要求更新', () => {
  const products = [{qcProductId:'123'}];
  const fallback = {selection_analysis:{schema_version:'1.0',product_analyses:[{product_id:'123'}]},processing:{inference_error:'ValueError'}};
  assert.equal(productAnalysisPresentation(fallback,'SUCCEEDED',products),'failed');
  assert.equal(productAnalysisPresentation({processing:{inference_status:'FAILED'}},'SUCCEEDED',products),'failed');
  assert.equal(productAnalysisPresentation({dimensions:{}},'SUCCEEDED',products),'outdated');
  assert.equal(productAnalysisPresentation({preparation:{products:[{product_id:'123',source:'product_facts'}]}},'SUCCEEDED',products),'outdated');
});

test('分析中不展示旧报告，只有覆盖当前商品的模型报告才能展示', () => {
  const products = [{qcProductId:'123'}];
  const report = {selection_analysis:{schema_version:'1.0',product_analyses:[{product_id:'123'}]}};
  assert.equal(productAnalysisPresentation(report,'RUNNING',products),'pending');
  assert.equal(productAnalysisPresentation(report,'SUCCEEDED',products),'ready');
  assert.equal(productAnalysisPresentation(report,'SUCCEEDED',[{qcProductId:'456'}]),'outdated');
  assert.equal(productAnalysisPresentation({preparation:{products:[{product_id:'123',source:'ai_suggestion'}]}},'SUCCEEDED',products),'ready');
  assert.equal(productAnalysisPresentation(undefined,'NONE',products),'empty');
});

test('商品分析输入变化包括价格口径、图片、库存和可投状态，刷新时间和草稿装饰不使结果失效', () => {
  const p = { qcProductId:'123',name:'商品',category:'类目',price:100,userPrice:null,stock:10,imageUrl:'https://assets.test/a.jpg',unavailableReasons:[],priceInfo:{minPrice:100,maxPrice:200,fetchedAt:'old'} };
  const original=productAnalysisInput([p]);
  for (const change of [{price:99},{stock:0},{imageUrl:'https://assets.test/new.jpg'},{unavailableReasons:['审核中']},{priceInfo:{minPrice:100,maxPrice:300}}])
    assert.notEqual(productAnalysisInput([{...p,...change}]),original);
  assert.equal(productAnalysisInput([{...p, draft:{planId:'draft'},priceInfo:{...p.priceInfo,fetchedAt:'new'}}]),original);
});
test('基础检查通过仍须等待 AI 完成，旧报告不能放行排队、执行或失败状态', () => {
  for (const status of ['NONE', 'QUEUED', 'RUNNING', 'FAILED'])
    assert.equal(canContinueProductAnalysis(passed, status, report, products), false);
  assert.equal(canContinueProductAnalysis(passed, 'SUCCEEDED', report, products), true);
});
test('AI 成功不能绕过不可投商品；没有基础检查的旧接口也须返回有效报告', () => {
  for (const status of ['NONE', 'RUNNING', 'FAILED', 'SUCCEEDED'])
    assert.equal(canContinueProductAnalysis(blocked, status, report, products), false);
  assert.equal(canContinueProductAnalysis({status:'BLOCKED', canContinue:true}, 'SUCCEEDED', report, products), false);
  assert.equal(canContinueProductAnalysis(undefined, 'RUNNING', report, products), false);
  assert.equal(canContinueProductAnalysis(undefined, 'SUCCEEDED', undefined, products), false);
  assert.equal(canContinueProductAnalysis(undefined, 'SUCCEEDED', report, products), true);
});
test('无结果、旧版结果、模型失败、未覆盖当前所选商品和空选品均不能继续', () => {
  for (const analysis of [undefined, {dimensions:{}}, {...report, processing:{inference_error:'模型失败'}},
    {...report, processing:{inference_status:'FAILED'}}])
    assert.equal(canContinueProductAnalysis(passed, 'SUCCEEDED', analysis, products), false);
  assert.equal(canContinueProductAnalysis(passed, 'SUCCEEDED', report, [{qcProductId:'456'}]), false);
  assert.equal(canContinueProductAnalysis(passed, 'SUCCEEDED', report, [...products, {qcProductId:'456'}]), false);
  assert.equal(canContinueProductAnalysis(passed, 'SUCCEEDED', {selection_analysis:{schema_version:'1.0',product_analyses:[]}}, []), false);
  assert.equal(canContinueProductAnalysis(passed, 'SUCCEEDED', {preparation:{products:[{product_id:'123',source:'ai_suggestion'}]}}, products), true);
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
