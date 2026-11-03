import { creationGenerateTitle, creationProductTitles, type CreationState, type TitleGeneration } from './creationApi';
import { productTitleValues, titleAdditionError, type TitleKind } from './productTitles';

export type TitleSource = 'AI' | 'MANUAL';
export interface TitleAddition { productId: string; kind: TitleKind; source: TitleSource; text: string; requestId?: string }
export interface TitleAdditionReceipt { requestId?: string; done: (error?: string, generation?: TitleGeneration) => void }
export async function addProductTitle(state: CreationState, body: TitleAddition) {
  const materials=state.materials.filter(m=>m.productId===body.productId),pool=state.titlePools?.[body.productId];
  const error=titleAdditionError(materials,pool,body.kind,body.source==='MANUAL'?body.text:undefined);
  if(error)throw new Error(error);
  return body.source==='AI' ? creationGenerateTitle(state,body.productId,body.kind,body.requestId!)
    : creationProductTitles(state,body.productId,body.kind,[...productTitleValues(materials,pool,body.kind),body.text.trim()],true);
}
