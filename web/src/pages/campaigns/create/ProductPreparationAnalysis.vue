<template>
  <section class="preparation" aria-label="投放准备分析">
    <article class="prep-summary">
      <span class="prep-summary__icon"><PreparationIcon name="bulb" /></span>
      <div class="prep-summary__body"><p class="prep-label">小灵的建议</p><h3>{{ report?.summary.title || '确认商品信息，准备投放素材' }}</h3><p>{{ report?.summary.message || preparation?.summary }}</p><small>{{ coverage }}</small></div>
    </article>

    <article v-if="products.length > 1" class="prep-group">
      <div class="prep-group-copy"><div class="prep-heading"><span class="prep-icon"><PreparationIcon name="combination" /></span><h3>商品组合建议</h3></div><p class="prep-clamp" :title="report?.combination.conclusion">{{ report?.combination.conclusion || '请结合商品用途、经营目标和测试预算，确认当前组合。' }}</p></div>
      <div class="prep-group-points"><div v-for="point in highlights" :key="point.icon" class="prep-point"><span class="prep-icon prep-icon--round"><PreparationIcon :name="point.icon" /></span><div><b>{{ point.title }}</b><small class="prep-clamp" :title="point.description">{{ point.description }}</small></div></div></div>
      <div class="prep-group-actions"><button type="button" class="prep-button" @click="groupOpen = true">查看分组建议</button><span class="prep-decision"><span class="prep-pending" :class="{'prep-pending--kept':kept}">{{ kept ? '已确认' : readOnly ? '未确认' : '待你确认' }}</span><button v-if="!readOnly" type="button" class="prep-button prep-button--primary" :disabled="busy || kept" @click="emit('keep')">{{ kept ? '已保留当前组合' : '保留当前组合' }}</button><span v-else class="prep-kept-label">{{ kept ? '已保留当前组合' : '当前商品组合' }}</span></span></div>
    </article>

    <article class="prep-table">
      <div class="prep-heading"><span class="prep-icon"><PreparationIcon name="document" /></span><h3>逐商品投放准备分析</h3></div>
      <div class="prep-table-scroll"><table><colgroup><col class="prep-col-product"/><col class="prep-col-scene"/><col class="prep-col-material"/><col class="prep-col-confirm"/></colgroup><thead><tr><th>商品</th><th>需求与场景（待验证）</th><th>素材方向</th><th>需你确认</th></tr></thead><tbody>
        <template v-for="row in visibleRows" :key="row.product_id">
          <tr class="prep-product-row" :class="{'prep-product-row--open':expandedProducts.includes(row.product_id)}" @click="toggleProduct(row.product_id)">
            <td><button class="prep-product" type="button" :aria-expanded="expandedProducts.includes(row.product_id)" :aria-label="`查看${row.fullName}的分析详情`" :title="row.fullName" @click.stop="toggleProduct(row.product_id)"><img v-if="row.image && !broken[row.product_id]" :src="row.image" alt="" @error="broken[row.product_id] = true"/><span v-else class="prep-product-placeholder">{{ row.name.slice(0,2) }}</span><b class="prep-clamp">{{ row.name }}</b><PreparationIcon name="chevron" class="prep-row-chevron" /></button></td>
            <td><span class="prep-clamp" :title="row.demand_scene">{{ row.demand_scene }}</span></td><td><span class="prep-clamp" :title="row.material_direction">{{ row.material_direction }}</span></td><td><span class="prep-clamp" :title="row.confirmations.join('；')">{{ row.confirmations.slice(0,2).join('；') || '暂无额外待确认事项' }}</span></td>
          </tr>
          <tr v-if="expandedProducts.includes(row.product_id)"><td colspan="4" class="prep-details"><div class="prep-detail-grid">
            <section><h4>已知事实</h4><ul><li v-for="fact in row.report?.known_facts || []" :key="fact.statement">{{ factText(fact) }}<small>依据：{{ evidenceText(fact.evidence_ids) }}</small></li></ul><p v-if="!row.report?.known_facts.length">该历史报告未保存字段级证据，请核对商品信息。</p></section>
            <section><h4>创意假设 · 待验证</h4><p v-for="hypothesis in row.report?.hypotheses || []" :key="hypothesis.need_or_scene">{{ hypothesis.need_or_scene }}<small>推断依据：{{ hypothesis.basis }}</small><small>验证方式：{{ hypothesis.validation_needed }}</small></p><p v-if="!row.report?.hypotheses.length">{{ row.demand_scene }}</p></section>
            <section><h4>素材准备要求</h4><div v-for="direction in row.report?.creative_directions || []" :key="direction.title" class="prep-detail-item"><b>{{ direction.title }}</b><p>{{ direction.display_content }}</p><small>所需素材：{{ direction.required_assets.join('、') || '待确认' }}</small><small v-if="direction.pending_claims.length">待确认卖点：{{ direction.pending_claims.join('、') }}</small><small v-if="direction.avoid.length">不可直接使用：{{ direction.avoid.join('、') }}</small></div><p v-if="!row.report">{{ row.material_direction }}</p></section>
            <section><h4>待补充与待确认</h4><ul><li v-for="question in row.confirmations" :key="question">{{ question }}</li><li v-for="issue in row.report?.issues || []" :key="issue.code">{{ issue.severity==='blocking' ? '需先处理：' : '' }}{{ issue.message }}<small>{{ issue.required_input }}</small></li></ul></section>
            <section v-if="row.report?.historical_reference.length || row.report?.platform_reference.length"><h4>历史与平台参考</h4><p v-for="reference in [...(row.report?.historical_reference || []),...(row.report?.platform_reference || [])]" :key="reference.metric+reference.observed_period">{{ reference.metric_definition }}：{{ reference.value==null ? '缺少可计算口径' : `${reference.value}${reference.unit || ''}` }}<small>{{ reference.observed_period }} · {{ evidenceText(reference.evidence_ids) }}</small></p><small>历史表现与平台建议不代表未来投放结果。</small></section>
            <section><h4>该商品的市场依据</h4><p v-for="market in productMarkets(row.product_id)" :key="market.dimension">{{ market.missing_reason || market.observations.map(o=>o.statement).join('；') || market.limitation }}</p><p v-if="!productMarkets(row.product_id).length">本次没有该商品可核验的市场统计。</p></section>
          </div></td></tr>
        </template>
      </tbody></table></div>
      <button v-if="rows.length > 3" type="button" class="prep-expand" :aria-expanded="expanded" @click="expanded = !expanded">{{ expanded ? '收起商品' : `其余 ${rows.length-3} 款已收起，展开查看` }}<PreparationIcon name="chevron" :class="{'is-up':expanded}"/></button>
    </article>

    <article class="prep-market"><div class="prep-heading"><span class="prep-icon"><PreparationIcon name="chart" /></span><h3>市场竞争参考</h3></div><p class="prep-clamp" :title="marketMessage">{{ marketMessage }}</p><button type="button" class="prep-link" @click="dataOpen = true">查看数据说明<PreparationIcon name="external" /></button></article>

    <article class="prep-next">
      <div class="prep-heading"><span class="prep-icon"><PreparationIcon name="play" /></span><h3>这些分析将用于下一步</h3></div>
      <p class="prep-disclaimer"><PreparationIcon name="info"/>本次分析不包含未来 ROI 或成交预测。</p>
      <button type="button" class="prep-handoff" @click="handoffOpen = 'material'"><span class="prep-icon prep-icon--round"><PreparationIcon name="image" /></span><span><b>素材生成</b><small>按每款商品的已确认卖点和场景准备素材，未确认的信息先请你核实。</small></span><PreparationIcon class="prep-handoff-arrow" name="chevron" /></button>
      <button type="button" class="prep-handoff" @click="handoffOpen = 'budget'"><span class="prep-icon prep-icon--round prep-icon--blue"><PreparationIcon name="chart" /></span><span><b>预算评估</b><small>结合历史表现、平台建议和你确认的经营目标，评估测试预算。</small></span><PreparationIcon class="prep-handoff-arrow" name="chevron" /></button>
    </article>

    <el-dialog v-model="groupOpen" title="商品分组建议" width="760px" append-to-body class="prep-dialog" :close-on-click-modal="false">
      <div class="prep-dialog-content"><p>{{ report?.combination.conclusion || '请结合需求场景、经营目标和测试预算确认组合。' }}</p><div v-if="groups.length" class="prep-groups"><section v-for="(group,index) in groups" :key="group.name" class="prep-dialog-card"><div class="prep-group-title"><h4>建议组合 {{ index+1 }}</h4><span>{{ group.product_ids.length }} 款商品</span></div><div class="prep-group-products"><div v-for="product in groupProducts(group.product_ids)" :key="product.qcProductId"><img v-if="product.imageUrl && !broken[product.qcProductId]" :src="product.imageUrl" alt="" @error="broken[product.qcProductId]=true"/><b>{{ product.name }}</b></div></div><p>{{ group.reason }}</p><button v-if="!readOnly && group.product_ids.length < products.length" class="prep-button prep-button--primary" type="button" :disabled="busy" @click="chooseGroup(group.product_ids)">使用这组商品</button></section></div><div v-else class="prep-dialog-card"><h4>暂不提供具体分组</h4><p>当前依据不足以提出具体分组。你可以保留当前组合，也可以调整商品后重新分析。</p></div><section v-if="report?.combination.unanswered_questions.length"><h4>分组前需你确认</h4><ul><li v-for="question in report.combination.unanswered_questions" :key="question">{{ question }}</li></ul></section><p class="prep-dialog-note">使用建议组合后，将按保留的商品更新分析。其余商品不会自动创建投放计划。</p></div>
      <template #footer><button type="button" class="prep-button" @click="groupOpen=false">返回分析</button><button v-if="!readOnly" type="button" class="prep-button prep-button--primary" :disabled="busy || kept" @click="keepSelection">{{ kept ? '已保留当前组合' : '保留当前组合' }}</button></template>
    </el-dialog>

    <el-dialog v-model="dataOpen" title="分析数据说明" width="760px" append-to-body class="prep-dialog">
      <div class="prep-dialog-content"><section><h4>本次分析依据</h4><p>{{ coverage }}</p><p>{{ report?.summary.next_action }}</p></section><section v-for="(market,index) in report?.market_evidence || []" :key="index" class="prep-dialog-card"><div class="prep-group-title"><h4>{{ marketLabels[market.dimension] || market.dimension }}</h4><span>{{ availabilityLabels[market.availability] }}</span></div><p v-for="observation in market.observations" :key="observation.statement">{{ observation.statement }}<small>依据：{{ evidenceText(observation.evidence_ids) }}</small></p><p v-if="market.missing_reason">{{ market.missing_reason }}</p><dl class="prep-scope"><template v-for="(value,key) in market.scope" :key="key"><template v-if="value"><dt>{{ scopeLabels[key] || key }}</dt><dd>{{ value }}</dd></template></template></dl><small>{{ market.limitation }}</small></section><section v-if="!report?.market_evidence.length"><p>{{ marketMessage }}</p></section><section v-if="sources.length"><h4>使用的来源</h4><div v-for="source in sources" :key="source.source_id" class="prep-source"><b>{{ source.provider }}{{ source.productNames ? ` · ${source.productNames}` : '' }}</b><small>{{ source.displayTime }} · {{ source.verified ? '来源适用范围已校验' : '待核验' }}</small><a v-if="source.link" :href="source.link" target="_blank" rel="noopener noreferrer">查看来源<PreparationIcon name="external"/></a></div></section><section><h4>适用范围与限制</h4><ul><li v-for="limit in report?.limitations || []" :key="limit">{{ limit }}</li></ul><p>公开搜索样本不能代表全市场或真实广告投放量；缺少对应时间窗的指标时，不推断近7天趋势。</p></section></div>
      <template #footer><button type="button" class="prep-button prep-button--primary" @click="dataOpen=false">返回分析</button></template>
    </el-dialog>

    <el-dialog :model-value="!!handoffOpen" :title="handoffOpen==='material' ? '素材生成准备事项' : '预算评估所需信息'" width="760px" append-to-body class="prep-dialog" @update:model-value="closeHandoff">
      <div class="prep-dialog-content">
        <template v-if="handoffOpen==='material'"><p>以下内容会随你确认的商品分析传递给后续素材准备。候选创意需测试，待确认信息不能直接作为广告宣称。</p><section v-for="item in report?.handoff.material || []" :key="item.product_id" class="prep-dialog-card"><h4>{{ names([item.product_id]) }}</h4><b>已确认可用事实</b><ul v-if="item.approved_facts.length"><li v-for="fact in item.approved_facts" :key="fact.statement">{{ factText(fact) }}</li></ul><p v-else>尚无已确认的宣传卖点，需核实后使用。</p><b>候选创意方向 · 待测试</b><p v-for="direction in item.candidate_directions" :key="direction.title">{{ direction.title }}：{{ direction.display_content }}</p><b>待你确认</b><ul><li v-for="question in item.pending_confirmation" :key="question">{{ question }}</li></ul><b>素材缺口</b><p>{{ item.required_assets.join('、') || '暂无额外素材要求' }}</p><b v-if="item.blocked_claims.length">不可直接使用</b><ul><li v-for="claim in item.blocked_claims" :key="claim">{{ claim }}</li></ul></section><p v-if="!report">该历史报告未保存完整交接事项，请重新分析后查看。</p></template>
        <template v-else><p>预算和经营目标由你确认。本阶段不生成未来 ROI、成交量或最佳日预算。</p><section><h4>仍需补充的信息</h4><ul><li v-for="input in report?.handoff.budget.required_inputs || []" :key="input">{{ input }}</li></ul></section><section v-if="report?.handoff.budget.grouping_considerations.length"><h4>组合对测试的影响</h4><ul><li v-for="item in report.handoff.budget.grouping_considerations" :key="item">{{ item }}</li></ul></section><section v-if="budgetReferences.length"><h4>历史表现与平台参考</h4><p v-for="reference in budgetReferences" :key="reference.metric+reference.observed_period">{{ reference.metric_definition }}：{{ reference.value==null ? '缺少可计算口径' : `${reference.value}${reference.unit || ''}` }}<small>{{ reference.observed_period }} · {{ evidenceText(reference.evidence_ids) }}</small></p></section><section><h4>评估限制</h4><ul><li v-for="limit in report?.handoff.budget.limitations || []" :key="limit">{{ limit }}</li></ul></section></template>
      </div><template #footer><button type="button" class="prep-button prep-button--primary" @click="handoffOpen=undefined">返回分析</button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import PreparationIcon from '@/components/PreparationIcon.vue';
