import assert from 'node:assert/strict';
import test from 'node:test';
import { productAdjustmentNotice, selectionImpact, selectionImpactText } from '../src/features/delivery/selectionImpact.ts';
import { shouldPromptGeneration, selectedGenerationProducts, hasGenerationDecision } from '../src/features/delivery/generationSelection.ts';
const a={productId:'a',qcProductId:'123',name:'原商品A'},b={productId:'b',qcProductId:'456',name:'原商品B'},c={productId:'c',qcProductId:'789',name:'新增商品C'};

test('统一调整提示说明实际保存后的影响，保留素材中心原文件和已有素材',()=>{
  const notice=productAdjustmentNotice();
  assert.match(notice.analysisGoal,/已有分析.*已设预算.*目标将清空/);
  assert.match(notice.materials,/保留商品的素材仍可用/);assert.match(notice.materials,/仅从本计划移除.*原文件保留在素材中心/);
  assert.match(notice.added,/生成或添加素材/);assert.match(notice.usage,/如已生成素材.*不退回.*新额度/);
  assert.match(notice.effective,/仅进入选择不会改变.*保存实际变更后才生效/);
});

test('已投放计划明确保留原目标并在保存成功后更新，避免误报立即清空',()=>{
  const notice=productAdjustmentNotice(true);
  assert.match(notice.analysisGoal,/预算、ROI 等目标保留/);assert.match(notice.analysisGoal,/重新核对/);
  assert.match(notice.effective,/保存修改.*同步成功后/);assert.doesNotMatch(notice.analysisGoal,/目标将清空/);
});
test('新增商品提示保留原素材，只默认选中新商品生成',()=>{
  const impact=selectionImpact([a,b],[a,b,c],[{productId:'123'},{productId:'456'}]);
  assert.equal(impact.retainedMaterials,2);assert.equal(impact.removedMaterials,0);assert.match(selectionImpactText(impact),/无需重复生成/);
  const state={products:[a,b,c],generationSelection:['123','456'],generationPendingProductIds:['789'],generationStatus:'NONE',generationProductIds:[],materials:[{}]};
  assert.equal(shouldPromptGeneration(state),true);assert.equal(hasGenerationDecision(state),false);assert.deepEqual(selectedGenerationProducts(state),['789']);
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
