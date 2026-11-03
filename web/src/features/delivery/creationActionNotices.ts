import type { CreationMaterial, CreationState } from './creationApi';
import { productTitleValues, type TitleKind } from './productTitles.ts';

export type CompletionMessage = '新增完成' | '素材生成成功' | '删除成功';
type Pending = { kind:'ADD_FILES'|'ADD_JOB'|'REGENERATE'|'TITLE'|'DELETE'; id:string; ids?:string[] };
const active = new Set(['QUEUED','SUBMITTING','SENDING','RESERVED','ACCEPTED','RUNNING','IMPORTING','PROCESSING','UNCERTAIN']);
const ready = (m?:CreationMaterial) => !!m && m.status==='READY' && (!m.processing || m.processing.stage==='READY');
const find = (s:CreationState,id:string) => s.materials.find(m=>m.id===id) || s.materialVersions?.find(v=>v.material.id===id)?.material;

/** Observe durable completion receipts, never a submitted request or an unchanged original. */
export function createCreationActionNotices(show:(options:{message:CompletionMessage;duration:number})=>void) {
  const pending=new Map<string,Map<string,Pending>>(), completed=new Set<string>();
  function success(plan:string,key:string,message:CompletionMessage) {
    const identity=`${plan}:${key}`;
    if(completed.has(identity))return;
    completed.add(identity);
    if(completed.size>1000)completed.delete(completed.values().next().value!);
    show({message,duration:3000});
  }
  function track(s:CreationState,key:string,task:Pending) {
    if(completed.has(`${s.planId}:${key}`))return;
    let tasks=pending.get(s.planId);if(!tasks){tasks=new Map();pending.set(s.planId,tasks);}
    tasks.set(key,task);
  }
  function title(s:CreationState) {
    const task=s.titleGeneration;
    if(task?.requestId)track(s,`title:${task.requestId}`,{kind:'TITLE',id:task.requestId});
  }
  function regeneration(s:CreationState) {
    const task=s.materialRevision;
    if(task?.requestId)track(s,`regenerate:${task.requestId}`,{kind:'REGENERATE',id:task.requestId});
  }
  function observe(s?:CreationState) {
    if(!s)return;
    // Resume only unfinished work on entry; old completed work must stay quiet.
    for(const job of s.generationAdditions || [])if(active.has(job.status))track(s,`addition:${job.id}`,{kind:'ADD_JOB',id:job.id});
    if(s.titleGeneration && active.has(s.titleGeneration.status))title(s);
    if(s.generationStatus==='REVISING' || s.materialVersions?.some(v=>v.material.status==='PROCESSING'))regeneration(s);
    for(const m of s.materials)if(m.status==='PROCESSING' && ['UPLOAD','LIBRARY'].includes(m.addedVia || ''))
      track(s,`files:${m.id}`,{kind:'ADD_FILES',id:m.id,ids:[m.id]});
    const tasks=pending.get(s.planId);if(!tasks)return;
    for(const [key,task] of tasks) {
      let message:CompletionMessage|undefined, failed=false;
      if(task.kind==='ADD_FILES') {
        const files=task.ids!.map(id=>find(s,id));
        if(files.every(ready))message='新增完成';
        else failed=files.some(m=>!m || m.status==='FAILED');
      }else if(task.kind==='ADD_JOB') {
        const job=s.generationAdditions?.find(r=>r.id===task.id);
        if(job?.status==='SUCCEEDED' && job.materialId && ready(find(s,job.materialId)))message='新增完成';
        else failed=!job || ['FAILED','CANCELLED'].includes(job.status);
      }else if(task.kind==='REGENERATE') {
        const receipt=s.materialRevision;
        if(receipt?.requestId===task.id && receipt.status!=='FAILED' && receipt.materialId && receipt.materialId!==receipt.sourceId && ready(find(s,receipt.materialId)))message='素材生成成功';
        else failed=receipt?.requestId!==task.id || receipt.status==='FAILED';
      }else if(task.kind==='TITLE') {
        const receipt=s.titleGeneration;
        if(receipt?.requestId===task.id && receipt.status==='SUCCEEDED')message=receipt.addition?'新增完成':'素材生成成功';
        else failed=receipt?.requestId!==task.id || receipt.status==='FAILED';
      }else {
        failed=s.materialEdit?.status==='FAILED';
        if(!failed && !find(s,task.id))message='删除成功';
      }
      if(message || failed)tasks.delete(key);
      if(message)success(s.planId,key,message);
    }
    if(!tasks.size)pending.delete(s.planId);
  }
  return {
    observe,
    added(before:CreationState,next:CreationState) {
      if(before.planId!==next.planId)return;
      // One receipt per actual material, shared by explicit adds and resumed upload polling.
      for(const m of next.materials)if(!before.materials.some(old=>old.id===m.id))track(next,`files:${m.id}`,{kind:'ADD_FILES',id:m.id,ids:[m.id]});
      observe(next);
    },
    addition(next:CreationState,id:string) {track(next,`addition:${id}`,{kind:'ADD_JOB',id});observe(next);},
    regenerated(next:CreationState) {regeneration(next);observe(next);},
    titleGenerated(next:CreationState) {title(next);observe(next);},
    titleAdded(before:CreationState,next:CreationState,product:string,kind:TitleKind,ai:boolean) {
      if(ai){title(next);observe(next);return;}
      const values=(s:CreationState)=>productTitleValues(s.materials.filter(m=>m.productId===product),s.titlePools?.[product],kind);
      if(values(next).some(value=>!values(before).includes(value)))success(next.planId,`title-add:${product}:${kind}:${next.revision}`,'新增完成');
    },
    titlesEdited(before:CreationState,next:CreationState,product:string,kind:TitleKind) {
      const count=(s:CreationState)=>productTitleValues(s.materials.filter(m=>m.productId===product),s.titlePools?.[product],kind).length;
      if(count(next)<count(before))success(next.planId,`title-delete:${product}:${kind}:${next.revision}`,'删除成功');
    },
    deleted(next:CreationState,id:string) {track(next,`delete:${id}`,{kind:'DELETE',id});observe(next);},
    failureDeleted(next:CreationState,slot:string) {
      if(!next.generationIssues?.some(issue=>issue.slotId===slot))success(next.planId,`delete-slot:${slot}:${next.revision}`,'删除成功');
    },
    reset() {pending.clear();completed.clear();},
  };
}
