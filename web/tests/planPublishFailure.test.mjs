import test from 'node:test';
import assert from 'node:assert/strict';
import { planPublishFailure, showPublishFailureResult } from '../src/features/delivery/planPublishFailure.ts';

test('默认恢复失败结果页，明确返回编辑、消息定位或未保存选品时保留操作上下文', () => {
  assert.equal(showPublishFailureResult('FAILED'), true);
  for (const step of [1, 2, 3]) assert.equal(showPublishFailureResult('FAILED', step), false);
  assert.equal(showPublishFailureResult('FAILED', undefined, true), false);
  assert.equal(showPublishFailureResult('FAILED', undefined, false, false), false);
  for (const status of ['NONE', 'CREATED', 'UNCERTAIN', 'SENDING']) assert.equal(showPublishFailureResult(status), false);
});

test('投放参考信息更新时明确处理事项与下一步，返回目标设置而不直接重发', () => {
  const view = planPublishFailure('REFRESH_ESTIMATE', '平台效果预估已更新');
  assert.equal(view.title, '投放计划暂未提交成功');
  assert.equal(view.description, '千川的投放参考信息已更新，需要重新获取后再提交。');
  assert.equal(view.handling, '更新投放参考信息');
  assert.match(view.next, /确认预算与目标后重新提交/);
  assert.equal(view.targetStep, 2);
  assert.equal(view.action, '返回更新投放设置');
  assert.match(view.retention, /商品、素材和投放设置会保留/);
});

test('普通或未知错误不伪装成预估过期，指向投放内容核对', () => {
  for (const recovery of [undefined, null, 'UNKNOWN_FUTURE_RECOVERY']) {
    const view = planPublishFailure(recovery, '素材不满足平台要求');
    assert.doesNotMatch(view.description, /参考信息已更新/);
    assert.equal(view.targetStep, 3);
    assert.equal(view.action, '返回检查投放内容');
    assert.equal(view.detail, '素材不满足平台要求');
  }
});

test('错误详情保留业务原因并隐藏敏感信息，空详情不编造平台错误', () => {
  const view = planPublishFailure(undefined, '错误 1234 access_token=secret123 https://internal.example/api');
  assert.match(view.detail, /错误 1234/);
  assert.doesNotMatch(view.detail, /secret123|internal\.example/);
  assert.match(planPublishFailure().detail, /暂未返回具体错误信息/);
});
