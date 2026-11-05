<template>
  <section class="video-library-upload">
    <header class="video-upload-head"><h2 id="asset-modal-title">上传视频</h2><button class="video-upload-close" type="button" aria-label="关闭" :disabled="busy" @click="close"><AssetIcon name="close" /></button></header>
    <div class="video-upload-body">
      <input ref="input" class="sr-only" type="file" accept=".mp4" multiple aria-label="选择本地视频" :disabled="busy" @change="changed" />
      <template v-if="!rows.length">
        <p class="video-upload-intro">选择本地视频，选中后将自动上传、检查并入库。</p>
        <div class="video-upload-drop" role="button" tabindex="0" aria-label="选择视频" :aria-disabled="busy" @click="choose" @keydown.enter="choose" @keydown.space.prevent="choose" @dragover.prevent @drop.prevent="add($event.dataTransfer?.files)">
          <span class="video-upload-symbol"><AssetIcon :name="adding ? 'refresh' : 'upload'" :class="{ spinning: adding }" /></span><strong aria-live="polite">{{ adding ? '上传中…' : '点击选择视频，或拖拽文件到这里' }}</strong><span>MP4 · 单个≤1000M · 时长4—300秒</span><span class="video-select-button"><AssetIcon name="folder" />选择视频</span>
        </div>
      </template>
      <template v-else>
        <div class="video-batch-head"><strong><i v-if="allStored" class="video-check" aria-hidden="true">✓</i>{{ allStored ? `${rows.length}个视频已上传完成` : `正在处理${rows.length}个视频` }}</strong><button type="button" :disabled="busy" @click="choose">＋ 继续选择</button></div>
        <div class="video-upload-files" aria-live="polite">
          <article v-for="row in rows" :key="row.key" class="video-upload-file">
            <div class="video-file-top">
              <div class="video-file-cover"><img v-if="row.metadata?.posterDataUrl" :src="row.metadata.posterDataUrl" :alt="row.file.name" /><AssetIcon v-else name="video" /><span v-if="row.metadata?.duration">▣ {{ duration(row.metadata.duration) }}</span></div>
              <div class="video-file-info"><strong :title="row.file.name">{{ row.file.name }}</strong><p>{{ fileSize(row.file.size) }}<template v-if="row.metadata?.width"> · {{ row.metadata.width }}×{{ row.metadata.height }}</template><template v-if="row.metadata?.duration"> · {{ duration(row.metadata.duration) }}</template></p><p>{{ videoLibraryDescription(row) }}</p></div>
              <div class="video-file-state" :class="{ stored: phase(row) === '已入库', failed: phase(row) === '上传失败' || row.stopped }"><span><i v-if="phase(row) === '已入库'" aria-hidden="true">✓</i><i v-else-if="!row.error && !row.stopped" class="video-spinner" aria-hidden="true" />{{ phase(row) }}<template v-if="phase(row) === '上传中'"> {{ row.progress }}%</template></span></div>
            </div>
            <progress v-if="!row.error && !row.stopped && phase(row) !== '已入库'" :value="row.progress" max="100" :aria-label="`${row.file.name}上传进度`" />
            <p v-if="row.error || row.result?.errorMessage" class="video-upload-error" role="alert">{{ row.error || row.result?.errorMessage }}</p>
            <div v-if="row.error || row.stopped" class="video-file-actions"><button v-if="row.valid && !row.result" type="button" :disabled="busy" @click="retry(row)">重试</button><button type="button" :disabled="busy" :aria-label="`移除 ${row.file.name}`" @click="remove(row)">移除</button></div>
          </article>
        </div>
        <p v-if="allStored" class="video-saved-note"><AssetIcon name="info-line" />素材已保存，可在视频库查看。</p>
      </template>
      <p v-if="error" class="video-upload-error" role="alert">{{ error }}</p>
      <div class="video-upload-options">
        <button class="video-requirements-toggle" type="button" :aria-expanded="requirementsOpen" @click="requirementsOpen = !requirementsOpen">视频上传要求 <span>{{ requirementsOpen ? '收起' : '展开' }}</span><AssetIcon name="chevron-down" :class="{ reversed: requirementsOpen }" /></button>
        <label v-if="!rows.length" class="video-ai"><input v-model="aigc" type="checkbox" :disabled="busy" />包含 AI 生成内容</label>
      </div>
      <div v-if="requirementsOpen" class="video-requirements">
        <table><thead><tr><th>类型</th><th>宽高比</th><th>分辨率范围</th></tr></thead><tbody><tr><td>横版视频</td><td>16:9</td><td>1280×720 ～ 2560×1440</td></tr><tr><td>竖版视频</td><td>9:16</td><td>720×1280 ～ 1440×2560</td></tr></tbody></table>
        <div class="video-limits"><span><AssetIcon name="video" />码率≥516kbps</span><span><AssetIcon name="folder" />大小≤1000M</span><span><AssetIcon name="clock" />时长4—300秒</span></div><p>分辨率范围含上下限，宽、高需同时符合要求。</p>
      </div>
    </div>
    <footer class="video-upload-foot"><span>{{ !rows.length ? '尚未选择视频' : allStored ? `已完成${storedCount}个视频` : `已入库${storedCount}/${rows.length}个视频` }}</span><button type="button" :disabled="busy" :class="{ primary: allStored }" @click="close">{{ allStored ? '完成，返回视频库' : '关闭' }}</button></footer>
  </section>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { addUploadFiles, beginUploadDialog, startUploads, uploadRows, type UploadRow } from '@/features/creative/assetUploads';
