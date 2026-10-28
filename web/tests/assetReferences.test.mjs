import test from 'node:test';
import assert from 'node:assert/strict';
import { referenceLabel } from '../src/features/creative/assetReferences.ts';

test('引用数量保留暂停和已完成计划；同步中不会冒充未引用', () => {
  assert.equal(referenceLabel({used:true,usageKnown:true,references:[{planId:'a',status:'PAUSED'},{planId:'b',status:'FINISHED'}]}),'2 个计划引用');
  assert.equal(referenceLabel({used:false,usageKnown:false,references:[]}),'引用待同步');
  assert.equal(referenceLabel({used:false,usageKnown:true,references:[]}),'未被引用');
  assert.equal(referenceLabel({used:true,usageKnown:false,references:[{planId:'a'}]}),'1 个计划引用');
});
