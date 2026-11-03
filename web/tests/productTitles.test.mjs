import test from 'node:test';
import assert from 'node:assert/strict';
import { productTitleValues,isGeneratedTitle,productTitleError,titleLimit,titleAdditionError } from '../src/features/delivery/productTitles.ts';
const materials=[{type:'VIDEO',engineMaterialId:'engine',titles:['通勤出行，看看这款双肩包']},{type:'VIDEO',titles:['手写的商品投放标题']},{type:'IMAGE',engineMaterialId:'card',titles:['多功能户外双肩包']}];

test('标题与商品卡标题分别读取，删除后的空集合不会从素材中复活',()=>{
  assert.deepEqual(productTitleValues(materials,undefined,'CARD'),['多功能户外双肩包']);
  assert.deepEqual(productTitleValues(materials,{customTitles:[],cardTitles:[]},'CUSTOM'),[]);
});
test('生成标签只来自生成记录，手动修改后可明确取消标签',()=>{
  assert.equal(isGeneratedTitle(materials,undefined,'CUSTOM',materials[0].titles[0]),true);
  assert.equal(isGeneratedTitle(materials,undefined,'CUSTOM',materials[1].titles[0]),false);
  const pool={customTitles:[materials[0].titles[0]],cardTitles:[],customGeneratedTitles:[]};
  assert.equal(isGeneratedTitle(materials,pool,'CUSTOM',materials[0].titles[0]),false);
});
test('普通标题55字、商品卡30字，并保留平台最小字符要求',()=>{
  assert.equal(titleLimit('CUSTOM'),55);assert.equal(titleLimit('CARD'),30);
  assert.equal(productTitleError('CUSTOM','中'.repeat(55)),'');assert.ok(productTitleError('CUSTOM','中'.repeat(56)));
  assert.equal(productTitleError('CARD','中'.repeat(30)),'');assert.ok(productTitleError('CARD','中'.repeat(31)));
  assert.ok(productTitleError('CARD','中'));
});
test('新增检查限制长度、同类型重复和商品总量，并允许从空集合开始',()=>{
  assert.equal(titleAdditionError([],undefined,'CARD','全新的商品卡标题'),'');
  assert.equal(titleAdditionError(materials,undefined,'CUSTOM',materials[0].titles[0]),'当前类型已存在相同标题');
  assert.ok(titleAdditionError([],undefined,'CARD','中'.repeat(31)));
  const pool={customTitles:Array.from({length:29},(_,i)=>'商品标题内容'+i),cardTitles:[]};
  assert.match(titleAdditionError([],pool,'CUSTOM'),/上限/);
  pool.cardTitles=['商品卡内容标题'];assert.match(titleAdditionError([],pool,'CARD','另一条商品卡标题'),/上限/);
  pool.customTitles.pop();assert.equal(titleAdditionError([],pool,'CARD','另一条商品卡标题'),'');
});
