<template>
  <ReferenceModal class="creation-action-dialog" :class="{ 'creation-action-dialog--cancel': action === 'cancel' }" :title="title" :width="action === 'cancel' ? 720 : 800" :busy="busy" :show-close="true" @close="emit('close')">
    <template v-if="action === 'cancel'">
      <p class="cancel-question">取消后是否保存当前投放计划？</p>
      <p class="cancel-hint">若保存投放计划，可至“广告投放”功能列表查看</p>
    </template>
    <template v-else>
      <p class="operation-warning">{{ action === 'regenerate' ? published ? '重新生成会调用模型。原素材继续投放，新素材生成、质检和同步完成后，将自动替换计划中的原素材。' : '重新生成会调用模型。原素材会保留，待新素材生成、质检和同步完成后再替换。' : published ? '确认后将从当前及关联投放计划中移除此素材，素材中心文件仍保留。请确认是否继续？' : '素材删除后将无法恢复，建议慎重操作。请确认是否继续？' }}</p>
      <div class="feedback-row">
        <label for="material-feedback-reasons">{{ action === 'regenerate' ? '重新生成原因' : '删除素材原因' }}</label>
        <el-select id="material-feedback-reasons" v-model="reasons" multiple :teleported="false" :disabled="busy" placeholder="请如实选择原因，新生成素材将质量更高，可多选" aria-label="素材操作原因" @keydown.esc.stop="() => {}">
          <el-option v-for="reason in reasonOptions" :key="reason" :label="reason" :value="reason" />
        </el-select>
      </div>
      <p v-if="action === 'regenerate' && feedbackError" class="feedback-error" role="status">{{ feedbackError }}</p>
      <div class="feedback-row feedback-row--description">
        <label for="material-feedback-description">{{ action === 'regenerate' ? '预期描述' : '补充说明' }}</label>
        <div class="feedback-description">
          <textarea id="material-feedback-description" v-model="description" maxlength="50" :disabled="busy" :placeholder="action === 'regenerate' ? '可简单描述对新生成素材的预期，如新素材生成需要增加文案趣味性' : '可描述删除素材的详细原因，便于灵投后续能力更优以生成更好素材'" />
          <span class="feedback-count">{{ description.length }}/50</span>
        </div>
      </div>
      <p class="operation-note"><span aria-hidden="true">i</span>{{ action === 'regenerate' ? '提交后可离开页面，后台会继续处理；结果待确认时不会自动重复生成。' : published ? '确认后立即同步千川，每款商品至少保留一条投放素材。' : '提交后系统将删除所选素材，且不可恢复' }}</p>
      <p v-if="action === 'regenerate' && waitSeconds" class="feedback-error" role="status">重新生成操作间隔为30秒，请在 {{ waitSeconds }} 秒后再试。</p>
    </template>
    <template #footer>
      <template v-if="action === 'cancel'">
        <button class="dialog-primary dialog-primary--save" type="button" :disabled="busy" @click="emit('save')">{{ busy ? '处理中…' : '保存' }}</button>
        <button class="dialog-secondary" type="button" :disabled="busy" @click="emit('discard')">不保存</button>
      </template>
      <template v-else>
        <button class="dialog-secondary" type="button" :disabled="busy" @click="emit('close')">取消</button>
        <button class="dialog-primary" type="button" :disabled="busy || (action === 'regenerate' && (!!feedbackError || waitSeconds > 0))" @click="submitFeedback">{{ busy ? '处理中…' : '确定' }}</button>
      </template>
    </template>
  </ReferenceModal>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import ReferenceModal from '../reference/ReferenceModal.vue';
