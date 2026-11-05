import test from 'node:test';
import assert from 'node:assert/strict';
import { monitoringReviewHeader, reviewStatus } from '../src/features/delivery/optimizationDecisions.ts';
import { optimizationDetailState } from '../src/features/delivery/optimizationDetails.ts';
const id = '12345678-1234-4567-8123-123456789abc';
test('review correlation applies only to writes on the original plan', () => {
  assert.equal(monitoringReviewHeader('/campaigns/a/edit', `?monitoringReview=${id}`, 'PUT', '/api/v1/delivery/plans/a/settings'), id);
  for (const [route, method, url] of [['/campaigns/b/edit','PUT','/api/v1/delivery/plans/a'], ['/campaigns/a/edit','GET','/api/v1/delivery/plans/a'], ['/campaigns/a/edit','POST','/api/v1/delivery/plans/a/optimization/check'], ['/campaigns/a/edit','POST','/api/v1/delivery/plans/a/transfer'], ['/campaigns/a/edit','PUT','https://other.example/api/v1/delivery/plans/a'], ['/campaigns/a','PUT','/api/v1/delivery/plans/a']]) assert.equal(monitoringReviewHeader(route, `?monitoringReview=${id}`, method, url), undefined);
  assert.equal(monitoringReviewHeader('/campaigns/a/edit','?monitoringReview=bad','PUT','/api/v1/delivery/plans/a'), undefined);
  assert.equal(monitoringReviewHeader('/campaigns/a/edit', `?monitoringReview=${id}`, 'POST', '/api/v1/delivery/creation/work/content-edit/save'), id);
  for (const url of ['/api/v1/delivery/creation/work/publish', '/api/v1/delivery/creation/work/content-edit/save/extra', 'https://other.example/api/v1/delivery/creation/work/content-edit/save']) assert.equal(monitoringReviewHeader('/campaigns/a/edit', `?monitoringReview=${id}`, 'POST', url), undefined);
});
test('reviewing and missing comparable evidence are distinct from effect improvement', () => {
  assert.equal(reviewStatus('COMPARED'), '已完成可比复评');
  assert.equal(reviewStatus('INCOMPARABLE'), '不可单独比较');
  assert.equal(reviewStatus('WAITING_DATA'), '等待成熟数据');
  const d = { view: { policy: {enabled:true}, result: {state:'READY',strategy_approved:false,daily_spend:'120',daily_net_revenue:'0'} }, pending:[] };
  assert.equal(optimizationDetailState(d,'DELIVERING','ACTIVE').label,'部分受限');
  d.view.result.strategy_approved=true;d.view.result.business_ready=false;
  assert.equal(optimizationDetailState(d,'DELIVERING','ACTIVE').label,'部分受限');
});
