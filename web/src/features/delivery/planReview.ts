export interface PlanReviewProgress {
  state: 'WAITING' | 'DELAYED' | 'CHECK_UNAVAILABLE';
  pendingSince?: string | null; lastCheckedAt?: string | null; nextCheckAt?: string | null;
  lastResult?: string | null; checking: boolean; manualRefreshAt?: string | null; message: string;
}
export function reviewNotice(plan: { status: string; qcPlanId?: string; review?: PlanReviewProgress | null }, now=Date.now()) {
  if(plan.status!=='PENDING_AUDIT' || !plan.qcPlanId)return null;
  const review=plan.review;
  const since=review?.pendingSince?Date.parse(review.pendingSince):NaN;
  const elapsed=Number.isFinite(since)?Math.max(0,Math.floor((now-since)/60000)):null;
  const waiting=elapsed==null?'本轮审核等待时间尚未记录':elapsed<1?'本轮审核已记录不足1分钟':`本轮审核已记录 ${elapsed} 分钟`;
  return {
    title:review?.state==='CHECK_UNAVAILABLE'?'审核结果暂未更新':review?.state==='DELAYED'?'平台审核等待较久':'等待平台审核',
    message:review?.message || '计划正在等待平台审核，结果以千川返回为准。无需重复发布，可继续其他操作。',
    waiting,
    unavailable:review?.state==='CHECK_UNAVAILABLE', delayed:review?.state==='DELAYED',
    cooldown:Math.max(0,Math.ceil(((review?.manualRefreshAt?Date.parse(review.manualRefreshAt):0)-now)/1000)) || 0,
  };
}
