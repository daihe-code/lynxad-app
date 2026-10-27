import type { CreationMaterial, ProductTitlesPool } from './creationApi';
import { titlesInputError } from './inputValidation.ts';

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
