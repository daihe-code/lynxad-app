<template>
  <div class="campaign-page">
    <div class="title-row">
      <div class="title-group"><h1>广告投放管理</h1><span class="platform-chip">抖音 · 巨量千川</span></div>
      <div class="title-actions">
        <div class="statistics-picker">
          <el-date-picker v-model="rangeDraft" type="daterange" value-format="YYYY-MM-DD" format="YYYY-MM-DD" range-separator="至" start-placeholder="统计开始日期" end-placeholder="统计结束日期" :clearable="false" :shortcuts="rangeShortcuts" :disabled-date="disabledStatisticsDate" popper-class="campaign-date-picker" @calendar-change="onRangeCalendar" @visible-change="onRangeVisible" @change="applyStatisticsRange" />
        </div>
        <el-button class="square-button" :loading="syncing" aria-label="刷新数据" @click="syncRemote"><el-icon><Refresh /></el-icon></el-button>
      </div>
    </div>
    <el-alert v-if="!user.trialActive" class="trial-alert" type="warning" show-icon :closable="false" title="试用已结束，工作台只读。仍可查看历史计划与报表。" />
    <el-alert v-if="loadFailed && records.length" type="warning" :closable="false" title="列表加载失败，当前保留上次加载的数据，请重试" /><el-alert v-if="syncWarning" type="warning" show-icon :closable="false" :title="syncWarning" />
    <section class="panel overview" aria-labelledby="overview-title" v-loading="summaryLoading">
      <div class="section-heading"><h2 id="overview-title">广告投放数据概览</h2></div>
      <div class="metrics">
        <article class="metric metric-cost"><span>整体消耗(元)</span><strong>{{ summary?.cost != null ? money(summary.cost) : '—' }}</strong><small>与列表采用相同数据范围</small></article>
        <article class="metric metric-orders"><span>整体成交订单数</span><strong>{{ summary?.orders != null ? summary.orders.toLocaleString('zh-CN') : '—' }}</strong><small>与列表采用相同数据范围</small></article>
        <article class="metric metric-gmv"><span>整体成交金额(元)</span><strong>{{ summary?.gmv != null ? money(summary.gmv) : '—' }}</strong><small>与列表采用相同数据范围</small></article>
        <article class="metric metric-roi"><span>整体支付ROI</span><strong>{{ roiText(summary?.roi) }}</strong><small>汇总成交金额 / 汇总消耗</small></article>
      </div>
    </section>
    <section class="panel plans-panel" aria-labelledby="plans-title">
      <div class="section-heading"><h2 id="plans-title">投放计划列表</h2></div>
      <div class="toolbar" :class="{ expanded: filtersExpanded }">
        <div class="toolbar-left">
          <el-button type="primary" :disabled="!user.trialActive" @click="startCreate"><el-icon><Plus /></el-icon>创建投放计划</el-button>
          <el-input v-model="keyword" class="search-input" clearable placeholder="搜索计划名称 / 商品名称 / 计划ID" aria-label="搜索计划名称或 ID"><template #prefix><el-icon><Search /></el-icon></template></el-input>
          <el-cascader ref="statusPicker" v-model="status" class="status-select" :options="planStatusOptions" :props="{ emitPath: false, checkStrictly: true, expandTrigger: 'click' }" :show-all-levels="false" clearable placeholder="全部状态" aria-label="计划状态" popper-class="campaign-status-menu" @change="closeStatusFilter">
            <template #default="{ data }"><span class="status-option-label" @click="selectStatusLabel(data, $event)">{{ data.label }}</span></template>
          </el-cascader>
          <el-select v-model="accountId" class="status-select" placeholder="全部广告账户" aria-label="广告账户" popper-class="campaign-dropdown"><el-option label="全部广告账户" value="" /><el-option v-for="account in accounts" :key="account.accountId" :label="account.accountName" :value="account.accountId" /></el-select>
        </div>
        <div class="toolbar-right">
          <el-button :aria-expanded="filtersExpanded" aria-controls="campaign-extra-filters" @click="filtersExpanded = !filtersExpanded"><el-icon><Filter /></el-icon>更多筛选</el-button>
          <el-popover placement="bottom-end" :width="290" trigger="click" popper-class="campaign-popover">
            <template #reference><el-button class="square-button" aria-label="列表设置"><el-icon><Operation /></el-icon></el-button></template>
            <div class="popup-heading"><strong>列表设置</strong><span>即时生效</span></div>
            <div class="column-setting"><span>调控工具列<small>显示计划的快捷操作</small></span><el-switch v-model="showTools" aria-label="显示调控工具" /></div>
            <div class="column-setting"><span>每页条数</span><el-select v-model="pageSize" style="width: 124px" aria-label="每页条数" popper-class="campaign-dropdown"><el-option v-for="size in [10, 20, 50]" :key="size" :label="`${size} 条 / 页`" :value="size" /></el-select></div>
            <div class="column-setting"><span>行间距</span><div class="density-options"><button :class="{ active: !compact }" :aria-pressed="!compact" @click="compact = false">舒适</button><button :class="{ active: compact }" :aria-pressed="compact" @click="compact = true">紧凑</button></div></div>
            <p class="settings-hint">显示偏好会保存在当前浏览器中。</p>
          </el-popover>
          <el-tooltip :content="compact ? '切换为舒适行距' : '切换为紧凑行距'" popper-class="campaign-tooltip"><el-button class="square-button" :class="{ 'filter-active': compact }" aria-label="切换行距" @click="compact = !compact"><el-icon><Sort /></el-icon></el-button></el-tooltip>
          <el-tooltip content="导出筛选结果" popper-class="campaign-tooltip"><el-button class="square-button" :disabled="!filtered.length" aria-label="导出计划" @click="exportRows"><el-icon><Download /></el-icon></el-button></el-tooltip>
        </div>
      </div>
      <div v-if="filtersExpanded" id="campaign-extra-filters" class="extra-filters">
        <div class="inline-filter-group" role="group" aria-label="投放平台"><span>投放平台</span><button v-for="option in platformOptions" :key="option.value" type="button" :class="{ active: deliveryPlatform === option.value }" :aria-pressed="deliveryPlatform === option.value" @click="deliveryPlatform = deliveryPlatform === option.value ? '' : option.value">{{ option.label }}</button></div>
        <div class="inline-filter-group" role="group" aria-label="投放模式"><span>投放模式</span><button v-for="option in bidOptions" :key="option.value" type="button" :class="{ active: bidType === option.value }" :aria-pressed="bidType === option.value" @click="bidType = bidType === option.value ? '' : option.value">{{ option.label }}</button></div>
        <div class="inline-filter-group" role="group" aria-label="创建时间"><span>创建时间</span><button v-for="days in [7, 30]" :key="days" type="button" :class="{ active: createdWithin === days }" :aria-pressed="createdWithin === days" @click="createdWithin = createdWithin === days ? null : days">近 {{ days }} 天</button></div>
      </div>
      <div class="table-scroll" v-loading="loading">
        <table class="plan-table" :class="{ compact, 'without-tools': !showTools }">
          <colgroup><col style="width: 320px"><col style="width: 160px"><col style="width: 192px"><col style="width: 192px"><col style="width: 108px"><col style="width: 92px"><col style="width: 122px"><col style="width: 106px"><col v-if="showTools" style="width: 136px"><col style="width: 80px"></colgroup>
          <thead><tr>
            <th><button class="sort-heading" @click="toggleSort('name')">计划名称<el-icon :class="{ 'sort-active': sortField === 'name' }"><Sort /></el-icon></button></th><th>计划状态</th><th>投放设置</th><th>投放时间</th>
            <th v-for="column in metricColumns" :key="column.field"><button class="sort-heading" @click="toggleSort(column.field)">{{ column.label }}<el-icon :class="{ 'sort-active': sortField === column.field }"><Sort /></el-icon></button></th>
            <th v-if="showTools">调控工具</th><th>操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="row in pagedRows" :key="row.planId" >
              <td class="plan-info-cell">
                  <span v-if="isDeleted(row)" class="plan-name" :title="row.name">{{ row.name }}</span>
                  <router-link v-else class="plan-name" :to="isDraft(row) ? `/campaigns/${row.planId}/edit` : `/campaigns/${row.planId}`" :title="row.name">{{ row.name }}</router-link>
                <div class="plan-id" :title="row.qcPlanId || row.planId">ID：{{ row.qcPlanId || row.planId }}</div>
                <PlanContentSummary :plan="row" :account="accounts.find(account => account.accountId === row.accountId)" :disabled="isDeleted(row)" :products-link="isDraft(row) ? `/campaigns/${row.planId}/edit?step=1` : `/campaigns/${row.planId}?step=1`" :materials-link="`/campaigns/${row.planId}?step=3`" />
                <router-link v-if="!isDeleted(row) && alertsOf(row.planId).length" :to="`/campaigns/${row.planId}`" class="alert-link">{{ alertsOf(row.planId).map(ruleLabel).join(' · ') }}</router-link>
              </td>
              <td><span class="status-pill" :class="planStatusDisplay(row.status,row.qcStatus).group.toLowerCase()" :title="`${planStatusLabel(row.status, row.qcStatus)}（${row.qcStatus || row.status}）`"><i></i><span class="status-pill__label">{{ planStatusLabel(row.status, row.qcStatus) }}</span></span><router-link v-if="row.status === 'PENDING_AUDIT' && row.review && row.review.state !== 'WAITING'" class="review-delay-link" :title="row.review.message" :to="`/campaigns/${row.planId}/result`">{{ row.review.state === 'DELAYED' ? '审核等待较久' : '最新结果待查询' }}</router-link></td>
              <td class="settings-cell">
                <div><span>日预算</span><b>{{ budgetText(row.budget) }}<span v-if="row.budget != null"> 元</span></b><button v-if="canManage(row)" class="inline-edit" :aria-label="`调整 ${row.name} 的预算`" @click="openOperation('调整预算', row)"><el-icon><EditPen /></el-icon></button></div>
                <div v-if="row.smartBidType === 'SMART_BID_CONSERVATIVE'" class="roi-setting" :title="row.roiEstimate ? '千川对当前日预算的预估，非固定ROI目标，实际效果以平台为准' : '放量投放不设置固定ROI目标，平台暂无当前预算的ROI预估'"><span>预估净成交ROI</span><b>{{ estimateText(row.roiEstimate) }}</b></div>
                <div v-else class="roi-setting"><span>净成交ROI目标</span><b>{{ goalText(row.roi2Goal) }}</b><button v-if="canManage(row) && row.smartBidType === 'SMART_BID_CUSTOM'" class="inline-edit" :aria-label="`调整 ${row.name} 的 ROI`" @click="openOperation('调整 ROI 目标', row)"><el-icon><EditPen /></el-icon></button></div>
                <div class="bid-mode">
                  <span>{{ BID_TYPE[row.smartBidType || ''] || '投放方式未知' }}</span>
                  <button v-if="row.qcPlanId && canManage(row) && row.canTransfer !== false" type="button" class="inline-edit transfer-link" :class="{ 'transfer-pending': transferringId === row.planId }" :disabled="!!transferringId || !!actingId" :aria-busy="transferringId === row.planId" :aria-label="`转投 ${row.name}`" :title="PUBLISHED_BID_MODE_HINT" @click="onTransfer(row)">{{ transferringId === row.planId ? '切换中…' : '↔ 转投' }}</button>
                  <router-link v-else-if="canSwitchDraft(row)" class="inline-edit" :to="`/campaigns/${row.planId}/edit?step=2`" :aria-label="`切换 ${row.name} 的投放方式`"><el-icon><EditPen /></el-icon></router-link>
                </div>
              </td>
              <td class="schedule-cell"><template v-if="row.startDate"><span class="schedule-range" :title="`${row.startDate} ~ ${row.endDate || '不限'}`">{{ fullDate(row.startDate) }} ~ {{ row.endDate ? fullDate(row.endDate) : '不限' }}</span><small>{{ row.status === 'FINISHED' ? '已结束' : row.endDate ? remainingDays(row.endDate) : '持续投放' }}</small></template><span v-else class="muted">—</span></td>
              <td class="number-cell"><strong>{{ yuan(row.costYuan) }}</strong><small v-if="row.historicalMetrics && row.statisticsDate" class="statistics-date" :title="`本行消耗、订单、成交金额与ROI均为 ${row.statisticsDate} 的报表，今日暂无有效报表`">{{ row.statisticsDate }} 数据</small><div v-if="(row.costYuan != null || row.comparison) && !row.historicalMetrics" class="metric-change-line"><MetricDelta v-bind="deltaProps(row, 'cost')" label="消耗" /></div></td>
              <td class="number-cell"><strong>{{ row.convert == null ? '—' : row.convert.toLocaleString('zh-CN') }}<small v-if="row.convert != null">单</small></strong><div v-if="(row.convert != null || row.comparison) && !row.historicalMetrics" class="metric-change-line"><MetricDelta v-bind="deltaProps(row, 'orders')" label="成交订单" /></div></td>
              <td class="number-cell gmv-cell"><strong>{{ yuan(row.gmvYuan) }}</strong><div v-if="(row.gmvYuan != null || row.comparison) && !row.historicalMetrics" class="metric-change-line"><MetricDelta v-bind="deltaProps(row, 'gmv')" label="成交金额" /></div></td>
              <td class="number-cell"><div class="roi-line"><strong :class="roiClass(row)">{{ roiText(row.roi) }}</strong></div><div v-if="(row.roi != null || row.comparison) && !row.historicalMetrics" class="metric-change-line"><MetricDelta v-bind="deltaProps(row, 'roi')" label="支付 ROI" /></div></td>
              <td v-if="showTools" class="tools-cell">
                <div v-for="tool in tools" :key="tool" class="tool-shortcuts">
                  <button class="tool-name" :disabled="!canManage(row) || !row.canControl" @click="openOperation(tool, row)">{{ tool }}</button>
                  <el-tooltip v-if="canViewToolReport(row, tool)" :content="`查看${tool}报表`"><button type="button" class="tool-report" :aria-label="`查看${tool}报表 ${row.qcPlanId}`" @click="openToolReport(row, tool)"><el-icon><DataAnalysis /></el-icon></button></el-tooltip>
                  <span v-else aria-hidden="true" />
                  <button class="tool-add" :disabled="!canManage(row) || !row.canControl" :aria-label="`创建${tool}任务 ${row.qcPlanId || row.planId}`" @click="openOperation(tool, row)">+</button>
                </div>
                <div v-if="!isDeleted(row)" class="tool-shortcuts">
                  <router-link class="tool-name tool-monitor" :to="insightsLink(row.planId, 'optimization')" :aria-label="`打开 ${row.name} 的自动盯盘`">自动盯盘</router-link>
                </div>
              </td>
              <td class="actions-cell">
                <router-link v-if="isDraft(row)" :to="`/campaigns/${row.planId}/edit`">继续编辑</router-link>
                <span v-else-if="isDeleted(row)" class="muted">已删除</span>
                <template v-else>
                  <router-link :to="`/campaigns/${row.planId}`">查看详情</router-link>
                  <router-link v-if="canManage(row) || (row.source !== 'EXTERNAL' && row.status === 'REJECTED')" :to="`/campaigns/${row.planId}/edit`">修改</router-link>
                  <button v-if="row.status === 'DELIVERING' || row.status === 'PAUSED'" :class="row.status === 'DELIVERING' ? 'pause-action' : 'enable-action'" :disabled="!canManage(row) || actingId === row.planId" @click="onAct(row)">{{ actingId === row.planId ? '处理中' : row.status === 'DELIVERING' ? '暂停' : '启用' }}</button>
                </template>
                <button type="button" v-if="!isDeleted(row)" class="delete-action" :disabled="!canDelete(row) || !!deletingId || !!actingId || !!transferringId" :aria-label="`删除 ${row.name}`" @click="onDelete(row)">{{ deletingId === row.planId ? '删除中…' : '删除' }}</button>
              </td>
            </tr>
            <tr v-if="!pagedRows.length && !loading"><td :colspan="showTools ? 10 : 9" class="empty-state"><el-icon><Search /></el-icon><p>{{ loadFailed ? '计划加载失败，请刷新重试' : keyword || status || deliveryPlatform || bidType || createdWithin ? '没有符合条件的投放计划' : '暂无投放计划' }}</p><el-button v-if="keyword || status || deliveryPlatform || bidType || createdWithin" text type="primary" @click="resetFilters">清除筛选</el-button></td></tr>
          </tbody>
        </table>
      </div>
      <div class="pager"><span>共 {{ total }} 条记录</span><el-pagination background layout="prev, pager, next, jumper" :total="total" :page-size="pageSize" v-model:current-page="pageNum" /></div>
    </section>
    <CampaignOperationDialog v-model="operationOpen" :kind="operationKind" :plan="operationPlan" @saved="load" />
    <el-dialog v-model="confirmOpen" :title="confirmPlan?.status === 'DELIVERING' ? '暂停计划提醒' : '启用投放计划'" width="460px" class="campaign-dialog campaign-confirm" modal-class="campaign-overlay" align-center :close-on-click-modal="false" :close-on-press-escape="!actingId" :show-close="!actingId" :before-close="closeConfirmation">
      <div class="confirmation-intro"><span class="confirmation-icon" :class="{ resume: confirmPlan?.status === 'PAUSED' }"><el-icon><component :is="confirmPlan?.status === 'DELIVERING' ? VideoPause : VideoPlay" /></el-icon></span><div><strong>{{ confirmPlan?.status === 'DELIVERING' ? '确认暂停该计划？' : '确认恢复该计划的投放？' }}</strong><p>{{ confirmPlan?.status === 'DELIVERING' ? '暂停后计划将停止投放，可在列表中重新启用。' : '启用后计划将恢复投放；如有关联的转投计划，会先暂停另一种投放方式。请确认当前预算和目标。' }}</p></div></div>
      <div class="confirmation-plan"><strong>{{ confirmPlan?.name }}</strong><span>ID：{{ confirmPlan?.qcPlanId || confirmPlan?.planId }}</span><div><span>日预算 <b>{{ yuan(confirmPlan?.budget) }}</b></span><span>{{ confirmPlan?.smartBidType === 'SMART_BID_CONSERVATIVE' ? '预估净成交ROI' : 'ROI 目标' }} <b>{{ confirmPlan?.smartBidType === 'SMART_BID_CONSERVATIVE' ? estimateText(confirmPlan?.roiEstimate) : roiText(confirmPlan?.roi2Goal) }}</b></span></div></div>
      <template #footer><el-button :disabled="!!actingId" @click="confirmOpen = false">取消</el-button><el-button type="primary" :loading="!!actingId" @click="confirmAction">{{ confirmPlan?.status === 'DELIVERING' ? '确认暂停' : '确认启用' }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="deleteOpen" title="删除投放计划" width="460px" class="campaign-dialog campaign-confirm" modal-class="campaign-overlay" align-center :close-on-click-modal="false" :close-on-press-escape="!deletingId" :show-close="!deletingId" :before-close="closeDeleteConfirmation">
      <div class="confirmation-intro"><span class="confirmation-icon delete-icon"><el-icon><Delete /></el-icon></span><div><strong>确认删除该计划？</strong><p>{{ deleteTarget?.qcPlanId ? '计划将从千川和当前列表中删除，删除后无法恢复。' : '该草稿将从列表中删除，删除后无法恢复。' }}仅删除当前计划，关联的转投计划和共用素材会保留。</p></div></div>
      <div class="confirmation-plan"><strong>{{ deleteTarget?.name }}</strong><span>ID：{{ deleteTarget?.qcPlanId || deleteTarget?.planId }}</span><div><span>{{ BID_TYPE[deleteTarget?.smartBidType || ''] || '未设置投放方式' }}</span><span>{{ planStatusLabel(deleteTarget?.status || '') }}</span></div></div>
      <template #footer><el-button :disabled="!!deletingId" @click="deleteOpen = false">取消</el-button><el-button type="danger" :loading="!!deletingId" @click="confirmDelete">确认删除</el-button></template>
    </el-dialog>
    <ControlTaskReport v-model="quickReportOpen" :plan-id="quickReportPlan?.planId || ''" :plan-name="quickReportPlan?.name" :task="quickReportTask" :tasks="quickReportTasks" :tasks-loading="quickReportLoading" :tasks-error="quickReportError" :kind="quickReportKind" :initial-range="quickReportRange" @select-task="selectQuickReportTask" @reload-tasks="loadQuickReportTasks" />
    <el-drawer :model-value="!!insightsPlanId" :with-header="false" size="min(1180px, 80vw)" class="plan-insights-drawer" destroy-on-close @update:model-value="onInsightsVisible">
      <button type="button" class="insights-close" aria-label="关闭计划运营面板" @click="closeInsights">×</button>
      <PlanInsights v-if="insightsPlanId" :key="insightsPlanId" :plan-id="insightsPlanId" :tab="insightsTab" :initial-range="insightsRange" @tab="setInsightsTab" @range="setInsightsRange" @changed="load()" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, type CascaderInstance } from 'element-plus';
