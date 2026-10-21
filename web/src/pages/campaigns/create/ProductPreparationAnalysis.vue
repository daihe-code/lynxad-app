<template>
  <section class="preparation" aria-label="投放准备分析">
    <article class="prep-summary"><span class="prep-icon" aria-hidden="true">✦</span><div><p class="prep-label">小灵的建议</p><h3>{{ mixed ? '建议先确认商品组合，再准备投放' : '先确认商品卖点，再准备不同方向的素材' }}</h3><p>{{ preparation?.summary || '已根据商品基础信息整理准备事项；需求、场景及卖点尚需你确认。' }}</p><small>当前依据：商品基础信息。{{ analysis?.processing?.inference_error ? '智能建议暂不可用，基础准备事项仍可查看。' : '市场趋势及历史投放数据需结合实际情况核验。' }}</small></div></article>
    <article v-if="products.length > 1" class="prep-group"><div><h3>商品组合建议</h3><p>跨品类不代表不能合投，先确认目标和预算，再决定是否拆分。</p></div><div class="prep-group-actions"><button type="button" :aria-expanded="groupOpen" @click="groupOpen = !groupOpen">查看分组建议</button><button type="button" :disabled="busy || readOnly" @click="emit('keep')">{{ kept ? '已保留当前组合' : '保留当前组合' }}</button></div><div v-if="groupOpen" class="prep-groups"><article v-for="group in groups" :key="group.name"><h4>{{ group.name }}</h4><p>{{ names(group.product_ids) }}</p><small>{{ group.reason }}</small><button v-if="group.product_ids.length < products.length && !readOnly" type="button" :disabled="busy" @click="emit('group', group.product_ids)">仅将这组商品保留在本计划</button></article><p>此处不会自动创建其他草稿或计划。</p></div></article>
    <article class="prep-table"><h3>逐商品投放准备分析</h3><div class="prep-table-scroll"><table><thead><tr><th>商品</th><th>需求与场景（待验证）</th><th>素材方向</th><th>需你确认</th></tr></thead><tbody><tr v-for="row in visibleRows" :key="row.product_id"><td><b>{{ row.name }}</b><small>{{ row.source === 'ai_suggestion' ? '智能建议 · 待验证' : '商品信息基础建议' }}</small></td><td>{{ row.demand_scene }}</td><td>{{ row.material_direction }}</td><td><ul><li v-for="check in row.confirmations" :key="check">{{ check }}</li></ul></td></tr></tbody></table></div><button v-if="rows.length > 3" type="button" class="prep-expand" @click="expanded = !expanded">{{ expanded ? '收起' : `其余 ${rows.length - 3} 款已收起，展开查看` }}</button></article>
    <details class="prep-market"><summary>市场竞争参考 · 查看数据说明</summary><p>{{ preparation?.market_reference.message || '当前结果未提供符合本次分析口径的市场数据，不据此判断商品优劣。' }}</p><article v-for="(record,index) in marketRecords" :key="`${record.knowledge_id}:${index}`"><b>{{ record.label }}：{{ record.value }} {{ record.unit }}</b><p>{{ record.platform }} · {{ record.scope }}</p><small>{{ record.observed_at }} · {{ record.source }}</small><a v-if="record.url" :href="record.url" target="_blank" rel="noopener noreferrer">{{ record.title }}</a></article><p>商品信息推断不等于平台统计；没有可核验数据时不展示竞品数量或近7天搜索指数。</p></details>
    <article class="prep-next"><h3>这些分析将用于下一步</h3><div><b>素材生成</b><p>按已确认的卖点和场景准备差异化素材，未确认的信息请先核实。</p></div><div><b>预算评估</b><p>结合历史表现、平台建议和你的经营目标，确认测试预算。</p></div><small>本次分析不包含未来 ROI 或成交预测。</small></article>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import type { CreationProduct, ProductAnalysis } from '@/features/delivery/creationApi';
