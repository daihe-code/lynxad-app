<template>
  <section :key="plan.planId" class="paused-result" aria-label="已暂停计划的投放结果">
    <header class="paused-result__heading"><span class="paused-result__icon" aria-hidden="true"><ReferenceIcon name="pause" /></span><h2>已暂停</h2><p>广告计划已暂停投放，恢复前请核对暂停原因与投放设置。</p><span v-if="elapsed" class="paused-result__elapsed"><ReferenceIcon name="pause" />{{ elapsed }}</span></header>
    <details class="paused-fold paused-reason" open>
      <summary><h3>暂停原因</h3><span class="paused-fold__brief">{{ pauseRecord?.automatic ? '系统自动暂停 · 无需选择原因' : pauseRecord?.manual ? pauseRecord.reason || '历史记录未填写具体原因' : pauseRecord ? '平台异常暂停' : '暂停原因待核对' }}</span><ReferenceIcon class="paused-fold__caret" name="caret" /></summary>
      <div class="paused-fold__body">
        <article v-if="pauseRecord?.automatic" class="paused-automatic" aria-label="自动暂停触发原因">
          <span class="paused-automatic__icon" aria-hidden="true"><ReferenceIcon name="pause" /></span>
          <div><h4>{{ pauseRecord.reason }}</h4><p v-if="pauseRecord.action.automation?.evidence">{{ pauseRecord.action.automation.evidence }}</p><p>灵投已自动暂停此计划，千川回查已确认生效。</p><p>确认生效时间：{{ reviewTime(pauseRecord.action.snapshotTs) }}</p><p>{{ automaticPauseAdvice(pauseRecord.action.automation?.cause || '') }}</p><router-link :to="{ path:'/monitoring', query:{ planId:plan.planId, tab:'history' } }">查看自动调控记录 →</router-link></div>
        </article>
        <template v-if="pauseRecord?.manual"><ul class="pause-reason-options" aria-label="已记录的暂停原因（只读）"><li v-for="reason in PLAN_PAUSE_REASONS" :key="reason" :class="{ selected: pauseRecord.selected === reason }"><i aria-hidden="true" /><span>{{ reason }}</span><span v-if="pauseRecord.selected === reason" class="sr-only">（已记录）</span></li></ul><p class="paused-muted">已记录原因：<strong>{{ pauseRecord.reason || '历史记录未填写具体原因' }}</strong></p></template>
        <p v-else-if="!pauseRecord?.automatic" class="paused-muted">{{ pauseRecord?.reason || (actionsLoading ? '正在读取本计划的暂停记录…' : '尚未取得本次暂停原因，历史暂停原因不会自动作为本次原因。') }}</p>
        <p v-if="!pauseRecord?.automatic" class="paused-muted">{{ pauseRecord ? `系统记录的暂停时间：${reviewTime(pauseRecord.action.snapshotTs)}` : '暂停时间尚未记录。' }}</p>
        <p v-if="actionsError" class="paused-error" role="status">{{ actionsError }}</p>
        <router-link v-if="actionsError && !pauseRecord?.automatic" class="paused-records-link" :to="{ path:'/monitoring', query:{ planId:plan.planId, tab:'history' } }">查看自动调控记录 →</router-link>
        <button class="paused-read" type="button" :disabled="loading || busy" @click="read(true)"><ReferenceIcon name="refresh" />{{ loading ? '正在读取…' : '刷新记录与数据' }}</button>
      </div>
    </details>
    <details class="paused-fold paused-performance" open>
      <summary><h3>暂停前的投放表现</h3><span class="paused-fold__brief">{{ period ? `${period.range[0]} 至 ${period.range[1]}` : '统计区间待确认' }}</span><ReferenceIcon class="paused-fold__caret" name="caret" /></summary>
      <div class="paused-fold__body">
        <div class="paused-metrics" :aria-busy="loading"><article v-for="card in cards" :key="card.label" :class="{ warning:card.warning }"><h4><ReferenceIcon :name="card.icon" />{{ card.label }}</h4><p>{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></p><span>{{ card.note }}</span></article></div>
        <p v-if="!period" class="paused-muted">未取得本次暂停时间，暂无法确定暂停前的统计区间。</p>
        <template v-else><p class="paused-muted">统计范围截至暂停当日（按日期汇总），平台可能延迟回补数据。{{ period.clipped ? '当前仅展示截至暂停当日最近 180 天的数据。' : !period.startKnown ? '投放开始日期未记录，当前仅展示暂停当日数据。' : '' }}</p><p class="paused-muted">{{ statistics ? `报表读取时间：${reviewTime(statistics.fetchedAt)}` : loading ? '正在读取平台报表…' : '尚未取得此区间的投放数据。' }}</p></template>
        <p v-if="statistics?.warning" class="paused-error" role="status">{{ statistics.warning }}</p><p v-if="statisticsError" class="paused-error" role="status">{{ statisticsError }}</p>
        <p class="paused-muted">恢复投放前，建议先处理暂停原因，并核对预算、目标和投放时段。</p>
      </div>
    </details>
    <details class="paused-fold paused-history">
      <summary><h3>状态流转记录</h3><span class="paused-fold__brief">{{ history.length + (createdAtKnown ? 1 : 0) }} 条已记录事件</span><ReferenceIcon class="paused-fold__caret" name="caret" /></summary>
      <div class="paused-fold__body"><ol class="paused-timeline"><li v-if="createdAtKnown"><h4>计划创建 <time>{{ reviewTime(plan.createdAt) }}</time></h4><p>{{ plan.name }}</p></li><li v-for="action in history" :key="action.id"><h4>{{ pauseActionLabel(action.type) }} <time>{{ reviewTime(action.snapshotTs) }}</time></h4><p v-if="action.summary && action.summary !== action.type">{{ action.summary }}</p></li></ol><p v-if="!history.length && !createdAtKnown" class="paused-muted">暂无已记录的状态流转事件。</p><p v-if="actionsError" class="paused-error" role="status">{{ actionsError }}</p><p class="paused-current">当前状态：已暂停</p></div>
    </details>
    <PlanResultSummary :plan="plan" :aweme-name="awemeName" />
    <footer class="paused-result__footer"><div><button v-if="canRestore" type="button" class="paused-result__restore" :disabled="busy || loading" @click="restoreOpen = true; actionError = ''"><ReferenceIcon name="play" />恢复投放</button><router-link to="/campaigns">查看所有投放计划</router-link></div></footer>
    <CampaignPlanActionDialog v-model="restoreOpen" :plan="plan" action="restore" :busy="busy" :error="actionError" :pause-origin="pauseRecord?.automatic ? 'automatic' : pauseRecord?.manual ? 'manual' : pauseRecord ? 'platform' : 'unknown'" @confirm="restore" />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted, onUnmounted } from 'vue';
