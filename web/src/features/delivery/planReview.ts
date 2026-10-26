export interface PlanReviewProgress {
  state: 'WAITING' | 'DELAYED' | 'CHECK_UNAVAILABLE';
  pendingSince?: string | null; lastCheckedAt?: string | null; nextCheckAt?: string | null;
  lastResult?: string | null; checking: boolean; manualRefreshAt?: string | null; message: string;
}
export interface ReviewPlan {
  status: string; qcPlanId?: string; review?: PlanReviewProgress | null;
}
export function isPlanUnderReview(plan?: ReviewPlan | null): boolean {
  return plan?.status === 'PENDING_AUDIT' && !!plan.qcPlanId;
}
export function hasPlanAuditResult(plan?: ReviewPlan | null): boolean {
  return isPlanUnderReview(plan) || plan?.status === 'REJECTED';
}
export function reviewNotice(plan: ReviewPlan, now=Date.now()) {
  if(!isPlanUnderReview(plan))return null;
  const review=plan.review;
  const since=reviewTimestamp(review?.pendingSince);
  const elapsed=Number.isFinite(since)?Math.max(0,Math.floor((now-since)/60000)):null;
  const waiting=elapsed==null?'本轮审核等待时间尚未记录':elapsed<1?'本轮审核已记录不足1分钟':`本轮审核已记录 ${elapsed} 分钟`;
  return {
    title:review?.state==='CHECK_UNAVAILABLE'?'审核结果暂未更新':review?.state==='DELAYED'?'平台审核等待较久':'等待平台审核',
    message:review?.message || '计划正在等待平台审核，结果以千川返回为准。无需重复发布，可继续其他操作。',
    waiting,
    unavailable:review?.state==='CHECK_UNAVAILABLE', delayed:review?.state==='DELAYED',
    cooldown:Math.max(0,Math.ceil((reviewTimestamp(review?.manualRefreshAt)-now)/1000)) || 0,
  };
}

function reviewTimestamp(value?: string | null): number {
  if (!value) return NaN;
  const timestamp = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(value)
    ? `${value.replace(' ', 'T')}+08:00` : value;
  return Date.parse(timestamp);
}

export function reviewTime(value?: string | null, missing = '尚未记录'): string {
  const date = new Date(reviewTimestamp(value));
  if (!Number.isFinite(date.getTime())) return missing;
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(date).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
}

export function reviewTimeline(plan: ReviewPlan) {
  if (!isPlanUnderReview(plan)) return [];
  return [
    { title: '提交至平台审核', state: 'done', time: reviewTime(plan.review?.pendingSince, '提交时间待同步'),
      description: '计划已进入巨量千川审核队列，投放信息见上方摘要。' },
    { title: '平台审核中', state: 'current', time: '进行中',
      description: '平台正在审核素材创意、商品及投放资质，等待期间你可以正常进行其他操作。' },
    { title: '审核结果', state: 'waiting', time: '待开始',
      description: '审核通过后将按投放设置进入投放队列；若未通过，将展示审核意见和对应处理入口。' },
  ];
}
