<template>
  <div class="local-material-upload">
    <p v-if="type !== 'IMAGE'" class="account-note">素材将同步到当前计划的投放账户。</p>
    <template v-if="type !== 'CAROUSEL'">
      <label class="aigc upload-aigc"><input v-model="aigc" type="checkbox" :disabled="blocked || busy" />包含 AI 生成内容</label>
      <input ref="input" class="sr-only" type="file" multiple :accept="policy.extensions.map(e => `.${e}`).join(',')" aria-label="选择本地素材文件" :disabled="blocked || busy" @change="changed" />
      <div v-if="!rows.length" class="drop-zone" :class="{ 'drop-zone--image': type === 'IMAGE' }" role="button" tabindex="0" :aria-label="type === 'IMAGE' ? '选择图片' : undefined" :aria-disabled="blocked || busy" @click="chooseFiles" @keydown.enter="chooseFiles" @keydown.space.prevent="chooseFiles" @dragover.prevent @drop.prevent="add($event.dataTransfer?.files)">
        <ReferenceIcon :name="type === 'IMAGE' ? 'cloud-upload' : 'upload'" />
        <template v-if="type === 'IMAGE'">
          <strong>点击选择图片，或将文件拖到此处</strong>
          <small class="image-formats">支持 JPG / JPEG / PNG，单张不超过 10M</small>
          <span class="image-select-button" aria-hidden="true">选择图片</span>
        </template>
        <template v-else><strong>点击选择文件，或拖拽至此处</strong><small v-if="policy.requirements.length" class="upload-requirements"><span v-for="requirement in policy.requirements" :key="requirement">{{ requirement }}</span></small><small v-else>{{ policy.label }}：{{ policy.hint }}</small></template>
      </div>
      <template v-if="type === 'IMAGE' && !rows.length">
        <details class="image-requirements"><summary>查看图片要求</summary><p v-for="requirement in policy.requirements" :key="requirement">{{ requirement }}</p></details>
        <p class="account-note">上传后将进行检查和同步，完成后可添加至当前商品。</p>
      </template>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <section v-if="rows.length" class="upload-processing" aria-label="素材上传处理进度">
        <div class="upload-summary"><strong>已就绪 {{ readyCount }} / {{ rows.length }} {{ type === 'VIDEO' ? '个视频' : '张图片' }}</strong><ol aria-label="处理步骤"><li v-for="(label, index) in ['上传', '检查', '同步']" :key="label" :class="{ complete: processingStep > index, active: processingStep === index }"><span>{{ processingStep > index ? '✓' : index + 1 }}</span>{{ label }}</li></ol></div>
        <div class="upload-list" aria-live="polite">
        <article v-for="row in rows" :key="row.key" :class="{ selected: selectedId === row.result?.id, ready: uploadedReady(row.result, accountId) }">
          <div class="file-line"><strong :title="row.file.name">{{ row.file.name }}</strong><span :class="{ 'phase-working': isWorking(row), 'phase-error': !!row.error || phase(row).includes('未') }"><i v-if="isWorking(row)" class="phase-spinner" aria-hidden="true" /><i v-else-if="uploadedReady(row.result, accountId)" class="phase-check" aria-hidden="true">✓</i>{{ phase(row) }}<b v-if="phase(row) === '上传中'">{{ progress(row) }}%</b></span></div>
          <small>{{ fileSize(row.file.size) }}<template v-if="row.metadata?.width && row.metadata?.height"> · {{ row.metadata.width }} × {{ row.metadata.height }}</template><template v-if="row.metadata?.duration"> · {{ row.metadata.duration.toFixed(2) }} 秒</template></small>
          <small>{{ (row.result?.isAigc ?? row.isAigc ?? aigc) ? '包含 AI 生成内容' : '非 AI 生成内容' }}</small>
          <progress :value="progress(row)" max="100" :aria-label="`${row.file.name}上传进度`" />
          <p v-if="row.error" class="error">{{ row.error }}</p>
          <template v-if="row.result && !uploadedReady(row.result, accountId)">
            <AssetReviewStatus v-if="['FAILED', 'REJECTED', 'MANUAL_REVIEW'].includes(row.result.aiReview?.status || '')" :review="row.result.aiReview" :retrying="retrying === row.result.id" @retry="retryReview(row)" />
            <div v-for="sync in row.result.syncs.filter(s => s.accountId === accountId && (['FAILED', 'WAITING_AUTH', 'UNCERTAIN', 'PAUSED'].includes(s.status) || s.reviewStatus === 'REJECTED'))" :key="sync.id" class="sync-line">
              <span>{{ syncLabel(sync.status, sync.reviewStatus) }}<small v-if="sync.errorMessage" class="error">{{ sync.errorMessage }}</small></span>
              <AssetSyncAction :sync="sync" :busy="retrying === sync.id" :can-retry="['APPROVED', 'NOT_REQUIRED'].includes(row.result.aiReview?.status || '')" @retry="retrySync(row, sync)" @verify="verify(row, sync)" />
            </div>
          </template>
          <div class="row-actions">
            <button v-if="uploadedReady(row.result, accountId)" type="button" :aria-pressed="selectedId === row.result?.id" :disabled="busy" @click="select(row)">{{ selectedId === row.result?.id ? '已选择' : '选择此素材' }}</button>
            <button v-else-if="row.error && row.valid && !row.result" type="button" :disabled="blocked || busy" @click="start([row.key])">重试上传</button>
            <button v-if="!row.busy" type="button" :disabled="blocked || busy" @click="remove(row)">移除</button>
          </div>
        </article>
        </div>
      </section>
      <button v-if="rows.length" class="upload-more" type="button" :disabled="blocked || busy" @click="chooseFiles">{{ type === 'IMAGE' ? '继续添加图片' : '继续添加文件' }}</button>
    </template>
    <div v-show="type === 'CAROUSEL'">
      <CarouselUploadPanel v-if="carouselMounted" :default-account-ids="[accountId]" :fixed-account-id="accountId" embedded @close-blocked="carouselBlocked = $event" />
      <div v-if="carouselReady(carouselDraft.result, accountId)" class="carousel-ready"><span>图文已就绪</span><button type="button" :disabled="busy" @click="selectCarousel">{{ selectedId === carouselDraft.result?.id ? '已选择' : '选择此图文' }}</button></div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import CarouselUploadPanel from '@/pages/creatives/CarouselUploadPanel.vue';
