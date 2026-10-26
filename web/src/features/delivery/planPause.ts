export const PLAN_PAUSE_REASONS = [
  '预算消耗过快',
  '净成交 ROI 低于预期',
  '商品库存不足或临时缺货',
  '需要更换素材创意',
  '需要调整投放时段',
  '商品价格或活动调整中',
  '账号或资质问题排查中',
  '其他原因',
] as const;

export type PlanPauseReason = typeof PLAN_PAUSE_REASONS[number];

export function isPlanPauseReason(value: unknown): value is PlanPauseReason {
  return typeof value === 'string' && PLAN_PAUSE_REASONS.some(reason => reason === value);
}
