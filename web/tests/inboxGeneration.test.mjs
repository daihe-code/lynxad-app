import assert from 'node:assert/strict';
import test from 'node:test';
import { generationNotice, generationNoticeRoute } from '../src/features/tenant/inboxGeneration.ts';
import { generationNoticeTarget } from '../src/features/delivery/generationNoticeTarget.ts';
const legacy = (count) => ({id:'notice',bizType:'CREATION_GENERATED',bizId:'plan',title:'部分素材生成未完成',body:`计划「旧计划」生成阶段已结束，已产出 ${count} 条素材。成功结果已保留，可重试失败项。素材登记、检查和千川同步状态请返回本计划查看。`});
test('历史0产出消息改为失败，部分结果只保留一份计划摘要和短说明',()=>{
  const zero=generationNotice(legacy(0));assert.equal(zero.title,'素材生成失败');assert.equal(zero.action,'查看失败原因');assert.equal(zero.planName,'旧计划');assert.doesNotMatch(zero.body,/部分|成功结果已保留/);
  const partial=generationNotice(legacy(4));assert.equal(partial.title,'部分素材生成完成');assert.equal(partial.action,'查看素材结果');assert.match(partial.body,/4 条素材/);assert.doesNotMatch(partial.body,/旧计划|返回本计划/);
});
test('消息进入素材步骤并携带唯一消息标识，修改中的素材使用确切工作草稿',()=>{
  assert.deepEqual(generationNoticeRoute(legacy(4)),{path:'/campaigns/plan',query:{step:'3',notice:'notice'}});
  const version={...legacy(1),bizType:'CREATION_EDIT_GENERATED',bizId:'original',result:{outcome:'NEW_VERSION',planName:'原计划',creationPlanId:'working',jobId:'job',materialId:'new',produced:1}};
  assert.equal(generationNotice(version).action,'查看新版本');assert.deepEqual(generationNoticeRoute(version),{path:'/campaigns/create',query:{draftId:'working',step:'3',notice:'notice'}});
});
test('任务入口定位新版本及失败商品，旧任务不冒充当前任务',()=>{
  const state={planId:'plan',engineJobId:'job',products:[],materials:[{id:'new',engineJobId:'job',type:'VIDEO',productId:'product'}],generationIssues:[{productId:'failed-product',type:'IMAGE',message:'服务返回失败'}]};
  const current={...legacy(1),result:{outcome:'NEW_VERSION',planName:'计划',creationPlanId:'plan',jobId:'job',operationId:'batch',materialId:'new',produced:1}};
  const target=generationNoticeTarget(state,current);assert.equal(target.materialId,'new');assert.equal(target.productId,'product');assert.equal(target.type,'VIDEO');assert.deepEqual(target.reasons,['服务返回失败']);
  const past=generationNoticeTarget(state,{...current,result:{...current.result,jobId:'old-job',materialId:'removed'}});assert.match(past.note,/较早的生成任务/);assert.deepEqual(past.reasons,[]);assert.equal(past.materialId,undefined);
  assert.equal(generationNoticeTarget(state,{...current,result:{...current.result,creationPlanId:'other'}}),null);
  assert.match(generationNoticeTarget(state,legacy(4)).note,/历史消息未记录具体任务/);
});
test('查看新版本可定位尚未加入投放计划的候选素材',()=>{
  const state={planId:'plan',materials:[{id:'old',productId:'product',type:'VIDEO'}],materialVersions:[{sourceId:'old',material:{id:'new',productId:'product',type:'VIDEO',status:'READY'}}]};
  const message={...legacy(1),result:{outcome:'NEW_VERSION',creationPlanId:'plan',materialId:'new',jobId:'past-job'}};
  assert.equal(generationNoticeTarget(state,message).materialId,'new');assert.equal(state.materials.length,1);
});