const props=defineProps<{products: CreationProduct[]; analysis?: ProductAnalysis; busy: boolean; readOnly?: boolean; groupDecision?:string}>();
const emit=defineEmits<{group:[ids:string[]];keep:[]}>();
const preparation=computed(()=>props.analysis?.preparation), groupOpen=ref(false), expanded=ref(false), kept=computed(()=>props.groupDecision === 'KEEP_CURRENT');
const rows=computed(()=>props.products.map(p=>{
  const row=preparation.value?.products.find(row=>row.product_id===p.qcProductId);
  return row ? {...row,name:p.name} : {product_id:p.qcProductId,name:p.name,source:'product_facts',demand_scene:'需求及使用场景待验证，请结合商品用途和目标人群确认。',material_direction:'展示真实外观、结构与已确认卖点，准备不同场景及表达的素材。',confirmations:['确认实际用途、适用人群和商品规格','核对价格、主图和可使用的卖点']};
}));
const visibleRows=computed(()=>expanded.value?rows.value:rows.value.slice(0,3));
const groups=computed(()=>{
  const supplied=preparation.value?.group_suggestion.groups;
  if(supplied?.length)return supplied.filter(g=>g.product_ids.length && g.product_ids.every(id=>props.products.some(p=>p.qcProductId===id)));
  const map=new Map<string,string[]>();for(const p of props.products){const key=p.category||'待确认类目';map.set(key,[...(map.get(key)||[]),p.qcProductId]);}
  return [...map].map(([name,product_ids])=>({name,product_ids,reason:'按商品类目提供的待验证组合，需结合目标、历史表现与预算决定。'}));
});
const mixed=computed(()=>groups.value.length>1);
const marketRecords=computed(()=>(preparation.value?.market_reference.records||[]).map(record=>{
  let url:string|undefined;try{const u=new URL(record.url||'');if(['https:','http:'].includes(u.protocol)&&!u.username&&!u.password)url=u.href;}catch{/* Internal knowledge does not require a public link. */}
  return {...record,url};
}));
const names=(ids:string[])=>props.products.filter(p=>ids.includes(p.qcProductId)).map(p=>p.name).join('、');
</script>
<style scoped>
.preparation{display:grid;gap:20px;color:#30344e}.preparation article,.prep-market{border:1px solid #ececf6;border-radius:16px;padding:24px;background:#fff}.preparation h3{font-size:17px;font-weight:650;margin:0 0 8px}.preparation p{font-size:13px;color:#747990;line-height:1.8;margin:4px 0}.preparation small{display:block;font-size:12px;line-height:1.8;color:#868ba1}.prep-summary{display:flex;gap:18px;background:#f7f6ff!important}.prep-icon{font-size:30px;color:#575cf6}.prep-label{color:#575cf6!important;font-weight:600}.prep-group{display:flex;align-items:center;gap:20px;flex-wrap:wrap}.prep-group>div:first-child{flex:1;min-width:220px}.prep-group-actions{display:flex;gap:12px;flex-wrap:wrap}.preparation button{padding:9px 14px;border:1px solid #dcdaf8;border-radius:8px;background:white;color:#575cf6;font-size:13px;cursor:pointer}.preparation button:disabled{opacity:.5;cursor:default}.prep-groups{flex-basis:100%;display:grid;gap:12px}.prep-groups article{padding:16px}.prep-groups h4{font-size:14px;font-weight:600}.prep-groups button{margin-top:10px}.prep-table-scroll{overflow:auto}table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:13px;line-height:1.8}th,td{text-align:left;padding:14px;vertical-align:top;border-bottom:1px solid #f0eff8;overflow-wrap:anywhere}th{background:#f7f7fc;color:#7f849a;font-size:12px;font-weight:500}th:first-child{width:19%}td ul{padding-left:16px;margin:0}.prep-expand{margin-top:14px}.prep-market summary{cursor:pointer;color:#575cf6;font-size:14px}.prep-market p{margin-top:12px}.prep-next{display:grid;grid-template-columns:1fr 1fr;gap:12px;background:#fafaff!important}.prep-next h3,.prep-next>small{grid-column:1/-1}.prep-next b{font-size:14px}@media(max-width:700px){.preparation article,.prep-market{padding:18px}.prep-next{grid-template-columns:1fr}.prep-table-scroll table{min-width:680px}}
</style>
