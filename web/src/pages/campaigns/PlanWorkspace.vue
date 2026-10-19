<template>
  <div class="campaign-reference creative-workspace wizard plan-workspace">
    <div v-if="loading" class="workspace-loading" role="status">正在加载计划…</div>
    <div v-else-if="loadError" class="workspace-loading" role="alert"><p>{{ loadError }}</p><button class="btn-prev" @click="load">重新加载</button><router-link to="/campaigns">返回投放列表</router-link></div>
    <template v-else-if="plan">
      <header class="workspace-header container-page">
        <div class="workspace-heading"><router-link class="workspace-back" to="/campaigns"><ReferenceIcon name="arrow-left" />返回投放列表</router-link><span class="workspace-mode">{{ transferring ? '转投计划' : draftEditor ? '继续编辑草稿' : editing ? '修改投放计划' : '查看投放计划' }}</span><span class="stpill" :class="`stpill--${statusInfo(plan.status)[1]}`">{{ planStatusLabel(plan.status, plan.qcStatus) }}</span></div>
        <div class="workspace-title">
          <input v-if="editing && !transferring && !draftEditor" v-model="name" maxlength="30" :disabled="saving || !canEdit" aria-label="计划名称" />
          <h1 v-else>{{ plan.name }}</h1>
          <button v-if="!editing && canEdit" class="btn-next-primary" type="button" @click="enterEdit"><ReferenceIcon name="edit" />修改计划</button>
        </div>
        <p class="workspace-meta">计划ID：{{ plan.qcPlanId || plan.planId }} · {{ transferring ? '仅第二步可修改投放方式、日预算与ROI，商品与素材保持原设置' : draftEditor ? '继续完成投放设置与素材，确认后发布投放' : editing ? '修改完成后点击保存修改生效' : '当前为只读模式，可切换步骤查看计划内容与素材预览' }}</p>
        <p v-if="transferring" class="workspace-notice">{{ transferContext?.reuse ? '确认后暂停当前计划，启用另一种投放方式的已有计划。' : '确认后暂停当前计划，并使用已发布的商品与素材创建另一种投放方式的计划。' }}两种投放方式均展示在列表中，共用商品与素材，同时只启用一种。</p>
        <p v-if="transferring && transferMessage" class="workspace-notice" role="status">{{ transferMessage }}</p>
        <p v-if="editing && !canEdit" class="workspace-notice" role="status">当前计划状态或账户权限不支持修改，内容以只读方式展示。</p>
        <p v-else-if="editing && !transferring && !draftEditor" class="workspace-notice">可修改计划名称、日预算{{ isCost ? '与净成交ROI目标' : '' }}。已发布计划的商品、投放方式、日期与素材沿用原设置。</p>
        <p v-if="mediaError" class="workspace-notice" role="status">{{ mediaError }}<button class="panel__toggle" @click="load">重新加载素材</button></p>
        <p v-if="nameError" class="workspace-error" role="alert">{{ nameError }}</p>
      </header>

      <Create v-if="draftEditor" :key="plan.planId" :initial-plan-id="plan.planId" :initial-step="draftInitialStep" />
      <template v-else>
        <CreationFrame :step="step" :account="account" :account-chips="accountChips" navigable :navigation-disabled="saving" @navigate="navigate" />
        <CreateProductsStep v-if="step === 1" :products="products" :selected="products" :loaded="true" :load-error="false" :saving="false" :account="account" :accounts="[]" :account-chips="accountChips" :total="products.length" :analysis-status="creation?.analysisStatus || 'NONE'" :analysis-error="creation?.analysisError" :analysis="creation?.analysis" read-only embedded />
        <CreateGoalStep v-else-if="step === 2" v-model:budget="budget" v-model:roi="roi" v-model:duration="duration" v-model:start="start" v-model:end="end" :mode="bidType" :saving="saving" :read-only="transferring ? !transferAllowed : !editing || !canEdit" :allow-mode-change="transferring" settings-only embedded :budget-options="budgetOptions(creation?.recommendations)" :budget-error="budgetError" budget-warn="" :roi-error="roiError" :order-lo="0" :order-hi="0" :estimate-roi-hint="estimates.roi" :estimate-orders-hint="estimates.orders" :budget-hint="budgetHint" :roi-hint="editing ? '输入修改后的净成交ROI目标' : '当前计划的净成交ROI目标'" selection-warning="" :demo-mode="false" :account="account" :accounts="[]" :account-chips="accountChips" @mode="changeTransferMode" />
        <template v-else-if="step === 3">
          <CreateMaterialsStep v-if="materialState" v-model:plan-name="name" :state="materialState" :busy="false" :account="account" :account-chips="accountChips" read-only embedded />
          <main v-else class="ad-main"><div class="container-page"><section class="panel"><div class="panel__pad"><h2 class="panel__title">投放素材</h2><p class="workspace-empty">平台暂未提供此计划的素材预览信息。</p></div></section></div></main>
        </template>
        <main v-else class="ad-main"><div class="container-page"><section class="panel"><div class="panel__pad"><h2 class="panel__title">投放结果</h2><p class="panel__sub">{{ plan.rejectReason || '投放状态与效果以千川同步数据为准' }}</p><dl class="workspace-result"><div><dt>投放状态</dt><dd>{{ planStatusLabel(plan.status, plan.qcStatus) }}</dd></div><div><dt>投放时间</dt><dd>{{ plan.startDate || '—' }} 至 {{ materialState?.scheduleType === 'FROM_NOW' ? '不限' : plan.endDate || '不限' }}</dd></div><div><dt>统计日期</dt><dd>{{ plan.statisticsDate || '—' }}{{ plan.historicalMetrics ? '（今日暂无报表，展示历史数据）' : '' }}</dd></div><div><dt>数据同步时间</dt><dd>{{ dateText(plan.snapshotTs) }}</dd></div></dl><PlanKpis :metrics="{ cost: plan.costYuan, gmv: plan.gmvYuan, roi: plan.roi, orders: plan.convert }" /><div class="workspace-analysis"><router-link class="panel__toggle" :to="`/campaigns/${plan.planId}/analysis`">查看投放效果分析<ReferenceIcon name="forward" /></router-link></div></div></section></div></main>
        <div class="actionbar"><div class="container-page actionbar__inner">
          <button v-if="step > 1" class="btn-prev" type="button" :disabled="saving" @click="navigate(step - 1)">上一步</button>
          <button v-if="step < 4" class="btn-next-primary" type="button" :disabled="saving" @click="navigate(step + 1)">下一步</button>
          <button v-if="transferring && !transferPending" class="btn-next-primary" type="button" :disabled="saving || !transferAllowed || bidType === plan.smartBidType || !!budgetError || !!roiError" @click="submitTransfer">{{ saving ? '转投处理中…' : '确认转投' }}</button>
          <button v-else-if="transferring && user.trialActive" class="btn-next-primary" type="button" :disabled="saving" @click="reconcileTransfer">{{ saving ? '核查中…' : '核查转投结果' }}</button>
          <button v-else-if="editing && canEdit" class="btn-next-primary" type="button" :disabled="saving || !dirty || !!nameError || !!budgetError || !!roiError" @click="save">{{ saving ? '保存中…' : '保存修改' }}</button>
          <button v-if="editing" class="btn-cancel" type="button" :disabled="saving" @click="cancelEdit">{{ transferring ? transferPending ? '返回详情' : '取消转投' : '取消修改' }}</button>
          <router-link v-else class="btn-cancel" to="/campaigns">返回投放列表</router-link>
        </div></div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getPlan, updatePlanSettings, updateDraft, getPlanTransfer, transferPlan, reconcilePlanTransfer, type PlanVo, type PlanTransferContext, type PlanTransferRequest, type PlanTransferResult } from '@/features/delivery/deliveryApi';
