<template>
  <div class="wizard">
    <CreateProductsStep v-if="step === 1" :key="accountKey" :initial-query="query" :products="products" :selected="selected" :loaded="loaded" :load-error="loadError" :saving="busy" :total="total" :analysis-status="sameSelection ? state?.analysisStatus || 'NONE' : 'NONE'" :analysis="state?.analysis" :analysis-error="state?.analysisError" :account="account" :accounts="accountOptions" :account-chips="accountChips" @select="toggle" @remove="remove" @account="chooseAccount" @query="changeQuery" @retry="loadProducts()" @refresh-prices="loadProducts(true)" @analyze="analyze" @next="nextGoal" @cancel="cancel" />
    <CreateGoalStep v-else-if="step === 2" v-model:budget="budget" v-model:roi="roi" v-model:duration="duration" v-model:start="start" v-model:end="end" :mode="mode === 'CUSTOM' ? 'SMART_BID_CUSTOM' : 'SMART_BID_CONSERVATIVE'" :saving="busy" :budget-error="budgetError" budget-warn="" :roi-error="roiError" :date-error="dateError" :budget-hint="budgetHint" :roi-hint="roiHint" :estimate-roi-hint="estimateRoiHint" :estimate-orders-hint="estimateOrdersHint" :budget-options="budgetOptions" :order-lo="0" :order-hi="0" selection-warning="" :demo-mode="false" :account="account" :accounts="accountOptions" :account-chips="accountChips" @mode="changeMode" @recommendations="refreshRecommendations" @account="chooseAccount" @prev="goToStep(1)" @next="saveGoal" @cancel="cancel" />
    <LiveMaterialsStep v-else-if="step === 3 && state" v-model:plan-name="name" :state="state" :busy="busy" :account="account" :account-chips="accountChips" @prev="goToStep(2)" @generate="generate" @generate-addition="generateAddition" @add="add" @action="openAction" @titles="editTitles" @visibility="setVisibility" @recheck="recheck" @context="analyzeContext" @confirm="confirm" @publish="publish" @cancel="cancel" />
    <PlanResult v-else-if="state?.publishStatus === 'CREATED'" :plan-id="state.planId" />
    <div v-else-if="step === 4 && state" class="submit-result campaign-result ad-step3 ad-step4"><CreationFrame :step="4" :account="account" :account-chips="accountChips" /><section><h2>{{ state.publishStatus === 'UNCERTAIN' ? '平台创建结果待确认' : state.publishStatus === 'FAILED' ? '发布失败' : '正在提交投放计划' }}</h2><p>{{ state.publishError || '正在将已确认的素材包提交至千川，请稍候…' }}</p><p v-if="state.publishStatus === 'UNCERTAIN'">请核对千川平台计划后处理，当前请求不会自动重复创建。</p><div v-if="state.publishStatus === 'UNCERTAIN'" class="reconcile"><input v-model="reconcileId" inputmode="numeric" placeholder="填写已核对的千川计划ID" aria-label="千川计划ID" /><button :disabled="busy || !reconcileId.trim()" @click="reconcile">核验并关联平台计划</button></div><button v-if="state.publishStatus === 'FAILED'" class="btn-brand" @click="step = 3">返回修改</button><button class="btn-cancel" @click="router.push('/campaigns')">返回投放列表</button></section></div>
    <p v-if="initialError" class="creation-error" role="alert">{{ initialError }}<button @click="initialize">重新加载</button></p>
    <CreationActionDialog v-if="action" :action="action.kind" :busy="busy" @close="action = null" @save="saveAndLeave" @discard="discardAndLeave" @confirm="feedback" />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listAccounts, type AccountVo } from '@/features/identity/oauthApi';