import AssetReviewStatus from '@/pages/creatives/AssetReviewStatus.vue';
import AssetSyncAction from '@/pages/creatives/AssetSyncAction.vue';
import { fileSize } from '@/pages/creatives/assetFormat';
import { addUploadFiles, beginUploadDialog, startUploads, syncLabel, uploadRows, type UploadRow } from '@/features/creative/assetUploads';
import { libraryUploadPolicy } from '@/features/creative/assetUploadTypes';
import { carouselDraft } from '@/features/creative/carouselUploads';
import { reconcileAssetSync, retryAssetReview, retryAssetSync, type AssetSync, type LibraryItemVo } from '@/features/creative/creativeApi';
import { carouselItem, carouselReady, uploadedItem, uploadedReady, uploadPhase } from '@/features/creative/materialSources';
const props = defineProps<{ type: 'VIDEO' | 'IMAGE' | 'CAROUSEL'; accountId: string; busy: boolean; active: boolean }>();
const emit = defineEmits<{ select: [item: LibraryItemVo | undefined]; blocked: [value: boolean]; filesChanged: [count: number] }>();
beginUploadDialog();
const uploadScope = crypto.randomUUID();
const input = ref<HTMLInputElement>(), adding = ref(false), retrying = ref(''), error = ref(''), selectedId = ref(''), aigc = ref(false);
const carouselMounted = ref(props.type === 'CAROUSEL' && props.active), carouselBlocked = ref(false);
const scopedRows = computed(() => uploadRows.filter(r => r.scope === uploadScope));
const rows = computed(() => scopedRows.value.filter(r => r.library === props.type));
const policy = computed(() => libraryUploadPolicy(props.type));
const blocked = computed(() => adding.value || !!retrying.value || scopedRows.value.some(r => r.busy) || carouselBlocked.value);
const readyCount = computed(() => rows.value.filter(r => uploadedReady(r.result, props.accountId)).length);
const processingStep = computed(() => Math.min(3, ...rows.value.map(row => {
  if (uploadedReady(row.result, props.accountId)) return 3;
  if (!row.result) return /校验|合并/.test(row.stage) ? 1 : 0;
  return ['APPROVED', 'NOT_REQUIRED'].includes(row.result.aiReview?.status || '') ? 2 : 1;
})));
watch(blocked, value => emit('blocked', value), { immediate: true, flush: 'sync' });
watch(() => rows.value.length, value => emit('filesChanged', value), { immediate: true, flush: 'sync' });
watch([() => props.type, () => props.active], ([value, active]) => { if (value === 'CAROUSEL' && active) carouselMounted.value = true; });
watch(() => props.type, () => { selectedId.value = ''; emit('select', undefined); error.value = ''; });
function chooseFiles() { if (!blocked.value && !props.busy) input.value?.click(); }
async function add(files?: FileList | null) {
  if (blocked.value || props.busy || props.type === 'CAROUSEL') return;
  adding.value = true; error.value = '';
  const before = new Set(scopedRows.value.map(r => r.key)), containsAi = aigc.value;
  try {
    error.value = (await addUploadFiles(files, props.type, uploadScope)).join('；');
    const added = scopedRows.value.filter(row => !before.has(row.key));
    for (const row of added) row.isAigc = containsAi;
    const valid = added.filter(row => row.valid).map(row => row.key);
    if (valid.length) await start(valid);
  } catch (value) { error.value = message(value); }
  finally { adding.value = false; }
}
function changed(event: Event) { const el = event.target as HTMLInputElement; void add(el.files); el.value = ''; }
function phase(row: UploadRow) {
  if (row.stage === '正在本地预检') return '检查中';
  if (!row.valid) return '检查未通过';
  if (row.result?.storageStatus === 'VALIDATING') return '检查中';
  if (!row.result && !row.error && /校验|合并/.test(row.stage)) return '检查中';
  return uploadPhase(row.result, props.accountId, row.busy || adding.value && !row.result, row.error);
}
function progress(row: UploadRow) { return Math.max(0, Math.min(100, row.progress)); }
function isWorking(row: UploadRow) { return ['上传中', '检查中', '同步中'].includes(phase(row)); }
function message(value: unknown) { const e = value as { message?: string; response?: { data?: { message?: string } } }; return e.response?.data?.message || e.message || '操作失败，请重试'; }
async function start(requested?: string[]) {
  const keys = requested || rows.value.map(row => row.key);
  try { await startUploads([props.accountId], aigc.value, props.type, keys); }
  catch (value) {
    for (const row of scopedRows.value) if (keys.includes(row.key) && row.valid && !row.result && !row.busy) {
      row.error = message(value); row.stage = '平台上传失败';
    }
  }
}
function select(row: UploadRow) { if (uploadedReady(row.result, props.accountId)) { selectedId.value = row.result!.id; emit('select', uploadedItem(row.result!)); } }
function selectCarousel() { if (carouselReady(carouselDraft.result, props.accountId)) { selectedId.value = carouselDraft.result!.id; emit('select', carouselItem(carouselDraft.result!)); } }
function remove(row: UploadRow) { if (blocked.value || props.busy) return; if (selectedId.value === row.result?.id) { selectedId.value = ''; emit('select', undefined); } const n = uploadRows.indexOf(row); if (n >= 0) uploadRows.splice(n, 1); }
async function retryReview(row: UploadRow) {
  if (!row.result || retrying.value) return; retrying.value = row.result.id;
  try { row.result = await retryAssetReview(row.result.id); } catch (e) { error.value = message(e); } finally { retrying.value = ''; }
}
async function retrySync(row: UploadRow, sync: AssetSync) {
  if (!row.result || retrying.value || sync.status === 'UNCERTAIN') return; retrying.value = sync.id;
  try { row.result = await retryAssetSync(row.result.id, props.accountId); } catch (e) { error.value = message(e); } finally { retrying.value = ''; }
}
async function verify(row: UploadRow, sync: AssetSync) {
  if (!row.result || retrying.value) return; retrying.value = sync.id;
  try { row.result = (await reconcileAssetSync(row.result.id, props.accountId)).asset; } catch (e) { error.value = message(e); } finally { retrying.value = ''; }
}
// A polling update can revoke readiness; never leave an old selection enabled after a rejection.
watch(() => [rows.value.map(r => [r.result?.id, uploadedReady(r.result, props.accountId)]), carouselDraft.result && carouselReady(carouselDraft.result, props.accountId)], () => {
  if (!selectedId.value) return;
  const valid = props.type === 'CAROUSEL' ? carouselDraft.result?.id === selectedId.value && carouselReady(carouselDraft.result, props.accountId) : rows.value.some(r => r.result?.id === selectedId.value && uploadedReady(r.result, props.accountId));
  if (!valid) { selectedId.value = ''; emit('select', undefined); }
}, { deep: true });
onUnmounted(() => emit('blocked', false));
</script>
<style scoped>
.account-note{color:#7c7f99;font-size:12px;margin:12px 0}.drop-zone{border:1px dashed #bbb9ef;border-radius:12px;background:#fafaff;display:grid;justify-items:center;gap:10px;padding:22px;cursor:pointer}.drop-zone>svg{width:26px;height:26px;color:#5b55ff}.drop-zone small{color:#7c7f99;text-align:center;line-height:1.6}.drop-zone[aria-disabled=true]{opacity:.5;cursor:not-allowed}.upload-list{display:grid;gap:10px;margin-top:16px}.upload-list article{border:1px solid #e5e5f3;padding:12px;border-radius:10px;min-width:0}.upload-list article.selected{border-color:#5b55ff;background:#f8f7ff}.file-line,.row-actions,.sync-line,.carousel-ready{display:flex;justify-content:space-between;align-items:center;gap:12px}.file-line strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file-line>span{white-space:nowrap;color:#7c7f99;font-size:12px}.ready .file-line>span{color:#0cab76}.upload-list small{display:block;color:#7c7f99;font-size:12px;line-height:1.8}.aigc{display:flex;align-items:center;gap:6px;font-size:12px;margin-top:6px}.aigc input{accent-color:#5b55ff}.row-actions{justify-content:flex-end;margin-top:8px}.row-actions button,.sync-line button{color:#5b55ff}.row-actions button[aria-pressed=true]{font-weight:600}.carousel-ready button{padding:9px 16px;background:#5b55ff;color:white;border-radius:8px;margin-left:auto}.error{color:#e5484d;font-size:12px;line-height:1.7;overflow-wrap:anywhere}.sync-line{font-size:12px}.sync-line>span{min-width:0}.carousel-ready{margin-top:15px;color:#0cab76}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}progress{width:100%;height:6px;accent-color:#5b55ff}button:disabled{opacity:.45;cursor:not-allowed}
.upload-aigc{margin:0 0 12px;color:#555978}
.drop-zone .upload-requirements{display:grid;gap:5px;max-width:100%;text-align:left;overflow-wrap:anywhere}
.drop-zone--image{min-height:360px;padding:40px 24px;align-content:center;gap:18px;border-color:#c3b7ff;background:linear-gradient(135deg,#faf9ff,#f4f2ff)}
.drop-zone--image>svg{width:72px;height:72px;color:#5b48ff}
.drop-zone--image strong{font-size:20px;font-weight:600;line-height:1.5;text-align:center;color:#171830}
.drop-zone--image .image-formats{font-size:14px;color:#8b83aa;overflow-wrap:anywhere}
.image-select-button{display:grid;place-items:center;min-width:180px;min-height:52px;padding:12px 24px;border-radius:9px;background:#5342ff;color:white;font-size:18px;line-height:1.4}
.drop-zone--image:hover{border-color:#6857ff}
.drop-zone--image:focus-visible{outline:2px solid #8584ff;outline-offset:3px}
.image-requirements{margin:12px 0;font-size:12px;line-height:1.7;color:#7c7f99}
.image-requirements summary{color:#5b48ff;font-weight:600;cursor:pointer;list-style:none}
.image-requirements summary::-webkit-details-marker{display:none}
.image-requirements p{padding:10px 12px;margin-top:8px;background:#f7f5ff;border:1px solid #e5e0ff;border-radius:8px;overflow-wrap:anywhere}
.upload-more{display:block;margin:12px 0 0 auto;padding:7px 10px;border:1px solid #dcd9ff;border-radius:6px;color:#5b48ff;font-size:12px}
.upload-processing{margin-top:18px;padding:14px;border-radius:12px;background:linear-gradient(115deg,#f7f5ff,#f2f2ff)}
.upload-summary{text-align:center;color:#30344e}
.upload-summary strong{font-size:16px;font-weight:600}
.upload-summary ol{display:flex;justify-content:center;gap:58px;list-style:none;margin:16px 0 18px;padding:0;color:#a5a1ca;font-size:12px}
.upload-summary li{position:relative;display:flex;flex-direction:column;align-items:center;gap:6px;min-width:32px}
.upload-summary li+li:before{position:absolute;content:'';top:12px;right:calc(50% + 13px);width:64px;height:1px;background:#d9d4f9}
.upload-summary li span{display:grid;place-items:center;position:relative;z-index:1;width:25px;height:25px;border-radius:50%;color:#fff;background:#c6c0eb}
.upload-summary li.active,.upload-summary li.complete{color:#5b48ff}
.upload-summary li.active span,.upload-summary li.complete span{background:#5b48ff}
.upload-processing .upload-list{margin:0}
.upload-processing article{background:#fff}
.file-line>span{display:inline-flex;align-items:center;gap:7px}
.file-line>span b{font-size:12px;font-weight:500}
.file-line>span.phase-working{color:#5b48ff}
.file-line>span.phase-error{color:#e5484d}
.phase-spinner{width:15px;height:15px;flex-shrink:0;border:2px solid #e2ddff;border-top-color:#5b48ff;border-radius:50%;animation:upload-spin .8s linear infinite}
.phase-check{display:grid;place-items:center;width:17px;height:17px;border-radius:50%;background:#0cab76;color:#fff;font-style:normal;font-size:12px}
.upload-list progress{display:block;height:6px;margin:10px 0 0;border:0;border-radius:6px;overflow:hidden;background:#e8e5f6}
.upload-list progress::-webkit-progress-bar{background:#e8e5f6;border-radius:6px}
.upload-list progress::-webkit-progress-value{background:#5b48ff;border-radius:6px}
.upload-list progress::-moz-progress-bar{background:#5b48ff;border-radius:6px}
.upload-list article.ready progress{accent-color:#0cab76}
.upload-list article.ready progress::-webkit-progress-value{background:#0cab76}
.upload-list article.ready progress::-moz-progress-bar{background:#0cab76}
@keyframes upload-spin{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.phase-spinner{animation:none}}
@media(max-width:639px){.upload-processing{padding:10px}.upload-summary ol{gap:40px}.upload-summary li+li:before{width:46px}.file-line{align-items:flex-start;flex-wrap:wrap}.file-line strong{flex-basis:100%}}
@media(max-width:639px){.drop-zone--image{min-height:290px;padding:28px 16px;gap:16px}.drop-zone--image>svg{width:60px;height:60px}.drop-zone--image strong{font-size:17px}.drop-zone--image .image-formats{font-size:12px}.image-select-button{min-width:160px;min-height:46px;font-size:16px}}

</style>
