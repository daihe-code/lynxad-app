<template>
  <div v-loading="loading" class="monitor-details">
    <header>
      <div class="heading"><h2>盯盘优化详情</h2><span v-if="state" class="badge" :class="state.tone">● {{ state.label }}</span><button class="close" aria-label="关闭盯盘详情" @click="emit('close')">×</button></div>
      <div class="plan" :class="{ 'plan-recovery': recoveryBlocked }">
        <div><strong>{{ data?.view.planName || plan?.name || '当前投放计划' }}</strong><small>广告账户：{{ data?.accountName || account?.accountName || data?.view.accountId || '—' }}<template v-if="plan?.qcPlanId"> · 计划ID：{{ plan.qcPlanId }}</template></small></div>
        <span v-if="recoveryBlocked" class="badge muted">广告计划：状态待核实</span>
        <el-tag v-else size="small">{{ planDisplay.label }}</el-tag>
      </div>
      <button v-if="risks.length" class="risk-banner" @click="tab = 'current'"><span>!　{{ risks.length }}项风险需核对处理</span><span>去处理 ›</span></button>
      <nav><button :class="{ active: tab === 'overview' }" @click="tab = 'overview'">盯盘成效</button><button :class="{ active: tab === 'current' }" @click="tab = 'current'">当前处理<template v-if="!recoveryBlocked">（{{ risks.length + (data?.pending.length || 0) }}）</template></button><button :class="{ active: tab === 'history' }" @click="tab = 'history'">工作记录</button></nav>
    </header>
    <main ref="scrollArea">
      <el-alert v-if="error && !recoveryBlocked" :title="error" type="error" :closable="false" show-icon /><el-button v-if="error && !recoveryBlocked" link type="primary" @click="load">重试</el-button>
      <template v-if="data">
        <template v-if="tab === 'overview'">
          <template v-if="paused">
            <div v-if="!recoveryBlocked && planDisplay.group === 'DELIVERING'" class="status-notice warning">广告仍在投放，消耗保护当前不生效。</div>
            <div v-if="data.pause?.pausedAt" class="summary">暂停时间 {{ time(data.pause.pausedAt) }}</div>
          </template>
          <div class="section-heading">
            <h3>{{ paused ? (data.pause?.snapshot ? '暂停前累计成效' : '累计盯盘成效') : '小灵盯盘成效' }}</h3>
            <div v-if="!paused" class="range"><button :class="{ active: !today }" @click="today = false">自开启以来</button><button :class="{ active: today }" @click="today = true">今日</button></div>
          </div>
          <div class="metrics"><article><el-icon><Clock /></el-icon><small>有效盯盘</small><strong>{{ monitoringDuration(data.effectiveSeconds) }}</strong></article><article><el-icon><Document /></el-icon><small>发现优化点</small><strong>{{ data.summary.found }}<em>项</em></strong></article><article><el-icon><CircleCheck /></el-icon><small>完成优化任务</small><strong>{{ data.summary.completed }}<em>项</em></strong><small>已核实生效</small></article></div>
          <template v-if="paused">
            <section v-if="data.pending.length || followingReviews">
              <h3>暂停后仍在跟进</h3>
              <div v-if="data.pending.length" class="state-row"><strong>已提交操作的结果核实</strong><span>{{ data.pending.length }}项待核实</span></div>
              <div v-if="followingReviews" class="state-row"><strong>历史任务效果复评</strong><span>继续跟进</span></div>
              <button class="panel-link" @click="tab = 'current'">查看当前处理 ›</button>
            </section>
            <section v-if="!recoveryBlocked && runningTasks.length">
              <h3>进行中的独立调控任务</h3>
              <div v-for="task in runningTasks" :key="`${task.kind}:${task.id}`" class="state-row">
                <strong>{{ task.name || task.kind }}</strong>
                <el-button link type="primary" @click="emit('data', 'controls')">查看任务 ›</el-button>
              </div>
            </section>
            <small v-if="data.pause?.snapshot?.dailySpend != null">
              暂停前最近记录的当日消耗 {{ money(data.pause.snapshot.dailySpend) }}
              <template v-if="data.pause.snapshot.dataAt"> · 数据截至 {{ time(data.pause.snapshot.dataAt) }}</template>
            </small>
          </template>
          <template v-else>
          <div class="pair"><section class="protection"><h3>消耗风险控制</h3><strong class="large">{{ data.summary.protectedCount }}<em>项已核实暂停</em></strong><p>减少损失金额：—</p><small>暂无可靠估算</small><button class="panel-link" @click="showHistory('RISK')">查看保护记录 ›</button></section><section class="improvement"><h3>投放效果改善</h3><strong class="large">ROI {{ latestEffect?.roi_change == null ? '—' : (Number(latestEffect.roi_change) > 0 ? '+' : '') + number(latestEffect.roi_change) }}</strong><p>{{ latestEffect ? number(latestEffect.before_roi) + ' → ' + number(latestEffect.after_roi) : '暂无同口径的可靠对比数据' }}</p><small>结果变化不等于全部由小灵带来。</small><button class="panel-link" @click="tab = 'current'">查看效果依据 ›</button></section></div>
          <section><h3>近期完成的工作</h3><div v-for="event in completed.slice(0, 3)" :key="event.id" class="completed-row"><el-icon><CircleCheck /></el-icon><span>{{ event.title }}</span><small>{{ time(event.finishedAt || event.createdAt) }}</small></div><el-empty v-if="!completed.length" description="暂无已核实完成的任务" :image-size="40" /></section>
          <section><h3>当前盯盘与处理</h3><p v-if="state.reason">{{ state.reason }}</p><template v-if="risks.length"><button v-for="(item, index) in risks" :key="index" class="issue-link" @click="tab = 'current'"><span>{{ item.title }}</span><span>查看并处理 ›</span></button></template><p v-else>{{ state.label === '盯盘中' ? '持续检查投放表现，暂无待处理建议。' : '数据未满足条件时继续观察，已有工作记录保留。' }}</p><button class="panel-link" @click="tab = 'current'">查看当前处理 ›</button></section>
          </template>
        </template>
        <PlanOptimizationAuthorizationRecovery v-else-if="tab === 'current' && recoveryBlocked"
          :authorization-url="authorizationUrl" :can-check="canCheckRecovery" :checking="canCheckRecovery && (recoveryChecking || tasksLoading)"
          :error="recoveryError" @authorize="openAuthorization" @check="checkRecovery" />
        <template v-else-if="tab === 'current'">
          <div class="stats-strip">有效盯盘 {{ monitoringDuration(data.effectiveSeconds) }} · 优化点 {{ data.summary.found }}项 · 已完成 {{ data.summary.completed }}项 <button @click="tab = 'overview'">查看成效 ›</button></div>
          <div v-if="state.reason" class="status-notice" :class="state.tone">{{ state.reason }}</div>
          <section v-if="state.label === '授权失效'"><h3>请重新授权，恢复检查能力</h3><p>无法获取新的投放状态，也不能确认广告已停止消耗。</p><el-button type="primary" @click="router.push('/accounts')">前往账户管理</el-button><div class="summary">最近成功检查：{{ time(data.view.lastSuccess) }}<br>最近可见消耗：{{ money(data.view.result?.daily_spend) }}（历史数据）</div></section>
          <section v-if="data.pending.length"><h3>小灵正在核对操作结果</h3><article v-for="event in data.pending" :key="event.id" class="verification"><strong>{{ event.title }}</strong><span class="badge warning">{{ event.status === 'UNKNOWN' ? '结果待人工核查' : '核实中' }}</span><p>{{ event.error || '已提交请求，平台状态回读尚未确认。' }}</p><div class="steps"><span>保护条件命中</span><span>请求已提交</span><span>平台结果核实中</span></div><small>结果未明前不会重复提交。独立调控任务另行核对。</small><el-button link type="primary" :loading="checking === event.id" @click="recheck(event)">查看最新状态</el-button></article></section>
          <section v-if="planDisplay.group === 'PAUSED'"><h3>暂停广告，保留必要的跟踪</h3><div class="state-row"><strong>新的经营判断与增长建议</strong><span class="badge muted">暂停</span></div><div class="state-row"><strong>已提交操作的结果核实</strong><span>{{ data.pending.length ? '继续核实' : '暂无待核实操作' }}</span></div><p>恢复投放需再次确认，跨日、ROI回升或商品恢复均不会自动启动广告。</p><el-button type="primary" @click="emit('data', 'data')">核对计划与投放设置</el-button></section>
          <section v-if="tasks.length || taskError"><h3>{{ planDisplay.group === 'PAUSED' ? '独立任务仍有风险需要核对' : '独立调控任务' }}</h3><p v-if="taskError">{{ taskError }}</p><article v-for="task in tasks" :key="`${task.kind}:${task.id}`" class="task"><strong>{{ task.name || task.kind }} · {{ task.id }}</strong><span class="badge warning">{{ task.task_status === 'PROCESSING' ? '仍在运行' : '状态需核对' }}</span><div class="summary">任务预算 {{ money(task.budget) }} · 已消耗 {{ money(task.metrics?.cost) }}</div><button class="panel-link" @click="emit('data', 'controls')">查看任务并确认处理 ›</button></article><small>本计划的自动暂停授权不包含独立任务终止；计划整体报表中的消耗不再与任务消耗相加。</small></section>
          <template v-if="canShowCurrent">
            <div class="metrics current-metrics"><article><small>今日消耗</small><strong>{{ money(data.view.result?.daily_spend) }}</strong></article><article><small>成熟窗口净成交ROI</small><strong>{{ number(data.view.result?.actual_net_roi) }}</strong></article><article><small>最近检查</small><strong>{{ time(data.view.lastCheck, true) }}</strong></article></div>
            <h3 v-if="risks.length">先看原因，再决定怎样调整</h3>
            <section v-for="(item, index) in risks" :key="index" class="risk"><h3>{{ item.title }}</h3><p>{{ item.evidence }}</p><template v-if="item.type === 'ROI_PROTECTION'"><div class="metrics"><article><small>成熟窗口ROI</small><strong>{{ number(data.view.result?.actual_net_roi) }}</strong></article><article><small>你的要求</small><strong>{{ number(data.view.policy.rules?.roiFloor) }}</strong></article><article><small>窗口消耗</small><strong>{{ money(data.view.result?.mature_spend) }}</strong></article></div><div v-if="data.view.result?.roi_windows?.length" class="windows"><div v-for="window in data.view.result.roi_windows" :key="window.window_end"><small>{{ time(window.window_start, true) }}—{{ time(window.window_end, true) }}</small><strong>ROI {{ windowRoi(window) }}</strong></div></div><p>根据真实的连续完整小时与成交回补判断；原因仍需结合商品、素材和流量数据核对。</p></template><div class="actions"><el-button v-if="item.action && eventFor(item)?.status === 'RECOMMENDED'" type="primary" :loading="deciding === eventFor(item)?.id" @click="preview(item)">查看并确认{{ item.action.kind === 'PAUSE' ? '暂停' : '预算调整' }}</el-button><el-button v-else type="primary" :loading="!!deciding" @click="handoff(item)">{{ item.workflow === 'materials' ? '调整对应素材' : '核对计划并处理' }}</el-button></div></section>
            <section v-if="!risks.length"><h3>小灵正在观察，暂不急着调整</h3><p>{{ data.view.result?.reasons?.join('；') || '持续核对最新消耗与成交数据。没有合适机会时保持当前设置。' }}</p><div class="state-row"><strong>消耗检查</strong><span class="badge" :class="data.view.result?.complete && !data.view.stale ? 'success' : 'warning'">{{ data.view.result?.complete && !data.view.stale ? '数据可用' : '等待最新数据' }}</span></div><div class="state-row"><strong>ROI效果判断</strong><span>{{ data.view.result?.actual_net_roi ? '已取得成熟窗口' : '等待有效成交数据' }}</span></div></section>
            <section v-if="growth.length"><h3>增长机会</h3><article v-for="(item, index) in growth" :key="index"><strong>{{ item.title }}</strong><p>{{ item.evidence }}</p><div class="summary">单次建议上限 {{ money(data.view.policy.rules?.maxAddition) }} · 每日累计建议上限 {{ money(data.view.policy.rules?.dailyAddition) }}</div><el-button type="primary" :loading="!!deciding" @click="item.action ? preview(item) : handoff(item)">{{ item.action ? '查看预算调整方案' : '查看素材调整方案' }}</el-button></article></section>
          </template>
          <section v-else-if="!paused && !planEnded && state.label !== '授权失效' && planDisplay.group !== 'PAUSED' && !data.pending.length"><h3>仅暂停受影响的判断</h3><p>{{ data.view.error || '等待最新可用数据；历史数值不能代表当前投放状态。' }}</p><el-button @click="load">刷新状态</el-button></section>
          <PlanOptimizationReviews :reviews="data.reviews || []" />
        </template>
        <template v-else>
          <h3>小灵的工作记录</h3><p>发现、处理和结果均有记录，同一事项持续更新。</p>
          <div class="history-filters"><button v-for="item in categories" :key="item.value" :class="{ active: category === item.value }" @click="category = item.value">{{ item.label }}</button><el-select v-model="days" aria-label="工作记录时间范围"><el-option :value="7" label="最近7天" /><el-option :value="30" label="最近30天" /><el-option :value="180" label="最近180天" /></el-select></div>
          <div class="timeline"><article v-for="event in data.history.records" :key="event.id"><small>{{ time(event.createdAt) }}</small><details><summary><strong>{{ event.title }}</strong><span class="badge" :class="event.status === 'SUCCEEDED' ? 'success' : 'muted'">{{ eventStatus(event.status) }}</span></summary><p>{{ event.evidence }}</p><p v-if="event.error">{{ event.error }}</p><div v-if="event.kind === 'RULES_CHANGE' && event.report.rules" class="summary">{{ ruleChangeText(event) }}</div><template v-if="event.kind !== 'RULES_CHANGE'"><p>当前结果：{{ event.status === 'SUCCEEDED' ? '已回查确认生效' : event.status === 'RECOMMENDED' ? '已记录建议，尚无已执行结果' : eventStatus(event.status) }}</p><el-button v-if="['EXECUTING','VERIFY_PENDING','UNKNOWN'].includes(event.status)" link type="primary" @click="recheck(event)">只读回查结果</el-button></template></details></article></div>
          <el-empty v-if="!data.history.records.length" description="暂无工作记录" :image-size="50" /><el-pagination v-if="data.history.total > 20" small layout="prev, pager, next" :total="data.history.total" :page-size="20" v-model:current-page="page" />
        </template>
        <details v-if="tab !== 'history'" class="current-rules"><summary>{{ paused || planEnded ? '已保存规则' : '当前生效规则' }} <span>查看 ⌄</span></summary><div v-if="data.view.policy.rules" class="rule-grid"><div><small>净成交ROI不低于</small><strong>{{ number(data.view.policy.rules.roiFloor) }}</strong></div><div><small>提醒 / 保护金额</small><strong>{{ money(data.view.policy.rules.spendReminder) }} / {{ money(data.view.policy.rules.spendProtection) }}</strong></div><div><small>暂停方式</small><strong>{{ data.view.policy.rules.protectionAction === 'AUTO_PAUSE' ? '授权本计划自动暂停' : '逐次确认' }}</strong></div><div><small>跑飞提醒</small><strong>{{ data.view.policy.rules.runawayMinutes }}分钟 / {{ money(data.view.policy.rules.runawaySpend) }}</strong></div><div><small>单次 / 每日追加建议上限</small><strong>{{ money(data.view.policy.rules.maxAddition) }} / {{ money(data.view.policy.rules.dailyAddition) }}</strong></div></div><p v-else>当前为历史版本规则。</p></details>
      </template>
    </main>
    <el-dialog :model-value="!!decision" title="确认盯盘处理方案" width="min(480px, 90vw)" append-to-body :close-on-click-modal="false" :show-close="!confirming" @close="!confirming && (decision = undefined)">
      <template v-if="decision"><p>{{ decision.title }}</p><div class="summary">{{ decision.preview.action.kind === 'PAUSE' ? '暂停当前主计划' : `日预算：${money(decision.preview.action.expected)} → ${money(decision.preview.action.target)}` }}</div><p v-if="decision.preview.action.kind === 'BUDGET'">本次新增 {{ money(decision.preview.addition) }} · 今日已承诺 {{ money(decision.preview.committed) }}</p><p v-else>独立调控任务需单独确认处理。</p><el-alert v-if="decisionError" type="error" :title="decisionError" :closable="false" /></template>
      <template #footer><el-button :disabled="confirming" @click="decision = undefined">取消</el-button><el-button type="primary" :loading="confirming" :disabled="!!decisionError" @click="confirmDecision">确认执行</el-button></template>
    </el-dialog>
    <PlanOptimizationPauseDialog v-model="pauseConfirmOpen" :busy="closing" :error="pauseError" :tasks="tasks" :tasks-loading="tasksLoading" :tasks-unavailable="!!taskError" @confirm="pauseOptimization" @tasks="showControlTasks" />
    <PlanOptimizationResumeDialog v-model="resumeOpen" :preview="resumePreview" :loading="resumeLoading"
      :busy="resuming" :error="resumeError" :running-task-count="runningTasks.length"
      @confirm="resumeOptimization" @refresh="loadResumePreview" @edit="emit('edit')" @tasks="emit('data', 'controls')" />
    <footer v-if="data && (!planEnded || data.pending.length)">
      <el-button v-if="!planEnded && data.view.policy.enabled" :loading="closing" @click="openPauseConfirmation">暂停盯盘优化</el-button>
      <small v-if="recoveryBlocked">{{ canCheckRecovery ? '请完成数据核对' : '请先重新授权并完成数据核对' }}</small>
      <div class="footer-actions">
        <el-button v-if="data.pending.length && !paused" :loading="refreshing" @click="load">查看最新状态</el-button>
        <el-button v-if="!planEnded" :type="paused ? 'default' : 'primary'" :disabled="closing || resuming || recoveryChecking" @click="emit('edit')">
          {{ data.view.policy.enabled || paused ? '修改规则' : '设置并开启' }}
        </el-button>
        <el-button v-if="paused && !planEnded" type="primary" :disabled="closing || recoveryBlocked" @click="openResumeConfirmation">恢复盯盘优化</el-button>
      </div>
    </footer>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Clock, CircleCheck, Document } from '@element-plus/icons-vue';
