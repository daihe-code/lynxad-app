<template>
  <section v-if="account?.accountId && account.awemeId && selected.length" ref="panel" class="product-suggestions" aria-labelledby="suggestion-heading" :aria-busy="loading">
    <div class="suggestion-heading"><div><h3 id="suggestion-heading">小灵推荐商品</h3><p v-if="sourceProduct">与「{{ sourceProduct.name }}」相关的可投商品，默认不选中，可手动勾选加入。</p><p v-else>根据已选商品匹配其他可投商品，默认不选中，可手动勾选加入。</p></div><div class="suggestion-actions"><button v-if="sourceProduct" type="button" :disabled="busy" @click="emit('clearFocus')">按全部已选推荐</button><button type="button" :disabled="loading || busy" @click="load(true)">{{ loading ? '正在匹配…' : '重新匹配' }}</button></div></div>
    <p v-if="selected.length >= 10" class="suggestion-note">已选满 10 款商品；可继续查看推荐，移除部分商品后再加入。</p>
      <p v-if="loading" role="status" class="suggestion-note">正在查找同一账户、同一抖音号下的相关可投商品，不影响手动选品…</p>
      <div v-else-if="failure" class="suggestion-error" role="alert"><p>推荐暂未获取成功，你仍可手动选择商品。</p><small>{{ failure.message }}</small><button type="button" :disabled="busy" @click="load(true)">重试推荐</button></div>
      <template v-else-if="result">
        <div v-if="visible.length" class="suggestion-grid"><article v-for="item in visible" :key="item.product.qcProductId" class="suggestion-card">
          <img v-if="item.product.imageUrl && !broken[item.product.qcProductId]" :src="item.product.imageUrl" alt="" loading="lazy" @error="broken[item.product.qcProductId] = true" /><span v-else class="suggestion-placeholder" aria-hidden="true">商品</span>
          <div class="suggestion-copy"><h4 :title="item.product.name">{{ item.product.name }}</h4><ProductPrice :price-info="item.product.priceInfo" :price="item.product.price" :user-price="item.product.userPrice" /><ul><li v-for="reason in item.reasons" :key="reason">{{ reason }}</li></ul><small v-if="item.product.stock == null">库存待核实</small><label class="suggestion-select"><input type="checkbox" :checked="isSelected(item.product.qcProductId)" :disabled="busy || selected.length >= 10 && !isSelected(item.product.qcProductId)" :aria-label="`选择推荐商品：${item.product.name}`" @change="toggle(item.product)" /><span>{{ isSelected(item.product.qcProductId) ? '已加入选择' : '加入选择' }}</span></label></div>
        </article></div>
        <p v-else class="suggestion-note">本次候选范围内，暂无相关且可加入当前计划的商品。可继续手动选品，或调整已选商品后再试。</p>
        <p class="suggestion-basis">{{ result.message }} <span>本次评估 {{ result.scannedCount }} 个候选商品{{ result.partial ? '，未覆盖全部商品' : '' }}。</span></p>
      </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { creationProductSuggestions, type CreationProduct, type ProductSuggestions } from '@/features/delivery/creationApi';
import { ApiRequestError, requestFailure } from '@/api/apiErrors';
import ProductPrice from '@/components/ProductPrice.vue';
const props=defineProps<{account?: {accountId?:string;awemeId:string};selected:CreationProduct[];draftId?:string;busy:boolean;sourceProductId?:string}>();
const emit=defineEmits<{select:[product:CreationProduct];clearFocus:[]}>();
const panel=ref<HTMLElement>();
defineExpose({reveal:()=>panel.value?.scrollIntoView({behavior:'smooth',block:'start'})});
const result=ref<ProductSuggestions>(),failure=ref<ApiRequestError>(),loading=ref(false),broken=ref<Record<string,boolean>>({});
const sourceProduct=computed(()=>props.selected.find(p=>p.qcProductId===props.sourceProductId));
const sourceIds=computed(()=>sourceProduct.value?[sourceProduct.value.qcProductId]:props.selected.map(p=>p.qcProductId).sort());
const scope=computed(()=>props.account?.accountId&&props.account.awemeId&&sourceIds.value.length>0
  ? JSON.stringify([props.account.accountId,props.account.awemeId,props.draftId||'',sourceIds.value]) : '');
