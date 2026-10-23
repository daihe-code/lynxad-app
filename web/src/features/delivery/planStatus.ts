import rules from '../../../../lynx-ad-app/src/main/resources/plan-status-policy.json' with { type: 'json' };

export interface PlanStatusOption { value: string; label: string; children?: PlanStatusOption[] }
export interface PlanStatusDisplay { group: string; detail?: string; label: string }
const localLabels: Record<string,string> = rules.localLabels;
const platform: Record<string,PlanStatusDisplay> = rules.platform;
export const planStatusOptions: PlanStatusOption[] = rules.options;

const localDisplay = (status: string): PlanStatusDisplay => ({group:localLabels[status] ? status : 'UNKNOWN',label:localLabels[status] || '状态待核查'});
export function planStatusDisplay(status?: string | null, qcStatus?: string | null): PlanStatusDisplay {
  const local=status || 'UNKNOWN', raw=qcStatus?.trim() || '';
  if(local==='DELETED') return localDisplay(local);
  const remote=platform[raw];
  if(remote) {
    if(['DELETED','TERMINATED','COMPLETED','UNKNOWN'].includes(remote.group)) return remote;
    if(['DRAFT','SUBMITTING'].includes(local)
      || local==='PAUSED' && remote.group==='DELIVERING'
      || local==='DELIVERING' && remote.detail==='DISABLE'
      || local==='FINISHED' && !['FINISHED','COMPLETED','TERMINATED'].includes(remote.group)) return localDisplay(local);
    return remote;
  }
  return raw ? {group:'UNKNOWN',label:`平台状态：${raw}`} : localDisplay(local);
}
export function matchesPlanStatus(status: string, qcStatus: string | null | undefined, filter?: string | null) {
  const display=planStatusDisplay(status,qcStatus);
  if(filter==='ALL_INCLUDE_DELETED') return true;
  if(!filter?.trim() || filter==='ALL') return display.group!=='DELETED';
  if(filter==='PENDING_AUDIT') return ['AUDIT','REAUDIT','PENDING_AUDIT'].includes(display.group);
  if(filter==='FINISHED') return ['COMPLETED','TERMINATED','FINISHED'].includes(display.group);
  if(filter===display.group || filter===display.detail) return true;
  const raw=platform[filter];
  return !!raw && (raw.detail ? raw.detail===display.detail : raw.group===display.group
    && (filter===qcStatus || ['DELIVERY_OK','DELETE','TIME_DONE','FROZEN','OFFLINE_AUDIT'].includes(filter)));
}
