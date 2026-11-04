<template>
  <article class="plan-insights" v-loading="initialLoading">
    <header class="insights-header">
      <div><p class="eyebrow">计划运营</p><h2>{{ plan?.name || '加载计划信息…' }}</h2><div class="plan-facts"><el-tag :type="plan?.status === 'DELIVERING' ? 'success' : 'info'" size="small">{{ planStatusLabel(plan?.status || '', plan?.qcStatus) }}</el-tag><span>ID：{{ plan?.qcPlanId || planId }}</span><span>{{ BID_TYPE[plan?.smartBidType || ''] || '未设置投放方式' }}</span><span>日预算 {{ yuan(plan?.budget) }}</span><span v-if="plan?.smartBidType === 'SMART_BID_CUSTOM'">ROI 目标 {{ roiText(plan.roi2Goal) }}</span></div></div>
      <el-button :loading="refreshing" aria-label="刷新计划运营数据" :icon="Refresh" @click="refresh">刷新</el-button>
    </header>
    <el-alert v-if="initialError" :title="initialError" type="error" :closable="false" show-icon />
    <div class="insights-toolbar">
      <nav class="insights-tabs" role="tablist" aria-label="计划运营模块"><button v-for="item in tabs" :key="item.id" role="tab" :aria-selected="activeTab === item.id" :class="{ active: activeTab === item.id }" @click="emit('tab', item.id)">{{ item.label }}</button></nav>
      <div class="insights-date"><el-date-picker v-model="rangeDraft" type="daterange" value-format="YYYY-MM-DD" format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" :shortcuts="shortcuts" :disabled-date="disabledDate" @calendar-change="calendarChange" @visible-change="calendarVisible" @change="changeRange" /><small>区间最长180天</small></div>
    </div>
    <section class="insights-section" v-loading="sectionLoading" :aria-label="tabLabel">
      <details v-if="activeTab === 'data' && plan" @toggle="loadPlanDetails">
        <summary>计划信息</summary>
        <p v-if="detailLoading" role="status">正在加载计划信息…</p>
        <p v-if="detailError" role="alert">{{ detailError }} <button @click="loadPlanDetails()">重试</button></p>
        <PlanDetails :plan="plan" :creation="creation" :account="previewAccount" :account-name="boundAccount?.accountName" @products="emit('tab', 'products')" />
      </details>
      <div v-if="sectionError" class="section-error" role="alert"><span>{{ sectionError }}</span><el-button @click="loadSection(true)">重新加载</el-button></div>
      <template v-if="activeTab === 'data' && !sectionError">
        <div class="section-title"><h3>数据概览</h3><span>{{ range[0] }} ～ {{ range[1] }} · 当前计划</span></div>
        <el-alert v-if="statistics?.warning" :title="statistics.warning" type="warning" :closable="false" show-icon />
        <div class="insight-metrics"><div v-for="metric in metrics" :key="metric.key" class="insight-metric"><span>{{ metric.label }}</span><strong>{{ metricValue(statistics?.summary[metric.key], metric.kind) }}</strong></div></div>
        <div class="section-title trend-title"><h3>趋势对比</h3><div class="trend-select"><el-select v-model="primary" aria-label="趋势第一指标"><el-option v-for="m in metrics.slice(0, 6)" :key="m.key" :value="m.key" :label="m.label" /></el-select><el-select v-model="secondary" aria-label="趋势第二指标"><el-option v-for="m in metrics.slice(0, 6)" :key="m.key" :value="m.key" :label="m.label" /></el-select></div></div>
        <PlanTrendChart v-if="statistics?.trend.length" :points="statistics.trend" :primary="primary" :secondary="secondary" :primary-label="metricLabel(primary)" :secondary-label="metricLabel(secondary)" />
        <el-empty v-else description="当前区间暂无可展示的趋势数据" :image-size="72" />
        <h3>详细数据</h3><el-table :data="statistics?.trend || []" stripe empty-text="平台尚未返回时间明细"><el-table-column prop="time" :label="statistics?.timeDimension === 'stat_time_hour' ? '时间（小时）' : '日期'" min-width="160" /><el-table-column v-for="m in metrics.slice(0, 6)" :key="m.key" :label="m.label" min-width="135"><template #default="{ row }">{{ metricValue(row.metrics[m.key], m.kind) }}</template></el-table-column></el-table>
        <p class="insights-note">统计仅对应当前计划 ID，暂停计划也可查询已有数据。平台未返回的指标显示「—」。<span v-if="statistics?.fetchedAt"> 查询时间：{{ timeText(statistics.fetchedAt) }}</span></p>
      </template>
      <template v-else-if="activeTab === 'products'">
        <PlanProducts v-if="plan?.qcPlanId" :plan-id="planId" :qc-plan-id="plan.qcPlanId" :range="range" :refresh-token="productRefresh" @changed="productsChanged" />
        <template v-else><el-empty description="草稿尚未发布，可在计划修改页面管理商品" /><router-link class="wizard-link" :to="`/campaigns/${planId}/edit`">进入计划修改页面 →</router-link></template>
      </template>
      <PlanMaterials v-else-if="activeTab === 'materials'" :plan-id="planId" :range="range" :refresh-token="productRefresh" :account="previewAccount" />
      <template v-else-if="activeTab === 'controls'">
        <PlanControls v-if="plan?.smartBidType === 'SMART_BID_CUSTOM'" :plan-id="planId" :range="range" :refresh-token="productRefresh" :plan-status="plan.status" :account-active="boundAccount?.status === 'ACTIVE'" :account="previewAccount" />
        <el-empty v-else description="放量投放不支持调控任务" />
      </template>
      <template v-else-if="activeTab === 'logs'">
        <div class="section-title"><h3>操作日志</h3><el-radio-group v-model="logSource" @change="logPage = 1; loadSection()"><el-radio-button value="PLATFORM">千川日志</el-radio-button><el-radio-button value="LINGTOU">灵投日志</el-radio-button></el-radio-group></div>
        <el-table v-if="!sectionError" :data="logs?.records || []" empty-text="所选区间没有该计划的操作记录"><el-table-column label="操作时间" width="170"><template #default="{ row }">{{ timeText(row.time) }}</template></el-table-column><el-table-column prop="actor" label="操作人" width="120" /><el-table-column label="操作内容" min-width="300"><template #default="{ row }"><strong>{{ row.detail || row.operation }}</strong><p v-for="(line, i) in row.details || []" :key="i" class="log-detail">{{ line }}</p></template></el-table-column><el-table-column v-if="logSource === 'LINGTOU'" label="结果" width="100"><template #default="{ row }">{{ row.result === 'SUCCESS' ? '成功' : row.result === 'FAILED' ? '失败' : row.result }}</template></el-table-column></el-table>
        <el-pagination v-if="logs && logs.total > 20 && !sectionError" v-model:current-page="logPage" :total="logs.total" :page-size="20" layout="total, prev, pager, next" @current-change="loadSection()" />
        <p class="insights-note">仅展示当前计划的操作；{{ logSource === 'PLATFORM' ? '千川日志包含平台后台操作，查询依赖广告账户权限。' : '灵投日志记录在灵投中完成的操作。' }}</p>
      </template>
      <PlanGuarantee v-else-if="activeTab === 'guarantee' && !sectionError" :plan-id="planId" :range="range" :guarantee="guarantee" :page="guaranteePage" @page="guaranteePage = $event; loadSection()" />
    </section>
  </article>
