<template>
  <div class="wizard" :class="{ 'wizard--creating': step < 4 }">
    <aside v-if="draftStatus || !draftOnline" class="draft-save-note" :class="{ 'draft-save-note--warning': draftUnresolved || !draftOnline }" aria-live="polite">
      <p v-if="!draftOnline">网络已断开，未提交内容将继续保存在本机；联网后会重新核对服务器草稿。</p>
      <p v-if="draftStatus">{{ draftStatus }}</p>
      <div v-if="draftUnresolved"><button type="button" :disabled="draftRestoring" @click="autosave.retryRestore">{{ draftRestoring ? '正在核验商品…' : '载入本机内容核对' }}</button><button type="button" :disabled="draftRestoring" @click="autosave.keepServer">保留服务器内容</button></div>
    </aside>
    <RequestFailureNotice :failure="operationFailure" :busy="busy || recovering" @retry="recoverOperation" />
    <RequestFailureNotice :failure="pollFailure" context="状态暂未刷新，当前展示上次查询结果。" :busy="polling" @retry="poll" />
    <aside v-if="contentEditing" class="draft-save-note" role="status"><p>{{ state?.contentEdit?.message || '当前正在调整原投放计划的商品与素材；暂存修改只保存编辑内容，点击“保存修改”并同步成功后更新原计划。' }}</p><button v-if="contentLocked" type="button" :disabled="busy" @click="verifyContent">核查保存结果</button></aside>
    <CreateProductsStep ref="productStep" :adjustment-message="adjustmentMessage" @adjust-confirmed="adjustmentAcknowledged = true" :plan-name="name" :draft-save-disabled="!canSaveDraft" @save-draft="saveDraft" v-model:tip-dismissed="productTipDismissed" :draft-id="state?.planId" :plan-status="state?.contentEdit?.planStatus" :content-editing="contentEditing" :reached-step="reachedStep" :max-step="maxStep" @navigate="goToStep" v-if="step === 1" :key="accountKey" :initial-query="query" :pagination="productPagination" :cursor-history="productCursors" :products="products" :selection-locked="selectionLocked" :selected="selected" :loaded="loaded" :load-error="loadError" :load-failure="productFailure" :saving="busy || contentLocked || draftRestoring || draftUnresolved" :total="total" :analysis-status="sameAnalysisInput ? state?.analysisStatus || 'NONE' : 'NONE'" :analysis="sameAnalysisInput ? state?.analysis : undefined" :analysis-error="sameAnalysisInput ? state?.analysisError : undefined" :basic-analysis="sameAnalysisInput ? state?.basicAnalysis : undefined" :analysis-started-at="state?.analysisStartedAt" :account="account" :accounts="contentEditing ? [] : accountOptions" :account-chips="accountChips" @select="toggle" @remove="remove" @account="chooseAccount" @query="changeQuery" @catalog="ensureProductCatalog" @retry="loadProducts()" @keep="keepGroup" :group-decision="state?.groupingDecision" @group="applyGroup" @analyze="analyze" @next="nextGoal" @cancel="cancel" />
    <CreateGoalStep can-refresh-recommendations @refresh-recommendations="refreshRecommendations(true)" :read-only="contentEditing" @roi-input="editRoi" :plan-name="name" :draft-save-disabled="!canSaveDraft" @save-draft="saveDraft" :plan-status="state?.contentEdit?.planStatus" :content-editing="contentEditing" :reached-step="reachedStep" :max-step="maxStep" @navigate="goToStep" v-else-if="step === 2" v-model:budget="budget" v-model:roi="roi" v-model:duration="duration" v-model:start="start" v-model:end="end" :mode="mode === 'CUSTOM' ? 'SMART_BID_CUSTOM' : 'SMART_BID_CONSERVATIVE'" :saving="busy || contentLocked || draftRestoring || draftUnresolved" :budget-error="budgetError" budget-warn="" :roi-error="roiError" :date-error="dateError" :budget-hint="budgetHint" :roi-hint="roiHint" :estimate-roi-hint="estimateRoiHint" :estimate-orders-hint="estimateOrdersHint" :estimate-hint="estimateHint" :budget-options="budgetOptions" :order-lo="0" :order-hi="0" selection-warning="" :demo-mode="false" :account="account" :accounts="contentEditing ? [] : accountOptions" :account-chips="accountChips" @mode="changeMode" @account="chooseAccount" @prev="goToStep(1)" @next="saveGoal" @cancel="cancel" />
    <LiveMaterialsStep :initial-product-id="initialMaterialProduct" :initial-tab="initialMaterialTab" @regenerate-title="regenerateTitle" :resuming-materials="resumingMaterials" :draft-save-disabled="!canSaveDraft" @save-draft="saveDraft" :plan-status="state?.contentEdit?.planStatus" :content-editing="contentEditing" :reached-step="reachedStep" :max-step="maxStep" @navigate="goToStep" v-else-if="step === 3 && state" v-model:plan-name="name" :state="state" :busy="busy || contentLocked || draftRestoring || draftUnresolved" :account="account" :accounts="contentEditing ? [] : accountOptions" @account="chooseAccount" :account-chips="accountChips" :reconnecting="!!pollFailure" @prev="goToStep(2)" @generate="generate" @generate-addition="generateAddition" @add="add" @action="openAction" @resolve-version="resolveVersion" :version-error="operationFailure?.message" @titles="editTitles" @visibility="setVisibility" @recheck="recheck" @retry-sync="retrySync" @verify-sync="verifySync" @refresh-status="poll" @typography="repairTypography" @context="analyzeContext" @product-titles="editProductTitles" @retry-generation="retryGeneration" @publish="publish" @cancel="cancel" />
    <PlanResult v-else-if="state?.publishStatus === 'CREATED'" :plan-id="state.planId" />
    <div v-else-if="step === 4 && state" class="submit-result campaign-result ad-step3 ad-step4">
      <CreationFrame :step="4" :account="account" :account-chips="accountChips" :result-state="state.publishStatus === 'FAILED' ? 'warning' : undefined" />
      <main class="ad-main"><div class="container-page">
        <PlanSubmissionCard :phase="submissionPhase" :summary="submissionSummary" :error="state.publishError" :recovery="state.publishRecovery" :busy="busy || initializing || draftRestoring || draftUnresolved" @recover="recoverPublishFailure" @back="router.push('/campaigns')">
          <template v-if="state.publishStatus === 'UNCERTAIN'" #recovery>
            <div class="submit-recovery">
              <input v-model="reconcileId" inputmode="numeric" placeholder="填写已核对的千川计划ID" aria-label="千川计划ID" />
              <button type="button" :disabled="busy || !reconcileId.trim()" @click="reconcile">核验并关联平台计划</button>
            </div>
          </template>
        </PlanSubmissionCard>
      </div></main>
    </div>
    <RequestFailureNotice :failure="initialFailure" :busy="initializing" @retry="initialize" />
    <CreationActionDialog v-if="action" :action="action.kind" :error="operationFailure?.message" :material="state?.materials.find(m => m.id === action?.id)" :retry-at="state?.regenerationRetryAt" :busy="busy || contentLocked || draftRestoring || draftUnresolved" @close="action = null" @save="saveAndLeave" @discard="discardAndLeave" @confirm="feedback" />
  </div>
