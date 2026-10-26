<template>
  <el-dialog :model-value="modelValue" :title="title" :width="action === 'pause' ? '600px' : '500px'" class="campaign-dialog campaign-plan-action" modal-class="campaign-overlay" align-center :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="close" @update:model-value="setVisible">
    <template #header="{ titleId }">
      <div class="plan-confirm-heading">
        <span class="plan-confirm-icon" :class="{ 'is-delete': action === 'delete', 'is-pause': action === 'pause' }" aria-hidden="true"><el-icon><Delete v-if="action === 'delete'" /><VideoPause v-else-if="action === 'pause'" /><VideoPlay v-else /></el-icon></span>
        <div><h2 :id="titleId">{{ title }}</h2><p>{{ subtitle }}</p></div>
      </div>
    </template>
    <section v-if="plan" class="plan-confirm-card" aria-label="核对计划信息">
      <span class="plan-confirm-label">计划名称</span><strong class="plan-confirm-name">{{ plan.name }}</strong>
      <div v-if="action === 'restore'" class="plan-confirm-id"><span :title="id">ID：{{ id }}</span><button type="button" aria-label="复制计划ID" @click="copyId"><el-icon><CopyDocument /></el-icon></button></div>
      <div class="plan-confirm-fields">
        <div><span class="plan-confirm-label">投放方式</span><strong>{{ BID_TYPE[plan.smartBidType || ''] || '未设置投放方式' }}</strong></div>
        <div><span class="plan-confirm-label">{{ action === 'restore' ? '当前状态' : '计划状态' }}</span><span class="plan-confirm-status" :class="state.group.toLowerCase()"><i></i>{{ state.label }}</span></div>
        <template v-if="action === 'restore'">
          <div><span class="plan-confirm-label">每日预算</span><strong>{{ yuan(plan.budget) }}</strong></div>
          <div v-if="plan.smartBidType === 'SMART_BID_CUSTOM'"><span class="plan-confirm-label">ROI目标（净成交）</span><strong>{{ roiText(plan.roi2Goal) }}</strong></div>
        </template>
      </div>
      <div v-if="action !== 'restore'" class="plan-confirm-id plan-confirm-id--bottom"><span :title="id">计划ID：{{ id }}</span><button type="button" aria-label="复制计划ID" @click="copyId"><el-icon><CopyDocument /></el-icon></button></div>
    </section>
    <template v-if="action === 'pause'">
      <fieldset class="plan-pause-reasons" :disabled="busy">
        <legend>暂停原因 <span>（必选一项，便于后续复盘与优化）</span></legend>
        <div class="plan-pause-options">
          <label v-for="reason in PLAN_PAUSE_REASONS" :key="reason" :class="{ selected: pauseReason === reason }">
            <input v-model="pauseReason" type="radio" :name="`pause-reason-${plan?.planId}`" :value="reason" />
            <span>{{ reason }}</span>
          </label>
        </div>
      </fieldset>
      <p class="plan-confirm-impact">暂停后计划将停止投放，可随时在计划列表中恢复。已投放数据、商品、素材和投放设置会保留。</p>
    </template>
    <template v-else-if="action === 'restore'">
      <p class="plan-confirm-impact">恢复后将沿用以上预算{{ plan?.smartBidType === 'SMART_BID_CUSTOM' ? '和目标' : '' }}，可能产生广告消耗。</p>
      <div class="plan-confirm-notice"><el-icon aria-hidden="true"><WarningFilled /></el-icon><div><strong>关联计划提醒</strong><p>如有关联的转投计划，恢复时将暂停另一种投放方式的关联计划。</p></div></div>
      <p v-if="blockedReason" class="plan-confirm-blocked" role="alert">{{ blockedReason }}</p>
    </template>
    <template v-else>
      <p v-if="!draft" class="plan-confirm-impact">删除后，计划将从千川和当前列表中移除。</p>
      <div class="plan-confirm-notice"><el-icon aria-hidden="true"><WarningFilled /></el-icon><strong>{{ draft ? '删除后无法恢复，请确认不再需要此草稿。' : '此操作无法恢复，删除后不能恢复该计划的投放。' }}</strong></div>
      <p class="plan-confirm-retained">仅删除当前{{ draft ? '草稿' : '计划' }}，关联的转投计划和共用素材会保留。</p>
      <p v-if="!draft && plan?.status === 'PAUSED'" class="plan-confirm-retained">若只是暂时不投放，可继续保持暂停。</p>
    </template>
    <p v-if="error" class="plan-confirm-blocked" role="alert">{{ error }}</p>
    <template #footer>
      <span v-if="action === 'restore'" class="plan-confirm-footer-note">恢复结果以千川返回状态为准。</span>
      <el-button :disabled="busy" @click="emit('update:modelValue', false)">{{ action === 'pause' ? '暂不暂停' : action === 'restore' ? '暂不恢复' : draft ? '取消' : '暂不删除' }}</el-button>
      <el-button :type="action === 'delete' ? 'danger' : 'primary'" :loading="busy" :disabled="busy || !!blockedReason || (action === 'pause' && !isPlanPauseReason(pauseReason))" @click="confirm">{{ action === 'pause' ? '确认暂停' : action === 'restore' ? '确认恢复' : draft ? '删除草稿' : '确认删除' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { CopyDocument, Delete, VideoPause, VideoPlay, WarningFilled } from '@element-plus/icons-vue';
import type { PlanListVo } from '@/features/delivery/deliveryApi';
import { planStatusDisplay } from '@/features/delivery/planStatus';
import { BID_TYPE, yuan, roiText } from '@/utils/labels';
import { PLAN_PAUSE_REASONS, isPlanPauseReason, type PlanPauseReason } from '@/features/delivery/planPause';

const props = defineProps<{ modelValue: boolean; plan: PlanListVo | null; action: 'pause' | 'restore' | 'delete'; busy: boolean; blockedReason?: string; error?: string; pauseOrigin?: 'automatic' | 'manual' | 'platform' | 'unknown' }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: [pauseReason?: PlanPauseReason] }>();
const pauseReason = ref('');
watch([() => props.modelValue, () => props.plan?.planId, () => props.action], () => { pauseReason.value = ''; });
const draft = computed(() => !props.plan?.qcPlanId);
const id = computed(() => props.plan?.qcPlanId || props.plan?.planId || '');
const state = computed(() => {
  const display = planStatusDisplay(props.plan?.status, props.plan?.qcStatus);
  return props.plan?.status === 'PAUSED' && props.pauseOrigin ? { ...display, label: { automatic:'系统自动暂停', manual:'手动暂停', platform:'平台异常暂停', unknown:'已暂停（原因待核对）' }[props.pauseOrigin] } : display;
});
const title = computed(() => props.action === 'pause' ? '暂停这条投放计划？' : props.action === 'restore' ? '恢复这条计划的投放？' : draft.value ? '删除这条草稿计划？' : props.plan?.status === 'PAUSED' ? '删除这条已暂停计划？' : '删除这条投放计划？');
const subtitle = computed(() => props.action === 'delete' && draft.value ? '请核对计划信息，确认后将从列表中删除。' : `该计划当前为${state.value.label}，${props.action === 'restore' ? '请确认恢复后的投放设置。' : '请核对后再操作。'}`);
function close(done: () => void) { if (!props.busy) done(); }
function setVisible(value: boolean) { if (!props.busy) emit('update:modelValue', value); }
function confirm() {
  if (props.busy || props.blockedReason) return;
  if (props.action === 'pause') {
    if (isPlanPauseReason(pauseReason.value)) emit('confirm', pauseReason.value);
  } else emit('confirm');
}
async function copyId() {
  try { await navigator.clipboard.writeText(id.value); ElMessage.success('计划ID已复制'); }
  catch { ElMessage.warning('复制失败，请手动复制计划ID'); }
}
</script>

<style scoped>
.plan-confirm-heading { display: flex; gap: 12px; padding-right: 22px; }
.plan-confirm-icon { display: grid; place-items: center; flex: none; width: 40px; height: 40px; border-radius: 12px; font-size: 22px; background: #efedff; color: #6256e8; }
.plan-confirm-icon.is-delete { color: #e5484d; background: #fff0f0; }
.plan-confirm-icon.is-pause { color: #d58b24; background: #fff3e2; }
.plan-confirm-heading h2 { margin: 0; font-size: 17px; font-weight: 600; line-height: 24px; color: #1a1b2e; }
.plan-confirm-heading p { margin: 5px 0 0; color: #7c7f99; font-size: 12px; line-height: 20px; }
.plan-confirm-card { padding: 16px; background: #f7f7fc; border: 1px solid #eeedf5; border-radius: 10px; }
.plan-confirm-label { display: block; color: #7c7f99; font-size: 12px; line-height: 18px; }
.plan-confirm-name { display: block; margin-top: 5px; color: #1a1b2e; font-size: 14px; font-weight: 600; line-height: 21px; overflow-wrap: anywhere; }
.plan-confirm-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px 24px; margin-top: 18px; }
.plan-confirm-fields strong { display: block; margin-top: 5px; color: #1a1b2e; font-size: 13px; font-weight: 500; line-height: 20px; }
.plan-confirm-status { display: inline-flex; align-items: center; gap: 4px; margin-top: 5px; padding: 1px 8px; border-radius: 20px; font-size: 11px; line-height: 18px; color: #7c7f99; background: #eeeef5; }
.plan-confirm-status i { width: 4px; height: 4px; border-radius: 50%; background: currentColor; }
.plan-confirm-status.paused { color: #d58b24; background: #fff1df; }
.plan-confirm-status.delivering { color: #0ba05f; background: #e5f8ed; }
.plan-confirm-status.draft { color: #7562cf; background: #efebff; }
.plan-confirm-id { display: flex; align-items: center; gap: 6px; margin-top: 7px; color: #7c7f99; font-size: 11px; line-height: 18px; }
.plan-confirm-id span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.plan-confirm-id button { display: grid; place-items: center; flex: none; width: 24px; height: 24px; padding: 0; border: 0; border-radius: 5px; background: transparent; color: #7c7f99; cursor: pointer; }
.plan-confirm-id button:hover { background: #eae8fa; color: #575cf6; }
.plan-confirm-id--bottom { padding-top: 12px; margin-top: 14px; border-top: 1px solid #e6e8f2; }
.plan-confirm-impact { margin: 14px 0 10px; color: #4e5069; font-size: 13px; line-height: 21px; }
.plan-confirm-notice { display: flex; align-items: flex-start; gap: 8px; margin-top: 14px; padding: 10px 12px; border-radius: 8px; background: #fff6ec; color: #c78324; font-size: 12px; line-height: 19px; }
.plan-confirm-notice > .el-icon { flex: none; margin-top: 2px; font-size: 15px; }
.plan-confirm-notice strong { font-weight: 600; }
.plan-confirm-notice p { margin: 3px 0 0; color: #8e775c; font-size: 12px; line-height: 19px; }
.plan-confirm-retained { margin: 8px 0 0; color: #7c7f99; font-size: 12px; line-height: 20px; }
.plan-confirm-blocked { margin: 12px 0 0; color: #d0474f; font-size: 12px; line-height: 20px; }
.plan-confirm-footer-note { flex: 1; color: #7c7f99; font-size: 11px; line-height: 18px; text-align: left; }
.plan-pause-reasons { min-width: 0; margin: 16px 0 0; padding: 0; border: 0; }
.plan-pause-reasons + .plan-confirm-impact { margin-bottom: 0; }
.plan-pause-reasons legend { padding: 0; color: #1a1b2e; font-size: 14px; font-weight: 600; }
.plan-pause-reasons legend span { color: #7c7f99; font-size: 12px; font-weight: 400; }
.plan-pause-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 12px; }
.plan-pause-options label { box-sizing: border-box; display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 8px 12px; border: 1px solid #e6e8f2; border-radius: 10px; color: #4e5069; font-size: 12px; line-height: 20px; cursor: pointer; }
.plan-pause-options label:hover { border-color: #a2a5ff; }
.plan-pause-options label.selected { border-color: #575cf6; background: #f0eeff; color: #4348db; }
.plan-pause-options label:has(input:focus-visible) { outline: 2px solid #575cf6; outline-offset: 2px; }
.plan-pause-options input { width: 15px; height: 15px; margin: 0; flex: none; accent-color: #575cf6; }
.plan-pause-reasons:disabled label { cursor: default; opacity: .7; }
:global(.campaign-plan-action.el-dialog .el-dialog__header) { border-bottom: 0; padding: 24px 24px 18px; }
:global(.campaign-plan-action.el-dialog .el-dialog__body) { padding: 0 24px 20px; max-height: calc(100dvh - 220px); }
:global(.campaign-plan-action.el-dialog .el-dialog__footer) { margin-top: 0; padding: 16px 24px; gap: 10px; }
:global(.campaign-plan-action.el-dialog .el-dialog__headerbtn) { top: 20px; right: 16px; }
@media (max-width: 520px) { .plan-confirm-footer-note { flex: auto; width: 100%; } }
@media (max-width: 420px) { .plan-pause-options { grid-template-columns: 1fr; } }
</style>
