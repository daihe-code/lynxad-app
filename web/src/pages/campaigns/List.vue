<template>
  <div class="campaign-page">
    <div class="title-row">
      <div class="title-group"><h1>广告投放管理</h1><span class="platform-chip">抖音 · 巨量千川</span></div>
      <div class="title-actions">
        <el-popover :width="310" trigger="click" placement="bottom-end" popper-class="campaign-popover">
          <template #reference><button class="snapshot-label" type="button" aria-label="查看统计口径"><el-icon><Calendar /></el-icon>最新数据<span v-if="snapshotDate"> · {{ snapshotDate }}</span><el-icon><ArrowDown /></el-icon></button></template>
          <div class="popup-heading"><strong>统计口径</strong><el-icon><Calendar /></el-icon></div><div class="snapshot-info"><span>统计时间</span><strong>{{ snapshotDate || '暂无统计快照' }}</strong><p>概览和列表展示各计划最近一次同步的统计数据。</p><div class="snapshot-note">历史日期范围查询待接入</div></div>
        </el-popover>
        <el-button class="square-button" :loading="loading" aria-label="刷新数据" @click="load"><el-icon><Refresh /></el-icon></el-button>
      </div>
    </div>
    <el-alert v-if="!user.trialActive" class="trial-alert" type="warning" show-icon :closable="false" title="试用已结束，工作台只读。仍可查看历史计划与报表。" />
    <section class="panel overview" aria-labelledby="overview-title" v-loading="summaryLoading">
      <div class="section-heading"><h2 id="overview-title">广告投放数据概览</h2><span>最新统计快照</span></div>
      <div class="metrics">
        <article class="metric metric-cost"><span>广告消耗（元）</span><strong>{{ summary ? money(summary.cost) : '—' }}</strong><small>暂无昨日对比</small></article>
        <article class="metric metric-orders"><span>成交订单数（单）</span><strong>{{ summary ? summary.orders.toLocaleString('zh-CN') : '—' }}</strong><small>暂无昨日对比</small></article>
        <article class="metric metric-gmv"><span>成交金额（元）</span><strong>{{ summary ? money(summary.gmv) : '—' }}</strong><small>暂无昨日对比</small></article>
        <article class="metric metric-roi"><span>支付 ROI</span><strong>{{ summary && summary.cost > 0 ? roiText(summary.gmv / summary.cost) : '—' }}</strong><small>成交金额 / 广告消耗</small></article>
      </div>
    </section>
    <section class="panel plans-panel" aria-labelledby="plans-title">
      <div class="section-heading"><h2 id="plans-title">投放计划列表</h2><router-link class="analytics-link" to="/analytics">数据分析 <el-icon><ArrowRight /></el-icon></router-link></div>
      <div class="toolbar" :class="{ expanded: filtersExpanded }">
        <div class="toolbar-left">
          <el-button type="primary" :disabled="!user.trialActive" @click="$router.push('/campaigns/manage/create')"><el-icon><Plus /></el-icon>创建投放计划</el-button>
          <el-input v-model="keyword" class="search-input" clearable placeholder="搜索计划名称 / 商品名称 / 计划ID" aria-label="搜索计划名称或 ID"><template #prefix><el-icon><Search /></el-icon></template></el-input>
          <el-select v-model="status" class="status-select" placeholder="全部状态" aria-label="计划状态" popper-class="campaign-dropdown"><el-option label="全部状态" value="" /><el-option v-for="(label, value) in PLAN_STATUS" :key="value" :label="label" :value="value" /></el-select>
        </div>
        <div class="toolbar-right">
          <el-button :aria-expanded="filtersExpanded" aria-controls="campaign-extra-filters" @click="filtersExpanded = !filtersExpanded"><el-icon><Filter /></el-icon>更多筛选</el-button>
          <el-popover placement="bottom-end" :width="290" trigger="click" popper-class="campaign-popover">
            <template #reference><el-button class="square-button" aria-label="列表设置"><el-icon><Operation /></el-icon></el-button></template>
            <div class="popup-heading"><strong>列表设置</strong><span>即时生效</span></div>
            <div class="column-setting"><span>控调工具列<small>显示计划的快捷操作</small></span><el-switch v-model="showTools" aria-label="显示控调工具" /></div>
            <div class="column-setting"><span>每页条数</span><el-select v-model="pageSize" style="width: 124px" aria-label="每页条数" popper-class="campaign-dropdown"><el-option v-for="size in [10, 20, 50]" :key="size" :label="`${size} 条 / 页`" :value="size" /></el-select></div>
            <div class="column-setting"><span>行间距</span><div class="density-options"><button :class="{ active: !compact }" :aria-pressed="!compact" @click="compact = false">舒适</button><button :class="{ active: compact }" :aria-pressed="compact" @click="compact = true">紧凑</button></div></div>
            <p class="settings-hint">显示偏好会保存在当前浏览器中。</p>
          </el-popover>
          <el-tooltip :content="compact ? '切换为舒适行距' : '切换为紧凑行距'" popper-class="campaign-tooltip"><el-button class="square-button" :class="{ 'filter-active': compact }" aria-label="切换行距" @click="compact = !compact"><el-icon><Sort /></el-icon></el-button></el-tooltip>
          <el-tooltip :content="selectedIds.length ? `导出已选 ${selectedIds.length} 条计划` : '导出筛选结果'" popper-class="campaign-tooltip"><el-button class="square-button" :disabled="!filtered.length" aria-label="导出计划" @click="exportRows"><el-icon><Download /></el-icon></el-button></el-tooltip>
        </div>
      </div>
      <div v-if="filtersExpanded" id="campaign-extra-filters" class="extra-filters">
        <div class="inline-filter-group" role="group" aria-label="投放平台"><span>投放平台</span><button v-for="option in platformOptions" :key="option.value" type="button" :class="{ active: deliveryPlatform === option.value }" :aria-pressed="deliveryPlatform === option.value" @click="deliveryPlatform = deliveryPlatform === option.value ? '' : option.value">{{ option.label }}</button></div>
        <div class="inline-filter-group" role="group" aria-label="投放模式"><span>投放模式</span><button v-for="option in bidOptions" :key="option.value" type="button" :class="{ active: bidType === option.value }" :aria-pressed="bidType === option.value" @click="bidType = bidType === option.value ? '' : option.value">{{ option.label }}</button></div>
        <div class="inline-filter-group" role="group" aria-label="创建时间"><span>创建时间</span><button v-for="days in [7, 30]" :key="days" type="button" :class="{ active: createdWithin === days }" :aria-pressed="createdWithin === days" @click="createdWithin = createdWithin === days ? null : days">近 {{ days }} 天</button></div>
      </div>
      <div v-if="selectedIds.length" class="selection-bar">已选择 {{ selectedIds.length }} 条计划<el-button text type="primary" @click="selectedIds = []">取消选择</el-button><el-button text type="primary" @click="exportRows">导出已选</el-button></div>
      <div class="table-scroll" v-loading="loading">
        <table class="plan-table" :class="{ compact, 'without-tools': !showTools }">
          <colgroup><col style="width: 40px"><col style="width: 244px"><col style="width: 112px"><col style="width: 192px"><col style="width: 116px"><col style="width: 108px"><col style="width: 92px"><col style="width: 122px"><col style="width: 106px"><col v-if="showTools" style="width: 116px"><col style="width: 80px"></colgroup>
          <thead><tr>
            <th><el-checkbox :model-value="allPageSelected" :indeterminate="somePageSelected && !allPageSelected" aria-label="选择本页全部计划" @change="togglePageSelection" /></th>
            <th><button class="sort-heading" @click="toggleSort('name')">计划名称<el-icon :class="{ 'sort-active': sortField === 'name' }"><Sort /></el-icon></button></th><th>计划状态</th><th>投放设置</th><th>投放时间</th>
            <th v-for="column in metricColumns" :key="column.field"><button class="sort-heading" @click="toggleSort(column.field)">{{ column.label }}<el-icon :class="{ 'sort-active': sortField === column.field }"><Sort /></el-icon></button></th>
            <th v-if="showTools">控调工具</th><th>操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="row in pagedRows" :key="row.planId" :class="{ selected: selectedIds.includes(row.planId) }">
              <td><el-checkbox v-model="selectedIds" :value="row.planId" :aria-label="`选择计划 ${row.name}`" /></td>
              <td class="plan-info-cell">
                <router-link v-if="row.source !== 'EXTERNAL'" class="plan-name" :to="`/campaigns/${row.planId}`" :title="row.name">{{ row.name }}</router-link><span v-else class="plan-name" :title="row.name">{{ row.name }}</span>
                <div class="plan-id" :title="row.qcPlanId || row.planId">ID：{{ row.qcPlanId || row.planId }}</div>
                <div class="product-line">
                  <el-popover v-if="row.products?.length" placement="bottom-start" :width="330" trigger="click" popper-class="campaign-popover">
                    <template #reference><button class="product-trigger" :aria-label="`查看 ${row.name} 的 ${row.products.length} 件商品`"><span class="goods-thumb"><el-image v-if="row.products[0]?.imageUrl" :src="row.products[0].imageUrl" fit="cover"><template #error><el-icon><Picture /></el-icon></template></el-image><el-icon v-else><Picture /></el-icon></span>商品（{{ row.products.length }}）</button></template>
                    <div class="popup-heading"><strong>投放商品</strong><span>{{ row.products.length }} 件商品</span></div><div class="product-preview-list"><div v-for="product in row.products" :key="product.id" class="product-preview"><span class="product-thumbnail"><el-image v-if="product.imageUrl" :src="product.imageUrl" fit="cover"><template #error><el-icon><Picture /></el-icon></template></el-image><el-icon v-else><Goods /></el-icon></span><div><strong>{{ product.name }}</strong><span>ID：{{ product.id }}</span></div></div></div>
                  </el-popover>
                  <span v-else class="product-trigger muted"><span class="goods-thumb"><el-icon><Picture /></el-icon></span>商品（—）</span>
                  <span v-if="row.source === 'EXTERNAL'" class="source-tag external">外部</span>
                </div>
                <router-link v-if="alertsOf(row.planId).length" :to="`/campaigns/${row.planId}`" class="alert-link">{{ alertsOf(row.planId).map(ruleLabel).join(' · ') }}</router-link>
              </td>
              <td><span class="status-pill" :class="row.status.toLowerCase()"><i></i>{{ PLAN_STATUS[row.status] || row.status }}</span></td>
              <td class="settings-cell">
                <div><span>日预算</span><b>{{ budgetText(row.budget) }}<span v-if="row.budget != null"> 元</span></b><button v-if="canManage(row)" class="inline-edit" :aria-label="`调整 ${row.name} 的预算`" @click="openOperation('调整预算', row)"><el-icon><EditPen /></el-icon></button></div>
                <div class="roi-setting"><span>净成交ROI目标</span><b>{{ goalText(row.roi2Goal) }}</b><button v-if="canManage(row)" class="inline-edit" :aria-label="`调整 ${row.name} 的 ROI`" @click="openOperation('调整 ROI 目标', row)"><el-icon><EditPen /></el-icon></button></div>
                <div class="bid-mode"><span>{{ BID_TYPE[row.smartBidType || ''] || '投放方式未知' }}</span><button v-if="canManage(row) && alternateBidType(row)" class="bid-edit" :aria-label="`切换 ${row.name} 为${alternateBidType(row)?.label}`" @click="openOperation('切换投放方式', row, alternateBidType(row)?.value)"><el-icon><Switch /></el-icon><span>{{ alternateBidType(row)?.label }}</span></button></div>
              </td>
              <td class="schedule-cell"><template v-if="row.startDate"><span class="schedule-range" :title="`${row.startDate} ~ ${row.endDate || '不限'}`">{{ shortDate(row.startDate) }} ~ {{ row.endDate ? shortDate(row.endDate) : '不限' }}</span><small>{{ row.status === 'FINISHED' ? '已结束' : row.endDate ? remainingDays(row.endDate) : '持续投放' }}</small></template><span v-else class="muted">—</span></td>
              <td class="number-cell"><strong>{{ yuan(row.costYuan) }}</strong><div v-if="row.costYuan != null" class="metric-change-line"><MetricDelta :value="row.comparison?.costChangePct" percent :previous-ts="row.comparison?.previousSnapshotTs" label="消耗" /></div></td>
              <td class="number-cell"><strong>{{ row.convert == null ? '—' : row.convert.toLocaleString('zh-CN') }}<small v-if="row.convert != null">单</small></strong><div v-if="row.convert != null" class="metric-change-line"><MetricDelta :value="row.comparison?.convertChangePct" percent :previous-ts="row.comparison?.previousSnapshotTs" label="成交订单" /></div></td>
              <td class="number-cell gmv-cell"><strong>{{ yuan(row.gmvYuan) }}</strong></td>
              <td class="number-cell"><div class="roi-line"><strong :class="roiClass(row)">{{ roiText(row.roi) }}</strong><MetricDelta v-if="row.roi != null" :value="row.comparison?.roiChangePct" percent :previous-ts="row.comparison?.previousSnapshotTs" label="支付 ROI" /></div></td>
              <td v-if="showTools" class="tools-cell"><button v-for="tool in tools" :key="tool" :disabled="!canManage(row) || row.status !== 'DELIVERING'" @click="openOperation(tool, row)">{{ tool }}<span>+</span></button></td>
              <td class="actions-cell">
                <template v-if="row.source !== 'EXTERNAL'"><router-link :to="`/campaigns/${row.planId}/analysis`">查看详情</router-link><router-link :to="`/campaigns/${row.planId}/edit`">修改</router-link><button v-if="row.status === 'DELIVERING' || row.status === 'PAUSED'" :class="row.status === 'DELIVERING' ? 'pause-action' : 'enable-action'" :disabled="!canManage(row) || actingId === row.planId" @click="onAct(row)">{{ actingId === row.planId ? '处理中' : row.status === 'DELIVERING' ? '暂停' : '启用' }}</button></template>
                <el-tooltip v-else content="外部计划请前往千川查看和管理" popper-class="campaign-tooltip"><span class="muted">外部计划</span></el-tooltip>
              </td>
            </tr>
            <tr v-if="!pagedRows.length && !loading"><td :colspan="showTools ? 11 : 10" class="empty-state"><el-icon><Search /></el-icon><p>{{ loadFailed ? '计划加载失败，请刷新重试' : keyword || status || deliveryPlatform || bidType || createdWithin ? '没有符合条件的投放计划' : '暂无投放计划' }}</p><el-button v-if="keyword || status || deliveryPlatform || bidType || createdWithin" text type="primary" @click="resetFilters">清除筛选</el-button></td></tr>
          </tbody>
        </table>
      </div>
      <div class="pager"><span>共 {{ filtered.length }} 条记录</span><el-pagination background layout="prev, pager, next, jumper" :total="filtered.length" :page-size="pageSize" v-model:current-page="pageNum" /></div>
    </section>
    <CampaignOperationDialog v-model="operationOpen" :kind="operationKind" :plan="operationPlan" :initial-bid-type="operationBidType" />
    <el-dialog v-model="confirmOpen" :title="confirmPlan?.status === 'DELIVERING' ? '暂停计划提醒' : '启用投放计划'" width="460px" class="campaign-dialog campaign-confirm" modal-class="campaign-overlay" align-center :close-on-click-modal="false" :close-on-press-escape="!actingId" :show-close="!actingId" :before-close="closeConfirmation">
      <div class="confirmation-intro"><span class="confirmation-icon" :class="{ resume: confirmPlan?.status === 'PAUSED' }"><el-icon><component :is="confirmPlan?.status === 'DELIVERING' ? VideoPause : VideoPlay" /></el-icon></span><div><strong>{{ confirmPlan?.status === 'DELIVERING' ? '确认暂停该计划？' : '确认恢复该计划的投放？' }}</strong><p>{{ confirmPlan?.status === 'DELIVERING' ? '暂停后计划将停止投放，可在列表中重新启用。' : '启用后计划将恢复投放，请确认当前预算和目标。' }}</p></div></div>
      <div class="confirmation-plan"><strong>{{ confirmPlan?.name }}</strong><span>ID：{{ confirmPlan?.qcPlanId || confirmPlan?.planId }}</span><div><span>日预算 <b>{{ yuan(confirmPlan?.budget) }}</b></span><span>ROI 目标 <b>{{ roiText(confirmPlan?.roi2Goal) }}</b></span></div></div>
      <template #footer><el-button :disabled="!!actingId" @click="confirmOpen = false">取消</el-button><el-button type="primary" :loading="!!actingId" @click="confirmAction">{{ confirmPlan?.status === 'DELIVERING' ? '确认暂停' : '确认启用' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { ArrowDown, ArrowRight, Calendar, Download, EditPen, Filter, Goods, Operation, Picture, Plus, Refresh, Search, Sort, Switch, VideoPause, VideoPlay } from '@element-plus/icons-vue';
