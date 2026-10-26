import { safeErrorMessage } from '../../api/apiErrors.ts';

export type PublishFailureTarget = 'PRODUCTS' | 'GOAL' | 'MATERIALS' | 'ACCOUNTS';
export type PublishFailureRecovery = PublishFailureTarget | 'REFRESH_ESTIMATE' | 'UNKNOWN';
export const publishFailurePages: { value: PublishFailureTarget; label: string }[] = [
  { value: 'PRODUCTS', label: '选择商品' }, { value: 'GOAL', label: '设置投放目标' },
  { value: 'MATERIALS', label: '定素材并投放' }, { value: 'ACCOUNTS', label: '广告投放账户授权' },
];
export function classifyPublishFailure(error?: string | null): PublishFailureRecovery {
  const detail = safeErrorMessage(error).split(/[（(](?:错误码|code)/i)[0] || '';
  if (/授权账户|(?:账户|广告主|抖音号).{0,12}(?:授权|权限)|授权(?:已)?(?:失效|过期|无效|取消|不可用)|凭证|权限不足|无权限操作|无权操作(?:此)?账户|access_token|refresh_token|\b(?:advertiser_id|aweme_id)\b/i.test(detail)) return 'ACCOUNTS';
  if (/(?:效果预估|投放参考信息|预估结果|平台预估).{0,30}(?:更新|过期|失效|重新获取)/i.test(detail)) return 'REFRESH_ESTIMATE';
  if (/\b(?:product_ids?|goods_ids?|commodity_ids?)\b|库存不足|缺货|商品(?!素材|视频|图片|图文|标题|文案)[^，。；;,]{0,20}(?:下架|不存在|不可投|不可推广|无投放权限|类目不支持|资质不符合)/i.test(detail)) return 'PRODUCTS';
  if (/预算|日限额|投放时间|开始时间|结束时间|投放日期|转化目标|成本目标|投放方式|\b(?:roi|roi2_goal|budget|start_date|end_date|start_time|end_time|schedule_type|smart_bid_type)\b/i.test(detail)) return 'GOAL';
  if (/素材|视频|图片|图文|封面|创意|标题|文案|\b(?:video_ids?|image_ids?|creative_list|title_list)\b/i.test(detail)) return 'MATERIALS';
  return 'UNKNOWN';
}
export function publishFailureTarget(recovery?: string | null, error?: string | null, choice?: string): PublishFailureTarget | undefined {
  const failure = planPublishFailure(recovery, error);
  return failure.target || publishFailurePages.find(page => page.value === choice)?.value;
}
export function publishFailureStep(target?: PublishFailureTarget): 1 | 2 | 3 | undefined {
  return target === 'PRODUCTS' ? 1 : target === 'GOAL' ? 2 : target === 'MATERIALS' ? 3 : undefined;
}
export function publishFailureLocation(planId: string, recovery?: string | null, error?: string | null, choice?: string) {
  const target = publishFailureTarget(recovery, error, choice);
  if (!target) return;
  return target === 'ACCOUNTS' ? { path: '/accounts', query: { tab: 'oauth' } }
    : { path: `/campaigns/${planId}/edit`, query: { step: String(publishFailureStep(target)), recover: target } };
}

export function showPublishFailureResult(status?: string, requestedStep?: number, materialNotice = false, sameProducts = true) {
  return status === 'FAILED' && requestedStep == null && !materialNotice && sameProducts;
}

export function planPublishFailure(recovery?: string | null, error?: string | null) {
  const known: PublishFailureRecovery[] = ['REFRESH_ESTIMATE', 'PRODUCTS', 'GOAL', 'MATERIALS', 'ACCOUNTS', 'UNKNOWN'];
  const code = recovery ? known.includes(recovery as PublishFailureRecovery) ? recovery as PublishFailureRecovery : 'UNKNOWN' : classifyPublishFailure(error);
  const refreshEstimate = code === 'REFRESH_ESTIMATE';
  const target: PublishFailureTarget | undefined = refreshEstimate ? 'GOAL' : code === 'UNKNOWN' ? undefined : code;
  const instructions = {
    PRODUCTS: { handling: '检查投放商品', next: '返回选择商品页面，处理下架、库存或商品可投问题后重新分析。', action: '返回调整商品' },
    GOAL: { handling: '检查投放目标与设置', next: '返回投放目标页面，按错误详情检查预算、ROI、投放方式或日期后重新提交。', action: '返回修改投放目标' },
    MATERIALS: { handling: '检查素材与标题', next: '返回定素材并投放页面，按错误详情修改对应素材或标题后重新提交。', action: '返回修改素材' },
    ACCOUNTS: { handling: '检查广告账户授权', next: '前往账户管理核对千川授权，恢复对应账户与抖音号权限后再返回计划。', action: '前往授权管理' },
  };
  const instruction = target ? instructions[target] : {
    handling: '核对平台返回的失败原因', next: '查看错误详情，选择需要检查的页面后返回处理。', action: '返回处理问题',
  };
  return {
    target, targetStep: publishFailureStep(target), refreshEstimate,
    title: '投放计划暂未提交成功',
    description: refreshEstimate
      ? '千川的投放参考信息已更新，需要重新获取后再提交。'
      : target ? '本次投放计划未提交成功，请按失败原因返回对应页面处理。' : '平台未指明具体出错项，请查看详情并选择需要检查的页面。',
    handling: refreshEstimate ? '更新投放参考信息' : instruction.handling,
    next: refreshEstimate
      ? '返回投放目标页面刷新信息，确认预算与目标后重新提交。'
      : instruction.next,
    action: refreshEstimate ? '返回更新投放设置' : instruction.action,
    retention: '已保存的商品、素材和投放设置会保留，无需重新准备。',
    detail: safeErrorMessage(error) || '平台暂未返回具体错误信息，请返回检查投放内容后再试。',
  };
}
