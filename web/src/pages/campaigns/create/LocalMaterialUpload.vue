<template>
  <div class="local-material-upload">
    <p v-if="type === 'VIDEO' && !rows.length" class="account-note">素材将同步到当前计划的投放账户。</p>
    <template v-if="type !== 'CAROUSEL'">
      <label v-if="!rows.length" class="aigc upload-aigc"><input v-model="aigc" type="checkbox" :disabled="blocked || busy" />包含 AI 生成内容</label>
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
      <LocalUploadProgress v-if="rows.length" :type="type" :rows="rows" :state="uploadBatchState" :account-id="accountId" :added-ids="addedAssetIds || []" :saving="!!batchSaving" :retrying="retrying" :disabled="blocked || busy" :phase="phase" @retry-review="retryReview" @retry-sync="retrySync" @verify-sync="verify" @retry-upload="row => start([row.key])" @remove="remove" />
    </template>
    <div v-show="type === 'CAROUSEL'">
      <CarouselLocalUpload v-if="carouselMounted" ref="carouselForm" :account-id="accountId" :busy="busy" :added-ids="addedAssetIds" :attach-error="attachError" @state="carouselStateChanged" @ready="item => emit('select', item)" />
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import LocalUploadProgress from './LocalUploadProgress.vue';
import type { UploadBatchState } from '@/features/creative/localUploadBatch';
import CarouselLocalUpload, { type CarouselFormState } from './CarouselLocalUpload.vue';
import { addUploadFiles, beginUploadDialog, startUploads, stopUploadRows, uploadRows, type UploadRow } from '@/features/creative/assetUploads';
import { libraryUploadPolicy } from '@/features/creative/assetUploadTypes';
import { reconcileAssetSync, retryAssetReview, retryAssetSync, type AssetSync, type LibraryItemVo } from '@/features/creative/creativeApi';
import { uploadedReady, uploadPhase } from '@/features/creative/materialSources';
const props = defineProps<{ type: 'VIDEO' | 'IMAGE' | 'CAROUSEL'; accountId: string; busy: boolean; active: boolean; addedAssetIds?: string[]; batchSaving?: boolean; attachError?: string }>();
const emit = defineEmits<{ select: [item: LibraryItemVo | undefined]; blocked: [value: boolean]; filesChanged: [count: number]; carouselState: [state: CarouselFormState]; uploadBatch: [state: UploadBatchState] }>();
beginUploadDialog();
const uploadScope = crypto.randomUUID();
const carouselForm = ref<InstanceType<typeof CarouselLocalUpload>>();
const input = ref<HTMLInputElement>(), adding = ref(false), retrying = ref(''), error = ref(''), selectedId = ref(''), aigc = ref(false);
const carouselMounted = ref(props.type === 'CAROUSEL' && props.active), carouselBlocked = ref(false);
const scopedRows = computed(() => uploadRows.filter(r => r.scope === uploadScope));
const rows = computed(() => scopedRows.value.filter(r => r.library === props.type));
const policy = computed(() => libraryUploadPolicy(props.type));
const blocked = computed(() => adding.value || !!retrying.value || scopedRows.value.some(r => r.busy) || carouselBlocked.value);
let picking: AbortController | undefined;
const uploadBatchState = computed<UploadBatchState>(() => {
  const files = props.type !== 'CAROUSEL' ? rows.value : [];
  return { count: files.length, readyCount: files.filter(row => !row.stopped && uploadedReady(row.result, props.accountId)).length,
    addedCount: files.filter(row => row.result && props.addedAssetIds?.includes(row.result.id)).length,
    stoppedCount: files.filter(row => row.stopped).length,
    processing: files.some(row => !row.stopped && (row.busy || adding.value || isWorking(row))),
    readyIds: [...new Set(files.filter(row => !row.stopped && uploadedReady(row.result, props.accountId)).map(row => row.result!.id))] };
});
watch(uploadBatchState, value => emit('uploadBatch', value), { immediate: true });
watch(blocked, value => emit('blocked', value), { immediate: true, flush: 'sync' });
watch(() => rows.value.length, value => emit('filesChanged', value), { immediate: true, flush: 'sync' });
watch([() => props.type, () => props.active], ([value, active]) => { if (value === 'CAROUSEL' && active) carouselMounted.value = true; });
watch(() => props.type, () => { selectedId.value = ''; emit('select', undefined); error.value = ''; });
function chooseFiles() { if (!blocked.value && !props.busy) input.value?.click(); }
async function add(files?: FileList | null) {
  if (blocked.value || props.busy || props.type === 'CAROUSEL') return;
  adding.value = true; error.value = '';
  picking = new AbortController();
  const before = new Set(scopedRows.value.map(r => r.key)), containsAi = aigc.value;
  try {
    error.value = (await addUploadFiles(files, props.type, uploadScope, picking.signal)).join('；');
    const added = scopedRows.value.filter(row => !before.has(row.key));
    for (const row of added) {
      row.isAigc = containsAi;
      if (props.type === 'IMAGE' && row.metadata?.width && row.metadata?.height && row.metadata.width !== row.metadata.height) {
        row.valid = false; row.stage = '预检未通过'; row.error = '商品卡图片须为 1:1 方图，请裁切后上传或选择其他图片。';
      }
    }
    if (picking.signal.aborted) { await stopUploadRows(added.map(row => row.key)); return; }
    const valid = added.filter(row => row.valid && !row.stopped).map(row => row.key);
    if (valid.length) await start(valid);
  } catch (value) { error.value = message(value); }
  finally { adding.value = false; }
}
function changed(event: Event) { const el = event.target as HTMLInputElement; void add(el.files); el.value = ''; }
function phase(row: UploadRow) {
  if (row.stopped) return '已停止';
  if (row.stage === '正在本地预检') return '检查中';
  if (!row.valid) return '检查未通过';
  if (row.result?.storageStatus === 'VALIDATING') return '检查中';
  if (!row.result && !row.error && /校验|合并/.test(row.stage)) return '检查中';
  return uploadPhase(row.result, props.accountId, row.busy, row.error);
}
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
onUnmounted(() => emit('blocked', false));
async function stopUploads() {
  if (props.busy) return;
  picking?.abort();
  await stopUploadRows(rows.value.filter(row => !uploadedReady(row.result, props.accountId)).map(row => row.key));
}
function continueUploads() {
  if (blocked.value || props.busy || uploadBatchState.value.processing) return;
  const keys = new Set(rows.value.map(row => row.key));
  for (let index = uploadRows.length - 1; index >= 0; index--) if (keys.has(uploadRows[index]!.key)) uploadRows.splice(index, 1);
  chooseFiles();
}
function carouselStateChanged(state: CarouselFormState) { carouselBlocked.value = state.processing; emit('carouselState', state); }
function createLocalCarousel() { void carouselForm.value?.create(); }
function resetLocalCarousel() { carouselForm.value?.reset(); }
function editLocalCarousel() { carouselForm.value?.edit(); }
defineExpose({ stopUploads, continueUploads, createLocalCarousel, resetLocalCarousel, editLocalCarousel });
</script>
<style scoped>
.account-note{color:#7c7f99;font-size:12px;margin:12px 0}.drop-zone{border:1px dashed #bbb9ef;border-radius:12px;background:#fafaff;display:grid;justify-items:center;gap:10px;padding:22px;cursor:pointer}.drop-zone>svg{width:26px;height:26px;color:#5b55ff}.drop-zone small{color:#7c7f99;text-align:center;line-height:1.6}.drop-zone[aria-disabled=true]{opacity:.5;cursor:not-allowed}.aigc{display:flex;align-items:center;gap:6px;font-size:12px;margin-top:6px}.aigc input{accent-color:#5b55ff}.carousel-ready{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:15px;color:#0cab76}.carousel-ready button{padding:9px 16px;background:#5b55ff;color:white;border-radius:8px;margin-left:auto}.error{color:#e5484d;font-size:12px;line-height:1.7;overflow-wrap:anywhere}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}button:disabled{opacity:.45;cursor:not-allowed}
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
@media(max-width:639px){.drop-zone--image{min-height:280px;padding:28px 16px;gap:14px}.drop-zone--image>svg{width:58px;height:58px}.drop-zone--image strong{font-size:17px}.image-select-button{min-width:150px;min-height:44px;font-size:16px}.drop-zone--image .image-formats{font-size:12px}}
</style>
