<template>
  <ReferenceModal class="add-material-dialog" :class="{ 'upload-view': uploadOpen }" title="新增素材" :width="820" :busy="locked" @close="emit('close')">
    <template v-if="uploadOpen">
      <div class="upload-heading"><strong>{{ uploadType === 'VIDEO' ? '上传视频' : uploadType === 'IMAGE' ? '上传图片' : '上传图文' }}</strong><button type="button" :disabled="uploadBlocked" @click="closeUpload">返回{{ libraryLabel }}</button></div>
      <div class="asset-center dialog-upload">
        <CarouselUploadPanel v-if="uploadType === 'CAROUSEL'" :default-account-ids="[state.accountId]" @close="closeUpload" @close-blocked="uploadBlocked = $event" @changed="uploadChanged" />
        <AssetUploadPanel v-else :library-type="uploadType" :default-account-ids="[state.accountId]" @close="closeUpload" @close-blocked="uploadBlocked = $event" @changed="uploadChanged" />
      </div>
    </template>
    <template v-else>
    <div class="format-tabs" role="tablist" aria-label="新增素材类型">
      <button v-for="(label, key) in formats" :key="key" type="button" role="tab" :aria-selected="type === key" :disabled="locked" @click="chooseType(key)">{{ label }}</button>
    </div>
    <div class="current-product"><img v-if="product?.imageUrl" :src="product.imageUrl" alt="" /><span>当前商品：<strong>{{ product?.name }}</strong></span></div>
    <div class="dialog-row"><span>{{ type === 'VIDEO' ? '视频来源' : '素材来源' }}</span><div class="source-options" role="group" aria-label="素材来源">
      <button type="button" :aria-pressed="source === 'AI'" :disabled="locked" @click="source = 'AI'">智能生成</button>
      <button type="button" :aria-pressed="source === 'LIBRARY'" :disabled="locked" @click="source = 'LIBRARY'">{{ libraryLabel }}</button>
    </div></div>
    <template v-if="source === 'AI'">
      <p class="addition-note">提交后将根据商品分析为当前商品新增{{ type === 'VIDEO' ? '一条视频' : type === 'IMAGE' ? '一张商品卡图片' : '一套图文' }}，保留已有素材。</p>
    </template>
    <template v-else>
      <div class="library-search"><input v-model="query" aria-label="搜索我的素材" placeholder="搜索素材名称" :disabled="locked" @keyup.enter="search" /><button type="button" :disabled="locked || loading" @click="search">搜索</button><button type="button" :disabled="locked || loading" @click="refreshPreviews">刷新素材</button><button type="button" class="local-upload" :disabled="locked" @click="openUpload(type)">本地上传</button></div>
      <p v-if="type === 'VIDEO'" class="library-hint">这里选择视频，卡片图片是视频预览缩略图；投放封面在下方单独选择。</p>
      <p v-if="items.some(item => assetPreview(item).source === 'UNAVAILABLE')" class="preview-warning" role="status">部分素材的原文件和千川副本暂不可预览，请检查存储服务及千川授权，恢复后点击“刷新素材”。</p>
      <p v-if="loading" role="status">正在加载{{ libraryLabel }}…</p><p v-else-if="loadError" class="dialog-error" role="alert">{{ loadError }}<button type="button" @click="loadLibrary()">重试</button></p>
      <div v-else ref="libraryScroll" class="asset-scroll" tabindex="0" :aria-label="`${libraryLabel}，可上下滚动查看更多`">
        <div class="asset-grid" role="group" :aria-label="libraryLabel">
          <button v-for="item in items" :key="`${item.id}:${previewVersion}`" type="button" :aria-pressed="selectedId === item.id" :disabled="locked" @click="selectItem(item)"><span class="asset-preview"><AssetThumbnail :item="item" /><span v-if="item.type === 'VIDEO'" class="video-badge"><ReferenceIcon name="video" />视频</span></span><strong :title="item.filename">{{ item.filename }}</strong><small>{{ item.type === 'VIDEO' ? item.durationSec && item.durationSec > 0 ? `${item.durationSec} 秒` : '视频' : item.type === 'CAROUSEL' ? item.category || '图文' : '图片' }}</small></button>
          <p v-if="!items.length">暂无可选择的素材，可点击本地上传；图文审核完成后会显示在这里。</p>
        </div>
      </div>
      <nav class="library-pager" :aria-label="`${libraryLabel}分页`"><span>共 {{ total }} 条，每页 {{ pageSize }} 条</span><div><button type="button" :aria-label="`${libraryLabel}上一页`" :disabled="locked || loading || page <= 1" @click="changePage(page - 1)">上一页</button><span aria-live="polite">第 {{ page }} / {{ totalPages }} 页</span><button type="button" :aria-label="`${libraryLabel}下一页`" :disabled="locked || loading || page >= totalPages" @click="changePage(page + 1)">下一页</button></div></nav>
      <p v-if="selected" class="selection-note">已选{{ type === 'VIDEO' ? '视频' : '素材' }}：{{ selected.filename }}</p>
      <video v-if="!loading && type === 'VIDEO' && selectedUrls?.playUrl" class="selected-preview" :src="assetUrl(selectedUrls.playUrl)" :poster="assetUrl(selectedUrls.posterUrl)" controls preload="metadata" @error="selectedPreviewFailed" />
      <p v-if="selectedMediaError" class="preview-warning" role="status">{{ selectedUrls?.message || '视频暂不可播放，请检查存储服务后刷新素材。' }}</p>
      <div v-if="type === 'VIDEO'" class="library-field">
        <div class="cover-heading"><label for="addition-cover">视频封面</label><button type="button" :disabled="locked" @click="openUpload('IMAGE')">上传封面图片</button></div>
        <button id="addition-cover" type="button" class="cover-select" :aria-expanded="coverPickerOpen" aria-controls="addition-cover-options" :disabled="locked" @click="coverPickerOpen = !coverPickerOpen"><span v-if="chosenCover" class="chosen-cover-preview"><AssetThumbnail :key="`${chosenCover.id}:${previewVersion}`" :item="chosenCover" /></span><span>{{ chosenCover ? `已选封面：${chosenCover.filename}` : '选择封面图片' }}</span><span class="cover-select-action">{{ coverPickerOpen ? '收起' : chosenCover ? '更换' : '展开选择' }}</span></button>
        <div v-if="coverPickerOpen" id="addition-cover-options" class="cover-library">
          <p class="library-hint">以下为我的图片，可选作视频投放封面，也可上传新的封面图片。</p>
          <p v-if="coverLoading" role="status">正在加载封面图片…</p><p v-else-if="coverError" class="dialog-error" role="alert">{{ coverError }}<button type="button" @click="loadCovers()">重试</button></p>
          <div v-else class="cover-grid" role="group" aria-label="可选视频封面"><button v-for="cover in covers" :key="`${cover.id}:${previewVersion}`" type="button" :disabled="locked" :aria-pressed="coverId === cover.assetId" @click="selectCover(cover)"><span class="cover-preview"><AssetThumbnail :item="cover" /></span><strong :title="cover.filename">{{ cover.filename }}</strong></button><p v-if="!covers.length">暂无可用封面图片，请点击“上传封面图片”。</p></div>
          <nav class="library-pager cover-pager" aria-label="视频封面分页"><span>共 {{ coverTotal }} 张，每页 {{ pageSize }} 张</span><div><button type="button" :disabled="locked || coverLoading || coverPage <= 1" @click="changeCoverPage(coverPage - 1)">上一页封面</button><span aria-live="polite">第 {{ coverPage }} / {{ coverTotalPages }} 页</span><button type="button" :disabled="locked || coverLoading || coverPage >= coverTotalPages" @click="changeCoverPage(coverPage + 1)">下一页封面</button></div></nav>
        </div>
      </div>
      <div class="library-field"><label for="addition-titles">素材标题</label><textarea id="addition-titles" v-model="titles" :disabled="locked" rows="2" placeholder="每行一条标题，最多29条，每条55字" /><p v-if="titleError" class="dialog-error">{{ titleError }}</p></div>
    </template>
    <p v-if="submitError" class="dialog-error" role="alert">{{ submitError }}</p>
    </template>
    <template #footer><template v-if="!uploadOpen"><button type="button" class="dialog-secondary" :disabled="locked" @click="emit('close')">取消</button><button type="button" class="dialog-primary" :disabled="locked || !canSubmit" @click="submit">{{ locked ? '提交中…' : '提交' }}</button></template></template>
  </ReferenceModal>