</template>
<script setup lang="ts">
import { roiInputError, scheduleInputError } from '@/features/delivery/inputValidation';
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';
import { ElMessageBox, ElMessage } from 'element-plus';
import { ApiRequestError, requestFailure } from '@/api/apiErrors';
import { pollRecoveryDelay } from '@/api/readRetry';
import RequestFailureNotice from '@/components/RequestFailureNotice.vue';
import { listAccounts, type AccountVo } from '@/features/identity/oauthApi';
import * as api from '@/features/delivery/creationApi';
import * as recommendations from '@/features/delivery/creationRecommendations';
import { recommendedRoiDefault } from '@/features/delivery/creationRoiDefault';
import { creationReachedStep, creationGoalChanged, validReachedStep } from '@/features/delivery/creationProgress';
import { productAdjustmentText, selectionImpact, selectionImpactText } from '@/features/delivery/selectionImpact';
import { productSelectionLocked } from '@/features/delivery/generationSelection';
import { quoteNeedsReview, quoteReviewKey } from '@/features/delivery/creationQuoteReview';
import { creationBudgetError } from '@/features/delivery/budgetValidation';
import { useCreationModeBudgets } from '@/features/delivery/creationModeBudgets';
import { awemeLabels } from '@/features/identity/awemeLabels';
import { getPlan } from '@/features/delivery/deliveryApi';
import { planSubmissionPhase, planSubmissionSummary } from '@/features/delivery/planSubmissionPresentation';
import { planPublishFailure, showPublishFailureResult, publishFailureTarget, publishFailureStep, type PublishFailureTarget } from '@/features/delivery/planPublishFailure';
import { creationScheduleDefaults } from '@/features/delivery/creationSchedule';
import { canContinueProductAnalysis, isCurrentCreationPoll, productAnalysisInput } from '@/features/delivery/productAnalysisReadiness';
import { DRAFT_INPUT_FIELDS, recalledDraftAccount, type DraftInputs, type DraftScope } from '@/features/delivery/creationDraftCache';
import { draftGoalFields, serverDraftInputs, useCreationDraftAutosave } from '@/features/delivery/useCreationDraftAutosave';
import { changedDraftFields } from '@/features/delivery/creationDraftCache';
import { useUserStore } from '@/stores/user';
import type { MaterialFeedback } from '@/features/creative/creativeApi';
import CreateProductsStep from './create/CreateProductsStep.vue';
import CreateGoalStep from './create/CreateGoalStep.vue';
import LiveMaterialsStep from './create/CreateMaterialsStep.vue';
import CreationActionDialog from './create/CreationActionDialog.vue';
import CreationFrame from './reference/CreationFrame.vue';
import PlanResult from './reference/PlanResult.vue';
import PlanSubmissionCard from './components/PlanSubmissionCard.vue';
import './create/create-theme.css';
const route = useRoute(), router = useRouter(), user = useUserStore();
const props = defineProps<{ initialPlanId?: string; initialStep?: number; initialMaterialTab?:'VIDEO'|'IMAGE'|'CAROUSEL'|'TITLE'; initialMaterialProduct?:string; publishedEditTarget?: string; adjustProductsOnOpen?: boolean }>();
const state = ref<api.CreationState>(), step = ref(1), busy = ref(false), loaded = ref(false), loadError = ref(false);
const resumingMaterials = ref(false);
const productStep = ref<InstanceType<typeof CreateProductsStep>>();
const adjustmentAcknowledged = ref(!!props.adjustProductsOnOpen);
const contentEditing=computed(()=>!!props.publishedEditTarget || !!state.value?.contentEdit);
const contentLocked=computed(()=>!!state.value?.contentEdit && state.value.contentEdit.status !== 'DRAFT');
const catalogLoading = ref(false);
const initialFailure = ref<ApiRequestError>(), productFailure = ref<ApiRequestError>(), operationFailure = ref<ApiRequestError>(), pollFailure = ref<ApiRequestError>();
const initializing = ref(false), recovering = ref(false), polling = ref(false);
const productTipDismissed = ref(false);
const accounts = ref<AccountVo[]>([]), accountKey = ref(''), selected = ref<api.CreationProduct[]>([]), products = ref<api.CreationProduct[]>([]), total = ref(0);
const query = ref<api.ProductQuery>({ page: 1, pageSize: 10, keyword: '', tab: 'ALL', unpromoted: true });
const productPagination = ref<{ nextCursor?: string | null; hasMore?: boolean }>({});
const productCursors = ref<Record<number, string>>({ 1: '0' });
const accountOptions = computed(() => accounts.value.filter(a => a.platform === 'QIANCHUAN' && a.status === 'ACTIVE' && a.credentialSource !== 'MOCK').flatMap(a => a.awemes.map(w => ({ key: `${a.accountId}:${w.awemeId}`, accountId: a.accountId, awemeId: w.awemeId, awemeName: w.name, avatar: w.avatar, type: w.type, authTypes: w.authTypes, isAllowMall: w.isAllowMall }))));
const account = computed(() => accountOptions.value.find(a => a.key === accountKey.value));
const accountChips = computed(() => awemeLabels(account.value));
const submissionCreatedAt = ref<string | null>();
const submissionPhase = computed(() => planSubmissionPhase(state.value || {}) || 'SUBMITTING');
const submissionSummary = computed(() => planSubmissionSummary({ ...state.value, createdAt: submissionCreatedAt.value }, account.value?.awemeName));
watch(() => step.value === 4 && state.value?.publishStatus !== 'CREATED' ? state.value?.planId : undefined, async (id, _previous, onCleanup) => {
  submissionCreatedAt.value = undefined;
  if (!id) return;
  const controller = new AbortController();
  onCleanup(() => controller.abort());
  try {
    const plan = await getPlan(id, { quiet: true, signal: controller.signal });
    if (!controller.signal.aborted) submissionCreatedAt.value = plan.createdAt;
  } catch { /* An unavailable creation time stays unknown; submission polling continues independently. */ }
});
const sameSelection = computed(() => !!state.value && JSON.stringify(state.value.products.map(p => p.qcProductId)) === JSON.stringify(selected.value.map(p => p.qcProductId)));
const pendingSelectionImpact = computed(() => state.value ? selectionImpact(state.value.products,selected.value,state.value.materials) : undefined);
const adjustmentMessage = computed(() => productAdjustmentText({
  materialCount: state.value?.materials.filter(material => material.status === 'READY').length ?? 0,
  hasAnalysis: !!state.value?.analysis || state.value?.analysisStatus === 'SUCCEEDED',
  hasGoal: !!budget.value.trim() || !!roi.value.trim(), published: contentEditing.value,
}));
const sameAnalysisInput = computed(() => !!state.value && productAnalysisInput(state.value.products) === productAnalysisInput(selected.value));
const analysisReady = computed(() => !!state.value && sameAnalysisInput.value
  && canContinueProductAnalysis(state.value.basicAnalysis, state.value.analysisStatus, state.value.analysis, selected.value));