const visible=computed(()=>result.value?.items||[]);
function isSelected(id:string){return props.selected.some(p=>p.qcProductId===id);}
function toggle(product:CreationProduct){
  if(props.busy||props.selected.length>=10&&!isSelected(product.qcProductId))return;
  emit('select',product);
}
let timer:ReturnType<typeof setTimeout>|undefined,request:AbortController|undefined,sequence=0,disposed=false;
watch(scope,()=>{
  clearTimeout(timer);request?.abort();sequence++;result.value=undefined;failure.value=undefined;loading.value=false;
  if(scope.value){loading.value=true;timer=setTimeout(()=>void load(false),650);}
},{immediate:true});
async function load(refresh:boolean){
  clearTimeout(timer);if(!scope.value||disposed)return;
  request?.abort();request=new AbortController();const current=++sequence,key=scope.value,account=props.account!,ids=[...sourceIds.value];
  loading.value=true;failure.value=undefined;result.value=undefined;
  try{
    const next=await creationProductSuggestions(account.accountId!,account.awemeId,ids,props.draftId,refresh,request.signal);
    if(disposed||current!==sequence||key!==scope.value)return;
    if(next.accountId!==account.accountId||next.awemeId!==account.awemeId||JSON.stringify([...next.sourceProductIds].sort())!==JSON.stringify([...ids].sort()))throw new Error('推荐结果与当前选品不一致，请重新匹配');
    // Keep explicitly adopted candidates in this result so their checked state stays visible.
    const seen=new Set(props.selected.map(p=>p.qcProductId));
    result.value={...next,items:next.items.filter(({product})=>{
      if(seen.has(product.qcProductId)||product.draft||product.unavailableReasons?.length||product.stock!=null&&product.stock<=0)return false;
      seen.add(product.qcProductId);return true;
    })};
  }catch(error){if(!disposed&&current===sequence&&key===scope.value){const next=requestFailure(error,{method:'GET'});if(next.kind!=='CANCELLED')failure.value=next;}}
  finally{if(!disposed&&current===sequence)loading.value=false;}
}
onUnmounted(()=>{disposed=true;sequence++;clearTimeout(timer);request?.abort();});
</script>
<style scoped>
.product-suggestions{scroll-margin-top:90px}.suggestion-actions{display:flex;flex-shrink:0;flex-wrap:wrap;gap:8px}.suggestion-select{display:inline-flex;align-items:center;gap:6px;margin-top:8px;color:var(--color-primary,#575cf6);cursor:pointer;font-size:12px}.suggestion-select input{width:16px;height:16px;margin:0;accent-color:var(--color-primary,#575cf6);cursor:pointer}.suggestion-select:has(input:disabled){opacity:.5;cursor:default}.suggestion-select input:focus-visible,.product-suggestions button:focus-visible{outline:2px solid var(--color-primary,#575cf6);outline-offset:3px}@media(max-width:650px){.suggestion-heading{flex-wrap:wrap}.suggestion-actions{flex-shrink:1}}
.product-suggestions{margin:0 0 22px;padding:20px;border:1px solid var(--color-primary-soft,#e2dfff);border-radius:14px;background:#fafaff;color:var(--color-text,#25263a)}.suggestion-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.suggestion-heading h3{font-size:16px;font-weight:650;margin:0}.suggestion-heading p,.suggestion-note,.suggestion-error p{font-size:13px;line-height:1.7;color:var(--color-muted,#787e96);margin:6px 0}.product-suggestions button{border:1px solid #dcdaf8;border-radius:8px;background:white;padding:7px 12px;font:inherit;font-size:12px;color:var(--color-primary,#575cf6);cursor:pointer;white-space:nowrap}.product-suggestions button:disabled{opacity:.5;cursor:default}.suggestion-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:16px}.suggestion-card{display:flex;align-items:flex-start;gap:10px;min-width:0;background:white;padding:12px;border:1px solid #eae9f4;border-radius:10px}.suggestion-card img,.suggestion-placeholder{width:66px;height:66px;flex-shrink:0;border-radius:8px;object-fit:cover;background:#f0eff8}.suggestion-placeholder{display:grid;place-items:center;font-size:12px;color:#9396a5}.suggestion-copy{min-width:0;flex:1;font-size:13px}.suggestion-copy h4{font-size:13px;line-height:1.5;font-weight:600;margin:0 0 4px;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}.suggestion-copy ul{padding-left:14px;margin:6px 0;color:#797e92;font-size:12px;line-height:1.6;overflow-wrap:anywhere}.suggestion-copy small{display:block;font-size:11px;color:#9497a6}.suggestion-copy button{margin-top:8px}.suggestion-basis,.suggestion-error small{display:block;font-size:12px;line-height:1.7;color:#8a8ea1;overflow-wrap:anywhere;margin-top:12px}.suggestion-error button{margin-top:10px}@media(max-width:1100px){.suggestion-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:650px){.product-suggestions{padding:14px}.suggestion-grid{grid-template-columns:1fr}.suggestion-heading{gap:8px}}
</style>