import { listActions } from '@/features/agentops/agentopsApi';
import { getOptimizationHistory } from '@/features/delivery/optimizationApi';
import { actPlan, syncPlanDetail, type PlanVo, type PlanListVo } from '@/features/delivery/deliveryApi';
import { insightData, type InsightStatistics } from '@/features/delivery/planInsightsApi';
import { currentPause, pauseElapsed, pauseHistory, pauseStatisticsRange, pauseTimestamp, pausedMetricCards, pauseActionLabel, automaticPauseActions, automaticPauseAdvice, loadPauseAutomation, type PauseAction } from '@/features/delivery/planPausedResult';
import { PLAN_PAUSE_REASONS } from '@/features/delivery/planPause';
import { reviewTime } from '@/features/delivery/planReview';
import { requestFailure } from '@/api/apiErrors';
import { useUserStore } from '@/stores/user';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import PlanResultSummary from './PlanResultSummary.vue';
import CampaignPlanActionDialog from './CampaignPlanActionDialog.vue';
const props = defineProps<{ plan: PlanListVo; awemeName?: string }>();
const emit = defineEmits<{ updated: [plan: PlanVo] }>();
const user = useUserStore(), actions = ref<PauseAction[]>([]), now = ref(Date.now());
const history = computed(() => pauseHistory(props.plan,actions.value));
const pauseRecord = computed(() => currentPause(props.plan,actions.value,now.value));
const elapsed = computed(() => pauseElapsed(pauseRecord.value?.timestamp ?? NaN,now.value));
const period = computed(() => pauseStatisticsRange(props.plan,pauseRecord.value?.timestamp ?? NaN,now.value));
const createdAtKnown = computed(() => Number.isFinite(pauseTimestamp(props.plan.createdAt)));
const statistics = ref<InsightStatistics>(), cards = computed(() => pausedMetricCards(props.plan,statistics.value?.summary));
const loading = ref(false), actionsLoading = ref(false), actionsError = ref(''), statisticsError = ref('');
const restoreOpen = ref(false), busy = ref(false), actionError = ref('');
const canRestore = computed(() => user.trialActive && props.plan.status === 'PAUSED' && !!props.plan.qcPlanId && props.plan.source !== 'EXTERNAL' && props.plan.canControl !== false);
let context = 0, controller: AbortController | undefined, statisticsKey = '', timer: ReturnType<typeof setInterval> | undefined;
async function read(force = false) {
  if (loading.value || busy.value) return;
  const id = props.plan.planId, turn = context;
  controller = new AbortController(); const signal = controller.signal;
  loading.value = true; actionsLoading.value = true; actionsError.value = ''; statisticsError.value = '';
  const [events,automation,status] = await Promise.allSettled([listActions(signal),loadPauseAutomation(id,getOptimizationHistory,signal),force ? syncPlanDetail(id,true) : Promise.resolve(null)]);
  if (turn !== context || signal.aborted) return;
  actionsLoading.value = false;
  const recordsComplete = events.status === 'fulfilled' && Array.isArray(events.value) && automation.status === 'fulfilled' && automation.value.complete;
  if (recordsComplete && events.status === 'fulfilled' && automation.status === 'fulfilled') actions.value = [...events.value.filter(action=>action?.planId === id),...automaticPauseActions(props.plan,automation.value.records)];
  else actionsError.value = actions.value.length ? '本次暂停记录未完整读取，当前显示上次核对的记录，请稍后刷新或前往自动调控记录核查。' : '本次暂停记录未完整读取，暂不能确认当前暂停原因，请稍后刷新或前往自动调控记录核查。';
  if (status.status === 'fulfilled' && status.value) {
    if (status.value.status !== 'PAUSED') { loading.value = false; emit('updated',status.value); return; }
    emit('updated',status.value);
    await nextTick();
    if (turn !== context || signal.aborted) return;
  }
  if (status.status === 'rejected') actionsError.value += `${actionsError.value ? ' ' : ''}计划状态暂未刷新，请稍后重试。`;
  const range = period.value;
  const key = range ? `${pauseRecord.value?.action.id}:${range.range.join(':')}` : '';
  if (key !== statisticsKey) { statistics.value = undefined; statisticsKey = key; }
  if (recordsComplete && range) {
    try { const result = await insightData(id,range.range,force,signal); if (turn === context && !signal.aborted && result?.summary && typeof result.summary === 'object') statistics.value = result; }
    catch { if (turn === context && !signal.aborted) statisticsError.value = statistics.value ? '本次报表刷新未完成，已保留上次读取的数据。' : '暂停前的投放数据暂未取得，请稍后刷新。'; }
  }
  if (turn === context && !signal.aborted) loading.value = false;
}
async function restore() {
  if (!canRestore.value || busy.value || loading.value) return;
  const id = props.plan.planId, turn = context;
  busy.value = true; actionError.value = '';
  try { const result = await actPlan(id,'ENABLE'); if (turn === context) { restoreOpen.value = false; emit('updated',result); } }
  catch (error) { if (turn === context) actionError.value = requestFailure(error,{method:'POST'}).message; }
  finally { if (turn === context) busy.value = false; }
}
watch(() => `${props.plan.planId}:${props.plan.qcPlanId}`, () => {
  context++; controller?.abort(); actions.value = []; statistics.value = undefined; statisticsKey = ''; loading.value = false; actionsLoading.value = false; actionsError.value = ''; statisticsError.value = ''; restoreOpen.value = false; busy.value = false; actionError.value = ''; void read();
}, {immediate:true});
onMounted(() => { timer = setInterval(() => { now.value = Date.now(); },30000); });
onUnmounted(() => { context++; controller?.abort(); clearInterval(timer); });
</script>

