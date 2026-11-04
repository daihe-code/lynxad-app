// Pure edit reconciliation: keep only fields the user changed, and adopt fresh values for the rest.
export interface PlanEditSnapshot {
  planId: string; name: string; qcPlanId?: string; accountId?: string; awemeId?: string;
  scheduleType?: string | null; startDate?: string | null; endDate?: string | null;
  smartBidType?: string; status: string; budget?: number; roi2Goal?: number | null;
  settingsEdit?: {version: string; status: string; message?: string | null} | null;
}
export interface PlanEditInputs {name: string; budget: string; roi: string; duration?: 'long' | 'custom'; end?: string}
const duration = (p: PlanEditSnapshot) => p.scheduleType === 'FROM_NOW' ? 'long' : p.scheduleType === 'DATE_RANGE' || p.endDate ? 'custom' : 'long';
const numberSame = (input: string, value?: number | null) => input.trim() !== '' && value != null && Number(input) === value;
export function changedPlanInputs(base: PlanEditSnapshot, inputs: PlanEditInputs) {
  return {name:inputs.name.trim() !== base.name,budget:!numberSame(inputs.budget,base.budget),
    roi:base.smartBidType === 'SMART_BID_CUSTOM' && !numberSame(inputs.roi,base.roi2Goal),
    ...(inputs.duration ? {schedule:inputs.duration !== duration(base) || inputs.duration === 'custom' && inputs.end !== base.endDate} : {})};
}
export function canRebasePlan(base: PlanEditSnapshot, latest: PlanEditSnapshot) {
  return !!latest.settingsEdit?.version && latest.settingsEdit.status === 'IDLE' && ['DELIVERING','PAUSED'].includes(latest.status)
    && ['planId','qcPlanId','accountId','awemeId','smartBidType'].every(key=>base[key as keyof PlanEditSnapshot] === latest[key as keyof PlanEditSnapshot]);
}
/** A content save advances the plan version; unrelated goal changes still need explicit review. */
export function canContinueSettingsAfterContent(base: PlanEditSnapshot, latest: PlanEditSnapshot) {
  return canRebasePlan(base,latest) && ['name','status','budget','roi2Goal','startDate'].every(key=>base[key as keyof PlanEditSnapshot] === latest[key as keyof PlanEditSnapshot])
    && duration(base) === duration(latest) && (duration(base)==='long' || base.endDate===latest.endDate);
}
export function rebasePlanInputs(base: PlanEditSnapshot, latest: PlanEditSnapshot, inputs: PlanEditInputs): PlanEditInputs {
  if (!canRebasePlan(base,latest)) throw new Error('计划绑定、投放方式或状态已变化，请采用最新设置后重新编辑');
  const changed=changedPlanInputs(base,inputs);
  return {name:changed.name ? inputs.name : latest.name,budget:changed.budget ? inputs.budget : String(latest.budget ?? ''),
    roi:changed.roi ? inputs.roi : String(latest.roi2Goal ?? ''),
    ...(inputs.duration ? {duration:changed.schedule ? inputs.duration : duration(latest) as 'long' | 'custom',end:changed.schedule ? inputs.end : latest.endDate || inputs.end} : {})};
}
export function planEditComparison(base: PlanEditSnapshot, latest: PlanEditSnapshot, inputs: PlanEditInputs) {
  const changed=changedPlanInputs(base,inputs);
  const schedule=(d:string,e?:string|null)=>d==='long'?'长期投放':`自定日期，结束于 ${e||'待填写'}`;
  const mode=(value?:string)=>value === 'SMART_BID_CUSTOM'?'控成本投放':value === 'SMART_BID_CONSERVATIVE'?'放量投放':value||'—';
  const status=(value:string)=>({DELIVERING:'投放中',PAUSED:'已暂停',PENDING_AUDIT:'审核中',REJECTED:'审核未通过',FINISHED:'已结束',DELETED:'已删除',DRAFT:'草稿'} as Record<string,string>)[value]||value;
  return [{key:'name',label:'计划名称',before:base.name,mine:inputs.name,latest:latest.name,changed:changed.name},
    {key:'budget',label:'日预算（元）',before:String(base.budget ?? '—'),mine:inputs.budget,latest:String(latest.budget ?? '—'),changed:changed.budget},
    ...(base.smartBidType === 'SMART_BID_CUSTOM' ? [{key:'roi',label:'净成交ROI目标',before:String(base.roi2Goal ?? '—'),mine:inputs.roi,latest:String(latest.roi2Goal ?? '—'),changed:changed.roi}] : []),
    ...(inputs.duration ? [{key:'schedule',label:'投放时长',before:schedule(duration(base),base.endDate),mine:schedule(inputs.duration,inputs.end),latest:schedule(duration(latest),latest.endDate),changed:!!changed.schedule}] : []),
    ...(base.smartBidType!==latest.smartBidType?[{key:'mode',label:'投放方式',before:mode(base.smartBidType),mine:mode(base.smartBidType),latest:mode(latest.smartBidType),changed:false}]:[]),
    ...(base.status!==latest.status?[{key:'status',label:'计划状态',before:status(base.status),mine:status(base.status),latest:status(latest.status),changed:false}]:[])];
}
