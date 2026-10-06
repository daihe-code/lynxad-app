<template>
  <div class="wizard">
    <CreateProductsStep v-if="wizard.step === 1" :key="awemeKey" :products="products" :selected="wizard.products" :loaded="productsLoaded" :load-error="productsLoadError" :saving="saving" :account="currentAweme" :accounts="awemeOptions" :account-chips="accountChips" @select="toggleProduct" @remove="removeProduct" @account="chooseAweme" @retry="loadProducts" @next="toStep2" @cancel="cancelCreation" />

    <CreateGoalStep v-else-if="wizard.step === 2" v-model:budget="budgetText" v-model:roi="roiText" v-model:duration="durationMode" v-model:start="customStart" v-model:end="customEnd" :mode="wizard.smartBidType" :saving="saving" :budget-error="budgetErrorText" :budget-warn="budgetWarnText" :roi-error="roiErrorText" :order-lo="orderLo" :order-hi="orderHi" :selection-warning="selectWarn" :demo-mode="user.demoMode" :account="currentAweme" :accounts="awemeOptions" :account-chips="accountChips" @mode="pickMode" @account="chooseAweme" @prev="backToProducts" @next="toStep3" @cancel="cancelCreation" />
    <CreateMaterialsStep v-else-if="wizard.step === 3" v-model:plan-name="wizard.planName" :task="task" :saving="saving || !!materialTaskId" :confirmed="confirmed" :replacement-version="replacementVersion" :selected="wizard.products" :ai-product-ids="wizard.aiSelectionConfirmed ? wizard.aiProductIds : []" :generation-picker-open="generationPickerOpen" :generation-selection="wizard.aiProductIds" :account="currentAweme" :account-chips="accountChips" @prev="backToGoal" @retry="retryGenerate" @generate="generateSelected" @generation-selection="wizard.aiProductIds = $event" @close-generation-picker="generationPickerOpen = false" @confirm="onConfirm" @publish="onPublish" @replace="onReplace" @regenerate="(productId, type) => openMaterialAction('regenerate', productId, type)" @delete-material="(productId, type) => openMaterialAction('delete', productId, type)" @cancel="cancelCreation" />
    <PlanResult v-else :plan-id="wizard.planId" />
    <CreationActionDialog v-if="actionDialog" :key="actionDialog.action" :action="actionDialog.action" :busy="saving" @close="actionDialog = null" @save="saveCancelledPlan" @discard="discardCancelledPlan" @confirm="confirmMaterialAction" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { listAccounts, type AwemeVo } from "@/features/identity/oauthApi";
import { listProducts, selectProducts, type ProductVo } from "@/features/catalog/catalogApi";
import { confirmPackage, deleteMaterial, generateCreatives, getCreativeTask, regenerateMaterial, replaceItem, type MaterialFeedback, type MaterialType, type TaskVo } from "@/features/creative/creativeApi";
import { createDraft, discardDraft, publishPlan, updateDraft } from "@/features/delivery/deliveryApi";
import { useWizardStore } from "@/stores/wizard";

import "./create/create-theme.css";
import CreateProductsStep from "./create/CreateProductsStep.vue";
import CreateGoalStep from "./create/CreateGoalStep.vue";
import CreateMaterialsStep from "./create/CreateMaterialsStep.vue";
import CreationActionDialog from "./create/CreationActionDialog.vue";
import PlanResult from "./reference/PlanResult.vue";
import { useUserStore } from "@/stores/user";

const SCENE_CHIPS = ["自运营", "合作达人", "合作机构", "联盟"];

interface AwemeOpt {
  key: string;
  accountId: string;
  awemeId: string;
  awemeName: string;
  isPrimary: boolean;
  type: string;
  sceneHint?: string;
}

const wizard = useWizardStore();
const user = useUserStore();
const router = useRouter();
function cancelCreation() {
  if (!saving.value && !materialTaskId.value) actionDialog.value = { action: 'cancel' };
}
type CreationAction = { action: 'cancel' } | { action: 'regenerate' | 'delete'; productId: string; type: MaterialType };
const actionDialog = ref<CreationAction | null>(null);