import * as api from '@/features/delivery/creationApi';
import * as recommendations from '@/features/delivery/creationRecommendations';
import type { MaterialFeedback } from '@/features/creative/creativeApi';
import CreateProductsStep from './create/CreateProductsStep.vue';
import CreateGoalStep from './create/CreateGoalStep.vue';
import LiveMaterialsStep from './create/CreateMaterialsStep.vue';
import CreationActionDialog from './create/CreationActionDialog.vue';
import CreationFrame from './reference/CreationFrame.vue';
import PlanResult from './reference/PlanResult.vue';
import './create/create-theme.css';
const route = useRoute(), router = useRouter();
const props = defineProps<{ initialPlanId?: string; initialStep?: number }>();
const state = ref<api.CreationState>(), step = ref(1), busy = ref(false), loaded = ref(false), loadError = ref(false), initialError = ref('');
const accounts = ref<AccountVo[]>([]), accountKey = ref(''), selected = ref<api.CreationProduct[]>([]), products = ref<api.CreationProduct[]>([]), total = ref(0);
const query = ref<api.ProductQuery>({ page: 1, pageSize: 30, keyword: '', tab: 'ALL', unpromoted: true });
const accountOptions = computed(() => accounts.value.filter(a => a.platform === 'QIANCHUAN' && a.status === 'ACTIVE' && a.credentialSource !== 'MOCK').flatMap(a => a.awemes.map(w => ({ key: `${a.accountId}:${w.awemeId}`, accountId: a.accountId, awemeId: w.awemeId, awemeName: w.name, avatar: w.avatar, type: w.type }))));
const account = computed(() => accountOptions.value.find(a => a.key === accountKey.value));
const accountChips = computed(() => account.value ? [({ OFFICIAL:'官方账号', SELF:'自运营', COOP:'合作达人', COOP_AGENCY:'合作机构', ALLIANCE:'联盟', AUTHORIZED:'授权账号', OTHER:'其他账号' } as Record<string,string>)[account.value.type] || account.value.type] : []);
const sameSelection = computed(() => !!state.value && JSON.stringify(state.value.products.map(p => p.qcProductId)) === JSON.stringify(selected.value.map(p => p.qcProductId)));
const working = computed(() => !!state.value && (['QUEUED','RUNNING'].includes(state.value.analysisStatus) || ['QUEUED','RUNNING','REVISING','SUBMITTING','UNCERTAIN','IMPORTING'].includes(state.value.generationStatus)));
const reconcileId = ref('');
async function reconcile() { await run(async () => { state.value = await api.creationReconcile(state.value!, reconcileId.value.trim()); }); }
const budget = ref(''), roi = ref(''), mode = ref<'CUSTOM'|'CONSERVATIVE'>('CUSTOM'), name = ref(''), duration = ref<'long'|'custom'>('long');
const today = () => new Date(Date.now() + 8*3600000).toISOString().slice(0,10);
const start = ref(today()), end = ref(today());
const validNumber = (v: string) => /^\d+(\.\d{1,2})?$/.test(v);
const budgetError = computed(() => !validNumber(budget.value) || Number(budget.value) <= 0 || Number(budget.value) > 100000 ? '日预算须大于 0、不超过 100000 元，最多两位小数' : '');
const roiError = computed(() => mode.value === 'CUSTOM' && (!validNumber(roi.value) || Number(roi.value) < .1 || Number(roi.value) > 50) ? '净成交ROI目标须为 0.1–50，最多两位小数' : '');
const dateError = computed(() => duration.value === 'custom' && (!start.value || !end.value || start.value < today() || end.value < start.value) ? '开始日期不得早于今天，结束日期不得早于开始日期' : '');
const budgetOptions = computed(() => recommendations.budgetOptions(state.value?.recommendations));
const estimate = computed(() => recommendations.budgetEstimateHints(state.value?.recommendations, budget.value));
const estimateRoiHint = computed(() => busy.value ? '正在获取平台预估…' : estimate.value.roi);
const estimateOrdersHint = computed(() => busy.value ? '正在获取平台预估…' : estimate.value.orders);
const budgetHint = computed(() => busy.value ? '正在获取千川推荐预算…' : state.value?.recommendations?.budgetError || (recommendedBudget() ? `千川推荐预算 ¥${recommendedBudget()}，可调整` : '平台未返回推荐预算，可重新获取或手动填写'));
const roiHint = computed(() => busy.value ? '正在获取千川推荐ROI…' : state.value?.recommendations?.roiError || (recommendedRoi() ? `千川推荐净成交ROI ${recommendedRoi()}，可参考实际经营目标调整` : '平台未返回推荐ROI，可重新获取或根据经营目标填写'));
function recommendedBudget() { return recommendations.recommendedBudget(state.value?.recommendations); }
function recommendedRoi() { return recommendations.recommendedRoi(state.value?.recommendations); }
const action = ref<{ kind: 'cancel'|'regenerate'|'delete'; id?: string } | null>(null);
let disposed = false, pollTimer: ReturnType<typeof setTimeout> | undefined, requestNo = 0;
const errorText = (e: unknown) => (e as {message?:string})?.message || '请求失败，请稍后重试';
async function run(task: () => Promise<void>) { if (busy.value) return; busy.value = true; try { await task(); } catch (e) { if (!(e as {code?:number})?.code) ElMessage.error(errorText(e)); if ((e as {code?:number})?.code === 1404 && state.value) state.value = await api.creationGet(state.value.planId); } finally { busy.value = false; } }
async function loadProducts(refreshPrices = false) {
  if (!account.value) { products.value = []; loaded.value = true; return; }
  const current = ++requestNo; loaded.value = false; loadError.value = false;
  try { const result = await api.creationProducts(account.value.accountId, account.value.awemeId, query.value, refreshPrices); if (current === requestNo) { products.value = result.items; selected.value = selected.value.map(p => { const next=result.items.find(next => next.qcProductId === p.qcProductId); return next ? { ...next, userPrice: next.userPrice ?? p.userPrice } : p; }); total.value = result.total; loaded.value = true; } }
  catch { if (current === requestNo) { loadError.value = true; loaded.value = true; } }
}
function changeQuery(q: api.ProductQuery) { query.value = q; void loadProducts(); }
function toggle(p: api.CreationProduct) { if (busy.value || working.value) return; if (p.unavailableReasons?.length) { ElMessage.warning(p.unavailableReasons.join('；')); return; } if (selected.value.some(x => x.productId === p.productId)) remove(p.productId); else if (selected.value.length < 10) selected.value.push(p); else ElMessage.warning('最多选择 10 个商品'); }
function remove(id: string) { selected.value = selected.value.filter(p => p.productId !== id); }
async function chooseAccount(key: string) { if (working.value || busy.value || key === accountKey.value) return; if (state.value) ElMessage.info('原账户草稿已保留，切换账户将新建草稿'); state.value = undefined; selected.value = []; budget.value = ''; roi.value = ''; name.value = ''; step.value = 1; accountKey.value = key; await router.replace('/campaigns/create'); await loadProducts(); }
async function ensureSelection() {
  if (!account.value || !selected.value.length) throw new Error('请先选择授权账号和商品');
  state.value = await api.creationSelect({ planId: state.value?.planId, revision: state.value?.revision, accountId: account.value.accountId, awemeId: account.value.awemeId, productIds: selected.value.map(p => p.qcProductId) });
  name.value ||= state.value.name; selected.value = state.value.products;
  if (!props.initialPlanId) await router.replace({ path: '/campaigns/create', query: { draftId: state.value.planId } });
}
async function analyze(refresh = false) { await run(async () => { await ensureSelection(); state.value = await api.creationAnalyze(state.value!, refresh); schedulePoll(); }); }
async function persistStep(target: number) {
  if (state.value && state.value.step !== target) state.value = await api.creationStep(state.value, target);
}
async function goToStep(target: number) {
  await run(async () => { await persistStep(target); step.value = target; window.scrollTo(0,0); });
}
async function nextGoal() {
  if (!sameSelection.value || state.value?.analysisStatus !== 'SUCCEEDED') return;
  await goToStep(2);
  if (step.value === 2) await refreshRecommendations();
}
async function refreshRecommendations() {
  if (!state.value) return;
  await run(async () => {
    state.value = await api.creationRecommendations(state.value!.planId);
    if (!budget.value && recommendedBudget()) budget.value = String(recommendedBudget());
    if (!roi.value && recommendedRoi()) roi.value = String(recommendedRoi());
  });
}
async function changeMode(value: 'SMART_BID_CUSTOM' | 'SMART_BID_CONSERVATIVE') {
  if (busy.value) return;
  mode.value = value === 'SMART_BID_CUSTOM' ? 'CUSTOM' : 'CONSERVATIVE';
  const r = state.value?.recommendations;
  if (!r || (mode.value === 'CONSERVATIVE' ? !r.budget || r.budgetError : !r.roi || r.roiError)) await refreshRecommendations();
}
async function persistGoal() {
  if (!state.value || budgetError.value || roiError.value || dateError.value || !name.value.trim()) throw new Error('请检查计划名称、预算、ROI 和日期');
  const goal = { name: name.value.trim(), smartBidType: mode.value, budget: Number(budget.value), roi2Goal: mode.value === 'CUSTOM' ? Number(roi.value) : null, scheduleType: duration.value === 'custom' ? 'DATE_RANGE' : 'FROM_NOW', startDate: duration.value === 'custom' ? start.value : null, endDate: duration.value === 'custom' ? end.value : null };
  const saved = state.value;
  if (saved.name === goal.name && saved.smartBidType === goal.smartBidType && saved.budget === goal.budget && (saved.roi2Goal ?? null) === goal.roi2Goal && saved.scheduleType === goal.scheduleType && (saved.startDate ?? null) === goal.startDate && (saved.endDate ?? null) === goal.endDate) return;
  state.value = await api.creationGoal(saved, goal);
}
async function saveGoal() { await run(async () => { await persistGoal(); await persistStep(3); step.value = 3; window.scrollTo(0,0); }); }
async function generate(ids: string[]) { await run(async () => { state.value = await api.creationGenerate(state.value!, ids); schedulePoll(); }); }
async function generateAddition(body: api.CreationAdditionBody) { await run(async () => { state.value = await api.creationAddition(state.value!, body); schedulePoll(); }); }
async function editTitles(id: string, titles: string[]) { await run(async () => { state.value = await api.creationTitles(state.value!, id, titles); schedulePoll(); }); }
async function setVisibility(hideInAweme: boolean) { await run(async () => { state.value = await api.creationVisibility(state.value!, hideInAweme); }); }
async function analyzeContext(id: string) { await run(async () => { state.value = await api.creationMaterialContext(state.value!, id); schedulePoll(); }); }
async function recheck(id: string) { await run(async () => { state.value = await api.creationRecheck(state.value!, id); schedulePoll(); }); }
async function add(body: Parameters<typeof api.creationAdd>[1]) { await run(async () => { state.value = await api.creationAdd(state.value!, body); schedulePoll(); }); }
function openAction(kind: 'regenerate'|'delete', id: string) { if (!busy.value) action.value = { kind, id }; }
async function feedback(body: MaterialFeedback) { const a = action.value; if (!a?.id) return; await run(async () => { state.value = await api.creationFeedback(state.value!, a.id!, a.kind, body); action.value = null; schedulePoll(); }); }
async function confirm() { await run(async () => { if (name.value.trim() !== state.value!.name) await persistGoal(); state.value = await api.creationConfirm(state.value!); ElMessage.success('当前素材包已确认'); }); }
async function publish() { await run(async () => { const s = state.value!; const key = sessionStorage.getItem(`creation:${s.planId}:${s.revision}`) || crypto.randomUUID(); sessionStorage.setItem(`creation:${s.planId}:${s.revision}`, key); state.value = await api.creationPublish(s, key); step.value = 4; schedulePoll(); }); }
function cancel() { if (!busy.value) action.value = { kind: 'cancel' }; }
async function saveAndLeave() {
  await run(async () => {
    if (!state.value || !sameSelection.value) await ensureSelection();
    if (step.value >= 2 && !working.value && !budgetError.value && !roiError.value && !dateError.value) await persistGoal();
    await persistStep(step.value);
    ElMessage.success('草稿已保存，可在投放列表继续编辑');
    await router.push('/campaigns');
  });
}
async function discardAndLeave() { await run(async () => { if (state.value) await api.creationDiscard(state.value.planId); await router.push('/campaigns'); }); }
function schedulePoll() { clearTimeout(pollTimer); if (!disposed && state.value) pollTimer = setTimeout(poll, working.value || state.value.materials.some(m => m.status === 'PROCESSING') || ['QUEUED','SENDING'].includes(state.value.publishStatus) ? 2500 : 15000); }
async function poll() {
  if (disposed || !state.value) return;
  try { if (!busy.value) { const updated = await api.creationGet(state.value.planId); if (!disposed && !busy.value) { state.value = updated; if (['QUEUED','SENDING','UNCERTAIN','CREATED'].includes(updated.publishStatus)) step.value = 4; } } } catch { /* Quiet reads resume when the service becomes available. */ }
  if (!disposed && state.value && state.value.publishStatus !== 'CREATED') schedulePoll();
}
async function initialize() {
  initialError.value = '';
  try {
    accounts.value = await listAccounts();
    const id = props.initialPlanId || (typeof route.query.draftId === 'string' ? route.query.draftId : '');
    if (id) { state.value = await api.creationGet(id); const s = state.value; accountKey.value = `${s.accountId}:${s.awemeId}`; selected.value = s.products; step.value = Math.min(s.step, Math.max(1, Math.min(4, props.initialStep ?? s.step))); name.value = s.name; mode.value = s.smartBidType; budget.value = s.budget == null ? '' : String(s.budget); roi.value = s.roi2Goal == null ? '' : String(s.roi2Goal); duration.value = s.scheduleType === 'DATE_RANGE' ? 'custom' : 'long'; start.value = s.startDate || today(); end.value = s.endDate || today(); schedulePoll(); }
    else accountKey.value = accountOptions.value[0]?.key || '';
    if (!account.value) initialError.value = '尚无可用的千川授权抖音号，请在账户管理中完成授权。';
    await loadProducts();
  } catch (e) { initialError.value = errorText(e); }
}
onMounted(initialize); onUnmounted(() => { disposed = true; clearTimeout(pollTimer); requestNo++; });
</script>
<style scoped>
.creation-error{padding:24px;color:#db515b;text-align:center}.creation-error button{margin-left:15px}.submit-result{background:#f7f7ff;min-height:85vh}.submit-result section{max-width:900px;margin:30px auto;padding:60px;background:white;border-radius:24px;text-align:center}.submit-result h2{font-size:26px;font-weight:700}.submit-result p{margin:22px;color:#8588a5}.submit-result button{margin:10px;padding:12px 24px;background:#575cf6;color:white;border-radius:10px}
</style>
