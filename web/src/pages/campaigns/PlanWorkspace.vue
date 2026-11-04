<template>
  <div class="campaign-reference creative-workspace wizard plan-workspace" :class="{ 'plan-workspace--submission': step === 4 && submissionPhase && !editing, 'plan-workspace--audit': step === 4 && (auditResult || deliveryResult || pausedResult || terminatedResult || completedResult) }">
    <div v-if="loading" class="workspace-loading" role="status">正在加载计划…</div>
    <div v-else-if="loadError" class="workspace-loading"><RequestFailureNotice :failure="loadFailure" :busy="loading" @retry="load" /><router-link to="/campaigns">返回投放列表</router-link></div>
    <template v-else-if="plan">
      <div v-if="!draftEditor" class="workspace-feedback container-page">
        <p v-if="editing && !canEdit" class="workspace-notice" role="status">当前计划状态或账户权限不支持修改，内容以只读方式展示。</p>
        <section v-if="editing && plan.qcPlanId && (settingsFailure || settingsMessage || settingsPending || settingsConflict || !plan.settingsEdit?.version || latestSettings)" class="settings-recovery" aria-label="计划修改核对" aria-live="polite">
          <p v-if="settingsMessage">{{ settingsMessage }}</p>
          <RequestFailureNotice :failure="settingsFailure" :busy="saving || settingsChecking" @retry="settingsFailure?.action === 'VERIFY' ? verifySettingsResult() : readLatestSettings()" />
          <p v-if="!plan.settingsEdit?.version">当前页面缺少计划版本，请读取最新设置后核对。</p>
          <p v-if="settingsPending">{{ (latestSettings || plan).settingsEdit?.message || '上次修改结果尚未确认，当前输入已保留，暂不重复提交。' }}</p>
          <div class="settings-actions"><button v-if="settingsPending" type="button" :disabled="saving || settingsChecking" @click="verifySettingsResult()">{{ settingsChecking ? '核查中…' : '核查上次修改结果' }}</button><button type="button" :disabled="saving || settingsChecking" @click="readLatestSettings()">{{ settingsChecking ? '读取中…' : '读取最新值并核对' }}</button></div>
          <template v-if="latestSettings">
            <p>当前填写内容未被覆盖。请核对后选择采用最新值，或仅保留自己改过的字段。</p>
            <div class="settings-table-scroll"><table><thead><tr><th>字段</th><th>原值</th><th>我的输入</th><th>最新值</th></tr></thead><tbody><tr v-for="row in settingsComparison" :key="row.key" :class="{'settings-changed':row.changed}"><th>{{ row.label }}</th><td>{{ row.before }}</td><td>{{ row.mine }}</td><td>{{ row.latest }}</td></tr></tbody></table></div>
            <p v-if="!canKeepSettings && latestSettings.settingsEdit?.status === 'IDLE'">计划绑定、投放方式或可编辑状态已变化，请采用最新设置后重新编辑。</p>
            <div class="settings-actions"><button type="button" :disabled="settingsChecking || latestSettings.settingsEdit?.status !== 'IDLE' || !latestSettings.settingsEdit?.version" @click="applyLatestSettings(false)">采用最新值</button><button v-if="canKeepSettings" type="button" :disabled="settingsChecking" @click="applyLatestSettings(true)">保留我的修改，采用最新版本</button><button v-if="canAcceptCurrent" type="button" :disabled="settingsChecking" @click="acceptCurrentSettings">接受千川当前设置，结束本次核查</button></div>
          </template>
        </section>
        <PlanReviewNotice v-if="!(step === 4 && auditResult)" :plan="plan" auto-read @updated="plan = $event" />
        <RequestFailureNotice :failure="contextFailure" context="历史计划的抖音号与商品信息暂未补全。" :busy="loading" @retry="load" />
        <RequestFailureNotice :failure="mediaFailure" context="素材预览未刷新，计划信息仍可查看。" :busy="mediaLoading" @retry="reloadMedia" />
      </div>

      <KeepAlive v-if="contentDraftId" :max="1">
        <Create v-if="contentDraftId && (step === 1 || step === 3)" :key="contentDraftId" :ref="rememberContentEditor" :initial-plan-id="contentDraftId" :published-edit-target="plan.planId" :initial-step="step" :initial-material-product="contentTitleProduct" :initial-material-tab="contentTitleEditing ? contentMaterialTab : undefined" :adjust-products-on-open="adjustProductsOnOpen" published-workspace :on-published-saved="finishContentSave" @navigate="navigate" />
      </KeepAlive>
      <Create v-if="draftEditor" :key="plan.planId" :initial-plan-id="plan.planId" :initial-step="draftInitialStep" />
      <template v-else-if="!contentDraftId || step === 2 || step === 4">
        <CreationFrame :step="step" :reached-step="reachedStep" :max-step="plan.qcPlanId || plan.status === 'SUBMITTING' || submissionPhase === 'FAILED' ? 4 : creationReachedStep(creation,step)" :account="account" :account-chips="accountChips" :result-state="step === 4 && (submissionPhase === 'FAILED' || plan.status === 'REJECTED') ? 'warning' : undefined" :result-label="plan.status === 'REJECTED' ? '审核未通过' : undefined" navigable :navigation-disabled="saving || resultActionBusy" @navigate="navigate" />
        <CreateProductsStep v-if="step === 1" :products="products" :selected="products" :loaded="true" :load-error="false" :saving="saving || materialBusy" :account="account" :accounts="[]" :account-chips="accountChips" :total="products.length" :analysis-status="creation?.analysisStatus || 'NONE'" :analysis-error="creation?.analysisError" :analysis="creation?.analysis" :basic-analysis="creation?.basicAnalysis" :analysis-started-at="creation?.analysisStartedAt" :group-decision="creation?.groupingDecision" :allow-adjustment="productAdjustmentAvailable" :adjustment-blocked-reason="productAdjustmentBlockedReason" :selection-locked="editing && selectionLocked" @adjust="startContentEdit" :content-editing="!!plan.qcPlanId" read-only embedded />
        <CreateGoalStep v-else-if="step === 2" v-model:budget="budget" v-model:roi="roi" v-model:duration="duration" v-model:start="start" v-model:end="end" :mode="bidType" :saving="saving" :read-only="!editing || !canEdit || transferring && transferPending" :allow-mode-change="transferring && !!transferAllowed" :disabled-mode="transferring ? plan.smartBidType : undefined" :allow-schedule-change="editing && canEdit" :date-error="dateError" settings-only embedded :budget-options="budgetOptions(creation?.recommendations)" :budget-error="budgetError" budget-warn="" :roi-error="roiError" :order-lo="0" :order-hi="0" :estimate-roi-hint="estimates.roi" :estimate-orders-hint="estimates.orders" :estimate-hint="estimates.message" :budget-hint="budgetHint" :roi-hint="editing ? '输入修改后的净成交ROI目标' : '当前计划的净成交ROI目标'" selection-warning="" :demo-mode="false" :account="account" :accounts="[]" :account-chips="accountChips" @mode="changeTransferMode" />
        <template v-else-if="step === 3">
          <CreateMaterialsStep v-if="materialState" v-model:plan-name="name" :state="materialState" :busy="saving || materialBusy" :account="account" :account-chips="accountChips" read-only :addition-actions="productAdjustmentAvailable && !settingsBlocked && !selectionLocked" @add="addPublishedMaterial" @add-uploads="addPublishedUploads" @upload-batch-finished="finishPublishedUploads" @generate-addition="generatePublishedAddition" :material-actions="editing && canEdit" :title-actions="productAdjustmentAvailable && !settingsBlocked && !selectionLocked" @product-titles="saveProductTitles" @add-title="addPublishedTitle" @regenerate-title="regenerateProductTitle" embedded @action="openMaterialAction" @resolve-version="resolveMaterialVersion" @retry-sync="retryVersionSync" :version-error="mediaFailure?.message" />
          <PublishedPlanMaterials v-else-if="plan.qcPlanId" :key="plan.planId" :plan="plan" :products="products" :account="account" />
          <main v-else class="ad-main"><div class="container-page"><section class="panel"><div class="panel__pad"><h2 class="panel__title">投放素材</h2><p class="workspace-empty">当前计划暂无已保存的素材。</p></div></section></div></main>
          <main class="ad-main workspace-plan-settings"><div class="container-page"><section class="panel"><div class="panel__pad"><label for="workspace-plan-name">投放计划名称</label><input id="workspace-plan-name" v-model="name" maxlength="30" :disabled="saving || !editing || !canEdit" aria-label="计划名称" /><p v-if="nameError" class="workspace-error" role="alert">{{ nameError }}</p></div></section></div></main>
        </template>
        <main v-else-if="completedResult" class="ad-main workspace-audit"><div class="container-page"><PlanCompletedResult :show-footer="false" :plan="plan" :aweme-name="account?.awemeName" @updated="plan = $event" /></div></main>
        <main v-else-if="terminatedResult" class="ad-main workspace-audit"><div class="container-page"><PlanTerminatedResult :show-footer="false" :plan="plan" :aweme-name="account?.awemeName" @updated="plan = $event" /></div></main>
        <main v-else-if="pausedResult" class="ad-main workspace-audit"><div class="container-page"><PlanPausedResult ref="pausedView" :show-footer="false" :read-only="!canEdit" inline-footer :restore-disabled="editing && (dirty || settingsBlocked || saving || materialBusy)" :plan="plan" :aweme-name="account?.awemeName" @updated="plan = $event" /></div></main>
        <main v-else-if="deliveryResult" class="ad-main workspace-audit"><div class="container-page"><PlanDeliveryResult ref="deliveryView" :show-footer="false" :read-only="!canEdit" inline-footer :pause-disabled="editing && (dirty || settingsBlocked || saving || materialBusy)" :plan="plan" :aweme-name="account?.awemeName" @updated="plan = $event" /></div></main>
        <main v-else-if="auditResult" class="ad-main workspace-audit"><div class="container-page"><PlanAuditResult :read-only="!editing || !canEdit" :show-footer="false" :plan="plan" :aweme-name="account?.awemeName" @updated="plan = $event" /></div></main>
        <main v-else-if="submissionPhase" class="ad-main workspace-submission" :class="{ 'workspace-submission--editing': editing }"><div class="container-page"><PlanSubmissionCard :phase="submissionPhase" :summary="submissionSummary" :error="creation?.publishError" :recovery="creation?.publishRecovery" @recover="recoverFailedPlan" @back="backToPlans" /></div></main>
        <main v-else class="ad-main"><div class="container-page"><section class="panel"><div class="panel__pad"><h2 class="panel__title">投放结果</h2><p class="panel__sub">{{ plan.rejectReason || '投放状态与效果以千川同步数据为准' }}</p><dl class="workspace-result"><div><dt>投放状态</dt><dd>{{ planStatusLabel(plan.status, plan.qcStatus) }}</dd></div><div><dt>投放时间</dt><dd>{{ plan.startDate || '—' }} 至 {{ publishedSchedule(plan).duration === 'long' ? '不限' : plan.endDate || '不限' }}</dd></div><div><dt>统计日期</dt><dd>{{ plan.statisticsDate || '—' }}{{ plan.historicalMetrics ? '（今日暂无报表，展示历史数据）' : '' }}</dd></div><div><dt>数据同步时间</dt><dd>{{ dateText(plan.snapshotTs) }}</dd></div></dl><PlanKpis :metrics="{ cost: plan.costYuan, gmv: plan.gmvYuan, roi: plan.roi, orders: plan.convert }" /><div class="workspace-analysis"><router-link class="panel__toggle" :to="`/campaigns/${plan.planId}/analysis`">查看投放效果分析<ReferenceIcon name="forward" /></router-link></div></div></section></div></main>
        <CreationFooter v-if="step !== 4 || auditResult || deliveryResult || pausedResult || terminatedResult || completedResult || !submissionPhase || editing" :plan-name="name" :plan-status="plan.status" :qc-status="plan.qcStatus" :busy="saving || materialBusy || resultActionBusy" :show-draft-save="false" :hint="footerHint" @back="backToPlans">
          <template #previous>
            <button v-if="step > 1" class="btn-prev" type="button" :disabled="saving || materialBusy || resultActionBusy" @click="navigate(step - 1)">上一步</button>
          </template>
          <button v-if="!editing && step === 4 && deliveryView?.canPause" class="btn-prev btn-pause" type="button" :disabled="deliveryView.actionDisabled" @click="deliveryView.openPause()"><ReferenceIcon name="pause" />暂停投放</button>
          <button v-if="!editing && step === 4 && pausedView?.canRestore" class="btn-next-primary" type="button" :disabled="pausedView.actionDisabled" @click="pausedView.openRestore()"><ReferenceIcon name="play" />恢复投放</button>
          <button v-if="step < 4" :class="editing && canEdit ? 'btn-prev' : 'btn-next-primary'" type="button" :disabled="saving || materialBusy" @click="navigate(step + 1)">下一步</button>
          <button v-if="editing && canEdit" class="btn-next-primary" type="button" :disabled="saving || (!transferPending && (settingsBlocked || (!dirty && !contentDraftId) || !!nameError || !!budgetError || !!roiError || !!dateError || changedMode && !transferAllowed))" @click="save">{{ saving ? '保存中…' : '保存修改' }}</button>
          <button v-if="editing" class="btn-cancel-edit" type="button" :disabled="saving || materialBusy" @click="backToPlans">取消修改</button>
        </CreationFooter>
      </template>
      <CreationActionDialog v-if="materialAction" :action="materialAction.kind" :error="mediaFailure?.message" :material="creation?.materials.find(m => m.id === materialAction?.id)" :busy="materialBusy" :retry-at="creation?.regenerationRetryAt" published @close="materialAction = undefined" @confirm="submitMaterialAction" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { addProductTitle, type TitleAddition, type TitleAdditionReceipt } from '@/features/delivery/titleAddition';
