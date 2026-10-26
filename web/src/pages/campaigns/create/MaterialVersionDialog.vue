<template>
  <ReferenceModal class="material-version-dialog" :class="{ 'material-version-dialog--ready': ready, 'material-version-dialog--failed': failed }" :title="title" :width="600" :busy="busy" @close="emit('close')">
    <p class="version-subtitle">{{ ready ? '请查看新版本，并选择如何处理原素材。' : failed ? '原素材仍保留，可以关闭弹窗查看原因后重试。' : '新版本完成前，原素材保持不变。' }}</p>
    <template v-if="ready && version">
      <div class="version-comparison"><MaterialSummary :material="source" label="原素材" preview /><MaterialSummary :material="version.material" label="新版本 · 就绪" preview /></div>
      <fieldset class="version-options" :disabled="busy || applying"><legend>选择处理方式</legend>
        <label :class="{ selected: decision === 'REPLACE' }"><input v-model="decision" type="radio" value="REPLACE" name="material-version-decision" /><span><strong>替换原素材</strong><small>用新版本替换当前计划中的原素材，素材数量不变。</small></span></label>
        <label :class="{ selected: decision === 'KEEP_BOTH', unavailable: materialCount >= 100 }"><input v-model="decision" type="radio" value="KEEP_BOTH" name="material-version-decision" :disabled="materialCount >= 100" /><span><strong>两者都保留</strong><small>{{ materialCount >= 100 ? '素材数量已达100条，可选择替换原素材。' : '原素材保留，新版本作为一条新素材加入当前计划。' }}</small></span></label>
      </fieldset>
      <p class="version-note">{{ applying ? '已确认处理方式，正在核查投放计划中的保存结果，请勿重复提交。' : '确认前不会替换原素材；新版本已保留，可稍后处理。' }}</p>
      <p class="version-count">{{ decision === 'KEEP_BOTH' ? `保留后，计划素材由 ${materialCount} 条增加至 ${materialCount + 1} 条。` : '替换后，计划素材数量不变。' }}</p>
    </template>
    <template v-else>
      <MaterialSummary :material="source" />
      <div class="version-progress" role="status"><span v-if="!failed" class="version-spinner" aria-hidden="true" /><strong>{{ failed ? '本次重新生成未完成' : '正在重新生成素材' }}</strong><p>{{ failed ? version?.material.error || message || '请查看失败原因，原素材仍可使用。' : '可以关闭弹窗继续操作，后台会继续生成；完成后可在原素材处查看新版本。' }}</p></div>
    </template>
    <button v-if="version?.material.processing?.canRetrySync && !ready" class="version-secondary" :disabled="busy" @click="emit('retry', version.material.id)">重试失败组件（不重新生成）</button>
    <button v-if="failed && version" class="version-secondary" :disabled="busy" @click="emit('confirm', version.material.id, 'DISCARD')">保留原素材，放弃此失败版本</button>
    <slot />
    <template #footer><button class="version-secondary" :disabled="busy" @click="emit('close')">{{ ready ? '稍后处理' : '关闭' }}</button><button v-if="ready && version" class="version-primary" :disabled="busy || applying" @click="emit('confirm', version.material.id, decision)">{{ busy ? '正在保存…' : applying ? '正在核查结果…' : decision === 'REPLACE' ? '确认替换' : '确认保留两者' }}</button></template>
  </ReferenceModal>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import ReferenceModal from '../reference/ReferenceModal.vue';
import MaterialSummary from './MaterialSummary.vue';
import type { CreationMaterial, CreationState } from '@/features/delivery/creationApi';
const props = defineProps<{ source: CreationMaterial; version?: NonNullable<CreationState['materialVersions']>[number]; busy: boolean; materialCount: number; failed?: boolean; message?: string }>();
const emit = defineEmits<{ close: []; retry: [id: string]; confirm: [id: string, decision: 'REPLACE' | 'KEEP_BOTH' | 'DISCARD'] }>();
const decision = ref<'REPLACE' | 'KEEP_BOTH' | 'DISCARD'>('REPLACE');
const ready = computed(() => props.version?.material.status === 'READY');
const applying = computed(() => props.version?.decision?.startsWith('APPLYING_'));
const title = computed(() => ready.value ? '新素材已生成' : props.failed ? '本次重新生成未完成' : '正在重新生成素材');
</script>
<style scoped>
.material-version-dialog :deep(.reference-modal__mask){backdrop-filter:none}.material-version-dialog :deep(.reference-modal__box){display:flex;flex-direction:column;overflow:hidden}.material-version-dialog :deep(header),.material-version-dialog :deep(footer){flex-shrink:0}.material-version-dialog :deep(.reference-modal__body){margin:0 0 16px;overflow-y:auto;min-height:0;padding-right:4px}.material-version-dialog :deep(h2):before{content:'↻';display:inline-grid;place-items:center;width:30px;height:30px;border-radius:50%;background:#eee9ff;color:#7051ed;margin-right:10px}.material-version-dialog--ready :deep(h2):before{content:'✓';background:#e8f8ed;color:#26a94d}.material-version-dialog--failed :deep(h2):before{content:'!';background:#fff0d7;color:#d99628}.version-subtitle{margin:6px 0 18px 40px;color:#858096;font-size:12px;line-height:1.7}.version-comparison{display:grid;grid-template-columns:1fr 1fr;gap:12px}.version-options{border:0;margin:18px 0 0;padding:0}.version-options legend{font-size:13px;font-weight:600;margin-bottom:10px}.version-options label{display:flex;gap:12px;align-items:flex-start;padding:12px;border:1px solid #e2dfea;border-radius:8px;margin-bottom:8px;cursor:pointer}.version-options label.selected{background:#f3efff;border-color:#8261ed}.version-options input{margin:3px 0 0;accent-color:#7051e9;width:17px;height:17px;flex-shrink:0}.version-options strong{font-size:13px;color:#302a49}.version-options small{display:block;margin-top:5px;font-size:12px;line-height:1.6;color:#878197}.version-options label.unavailable{opacity:.55;cursor:not-allowed}.version-note{padding:10px 12px;margin:12px 0;color:#7152d8;background:#f3efff;border-radius:7px;font-size:12px;line-height:1.7}.version-count{font-size:11px;line-height:1.6;color:#9690a2;margin:0}.version-primary,.version-secondary{min-height:36px;border-radius:7px;padding:0 18px;font:inherit;font-size:13px;cursor:pointer}.version-primary{background:#6950e8;border:1px solid #6950e8;color:white}.version-secondary{background:white;border:1px solid #dcd6ec;color:#6b51c7}.version-progress{padding:32px 12px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:12px}.version-progress strong{font-size:15px}.version-progress p{margin:0;line-height:1.8;font-size:12px;color:#888197}.version-spinner{width:32px;height:32px;border:3px solid #ece5ff;border-top-color:#7854ef;border-radius:50%;animation:version-spin 1s linear infinite}button:disabled{opacity:.55;cursor:not-allowed}@keyframes version-spin{to{transform:rotate(360deg)}}@media(max-width:500px){.version-subtitle{margin-left:0}.version-comparison{gap:8px}.material-version-dialog :deep(.reference-modal__box){padding:18px}.material-version-dialog :deep(.material-summary--preview>video),.material-version-dialog :deep(.material-summary--preview>img){height:100px}}
</style>
