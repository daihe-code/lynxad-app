import assert from 'node:assert/strict';
import test from 'node:test';
import { ref } from 'vue';
import { useCreationModeBudgets } from '../src/features/delivery/creationModeBudgets.ts';

test('默认预算跟随平台推荐刷新，用户编辑后即使数值相同也不再覆盖', () => {
  const mode=ref('CONSERVATIVE'), inputs=useCreationModeBudgets(mode);
  inputs.applyRecommendation('50');inputs.applyRecommendation('100');
  assert.equal(inputs.budget.value,'100');
  inputs.budget.value='100';inputs.applyRecommendation('50');
  assert.equal(inputs.budget.value,'100');
  inputs.budget.value='157.25';inputs.applyRecommendation('100');
  assert.equal(inputs.budget.value,'157.25');
});

test('控成本300与放量100分别保留，切换不复制或清空另一种方式的金额', () => {
  const mode=ref('CUSTOM'), inputs=useCreationModeBudgets(mode);
  inputs.budget.value='300';
  mode.value='CONSERVATIVE';
  assert.equal(inputs.budget.value,'');
  inputs.applyRecommendation('50');
  assert.equal(inputs.budget.value,'50');
  inputs.budget.value='100';
  for (let i=0;i<3;i++) {
    mode.value='CUSTOM';assert.equal(inputs.budget.value,'300');
    mode.value='CONSERVATIVE';assert.equal(inputs.budget.value,'100');
  }
});

test('已保存的放量预算只恢复到放量，控成本首次为空，填写后独立记忆', () => {
  const mode=ref('CONSERVATIVE'), inputs=useCreationModeBudgets(mode);
  inputs.restore('100');mode.value='CUSTOM';
  assert.equal(inputs.budget.value,'');
  inputs.applyRecommendation('50');assert.equal(inputs.budget.value,'');
  inputs.budget.value='300';mode.value='CONSERVATIVE';
  inputs.applyRecommendation('50');assert.equal(inputs.budget.value,'100');
  mode.value='CUSTOM';assert.equal(inputs.budget.value,'300');
});

test('推荐刷新不覆盖已选档位、手填金额和主动清空的输入', () => {
  const mode=ref('CONSERVATIVE'), inputs=useCreationModeBudgets(mode);
  for (const value of ['100','157.25','']) {
    inputs.budget.value=value;mode.value='CUSTOM';inputs.budget.value='350';mode.value='CONSERVATIVE';
    inputs.applyRecommendation('50');assert.equal(inputs.budget.value,value);
    mode.value='CUSTOM';assert.equal(inputs.budget.value,'350');mode.value='CONSERVATIVE';
  }
});

test('恢复双模式草稿以当前保存金额为准，并保留另一个模式的金额', () => {
  const mode=ref('CUSTOM'), inputs=useCreationModeBudgets(mode);
  inputs.restore('400',{CUSTOM:'300',CONSERVATIVE:'100'});
  assert.equal(inputs.budget.value,'400');mode.value='CONSERVATIVE';assert.equal(inputs.budget.value,'100');
  inputs.restore('',{CUSTOM:'400',CONSERVATIVE:''});inputs.applyRecommendation('50');assert.equal(inputs.budget.value,'');
});

test('切换计划、账户或商品组合后重置预算记忆，不带入上个上下文的金额', () => {
  const mode=ref('CUSTOM'), inputs=useCreationModeBudgets(mode);
  inputs.restore('300',{CONSERVATIVE:'100'});inputs.reset();
  assert.equal(inputs.budget.value,'');mode.value='CONSERVATIVE';inputs.applyRecommendation('50');assert.equal(inputs.budget.value,'50');
  inputs.restore('80');mode.value='CUSTOM';assert.equal(inputs.budget.value,'');
});
