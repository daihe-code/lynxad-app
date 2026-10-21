import type { CreationMaterial, CreationState } from './creationApi';
export function processingLabel(status?: string): string {
  return ({SUCCEEDED:'完成',NOT_REQUIRED:'无需处理',PENDING:'等待中',RUNNING:'处理中',FAILED:'失败',
    REJECTED:'未通过',UNCERTAIN:'结果待确认',WAITING_AUTH:'等待授权',PAUSED:'已暂停',UNKNOWN:'待核验'} as Record<string,string>)[status || 'UNKNOWN'] || '待核验';
}
export function materialIsReady(material: CreationMaterial): boolean {
  return material.status === 'READY' && (!material.processing || material.processing.stage === 'READY');
}
export function materialHasPreview(material: CreationMaterial): boolean {
  return !!(material.posterUrl || material.imageUrls?.length || (material.type === 'VIDEO' && material.previewUrl));
}
export function materialProcessingLabel(material: CreationMaterial): string {
  const p=material.processing;
  if (!p) return material.status === 'READY' ? '可用 · 已同步千川' : '处理状态待核验';
  const origin=p.generationStatus === 'SUCCEEDED' ? '已生成' : p.storageStatus === 'SUCCEEDED' ? '已上传' : '素材';
  if (p.stage === 'READY') return '可用 · 已同步千川';
  if (p.stage === 'REGISTRATION') return `${origin} · ${p.storageStatus === 'FAILED' ? '登记失败' : p.storageStatus === 'UNKNOWN' ? '登记状态待核验' : '正在登记素材'}`;
  if (p.stage === 'REVIEW') return `${origin} · ${['REJECTED','FAILED'].includes(p.reviewStatus) ? '质检未通过' : '等待质检'}`;
  if (p.stage === 'SYNC') return `${origin} · ${{SUCCEEDED:'已同步千川',FAILED:'千川同步失败',UNCERTAIN:'千川上传待确认',WAITING_AUTH:'等待千川授权',REJECTED:'千川审核未通过',PAUSED:'千川同步已暂停',UNKNOWN:'同步状态待核验'}[p.syncStatus] || '正在同步千川'}`;
  if (p.stage === 'VALIDATION') return '已同步千川 · 投放校验待处理';
  return '处理状态待核验';
}
export function generationResultLabel(state: Pick<CreationState,'generationResultStatus'|'generationStatus'>): string {
  const status=state.generationResultStatus || (state.generationStatus === 'SUCCEEDED' ? 'SUCCEEDED' : 'UNKNOWN');
  return ({NONE:'尚未发起智能生成',QUEUED:'等待生成',SUBMITTING:'正在提交生成任务',RUNNING:'正在生成',REVISING:'正在生成修改版',
    SUCCEEDED:'生成完成',PARTIAL_FAILED:'部分生成未完成',FAILED:'生成失败',CANCELLED:'生成已取消',UNCERTAIN:'生成任务提交待确认',UNKNOWN:'生成结果待核验'} as Record<string,string>)[status] || '生成结果待核验';
}
export function processingSummary(materials: CreationMaterial[]): string {
  const ready=materials.filter(materialIsReady).length;
  const count=(stage:string)=>materials.filter(m=>m.processing?.stage === stage).length;
  const parts=[`可投放 ${ready}/${materials.length} 条`];
  for(const [stage,label] of [['REGISTRATION','登记待处理'],['REVIEW','质检待处理'],['SYNC','千川同步待处理'],['VALIDATION','投放校验待处理'],['UNKNOWN','状态待核验']]) {
    const total=count(stage!);if(total)parts.push(`${label} ${total} 条`);
  }
  return parts.join(' · ');
}
export const componentLabel=(key:string)=>key.startsWith('IMAGE_') ? `图片 ${Number(key.slice(6))+1}`
  : ({VIDEO:'视频',COVER:'视频封面',AUDIO:'音频',TEXT:'文案',CAROUSEL:'图文组合'} as Record<string,string>)[key] || '素材组件';
