import { safeErrorMessage } from '../../api/apiErrors.ts';

export function showPublishFailureResult(status?: string, requestedStep?: number, materialNotice = false, sameProducts = true) {
  return status === 'FAILED' && requestedStep == null && !materialNotice && sameProducts;
}

export function planPublishFailure(recovery?: string | null, error?: string | null) {
  const refreshEstimate = recovery === 'REFRESH_ESTIMATE';
  return {
    title: '投放计划暂未提交成功',
    description: refreshEstimate
      ? '千川的投放参考信息已更新，需要重新获取后再提交。'
      : '本次投放计划未提交成功，请查看错误详情并核对投放内容。',
    handling: refreshEstimate ? '更新投放参考信息' : '检查并处理提交问题',
    next: refreshEstimate
      ? '返回投放目标页面刷新信息，确认预算与目标后重新提交。'
      : '返回素材与投放页面，核对投放内容并根据错误详情处理后重新提交。',
    action: refreshEstimate ? '返回更新投放设置' : '返回检查投放内容',
    targetStep: refreshEstimate ? 2 : 3,
    retention: '已保存的商品、素材和投放设置会保留，无需重新准备。',
    detail: safeErrorMessage(error) || '平台暂未返回具体错误信息，请返回检查投放内容后再试。',
  };
}