const analysisPending = computed(() => !!state.value && ['QUEUED','RUNNING'].includes(state.value.analysisStatus));
const working = computed(() => !!state.value && ['QUEUED','RUNNING','REVISING','SUBMITTING','UNCERTAIN','IMPORTING'].includes(state.value.generationStatus));
const selectionLocked=computed(()=>contentLocked.value || productSelectionLocked(state.value));
const reconcileId = ref(''), reviewedQuote=ref('');
const maxStep = computed(() => analysisReady.value ? Math.max(step.value, state.value?.budget != null ? 3 : 2) : 1);
async function reconcile() { await run(async () => { acceptWrite(await api.creationReconcile(state.value!, reconcileId.value.trim())); }); }
const roi = ref(''), mode = ref<'CUSTOM'|'CONSERVATIVE'>('CUSTOM'), name = ref(''), duration = ref<'long'|'custom'>('long');
const { budget, budgets: modeBudgets, restore: restoreBudgets, reset: resetBudgets, applyRecommendation: applyBudgetRecommendation } = useCreationModeBudgets(mode);
const roiEdited = ref(false), automaticRoi = ref<string>();
function editRoi() { roiEdited.value=true;automaticRoi.value=undefined; }
function restoreRoi(value: string, source?: DraftInputs['roiSource']) {
  roi.value=value;
  const automatic=source==='PLATFORM' || source===undefined && !!value.trim() && state.value?.roi2Goal == null && Number(value)===recommendedRoi();
  roiEdited.value=source==='MANUAL' || !!value.trim() && !automatic;automaticRoi.value=automatic&&value.trim()?value:undefined;
}
const today = () => new Date(Date.now() + 8*3600000).toISOString().slice(0,10);
const initialSchedule = creationScheduleDefaults(today());
const start = ref(initialSchedule.start), end = ref(initialSchedule.end);
function captureDraft(): DraftInputs {
  return { productIds: selected.value.map(p => p.qcProductId), name: name.value, budget: budget.value, roi: roi.value, roiSource:roiEdited.value?'MANUAL':'PLATFORM',
    mode: mode.value, modeBudgets: { ...modeBudgets.value }, duration: duration.value, start: start.value, end: end.value, step: Math.min(3,step.value) };
}
function draftScope(): DraftScope | undefined {
  if (!user.me?.tenantId || !user.me.userId || !account.value) return;
  return { tenantId: user.me.tenantId, userId: user.me.userId, accountId: account.value.accountId,
    awemeId: account.value.awemeId, planId: state.value?.planId || null };
}
function fillDraft(values: DraftInputs) {
  name.value = values.name; mode.value = values.mode; restoreBudgets(values.budget,values.modeBudgets); restoreRoi(values.roi,values.roiSource);
  duration.value = values.duration; start.value = values.start; end.value = values.end;
  step.value = analysisReady.value ? values.step : 1;
}
async function restoreDraft(values: DraftInputs): Promise<string | undefined> {
  if (!account.value) throw new Error('当前账户不可用');
  const key = accountKey.value, planId = state.value?.planId;
  const rows = values.productIds.length ? await api.creationRestoreProducts(account.value.accountId,account.value.awemeId,values.productIds,state.value?.planId) : [];
  if (key !== accountKey.value || planId !== state.value?.planId) throw new Error('账户或草稿已切换');
  const eligible = rows.filter(p => values.productIds.includes(p.qcProductId) && p.name?.trim()
    && !p.draft && !p.unavailableReasons?.length && (p.stock == null || p.stock > 0));
  selected.value = values.productIds.flatMap(id => { const p = eligible.find(item => item.qcProductId === id); return p ? [p] : []; });
  fillDraft(values);
  const same = !!state.value && JSON.stringify(values.productIds) === JSON.stringify(state.value.products.map(p => p.qcProductId));
  const missing = values.productIds.filter(id => !eligible.some(p => p.qcProductId === id));
  if (!same || missing.length || !analysisReady.value) step.value = 1;
  else if (step.value === 3 && changedDraftFields(values,serverDraftInputs(state.value,today())).some(key => draftGoalFields.includes(key))) step.value = 2;
  return missing.length ? `已恢复可用内容；${missing.length} 个商品已被其他草稿占用、下架、不可投或信息不完整，未恢复选中（ID：${missing.join('、')}）。请重新确认选品。` : undefined;
}
const autosave = useCreationDraftAutosave({ scope: draftScope, server: () => state.value, today, capture: captureDraft, restore: restoreDraft,
  refreshServer: async () => { if (state.value) state.value = await api.creationGet(state.value.planId); },
  reconnect: () => { if (!disposed && !busy.value) { if (initialFailure.value || !account.value) void initialize(); else { void poll(); void loadProducts(); } } },
});
const { status: draftStatus, unresolved: draftUnresolved, restoring: draftRestoring, online: draftOnline } = autosave;
function acceptWrite(saved: api.CreationState, fields: (keyof DraftInputs)[] = []) {
  state.value = saved; autosave.acknowledge(fields);
}
function acceptRead(updated: api.CreationState) {
  const keepFailureResult = step.value === 4 && updated.publishStatus === 'FAILED';
  const refreshInputs = autosave.observeServer(updated);
  state.value = updated;
  if (refreshInputs) { selected.value = updated.products; fillDraft(serverDraftInputs(updated,today())); autosave.acknowledge(DRAFT_INPUT_FIELDS); }
  if (keepFailureResult) step.value = 4;
  if (updated.publishStatus === 'CREATED') autosave.discard();
}
const budgetError = computed(() => contentEditing.value ? '' : creationBudgetError(budget.value, mode.value));
const roiError = computed(() => contentEditing.value ? '' : roiInputError(roi.value, mode.value));
const dateError = computed(() => contentEditing.value ? '' : scheduleInputError(duration.value,start.value,end.value,today()));
const goalChanged = computed(() => creationGoalChanged(state.value, { mode:mode.value,budget:budget.value,roi:roi.value,duration:duration.value,start:start.value,end:end.value }));
const reachedStep = computed(() => validReachedStep(creationReachedStep(state.value,step.value),!sameAnalysisInput.value,goalChanged.value));
const draftHasGoal = computed(() => step.value >= 2 || sameSelection.value && state.value?.budget != null);
const canSaveDraft = computed(() => !!account.value && !!selected.value.length && !selectionLocked.value
  && (!draftHasGoal.value || !!name.value.trim() && !budgetError.value && !roiError.value && !dateError.value));
