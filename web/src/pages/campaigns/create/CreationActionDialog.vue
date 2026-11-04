<template>
  <ReferenceModal class="creation-action-dialog" :class="{ 'creation-action-dialog--cancel': action === 'cancel', 'creation-action-dialog--delete': action === 'delete' }" :title="title" :width="action === 'cancel' ? 520 : 560" :busy="busy" @close="emit('close')">
    <template v-if="action === 'cancel'">
      <p class="cancel-intro">离开前，你可以保留当前计划，也可以直接删除。</p>
      <div class="cancel-plan"><span class="cancel-plan__label">当前计划</span><strong :title="planName">{{ planName || '未命名计划' }}</strong><span class="cancel-plan__status">草稿</span></div>
      <p class="cancel-description">保留：保存当前编辑内容，之后可从计划列表继续编辑。</p>
      <p class="cancel-description">删除：删除当前草稿，<span class="cancel-danger">删除后无法恢复。</span></p>
      <p class="cancel-note">保留草稿不会发布广告。</p>
    </template>
    <template v-else>
      <p class="dialog-subtitle">{{ action === 'regenerate' ? `根据当前商品信息和你的反馈，重新生成1条${typeLabel}。` : '请核对要删除的素材，避免误删。' }}</p>
      <MaterialSummary v-if="material" :material="material" />
      <p v-if="action === 'delete'" class="operation-note operation-note--warning"><span aria-hidden="true">!</span>{{ published ? '将从当前及关联投放计划中移除，素材中心文件仍保留；其他素材不受影响。' : '删除后无法恢复，其他素材不受影响。' }}</p>
      <fieldset class="feedback-reasons" :disabled="busy"><legend>{{ action === 'regenerate' ? '重新生成原因（可多选）' : '删除原因（选填）' }}<small v-if="action === 'delete'">仅用于问题反馈，不影响删除。</small></legend>
        <div :class="action === 'delete' ? 'reason-chips' : 'reason-grid'"><label v-for="reason in reasonOptions" :key="reason" :class="{ checked: reasons.includes(reason) }"><input v-model="reasons" type="checkbox" :value="reason" /><span>{{ reason }}</span></label></div>
      </fieldset>
      <p v-if="action === 'regenerate' && reasons.length && feedbackError" class="feedback-error" role="status">{{ feedbackError }}</p>
      <div v-if="action === 'regenerate'" class="feedback-description"><label for="material-feedback-description">预期描述（选填）</label><textarea id="material-feedback-description" v-model="description" :disabled="busy" placeholder="例如：增加商品细节展示，文案更简洁，突出适用场景。" @input="description = [...description].slice(0, 50).join('')" /><span class="feedback-count">{{ [...description].length }}/50</span></div>
      <p v-if="action === 'regenerate'" class="operation-note"><span aria-hidden="true">i</span>原素材会保留，新版本生成成功后由你确认处理方式。</p>
      <p v-if="action === 'regenerate' && waitSeconds" class="feedback-error" role="status">请在 {{ waitSeconds }} 秒后再次生成。</p>
    </template>
    <p v-if="error" class="feedback-error" role="alert">{{ error }}</p>
    <template #footer>
      <template v-if="action === 'cancel'"><button class="dialog-continue" :disabled="busy" @click="emit('close')">继续编辑</button><button class="dialog-delete" :disabled="busy" @click="emit('discard')">删除计划并返回</button><button class="dialog-primary" :disabled="busy" @click="emit('save')">保留计划并返回</button></template>
      <template v-else><button class="dialog-secondary" :disabled="busy" @click="emit('close')">取消</button><button class="dialog-primary" :disabled="busy || action === 'regenerate' && (!!feedbackError || waitSeconds > 0)" @click="submitFeedback">{{ busy ? '处理中…' : action === 'regenerate' ? '确认生成' : '确认删除' }}</button></template>
    </template>
  </ReferenceModal>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import ReferenceModal from '../reference/ReferenceModal.vue';
