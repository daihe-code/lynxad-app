import assert from 'node:assert/strict';
import test from 'node:test';
import { reviewNotice, reviewTimeline, reviewTime, isPlanUnderReview, hasPlanAuditResult } from '../src/features/delivery/planReview.ts';
const now=Date.parse('2026-10-17T10:30:00Z');
const plan={status:'PENDING_AUDIT',qcPlanId:'456',createdAt:'2025-01-01T00:00:00Z'};
test('历史草稿创建时间不冒充审核开始时间，也不推断审核失败',()=>{
  assert.match(reviewNotice(plan,now).waiting,/尚未记录/);
  assert.equal(reviewNotice(plan,now).title,'等待平台审核');
});
test('延迟和查询失败分别展示，保留本轮等待时间',()=>{
  const review={state:'DELAYED',pendingSince:'2026-10-17T10:00:00Z',checking:false,message:'仍待审核'};
  assert.equal(reviewNotice({...plan,review},now).title,'平台审核等待较久');
  assert.match(reviewNotice({...plan,review},now).waiting,/30 分钟/);
  assert.equal(reviewNotice({...plan,review:{...review,state:'CHECK_UNAVAILABLE'}},now).title,'审核结果暂未更新');
});
test('已结束的审核和未发布草稿不显示审核等待提示',()=>{
  for(const status of ['DRAFT','SUBMITTING','REJECTED','DELIVERING','PAUSED','DELETED'])assert.equal(reviewNotice({...plan,status},now),null);
  assert.equal(reviewNotice({status:'PENDING_AUDIT'},now),null);
});
test('手动刷新遵循共享冷却时间，未来时间不显示负等待',()=>{
  const review={state:'WAITING',checking:false,message:'等待',pendingSince:'2026-10-17T11:00:00Z',manualRefreshAt:'2026-10-17T10:30:20Z'};
  assert.equal(reviewNotice({...plan,review},now).cooldown,20);
  assert.match(reviewNotice({...plan,review},now).waiting,/不足1分钟/);
});

test('审批进度使用本轮审核记录，不把早期草稿创建时间当作提交审核时间',()=>{
  const nodes=reviewTimeline({...plan,review:{state:'WAITING',pendingSince:'2026-10-26T23:00:12Z'}});
  assert.deepEqual(nodes.map(node=>node.state),['done','current','waiting']);
  assert.deepEqual(nodes.map(node=>node.title),['提交至平台审核','平台审核中','审核结果']);
  assert.equal(nodes[0].time,'2026-10-27 07:00:12');
  assert.equal(reviewTimeline(plan)[0].time,'提交时间待同步');
  assert.equal(reviewTimeline({...plan,review:{pendingSince:'invalid'}})[0].time,'提交时间待同步');
});

test('仅已提交平台的审核中计划显示审批模块，查询失败和等待较久仍保持审核中',()=>{
  for(const state of ['WAITING','DELAYED','CHECK_UNAVAILABLE']) {
    const current={...plan,review:{state}};
    assert.equal(isPlanUnderReview(current),true);
    assert.equal(reviewTimeline(current)[2].state,'waiting');
  }
  for(const status of ['SUBMITTING','REJECTED','DELIVERING','SCHEDULED','DRAFT']) {
    assert.equal(isPlanUnderReview({...plan,status}),false);
    assert.deepEqual(reviewTimeline({...plan,status}),[]);
  }
  assert.equal(isPlanUnderReview({...plan,qcPlanId:undefined}),false);
});

test('审批时间统一用北京时间，缺失和无效时间保留未知',()=>{
  for(const value of ['2026-10-27T07:00:12','2026-10-27 07:00:12','2026-10-26T23:00:12Z','2026-10-27T07:00:12+08:00']) {
    assert.equal(reviewTime(value),'2026-10-27 07:00:12');
  }
  for(const value of [null,undefined,'','bad'])assert.equal(reviewTime(value,'尚未查询'),'尚未查询');
  assert.match(reviewNotice({...plan,review:{pendingSince:'2026-10-27 07:00:12'}},Date.parse('2026-10-26T23:05:12Z')).waiting,/5 分钟/);
});

test('拒审结果仍展示审核结果模块，审核完成后不再显示审批进度',()=>{
  assert.equal(hasPlanAuditResult(plan),true);
  assert.equal(hasPlanAuditResult({status:'REJECTED',qcPlanId:'456'}),true);
  assert.equal(hasPlanAuditResult({status:'REJECTED'}),true);
  assert.deepEqual(reviewTimeline({status:'REJECTED',qcPlanId:'456'}),[]);
  for(const status of ['DELIVERING','SCHEDULED','PAUSED','FINISHED','COMPLETED','DRAFT'])assert.equal(hasPlanAuditResult({status,qcPlanId:'456'}),false);
});