import { computed, onUnmounted, ref, watch } from 'vue';
import { persistUploadBatch, localUploadSelection, type LocalUploadBatch, type UploadBatchReceipt } from '@/features/creative/localUploadBatch';
import { useRoute, useRouter } from 'vue-router';
import { useCreationActionNotices } from '@/features/delivery/useCreationActionNotices';
import { ElMessage, ElMessageBox } from 'element-plus';
import { canContinueSettingsAfterContent, canRebasePlan, rebasePlanInputs, planEditComparison } from '@/features/delivery/planEditConflict';
import { requestFailure, type ApiRequestError } from '@/api/apiErrors';
import RequestFailureNotice from '@/components/RequestFailureNotice.vue';
import { getPlan, updatePlanSettings, refreshPlanSettings, verifyPlanSettings, updateDraft, getPlanTransfer, transferPlan, reconcilePlanTransfer, type PlanVo, type PlanTransferContext, type PlanTransferRequest, type PlanTransferResult } from '@/features/delivery/deliveryApi';
import { beginContentEdit, creationAdd, creationAddition, type CreationAdditionBody, creationGet, creationProductTitles, creationRegenerateTitle, creationFeedback, creationResolveVersion, creationRetrySync, type CreationProduct, type CreationState } from '@/features/delivery/creationApi';
import type { MaterialFeedback } from '@/features/creative/creativeApi';
import CreationActionDialog from './create/CreationActionDialog.vue';
import { budgetEstimateHints, budgetOptions } from '@/features/delivery/creationRecommendations';
import { awemeLabels } from '@/features/identity/awemeLabels';
import { creationReachedStep, validReachedStep } from '@/features/delivery/creationProgress';
import { productSelectionLocked } from '@/features/delivery/generationSelection';
import { publishedSchedule, publishedScheduleError } from '@/features/delivery/publishedSchedule';
import { listAccounts, type AccountVo } from '@/features/identity/oauthApi';
import { useUserStore } from '@/stores/user';
import { planStatusLabel } from '@/utils/labels';
import { dateText } from './reference/planPresentation';
import CreationFrame from './reference/CreationFrame.vue';
import CreationFooter from './create/CreationFooter.vue';
import ReferenceIcon from './reference/ReferenceIcon.vue';
import PlanKpis from './reference/PlanKpis.vue';
import PlanSubmissionCard from './components/PlanSubmissionCard.vue';
import { planSubmissionPhase, planSubmissionSummary } from '@/features/delivery/planSubmissionPresentation';
import { publishFailureLocation, type PublishFailureTarget } from '@/features/delivery/planPublishFailure';
import PlanReviewNotice from './components/PlanReviewNotice.vue';
import PlanAuditResult from './components/PlanAuditResult.vue';
import PlanDeliveryResult from './components/PlanDeliveryResult.vue';
import { hasPlanDeliveryResult } from '@/features/delivery/planDeliveryResult';
import PlanPausedResult from './components/PlanPausedResult.vue';
import { hasPlanPausedResult } from '@/features/delivery/planPausedResult';
import PlanTerminatedResult from './components/PlanTerminatedResult.vue';
import { hasPlanTerminatedResult } from '@/features/delivery/planTerminatedResult';
import PlanCompletedResult from './components/PlanCompletedResult.vue';
import PublishedPlanMaterials from './components/PublishedPlanMaterials.vue';
import { hasPlanCompletedResult } from '@/features/delivery/planCompletedResult';
import { hasPlanAuditResult } from '@/features/delivery/planReview';
import CreateProductsStep from './create/CreateProductsStep.vue';
import CreateGoalStep from './create/CreateGoalStep.vue';
import { budgetInputError, roiInputError, planNameError, budgetMinimum as minimumBudget, deliveryInputRules } from '@/features/delivery/inputValidation';
import CreateMaterialsStep from './create/CreateMaterialsStep.vue';
import Create from './Create.vue';
import './reference/reference.css';
import './reference/creative-workspace.css';
import './create/create-theme.css';

