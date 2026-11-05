<template>
  <main class="ad-main"><div class="container-page"><section class="panel"><div class="panel__pad published-materials">
    <header class="published-materials__header"><div><h2 class="panel__title">已投放素材</h2><p class="panel__sub">查看当前计划的千川素材，点击素材或标题可查看预览</p></div></header>
    <label class="published-materials__product">素材对应商品<select v-model="productId" aria-label="素材对应商品"><option value="">全部商品</option><option v-for="product in productOptions" :key="product.id" :value="product.id">{{ product.name }}</option></select></label>
    <nav class="published-materials__tabs" role="tablist" aria-label="已投放素材分类"><button v-for="tab in tabs" :key="tab.type" type="button" role="tab" :aria-selected="type === tab.type" @click="type = tab.type">{{ tab.label }}<span v-if="snapshots[tab.type]">{{ visiblePlanMaterials(snapshots[tab.type]!.records,productId).length }}</span></button></nav>
    <p v-if="error" class="published-materials__error" role="alert">{{ error }}<button type="button" :disabled="loading" @click="load">重新读取</button></p>
    <p v-if="current?.warning" class="published-materials__notice" role="status">{{ current.warning }}</p>
    <p v-if="loading" class="published-materials__notice" role="status">正在读取{{ labels[type] }}素材…</p>
    <div v-if="rows.length" class="published-materials__work" :aria-busy="loading">
      <ul class="published-materials__list"><li v-for="material in rows" :key="material.id"><button type="button" class="published-materials__item" :class="{ selected: active?.id === material.id }" :aria-pressed="active?.id === material.id" :aria-label="`查看素材 ${planMaterialName(material)}`" @click="selectedId = material.id">
        <span class="published-materials__thumb"><img v-if="material.posterUrl || material.imageUrls[0]" :src="material.posterUrl || material.imageUrls[0]" alt="" /><ReferenceIcon v-else :name="type === 'VIDEO' ? 'video' : type === 'IMAGE' ? 'image' : 'article'" /></span>
        <span class="published-materials__copy"><strong :title="planMaterialName(material)">{{ planMaterialName(material) }}</strong><span v-if="material.reasons.length" class="published-materials__reason">{{ material.reasons.join('；') }}</span></span>
      </button></li></ul>
      <div class="published-materials__preview"><blockquote v-if="active?.type === 'TITLE'">{{ active.text || active.name }}</blockquote><MaterialPreview v-else :key="`${preview?.id}:${current?.fetchedAt}`" :material="preview" :product="previewProduct" :account="account" /></div>
    </div>
    <p v-else-if="current && !loading" class="published-materials__empty">{{ error ? '暂无法刷新此分类的素材，请重新读取。' : productId ? '此商品在当前分类下暂无素材，可切换商品或分类查看。' : '千川在所查询区间内未返回此分类的素材，可切换分类查看。' }}</p>
    <p class="published-materials__period">查询区间：{{ range[0] }} 至 {{ range[1] }}{{ current?.fetchedAt ? ` · 最近读取 ${reviewTime(current.fetchedAt)}` : '' }}</p>
  </div></section></div></main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { CreationProduct } from '@/features/delivery/creationApi';
import type { PlanVo } from '@/features/delivery/deliveryApi';
import { insightMaterials, type PlanMaterials } from '@/features/delivery/planInsightsApi';
import { planMaterialRange, visiblePlanMaterials, planMaterialPreview, planMaterialProduct, planMaterialName } from '@/features/delivery/planMaterialPreview';
import { reviewTime } from '@/features/delivery/planReview';
import MaterialPreview from '../create/MaterialPreview.vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';