import CampaignOperationDialog from './components/CampaignOperationDialog.vue';
import MetricDelta from './components/MetricDelta.vue';
import { actPlan, listPlans, type PlanListVo } from '@/features/delivery/deliveryApi';
import { listActions, ruleLabel } from '@/features/agentops/agentopsApi';
import { useUserStore } from '@/stores/user';
import { listInsightReports } from '@/features/insight/insightApi';
import { BID_TYPE, PLAN_STATUS, roiText, yuan } from '@/utils/labels';

const user = useUserStore();
const loading = ref(false), summaryLoading = ref(false), loadFailed = ref(false);
const keyword = ref(''), status = ref(''), bidType = ref('');
const filtersExpanded = ref(false), deliveryPlatform = ref('ALL'), createdWithin = ref<number | null>(7);
const records = ref<PlanListVo[]>([]), selectedIds = ref<string[]>([]);
const openByPlan = ref<Record<string, string[]>>({});
const pageNum = ref(1), pageSize = ref(10), compact = ref(false), showTools = ref(true);
type SortField = 'costYuan' | 'gmvYuan' | 'convert' | 'roi';
const metricColumns: { field: SortField; label: string }[] = [{ field: 'costYuan', label: '广告消耗' }, { field: 'convert', label: '成交订单' }, { field: 'gmvYuan', label: '成交金额' }, { field: 'roi', label: '支付 ROI' }];
const sortField = ref<SortField | 'name' | ''>(''), sortDescending = ref(true);
const summary = ref<{ cost: number; gmv: number; orders: number } | null>(null);
const snapshotDate = ref(''), actingId = ref('');
const tools = ['一键起量', '素材追投', '搜索抢首屏'];
const operationOpen = ref(false), operationKind = ref(''), operationPlan = ref<PlanListVo | null>(null);
const operationBidType = ref<string>();
const confirmOpen = ref(false), confirmPlan = ref<PlanListVo | null>(null);
const platformOptions = [{ value: 'ALL', label: '商品全域投放' }, { value: 'STANDARD', label: '标准投放' }];
const bidOptions = [{ value: 'SMART_BID_CUSTOM', label: '控成本投放' }, { value: 'SMART_BID_CONSERVATIVE', label: '放量投放' }];
try { const settings = JSON.parse(localStorage.getItem('lynx.campaign.display') || '{}'); compact.value = settings.compact === true; showTools.value = settings.showTools !== false; if ([10, 20, 50].includes(settings.pageSize)) pageSize.value = settings.pageSize; } catch { /* Use defaults for invalid preferences. */ }
watch([compact, showTools, pageSize], () => { try { localStorage.setItem('lynx.campaign.display', JSON.stringify({ compact: compact.value, showTools: showTools.value, pageSize: pageSize.value })); } catch { /* Display remains usable when storage is unavailable. */ } });
const money = (value: number) => value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const budgetText = (value?: number | null) => value == null ? '—' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
const goalText = (value?: number | null) => value == null ? '—' : Number(value).toString();
const shortDate = (date: string) => date.slice(5, 10);
function alternateBidType(row: PlanListVo) { return row.smartBidType === 'SMART_BID_CONSERVATIVE' ? { value: 'SMART_BID_CUSTOM', label: '控成本投放' } : row.smartBidType === 'SMART_BID_CUSTOM' ? { value: 'SMART_BID_CONSERVATIVE', label: '放量投放' } : null; }
function roiClass(row: PlanListVo) { return row.roi != null && row.roi > 0 ? 'roi-good' : ''; }
const canManage = (row: PlanListVo) => user.trialActive && row.source !== 'EXTERNAL';
const alertsOf = (id: string) => openByPlan.value[id] || [];
const shanghaiDay = (date: Date) => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(date);
function createdInRange(row: PlanListVo) {
  if (!createdWithin.value) return true;
  if (!row.createdAt) return false;
  const created = new Date(row.createdAt);
  if (!Number.isFinite(created.getTime())) return false;
  const today = shanghaiDay(new Date());
  const firstDay = new Date(`${today}T00:00:00+08:00`).getTime() - (createdWithin.value - 1) * 86400000;
  const day = shanghaiDay(created);
  return day >= shanghaiDay(new Date(firstDay)) && day <= today;
}
const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  const rows = records.value.filter(r => (!q || [r.name, r.planId, r.qcPlanId || '', ...(r.products || []).map(p => p.name)].some(v => v.toLowerCase().includes(q))) && (!status.value || r.status === status.value) && (!deliveryPlatform.value || r.deliveryPlatform === deliveryPlatform.value) && (!bidType.value || r.smartBidType === bidType.value) && createdInRange(r));
  const field = sortField.value;
  if (field) rows.sort((a, b) => {
    if (field === 'name') return a.name.localeCompare(b.name, 'zh-CN', { numeric: true }) * (sortDescending.value ? -1 : 1);
    if (a[field] == null) return b[field] == null ? 0 : 1;
    if (b[field] == null) return -1;
    return (Number(a[field]) - Number(b[field])) * (sortDescending.value ? -1 : 1);
  });
  return rows;
});
const pagedRows = computed(() => filtered.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value));
const allPageSelected = computed(() => pagedRows.value.length > 0 && pagedRows.value.every(r => selectedIds.value.includes(r.planId)));
const somePageSelected = computed(() => pagedRows.value.some(r => selectedIds.value.includes(r.planId)));
watch([keyword, status, deliveryPlatform, bidType, createdWithin, pageSize], () => { pageNum.value = 1; selectedIds.value = []; });
watch(() => filtered.value.length, () => { pageNum.value = Math.min(pageNum.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value))); });
function resetFilters() { keyword.value = ''; status.value = ''; deliveryPlatform.value = ''; bidType.value = ''; createdWithin.value = null; }
function toggleSort(field: SortField | 'name') { sortDescending.value = sortField.value === field ? !sortDescending.value : field !== 'name'; sortField.value = field; }
function togglePageSelection(checked: boolean | string | number) {
  const ids = new Set(selectedIds.value);
  pagedRows.value.forEach(r => checked ? ids.add(r.planId) : ids.delete(r.planId));
  selectedIds.value = [...ids];
}
function remainingDays(date: string) {
  const now = new Date(); now.setHours(0, 0, 0, 0);
  const days = Math.round((new Date(`${date}T00:00:00`).getTime() - now.getTime()) / 86400000);
  return days < 0 ? '投放时间已结束' : days === 0 ? '今日结束' : `剩余 ${days} 天`;
}
function openOperation(title: string, row: PlanListVo, bidType?: string) { operationKind.value = title; operationPlan.value = row; operationBidType.value = bidType; operationOpen.value = true; }
async function load() {
  if (loading.value || summaryLoading.value) return;
  loading.value = true; summaryLoading.value = true; loadFailed.value = false;
  const plansTask = (async () => {
    try {
      const first = await listPlans(1, 100);
      const all = [...first.records];
      const count = Math.ceil(first.total / first.pageSize);
      for (let page = 2; page <= count; page++) all.push(...(await listPlans(page, first.pageSize)).records);
      records.value = [...new Map(all.map(r => [r.planId, r])).values()];
      selectedIds.value = selectedIds.value.filter(id => records.value.some(r => r.planId === id));
    } catch { loadFailed.value = true; records.value = []; selectedIds.value = []; }
    finally { loading.value = false; }
  })();
  const summaryTask = (async () => {
    try {
      const accounts = await listInsightReports('account');
      summary.value = accounts.reduce((s, r) => ({ cost: s.cost + Number(r.cost || 0), gmv: s.gmv + Number(r.gmv || 0), orders: s.orders + Number(r.convert || 0) }), { cost: 0, gmv: 0, orders: 0 });
      const timestamps = accounts.map(r => r.snapshotTs).filter((ts): ts is string => !!ts).sort();
      snapshotDate.value = timestamps.length ? new Date(timestamps[timestamps.length - 1]!).toLocaleDateString('zh-CN') : '';
    } catch { summary.value = null; snapshotDate.value = ''; }
    finally { summaryLoading.value = false; }
  })();
  const alertsTask = (async () => {
    const actions = await listActions().catch(() => []);
    const grouped: Record<string, string[]> = {};
    for (const row of actions) if (row.open) { const list = grouped[row.planId] ||= []; if (!list.includes(row.type)) list.push(row.type); }
    openByPlan.value = grouped;
  })();
  await Promise.all([plansTask, summaryTask, alertsTask]);
}
function onAct(row: PlanListVo) {
  if (actingId.value || !canManage(row)) return;
  confirmPlan.value = row; confirmOpen.value = true;
}
function closeConfirmation(done: () => void) { if (!actingId.value) done(); }
async function confirmAction() {
  const row = confirmPlan.value;
  if (!row || actingId.value || !canManage(row)) return;
  const label = row.status === 'DELIVERING' ? '暂停' : '启用';
  actingId.value = row.planId;
  try { await actPlan(row.planId, row.status === 'DELIVERING' ? 'DISABLE' : 'ENABLE'); confirmOpen.value = false; ElMessage.success(`计划已${label}`); await load(); }
  catch { /* API interceptor displays the error. */ }
  finally { actingId.value = ''; }
}
function exportRows() {
  const rows = selectedIds.value.length ? filtered.value.filter(r => selectedIds.value.includes(r.planId)) : filtered.value;
  // Neutralize spreadsheet formulas in names and IDs before producing CSV.
  const cell = (value: unknown) => { let s = value == null ? '' : String(value); if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`; return `"${s.replaceAll('"', '""')}"`; };
  const header = ['计划名称', '计划ID', '状态', '投放方式', '日预算', '净成交ROI目标', '开始日期', '结束日期', '广告消耗', '成交订单', '成交金额', '支付ROI'];
  const csv = [header, ...rows.map(r => [r.name, r.qcPlanId || r.planId, PLAN_STATUS[r.status] || r.status, BID_TYPE[r.smartBidType || ''], r.budget, r.roi2Goal, r.startDate, r.endDate, r.costYuan, r.convert, r.gmvYuan, r.roi])].map(row => row.map(cell).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8;' }));
  const link = document.createElement('a'); link.href = url; link.download = `投放计划-${new Date().toLocaleDateString('sv-SE')}.csv`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  ElMessage.success(`已导出 ${rows.length} 条计划`);
}
onMounted(load);
</script>

<style scoped>
.campaign-page { --el-color-primary: #575cf6; --el-color-primary-light-3: #9189f8; --el-color-primary-light-9: #f0eeff; --el-border-radius-base: 8px; color: #303044; }
.title-row, .title-group, .title-actions, .section-heading, .toolbar, .toolbar-left, .toolbar-right, .pager { display: flex; align-items: center; }
.title-row { justify-content: space-between; gap: 16px; margin: 8px 0 19px; }.title-group { gap: 14px; }
h1 { margin: 0; padding-right: 14px; border-right: 3px solid #575cf6; font-size: 24px; line-height: 24px; font-weight: 750; }.platform-chip { padding: 5px 12px; font-size: 12px; border-radius: 20px; color: #fff; background: #575cf6; }
.title-actions { gap: 10px; }.snapshot-label { display: flex; gap: 8px; align-items: center; background: #fff; padding: 10px 14px; border: 1px solid #e9e8f1; border-radius: 8px; font-size: 12px; color: #666679; }.trial-alert { margin-bottom: 20px; }
.panel { background: #fff; border: 1px solid #eeedf6; border-radius: 24px; padding: 24px 32px 28px; box-shadow: 0 2px 8px #24204003; }.overview { margin-bottom: 24px; padding-bottom: 24px; }.section-heading { justify-content: space-between; margin-bottom: 18px; }.overview .section-heading { margin-bottom: 16px; }
h2 { margin: 0; font-size: 18px; line-height: 24px; font-weight: 700; color: #252537; }.section-heading > span { color: #aaa8ba; font-size: 12px; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 20px; }.metric { border-radius: 14px; padding: 16px 22px; }.metric > span { display: block; color: #7e7b94; font-size: 13px; }.metric strong { display: block; font-size: 25px; font-weight: 750; color: #222234; margin: 8px 0; font-variant-numeric: tabular-nums; }.metric small { color: #9996ad; font-size: 11px; }
.metric-cost { background: #ebecfe; }.metric-orders { background: #f5f0f8; }.metric-gmv { background: #eaf0fd; }.metric-roi { background: #f4eef6; }.analytics-link { color: #8b87a3; font-size: 12px; display: flex; gap: 4px; align-items: center; }
.metric > span { line-height: 18px; }.metric strong { line-height: 30px; margin: 5px 0; }.metric small { display: block; line-height: 16px; }.actions-cell button.pause-action { color: #dc777a; }.actions-cell button.enable-action { color: #45aa79; }
.toolbar { justify-content: space-between; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }.toolbar-left, .toolbar-right { gap: 10px; }
.toolbar :deep(.el-button), .title-actions :deep(.el-button) { height: 38px; margin-left: 0; font-size: 12px; padding: 0 14px; }.toolbar :deep(.el-button .el-icon) { margin-right: 5px; font-size: 14px; }.toolbar :deep(.el-button--primary) { padding: 0 18px; font-size: 13px; box-shadow: 0 3px 8px #575cf618; }
.search-input { width: 300px; }.status-select { width: 134px; }.toolbar :deep(.el-input__wrapper), .toolbar :deep(.el-select__wrapper) { min-height: 38px; font-size: 12px; box-shadow: 0 0 0 1px #e9e8f1 inset; }
.toolbar :deep(.el-input__wrapper.is-focus), .toolbar :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px #575cf6 inset; }.toolbar :deep(.square-button), .title-actions :deep(.square-button) { width: 38px; padding: 0; }.toolbar :deep(.square-button .el-icon) { margin: 0; }
.filter-active { color: #575cf6; border-color: #bcb7fb; background: #f7f6ff; }.filter-dot { width: 5px; height: 5px; border-radius: 50%; background: #575cf6; margin-left: 4px; }.column-setting { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; font-size: 13px; }
.selection-bar { display: flex; align-items: center; gap: 12px; font-size: 12px; background: #f4f2ff; color: #575cf6; padding: 4px 12px; border-radius: 8px; margin-bottom: 12px; }.table-scroll { overflow-x: auto; }
.plan-table { border-collapse: collapse; width: 100%; min-width: 1328px; table-layout: fixed; text-align: left; }.plan-table.without-tools { min-width: 1212px; }
th { background: #f7f8fb; padding: 13px 8px; color: #8b899d; font-size: 12px; font-weight: 500; white-space: nowrap; }th:first-child { border-radius: 8px 0 0 8px; padding-left: 12px; }th:last-child { border-radius: 0 8px 8px 0; }
td { vertical-align: top; border-bottom: 1px solid #f0eff5; padding: 18px 8px; font-size: 12px; line-height: 20px; }td:first-child { padding-left: 12px; }tbody tr:hover, tbody tr.selected { background: #faf9ff; }.compact td { padding-top: 10px; padding-bottom: 10px; }
.plan-table :deep(.el-checkbox) { height: 18px; vertical-align: top; }.plan-table :deep(.el-checkbox__inner) { border-color: #dbd9e9; border-radius: 4px; }.plan-table :deep(.is-checked .el-checkbox__inner) { border-color: #575cf6; }
.sort-heading { display: flex; gap: 4px; align-items: center; border: 0; padding: 0; background: none; color: inherit; font: inherit; cursor: pointer; }.sort-heading .el-icon { font-size: 11px; color: #c2bfce; }.sort-heading .sort-active { color: #575cf6; }
.plan-name { display: block; max-width: 100%; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 13px; font-weight: 500; color: #55536a; }.plan-name:hover { color: #575cf6; }.plan-id { color: #aaa7b9; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-line { display: flex; gap: 8px; align-items: center; margin-top: 3px; }.product-trigger { display: inline-flex; align-items: center; gap: 4px; color: #88849d; font-size: 11px; background: none; border: 0; padding: 0; line-height: 20px; }.product-trigger:is(button) { cursor: pointer; }.product-trigger:is(button):hover { color: #575cf6; }
.source-tag { color: #7d70e3; background: #f0edff; font-size: 10px; border-radius: 4px; padding: 0 5px; line-height: 16px; }.source-tag.external { background: #f2f3f6; color: #9693a5; }.product-preview { display: flex; gap: 10px; align-items: center; padding: 6px 0; font-size: 12px; }.product-preview .el-image { width: 40px; height: 40px; border-radius: 6px; flex-shrink: 0; }.alert-link { display: block; color: #df726f; font-size: 11px; line-height: 16px; margin-top: 4px; }
.status-pill { display: inline-flex; gap: 5px; align-items: center; border-radius: 20px; font-size: 11px; line-height: 22px; padding: 0 9px; color: #9391a5; background: #f2f2f6; }.status-pill i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }.status-pill.delivering { background: #eaf8ee; color: #53ac6e; }.status-pill.paused { background: #fff4e9; color: #d5a367; }.status-pill.rejected { color: #d87272; background: #fff0f0; }.status-pill.pending_audit { color: #8680d2; background: #f0efff; }
.settings-cell { color: #878499; font-size: 11px; }.settings-cell > div { display: flex; align-items: center; gap: 2px; white-space: nowrap; }.settings-cell b { color: #625e77; font-weight: 500; }.settings-cell b.roi-goal { color: #45ab76; }.inline-edit { border: 0; background: none; color: #b0a6ed; cursor: pointer; padding: 0 4px; height: 18px; display: inline-flex; align-items: center; }.inline-edit:hover { color: #575cf6; }.bid-mode { color: #a5a0b8; }.bid-edit { opacity: 0; }tr:hover .bid-edit, .bid-edit:focus-visible { opacity: 1; }
.schedule-cell { color: #9b97ad; font-size: 11px; line-height: 19px; }.schedule-cell small { display: block; color: #b4b0c1; font-size: 10px; }.number-cell { color: #504c65; font-size: 14px; font-weight: 600; font-variant-numeric: tabular-nums; white-space: nowrap; }.number-cell small { color: #aba6ba; font-size: 10px; font-weight: 400; }.number-cell.roi-good { color: #46ad77; }
.tools-cell button, .actions-cell button, .actions-cell a { display: flex; align-items: center; justify-content: space-between; color: #8e83df; background: none; border: 0; padding: 0; font-size: 11px; line-height: 20px; cursor: pointer; }.tools-cell button { width: 85px; color: #9995ad; }.tools-cell button span { color: #a897ed; font-size: 14px; }.tools-cell button:hover, .actions-cell a:hover, .actions-cell button:hover { color: #575cf6; }.tools-cell button:disabled, .tools-cell button:disabled span, .actions-cell button:disabled { color: #ccc8d9; cursor: not-allowed; }.actions-cell { padding-left: 14px; }.muted { color: #b0acbd; }
.pager { justify-content: flex-end; gap: 18px; margin-top: 20px; color: #aaa5ba; font-size: 11px; }.pager :deep(.el-pagination) { font-size: 12px; --el-pagination-button-width: 30px; --el-pagination-button-height: 30px; }.pager :deep(.el-pagination button), .pager :deep(.el-pager li) { background: #fff; border: 1px solid #e9e6f3; border-radius: 6px; font-size: 12px; font-weight: 400; }.pager :deep(.el-pager li.is-active) { color: #575cf6; background: #f3f0ff; border-color: #d0c7ff; }.pager :deep(.el-pagination__jump) { margin-left: 8px; color: #aaa5ba; font-size: 11px; }
.empty-state { height: 180px; text-align: center; vertical-align: middle; color: #7c7f99; }.empty-state > .el-icon { font-size: 26px; color: #b7baf1; }.empty-state p { margin: 8px; }
/* Match the reference typography, including native controls and secondary text. */
.campaign-page { color: #4e5069; }
h1 { font-weight: 700; color: #1a1b2e; }h2 { color: #1a1b2e; }
.metric strong { font-size: 24px; font-weight: 700; color: #1a1b2e; }
.metric > span, .metric small, .section-heading > span { color: #7c7f99; }.metric small { font-size: 12px; }
.snapshot-label { cursor: pointer; font-size: 13px; height: 38px; color: #4e5069; }.snapshot-label:hover { border-color: #bfc1fc; }
.toolbar :deep(.el-button), .toolbar :deep(.el-input__wrapper), .toolbar :deep(.el-select__wrapper) { font-size: 13px; }
th { color: #7c7f99; padding-top: 12px; padding-bottom: 12px; }
td { font-size: 13px; line-height: 22px; padding-top: 14px; padding-bottom: 14px; }.compact td { padding-top: 8px; padding-bottom: 8px; }
.plan-name { font-size: 13px; font-weight: 400; color: #7c7f99; }.plan-id { font-size: 12px; color: #7c7f99; line-height: 20px; }.product-line { margin-top: 6px; }.product-trigger { font-size: 13px; color: #4e5069; }.source-tag { font-size: 11px; }
.status-pill { font-size: 12px; font-weight: 500; }.status-pill.delivering { color: #0ba05f; }.status-pill.paused { color: #cc8b19; }.status-pill.rejected { color: #d54c54; }
.settings-cell { font-size: 13px; color: #4e5069; }.settings-cell b { font-weight: 500; color: #4e5069; }.settings-cell b.roi-goal { color: #0ba05f; }.bid-mode { color: #4e5069; }.inline-edit { color: #575cf6; padding: 2px 5px; }.schedule-cell { color: #4e5069; font-size: 13px; line-height: 20px; }.schedule-cell small { color: #7c7f99; font-size: 12px; }
.number-cell { color: #1a1b2e; }.number-cell small { color: #7c7f99; font-size: 12px; }.number-cell.roi-good { color: #0ba05f; }
.tools-cell button, .actions-cell button, .actions-cell a { font-size: 13px; line-height: 22px; }.tools-cell button { color: #4e5069; }.tools-cell button span { color: #575cf6; }.actions-cell a, .actions-cell button { color: #4348db; }.actions-cell button.pause-action { color: #e5484d; }.actions-cell button.enable-action { color: #0ba05f; }.tools-cell button:disabled, .tools-cell button:disabled span, .actions-cell button:disabled { color: #a3a6bb; }
.muted { color: #7c7f99; }.pager { color: #7c7f99; font-size: 12px; }.pager :deep(.el-pagination__jump) { font-size: 12px; color: #7c7f99; }.analytics-link { color: #7c7f99; }.column-setting { padding: 14px 0; border-bottom: 1px solid #eeeff4; }.column-setting small { display: block; font-size: 11px; color: #7c7f99; margin-top: 5px; }
.toolbar.expanded { margin-bottom: 16px; }
.extra-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 12px 24px; border-top: 1px solid #eeeff4; padding-top: 14px; margin-bottom: 18px; }
.inline-filter-group { display: flex; align-items: center; gap: 12px; font-size: 12px; color: #7c7f99; }
.inline-filter-group > span { white-space: nowrap; }
.inline-filter-group button { height: 30px; padding: 0 13px; border: 1px solid #e6e8f2; border-radius: 16px; color: #4e5069; background: #fff; font: inherit; white-space: nowrap; cursor: pointer; transition: border-color .15s, background .15s; }
.inline-filter-group button:hover { border-color: #a2a5ff; }
.inline-filter-group button.active { color: #575cf6; border-color: #575cf6; background: #f0eeff; }
.inline-filter-group button:focus-visible { outline: 2px solid #575cf6; outline-offset: 2px; }
.density-options { display: flex; background: #f4f5f9; padding: 3px; border-radius: 8px; }.density-options button { border: 0; color: #7c7f99; background: transparent; border-radius: 6px; padding: 5px 10px; font-size: 12px; cursor: pointer; }.density-options button.active { background: #fff; color: #4348db; box-shadow: 0 1px 4px #1a1b2e12; }.settings-hint { font-size: 12px; color: #7c7f99; margin: 14px 0 0; line-height: 20px; }
.snapshot-info { font-size: 13px; }.snapshot-info > span { display: block; color: #7c7f99; font-size: 12px; }.snapshot-info strong { display: block; margin: 5px 0 12px; color: #4e5069; font-weight: 500; }.snapshot-info p { color: #7c7f99; font-size: 12px; line-height: 20px; }.snapshot-note { padding: 10px 12px; border-radius: 8px; background: #f7f8fe; color: #7c7f99; font-size: 12px; }
.product-preview-list { max-height: 300px; overflow-y: auto; }.product-preview { padding: 10px 0; gap: 12px; border-top: 1px solid #eeeff4; }.product-thumbnail { display: grid; place-items: center; width: 48px; height: 48px; background: #f4f5f9; border-radius: 8px; flex-shrink: 0; color: #a0a3bf; font-size: 20px; }.product-preview .el-image { width: 48px; height: 48px; border-radius: 8px; }.product-preview > div { min-width: 0; }.product-preview strong { display: block; font-size: 13px; font-weight: 500; color: #4e5069; line-height: 20px; overflow-wrap: anywhere; }.product-preview div span { display: block; font-size: 12px; color: #7c7f99; margin-top: 4px; overflow-wrap: anywhere; }
.confirmation-intro { display: flex; gap: 14px; align-items: flex-start; margin-bottom: 20px; }.confirmation-icon { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 12px; background: #fff3e9; color: #d79a3a; flex-shrink: 0; font-size: 22px; }.confirmation-icon.resume { background: #eaf8ee; color: #0ba05f; }.confirmation-intro strong { font-weight: 600; color: #1a1b2e; font-size: 15px; }.confirmation-intro p { color: #7c7f99; font-size: 13px; line-height: 22px; margin: 8px 0 0; }.confirmation-plan { border: 1px solid #e6e8f2; border-radius: 10px; padding: 14px; margin-bottom: 24px; background: #fafbfe; }.confirmation-plan > strong { display: block; font-size: 13px; font-weight: 500; color: #4e5069; overflow-wrap: anywhere; }.confirmation-plan > span { display: block; font-size: 12px; color: #7c7f99; margin-top: 5px; overflow-wrap: anywhere; }.confirmation-plan > div { display: flex; gap: 24px; margin-top: 12px; font-size: 12px; color: #7c7f99; }.confirmation-plan b { color: #4e5069; font-weight: 500; margin-left: 4px; }
.plan-info-cell .plan-name { color: #575cf6; line-height: 21px; }.plan-info-cell .plan-name:hover { color: #4348db; }.plan-info-cell .plan-id { margin-top: 1px; line-height: 21px; }.product-line { margin-top: 8px; min-height: 22px; }.product-trigger { gap: 8px; font-size: 13px; }.goods-thumb { display: inline-grid; place-items: center; width: 22px; height: 22px; background: #f7f8fa; border-radius: 4px; color: #9a9eb6; flex-shrink: 0; }.goods-thumb .el-image { width: 22px; height: 22px; border-radius: 4px; }.goods-thumb > .el-icon { font-size: 16px; }
.settings-cell > div { gap: 4px; line-height: 22px; }.settings-cell b { font-size: 14px; font-weight: 600; }.settings-cell .roi-setting, .settings-cell .roi-setting b { color: #0ba05f; font-size: 13px; }.settings-cell .bid-mode { display: flex; gap: 8px; }
.bid-edit { display: inline-flex; align-items: center; gap: 7px; padding: 0; border: 0; color: #575cf6; background: none; cursor: pointer; font-size: 12px; line-height: 22px; opacity: 0; pointer-events: none; transition: opacity .15s; }.bid-edit .el-icon { font-size: 14px; }tr:hover .bid-edit, tr:focus-within .bid-edit { opacity: 1; pointer-events: auto; }.bid-edit:focus-visible { opacity: 1; pointer-events: auto; }
.schedule-range { display: block; white-space: nowrap; }.schedule-cell small { display: block; margin-top: 1px; line-height: 20px; }
.number-cell strong { font: inherit; font-weight: 600; line-height: 22px; }.number-cell.gmv-cell { color: #4e5069; }.number-cell small { font-weight: 400; }.metric-change-line { display: flex; align-items: center; margin-top: 1px; min-height: 20px; }.roi-line { display: flex; align-items: baseline; flex-wrap: wrap; column-gap: 8px; }.roi-line strong.roi-good { color: #0ba05f; }.roi-line strong.roi-low { color: #e5484d; }
@media (max-width: 1100px) { .toolbar-left { flex-wrap: wrap; }.search-input { width: 250px; }.panel { padding: 24px; }.metrics { gap: 12px; }.metric { padding: 16px; }.metric strong { font-size: 22px; } }
@media (max-width: 700px) { .title-row { align-items: flex-start; flex-direction: column; gap: 16px; }.title-group { flex-wrap: wrap; gap: 12px; }h1 { font-size: 21px; }.panel { padding: 20px 16px; border-radius: 18px; }.metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }.metric strong { font-size: 20px; }.toolbar-left { width: 100%; }.search-input { width: 100%; order: 3; }.toolbar-right { flex-wrap: wrap; }.pager { flex-wrap: wrap; justify-content: flex-start; }.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 10px; } }
.plan-info-cell .plan-name { color: #7c7f99; }
tr:hover .plan-info-cell .plan-name, tr:focus-within .plan-info-cell .plan-name { color: #575cf6; }
.goods-thumb { width: 0; opacity: 0; overflow: hidden; transition: width .18s, opacity .18s; }
.product-trigger { gap: 0; }
tr:hover .goods-thumb, tr:focus-within .goods-thumb { width: 26px; height: 26px; opacity: 1; }
tr:hover .product-trigger, tr:focus-within .product-trigger { gap: 8px; }
.goods-thumb .el-image { width: 26px; height: 26px; }
.bid-edit { max-width: 0; opacity: 0; overflow: hidden; padding: 0; transition: max-width .18s, opacity .18s; }
tr:hover .bid-edit, tr:focus-within .bid-edit { max-width: 120px; opacity: 1; }
@media (hover: none) { .goods-thumb { width: 26px; height: 26px; opacity: 1; } .product-trigger { gap: 8px; } .bid-edit { max-width: 120px; opacity: 1; } }
</style>
