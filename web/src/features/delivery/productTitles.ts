import type { CreationMaterial, ProductTitlesPool } from './creationApi';
import { titlesInputError, deliveryInputRules } from './inputValidation.ts';

export type TitleKind='CUSTOM'|'CARD';
export const titleLimit=(kind:TitleKind)=>kind==='CARD'?30:55;
export function productTitleValues(materials:CreationMaterial[],pool:ProductTitlesPool|undefined,kind:TitleKind) {
  if(pool)return kind==='CARD'?pool.cardTitles:pool.customTitles;
  return [...new Set(materials.filter(m=>kind==='CARD'?m.type==='IMAGE':m.type!=='IMAGE').flatMap(m=>m.titles))];
}
export function isGeneratedTitle(materials:CreationMaterial[],pool:ProductTitlesPool|undefined,kind:TitleKind,text:string) {
  const provenance=kind==='CARD'?pool?.cardGeneratedTitles:pool?.customGeneratedTitles;
  return provenance ? provenance.includes(text) : materials.some(m=>!!m.engineMaterialId&&(kind==='CARD'?m.type==='IMAGE':m.type!=='IMAGE')&&m.titles.includes(text));
}
export function productTitleError(kind:TitleKind,text:string) {
  return kind==='CARD'&&text.trim().length>30?'商品卡标题最多30字':titlesInputError([text]);
}
export function titleAdditionError(materials:CreationMaterial[],pool:ProductTitlesPool|undefined,kind:TitleKind,text?:string) {
  const current=productTitleValues(materials,pool,kind),other=productTitleValues(materials,pool,kind==='CARD'?'CUSTOM':'CARD');
  if(text!==undefined) {
    const error=productTitleError(kind,text);if(error)return error;
    if(current.includes(text.trim()))return '当前类型已存在相同标题';
  }
  const all=new Set([...current,...other]);
  if(current.length>=deliveryInputRules.customTitleMaximum || (text===undefined?all.size>=30:new Set([...all,text.trim()]).size>30))return '已达到当前商品的标题数量上限';
  return '';
}