</template>
<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import ReferenceModal from '../reference/ReferenceModal.vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import { listLibraryFull, type LibraryItemVo } from '@/features/creative/creativeApi';
import type { CreationAdditionBody, CreationState, creationAdd } from '@/features/delivery/creationApi';
import AssetThumbnail from '@/pages/creatives/AssetThumbnail.vue';
import AssetUploadPanel from '@/pages/creatives/AssetUploadPanel.vue';
import CarouselUploadPanel from '@/pages/creatives/CarouselUploadPanel.vue';
import { assetUrl } from '@/pages/creatives/assetFormat';
import { assetPreview, resolveAssetPreview, clearAssetPreviews } from '@/features/creative/assetPreviews';
import { uploadRows, reviewPending } from '@/features/creative/assetUploads';
import { carouselDraft, carouselPending } from '@/features/creative/carouselUploads';
type Format = CreationAdditionBody['type'];
const props = defineProps<{ state: CreationState; productId: string; initialType: Format; busy: boolean }>();
const emit = defineEmits<{ close: []; generate: [body: CreationAdditionBody]; add: [body: Parameters<typeof creationAdd>[1]] }>();
const formats: Record<Format, string> = { VIDEO: '新增视频', IMAGE: '新增商品卡图片', CAROUSEL: '新增图文' };
const type = ref<Format>(props.initialType), source = ref<'AI' | 'LIBRARY'>('AI');
const awaiting = ref(false), submitError = ref(''), requestId = ref('');
const uploadOpen = ref(false), uploadBlocked = ref(false), uploadType = ref<Format>(props.initialType);
const locked = computed(() => props.busy || awaiting.value || uploadBlocked.value);
const product = computed(() => props.state.products.find(p => p.qcProductId === props.productId));
const libraryLabel = computed(() => ({ VIDEO: '我的视频', IMAGE: '我的图片', CAROUSEL: '我的图文' })[type.value]);
const query = ref(''), page = ref(1), total = ref(0), items = ref<LibraryItemVo[]>([]), covers = ref<LibraryItemVo[]>([]), selectedId = ref(''), coverId = ref(''), titles = ref('');
const pageSize = 6, libraryScroll = ref<HTMLElement>(), chosenItem = ref<LibraryItemVo>();
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const coverPage = ref(1), coverTotal = ref(0), chosenCover = ref<LibraryItemVo>();
const coverTotalPages = computed(() => Math.max(1, Math.ceil(coverTotal.value / pageSize)));
const coverPickerOpen = ref(false), coverLoading = ref(false), coverError = ref('');
const loading = ref(false), loadError = ref('');
const selected = computed(() => items.value.find(item => item.id === selectedId.value) || chosenItem.value);
function selectItem(item: LibraryItemVo) { selectedId.value = item.id; chosenItem.value = item; }
function selectCover(item: LibraryItemVo) { coverId.value = item.assetId!; chosenCover.value = item; coverPickerOpen.value = false; }
const selectedUrls = computed(() => selected.value ? assetPreview(selected.value) : undefined);
const previewVersion = ref(0), selectedMediaError = ref(false);
watch(selected, () => { selectedMediaError.value = false; });
async function selectedPreviewFailed() {
  const item = selected.value; if (!item) return;
  if (assetPreview(item).source === 'ORIGINAL' && await resolveAssetPreview(item) && assetPreview(item).playUrl) return;
  if (selected.value?.id === item.id) selectedMediaError.value = true;
}
async function refreshPreviews() {
  loading.value = true;
  await nextTick();
  clearAssetPreviews([...items.value, ...covers.value, ...(chosenItem.value ? [chosenItem.value] : []), ...(chosenCover.value ? [chosenCover.value] : [])]); selectedMediaError.value = false; previewVersion.value++;
  await Promise.all([loadLibrary(true), type.value === 'VIDEO' ? loadCovers() : Promise.resolve()]);
}
const titleList = computed(() => titles.value.split('\n').map(t => t.trim()).filter(Boolean));
const titleError = computed(() => titleList.value.length > 29 || titleList.value.some(t => t.length > 55) ? '最多29条标题，每条最多55字' : '');
const canSubmit = computed(() => !!product.value && (source.value === 'AI' || !!selected.value && !!titleList.value.length && !titleError.value && (type.value !== 'VIDEO' || !!coverId.value)));
let loadNumber = 0, coverLoadNumber = 0, previousIds = new Set<string>(), submissionSource: 'AI' | 'LIBRARY' = 'AI';
let submissionKey = '';
const available = (i: LibraryItemVo) => (i.assetId || i.carouselId) && i.storageStatus !== 'FAILED' && i.reviewBadge !== 'REJECTED';
async function loadLibrary(keepSelection = false) {
  const number = ++loadNumber; loading.value = true; if (!keepSelection) { selectedId.value = ''; chosenItem.value = undefined; } loadError.value = '';
  try {
    const result = await listLibraryFull({ type: type.value, q: query.value.trim(), page: page.value, pageSize, selectable: true });
    if (number !== loadNumber) return;
    total.value = result.pageInfo?.total ?? result.items.length;
    if (page.value > totalPages.value) { page.value = totalPages.value; await loadLibrary(keepSelection); return; }
    items.value = result.items;
    const current = items.value.find(i => i.id === selectedId.value); if (current) chosenItem.value = current;
    await nextTick(); libraryScroll.value?.scrollTo({ top: 0 });
  } catch { if (number === loadNumber) { loadError.value = '素材库加载失败，请重试'; items.value = []; } }
  finally { if (number === loadNumber) loading.value = false; }
}
async function loadCovers() {
  const number = ++coverLoadNumber; coverLoading.value = true; coverError.value = '';
  try {
    const result = await listLibraryFull({ type: 'IMAGE', page: coverPage.value, pageSize, selectable: true });
    if (number !== coverLoadNumber) return;
    coverTotal.value = result.pageInfo?.total ?? result.items.length;
    if (coverPage.value > coverTotalPages.value) { coverPage.value = coverTotalPages.value; await loadCovers(); return; }
    covers.value = result.items.filter(i => available(i) && !!i.assetId);
    const current = covers.value.find(i => i.assetId === coverId.value); if (current) chosenCover.value = current;
  } catch { if (number === coverLoadNumber) { coverError.value = '封面图片加载失败，请重试'; covers.value = []; } }
  finally { if (number === coverLoadNumber) coverLoading.value = false; }
}
function changePage(next: number) { if (loading.value || next < 1 || next > totalPages.value) return; page.value = next; void loadLibrary(true); }
function changeCoverPage(next: number) { if (coverLoading.value || next < 1 || next > coverTotalPages.value) return; coverPage.value = next; void loadCovers(); }
function openUpload(format: Format) { uploadType.value = format; uploadOpen.value = true; }
function closeUpload() { if (uploadBlocked.value) return; uploadOpen.value = false; if (type.value === 'VIDEO' && uploadType.value === 'IMAGE') { coverPickerOpen.value = true; void loadCovers(); } else void loadLibrary(true); }
function uploadChanged() { if (type.value === 'VIDEO' && uploadType.value === 'IMAGE') { coverPage.value = 1; void loadCovers(); } else { query.value = ''; page.value = 1; void loadLibrary(true); } }
function search() { page.value = 1; void loadLibrary(); }
function chooseType(value: Format) { if (value === type.value) return; type.value = value; selectedId.value = ''; chosenItem.value = undefined; coverId.value = ''; chosenCover.value = undefined; coverPage.value = 1; coverPickerOpen.value = false; covers.value = []; coverTotal.value = 0; coverError.value = ''; titles.value = ''; query.value = ''; page.value = 1; }
watch([source, type], () => { loadNumber++; coverLoadNumber++; submitError.value = ''; if (source.value === 'LIBRARY') { void loadLibrary(); if (type.value === 'VIDEO') void loadCovers(); } });
watch([() => props.state, () => props.busy], () => {
  const accepted = submissionSource === 'AI' ? props.state.generationAdditions?.some(r => r.id === requestId.value) : props.state.materials.some(m => !previousIds.has(m.id));
  if (!awaiting.value && !(requestId.value && submissionSource === 'AI' && accepted)) return;
  if (accepted) { if (submissionKey) sessionStorage.removeItem(submissionKey); emit('close'); }
  else if (!props.busy) { awaiting.value = false; submitError.value = '提交状态暂未确认，请稍后重试。已填写内容会保留。'; }
});
function submit() {
  if (locked.value || !canSubmit.value) return;
  awaiting.value = true; submitError.value = ''; submissionSource = source.value; previousIds = new Set(props.state.materials.map(m => m.id));
  if (source.value === 'AI') {
    const body = { productId: props.productId, type: type.value, description: '' };
    submissionKey = `creation-addition:${props.state.planId}:${JSON.stringify(body)}`;
    const pending = sessionStorage.getItem(submissionKey);
    requestId.value = pending && !props.state.generationAdditions?.some(r => r.id === pending) ? pending : crypto.randomUUID();
    sessionStorage.setItem(submissionKey, requestId.value);
    emit('generate', { ...body, requestId: requestId.value });
  } else {
    const item = selected.value!;
    emit('add', { productId: props.productId, type: type.value, titles: titleList.value,
      ...(type.value === 'VIDEO' ? { videoAssetId: item.assetId, coverAssetId: coverId.value } : type.value === 'IMAGE' ? { imageAssetIds: [item.assetId!] } : { carouselId: item.carouselId }) });
  }
}
const poll = setInterval(() => {
  const pending = uploadRows.some(r => reviewPending(r.result?.aiReview?.status)) || (carouselDraft.result && carouselPending(carouselDraft.result));
  if (source.value === 'LIBRARY' && !uploadOpen.value && !locked.value && !loading.value && pending) void loadLibrary(true);
}, 5000);
watch(() => JSON.stringify([uploadRows.map(r => [r.result?.id,r.result?.aiReview?.status]),carouselDraft.result?.id,carouselDraft.result?.aiReview.status]), () => {
  if (source.value === 'LIBRARY' && !uploadOpen.value && !locked.value) void loadLibrary(true);
});
onUnmounted(() => { loadNumber++; coverLoadNumber++; clearInterval(poll); });
</script>
<style scoped>
.preview-warning{color:#9b741e;background:#fff9e9;border-radius:6px;padding:8px 10px;font-size:12px;line-height:1.6}
.add-material-dialog :deep(.reference-modal__box){padding:28px 38px;border-radius:24px}.add-material-dialog :deep(.reference-modal__body){margin:24px 0 32px}.format-tabs{display:flex;padding:4px;background:#f5f5fa;border-radius:12px;margin-bottom:18px}.format-tabs button{flex:1;padding:12px 5px;border:1px solid transparent;border-radius:8px;color:#555978;background:transparent}.format-tabs button[aria-selected=true]{border-color:#a9aaff;background:white;color:#22263c;font-weight:600}.current-product{display:flex;align-items:center;gap:10px;color:#878aa5;font-size:12px;margin-bottom:22px}.current-product img{width:32px;height:32px;object-fit:cover;border-radius:6px}.current-product strong{font-weight:500;color:#555978}.dialog-row{display:flex;align-items:center;gap:22px;margin:14px 0;color:#555978;font-size:14px}.dialog-row>span:first-child,.dialog-row>label{width:84px;flex-shrink:0}.source-options{display:flex;gap:4px}.source-options button{border-radius:8px;padding:10px 22px;border:1px solid transparent;color:#555978;background:transparent}.source-options button[aria-pressed=true]{background:#eeedff;border-color:#b1adff;color:#282d47}.library-field textarea{box-sizing:border-box;width:100%;border:1px solid #dedff2;border-radius:8px;resize:vertical;padding:12px 58px 12px 12px;color:#282d47;font:inherit;min-height:72px}.addition-note{margin:30px 0 0 106px;color:#878aa5;font-size:12px;line-height:1.7}.addition-note:before{content:'!';display:inline-block;width:13px;height:13px;margin-right:6px;border-radius:50%;background:#f2a026;color:white;text-align:center;line-height:13px}.dialog-primary,.dialog-secondary{border-radius:9px;padding:10px 22px;border:1px solid #dedff2}.dialog-primary{background:#575cf6;color:white;border-color:#575cf6}.dialog-secondary{color:#555978;background:white}button:disabled{opacity:.5;cursor:not-allowed}.library-search{display:flex;gap:10px;margin:20px 0}.library-search input{flex:1;padding:10px;border:1px solid #dedff2;border-radius:8px}.library-search button,.dialog-error button{color:#575cf6}.asset-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:12px 0}.asset-grid button{border:1px solid #dedff2;border-radius:9px;overflow:hidden;background:white;text-align:left;padding:8px}.asset-grid button[aria-pressed=true]{border-color:#575cf6;background:#f4f3ff}.asset-grid img,.asset-placeholder{display:flex;align-items:center;justify-content:center;width:100%;height:95px;object-fit:contain;background:#f5f5fa}.asset-grid strong{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:12px;margin-top:8px}.asset-grid small{font-size:11px;color:#878aa5}.selected-preview{display:block;width:100%;max-height:220px;margin:15px 0;background:#f5f5fa}.library-field{margin:16px 0;font-size:13px;color:#555978}.library-field label{display:block;margin-bottom:8px}.library-field select{width:100%;padding:9px;border:1px solid #dedff2;border-radius:8px}.dialog-error{color:#dc5966;font-size:12px;margin-top:10px}textarea:focus,input:focus,select:focus{outline:1px solid #8584ff}.add-material-dialog :deep(footer){gap:12px}
@media(max-width:639px){.add-material-dialog :deep(.reference-modal__box){padding:20px 16px}.format-tabs button{font-size:12px}.dialog-row{gap:10px}.dialog-row>span:first-child,.dialog-row>label{width:66px}.addition-note{margin-left:0}.asset-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.source-options button{padding:9px 15px}}
</style>
<style scoped>
.library-hint,.selection-note{font-size:12px;line-height:1.6;color:#878aa5;margin:8px 0}.selection-note{color:#555978;overflow-wrap:anywhere}.video-badge{position:absolute;left:5px;bottom:5px;display:flex;align-items:center;gap:4px;background:#252946b8;color:white;font-size:11px;border-radius:4px;padding:2px 5px}.video-badge svg{width:13px;height:13px}.library-pager{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;margin:12px 0;font-size:12px;color:#878aa5}.library-pager>div{display:flex;align-items:center;gap:12px}.library-pager button{color:#575cf6;padding:4px 0}.library-pager button:disabled{color:#b1b3c7}.cover-select{display:flex;align-items:center;gap:10px;width:100%;padding:10px;border:1px solid #dedff2;border-radius:8px;text-align:left;background:white;color:#555978}.cover-select>span:not(.chosen-cover-preview){overflow-wrap:anywhere}.cover-select-action{color:#575cf6;white-space:nowrap;margin-left:auto;flex-shrink:0}.chosen-cover-preview{position:relative;width:54px;height:54px;flex-shrink:0;overflow:hidden;border-radius:5px;background:#f5f5fa}.cover-library{padding:12px;margin-top:10px;border:1px solid #dedff2;border-radius:9px;background:#fcfcff}.cover-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.cover-grid button{min-width:0;padding:7px;border:1px solid #dedff2;border-radius:8px;background:white;text-align:left}.cover-grid button[aria-pressed=true]{border-color:#575cf6;background:#f4f3ff}.cover-preview{display:block;position:relative;height:95px;overflow:hidden;background:#f5f5fa;border-radius:4px}.cover-grid strong{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:12px;line-height:18px;margin-top:6px}.cover-pager{margin-bottom:0}
@media(max-width:639px){.cover-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.library-pager{justify-content:center}.library-pager>span{text-align:center;width:100%}.library-pager>div{justify-content:center}}
.dialog-upload{min-height:0;background:transparent}.upload-view :deep(.reference-modal__box>footer){display:none}
</style>
<style src="@/pages/creatives/asset-center.css"></style>
<style scoped>
.add-material-dialog :deep(.reference-modal__box){display:flex;flex-direction:column;overflow:hidden;box-sizing:border-box}.add-material-dialog :deep(.reference-modal__body){overflow-y:auto;min-height:0;overscroll-behavior:contain}.add-material-dialog :deep(header),.add-material-dialog :deep(footer){flex-shrink:0}.add-material-dialog :deep(footer:empty){display:none}.library-search{align-items:center}.library-search input{min-width:0}.library-search .local-upload{border:1px solid #c5c3ff;border-radius:8px;padding:9px 12px;white-space:nowrap;background:#f3f2ff}.asset-scroll{max-height:312px;overflow-y:auto;overscroll-behavior:contain;margin:12px 0;padding:1px 3px 1px 1px;border-radius:8px}.asset-grid{margin:0;grid-auto-rows:150px}.asset-grid button{box-sizing:border-box;min-height:0}.asset-grid .asset-preview{position:relative;display:block;height:95px;background:#f5f5fa;border-radius:5px;overflow:hidden}.asset-grid strong{line-height:18px}.scroll-note{font-size:12px;color:#878aa5;margin:8px 0}.cover-heading,.upload-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:8px}.cover-heading label{margin-bottom:0}.cover-heading button,.upload-heading button{color:#575cf6}.upload-heading{margin-bottom:18px}.dialog-upload{font-size:13px}.dialog-upload :deep(.modal__foot){position:static;padding:16px 0 0}.asset-scroll:focus-visible{outline:2px solid #8584ff;outline-offset:2px}
@media(max-width:639px){.asset-scroll{max-height:474px}.library-search{gap:6px;flex-wrap:wrap}.library-search input{min-width:130px}.library-search .local-upload{font-size:12px;padding:9px}.dialog-upload :deep(.upload-settings){padding-bottom:10px}}
</style>
