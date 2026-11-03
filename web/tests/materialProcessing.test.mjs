import assert from 'node:assert/strict';
import test from 'node:test';
import { materialProcessingLabel, materialIsReady, materialHasPreview, generationResultLabel, processingSummary, componentLabel } from '../src/features/delivery/materialProcessing.ts';
const material=(syncStatus,stage='SYNC')=>({status:'FAILED',engineMaterialId:'engine-result',processing:{generationStatus:'SUCCEEDED',storageStatus:'SUCCEEDED',reviewStatus:'SUCCEEDED',syncStatus,stage,components:[],canRetrySync:syncStatus==='FAILED'}});
test('生成成功与同步失败、待确认分开显示，不依赖可过期的预览链接',()=>{
  assert.equal(materialProcessingLabel(material('FAILED')),'素材处理中');
  assert.equal(materialProcessingLabel(material('UNCERTAIN')),'素材处理中');
  assert.equal(materialProcessingLabel(material('WAITING_AUTH')),'素材处理中');
  assert.equal(generationResultLabel({generationResultStatus:'SUCCEEDED',generationStatus:'FAILED'}),'生成完成');
});
test('质检、登记、投放校验分开归因，不能全部显示成上传失败',()=>{
  const m=material('PENDING','REVIEW');m.processing.reviewStatus='REJECTED';assert.equal(materialProcessingLabel(m),'已生成 · 质检未通过');
  m.processing.stage='REGISTRATION';m.processing.storageStatus='FAILED';assert.equal(materialProcessingLabel(m),'已生成 · 登记失败');
  m.processing.stage='VALIDATION';assert.equal(materialProcessingLabel(m),'素材处理中');
});
test('人工素材不标成AI生成；旧接口的失败不猜测原因',()=>{
  const m=material('FAILED');m.processing.generationStatus='NOT_REQUIRED';assert.equal(materialProcessingLabel(m),'素材处理中');
  assert.equal(materialProcessingLabel({status:'FAILED',previewUrl:'https://preview.test/image.png'}),'素材处理中');
});
test('文件有预览也不能绕过质检与同步状态的发布门槛',()=>{
  const m=material('UNCERTAIN');m.status='READY';m.previewUrl='https://preview.test/file';assert.equal(materialIsReady(m),false);
  m.processing.stage='READY';assert.equal(materialIsReady(m),true);
  assert.equal(materialIsReady({status:'READY'}),true);
});
test('汇总按阶段计数，原始图片、视频封面和图文组件均有明确名称',()=>{
  const ready=material('SUCCEEDED','READY');ready.status='READY';
  assert.equal(processingSummary([ready,material('FAILED'),material('PENDING','REVIEW')]),'可投放 1/3 条 · 质检待处理 1 条 · 千川同步待处理 1 条');
  assert.equal(componentLabel('COVER'),'视频封面');assert.equal(componentLabel('IMAGE_1'),'图片 2');
  assert.equal(generationResultLabel({generationResultStatus:'PARTIAL_FAILED',generationStatus:'FAILED'}),'部分生成未完成');
});
test('预览失效只影响预览入口，不把已就绪素材改为生成失败',()=>{
  const m=material('SUCCEEDED','READY');m.status='READY';m.type='VIDEO';
  assert.equal(materialHasPreview(m),false);assert.equal(materialIsReady(m),true);assert.equal(materialProcessingLabel(m),'已就绪');
  m.type='CAROUSEL';m.previewUrl='https://preview.test/audio.mp3';assert.equal(materialHasPreview(m),false);
  m.imageUrls=['https://preview.test/image.jpg'];assert.equal(materialHasPreview(m),true);
});
