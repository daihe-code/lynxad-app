import assert from 'node:assert/strict';
import test from 'node:test';
import { regenerationFeedbackError, regenerationWait } from '../src/features/delivery/regenerationPolicy.ts';
test('重新生成必须有明确原因，空白原因和超长说明均不能提交',()=>{
  assert.match(regenerationFeedbackError([],''),/请选择/);
  assert.match(regenerationFeedbackError(['   '],''),/请选择/);
  assert.equal(regenerationFeedbackError(['商品展示不清晰'],'  保留商品款式  '),'');
  assert.match(regenerationFeedbackError(['商品展示不清晰'],'字'.repeat(51)),/最多50/);
  assert.match(regenerationFeedbackError(['字'.repeat(100),'句'.repeat(100)],'说明'),/最多200/);
});
test('冷却时间只约束重新生成，历史无时间戳立即可用且不会出现负数',()=>{
  assert.equal(regenerationWait(undefined,1000),0);assert.equal(regenerationWait(0,1000),0);
  assert.equal(regenerationWait(31000,1000),30);assert.equal(regenerationWait(31000,30999),1);assert.equal(regenerationWait(31000,31000),0);assert.equal(regenerationWait(31000,32000),0);
});