import { videoLibraryDescription, videoLibraryFileError, videoLibraryUploadState } from '@/features/creative/videoLibraryUpload';
import { duration, fileSize } from './assetFormat';
import AssetIcon from './AssetIcon.vue';
const props = defineProps<{ accountId?: string }>();
const emit = defineEmits<{ close: []; changed: []; closeBlocked: [value: boolean] }>();
beginUploadDialog();
const scope = crypto.randomUUID(), targetAccounts = props.accountId ? [props.accountId] : [];
const input = ref<HTMLInputElement>(), adding = ref(false), aigc = ref(false), error = ref(''), requirementsOpen = ref(false);
const rows = computed(() => uploadRows.filter(row => row.scope === scope));
const busy = computed(() => adding.value || rows.value.some(row => row.busy));
const phase = videoLibraryUploadState;
const storedCount = computed(() => rows.value.filter(row => phase(row) === '已入库').length);
const allStored = computed(() => rows.value.length > 0 && storedCount.value === rows.value.length);
watch(busy, value => emit('closeBlocked', value), { immediate: true, flush: 'sync' });
watch(storedCount, (value, old) => { if (value > old) emit('changed'); });
function choose() { if (!busy.value) input.value?.click(); }
function close() { if (!busy.value) emit('close'); }
async function add(files?: FileList | null) {
  if (busy.value || !files?.length) return;
  adding.value = true; error.value = ''; requirementsOpen.value = false;
  const before = new Set(rows.value.map(row => row.key)), containsAi = aigc.value;
  try {
    error.value = (await addUploadFiles(files, 'VIDEO', scope)).join('；');
    const added = rows.value.filter(row => !before.has(row.key));
    for (const row of added) {
      row.isAigc = containsAi;
      if (row.valid) { const problem = videoLibraryFileError(row.metadata); if (problem) { row.valid = false; row.error = problem; } }
    }
    await upload(added.filter(row => row.valid).map(row => row.key));
  } catch { error.value = '暂时无法上传视频，请重试。'; }
  finally { adding.value = false; }
}
async function upload(keys: string[]) {
  if (!keys.length) return;
  try { await startUploads(targetAccounts, false, 'VIDEO', keys); }
  catch { for (const row of rows.value) if (keys.includes(row.key) && !row.result) row.error = '暂时无法上传视频，请重试。'; }
}
async function retry(row: UploadRow) { if (!busy.value) { adding.value = true; try { await upload([row.key]); } finally { adding.value = false; } } }
function changed(event: Event) { const el = event.target as HTMLInputElement; void add(el.files); el.value = ''; }
function remove(row: UploadRow) { if (busy.value) return; const index = uploadRows.indexOf(row); if (index >= 0) uploadRows.splice(index, 1); }
onUnmounted(() => emit('closeBlocked', false));
</script>
<style scoped>
.video-library-upload{display:flex;flex-direction:column;min-height:0;color:#23233c}.video-upload-head{display:flex;align-items:center;justify-content:space-between;padding:22px 26px 12px;gap:16px}.video-upload-head h2{font-size:22px;line-height:1.4;font-weight:700;margin:0}.video-upload-close{display:grid;place-items:center;width:30px;height:30px;border:0;border-radius:7px;background:white;color:#79759b;cursor:pointer}.video-library-upload svg{width:19px;height:19px;flex:none}.video-upload-body{padding:0 26px 16px;overflow-y:auto;min-height:0}.video-upload-intro{font-size:14px;line-height:1.6;margin:0 0 16px;color:#77738e}.video-upload-drop{display:flex;flex-direction:column;align-items:center;gap:9px;padding:20px 16px;border:1px dashed #8e80ff;border-radius:14px;background:linear-gradient(125deg,#f9f8ff,#f0edff);cursor:pointer;text-align:center;color:#77718f;font-size:13px}.video-upload-drop[aria-disabled=true]{cursor:progress}.spinning{animation:video-spin .8s linear infinite}.video-upload-drop strong{font-size:16px;font-weight:600;color:#25233c;line-height:1.5}.video-upload-symbol{width:42px;height:42px;border-radius:50%;background:#e9e4ff;color:#5b46ff;display:grid;place-items:center}.video-upload-symbol svg{width:24px;height:24px}.video-select-button{display:inline-flex;align-items:center;gap:8px;background:#5945ff;color:white;border-radius:8px;padding:9px 20px;font-size:14px;line-height:1.3}.video-upload-options{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:16px;flex-wrap:wrap}.video-requirements-toggle{display:inline-flex;gap:8px;align-items:center;padding:0;color:#23233c;background:none;border:0;font-size:13px;font-weight:600;cursor:pointer}.video-requirements-toggle span{margin-left:4px;color:#5b46ff}.video-requirements-toggle svg{width:14px;height:14px;color:#5b46ff}.reversed{transform:rotate(180deg)}.video-ai{display:flex;align-items:center;gap:5px;color:#858096;font-size:12px}.video-ai input{accent-color:#5b46ff}.video-requirements{font-size:13px;margin-top:12px}.video-requirements table{width:100%;border-collapse:separate;border-spacing:0;border:1px solid #dfdbf2;border-radius:10px;overflow:hidden;text-align:center;white-space:nowrap}.video-requirements th,.video-requirements td{padding:9px 7px;font-weight:400;border-right:1px solid #dfdbf2;border-bottom:1px solid #dfdbf2}.video-requirements th{font-weight:600;background:#eeebff}.video-requirements td:last-child,.video-requirements th:last-child{border-right:0}.video-requirements tbody tr:last-child td{border-bottom:0}.video-limits{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:12px}.video-limits span{display:flex;justify-content:center;align-items:center;gap:6px;background:#f1eeff;border-radius:9px;padding:10px 5px;font-size:12px;white-space:nowrap}.video-limits svg{color:#6049ff}.video-requirements>p{font-size:12px;color:#898299;margin:9px 0 0;line-height:1.5}.video-upload-foot{display:flex;justify-content:space-between;align-items:center;gap:16px;border-top:1px solid #e8e5f1;padding:16px 26px;margin-top:2px;flex:none}.video-upload-foot>span{color:#858096;font-size:13px}.video-upload-foot button,.video-batch-head button{border:1px solid #ded7fa;background:white;border-radius:8px;line-height:1.4;padding:8px 18px;font-size:13px;color:#37334f;cursor:pointer}.video-upload-foot button.primary{background:#5945ff;color:white;border-color:#5945ff}.video-library-upload button:disabled{opacity:.45;cursor:not-allowed}.video-library-upload button:focus-visible,.video-upload-drop:focus-visible{outline:2px solid #9182ff;outline-offset:3px}.video-batch-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:6px 0 16px}.video-batch-head strong{font-size:16px;font-weight:600;display:flex;align-items:center;gap:8px}.video-batch-head button{color:#5b46ff;font-size:12px;padding:7px 12px;white-space:nowrap}.video-check{font-style:normal;width:22px;height:22px;display:grid;place-items:center;background:#25ac69;color:white;border-radius:50%;flex:none}.video-upload-files{display:grid;gap:12px;max-height:370px;overflow-y:auto}.video-upload-file{padding:11px;border-radius:12px;background:linear-gradient(125deg,#f9f8fe,#f3f1fc)}.video-file-top{display:flex;align-items:center;gap:14px}.video-file-cover{position:relative;background:#eae7f4;border-radius:8px;overflow:hidden;width:136px;aspect-ratio:16/9;flex:none;display:grid;place-items:center;color:#a299bc}.video-file-cover img,.video-file-cover video{width:100%;height:100%;object-fit:cover;position:absolute;inset:0}.video-file-cover>span{position:absolute;bottom:5px;right:5px;padding:2px 5px;font-size:10px;border-radius:4px;color:white;background:#292638bb;font-variant-numeric:tabular-nums}.video-file-info{min-width:0;flex:1}.video-file-info>strong{display:block;font-size:14px;font-weight:600;overflow-wrap:anywhere;line-height:1.5}.video-file-info p{font-size:12px;color:#88819d;line-height:1.5;margin:4px 0 0;font-variant-numeric:tabular-nums}.video-file-state{flex:none;color:#6453ff;font-size:12px}.video-file-state>span{display:flex;align-items:center;gap:5px;white-space:nowrap}.video-file-state i{font-style:normal}.video-file-state.stored{color:#16a263;background:#e2f7ed;border-radius:7px;padding:5px 8px}.video-file-state.failed{color:#e05d66}.video-upload-file progress{display:block;width:100%;height:5px;margin:10px 0 0;appearance:none;-webkit-appearance:none;border:0;border-radius:5px;overflow:hidden;background:#e5e0f4;accent-color:#6750ff}.video-upload-file progress::-webkit-progress-bar{background:#e5e0f4}.video-upload-file progress::-webkit-progress-value{background:#6750ff}.video-upload-file progress::-moz-progress-bar{background:#6750ff}.video-spinner{display:inline-block;width:13px;height:13px;border:2px solid #d9d0ff;border-top-color:#6750ff;border-radius:50%;animation:video-spin .8s linear infinite}.video-upload-error{color:#df5c65;font-size:12px;line-height:1.6;margin:8px 0 0;overflow-wrap:anywhere}.video-file-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:7px}.video-file-actions button{font-size:12px;border:0;background:none;color:#6049ff;padding:0;cursor:pointer}.video-saved-note{display:flex;gap:7px;align-items:center;color:#858096;font-size:12px;margin:14px 0 0}.video-saved-note svg{width:15px;height:15px}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}@keyframes video-spin{to{transform:rotate(360deg)}}
@media(max-width:600px){.video-upload-head{padding:18px 18px 12px}.video-upload-body{padding:0 18px 14px}.video-upload-foot{padding:14px 18px}.video-upload-head h2{font-size:20px}.video-upload-drop{padding:18px 10px}.video-upload-drop strong{font-size:14px}.video-requirements{font-size:11px}.video-requirements th,.video-requirements td{padding:8px 4px}.video-limits{gap:5px}.video-limits span{font-size:10px;gap:3px;padding:9px 3px}.video-limits svg{width:14px;height:14px}.video-file-top{gap:9px;flex-wrap:wrap}.video-file-cover{width:96px}.video-file-info strong{font-size:13px}.video-file-state{margin-left:auto}.video-batch-head strong{font-size:14px}.video-upload-files{max-height:340px}.video-upload-foot>span{font-size:12px}}@media(prefers-reduced-motion:reduce){.video-spinner,.spinning{animation:none}}
</style>
