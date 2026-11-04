<template>
  <section class="carousel-library-upload">
    <header class="carousel-upload-head"><h2 id="asset-modal-title">上传图文</h2><button class="close-button" type="button" aria-label="关闭" :disabled="busy" @click="close"><AssetIcon name="close" /></button></header>
    <div ref="body" class="carousel-upload-body">
      <p class="carousel-intro">上传图片、音频并填写信息，创建图文素材，完成后将保存至图文库。</p>
      <template v-if="!result">
        <div class="form-field">
          <label for="library-carousel-name">图文名称 <em>*</em></label>
          <div class="counted-field"><input id="library-carousel-name" v-model="name" aria-label="图文名称" maxlength="50" :disabled="creating" :aria-invalid="!!nameError" placeholder="请输入名称，最多50个字" @blur="nameTouched = true" /><span>{{ name.length }}/50</span></div>
          <p v-if="nameError" class="error" role="alert">{{ nameError }}</p>
        </div>
        <div class="form-field">
          <label for="library-carousel-description">图文作品描述（选填）</label>
          <div class="counted-field multiline"><textarea id="library-carousel-description" v-model="description" aria-label="图文作品描述" maxlength="1000" :disabled="creating" :aria-invalid="!!descriptionError" placeholder="可补充作品介绍，最多1000个字" /><span>{{ description.length }}/1000</span></div>
          <p v-if="descriptionError" class="error" role="alert">{{ descriptionError }}</p>
        </div>
        <div class="form-field images-field" @dragover.prevent @drop.prevent="chooseImages($event.dataTransfer?.files)">
          <div class="field-heading"><label>图片 <em>*</em><small>{{ images.length }}/{{ assetFileRules.carouselMaxImages }}张 · 拖动可调整顺序</small></label><button class="choose-button" type="button" :disabled="creating" @click="imageInput?.click()"><AssetIcon name="plus" />{{ images.length ? '继续添加' : '选择图片' }}</button></div>
          <input ref="imageInput" class="sr-only" type="file" accept=".jpg,.jpeg,.png" multiple aria-label="选择图文图片" :disabled="creating" @change="imagesChanged" />
          <div v-if="!images.length" class="empty-images"><AssetIcon name="image" /><span>添加1—{{ assetFileRules.carouselMaxImages }}张图片，按顺序组成图文</span></div>
          <div v-else class="image-list" role="list" aria-label="已选图文图片">
            <article v-for="(entry, index) in images" :key="entry.key" role="listitem" :draggable="!busy" tabindex="0" :aria-label="`图片 ${index + 1}：${entry.file.name}`" @dragstart="dragStart($event, index)" @dragend="dragging = -1" @dragover.prevent @drop.stop.prevent="dropImage($event, index)" @keydown.alt.left.prevent="moveImage(index, index - 1)" @keydown.alt.right.prevent="moveImage(index, index + 1)">
              <div class="image-thumb"><img :src="entry.url" :alt="entry.file.name" /><span class="image-number">{{ index + 1 }}</span><button type="button" :disabled="busy" :aria-label="`移除图片 ${index + 1}`" @click="removeImage(index)">×</button></div>
              <p v-if="!allImagesStored || entryError(entry)" class="file-status" :class="{ uploaded: stored(entry), failed: !!entryError(entry) }"><span>{{ phase(entry) }}</span><button v-if="entryError(entry) && entry.row?.valid !== false" type="button" :disabled="busy" @click="retryUpload(entry)">重试</button></p>
              <progress v-if="entry.row?.busy" :value="entry.row.progress" max="100" :aria-label="`${entry.file.name}上传进度`" />
              <p v-if="entryError(entry)" class="error" role="alert">{{ entryError(entry) }}</p>
            </article>
          </div>
          <p v-if="allImagesStored" class="image-complete"><AssetIcon name="check-circle" />{{ images.length }}张图片已上传并检查。</p>
          <p v-if="imageError" class="error" role="alert">{{ imageError }}</p>
          <p class="image-requirements">JPG / PNG / JPEG · 单张≤10M<br />宽高比9:16—16:9 · 宽度360—2560px</p>
        </div>
        <div class="audio-field" @dragover.prevent @drop.prevent="chooseAudio($event.dataTransfer?.files)">
          <label>音频 <em>*</em></label><div class="audio-content">
            <input ref="audioInput" class="sr-only" type="file" accept=".mp3,.m4a" aria-label="选择图文音频" :disabled="busy" @change="audioChanged" />
            <button v-if="!audio" class="choose-button" type="button" :disabled="busy" @click="audioInput?.click()"><AssetIcon name="audio" />选择音频</button>
            <div v-else class="audio-file"><AssetIcon name="audio" /><span class="audio-filename" :title="audio.file.name">{{ audio.file.name }} · {{ fileSize(audio.file.size) }}<template v-if="audio.row?.metadata?.duration"> · {{ duration(audio.row.metadata.duration) }}</template></span><span class="file-status" :class="{ uploaded: stored(audio), failed: !!entryError(audio) }"><AssetIcon v-if="stored(audio)" name="check-circle" />{{ phase(audio) }}</span><button v-if="entryError(audio) && audio.row?.valid !== false" type="button" :disabled="busy" @click="retryUpload(audio)">重试</button><button type="button" :disabled="busy" @click="audioInput?.click()">更换</button><progress v-if="audio.row?.busy" :value="audio.row.progress" max="100" aria-label="音频上传进度" /></div>
            <details class="audio-requirements"><summary><span v-if="!audio">MP3 / M4A · </span>查看音频要求</summary><p>{{ carouselAudioPolicy.hint }}</p></details>
            <p v-if="audioError || audio && entryError(audio)" class="error" role="alert">{{ audioError || audio && entryError(audio) }}</p>
          </div>
        </div>
        <label class="aigc"><input v-model="isAigc" type="checkbox" :disabled="busy || !!images.length || !!audio" />包含 AI 生成内容</label>
        <p v-if="createError" class="error" role="alert">{{ createError }}</p>
      </template>
      <div v-else class="carousel-created" aria-live="polite">
        <AssetIcon :name="reviewState === '已入库' ? 'check-circle' : reviewState === '检查未通过' ? 'warn-fill' : 'refresh'" :class="{ spinning: reviewState === '检查中' }" />
        <h3>{{ reviewState === '已入库' ? '1套图文已入库' : reviewState === '检查未通过' ? '图文检查未通过' : '图文已创建，检查中' }}</h3>
        <div class="created-summary"><img :src="images[0]?.url" alt="图文封面" /><div><strong>{{ result.name }}</strong><p>{{ images.length }}张图片 · 1段音频<template v-if="description.trim()"> · 已填写作品描述</template></p></div></div>
        <p v-if="reviewState === '检查未通过'" class="error">请调整文件后重新创建。</p>
        <p v-if="refreshError" class="error" role="alert">暂时无法获取检查结果。<button type="button" @click="refresh">重试</button></p>
      </div>
    </div>
    <footer class="carousel-upload-foot">
      <span class="footer-state" :class="{ complete: canCreate || reviewState === '已入库' }"><AssetIcon :name="canCreate || reviewState === '已入库' ? 'check-circle' : 'info-line'" />{{ result ? reviewState === '已入库' ? '图文已保存至图文库' : '图文已保存' : busy ? creating ? '正在创建图文…' : '正在上传文件…' : canCreate ? '名称、图片和音频已齐全。' : '请填写名称，并添加图片和音频' }}</span>
      <button type="button" :disabled="busy" @click="close">{{ result ? '完成，返回图文库' : '关闭' }}</button>
      <button v-if="!result" class="primary" type="button" :disabled="!canCreate" @click="create">{{ creating ? '创建中…' : '创建图文' }}</button><button v-else class="primary" type="button" @click="reset">继续上传</button>
    </footer>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { addUploadFiles, startUploads, stopUploadRows, uploadRows, type UploadRow } from '@/features/creative/assetUploads';
