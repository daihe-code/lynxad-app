<template>
  <div class="carousel-local-upload">
    <div class="form-field"><label for="local-carousel-name">图文名称 <em>*</em></label><div class="counted-field"><input id="local-carousel-name" v-model="name" aria-label="图文名称" :maxlength="carouselTextLimits.name" :disabled="locked" :aria-invalid="!!nameError" placeholder="请输入图文名称，最多50个汉字" @blur="nameTouched = true" /><span>{{ name.length }}/50</span></div><p v-if="nameError" class="error" role="alert">{{ nameError }}</p></div>
    <div class="form-field"><div class="field-heading"><label for="local-carousel-description">图文作品描述（选填）</label><button type="button" class="text-button" :disabled="locked || importing" @click="textInput?.click()">导入 TXT</button></div><input ref="textInput" class="sr-only" type="file" accept=".txt" aria-label="导入图文文本" :disabled="locked || importing" @change="importText" /><div class="counted-field multiline"><textarea id="local-carousel-description" v-model="description" aria-label="图文作品描述" :maxlength="carouselTextLimits.text" :disabled="locked" placeholder="可补充图文作品的介绍或说明，最多1000个汉字" @input="textError = ''" /><span>{{ description.length }}/1000</span></div><p v-if="textError" class="error" role="alert">{{ textError }}</p></div>
    <div class="form-field" @dragover.prevent @drop.prevent="chooseImages($event.dataTransfer?.files)">
      <div class="field-heading"><label>图片 <em>*</em><small>{{ images.length }}/{{ assetFileRules.carouselMaxImages }}张 · 拖动可调整顺序</small></label><div class="image-actions"><label class="aigc"><input v-model="isAigc" type="checkbox" :disabled="locked" />包含 AI 生成内容</label><button type="button" class="choose-images" :disabled="locked" @click="imageInput?.click()"><ReferenceIcon name="plus" />{{ images.length ? '继续添加' : '选择图片' }}</button></div></div>
      <input ref="imageInput" class="sr-only" type="file" accept=".jpg,.jpeg,.png" multiple aria-label="选择图文图片" :disabled="locked" @change="imagesChanged" />
      <div v-if="!images.length" class="empty-images"><ReferenceIcon name="image" /><span>暂无图片，请从右侧选择，最多{{ assetFileRules.carouselMaxImages }}张</span></div>
      <div v-else class="image-list" role="list" aria-label="已选图文图片"><article v-for="(entry, index) in images" :key="entry.key" role="listitem" :draggable="!locked && !pumping" :aria-label="`图片 ${index + 1}：${entry.file.name}`" tabindex="0" @dragstart="dragStart($event, index)" @dragend="dragging = -1" @dragover.prevent @drop.stop.prevent="dropImage($event, index)" @keydown.alt.left.prevent="moveImage(index, index - 1)" @keydown.alt.right.prevent="moveImage(index, index + 1)"><div class="image-thumb"><img :src="entry.url" :alt="entry.file.name" /><span class="image-number">{{ index + 1 }}</span><button type="button" :disabled="locked || pumping" :aria-label="`移除图片 ${index + 1}`" @click="removeImage(index)">×</button></div><p class="file-status" :class="{ uploaded: stored(entry), failed: !!entryError(entry) }"><span>{{ stored(entry) ? '✓ 已上传' : phase(entry) }}</span><button v-if="entryError(entry)" type="button" :disabled="locked || pumping" @click="retryUpload(entry)">重试</button></p><progress v-if="entry.row?.busy" :value="entry.row.progress" max="100" :aria-label="`${entry.file.name}上传进度`" /><small v-if="entry.isAigc" class="file-aigc">AI 生成</small><p v-if="entryError(entry)" class="error" role="alert">{{ entryError(entry) }}</p></article></div>
      <p v-if="imageError" class="error" role="alert">{{ imageError }}</p><details class="requirements"><summary>查看图片要求</summary><p>支持jpg，png，jpeg等图片格式，9:16≤宽高比≤16:9，360px≤宽度≤2560px，单个图片大小不超过10M</p></details>
    </div>
    <div class="form-field audio-field" @dragover.prevent @drop.prevent="chooseAudio($event.dataTransfer?.files)"><div class="field-heading"><label>音频 <em>*</em></label><div class="audio-content"><input ref="audioInput" class="sr-only" type="file" accept=".mp3,.m4a" aria-label="选择图文音频" :disabled="locked" @change="audioChanged" /><button type="button" class="choose-audio" :disabled="locked" @click="audioInput?.click()"><ReferenceIcon name="music" />{{ audio ? '更换音频' : '选择音频' }}</button><button class="listen-button" type="button" :aria-label="playing ? '暂停试听' : '试听音频'" :disabled="!audio || !!entryError(audio)" @click="toggleAudio"><ReferenceIcon :name="playing ? 'pause' : 'play'" /></button><span v-if="!audio" class="audio-hint">选中后自动上传</span><div v-else class="audio-file"><strong>{{ audio.file.name }}</strong><small>{{ fileSize(audio.file.size) }}<template v-if="audio.row?.metadata?.duration"> · {{ duration(audio.row.metadata.duration) }}</template></small><span class="file-status" :class="{ uploaded: stored(audio), failed: !!entryError(audio) }">{{ stored(audio) ? '✓ 已上传' : phase(audio) }}</span><button v-if="entryError(audio)" type="button" :disabled="locked || pumping" @click="retryUpload(audio)">重试</button><progress v-if="audio.row?.busy" :value="audio.row.progress" max="100" aria-label="音频上传进度" /></div></div></div><audio v-if="audio" ref="audioElement" :src="audio.url" preload="metadata" @play="playing = true" @pause="playing = false" @ended="playing = false" /><p v-if="audioError || audio && entryError(audio)" class="error" role="alert">{{ audioError || audio && entryError(audio) }}</p><details class="requirements"><summary>查看音频要求</summary><p>{{ carouselAudioPolicy.hint }}</p></details></div>
    <p v-if="createError" class="error" role="alert">{{ createError }}</p>
    <div v-if="result && !ready" class="component-statuses" aria-live="polite"><div v-for="part in result.parts" :key="`${part.role}:${part.position}`"><span>{{ part.role === 'TEXT' ? '描述' : part.role === 'AUDIO' ? '音频' : `图片 ${part.position + 1}` }} · {{ reviewLabel(part.asset.aiReview?.status) }}</span><button v-if="part.asset.aiReview?.status === 'FAILED'" type="button" :disabled="!!retrying" @click="retryPart(part.asset.id)">重试检查</button><p v-if="part.asset.aiReview?.errorMessage" class="error">{{ part.asset.aiReview.errorMessage }}</p><p v-for="reason in part.asset.aiReview?.reasons || []" :key="reason" class="error">{{ reason }}</p><div v-for="sync in part.asset.syncs.filter(s => s.accountId === accountId)" :key="sync.id"><span>{{ syncLabel(sync.status, sync.reviewStatus) }}</span><AssetSyncAction :sync="sync" :busy="retrying === sync.id" :can-retry="['APPROVED','NOT_REQUIRED'].includes(part.asset.aiReview?.status || '')" @retry="retrySync(part.asset.id, sync)" @verify="verifySync(part.asset.id, sync)" /></div></div><button v-if="refreshError" type="button" @click="refresh">刷新状态</button></div>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import AssetSyncAction from '@/pages/creatives/AssetSyncAction.vue';
