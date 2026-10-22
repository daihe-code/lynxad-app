import assert from 'node:assert/strict';
import test from 'node:test';
import { activeGenerationIssues, generationFailureRows, materialFailed, materialFailureReason, productHasFailure } from '../src/features/delivery/materialFailures.ts';

const state=()=>({materials:[],generationIssues:[{productId:'p2',type:'VIDEO',slotId:'video-2',message:'视频服务超时',title:'户外通勤视频'}]});
test('失败只标记对应商品和素材类型，未产出文件也有失败条目',()=>{
  const s=state();assert.equal(productHasFailure(s,'p1'),false);assert.equal(productHasFailure(s,'p2'),true);
  assert.equal(productHasFailure(s,'p2','VIDEO'),true);
  for(const type of ['IMAGE','CAROUSEL','TITLE'])assert.equal(productHasFailure(s,'p2',type),false);
  assert.equal(generationFailureRows(s,'p2','VIDEO')[0].message,'视频服务超时');
  assert.deepEqual(generationFailureRows(s,'p1','VIDEO'),[]);
});
test('同步失败归到已有素材行，保留真实原因且不重复生成失败占位',()=>{
  const m={id:'v2',productId:'p2',type:'VIDEO',status:'FAILED',error:'千川上传被拒绝',processing:{stage:'SYNC',generationStatus:'SUCCEEDED',storageStatus:'SUCCEEDED',reviewStatus:'SUCCEEDED',syncStatus:'FAILED'}};
  const s=state();s.generationIssues[0].materialId='v2';s.materials=[m];
  assert.equal(materialFailed(m),true);assert.equal(materialFailureReason(m,s),'千川上传被拒绝');
  assert.deepEqual(generationFailureRows(s,'p2','VIDEO'),[]);
  assert.equal(productHasFailure(s,'p2','VIDEO'),true);
});
test('成功回显后移除过期失败标记，历史原因不污染现有结果',()=>{
  const s=state();s.generationIssues[0].materialId='v2';
  s.materials=[{id:'v2',productId:'p2',type:'VIDEO',status:'READY',processing:{stage:'READY',syncStatus:'SUCCEEDED'}}];
  assert.equal(materialFailed(s.materials[0]),false);assert.deepEqual(activeGenerationIssues(s),[]);
  assert.equal(productHasFailure(s,'p2','VIDEO'),false);assert.deepEqual(generationFailureRows(s,'p2','VIDEO'),[]);
});
test('缺少类型的旧记录仅在对应商品下提示，不猜测四个tab均失败',()=>{
  const s=state();s.generationIssues=[{productId:'p2',message:'创意规划失败'}];
  assert.equal(productHasFailure(s,'p2'),true);
  for(const type of ['VIDEO','IMAGE','CAROUSEL','TITLE'])assert.equal(productHasFailure(s,'p2',type),false);
});
