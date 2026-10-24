import test from 'node:test';
import assert from 'node:assert/strict';
import { uploadedReady, carouselReady, uploadPhase, materialMeta } from '../src/features/creative/materialSources.ts';
const asset = () => ({ id:'a',kind:'VIDEO',storageStatus:'STORED',aiReview:{status:'APPROVED'},syncs:[{accountId:'current',status:'UPLOADED',reviewStatus:'PASS'}] });
test('local selection waits for the current account review and sync, including uncertain outcomes',()=>{
  const value=asset();assert.equal(uploadedReady(value,'current'),true);assert.equal(uploadedReady(value,'other'),false);
  for(const status of ['QUEUED','RUNNING','FAILED','REJECTED','MANUAL_REVIEW']){value.aiReview.status=status;assert.equal(uploadedReady(value,'current'),false);}
  value.aiReview.status='APPROVED';for(const status of ['UNCERTAIN','WAITING_AUTH','RUNNING','FAILED']){value.syncs[0].status=status;assert.equal(uploadedReady(value,'current'),false);}
  value.syncs[0].status='UPLOADED';value.syncs[0].reviewStatus='REJECTED';assert.equal(uploadedReady(value,'current'),false);
});
test('upload phases distinguish transfer, review, sync and readiness',()=>{
  const value=asset();assert.equal(uploadPhase(undefined,'current'),'待上传');assert.equal(uploadPhase(undefined,'current',true),'上传中');
  value.aiReview.status='RUNNING';assert.equal(uploadPhase(value,'current'),'检查中');value.aiReview.status='FAILED';assert.equal(uploadPhase(value,'current'),'检查未通过');
  value.aiReview.status='APPROVED';value.syncs[0].status='QUEUED';assert.equal(uploadPhase(value,'current'),'同步中');value.syncs[0].status='UNCERTAIN';assert.equal(uploadPhase(value,'current'),'同步未完成');value.syncs[0].status='UPLOADED';assert.equal(uploadPhase(value,'current'),'已就绪');
});
test('carousel needs every component and the requested account to be ready',()=>{
  const value={accountIds:['current'],aiReview:{status:'APPROVED'},parts:[{role:'TEXT',asset:{...asset(),syncs:[]}},{role:'IMAGE',asset:asset()},{role:'AUDIO',asset:asset()}]};
  assert.equal(carouselReady(value,'current'),true);assert.equal(carouselReady(value,'other'),false);
  value.parts[2].asset.syncs=[];assert.equal(carouselReady(value,'current'),false);value.parts[2].asset=asset();value.parts[0].asset.aiReview.status='FAILED';assert.equal(carouselReady(value,'current'),false);
});
test('video cards display factual duration and orientation without inventing missing values',()=>{
  assert.equal(materialMeta({type:'VIDEO',durationSec:18.25,width:1080,height:1920}),'18.3 秒 · 竖屏');
  assert.equal(materialMeta({type:'VIDEO',durationSec:20,imageMode:'VIDEO_HORIZONTAL'}),'20 秒 · 横屏');
  assert.equal(materialMeta({type:'VIDEO'}),'时长待获取 · 画幅待获取');
});