import { creationGet, type CreationProduct, type CreationState } from '@/features/delivery/creationApi';
import { budgetEstimateHints, budgetOptions } from '@/features/delivery/creationRecommendations';
import { listAccounts, type AccountVo } from '@/features/identity/oauthApi';
import { useUserStore } from '@/stores/user';
import { planStatusLabel } from '@/utils/labels';
import { dateText, statusInfo } from './reference/planPresentation';
import CreationFrame from './reference/CreationFrame.vue';
import ReferenceIcon from './reference/ReferenceIcon.vue';
import PlanKpis from './reference/PlanKpis.vue';
import CreateProductsStep from './create/CreateProductsStep.vue';
import CreateGoalStep from './create/CreateGoalStep.vue';
import { COST_DAILY_BUDGET_MINIMUM, creationBudgetError, creationRoiError } from '@/features/delivery/budgetValidation';
import CreateMaterialsStep from './create/CreateMaterialsStep.vue';
import Create from './Create.vue';
import './reference/reference.css';
import './reference/creative-workspace.css';
import './create/create-theme.css';

const props = defineProps<{ mode: 'view' | 'edit' | 'transfer' }>();
const route = useRoute(), router = useRouter(), user = useUserStore();
const plan = ref<PlanVo>(), creation = ref<CreationState>(), accounts = ref<AccountVo[]>([]);
const loading = ref(true), saving = ref(false), loadError = ref(''), mediaError = ref('');
const name = ref(''), budget = ref(''), roi = ref(''), duration = ref<'long' | 'custom'>('long'), start = ref(''), end = ref('');
let loadNumber = 0;
const transferring = computed(() => props.mode === 'transfer');
const editing = computed(() => props.mode !== 'view');
const transferContext = ref<PlanTransferContext>(), transferMode = ref('');
const transferMessage = ref(''), pendingSubmission = ref(false);
let submission: PlanTransferRequest | undefined;
const transferAllowed = computed(() => canEdit.value && !!plan.value?.qcPlanId && transferContext.value?.available && !pendingSubmission.value);
const transferPending = computed(() => pendingSubmission.value || !!transferContext.value?.pendingKey);
const step = computed(() => { const value = Number(route.query.step); return Number.isInteger(value) && value >= 1 && value <= 4 ? value : 1; });
const draftInitialStep = computed(() => route.query.step == null ? undefined : step.value);
const boundAccount = computed(() => accounts.value.find(a => a.accountId === plan.value?.accountId));
const account = computed(() => {
  const p = plan.value; if (!p?.awemeId) return undefined;
  const aweme = boundAccount.value?.awemes.find(w => w.awemeId === p.awemeId);
  return { key: `${p.accountId}:${p.awemeId}`, awemeName: aweme?.name || p.awemeId, awemeId: p.awemeId, avatar: aweme?.avatar };
});
const accountChips = computed(() => boundAccount.value?.accountName ? [boundAccount.value.accountName] : []);
const canEdit = computed(() => user.trialActive && boundAccount.value?.status === 'ACTIVE' && !!plan.value && (plan.value.qcPlanId ? ['DELIVERING', 'PAUSED'].includes(plan.value.status) : plan.value.source !== 'EXTERNAL' && ['DRAFT', 'REJECTED'].includes(plan.value.status)));
const draftEditor = computed(() => !transferring.value && editing.value && canEdit.value && !!creation.value && !plan.value?.qcPlanId);
const bidType = computed(() => transferring.value ? transferMode.value : plan.value?.smartBidType || (creation.value?.smartBidType ? `SMART_BID_${creation.value.smartBidType}` : ''));
const isCost = computed(() => bidType.value === 'SMART_BID_CUSTOM');
const products = computed<CreationProduct[]>(() => (plan.value?.products || []).map(p => {
  const original = creation.value?.products.find(x => x.qcProductId === p.id);
  return { ...original, productId: p.id, qcProductId: p.id, name: p.name, imageUrl: p.imageUrl || original?.imageUrl, price: p.priceInfo?.minPrice ?? original?.price ?? null, priceInfo: p.priceInfo || original?.priceInfo, userPrice: original?.userPrice ?? null, tags: original?.tags || [], unavailableReasons: original?.unavailableReasons || [] };
}));
const materialState = computed<CreationState | undefined>(() => creation.value && plan.value ? { ...creation.value, name: name.value, products: products.value, budget: plan.value.budget, roi2Goal: plan.value.roi2Goal ?? undefined } : undefined);
const estimates = computed(() => budgetEstimateHints(creation.value?.recommendations, budget.value));
const changedBudget = computed(() => plan.value != null && Number(budget.value) !== plan.value.budget);
const changedRoi = computed(() => isCost.value && plan.value != null && Number(roi.value) !== plan.value.roi2Goal);
const dirty = computed(() => !!plan.value && (name.value.trim() !== plan.value.name || changedBudget.value || changedRoi.value));
const validNumber = (value: string, min: number, max: number) => /^\d+(\.\d{1,2})?$/.test(value.trim()) && Number(value) >= min && Number(value) <= max;
const nameError = computed(() => !transferring.value && editing.value && name.value.trim() !== plan.value?.name && (!name.value.trim() || name.value.trim().length > 30) ? '计划名称须为 1–30 字' : '');
const budgetMinimum = computed(() => isCost.value ? COST_DAILY_BUDGET_MINIMUM : 50);
const budgetHint = computed(() => transferring.value ? '设置转投后的日预算' : editing.value ? `预算范围 ${budgetMinimum.value}–999,999,999.99 元，最多两位小数，保存后同步千川` : '当前计划的日预算');
const budgetError = computed(() => transferring.value || draftEditor.value ? creationBudgetError(budget.value, bidType.value) : editing.value && changedBudget.value && !validNumber(budget.value, budgetMinimum.value, 999999999.99) ? `修改后的日预算须为 ${budgetMinimum.value}–999,999,999.99 元，最多两位小数` : '');
const roiError = computed(() => transferring.value ? creationRoiError(roi.value, bidType.value) : editing.value && changedRoi.value && !validNumber(roi.value, .01, 100) ? '净成交ROI目标须为 0.01–100，最多两位小数' : '');
function resetForm() {
  const p = plan.value; if (!p) return;
  name.value = p.name; budget.value = p.budget == null ? '' : String(p.budget); roi.value = p.roi2Goal == null ? '' : String(p.roi2Goal);
  duration.value = creation.value ? creation.value.scheduleType === 'DATE_RANGE' ? 'custom' : 'long' : p.endDate ? 'custom' : 'long';
  start.value = p.startDate || creation.value?.startDate || ''; end.value = p.endDate || creation.value?.endDate || '';
  if (transferring.value && transferContext.value) {
    const t = transferContext.value; transferMode.value = t.targetMode;
    budget.value = t.budget == null ? '' : String(t.budget); roi.value = t.roi2Goal == null ? '' : String(t.roi2Goal);
  }
}
async function load() {
  const request = ++loadNumber, id = String(route.params.id || ''); loading.value = true; loadError.value = ''; mediaError.value = '';
  const results = await Promise.allSettled([getPlan(id), listAccounts(), creationGet(id), transferring.value ? getPlanTransfer(id) : Promise.resolve(undefined)]);
  if (request !== loadNumber) return;
  const [p, a, c, t] = results;
  if (p.status === 'rejected') { plan.value = undefined; loadError.value = '计划加载失败，请重试。'; }
  else {
    plan.value = p.value; accounts.value = a.status === 'fulfilled' ? a.value : [];
    creation.value = c.status === 'fulfilled' ? c.value : undefined;
    transferContext.value = t.status === 'fulfilled' ? t.value : undefined;
    if (transferring.value && t.status === 'rejected') transferMessage.value = '转投配置加载失败，请重新加载。';
    else if (transferring.value) transferMessage.value = transferContext.value?.pendingError || transferContext.value?.reason || '';
    if (c.status === 'rejected' && ![1403, 1405].includes(Number(c.reason?.code))) mediaError.value = '素材预览加载失败，请重新加载。';
    resetForm();
  }
  loading.value = false;
}
function navigate(value: number) { if (!saving.value && value >= 1 && value <= 4) { void router.replace({ path: route.path, query: { ...route.query, step: String(value) } }); window.scrollTo(0, 0); } }
function enterEdit() { if (plan.value && canEdit.value) void router.push({ path: `/campaigns/${plan.value.planId}/edit`, query: { step: String(step.value) } }); }
function cancelEdit() { if (!saving.value && plan.value) { resetForm(); void router.replace({ path: `/campaigns/${plan.value.planId}`, query: { step: String(step.value) } }); } }
function changeTransferMode(value: string) {
  if (!transferring.value || !transferAllowed.value || saving.value) return;
  transferMode.value = value;
  if (value === transferContext.value?.targetMode) {
    budget.value = String(transferContext.value.budget);
    roi.value = transferContext.value.roi2Goal == null ? '' : String(transferContext.value.roi2Goal);
  } else { budget.value = plan.value?.budget == null ? '' : String(plan.value.budget); roi.value = plan.value?.roi2Goal == null ? '' : String(plan.value.roi2Goal); }
}
async function finishTransfer(result: PlanTransferResult) {
  transferMessage.value = result.message;
  if (result.status === 'SUCCEEDED') {
    submission = undefined; pendingSubmission.value = false; ElMessage.success(result.message);
    await router.replace({ path: `/campaigns/${result.planId}`, query: { step: '2' } }); await load();
  } else {
    pendingSubmission.value = ['RUNNING', 'UNCERTAIN'].includes(result.status);
    if (result.status === 'FAILED') submission = undefined;
    transferContext.value = await getPlanTransfer(String(route.params.id));
  }
}
async function submitTransfer() {
  const p = plan.value;
  if (!p?.qcPlanId || !transferAllowed.value || saving.value || budgetError.value || roiError.value || bidType.value === p.smartBidType) return;
  submission ||= { requestKey: crypto.randomUUID(), expectedQcPlanId: p.qcPlanId, smartBidType: bidType.value, budget: Number(budget.value), ...(isCost.value ? { roi2Goal: Number(roi.value) } : {}) };
  saving.value = true;
  try { await finishTransfer(await transferPlan(p.planId, submission)); }
  catch (error) {
    // A browser timeout is not evidence that CREATE failed. Do not allow a fresh request.
    const code = Number((error as { code?: number })?.code);
    pendingSubmission.value = ![1201, 1402, 1403, 1404, 1405].includes(code);
    transferMessage.value = pendingSubmission.value ? '提交结果待确认，请点击核查转投结果，勿重复提交。' : (error as Error).message;
    if (!pendingSubmission.value) submission = undefined;
    try { transferContext.value = await getPlanTransfer(p.planId); } catch { /* Keep the pending state. */ }
  } finally { saving.value = false; }
}
async function reconcileTransfer() {
  if (saving.value || !plan.value || !user.trialActive) return;
  saving.value = true;
  try {
    // Replaying the exact request only when no server attempt exists also covers pre-validation failures.
    const context = await getPlanTransfer(plan.value.planId);
    if (!context.pendingKey && submission) await finishTransfer(await transferPlan(plan.value.planId, submission));
    else { await finishTransfer(await reconcilePlanTransfer(plan.value.planId)); if (!context.pendingKey) { pendingSubmission.value = false; submission = undefined; } }
  } catch { transferMessage.value = '转投结果尚未核查成功，请稍后重试。'; }
  finally { saving.value = false; }
}
async function save() {
  const p = plan.value;
  if (!editing.value || !canEdit.value || !p || saving.value || !dirty.value || nameError.value || budgetError.value || roiError.value) return;
  const body: { name?: string; budget?: number; roi2Goal?: number } = {};
  if (name.value.trim() !== p.name) body.name = name.value.trim();
  if (changedBudget.value) body.budget = Number(budget.value);
  if (changedRoi.value) body.roi2Goal = Number(roi.value);
  saving.value = true;
  try {
    plan.value = p.qcPlanId ? await updatePlanSettings(p.planId, body) : await updateDraft(p.planId, { accountId: p.accountId!, awemeId: p.awemeId!, name: name.value.trim(), smartBidType: bidType.value, dailyBudget: Number(budget.value), ...(isCost.value ? { roi2Goal: Number(roi.value) } : {}), productIds: products.value.map(x => x.qcProductId) });
    resetForm(); ElMessage.success('计划修改已保存');
    await router.replace({ path: `/campaigns/${p.planId}`, query: { step: String(step.value) } });
  } catch { /* Keep entered values; the API interceptor shows the platform error. */ }
  finally { saving.value = false; }
}
watch(() => route.params.id, load, { immediate: true });
watch(() => props.mode, () => { submission = undefined; pendingSubmission.value = false; void load(); });
onUnmounted(() => { loadNumber++; });
</script>