import type { MaterialFeedback } from '@/features/creative/creativeApi';
import { regenerationFeedbackError, regenerationWait } from '@/features/delivery/regenerationPolicy';
const props = defineProps<{ action: 'cancel' | 'regenerate' | 'delete'; busy: boolean; retryAt?: number; published?: boolean }>();
const emit = defineEmits<{ close: []; save: []; discard: []; confirm: [feedback: MaterialFeedback] }>();
const title = computed(() => props.action === 'cancel' ? '取消投放提醒' : props.action === 'regenerate' ? '重新生成素材提醒' : '删除素材提醒');
const reasons = ref<string[]>([]), description = ref('');
const now = ref(Date.now()), timer = setInterval(() => { now.value = Date.now(); }, 1000);
onUnmounted(() => clearInterval(timer));
watch(() => props.retryAt, () => { now.value = Date.now(); }, { flush: 'sync' });
const feedbackError = computed(() => regenerationFeedbackError(reasons.value, description.value));
const waitSeconds = computed(() => regenerationWait(props.retryAt, now.value));
function submitFeedback() {
  if (props.busy || (props.action === 'regenerate' && (feedbackError.value || waitSeconds.value))) return;
  emit('confirm', { reasons: [...reasons.value], description: description.value.trim() });
}
const reasonOptions = ['画面质量不佳', '文案不符合预期', '商品展示不清晰', '内容与商品不匹配', '素材重复', '其他'];
</script>
<style scoped>
.creation-action-dialog :deep(.reference-modal__mask) { background: #0006; backdrop-filter: none; }
.creation-action-dialog :deep(.reference-modal__box) { box-sizing: border-box; padding: 24px 36px; border-radius: 20px; color: #222; box-shadow: 0 3px 8px #0004; font-family: inherit; }
.creation-action-dialog :deep(h2) { font-size: 18px; font-weight: 600; }
.creation-action-dialog :deep(header button) { color: #888; }
.creation-action-dialog :deep(.reference-modal__body) { margin: 60px 0 76px; }
.creation-action-dialog :deep(footer) { gap: 14px; }
.cancel-question { margin: 0 0 16px; font-size: 18px; color: #111; }
.cancel-hint { margin: 0; color: #707070; font-size: 17px; }
.creation-action-dialog--cancel :deep(h2) { font-size: 22px; font-weight: 500; }
.creation-action-dialog--cancel :deep(.reference-modal__body) { padding: 0 20px; margin: 60px 0 64px; }
.operation-warning { margin: 0 0 26px; font-size: 16px; line-height: 1.6; }
.feedback-row { display: grid; grid-template-columns: 112px minmax(0, 1fr); align-items: center; gap: 10px; margin-top: 14px; font-size: 16px; }
.feedback-row--description { align-items: start; }
.feedback-row--description label { padding-top: 6px; }
.feedback-row :deep(.el-select__wrapper) { min-height: 42px; border-radius: 6px; box-shadow: 0 0 0 1px #bfbfbf inset; font-size: 14px; }
.feedback-row :deep(.el-select__placeholder) { pointer-events: none; }
.feedback-description { position: relative; }
.feedback-description textarea { box-sizing: border-box; display: block; width: 100%; height: 58px; resize: vertical; padding: 6px 46px 6px 8px; border: 1px solid #bfbfbf; border-radius: 6px; font: inherit; font-size: 14px; outline-color: #575cf6; }
.feedback-description textarea::placeholder { color: #999; }
.feedback-count { position: absolute; top: 6px; right: 8px; font-size: 14px; color: #999; pointer-events: none; }
.operation-note { margin: 16px 0 0 122px; display: flex; align-items: flex-start; gap: 8px; font-size: 12px; line-height: 20px; color: #666; }
.operation-note span { display: inline-flex; flex-shrink: 0; justify-content: center; align-items: center; width: 16px; height: 16px; margin-top: 2px; border-radius: 50%; background: #e7a24b; color: white; font-weight: 700; font-style: normal; }
.feedback-error { margin:8px 0 0 122px; color:#b56b19; font-size:12px; line-height:1.6; overflow-wrap:anywhere; }
.dialog-primary, .dialog-secondary { display: inline-flex; align-items: center; justify-content: center; min-width: 76px; height: 32px; border-radius: 6px; padding: 0 20px; font: inherit; font-size: 13px; cursor: pointer; }
.dialog-primary { color: white; background: #2855e6; border: 1px solid transparent; font-weight: 600; }
.dialog-secondary { color: #555; background: white; border: 1px solid #bfbfbf; }
.creation-action-dialog--cancel .dialog-primary, .creation-action-dialog--cancel .dialog-secondary { min-width: 106px; height: 52px; font-size: 18px; border-radius: 9px; }
.dialog-primary--save { background: #575cf6; }
button:disabled { opacity: .6; cursor: not-allowed; }
@media (max-width: 639px) {
  .creation-action-dialog :deep(.reference-modal__box) { padding: 22px; }
  .creation-action-dialog :deep(.reference-modal__body) { margin: 32px 0; padding: 0; }
  .feedback-row { grid-template-columns: 1fr; gap: 8px; }
  .operation-note { margin-left: 0; }
  .feedback-error { margin-left:0; }
  .cancel-question, .cancel-hint { font-size: 16px; line-height: 1.6; }
  .creation-action-dialog--cancel .dialog-primary, .creation-action-dialog--cancel .dialog-secondary { height: 42px; min-width: 90px; font-size: 16px; }
}
</style>
