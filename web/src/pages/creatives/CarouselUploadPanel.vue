<template>
  <div class="carousel-upload">
    <p class="tip">文本、图片和音频组成一条图文，任意一项质检不合格，整条图文不合格。</p>
    <section><h3>1. 文本 <span>必填</span></h3>
      <label>图文名称<input v-model="draft.name" maxlength="100" :disabled="locked" placeholder="选填，默认使用第一张图片的名称" /></label>
      <input ref="textInput" class="sr-only" type="file" accept=".txt" aria-label="导入图文文本" :disabled="locked" @change="textChanged" />
      <button class="choose-file" type="button" :disabled="locked" @click="textInput?.click()">导入 TXT 文件</button>
      <textarea v-model="draft.text" aria-label="图文文本" :disabled="locked" :maxlength="assetFileRules.maxTextChars" rows="4" placeholder="请输入图文内容，将单独进行文本质检"></textarea>
      <small>{{ draft.text.length.toLocaleString() }} / {{ assetFileRules.maxTextChars.toLocaleString() }} 字符 · {{ textPolicy.hint }}</small>
    </section>
    <section @dragover.prevent @drop.prevent="chooseImages($event.dataTransfer?.files)"><h3>2. 图片 <span>必填 · 支持多张</span></h3>
      <input ref="imageInput" class="sr-only" type="file" :accept="imagePolicy.extensions.map(e => `.${e}`).join(',')" multiple aria-label="选择图文图片" :disabled="locked" @change="imagesChanged" />
      <button class="choose-file" type="button" :disabled="locked" @click="imageInput?.click()">{{ draft.images.length ? '继续添加图片' : '选择图片（支持多选）' }}</button>
      <div class="images"><div v-for="(image, index) in previews" :key="image.url" class="image"><img :src="image.url" :alt="image.name" /><button type="button" :disabled="locked" :aria-label="`移除图片 ${index + 1}`" @click="draft.images.splice(index, 1)">×</button><small>{{ image.name }}</small></div></div>
      <small>{{ draft.images.length }} / {{ assetFileRules.carouselMaxImages }} 张 · {{ imagePolicy.hint }} · {{ (assetFileRules.maxPixels / 10000).toLocaleString() }} 万像素</small>
    </section>
    <section @dragover.prevent @drop.prevent="chooseAudio($event.dataTransfer?.files)"><h3>3. 音频 <span>必填</span></h3>
      <input ref="audioInput" class="sr-only" type="file" :accept="audioPolicy.extensions.map(e => `.${e}`).join(',')" aria-label="选择图文音频" :disabled="locked" @change="audioChanged" />
      <button class="choose-file" type="button" :disabled="locked" @click="audioInput?.click()">{{ draft.audio ? '更换音频' : '选择音频' }}</button>
      <p v-if="draft.audio">{{ draft.audio.name }} · {{ fileSize(draft.audio.size) }} <button type="button" :disabled="locked" @click="draft.audio = undefined">移除</button></p>
      <small>{{ audioPolicy.hint }} · M4A 需 {{ assetFileRules.m4aCodec.toUpperCase() }} 编码，时长大于 0；WAV 仅存平台</small>
    </section>
    <label class="accounts">同步到千川账户<el-select v-model="draft.accounts" multiple collapse-tags :disabled="locked" placeholder="不选则仅保存到平台"><el-option v-for="a in accounts" :key="a.accountId" :value="a.accountId" :label="`${a.accountName}（${a.advertiserId}）`" /></el-select></label>
    <p v-if="accountError" class="error">{{ accountError }} <button type="button" @click="loadAccounts">重试</button></p>
    <label class="aigc"><input v-model="draft.aigc" type="checkbox" :disabled="locked" />包含 AI 生成内容（如实申报）</label>
    <div v-if="rows.length" class="progress-list" aria-live="polite"><div v-for="row in rows" :key="row.key"><b>{{ row.file.name }}</b><span>{{ row.stage }}</span><progress :value="row.progress" max="100"></progress><p v-if="row.error" class="error">{{ row.error }}</p></div></div>
    <p v-if="draft.error" class="error" role="alert">{{ draft.error }}</p>
    <AssetReviewStatus v-if="draft.result" :review="draft.result.aiReview" composite />
    <div v-if="draft.result" class="parts"><div v-for="part in draft.result.parts" :key="`${part.role}:${part.position}`"><span>{{ part.role === 'TEXT' ? '文本' : part.role === 'AUDIO' ? '音频' : `图片 ${part.position + 1}` }}</span><b>{{ reviewLabel(part.asset.aiReview?.status) }}</b></div></div>
    <p class="tip">三部分全部质检通过后，才同步图片和音频组件到所选千川账户。</p>
    <p v-if="draft.busy" class="tip" role="status">正在预检、上传并保存图文，进入审核后即可关闭。</p>
    <p v-else-if="draft.result" class="tip" role="status">图文已保存，可关闭弹窗；后台将继续处理审核与同步。</p>
    <div class="modal__foot"><button class="btn-modal-ghost" type="button" :disabled="draft.busy" @click="emit('close')">关闭</button><button class="btn-modal-primary" type="button" :disabled="locked || !draft.text.trim() || !draft.images.length || !draft.audio" @click="start">{{ draft.busy ? '正在上传…' : draft.result ? '图文已保存' : '上传图文并质检' }}</button></div>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { listAccounts, type AccountVo } from '@/features/identity/oauthApi';