async function saveCancelledPlan() {
  if (saving.value) return;
  if (!wizard.accountId || !wizard.awemeId) { ElMessage.warning('请先选择已授权的抖音账号'); return; }
  if (budgetErrorText.value || roiErrorText.value) {
    ElMessage.warning(budgetErrorText.value || roiErrorText.value);
    return;
  }
  if (wizard.planName.length > 30) { ElMessage.warning('计划名称须为 1–30 字'); return; }
  saving.value = true;
  try {
    wizard.dailyBudget = Number(budgetText.value);
    if (wizard.smartBidType === 'SMART_BID_CUSTOM') wizard.roi2Goal = Number(roiText.value);
    if (!wizard.planName.trim()) wizard.planName = defaultName();
    if (wizard.planId) await updateDraft(wizard.planId, draftBody());
    else wizard.planId = (await createDraft(draftBody())).plan.planId;
    ElMessage.success('投放计划已保存为草稿');
    await leaveCreation();
  } catch { /* Keep the dialog and inputs available if saving fails. */ }
  finally { saving.value = false; }
}

async function discardCancelledPlan() {
  if (saving.value) return;
  saving.value = true;
  try {
    if (wizard.planId) await discardDraft(wizard.planId);
    await leaveCreation();
  } catch { /* Do not leave the page if discarding the draft fails. */ }
  finally { saving.value = false; }
}

async function leaveCreation() {
  const failure = await router.push('/campaigns');
  if (!failure) wizard.reset();
}

function openMaterialAction(action: 'regenerate' | 'delete', productId: string, type: MaterialType) {
  if (!saving.value && !materialTaskId.value && wizard.packageId) actionDialog.value = { action, productId, type };
}

async function confirmMaterialAction(feedback: MaterialFeedback) {
  const action = actionDialog.value;
  if (!action || action.action === 'cancel') return;
  if (action.action === 'regenerate') await onRegenerate(action.productId, action.type, feedback);
  else await onDeleteMaterial(action.productId, action.type, feedback);
}
const accounts = ref<Awaited<ReturnType<typeof listAccounts>>>([]);
const products = ref<ProductVo[]>([]);
const productsLoaded = ref(false);
const productsLoadError = ref(false);
let productsRequest = 0;
const awemeKey = ref("");
const saving = ref(false);
const task = ref<TaskVo | null>(null);
const confirmed = ref(false);
const generationPickerOpen = ref(false);
const replacementVersion = ref(0);
let polling = false;
let progressUnavailable = false;
let disposed = false;
let materialPollTimer: number | undefined;
let materialPolling = false;
const materialTaskId = ref('');
const accountOpen = ref(false);
const selectWarn = ref("");
const budgetText = ref(String(wizard.dailyBudget));
const roiText = ref(String(wizard.roi2Goal ?? 5.12));
const durationMode = ref<"long" | "custom">("long");
const customStart = ref(shiftDate(0));
const customEnd = ref(shiftDate(30));
let pollTimer: number | undefined;
let modeMessage: ReturnType<typeof ElMessage> | undefined;

const awemeOptions = computed<AwemeOpt[]>(() =>
  accounts.value.filter(a => a.status === "ACTIVE").flatMap((a) =>
    (a.awemes || []).map((aw: AwemeVo) => ({
      key: `${a.accountId}::${aw.awemeId}`,
      accountId: a.accountId,
      awemeId: aw.awemeId,
      awemeName: aw.name,
      isPrimary: aw.isPrimary,
      type: aw.type,
      sceneHint: aw.sceneHint,
    })),
  ),
);

const currentAweme = computed(() => awemeOptions.value.find((o) => o.key === awemeKey.value));
// Catalog selection, ad drafts and creative generation all accept Qianchuan IDs.
const selectedQcProductIds = computed(() => wizard.products.map((p) => p.qcProductId));
watch(selectedQcProductIds, (ids) => {
  wizard.aiProductIds = [...ids];
  wizard.aiSelectionConfirmed = false;
});
const accountChips = computed(() => {
  const aw = currentAweme.value;
  if (!aw) return [];
  const chips: string[] = [];
  if (aw.isPrimary || aw.type === "OFFICIAL") chips.push("官方账号");
  if (aw.sceneHint && SCENE_CHIPS.includes(aw.sceneHint)) chips.push(aw.sceneHint);
  return chips;
});
const budgetErrorText = computed(() => budgetError(budgetText.value));
const budgetWarnText = computed(() => budgetWarn(budgetText.value));
const roiErrorText = computed(() => (wizard.smartBidType === "SMART_BID_CUSTOM" ? roiError(roiText.value) : ""));
const estimateBudget = computed(() => {
  const n = Number(budgetText.value);
  return Number.isFinite(n) && n > 0 ? n : 200;
});
const orderLo = computed(() => Math.max(1, Math.round(estimateBudget.value * 0.015)));
const orderHi = computed(() => Math.max(orderLo.value + 1, Math.round(estimateBudget.value * 0.02)));

