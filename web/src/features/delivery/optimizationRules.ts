import type { OptimizationRules } from './optimizationApi';

export const defaultOptimizationRules = (roi?: number | null, budget?: number | null): OptimizationRules => ({
  roiFloor: roi && roi > 0 ? roi : 4,
  spendReminder: budget && budget > 0 ? budget : 400,
  spendProtection: budget && budget > 0 ? Math.round(budget * 120) / 100 : 480,
  protectionAction: 'CONFIRM', runawayMinutes: 5, runawaySpend: 100, maxAddition: 100, dailyAddition: 200,
});
export function validateOptimizationRules(rules: OptimizationRules) {
  const errors: Partial<Record<keyof OptimizationRules, string>> = {};
  for (const key of ['roiFloor', 'spendReminder', 'spendProtection', 'runawaySpend', 'maxAddition', 'dailyAddition'] as const) {
    const value = rules[key], max = key === 'roiFloor' ? 100 : 999999999.99;
    if (value == null || !Number.isFinite(Number(value)) || Number(value) < .01 || Number(value) > max || !/^\d+(\.\d{1,2})?$/.test(String(value)))
      errors[key] = key === 'roiFloor' ? '请输入0.01–100，最多两位小数' : '请输入0.01–999,999,999.99，最多两位小数';
  }
  if (!errors.spendProtection && !errors.spendReminder && Number(rules.spendProtection) < Number(rules.spendReminder)) errors.spendProtection = '保护金额不能低于提醒金额';
  if (!errors.dailyAddition && !errors.maxAddition && Number(rules.dailyAddition) < Number(rules.maxAddition)) errors.dailyAddition = '每日累计金额不能低于单次金额';
  if (!Number.isInteger(Number(rules.runawayMinutes)) || Number(rules.runawayMinutes) < 1 || Number(rules.runawayMinutes) > 60) errors.runawayMinutes = '请输入1–60的整数';
  if (!['CONFIRM', 'AUTO_PAUSE'].includes(rules.protectionAction)) errors.protectionAction = '请选择处理方式';
  return errors;
}
export const numericOptimizationRules = (rules: OptimizationRules): OptimizationRules => ({
  roiFloor: Number(rules.roiFloor), spendReminder: Number(rules.spendReminder), spendProtection: Number(rules.spendProtection),
  protectionAction: rules.protectionAction, runawayMinutes: Number(rules.runawayMinutes), runawaySpend: Number(rules.runawaySpend),
  maxAddition: Number(rules.maxAddition), dailyAddition: Number(rules.dailyAddition),
});