import { assetFileRules, carouselAudioPolicy, carouselAudioError, carouselTextLimits, carouselTextLengthError, libraryFileError } from '@/features/creative/assetUploadTypes';
import { addUploadFiles, startUploads, stopUploadRows, uploadRows, reviewLabel, syncLabel, type UploadRow } from '@/features/creative/assetUploads';
import { createCarousel, getCarousel, getUploadPolicy, retryAssetReview, retryAssetSync, reconcileAssetSync, type CarouselDetail, type AssetSync, type LibraryItemVo } from '@/features/creative/creativeApi';
import { carouselReady, carouselItem } from '@/features/creative/materialSources';
import { carouselPending } from '@/features/creative/carouselUploads';
import { decodeText, preflight } from '@/features/creative/assetPreflight';
import { duration, fileSize } from '@/pages/creatives/assetFormat';
interface Entry { key: string; file: File; url: string; isAigc: boolean; queued: boolean; row?: UploadRow; error: string }
export interface CarouselFormState { canCreate: boolean; processing: boolean; summary: string; created: boolean }
const props = defineProps<{ accountId: string; busy: boolean }>();
const emit = defineEmits<{ state: [value: CarouselFormState]; ready: [item: LibraryItemVo] }>();
const name = ref(''), description = ref(''), nameTouched = ref(false), isAigc = ref(false), textError = ref(''), imageError = ref(''), audioError = ref(''), createError = ref('');
const images = ref<Entry[]>([]), audio = ref<Entry>(), textEntry = ref<Entry>(), result = ref<CarouselDetail>();
const imageInput = ref<HTMLInputElement>(), audioInput = ref<HTMLInputElement>(), textInput = ref<HTMLInputElement>(), audioElement = ref<HTMLAudioElement>();
const pumping = ref(false), creating = ref(false), importing = ref(false), retrying = ref(''), playing = ref(false), refreshError = ref(false);
const scope = crypto.randomUUID(), token = localStorage.getItem('lynx.token'), controller = new AbortController();
let alive = true, requestId = '', signature = '', emitted = '', dragging = -1, polling = false;
const locked = computed(() => props.busy || creating.value || !!result.value || !!retrying.value);
const nameError = computed(() => carouselTextLengthError(name.value, 'name') || (nameTouched.value && !name.value.trim() ? '请输入图文名称' : ''));
const ready = computed(() => carouselReady(result.value, props.accountId));
const stored = (entry: Entry) => entry.row?.result?.storageStatus === 'STORED';
const entryError = (entry: Entry) => entry.error || entry.row?.error || '';
const phase = (entry: Entry) => entry.row?.stage === '正在本地预检' ? '检查中' : entry.row?.busy ? `上传中 ${entry.row.progress}%` : entryError(entry) ? '上传失败' : '待上传';
const state = computed<CarouselFormState>(() => ({
  canCreate: !!name.value.trim() && !nameError.value && !textError.value && !carouselTextLengthError(description.value, 'text') && !!images.value.length && images.value.every(stored) && !!audio.value && stored(audio.value) && !pumping.value && !creating.value && !result.value && !importing.value,
  processing: pumping.value || creating.value || importing.value || !!retrying.value || !!result.value && !ready.value && carouselPending(result.value),
  created: !!result.value,
  summary: result.value ? ready.value ? '图文已就绪，正在添加至当前商品。' : '图文已创建，正在检查与同步。' : pumping.value ? '正在上传所选文件…' : name.value.trim() && images.value.length && images.value.every(stored) && audio.value && stored(audio.value) ? `图文名称、${images.value.length}张图片和音频已就绪` : '请填写图文名称，并添加图片和音频；本地文件选中后自动上传。'
}));
watch(state, value => emit('state', value), { immediate: true });
watch(ready, value => { if (value && result.value && emitted !== result.value.id) { emitted = result.value.id; emit('ready', carouselItem(result.value)); } });
function check() { if (!alive || token !== localStorage.getItem('lynx.token')) throw new DOMException('上传已停止', 'AbortError'); }
function message(error: unknown) { const e = error as { message?: string; response?: { data?: { message?: string } } }; return e.response?.data?.message || e.message || '操作失败，请重试'; }
function entry(file: File): Entry { return { key: crypto.randomUUID(), file, url: file.name.toLowerCase().endsWith('.txt') ? '' : URL.createObjectURL(file), isAigc: isAigc.value, queued: true, error: '' }; }
function allEntries() { return [...images.value, ...(audio.value ? [audio.value] : []), ...(textEntry.value ? [textEntry.value] : [])]; }
function release(value?: Entry) { if (!value) return; if (value.url) URL.revokeObjectURL(value.url); if (value.row) { const index = uploadRows.findIndex(row => row.key === value.row?.key); if (index >= 0) uploadRows.splice(index, 1); } }
async function pump() {
  if (pumping.value) return;
  pumping.value = true;
  try {
    for (let pending = allEntries().filter(e => e.queued); pending.length; pending = allEntries().filter(e => e.queued)) {
      pending.forEach(e => { e.queued = false; e.error = ''; });
      const errors = await addUploadFiles(pending.map(e => e.file), 'CAROUSEL', scope, controller.signal); check();
      for (const item of pending) {
        item.row = uploadRows.find(row => row.scope === scope && row.file === item.file);
        if (item.row) item.row.isAigc = item.isAigc; else item.error = errors.join('；') || '文件未能上传，请重试';
      }
      const valid = pending.filter(e => e.row?.valid && !e.row.result).map(e => e.row!.key);
      if (valid.length) await startUploads([], false, 'CAROUSEL', valid);
      check();
    }
  } catch (error) { if (alive) for (const item of allEntries().filter(e => !stored(e) && !entryError(e))) item.error = message(error); }
  finally { pumping.value = false; }
}
function chooseImages(files?: FileList | null) {
  if (locked.value) return;
  imageError.value = '';
  for (const file of Array.from(files || [])) {
    const error = libraryFileError(file.name, 'IMAGE'); if (error) { imageError.value = error; continue; }
    if (images.value.some(e => e.file.name === file.name && e.file.size === file.size && e.file.lastModified === file.lastModified)) continue;
    if (images.value.length >= assetFileRules.carouselMaxImages) { imageError.value = `最多选择 ${assetFileRules.carouselMaxImages} 张图片`; break; }
    images.value.push(entry(file));
  }
  void pump();
}
function chooseAudio(files?: FileList | null) {
  if (locked.value || !files?.length) return;
  audioError.value = files.length === 1 ? carouselAudioError(files[0]!) : '请选择一个音频文件';
  if (audioError.value) return;
  if (pumping.value && audio.value && !stored(audio.value)) { audioError.value = '当前音频正在上传，请完成后更换'; return; }
  audioElement.value?.pause(); release(audio.value); audio.value = entry(files[0]!); void pump();
}
function imagesChanged(event: Event) { const input = event.target as HTMLInputElement; chooseImages(input.files); input.value = ''; }
function audioChanged(event: Event) { const input = event.target as HTMLInputElement; chooseAudio(input.files); input.value = ''; }
function removeImage(index: number) { if (!locked.value && !pumping.value) { release(images.value[index]); images.value.splice(index, 1); imageError.value = ''; } }
function moveImage(from: number, to: number) { if (!locked.value && !pumping.value && from >= 0 && from < images.value.length && to >= 0 && to < images.value.length) images.value.splice(to, 0, images.value.splice(from, 1)[0]!); }
function dragStart(event: DragEvent, index: number) { dragging = index; event.dataTransfer?.setData('text/plain', String(index)); }
function dropImage(event: DragEvent, index: number) { if (event.dataTransfer?.files.length) chooseImages(event.dataTransfer.files); else moveImage(dragging, index); dragging = -1; }
async function toggleAudio() { if (!audioElement.value) return; if (audioElement.value.paused) { try { await audioElement.value.play(); } catch { audioError.value = '音频暂不可播放，请重新选择'; } } else audioElement.value.pause(); }
async function importText(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; input.value = ''; if (!file || locked.value) return;
  importing.value = true; textError.value = '';
  try { const invalid = libraryFileError(file.name, 'TEXT'); if (invalid) throw new Error(invalid); await preflight(file, await getUploadPolicy()); check(); const text = decodeText(new Uint8Array(await file.arrayBuffer())); const error = carouselTextLengthError(text, 'text'); if (error) throw new Error(error); description.value = text; }
  catch (error) { if (alive) textError.value = message(error); } finally { importing.value = false; }
}
async function create() {
  if (!state.value.canCreate || props.busy) return;
  creating.value = true; createError.value = ''; audioElement.value?.pause();
  try {
    const text = description.value.trim();
    if (text && (!textEntry.value || await textEntry.value.file.text() !== text)) { release(textEntry.value); textEntry.value = entry(new File([text], '图文作品描述.txt', { type: 'text/plain' })); }
    if (!text) { release(textEntry.value); textEntry.value = undefined; }
    if (textEntry.value && !stored(textEntry.value)) await pump(); check();
    if (textEntry.value && !stored(textEntry.value)) throw new Error(entryError(textEntry.value) || '描述上传失败，请重试');
    const body = { name: name.value.trim(), ...(textEntry.value ? { textAssetId: textEntry.value.row!.result!.id } : {}), imageAssetIds: [...new Set(images.value.map(e => e.row!.result!.id))], audioAssetId: audio.value!.row!.result!.id, accountIds: [props.accountId] };
    const current = JSON.stringify(body); if (current !== signature) { signature = current; requestId = crypto.randomUUID(); }
    result.value = await createCarousel({ requestId, ...body }); check();
  } catch (error) { if (alive) createError.value = message(error); }
  finally { creating.value = false; }
}
async function refresh() { if (!result.value || polling) return; polling = true; try { const updated = await getCarousel(result.value.id); check(); result.value = updated; refreshError.value = false; } catch { if (alive) refreshError.value = true; } finally { polling = false; } }
async function retryPart(id: string) { retrying.value = id; try { await retryAssetReview(id); await refresh(); } catch (e) { createError.value = message(e); } finally { retrying.value = ''; } }
async function retrySync(id: string, sync: AssetSync) { retrying.value = sync.id; try { await retryAssetSync(id, props.accountId); await refresh(); } catch (e) { createError.value = message(e); } finally { retrying.value = ''; } }
async function verifySync(id: string, sync: AssetSync) { retrying.value = sync.id; try { await reconcileAssetSync(id, props.accountId); await refresh(); } catch (e) { createError.value = message(e); } finally { retrying.value = ''; } }
function retryUpload(value: Entry) { if (!locked.value && !pumping.value) { release(value); value.row = undefined; value.queued = true; value.error = ''; void pump(); } }
const timer = setInterval(() => { if (result.value && !ready.value && carouselPending(result.value)) void refresh(); }, 3000);
onBeforeUnmount(() => { alive = false; controller.abort(); clearInterval(timer); audioElement.value?.pause(); void stopUploadRows(uploadRows.filter(row => row.scope === scope && !row.result).map(row => row.key)); allEntries().forEach(release); });
defineExpose({ create });
</script>
<style scoped>
.carousel-local-upload{color:#252440;font-size:14px}.form-field{margin:0 0 28px}.form-field>label,.field-heading>label{font-size:16px;font-weight:600;line-height:24px}.field-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px}.field-heading label small{display:inline;margin-left:18px;font-size:13px;font-weight:400;color:#8781aa}em{font-style:normal;color:#f14b65;margin-left:5px}.counted-field{position:relative;margin-top:10px}.counted-field input,.counted-field textarea{box-sizing:border-box;width:100%;border:1px solid #dedaf4;border-radius:7px;background:white;padding:11px 65px 11px 14px;color:#343153;font:inherit}.counted-field input{height:44px}.counted-field textarea{height:104px;min-height:84px;resize:vertical;padding-bottom:30px}.counted-field span{position:absolute;right:13px;bottom:11px;font-size:13px;color:#a09ab5;pointer-events:none}.counted-field ::placeholder{color:#aaa5bc}.text-button,.requirements summary,.audio-file button{color:#5b48ff}.field-heading .aigc{font-size:12px;font-weight:400;color:#8781aa;display:flex;align-items:center;gap:5px}.aigc input{accent-color:#5b48ff}.image-actions{display:flex;align-items:center;gap:16px}.choose-images,.choose-audio{display:inline-flex;align-items:center;justify-content:center;gap:9px;border:1px solid #6655ff;border-radius:7px;padding:9px 16px;color:#5b48ff;white-space:nowrap;background:white;font:inherit}.choose-images svg,.choose-audio svg{width:18px;height:18px}.empty-images{height:100px;border:1px dashed #d9d4f2;border-radius:8px;background:#faf9ff;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#9690b2;font-size:13px}.empty-images svg{width:34px;height:34px;color:#d2ccea}.image-list{display:flex;gap:18px;flex-wrap:wrap}.image-list article{width:110px;min-width:0}.image-list article[draggable=true]{cursor:grab}.image-thumb{height:100px;position:relative;border:1px solid #e1dcf7;border-radius:7px;overflow:hidden;background:#f7f6ff}.image-thumb img{width:100%;height:100%;object-fit:contain}.image-number,.image-thumb button{position:absolute;top:5px;border-radius:50%;display:grid;place-items:center;width:21px;height:21px;background:#424252b3;color:#fff;font-size:12px}.image-number{left:5px}.image-thumb button{right:5px;font-size:18px}.file-status{display:flex;align-items:center;gap:8px;font-size:12px;color:#8d86ab;margin:7px 0 0}.file-status.uploaded{color:#0bb76e}.file-status.failed{color:#dc5966}.file-status button{color:#5b48ff}.file-aigc{color:#8d86ab;font-size:11px}.requirements{font-size:13px;margin-top:10px;color:#8781aa}.requirements summary{list-style:none;cursor:pointer;font-weight:500}.requirements summary::-webkit-details-marker{display:none}.requirements p{padding:10px;border:1px solid #e2ddf9;border-radius:6px;line-height:1.7;background:#faf9ff}.audio-field>.field-heading{justify-content:flex-start;align-items:flex-start}.audio-field>.field-heading>label{padding-top:9px}.audio-content{display:flex;align-items:center;gap:14px;flex:1;min-width:0}.choose-audio{background:#efecff;border-color:#ded6ff;font-size:13px;font-weight:600}.listen-button{border:1px solid #e3dff6;border-radius:50%;width:30px;height:30px;display:grid;place-items:center;color:#7d74b3;flex-shrink:0}.listen-button svg{width:16px;height:16px}.audio-hint{color:#8781aa;font-size:13px}.audio-field audio{display:none}.audio-file{min-width:0;flex:1;display:flex;align-items:center;gap:10px;flex-wrap:wrap}.audio-file strong{max-width:250px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px;font-weight:500}.audio-file small{font-size:12px;color:#8781aa}.audio-file .file-status{margin:0}.audio-field>.requirements{margin-left:75px}.error{color:#dc5966;font-size:12px;line-height:1.6;overflow-wrap:anywhere;margin:8px 0 0}.component-statuses{display:grid;gap:10px;color:#8781aa;font-size:12px}.component-statuses>div{padding:10px;border:1px solid #e3dff6;border-radius:6px}.component-statuses>div>div{display:flex;gap:10px;flex-wrap:wrap;margin-top:5px}.component-statuses button{color:#5b48ff}progress{width:100%;height:5px;accent-color:#5b48ff}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}button:disabled{opacity:.45;cursor:not-allowed}input:focus-visible,textarea:focus-visible,button:focus-visible,article:focus-visible{outline:2px solid #8584ff;outline-offset:2px}@media(max-width:639px){.form-field{margin-bottom:22px}.field-heading{flex-wrap:wrap}.field-heading>label{font-size:14px}.field-heading label small{margin-left:10px;font-size:12px}.image-actions{gap:10px}.field-heading .aigc{font-size:11px}.choose-images,.choose-audio{padding:8px 10px;font-size:12px}.image-list{gap:12px}.image-list article{width:92px}.image-thumb{height:88px}.audio-content{flex-wrap:wrap;gap:10px}.audio-field>.requirements{margin-left:0}.audio-field>.field-heading>label{padding-top:7px}.audio-file{flex-basis:100%}.empty-images{font-size:12px;text-align:center;padding:10px}}
</style>
