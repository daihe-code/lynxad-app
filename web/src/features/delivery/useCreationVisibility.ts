import { computed, ref, watch, type Ref } from 'vue';
import type { CreationState, VisibilitySetting } from './creationApi';

/** One write at a time; replies never replace a newer local selection. */
export function useCreationVisibility(options: {
  current: () => CreationState | undefined; scope: () => string; blocked: () => boolean;
  save: (state: CreationState, value: boolean) => Promise<VisibilitySetting>;
  accept: (setting: VisibilitySetting) => void;
  recover: (state: CreationState) => Promise<CreationState>; acceptRecovery: (state: CreationState) => void;
  message: (error: unknown) => string; saving: Ref<boolean>;
}) {
  const desired=ref<boolean>(),error=ref('');
  let version=0,active=true,failedValue:boolean|undefined,inFlight:Promise<boolean>|undefined;
  const value=computed(()=>desired.value ?? options.current()?.hideInAweme ?? true);
  const current=(epoch:number,scope:string)=>active&&version===epoch&&options.scope()===scope;
  function reset(){version++;desired.value=undefined;error.value='';failedValue=undefined;options.saving.value=false;inFlight=undefined;}
  const unwatch=watch(options.scope,reset,{flush:'sync'});
  async function drain(epoch:number,scope:string) {
    let snapshot=options.current()!;
    try {
      while(current(epoch,scope)&&desired.value!==undefined) {
        snapshot=options.current()!;
        if(snapshot.hideInAweme===desired.value)break;
        const requested=desired.value,saved=await options.save(snapshot,requested);
        if(!current(epoch,scope))return false;
        if(saved.planId!==snapshot.planId||saved.hideInAweme!==requested||saved.revision<snapshot.revision)throw new Error('素材可见性保存结果未确认，请重试。');
        options.accept(saved);
      }
      if(!current(epoch,scope))return false;
      desired.value=undefined;return true;
    } catch(failure) {
      if(!current(epoch,scope))return false;
      // Read back an ambiguous or rejected write before exposing the actual saved choice.
      try{const saved=await options.recover(snapshot);if(!current(epoch,scope))return false;options.acceptRecovery(saved);}catch{/* Keep the last acknowledged state if offline. */}
      if(!current(epoch,scope))return false;
      if(options.current()?.hideInAweme===desired.value){desired.value=undefined;return true;}
      failedValue=desired.value;desired.value=undefined;error.value=options.message(failure);return false;
    } finally {if(current(epoch,scope)){options.saving.value=false;inFlight=undefined;}}
  }
  function select(next:boolean) {
    if(!active||options.blocked()||!options.current())return;
    desired.value=next;error.value='';failedValue=undefined;
    if(options.saving.value)return;
    if(options.current()!.hideInAweme===next){desired.value=undefined;return;}
    options.saving.value=true;inFlight=drain(version,options.scope());
  }
  function retry(){if(failedValue!==undefined)select(failedValue);}
  function flush(){return inFlight || Promise.resolve(true);}
  function stop(){active=false;unwatch();reset();}
  return {value,error,select,retry,flush,stop};
}
