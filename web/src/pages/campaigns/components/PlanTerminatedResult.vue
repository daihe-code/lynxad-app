<template>
  <section :key="plan.planId" class="terminated-result" aria-label="已终止计划的投放结果">
    <header class="terminated-heading"><span class="terminated-icon" aria-hidden="true"><ReferenceIcon name="stop" /></span><h2>已终止</h2><p>广告计划已终止，不再继续投放，历史数据可继续查看。</p><span class="terminated-badge"><ReferenceIcon name="stop" />终态 · 不可恢复</span></header>
    <details class="terminated-fold" open>
      <summary><h3>终止信息</h3><span class="terminated-brief">千川状态：已终止</span><ReferenceIcon class="terminated-caret" name="caret" /></summary>
      <div class="terminated-body">
        <div class="terminated-reason"><span>终止原因</span><strong>{{ record?.reason || (loading ? '正在读取终止记录…' : '尚未记录具体终止原因') }}</strong></div>
        <dl class="terminated-facts" :aria-busy="loading">
          <div><dt>终止时间</dt><dd>{{ reviewTime(record?.action.snapshotTs) }}</dd></div>
          <div><dt>计划累计投放</dt><dd class="terminated-unknown">时长尚未记录</dd></div>
          <div><dt>{{ period.cumulative ? '累计消耗' : '区间消耗' }}</dt><dd>{{ metrics.cost }}</dd></div>
          <div><dt>{{ period.cumulative ? '累计成交' : '区间成交' }}</dt><dd>{{ metrics.orders }}<small v-if="metrics.orders !== '—'">单</small></dd></div>
        </dl>
        <p v-if="pauseDuration" class="terminated-muted">{{ pauseDuration }}</p>
        <p class="terminated-muted">报表统计：{{ period.range[0] }} 至 {{ period.range[1] }}（北京时间，按日期汇总）。{{ !period.endKnown ? '终止时间尚未记录，统计查询截至本次读取日期。' : '' }}{{ period.clipped || !period.startKnown ? '当前仅展示该区间内的数据，不代表全部投放累计。' : '' }}</p>
        <p class="terminated-muted">历史投放数据可继续查看，当前单次查询最长 180 天；平台可能延迟回补数据。</p>
        <p v-if="statistics" class="terminated-muted">报表读取时间：{{ reviewTime(statistics.fetchedAt) }}</p>
        <p v-if="recordsError" class="terminated-error" role="status">{{ recordsError }}</p><p v-if="statisticsError" class="terminated-error" role="status">{{ statisticsError }}</p><p v-if="statistics?.warning" class="terminated-error" role="status">{{ statistics.warning }}</p>
        <button class="terminated-read" type="button" :disabled="loading" @click="read(true)"><ReferenceIcon name="refresh" />{{ loading ? '正在读取…' : '刷新记录与数据' }}</button>
      </div>
    </details>
    <details class="terminated-fold">
      <summary><h3>状态流转记录</h3><span class="terminated-brief">{{ history.length + (createdAtKnown ? 1 : 0) }} 条已记录事件</span><ReferenceIcon class="terminated-caret" name="caret" /></summary>
      <div class="terminated-body"><ol class="terminated-timeline"><li v-if="createdAtKnown"><h4>计划创建 <time>{{ reviewTime(plan.createdAt) }}</time></h4><p>{{ plan.name }}</p></li><li v-for="action in history" :key="action.id"><h4>{{ action.type === 'MANUAL_FINISH' ? '终止计划' : pauseActionLabel(action.type) }} <time>{{ reviewTime(action.snapshotTs) }}</time></h4><p v-if="action.summary && action.summary !== action.type">{{ action.summary }}</p></li></ol><p v-if="!createdAtKnown && !history.length" class="terminated-muted">暂无已记录的状态流转事件。</p><p v-if="recordsError" class="terminated-error" role="status">{{ recordsError }}</p><p class="terminated-current">当前状态：已终止，无法恢复投放。</p></div>
    </details>
    <PlanResultSummary :plan="plan" :aweme-name="awemeName" />
    <div class="terminated-links"><router-link to="/campaigns">查看所有计划</router-link></div>
  </section>
</template>

