import test from 'node:test';
import assert from 'node:assert/strict';
import { recommendedRoiDefault } from '../src/features/delivery/creationRoiDefault.ts';

test('默认ROI直接采用平台返回值，平台推荐更新时替换旧自动值',()=>{
  assert.deepEqual(recommendedRoiDefault('',undefined,4.77,false),{value:'4.77',defaultValue:'4.77'});
  assert.deepEqual(recommendedRoiDefault('4.82','4.82',4.77,false),{value:'4.77',defaultValue:'4.77'});
  assert.deepEqual(recommendedRoiDefault('4.820','4.82',4.77,false),{value:'4.77',defaultValue:'4.77'});
});
test('手动输入、清空以及已保存的目标不被平台刷新覆盖',()=>{
  assert.deepEqual(recommendedRoiDefault('4.6','4.82',4.77,true),{value:'4.6'});
  assert.deepEqual(recommendedRoiDefault('4.82','4.82',4.77,true),{value:'4.82'});
  assert.deepEqual(recommendedRoiDefault('',undefined,4.77,true),{value:''});
  assert.deepEqual(recommendedRoiDefault('4.82',undefined,4.77,false),{value:'4.82'});
});
test('平台没有有效推荐时不填写固定ROI，也不保留失效的自动推荐',()=>{
  for(const value of [undefined,0,-1,NaN,Infinity,4.777]){
    assert.deepEqual(recommendedRoiDefault('',undefined,value,false),{value:''});
    assert.deepEqual(recommendedRoiDefault('4.82','4.82',value,false),{value:''});
  }
});
test('自动值清空后平台恢复推荐，可以继续填入当前返回值',()=>{
  const missing=recommendedRoiDefault('4.82','4.82',undefined,false);
  assert.deepEqual(recommendedRoiDefault(missing.value,missing.defaultValue,4.77,false),{value:'4.77',defaultValue:'4.77'});
});
