import assert from 'node:assert/strict';
import test from 'node:test';
import { canContinueProductAnalysis, productAnalysisProgress, isCurrentCreationPoll } from '../src/features/delivery/productAnalysisReadiness.ts';

const passed = { status: 'PASSED', canContinue: true };
const blocked = { status: 'BLOCKED', canContinue: false };
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