const props = defineProps<{ mode: 'view' | 'edit' | 'transfer' }>();
const route = useRoute(), router = useRouter(), user = useUserStore();
const contentTitleEditing=ref(false), contentTitleProduct=ref('');
const contentMaterialTab=ref<'VIDEO'|'IMAGE'|'CAROUSEL'|'TITLE'>('TITLE');
const contentDraftId=ref(''), adjustProductsOnOpen=ref(false);
let contentEditor: {savePublished:()=>Promise<boolean>} | undefined;
function rememberContentEditor(value:unknown){if(value)contentEditor=value as NonNullable<typeof contentEditor>;}
async function startContentEdit(){if(!plan.value||!productAdjustmentAvailable.value||settingsBlocked.value||selectionLocked.value||saving.value||materialBusy.value)return;saving.value=true;try{const result=await beginContentEdit(plan.value.planId);adjustProductsOnOpen.value=true;contentDraftId.value=result.planId;}catch(error){mediaFailure.value=requestFailure(error,{method:'POST'});}finally{saving.value=false;}}
async function editProductCopy(product:string,change:(state:CreationState)=>Promise<CreationState>,tab:'VIDEO'|'IMAGE'|'CAROUSEL'|'TITLE'='TITLE') {
  if(!plan.value||!productAdjustmentAvailable.value||settingsBlocked.value||selectionLocked.value||saving.value||materialBusy.value)return;
  materialBusy.value=true;mediaFailure.value=undefined;
  try{const draft=await beginContentEdit(plan.value.planId);await change(draft);contentTitleEditing.value=true;contentTitleProduct.value=product;contentMaterialTab.value=tab;adjustProductsOnOpen.value=false;contentDraftId.value=draft.planId;}
  catch(error){mediaFailure.value=requestFailure(error,{method:'POST'});}
  finally{materialBusy.value=false;}
}
function addPublishedMaterial(body:Parameters<typeof creationAdd>[1]) {return editProductCopy(body.productId,async s=>{const next=await creationAdd(s,body);notices.added(s,next);return next;},body.type as 'VIDEO'|'IMAGE'|'CAROUSEL');}
let pendingUploadDraft: CreationState | undefined;
async function addPublishedUploads(body: LocalUploadBatch, receipt: UploadBatchReceipt) {
  if (!plan.value || !productAdjustmentAvailable.value || settingsBlocked.value || selectionLocked.value || saving.value || materialBusy.value) { receipt.done('暂时无法添加素材，请稍后重试。'); return; }
  materialBusy.value = true; mediaFailure.value = undefined;
  try {
    pendingUploadDraft = pendingUploadDraft ? await creationGet(pendingUploadDraft.planId) : await beginContentEdit(plan.value.planId);
    contentTitleProduct.value = body.productId; contentMaterialTab.value = body.type;
    await persistUploadBatch(pendingUploadDraft, body.assetIds,
      (current, id) => creationAdd(current, { productId: body.productId, type: body.type, addedVia: 'UPLOAD', ...localUploadSelection(body.type, id) }),
      (id, next) => { notices.added(pendingUploadDraft!,next);pendingUploadDraft=next;receipt.added(id); });
    receipt.done();
  } catch (error) { mediaFailure.value = requestFailure(error, { method: 'POST' }); receipt.done(mediaFailure.value.message); }
  finally { materialBusy.value = false; }
}
function finishPublishedUploads() {
  if (!pendingUploadDraft) return;
  contentTitleEditing.value = true; adjustProductsOnOpen.value = false;
  contentDraftId.value = pendingUploadDraft.planId; pendingUploadDraft = undefined;
}
function generatePublishedAddition(body:CreationAdditionBody) {return editProductCopy(body.productId,async s=>{const next=await creationAddition(s,body);notices.addition(next,body.requestId);return next;},body.type);}
async function addPublishedTitle(body:TitleAddition,receipt:TitleAdditionReceipt) {
  if(!plan.value||!productAdjustmentAvailable.value||settingsBlocked.value||selectionLocked.value||saving.value||materialBusy.value){receipt.done('暂时无法新增标题，请稍后重试。');return;}
  materialBusy.value=true;mediaFailure.value=undefined;
  try {
    const draft=await beginContentEdit(plan.value.planId),next=await addProductTitle(draft,{...body,requestId:receipt.requestId});
    notices.titleAdded(draft,next,body.productId,body.kind,body.source==='AI');
    receipt.done(undefined,body.source==='AI'?next.titleGeneration:undefined);
    if(body.source==='AI'&&next.titleGeneration?.status==='FAILED')return;
    contentTitleEditing.value=true;contentTitleProduct.value=body.productId;contentMaterialTab.value='TITLE';adjustProductsOnOpen.value=false;contentDraftId.value=next.planId;
  }catch(error){receipt.done(requestFailure(error,{method:'POST'}).message);}
  finally{materialBusy.value=false;}
}
function saveProductTitles(product:string,kind:'CUSTOM'|'CARD',titles:string[]){return editProductCopy(product,async s=>{const next=await creationProductTitles(s,product,kind,titles);notices.titlesEdited(s,next,product,kind);return next;});}
function regenerateProductTitle(product:string,kind:'CUSTOM'|'CARD',index:number,original:string){return editProductCopy(product,async s=>{const next=await creationRegenerateTitle(s,product,kind,index,original);notices.titleGenerated(next);return next;});}
const plan = ref<PlanVo>(), creation = ref<CreationState>(), accounts = ref<AccountVo[]>([]);
const notices=useCreationActionNotices(creation);
const auditResult = computed(() => hasPlanAuditResult(plan.value));
const deliveryResult = computed(() => hasPlanDeliveryResult(plan.value));
const pausedResult = computed(() => hasPlanPausedResult(plan.value));
const terminatedResult = computed(() => hasPlanTerminatedResult(plan.value));
const completedResult = computed(() => hasPlanCompletedResult(plan.value));
const pausedView = ref<InstanceType<typeof PlanPausedResult>>(), deliveryView = ref<InstanceType<typeof PlanDeliveryResult>>();
const resultActionBusy = computed(() => !!(pausedView.value?.busy || deliveryView.value?.busy));
const submissionPhase = computed(() => planSubmissionPhase({ ...plan.value, publishStatus: !plan.value?.qcPlanId && creation.value?.publishStatus === 'FAILED' ? 'FAILED' : undefined }));
const submissionSummary = computed(() => planSubmissionSummary(plan.value || {}, account.value?.awemeName));
function recoverFailedPlan(choice: PublishFailureTarget) {
  if (!plan.value) return;
  const location = publishFailureLocation(plan.value.planId, creation.value?.publishRecovery, creation.value?.publishError, choice);
  if (location) void router.push(location);
}
const loading = ref(true), saving = ref(false), loadError = ref('');
const materialBusy=ref(false), materialAction=ref<{kind:'regenerate'|'delete';id:string}>();
const materialPending=computed(()=>!!creation.value?.materialEdit&&!['SUCCEEDED','FAILED'].includes(creation.value.materialEdit.status));
const selectionLocked=computed(()=>productSelectionLocked(creation.value));
let materialTimer:ReturnType<typeof setTimeout>|undefined;
function openMaterialAction(kind:'regenerate'|'delete',id:string) {
  if(editing.value&&canEdit.value&&!saving.value&&!materialBusy.value&&!materialPending.value)materialAction.value={kind,id};
}
async function retryVersionSync(id:string) { const s=creation.value,p=plan.value;if(!s||!p||!editing.value||!canEdit.value||materialBusy.value||saving.value)return;const version=loadNumber;materialBusy.value=true;mediaFailure.value=undefined;try{const updated=await creationRetrySync({...s,planId:p.planId},id);if(version===loadNumber)creation.value=updated;}catch(error){if(version===loadNumber)mediaFailure.value=requestFailure(error,{method:'POST'});}finally{if(version===loadNumber){materialBusy.value=false;scheduleMaterialPoll();}} }
async function resolveMaterialVersion(id:string,decision:'REPLACE'|'KEEP_BOTH'|'DISCARD') {
  const s=creation.value,p=plan.value;if(!s||!p||!editing.value||!canEdit.value||materialBusy.value||saving.value||materialPending.value)return;
  const version=loadNumber;materialBusy.value=true;mediaFailure.value=undefined;
  try {const updated=await creationResolveVersion({...s,planId:p.planId},id,decision);if(version===loadNumber){creation.value=updated;if(decision==='DISCARD')notices.deleted(updated,id);}}
  catch(error){if(version===loadNumber)mediaFailure.value=requestFailure(error,{method:'POST'});}
  finally{if(version===loadNumber){materialBusy.value=false;scheduleMaterialPoll();}}
}
async function submitMaterialAction(feedback:MaterialFeedback) {
  const action=materialAction.value,s=creation.value,p=plan.value;
  if(!action||!s||!p||!editing.value||!canEdit.value||materialBusy.value||saving.value||materialPending.value)return;
  const version=loadNumber;materialBusy.value=true;mediaFailure.value=undefined;
  try {const updated=await creationFeedback({...s,planId:p.planId},action.id,action.kind,feedback);if(version===loadNumber){creation.value=updated;if(action.kind==='delete')notices.deleted(updated,action.id);else notices.regenerated(updated);materialAction.value=undefined;}}
  catch(error){if(version===loadNumber)mediaFailure.value=requestFailure(error,{method:action.kind==='delete'?'DELETE':'POST'});}
  finally{if(version===loadNumber){materialBusy.value=false;scheduleMaterialPoll();}}
}
function scheduleMaterialPoll() {
  clearTimeout(materialTimer);if(!materialPending.value&&!selectionLocked.value)return;
  const version=loadNumber,id=plan.value?.planId;if(!id)return;
  materialTimer=setTimeout(async()=>{try{const current=await creationGet(id);if(version===loadNumber){creation.value=current;mediaFailure.value=undefined;}}catch(error){if(version===loadNumber)mediaFailure.value=requestFailure(error,{method:'GET'});}finally{if(version===loadNumber)scheduleMaterialPoll();}},3000);
}
const loadFailure = ref<ApiRequestError>(), mediaFailure = ref<ApiRequestError>(), mediaLoading = ref(false);
const contextFailure = ref<ApiRequestError>();
const settingsFailure = ref<ApiRequestError>(), latestSettings = ref<PlanVo>();
const settingsChecking = ref(false), settingsConflict = ref(false), settingsUnknown = ref(false), canAcceptCurrent = ref(false), settingsMessage = ref('');
const settingsPending = computed(() => transferring.value && transferPending.value || settingsUnknown.value || !!(latestSettings.value || plan.value)?.settingsEdit && (latestSettings.value || plan.value)!.settingsEdit!.status !== 'IDLE');
const settingsBlocked = computed(() => materialBusy.value || materialPending.value || !!plan.value?.qcPlanId && (settingsChecking.value || settingsConflict.value || settingsPending.value || !plan.value.settingsEdit?.version));
const settingsInputs = () => ({name:name.value,budget:budget.value,roi:roi.value,duration:duration.value,end:end.value});
const settingsComparison = computed(() => plan.value && latestSettings.value ? planEditComparison(plan.value,latestSettings.value,settingsInputs()) : []);
const canKeepSettings = computed(() => !!plan.value && !!latestSettings.value && canRebasePlan(plan.value,latestSettings.value));
function clearSettingsRecovery() {settingsChecking.value=false;settingsFailure.value=undefined;latestSettings.value=undefined;settingsConflict.value=false;settingsUnknown.value=false;canAcceptCurrent.value=false;settingsMessage.value='';}
const name = ref(''), budget = ref(''), roi = ref(''), duration = ref<'long' | 'custom'>('long'), start = ref(''), end = ref('');
let loadNumber = 0;
const transferring = computed(() => props.mode === 'transfer');
const editing = computed(() => props.mode !== 'view');
const transferContext = ref<PlanTransferContext>(), transferMode = ref('');
const pendingSubmission = ref(false);
let submission: PlanTransferRequest | undefined;
const transferAllowed = computed(() => canEdit.value && !!plan.value?.qcPlanId && transferContext.value?.available && !pendingSubmission.value);
const transferPending = computed(() => pendingSubmission.value || !!transferContext.value?.pendingKey);
const initialViewStep = ref(1);
const step = computed(() => { const value = Number(route.query.step); return Number.isInteger(value) && value >= 1 && value <= 4 ? value : transferring.value ? 2 : initialViewStep.value; });
const draftInitialStep = computed(() => route.query.step == null ? undefined : step.value);
const boundAccount = computed(() => accounts.value.find(a => a.accountId === plan.value?.accountId));
const account = computed(() => {
  const p = plan.value; if (!p?.awemeId) return undefined;
  const aweme = boundAccount.value?.awemes.find(w => w.awemeId === p.awemeId);
  const known = /^[1-9][0-9]{0,18}$/.test(p.awemeId);
  return { key: `${p.accountId}:${p.awemeId}`, awemeName: aweme?.name || (known ? p.awemeId : '暂未获取抖音号'), awemeId: known ? p.awemeId : '—', avatar: aweme?.avatar };
});
const accountChips = computed(() => awemeLabels(boundAccount.value?.awemes.find(w => w.awemeId === plan.value?.awemeId)));
const canEdit = computed(() => user.trialActive && boundAccount.value?.status === 'ACTIVE' && !!plan.value && (plan.value.qcPlanId ? ['DELIVERING', 'PAUSED'].includes(plan.value.status) : plan.value.source !== 'EXTERNAL' && ['DRAFT', 'REJECTED'].includes(plan.value.status)));
const productAdjustmentAvailable = computed(() => editing.value && canEdit.value && !!plan.value?.qcPlanId && !!creation.value);
const productAdjustmentBlockedReason = computed(() => settingsBlocked.value ? '请先完成当前计划修改的核查，再调整商品。' : '');
const draftEditor = computed(() => !transferring.value && editing.value && canEdit.value && !!creation.value && !plan.value?.qcPlanId);
const footerHint = computed(() => {
  if (!editing.value || !canEdit.value) return '';
  if (!plan.value?.qcPlanId) return '保存修改后更新草稿';
  return step.value === 3 ? '素材操作单独确认，计划名称修改需保存' : '';
});
const bidType = computed(() => transferring.value ? transferMode.value : plan.value?.smartBidType || (creation.value?.smartBidType ? `SMART_BID_${creation.value.smartBidType}` : ''));
const isCost = computed(() => bidType.value === 'SMART_BID_CUSTOM');
const products = computed<CreationProduct[]>(() => (plan.value?.products || []).map(p => {
  const original = creation.value?.products.find(x => x.qcProductId === p.id);
  return { ...original, productId: p.id, qcProductId: p.id, name: p.name, imageUrl: p.imageUrl || original?.imageUrl, price: p.priceInfo?.minPrice ?? original?.price ?? null, priceInfo: p.priceInfo || original?.priceInfo, stock: p.stock ?? original?.stock, sales: p.sales ?? original?.sales, userPrice: original?.userPrice ?? null, tags: original?.tags || [], unavailableReasons: original?.unavailableReasons || [] };
}));
const materialState = computed<CreationState | undefined>(() => creation.value && plan.value ? { ...creation.value, name: name.value, products: products.value, budget: plan.value.budget, roi2Goal: plan.value.roi2Goal ?? undefined } : undefined);
const estimates = computed(() => budgetEstimateHints(creation.value?.recommendations, budget.value));
const changedMode = computed(() => transferring.value && !!plan.value && bidType.value !== plan.value.smartBidType);
const changedBudget = computed(() => plan.value != null && Number(budget.value) !== plan.value.budget);
const changedRoi = computed(() => isCost.value && plan.value != null && Number(roi.value) !== plan.value.roi2Goal);
const changedSchedule = computed(() => !!plan.value && (duration.value !== publishedSchedule(plan.value).duration || duration.value === 'custom' && end.value !== (plan.value.endDate || '')));
const dateError = computed(() => editing.value && changedSchedule.value ? publishedScheduleError(duration.value,start.value,end.value) : '');
const reachedStep = computed(() => {
  const reached = plan.value?.qcPlanId || plan.value?.status === 'SUBMITTING' ? 4 : creationReachedStep(creation.value,step.value);
  if (!editing.value || plan.value?.qcPlanId) return reached;
  const valid = validReachedStep(reached,false,changedBudget.value || changedRoi.value || changedSchedule.value);
  return materialBusy.value || materialPending.value || name.value.trim() !== plan.value?.name ? Math.min(valid,3) : valid;
});
const dirty = computed(() => !!plan.value && (name.value.trim() !== plan.value.name || changedBudget.value || changedRoi.value || changedSchedule.value || changedMode.value));
const nameError = computed(() => editing.value && name.value.trim() !== plan.value?.name ? planNameError(name.value) : '');
const inputProfile = computed(() => changedMode.value || draftEditor.value ? 'creation' : 'update');
const budgetMinimum = computed(() => minimumBudget(bidType.value));
const budgetHint = computed(() => editing.value ? `预算范围 ${budgetMinimum.value}–${deliveryInputRules[inputProfile.value].budgetMaximum.toLocaleString()} 元，最多两位小数，保存后同步千川` : '当前计划的日预算');
const budgetError = computed(() => changedMode.value || draftEditor.value || editing.value && changedBudget.value ? budgetInputError(budget.value,bidType.value,inputProfile.value) : '');
const roiError = computed(() => changedMode.value || draftEditor.value || editing.value && changedRoi.value ? roiInputError(roi.value,bidType.value,inputProfile.value) : '');
function resetForm() {
  const p = plan.value; if (!p) return;
  name.value = p.name; budget.value = p.budget == null ? '' : String(p.budget); roi.value = p.roi2Goal == null ? '' : String(p.roi2Goal);
  const schedule=publishedSchedule(p);duration.value=schedule.duration;
  start.value = p.startDate || creation.value?.startDate || ''; end.value = schedule.end;
  if (transferring.value) {
    transferMode.value = p.smartBidType || '';
  }
}
async function load() {
  clearTimeout(materialTimer);contentEditor=undefined;pendingUploadDraft=undefined;materialAction.value=undefined;materialBusy.value=false;contentTitleEditing.value=false;contentDraftId.value='';adjustProductsOnOpen.value=false;
  const request = ++loadNumber, id = String(route.params.id || ''); clearSettingsRecovery(); loading.value = true; loadError.value = ''; loadFailure.value = undefined; mediaFailure.value = undefined; contextFailure.value = undefined;
  const results = await Promise.allSettled([getPlan(id), listAccounts(), creationGet(id), transferring.value ? getPlanTransfer(id) : Promise.resolve(undefined)]);
  if (request !== loadNumber) return;
  const [p, a, c, t] = results;
  if (p.status === 'rejected') { plan.value = undefined; loadFailure.value = requestFailure(p.reason,{method:'GET'}); loadError.value = loadFailure.value.message; }
  else {
    let current=p.value;
    // Historical list imports omit the aweme ID; full detail restores it without guessing an account.
    // Editing also reads current platform settings before accepting changes.
    const missingAweme = !/^[1-9][0-9]{0,18}$/.test(current.awemeId || '');
    if (current.qcPlanId && (editing.value || missingAweme) && (!current.settingsEdit || current.settingsEdit.status === 'IDLE')) {
      try { current=await refreshPlanSettings(id); }
      catch(error) { if(request === loadNumber) { if(missingAweme)contextFailure.value=requestFailure(error,{method:'GET'}); else settingsFailure.value=requestFailure(error,{method:'GET'}); } }
      if(request !== loadNumber)return;
    }
    plan.value = current; accounts.value = a.status === 'fulfilled' ? a.value : [];
    creation.value = c.status === 'fulfilled' ? c.value : undefined;
    initialViewStep.value = submissionPhase.value || auditResult.value || deliveryResult.value || pausedResult.value || terminatedResult.value || completedResult.value ? 4 : 1;
    transferContext.value = t.status === 'fulfilled' ? t.value : undefined;
    if (transferring.value && t.status === 'rejected') settingsFailure.value = requestFailure(t.reason,{method:'GET'});
    if (c.status === 'rejected' && ![1403, 1405].includes(Number(c.reason?.code))) mediaFailure.value = requestFailure(c.reason,{method:'GET'});
    resetForm();
  }
  loading.value = false;
  scheduleMaterialPoll();
}
async function reloadMedia() {
  if (!plan.value || mediaLoading.value) return;
  const id=plan.value.planId, version=loadNumber; mediaLoading.value = true;
  try {
    const result=await creationGet(id);
    if (version === loadNumber && plan.value?.planId === id) { creation.value=result; mediaFailure.value=undefined; }
  } catch(error) { if (version === loadNumber && plan.value?.planId === id) mediaFailure.value=requestFailure(error,{method:'GET'}); }
  finally { mediaLoading.value=false; }
}
function navigate(value: number) { if (!saving.value && !resultActionBusy.value && value >= 1 && value <= 4) { void router.replace({ path: route.path, query: { ...route.query, step: String(value) } }); window.scrollTo(0, 0); } }
function backToPlans() { if (!saving.value && !materialBusy.value && !resultActionBusy.value) void router.push('/campaigns'); }
function changeTransferMode(value: string) {
  if (!transferring.value || !transferAllowed.value || saving.value || value === plan.value?.smartBidType || value === transferMode.value) return;
  transferMode.value = value;
  if (value === transferContext.value?.targetMode) {
    budget.value = String(transferContext.value.budget);
    roi.value = transferContext.value.roi2Goal == null ? '' : String(transferContext.value.roi2Goal);
  } else { budget.value = plan.value?.budget == null ? '' : String(plan.value.budget); roi.value = plan.value?.roi2Goal == null ? '' : String(plan.value.roi2Goal); }
}
async function finishTransfer(result: PlanTransferResult) {
  settingsMessage.value = result.status === 'SUCCEEDED' ? '' : result.message;
  if (result.status === 'SUCCEEDED') {
    submission = undefined; pendingSubmission.value = false; ElMessage.success('计划修改已保存');
    await router.replace({ path: `/campaigns/${result.planId}`, query: { step: String(step.value) } }); await load();
  } else {
    pendingSubmission.value = ['RUNNING', 'UNCERTAIN'].includes(result.status);
    if (result.status === 'FAILED') submission = undefined;
    transferContext.value = await getPlanTransfer(String(route.params.id));
  }
}
async function submitTransfer() {
  const p = plan.value;
  if (!p?.qcPlanId || !transferAllowed.value || saving.value || budgetError.value || roiError.value || bidType.value === p.smartBidType) return;
  submission ||= { requestKey: crypto.randomUUID(), expectedQcPlanId: p.qcPlanId, smartBidType: bidType.value, budget: Number(budget.value), ...(isCost.value ? { roi2Goal: Number(roi.value) } : {}), ...(name.value.trim() !== p.name ? {name:name.value.trim()} : {}), ...(changedSchedule.value ? {scheduleType:duration.value==='long'?'FROM_NOW' as const:'DATE_RANGE' as const,...(duration.value==='custom'?{endDate:end.value}:{})} : {}) };
  saving.value = true;
  try { await finishTransfer(await transferPlan(p.planId, submission)); }
  catch (error) {
    // A browser timeout is not evidence that CREATE failed. Do not allow a fresh request.
    const code = Number((error as { code?: number })?.code);
    pendingSubmission.value = ![1201, 1401, 1402, 1403, 1404, 1405].includes(code);
    settingsFailure.value = requestFailure(error,{method:'POST'});
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
  } catch(error) { settingsFailure.value = requestFailure(error,{method:'GET'}); }
  finally { saving.value = false; }
}
async function save() {
  const p = plan.value;
  if (!editing.value || !canEdit.value || !p || saving.value) return;
  if (transferring.value && transferPending.value) { await reconcileTransfer(); return; }
  if ((!dirty.value && !contentDraftId.value) || settingsBlocked.value || nameError.value || budgetError.value || roiError.value || dateError.value) return;
  if (contentDraftId.value) {
    if (!contentEditor) return;
    let ready=false;saving.value=true;try{ready=await contentEditor.savePublished();}finally{saving.value=false;}
    if(!ready)navigate(3);return;
  }
  await saveSettings();
}
async function saveSettings() {
  const p=plan.value;if(!p)return;
  if (transferring.value && changedMode.value) { saving.value=false;await submitTransfer();return; }
  const body: { name?: string; budget?: number; roi2Goal?: number; scheduleType?: 'FROM_NOW' | 'DATE_RANGE'; endDate?: string } = {};
  if (name.value.trim() !== p.name) body.name = name.value.trim();
  if (changedBudget.value) body.budget = Number(budget.value);
  if (changedRoi.value) body.roi2Goal = Number(roi.value);
  if (changedSchedule.value) { body.scheduleType=duration.value==='long'?'FROM_NOW':'DATE_RANGE';if(duration.value==='custom')body.endDate=end.value; }
  settingsFailure.value=undefined;settingsMessage.value='';canAcceptCurrent.value=false;
  saving.value = true;
  try {
    plan.value = p.qcPlanId ? await updatePlanSettings(p.planId, { ...body, expectedVersion:p.settingsEdit!.version }) : await updateDraft(p.planId, { accountId: p.accountId!, awemeId: p.awemeId!, name: name.value.trim(), smartBidType: bidType.value, dailyBudget: Number(budget.value), ...(isCost.value ? { roi2Goal: Number(roi.value) } : {}), productIds: products.value.map(x => x.qcProductId) });
    resetForm(); ElMessage.success('计划修改已保存');
    await router.replace({ path: `/campaigns/${p.planId}`, query: { step: String(step.value) } });
  } catch(error) {
    settingsFailure.value=requestFailure(error,{method:'PUT'});
    if(p.qcPlanId) {
      settingsConflict.value=settingsFailure.value.kind === 'CONFLICT';
      settingsUnknown.value=settingsFailure.value.kind === 'UNCERTAIN';
    }
  }
  finally { saving.value = false; }
}
async function finishContentSave() {
  const before=plan.value;if(!before)return;
  const hadSettings=dirty.value;saving.value=true;
  try {
    const latest=await refreshPlanSettings(before.planId);
    creation.value=await creationGet(before.planId);
    contentDraftId.value='';contentEditor=undefined;contentTitleEditing.value=false;adjustProductsOnOpen.value=false;
    if(hadSettings && !canContinueSettingsAfterContent(before,latest)) {
      latestSettings.value=latest;settingsConflict.value=true;settingsMessage.value='计划设置已变化，请核对后保存。';await router.replace({path:route.path,query:{...route.query,step:'2'}});return;
    }
    plan.value=latest;
    if(hadSettings)await saveSettings();
    else {resetForm();ElMessage.success('计划修改已保存');await router.replace({path:`/campaigns/${before.planId}`,query:{step:'3'}});}
  } catch(error) {
    settingsFailure.value=requestFailure(error,{method:'GET'});
    settingsConflict.value=true;contentDraftId.value='';contentEditor=undefined;
  } finally{saving.value=false;}
}
async function readLatestSettings() {
  if (transferring.value && transferPending.value) { await reconcileTransfer(); return; }
  const p=plan.value, version=loadNumber;if(!p?.qcPlanId || settingsChecking.value || saving.value)return;
  settingsChecking.value=true;
  try {
    const current=await getPlan(p.planId);
    if(version!==loadNumber)return;
    // Pending settings are reconciled through a dedicated readback; ordinary sync must not erase their claim.
    const latest=current.settingsEdit && current.settingsEdit.status !== 'IDLE' ? current : await refreshPlanSettings(p.planId);
    if(version!==loadNumber)return;
    latestSettings.value=latest;settingsConflict.value=true;settingsFailure.value=undefined;canAcceptCurrent.value=false;
    settingsMessage.value='已读取最新设置，请核对下表；当前输入保持原样。';
  }catch(error){if(version===loadNumber)settingsFailure.value=requestFailure(error,{method:'GET'});}
  finally{if(version===loadNumber)settingsChecking.value=false;}
}
function applyLatestSettings(keepMine:boolean) {
  const base=plan.value,latest=latestSettings.value;
  if(!base || !latest || latest.settingsEdit?.status!=='IDLE' || !latest.settingsEdit.version)return;
  const values=keepMine?rebasePlanInputs(base,latest,settingsInputs()):undefined;
  plan.value=latest;resetForm();
  if(values){name.value=values.name;budget.value=values.budget;roi.value=values.roi;if(values.duration)duration.value=values.duration;if(values.end!=null)end.value=values.end;}
  clearSettingsRecovery();
  settingsMessage.value=keepMine?'已保留你改过的字段，其他字段采用最新值；核对后点击保存修改。':'已采用最新设置，可继续编辑。';
}
async function verifySettingsResult(acceptCurrent=false) {
  if (transferring.value && transferPending.value) { await reconcileTransfer(); return; }
  const p=plan.value, version=loadNumber;if(!p?.qcPlanId || settingsChecking.value || saving.value)return;
  settingsChecking.value=true;
  try {
    const current=acceptCurrent ? latestSettings.value : await getPlan(p.planId);
    if(version!==loadNumber || !current)return;
    if(!current.settingsEdit?.version)throw new Error('当前页面缺少有效版本，请重新读取计划');
    if(current.settingsEdit.status === 'IDLE') {
      latestSettings.value=current;settingsConflict.value=true;settingsFailure.value=undefined;
      settingsUnknown.value=false;canAcceptCurrent.value=false;settingsMessage.value='服务端已无待确认修改，请核对最新设置；当前输入保持原样。';return;
    }
    const result=await verifyPlanSettings(p.planId,current.settingsEdit.version,acceptCurrent);
    if(version!==loadNumber)return;
    latestSettings.value=result.plan;settingsConflict.value=true;settingsUnknown.value=result.status==='UNCERTAIN';
    canAcceptCurrent.value=result.canAcceptCurrent;settingsMessage.value=result.message;settingsFailure.value=undefined;
  }catch(error){if(version===loadNumber)settingsFailure.value=requestFailure(error,{method:'GET'});}
  finally{if(version===loadNumber)settingsChecking.value=false;}
}
async function acceptCurrentSettings() {
  try {await ElMessageBox.confirm('这会结束上次修改的待确认状态，并采用刚核查的千川当前设置。尚未生效的修改不会自动重发；当前输入继续保留，核对后可重新编辑。','核对千川当前设置',{confirmButtonText:'接受当前设置',cancelButtonText:'继续核查'});}
  catch{return;}
  await verifySettingsResult(true);
}

