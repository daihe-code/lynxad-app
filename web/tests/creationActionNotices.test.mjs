import test from 'node:test';
import assert from 'node:assert/strict';
import { createCreationActionNotices } from '../src/features/delivery/creationActionNotices.ts';
const state=(extra={})=>({planId:'plan',revision:1,materials:[],generationStatus:'SUCCEEDED',...extra});
const material=(id,status='READY',extra={})=>({id,type:'VIDEO',productId:'product',status,titles:[],...extra});
function setup(){const shown=[];return {shown,notices:createCreationActionNotices(options=>{assert.equal(options.duration,3000);shown.push(options.message);})};}

test('each newly added video, product card and carousel waits for readiness for library and upload',()=>{
  for(const type of ['VIDEO','IMAGE','CAROUSEL'])for(const addedVia of ['LIBRARY','UPLOAD']){
    const {shown,notices}=setup(),before=state(),waiting=state({materials:[material('new','PROCESSING',{type,addedVia})]});
    notices.observe(before);notices.added(before,waiting);assert.deepEqual(shown,[]);
    const done=state({materials:[material('new','READY',{type,addedVia})]});notices.observe(done);notices.observe(done);notices.added(before,done);
    assert.deepEqual(shown,['新增完成']);
  }
});
test('synchronous additions notify once; old completed materials and duplicate library selections stay quiet',()=>{
  const {shown,notices}=setup(),before=state({materials:[material('existing')]});
  notices.observe(before);notices.added(before,before);assert.deepEqual(shown,[]);
  notices.added(before,state({materials:[...before.materials,material('new')]}));assert.deepEqual(shown,['新增完成']);
});
test('AI additions require the matching job success AND the resulting material to be ready',()=>{
  const {shown,notices}=setup(),job={id:'add',status:'QUEUED',materialId:'new'};
  notices.addition(state({generationAdditions:[job],materials:[material('original')]}),'add');
  notices.observe(state({generationAdditions:[{...job,status:'SUCCEEDED'}],materials:[material('new','PROCESSING')]}));assert.deepEqual(shown,[]);
  const done=state({generationAdditions:[{...job,status:'SUCCEEDED'}],materials:[material('new')]});notices.observe(done);notices.observe(done);
  assert.deepEqual(shown,['新增完成']);
});
test('failed or excluded additions never report success from an existing usable material',()=>{
  const {shown,notices}=setup();notices.addition(state({generationAdditions:[{id:'bad',status:'RUNNING'}],materials:[material('old')]}),'bad');
  notices.observe(state({generationAdditions:[{id:'bad',status:'FAILED'}],materials:[material('old')]}));assert.deepEqual(shown,[]);
  notices.added(state(),state({materials:[material('new','PROCESSING')]}));notices.observe(state({materials:[]}));assert.deepEqual(shown,[]);
});
test('regeneration waits for the new version, not the original, and does not wait for a version decision',()=>{
  const {shown,notices}=setup(),receipt={requestId:'revision',sourceId:'old',materialId:'new',status:'ACCEPTED'};
  notices.regenerated(state({materialRevision:receipt,materials:[material('old')]}));assert.deepEqual(shown,[]);
  notices.observe(state({materialRevision:receipt,materials:[material('old')],materialVersions:[{sourceId:'old',material:material('new','PROCESSING')}]}));assert.deepEqual(shown,[]);
  const done=state({materialRevision:receipt,materials:[material('old')],materialVersions:[{sourceId:'old',material:material('new'),decision:'PENDING'}]});notices.observe(done);notices.regenerated(done);
  assert.deepEqual(shown,['素材生成成功']);
});
test('failed regeneration and receipts pointing to the original never report success',()=>{
  for(const receipt of [{requestId:'bad',sourceId:'old',materialId:'old',status:'ACCEPTED'},{requestId:'bad',sourceId:'old',materialId:'new',status:'FAILED'}]){
    const {shown,notices}=setup();notices.regenerated(state({materialRevision:receipt,materials:[material('old'),material('new')]}));assert.deepEqual(shown,[]);
  }
});
test('manual and AI titles and product card titles use the same completion text',()=>{
  for(const kind of ['CUSTOM','CARD']){
    const {shown,notices}=setup(),pool={customTitles:[],cardTitles:[]},field=kind==='CARD'?'cardTitles':'customTitles';
    const before=state({titlePools:{product:pool}}),next=state({revision:2,titlePools:{product:{...pool,[field]:['新增商品标题']}}});
    notices.titleAdded(before,next,'product',kind,false);notices.titleAdded(before,next,'product',kind,false);assert.deepEqual(shown,['新增完成']);
    const receipt={requestId:'ai-'+kind,status:'PROCESSING',addition:true};notices.titleAdded(next,state({titleGeneration:receipt}),'product',kind,true);assert.equal(shown.length,1);
    notices.observe(state({titleGeneration:{...receipt,status:'SUCCEEDED'}}));assert.deepEqual(shown,['新增完成','新增完成']);
  }
});
test('title regeneration reports only a matching successful receipt; title deletions wait for saved pool changes',()=>{
  const {shown,notices}=setup(),before=state({titlePools:{product:{customTitles:['标题甲','标题乙'],cardTitles:[]}}});
  notices.titlesEdited(before,before,'product','CUSTOM');assert.deepEqual(shown,[]);
  notices.titlesEdited(before,state({revision:2,titlePools:{product:{customTitles:['标题甲'],cardTitles:[]}}}),'product','CUSTOM');
  notices.titleGenerated(state({titleGeneration:{requestId:'regen-title',status:'SENDING',addition:false}}));
  notices.observe(state({titleGeneration:{requestId:'regen-title',status:'SUCCEEDED',addition:false}}));assert.deepEqual(shown,['删除成功','素材生成成功']);
});
test('published plan deletions wait until the material actually disappears; failures are quiet',()=>{
  const {shown,notices}=setup();notices.deleted(state({materials:[material('old')],materialEdit:{status:'SENDING'}}),'old');assert.deepEqual(shown,[]);
  notices.observe(state({materials:[material('old')],materialEdit:{status:'UNCERTAIN'}}));assert.deepEqual(shown,[]);
  notices.observe(state({materialEdit:{status:'SUCCEEDED'}}));notices.observe(state({materialEdit:{status:'SUCCEEDED'}}));assert.deepEqual(shown,['删除成功']);
  notices.deleted(state({revision:3,materials:[material('kept')],materialEdit:{status:'FAILED'}}),'kept');notices.observe(state({revision:3,materials:[material('kept')]}));assert.equal(shown.length,1);
});
test('unfinished jobs resume across editor handoff without replaying historical successes or crossing plans',()=>{
  const {shown,notices}=setup(),job={id:'resume',materialId:'new',status:'IMPORTING'};
  notices.observe(state({generationAdditions:[{...job,status:'SUCCEEDED'}],materials:[material('new')]}));assert.deepEqual(shown,[]);
  notices.observe(state({generationAdditions:[job],materials:[material('new','PROCESSING')]}));
  notices.observe(state({planId:'other',generationAdditions:[{...job,status:'SUCCEEDED'}],materials:[material('new')]}));assert.deepEqual(shown,[]);
  const done=state({generationAdditions:[{...job,status:'SUCCEEDED'}],materials:[material('new')]});notices.observe(done);notices.observe(done);assert.deepEqual(shown,['新增完成']);
});
