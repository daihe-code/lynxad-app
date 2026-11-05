<template>
  <section class="image-library-upload">
    <header class="image-upload-head">
      <h2 id="asset-modal-title">上传图片</h2>
      <button class="image-upload-close" type="button" aria-label="关闭" :disabled="busy" @click="close"><AssetIcon name="close" /></button>
    </header>
    <div class="image-upload-body">
      <input ref="input" class="sr-only" type="file" accept=".jpg,.jpeg,.png" multiple aria-label="选择本地图片" :disabled="busy" @change="changed" />
      <template v-if="!rows.length">
        <p class="image-upload-intro">请选择本地图片，选中后将自动上传、检查并进入素材库。</p>
        <div class="image-upload-drop" role="button" tabindex="0" aria-label="选择图片" :aria-disabled="busy" @click="choose" @keydown.enter="choose" @keydown.space.prevent="choose" @dragover.prevent @drop.prevent="add($event.dataTransfer?.files)">
          <span class="image-upload-symbol"><AssetIcon :name="adding ? 'refresh' : 'image'" :class="{ spinning: adding }" /></span>
          <span aria-live="polite">{{ adding ? '上传中…' : '支持 JPG / PNG / JPEG' }}</span>
          <span class="image-select-button"><AssetIcon name="image" />选择图片</span>
        </div>
      </template>
      <template v-else>
        <div class="image-batch-head"><strong>{{ allStored ? `${rows.length}张图片已上传完成` : `正在处理${rows.length}张图片` }}</strong><button type="button" :disabled="busy" @click="choose">＋ 继续选择</button></div>
        <div class="image-upload-files" aria-live="polite">
          <article v-for="row in rows" :key="row.key" class="image-upload-file">
            <div class="image-file-top">
              <div class="image-file-cover"><img v-if="previews[row.key]" :src="previews[row.key]" :alt="row.file.name" /><AssetIcon v-else name="image" /></div>
              <div class="image-file-info">
                <strong :title="row.file.name">{{ row.file.name }}</strong>
                <p><template v-if="row.metadata?.width">{{ row.metadata.width }}×{{ row.metadata.height }} · </template>{{ fileSize(row.file.size) }}<template v-if="imageLibraryDescription(row.metadata)"> ｜ {{ imageLibraryDescription(row.metadata) }}</template></p>
              </div>
              <div class="image-file-state" :class="{ stored: phase(row) === '已入库', failed: phase(row) === '上传失败' || row.stopped }">
                <span><AssetIcon v-if="phase(row) === '已入库'" name="check-circle" /><i v-else-if="!row.error && !row.stopped" class="image-spinner" aria-hidden="true" />{{ phase(row) }}<template v-if="phase(row) === '上传中'"> {{ row.progress }}%</template></span>
              </div>
            </div>
            <progress v-if="!row.error && !row.stopped && phase(row) !== '已入库'" :value="row.progress" max="100" :aria-label="`${row.file.name}上传进度`" />
            <p v-if="row.error || row.result?.storageStatus === 'FAILED'" class="image-upload-error" role="alert">{{ row.error || row.result?.errorMessage || '图片入库失败，请重试。' }}</p>
            <div v-if="phase(row) === '上传失败' || row.stopped" class="image-file-actions"><button v-if="row.valid && !row.result" type="button" :disabled="busy" @click="retry(row)">重试</button><button type="button" :disabled="busy" :aria-label="`移除 ${row.file.name}`" @click="remove(row)">移除</button></div>
          </article>
        </div>
      </template>
      <p v-if="error" class="image-upload-error" role="alert">{{ error }}</p>
      <label v-if="!rows.length" class="image-ai"><input v-model="aigc" type="checkbox" :disabled="busy" />包含 AI 生成内容</label>
      <div class="image-upload-options">
        <button class="image-requirements-toggle" type="button" :aria-expanded="requirementsOpen" @click="requirementsOpen = !requirementsOpen">图片上传要求 <span>{{ requirementsOpen ? '收起' : '展开' }}</span><AssetIcon name="chevron-down" :class="{ reversed: requirementsOpen }" /></button>
      </div>
      <div v-if="requirementsOpen" class="image-requirements">
        <table>
          <thead><tr><th>类型</th><th>宽高比</th><th>大小</th><th>尺寸范围</th></tr></thead>
          <tbody><tr v-for="rule in imageLibraryRules" :key="rule.label"><td>{{ rule.label }}</td><td>{{ rule.ratio }}</td><td>≤{{ rule.maxMb }}M</td><td>{{ rule.minWidth }}×{{ rule.minHeight }} ～ {{ rule.maxWidth }}×{{ rule.maxHeight }}</td></tr></tbody>
        </table>
        <p><AssetIcon name="info-line" />尺寸范围含上下限，宽、高需同时符合要求。</p>
      </div>
      <p v-if="allStored" class="image-saved-note">素材已保存，可在图片库查看。</p>
    </div>
    <footer class="image-upload-foot"><span>{{ !rows.length ? '尚未选择图片' : allStored ? `已完成${storedCount}张图片` : `已入库${storedCount}/${rows.length}张图片` }}</span><button type="button" :disabled="busy" :class="{ primary: allStored }" @click="close">{{ allStored ? '完成，返回图片库' : '关闭' }}</button></footer>
  </section>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { addUploadFiles, beginUploadDialog, startUploads, uploadRows, type UploadRow } from '@/features/creative/assetUploads';