import MaterialSummary from './MaterialSummary.vue';
import type { CreationMaterial } from '@/features/delivery/creationApi';
import type { MaterialFeedback } from '@/features/creative/creativeApi';
import { regenerationFeedbackError, regenerationWait } from '@/features/delivery/regenerationPolicy';
const props = defineProps<{ action: 'cancel' | 'regenerate' | 'delete'; busy: boolean; retryAt?: number; published?: boolean; material?: CreationMaterial; error?: string; planName?: string }>();
const emit = defineEmits<{ close: []; save: []; discard: []; confirm: [feedback: MaterialFeedback] }>();
const typeLabel = computed(() => props.material?.type === 'VIDEO' ? '视频' : props.material?.type === 'IMAGE' ? '图片' : props.material?.type === 'CAROUSEL' ? '图文' : '素材');
const title = computed(() => props.action === 'cancel' ? '返回计划列表' : props.action === 'regenerate' ? '重新生成素材' : `删除这条${typeLabel.value}素材？`);
const reasons = ref<string[]>([]), description = ref('');
const now = ref(Date.now()), timer = setInterval(() => { now.value = Date.now(); }, 1000);
onUnmounted(() => clearInterval(timer));
watch(() => props.retryAt, () => { now.value = Date.now(); }, { flush: 'sync' });
watch([() => props.action, () => props.material?.id], () => { reasons.value = []; description.value = ''; });
const feedbackError = computed(() => regenerationFeedbackError(reasons.value, description.value));
const waitSeconds = computed(() => regenerationWait(props.retryAt, now.value));
const reasonOptions = computed(() => props.action === 'delete' ? ['不符合商品', '画面不满意', '内容重复', '其他'] : ['画面质量不佳', '文案不符合预期', '商品展示不清晰', '内容与商品不匹配', '素材重复', '其他']);
function submitFeedback() { if (props.busy || props.action === 'regenerate' && (feedbackError.value || waitSeconds.value)) return; emit('confirm', { reasons: [...reasons.value], description: description.value.trim() }); }
</script>
<style scoped>
.creation-action-dialog :deep(.reference-modal__mask){backdrop-filter:none}.creation-action-dialog :deep(.reference-modal__box){padding:24px;border-radius:16px}.creation-action-dialog :deep(.reference-modal__body){margin:0 0 22px}.creation-action-dialog :deep(h2){font-size:19px;line-height:1.6}.creation-action-dialog :deep(h2):before{content:'✦';display:inline-grid;place-items:center;width:32px;height:32px;border-radius:50%;background:#eee9ff;color:#7051ed;margin-right:10px}.creation-action-dialog--delete :deep(h2):before{content:'!';background:#fff0d7;color:#dc951f}.dialog-subtitle{margin:4px 0 20px 42px;font-size:12px;color:#828195;line-height:1.7}.feedback-reasons{border:0;padding:0;margin:22px 0}.feedback-reasons legend,.feedback-description>label{font-size:13px;font-weight:600;color:#2b293f;margin-bottom:12px}.feedback-reasons legend small{font-weight:400;font-size:11px;color:#918da0;margin-left:12px}.reason-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px 10px}.reason-grid label{display:flex;align-items:center;gap:7px;font-size:12px;line-height:1.5;cursor:pointer}.reason-grid input{accent-color:#6650ee;width:16px;height:16px;flex-shrink:0;margin:0}.reason-chips{display:flex;gap:10px;flex-wrap:wrap}.reason-chips label{position:relative;border:1px solid #e1dfeb;border-radius:7px;padding:8px 12px;font-size:12px;cursor:pointer}.reason-chips label.checked{background:#f2efff;border-color:#7557f2;color:#6245dd}.reason-chips input{position:absolute;opacity:0;width:1px;height:1px}.reason-chips label:focus-within{outline:2px solid #7557f2;outline-offset:2px}.feedback-description{position:relative}.feedback-description>label{display:block}.feedback-description textarea{display:block;box-sizing:border-box;width:100%;min-height:92px;resize:vertical;padding:12px 12px 26px;border:1px solid #e1dfeb;border-radius:8px;font:inherit;font-size:12px;line-height:1.7;outline-color:#7557f2}.feedback-description textarea::placeholder{color:#a19eae}.feedback-count{position:absolute;bottom:8px;right:12px;font-size:11px;color:#9691a6}.operation-note{display:flex;gap:10px;align-items:flex-start;background:#f1edff;color:#6851d7;padding:10px 12px;border-radius:7px;font-size:12px;line-height:1.7;margin:16px 0 0}.operation-note span{border:1px solid currentColor;border-radius:50%;width:15px;height:15px;display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;line-height:1;margin-top:2px}.operation-note--warning{background:#fff6e9;color:#b57c23}.feedback-error{font-size:12px;color:#b66b20;line-height:1.6}.dialog-primary,.dialog-secondary{min-height:36px;padding:0 18px;border-radius:7px;font:inherit;font-size:13px;cursor:pointer}.dialog-primary{background:#6650ee;color:#fff;border:1px solid #6650ee;font-weight:600}.creation-action-dialog--delete .dialog-primary{background:#dd3552;border-color:#dd3552}.dialog-secondary{background:white;border:1px solid #dfdce8;color:#49445c}.cancel-question{margin:34px 0 14px;font-size:18px}.cancel-hint{color:#817b91;font-size:15px;margin-bottom:40px}.creation-action-dialog--cancel :deep(h2):before{display:none}button:disabled{opacity:.5;cursor:not-allowed}@media(max-width:600px){.reason-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.dialog-subtitle{margin-left:0}.feedback-reasons legend small{display:block;margin:6px 0 0}.creation-action-dialog :deep(.reference-modal__box){padding:20px}}
.creation-action-dialog--cancel :deep(.reference-modal__box){box-sizing:border-box}
.creation-action-dialog--cancel :deep(.reference-modal__body){margin:12px 0 16px}
.creation-action-dialog--cancel :deep(footer){align-items:center;border-top:1px solid #ecebf4;padding-top:16px;gap:12px}
.cancel-intro,.cancel-description{font-size:14px;line-height:1.7;color:#29273e}
.cancel-intro{margin:0 0 12px}
.cancel-plan{display:flex;align-items:center;gap:10px;padding:9px 12px;margin:0 0 12px;border-radius:10px;background:#f4f3f9}
.cancel-plan__label,.cancel-plan__status{flex:none;padding:3px 10px;border-radius:16px;font-size:11px;line-height:1.5;white-space:nowrap}
.cancel-plan__label{background:#e9e8f2;color:#77738d}
.cancel-plan strong{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;font-weight:600}
.cancel-plan__status{background:#ece8ff;color:#7b60e8}
.cancel-description{margin:6px 0}
.cancel-danger{color:#f44336}
.cancel-note{margin:8px 0 0;font-size:12px;line-height:1.6;color:#858099}
.dialog-continue,.dialog-delete{min-height:40px;border-radius:8px;font:inherit;font-size:13px;white-space:nowrap;cursor:pointer}
.dialog-continue{margin-right:auto;padding:0;border:0;background:transparent;color:#29273e;font-weight:600}
.dialog-delete{padding:0 16px;border:1px solid #ff4d4f;background:#fff;color:#f44336}
.creation-action-dialog--cancel .dialog-primary{min-height:40px;padding:0 16px;white-space:nowrap}
.dialog-continue:hover:not(:disabled){color:#6650ee}
.dialog-delete:hover:not(:disabled){background:#fff4f3}
.creation-action-dialog--cancel button:focus-visible{outline:2px solid #6650ee;outline-offset:3px}
@media(max-width:520px){.creation-action-dialog--cancel :deep(footer){display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 8px}.dialog-continue{grid-column:1/-1;justify-self:start}.dialog-delete,.creation-action-dialog--cancel .dialog-primary{padding:0 8px}.cancel-plan{gap:6px;padding:9px 8px}.cancel-plan__label,.cancel-plan__status{padding:3px 7px}}
</style>
