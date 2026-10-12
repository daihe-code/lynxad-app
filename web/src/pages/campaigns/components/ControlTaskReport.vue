<template>
  <el-dialog v-model="open" :title="`${kind}数据报告`" class="control-report-dialog" width="1040px" append-to-body destroy-on-close :close-on-click-modal="false">
    <div class="control-report">
      <p v-if="planName" class="report-plan">{{ planName }}</p>
      <div v-if="tasks && tasks.length > 1" class="report-task-picker"><label>调控任务</label><el-select :model-value="task?.id" filterable aria-label="选择报告调控任务" @change="emit('select-task', $event)"><el-option v-for="item in tasks" :key="item.id" :value="item.id" :label="`${item.name || '未命名任务'} · ${item.id}`"><div class="report-task-option"><span>{{ item.name || '未命名任务' }}</span><small>{{ taskStatusText(item.task_status) }} · ID：{{ item.id }}</small></div></el-option></el-select></div>
      <div class="report-task"><strong>{{ metadata?.name || (tasksLoading ? '正在加载调控任务…' : tasks ? '暂无调控任务' : '未命名任务') }}</strong><div v-if="metadata"><span>ID：{{ metadata.id }}</span><span>创建时间：{{ metadata.create_time?.replace('T', ' ') || '—' }}</span><span>{{ taskStatusText(metadata.task_status) }}</span></div></div>
      <div class="report-range"><strong>调控数据</strong><div><el-select v-model="granularity" aria-label="任务报表时间维度" @change="load"><el-option label="分时" value="HOUR" :disabled="rangeDays(range) > 7" /><el-option label="分日" value="DAY" /></el-select><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" :clearable="false" :disabled-date="disabledDate" start-placeholder="开始日期" end-placeholder="结束日期" aria-label="任务报告统计区间" @change="changeRange" /><el-button :icon="Refresh" :loading="loading" :disabled="!task || tasksLoading" aria-label="刷新任务报告" @click="load" /></div></div>
      <p class="range-note">分日统计最长180天，分时统计最长7天（均包含起止日期）。只展示当前调控任务的数据。</p>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"><el-button text @click="load">重新加载</el-button></el-alert>
      <el-alert v-if="tasksError" :title="tasksError" type="error" show-icon :closable="false"><el-button text @click="emit('reload-tasks')">重新加载任务</el-button></el-alert>
      <div v-loading="loading || tasksLoading" class="report-content">
        <template v-if="report">
          <el-alert v-if="report.warning" :title="report.warning" type="warning" show-icon :closable="false" />
          <h3>数据概览</h3>
          <div class="metric-grid"><div v-for="metric in overview" :key="metric.key" class="metric-card" :class="{ cost: metric.key === 'stat_cost_for_roi2_assist' }"><span>{{ metric.label }}</span><strong>{{ value(report.summary[metric.key], metric.kind) }}</strong></div></div>
          <div class="section-heading"><h3>数据对比</h3><el-button link type="primary" @click="showTrend = !showTrend">{{ showTrend ? '收起对比' : '展开对比' }}</el-button></div>
          <template v-if="showTrend"><div class="metric-selectors"><el-select v-model="primary" aria-label="报告趋势主指标"><el-option v-for="m in metrics" :key="m.key" :label="m.label" :value="m.key" /></el-select><el-select v-model="secondary" aria-label="报告趋势对比指标"><el-option v-for="m in metrics" :key="m.key" :label="m.label" :value="m.key" /></el-select></div>
            <PlanTrendChart v-if="report.trend.length" :points="report.trend" :primary="primary" :secondary="secondary" :primary-label="metricLabel(primary)" :secondary-label="metricLabel(secondary)" />
            <el-empty v-else description="当前区间暂无趋势明细" :image-size="70" />
          </template>
          <div class="section-heading"><h3>数据明细</h3><div><el-tooltip content="导出当前报告数据"><el-button :icon="Download" aria-label="导出任务报告" @click="download" /></el-tooltip><el-popover trigger="click" placement="bottom-end" :width="280"><template #reference><el-button :icon="Setting" aria-label="设置任务报告数据列" /></template><el-checkbox-group v-model="visibleColumns"><el-checkbox v-for="m in metrics" :key="m.key" :value="m.key">{{ m.label }}</el-checkbox></el-checkbox-group></el-popover></div></div>
          <el-table :data="details" scrollbar-always-on row-key="time"><el-table-column label="任务名称" min-width="230"><template #default><strong>{{ report.task.name || '未命名任务' }}</strong><small>ID：{{ report.task.id }}</small></template></el-table-column><el-table-column prop="time" :label="report.granularity === 'HOUR' ? '时间' : '日期'" width="175" /><el-table-column v-for="m in columns" :key="m.key" :label="m.label" min-width="165" align="right"><template #default="{ row }">{{ value(row.metrics[m.key], m.kind) }}</template></el-table-column></el-table>
          <p class="range-note">{{ report.trend.length ? '明细按所选时间维度展示。' : '平台未返回趋势明细，表格展示当前区间汇总。' }}平台未返回的指标显示「—」，实际返回的零值显示为0。更新于 {{ fetchedTime }}</p>
        </template>
        <el-empty v-else-if="!loading && !tasksLoading && !error && !tasksError" :description="tasks && !tasks.length ? '当前计划暂无此类调控任务' : '暂无任务报告'" :image-size="70" />
      </div>
    </div>
    <template #footer><el-button @click="open = false">关闭</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { Download, Refresh, Setting } from '@element-plus/icons-vue';