const props = defineProps<{plan: PlanVo;products: CreationProduct[];account?: {awemeName:string;avatar?:string}}>();
const tabs = [{type:'VIDEO',label:'视频'},{type:'IMAGE',label:'图片'},{type:'CAROUSEL',label:'图文'},{type:'TITLE',label:'标题'}];
const labels: Record<string,string> = Object.fromEntries(tabs.map(tab=>[tab.type,tab.label]));
const type = ref('VIDEO'), productId = ref(''), selectedId = ref(''), loading = ref(false), error = ref('');
const snapshots = ref<Record<string,PlanMaterials>>({});
const range = computed(()=>planMaterialRange(props.plan));
const current = computed(()=>snapshots.value[type.value]);
const rows = computed(()=>visiblePlanMaterials(current.value?.records || [],productId.value));
const active = computed(()=>rows.value.find(material=>material.id === selectedId.value) || rows.value[0]);
const preview = computed(()=>planMaterialPreview(active.value));
const previewProduct = computed(()=>planMaterialProduct(active.value,props.products,productId.value));
const productOptions = computed(()=>[...new Map([
  ...props.products.map(product=>[product.qcProductId,{id:product.qcProductId,name:product.name}] as const),
  ...Object.values(snapshots.value).flatMap(snapshot=>snapshot.records.flatMap(material=>material.products.map(product=>[product.id,product] as const))),
]).values()]);
let controller: AbortController | undefined, request = 0;
async function load() {
  controller?.abort();controller = new AbortController();const signal=controller.signal,turn=++request,category=type.value;
  loading.value=true;error.value='';selectedId.value='';
  try {
    const result=await insightMaterials(props.plan.planId,range.value,category,signal,true);
    if (turn!==request || signal.aborted) return;
    if (!Array.isArray(result.records)) throw new Error('素材响应不完整，请重新读取。');
    snapshots.value={...snapshots.value,[category]:result};
  } catch (failure: any) {
    if (turn===request && !signal.aborted) error.value=`${failure?.message || '素材读取失败，请稍后重试。'}${snapshots.value[category] ? ' 已保留上次读取的素材。' : ''}`;
  } finally { if (turn===request) loading.value=false; }
}
watch(()=>[props.plan.planId,props.plan.qcPlanId,props.plan.startDate,props.plan.endDate],()=>{controller?.abort();request++;snapshots.value={};productId.value='';selectedId.value='';void load();},{immediate:true});
watch(type,()=>{if (snapshots.value[type.value]) {controller?.abort();request++;loading.value=false;error.value='';selectedId.value='';} else void load();});
onBeforeUnmount(()=>{request++;controller?.abort();});
</script>

<style scoped>
.published-materials__header{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.published-materials__product{display:flex;align-items:center;gap:22px;margin:30px 0 22px;font-weight:600}.published-materials__product select{flex:1;min-width:0;max-width:700px;padding:11px 14px;border:1px solid #e1e1f3;border-radius:10px;background:#fff;color:#555b79;font:inherit;font-weight:400}.published-materials__tabs{display:flex;gap:6px;max-width:510px;padding:5px;margin:0 0 28px;background:#f5f5fa;border-radius:12px}.published-materials__tabs button{display:flex;flex:1;justify-content:center;gap:9px;padding:10px 14px;border-radius:9px;font:inherit;color:#656985}.published-materials__tabs button[aria-selected=true]{background:#fff;color:#5b55ff;box-shadow:0 1px 4px #24244220;font-weight:600}.published-materials__tabs span{font-size:12px;padding:0 5px;background:#efeeff;border-radius:5px}.published-materials__work{display:grid;grid-template-columns:minmax(0,1fr) 350px;gap:30px;align-items:start}.published-materials__list{display:grid;gap:12px;min-width:0;margin:0;padding:0;list-style:none}.published-materials__item{display:flex;align-items:center;gap:14px;width:100%;padding:14px;border:1px solid #e1e3f4;border-radius:13px;text-align:left;font:inherit}.published-materials__item.selected{border-color:#6155ff;background:#f8f7ff}.published-materials__thumb{flex:none;display:grid;place-items:center;width:66px;height:66px;overflow:hidden;border-radius:8px;background:#f1effc;color:#837cc2}.published-materials__thumb img{width:100%;height:100%;object-fit:contain}.published-materials__thumb svg{width:27px;height:27px}.published-materials__copy{min-width:0;display:grid;gap:7px}.published-materials__copy strong{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;line-height:1.6;font-size:15px}.published-materials__copy small{color:#8588a5;font-size:12px}.published-materials__preview{min-width:0;padding:18px;border-radius:18px;background:#eef3ff}.published-materials__preview blockquote{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9}.published-materials__reason,.published-materials__error{color:#c45057;font-size:13px;line-height:1.8}.published-materials__error{margin:16px 0}.published-materials__error button{margin-left:12px;text-decoration:underline;color:#5b55ff;font:inherit}.published-materials__notice,.published-materials__period{color:#8588a5;font-size:12px;line-height:1.8;margin:14px 0}.published-materials__empty{padding:45px 0;color:#8588a5;text-align:center;font-size:14px;line-height:1.8}.published-materials__period{margin-top:24px}.published-materials button:focus-visible,.published-materials select:focus-visible{outline:2px solid #5b55ff;outline-offset:3px}
@media(max-width:1000px){.published-materials__work{grid-template-columns:minmax(0,1fr) 310px;gap:18px}}
@media(max-width:767px){.published-materials__work{grid-template-columns:1fr}.published-materials__header{flex-wrap:wrap;gap:14px}.published-materials__product{align-items:stretch;flex-direction:column;gap:10px;margin-top:24px}.published-materials__tabs button{min-width:0;padding:10px 4px;gap:4px;font-size:14px;white-space:nowrap}.published-materials__preview{max-width:350px;width:100%;box-sizing:border-box;margin:auto}}
</style>
