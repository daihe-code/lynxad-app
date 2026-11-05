import assert from 'node:assert/strict';
import test from 'node:test';
import { computed, ref } from 'vue';
import { creationReachedStep, validReachedStep } from '../src/features/delivery/creationProgress.ts';
import { useCreationModeBudgets } from '../src/features/delivery/creationModeBudgets.ts';
import { creationBudgetError } from '../src/features/delivery/budgetValidation.ts';

const draft = () => ({step:1,reachedStep:3,budget:300,roi2Goal:4.77,smartBidType:'CUSTOM',scheduleType:'FROM_NOW',generationStatus:'NONE',materials:[],publishStatus:'NONE'});

test('返回前面查看不会降低草稿或已投放计划的点亮进度', () => {
  assert.equal(creationReachedStep(draft()),3);
  assert.equal(creationReachedStep({...draft(),qcPlanId:'123'}),4);
  assert.equal(creationReachedStep({...draft(),publishStatus:'CREATED'}),4);
  assert.equal(creationReachedStep({...draft(),reachedStep:2}),2);
});

test('只有商品分析上下文改变才重置已到达步骤', () => {
  assert.equal(validReachedStep(4),4);
  assert.equal(validReachedStep(4,true),1);
  assert.equal(validReachedStep(3),3);
  assert.equal(validReachedStep(1),1);
});

test('素材步骤返回目标设置后，多次切换投放方式保留进度及各自预算', () => {
  const state=ref({...draft(),step:2,budget:5000}), mode=ref('CUSTOM');
  const inputs=useCreationModeBudgets(mode);inputs.restore('5000');
  const reached=computed(()=>validReachedStep(creationReachedStep(state.value)));
  for(let i=0;i<3;i++) {
    mode.value='CONSERVATIVE';inputs.applyRecommendation('50');
    assert.equal(reached.value,3);assert.equal(inputs.budget.value,'50');
    mode.value='CUSTOM';
    assert.equal(reached.value,3);assert.equal(inputs.budget.value,'5000');
  }
  inputs.budget.value='';
  assert.equal(reached.value,3);
  assert.notEqual(creationBudgetError(inputs.budget.value,mode.value),'');
});

test('历史草稿以已有工作兼容恢复，新草稿不会提前点亮未来步骤', () => {
  assert.equal(creationReachedStep({...draft(),reachedStep:undefined}),3);
  assert.equal(creationReachedStep({...draft(),reachedStep:undefined,budget:undefined,step:2}),2);
  assert.equal(creationReachedStep({...draft(),reachedStep:undefined,budget:undefined,step:1}),1);
});
