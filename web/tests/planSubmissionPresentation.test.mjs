import test from 'node:test';
import assert from 'node:assert/strict';
import { planSubmissionPhase, planSubmissionMessage, planSubmissionSummary } from '../src/features/delivery/planSubmissionPresentation.ts';

const source = {
  name: '户外用品投放计划', smartBidType: 'CUSTOM', budget: 300, roi2Goal: 4.82,
  scheduleType: 'FROM_NOW', startDate: '2026-10-27', endDate: '2036-10-27',
  products: [{ id: '1' }, { id: '2' }], createdAt: '2026-10-27T02:00:00Z',
};
const rows = plan => Object.fromEntries(planSubmissionSummary(plan, '户外店铺').flatMap(column => column.rows));

test('提交中、投放中与结果待确认分别展示真实阶段，成功后不继续显示提交动画', () => {
  for (const publishStatus of ['QUEUED', 'SENDING']) assert.equal(planSubmissionPhase({ publishStatus }), 'SUBMITTING');
  assert.equal(planSubmissionPhase({ status: 'SUBMITTING' }), 'SUBMITTING');
  assert.equal(planSubmissionPhase({ status: 'DELIVERING' }), 'DELIVERING');
  assert.equal(planSubmissionPhase({ publishStatus: 'UNCERTAIN' }), 'UNCERTAIN');
  assert.equal(planSubmissionPhase({ publishStatus: 'FAILED' }), 'FAILED');
  for (const status of ['DRAFT', 'PENDING_AUDIT', 'SCHEDULED', 'PAUSED', 'REJECTED', 'COMPLETED', 'FINISHED', 'TERMINATED']) {
    assert.equal(planSubmissionPhase({ status }), undefined);
  }
  assert.equal(planSubmissionPhase({ publishStatus: 'CREATED' }), undefined);
  assert.equal(planSubmissionMessage('SUBMITTING').title, '正在提交投放计划');
  assert.equal(planSubmissionMessage('DELIVERING').title, '投放中');
  assert.match(planSubmissionMessage('SUBMITTING').note, /不影响本次提交/);
});

test('摘要使用当前计划与抖音号，不把远期占位日期作为长期投放的结束日', () => {
  const result = rows(source);
  assert.equal(result['投放抖音号'], '户外店铺');
  assert.equal(result['投放方式'], '控成本投放');
  assert.equal(result['计划名称'], source.name);
  assert.equal(result['投放商品'], '2个');
  assert.equal(result['日预算'], '¥300 / 天');
  assert.equal(result['净成交ROI目标'], '4.82');
  assert.equal(result['投放时长'], '长期投放');
  assert.equal(result['广告计划创建时间'], '2026-10-27 10:00:00');
  assert.equal(rows({ ...source, scheduleType: 'DATE_RANGE', endDate: '2026-11-01' })['投放时长'], '2026-10-27 至 2026-11-01');
});

test('创建草稿与已发布计划的投放方式枚举一致，放量不回显残留ROI目标', () => {
  assert.equal(rows({ ...source, smartBidType: 'SMART_BID_CUSTOM' })['净成交ROI目标'], '4.82');
  for (const smartBidType of ['CONSERVATIVE', 'SMART_BID_CONSERVATIVE']) {
    const result = rows({ ...source, smartBidType });
    assert.equal(result['投放方式'], '放量投放');
    assert.equal(result['净成交ROI目标'], '无需设置');
  }
});

test('缺失字段保留未知，不使用当前时间、零数量或默认投放方式掩盖缺失数据', () => {
  const result = rows({});
  for (const key of ['计划名称', '投放商品', '日预算', '净成交ROI目标', '投放时长', '广告计划创建时间', '投放方式']) assert.equal(result[key], '—', key);
  assert.equal(rows({ products: [] })['投放商品'], '0个');
  assert.equal(rows({ budget: NaN })['日预算'], '—');
  assert.equal(rows({ ...source, createdAt: 'invalid' })['广告计划创建时间'], '—');
  assert.equal(rows({ ...source, createdAt: '2026-10-27T10:00:00' })['广告计划创建时间'], '2026-10-27 10:00:00');
  assert.equal(rows({ ...source, createdAt: '2026-10-27T10:00:00+08:00' })['广告计划创建时间'], '2026-10-27 10:00:00');
});
