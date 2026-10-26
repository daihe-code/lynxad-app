import assert from 'node:assert/strict';
import test from 'node:test';
import { selectionImpact, selectionImpactText } from '../src/features/delivery/selectionImpact.ts';
import { shouldPromptGeneration, selectedGenerationProducts, hasGenerationDecision } from '../src/features/delivery/generationSelection.ts';
const a={productId:'a',qcProductId:'123',name:'原商品A'},b={productId:'b',qcProductId:'456',name:'原商品B'},c={productId:'c',qcProductId:'789',name:'新增商品C'};
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
