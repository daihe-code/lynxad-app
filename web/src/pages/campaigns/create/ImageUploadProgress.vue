<template>
  <section class="image-upload-progress" aria-label="图片上传处理进度" aria-live="polite">
    <h3>{{ complete ? `${state.addedCount} 张图片已添加` : state.processing || saving ? `正在处理 ${state.count} 张图片` : state.stoppedCount ? '图片处理已停止' : '图片处理未完成' }}</h3>
    <p class="batch-subtitle">{{ complete ? '已完成检查与同步，可返回素材列表预览。' : '处理流程：上传 → 检查 → 同步' }}</p>
    <div class="image-upload-list">
      <article v-for="row in rows" :key="row.key">
        <div class="image-file-thumb"><img :src="previews.get(row.key)" :alt="row.file.name" /></div>
        <div class="image-file-info"><strong :title="row.file.name">{{ row.file.name }}</strong><p>{{ fileSize(row.file.size) }}<template v-if="row.metadata?.width && row.metadata?.height"> · {{ row.metadata.width }}×{{ row.metadata.height }}</template> · {{ (row.result?.isAigc ?? row.isAigc) ? '包含 AI 生成内容' : '非 AI 生成内容' }}</p></div>
        <div class="image-file-state" :class="{ success: added(row) || ready(row), failed: !!row.error || phase(row).includes('未') || phase(row).includes('失败'), stopped: row.stopped }"><i v-if="added(row) || ready(row)" class="status-check" aria-hidden="true">✓</i><i v-else-if="!row.stopped && ['上传中', '检查中', '同步中'].includes(phase(row))" class="status-spinner" aria-hidden="true" /><div><strong>{{ added(row) ? '已添加' : saving && ready(row) ? '添加中' : phase(row) }}<template v-if="phase(row) === '上传中'"> {{ row.progress }}%</template></strong><p>{{ detail(row) }}</p><progress v-if="phase(row) === '上传中'" :value="row.progress" max="100" :aria-label="`${row.file.name}上传进度`" /></div></div>
        <div v-if="!added(row) && !row.stopped" class="image-file-actions">
          <p v-if="row.error" class="error" role="alert">{{ row.error }}</p>
          <AssetReviewStatus v-if="row.result && ['FAILED', 'REJECTED', 'MANUAL_REVIEW'].includes(row.result.aiReview?.status || '')" :review="row.result.aiReview" :retrying="retrying === row.result.id" @retry="emit('retryReview', row)" />
          <template v-if="row.result"><div v-for="sync in row.result.syncs.filter(s => s.accountId === accountId && (['FAILED', 'WAITING_AUTH', 'UNCERTAIN', 'PAUSED'].includes(s.status) || s.reviewStatus === 'REJECTED'))" :key="sync.id" class="sync-error"><span>{{ sync.errorMessage }}</span><AssetSyncAction :sync="sync" :busy="retrying === sync.id" :can-retry="['APPROVED','NOT_REQUIRED'].includes(row.result.aiReview?.status || '')" @retry="emit('retrySync', row, sync)" @verify="emit('verifySync', row, sync)" /></div></template>
          <button v-if="row.error && row.valid && !row.result" type="button" :disabled="disabled" @click="emit('retryUpload', row)">重试上传</button>
          <button v-if="!row.busy && (!row.valid || !!row.error)" type="button" :disabled="disabled" @click="emit('remove', row)">移除</button>
        </div>
      </article>
    </div>
    <p v-if="complete" class="batch-note">新增图片已加入当前商品，确认素材后再发布投放。</p><p v-else-if="state.processing" class="batch-note">图片处理中，请勿重复提交。</p>
  </section>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import type { UploadRow } from '@/features/creative/assetUploads';