const budgetOptions = computed(() => recommendations.budgetOptions(state.value?.recommendations));
const estimate = computed(() => recommendations.budgetEstimateHints(state.value?.recommendations, budget.value));
const estimateRoiHint = computed(() => busy.value ? '正在获取平台预估…' : estimate.value.roi);
const estimateOrdersHint = computed(() => busy.value ? '正在获取平台预估…' : estimate.value.orders);
const estimateHint = computed(() => busy.value ? undefined : estimate.value.message);
const budgetHint = computed(() => busy.value ? '正在获取千川推荐预算…'
  : state.value?.recommendations?.budgetFromCache ? (state.value.recommendations.budgetStatus==='RATE_LIMITED' ? '千川请求频率受限，已保留上次推荐预算，可手动调整' : '千川本次推荐获取失败，已保留上次推荐预算，可手动调整')
  : state.value?.recommendations?.budgetError || (recommendedBudget() ? '自动获取巨量千川推荐预算，可调整' : '平台未返回推荐预算，可手动填写'));
const roiHint = computed(() => busy.value ? '正在获取千川推荐ROI…' : state.value?.recommendations?.roiError || (recommendedRoi() ? `千川推荐净成交ROI ${recommendedRoi()}，可参考实际经营目标调整` : '平台未返回推荐ROI，可根据经营目标填写'));
function recommendedBudget() { return recommendations.recommendedBudget(state.value?.recommendations); }
function recommendedRoi() { return recommendations.recommendedRoi(state.value?.recommendations); }
const action = ref<{ kind: 'cancel'|'regenerate'|'delete'; id?: string } | null>(null);
let disposed = false, pollTimer: ReturnType<typeof setTimeout> | undefined, requestNo = 0, pollFailures = 0;
let productRequest: AbortController | undefined, pollRequest: AbortController | undefined;
async function run(task: () => Promise<void>) {
  if (busy.value || draftRestoring.value || recovering.value) return;
  if (draftUnresolved.value) { ElMessage.warning('请先核对本机未提交内容与服务器草稿'); return; }
  operationFailure.value = undefined; busy.value = true;
  try { await task(); }
  catch (error) {
    const failure = requestFailure(error,{method:'POST'});
    if (failure.kind !== 'CANCELLED') operationFailure.value = failure;
    if (failure.kind === 'CONFLICT') {
      if (state.value) { try { acceptRead(await api.creationGet(state.value.planId)); } catch { /* Keep local inputs for explicit recovery. */ } }
      void loadProducts();
    }
  } finally { busy.value = false; }
}
async function recoverOperation() {
  if (busy.value || recovering.value || draftRestoring.value) return;
  recovering.value = true;
  try {
    if (state.value) { acceptRead(await api.creationGet(state.value.planId)); operationFailure.value = undefined; }
    else await router.push('/campaigns');
  } catch (error) { operationFailure.value = requestFailure(error,{method:'GET'}); }
  finally { recovering.value = false; }
}
function ensureProductCatalog() {
  if (!loaded.value && !catalogLoading.value) void loadProducts();
}
async function loadProducts() {
  if (!account.value) { products.value = []; loaded.value = true; return; }
  productRequest?.abort(); productRequest = new AbortController();
  const current = ++requestNo; catalogLoading.value = true; loaded.value = false; loadError.value = false; productFailure.value = undefined;
  try {
    const result = await api.creationProducts(account.value.accountId, account.value.awemeId, query.value, false, productRequest.signal, state.value?.planId);
    if (current !== requestNo) return;
    products.value = result.items;
    selected.value = selected.value.map(p => {
      const next = result.items.find(next => next.qcProductId === p.qcProductId);
      return next ? { ...next, userPrice: next.userPrice ?? p.userPrice } : p;
    });
    total.value = result.total;
    productPagination.value = { nextCursor: result.nextCursor, hasMore: result.hasMore };
    if (productCursors.value[query.value.page + 1] !== result.nextCursor) {
      for (const n of Object.keys(productCursors.value)) if (Number(n) > query.value.page) delete productCursors.value[Number(n)];
    }
    if (result.hasMore && result.nextCursor) productCursors.value[query.value.page + 1] = result.nextCursor;
    loaded.value = true;
  }
  catch (error) { if (!disposed && current === requestNo) { const failure=requestFailure(error,{method:'GET'}); if(failure.kind !== 'CANCELLED') { productFailure.value = failure; loadError.value = true; } loaded.value = true; } }
  finally { if (current === requestNo) catalogLoading.value = false; }
}
function changeQuery(q: api.ProductQuery) {
  const previous = query.value;
  if (q.tab !== previous.tab || q.keyword !== previous.keyword || q.pageSize !== previous.pageSize || q.unpromoted !== previous.unpromoted) {
    productCursors.value = { 1: '0' }; productPagination.value = {};
  }
  query.value = q; void loadProducts();
}
function toggle(p: api.CreationProduct) { if (busy.value || selectionLocked.value || draftRestoring.value || draftUnresolved.value) return; if (selected.value.some(x => x.productId === p.productId)) { remove(p.productId); return; } if (p.draft) { ElMessage.warning(`商品已在草稿「${p.draft.planName}」中，请继续编辑原草稿或先移除商品`); return; } if (p.unavailableReasons?.length) { ElMessage.warning(p.unavailableReasons.join('；')); return; } if (selected.value.length < 10) selected.value = [...selected.value, p]; else ElMessage.warning('最多选择 10 个商品'); }
function remove(id: string) { if (busy.value || selectionLocked.value || draftRestoring.value || draftUnresolved.value) return; selected.value = selected.value.filter(p => p.productId !== id); }
async function chooseAccount(key: string) {
  if (working.value || busy.value || draftRestoring.value || key === accountKey.value || !accountOptions.value.some(option => option.key === key)) return;
  if (!autosave.flush()) { ElMessage.warning('当前内容尚未可靠保存，请先处理草稿提示'); return; }
  productCursors.value = { 1: '0' }; productPagination.value = {}; query.value = { ...query.value, page: 1, cursor: undefined };
  autosave.suspend(); clearTimeout(pollTimer); productRequest?.abort(); pollRequest?.abort(); requestNo++;
  operationFailure.value = undefined; pollFailure.value = undefined; pollFailures = 0;
  if (state.value) ElMessage.info('原账户草稿已保留，切换账户将新建草稿');
  resumingMaterials.value = false; state.value = undefined; selected.value = []; fillDraft(serverDraftInputs(undefined,today())); accountKey.value = key;
  await router.replace({ path: '/campaigns/create', query: { account: key } }); if (disposed) return; await loadProducts(); await autosave.activate();
}
async function ensureSelection() {
  if(selectionLocked.value)throw new Error('素材正在生成或处理中，暂不能调整商品，请完成后再试');
  if (!account.value || !selected.value.length) throw new Error('请先选择授权账号和商品');
  const fresh = !state.value, emptyName = !name.value, selectionChanged=!!state.value && !sameSelection.value;
  const impact=pendingSelectionImpact.value;
  if (selectionChanged && impact?.changed && !adjustmentAcknowledged.value) {
    try { await ElMessageBox.confirm(selectionImpactText(impact,contentEditing.value),'确认商品与素材调整',{confirmButtonText:'确认调整商品',cancelButtonText:'返回继续核对',type:'warning',customClass:'selection-impact-confirm'}); }
    catch { return false; }
  }
  if(selectionLocked.value)throw new Error('素材正在生成或处理中，暂不能调整商品，请完成后再试');
  const saved = await api.creationSelect({ planId: state.value?.planId, revision: state.value?.revision, accountId: account.value.accountId, awemeId: account.value.awemeId, productIds: selected.value.map(p => p.qcProductId) });
  if (fresh) autosave.suspend();
  state.value = saved;
  if (fresh || selectionChanged) resumingMaterials.value = false;
  if(selectionChanged && !contentEditing.value){resetBudgets();restoreRoi('');reviewedQuote.value='';}
  name.value ||= state.value.name; selected.value = state.value.products;
  if (fresh) { await autosave.activate(true); }
  else autosave.acknowledge(['productIds',...(selectionChanged ? draftGoalFields : emptyName ? ['name' as const] : [])]);
  if (!props.initialPlanId) await router.replace({ path: '/campaigns/create', query: { draftId: state.value.planId } });
  adjustmentAcknowledged.value = false;
  return true;
}
async function keepGroup(){ await run(async()=>{acceptWrite(await api.creationKeepGroup(state.value!));}); }
async function applyGroup(ids: string[]) {
  if(busy.value || selectionLocked.value || !ids.length)return;
  selected.value=selected.value.filter(p=>ids.includes(p.qcProductId));await analyze();
}
async function analyze(refresh = false) { await run(async () => { if(!await ensureSelection())return; acceptWrite(await api.creationAnalyze(state.value!, refresh)); schedulePoll(); }); }
async function persistStep(target: number) {
  if (state.value && (state.value.step !== target || creationReachedStep(state.value) < target)) acceptWrite(await api.creationStep(state.value, target));
  if (state.value) autosave.acknowledge(['step'],state.value.revision,{ ...captureDraft(),step: target });
}
async function goToStep(target: number) {
  if (target > 1 && state.value && !sameAnalysisInput.value) { ElMessage.warning('商品已调整，请先更新商品分析'); return; }
  if (target < 1 || target > 3 || target > maxStep.value) return;
  await run(async () => { if(step.value === 2 && (target === 3 || !budgetError.value && !roiError.value && !dateError.value)) await persistGoal(); await persistStep(target); step.value = target; window.scrollTo(0,0); });
  if(target === 2 && step.value === 2) await refreshRecommendations();
}
async function nextGoal() {
  if (!analysisReady.value) return;
  await goToStep(2);
}
async function refreshRecommendations(force = false) {
  if(contentEditing.value)return;
  if (!state.value) return;
  if (!force && !recommendations.recommendationsNeedRefresh(state.value)) { applyRecommendedInputs(); return; }
  await run(async () => {
    const previous = state.value!, updated = await api.creationRecommendations(previous.planId, force);
    acceptRecommendations(previous,updated);
    applyRecommendedInputs();
  });
}
function acceptRecommendations(previous: api.CreationState, updated: api.CreationState) {
  // A recommendation refresh advances the revision without saving the user's goal inputs.
  if (previous.planId === updated.planId && previous.accountId === updated.accountId && previous.awemeId === updated.awemeId
    && !changedDraftFields(serverDraftInputs(previous,today()),serverDraftInputs(updated,today())).length) acceptWrite(updated);
  else acceptRead(updated);
}
function applyRecommendedInputs() {
  if (recommendedBudget() && !creationBudgetError(String(recommendedBudget()), mode.value)) applyBudgetRecommendation(String(recommendedBudget()));
  const next=recommendedRoiDefault(roi.value,automaticRoi.value,recommendedRoi(),roiEdited.value);
  roi.value=next.value;automaticRoi.value=next.defaultValue;
}
async function changeMode(value: 'SMART_BID_CUSTOM' | 'SMART_BID_CONSERVATIVE') {
  if (busy.value) return;
  const nextMode = value === 'SMART_BID_CUSTOM' ? 'CUSTOM' : 'CONSERVATIVE';
  if (nextMode === mode.value) return;
  mode.value = nextMode;
  await refreshRecommendations(nextMode === 'CONSERVATIVE');
}
async function persistGoal() {
  if(contentEditing.value)return;
  if (!state.value || budgetError.value || roiError.value || dateError.value || !name.value.trim()) throw new Error('请检查计划名称、预算、ROI 和日期');
  const goal = { name: name.value.trim(), smartBidType: mode.value, budget: Number(budget.value), roi2Goal: mode.value === 'CUSTOM' ? Number(roi.value) : null, scheduleType: duration.value === 'custom' ? 'DATE_RANGE' : 'FROM_NOW', startDate: duration.value === 'custom' ? start.value : null, endDate: duration.value === 'custom' ? end.value : null };
  const saved = state.value;
  if (saved.name === goal.name && saved.smartBidType === goal.smartBidType && saved.budget === goal.budget && (saved.roi2Goal ?? null) === goal.roi2Goal && saved.scheduleType === goal.scheduleType && (saved.startDate ?? null) === goal.startDate && (saved.endDate ?? null) === goal.endDate) return;
  acceptWrite(await api.creationGoal(saved, goal),draftGoalFields);
}
async function saveGoal() { await run(async () => { await persistGoal(); await persistStep(3); step.value = 3; window.scrollTo(0,0); }); }
async function generate(ids: string[]) { await run(async () => { acceptWrite(await api.creationGenerate(state.value!, ids)); schedulePoll(); }); }
async function generateAddition(body: api.CreationAdditionBody) { await run(async () => { acceptWrite(await api.creationAddition(state.value!, body)); schedulePoll(); }); }
async function regenerateTitle(product:string,kind:'CUSTOM'|'CARD',index:number,original:string) { await run(async()=>{acceptWrite(await api.creationRegenerateTitle(state.value!,product,kind,index,original));schedulePoll();}); }
async function editProductTitles(product: string,kind:'CUSTOM'|'CARD',titles:string[]) { await run(async()=>{acceptWrite(await api.creationProductTitles(state.value!,product,kind,titles));schedulePoll();}); }
async function retryGeneration(type:'VIDEO'|'IMAGE'|'CAROUSEL') {
  try { await ElMessageBox.confirm('仅重试此类型未完成的生成项，成功素材会保留；模型服务可能产生新的生成费用。','重试失败项',{confirmButtonText:'重试失败项',cancelButtonText:'暂不重试'}); } catch { return; }
  await run(async()=>{acceptWrite(await api.creationRetryGeneration(state.value!,type));schedulePoll();});
}
async function editTitles(id: string, titles: string[]) { await run(async () => { acceptWrite(await api.creationTitles(state.value!, id, titles)); schedulePoll(); }); }
async function setVisibility(hideInAweme: boolean) { await run(async () => { acceptWrite(await api.creationVisibility(state.value!, hideInAweme)); }); }
async function analyzeContext(id: string) { await run(async () => { acceptWrite(await api.creationMaterialContext(state.value!, id)); schedulePoll(); }); }
async function recheck(id: string) { await run(async () => { acceptWrite(await api.creationRecheck(state.value!, id)); schedulePoll(); }); }
async function verifySync(id: string) {
  if (!state.value || busy.value || draftRestoring.value || recovering.value) return;
  const snapshot = state.value, planId = snapshot.planId, revision = snapshot.revision;
  pollRequest?.abort();
  busy.value = true; operationFailure.value = undefined;
  try {
    const result = await api.creationVerifySync(snapshot, id);
    if (disposed || state.value?.planId !== planId || state.value.revision !== revision || result.revision !== revision) return;
    const material = state.value.materials.find(m => m.id === result.materialId);
    if (material) { material.processing = result.processing; ElMessage.info(result.message); }
  } catch (error) {
    if (!disposed && state.value?.planId === planId) operationFailure.value = requestFailure(error, {method:'GET'});
  } finally { busy.value = false; }
}
async function retrySync(id: string) { await run(async () => { acceptWrite(await api.creationRetrySync(state.value!,id)); schedulePoll(); }); }
async function repairTypography(id: string) { await run(async () => { acceptWrite(await api.creationTypography(state.value!, id)); schedulePoll(); }); }
async function add(body: Parameters<typeof api.creationAdd>[1]) { await run(async () => { acceptWrite(await api.creationAdd(state.value!, body)); schedulePoll(); }); }
function openAction(kind: 'regenerate'|'delete', id: string) { if (!busy.value) action.value = { kind, id }; }
async function resolveVersion(id: string, decision: 'REPLACE' | 'KEEP_BOTH' | 'DISCARD') { await run(async () => { acceptWrite(await api.creationResolveVersion(state.value!, id, decision)); schedulePoll(); }); }
async function feedback(body: MaterialFeedback) { const a = action.value; if (!a?.id) return; await run(async () => { acceptWrite(await api.creationFeedback(state.value!, a.id!, a.kind, body)); action.value = null; schedulePoll(); }); }
async function recoverPublishFailure(choice: PublishFailureTarget) {
  if (!state.value || state.value.publishStatus !== 'FAILED' || busy.value || initializing.value || draftRestoring.value || draftUnresolved.value) return;
  const recovery = planPublishFailure(state.value.publishRecovery, state.value.publishError);
  const target = publishFailureTarget(state.value.publishRecovery, state.value.publishError, choice);
  if (target === 'ACCOUNTS') { await router.push({ path: '/accounts', query: { tab: 'oauth' } }); return; }
  const targetStep = publishFailureStep(target);
  if (!targetStep) return;
  await run(async () => {
    if (recovery.refreshEstimate) {
      const previous = state.value!;
      acceptRecommendations(previous, await api.creationRecommendations(previous.planId, true));
      reviewedQuote.value = '';
    }
    await persistStep(targetStep);
    step.value = targetStep;
    window.scrollTo(0, 0);
  });
  if (targetStep === 1 && step.value === 1) {
    await nextTick();
    await productStep.value?.confirmProductAdjustment();
  }
}
async function reviewPublishEstimate() {
  if(!state.value || busy.value)return;
  await run(async()=>{
    const old=estimate.value.roi;
    const previous=state.value!;
    acceptRecommendations(previous,await api.creationRecommendations(previous.planId,true));
    try { await ElMessageBox.confirm(`日预算保持 ${budget.value} 元，不会自动增加。此前预计 ROI：${old}；当前平台预估：${estimate.value.roi}。请核对目标与预算，确认后可再次点击发布。`, '核对最新平台预估', {confirmButtonText:'已核对，返回素材',cancelButtonText:'返回目标设置'}); reviewedQuote.value=quoteReviewKey(state.value!,budget.value); step.value=3; }
    catch { step.value=2; }
  });
}
async function verifyContent(){await run(async()=>{if(!state.value)return;acceptRead(await api.verifyContentEdit(state.value));await contentSaved();});}
async function contentSaved(){const s=state.value;if(s?.contentEdit?.status==='SUCCEEDED'){autosave.discard();ElMessage.success('修改已保存到原投放计划');await router.replace({path:`/campaigns/${s.contentEdit.targetPlanId}`,query:{step:'3'}});}}
async function publish() {
  if(contentEditing.value){
    try{await ElMessageBox.confirm('将当前商品、标题与素材保存到原投放计划。保留商品的现有素材继续使用，移出的商品及其素材将停止在本计划中展示和投放；素材中心文件保留。已发生的生成用量不会撤销。','保存商品与素材修改',{confirmButtonText:'保存修改',cancelButtonText:'返回核对',type:'warning'});}catch{return;}
    await run(async()=>{acceptWrite(await api.saveContentEdit(state.value!));await contentSaved();schedulePoll();});return;
  }
  if(state.value && (quoteNeedsReview({...state.value,smartBidType:mode.value},budget.value) || state.value.publishRecovery === 'REFRESH_ESTIMATE') && reviewedQuote.value !== quoteReviewKey(state.value,budget.value)){await reviewPublishEstimate();return;}
  await run(async () => { if(name.value.trim() !== state.value!.name)await persistGoal(); const s = state.value!; const key = s.publishStatus === 'FAILED' ? crypto.randomUUID() : sessionStorage.getItem(`creation:${s.planId}:${s.revision}`) || crypto.randomUUID(); sessionStorage.setItem(`creation:${s.planId}:${s.revision}`, key); acceptWrite(await api.creationPublish(s, key)); autosave.discard(); step.value = 4; schedulePoll(); }); }