watch(() => route.params.id, load, { immediate: true });
watch(() => props.mode, () => { submission = undefined; pendingSubmission.value = false; void load(); });
watch(materialPending, scheduleMaterialPoll);
watch(() => !editing.value && step.value === 4 && (submissionPhase.value === 'SUBMITTING' || deliveryResult.value || pausedResult.value) ? plan.value?.planId : undefined, (id, _previous, onCleanup) => {
  if (!id) return;
  let stopped = false;
  let timer: ReturnType<typeof setTimeout>;
  const controller = new AbortController();
  const version = loadNumber;
  const schedule = () => { if (!stopped) timer = setTimeout(readStatus, submissionPhase.value === 'SUBMITTING' ? 2500 : 60000); };
  async function readStatus() {
    try {
      if (document.visibilityState !== 'visible') return;
      const current = await getPlan(id!, { quiet: true, signal: controller.signal });
      const publication = current.status === 'DRAFT' ? await creationGet(id!, controller.signal) : undefined;
      if (!stopped && version === loadNumber && plan.value?.planId === id) {
        plan.value = current;
        if (publication) creation.value = publication;
      }
    } catch { /* Keep the last confirmed status and retry on the next read. */ }
    finally { schedule(); }
  }
  schedule();
  onCleanup(() => { stopped = true; clearTimeout(timer); controller.abort(); });
});
onUnmounted(() => { loadNumber++;clearTimeout(materialTimer); });
</script>

