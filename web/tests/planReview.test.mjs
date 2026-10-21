import assert from 'node:assert/strict';
import test from 'node:test';
import { reviewNotice } from '../src/features/delivery/planReview.ts';
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
