import test from 'node:test';
import assert from 'node:assert/strict';
import {quoteNeedsReview,quoteReviewKey} from '../src/features/delivery/creationQuoteReview.ts';
const now=Date.now();
const state=()=>({planId:'p',accountId:'a',awemeId:'w',smartBidType:'CONSERVATIVE',products:[{qcProductId:'123'}],recommendations:{accountId:'a',awemeId:'w',productIds:['123'],fetchedAt:new Date(now).toISOString(),budget:{estimate_trans_cost_list:[{budget:300,estimate_roi:2}]}}});
test('仅在使用对应档位的预估时检查绑定与新鲜度，不增加用户预算',()=>{
 const s=state();assert.equal(quoteNeedsReview(s,'300',now),false);assert.equal(quoteNeedsReview(s,'300.00',now),false);assert.equal(quoteNeedsReview(s,'301',now+900000),false);
 for(const mutate of [s=>s.recommendations.accountId='b',s=>s.recommendations.awemeId='other',s=>s.recommendations.productIds=['456'],s=>delete s.recommendations.fetchedAt,s=>s.recommendations.fetchedAt=new Date(now-300001).toISOString(),s=>s.recommendations.fetchedAt=new Date(now+90000).toISOString()]){const s=state();mutate(s);assert.equal(quoteNeedsReview(s,'300',now),true);}
 const before=JSON.stringify(s);quoteNeedsReview(s,'300',now+900000);assert.equal(JSON.stringify(s),before);
});
test('确认仅绑定当前草稿、商品、输入预算和预估快照；新值需重新确认',()=>{
 const s=state(),key=quoteReviewKey(s,'300');for(const mutate of [s=>s.planId='other',s=>s.products=[{qcProductId:'456'}],s=>s.recommendations.budget.estimate_trans_cost_list[0].estimate_roi=3]){const changed=structuredClone(s);mutate(changed);assert.notEqual(quoteReviewKey(changed,'300'),key);}assert.notEqual(quoteReviewKey(s,'301'),key);
 s.smartBidType='CUSTOM';assert.equal(quoteNeedsReview(s,'300',now+900000),false);
});