import { ArrowDown, Calendar, DataAnalysis, Delete, Download, EditPen, Filter, Operation, Plus, Refresh, Search, Sort, VideoPause, VideoPlay } from '@element-plus/icons-vue';
import PlanContentSummary from './components/PlanContentSummary.vue';
import CampaignOperationDialog from './components/CampaignOperationDialog.vue';
import MetricDelta from './components/MetricDelta.vue';
import PlanInsights from './PlanInsights.vue';
import ControlTaskReport from './components/ControlTaskReport.vue';
import { actPlan, deletePlan, getPlanTransfer, listControlTasks, listPlans, switchTransferredPlan, syncPlans, type ControlTask, type PlanListVo, type PageResult } from '@/features/delivery/deliveryApi';
import { listActions, ruleLabel } from '@/features/agentops/agentopsApi';
import { useUserStore } from '@/stores/user';
import { useWizardStore } from '@/stores/wizard';
import { listAccounts, type AccountVo } from '@/features/identity/oauthApi';
import { BID_TYPE, PUBLISHED_BID_MODE_HINT, planStatusLabel, roiText, yuan } from '@/utils/labels';
import { planStatusOptions, planStatusDisplay, type PlanStatusOption } from '@/features/delivery/planStatus';
import { recentRange, rangeError, shanghaiToday, shiftDay, type StatisticsRange } from '@/features/delivery/statisticsRange';

