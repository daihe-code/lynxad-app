<template>
  <div class="local-material-upload">
    <p class="account-note">素材将同步到当前计划的投放账户。</p>
    <template v-if="type !== 'CAROUSEL'">
      <input ref="input" class="sr-only" type="file" multiple :accept="policy.extensions.map(e => `.${e}`).join(',')" aria-label="选择本地素材文件" :disabled="blocked || busy" @change="changed" />
      <div class="drop-zone" role="button" tabindex="0" :aria-disabled="blocked || busy" @click="chooseFiles" @keydown.enter="chooseFiles" @keydown.space.prevent="chooseFiles" @dragover.prevent @drop.prevent="add($event.dataTransfer?.files)">
        <ReferenceIcon name="upload" /><strong>点击选择文件，或拖拽至此处</strong><small>{{ policy.label }}：{{ policy.hint }}</small>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div v-if="rows.length" class="upload-list" aria-live="polite">
        <article v-for="row in rows" :key="row.key" :class="{ selected: selectedId === row.result?.id, ready: uploadedReady(row.result, accountId) }">
          <div class="file-line"><strong :title="row.file.name">{{ row.file.name }}</strong><span>{{ phase(row) }}</span></div>
          <small>{{ fileSize(row.file.size) }}<template v-if="row.metadata"> · {{ row.metadata.summary }}</template></small>
          <label v-if="!row.result" class="aigc"><input v-model="row.isAigc" type="checkbox" :disabled="row.busy || busy" />包含 AI 生成内容</label>
          <small v-else>{{ row.result.isAigc ? '包含 AI 生成内容' : '非 AI 生成内容' }}</small>
          <progress v-if="row.busy" :value="row.progress" max="100" :aria-label="`${row.file.name}上传进度`" />
          <p v-if="row.error" class="error">{{ row.error }}</p>
          <template v-if="row.result && !uploadedReady(row.result, accountId)">
            <AssetReviewStatus :review="row.result.aiReview" :retrying="retrying === row.result.id" @retry="retryReview(row)" />
            <div v-for="sync in row.result.syncs.filter(s => s.accountId === accountId)" :key="sync.id" class="sync-line">
              <span>{{ syncLabel(sync.status, sync.reviewStatus) }}<small v-if="sync.errorMessage" class="error">{{ sync.errorMessage }}</small></span>
              <AssetSyncAction :sync="sync" :busy="retrying === sync.id" :can-retry="['APPROVED', 'NOT_REQUIRED'].includes(row.result.aiReview?.status || '')" @retry="retrySync(row, sync)" @verify="verify(row, sync)" />
            </div>
          </template>
          <div class="row-actions">
            <button v-if="uploadedReady(row.result, accountId)" type="button" :aria-pressed="selectedId === row.result?.id" :disabled="busy" @click="select(row)">{{ selectedId === row.result?.id ? '已选择' : '选择此素材' }}</button>
            <button v-else-if="row.error && row.valid && !row.result" type="button" :disabled="blocked || busy" @click="start([row.key])">重试上传</button>
            <button v-if="!row.busy" type="button" :disabled="busy" @click="remove(row)">移除</button>
          </div>
        </article>
      </div>
      <div class="upload-actions"><span v-if="rows.length">已就绪 {{ readyCount }} / {{ rows.length }} 个文件</span><button type="button" :disabled="blocked || busy || !pending" @click="start()">{{ blocked ? '正在上传…' : '开始上传' }}</button></div>
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
const emit = defineEmits<{ select: [item: LibraryItemVo | undefined]; blocked: [value: boolean] }>();
beginUploadDialog();
const uploadScope = crypto.randomUUID();
const keys = ref<string[]>([]), input = ref<HTMLInputElement>(), adding = ref(false), retrying = ref(''), error = ref(''), selectedId = ref('');
const carouselMounted = ref(props.type === 'CAROUSEL' && props.active), carouselBlocked = ref(false);
const rows = computed(() => uploadRows.filter(r => keys.value.includes(r.key) && r.library === props.type));
const policy = computed(() => libraryUploadPolicy(props.type));
const blocked = computed(() => adding.value || !!retrying.value || uploadRows.some(r => keys.value.includes(r.key) && r.busy) || carouselBlocked.value);
const pending = computed(() => rows.value.some(r => r.valid && !r.result && !r.busy));
const readyCount = computed(() => rows.value.filter(r => uploadedReady(r.result, props.accountId)).length);
watch(blocked, value => emit('blocked', value), { immediate: true, flush: 'sync' });
watch([() => props.type, () => props.active], ([value, active]) => { if (value === 'CAROUSEL' && active) carouselMounted.value = true; });
watch(() => props.type, () => { selectedId.value = ''; emit('select', undefined); error.value = ''; });
function chooseFiles() { if (!blocked.value && !props.busy) input.value?.click(); }
async function add(files?: FileList | null) {
  if (blocked.value || props.busy || props.type === 'CAROUSEL') return;
  adding.value = true; error.value = '';
  const before = new Set(uploadRows.map(r => r.key));
  try { error.value = (await addUploadFiles(files, props.type, uploadScope)).join('；'); }
  finally {
    for (const row of uploadRows) if (!before.has(row.key)) { row.isAigc = false; keys.value.push(row.key); }
    adding.value = false;
  }
}
function changed(event: Event) { const el = event.target as HTMLInputElement; void add(el.files); el.value = ''; }
function phase(row: UploadRow) { return row.stage === '正在本地预检' ? '预检中' : !row.valid ? '预检未通过' : uploadPhase(row.result, props.accountId, row.busy, row.error); }
function message(value: unknown) { const e = value as { message?: string; response?: { data?: { message?: string } } }; return e.response?.data?.message || e.message || '操作失败，请重试'; }
async function start(requested?: string[]) {
  error.value = '';
  try { await startUploads([props.accountId], false, props.type, requested || rows.value.map(r => r.key)); }
  catch (value) { error.value = message(value); }
}
function select(row: UploadRow) { if (uploadedReady(row.result, props.accountId)) { selectedId.value = row.result!.id; emit('select', uploadedItem(row.result!)); } }
function selectCarousel() { if (carouselReady(carouselDraft.result, props.accountId)) { selectedId.value = carouselDraft.result!.id; emit('select', carouselItem(carouselDraft.result!)); } }
function remove(row: UploadRow) { if (row.busy) return; if (selectedId.value === row.result?.id) { selectedId.value = ''; emit('select', undefined); } const n = uploadRows.indexOf(row); if (n >= 0) uploadRows.splice(n, 1); keys.value = keys.value.filter(k => k !== row.key); }
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
.account-note{color:#7c7f99;font-size:12px;margin:12px 0}.drop-zone{border:1px dashed #bbb9ef;border-radius:12px;background:#fafaff;display:grid;justify-items:center;gap:10px;padding:22px;cursor:pointer}.drop-zone>svg{width:26px;height:26px;color:#5b55ff}.drop-zone small{color:#7c7f99;text-align:center;line-height:1.6}.drop-zone[aria-disabled=true]{opacity:.5;cursor:not-allowed}.upload-list{display:grid;gap:10px;margin-top:16px}.upload-list article{border:1px solid #e5e5f3;padding:12px;border-radius:10px;min-width:0}.upload-list article.selected{border-color:#5b55ff;background:#f8f7ff}.file-line,.row-actions,.upload-actions,.sync-line,.carousel-ready{display:flex;justify-content:space-between;align-items:center;gap:12px}.file-line strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file-line>span{white-space:nowrap;color:#7c7f99;font-size:12px}.ready .file-line>span{color:#0cab76}.upload-list small{display:block;color:#7c7f99;font-size:12px;line-height:1.8}.aigc{display:flex;align-items:center;gap:6px;font-size:12px;margin-top:6px}.aigc input{accent-color:#5b55ff}.row-actions{justify-content:flex-end;margin-top:8px}.row-actions button,.sync-line button{color:#5b55ff}.row-actions button[aria-pressed=true]{font-weight:600}.upload-actions{margin:16px 0;font-size:12px;color:#7c7f99}.upload-actions>button,.carousel-ready button{padding:9px 16px;background:#5b55ff;color:white;border-radius:8px;margin-left:auto}.error{color:#e5484d;font-size:12px;line-height:1.7;overflow-wrap:anywhere}.sync-line{font-size:12px}.sync-line>span{min-width:0}.carousel-ready{margin-top:15px;color:#0cab76}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}progress{width:100%;height:6px;accent-color:#5b55ff}button:disabled{opacity:.45;cursor:not-allowed}
</style>
