import test from 'node:test';
import assert from 'node:assert/strict';
import { materialSourceLabel, isGeneratedMaterial, materialSelectionAccepted } from '../src/features/delivery/materialSelection.ts';

test('素材库复用的生成视频沿用文件来源，无需伪造引擎任务绑定', () => {
  const m = { source: 'LYNX', engineMaterialId: undefined };
  assert.equal(isGeneratedMaterial(m), true);
  assert.equal(materialSourceLabel(m), '智能生成');
  assert.equal(materialSourceLabel({ source: 'UPLOAD' }), '人工上传');
  assert.equal(materialSourceLabel({ engineMaterialId: 'legacy' }), '智能生成');
  assert.equal(materialSourceLabel({ source: 'LYNX', deliverySource: 'product_reference' }), '商品原图版');
});

test('复用已有条目的成功响应无需新增行即可确认，等待中或错误响应不得确认成功', () => {
  assert.equal(materialSelectionAccepted(true, false, ''), true);
  assert.equal(materialSelectionAccepted(false, false, ''), false);
  assert.equal(materialSelectionAccepted(true, true, ''), false);
  assert.equal(materialSelectionAccepted(true, false, '提交失败'), false);
});