function cancel() { if(contentEditing.value){
  if(!contentLocked.value)void ElMessageBox.confirm('是否暂存本次商品与素材调整？暂存后可从原计划“修改”入口继续编辑，原投放内容保持不变。若丢弃修改，已生成素材仍保留在素材中心，生成用量不会撤销。','离开商品与素材调整',{confirmButtonText:'暂存并离开',cancelButtonText:'丢弃本次修改',distinguishCancelAndClose:true,type:'warning'}).then(saveAndLeave).catch(reason=>{if(reason==='cancel')void discardAndLeave();});
  else void router.push('/campaigns');return;
} if (!busy.value) action.value = { kind: 'cancel' }; }
async function saveDraft() {
  if (!canSaveDraft.value) return;
  await run(async () => {
    if ((!state.value || !sameSelection.value) && !await ensureSelection()) return;
    if (draftHasGoal.value) await persistGoal();
    await persistStep(step.value);
    ElMessage.success(contentEditing.value ? '修改已暂存，尚未同步原投放计划' : '草稿已保存');
  });
}
async function saveAndLeave() {
  await run(async () => {
    if ((!state.value || !sameSelection.value) && !await ensureSelection()) return;
    if (step.value >= 2 && !working.value && !budgetError.value && !roiError.value && !dateError.value) await persistGoal();
    await persistStep(step.value);
    ElMessage.success(contentEditing.value ? '修改已暂存，可通过原计划的修改入口继续编辑' : '草稿已保存，可在投放列表继续编辑');
    await router.push('/campaigns');
  });
}
async function discardAndLeave() { await run(async () => { if (state.value) await api.creationDiscard(state.value.planId); autosave.discard(); await router.push('/campaigns'); }); }
function schedulePoll() { clearTimeout(pollTimer); if (!disposed && state.value) pollTimer = setTimeout(poll, state.value.generationResultStatus === 'UNCERTAIN' ? 30000 : analysisPending.value || working.value || state.value.titleGeneration && ['SENDING','PROCESSING'].includes(state.value.titleGeneration.status) || state.value.materials.some(m => m.status === 'PROCESSING' || m.processing?.stage === 'REVIEW') || ['QUEUED','SENDING'].includes(state.value.publishStatus) ? 2500 : 15000); }
async function poll() {
  clearTimeout(pollTimer);
  if (disposed || !state.value || polling.value || busy.value) { if (!disposed && state.value) schedulePoll(); return; }
  const planId = state.value.planId, selectedAccount = accountKey.value;
  pollRequest = new AbortController(); polling.value = true;
  try {
    const updated = await api.creationGet(planId,pollRequest.signal);
    if (!disposed && !busy.value && !draftRestoring.value && accountKey.value === selectedAccount && isCurrentCreationPoll(state.value, updated, planId)) {
      acceptRead(updated); if(contentEditing.value)await contentSaved(); pollFailure.value = undefined; pollFailures = 0;
      if (['QUEUED','SENDING','UNCERTAIN','CREATED'].includes(updated.publishStatus)) step.value = 4;
    }
  } catch (error) {
    if (!disposed && accountKey.value === selectedAccount && state.value?.planId === planId) {
      const failure=requestFailure(error,{method:'GET'});
      if (failure.kind !== 'CANCELLED') { pollFailure.value = failure; pollFailures++; }
    }
  } finally { polling.value = false; }
  if (!disposed && state.value && state.value.publishStatus !== 'CREATED') {
    if (!pollFailure.value) schedulePoll();
    else { const delay=pollRecoveryDelay(pollFailure.value,pollFailures); if (delay !== undefined) pollTimer=setTimeout(poll,delay); }
  }
}
async function initialize() {
  if (initializing.value || disposed) return;
  initialFailure.value = undefined; initializing.value = true;
  try {
    accounts.value = await listAccounts();
    if (disposed) return;
    const id = props.initialPlanId || (typeof route.query.draftId === 'string' ? route.query.draftId : '');
    if (id) {
      state.value = await api.creationGet(id); if (disposed) return;
      const s = state.value;
      resumingMaterials.value = creationReachedStep(s) >= 3;
      accountKey.value = `${s.accountId}:${s.awemeId}`; selected.value = s.products;
      const noticeMaterials = typeof route.query.notice === 'string' && route.query.step === '3' && creationReachedStep(s) >= 3;
      const restoredStep = ['QUEUED', 'SENDING', 'UNCERTAIN', 'CREATED'].includes(s.publishStatus) || showPublishFailureResult(s.publishStatus, props.initialStep, noticeMaterials) ? 4
        : Math.min(creationReachedStep(s), Math.max(1, Math.min(4, props.initialStep ?? (noticeMaterials ? 3 : s.step))));
      step.value = restoredStep < 4 && !analysisReady.value && !noticeMaterials && !(contentEditing.value && props.initialStep===3 && !props.adjustProductsOnOpen) ? 1 : restoredStep;
      name.value = s.name; mode.value = s.smartBidType; restoreBudgets(s.budget == null ? '' : String(s.budget));
      restoreRoi(s.roi2Goal == null ? '' : String(s.roi2Goal)); duration.value = s.scheduleType === 'DATE_RANGE' ? 'custom' : 'long';
      const schedule = creationScheduleDefaults(today(), s.startDate, s.endDate); start.value = schedule.start; end.value = schedule.end;
      schedulePoll();
    }
    else {
      let remembered: string | undefined;
      try { if (user.me?.tenantId) remembered = recalledDraftAccount(localStorage,user.me.tenantId,user.me.userId); } catch { /* Storage can be disabled. */ }
      const requested = typeof route.query.account === 'string' ? route.query.account : undefined;
      accountKey.value = accountOptions.value.find(a => a.key === requested)?.key || accountOptions.value.find(a => a.key === remembered)?.key || accountOptions.value[0]?.key || '';
    }
    if (!account.value) initialFailure.value = new ApiRequestError('尚无可用的千川授权抖音号，请在账户管理中完成授权。',{kind:'AUTHORIZATION',action:'ACCOUNTS',method:'GET'});
    // Restored analysis is independent of the catalog; load it when the selection list opens.
    if (!state.value || state.value.analysisStatus === 'NONE' && !state.value.analysis) await loadProducts();
    if (!disposed) {
      await autosave.activate();
      const noticeMaterials = typeof route.query.notice === 'string' && route.query.step === '3';
      const recoveryStep = state.value?.publishStatus === 'FAILED' && typeof route.query.recover === 'string'
        ? publishFailureStep(publishFailureTarget(state.value.publishRecovery, state.value.publishError, route.query.recover)) : undefined;
      if (recoveryStep) step.value = recoveryStep;
      else if (showPublishFailureResult(state.value?.publishStatus, props.initialStep, noticeMaterials, sameSelection.value)) step.value = 4;
      if (step.value < 4 && !analysisReady.value && !(contentEditing.value && props.initialStep===3 && !props.adjustProductsOnOpen) && !(typeof route.query.notice === 'string' && route.query.step === '3' && state.value && creationReachedStep(state.value) >= 3)) step.value = 1;
      if(step.value === 2 && state.value) {
        await refreshRecommendations(planPublishFailure(state.value.publishRecovery, state.value.publishError).refreshEstimate);
      }
      if ((props.adjustProductsOnOpen || recoveryStep === 1) && step.value === 1 && !initialFailure.value) {
        await nextTick();
        if (props.adjustProductsOnOpen) await productStep.value?.openProductAdjustment();
        else await productStep.value?.confirmProductAdjustment();
      }
    }
  } catch (error) { if (!disposed) { const failure=requestFailure(error,{method:'GET'}); if (failure.kind !== 'CANCELLED') initialFailure.value = failure; } }
  finally { initializing.value = false; }
}
onMounted(initialize); onUnmounted(() => { disposed = true; clearTimeout(pollTimer); productRequest?.abort(); pollRequest?.abort(); requestNo++; });
onBeforeRouteLeave(() => { if (!autosave.flush()) { ElMessage.warning('未提交内容尚未可靠保存，请先处理草稿提示或明确选择不保存'); return false; } });
onBeforeRouteUpdate((to, from) => {
  if (!props.initialPlanId && to.query.draftId !== from.query.draftId && to.query.draftId !== state.value?.planId && !autosave.flush()) {
    ElMessage.warning('未提交内容尚未可靠保存，请先处理草稿提示或明确选择不保存'); return false;
  }
});
watch(() => route.query.draftId, id => { if (!props.initialPlanId && id !== state.value?.planId) void initialize(); });
watch(() => route.query.notice, () => {
  if (route.query.step === '3' && state.value && creationReachedStep(state.value) >= 3) { step.value = 3; resumingMaterials.value = true; }
});
</script>
<style scoped>
.creation-error{padding:24px;color:#db515b;text-align:center}.creation-error button{margin-left:15px}.submit-result{background:#f7f7ff;min-height:85vh}
.submit-result .ad-main{padding:24px 0 32px}.submit-recovery{display:flex;justify-content:center;flex-wrap:wrap;gap:12px}.submit-recovery input{min-width:0;max-width:100%;padding:10px 12px;border:1px solid #dcddeb;border-radius:8px;font:inherit}.submit-recovery button{padding:10px 16px;border:1px solid #dcddeb;border-radius:8px;background:white;color:#575cf6;font:inherit;cursor:pointer}.submit-recovery button:disabled{opacity:.5;cursor:not-allowed}
.draft-save-note{max-width:1200px;margin:12px auto;padding:10px 16px;border:1px solid var(--color-primary-soft);border-radius:10px;background:white;color:var(--color-muted);font-size:13px;line-height:1.6;overflow-wrap:anywhere}.draft-save-note--warning{border-color:#f4cc91;background:#fff9ef}.draft-save-note p{margin:0}.draft-save-note button{margin:8px 16px 0 0;border:0;background:transparent;color:var(--color-primary);font:inherit;text-decoration:underline;cursor:pointer}.draft-save-note button:disabled{opacity:.5;cursor:wait}
.wizard > .request-failure{max-width:1200px;margin:12px auto}
.wizard--creating :deep(.ad-main){padding-bottom:132px}
@media(max-width:767px){.wizard--creating :deep(.ad-main){padding-bottom:192px}}
</style>