import { imageLibraryDescription, imageLibraryFileError, imageLibraryRules } from '@/features/creative/imageLibraryUpload';
import { libraryUploadState as phase } from '@/features/creative/libraryUploadState';
import { fileSize } from './assetFormat';
import AssetIcon from './AssetIcon.vue';

const props = defineProps<{ accountId?: string }>();
const emit = defineEmits<{ close: []; changed: []; closeBlocked: [value: boolean] }>();
beginUploadDialog();
const scope = crypto.randomUUID(), targetAccounts = props.accountId ? [props.accountId] : [];
const input = ref<HTMLInputElement>(), adding = ref(false), aigc = ref(false), error = ref(''), requirementsOpen = ref(false);
const rows = computed(() => uploadRows.filter(row => row.scope === scope));
const busy = computed(() => adding.value || rows.value.some(row => row.busy));
const storedCount = computed(() => rows.value.filter(row => phase(row) === '已入库').length);
const allStored = computed(() => rows.value.length > 0 && storedCount.value === rows.value.length);
const previews = ref<Record<string, string>>({});
watch(() => rows.value.filter(row => row.metadata?.width).map(row => row.key), keys => {
  for (const key of Object.keys(previews.value)) if (!keys.includes(key)) { URL.revokeObjectURL(previews.value[key]!); delete previews.value[key]; }
  for (const row of rows.value) if (keys.includes(row.key) && !previews.value[row.key]) previews.value[row.key] = URL.createObjectURL(row.file);
});
watch(busy, value => emit('closeBlocked', value), { immediate: true, flush: 'sync' });
watch(storedCount, (value, old) => { if (value > old) emit('changed'); });
function choose() { if (!busy.value) input.value?.click(); }
function close() { if (!busy.value) emit('close'); }
async function add(files?: FileList | null) {
  if (busy.value || !files?.length) return;
  adding.value = true; error.value = ''; requirementsOpen.value = false;
  const before = new Set(rows.value.map(row => row.key)), containsAi = aigc.value;
  try {
    error.value = (await addUploadFiles(files, 'IMAGE', scope)).join('；');
    const added = rows.value.filter(row => !before.has(row.key));
    for (const row of added) {
      row.isAigc = containsAi;
      if (row.valid) { const problem = imageLibraryFileError(row.file, row.metadata); if (problem) { row.valid = false; row.error = problem; } }
    }
    await upload(added.filter(row => row.valid).map(row => row.key));
  } catch { error.value = '暂时无法上传图片，请重试。'; }
  finally { adding.value = false; }
}
async function upload(keys: string[]) {
  if (!keys.length) return;
  try { await startUploads(targetAccounts, false, 'IMAGE', keys); }
  catch { for (const row of rows.value) if (keys.includes(row.key) && !row.result) row.error = '暂时无法上传图片，请重试。'; }
}
async function retry(row: UploadRow) { if (!busy.value) { adding.value = true; try { await upload([row.key]); } finally { adding.value = false; } } }
function changed(event: Event) { const el = event.target as HTMLInputElement; void add(el.files); el.value = ''; }
function remove(row: UploadRow) { if (busy.value) return; const index = uploadRows.indexOf(row); if (index >= 0) uploadRows.splice(index, 1); }
onUnmounted(() => { for (const url of Object.values(previews.value)) URL.revokeObjectURL(url); emit('closeBlocked', false); });
</script>
<style scoped>
.image-library-upload{display:flex;flex-direction:column;min-height:0;color:#23233c}
.image-upload-head{display:flex;align-items:center;justify-content:space-between;padding:22px 26px 12px;gap:16px}
.image-upload-head h2{font-size:22px;line-height:1.4;font-weight:700;margin:0}
.image-upload-close{display:grid;place-items:center;width:30px;height:30px;border:0;border-radius:7px;background:white;color:#79759b;cursor:pointer}
.image-library-upload svg{width:19px;height:19px;flex:none}
.image-upload-body{padding:0 26px 16px;overflow-y:auto;min-height:0}
.image-upload-intro{font-size:14px;line-height:1.6;margin:0 0 16px;color:#77738e}
.image-upload-drop{display:flex;flex-direction:column;align-items:center;gap:10px;padding:16px;border:1px dashed #8e80ff;border-radius:14px;background:linear-gradient(125deg,#f9f8ff,#f0edff);cursor:pointer;text-align:center;color:#77718f;font-size:13px}
.image-upload-drop[aria-disabled=true]{cursor:progress}
.image-upload-symbol{width:42px;height:42px;border-radius:50%;background:#e9e4ff;color:#5b46ff;display:grid;place-items:center}
.image-upload-symbol svg{width:25px;height:25px}
.image-select-button{display:inline-flex;align-items:center;gap:8px;background:#5945ff;color:white;border-radius:8px;padding:9px 20px;font-size:14px;line-height:1.3}
.image-upload-options{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:16px;flex-wrap:wrap}
.image-requirements-toggle{display:inline-flex;gap:8px;align-items:center;width:100%;padding:9px 12px;color:#35304e;background:#f3f1fb;border:0;border-radius:9px;font-size:14px;font-weight:600;cursor:pointer;text-align:left}
.image-requirements-toggle span{color:#5b46ff;font-size:12px}
.image-requirements-toggle svg{width:14px;height:14px;color:#5b46ff}
.reversed{transform:rotate(180deg)}
.image-ai{display:flex;align-items:center;gap:5px;color:#858096;font-size:12px;margin-top:12px}
.image-ai input{accent-color:#5b46ff}
.image-requirements{font-size:13px;margin-top:9px}
.image-requirements table{width:100%;border-collapse:separate;border-spacing:0;border:1px solid #dfdbf2;border-radius:10px;overflow:hidden;text-align:center;white-space:nowrap}
.image-requirements th,.image-requirements td{padding:8px 5px;font-weight:400;border-bottom:1px solid #e5e1f2}
.image-requirements th{font-weight:600;background:#eeebff}
.image-requirements tbody tr:last-child td{border-bottom:0}
.image-requirements>p{display:flex;align-items:center;gap:6px;font-size:12px;color:#898299;margin:9px 0 0;line-height:1.5}
.image-requirements>p svg{width:15px;height:15px}
.image-upload-foot{display:flex;justify-content:space-between;align-items:center;gap:16px;border-top:1px solid #e8e5f1;padding:14px 26px;flex:none}
.image-upload-foot>span{color:#858096;font-size:13px}
.image-upload-foot button,.image-batch-head button{border:1px solid #ded7fa;background:white;border-radius:8px;line-height:1.4;padding:8px 18px;font-size:13px;color:#37334f;cursor:pointer}
.image-upload-foot button.primary{background:#5945ff;color:white;border-color:#5945ff}
.image-library-upload button:disabled{opacity:.45;cursor:not-allowed}
.image-library-upload button:focus-visible,.image-upload-drop:focus-visible{outline:2px solid #9182ff;outline-offset:3px}
.image-batch-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:6px 0 16px}
.image-batch-head strong{font-size:15px;font-weight:600}
.image-batch-head button{color:#5b46ff;font-size:12px;padding:7px 12px;white-space:nowrap}
.image-upload-files{display:grid;gap:9px;max-height:390px;overflow-y:auto}
.image-upload-file{padding:8px 12px;border:1px solid #e4dff2;border-radius:12px;background:#fff}
.image-file-top{display:flex;align-items:center;gap:12px}
.image-file-cover{position:relative;background:#f4f2f8;border-radius:7px;overflow:hidden;width:86px;height:52px;flex:none;display:grid;place-items:center;color:#a299bc}
.image-file-cover img{position:absolute;inset:0;width:100%;height:100%;object-fit:contain}
.image-file-info{min-width:0;flex:1}
.image-file-info>strong{display:block;font-size:14px;font-weight:600;overflow-wrap:anywhere;line-height:1.5}
.image-file-info p{font-size:12px;color:#88819d;line-height:1.5;margin:4px 0 0;font-variant-numeric:tabular-nums}
.image-file-state{flex:none;color:#6453ff;font-size:12px}
.image-file-state>span{display:flex;align-items:center;gap:5px;white-space:nowrap}
.image-file-state svg{width:14px;height:14px}
.image-file-state.stored{color:#16a263;background:#e9f7eb;border-radius:7px;padding:5px 8px}
.image-file-state.failed{color:#e05d66}
.image-upload-file progress{display:block;width:100%;height:5px;margin:9px 0 2px;appearance:none;-webkit-appearance:none;border:0;border-radius:5px;overflow:hidden;background:#e5e0f4;accent-color:#6750ff}
.image-upload-file progress::-webkit-progress-bar{background:#e5e0f4}
.image-upload-file progress::-webkit-progress-value{background:#6750ff}
.image-upload-file progress::-moz-progress-bar{background:#6750ff}
.image-spinner{display:inline-block;width:13px;height:13px;border:2px solid #d9d0ff;border-top-color:#6750ff;border-radius:50%;animation:image-spin .8s linear infinite}
.spinning{animation:image-spin .8s linear infinite}
.image-upload-error{color:#df5c65;font-size:12px;line-height:1.6;margin:8px 0 0;overflow-wrap:anywhere}
.image-file-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:7px}
.image-file-actions button{font-size:12px;border:0;background:none;color:#6049ff;padding:0;cursor:pointer}
.image-saved-note{color:#858096;font-size:12px;margin:10px 0 0}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}
@keyframes image-spin{to{transform:rotate(360deg)}}
@media(max-width:600px){
  .image-upload-head{padding:18px 16px 12px}.image-upload-body{padding:0 16px 14px}.image-upload-foot{padding:14px 16px;gap:8px}
  .image-upload-head h2{font-size:20px}.image-upload-intro{font-size:13px}.image-requirements{font-size:10px}.image-requirements th,.image-requirements td{padding:8px 3px}
  .image-file-top{gap:8px;flex-wrap:wrap}.image-file-cover{width:70px;height:58px}.image-file-info strong{font-size:13px}.image-file-state{margin-left:auto}
  .image-upload-foot>span{font-size:12px}.image-upload-foot button{padding:8px 12px}.image-batch-head strong{font-size:14px}
}
@media(prefers-reduced-motion:reduce){.image-spinner,.spinning{animation:none}}
</style>