import type { AssetSync } from '@/features/creative/creativeApi';
import type { ImageBatchState } from '@/features/creative/imageUploadBatch';
import { uploadedReady } from '@/features/creative/materialSources';
import { fileSize } from '@/pages/creatives/assetFormat';
import AssetReviewStatus from '@/pages/creatives/AssetReviewStatus.vue';
import AssetSyncAction from '@/pages/creatives/AssetSyncAction.vue';
const props = defineProps<{ rows: UploadRow[]; state: ImageBatchState; accountId: string; addedIds: string[]; saving: boolean; retrying: string; disabled: boolean; phase: (row: UploadRow) => string }>();
const emit = defineEmits<{ retryReview: [row: UploadRow]; retrySync: [row: UploadRow, sync: AssetSync]; verifySync: [row: UploadRow, sync: AssetSync]; retryUpload: [row: UploadRow]; remove: [row: UploadRow] }>();
const previews = ref(new Map<string, string>());
watch(() => props.rows.map(row => row.key), () => {
  for (const [key, url] of previews.value) if (!props.rows.some(row => row.key === key)) { URL.revokeObjectURL(url); previews.value.delete(key); }
  for (const row of props.rows) if (!previews.value.has(row.key)) previews.value.set(row.key, URL.createObjectURL(row.file));
}, { immediate: true });
onUnmounted(() => previews.value.forEach(url => URL.revokeObjectURL(url)));
const complete = computed(() => props.state.count > 0 && props.state.addedCount === props.state.count);
const added = (row: UploadRow) => !!row.result && props.addedIds.includes(row.result.id);
const ready = (row: UploadRow) => !row.stopped && uploadedReady(row.result, props.accountId);
function detail(row: UploadRow) {
  if (row.stopped) return '未加入当前商品';
  if (added(row)) return '同步已完成';
  if (ready(row)) return '检查、同步已完成';
  if (props.phase(row).startsWith('同步')) return '检查已完成';
  if (props.phase(row) === '检查中') return row.result || row.progress === 100 ? '上传已完成' : '正在检查图片格式';
  return '';
}
</script>
<style scoped>
.image-upload-progress{margin-top:20px;color:#22213c}.image-upload-progress h3{font-size:20px;font-weight:600;margin:0 0 9px}.batch-subtitle,.batch-note{font-size:14px;line-height:1.7;color:#8179aa;margin:0 0 18px}.batch-note{margin:16px 0 0}.image-upload-list{display:grid;gap:12px}.image-upload-list article{display:grid;grid-template-columns:86px minmax(0,1fr) 180px;gap:18px;align-items:center;padding:12px;border:1px solid #e3dfff;border-radius:8px;background:white}.image-file-thumb{width:86px;height:86px;border:1px solid #e3dfff;border-radius:6px;overflow:hidden;background:#f7f6ff}.image-file-thumb img{display:block;width:100%;height:100%;object-fit:contain}.image-file-info{min-width:0}.image-file-info strong{display:block;font-size:16px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.image-file-info p,.image-file-state p{margin:5px 0 0;font-size:13px;line-height:1.6;color:#8b82b1;overflow-wrap:anywhere}.image-file-state{display:flex;align-items:center;gap:16px;color:#5846ff}.image-file-state strong{font-size:16px;font-weight:600}.image-file-state>div{min-width:0;flex:1}.image-file-state.success{color:#0bb76e}.image-file-state.failed{color:#dc5966}.image-file-state.stopped{color:#8b87a0}.status-check{display:grid;place-items:center;flex-shrink:0;width:26px;height:26px;border-radius:50%;background:#12ba75;color:white;font-style:normal}.status-spinner{flex-shrink:0;width:25px;height:25px;border:2px solid #e1dfff;border-top-color:#5846ff;border-radius:50%;animation:spin 1s linear infinite}.image-file-actions{grid-column:2/-1;display:flex;flex-wrap:wrap;gap:8px;color:#dc5966;font-size:12px}.image-file-actions:empty{display:none}.image-file-actions button{color:#5846ff}.image-file-actions .error{width:100%;margin:0}.sync-error{display:flex;gap:8px;flex-wrap:wrap}.image-file-state progress{display:block;width:100%;height:6px;accent-color:#5846ff;margin-top:7px}button:disabled{opacity:.45;cursor:not-allowed}@keyframes spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.status-spinner{animation:none}}@media(max-width:639px){.image-upload-progress h3{font-size:17px}.image-upload-list article{grid-template-columns:64px minmax(0,1fr);gap:12px}.image-file-thumb{width:64px;height:64px}.image-file-info strong{font-size:14px}.image-file-info p{font-size:12px}.image-file-state{grid-column:2;gap:10px}.image-file-actions{grid-column:1/-1}.image-file-state strong{font-size:14px}.batch-subtitle,.batch-note{font-size:12px}}
</style>