import type { CreationProduct, ProductAnalysis } from '@/features/delivery/creationApi';
import type { AnalysisFact } from '@/features/delivery/productSelectionAnalysis';
import { analysisCoverage, combinationHighlights, marketOverview, readableFact, safeSourceLink } from '@/features/delivery/productPreparationView';
const props=defineProps<{products:CreationProduct[];analysis?:ProductAnalysis;busy:boolean;readOnly?:boolean;groupDecision?:string}>();
const emit=defineEmits<{group:[ids:string[]];keep:[]}>();
const report=computed(()=>props.analysis?.selection_analysis),preparation=computed(()=>props.analysis?.preparation);
const coverage=computed(()=>analysisCoverage(report.value)),highlights=computed(()=>combinationHighlights(report.value));
const groupOpen=ref(false),dataOpen=ref(false),handoffOpen=ref<'material'|'budget'>(),expanded=ref(false),expandedProducts=ref<string[]>([]),broken=ref<Record<string,boolean>>({});
const kept=computed(()=>props.groupDecision==='KEEP_CURRENT');
const rows=computed(()=>props.products.map(p=>{
  const detail=report.value?.product_analyses.find(row=>row.product_id===p.qcProductId),legacy=preparation.value?.products.find(row=>row.product_id===p.qcProductId);
  return {product_id:p.qcProductId,name:p.category?.trim()||p.name,fullName:p.name,image:p.imageUrl,report:detail,
    demand_scene:detail?.hypotheses[0]?.need_or_scene||legacy?.demand_scene||'需求与使用场景仍需确认',
    material_direction:detail?.creative_directions[0]?.display_content||legacy?.material_direction||'素材方向仍需确认',
    confirmations:detail?.confirmation_questions||legacy?.confirmations||[]};
}));
const visibleRows=computed(()=>expanded.value?rows.value:rows.value.slice(0,3));
const groups=computed(()=>{
  const supplied=report.value?.combination.proposed_groups.map(g=>({name:g.group_id,product_ids:g.product_ids,reason:g.rationale}))||preparation.value?.group_suggestion.groups||[];
  return supplied.filter(g=>g.product_ids.length&&g.product_ids.every(id=>props.products.some(p=>p.qcProductId===id)));
});
const marketMessage=computed(()=>marketOverview(report.value));
const budgetReferences=computed(()=>[...(report.value?.handoff.budget.historical_references||[]),...(report.value?.handoff.budget.platform_references||[])]);
const groupProducts=(ids:string[])=>props.products.filter(p=>ids.includes(p.qcProductId));
const names=(ids:string[])=>groupProducts(ids).map(p=>p.name).join('、');
const productMarkets=(id:string)=>report.value?.market_evidence.filter(row=>row.product_ids.includes(id))||[];
const factText=(fact:AnalysisFact)=>readableFact(fact,props.analysis?.analysis_input);
const sources=computed(()=>(props.analysis?.analysis_input?.sources||[]).filter(source=>report.value?.coverage.source_ids_used.includes(source.source_id)).map(source=>{
  const time=new Date(source.retrieved_at);
  return {...source,link:safeSourceLink(source.url),productNames:names(source.scope?.product_ids||[]),displayTime:Number.isNaN(time.getTime())?'未提供获取时间':time.toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false})};
}));
const marketLabels:Record<string,string>={competition:'同类竞争参考',search_trend:'搜索趋势参考',price_reference:'价格参考'};
const availabilityLabels:Record<string,string>={available:'可用',limited:'部分可用',unavailable:'暂不可用'};
const scopeLabels:Record<string,string>={platform:'平台',category_or_keywords:'类目或关键词',region:'地区',time_window:'时间范围',statistical_object:'统计对象',sample_boundary:'样本范围'};
function evidenceText(ids:string[]){const evidence=props.analysis?.analysis_input?.evidence||[];return [...new Set(ids.map(id=>evidence.find(row=>row.evidence_id===id)?.metric_definition||'保存的商品记录'))].join('、');}
function toggleProduct(id:string){expandedProducts.value=expandedProducts.value.includes(id)?expandedProducts.value.filter(x=>x!==id):[...expandedProducts.value,id];}
function chooseGroup(ids:string[]){if(props.busy||props.readOnly)return;groupOpen.value=false;emit('group',ids);}
function keepSelection(){if(props.busy||props.readOnly||kept.value)return;emit('keep');groupOpen.value=false;}
function closeHandoff(value:boolean){if(!value)handoffOpen.value=undefined;}
watch(()=>props.products.map(p=>p.qcProductId).join(','),()=>{expanded.value=false;expandedProducts.value=[];groupOpen.value=false;handoffOpen.value=undefined;});
</script>
<style scoped src="./product-preparation.css"></style>
