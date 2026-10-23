import test from 'node:test';
import assert from 'node:assert/strict';
import { awemeLabels } from '../src/features/identity/awemeLabels.ts';

test('抖音号保留多项平台关系和商城能力，标签去重且不使用默认授权身份覆盖',()=>{
  assert.deepEqual(awemeLabels({type:'AUTHORIZED',authTypes:['OFFICIAL','ALLIANCE','OFFICIAL'],isAllowMall:true}),['官方','联盟合作','可投商城']);
  assert.deepEqual(awemeLabels({type:'AUTHORIZED',authTypes:['AWEME_COOPERATOR'],isAllowMall:false}),['合作达人']);
});
test('缺少、未知或明确否定的字段不会产生官方、联盟或商城标签',()=>{
  assert.deepEqual(awemeLabels({type:'AUTHORIZED'}),['已授权抖音号']);
  assert.deepEqual(awemeLabels({authTypes:['NEW_PLATFORM_TYPE'],isAllowMall:null}),['已授权抖音号']);
  assert.deepEqual(awemeLabels({type:'OFFICIAL',isAllowMall:false}),['官方']);
  assert.deepEqual(awemeLabels(undefined),[]);
});