<script setup lang="ts">
import { computed,ref,watch,nextTick,onUnmounted } from 'vue';
import { listActions } from '@/features/agentops/agentopsApi';
import { getOptimizationHistory } from '@/features/delivery/optimizationApi';
import { syncPlanDetail,type PlanVo,type PlanListVo } from '@/features/delivery/deliveryApi';
import { insightData,type InsightStatistics } from '@/features/delivery/planInsightsApi';
import { loadPauseAutomation,automaticPauseActions,pauseHistory,pauseTimestamp,pauseActionLabel,type PauseAction } from '@/features/delivery/planPausedResult';
import { hasPlanTerminatedResult,terminationRecord,terminatedStatisticsRange,terminatedMetricValues,terminatedPauseDuration } from '@/features/delivery/planTerminatedResult';
import { reviewTime } from '@/features/delivery/planReview';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import PlanResultSummary from './PlanResultSummary.vue';
const props = defineProps<{plan:PlanListVo;awemeName?:string}>();
const emit = defineEmits<{updated:[plan:PlanVo]}>();
const actions = ref<PauseAction[]>([]),readAt = ref(Date.now()),statistics = ref<InsightStatistics>();
const loading = ref(false),recordsError = ref(''),statisticsError = ref('');
const history = computed(()=>pauseHistory(props.plan,actions.value));
const record = computed(()=>terminationRecord(props.plan,actions.value,readAt.value));
const period = computed(()=>terminatedStatisticsRange(props.plan,record.value?.timestamp ?? NaN,readAt.value));
const pauseDuration = computed(()=>terminatedPauseDuration(record.value?.pausedMinutes));
const metrics = computed(()=>terminatedMetricValues(statistics.value?.summary));
const createdAtKnown = computed(()=>Number.isFinite(pauseTimestamp(props.plan.createdAt)));
let context = 0,controller:AbortController|undefined,statisticsKey = '';
async function read(force = false) {
  if (loading.value) return;
  const id = props.plan.planId,turn = context;
  controller = new AbortController();const signal = controller.signal;
  loading.value = true;recordsError.value = '';statisticsError.value = '';readAt.value = Date.now();
  const [manual,automatic,status] = await Promise.allSettled([listActions(signal),loadPauseAutomation(id,getOptimizationHistory,signal),force ? syncPlanDetail(id,true) : Promise.resolve(null)]);
  if (turn !== context || signal.aborted) return;
  if (manual.status === 'fulfilled' && Array.isArray(manual.value) && automatic.status === 'fulfilled' && automatic.value.complete) actions.value = [...manual.value.filter(row=>row.planId === id),...automaticPauseActions(props.plan,automatic.value.records)];
  else recordsError.value = actions.value.length ? '本次状态记录未完整读取，当前显示上次核对的记录，请稍后刷新。' : '本次状态记录未完整读取，终止原因与时间暂未取得，请稍后刷新。';
  if (status.status === 'fulfilled' && status.value) {
    if (!hasPlanTerminatedResult(status.value)) { loading.value = false;emit('updated',status.value);return; }
    emit('updated',status.value);await nextTick();
    if (turn !== context || signal.aborted) return;
  } else if (status.status === 'rejected') recordsError.value += `${recordsError.value ? ' ' : ''}计划状态暂未刷新，请稍后重试。`;
  const range = period.value.range,key = range.join(':');
  if (key !== statisticsKey) { statistics.value = undefined;statisticsKey = key; }
  try { const result = await insightData(id,range,force,signal);if (turn === context && !signal.aborted && result?.summary && typeof result.summary === 'object') statistics.value = result; }
  catch { if (turn === context && !signal.aborted) statisticsError.value = statistics.value ? '本次报表刷新未完成，已保留上次读取的数据。' : '历史投放数据暂未取得，请稍后刷新。'; }
  if (turn === context && !signal.aborted) loading.value = false;
}
watch(()=>`${props.plan.planId}:${props.plan.qcPlanId}`,()=>{
  context++;controller?.abort();actions.value = [];statistics.value = undefined;statisticsKey = '';loading.value = false;recordsError.value = '';statisticsError.value = '';void read();
},{immediate:true});
onUnmounted(()=>{context++;controller?.abort();});
</script>

