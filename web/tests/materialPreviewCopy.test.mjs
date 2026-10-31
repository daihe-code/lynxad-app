import test from 'node:test';
import assert from 'node:assert/strict';
import { materialPreviewCopy } from '../src/features/delivery/materialPreviewCopy.ts';

const carousel = { type: 'CAROUSEL', title: '素材管理名称', titles: ['宽肩瓶型，收纳摆放更整齐'], text: '750ml玻璃瓶身，红色旋盖便于开合。\n透明瓶身方便查看余量。' };

test('图文预览展示实际标题和完整描述，而非只有图片里的字', () => {
  assert.equal(materialPreviewCopy(carousel), '宽肩瓶型，收纳摆放更整齐\n750ml玻璃瓶身，红色旋盖便于开合。\n透明瓶身方便查看余量。');
  assert.equal(materialPreviewCopy({ ...carousel, text: carousel.titles[0] }), carousel.titles[0]);
  assert.equal(materialPreviewCopy({ ...carousel, text: `${carousel.titles[0]}\n描述正文` }), `${carousel.titles[0]}\n描述正文`);
});

test('描述缺失保留真实标题，明确选中的标题不混入其他正文', () => {
  assert.equal(materialPreviewCopy({ ...carousel, text: '  ' }), carousel.titles[0]);
  assert.equal(materialPreviewCopy(carousel, undefined, '选中的投放标题'), '选中的投放标题');
  assert.equal(materialPreviewCopy({ ...carousel, titles: [], title: '', text: '只有图文描述' }), '只有图文描述');
});

test('视频和商品卡继续展示投放标题，不把视频脚本当成描述', () => {
  for (const type of ['VIDEO', 'IMAGE']) assert.equal(materialPreviewCopy({ ...carousel, type }), carousel.titles[0]);
  assert.equal(materialPreviewCopy(undefined, { name: '所选商品' }), '所选商品');
  assert.equal(materialPreviewCopy(), '');
});
