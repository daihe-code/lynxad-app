import type { CreationState } from './creationApi.ts';
/** Refresh is an application freshness policy, not a claim about platform quote expiry. */
export function quoteNeedsReview(state: CreationState,budget:string,now=Date.now()) {
  if(state.smartBidType !== 'CONSERVATIVE')return false;
  const r=state.recommendations, rows=r?.budget?.estimate_trans_cost_list;
  if(!Array.isArray(rows)||!rows.some(row=>Number(row?.budget)===Number(budget)))return false;
  const timestamp=Date.parse(r?.fetchedAt || '');
  const ids=new Set(r?.productIds||[]);
  return r?.accountId!==state.accountId || r.awemeId!==state.awemeId || ids.size!==state.products.length || state.products.some(p=>!ids.has(p.qcProductId)) || !Number.isFinite(timestamp) || now-timestamp>300000 || timestamp>now+60000;
}
export function quoteReviewKey(state: CreationState,budget:string) {return JSON.stringify([state.planId,state.accountId,state.awemeId,state.products.map(p=>p.qcProductId),budget,state.recommendations]);}