<style scoped>
.terminated-result{display:grid;gap:26px;padding:12px 0 40px;color:#20213b}.terminated-heading{text-align:center;margin-bottom:6px}.terminated-icon{display:grid;place-items:center;width:56px;height:56px;margin:0 auto 14px;border-radius:50%;background:#eff0f7;color:#959bb5}.terminated-icon svg{width:25px;height:25px}.terminated-heading h2{margin:0 0 8px;font-size:22px;line-height:1.5}.terminated-heading p{margin:3px 0;color:#81849f;font-size:14px;line-height:1.8}.terminated-badge{display:inline-flex;align-items:center;gap:7px;margin-top:14px;padding:7px 14px;border-radius:20px;background:#eff0f7;color:#81849f;font-size:12px}.terminated-badge svg{width:14px;height:14px}
.terminated-fold{border:1px solid #e9eaf4;border-radius:22px;background:#fff;box-shadow:0 4px 16px #262c6b05}.terminated-fold summary{display:flex;align-items:center;gap:14px;min-height:68px;box-sizing:border-box;padding:22px 32px;list-style:none;cursor:pointer}.terminated-fold summary::-webkit-details-marker{display:none}.terminated-fold h3{flex:none;margin:0;padding-left:13px;border-left:4px solid #5b55ff;font-size:17px;line-height:22px}.terminated-fold:not([open]) h3{border-color:#c9c9d6}.terminated-brief{margin-left:auto;text-align:right;color:#81849f;font-size:12px;line-height:1.8}.terminated-caret{flex:none;width:17px;height:17px;color:#81849f}.terminated-fold[open] .terminated-caret{transform:rotate(180deg)}.terminated-body{padding:4px 32px 30px}.terminated-fold summary:focus-visible,.terminated-result button:focus-visible,.terminated-result a:focus-visible{outline:2px solid #575cf6;outline-offset:3px;border-radius:8px}
.terminated-reason{padding:18px 20px;border:1px solid #e8eaf4;border-radius:14px;background:#fcfcff}.terminated-reason>span{display:block;color:#81849f;font-size:12px;line-height:1.8}.terminated-reason strong{display:block;margin-top:7px;font-size:15px;line-height:1.8;overflow-wrap:anywhere}.terminated-facts{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:24px 20px;margin:24px 0}.terminated-facts>div{min-width:0}.terminated-facts dt{margin-bottom:10px;color:#81849f;font-size:12px;line-height:1.8}.terminated-facts dd{margin:0;font-size:20px;font-weight:650;line-height:1.5;overflow-wrap:anywhere}.terminated-facts>div:first-child dd{font-size:15px}.terminated-facts dd.terminated-unknown{font-size:14px;font-weight:400;color:#81849f}.terminated-facts small{margin-left:5px;color:#81849f;font-size:12px;font-weight:400}.terminated-muted{margin:12px 0 0;color:#81849f;font-size:13px;line-height:1.9;overflow-wrap:anywhere}.terminated-error{margin:12px 0 0;color:#ac7834;font-size:13px;line-height:1.8;overflow-wrap:anywhere}.terminated-read{display:inline-flex;align-items:center;gap:6px;padding:8px 12px;margin-top:16px;border:1px solid #e1e3ef;border-radius:8px;background:#fff;color:#656a85;font:inherit;font-size:12px;cursor:pointer}.terminated-read svg{width:14px;height:14px}.terminated-read:disabled{opacity:.55;cursor:default}
.terminated-timeline{list-style:none;margin:0;padding:0 0 0 8px}.terminated-timeline li{position:relative;padding:0 0 24px 24px;border-left:2px solid #efeff7}.terminated-timeline li::before{position:absolute;content:'';left:-7px;top:3px;width:12px;height:12px;border-radius:50%;background:#655bff}.terminated-timeline li:last-child{border-color:transparent;padding-bottom:8px}.terminated-timeline h4{display:flex;align-items:baseline;flex-wrap:wrap;gap:12px;margin:0;font-size:14px;line-height:1.8}.terminated-timeline time{color:#81849f;font-size:12px;font-weight:400}.terminated-timeline p{margin:6px 0 0;color:#81849f;font-size:13px;line-height:1.8;overflow-wrap:anywhere}.terminated-current{margin:12px 0 0;padding:10px 14px;border-radius:10px;background:#f5f5fa;color:#81849f;font-size:13px}.terminated-links{text-align:center}.terminated-links a{font-size:14px;color:#5b55ff;text-decoration:none;line-height:1.8}
.terminated-heading h2{font-weight:700}.terminated-fold h3{font-weight:650}
@media(max-width:1000px){.terminated-facts{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:767px){.terminated-heading p{font-size:13px}.terminated-fold summary{padding:18px;gap:10px;flex-wrap:wrap}.terminated-fold h3{flex:1}.terminated-brief{order:3;width:100%;text-align:left;margin-left:17px}.terminated-body{padding:0 18px 24px}.terminated-reason{padding:16px}.terminated-facts{gap:20px 16px}.terminated-facts dd{font-size:18px}}
</style>
