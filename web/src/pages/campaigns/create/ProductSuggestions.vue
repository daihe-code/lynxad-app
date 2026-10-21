<template>
  <section v-if="account?.accountId && account.awemeId && selected.length" class="product-suggestions" aria-labelledby="suggestion-heading" :aria-busy="loading">
    <div class="suggestion-heading"><div><h3 id="suggestion-heading">小灵推荐商品</h3><p>根据已选商品匹配其他可投商品，由你决定是否加入。</p></div><button v-if="selected.length < 10" type="button" :disabled="loading || busy" @click="load(true)">{{ loading ? '正在匹配…' : '重新匹配' }}</button></div>
    <p v-if="selected.length >= 10" class="suggestion-note">已选满 10 款商品；移除部分商品后，可继续查看推荐。</p>
    <template v-else>
      <p v-if="loading" role="status" class="suggestion-note">正在查找同一账户、同一抖音号下的相关可投商品，不影响手动选品…</p>
      <div v-else-if="failure" class="suggestion-error" role="alert"><p>推荐暂未获取成功，你仍可手动选择商品。</p><small>{{ failure.message }}</small><button type="button" :disabled="busy" @click="load(true)">重试推荐</button></div>
      <template v-else-if="result">
        <div v-if="visible.length" class="suggestion-grid"><article v-for="item in visible" :key="item.product.qcProductId" class="suggestion-card">
          <img v-if="item.product.imageUrl && !broken[item.product.qcProductId]" :src="item.product.imageUrl" alt="" loading="lazy" @error="broken[item.product.qcProductId] = true" /><span v-else class="suggestion-placeholder" aria-hidden="true">商品</span>
          <div class="suggestion-copy"><h4 :title="item.product.name">{{ item.product.name }}</h4><ProductPrice :price-info="item.product.priceInfo" :price="item.product.price" :user-price="item.product.userPrice" /><ul><li v-for="reason in item.reasons" :key="reason">{{ reason }}</li></ul><small v-if="item.product.stock == null">库存待核实</small><button type="button" :disabled="busy || selected.length >= 10" @click="emit('select', item.product)" :aria-label="`加入选择：${item.product.name}`">＋ 加入选择</button></div>
        </article></div>
        <p v-else class="suggestion-note">本次候选范围内，暂无相关且可加入当前计划的商品。可继续手动选品，或调整已选商品后再试。</p>
        <p class="suggestion-basis">{{ result.message }} <span>本次评估 {{ result.scannedCount }} 个候选商品{{ result.partial ? '，未覆盖全部商品' : '' }}。</span></p>
      </template>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { creationProductSuggestions, type CreationProduct, type ProductSuggestions } from '@/features/delivery/creationApi';
import { ApiRequestError, requestFailure } from '@/api/apiErrors';
import ProductPrice from '@/components/ProductPrice.vue';
const props=defineProps<{account?: {accountId?:string;awemeId:string};selected:CreationProduct[];draftId?:string;busy:boolean}>();
const emit=defineEmits<{select:[product:CreationProduct]}>();
const result=ref<ProductSuggestions>(),failure=ref<ApiRequestError>(),loading=ref(false),broken=ref<Record<string,boolean>>({});
const scope=computed(()=>props.account?.accountId&&props.selected.length>0&&props.selected.length<10
  ? JSON.stringify([props.account.accountId,props.account.awemeId,props.draftId||'',props.selected.map(p=>p.qcProductId).sort()]) : '');
const visible=computed(()=>result.value?.items.filter(item=>!props.selected.some(p=>p.qcProductId===item.product.qcProductId)&&!item.product.draft&&!item.product.unavailableReasons?.length&&(item.product.stock==null||item.product.stock>0))||[]);
let timer:ReturnType<typeof setTimeout>|undefined,request:AbortController|undefined,sequence=0,disposed=false;
watch(scope,()=>{
  clearTimeout(timer);request?.abort();sequence++;result.value=undefined;failure.value=undefined;loading.value=false;
  if(scope.value){loading.value=true;timer=setTimeout(()=>void load(false),650);}
},{immediate:true});
async function load(refresh:boolean){
  clearTimeout(timer);if(!scope.value||disposed)return;
  request?.abort();request=new AbortController();const current=++sequence,key=scope.value,account=props.account!;
  loading.value=true;failure.value=undefined;result.value=undefined;
  try{
    const next=await creationProductSuggestions(account.accountId!,account.awemeId,props.selected.map(p=>p.qcProductId),props.draftId,refresh,request.signal);
    if(disposed||current!==sequence||key!==scope.value)return;
    if(next.accountId!==account.accountId||next.awemeId!==account.awemeId||JSON.stringify([...next.sourceProductIds].sort())!==JSON.stringify(props.selected.map(p=>p.qcProductId).sort()))throw new Error('推荐结果与当前选品不一致，请重新匹配');
    result.value=next;
  }catch(error){if(!disposed&&current===sequence&&key===scope.value){const next=requestFailure(error,{method:'GET'});if(next.kind!=='CANCELLED')failure.value=next;}}
  finally{if(!disposed&&current===sequence)loading.value=false;}
}
onUnmounted(()=>{disposed=true;sequence++;clearTimeout(timer);request?.abort();});
</script>
<style scoped>
.product-suggestions{margin:0 0 22px;padding:20px;border:1px solid var(--color-primary-soft,#e2dfff);border-radius:14px;background:#fafaff;color:var(--color-text,#25263a)}.suggestion-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.suggestion-heading h3{font-size:16px;font-weight:650;margin:0}.suggestion-heading p,.suggestion-note,.suggestion-error p{font-size:13px;line-height:1.7;color:var(--color-muted,#787e96);margin:6px 0}.product-suggestions button{border:1px solid #dcdaf8;border-radius:8px;background:white;padding:7px 12px;font:inherit;font-size:12px;color:var(--color-primary,#575cf6);cursor:pointer;white-space:nowrap}.product-suggestions button:disabled{opacity:.5;cursor:default}.suggestion-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:16px}.suggestion-card{display:flex;align-items:flex-start;gap:10px;min-width:0;background:white;padding:12px;border:1px solid #eae9f4;border-radius:10px}.suggestion-card img,.suggestion-placeholder{width:66px;height:66px;flex-shrink:0;border-radius:8px;object-fit:cover;background:#f0eff8}.suggestion-placeholder{display:grid;place-items:center;font-size:12px;color:#9396a5}.suggestion-copy{min-width:0;flex:1;font-size:13px}.suggestion-copy h4{font-size:13px;line-height:1.5;font-weight:600;margin:0 0 4px;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}.suggestion-copy ul{padding-left:14px;margin:6px 0;color:#797e92;font-size:12px;line-height:1.6;overflow-wrap:anywhere}.suggestion-copy small{display:block;font-size:11px;color:#9497a6}.suggestion-copy button{margin-top:8px}.suggestion-basis,.suggestion-error small{display:block;font-size:12px;line-height:1.7;color:#8a8ea1;overflow-wrap:anywhere;margin-top:12px}.suggestion-error button{margin-top:10px}@media(max-width:1100px){.suggestion-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:650px){.product-suggestions{padding:14px}.suggestion-grid{grid-template-columns:1fr}.suggestion-heading{gap:8px}}
</style>