import PlanOptimizationReviews from './PlanOptimizationReviews.vue';
import PlanOptimizationPauseDialog from './PlanOptimizationPauseDialog.vue';
import PlanOptimizationResumeDialog from './PlanOptimizationResumeDialog.vue';
import PlanOptimizationAuthorizationRecovery from './PlanOptimizationAuthorizationRecovery.vue';
import { requestFailure } from '@/api/apiErrors';
import { previewOptimizationDecision, confirmOptimizationDecision, startOptimizationReview, type DecisionPreview, type MonitoringRecommendation, getOptimizationDetails, recheckOptimization, saveOptimizationRules, type OptimizationDetails, type OptimizationEvent, getOptimizationResumePreview, confirmOptimizationResume, type OptimizationResumePreview } from '@/features/delivery/optimizationApi';
import { isOptimizationPlanEnded, monitoringDuration, optimizationDetailState } from '@/features/delivery/optimizationDetails';
import { defaultOptimizationRules } from '@/features/delivery/optimizationRules';
import { planStatusDisplay } from '@/features/delivery/planStatus';
import { listControlTasks, type ControlTask, type PlanListVo } from '@/features/delivery/deliveryApi';
import type { AccountVo } from '@/features/identity/oauthApi';
const props = defineProps<{ planId: string; plan?: PlanListVo; account?: AccountVo }>();
const emit = defineEmits<{ edit: []; close: []; saved: []; data: [tab: string] }>();
const authorizationRecovery = defineModel<'recheck' | 'reauthorize' | 'opened'>('authorizationRecovery');
const router = useRouter(), tab = ref('overview'), today = ref(false), category = ref('ALL'), days = ref(7), page = ref(1);
const categories = [{ value: 'ALL', label: '全部' }, { value: 'RISK', label: '风险处理' }, { value: 'GROWTH', label: '增长优化' }, { value: 'RULES', label: '规则变更' }];
const followingReviewStatuses = ['TRACKING', 'OBSERVING', 'WAITING_DATA'];
const data = ref<OptimizationDetails>(), loading = ref(true), refreshing = ref(false), closing = ref(false), checking = ref(''), error = ref(''), taskError = ref('');
const resumeOpen = ref(false), resumeLoading = ref(false), resuming = ref(false), resumeError = ref('');
const resumePreview = ref<OptimizationResumePreview>();
const recoveryChecking = ref(false), recoveryError = ref('');
const authorizationUrl = router.resolve({ path: '/accounts', query: { tab: 'oauth', modal: 'add' } }).href;
const tasks = ref<(ControlTask & { kind: string })[]>([]), scrollArea = ref<HTMLElement>();
const tasksLoading = ref(false), pauseConfirmOpen = ref(false), pauseError = ref('');
const deciding = ref(''), confirming = ref(false), decisionError = ref('');
const decision = ref<{ title: string; preview: DecisionPreview }>();
const latestEffect = computed(() => data.value?.reviews?.find(r => r.status === 'COMPARED')?.result);
let revision = 0, disposed = false, timer: ReturnType<typeof setTimeout> | undefined;
const planDisplay = computed(() => planStatusDisplay(data.value?.view.planStatus || props.plan?.status, data.value?.view.qcStatus ?? props.plan?.qcStatus));
const planEnded = computed(() => isOptimizationPlanEnded(planDisplay.value.group)
  || data.value?.view.planStatus === 'FINISHED' || props.plan?.status === 'FINISHED');
