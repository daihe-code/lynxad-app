<template>
  <section :key="plan.planId" class="completed-result" aria-label="已完成计划的投放结果">
    <header class="completed-heading"><span class="completed-icon" aria-hidden="true"><ReferenceIcon name="check-done" /></span><h2>投放已完成</h2><p>广告计划已结束投放，以下为本次投放的数据汇总。</p><span class="completed-badge"><ReferenceIcon name="check-done" />投放结束 · 历史数据</span></header>
    <details class="completed-fold" open>
      <summary><h3>最终投放数据汇总</h3><span class="completed-brief">{{ period.range[0] }} 至 {{ period.range[1] }}</span><ReferenceIcon class="completed-caret" name="caret" /></summary>
      <div class="completed-body">
        <div class="completed-metrics" :aria-busy="loading"><article v-for="card in cards" :key="card.label" :class="{positive:card.positive,warning:card.warning}"><h4><ReferenceIcon :name="card.icon" />{{ card.label }}</h4><p class="completed-value" :class="{ 'is-material':card.material }" :title="card.material ? card.value : undefined">{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></p><p class="completed-note">{{ card.note }}</p></article></div>
        <p class="completed-muted">统计区间按北京时间日期汇总，平台报表可能继续回补。{{ !period.endKnown ? '未取得有效的投放结束日期，统计查询截至本次读取日期。' : '' }}{{ !period.cumulative ? '当前仅展示该区间内的数据，不代表全部投放累计。' : '' }}</p>
        <p v-if="statistics" class="completed-muted">报表读取时间：{{ reviewTime(statistics.fetchedAt) }}</p>
        <p v-if="statisticsError" class="completed-error" role="status">{{ statisticsError }}</p><p v-if="statistics?.warning" class="completed-error" role="status">{{ statistics.warning }}</p><p v-if="materialsError" class="completed-error" role="status">{{ materialsError }}</p>
        <button class="completed-read" type="button" :disabled="loading" @click="read(true)"><ReferenceIcon name="refresh" />{{ loading ? '正在读取…' : '刷新数据与记录' }}</button>
      </div>
    </details>
    <details class="completed-fold" open>
      <summary><h3>投放期间的自动操作</h3><span class="completed-brief">按当前统计区间汇总</span><ReferenceIcon class="completed-caret" name="caret" /></summary>
      <div class="completed-body"><div class="completed-operations"><article v-for="item in completedAutomationKinds" :key="item.kind"><p>{{ automation.counts?.[item.kind] ?? '—' }}<small v-if="automation.counts">次</small></p><h4>{{ item.label }}</h4><span>{{ item.note }}</span></article></div><p class="completed-muted">仅统计已确认生效的自动操作，未执行的建议和结果待确认的操作不计入次数。</p><p v-if="automation.timeIncomplete" class="completed-error" role="status">部分自动操作缺少有效执行时间，暂无法准确汇总本次投放期间的次数。</p><p v-if="automationError" class="completed-error" role="status">{{ automationError }}</p><router-link class="completed-history" :to="{path:'/monitoring',query:{planId:plan.planId,tab:'history'}}">查看自动调控记录 →</router-link></div>
    </details>
    <PlanResultSummary :plan="plan" :aweme-name="awemeName" />
    <div class="completed-links"><PlanListButton /></div>
  </section>
</template>

<script setup lang="ts">
import { computed,ref,watch,nextTick,onUnmounted } from 'vue';
import { syncPlanDetail,type PlanVo,type PlanListVo } from '@/features/delivery/deliveryApi';
import { insightData,insightMaterials,type InsightStatistics,type PlanMaterial } from '@/features/delivery/planInsightsApi';
import { getOptimizationHistory,type OptimizationEvent } from '@/features/delivery/optimizationApi';
import { loadPauseAutomation } from '@/features/delivery/planPausedResult';
import { hasPlanCompletedResult,completedStatisticsRange,completedMetricCards,completedAutomationCounts,completedAutomationKinds } from '@/features/delivery/planCompletedResult';
import { reviewTime } from '@/features/delivery/planReview';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import PlanListButton from './PlanListButton.vue';
import PlanResultSummary from './PlanResultSummary.vue';
const props = defineProps<{plan:PlanListVo;awemeName?:string}>();
const emit = defineEmits<{updated:[plan:PlanVo]}>();
const statistics = ref<InsightStatistics>(),materials = ref<PlanMaterial[]>(),events = ref<OptimizationEvent[]>(),readAt = ref(Date.now());
const loading = ref(false),statisticsError = ref(''),materialsError = ref(''),automationError = ref('');
const period = computed(()=>completedStatisticsRange(props.plan,readAt.value));
const cards = computed(()=>completedMetricCards(props.plan,statistics.value?.summary || {},period.value,materials.value));
const automation = computed(()=>completedAutomationCounts(props.plan,events.value,period.value.range,readAt.value));
let context = 0,controller:AbortController|undefined,rangeKey = '';
async function read(force = false) {
  if (loading.value) return;
  const id = props.plan.planId,turn = context;
  controller = new AbortController();const signal = controller.signal;
  loading.value = true;statisticsError.value = '';materialsError.value = '';automationError.value = '';readAt.value = Date.now();
  if (force) {
    try { const status = await syncPlanDetail(id,true);if (turn !== context || signal.aborted) return;
      if (!hasPlanCompletedResult(status)) {loading.value = false;emit('updated',status);return;}
      emit('updated',status);await nextTick();if (turn !== context || signal.aborted) return;
    } catch {if (turn !== context || signal.aborted) return;statisticsError.value = '计划状态暂未刷新，当前仍展示上次确认的状态。';}
  }
  const range = period.value.range,key = range.join(':');
  if (key !== rangeKey) {statistics.value = undefined;materials.value = undefined;events.value = undefined;rangeKey = key;}
  const [report,history,...assets] = await Promise.allSettled([insightData(id,range,force,signal),loadPauseAutomation(id,getOptimizationHistory,signal),...['VIDEO','IMAGE','CAROUSEL'].map(type=>insightMaterials(id,range,type,signal))]);
  if (turn !== context || signal.aborted) return;
  if (report.status === 'fulfilled' && report.value?.summary && typeof report.value.summary === 'object') statistics.value = report.value;
  else statisticsError.value += `${statisticsError.value ? ' ' : ''}${statistics.value ? '本次报表刷新未完成，已保留上次读取的数据。' : '历史投放数据暂未取得，请稍后刷新。'}`;
  if (history.status === 'fulfilled' && history.value.complete) events.value = history.value.records;
  else automationError.value = events.value ? '本次自动操作记录未完整读取，当前显示上次核对的汇总。' : '自动操作记录未完整取得，暂无法汇总次数，请稍后刷新或查看自动调控记录。';
  if (assets.every(result=>result.status === 'fulfilled' && Array.isArray(result.value?.records))) materials.value = assets.flatMap(result=>result.status === 'fulfilled' ? result.value.records : []);
  else materialsError.value = materials.value ? '本次素材效果未完整刷新，当前素材比较保留上次读取的结果。' : '素材效果数据未完整取得，暂无法比较表现最好的素材。';
  loading.value = false;
}
watch(()=>`${props.plan.planId}:${props.plan.qcPlanId}`,()=>{
  context++;controller?.abort();statistics.value = undefined;materials.value = undefined;events.value = undefined;rangeKey = '';loading.value = false;statisticsError.value = '';materialsError.value = '';automationError.value = '';void read();
},{immediate:true});
onUnmounted(()=>{context++;controller?.abort();});
</script>

<style scoped>
.completed-result{display:grid;gap:26px;padding:12px 0 40px;color:#20213b}.completed-heading{text-align:center;margin-bottom:6px}.completed-icon{display:grid;place-items:center;width:56px;height:56px;margin:0 auto 14px;border-radius:50%;color:#17b77e;background:#e4f8ee}.completed-icon svg{width:25px;height:25px}.completed-heading h2{margin:0 0 8px;font-size:22px;line-height:1.5;font-weight:700}.completed-heading p{margin:3px 0;color:#81849f;font-size:14px;line-height:1.8}.completed-badge{display:inline-flex;align-items:center;gap:7px;margin-top:14px;padding:7px 14px;border-radius:20px;color:#17a673;background:#e4f8ee;font-size:12px}.completed-badge svg{width:14px;height:14px}
.completed-fold{border:1px solid #e9eaf4;border-radius:22px;background:#fff;box-shadow:0 4px 16px #262c6b05}.completed-fold summary{display:flex;align-items:center;gap:14px;min-height:68px;box-sizing:border-box;padding:22px 32px;list-style:none;cursor:pointer}.completed-fold summary::-webkit-details-marker{display:none}.completed-fold h3{flex:none;margin:0;padding-left:13px;border-left:4px solid #5b55ff;font-size:17px;line-height:22px;font-weight:650}.completed-fold:not([open]) h3{border-color:#c9c9d6}.completed-brief{margin-left:auto;text-align:right;color:#81849f;font-size:12px;line-height:1.8}.completed-caret{flex:none;width:17px;height:17px;color:#81849f}.completed-fold[open] .completed-caret{transform:rotate(180deg)}.completed-body{padding:4px 32px 30px}.completed-fold summary:focus-visible,.completed-result button:focus-visible,.completed-result a:focus-visible{outline:2px solid #575cf6;outline-offset:3px;border-radius:8px}
.completed-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.completed-metrics article{min-width:0;padding:20px 18px;border:1px solid #e8eaf4;border-radius:14px}.completed-metrics .positive{border-color:#c8ecde;background:#f5fdf9}.completed-metrics .warning{border-color:#f4e3c6;background:#fffbf3}.completed-metrics h4{display:flex;align-items:center;gap:6px;margin:0;color:#81849f;font-size:12px;font-weight:400;line-height:1.8}.completed-metrics h4 svg{flex:none;width:14px;height:14px}.completed-value{margin:8px 0;font-size:26px;line-height:1.4;font-weight:700;overflow-wrap:anywhere}.completed-value small{margin-left:4px;font-size:12px;font-weight:400;color:#81849f}.completed-value.is-material{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;font-size:17px;line-height:1.8}.completed-note{margin:0;color:#81849f;font-size:12px;line-height:1.9}.positive .completed-note{color:#499675}.warning .completed-note{color:#ac7832}
.completed-muted{margin:16px 0 0;color:#81849f;font-size:13px;line-height:1.9;overflow-wrap:anywhere}.completed-error{margin:12px 0 0;color:#ac7834;font-size:13px;line-height:1.8;overflow-wrap:anywhere}.completed-read{display:inline-flex;align-items:center;gap:6px;padding:8px 12px;margin-top:16px;border:1px solid #e1e3ef;border-radius:8px;background:#fff;color:#656a85;font:inherit;font-size:12px;cursor:pointer}.completed-read svg{width:14px;height:14px}.completed-read:disabled{opacity:.55;cursor:default}
.completed-operations{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.completed-operations article{min-width:0;padding:18px 16px;border:1px solid #dcd9ff;border-radius:14px;background:#f9f8ff}.completed-operations article>p{margin:0 0 6px;color:#574cf4;font-size:28px;font-weight:650;line-height:1.5}.completed-operations small{margin-left:4px;color:#81849f;font-size:12px;font-weight:400}.completed-operations h4{margin:0 0 6px;font-size:14px;font-weight:600;line-height:1.8}.completed-operations article>span{color:#81849f;font-size:12px;line-height:1.8}.completed-history{display:inline-block;margin-top:16px;color:#5b55ff;font-size:13px;text-decoration:none;line-height:1.8}.completed-links{text-align:center}
@media(max-width:1000px){.completed-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:767px){.completed-heading p{font-size:13px}.completed-fold summary{padding:18px;gap:10px;flex-wrap:wrap}.completed-fold h3{flex:1}.completed-brief{order:3;width:100%;text-align:left;margin-left:17px}.completed-body{padding:0 18px 24px}.completed-metrics{gap:12px}.completed-metrics article{padding:16px 12px}.completed-value{font-size:22px}.completed-value.is-material{font-size:14px}.completed-operations{grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.completed-operations article{padding:16px 12px}.completed-operations h4{font-size:13px}}
@media(max-width:420px){.completed-metrics{grid-template-columns:1fr}}
</style>