import { getControlTaskReport, type ControlTask, type ControlTaskReport } from '@/features/delivery/deliveryApi';
import { CONTROL_REPORT_METRICS as metrics, taskStatusText } from '@/features/delivery/controlTaskPresentation';
import { rangeDays, rangeError, recentRange, shanghaiToday, type StatisticsRange } from '@/features/delivery/statisticsRange';
import PlanTrendChart from './PlanTrendChart.vue';
const props = defineProps<{ modelValue: boolean; planId: string; planName?: string; task?: ControlTask; tasks?: ControlTask[]; tasksLoading?: boolean; tasksError?: string; kind: string; initialRange: StatisticsRange }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; 'select-task': [id: string]; 'reload-tasks': [] }>();
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });
const range = ref<StatisticsRange>(recentRange(1)), granularity = ref<'HOUR' | 'DAY'>('HOUR');
const report = ref<ControlTaskReport>(), loading = ref(false), error = ref(''), showTrend = ref(true);
const metadata = computed(() => report.value?.task || props.task);
const primary = ref('total_order_settle_amount_for_roi2_1h_assist'), secondary = ref('stat_cost_for_roi2_assist');
const overviewKeys = ['total_prepay_and_pay_settle_roi2_1h_assist', 'total_order_settle_amount_for_roi2_1h_assist', 'total_refund_order_gmv_for_roi2_1h_rate_assist', 'total_prepay_and_pay_order_roi2_assist', 'click_cnt_for_roi2_assist', 'show_cnt_for_roi2_assist', 'ctr_for_roi2_assist', 'convert_rate_for_roi2_assist', 'total_pay_order_gmv_include_coupon_for_roi2_assist', 'total_pay_order_count_for_roi2_assist', 'total_pay_order_gmv_for_roi2_assist', 'stat_cost_for_roi2_assist'];
const overview = overviewKeys.map(key => metrics.find(metric => metric.key === key)!);
const visibleColumns = ref(metrics.map(m => m.key)), columns = computed(() => metrics.filter(m => visibleColumns.value.includes(m.key)));
const details = computed(() => report.value?.trend.length ? report.value.trend : report.value ? [{ time: '区间汇总', metrics: report.value.summary }] : []);
const fetchedTime = computed(() => report.value ? new Date(report.value.fetchedAt).toLocaleString('sv-SE', { timeZone: 'Asia/Shanghai' }) : '');
const disabledDate = (date: Date) => date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0') > shanghaiToday();
function metricLabel(key: string) { return metrics.find(m => m.key === key)?.label || key; }
function value(v: unknown, kind: string) { const n = v == null || v === '' ? null : Number(v); return n == null || !Number.isFinite(n) ? '—' : kind === 'count' ? n.toLocaleString('zh-CN') : `${n.toFixed(2)}${kind === 'rate' ? '%' : ''}`; }
let serial = 0, disposed = false;
async function load() {
  const turn = ++serial; report.value = undefined; error.value = ''; loading.value = false;
  if (!open.value || !props.task) return;
  const invalid = !range.value ? '请选择统计时间区间' : rangeError(range.value) || (granularity.value === 'HOUR' && rangeDays(range.value) > 7 ? '分时报表区间最长7天，请改用分日' : null);
  if (invalid) { error.value = invalid; return; }
  loading.value = true;
  const id = props.task.id;
  try { const result = await getControlTaskReport(props.planId, id, props.kind, [...range.value], granularity.value); if (!disposed && turn === serial && open.value) { if (result.task.id !== id) throw new Error('报告任务ID不匹配，请重新加载'); report.value = result; } }
  catch (e: any) { if (!disposed && turn === serial && open.value) error.value = e?.response?.data?.message || e?.message || '任务报告加载失败，请稍后重试'; }
  finally { if (!disposed && turn === serial) loading.value = false; }
}
function changeRange() { if (range.value && rangeDays(range.value) > 7) granularity.value = 'DAY'; void load(); }
function download() {
  if (!report.value) return;
  const csv = (s: string) => `"${(/^[=+\-@\t\r]/.test(s) || /^\d{16,}$/.test(s) ? "'" : '') + s.replaceAll('"', '""')}"`;
  const lines = [['任务名称', '任务ID', '统计开始日期', '统计结束日期', '时间', ...columns.value.map(m => m.label)], ...details.value.map(row => [report.value!.task.name || '', report.value!.task.id, ...range.value, row.time, ...columns.value.map(m => value(row.metrics[m.key], m.kind))])];
  const url = URL.createObjectURL(new Blob(['\uFEFF' + lines.map(line => line.map(csv).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a'); a.href = url; a.download = `${props.kind}_报告_${report.value.task.id}_${range.value[0]}_${range.value[1]}.csv`; a.click(); URL.revokeObjectURL(url);
}
watch(() => [props.modelValue, props.planId, props.task?.id, props.kind], (current, previous) => {
  serial++; report.value = undefined; error.value = ''; loading.value = false;
  if (!props.modelValue) return;
  if (!previous[0] || current[1] !== previous[1] || current[3] !== previous[3]) {
    range.value = [...props.initialRange]; granularity.value = rangeDays(range.value) > 7 ? 'DAY' : 'HOUR'; showTrend.value = true;
  }
  void load();
});
onBeforeUnmount(() => { disposed = true; serial++; });
</script>
<style scoped>
.report-plan{color:#7b8098;font-size:13px;margin:0 0 14px;overflow-wrap:anywhere}.report-task-picker{display:flex;align-items:center;gap:14px;margin-bottom:16px}.report-task-picker label{font-size:13px;color:#7b8098;white-space:nowrap}.report-task-picker .el-select{flex:1;min-width:0}.report-task-option{display:flex;align-items:center;justify-content:space-between;gap:14px}.report-task-option small{color:#9196ac;font-size:11px}
.report-task{background:#f7f8fc;border-radius:9px;padding:18px 20px}.report-task>strong{font-size:15px;color:#24263b;overflow-wrap:anywhere}.report-task>div{display:flex;flex-wrap:wrap;gap:8px 24px;margin-top:10px;font-size:12px;color:#858a9f}.report-range{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-top:24px}.report-range>div{display:flex;align-items:center;gap:8px}.report-range .el-select{width:90px}.report-range :deep(.el-date-editor){width:270px}.report-content{min-height:130px}.range-note{font-size:12px;color:#979bb0;line-height:1.7;margin:12px 0}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.metric-card{background:#f7f8fb;padding:18px;border-radius:9px;display:flex;flex-direction:column;gap:13px}.metric-card.cost{background:#eafafb}.metric-card span{font-size:12px;color:#7b8098}.metric-card strong{font-size:22px;color:#25273c}.report-content h3{font-size:14px;color:#34364a;margin:22px 0 16px}.section-heading{display:flex;align-items:center;justify-content:space-between}.section-heading>div{display:flex;gap:8px}.metric-selectors{display:flex;gap:12px;justify-content:flex-end}.metric-selectors .el-select{width:230px}.report-content :deep(.el-alert){margin-top:14px}.report-content :deep(.el-table strong){font-weight:500;font-size:12px;overflow-wrap:anywhere}.report-content :deep(.el-table small){display:block;margin-top:6px;color:#9196ac}.el-checkbox-group{display:flex;flex-direction:column;max-height:380px;overflow:auto}
@media(max-width:700px){.report-range{flex-direction:column;align-items:flex-start}.report-range>div{width:100%;flex-wrap:wrap}.report-range :deep(.el-date-editor){flex:1;min-width:200px}.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.metric-card{padding:14px}.metric-card strong{font-size:19px}.metric-selectors{flex-wrap:wrap}.metric-selectors .el-select{width:100%}}
</style>
<style>
.control-report-dialog{max-width:calc(100vw - 32px);margin-top:5vh!important}.control-report-dialog .el-dialog__body{max-height:72vh;overflow-y:auto}.control-report-dialog .el-dialog__title{font-size:18px;font-weight:600}
</style>