import { beginCarouselDialog, carouselDraft as draft, uploadCarousel } from '@/features/creative/carouselUploads';
import { uploadRows, reviewLabel } from '@/features/creative/assetUploads';
import { assetFileRules, libraryUploadPolicy, libraryFileError } from '@/features/creative/assetUploadTypes';
import { decodeText, preflight } from '@/features/creative/assetPreflight';
import { getUploadPolicy } from '@/features/creative/creativeApi';
import AssetReviewStatus from './AssetReviewStatus.vue';
import { fileSize } from './assetFormat';
const emit = defineEmits<{ close: []; closeBlocked: [blocked: boolean]; changed: [] }>();
const props = defineProps<{ defaultAccountIds?: string[] }>();
beginCarouselDialog();
if (!draft.busy && !draft.result && !draft.accounts.length && props.defaultAccountIds) draft.accounts = [...props.defaultAccountIds];
const locked = computed(() => draft.busy || !!draft.result);
const rows = computed(() => uploadRows.filter(r => draft.keys.includes(r.key)));
const accounts = ref<AccountVo[]>([]), accountError = ref(''), textInput = ref<HTMLInputElement>(), imageInput = ref<HTMLInputElement>(), audioInput = ref<HTMLInputElement>();
const imagePolicy = libraryUploadPolicy('IMAGE'), audioPolicy = libraryUploadPolicy('AUDIO'), textPolicy = libraryUploadPolicy('TEXT');
const previews = ref<{ url: string; name: string }[]>([]);
watch(() => [...draft.images], images => { previews.value.forEach(p => URL.revokeObjectURL(p.url)); previews.value = images.map(f => ({ url: URL.createObjectURL(f), name: f.name })); }, { immediate: true });
watch(() => draft.busy, blocked => emit('closeBlocked', blocked), { immediate: true, flush: 'sync' });
watch(() => draft.result, () => emit('changed'));
function chooseImages(files?: FileList | null) {
  if (locked.value) return;
  for (const f of Array.from(files || [])) {
    const error = libraryFileError(f.name, 'IMAGE');
    if (error) { ElMessage.warning(error); continue; }
    if (draft.images.length >= assetFileRules.carouselMaxImages) { ElMessage.warning(`最多上传 ${assetFileRules.carouselMaxImages} 张图片`); break; }
    if (!draft.images.some(v => v.name === f.name && v.size === f.size && v.lastModified === f.lastModified)) draft.images.push(f);
  }
}
function chooseAudio(files?: FileList | null) {
  if (locked.value || !files?.length) return;
  if (files.length !== 1) { ElMessage.warning('音频区域只允许选择一个音频文件'); return; }
  const f = files[0]!; const error = libraryFileError(f.name, 'AUDIO');
  if (error) ElMessage.warning(error); else draft.audio = f;
}
function imagesChanged(e: Event) { const el = e.target as HTMLInputElement; chooseImages(el.files); el.value = ''; }
function audioChanged(e: Event) { const el = e.target as HTMLInputElement; chooseAudio(el.files); el.value = ''; }
async function textChanged(e: Event) {
  const el = e.target as HTMLInputElement, file = el.files?.[0]; el.value = '';
  if (!file || locked.value) return;
  const invalid = libraryFileError(file.name, 'TEXT'); if (invalid) { ElMessage.warning(invalid); return; }
  draft.busy = true;
  try { await preflight(file, await getUploadPolicy()); draft.text = decodeText(new Uint8Array(await file.arrayBuffer())); }
  catch (error) { ElMessage.warning(error instanceof Error ? error.message : '文本导入失败'); }
  finally { draft.busy = false; }
}
async function loadAccounts() { try { accounts.value = (await listAccounts()).filter(a => a.platform === 'QIANCHUAN' && a.status !== 'DELETED'); accountError.value = ''; } catch { accountError.value = '授权账户加载失败，可先仅保存平台'; } }
async function start() { await uploadCarousel(); emit('changed'); }
onMounted(() => { void loadAccounts(); });
onUnmounted(() => { previews.value.forEach(p => URL.revokeObjectURL(p.url)); });
</script>
<style scoped>
.carousel-upload { color:#50536f; font-size:13px; }.tip { color:#7c7f99; line-height:1.7; margin:12px 0; }section { padding:16px; border:1px solid #e3e4f3; border-radius:14px; margin:14px 0; background:#fcfcff; }h3 { font-size:15px; font-weight:700; margin-bottom:12px; color:#1b1d32; }h3 span { font-size:12px; font-weight:400; color:#7c7f99; margin-left:8px; }section label,.accounts { display:grid; gap:8px; margin-bottom:12px; }input:not([type=checkbox]),textarea { width:100%; border:1px solid #dddff0; border-radius:8px; padding:10px; font:inherit; color:inherit; background:white; }textarea { resize:vertical; }small { display:block; margin-top:8px; color:#7c7f99; line-height:1.5; }.images { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px; margin-top:12px; }.image { position:relative; min-width:0; }.image img { width:100%; height:85px; object-fit:cover; border-radius:8px; }.image button { position:absolute; right:3px; top:3px; border-radius:50%; background:white; width:22px; height:22px; }.image small { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.aigc { display:flex; gap:8px; align-items:center; }input[type=checkbox],progress { accent-color:#5b55ff; }.error { color:#e5484d; line-height:1.7; margin:8px 0; }button:disabled { opacity:.45; cursor:not-allowed; }.parts>div { display:flex; gap:16px; padding:8px 0; border-bottom:1px solid #eeeef7; }.parts b { font-weight:400; }.progress-list { margin-top:16px; }.progress-list>div { display:grid; gap:6px; padding:8px 0; }progress { width:100%; height:6px; }section p button,.error button { color:#5b55ff; }
</style>
<style scoped>
.choose-file { padding:8px 14px; margin-bottom:8px; border:1px solid #dcd9ff; background:#efedff; border-radius:8px; color:#5b55ff; font:inherit; font-weight:600; }.sr-only { position:absolute; width:1px !important; height:1px; padding:0 !important; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0 !important; }
</style>
