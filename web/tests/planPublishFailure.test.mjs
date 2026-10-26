import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { planPublishFailure, showPublishFailureResult, classifyPublishFailure, publishFailureTarget, publishFailureLocation } from '../src/features/delivery/planPublishFailure.ts';

test('新失败位置与旧错误消息使用同一组定位案例，商品、目标、素材分别对应负责页面', () => {
  const cases = JSON.parse(readFileSync(new URL('../../lynx-ad-app/src/test/resources/publish-failure-recovery.json',import.meta.url),'utf8'));
  for (const row of cases) assert.equal(classifyPublishFailure(row.message),row.recovery,row.message);
  for (const [code,step] of [['PRODUCTS',1],['GOAL',2],['MATERIALS',3]]) {
    const view = planPublishFailure(code,'没有描述具体字段');
    assert.equal(view.targetStep,step);
    assert.equal(publishFailureLocation('plan',code).query.step,String(step));
  }
  assert.deepEqual(publishFailureLocation('plan','ACCOUNTS'),{path:'/accounts',query:{tab:'oauth'}});
});

test('已保存的明确失败位置优先，不能被页面上的选择或旧错误文案覆盖', () => {
  assert.equal(publishFailureTarget('PRODUCTS','日预算过低','MATERIALS'),'PRODUCTS');
  assert.equal(publishFailureTarget(undefined,'日预算过低'),'GOAL');
  assert.equal(planPublishFailure(undefined,'效果预估结果已更新，请重新获取').refreshEstimate,true);
});

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

test('素材问题返回素材页；无法定位的错误必须明确选择，不能直接退回上一页', () => {
  for (const recovery of [undefined, null, 'MATERIALS']) {
    const view = planPublishFailure(recovery, '素材不满足平台要求');
    assert.doesNotMatch(view.description, /参考信息已更新/);
    assert.equal(view.targetStep, 3);
    assert.equal(view.action, '返回修改素材');
    assert.equal(view.detail, '素材不满足平台要求');
  }
  for (const recovery of [undefined,'UNKNOWN','UNKNOWN_FUTURE_RECOVERY']) {
    assert.equal(planPublishFailure(recovery,'平台拒绝请求：参数异常').targetStep,undefined);
    assert.equal(publishFailureLocation('plan',recovery,'平台拒绝请求：参数异常'),undefined);
    assert.equal(publishFailureLocation('plan',recovery,'平台拒绝请求：参数异常','GOAL').query.step,'2');
    assert.equal(publishFailureLocation('plan',recovery,'平台拒绝请求：参数异常','invalid'),undefined);
  }
});

test('错误详情保留业务原因并隐藏敏感信息，空详情不编造平台错误', () => {
  const view = planPublishFailure(undefined, '错误 1234 access_token=secret123 https://internal.example/api');
  assert.match(view.detail, /错误 1234/);
  assert.doesNotMatch(view.detail, /secret123|internal\.example/);
  assert.match(planPublishFailure().detail, /暂未返回具体错误信息/);
});