</template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { getPlan, syncPlanDetail, type PlanVo } from '@/features/delivery/deliveryApi';
import { creationGet, type CreationState } from '@/features/delivery/creationApi';
import { listAccounts, type AccountVo } from '@/features/identity/oauthApi';
import { insightData, insightLogs, insightGuarantee, type InsightStatistics, type InsightGuarantee, type InsightLog, type InsightPage, type InsightMetrics } from '@/features/delivery/planInsightsApi';
import { recentRange, rangeError, shanghaiToday, type StatisticsRange } from '@/features/delivery/statisticsRange';
import { BID_TYPE, planStatusLabel, roiText, yuan } from '@/utils/labels';
import PlanMaterials from './components/PlanMaterials.vue';
import PlanTrendChart from './components/PlanTrendChart.vue';
import PlanProducts from './components/PlanProducts.vue';
import PlanDetails from './components/PlanDetails.vue';
import PlanGuarantee from './components/PlanGuarantee.vue';
import PlanControls from './components/PlanControls.vue';
const props = defineProps<{ planId: string; tab?: string; initialRange: StatisticsRange }>();
const emit = defineEmits<{ tab: [value: string]; range: [value: StatisticsRange]; changed: [] }>();
const tabs = computed(() => [{ id: 'data', label: '数据' }, { id: 'products', label: '商品' }, { id: 'materials', label: '素材' }, { id: 'controls', label: '调控' }, { id: 'logs', label: '日志' }, { id: 'guarantee', label: '保障历史' }]);
const activeTab = computed(() => tabs.value.some(t => t.id === props.tab) ? props.tab! : 'data');
const tabLabel = computed(() => tabs.value.find(t => t.id === activeTab.value)!.label);
const plan = ref<PlanVo>(), creation = ref<CreationState>(), accounts = ref<AccountVo[]>([]);
const initialLoading = ref(true), initialError = ref(''), refreshing = ref(false), sectionLoading = ref(false), sectionError = ref('');
const detailLoading = ref(false), detailError = ref('');
async function loadPlanDetails(event?: Event) {
  if (event && !(event.target as HTMLDetailsElement).open) return;
  if (!plan.value?.qcPlanId || plan.value.platformDetail || detailLoading.value) return;
  detailLoading.value = true; detailError.value = '';
  try { const detail = await syncPlanDetail(props.planId); if (!disposed) plan.value = detail; }
  catch (error) { if (!disposed) detailError.value = errorText(error); }
  finally { if (!disposed) detailLoading.value = false; }
}
const productRefresh = ref(0);
const range = ref<StatisticsRange>(rangeError(props.initialRange) ? recentRange(7) : [...props.initialRange]), rangeDraft = ref<StatisticsRange>([...range.value]);
const calendarStart = ref<string | null>(null);
const shortDate = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const shortcuts = [1, 7, 30, 180].map(days => ({ text: days === 1 ? '今天' : `近${days}天`, value: () => recentRange(days).map(day => new Date(`${day}T00:00:00`)) }));
function disabledDate(date: Date) { const day = shortDate(date); return day > shanghaiToday() || (calendarStart.value != null && Math.abs(Date.parse(day) - Date.parse(calendarStart.value)) / 86400000 >= 180); }
function calendarChange(value: [Date, Date | null]) { calendarStart.value = value[1] ? null : shortDate(value[0]); }
function calendarVisible(visible: boolean) { if (!visible) calendarStart.value = null; }
function changeRange(value: StatisticsRange | null) { calendarStart.value = null; if (!value) return; const error = rangeError(value); if (error) { ElMessage.warning(error); rangeDraft.value = [...range.value]; return; } range.value = [...value]; logPage.value = guaranteePage.value = 1; emit('range', [...value]); void loadSection(); }
const statistics = ref<InsightStatistics>(), logs = ref<InsightPage<InsightLog>>(), guarantee = ref<InsightGuarantee>();
const logSource = ref('PLATFORM'), logPage = ref(1), guaranteePage = ref(1);
const primary = ref<keyof InsightMetrics>('cost'), secondary = ref<keyof InsightMetrics>('gmv');
const metrics: { key: keyof InsightMetrics; label: string; kind: string }[] = [{ key: 'cost', label: '整体消耗（元）', kind: 'money' }, { key: 'orders', label: '整体成交订单数', kind: 'count' }, { key: 'gmv', label: '整体成交金额（元）', kind: 'money' }, { key: 'roi', label: '整体支付 ROI', kind: 'roi' }, { key: 'netRoi', label: '净成交 ROI', kind: 'roi' }, { key: 'netGmv', label: '净成交金额（元）', kind: 'money' }, { key: 'settlementRate', label: '净成交金额结算率', kind: 'rate' }, { key: 'refundRate', label: '1小时内退款率', kind: 'rate' }];
const metricLabel = (key: keyof InsightMetrics) => metrics.find(m => m.key === key)!.label;
function metricValue(value: number | null | undefined, kind: string) { if (value == null) return '—'; return kind === 'money' ? yuan(value) : kind === 'roi' ? roiText(value) : kind === 'rate' ? `${Number(value).toFixed(2)}%` : Number(value).toLocaleString('zh-CN'); }
function timeText(value?: string | null) { if (!value) return '—'; if (!value.endsWith('Z') && !/[+-]\d\d:\d\d$/.test(value)) return value.replace('T', ' '); return new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }); }
const boundAccount = computed(() => accounts.value.find(a => a.accountId === plan.value?.accountId));
const previewAccount = computed(() => { const aweme = boundAccount.value?.awemes.find(a => a.awemeId === plan.value?.awemeId); return plan.value?.awemeId ? { awemeName: aweme?.name || plan.value.awemeId, avatar: aweme?.avatar } : undefined; });
let request = 0, disposed = false;
function errorText(error: any) { return error?.response?.data?.message || error?.message || '查询失败，请稍后重试'; }
async function initialize() {
  initialLoading.value = true; initialError.value = '';
  const results = await Promise.allSettled([getPlan(props.planId), creationGet(props.planId), listAccounts()]);
  if (disposed) return;
  if (results[0].status === 'fulfilled') plan.value = results[0].value; else initialError.value = errorText(results[0].reason);
  if (results[1].status === 'fulfilled') { creation.value = results[1].value; } else { creation.value = undefined; }
  if (results[2].status === 'fulfilled') accounts.value = results[2].value;
  initialLoading.value = false;
}
async function loadSection(force = false) {
  if (!plan.value || disposed) return;
  const turn = ++request, tab = activeTab.value; sectionLoading.value = true; sectionError.value = '';
  // Clear stale values before changing date/source; a failed query must not present the previous report.
  if (tab === 'data') statistics.value = undefined;
  if (tab === 'logs') logs.value = undefined;
  if (tab === 'guarantee') guarantee.value = undefined;
  try {
    if (tab === 'data') { const value = await insightData(props.planId, [...range.value], force); if (turn === request && !disposed) statistics.value = value; }
    else if (tab === 'logs') { const value = await insightLogs(props.planId, [...range.value], logSource.value, logPage.value); if (turn === request && !disposed) logs.value = value; }
    else if (tab === 'guarantee') { const value = await insightGuarantee(props.planId, [...range.value], guaranteePage.value); if (turn === request && !disposed) guarantee.value = value; }
  } catch (error) { if (turn === request && !disposed) sectionError.value = errorText(error); }
  finally { if (turn === request && !disposed) sectionLoading.value = false; }
}
async function refresh() { if (refreshing.value) return; refreshing.value = true; try { if (plan.value?.qcPlanId) await syncPlanDetail(props.planId); await initialize(); productRefresh.value++; await loadSection(true); } catch (error) { initialError.value = errorText(error); } finally { refreshing.value = false; } }
async function productsChanged() { await initialize(); emit('changed'); }
watch(activeTab, () => { if (!initialLoading.value) void loadSection(); });
onMounted(async () => { await initialize(); await loadSection(); });
onBeforeUnmount(() => { disposed = true; request++; });
</script>
<style scoped>
.plan-insights{color:#22243b;min-height:100%;background:#f7f8fd}.insights-header{padding:25px 28px;background:#fff;display:flex;align-items:center;gap:20px;justify-content:space-between;border-bottom:1px solid #eceefa}.eyebrow{font-size:12px;color:#8a8ea6;margin:0 0 7px}.insights-header h2{font-size:19px;margin:0 0 15px;overflow-wrap:anywhere}.plan-facts{display:flex;flex-wrap:wrap;gap:12px 20px;font-size:12px;color:#747992}.insights-toolbar{background:#fff;padding:10px 25px 0;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:15px}.insights-tabs{display:flex;gap:26px}.insights-tabs button{padding:17px 0;background:none;border:0;border-bottom:3px solid transparent;color:#6f738a;white-space:nowrap;cursor:pointer;font-size:14px}.insights-tabs button.active{color:#5b57ff;border-color:#5b57ff;font-weight:600}.insights-date{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.insights-date :deep(.el-date-editor){width:260px}.insights-date small{font-size:11px;color:#9095aa}.insights-section{margin:16px;padding:24px;background:#fff;border-radius:12px;min-height:480px}.section-title{display:flex;align-items:center;justify-content:space-between;gap:15px;margin-bottom:16px}.section-title h3,.insights-section h3{font-size:16px;margin:0 0 18px}.section-title h3{margin:0}.section-title>span,.section-title small{color:#8a8ea6;font-size:12px;font-weight:400}.insight-metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:20px 0 30px}.insight-metric{border-radius:8px;padding:18px 16px;background:#f5f6fb;min-width:0}.insight-metric:nth-child(4n+1){background:#f0f0ff}.insight-metric:nth-child(4n+3){background:#eefafa}.insight-metric span{font-size:12px;color:#737892;display:block;margin-bottom:13px}.insight-metric strong{font-size:21px;font-weight:650}.trend-title{margin-top:20px}.trend-select{display:flex;gap:10px}.trend-select .el-select{width:165px}.insights-note{font-size:12px;color:#8b90a5;line-height:1.8;margin-top:18px}.insight-product{display:flex;gap:12px;align-items:center;padding:8px 0}.insight-product img{width:60px;height:60px;border-radius:7px;object-fit:cover}.insight-product strong{font-size:13px;font-weight:500;display:block}.insight-product small{font-size:11px;color:#8a8ea6;display:block;margin-top:5px}.material-filters{display:flex;gap:16px;margin:18px 0;flex-wrap:wrap}.material-filters>.el-select{width:240px}.material-type-tabs{display:flex;padding:3px;background:#f5f5fb;border-radius:8px;gap:3px}.material-type-tabs button{padding:6px 15px;background:none;border:0;color:#777c94;border-radius:6px;cursor:pointer}.material-type-tabs button.active{background:white;color:#5b57ff;box-shadow:0 1px 4px #2222}.material-type-tabs small{margin-left:4px}.insight-material-layout{display:grid;grid-template-columns:minmax(240px,1fr) minmax(260px,340px);gap:24px;align-items:start}.insight-material-list{display:flex;flex-direction:column;gap:10px}.insight-material{display:flex;gap:12px;align-items:center;padding:14px;text-align:left;border:1px solid #e5e6f3;background:white;border-radius:10px;cursor:pointer;color:inherit;width:100%}.insight-material.selected{border-color:#625cff;background:#f8f8ff}.insight-material img{width:58px;height:64px;object-fit:cover;border-radius:6px;flex:none}.insight-material div{min-width:0}.insight-material strong{display:block;font-size:13px;line-height:1.6;overflow-wrap:anywhere}.insight-material span,.insight-material small{display:block;font-size:11px;line-height:1.7;color:#8b90a5}.insight-material small.failure{color:#f56c6c}.insight-material-layout :deep(.preview){width:100%;max-width:340px;box-sizing:border-box;margin:0}.wizard-link,.guarantee-current a{color:#5b57ff;text-decoration:none;font-size:13px}.log-detail{margin:6px 0;color:#7d8198;font-size:12px;overflow-wrap:anywhere}.guarantee-current{padding:20px;border:1px solid #e8e9f4;border-radius:9px;margin:15px 0 28px}.guarantee-current small{display:block;margin-top:15px;color:#9498ad}.section-error{display:flex;align-items:center;gap:16px;background:#fff5f5;color:#cc5151;padding:16px;border-radius:8px;margin-bottom:20px;overflow-wrap:anywhere}.insights-section :deep(.el-pagination){justify-content:flex-end;margin-top:20px}.insights-section :deep(.el-descriptions__content){overflow-wrap:anywhere}.insights-section :deep(.el-table__header th){background:#f8f9fd}.insights-section :deep(.el-table){font-size:12px}
@media(max-width:900px){.insight-metrics{grid-template-columns:repeat(2,1fr)}.insights-tabs{gap:20px}.insight-material-layout{grid-template-columns:1fr}.insight-material-layout :deep(.preview){justify-self:center}.insights-section{margin:10px;padding:16px}.insights-header{padding:20px 16px}.insights-toolbar{padding:0 16px}.trend-title{align-items:flex-start;flex-direction:column}.trend-select .el-select{width:145px}.insights-section :deep(.el-descriptions__label){min-width:85px}.plan-facts{gap:8px}.insights-tabs{overflow-x:auto;max-width:100%;gap:22px}}
</style>