import { assetFileRules, carouselAudioError, carouselAudioPolicy, carouselTextLengthError } from '@/features/creative/assetUploadTypes';
import { carouselImageError } from '@/features/creative/carouselLibraryUpload';
import { createCarousel, getCarousel, type CarouselDetail } from '@/features/creative/creativeApi';
import { duration, fileSize } from './assetFormat';
import AssetIcon from './AssetIcon.vue';

interface Entry { key: string; file: File; url: string; isAigc: boolean; queued: boolean; row?: UploadRow; error: string }
const props = defineProps<{ accountId?: string }>();
const emit = defineEmits<{ close: []; changed: []; closeBlocked: [value: boolean] }>();
const name = ref(''), description = ref(''), nameTouched = ref(false), isAigc = ref(false);
const images = ref<Entry[]>([]), audio = ref<Entry>(), textEntry = ref<Entry>(), result = ref<CarouselDetail>();
const imageError = ref(''), audioError = ref(''), createError = ref(''), refreshError = ref(false);
const imageInput = ref<HTMLInputElement>(), audioInput = ref<HTMLInputElement>(), body = ref<HTMLElement>();
const pumping = ref(false), creating = ref(false);
const scope = crypto.randomUUID(), token = localStorage.getItem('lynx.token'), controller = new AbortController();
const targetAccounts = props.accountId ? [props.accountId] : [];
let alive = true, dragging = -1, requestId = '', signature = '', polling = false, revision = 0;
const busy = computed(() => pumping.value || creating.value);
const nameError = computed(() => carouselTextLengthError(name.value, 'name') || (nameTouched.value && !name.value.trim() ? '请输入图文名称' : ''));
const descriptionError = computed(() => carouselTextLengthError(description.value, 'text'));
const stored = (entry: Entry) => entry.row?.result?.storageStatus === 'STORED';
const entryError = (entry: Entry) => entry.error || entry.row?.error || (entry.row?.result?.storageStatus === 'FAILED' ? '文件入库失败，请重试' : '');
const allImagesStored = computed(() => !!images.value.length && images.value.every(stored));
const canCreate = computed(() => !busy.value && !result.value && !!name.value.trim() && !nameError.value && !descriptionError.value && allImagesStored.value && !!audio.value && stored(audio.value));
const reviewState = computed(() => !result.value ? '' : ['APPROVED', 'NOT_REQUIRED'].includes(result.value.aiReview.status) ? '已入库' : ['REJECTED', 'MANUAL_REVIEW', 'FAILED'].includes(result.value.aiReview.status) ? '检查未通过' : '检查中');
watch(busy, value => emit('closeBlocked', value), { immediate: true, flush: 'sync' });
function check() { if (!alive || token !== localStorage.getItem('lynx.token')) throw new DOMException('上传已停止', 'AbortError'); }
function entry(file: File): Entry { return { key: crypto.randomUUID(), file, url: file.type === 'text/plain' ? '' : URL.createObjectURL(file), isAigc: isAigc.value, queued: true, error: '' }; }
function entries() { return [...images.value, ...(audio.value ? [audio.value] : []), ...(textEntry.value ? [textEntry.value] : [])]; }
function release(value?: Entry) { if (!value) return; if (value.url) URL.revokeObjectURL(value.url); if (value.row) { const index = uploadRows.findIndex(row => row.key === value.row!.key); if (index >= 0) uploadRows.splice(index, 1); } }
function phase(value: Entry) { return entryError(value) ? '上传失败' : stored(value) ? '已上传' : value.row?.progress === 100 ? '检查中' : value.row?.busy && value.row.stage !== '正在本地预检' ? `上传中 ${value.row.progress}%` : '检查中'; }
async function pump() {
  if (pumping.value) return;
  pumping.value = true;
  try {
    for (let pending = entries().filter(e => e.queued); pending.length; pending = entries().filter(e => e.queued)) {
      pending.forEach(e => { e.queued = false; e.error = ''; });
      const errors = await addUploadFiles(pending.map(e => e.file), 'CAROUSEL', scope, controller.signal); check();
      for (const item of pending) {
        item.row = uploadRows.find(row => row.scope === scope && row.file === item.file);
        if (!item.row) { item.error = errors.join('；') || '文件上传失败，请重试'; continue; }
        item.row.isAigc = item.isAigc;
        if (images.value.includes(item) && item.row.valid) { const error = carouselImageError(item.file, item.row.metadata); if (error) { item.row.valid = false; item.row.error = error; } }
      }
      const keys = pending.filter(e => e.row?.valid && !stored(e)).map(e => e.row!.key);
      if (keys.length) await startUploads([], false, 'CAROUSEL', keys);
      check();
    }
  } catch { if (alive) for (const item of entries().filter(e => !stored(e) && !entryError(e))) item.error = '暂时无法上传文件，请重试。'; }
  finally { pumping.value = false; }
}
function chooseImages(files?: FileList | null) {
  if (creating.value || result.value) return;
  imageError.value = '';
  for (const file of Array.from(files || [])) {
    if (!/\.(jpe?g|png)$/i.test(file.name)) { imageError.value = '图片仅支持 JPG、PNG、JPEG 格式'; continue; }
    if (images.value.some(e => e.file.name === file.name && e.file.size === file.size && e.file.lastModified === file.lastModified)) continue;
    if (images.value.length >= assetFileRules.carouselMaxImages) { imageError.value = `最多选择${assetFileRules.carouselMaxImages}张图片`; break; }
    images.value.push(entry(file));
  }
  void pump();
}
function chooseAudio(files?: FileList | null) {
  if (busy.value || result.value || !files?.length) return;
  audioError.value = files.length === 1 ? carouselAudioError(files[0]!) : '请选择一个音频文件';
  if (audioError.value) return;
  release(audio.value); audio.value = entry(files[0]!); void pump();
}
function imagesChanged(event: Event) { const input = event.target as HTMLInputElement; chooseImages(input.files); input.value = ''; }
function audioChanged(event: Event) { const input = event.target as HTMLInputElement; chooseAudio(input.files); input.value = ''; }
function retryUpload(value: Entry) { if (!busy.value) { if (value.row?.result?.storageStatus === 'FAILED') value.row.result = undefined; value.queued = true; value.error = ''; void pump(); } }
function removeImage(index: number) { if (!busy.value) { release(images.value[index]); images.value.splice(index, 1); imageError.value = ''; } }
function moveImage(from: number, to: number) { if (!busy.value && from >= 0 && from < images.value.length && to >= 0 && to < images.value.length) images.value.splice(to, 0, images.value.splice(from, 1)[0]!); }
function dragStart(event: DragEvent, index: number) { if (busy.value) { event.preventDefault(); return; } dragging = index; event.dataTransfer?.setData('text/plain', String(index)); }
function dropImage(event: DragEvent, index: number) { if (event.dataTransfer?.files.length) chooseImages(event.dataTransfer.files); else moveImage(dragging, index); dragging = -1; }
async function create() {
  if (!canCreate.value) return;
  creating.value = true; createError.value = '';
  try {
    const text = description.value.trim();
    if (text && (!textEntry.value || await textEntry.value.file.text() !== text)) { release(textEntry.value); textEntry.value = entry(new File([text], '图文作品描述.txt', { type: 'text/plain' })); }
    if (!text) { release(textEntry.value); textEntry.value = undefined; }
    if (textEntry.value && !stored(textEntry.value)) await pump(); check();
    if (textEntry.value && !stored(textEntry.value)) throw new Error('description');
    const imageIds = images.value.map(e => e.row!.result!.id);
    if (new Set(imageIds).size !== imageIds.length) { imageError.value = '图片内容重复，请移除重复图片'; return; }
    const payload = { name: name.value.trim(), ...(textEntry.value ? { textAssetId: textEntry.value.row!.result!.id } : {}), imageAssetIds: imageIds, audioAssetId: audio.value!.row!.result!.id, accountIds: targetAccounts };
    const current = JSON.stringify(payload); if (signature !== current) { signature = current; requestId = crypto.randomUUID(); }
    result.value = await createCarousel({ requestId, ...payload }); check();
    emit('changed'); await nextTick(); body.value?.scrollTo({ top: 0 });
  } catch { if (alive) createError.value = textEntry.value && !stored(textEntry.value) ? '描述上传失败，请重试。' : '暂时无法创建图文，请重试。'; }
  finally { creating.value = false; }
}
async function refresh() {
  if (!result.value || polling) return;
  const id = result.value.id, current = revision; polling = true;
  try { const updated = await getCarousel(id, controller.signal); check(); if (revision === current && result.value?.id === id) { result.value = updated; refreshError.value = false; emit('changed'); } }
  catch { if (alive && revision === current) refreshError.value = true; }
  finally { polling = false; }
}
function close() { if (!busy.value) emit('close'); }
function reset() {
  if (busy.value) return;
  entries().forEach(release); revision++; images.value = []; audio.value = undefined; textEntry.value = undefined; result.value = undefined;
  name.value = ''; description.value = ''; nameTouched.value = false; isAigc.value = false; imageError.value = ''; audioError.value = ''; createError.value = ''; refreshError.value = false; signature = ''; requestId = ''; dragging = -1;
}
const timer = setInterval(() => { if (reviewState.value === '检查中') void refresh(); }, 4000);
onBeforeUnmount(() => { alive = false; controller.abort(); clearInterval(timer); void stopUploadRows(uploadRows.filter(row => row.scope === scope && !row.result).map(row => row.key)); entries().forEach(release); emit('closeBlocked', false); });
</script>
<style scoped>
.carousel-library-upload{display:flex;flex-direction:column;min-height:0;color:#25233f;font-size:13px}.carousel-upload-head{display:flex;justify-content:space-between;align-items:center;gap:16px;padding:20px 24px 8px;flex:none}.carousel-upload-head h2{margin:0;font-size:22px;font-weight:700;line-height:1.4}.carousel-library-upload button{font:inherit;cursor:pointer}.carousel-library-upload button:disabled{opacity:.45;cursor:not-allowed}.carousel-library-upload svg{width:18px;height:18px;flex:none}.close-button{display:grid;place-items:center;width:30px;height:30px;background:white;border:0;border-radius:7px;color:#777294}.carousel-upload-body{min-height:0;overflow-y:auto;padding:0 24px 12px}.carousel-intro{font-size:13px;line-height:1.6;color:#77718e;margin:0 0 20px}.form-field{margin-bottom:20px}.form-field>label,.field-heading>label,.audio-field>label{font-weight:600;font-size:14px}em{font-style:normal;color:#ee425c;margin-left:4px}.counted-field{position:relative;margin-top:7px}.counted-field input,.counted-field textarea{box-sizing:border-box;width:100%;border:1px solid #dcd8ef;border-radius:7px;background:white;color:#34304d;font:inherit;padding:9px 65px 9px 12px;line-height:20px}.counted-field input{height:38px}.counted-field textarea{display:block;height:76px;min-height:76px;resize:vertical;padding-bottom:28px}.counted-field ::placeholder{color:#a5a0b7}.counted-field>span{position:absolute;right:12px;bottom:9px;color:#9891ae;font-size:12px;pointer-events:none}.field-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:8px}.field-heading small{font-weight:400;color:#827b9d;margin-left:14px;font-size:12px}.choose-button{display:inline-flex;align-items:center;gap:6px;padding:7px 12px;background:white;border:1px solid #7865ff;border-radius:6px;color:#5940ff;white-space:nowrap}.empty-images{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:10px;min-height:92px;border:1px dashed #d4caff;border-radius:8px;background:#faf8ff;color:#8c84a4;text-align:center;padding:12px}.empty-images svg{color:#c6b9f4;width:34px;height:34px}.image-list{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.image-list article{min-width:0}.image-list article[draggable=true]{cursor:grab}.image-thumb{position:relative;aspect-ratio:1.6;border:1px solid #e0dbf2;border-radius:8px;overflow:hidden;background:#f7f5ff}.image-thumb img{display:block;width:100%;height:100%;object-fit:contain}.image-number{position:absolute;top:5px;left:5px;display:grid;place-items:center;min-width:22px;height:22px;padding:0 3px;border-radius:6px;background:#6244ff;color:white;font-size:12px}.image-thumb button{position:absolute;right:5px;top:5px;border:0;border-radius:50%;width:22px;height:22px;background:#fffffff0;font-size:20px;color:#514b65;line-height:20px}.image-complete{display:flex;align-items:center;gap:6px;color:#20a358;font-size:12px;margin:7px 0 0}.image-requirements{margin:8px 0 0;color:#817998;line-height:1.8;font-size:12px}.file-status{display:flex;align-items:center;gap:5px;font-size:12px;margin:6px 0;color:#7968cc}.file-status.uploaded{color:#20a358}.file-status.failed{color:#dc5966}.file-status button,.audio-file button,.error button{border:0;background:none;color:#5940ff;padding:0}.file-status svg{width:15px;height:15px}.audio-field{display:flex;align-items:flex-start;gap:18px;margin:22px 0 10px}.audio-field>label{padding-top:8px;flex:none}.audio-content{display:flex;align-items:center;gap:12px;flex-wrap:wrap;min-width:0;flex:1}.audio-file{display:flex;align-items:center;gap:10px;flex:1;min-width:0;padding:9px 12px;border:1px solid #e4def9;border-radius:8px;background:#f8f6ff}.audio-file>svg{color:#6041ff}.audio-filename{overflow-wrap:anywhere;min-width:0;flex:1;line-height:1.6;font-size:12px}.audio-file .file-status{margin:0;white-space:nowrap}.audio-file button{white-space:nowrap}.audio-requirements{font-size:12px;color:#5b43ff}.audio-requirements summary{cursor:pointer;list-style:none}.audio-requirements summary::-webkit-details-marker{display:none}.audio-requirements summary span{color:#817998}.audio-requirements p{margin:8px 0;color:#817998;line-height:1.7}.audio-content>.error{width:100%}.aigc{display:flex;align-items:center;gap:7px;font-size:12px;color:#827b98;margin:14px 0 0}.aigc input{accent-color:#6244ff;margin:0}.error{color:#dc5966;font-size:12px;line-height:1.6;margin:6px 0 0;overflow-wrap:anywhere}progress{display:block;width:100%;height:5px;accent-color:#6244ff;margin-top:5px}.audio-file:has(progress){flex-wrap:wrap}.carousel-upload-foot{display:flex;align-items:center;justify-content:flex-end;gap:12px;padding:14px 24px;border-top:1px solid #e4dff4;flex:none;background:white}.carousel-upload-foot>button{border:1px solid #ddd7ef;border-radius:7px;background:white;padding:9px 14px;color:#3d3557;white-space:nowrap}.carousel-upload-foot>button.primary{color:white;background:#6244ff;border-color:#6244ff}.footer-state{display:flex;align-items:center;gap:7px;margin-right:auto;min-width:0;font-size:12px;color:#77708e;line-height:1.5}.footer-state.complete{color:#238746}.carousel-created{text-align:center;padding:22px 0}.carousel-created>svg{width:36px;height:36px;color:#6244ff}.carousel-created h3{font-size:20px;margin:14px 0 22px}.created-summary{display:flex;align-items:center;gap:16px;background:#f7f5ff;border:1px solid #e4def9;border-radius:10px;padding:14px;text-align:left}.created-summary img{width:74px;height:74px;object-fit:contain;border-radius:7px}.created-summary strong{font-size:14px;overflow-wrap:anywhere}.created-summary p{color:#827b98;font-size:12px;margin-top:7px}.spinning{animation:carousel-spin .9s linear infinite}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}input:focus-visible,textarea:focus-visible,button:focus-visible,article:focus-visible,summary:focus-visible{outline:2px solid #8b79ff;outline-offset:2px}@keyframes carousel-spin{to{transform:rotate(360deg)}}
@media(max-width:600px){.carousel-upload-head{padding:18px 16px 8px}.carousel-upload-body{padding:0 16px 12px}.carousel-upload-foot{padding:12px 16px;gap:8px;flex-wrap:wrap}.footer-state{flex-basis:100%}.carousel-upload-head h2{font-size:20px}.field-heading{flex-wrap:wrap}.field-heading small{margin-left:8px;font-size:11px}.image-list{gap:8px}.image-thumb{aspect-ratio:1}.image-number,.image-thumb button{width:20px;min-width:20px;height:20px}.audio-field{gap:12px}.audio-file{flex-wrap:wrap;gap:8px}.audio-filename{flex-basis:calc(100% - 28px)}.audio-file .file-status{margin-left:auto}.form-field{margin-bottom:18px}}
@media(prefers-reduced-motion:reduce){.spinning{animation:none}}
</style>