<style scoped>
.paused-result{display:grid;gap:26px;padding:12px 0 168px;color:#20213b}
.paused-result__heading{text-align:center;margin-bottom:6px}.paused-result__icon{display:grid;place-items:center;width:56px;height:56px;margin:0 auto 14px;border-radius:50%;background:#fff3df;color:#f3a31c}.paused-result__icon svg{width:25px;height:25px}.paused-result__heading h2{margin:0 0 8px;font-size:22px;font-weight:700;line-height:1.5}.paused-result__heading p{margin:3px 0;color:#81849f;font-size:14px;line-height:1.8}.paused-result__elapsed{display:inline-flex;align-items:center;gap:7px;margin-top:14px;padding:7px 14px;border-radius:20px;background:#fff3df;color:#c5821d;font-size:12px}.paused-result__elapsed svg{width:14px;height:14px}
.paused-fold{border:1px solid #e9eaf4;border-radius:22px;background:#fff;box-shadow:0 4px 16px #262c6b05}.paused-fold summary{display:flex;align-items:center;gap:14px;padding:22px 32px;min-height:68px;box-sizing:border-box;cursor:pointer;list-style:none}.paused-fold summary::-webkit-details-marker{display:none}.paused-fold summary:focus-visible{outline:2px solid #575cf6;outline-offset:3px;border-radius:22px}.paused-fold h3{flex:none;margin:0;padding-left:13px;border-left:4px solid #5b55ff;font-size:17px;font-weight:650;line-height:22px}.paused-fold:not([open]) h3{border-color:#c9c9d6}.paused-fold__brief{margin-left:auto;color:#81849f;font-size:12px;text-align:right;line-height:1.8}.paused-fold__caret{width:17px;height:17px;flex:none;color:#81849f}.paused-fold[open] .paused-fold__caret{transform:rotate(180deg)}.paused-fold__body{padding:4px 32px 30px}
.pause-reason-options{display:flex;flex-wrap:wrap;gap:12px 10px;margin:0 0 20px;padding:0;list-style:none}.pause-reason-options li{display:flex;align-items:center;gap:8px;padding:9px 14px;border:1px solid #e2e3ef;border-radius:24px;color:#696d86;font-size:13px;line-height:20px}.pause-reason-options i{width:13px;height:13px;border:1px solid #c9ccdc;border-radius:50%;box-sizing:border-box;flex:none}.pause-reason-options .selected{color:#5b55ff;background:#f0eeff;border-color:#7067ff;font-weight:600}.pause-reason-options .selected i{border:4px solid #655bff}
.paused-automatic{display:flex;align-items:flex-start;gap:14px;padding:22px 24px;border:1px solid #f4dfba;border-radius:14px;background:#fffbf2}.paused-automatic__icon{display:grid;place-items:center;flex:none;width:30px;height:30px;border-radius:50%;background:#ffedcd;color:#d99a28}.paused-automatic__icon svg{width:16px;height:16px}.paused-automatic>div{min-width:0}.paused-automatic h4{margin:2px 0 10px;font-size:15px;line-height:1.7}.paused-automatic p{margin:6px 0;color:#7f7b88;font-size:13px;line-height:1.9;overflow-wrap:anywhere}.paused-automatic a{display:inline-block;margin-top:10px;font-size:13px;color:#5b55ff;text-decoration:none;line-height:1.8}
.paused-records-link{display:block;width:fit-content;margin-top:10px;color:#5b55ff;font-size:13px;text-decoration:none;line-height:1.8}
.paused-muted{margin:12px 0 0;color:#81849f;font-size:13px;line-height:1.9;overflow-wrap:anywhere}.paused-muted strong{font-weight:600}.paused-error{margin:12px 0 0;color:#ac7834;font-size:13px;line-height:1.8;overflow-wrap:anywhere}.paused-read{display:inline-flex;align-items:center;gap:6px;padding:8px 12px;margin-top:16px;border:1px solid #e1e3ef;border-radius:8px;background:#fff;color:#656a85;font:inherit;font-size:12px;cursor:pointer}.paused-read svg{width:14px;height:14px}
.paused-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.paused-metrics article{min-width:0;padding:20px 16px;border:1px solid #e8eaf4;border-radius:14px}.paused-metrics .warning{background:#fffbf3;border-color:#f4e3c6}.paused-metrics h4{display:flex;align-items:center;gap:6px;margin:0;font-size:12px;font-weight:400;color:#81849f;line-height:1.8}.paused-metrics h4 svg{width:14px;height:14px;flex:none}.paused-metrics article>p{margin:8px 0;font-size:26px;font-weight:700;line-height:1.4;overflow-wrap:anywhere}.paused-metrics small{margin-left:4px;font-size:12px;font-weight:400;color:#81849f}.paused-metrics article>span{font-size:12px;color:#81849f;line-height:1.9}.paused-metrics .warning>span{color:#ac7832}
.paused-timeline{list-style:none;padding:0 0 0 8px;margin:0}.paused-timeline li{position:relative;padding:0 0 24px 24px;border-left:2px solid #efeff7}.paused-timeline li::before{position:absolute;content:'';left:-7px;top:3px;width:12px;height:12px;border-radius:50%;background:#655bff}.paused-timeline li:last-child{border-color:transparent;padding-bottom:8px}.paused-timeline h4{display:flex;align-items:baseline;flex-wrap:wrap;gap:12px;margin:0;font-size:14px;font-weight:600;line-height:1.8}.paused-timeline time{font-size:12px;font-weight:400;color:#81849f}.paused-timeline p{margin:6px 0 0;color:#81849f;font-size:13px;line-height:1.8;overflow-wrap:anywhere}.paused-current{margin:12px 0 0;padding:10px 14px;border-radius:10px;background:#fff8ed;color:#a87324;font-size:13px}
.paused-result__footer{position:fixed;z-index:40;bottom:0;left:0;right:0;display:grid;justify-items:center;gap:8px;padding:14px 24px;border-top:1px solid #eeeff7;background:#fffffffa}.paused-result__footer>div{display:flex;justify-content:center;gap:16px}.paused-result__footer button,.paused-result__footer a{display:inline-flex;align-items:center;justify-content:center;gap:8px;box-sizing:border-box;min-width:150px;padding:12px 22px;border:1px solid #e0e2f1;border-radius:10px;background:#fff;color:#525671;font:inherit;font-size:14px;font-weight:600;line-height:22px;text-decoration:none;cursor:pointer}.paused-result__footer button.paused-result__restore{background:#5b55f5;border-color:#5b55f5;color:#fff}.paused-result__footer svg{width:16px;height:16px}.paused-result button:disabled{opacity:.55;cursor:default}.paused-result button:focus-visible,.paused-result a:focus-visible{outline:2px solid #575cf6;outline-offset:3px}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
@media(max-width:1000px){.paused-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:767px){.paused-result{padding-bottom:184px}.paused-result__heading p{font-size:13px}.paused-fold summary{padding:18px;gap:10px;flex-wrap:wrap}.paused-fold h3{flex:1}.paused-fold__brief{order:3;width:100%;text-align:left;margin-left:17px}.paused-fold__body{padding:0 18px 24px}.pause-reason-options{gap:10px 8px}.pause-reason-options li{padding:7px 10px;font-size:12px}.paused-metrics{gap:12px}.paused-metrics article{padding:16px 12px}.paused-metrics article>p{font-size:23px}.paused-result__footer{padding:12px;gap:7px}.paused-result__footer>div{gap:8px;flex-wrap:wrap}.paused-result__footer button,.paused-result__footer a{min-width:0;padding:10px 12px;font-size:13px}}
</style>
