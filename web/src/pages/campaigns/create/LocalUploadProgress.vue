<template>
  <section class="local-upload-progress" :class="{ 'video-upload-progress': type === 'VIDEO' }" :aria-label="`${kind}上传处理进度`" aria-live="polite">
    <div :class="{ 'video-batch-panel': type === 'VIDEO' }">
    <h3>{{ complete ? `${state.addedCount} ${unit}已添加` : state.processing || saving ? `正在处理 ${state.count} ${unit}` : state.stoppedCount ? `${kind}处理已停止` : `${kind}处理未完成` }}</h3>
    <p class="batch-subtitle">{{ complete ? '已完成检查与同步，可返回素材列表预览。' : type === 'VIDEO' ? '文件上传后，还需完成检查与同步。' : '处理流程：上传 → 检查 → 同步' }}</p>
    <ol v-if="type === 'VIDEO'" class="batch-steps" aria-label="处理步骤"><li v-for="(label, index) in ['上传', '检查', '同步']" :key="label" :class="{ complete: processingStep > index, active: processingStep === index && state.processing }"><span>{{ processingStep > index ? '✓' : index + 1 }}</span>{{ label }}</li></ol>
    <div class="upload-batch-list">
      <article v-for="row in rows" :key="row.key">
        <div class="upload-file-thumb"><video v-if="type === 'VIDEO'" :src="previews.get(row.key)" :aria-label="row.file.name" muted playsinline preload="metadata" @loadedmetadata="seekPoster" /><img v-else :src="previews.get(row.key)" :alt="row.file.name" /></div>
        <div class="upload-file-info"><strong :title="row.file.name">{{ row.file.name }}</strong><p>{{ fileSize(row.file.size) }}<template v-if="type === 'IMAGE' && row.metadata?.width && row.metadata?.height"> · {{ row.metadata.width }}×{{ row.metadata.height }}</template> · {{ (row.result?.isAigc ?? row.isAigc) ? '包含 AI 生成内容' : '非 AI 生成内容' }}</p></div>
        <div class="upload-file-state" :class="{ success: added(row) || ready(row), failed: !!row.error || phase(row).includes('未') || phase(row).includes('失败'), stopped: row.stopped }"><i v-if="added(row) || ready(row)" class="status-check" aria-hidden="true">✓</i><i v-else-if="!row.stopped && ['上传中', '检查中', '同步中'].includes(phase(row))" class="status-spinner" aria-hidden="true" /><div><strong>{{ added(row) ? '已添加' : saving && ready(row) ? '添加中' : phase(row) }}<template v-if="phase(row) === '上传中'"> {{ row.progress }}%</template></strong><p>{{ detail(row) }}</p><progress v-if="phase(row) === '上传中'" :value="row.progress" max="100" :aria-label="`${row.file.name}上传进度`" /></div></div>
        <div v-if="!added(row) && !row.stopped" class="upload-file-actions">
          <p v-if="row.error" class="error" role="alert">{{ row.error }}</p>
          <AssetReviewStatus v-if="row.result && ['FAILED', 'REJECTED', 'MANUAL_REVIEW'].includes(row.result.aiReview?.status || '')" :review="row.result.aiReview" :retrying="retrying === row.result.id" @retry="emit('retryReview', row)" />
          <button v-if="row.error && row.valid && !row.result" type="button" :disabled="disabled" @click="emit('retryUpload', row)">重试上传</button>
          <button v-if="!row.busy && (!row.valid || !!row.error)" type="button" :disabled="disabled" @click="emit('remove', row)">移除</button>
        </div>
      </article>
    </div>
    </div>
    <p v-if="complete" class="batch-note">新增{{ kind }}已加入当前商品，确认素材后再发布投放。</p><p v-else-if="state.processing && type === 'IMAGE'" class="batch-note">图片处理中，请勿重复提交。</p>
  </section>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import type { UploadRow } from '@/features/creative/assetUploads';
