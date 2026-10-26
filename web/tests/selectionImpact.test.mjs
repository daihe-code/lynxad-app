import assert from 'node:assert/strict';
import test from 'node:test';
import { productAdjustmentText, selectionImpact, selectionImpactText } from '../src/features/delivery/selectionImpact.ts';
import { shouldPromptGeneration, selectedGenerationProducts, hasGenerationDecision } from '../src/features/delivery/generationSelection.ts';
const a={productId:'a',qcProductId:'123',name:'原商品A'},b={productId:'b',qcProductId:'456',name:'原商品B'},c={productId:'c',qcProductId:'789',name:'新增商品C'};

test('调整前按当前成果提示影响，空计划不误报已有素材和目标',()=>{
  const empty=productAdjustmentText({materialCount:0,hasAnalysis:false,hasGoal:false});
  assert.match(empty,/暂无可用素材/);assert.doesNotMatch(empty,/原智能分析结果将失效|投放目标将清空|生成用量不会退回/);
  const analyzed=productAdjustmentText({materialCount:0,hasAnalysis:true,hasGoal:false});
  assert.match(analyzed,/重新分析/);assert.doesNotMatch(analyzed,/投放目标将清空/);
  const ready=productAdjustmentText({materialCount:6,hasAnalysis:true,hasGoal:true});
  assert.match(ready,/6 条可用素材/);assert.match(ready,/不会保留在本计划预览/);assert.match(ready,/继续保留/);
  assert.match(ready,/也可重新生成/);assert.match(ready,/生成用量不会退回/);assert.match(ready,/投放目标将清空/);
  assert.match(ready,/尚未增删或替换商品时.*不会改变/);
});

test('已投放计划明确保留原目标并在保存成功后更新，避免误报立即清空',()=>{
  const text=productAdjustmentText({materialCount:3,hasAnalysis:true,hasGoal:true,published:true});
  assert.match(text,/预算和 ROI 目标会保留/);assert.match(text,/重新核对/);
  assert.match(text,/保存修改.*同步成功后/);assert.doesNotMatch(text,/投放目标将清空/);
});
test('新增商品提示保留原素材，只默认选中新商品生成',()=>{
  const impact=selectionImpact([a,b],[a,b,c],[{productId:'123'},{productId:'456'}]);
  assert.equal(impact.retainedMaterials,2);assert.equal(impact.removedMaterials,0);assert.match(selectionImpactText(impact),/无需重复生成/);
  const state={products:[a,b,c],generationSelection:['123','456'],generationPendingProductIds:['789'],generationStatus:'NONE',generationProductIds:[],materials:[{}]};
  assert.equal(shouldPromptGeneration(state,true),true);assert.equal(hasGenerationDecision(state),false);assert.deepEqual(selectedGenerationProducts(state),['789']);
});
test('删除和替换明确提示移出预览数量与生成用量，取消不会触及原数组',()=>{
  const materials=[{productId:'123'},{productId:'123'},{productId:'456'}];
  for(const after of [[b],[b,c]]) {
    const impact=selectionImpact([a,b],after,materials),text=selectionImpactText(impact,true);
    assert.equal(impact.removedMaterials,2);assert.equal(impact.retainedMaterials,1);
    assert.match(text,/原商品A/);assert.match(text,/2 条素材将从本计划预览/);assert.match(text,/生成用量提醒/);assert.match(text,/保存修改/);
  }
  assert.equal(materials.length,3);assert.equal(selectionImpact([a,b],[b,a],materials).changed,false);
});