<style scoped>
.plan-workspace { padding-bottom: 92px; }
.workspace-loading { padding: 80px 24px; text-align: center; color: #7c7f99; }.workspace-loading button,.workspace-loading a { margin: 16px 10px; }
.workspace-header { padding-top: 24px; padding-bottom: 24px; }.workspace-heading,.workspace-title { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }.workspace-back { display: inline-flex; align-items: center; gap: 6px; color: #575cf6; font-size: 13px; }.workspace-back svg,.workspace-title button svg { width: 16px; height: 16px; }.workspace-mode { color: #7c7f99; font-size: 13px; }
.workspace-title { margin-top: 16px; }.workspace-title h1,.workspace-title input { flex: 1; min-width: 0; font-size: 22px; font-weight: 700; color: #1a1b2e; overflow-wrap: anywhere; }.workspace-title input { padding: 10px 14px; border: 1px solid #dedff0; border-radius: 10px; background: #fff; font-size: 18px; }.workspace-title input:focus { outline: 2px solid #575cf640; border-color: #575cf6; }.workspace-title button { display: inline-flex; gap: 6px; align-items: center; }
.workspace-meta,.workspace-notice { margin-top: 12px; font-size: 13px; line-height: 1.7; color: #7c7f99; overflow-wrap: anywhere; }.workspace-notice { padding: 10px 14px; background: #eeeefe; border-radius: 10px; color: #555875; }.workspace-notice button { margin-left: 16px; }.workspace-error { color: #e65361; margin-top: 12px; }
.workspace-empty { padding: 60px 0; color: #8588a5; text-align: center; }.workspace-result { display: flex; gap: 32px; flex-wrap: wrap; margin: 28px 0; }.workspace-result dt { font-size: 13px; color: #8588a5; }.workspace-result dd { margin-top: 8px; color: #1a1b2e; }.workspace-analysis { margin-top: 28px; }
.plan-workspace > .actionbar .actionbar__inner { height: auto; min-height: 75px; flex-wrap: wrap; padding-top: 12px; padding-bottom: 12px; gap: 12px; }
@media(max-width:639px) { .workspace-title h1 { flex-basis: 100%; font-size: 18px; }.workspace-title input { flex-basis: 100%; }.workspace-heading { gap: 10px; }.workspace-meta { font-size: 12px; } }
</style>
