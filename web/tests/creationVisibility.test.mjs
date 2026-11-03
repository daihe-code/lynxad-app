import test from 'node:test';
import assert from 'node:assert/strict';
import { ref } from 'vue';
import { useCreationVisibility } from '../src/features/delivery/useCreationVisibility.ts';

function setup({ fail=false, recovered, blocked=false }={}) {
  const state=ref({planId:'a',revision:1,packageVersion:1,hideInAweme:true}),saving=ref(false),owner=ref('owner');
  const requests=[],releases=[],rejections=[],accepted=[];
  const choice=useCreationVisibility({current:()=>state.value,scope:()=>owner.value+state.value.planId,blocked:()=>blocked,saving,
    save:(snapshot,value)=>{requests.push([snapshot.planId,snapshot.revision,value]);return new Promise((resolve,reject)=>{releases.push(()=>resolve({...snapshot,hideInAweme:value,revision:snapshot.revision+1}));rejections.push(()=>reject(new Error('未保存')));});},
    accept:patch=>{accepted.push(patch);state.value={...state.value,...patch};},
    recover:async()=>{if(fail)throw new Error('offline');return recovered||state.value;},acceptRecovery:next=>{state.value=next;},message:e=>e.message});
  return {choice,state,saving,owner,requests,releases,rejections,accepted};
}
const tick=()=>new Promise(resolve=>setImmediate(resolve));

test('selection is immediate; pending clicks coalesce and writes use acknowledged revisions',async()=>{
  const t=setup();t.choice.select(false);assert.equal(t.choice.value.value,false);assert.equal(t.saving.value,true);
  t.choice.select(true);t.choice.select(false);t.choice.select(true);assert.equal(t.requests.length,1);
  const saved=t.choice.flush();t.releases[0]();await tick();assert.equal(t.choice.value.value,true);
  assert.deepEqual(t.requests,[['a',1,false],['a',2,true]]);assert.equal(t.saving.value,true);
  t.releases[1]();assert.equal(await saved,true);assert.equal(t.saving.value,false);assert.equal(t.state.value.hideInAweme,true);t.choice.stop();
});
test('selecting the saved option makes no request; intermediate pending choices do not create extra writes',async()=>{
  const t=setup();t.choice.select(true);assert.equal(t.requests.length,0);
  t.choice.select(false);t.choice.select(true);t.choice.select(false);const saved=t.choice.flush();t.releases[0]();await saved;
  assert.equal(t.requests.length,1);assert.equal(t.choice.value.value,false);t.choice.stop();
});
test('failed save restores the actual value, preserves the retry target and adopts the recovered revision',async()=>{
  const t=setup({recovered:{planId:'a',revision:7,packageVersion:2,hideInAweme:true}});
  t.choice.select(false);const saved=t.choice.flush();t.rejections[0]();assert.equal(await saved,false);
  assert.equal(t.choice.value.value,true);assert.equal(t.choice.error.value,'未保存');assert.equal(t.saving.value,false);
  t.choice.retry();assert.deepEqual(t.requests.at(-1),['a',7,false]);const retried=t.choice.flush();t.releases[1]();assert.equal(await retried,true);assert.equal(t.choice.error.value,'');t.choice.stop();
});
test('readback of a write whose response was lost confirms success without replaying it',async()=>{
  const t=setup({recovered:{planId:'a',revision:2,packageVersion:2,hideInAweme:false}});
  t.choice.select(false);const saved=t.choice.flush();t.rejections[0]();assert.equal(await saved,true);
  assert.equal(t.choice.value.value,false);assert.equal(t.choice.error.value,'');assert.equal(t.requests.length,1);t.choice.stop();
});
test('a response from an abandoned plan or session cannot overwrite the current draft or send a queued change',async()=>{
  const t=setup();t.choice.select(false);t.choice.select(true);const saved=t.choice.flush();
  t.state.value={planId:'b',revision:9,packageVersion:3,hideInAweme:false};t.releases[0]();assert.equal(await saved,false);
  assert.equal(t.state.value.planId,'b');assert.equal(t.choice.value.value,false);assert.equal(t.accepted.length,0);assert.equal(t.requests.length,1);t.choice.stop();
});
test('blocked or disposed controls cannot write',async()=>{
  const blocked=setup({blocked:true});blocked.choice.select(false);assert.equal(blocked.requests.length,0);blocked.choice.stop();
  const t=setup();t.choice.select(false);const saved=t.choice.flush();t.rejections[0]();await saved;t.choice.stop();t.choice.retry();t.choice.select(false);assert.equal(t.requests.length,1);
});
