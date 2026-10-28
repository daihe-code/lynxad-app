<template>
  <section class="delivery-result" aria-label="审核通过后的投放结果">
    <header class="delivery-result__heading">
      <span class="delivery-result__icon" aria-hidden="true"><ReferenceIcon :name="scheduled ? 'check-clock' : 'play'" /></span>
      <h2>{{ scheduled ? '审核通过，待投放' : '投放中' }}</h2>
      <p>{{ scheduled ? `广告计划已通过审核，${plan.startDate ? `计划开始日期为 ${plan.startDate}` : '等待平台确认开始时间'}。` : '广告计划正在投放，数据随平台报表同步更新。' }}</p>
    </header>
    <section class="delivery-data" aria-labelledby="delivery-data-title">
      <header class="delivery-data__heading"><h3 id="delivery-data-title">核心投放数据</h3><div class="delivery-data__tools"><span>统计日期 {{ statisticsDay }}（北京时间）</span><button type="button" :disabled="loading || busy" @click="read(true)"><ReferenceIcon name="refresh" />{{ loading ? '刷新中…' : '刷新数据' }}</button></div></header>
      <div class="delivery-data__cards" :aria-busy="loading">
        <article v-for="card in cards" :key="card.label" :class="{ 'delivery-data__card--warning': card.warning }"><h4><ReferenceIcon :name="card.icon" />{{ card.label }}</h4><p class="delivery-data__value">{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></p><p class="delivery-data__note">{{ card.note }}</p></article>
      </div>
      <p v-if="error" class="delivery-data__error" role="status">{{ error }}</p>
      <p v-if="statistics?.warning" class="delivery-data__warning" role="status">{{ statistics.warning }}</p>
      <p class="delivery-data__footnote">{{ statistics ? `最近读取 ${reviewTime(statistics.fetchedAt, '时间待同步')}。` : loading ? '正在读取当日平台报表。' : '暂未取得当日报表。' }}数据可能存在延迟，未返回的指标显示 —。</p>
    </section>
    <PlanResultSummary :plan="plan" :aweme-name="awemeName" />
    <footer v-if="showFooter !== false" class="delivery-result__footer"><button v-if="canPause" type="button" class="delivery-result__pause" :disabled="busy || loading" @click="pauseOpen = true; pauseError = ''"><ReferenceIcon name="pause" />暂停投放</button><router-link to="/campaigns">查看所有投放计划</router-link></footer>
    <CampaignPlanActionDialog v-model="pauseOpen" :plan="plan" action="pause" :busy="busy" :error="pauseError" @confirm="pause" />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue';
import { actPlan, syncPlanDetail, type PlanVo, type PlanListVo } from '@/features/delivery/deliveryApi';
import { insightData, type InsightStatistics } from '@/features/delivery/planInsightsApi';
import { deliveryMetricCards } from '@/features/delivery/planDeliveryResult';
import { isPlanPauseReason, type PlanPauseReason } from '@/features/delivery/planPause';
import { reviewTime } from '@/features/delivery/planReview';
import { shanghaiToday } from '@/features/delivery/statisticsRange';
import { useUserStore } from '@/stores/user';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import PlanResultSummary from './PlanResultSummary.vue';
import CampaignPlanActionDialog from './CampaignPlanActionDialog.vue';
const props = defineProps<{ plan: PlanListVo; awemeName?: string; readOnly?: boolean; showFooter?: boolean }>();
const emit = defineEmits<{ updated: [plan: PlanVo] }>();
const user = useUserStore();
const scheduled = computed(() => props.plan.status === 'SCHEDULED');
const canPause = computed(() => !props.readOnly && user.trialActive && props.plan.status === 'DELIVERING' && !!props.plan.qcPlanId && props.plan.source !== 'EXTERNAL' && props.plan.canControl !== false);
const statistics = ref<InsightStatistics>(), statisticsDay = ref(shanghaiToday()), loading = ref(false), error = ref('');
const cards = computed(() => deliveryMetricCards(props.plan, statistics.value?.summary));
const pauseOpen = ref(false), busy = ref(false), pauseError = ref('');
let context = 0, controller: AbortController | undefined, timer: ReturnType<typeof setInterval> | undefined;
async function read(force = false) {
  if (loading.value || busy.value) return;
  const id = props.plan.planId, turn = context, day = shanghaiToday();
  if (statisticsDay.value !== day) { statistics.value = undefined; statisticsDay.value = day; }
  loading.value = true; error.value = ''; controller = new AbortController();
  const signal = controller.signal;
  const [report, status] = await Promise.allSettled([insightData(id, [day, day], force, signal), force ? syncPlanDetail(id, true) : Promise.resolve(null)]);
  if (turn !== context || signal.aborted) return;
  if (report.status === 'fulfilled' && report.value?.summary && typeof report.value.summary === 'object') statistics.value = report.value;
  else error.value = statistics.value ? '本次刷新未完成，已保留上次读取的数据，请稍后重试。' : '当日投放数据暂未取得，请稍后点击刷新数据。';
  loading.value = false;
  if (status.status === 'fulfilled' && status.value) emit('updated', status.value);
}
async function pause(reason?: PlanPauseReason) {
  if (!canPause.value || busy.value || loading.value || !isPlanPauseReason(reason)) return;
  const id = props.plan.planId, turn = context;
  busy.value = true; pauseError.value = '';
  try { const result = await actPlan(id, 'DISABLE', reason); if (turn === context) { pauseOpen.value = false; emit('updated', result); } }
  catch { if (turn === context) pauseError.value = '暂停未完成，请核对计划状态后重试。'; }
  finally { if (turn === context) busy.value = false; }
}
watch(() => `${props.plan.planId}:${props.plan.qcPlanId}`, () => {
  context++; controller?.abort(); statistics.value = undefined; statisticsDay.value = shanghaiToday(); loading.value = false; error.value = ''; pauseOpen.value = false; busy.value = false; pauseError.value = ''; void read();
}, { immediate: true });
onMounted(() => { timer = setInterval(() => { if (document.visibilityState === 'visible' && !busy.value && !pauseOpen.value) void read(); }, 60000); });
onUnmounted(() => { context++; controller?.abort(); clearInterval(timer); });
</script>

<style scoped>
.delivery-result{padding:12px 0 112px;color:#20213b}
.delivery-result__heading{text-align:center;margin:0 auto 32px}
.delivery-result__icon{display:grid;place-items:center;width:56px;height:56px;margin:0 auto 14px;border-radius:50%;color:#16ad75;background:#e4f8ee}
.delivery-result__icon svg{width:25px;height:25px}
.delivery-result__heading h2{margin:0 0 8px;font-size:22px;line-height:1.5;font-weight:700}
.delivery-result__heading p{margin:3px 0;color:#81849f;font-size:14px;line-height:1.8}
.delivery-data{padding:32px;margin-bottom:30px;border:1px solid #e9eaf4;border-radius:22px;background:#fff;box-shadow:0 4px 16px #262c6b05}
.delivery-data__heading{display:flex;align-items:center;justify-content:space-between;gap:20px;flex-wrap:wrap;margin-bottom:24px}
.delivery-data h3{margin:0;padding-left:13px;border-left:4px solid #5b55ff;font-size:17px;line-height:22px;font-weight:650}
.delivery-data__tools{display:flex;align-items:center;gap:12px;flex-wrap:wrap;color:#81849f;font-size:12px}
.delivery-data__tools button{display:inline-flex;align-items:center;gap:6px;padding:8px 12px;border:1px solid #e2e3ef;border-radius:8px;background:#fff;color:#53566f;font:inherit;cursor:pointer}
.delivery-data__tools svg{width:14px;height:14px}.delivery-data button:disabled{opacity:.55;cursor:default}
.delivery-data__cards{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}
.delivery-data article{min-width:0;padding:20px 18px;border:1px solid #e8eaf4;border-radius:14px;background:#fff}
.delivery-data article.delivery-data__card--warning{border-color:#f4e3c6;background:#fffbf3}
.delivery-data h4{display:flex;align-items:center;gap:6px;margin:0;color:#81849f;font-size:12px;font-weight:400;line-height:1.7}
.delivery-data h4 svg{width:14px;height:14px;flex:none}
.delivery-data__value{display:flex;align-items:baseline;gap:4px;margin:8px 0;font-size:26px;font-weight:700;line-height:1.4;overflow-wrap:anywhere}
.delivery-data__value small{font-size:12px;font-weight:400;color:#81849f}
.delivery-data__note{margin:0;font-size:12px;color:#81849f;line-height:1.8}
.delivery-data__card--warning .delivery-data__note{color:#ac7832}
.delivery-data__footnote,.delivery-data__warning,.delivery-data__error{margin:18px 0 0;color:#81849f;font-size:12px;line-height:1.9;overflow-wrap:anywhere}
.delivery-data__warning,.delivery-data__error{color:#a77229}
.delivery-result__footer{position:fixed;z-index:40;bottom:0;left:0;right:0;display:flex;align-items:center;justify-content:center;gap:24px;padding:14px 24px;border-top:1px solid #eeeff7;background:#fffffffa}
.delivery-result__footer button,.delivery-result__footer a{box-sizing:border-box;display:inline-flex;align-items:center;justify-content:center;gap:9px;min-width:182px;padding:13px 24px;border:1px solid #e0e2f1;border-radius:10px;background:#fff;color:#252842;font:inherit;font-size:14px;font-weight:600;line-height:22px;text-decoration:none;cursor:pointer}
.delivery-result__footer button.delivery-result__pause{border-color:#f6cccc;color:#e5585e}.delivery-result__footer a{background:#5b55f5;border-color:#5b55f5;color:#fff;box-shadow:0 4px 14px #5755ef22}
.delivery-result__footer svg{width:16px;height:16px}.delivery-result button:focus-visible,.delivery-result a:focus-visible{outline:2px solid #575cf6;outline-offset:3px}
@media(max-width:1000px){.delivery-data__cards{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:767px){.delivery-result__heading p{font-size:13px}.delivery-data{padding:22px 18px}.delivery-data__tools{gap:10px}.delivery-data article{padding:16px 12px}.delivery-data__value{font-size:23px}.delivery-result__footer{gap:10px;padding:12px}.delivery-result__footer button,.delivery-result__footer a{min-width:0;padding:11px 12px;font-size:13px}}
</style>
