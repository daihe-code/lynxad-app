import test from 'node:test';
import assert from 'node:assert/strict';
import { planMaterialRange, visiblePlanMaterials, planMaterialPreview, planMaterialProduct } from '../src/features/delivery/planMaterialPreview.ts';

const material = (overrides={}) => ({id:'9007199254740995123',type:'VIDEO',name:'历史投放视频',text:'投放文案',previewUrl:'https://media.example/video.mp4',posterUrl:'https://media.example/cover.jpg',imageUrls:[],products:[{id:'p1',name:'商品一'}],deleted:false,tags:[],reasons:[],metrics:{},...overrides});

test('历史已完成计划按实际投放日期查询，避免只查今天导致素材丢失',()=>{
  assert.deepEqual(planMaterialRange({startDate:'2026-10-17',endDate:'2026-10-20'},'2026-10-27'),['2026-10-17','2026-10-20']);
  assert.deepEqual(planMaterialRange({startDate:'2026-10-17',endDate:'2026-11-20'},'2026-10-27'),['2026-10-17','2026-10-27']);
  assert.deepEqual(planMaterialRange({createdAt:'2026-10-17T02:00:00Z'},'2026-10-27'),['2026-10-17','2026-10-27']);
});

test('缺失、无效或过早日期仍生成合法且不超过180天的查询区间',()=>{
  assert.deepEqual(planMaterialRange({startDate:'2025-01-01',endDate:'2026-10-20'},'2026-10-27'),['2026-04-24','2026-10-20']);
  for (const value of [{},{startDate:'2026-02-30',endDate:'bad'},{startDate:'2026-11-01'}]) {
    assert.deepEqual(planMaterialRange(value,'2026-10-27'),['2026-05-01','2026-10-27']);
  }
});

test('素材按类型和完整字符串ID去重，已删除素材不冒充正在使用的素材',()=>{
  const video=material(), image=material({type:'IMAGE'}),deleted=material({id:'deleted',deleted:true});
  assert.deepEqual(visiblePlanMaterials([video,video,image,deleted]),[video,image]);
});

test('商品筛选只采用平台返回的关联，未关联素材不能错误归给唯一商品',()=>{
  const one=material(),two=material({id:'two',products:[{id:'p2',name:'商品二'}]}),unknown=material({id:'unknown',products:[]});
  assert.deepEqual(visiblePlanMaterials([one,two,unknown],'p1'),[one]);
  assert.deepEqual(visiblePlanMaterials([one,two,unknown],'p3'),[]);
  assert.equal(visiblePlanMaterials([one,two,unknown]).length,3);
});

test('平台素材转换仅用于预览，保留视频与图文配音且不伪造智能生成记录',()=>{
  const video=planMaterialPreview(material());assert.equal(video.previewUrl,'https://media.example/video.mp4');assert.equal(video.engineMaterialId,undefined);assert.equal(video.engineJobId,undefined);
  const carousel=planMaterialPreview(material({type:'CAROUSEL',imageUrls:['https://media.example/1.jpg'],musicUrl:'https://media.example/music.mp3'}));
  assert.equal(carousel.previewUrl,'https://media.example/music.mp3');assert.deepEqual(carousel.imageUrls,['https://media.example/1.jpg']);
  assert.equal(planMaterialPreview(material({type:'TITLE'})),undefined);
  assert.equal(planMaterialPreview(undefined),undefined);
});

test('预览只使用对应商品价格，未知关联不回退到第一个商品',()=>{
  const products=[{qcProductId:'p1',name:'商品一',price:10},{qcProductId:'p2',name:'商品二',price:30}];
  assert.equal(planMaterialProduct(material(),products).price,10);
  assert.equal(planMaterialProduct(material({products:[{id:'p3',name:'历史商品'}]}),products).price,null);
  assert.equal(planMaterialProduct(material({products:[]}),products),undefined);
  assert.equal(planMaterialProduct(material({products:[{id:'p1',name:'一'},{id:'p2',name:'二'}]}),products,'p2').price,30);
});
