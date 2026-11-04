<template>
  <section class="asset-statistics">
    <header class="statistics-head">
      <div class="statistics-name"><h2 id="asset-detail-title">{{ item.filename }}</h2><p>素材 ID：{{ report?.materialId || assetIdentifier(item) }}</p></div>
      <button class="statistics-preview" type="button" @click="emit('preview')"><AssetIcon name="expand" />预览</button>
      <button class="statistics-close" type="button" aria-label="关闭" @click="emit('close')"><AssetIcon name="close" /></button>
    </header>
    <div class="statistics-scroll">
      <div class="statistics-toolbar">
        <h3>全域投放数据</h3>
        <div class="statistics-range">
          <input v-model="from" type="date" aria-label="统计开始日期" :min="minimumDate" :max="to || today" :aria-invalid="!!rangeError" />
          <span>至</span>
          <input v-model="to" type="date" aria-label="统计结束日期" :min="from || minimumDate" :max="today" :aria-invalid="!!rangeError" />
          <button type="button" aria-label="刷新投放数据" :disabled="loading || !!rangeError" @click="load"><AssetIcon name="refresh" :class="{ spinning: loading }" /></button>
        </div>
        <p v-if="rangeError" class="statistics-error range-error" role="alert">{{ rangeError }}</p>
      </div>
      <div class="statistics-panel" :aria-busy="loading">
        <div v-if="loading" class="statistics-loading" role="progressbar" aria-label="正在加载投放数据"></div>
        <div v-if="error" class="statistics-error" role="alert">投放数据暂时无法加载，请重试。<button type="button" @click="load">重试</button></div>
        <div class="statistics-metrics">
          <div v-for="(metric, index) in materialMetrics" :key="metric.key" class="statistics-metric" :class="`metric-${index}`">
            <span>{{ metric.label }}</span><i v-if="loading" class="metric-skeleton"></i><strong v-else>{{ materialMetricValue(metric.key, report?.summary[metric.key]) }}</strong>
          </div>
        </div>
        <div class="statistics-comparison">
          <div class="comparison-head"><h3>数据对比</h3><button type="button" :aria-expanded="expanded" @click="expanded = !expanded">{{ expanded ? '收起对比' : '展开对比' }}<AssetIcon name="chevron-down" :class="{ reversed: expanded }" /></button></div>
          <template v-if="expanded">
            <div class="comparison-selects">
              <label><i></i><select v-model="primary" aria-label="第一对比指标"><option v-for="metric in materialMetrics" :key="metric.key" :value="metric.key">{{ metric.label }}</option></select></label>
              <label><i></i><select v-model="secondary" aria-label="第二对比指标"><option v-for="metric in materialMetrics" :key="metric.key" :value="metric.key">{{ metric.label }}</option></select></label>
            </div>
            <MaterialTrendChart :points="points" :primary="primary" :secondary="secondary" :empty-label="emptyLabel" />
          </template>
        </div>
        <details v-if="report?.trend.length" class="statistics-daily">
          <summary>每日数据</summary>
          <div class="statistics-table"><table><thead><tr><th>日期</th><th v-for="metric in materialMetrics" :key="metric.key">{{ metric.label }}</th></tr></thead>
            <tbody><tr v-for="point in points" :key="point.date"><th>{{ point.date }}</th><td v-for="metric in materialMetrics" :key="metric.key">{{ materialMetricValue(metric.key, point.metrics[metric.key]) }}</td></tr></tbody></table></div>
        </details>
      </div>
      <div class="statistics-references"><AssetPlanReferences :item="item" /></div>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { getMaterialStatistics, type LibraryItemVo, type MaterialStatistics } from '@/features/creative/creativeApi';