watch(
  () => wizard.step,
  async (step, prev) => {
    if (prev === 3 && step !== 3) stopPoll();
    if (step > 2) accountOpen.value = false;
    await nextTick();
    if (!disposed) window.scrollTo({ top: 0, behavior: "instant" });
  },
);

function shiftDate(days: number) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function budgetError(raw: string) {
  const text = raw.trim();
  if (!text) return "请输入日预算金额";
  const n = Number(text);
  if (!Number.isFinite(n) || n <= 0) return "日预算需为大于 0 的数字";
  if (n > 100000) return "日预算超出单计划上限（¥100,000/天）";
  return "";
}

function budgetWarn(raw: string) {
  const n = Number(raw.trim());
  if (Number.isFinite(n) && n > 0 && n < 100) return "低于系统建议值 ¥100/天，冷启动期可能起量困难";
  return "";
}

function roiError(raw: string) {
  const text = raw.trim();
  if (!text) return "请输入净成交ROI目标";
  const n = Number(text);
  if (!Number.isFinite(n) || n <= 0) return "净成交ROI目标需为大于 0 的数字";
  if (n < 0.1 || n > 50) return "净成交ROI目标建议在 0.1 ~ 50 之间";
  return "";
}


function defaultName() {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_商品全域投放_${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`.slice(0, 30);
}

function chooseAweme(key: string) {
  if (saving.value) return;
  accountOpen.value = false;
  awemeKey.value = key;
  onAwemeChange(key);
}

function onAwemeChange(key: string) {
  if (wizard.step > 2) return;
  const opt = awemeOptions.value.find((o) => o.key === key);
  if (!opt) return;
  const changed = wizard.awemeId !== opt.awemeId || wizard.accountId !== opt.accountId;
  wizard.accountId = opt.accountId;
  wizard.awemeId = opt.awemeId;
  wizard.awemeName = opt.awemeName;
  if (!changed) return;
  stopPoll();
  wizard.productIds = [];
  wizard.products = [];
  wizard.planId = "";
  wizard.taskId = "";
  wizard.packageId = "";
  wizard.step = 1;
  task.value = null;
  confirmed.value = false;
  selectWarn.value = "";
  loadProducts();
}

async function loadProducts() {
  const request = ++productsRequest;
  const awemeId = wizard.awemeId;
  products.value = [];
  productsLoadError.value = false;
  productsLoaded.value = false;
  if (!awemeId) { productsLoaded.value = true; return; }
  try {
    const result = await listProducts(awemeId);
    if (request === productsRequest) products.value = result;
  } catch {
    if (request === productsRequest) productsLoadError.value = true;
  } finally {
    if (request === productsRequest) productsLoaded.value = true;
  }
}

function removeProduct(productId: string) {
  if (saving.value) return;
  wizard.productIds = wizard.productIds.filter((id) => id !== productId);
  wizard.products = wizard.products.filter((p) => p.productId !== productId);
}

function toggleProduct(p: ProductVo) {
  const i = wizard.productIds.indexOf(p.productId);
  if (i >= 0) {
    removeProduct(p.productId);
    return;
  }
  if (wizard.productIds.length >= 10) {
    ElMessage.warning("最多选择 10 个商品");
    return;
  }
  wizard.productIds.push(p.productId);
  wizard.products.push(p);
}


async function toStep2() {
  if (wizard.productIds.length === 0 || saving.value) return;
  saving.value = true;
  try {
    const res = await selectProducts(wizard.awemeId, [...selectedQcProductIds.value]);
    selectWarn.value = res.warn || "";
    if (res.warn) ElMessage.warning(res.warn);
    wizard.step = 2;
  } catch {
    /* 接口已提示，留在本步 */
  } finally {
    saving.value = false;
  }
}

function backToProducts() {
  wizard.step = 1;
}

function pickMode(mode: "SMART_BID_CUSTOM" | "SMART_BID_CONSERVATIVE") {
  if (wizard.smartBidType === mode) return;
  wizard.smartBidType = mode;
  if (mode === "SMART_BID_CUSTOM") {
    wizard.dailyBudget = 600;
    wizard.roi2Goal = 5.12;
    budgetText.value = "600";
    roiText.value = "5.12";
    showModeMessage("已切换为控成本投放，日预算与目标已回填系统推荐值");
  } else {
    wizard.dailyBudget = 200;
    budgetText.value = "200";
    showModeMessage("已切换为放量投放，日预算与目标已回填系统推荐值");
  }
}

function showModeMessage(message: string) {
  modeMessage?.close();
  modeMessage = ElMessage.success({ message, duration: 2400 });
}

function draftBody() {
  return {
    accountId: wizard.accountId,
    awemeId: wizard.awemeId,
    name: wizard.planName || defaultName(),
    smartBidType: wizard.smartBidType,
    dailyBudget: wizard.dailyBudget,
    roi2Goal: wizard.smartBidType === "SMART_BID_CUSTOM" ? wizard.roi2Goal : undefined,
    productIds: [...selectedQcProductIds.value],
    packageId: wizard.packageId || undefined,
  };
}

async function toStep3() {
  if (saving.value || durationMode.value === "custom") return;
  if (budgetErrorText.value) return;
  if (wizard.smartBidType === "SMART_BID_CUSTOM" && roiErrorText.value) return;
  wizard.dailyBudget = Number(budgetText.value);
  if (wizard.smartBidType === "SMART_BID_CUSTOM") wizard.roi2Goal = Number(roiText.value);
  saving.value = true;
  try {
    if (!wizard.planName) wizard.planName = defaultName();
    if (wizard.planId) await updateDraft(wizard.planId, draftBody());
    else wizard.planId = (await createDraft(draftBody())).plan.planId;
    wizard.step = 3;
    stopPoll();
    task.value = null;
    wizard.taskId = "";
    wizard.packageId = "";
    confirmed.value = false;
    wizard.aiSelectionConfirmed = false;
    generationPickerOpen.value = true;
  } catch {
    /* 接口已提示，留在本步。不回写草稿里的预算建议 */
  } finally {
    saving.value = false;
  }
}

function backToGoal() {
  stopPoll();
  generationPickerOpen.value = false;
  wizard.step = 2;
}

async function generateSelected(ids: string[]) {
  if (saving.value) return;
  const valid = new Set(selectedQcProductIds.value);
  const selected = [...new Set(ids)].filter(id => valid.has(id));
  if (!selected.length) return;
  wizard.aiProductIds = selected;
  saving.value = true;
  try {
    await startGenerate();
    generationPickerOpen.value = false;
  } catch { /* Keep selections and the confirmation dialog available for retry. */ }
  finally { saving.value = false; }
}

async function retryGenerate() {
  if (saving.value) return;
  if (!task.value) { generationPickerOpen.value = true; return; }
  saving.value = true;
  try { await startGenerate(); } catch { /* API reports failure; allow retry. */ } finally { saving.value = false; }
}

async function startGenerate() {
  task.value = null;
  confirmed.value = false;
  const res = await generateCreatives(wizard.planId, [...wizard.aiProductIds]);
  if (disposed || wizard.step !== 3) return;
  wizard.aiSelectionConfirmed = true;
  wizard.taskId = res.taskId;
  task.value = { taskId: res.taskId, status: "PENDING", steps: [], items: [] };
  generationPickerOpen.value = false;
  stopPoll();
  pollTimer = window.setInterval(pollTask, 2000);
  await pollTask();
}

async function pollTask() {
  if (!wizard.taskId || polling || disposed) return;
  polling = true;
  const id = wizard.taskId;
  try {
    const next = await getCreativeTask(id);
    if (id !== wizard.taskId || disposed) return;
    progressUnavailable = false;
    task.value = next;
    if (next.packageId) wizard.packageId = next.packageId;
    if (["SUCCEEDED", "FAILED", "CANCELLED"].includes(next.status)) stopPoll();
  } catch {
    if (!disposed && wizard.step === 3 && id === wizard.taskId && !progressUnavailable) {
      progressUnavailable = true;
      ElMessage.warning("素材进度暂时获取失败，将自动重试");
    }
  } finally { polling = false; }
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

async function onReplace(productId: string, file?: File) {
  if (!file || !wizard.packageId) return;
  saving.value = true;
  try {
    await replaceItem(wizard.packageId, productId, file);
    confirmed.value = false;
    await pollTask();
    replacementVersion.value++;
    ElMessage.success("素材已替换");
  } catch {
    // Keep the dialog and selected file available for another attempt.
  } finally {
    saving.value = false;
  }
}

async function onRegenerate(productId: string, type: MaterialType, feedback: MaterialFeedback) {
  if (saving.value || materialTaskId.value || !wizard.packageId) return;
  saving.value = true;
  try {
    const result = await regenerateMaterial(wizard.packageId, productId, type, feedback);
    if (disposed) return;
    materialTaskId.value = result.taskId;
    actionDialog.value = null;
    confirmed.value = false;
    ElMessage.success('已提交重新生成，完成后将替换该素材');
    materialPollTimer = window.setInterval(pollMaterialTask, 2000);
    await pollMaterialTask();
  } catch { /* Keep the current material available if submission fails. */ }
  finally { saving.value = false; }
}

async function pollMaterialTask() {
  if (materialPolling || disposed || !materialTaskId.value) return;
  materialPolling = true;
  try {
    const result = await getCreativeTask(materialTaskId.value);
    if (disposed) return;
    if (!['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(result.status)) return;
    if (result.status === 'SUCCEEDED') {
      const updated = await getCreativeTask(wizard.taskId);
      if (disposed) return;
      task.value = updated;
      ElMessage.success('素材已重新生成');
    } else ElMessage.error(result.errorMsg || '重新生成失败，原素材已保留');
    window.clearInterval(materialPollTimer);
    materialTaskId.value = '';
  } catch { /* A temporary query failure must not discard the server-side task. */ }
  finally { materialPolling = false; }
}

async function onDeleteMaterial(productId: string, type: MaterialType, feedback: MaterialFeedback) {
  if (saving.value || materialTaskId.value || !wizard.packageId) return;
  if (disposed || saving.value || materialTaskId.value) return;
  saving.value = true;
  try {
    await deleteMaterial(wizard.packageId, productId, type, feedback);
    actionDialog.value = null;
    confirmed.value = false;
    if (task.value?.items) {
      task.value = { ...task.value, items: task.value.items.map(item => item.productId !== productId ? item : {
        ...item, ...(type === 'VIDEO' ? { playUrl: undefined } : { posterUrl: undefined }),
      }) };
    }
    await pollTask();
    ElMessage.success('素材已删除');
  } catch { /* The API reports failure; keep the current list unchanged. */ }
  finally { saving.value = false; }
}

async function onConfirm() {
  if (!wizard.packageId) return;
  saving.value = true;
  try {
    await confirmPackage(wizard.packageId);
    confirmed.value = true;
    ElMessage.success("素材已确认");
  } finally {
    saving.value = false;
  }
}

async function onPublish() {
  if (saving.value || task.value?.status !== "SUCCEEDED" || !wizard.packageId) return;
  if (selectedQcProductIds.value.some(id => !task.value?.items?.some(item => item.productId === id && (item.playUrl || item.posterUrl)))) {
    ElMessage.warning("请为所有投放商品补齐素材后再发布");
    return;
  }
  if (wizard.planName.trim().length < 1 || wizard.planName.length > 30) {
    ElMessage.warning("计划名称须为 1–30 字");
    return;
  }
  saving.value = true;
  try {
    if (!confirmed.value) { await confirmPackage(wizard.packageId); confirmed.value = true; }
    await updateDraft(wizard.planId, draftBody());
    const key = crypto.randomUUID();
    const res = await publishPlan(wizard.planId, key);
    wizard.qcPlanId = res.qcPlanId;
    wizard.publishStatus = res.status;
    stopPoll();
    wizard.step = 4;
    await router.replace(`/campaigns/${wizard.planId}/result`);
  } catch {
    // The API reports the error; keep the current package for retry.
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  accounts.value = await listAccounts();
  const first = awemeOptions.value.find(a => a.accountId === wizard.accountId && a.awemeId === wizard.awemeId) || awemeOptions.value[0];
  if (first) {
    awemeKey.value = first.key;
    const sameAweme = wizard.awemeId === first.awemeId;
    onAwemeChange(first.key);
    if (sameAweme) await loadProducts();
    if (wizard.step === 3 && wizard.taskId) {
      await pollTask();
      if (!task.value || task.value.status === "RUNNING" || task.value.status === "PENDING") pollTimer = window.setInterval(pollTask, 2000);
    } else if (wizard.step === 3) generationPickerOpen.value = true;
  }
});

onUnmounted(() => {
  disposed = true;
  stopPoll();
  window.clearInterval(materialPollTimer);
  modeMessage?.close();
});
</script>

<style scoped>.wizard { margin: 0; padding: 0; }</style>