const paused = computed(() => !!data.value?.view.policy.rules && !data.value.view.policy.enabled);
const accountStatus = computed(() => data.value?.accountStatus || props.account?.status);
const recoveryBlocked = computed(() => paused.value && !planEnded.value && !!authorizationRecovery.value);
const canCheckRecovery = computed(() => accountStatus.value === 'ACTIVE' && authorizationRecovery.value !== 'reauthorize');
const runningTasks = computed(() => tasks.value.filter(task => task.task_status === 'PROCESSING'));
const followingReviews = computed(() => data.value?.reviews?.some(review => followingReviewStatuses.includes(review.status)));
const state = computed(() => error.value && !recoveryBlocked.value ? { label: '检查受限', tone: 'warning', reason: '暂时无法核实最新状态，历史结果不作为实时判断。' } : data.value ? optimizationDetailState(data.value, planEnded.value ? 'FINISHED' : planDisplay.value.group, accountStatus.value, runningTasks.value.length) : { label: '加载中', tone: 'muted', reason: '' });
const canShowCurrent = computed(() => !!data.value && !planEnded.value && !error.value && !data.value.pending.length && data.value.view.policy.enabled && !data.value.view.error && !data.value.view.stale && !data.value.view.holdReason && planDisplay.value.group === 'DELIVERING' && state.value.label !== '授权失效');
const recommendations = computed(() => canShowCurrent.value ? data.value?.view.result?.recommendations || [] : []);
const risks = computed(() => recommendations.value.filter(r => !r.type.includes('GROWTH'))), growth = computed(() => recommendations.value.filter(r => r.type.includes('GROWTH')));
const completed = computed(() => data.value?.history.records.filter(e => e.status === 'SUCCEEDED' && e.kind !== 'RULES_CHANGE') || []);
const number = (value: unknown) => value == null || value === '' || !Number.isFinite(Number(value)) ? '—' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
const money = (value: unknown) => number(value) === '—' ? '—' : `¥${number(value)}`;
const time = (value?: string | null, short = false) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false, ...(short ? { hour: '2-digit', minute: '2-digit' } : {}) }) : '—';
const eventStatus = (status: string) => ({ SUCCEEDED: '已生效', RECOMMENDED: '待确认', READY: '待执行检查', EXECUTING: '已提交', VERIFY_PENDING: '核实中', UNKNOWN: '待人工核查', SKIPPED: '未执行', ACKNOWLEDGED: '已人工核查', RESOLVED: '建议已失效' }[status] || status);
function windowRoi(window: { metrics: { spend: string | null; net_revenue: string | null } }) { return window.metrics.spend != null && window.metrics.net_revenue != null && Number(window.metrics.spend) > 0 ? number(Number(window.metrics.net_revenue) / Number(window.metrics.spend)) : '—'; }
function ruleChangeText(event: OptimizationEvent) { const rules = (event.report as unknown as { rules: { roiFloor: number; spendReminder: number; spendProtection: number; protectionAction: string } }).rules; return `ROI不低于 ${number(rules.roiFloor)}；${money(rules.spendReminder)}提醒，${money(rules.spendProtection)}保护；${rules.protectionAction === 'AUTO_PAUSE' ? '授权本计划自动暂停' : '逐次确认暂停'}`; }
function showHistory(value: string) { category.value = value; tab.value = 'history'; }
function eventFor(item: MonitoringRecommendation) { return data.value?.actions?.find(e => e.title === item.title && (item.action ? JSON.stringify(e.proposal) === JSON.stringify(item.action) : !e.proposal)); }
async function preview(item: MonitoringRecommendation) {
  const event = eventFor(item); if (!event || deciding.value) return;
  deciding.value = event.id; decisionError.value = '';
  try { decision.value = { title: item.title, preview: await previewOptimizationDecision(props.planId, event.id) }; }
  catch (err: any) { error.value = err?.message || '方案已变化，请刷新后核对'; }
  finally { deciding.value = ''; }
}
async function confirmDecision() {
  if (!decision.value || confirming.value) return;
  confirming.value = true;
  try { const p = decision.value.preview; await confirmOptimizationDecision(props.planId, p.eventId, p.version, p.hash); decision.value = undefined; await load(); emit('saved'); }
  catch (err: any) { decisionError.value = err?.message || '暂未核实结果，请刷新后查看'; await load(); }
  finally { confirming.value = false; }
}
async function handoff(item: MonitoringRecommendation) {
  if (deciding.value) return;
  const event = eventFor(item), workflow = item.workflow || 'data';
  if (!event || data.value?.view.result?.strategy_approved !== true) { emit('data', workflow === 'materials' ? 'materials' : 'data'); return; }
  deciding.value = event.id;
  try { const review = await startOptimizationReview(props.planId, event.id, workflow); await router.push({ path: `/campaigns/${props.planId}/edit`, query: { step: workflow === 'materials' ? '3' : workflow === 'budget' ? '2' : '1', monitoringReview: review.id } }); emit('close'); }
  catch (err: any) { error.value = err?.message || '建议已变化，请刷新后核对'; }
  finally { deciding.value = ''; }
}
async function load() {
  const current = ++revision; refreshing.value = true;
  try { const value = await getOptimizationDetails(props.planId, { today: today.value, category: category.value, days: days.value, pageNum: page.value, pageSize: 20 }); if (!disposed && current === revision) { data.value = value; error.value = ''; } }
  catch (err: any) { if (!disposed && current === revision) error.value = err?.response?.data?.message || err?.message || '暂时无法加载盯盘详情，请重试'; }
  finally { if (!disposed && current === revision) { loading.value = false; refreshing.value = false; } }
}
function requireAuthorizationRecovery(rejected = false) {
  if (disposed || !paused.value || planEnded.value) return;
  if (!authorizationRecovery.value || rejected) tab.value = 'current';
  if (rejected) authorizationRecovery.value = 'reauthorize';
  else authorizationRecovery.value ??= 'recheck';
  resumeOpen.value = false;
  resumePreview.value = undefined;
}
function handleAuthorizationFailure(cause: unknown) {
  const failure = requestFailure(cause);
  if (!paused.value || planEnded.value || (failure.kind !== 'AUTHORIZATION' && ![1201, 1205].includes(failure.code ?? 0))) return false;
  requireAuthorizationRecovery(true);
  return true;
}
function openAuthorization() {
  authorizationRecovery.value = 'opened';
  recoveryError.value = '';
}
function refreshAuthorizationStatus() {
  if (recoveryBlocked.value && !recoveryChecking.value && !refreshing.value) void load();
}
async function checkRecovery() {
  if (!recoveryBlocked.value || !canCheckRecovery.value || recoveryChecking.value || tasksLoading.value) return;
  recoveryChecking.value = true;
  recoveryError.value = '';
  try {
    const preview = await getOptimizationResumePreview(props.planId);
    if (disposed || !recoveryBlocked.value || !canCheckRecovery.value) return;
    const tasksVerified = await loadTasks(true);
    if (disposed || !recoveryBlocked.value || !canCheckRecovery.value) return;
    if (!tasksVerified) {
      recoveryError.value = '暂时无法核对在途任务，请重新检查。';
      return;
    }
    authorizationRecovery.value = undefined;
    resumePreview.value = preview;
    resumeError.value = '';
    resumeOpen.value = true;
  } catch (cause) {
    if (!disposed && !handleAuthorizationFailure(cause)) recoveryError.value = '暂时无法核对计划状态和当天消耗，请重新检查。';
  } finally {
    recoveryChecking.value = false;
    if (!canCheckRecovery.value) refreshAuthorizationStatus();
  }
}
async function loadTasks(forRecovery = false): Promise<boolean> {
  if (tasksLoading.value || recoveryBlocked.value && !forRecovery) return false;
  if (props.plan?.smartBidType !== 'SMART_BID_CUSTOM' && data.value?.view.bidType !== 'CUSTOM') return true;
  tasksLoading.value = true;
  const values = await Promise.allSettled(['一键起量', '素材追投'].map(async kind => (await listControlTasks(props.planId, kind)).map(task => ({ ...task, kind }))));
  tasksLoading.value = false;
  if (disposed || recoveryBlocked.value && !forRecovery) return false;
  if (values.some(value => value.status === 'rejected' && handleAuthorizationFailure(value.reason))) {
    refreshAuthorizationStatus();
    return false;
  }
  tasks.value = values.flatMap(value => value.status === 'fulfilled' ? value.value : []).filter(task => !['FROZEN', 'OFFLINE_TIME', 'OFFLINE_BUDGET', 'DELETED'].includes(task.task_status || ''));
  taskError.value = values.some(value => value.status === 'rejected') ? '独立任务状态暂未核实，请到调控中查看。' : '';
  return !taskError.value;
}
function openPauseConfirmation() {
  if (planEnded.value || !data.value?.view.policy.enabled || closing.value) return;
  pauseError.value = ''; pauseConfirmOpen.value = true;
  void loadTasks();
}
function showControlTasks() { if (closing.value) return; pauseConfirmOpen.value = false; emit('data', 'controls'); }
async function pauseOptimization() {
  if (planEnded.value || !data.value?.view.policy.enabled || closing.value) return;
  closing.value = true;
  pauseError.value = '';
  try {
    const { view } = data.value;
    const saved = await saveOptimizationRules(props.planId, view.policy.rules || defaultOptimizationRules(props.plan?.roi2Goal, props.plan?.budget), false, view.version);
    if (disposed) return;
    data.value = { ...data.value, view: saved, actions: [] };
    pauseConfirmOpen.value = false;
    tab.value = 'overview';
    emit('saved');
    await load();
    ElMessage.success({ message: '盯盘优化已暂停', duration: 3000 });
  } catch (err: any) {
    pauseError.value = err?.response?.data?.message || err?.message || '暂停失败，请重试';
  } finally {
    closing.value = false;
  }
}
function openResumeConfirmation() {
  if (!paused.value || planEnded.value || recoveryBlocked.value || resumeOpen.value) return;
  resumeOpen.value = true;
  void loadResumePreview();
  void loadTasks();
}
async function loadResumePreview() {
  if (resumeLoading.value || resuming.value || recoveryBlocked.value) return;
  resumeLoading.value = true;
  resumeError.value = '';
  resumePreview.value = undefined;
  try {
    const preview = await getOptimizationResumePreview(props.planId);
    if (!disposed && resumeOpen.value) resumePreview.value = preview;
  } catch (cause) {
    if (handleAuthorizationFailure(cause)) refreshAuthorizationStatus();
    else resumeError.value = requestFailure(cause).message;
  } finally {
    resumeLoading.value = false;
  }
}
async function resumeOptimization() {
  const preview = resumePreview.value;
  if (!preview || !paused.value || planEnded.value || recoveryBlocked.value || resuming.value || resumeError.value) return;
  resuming.value = true;
  try {
    const result = await confirmOptimizationResume(props.planId, preview);
    if (disposed) return;
    resumeOpen.value = false;
    emit('saved');
    await load();
    if (preview.outcome !== 'LIMIT_PAUSE') ElMessage.success('盯盘优化已恢复');
    else if (result.pauseStatus === 'SUCCEEDED') ElMessage.success('盯盘优化已恢复，广告计划已暂停');
    else if (result.pauseStatus === 'SKIPPED') ElMessage.warning('盯盘优化已恢复，本次暂停未执行');
    else ElMessage.info('盯盘优化已恢复，暂停结果待核实');
  } catch (cause) {
    if (disposed) return;
    if (!handleAuthorizationFailure(cause)) resumeError.value = requestFailure(cause, { method: 'POST' }).message;
    await load();
    if (!paused.value) resumeOpen.value = false;
  } finally {
    resuming.value = false;
  }
}
async function recheck(event: OptimizationEvent) { if (checking.value) return; checking.value = event.id; try { await recheckOptimization(event.id); await load(); emit('saved'); } catch (err: any) { error.value = err?.response?.data?.message || err?.message || '暂时无法核实，请稍后重试'; } finally { checking.value = ''; } }
watch([today, category, days], () => { page.value = 1; void load(); }); watch(page, () => void load()); watch(tab, () => scrollArea.value?.scrollTo({ top: 0 }));
watch([paused, planEnded, accountStatus], ([isPaused, ended, status]) => {
  if (isPaused && !ended && status && status !== 'ACTIVE') requireAuthorizationRecovery();
}, { flush: 'sync' });
watch(recoveryBlocked, blocked => { if (blocked) tab.value = 'current'; });
async function poll() { if (disposed) return; if (!refreshing.value && !closing.value && !resuming.value && !confirming.value) { await load(); await loadTasks(); } if (!disposed) timer = setTimeout(poll, 30000); }
onMounted(async () => {
  window.addEventListener('focus', refreshAuthorizationStatus);
  await load();
  if (disposed) return;
  void loadTasks();
  timer = setTimeout(poll, 30000);
});
onBeforeUnmount(() => {
  disposed = true;
  revision++;
  window.removeEventListener('focus', refreshAuthorizationStatus);
  if (timer) clearTimeout(timer);
});
</script>
<style scoped>
.plan.plan-recovery { display: flex; align-items: center; justify-content: space-between; gap: 6px 12px; flex-wrap: wrap; padding-right: 14px; }
.monitor-details{height:100%;display:flex;flex-direction:column;color:#202444;background:#fff}header{flex:none;padding:18px 22px 0}.heading{display:flex;align-items:center;gap:12px}h2{font-size:18px;margin:0}.badge{border-radius:16px;padding:4px 10px;font-size:11px;white-space:nowrap;display:inline-block}.success{color:#15996a;background:#dff8eb}.warning{color:#d4801a;background:#fff0db}.muted{color:#8086aa;background:#eeeff8}.close{margin-left:auto;border:0;background:none;color:#71789c;font-size:27px;cursor:pointer}.plan{position:relative;background:linear-gradient(110deg,#f7f5ff,#f1f6ff);border:1px solid #e1e5f7;border-radius:6px;padding:12px 90px 12px 14px;margin:12px 0}.plan strong{font-size:14px;overflow-wrap:anywhere}.plan .el-tag{position:absolute;right:12px;top:18px}small{display:block;color:#7f86a9;font-size:12px;line-height:1.7}.plan small{margin-top:4px}.risk-banner{display:flex;justify-content:space-between;width:100%;padding:12px;border:1px solid #ffe0b6;background:#fff6eb;border-radius:6px;color:#ed8a1c;font-size:13px;font-weight:600;cursor:pointer}nav{display:flex;border-bottom:1px solid #dde2f6}nav button{flex:1;border:0;border-bottom:2px solid transparent;background:white;padding:15px 4px 12px;font-size:14px;color:#454e80;font-weight:600;cursor:pointer}nav button.active{border-bottom-color:#5947ff;color:#5947ff}main{flex:1;min-height:0;overflow:auto;padding:16px 22px 28px}h3{font-size:16px;margin:4px 0 14px}.section-heading{display:flex;justify-content:space-between;align-items:center;gap:12px}.range{display:flex;background:#efefff;border-radius:6px;padding:3px}.range button{border:0;background:none;padding:7px 12px;color:#737da9;border-radius:5px;cursor:pointer}.range button.active{background:#5847ff;color:#fff}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:12px 0}.metrics article{position:relative;border:1px solid #e3e7f8;border-radius:8px;padding:17px 12px;background:linear-gradient(120deg,#f5f4ff,#fff)}.metrics .el-icon{font-size:22px;background:#eeebff;color:#654eff;padding:7px;border-radius:8px;margin-bottom:8px}.metrics strong{display:block;font-size:22px;line-height:1.6}.metrics em,.large em{font-size:13px;font-style:normal;margin-left:4px}.pair{display:grid;grid-template-columns:1fr 1fr;gap:12px}section{border:1px solid #e1e5f5;border-radius:8px;padding:16px;margin:12px 0}.pair section{margin:0}.protection{background:linear-gradient(130deg,#fff8ed,#fff)}.improvement{background:linear-gradient(130deg,#ebfbf4,#fff);color:#125e4c}.large{font-size:23px}.panel-link{display:block;border:0;background:#f4f3ff;color:#5947ff;padding:10px;width:100%;border-radius:5px;margin-top:12px;cursor:pointer;font-size:12px}p{color:#7a83ab;font-size:12px;line-height:1.9;margin:8px 0}.completed-row{display:flex;align-items:center;gap:10px;font-size:12px;padding:9px 0;border-bottom:1px solid #f0f1fa}.completed-row .el-icon{color:#17aa70}.completed-row small{margin-left:auto;font-size:11px}.issue-link{display:flex;justify-content:space-between;gap:12px;border:1px solid #e4e6f7;border-radius:6px;background:white;color:#4a4d78;padding:12px;width:100%;font-size:12px;cursor:pointer}.issue-link span:last-child{color:#5947ff}.stats-strip,.summary{padding:12px;background:linear-gradient(120deg,#f3f2ff,#f1f6ff);border-radius:6px;font-size:12px;line-height:1.8;margin:10px 0;color:#55618c}.stats-strip button{border:0;float:right;background:none;color:#5947ff;cursor:pointer}.status-notice{padding:13px;border-radius:6px;font-size:13px;margin:12px 0}.state-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 0;border-bottom:1px solid #eef0fa;font-size:12px}.verification{margin-top:16px}.verification>.badge,.task>.badge{margin-left:10px}.steps{display:flex;gap:12px;padding:20px 0;font-size:12px;justify-content:space-between}.steps span{flex:1;border-top:2px solid #c7bfff;padding-top:12px}.steps span:last-child{border-top-style:dashed}.task{padding:14px;border:1px solid #e4e8f6;border-radius:6px;font-size:13px;margin:10px 0}.risk{background:#fffcf8}.actions{display:flex;gap:18px;align-items:center}.actions a{font-size:12px;color:#5947ff;text-decoration:none}.windows{display:flex;background:#f3f5ff;border-radius:6px;padding:12px;gap:14px}.windows>div{flex:1}.windows strong{display:block;margin-top:5px;font-size:14px}.history-filters{display:flex;gap:8px;margin:16px 0;flex-wrap:wrap}.history-filters>button{border:1px solid transparent;background:#f1f3fc;border-radius:18px;padding:8px 14px;color:#66749a;cursor:pointer;font-size:12px}.history-filters>button.active{border-color:#8c7aff;color:#5847ff;background:#f4f0ff}.history-filters .el-select{width:110px;margin-left:auto}.timeline>article{border-left:2px solid #e3e5f8;padding:0 0 18px 16px;position:relative}.timeline>article:before{content:'';position:absolute;left:-6px;top:4px;width:10px;height:10px;background:#8d87d1;border-radius:50%}.timeline details{border:1px solid #e3e7f6;border-radius:8px;padding:14px;margin-top:8px}.timeline summary{display:flex;align-items:center;gap:12px;cursor:pointer;font-size:13px}.timeline summary .badge{margin-left:auto}.current-rules{border:1px solid #e3e7f6;border-radius:7px;padding:14px;margin-top:16px}.current-rules>summary{cursor:pointer;font-size:13px;font-weight:600;list-style:none}.current-rules>summary span{float:right;color:#9297b4;font-weight:400}.rule-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-top:14px}.rule-grid>div{padding:12px;border:1px solid #eef0fa;border-radius:6px}.rule-grid strong{font-size:13px;display:block;margin-top:6px}footer{flex:none;display:flex;align-items:center;gap:12px;border-top:1px solid #e5e7f5;padding:12px 22px}footer>.el-button{font-size:12px}footer small{font-size:11px;max-width:230px}.footer-actions{margin-left:auto;display:flex;gap:10px}.footer-actions :deep(.el-button+.el-button){margin-left:0}@media(max-width:600px){header{padding:14px 14px 0}main{padding:14px}nav button{font-size:12px}.heading{gap:8px}.heading h2{font-size:16px}.pair{grid-template-columns:1fr}.metrics strong{font-size:17px}.metrics article{padding:12px 8px}.metrics small{font-size:10px}.plan strong{font-size:12px}footer{padding:12px 14px;flex-wrap:wrap}footer small{display:none}.windows{flex-wrap:wrap}.actions{flex-wrap:wrap}.rule-grid{grid-template-columns:1fr}}
</style>