import { materialMetrics, materialMetricValue, materialDailyPoints, reportingToday, shiftReportDate, statisticsRangeError } from '@/features/creative/materialStatistics';
import { assetIdentifier } from './assetFormat';
import AssetIcon from './AssetIcon.vue';
import AssetPlanReferences from './AssetPlanReferences.vue';
import MaterialTrendChart from './MaterialTrendChart.vue';
const props = defineProps<{ item: LibraryItemVo; accountId?: string }>();
const emit = defineEmits<{ close: []; preview: [] }>();
const today = reportingToday(), minimumDate = shiftReportDate(today, -179);
const from = ref(shiftReportDate(today, -6)), to = ref(today);
const rangeError = computed(() => statisticsRangeError(from.value, to.value, today));
const report = ref<MaterialStatistics>(), error = ref(false), loading = ref(false), expanded = ref(true);
const primary = ref('cost'), secondary = ref('ctr');
const points = computed(() => rangeError.value ? [] : materialDailyPoints(from.value, to.value, report.value?.trend || []));
const emptyLabel = computed(() => loading.value ? '正在加载…' : error.value ? '' : report.value?.state === 'ACCOUNT_REQUIRED' ? '请选择投放账户' : report.value?.state === 'READY' && !report.value.trendAvailable ? '暂无每日趋势数据' : '该时段暂无投放数据');
let request = 0, controller: AbortController | undefined;
async function load() {
  const current = ++request; controller?.abort(); report.value = undefined; error.value = false;
  if (rangeError.value) { loading.value = false; return; }
  controller = new AbortController(); loading.value = true;
  try {
    const result = await getMaterialStatistics({ id: props.item.id, accountId: props.accountId, from: from.value, to: to.value }, controller.signal);
    if (current === request) report.value = result;
  } catch { if (current === request) error.value = true; }
  finally { if (current === request) loading.value = false; }
}
watch(() => [props.item.id, props.accountId, from.value, to.value], () => void load(), { immediate: true });
onUnmounted(() => { ++request; controller?.abort(); });
</script>
<style scoped>
.asset-statistics{height:100%;display:flex;flex-direction:column;color:#232338;min-height:0}.statistics-head{display:flex;align-items:center;gap:20px;background:white;padding:22px 26px;border-bottom:1px solid #eeeef6;flex:none}.statistics-name{min-width:0;flex:1}.statistics-name h2{font-size:18px;font-weight:600;overflow-wrap:anywhere;margin:0}.statistics-name p{font-size:12px;color:#8b8ba3;margin:7px 0 0;overflow-wrap:anywhere}.statistics-close,.statistics-preview,.statistics-range button{display:inline-flex;align-items:center;justify-content:center;gap:6px;flex:none;background:white;color:#797992;padding:7px;border:0;border-radius:7px;cursor:pointer}.statistics-preview{font-size:13px;color:#5c55ff}.asset-statistics svg{width:18px;height:18px}.asset-statistics button:focus-visible,.asset-statistics select:focus-visible{outline:2px solid #5c55ff;outline-offset:2px}.statistics-scroll{overflow-y:auto;min-height:0;flex:1;padding:24px;background:#f5f5ff}.statistics-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin:0 0 18px}.asset-statistics h3{font-size:15px;font-weight:600;margin:0}.statistics-range{display:flex;align-items:center;gap:8px;font-size:12px;color:#8b8ba3}.statistics-range input{font:inherit;color:#666680;border:1px solid #e2e2f1;border-radius:7px;padding:8px;background:white;width:132px}.statistics-range input:focus{outline:1px solid #5c55ff}.statistics-range button{border:1px solid #e2e2f1}.statistics-range button:disabled{opacity:.5;cursor:default}.statistics-panel{position:relative;border-radius:14px;background:white;padding:24px;overflow:hidden}.statistics-metrics{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:14px}.statistics-metric{border-radius:9px;background:#f7f7fa;min-height:100px;padding:17px 14px;display:flex;flex-direction:column;justify-content:space-between;gap:16px;min-width:0}.statistics-metric span{font-size:12px;line-height:1.5;color:#66667e}.statistics-metric strong{font-size:22px;font-weight:600;overflow-wrap:anywhere;line-height:1.2;font-variant-numeric:tabular-nums}.metric-0{background:#f2f0ff}.metric-1{background:#effafb}.statistics-comparison{margin-top:30px}.comparison-head{display:flex;justify-content:space-between;align-items:center;gap:12px}.comparison-head button{border:0;background:none;color:#8a8aa1;display:flex;gap:5px;align-items:center;font-size:12px;cursor:pointer}.comparison-head svg{width:13px;height:13px}.reversed{transform:rotate(180deg)}.comparison-selects{display:flex;justify-content:center;gap:24px;flex-wrap:wrap;margin-top:20px}.comparison-selects label{display:flex;align-items:center;gap:7px}.comparison-selects i{width:8px;height:8px;border-radius:2px;background:#5c55ff}.comparison-selects label:nth-child(2) i{background:#23bec8}.comparison-selects select{font-size:12px;background:white;color:#5e5e75;border:0;max-width:210px;cursor:pointer}.statistics-error{font-size:13px;color:#e65a67;margin-bottom:20px}.statistics-error button{color:#5c55ff;background:none;border:0;margin-left:12px;cursor:pointer}.range-error{width:100%;margin:0;text-align:right}.statistics-loading{height:3px;position:absolute;inset:0 0 auto;overflow:hidden;background:#efedff}.statistics-loading:after{content:'';display:block;width:35%;height:100%;background:#5c55ff;animation:progress 1.2s ease-in-out infinite}.metric-skeleton{width:65%;height:24px;border-radius:5px;background:linear-gradient(90deg,#e9e7f4,#f9f8ff,#e9e7f4);background-size:200%;animation:shimmer 1.2s infinite}.spinning{animation:spin .8s linear infinite}.statistics-references{background:white;border-radius:14px;margin-top:20px;padding:0 24px 20px}.statistics-daily{margin-top:20px;font-size:12px;color:#78788e}.statistics-daily summary{cursor:pointer}.statistics-table{overflow-x:auto;margin-top:12px}.statistics-table table{width:100%;border-collapse:collapse;white-space:nowrap;text-align:right;font-variant-numeric:tabular-nums}.statistics-table td,.statistics-table th{padding:12px;border-bottom:1px solid #eeeef5}.statistics-table th{font-weight:500}.statistics-table tr>:first-child{text-align:left}@keyframes spin{to{transform:rotate(360deg)}}@keyframes progress{from{transform:translateX(-100%)}to{transform:translateX(380%)}}@keyframes shimmer{to{background-position:-200%}}
@media(max-width:1100px){.statistics-metrics{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:600px){.statistics-head{padding:18px;gap:10px}.statistics-name h2{font-size:16px}.statistics-scroll{padding:14px}.statistics-panel{padding:16px}.statistics-metrics{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.statistics-metric{min-height:95px;padding:14px 10px}.statistics-metric strong{font-size:20px}.statistics-range{gap:5px;flex-wrap:wrap}.statistics-range input{width:124px}.statistics-toolbar{gap:14px}.comparison-selects{gap:14px}.comparison-selects select{max-width:145px}.statistics-references{padding:0 16px 16px}}@media(prefers-reduced-motion:reduce){.spinning,.metric-skeleton,.statistics-loading:after{animation:none}}
</style>