<style scoped>
.plan-workspace { padding-bottom: 132px; }
.plan-workspace.plan-workspace--submission { padding-bottom: 0; }
.plan-workspace .workspace-submission { padding: 24px 0 32px; }
.workspace-loading { padding: 80px 24px; text-align: center; color: #7c7f99; }.workspace-loading button,.workspace-loading a { margin: 16px 10px; }
.workspace-notice { margin-top: 12px; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; padding: 10px 14px; background: #eeeefe; border-radius: 10px; color: #555875; }.workspace-error { color: #e65361; margin-top: 12px; }
.workspace-plan-settings{padding-top:0}.workspace-plan-settings .panel__pad{padding-top:20px;padding-bottom:24px}.workspace-plan-settings label{display:block;margin-bottom:12px;font-weight:600}.workspace-plan-settings input{width:100%;box-sizing:border-box;padding:10px 14px;border:1px solid #dedff0;border-radius:10px;background:#fff;color:#1a1b2e;font:inherit}.workspace-plan-settings input:focus{outline:2px solid #575cf640;border-color:#575cf6}
.workspace-empty { padding: 60px 0; color: #8588a5; text-align: center; }.workspace-result { display: flex; gap: 32px; flex-wrap: wrap; margin: 28px 0; }.workspace-result dt { font-size: 13px; color: #8588a5; }.workspace-result dd { margin-top: 8px; color: #1a1b2e; }.workspace-analysis { margin-top: 28px; }
@media(max-width:767px){.plan-workspace{padding-bottom:220px}}
</style>

<style scoped>
.settings-recovery{margin-top:16px;padding:14px 16px;border:1px solid #dcdcf3;border-radius:12px;background:#fafaff;font-size:13px;line-height:1.7;color:#555875;overflow-wrap:anywhere}.settings-recovery p+p{margin-top:8px}.settings-actions{display:flex;flex-wrap:wrap;gap:10px 18px;margin-top:12px}.settings-actions button{font:inherit;color:#575cf6;text-decoration:underline;white-space:normal;text-align:left}.settings-actions button:disabled{opacity:.45;cursor:not-allowed}.settings-table-scroll{max-width:100%;overflow:auto;margin-top:12px}.settings-table-scroll table{width:100%;border-collapse:collapse;table-layout:fixed;min-width:440px}.settings-table-scroll th,.settings-table-scroll td{padding:8px;border:1px solid #e4e4f3;vertical-align:top;overflow-wrap:anywhere;text-align:left;font-size:12px}.settings-table-scroll thead th{font-weight:600;background:#f0f0ff}.settings-table-scroll tbody th{font-weight:500}.settings-changed td:nth-child(3){color:#575cf6;background:#f0f0ff}
</style>
