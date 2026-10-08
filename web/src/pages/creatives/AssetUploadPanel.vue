<template>
  <div class="upload-settings">
    <label>同步到千川账户 <el-select v-model="selected" multiple collapse-tags placeholder="可多选；不选则仅保存到平台" :disabled="busy" style="width:100%"><el-option v-for="a in accounts" :key="a.accountId" :label="`${a.accountName}（${a.advertiserId}）`" :value="a.accountId" /></el-select></label>
    <p v-if="accountError" class="upload-error">{{ accountError }} <button type="button" @click="loadAccounts">重试</button></p>
    <p v-else-if="!accounts.length">尚无可选授权账户，可先保存到平台。<router-link to="/accounts?tab=oauth">前往广告授权</router-link></p>
    <p v-else-if="!selected.length">当前仅保存到平台，不会同步千川。文本始终作为文案入库。</p>
    <label class="upload-aigc"><input v-model="aigc" type="checkbox" :disabled="busy" />这些文件包含 AI 生成内容（如实申报）</label>
  </div>
  <input ref="input" class="sr-only" type="file" aria-label="选择上传文件" :accept="policy.extensions.map(extension => `.${extension}`).join(',')" multiple @change="changed" />
  <div class="ac-drop" role="button" tabindex="0" @click="input?.click()" @keydown.enter="input?.click()" @keydown.space.prevent="input?.click()" @dragover.prevent @drop.prevent="add($event.dataTransfer?.files)">
    <span class="ac-drop__ic"><AssetIcon name="upload" /></span><span class="ac-drop__t">点击选择文件，或将文件拖拽到此处</span>
    <span class="ac-drop__d">{{ policy.label }}：{{ policy.hint }}</span>
    <span class="ac-drop__btn"><AssetIcon name="folder" />浏览本地文件</span>
  </div>
  <div v-if="rows.length" class="ac-files upload-files" aria-live="polite">
    <div v-for="row in rows" :key="row.key" class="upload-row">
      <div class="ac-file"><span class="ac-file__ic"><AssetIcon :name="icon(row.file.name)" /></span><div class="ac-file__b"><span class="ac-file__n">{{ row.file.name }}</span><span class="ac-file__m">{{ fileSize(row.file.size) }} · {{ row.stage }}</span></div><button v-if="!row.busy" class="ac-file__del" type="button" :aria-label="`移除 ${row.file.name}`" @click="removeRow(row)"><AssetIcon name="close" /></button></div>
      <div v-if="row.busy || row.result" class="upload-progress"><progress :value="row.progress" max="100"></progress><span>{{ row.progress }}%</span></div>
      <p v-if="row.error" class="upload-error">{{ row.error }}</p>
      <p v-if="row.metadata" class="upload-metadata">{{ row.metadata.summary }}</p>
      <p v-if="!row.result && targetError(row.file, !!selected.length)" class="upload-error">{{ targetError(row.file, !!selected.length) }}</p>
      <AssetReviewStatus v-if="row.result" :review="row.result.aiReview" :retrying="reviewRetrying === row.result.id" @retry="retryReview(row)" />
      <div v-for="sync in row.result?.syncs || []" :key="sync.id" class="upload-sync"><span>{{ accountName(sync.accountId) }}：{{ syncLabel(sync.status, sync.reviewStatus) }}<small v-if="sync.errorMessage">{{ sync.errorMessage }}</small></span><button v-if="retryable(sync.status)" type="button" :disabled="retrying === sync.id || row.result?.aiReview?.status !== 'APPROVED'" @click="retry(row, sync)">重试同步</button></div>
    </div>
  </div>
  <p class="upload-note">{{ policy.note }}</p>
  <p class="upload-note">本地文件先上传平台 OSS，再调用 LynxAd-ai-engine 审核。视频／音频先创建任务并轮询结果；审核通过后才同步千川。审核失败时保留文件，可直接重试审核。</p>
  <p v-if="closeBlocked" class="upload-note upload-close-hint" role="status">正在上传或校验素材，进入 AI 审核后即可关闭。</p>
  <p v-else-if="rows.some(row => reviewPending(row.result?.aiReview?.status))" class="upload-note upload-close-hint" role="status">素材已进入 AI 审核，可关闭弹窗，后台会继续处理。</p>
  <div class="modal__foot"><button class="btn-modal-ghost" type="button" :disabled="closeBlocked" @click="requestClose">关闭</button><button class="btn-modal-primary" type="button" :disabled="busy || !pending" @click="start">{{ checking ? '正在预检…' : busy ? '正在上传…' : '开始上传' }}</button></div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listAccounts, type AccountVo } from '@/features/identity/oauthApi';