import type { AssetSync } from '@/features/creative/creativeApi';
import type { UploadBatchState } from '@/features/creative/localUploadBatch';
import { uploadedReady } from '@/features/creative/materialSources';
import { fileSize } from '@/pages/creatives/assetFormat';
import AssetReviewStatus from '@/pages/creatives/AssetReviewStatus.vue';
const props = defineProps<{ type: 'IMAGE' | 'VIDEO'; rows: UploadRow[]; state: UploadBatchState; accountId: string; addedIds: string[]; saving: boolean; retrying: string; disabled: boolean; phase: (row: UploadRow) => string }>();
const emit = defineEmits<{ retryReview: [row: UploadRow]; retrySync: [row: UploadRow, sync: AssetSync]; verifySync: [row: UploadRow, sync: AssetSync]; retryUpload: [row: UploadRow]; remove: [row: UploadRow] }>();
const previews = ref(new Map<string, string>());
watch(() => props.rows.map(row => row.key), () => {
  for (const [key, url] of previews.value) if (!props.rows.some(row => row.key === key)) { URL.revokeObjectURL(url); previews.value.delete(key); }
  for (const row of props.rows) if (!previews.value.has(row.key)) previews.value.set(row.key, URL.createObjectURL(row.file));
}, { immediate: true });
onUnmounted(() => previews.value.forEach(url => URL.revokeObjectURL(url)));
const kind = computed(() => props.type === 'VIDEO' ? '视频' : '图片');
const unit = computed(() => props.type === 'VIDEO' ? '个视频' : '张图片');
const processingStep = computed(() => Math.min(3, ...props.rows.map(row => {
  if (added(row) || ready(row)) return 3;
  if (!row.result) return /校验|合并/.test(row.stage) ? 1 : 0;
  return ['APPROVED', 'NOT_REQUIRED'].includes(row.result.aiReview?.status || '') ? 2 : 1;
})));
function seekPoster(event: Event) {
  const video = event.target as HTMLVideoElement;
  if (Number.isFinite(video.duration) && video.duration > 0) video.currentTime = Math.min(.1, video.duration / 2);
}
const complete = computed(() => props.state.count > 0 && props.state.addedCount === props.state.count);
const added = (row: UploadRow) => !!row.result && props.addedIds.includes(row.result.id);
const ready = (row: UploadRow) => !row.stopped && uploadedReady(row.result, props.accountId);
function detail(row: UploadRow) {
  if (row.stopped) return '未加入当前商品';
  if (added(row)) return '同步已完成';
  if (ready(row)) return '检查、同步已完成';
  if (props.phase(row).startsWith('同步')) return '检查已完成';
  if (props.phase(row) === '检查中') return row.result || row.progress === 100 ? '上传已完成' : `正在检查${kind.value}格式`;
  return '';
}
</script>
<style scoped>
.local-upload-progress{margin-top:20px;color:#22213c}.local-upload-progress h3{font-size:20px;font-weight:600;margin:0 0 9px}.batch-subtitle,.batch-note{font-size:14px;line-height:1.7;color:#8179aa;margin:0 0 18px}.batch-note{margin:16px 0 0}.upload-batch-list{display:grid;gap:12px}.upload-batch-list article{display:grid;grid-template-columns:86px minmax(0,1fr) 180px;gap:18px;align-items:center;padding:12px;border:1px solid #e3dfff;border-radius:8px;background:white}.upload-file-thumb{width:86px;height:86px;border:1px solid #e3dfff;border-radius:6px;overflow:hidden;background:#f7f6ff}.upload-file-thumb img,.upload-file-thumb video{display:block;width:100%;height:100%;object-fit:contain}.upload-file-info{min-width:0}.upload-file-info strong{display:block;font-size:16px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.upload-file-info p,.upload-file-state p{margin:5px 0 0;font-size:13px;line-height:1.6;color:#8b82b1;overflow-wrap:anywhere}.upload-file-state{display:flex;align-items:center;gap:16px;color:#5846ff}.upload-file-state strong{font-size:16px;font-weight:600}.upload-file-state>div{min-width:0;flex:1}.upload-file-state.success{color:#0bb76e}.upload-file-state.failed{color:#dc5966}.upload-file-state.stopped{color:#8b87a0}.status-check{display:grid;place-items:center;flex-shrink:0;width:26px;height:26px;border-radius:50%;background:#12ba75;color:white;font-style:normal}.status-spinner{flex-shrink:0;width:25px;height:25px;border:2px solid #e1dfff;border-top-color:#5846ff;border-radius:50%;animation:spin 1s linear infinite}.upload-file-actions{grid-column:2/-1;display:flex;flex-wrap:wrap;gap:8px;color:#dc5966;font-size:12px}.upload-file-actions:empty{display:none}.upload-file-actions button{color:#5846ff}.upload-file-actions .error{width:100%;margin:0}.sync-error{display:flex;gap:8px;flex-wrap:wrap}.upload-file-state progress{display:block;width:100%;height:6px;accent-color:#5846ff;margin-top:7px}button:disabled{opacity:.45;cursor:not-allowed}@keyframes spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.status-spinner{animation:none}}@media(max-width:639px){.local-upload-progress h3{font-size:17px}.upload-batch-list article{grid-template-columns:64px minmax(0,1fr);gap:12px}.upload-file-thumb{width:64px;height:64px}.upload-file-info strong{font-size:14px}.upload-file-info p{font-size:12px}.upload-file-state{grid-column:2;gap:10px}.upload-file-actions{grid-column:1/-1}.upload-file-state strong{font-size:14px}.batch-subtitle,.batch-note{font-size:12px}}
.video-batch-panel{padding:22px 10px 10px;border-radius:12px;background:linear-gradient(115deg,#f7f5ff,#f2f2ff)}
.video-upload-progress h3{text-align:center;font-size:24px}.video-upload-progress .batch-subtitle{text-align:center;margin-bottom:18px}
.video-upload-progress .upload-batch-list{gap:9px}.video-upload-progress .upload-batch-list article{grid-template-columns:64px minmax(0,1fr) 220px;gap:18px;padding:10px 14px;border-color:#efecff;border-radius:10px}
.video-upload-progress .upload-file-thumb{width:64px;height:64px}.video-upload-progress .upload-file-thumb video{height:100%;object-fit:cover}.video-upload-progress .upload-file-state{gap:14px}
.batch-steps{display:flex;justify-content:center;list-style:none;gap:112px;padding:0;margin:20px 0;color:#a5a1ca;font-size:14px}.batch-steps li{position:relative;display:flex;flex-direction:column;align-items:center;gap:7px;min-width:35px}.batch-steps li+li:before{position:absolute;content:'';top:13px;right:calc(50% + 13px);width:121px;height:1px;background:#d9d4f9}.batch-steps li span{display:grid;place-items:center;position:relative;z-index:1;width:27px;height:27px;border-radius:50%;color:white;background:#c6c0eb}.batch-steps li.complete,.batch-steps li.active{color:#30344e}.batch-steps li.complete span,.batch-steps li.complete:before{background:#0bb76e}.batch-steps li.active span{background:#5846ff}
@media(max-width:639px){.video-batch-panel{padding:18px 8px 8px}.video-upload-progress h3{font-size:20px}.video-upload-progress .upload-batch-list article{grid-template-columns:54px minmax(0,1fr);padding:10px;gap:10px}.video-upload-progress .upload-file-thumb{width:54px;height:54px}.batch-steps{gap:58px;font-size:12px}.batch-steps li+li:before{width:67px}}
</style>
