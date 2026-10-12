<template>
  <div class="plan-controls">
    <div class="control-toolbar">
      <nav class="control-tabs" role="tablist" aria-label="调控工具类型">
        <button v-for="tool in tools" :key="tool" role="tab" :aria-selected="kind === tool" :class="{ active: kind === tool }" @click="kind = tool">{{ tool }}</button>
      </nav>
      <template v-if="supported">
        <el-select v-model="status" clearable placeholder="投放状态：全部" aria-label="筛选调控状态" @change="page = 1"><el-option v-for="(label, key) in CONTROL_TASK_STATUS" :key="key" :value="key" :label="label" /></el-select>
        <el-input v-model="searchDraft" clearable placeholder="输入任务名称 / ID，回车搜索" aria-label="搜索调控任务" @keyup.enter="search" @clear="search"><template #suffix><el-button text :icon="Search" aria-label="搜索任务" @click="search" /></template></el-input>
      </template>
    </div>
    <template v-if="supported">
      <div class="control-options">
        <div class="filter-actions">
          <el-popover trigger="click" placement="bottom-start" :width="320"><template #reference><el-button :icon="Filter">更多筛选<span v-if="extraFilters" class="filter-count">{{ extraFilters }}</span></el-button></template>
            <div class="extra-filters"><label>所选统计区间有无消耗</label><el-select v-model="havingCost" aria-label="筛选调控消耗" @change="page = 1"><el-option label="全部" value="ALL" /><el-option label="有消耗" value="YES" /><el-option label="无消耗" value="NO" /></el-select>
              <label>任务创建日期</label><el-date-picker v-model="createdRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" aria-label="筛选任务创建日期" @change="page = 1" />
            </div>
          </el-popover><el-button v-if="hasFilters" link type="primary" @click="clearFilters">清空筛选</el-button>
        </div>
        <div class="table-tools"><span>已结束的任务保留30天</span><el-tooltip content="导出当前筛选的任务数据"><el-button :icon="Download" aria-label="导出调控数据" :disabled="!filtered.length" @click="download" /></el-tooltip>
          <el-popover trigger="click" placement="bottom-end" :width="270"><template #reference><el-button :icon="Setting" aria-label="设置调控数据列" /></template><strong>展示指标</strong><el-checkbox-group v-model="visibleColumns"><el-checkbox v-for="m in metrics" :key="m.key" :value="m.key">{{ m.label }}</el-checkbox></el-checkbox-group></el-popover>
          <el-button :icon="Refresh" aria-label="刷新调控任务" :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button text @click="load">重新加载</el-button></el-alert>
      <el-alert v-if="actionError" :title="actionError" type="error" :closable="false" show-icon><el-button text @click="load">刷新任务状态</el-button></el-alert>
      <el-table v-loading="loading" :data="paged" row-key="id" scrollbar-always-on :empty-text="error ? '任务加载失败，请重新加载' : hasFilters ? '没有符合条件的调控任务' : '当前计划暂无此类调控任务'" :default-sort="{ prop: 'create_time', order: 'descending' }" @sort-change="sortChange">
        <el-table-column label="任务名称" min-width="230"><template #default="{ row }"><div class="task-name"><strong>{{ row.name || '未命名任务' }}</strong><small>ID：{{ row.id }}</small></div></template></el-table-column>
        <el-table-column v-if="kind === '素材追投'" label="素材" min-width="295"><template #default="{ row }">
          <div v-for="(material, index) in taskMaterials(row)" :key="material.id || index" class="task-material">
            <button class="task-material-cover" :aria-label="`预览追投素材 ${material.id || row.id + '-' + index}`" @click="viewMaterial(material)"><el-image v-if="material.posterUrl" :src="material.posterUrl" fit="contain" alt="追投素材缩略图"><template #error><el-icon class="cover-placeholder"><VideoCamera /></el-icon></template></el-image><el-icon v-else class="cover-placeholder"><VideoCamera /></el-icon><el-icon class="cover-play"><VideoPlay /></el-icon></button>
            <div class="task-material-text"><el-tooltip :content="material.name"><button class="task-material-name" :aria-label="`查看追投素材 ${material.id || row.id + '-' + index}`" @click="viewMaterial(material)">{{ material.name }}</button></el-tooltip><small>素材 ID：{{ material.id || '平台未返回' }}</small><small v-if="material.duration != null">{{ value(material.duration, 'count') }} 秒</small></div>
          </div>
          <span v-if="!taskMaterials(row).length" class="material-missing">平台未返回素材信息</span>
        </template></el-table-column>
        <el-table-column label="调控状态" width="155"><template #default="{ row }"><span class="task-status" :class="{ processing: row.task_status === 'PROCESSING' }"><i />{{ taskStatusText(row.task_status) }}</span></template></el-table-column>
        <el-table-column label="操作" width="160"><template #default="{ row }"><div class="task-actions">
          <el-tooltip content="查看数据"><el-button link type="primary" :icon="DataAnalysis" :aria-label="`查看数据 ${row.id}`" @click="viewReport(row)" /></el-tooltip>
          <el-tooltip v-for="action in taskActions(row.task_status)" :key="action" :content="disabledReason(action) || actionLabels[action]"><span><el-button link :type="action === 'DELETE' ? 'danger' : 'primary'" :icon="actionIcons[action]" :aria-label="`${actionLabels[action]} ${row.id}`" :loading="busy === `${row.id}:${action}`" :disabled="!!busy || !!disabledReason(action)" @click="act(row, action)" /></span></el-tooltip>
        </div></template></el-table-column>
        <el-table-column prop="budget" label="调控预算（元）" width="140" align="right" sortable="custom"><template #default="{ row }">{{ value(row.budget, 'money') }}</template></el-table-column>
        <el-table-column prop="duration" label="调控时长（小时）" width="185" align="right" sortable="custom"><template #default="{ row }"><span>{{ value(row.duration, 'hours') }}</span><small v-if="estimatedEnd(row)" class="estimated-end">预计 {{ estimatedEnd(row) }} 结束</small></template></el-table-column>
        <el-table-column prop="create_time" label="创建时间" width="170" sortable="custom"><template #default="{ row }">{{ timeText(row.create_time) }}</template></el-table-column>
        <el-table-column v-for="m in columns" :key="m.key" :prop="`metrics.${m.key}`" :label="m.label" :min-width="m.label.length > 10 ? 185 : 150" align="right" sortable="custom"><template #default="{ row }">{{ value(row.metrics?.[m.key], m.kind) }}</template></el-table-column>
      </el-table>
      <footer class="control-footer"><p>统计区间：{{ range[0] }} ～ {{ range[1] }}<br />仅统计当前计划下每项调控任务的数据；平台未返回的指标显示「—」。</p><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="filtered.length" :page-sizes="[10, 20, 50, 100]" layout="total, prev, pager, next, sizes" @size-change="page = 1" /></footer>
    </template>
    <div v-else class="unsupported-tool"><el-empty description="搜索抢首屏任务暂未接入查询" :image-size="80" /><p>请前往千川后台查看此类任务及数据。</p><el-button tag="a" href="https://qianchuan.jinritemai.com/" target="_blank" rel="noopener noreferrer" type="primary">前往千川后台</el-button></div>
    <ControlTaskReport v-model="reportOpen" :plan-id="planId" :task="reportTask" :kind="reportKind" :initial-range="range" />
    <el-dialog :model-value="!!preview" :title="preview?.name || '追投素材预览'" width="min(600px, calc(100vw - 32px))" class="control-material-dialog" append-to-body destroy-on-close @close="closePreview">
      <el-alert v-if="previewError" :title="previewError" type="warning" :closable="false" show-icon />
      <div v-loading="previewLoading" class="control-material-preview"><MaterialPreview v-if="previewMaterial && !previewLoading" :key="preview?.id" :material="previewMaterial" :account="account" /><el-empty v-else-if="!previewLoading" description="平台暂未提供可预览的文件" :image-size="72" /></div>
      <p class="preview-detail">素材 ID：{{ preview?.id || '平台未返回' }}</p><template #footer><el-button @click="closePreview">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { DataAnalysis, Delete, Download, Filter, Refresh, Search, Setting, SwitchButton, VideoCamera, VideoPause, VideoPlay } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { actControlTask, listControlTasks, type ControlTask, type ControlTaskAction } from '@/features/delivery/deliveryApi';