import { retryAssetSync, retryAssetReview, type AssetSync } from '@/features/creative/creativeApi';
import { addUploadFiles, beginUploadDialog, reviewPending, startUploads, syncLabel, uploadRows, uploadStage, type UploadRow } from '@/features/creative/assetUploads';
import { libraryFileError, libraryUploadPolicy, type UploadLibrary } from '@/features/creative/assetUploadTypes';
import { targetError } from '@/features/creative/assetPreflight';
import { fileSize } from './assetFormat';
import AssetIcon from './AssetIcon.vue';
import AssetReviewStatus from './AssetReviewStatus.vue';
const reviewRetrying = ref('');
const props = defineProps<{ libraryType: UploadLibrary }>();
const policy = computed(() => libraryUploadPolicy(props.libraryType));
const rows = computed(() => uploadRows.filter(row => row.library !== 'CAROUSEL' && !libraryFileError(row.file.name, props.libraryType)));
const emit = defineEmits<{ close: []; changed: []; closeBlocked: [blocked: boolean] }>();
const input = ref<HTMLInputElement>(), accounts = ref<AccountVo[]>([]), selected = ref<string[]>([]), aigc = ref(false), accountError = ref(''), retrying = ref('');
const adding = ref(0);
beginUploadDialog();
const checking = computed(() => adding.value > 0 || rows.value.some(r => r.stage === '正在本地预检'));
const busy = computed(() => checking.value || uploadRows.some(r => r.busy)), pending = computed(() => rows.value.some(r => r.valid && !r.result && !r.busy && !targetError(r.file, !!selected.value.length)));
const closeBlocked = computed(() => checking.value || rows.value.some(r => r.busy));
watch(closeBlocked, blocked => emit('closeBlocked', blocked), { immediate: true, flush: 'sync' });
function requestClose() { if (!closeBlocked.value) emit('close'); }
async function loadAccounts() { try { accounts.value = (await listAccounts()).filter(a => a.platform === 'QIANCHUAN' && a.status !== 'DELETED'); accountError.value = ''; if (!selected.value.length) selected.value = accounts.value.filter(a => a.status === 'ACTIVE').slice(0, 1).map(a => a.accountId); } catch { accountError.value = '授权账户加载失败；文件仍可仅保存到平台'; } }
async function add(files: FileList | null | undefined) { adding.value++; try { (await addUploadFiles(files, props.libraryType)).forEach(message => ElMessage.warning(message)); } finally { adding.value--; } }
function removeRow(row: UploadRow) { const index = uploadRows.indexOf(row); if (index >= 0) uploadRows.splice(index, 1); }
function changed(e: Event) { const element = e.target as HTMLInputElement; add(element.files); element.value = ''; }
function icon(name: string) { return /\.mp4$/i.test(name) ? 'video' : /\.(mp3|m4a|wav)$/i.test(name) ? 'audio' : /\.txt$/i.test(name) ? 'note' : 'image'; }
function accountName(id: string) { return accounts.value.find(a => a.accountId === id)?.accountName || '千川账户'; }
function retryable(status: string) { return ['WAITING_AUTH', 'FAILED', 'UNCERTAIN', 'PAUSED'].includes(status); }
async function start() { await startUploads([...selected.value], aigc.value, props.libraryType); emit('changed'); }
async function retryReview(row: UploadRow) {
  if (!row.result) return;
  reviewRetrying.value = row.result.id;
  row.busy = true;
  try { row.result = await retryAssetReview(row.result.id); row.stage = uploadStage(row.result); emit('changed'); }
  finally { reviewRetrying.value = ''; row.busy = false; }
}
async function retry(row: UploadRow, sync: AssetSync) {
  if (!row.result) return;
  if (sync.status === 'UNCERTAIN') { try { await ElMessageBox.confirm('上次上传可能已被千川接收。请先在平台核对，确认需要重新上传后继续。', '确认重试', { confirmButtonText: '已核对，重新上传', cancelButtonText: '取消' }); } catch { return; } }
  retrying.value = sync.id;
  row.busy = true;
  try { row.result = await retryAssetSync(row.result.id, sync.accountId, sync.status === 'UNCERTAIN'); row.stage = uploadStage(row.result); emit('changed'); } finally { retrying.value = ''; row.busy = false; }
}
onMounted(() => { void loadAccounts(); });
</script>
<style scoped>
.upload-metadata { padding:4px 12px; color:#21854b; font-size:12px; line-height:1.6; }
.modal__foot { flex-wrap:wrap; }
.btn-modal-ghost:disabled { opacity:.45; cursor:not-allowed; }
.upload-close-hint { color:#5b55ff; }
.upload-settings { display:grid; gap:10px; padding-bottom:18px; font-size:13px; color:#50536f; }.upload-settings label:first-child { display:grid; gap:8px; }.upload-settings p,.upload-note { color:#7c7f99; font-size:12px; line-height:1.7; }.upload-settings a,.upload-sync button,.upload-error button { color:#5b55ff; }.upload-aigc { display:flex; gap:8px; align-items:center; }.upload-aigc input { accent-color:#5b55ff; }.upload-files { max-height:300px; overflow:auto; }.upload-row { padding-bottom:10px; border-bottom:1px solid #eeeef7; }.upload-progress { display:flex; gap:10px; align-items:center; margin:0 12px; font-size:12px; }.upload-progress progress { width:100%; height:7px; accent-color:#5b55ff; }.upload-error { color:#e5484d !important; font-size:12px; padding:5px 12px; }.upload-sync { display:flex; justify-content:space-between; gap:10px; padding:6px 12px; font-size:12px; line-height:1.6; }.upload-sync small { display:block; color:#9295ab; }.upload-sync button { flex:none; }.upload-note { margin-top:14px; }.sr-only { position:absolute; width:1px; height:1px; overflow:hidden; clip:rect(0,0,0,0); }.ac-file__ic svg { width:18px; height:18px; }
</style>
<style scoped>
.upload-progress progress { appearance:none; -webkit-appearance:none; border:0; background:#efeff8; border-radius:6px; overflow:hidden; }
.upload-progress progress::-webkit-progress-bar { background:#efeff8; border-radius:6px; }
.upload-progress progress::-webkit-progress-value { background:#5b55ff; border-radius:6px; }
.upload-progress progress::-moz-progress-bar { background:#5b55ff; border-radius:6px; }
</style>
