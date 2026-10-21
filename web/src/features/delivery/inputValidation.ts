import rules from '../../../../lynx-ad-app/src/main/resources/delivery-input-policy.json' with { type: 'json' };

export const deliveryInputRules = rules;
export type InputProfile = 'creation' | 'update';
export const isCostMode = (mode: string) => mode === 'CUSTOM' || mode === 'SMART_BID_CUSTOM';
const validMode = (mode: string) => ['CUSTOM','SMART_BID_CUSTOM','CONSERVATIVE','SMART_BID_CONSERVATIVE'].includes(mode);
export const budgetMinimum = (mode: string,profile:InputProfile='creation') => isCostMode(mode) ? rules.costBudgetMinimum : rules[profile].volumeBudgetMinimum;
// Decimal input is parsed without floating point rounding (e.g. 300.001 must never become 300).
function decimal(value: string): number | undefined {
  const text=value.trim();
  if (!/^\d+(?:\.\d+)?$/.test(text)) return;
  const fraction=(text.split('.')[1] || '').replace(/0+$/, '');
  if (fraction.length > rules.decimalPlaces) return;
  const amount=Number(text); return Number.isFinite(amount) ? amount : undefined;
}
export function budgetInputError(value: string, mode: string, profile: InputProfile = 'creation') {
  if (!validMode(mode)) return '请选择控成本或放量投放';
  const amount=decimal(value), min=budgetMinimum(mode,profile), max=isCostMode(mode) ? rules[profile].budgetMaximum : rules[profile].volumeBudgetMaximum;
  if (amount === undefined) return '请输入正确的日预算，最多两位小数';
  if (amount < min) return `${isCostMode(mode) ? '控成本' : '放量'}日预算不能低于 ${min} 元（含 ${min} 元），最多两位小数`;
  return amount > max ? `日预算范围为 ${min}–${max} 元，最多两位小数` : '';
}
export function roiInputError(value: string, mode: string, profile: InputProfile = 'creation') {
  if (!isCostMode(mode)) return '';
  const amount=decimal(value), p=rules[profile];
  return amount === undefined || amount < p.roiMinimum || amount > p.roiMaximum ? `净成交ROI目标须为 ${p.roiMinimum}–${p.roiMaximum}，最多两位小数` : '';
}
export function planNameError(value: string) { return !value.trim() || value.trim().length > rules.nameMaxLength ? `计划名称须为 1–${rules.nameMaxLength} 字` : ''; }
function validDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date=new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0,10) === value;
}
export function scheduleInputError(duration: string, start: string, end: string, today: string) {
  return duration === 'custom' && (!validDate(start) || !validDate(end) || start < today || end < start) ? '开始日期不得早于今天，结束日期不得早于开始日期' : '';
}
export function platformTitleLength(value: string) {return [...value].reduce((length, c) => length + (/\p{Script=Han}/u.test(c) ? 2 : 1),0);}
export function titlesInputError(values: readonly string[], custom = true) {
  if (!values.length || custom && values.length > rules.customTitleMaximum) return `请填写 1–${rules.customTitleMaximum} 条自定义标题`;
  return values.some(value => {
    const title=value.trim(), size=platformTitleLength(title);
    return !title || title.length > rules.titleMaxLength || size < rules.titlePlatformMinimum || size > rules.titlePlatformMaximum;
  }) ? '素材标题须满足千川的 10–110 字符要求（汉字按 2 个字符计算，最多 55 字）' : '';
}