import { CONTROL_TASK_METRICS as metrics, CONTROL_TASK_STATUS, taskActions, taskStatusText } from '@/features/delivery/controlTaskPresentation';
import type { StatisticsRange } from '@/features/delivery/statisticsRange';
import { useUserStore } from '@/stores/user';
import ControlTaskReport from './ControlTaskReport.vue';
import { insightMaterials } from '@/features/delivery/planInsightsApi';
import type { CreationMaterial } from '@/features/delivery/creationApi';
import MaterialPreview from '../create/MaterialPreview.vue';
const props = defineProps<{ planId: string; range: StatisticsRange; refreshToken: number; planStatus: string; accountActive: boolean; account?: { awemeName: string; avatar?: string } }>();
const user = useUserStore();
type TaskMaterial = { id?: string; name: string; posterUrl?: string; duration: number | null };
const safeUrl = (url?: string | null) => typeof url === 'string' && /^https?:\/\//i.test(url) ? url : undefined;
function taskMaterials(task: ControlTask): TaskMaterial[] {
  return (Array.isArray(task.material_list) ? task.material_list : []).filter(m => !!m && typeof m === 'object').map(m => ({ id: typeof m.material_id === 'string' && /^[1-9]\d*$/.test(m.material_id) ? m.material_id : undefined, name: typeof m.title === 'string' && m.title.trim() || '未命名素材', posterUrl: safeUrl(m.cover_image?.image_url) || safeUrl(m.cover_image?.url), duration: number(m.video_duration) }));
}
const preview = ref<TaskMaterial>(), previewMaterial = ref<CreationMaterial>(), previewLoading = ref(false), previewError = ref('');
let previewSerial = 0;
function closePreview() { previewSerial++; preview.value = undefined; previewMaterial.value = undefined; previewLoading.value = false; previewError.value = ''; }
async function viewMaterial(material: TaskMaterial) {
  closePreview(); preview.value = material;
  const fallback = (): CreationMaterial => ({ id: material.id || 'task-cover', productId: '', type: 'VIDEO', title: material.name, status: 'READY', posterUrl: material.posterUrl, titles: [] });
  previewMaterial.value = material.posterUrl ? fallback() : undefined;
  if (!material.id) { previewError.value = '平台未返回素材ID，暂不能查询播放地址'; return; }
  const turn = ++previewSerial, planId = props.planId;
  previewLoading.value = true;
  try {
    const result = await insightMaterials(planId, [...props.range], 'VIDEO');
    if (disposed || turn !== previewSerial) return;
    const matched = result.records.find(row => row.id === material.id && row.type === 'VIDEO');
    if (matched) previewMaterial.value = { ...fallback(), title: matched.name || material.name, previewUrl: safeUrl(matched.previewUrl), posterUrl: safeUrl(matched.posterUrl) || material.posterUrl };
    if (!previewMaterial.value?.previewUrl) previewError.value = '平台暂未返回此素材的播放地址，可查看缩略图';
  } catch (e: any) { if (!disposed && turn === previewSerial) previewError.value = e?.response?.data?.message || e?.message || '播放地址查询失败，可查看缩略图'; }
  finally { if (!disposed && turn === previewSerial) previewLoading.value = false; }
}
const busy = ref(''), actionError = ref(''), reportOpen = ref(false), reportTask = ref<ControlTask>(), reportKind = ref('一键起量');
const actionLabels = { PAUSE: '暂停调控', RESUME: '继续调控', END: '结束调控', DELETE: '删除任务' };
const actionIcons = { PAUSE: VideoPause, RESUME: VideoPlay, END: SwitchButton, DELETE: Delete };
function disabledReason(action: ControlTaskAction) {
  if (!user.trialActive) return '当前账户仅支持查看';
  if (!props.accountActive) return '广告账户不可用，请重新授权';
  if (!['DELIVERING', 'PAUSED'].includes(props.planStatus)) return '当前计划状态不支持操作';
  return action === 'RESUME' && props.planStatus !== 'DELIVERING' ? '请先启用当前投放计划，再继续调控' : '';
}
function viewReport(task: ControlTask) { reportTask.value = { ...task }; reportKind.value = kind.value; reportOpen.value = true; }
async function act(row: ControlTask, action: ControlTaskAction) {
  if (busy.value || disabledReason(action) || !taskActions(row.task_status).includes(action)) return;
  const planId = props.planId, tool = kind.value, state = row.task_status || '', context = serial;
  busy.value = `${row.id}:${action}`;
  try {
    if (action === 'END' || action === 'DELETE') {
      await ElMessageBox.confirm(`${action === 'END' ? '结束后该任务不能继续调控。' : '删除后任务将从列表移除；正在调控的任务也会停止。'}\n任务：${row.name || '未命名任务'}\nID：${row.id}`, `确认${actionLabels[action]}`, { type: 'warning', confirmButtonText: actionLabels[action], cancelButtonText: '取消' });
    }
    if (disposed || props.planId !== planId || kind.value !== tool || context !== serial || disabledReason(action)) return;
    actionError.value = '';
    const result = await actControlTask(planId, row.id, { kind: tool, action, expectedStatus: state });
    if (result.taskId !== row.id || result.status !== 'SUCCEEDED') throw new Error('未确认操作结果，请刷新任务状态核查，勿重复提交');
    if (!disposed && props.planId === planId && kind.value === tool) { ElMessage.success(`${actionLabels[action]}成功`); await load(); }
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && !disposed && props.planId === planId && kind.value === tool) actionError.value = e?.response?.data?.message || e?.message || '操作结果暂未确认，请刷新任务状态核查，勿重复提交';
  } finally { busy.value = ''; }
}
const tools = ['一键起量', '素材追投', '搜索抢首屏'];
const kind = ref('一键起量'), supported = computed(() => kind.value !== '搜索抢首屏');
const rows = ref<ControlTask[]>([]), loading = ref(false), error = ref('');
const searchDraft = ref(''), keyword = ref(''), status = ref(''), havingCost = ref('ALL'), createdRange = ref<StatisticsRange | null>(null);
const page = ref(1), pageSize = ref(10), sort = ref<{ prop?: string; order?: string }>({ prop: 'create_time', order: 'descending' });
const visibleColumns = ref(metrics.map(m => m.key)), columns = computed(() => metrics.filter(m => visibleColumns.value.includes(m.key)));
const extraFilters = computed(() => Number(havingCost.value !== 'ALL') + Number(!!createdRange.value));
const hasFilters = computed(() => !!keyword.value || !!status.value || !!extraFilters.value);
function number(v: unknown): number | null { return v == null || v === '' || !Number.isFinite(Number(v)) ? null : Number(v); }
function value(v: unknown, format: string) { const n = number(v); return n == null ? '—' : format === 'count' ? n.toLocaleString('zh-CN') : format === 'hours' ? n.toFixed(1) : `${n.toFixed(2)}${format === 'rate' ? '%' : ''}`; }
function timeText(time?: string) { return time ? time.replace('T', ' ') : '—'; }
function timeStamp(time?: string) { if (!time) return null; const stamp = Date.parse(/[Zz]$|[+-]\d\d:\d\d$/.test(time) ? time : time.replace(' ', 'T') + '+08:00'); return Number.isFinite(stamp) ? stamp : null; }
function estimatedEnd(task: ControlTask) { const stamp = timeStamp(task.create_time), hours = number(task.duration); if (stamp == null || hours == null || hours <= 0) return ''; return new Date(stamp + hours * 3600000).toLocaleString('sv-SE', { timeZone: 'Asia/Shanghai' }).slice(5, 16); }
function sortValue(row: ControlTask, prop: string) { return prop === 'create_time' ? timeStamp(row.create_time) : prop.startsWith('metrics.') ? number(row.metrics?.[prop.slice(8)]) : number(row[prop as 'budget' | 'duration']); }
const filtered = computed(() => {
  const text = keyword.value.toLocaleLowerCase(); const result = rows.value.filter(row => {
    if (status.value && row.task_status !== status.value) return false;
    if (text && !(row.name || '').toLocaleLowerCase().includes(text) && !row.id.includes(text)) return false;
    const cost = number(row.metrics?.stat_cost_for_roi2_assist);
    if (havingCost.value === 'YES' && (cost == null || cost <= 0)) return false;
    if (havingCost.value === 'NO' && cost !== 0) return false;
    const day = row.create_time?.slice(0, 10);
    return !createdRange.value || !!day && day >= createdRange.value[0] && day <= createdRange.value[1];
  });
  if (!sort.value.prop || !sort.value.order) return result;
  const prop = sort.value.prop, direction = sort.value.order === 'ascending' ? 1 : -1;
  return result.sort((a, b) => { const x = sortValue(a, prop), y = sortValue(b, prop); return x == null ? y == null ? 0 : 1 : y == null ? -1 : (x - y) * direction; });
});
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
function search() { keyword.value = searchDraft.value.trim(); page.value = 1; }
function clearFilters() { searchDraft.value = keyword.value = status.value = ''; havingCost.value = 'ALL'; createdRange.value = null; page.value = 1; }
function sortChange(v: { prop?: string; order?: string }) { sort.value = v; page.value = 1; }
let serial = 0, disposed = false;
async function load() {
  const turn = ++serial; closePreview(); rows.value = []; error.value = ''; loading.value = supported.value;
  if (!supported.value) return;
  try { const result = await listControlTasks(props.planId, kind.value, [...props.range]); if (!disposed && turn === serial) { rows.value = result; actionError.value = ''; page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value))); } }
  catch (e: any) { if (!disposed && turn === serial) error.value = e?.response?.data?.message || e?.message || '调控任务查询失败，请稍后重试'; }
  finally { if (!disposed && turn === serial) loading.value = false; }
}
function download() {
  const csv = (s: string) => `"${(/^[=+\-@\t\r]/.test(s) || /^\d{16,}$/.test(s) ? "'" : '') + s.replaceAll('"', '""')}"`;
  const materialsColumns = kind.value === '素材追投' ? ['素材名称', '素材ID'] : [];
  const lines = [['任务名称', '任务ID', ...materialsColumns, '调控状态', '调控预算（元）', '调控时长（小时）', '创建时间', '统计开始日期', '统计结束日期', ...columns.value.map(m => m.label)], ...filtered.value.map(row => [row.name || '', row.id, ...(materialsColumns.length ? [taskMaterials(row).map(m => m.name).join('；'), taskMaterials(row).map(m => m.id || '平台未返回').join('；')] : []), taskStatusText(row.task_status), value(row.budget, 'money'), value(row.duration, 'hours'), timeText(row.create_time), ...props.range, ...columns.value.map(m => value(row.metrics?.[m.key], m.kind))])];
  const url = URL.createObjectURL(new Blob(['\uFEFF' + lines.map(line => line.map(csv).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a'); a.href = url; a.download = `${kind.value}_${props.range[0]}_${props.range[1]}.csv`; a.click(); URL.revokeObjectURL(url);
}
watch(kind, () => { reportOpen.value = false; actionError.value = ''; clearFilters(); page.value = 1; void load(); });
watch(() => [props.planId, props.range[0], props.range[1], props.refreshToken], () => { reportOpen.value = false; actionError.value = ''; page.value = 1; void load(); });
onMounted(load); onBeforeUnmount(() => { disposed = true; serial++; closePreview(); });
</script>
<style scoped>
.task-material{display:flex;align-items:center;gap:12px;padding:7px 0}.task-material-cover{width:50px;height:66px;flex:none;position:relative;padding:0;border:0;border-radius:5px;background:#f2f3f8;overflow:hidden;cursor:pointer}.task-material-cover .el-image{width:100%;height:100%}.cover-placeholder{font-size:24px;color:#a6adc2}.cover-play{position:absolute;inset:0;margin:auto;font-size:23px;color:white;filter:drop-shadow(0 1px 4px #555)}.task-material-text{min-width:0;flex:1}.task-material-name{display:block;max-width:100%;text-align:left;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:0;background:none;border:0;color:#5b57ff;font:inherit;cursor:pointer}.task-material-text small{display:block;font-size:11px;color:#979db3;margin-top:5px;overflow-wrap:anywhere}.material-missing{color:#979db3;font-size:12px}.control-material-preview{min-height:150px;display:flex;justify-content:center;margin-top:16px}.control-material-preview :deep(.preview){margin:0;width:340px;max-width:100%;box-sizing:border-box}.preview-detail{font-size:12px;color:#8b90a5;overflow-wrap:anywhere}
.task-actions{display:flex;align-items:center;gap:10px}.task-actions .el-button{margin:0;padding:3px}.task-actions .el-button :deep(.el-icon){font-size:16px}
.control-toolbar,.control-options{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px}.control-tabs{display:flex;flex:none;background:#f6f6fb;border-radius:6px;padding:3px}.control-tabs button{background:transparent;border:1px solid transparent;border-radius:4px;padding:8px 13px;color:#72768c;cursor:pointer;white-space:nowrap}.control-tabs button.active{color:#5b57ff;border-color:#5b57ff;background:#fff}.control-toolbar>.el-select{width:180px}.control-toolbar>.el-input{width:255px}.control-options{justify-content:space-between}.filter-actions,.table-tools{display:flex;align-items:center;gap:8px}.table-tools>span{font-size:11px;color:#969bb0;margin-right:4px}.filter-count{background:#5b57ff;color:#fff;border-radius:10px;padding:1px 5px;font-size:11px;margin-left:5px}.task-name{padding:8px 0}.task-name strong{font-size:12px;font-weight:500;display:block;overflow-wrap:anywhere}.task-name small,.estimated-end{display:block;color:#979db3;font-size:11px;margin-top:5px}.task-status{display:flex;align-items:center;gap:6px;color:#7f849a}.task-status i{height:6px;width:6px;border-radius:50%;background:#b6bbcc;flex:none}.task-status.processing{color:#00a864}.task-status.processing i{background:#00b36a}.control-footer{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-top:16px}.control-footer p{color:#9297ab;font-size:11px;line-height:1.8;margin:0}.control-footer :deep(.el-pagination){margin:0}.plan-controls :deep(.el-alert){margin-bottom:14px}.extra-filters{display:flex;flex-direction:column;gap:12px}.extra-filters label{font-size:12px;color:#797e96}.extra-filters :deep(.el-date-editor){width:100%;box-sizing:border-box}.el-checkbox-group{display:flex;flex-direction:column;padding-top:10px;max-height:380px;overflow:auto}.unsupported-tool{text-align:center;color:#8e93a8;font-size:13px}.unsupported-tool .el-empty{padding-bottom:5px}
@media(max-width:700px){.control-toolbar>.el-select,.control-toolbar>.el-input{width:100%}.control-tabs{width:100%;box-sizing:border-box}.control-tabs button{flex:1;padding:8px}.control-options{gap:14px}.table-tools{flex-wrap:wrap}.control-footer{flex-direction:column;align-items:flex-start}.control-footer :deep(.el-pagination){max-width:100%;flex-wrap:wrap;row-gap:10px}}
</style>
<style>
.control-material-dialog{max-width:calc(100vw - 32px);margin-top:5vh!important}.control-material-dialog .el-dialog__body{max-height:72vh;overflow-y:auto}
</style>