const user = useUserStore();
const router = useRouter(), route = useRoute(), wizard = useWizardStore();
const insightsPlanId = computed(() => typeof route.query.plan === 'string' ? route.query.plan : '');
const insightsTab = computed(() => typeof route.query.tab === 'string' ? route.query.tab : 'data');
const insightsRange = computed<StatisticsRange>(() => typeof route.query.planStart === 'string' && typeof route.query.planEnd === 'string' ? [route.query.planStart, route.query.planEnd] : [...displayedRange.value]);
function insightsLink(id: string, tab = 'data') { return { path: '/campaigns', query: { ...route.query, plan: id, tab, planStart: displayedRange.value[0], planEnd: displayedRange.value[1] } }; }
function closeInsights() { const { plan, tab, planStart, planEnd, ...query } = route.query; void router.replace({ path: '/campaigns', query }); }
function onInsightsVisible(value: boolean) { if (!value) closeInsights(); }
function setInsightsTab(tab: string) { void router.replace({ query: { ...route.query, tab } }); }
function setInsightsRange(range: StatisticsRange) { void router.replace({ query: { ...route.query, planStart: range[0], planEnd: range[1] } }); }
function startCreate() {
  if (!user.trialActive) return;
  wizard.reset();
  void router.push('/campaigns/create');
}
const loading = ref(false), summaryLoading = ref(false), loadFailed = ref(false);
const syncing = ref(false), syncWarning = ref(''), total = ref(0), accountId = ref('');
const accounts = ref<AccountVo[]>([]);
const keyword = ref(''), status = ref(''), bidType = ref('');
const statusPicker = ref<CascaderInstance>();
function closeStatusFilter() { statusPicker.value?.togglePopperVisible(false); }
function selectStatusLabel(option: PlanStatusOption, event: MouseEvent) {
  if(option.children?.length) return;
  event.stopPropagation(); status.value = option.value; closeStatusFilter();
}
const filtersExpanded = ref(false), deliveryPlatform = ref('ALL'), createdWithin = ref<number | null>(null);
const records = ref<PlanListVo[]>([]);
const openByPlan = ref<Record<string, string[]>>({});
const pageNum = ref(1), pageSize = ref(10), compact = ref(false), showTools = ref(true);
type SortField = 'costYuan' | 'gmvYuan' | 'convert' | 'roi';
const metricColumns: { field: SortField; label: string }[] = [{ field: 'costYuan', label: '广告消耗' }, { field: 'convert', label: '成交订单' }, { field: 'gmvYuan', label: '成交金额' }, { field: 'roi', label: '支付 ROI' }];
const sortField = ref<SortField | 'name' | ''>(''), sortDescending = ref(true);
const summary = ref<PageResult<PlanListVo>['summary']>();
const actingId = ref(''), transferringId = ref('');
const statisticsRange = ref<StatisticsRange>(recentRange(1));
const rangeDraft = ref<StatisticsRange>([...statisticsRange.value]);
const displayedRange = ref<StatisticsRange>([...statisticsRange.value]);
function deltaProps(row: PlanListVo, metric: 'cost' | 'orders' | 'gmv' | 'roi') {
  const field = { cost: 'costChangePct', orders: 'convertChangePct', gmv: 'gmvChangePct', roi: 'roiChangePct' } as const;
  const comparison = row.comparison;
  return { value: comparison?.[field[metric]], current: comparison?.current?.[metric], previous: comparison?.previous?.[metric],
    percent: true, unit: metric === 'orders' ? '单' : metric === 'roi' ? '' : '元', basis: comparison?.basis,
    previousTs: comparison?.previousSnapshotTs, previousStartDate: comparison?.previousStartDate,
    previousEndDate: comparison?.previousEndDate, unavailableReason: comparison?.unavailableReason };
}
const calendarStart = ref<string | null>(null);
const pickerDate = (day: string) => new Date(`${day}T00:00:00`);
const rangeShortcuts = [
  { text: '今天', value: () => recentRange(1).map(pickerDate) },
  { text: '昨天', value: () => { const day = shiftDay(shanghaiToday(), -1); return [pickerDate(day), pickerDate(day)]; } },
  ...[7, 30, 180].map(days => ({ text: `近${days}天`, value: () => recentRange(days).map(pickerDate) })),
];
function calendarDay(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
function disabledStatisticsDate(date: Date) {
  const day = calendarDay(date);
  if (day > shanghaiToday()) return true;
  return calendarStart.value != null && Math.abs(Date.parse(`${day}T00:00:00Z`) - Date.parse(`${calendarStart.value}T00:00:00Z`)) / 86400000 >= 180;
}
function onRangeCalendar(values: [Date, Date | null]) { calendarStart.value = values[1] ? null : calendarDay(values[0]); }
function onRangeVisible(visible: boolean) { if (!visible) calendarStart.value = null; }
function applyStatisticsRange(value: StatisticsRange | null) {
  calendarStart.value = null;
  if (!value) { rangeDraft.value = [...statisticsRange.value]; return; }
  const error = rangeError(value);
  if (error) { ElMessage.warning(error); rangeDraft.value = [...statisticsRange.value]; return; }
  statisticsRange.value = [...value];
}
const tools = ['一键起量', '素材追投'];
const quickReportOpen = ref(false), quickReportPlan = ref<PlanListVo>(), quickReportKind = ref('');
const quickReportTask = ref<ControlTask>(), quickReportTasks = ref<ControlTask[]>([]);
const quickReportLoading = ref(false), quickReportError = ref(''), quickReportRange = ref<StatisticsRange>(recentRange(7));
let quickReportSerial = 0;
function canViewToolReport(row: PlanListVo, tool: string) { return !isDeleted(row) && row.smartBidType === 'SMART_BID_CUSTOM' && !!row.qcPlanId && ['一键起量', '素材追投'].includes(tool); }
function openToolReport(row: PlanListVo, tool: string) {
  if (!canViewToolReport(row, tool)) return;
  quickReportPlan.value = row; quickReportKind.value = tool; quickReportRange.value = [...displayedRange.value]; quickReportOpen.value = true;
  void loadQuickReportTasks();
}
async function loadQuickReportTasks() {
  const turn = ++quickReportSerial, planId = quickReportPlan.value?.planId, kind = quickReportKind.value;
  quickReportTask.value = undefined; quickReportTasks.value = []; quickReportError.value = ''; quickReportLoading.value = true;
  if (!quickReportOpen.value || !planId) { quickReportLoading.value = false; return; }
  try {
    const tasks = await listControlTasks(planId, kind, [...quickReportRange.value]);
    if (turn !== quickReportSerial || !quickReportOpen.value) return;
    quickReportTasks.value = tasks.filter(task => task.task_status !== 'DELETED').sort((a, b) => (b.create_time || '').localeCompare(a.create_time || ''));
    quickReportTask.value = quickReportTasks.value[0];
  } catch (e: any) { if (turn === quickReportSerial && quickReportOpen.value) quickReportError.value = e?.response?.data?.message || e?.message || '调控任务加载失败，请重试'; }
  finally { if (turn === quickReportSerial) quickReportLoading.value = false; }
}
function selectQuickReportTask(id: string) { quickReportTask.value = quickReportTasks.value.find(task => task.id === id); }
watch(quickReportOpen, value => { if (!value) { quickReportSerial++; quickReportLoading.value = false; quickReportTask.value = undefined; quickReportTasks.value = []; quickReportError.value = ''; } });
onUnmounted(() => { quickReportSerial++; });
const operationOpen = ref(false), operationKind = ref(''), operationPlan = ref<PlanListVo | null>(null);
const confirmOpen = ref(false), confirmPlan = ref<PlanListVo | null>(null);
const deleteOpen = ref(false), deleteTarget = ref<PlanListVo | null>(null), deletingId = ref('');
const platformOptions = [{ value: 'ALL', label: '商品全域投放' }, { value: 'STANDARD', label: '标准投放' }];
const bidOptions = [{ value: 'SMART_BID_CUSTOM', label: '控成本投放' }, { value: 'SMART_BID_CONSERVATIVE', label: '放量投放' }];
try { const settings = JSON.parse(localStorage.getItem('lynx.campaign.display') || '{}'); compact.value = settings.compact === true; showTools.value = settings.showTools !== false; if ([10, 20, 50].includes(settings.pageSize)) pageSize.value = settings.pageSize; } catch { /* Use defaults for invalid preferences. */ }
watch([compact, showTools, pageSize], () => { try { localStorage.setItem('lynx.campaign.display', JSON.stringify({ compact: compact.value, showTools: showTools.value, pageSize: pageSize.value })); } catch { /* Display remains usable when storage is unavailable. */ } });
const money = (value: number) => value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const budgetText = (value?: number | null) => value == null ? '—' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
const goalText = (value?: number | null) => value == null ? '—' : Number(value).toString();
const fullDate = (date: string) => date.slice(0, 10);
function estimateText(estimate: PlanListVo['roiEstimate']) {
  if (estimate?.min != null && estimate.max != null) return `${goalText(estimate.min)}～${goalText(estimate.max)}`;
  if (estimate?.value != null) return goalText(estimate.value);
  if (estimate?.min != null) return `不低于 ${goalText(estimate.min)}`;
  if (estimate?.max != null) return `不高于 ${goalText(estimate.max)}`;
  return '暂无预估';
}
function roiClass(row: PlanListVo) { return row.roi != null && row.roi > 0 ? 'roi-good' : ''; }
const isDeleted = (row: PlanListVo) => planStatusDisplay(row.status,row.qcStatus).group === 'DELETED';
const canManage = (row: PlanListVo) => !isDeleted(row) && user.trialActive && row.canEdit === true;
const isDraft = (row: PlanListVo) => row.status === 'DRAFT' && !row.qcPlanId && row.source !== 'EXTERNAL';
const canDelete = (row: PlanListVo) => !isDeleted(row) && user.trialActive && (row.qcPlanId
  ? row.status !== 'SUBMITTING' && row.status !== 'DELETED' && accounts.value.some(a => a.accountId === row.accountId && a.status === 'ACTIVE')
  : row.status === 'DRAFT' && row.source !== 'EXTERNAL');
const canSwitchDraft = (row: PlanListVo) => user.trialActive && !row.qcPlanId && row.source !== 'EXTERNAL' && ['DRAFT', 'REJECTED'].includes(row.status);
const alertsOf = (id: string) => openByPlan.value[id] || [];
const filtered = computed(() => records.value);
const pagedRows = computed(() => records.value);
let loadTimer: ReturnType<typeof setTimeout> | undefined;
let requestVersion = 0;
function scheduleLoad() { clearTimeout(loadTimer); loadTimer = setTimeout(() => void load(), 250); }
watch([keyword, status, deliveryPlatform, bidType, createdWithin, pageSize, accountId, sortField, sortDescending, statisticsRange], () => { requestVersion++; pageNum.value = 1; scheduleLoad(); });
watch(pageNum, scheduleLoad);
onUnmounted(() => { clearTimeout(loadTimer); requestVersion++; });
function resetFilters() { keyword.value = ''; status.value = ''; deliveryPlatform.value = ''; bidType.value = ''; createdWithin.value = null; accountId.value = ''; }
function toggleSort(field: SortField | 'name') { sortDescending.value = sortField.value === field ? !sortDescending.value : field !== 'name'; sortField.value = field; }
function remainingDays(date: string) {
  const now = new Date(); now.setHours(0, 0, 0, 0);
  const days = Math.round((new Date(`${date}T00:00:00`).getTime() - now.getTime()) / 86400000);
  return days < 0 ? '投放时间已结束' : days === 0 ? '今日结束' : `剩余 ${days} 天`;
}
function openOperation(title: string, row: PlanListVo) { operationKind.value = title; operationPlan.value = row; operationOpen.value = true; }
async function load(refreshStatistics = false) {
  const version = ++requestVersion;
  loading.value = true; summaryLoading.value = true; loadFailed.value = false;
  const plansTask = (async () => {
    try {
      const result = await listPlans(pageNum.value, pageSize.value, { ...queryFilters(), refreshStatistics });
      if (version !== requestVersion) return;
      records.value = result.records; total.value = result.total; summary.value = result.summary;
      if (result.statisticsRange) displayedRange.value = [result.statisticsRange.startDate, result.statisticsRange.endDate];
    } catch { if (version === requestVersion) loadFailed.value = true; }
    finally { if (version === requestVersion) { loading.value = false; summaryLoading.value = false; } }
  })();
  const alertsTask = (async () => {
    const actions = await listActions().catch(() => []);
    const grouped: Record<string, string[]> = {};
    for (const row of actions) if (row.open) { const list = grouped[row.planId] ||= []; if (!list.includes(row.type)) list.push(row.type); }
    if (version === requestVersion) openByPlan.value = grouped;
  })();
  await Promise.all([plansTask, alertsTask]);
}
function queryFilters() { return { keyword: keyword.value, status: status.value, platform: deliveryPlatform.value, bidType: bidType.value, createdWithin: createdWithin.value, accountId: accountId.value, sortField: sortField.value, descending: sortDescending.value, startDate: statisticsRange.value[0], endDate: statisticsRange.value[1] }; }
async function syncRemote(notify = true) {
  if (syncing.value) return;
  syncing.value = true;
  try {
    const result = await syncPlans(accountId.value || undefined);
    syncWarning.value = result.accounts.flatMap(a => a.warnings.map(w => `${accounts.value.find(x => x.accountId === a.accountId)?.accountName || a.accountId}：${w}`)).join('；');
    if (notify && !syncWarning.value) ElMessage.success(result.accounts.length ? '账户计划已同步' : '没有可同步的有效授权账户');
    await load(true);
  } catch { syncWarning.value = '千川同步失败，已保留最近同步的计划，请稍后刷新重试。'; }
  finally { syncing.value = false; }
}
function onAct(row: PlanListVo) {
  if (actingId.value || transferringId.value || deletingId.value || !canManage(row)) return;
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
function onDelete(row: PlanListVo) {
  if (deletingId.value || actingId.value || transferringId.value || !canDelete(row)) return;
  deleteTarget.value = row; deleteOpen.value = true;
}
function closeDeleteConfirmation(done: () => void) { if (!deletingId.value) done(); }
async function confirmDelete() {
  const row = deleteTarget.value;
  if (!row || deletingId.value || !canDelete(row)) return;
  deletingId.value = row.planId;
  try {
    await deletePlan(row.planId);
    requestVersion++;
    deleteOpen.value = false; deleteTarget.value = null;
    records.value = records.value.filter(p => p.planId !== row.planId);
    total.value = Math.max(0, total.value - 1);
    pageNum.value = Math.min(pageNum.value, Math.max(1, Math.ceil(total.value / pageSize.value)));
    ElMessage.success('计划已删除');
    await nextTick(); clearTimeout(loadTimer);
    await load(true);
  } catch { /* Keep the confirmation open; API interceptor displays the platform error. */ }
  finally { deletingId.value = ''; }
}
async function onTransfer(row: PlanListVo) {
  if (transferringId.value || actingId.value || deletingId.value || !canManage(row) || !row.qcPlanId || row.canTransfer === false) return;
  transferringId.value = row.planId;
  let submitted = false;
  const settingsPath = `/campaigns/${row.planId}/transfer?step=2`;
  try {
    const context = await getPlanTransfer(row.planId);
    if (context.pendingKey) { await router.push(settingsPath); return; }
    if (!context.available) { ElMessage.warning(context.reason || '当前计划无法转投，请刷新后重试'); return; }
    if (context.currentQcPlanId !== row.qcPlanId) { ElMessage.warning('当前计划已变化，请确认刷新后的投放方式'); await load(); return; }
    if (!context.reuse) { await router.push(settingsPath); return; }
    submitted = true;
    const result = await switchTransferredPlan(row.planId, { requestKey: crypto.randomUUID(), expectedQcPlanId: context.currentQcPlanId });
    if (result.status === 'SUCCEEDED') { ElMessage.success(`已切换为${BID_TYPE[context.targetMode] || '另一种投放方式'}，原计划已暂停`); await load(); }
    else if (result.status === 'FAILED') { ElMessage.error(result.message); await load(); }
    else { ElMessage.warning(result.message); await router.push(settingsPath); }
  } catch {
    if (submitted) { ElMessage.warning('转投结果待确认，请核查结果后再操作'); await router.push(settingsPath); }
  } finally { transferringId.value = ''; }
}
async function exportRows() {
  const exportFilters = { ...queryFilters(), startDate: displayedRange.value[0], endDate: displayedRange.value[1] };
  let rows: PlanListVo[];
  {
    try {
      const first = await listPlans(1, 100, exportFilters); rows = [...first.records];
      for (let page = 2; page <= Math.ceil(first.total / 100); page++) rows.push(...(await listPlans(page, 100, exportFilters)).records);
    } catch { return; }
  }
  // Neutralize spreadsheet formulas in names and IDs before producing CSV.
  const cell = (value: unknown) => { let s = value == null ? '' : String(value); if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`; return `"${s.replaceAll('"', '""')}"`; };
  const header = ['计划名称', '计划ID', '状态', '投放方式', '日预算', '净成交ROI目标', '投放开始日期', '投放结束日期', '广告消耗', '成交订单', '成交金额', '支付ROI', '统计开始日期', '统计结束日期', '数据同步时间'];
  const csv = [header, ...rows.map(r => [r.name, r.qcPlanId || r.planId, planStatusLabel(r.status,r.qcStatus), BID_TYPE[r.smartBidType || ''], r.budget, r.roi2Goal, r.startDate, r.endDate, r.costYuan, r.convert, r.gmvYuan, r.roi, exportFilters.startDate, exportFilters.endDate, r.snapshotTs])].map(row => row.map(cell).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8;' }));
  const link = document.createElement('a'); link.href = url; link.download = `投放计划-${new Date().toLocaleDateString('sv-SE')}.csv`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  ElMessage.success(`已导出 ${rows.length} 条计划`);
}
onMounted(async () => {
  accounts.value = (await listAccounts().catch(() => [])).filter(a => a.platform === 'QIANCHUAN' && a.credentialSource !== 'MOCK');
  await load();
  if (accounts.value.some(a => a.status === 'ACTIVE')) await syncRemote(false);
});
</script>

<style scoped>
.statistics-picker { display: flex; flex-direction: column; gap: 4px; }
.statistics-picker :deep(.el-date-editor) { width: 300px; height: 38px; border-radius: 10px; }
@media (max-width: 700px) {
  .statistics-picker { flex: 1; min-width: 0; }
  .statistics-picker :deep(.el-date-editor) { width: 100%; min-width: 0; }
  .title-actions { width: 100%; }
  .overview .section-heading { align-items: flex-start; flex-direction: column; gap: 8px; }
  :global(.campaign-date-picker) { max-width: calc(100vw - 24px); max-height: 75vh; overflow: auto; }
  :global(.campaign-date-picker .el-date-range-picker) { width: calc(100vw - 24px); min-width: 0; }
  :global(.campaign-date-picker .el-picker-panel__sidebar) { position: static; width: 100%; display: flex; flex-wrap: wrap; }
  :global(.campaign-date-picker .el-picker-panel__shortcut) { width: auto; }
  :global(.campaign-date-picker .el-picker-panel__body) { display: block; margin-left: 0; min-width: 0; width: 100%; }
  :global(.campaign-date-picker .el-date-range-picker__content) { display: block; float: none; width: 100%; border: 0; box-sizing: border-box; }
}
.number-cell .statistics-date { display: block; margin-top: 5px; font-size: 10px; line-height: 16px; color: #7c7f99; white-space: nowrap; }
.campaign-page { --el-color-primary: #575cf6; --el-color-primary-light-3: #9189f8; --el-color-primary-light-9: #f0eeff; --el-border-radius-base: 8px; color: #303044; }
.title-row, .title-group, .title-actions, .section-heading, .toolbar, .toolbar-left, .toolbar-right, .pager { display: flex; align-items: center; }
.title-row { justify-content: space-between; gap: 16px; margin: 8px 0 19px; }.title-group { gap: 14px; }
h1 { margin: 0; padding-right: 14px; border-right: 3px solid #575cf6; font-size: 24px; line-height: 24px; font-weight: 750; }.platform-chip { padding: 5px 12px; font-size: 12px; border-radius: 20px; color: #fff; background: #575cf6; }
.title-actions { gap: 10px; }.snapshot-label { display: flex; gap: 8px; align-items: center; background: #fff; padding: 10px 14px; border: 1px solid #e9e8f1; border-radius: 8px; font-size: 12px; color: #666679; }.trial-alert { margin-bottom: 20px; }
.panel { background: #fff; border: 1px solid #eeedf6; border-radius: 24px; padding: 24px 32px 28px; box-shadow: 0 2px 8px #24204003; }.overview { margin-bottom: 24px; padding-bottom: 24px; }.section-heading { justify-content: space-between; margin-bottom: 18px; }.overview .section-heading { margin-bottom: 16px; }
h2 { margin: 0; font-size: 18px; line-height: 24px; font-weight: 700; color: #252537; }.section-heading > span { color: #aaa8ba; font-size: 12px; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 20px; }.metric { border-radius: 14px; padding: 16px 22px; }.metric > span { display: block; color: #7e7b94; font-size: 13px; }.metric strong { display: block; font-size: 25px; font-weight: 750; color: #222234; margin: 8px 0; font-variant-numeric: tabular-nums; }.metric small { color: #9996ad; font-size: 11px; }
.metric-cost { background: #ebecfe; }.metric-orders { background: #f5f0f8; }.metric-gmv { background: #eaf0fd; }.metric-roi { background: #f4eef6; }
.metric > span { line-height: 18px; }.metric strong { line-height: 30px; margin: 5px 0; }.metric small { display: block; line-height: 16px; }.actions-cell button.pause-action { color: #dc777a; }.actions-cell button.enable-action { color: #45aa79; }
.toolbar { justify-content: space-between; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }.toolbar-left, .toolbar-right { gap: 10px; }
.toolbar :deep(.el-button), .title-actions :deep(.el-button) { height: 38px; margin-left: 0; font-size: 12px; padding: 0 14px; }.toolbar :deep(.el-button .el-icon) { margin-right: 5px; font-size: 14px; }.toolbar :deep(.el-button--primary) { padding: 0 18px; font-size: 13px; box-shadow: 0 3px 8px #575cf618; }
.search-input { width: 300px; }.status-select { width: 134px; }.toolbar :deep(.el-input__wrapper), .toolbar :deep(.el-select__wrapper) { min-height: 38px; font-size: 12px; box-shadow: 0 0 0 1px #e9e8f1 inset; }
.toolbar :deep(.el-input__wrapper.is-focus), .toolbar :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px #575cf6 inset; }.toolbar :deep(.square-button), .title-actions :deep(.square-button) { width: 38px; padding: 0; }.toolbar :deep(.square-button .el-icon) { margin: 0; }
.filter-active { color: #575cf6; border-color: #bcb7fb; background: #f7f6ff; }.filter-dot { width: 5px; height: 5px; border-radius: 50%; background: #575cf6; margin-left: 4px; }.column-setting { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; font-size: 13px; }
.selection-bar { display: flex; align-items: center; gap: 12px; font-size: 12px; background: #f4f2ff; color: #575cf6; padding: 4px 12px; border-radius: 8px; margin-bottom: 12px; }.table-scroll { overflow-x: auto; }
.plan-table { border-collapse: collapse; width: 100%; min-width: 1508px; table-layout: fixed; text-align: left; }.plan-table.without-tools { min-width: 1372px; }
th { background: #f7f8fb; padding: 13px 8px; color: #8b899d; font-size: 12px; font-weight: 500; white-space: nowrap; }th:first-child { border-radius: 8px 0 0 8px; padding-left: 12px; }th:last-child { border-radius: 0 8px 8px 0; }
td { vertical-align: top; border-bottom: 1px solid #f0eff5; padding: 18px 8px; font-size: 12px; line-height: 20px; }td:first-child { padding-left: 12px; }tbody tr:hover, tbody tr.selected { background: #faf9ff; }.compact td { padding-top: 10px; padding-bottom: 10px; }
.plan-table :deep(.el-checkbox) { height: 18px; vertical-align: top; }.plan-table :deep(.el-checkbox__inner) { border-color: #dbd9e9; border-radius: 4px; }.plan-table :deep(.is-checked .el-checkbox__inner) { border-color: #575cf6; }
.sort-heading { display: flex; gap: 4px; align-items: center; border: 0; padding: 0; background: none; color: inherit; font: inherit; cursor: pointer; }.sort-heading .el-icon { font-size: 11px; color: #c2bfce; }.sort-heading .sort-active { color: #575cf6; }
.plan-name { display: block; max-width: 100%; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 13px; font-weight: 500; color: #55536a; }.plan-name:hover { color: #575cf6; }.plan-id { color: #aaa7b9; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-line { display: flex; gap: 8px; align-items: center; margin-top: 3px; }.product-trigger { display: inline-flex; align-items: center; gap: 4px; color: #88849d; font-size: 11px; background: none; border: 0; padding: 0; line-height: 20px; }.product-trigger:is(button) { cursor: pointer; }.product-trigger:is(button):hover { color: #575cf6; }
.source-tag { color: #7d70e3; background: #f0edff; font-size: 10px; border-radius: 4px; padding: 0 5px; line-height: 16px; }.source-tag.external { background: #f2f3f6; color: #9693a5; }.product-preview { display: flex; gap: 10px; align-items: center; padding: 6px 0; font-size: 12px; }.product-preview .el-image { width: 40px; height: 40px; border-radius: 6px; flex-shrink: 0; }.alert-link { display: block; color: #df726f; font-size: 11px; line-height: 16px; margin-top: 4px; }
.status-pill { display: inline-flex; gap: 5px; align-items: center; border-radius: 20px; font-size: 11px; line-height: 22px; padding: 0 9px; color: #9391a5; background: #f2f2f6; }.status-pill i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }.status-pill.delivering { background: #eaf8ee; color: #53ac6e; }.status-pill.paused { background: #fff4e9; color: #d5a367; }.status-pill.rejected { color: #d87272; background: #fff0f0; }.status-pill.pending_audit { color: #8680d2; background: #f0efff; }
.settings-cell { color: #878499; font-size: 11px; }.settings-cell > div { display: flex; align-items: center; gap: 2px; white-space: nowrap; }.settings-cell b { color: #625e77; font-weight: 500; }.settings-cell b.roi-goal { color: #45ab76; }.inline-edit { border: 0; background: none; color: #b0a6ed; cursor: pointer; padding: 0 4px; height: 18px; display: inline-flex; align-items: center; opacity: 0; pointer-events: none; transition: opacity .15s; }.inline-edit:hover { color: #575cf6; }tr:hover .inline-edit, tr:focus-within .inline-edit { opacity: 1; pointer-events: auto; }.inline-edit:focus-visible { outline: 2px solid #575cf6; outline-offset: 2px; }.bid-mode { color: #a5a0b8; }.bid-edit { opacity: 0; }tr:hover .bid-edit, .bid-edit:focus-visible { opacity: 1; }
@media (hover: none) { .inline-edit { opacity: 1; pointer-events: auto; } }
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
.tools-cell button, .actions-cell button, .actions-cell a { font-size: 13px; line-height: 22px; }.tools-cell button { color: #4e5069; }.tools-cell button span { color: #575cf6; }.actions-cell a, .actions-cell button { color: #4348db; }.actions-cell button.pause-action, .actions-cell button.delete-action { color: #e5484d; }.actions-cell button.enable-action { color: #0ba05f; }.tools-cell button:disabled, .tools-cell button:disabled span, .actions-cell button:disabled { color: #a3a6bb; }
.tool-shortcuts{display:grid;grid-template-columns:minmax(0,1fr) 22px 18px;align-items:center;gap:3px;min-height:26px}.tools-cell .tool-name{width:auto;white-space:nowrap}.tools-cell .tool-report{width:22px;height:24px;justify-content:center;color:#575cf6;border-radius:4px}.tools-cell .tool-report:hover{background:#eeedff}.tool-report .el-icon{font-size:16px}.tools-cell .tool-add{width:18px;justify-content:center;font-size:16px;color:#575cf6}.tools-cell button:focus-visible{outline:2px solid #575cf6;outline-offset:2px}
.tools-cell .tool-monitor{color:#4348db;font-size:13px;line-height:22px}.tools-cell .tool-monitor:hover{color:#575cf6}.tools-cell .tool-monitor:focus-visible{outline:2px solid #575cf6;outline-offset:2px}
.confirmation-icon.delete-icon { color: #e5484d; background: #fff0f0; }
.muted { color: #7c7f99; }.pager { color: #7c7f99; font-size: 12px; }.pager :deep(.el-pagination__jump) { font-size: 12px; color: #7c7f99; }.column-setting { padding: 14px 0; border-bottom: 1px solid #eeeff4; }.column-setting small { display: block; font-size: 11px; color: #7c7f99; margin-top: 5px; }
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
.bid-mode-info { display: inline-flex; align-items: center; gap: 6px; border: 0; background: none; color: inherit; font: inherit; line-height: inherit; padding: 0; cursor: help; }.bid-mode-info .el-icon { color: #7c7f99; font-size: 12px; }.bid-mode-info:focus-visible { outline: 2px solid #575cf6; outline-offset: 2px; }
.transfer-link.transfer-pending { opacity: 1; pointer-events: none; }.transfer-link:disabled { cursor: wait; }
.schedule-range { display: block; white-space: nowrap; }.schedule-cell small { display: block; margin-top: 1px; line-height: 20px; }
.number-cell strong { font: inherit; font-weight: 600; line-height: 22px; }.number-cell.gmv-cell { color: #4e5069; }.number-cell small { font-weight: 400; }.metric-change-line { display: flex; align-items: center; margin-top: 1px; min-height: 20px; }.roi-line { display: flex; align-items: baseline; flex-wrap: wrap; column-gap: 8px; }.roi-line strong.roi-good { color: #0ba05f; }.roi-line strong.roi-low { color: #e5484d; }
@media (max-width: 1100px) { .toolbar-left { flex-wrap: wrap; }.search-input { width: 250px; }.panel { padding: 24px; }.metrics { gap: 12px; }.metric { padding: 16px; }.metric strong { font-size: 22px; } }
@media (max-width: 700px) { .title-row { align-items: flex-start; flex-direction: column; gap: 16px; }.title-group { flex-wrap: wrap; gap: 12px; }h1 { font-size: 21px; }.panel { padding: 20px 16px; border-radius: 18px; }.metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }.metric strong { font-size: 20px; }.toolbar-left { width: 100%; }.search-input { width: 100%; order: 3; }.toolbar-right { flex-wrap: wrap; }.pager { flex-wrap: wrap; justify-content: flex-start; }.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 10px; } }
.plan-info-cell .plan-name { color: #34374f; font-size: 14px; font-weight: 600; line-height: 22px; }
tr:hover .plan-info-cell .plan-name, tr:focus-within .plan-info-cell .plan-name { color: #575cf6; }
.product-line { flex-wrap: wrap; }
.product-image-trigger { border-radius: 6px; flex-shrink: 0; }
.product-image-trigger:hover, .product-image-trigger:focus-visible { outline: 2px solid #575cf6; outline-offset: 2px; }
.goods-thumb { width: 36px; height: 36px; border: 1px solid #eeedf6; border-radius: 6px; overflow: hidden; }
.goods-thumb .el-image { width: 100%; height: 100%; border-radius: 6px; }
.review-delay-link{display:block;margin-top:5px;font-size:12px;line-height:1.5;color:#986226;white-space:normal;}
.bid-edit { max-width: 0; opacity: 0; overflow: hidden; padding: 0; transition: max-width .18s, opacity .18s; }
tr:hover .bid-edit, tr:focus-within .bid-edit { max-width: 120px; opacity: 1; }
@media (hover: none) { .bid-edit { max-width: 120px; opacity: 1; } }
.status-pill{max-width:100%;box-sizing:border-box;white-space:nowrap}.status-pill i{flex:none}.status-pill__label{min-width:0;overflow:hidden;text-overflow:ellipsis}.status-pill.audit,.status-pill.reaudit{color:#d08a1c;background:#fff8e7}.status-pill.completed,.status-pill.terminated,.status-pill.deleted{color:#89899b;background:#f4f4f8}
:global(.campaign-status-menu.el-cascader__dropdown .status-option-label){display:block;width:100%}:global(.campaign-status-menu.el-cascader__dropdown .el-cascader-menu){min-width:185px}:global(.campaign-status-menu.el-cascader__dropdown .el-cascader-menu__wrap){height:310px}:global(.campaign-status-menu.el-cascader__dropdown .el-cascader-node){font-size:13px;min-height:34px}:global(.campaign-status-menu.el-cascader__dropdown .el-cascader-node__label){padding-right:16px}
</style>
<style>
.plan-insights-drawer .el-drawer__body{padding:0;overflow:auto}.plan-insights-drawer .insights-header{padding-right:65px}.plan-insights-drawer .insights-close{position:absolute;right:15px;top:16px;z-index:2;display:flex;align-items:center;justify-content:center;width:32px;height:32px;border:1px solid #e8e9f4;border-radius:7px;background:white;color:#737892;font-size:25px;cursor:pointer}.plan-insights-drawer .insights-close:hover{color:#5b57ff;background:#f6f6ff}@media(max-width:700px){.plan-insights-drawer{width:100%!important}}
</style>
