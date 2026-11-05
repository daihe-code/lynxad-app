<template>
  <ReferenceModal class="add-material-dialog" :class="{ 'video-upload-dialog': source === 'UPLOAD' && type === 'VIDEO', 'carousel-upload-dialog': localCarousel, 'compact-upload-dialog': type === 'IMAGE' && needsUploadFile || localCarousel && !carouselForm.started }" title="新增素材" :width="1020" :busy="locked" @close="close">
    <template #header-extra><div class="current-product"><img v-if="product?.imageUrl" :src="product.imageUrl" alt="" /><span>当前商品<strong>{{ product?.name }}</strong></span></div></template>
    <div class="format-tabs" role="tablist" aria-label="新增素材类型"><button v-for="(label, key) in formats" :key="key" type="button" role="tab" :aria-selected="type === key" :disabled="locked" @click="chooseType(key)">{{ label }}</button></div>
    <div v-if="!localCarousel || !carouselForm.started" class="source-row"><span>添加方式</span><div class="source-options" role="group" aria-label="素材来源"><button v-for="(label, key) in sources" :key="key" type="button" :aria-pressed="source === key" :disabled="locked || key === 'AI' && generationBusy" @click="chooseSource(key)"><span class="radio-dot" />{{ label }}</button></div></div>
    <div v-if="source === 'AI'" class="ai-source"><ReferenceIcon name="sparkle" /><div><strong>我会结合商品信息，为当前商品新增 {{ generationUnit }}。</strong><p>已有素材会保留，生成后可预览并决定是否用于投放。</p></div></div>
    <div v-if="source === 'LIBRARY'" class="library-source">
      <form class="library-search" @submit.prevent="search"><input v-model="query" aria-label="搜索素材名称" placeholder="搜索素材名称" :disabled="locked" /><button type="submit" :disabled="locked || loading">搜索</button><select :value="scope === 'AUTO' ? 'RELATED' : scope" aria-label="商品关联范围" :disabled="locked || loading" @change="scopeChanged"><option value="RELATED">当前商品相关</option><option value="ALL">{{ allLabel }}</option></select><button type="button" :disabled="locked || loading" @click="refresh"><ReferenceIcon name="refresh" />刷新</button></form>
      <p v-if="!loading && !loadError && items.length && relatedCount === 0 && scope === 'ALL'" class="hint">暂无明确关联当前商品的{{ kindLabel }}，可按名称搜索{{ allLabel }}。</p>
      <div v-if="loading" class="library-state" role="status">正在加载{{ kindLabel }}…</div>
      <div v-else-if="loadError" class="library-state" role="alert"><p>{{ loadError }}</p><button type="button" :disabled="locked" @click="loadLibrary()">重试</button></div>
      <div v-else-if="!items.length" class="library-state"><p>暂无{{ kindLabel }}，可切换至“本地上传”。</p><button v-if="scope === 'RELATED'" type="button" :disabled="locked" @click="showAll">查看{{ allLabel }}</button><button type="button" :disabled="locked" @click="chooseSource('UPLOAD')">本地上传</button></div>
      <div v-else class="asset-grid" role="group" :aria-label="`${kindLabel}素材库`"><article v-for="item in items" :key="`${item.id}:${previewVersion}`" :class="{ selected: librarySelected?.id === item.id }"><button type="button" class="asset-select" :aria-pressed="librarySelected?.id === item.id" :aria-label="`选择 ${item.filename}`" :disabled="locked" @click="librarySelected = item"><span class="asset-preview"><AssetThumbnail :item="item" fit="contain" /></span><span class="check-mark">{{ librarySelected?.id === item.id ? '✓' : '' }}</span><strong :title="item.filename">{{ item.filename }}</strong><small>{{ materialMeta(item) }}</small><small>{{ item.source === 'UPLOAD' ? '本地上传' : item.source === 'LYNX' ? '智能生成' : '千川素材' }} · {{ createdDate(item.createdAt) }}</small></button><button type="button" class="preview-button" :class="{ 'preview-button--carousel': type === 'CAROUSEL' }" :aria-label="`预览 ${item.filename}`" :title="type === 'VIDEO' ? '播放预览' : type === 'CAROUSEL' ? '图文预览' : '图片预览'" :disabled="locked" @click="preview(item)"><ReferenceIcon :name="type === 'VIDEO' ? 'play' : 'eye'" /></button></article></div>
      <nav v-if="!loadError && !loading && total > 0" class="library-pager" :aria-label="`${kindLabel}分页`"><span>共 {{ total }} 条，每页 {{ pageSize }} 条</span><div><button type="button" :aria-label="`${kindLabel}上一页`" :disabled="locked || page <= 1" @click="changePage(page - 1)">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button type="button" :aria-label="`${kindLabel}下一页`" :disabled="locked || page >= totalPages" @click="changePage(page + 1)">下一页</button></div></nav>
    </div>
    <LocalMaterialUpload v-if="uploadMounted" ref="uploader" v-show="source === 'UPLOAD'" :active="source === 'UPLOAD'" :type="type" :account-id="state.accountId" :busy="busy || awaiting || batchSaving" :added-asset-ids="addedAssetIds" :batch-saving="batchSaving" :attach-error="uploadAttachError" @upload-batch="uploadBatch = $event" @blocked="uploadBlocked = $event" @files-changed="localFileCount = $event" @carousel-state="carouselForm = $event" @select="localSelected = $event" />
    <div v-if="previewItem" ref="previewPanel" class="preview-panel"><div><strong>{{ previewItem.filename }}</strong><button type="button" aria-label="关闭素材预览" @click="previewItem = undefined">关闭预览</button></div><CarouselLibraryPreview v-if="previewItem.type === 'CAROUSEL'" :key="previewItem.id" :id="previewItem.carouselId" @ready="scrollPreview" /><video v-else-if="previewItem.type === 'VIDEO' && previewUrls?.playUrl" :key="previewItem.id" :src="assetUrl(previewUrls.playUrl)" :poster="assetUrl(previewUrls.posterUrl)" controls autoplay preload="metadata" @loadedmetadata="scrollPreview" @error="previewFailed" /><img v-else-if="imagePreviewUrl" :key="previewItem.id" :src="assetUrl(imagePreviewUrl)" :alt="previewItem.filename" @load="scrollPreview" /><p v-else class="hint">此素材暂不可预览，请刷新后重试。</p><p v-if="previewError" class="error">{{ previewError }}</p></div>
    <p v-if="imageSpecError" class="error" role="alert">{{ imageSpecError }}</p><p v-if="(uploadBatchMode || localCarousel) && uploadAttachError || submitError" class="error" role="alert">{{ (uploadBatchMode || localCarousel) && uploadAttachError || submitError }}</p>
    <template #footer>
      <template v-if="localCarousel">
        <template v-if="carouselForm.started">
          <span class="carousel-summary"><ReferenceIcon name="info" />{{ carouselForm.added ? '新增图文已加入当前商品，确认素材后再发布投放。' : '已有素材会保留，请勿重复创建' }}</span>
          <template v-if="carouselForm.added"><button type="button" class="secondary carousel-more" :disabled="locked" @click="continueCarousel">继续新增</button><button type="button" class="primary" :disabled="locked" @click="close">完成，返回素材列表</button></template>
          <template v-else><button v-if="!locked && !localSelected" type="button" class="secondary" @click="editCarousel">返回修改</button><button type="button" class="primary" :disabled="locked || !(localSelected || carouselForm.canCreate)" @click="localSelected ? retryCarouselAdd() : uploader?.createLocalCarousel()">{{ locked ? '创建中…' : localSelected ? '重试添加图文' : carouselForm.canCreate ? '重试创建图文' : '创建中…' }}</button></template>
        </template>
        <template v-else><span class="carousel-summary"><ReferenceIcon name="info" />{{ carouselForm.summary }}</span><button type="button" class="secondary" :disabled="locked" @click="close">取消</button><button type="button" class="primary" :disabled="locked || !carouselForm.canCreate" @click="uploader?.createLocalCarousel()">{{ carouselForm.processing ? '上传中…' : '创建并添加图文' }}</button></template>
      </template>
      <template v-else-if="uploadBatchMode">
        <span class="selection-count">{{ uploadBatch.addedCount === uploadBatch.count ? `成功添加 ${uploadBatch.addedCount} ${uploadUnit}` : `已就绪 ${uploadBatch.readyCount}/${uploadBatch.count} ${uploadUnit}` }}</span>
        <button v-if="uploadBatch.processing" type="button" class="secondary" :disabled="busy || batchSaving || stoppingUploads" @click="stopUploads">{{ stoppingUploads ? '停止中…' : '停止未完成任务' }}</button>
        <button v-else type="button" class="secondary" :class="{ 'upload-more-link': type === 'VIDEO' }" :disabled="locked || !!uploadAttachError" @click="continueUploads">继续上传</button>
        <button type="button" class="primary" :disabled="locked || stoppingUploads" @click="uploadAttachError ? retryUploadAdd() : close()">{{ batchSaving ? '添加中…' : uploadBatch.processing || stoppingUploads ? '处理中…' : uploadAttachError ? '重试添加' : '完成，返回素材列表' }}</button>
      </template>
      <template v-else><span v-if="source !== 'AI'" class="selection-count"><template v-if="needsUploadFile">请选择要上传的{{ type === 'VIDEO' ? '视频' : '图片' }}</template><template v-else>已选 {{ selected ? 1 : 0 }} {{ type === 'IMAGE' ? '张图片' : type === 'VIDEO' ? '条视频' : '套图文' }}</template></span><button type="button" class="secondary" :disabled="locked" @click="close">取消</button><button v-if="!needsUploadFile" type="button" class="primary" :disabled="locked || !canSubmit" @click="submit">{{ busy || awaiting ? '提交中…' : source === 'AI' ? generationButton : '添加所选素材' }}</button></template>
    </template>
  </ReferenceModal>
</template>
<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import ReferenceModal from '../reference/ReferenceModal.vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import LocalMaterialUpload from './LocalMaterialUpload.vue';
import CarouselLibraryPreview from './CarouselLibraryPreview.vue';
import type { CarouselFormState } from './CarouselLocalUpload.vue';
import AssetThumbnail from '@/pages/creatives/AssetThumbnail.vue';
import { assetUrl } from '@/pages/creatives/assetFormat';
import { assetPreview, clearAssetPreviews, resolveAssetPreview } from '@/features/creative/assetPreviews';
import { materialMeta } from '@/features/creative/materialSources';
import { assetDate } from '@/features/creative/assetDates';
import { listLibraryFull, type LibraryItemVo } from '@/features/creative/creativeApi';
import type { CreationAdditionBody, CreationState, creationAdd } from '@/features/delivery/creationApi';
import { materialSelectionAccepted } from '@/features/delivery/materialSelection';
import type { LocalUploadBatch, UploadBatchReceipt, UploadBatchState } from '@/features/creative/localUploadBatch';
type Format = CreationAdditionBody['type'];
type Source = 'AI' | 'LIBRARY' | 'UPLOAD';
const props = defineProps<{ state: CreationState; productId: string; initialType: Format; busy: boolean; generationBusy?: boolean; submissionError?: string }>();
const emit = defineEmits<{ close: []; generate: [body: CreationAdditionBody]; add: [body: Parameters<typeof creationAdd>[1]]; addUploads: [body: LocalUploadBatch, receipt: UploadBatchReceipt]; uploadBatchFinished: [] }>();
const formats: Record<Format, string> = { VIDEO: '视频', IMAGE: '商品卡图片', CAROUSEL: '图文' };
const sources: Record<Source, string> = { AI: '智能生成', LIBRARY: '从素材库选择', UPLOAD: '本地上传' };
const type = ref<Format>(props.initialType), source = ref<Source>(props.generationBusy ? 'UPLOAD' : 'AI');
const generationUnit = computed(() => ({ VIDEO: '1 条视频', IMAGE: '1 张商品卡图片', CAROUSEL: '1 套图文' })[type.value]);
const generationButton = computed(() => ({ VIDEO: '生成 1 条视频', IMAGE: '生成 1 张图片', CAROUSEL: '生成 1 套图文' })[type.value]);
const awaiting = ref(false), submitError = ref(''), requestId = ref(''), uploadBlocked = ref(false), uploadMounted = ref(!!props.generationBusy);
const uploader = ref<InstanceType<typeof LocalMaterialUpload>>();
const uploadUnit = computed(() => type.value === 'VIDEO' ? '个视频' : '张图片');
const addedAssetIds = ref<string[]>([]), batchSaving = ref(false), uploadAttachError = ref(''), stoppingUploads = ref(false);
const uploadBatch = ref<UploadBatchState>({ count: 0, readyCount: 0, addedCount: 0, stoppedCount: 0, processing: false, readyIds: [] });
const uploadBatchMode = computed(() => source.value === 'UPLOAD' && type.value !== 'CAROUSEL' && uploadBatch.value.count > 0);
const locked = computed(() => props.busy || awaiting.value || uploadBlocked.value || batchSaving.value || stoppingUploads.value || uploadBatchMode.value && uploadBatch.value.processing);
let alive = true;
function addReadyUploads() {
  if (type.value === 'CAROUSEL' || !uploadBatchMode.value || uploadBatch.value.processing || stoppingUploads.value || uploadBlocked.value || props.busy || batchSaving.value || uploadAttachError.value) return;
  const ids = uploadBatch.value.readyIds.filter(id => !addedAssetIds.value.includes(id));
  if (!ids.length) return;
  attachUploads(type.value, ids);
}
function attachUploads(format: LocalUploadBatch['type'], ids: string[]) {
  batchSaving.value = true;
  emit('addUploads', { productId: props.productId, type: format, assetIds: ids }, {
    added(id) { if (alive && !addedAssetIds.value.includes(id)) addedAssetIds.value.push(id); },
    done(error) { if (alive) { uploadAttachError.value = error || ''; batchSaving.value = false; } }
  });
}
watch([uploadBatch, source, type, () => props.busy, uploadBlocked, stoppingUploads], addReadyUploads, { flush: 'post' });
function retryUploadAdd() { uploadAttachError.value = ''; addReadyUploads(); }
async function stopUploads() { stoppingUploads.value = true; try { await uploader.value?.stopUploads(); } finally { stoppingUploads.value = false; } }
function continueUploads() { if (!locked.value) uploader.value?.continueUploads(); }
const product = computed(() => props.state.products.find(p => p.qcProductId === props.productId));
const kindLabel = computed(() => ({ VIDEO: '视频', IMAGE: '图片', CAROUSEL: '图文' })[type.value]);
const allLabel = computed(() => `全部${kindLabel.value}`);
const query = ref(''), scope = ref<'AUTO' | 'RELATED' | 'ALL'>('AUTO'), relatedCount = ref<number>(), page = ref(1), total = ref(0), items = ref<LibraryItemVo[]>([]);
const loading = ref(false), loadError = ref(''), librarySelected = ref<LibraryItemVo>(), localSelected = ref<LibraryItemVo>();
const pageSize = 6, totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const selected = computed(() => source.value === 'LIBRARY' ? librarySelected.value : source.value === 'UPLOAD' ? localSelected.value : undefined);
const localFileCount = ref(0);
const localCarousel = computed(() => source.value === 'UPLOAD' && type.value === 'CAROUSEL');
const carouselForm = ref<CarouselFormState>({ canCreate: false, processing: false, created: false, started: false, added: false, summary: '请填写图文名称，并添加图片和音频；本地文件选中后自动上传。' });
const needsUploadFile = computed(() => source.value === 'UPLOAD' && type.value !== 'CAROUSEL' && localFileCount.value === 0);
const imageSpecError = computed(() => type.value === 'IMAGE' && selected.value?.width && selected.value?.height && selected.value.width !== selected.value.height ? '商品卡图片须为 1:1 方图，请裁切后上传或选择其他图片。' : '');
const canSubmit = computed(() => !!product.value && (source.value === 'AI' ? !props.generationBusy : !!selected.value && !imageSpecError.value && (source.value !== 'LIBRARY' || !loading.value && !loadError.value)));
const previewItem = ref<LibraryItemVo>(), previewError = ref(''), previewVersion = ref(0);
const previewPanel = ref<HTMLElement>();
const previewUrls = computed(() => previewItem.value ? assetPreview(previewItem.value) : undefined);
const imagePreviewUrl = computed(() => previewUrls.value?.posterUrl || (previewItem.value?.type === 'IMAGE' ? previewUrls.value?.playUrl : ''));
async function scrollPreview() {
  await nextTick();
  // Wait for the loaded media and the scaled dialog to finish their layout.
  await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
  const panel = previewPanel.value, body = panel?.closest<HTMLElement>('.reference-modal__body');
  if (!panel || !body) return;
  const bounds = body.getBoundingClientRect(), scale = bounds.height / body.offsetHeight || 1;
  body.scrollTo({ top: body.scrollTop + (panel.getBoundingClientRect().top - bounds.top) / scale, behavior: 'instant' });
}
async function preview(item: LibraryItemVo) {
  previewError.value = ''; previewItem.value = item;
  await scrollPreview();
  if (item.type !== 'CAROUSEL' && !assetPreview(item).posterUrl && !assetPreview(item).playUrl) {
    await resolveAssetPreview(item);
    if (previewItem.value?.id === item.id) await scrollPreview();
  }
}
async function previewFailed() { const item = previewItem.value; if (item && assetPreview(item).source === 'ORIGINAL' && await resolveAssetPreview(item) && assetPreview(item).playUrl) return; previewError.value = '素材暂不可预览，请刷新后重试。'; }
function createdDate(value: string | null) { return assetDate(value); }
let loadNumber = 0, submissionSource: Source = 'AI', submissionKey = '';
let submissionStarted = false;
async function loadLibrary(keepSelection = false) {
  const number = ++loadNumber; loading.value = true; loadError.value = '';
  if (!keepSelection) librarySelected.value = undefined;
  try {
    const result = await listLibraryFull({ type: type.value, q: query.value.trim(), page: page.value, pageSize, selectable: true, accountId: props.state.accountId, productId: props.productId, productScope: scope.value }, { headers: { 'X-Quiet-Request': 'true' } });
    if (number !== loadNumber) return;
    total.value = result.pageInfo?.total ?? result.items.length; relatedCount.value = result.relatedCount;
    if (scope.value === 'AUTO') scope.value = result.productScope || 'ALL';
    if (page.value > totalPages.value) { page.value = totalPages.value; await loadLibrary(keepSelection); return; }
    items.value = result.items;
    const updated = items.value.find(item => item.id === librarySelected.value?.id); if (updated) librarySelected.value = updated;
  } catch { if (number === loadNumber) { loadError.value = `暂时无法加载${kindLabel.value}，请重试。`; items.value = []; total.value = 0; librarySelected.value = undefined; } }
  finally { if (number === loadNumber) loading.value = false; }
}
async function refresh() { clearAssetPreviews(items.value); previewVersion.value++; previewItem.value = undefined; await nextTick(); await loadLibrary(); }
function search() { if (locked.value) return; page.value = 1; previewItem.value = undefined; void loadLibrary(); }
function scopeChanged(event: Event) { scope.value = (event.target as HTMLSelectElement).value as 'RELATED' | 'ALL'; search(); }
function showAll() { scope.value = 'ALL'; query.value = ''; search(); }
function changePage(value: number) { if (locked.value || loading.value || value < 1 || value > totalPages.value) return; page.value = value; void loadLibrary(true); }
function chooseSource(value: Source) { if (locked.value || source.value === value || value === 'AI' && props.generationBusy) return; source.value = value; previewItem.value = undefined; submitError.value = ''; if (value === 'UPLOAD') uploadMounted.value = true; }
function chooseType(value: Format) { if (locked.value || type.value === value) return; type.value = value; librarySelected.value = undefined; localSelected.value = undefined; previewItem.value = undefined; scope.value = 'AUTO'; relatedCount.value = undefined; query.value = ''; page.value = 1; submitError.value = ''; }
watch([source, type], () => { ++loadNumber; if (source.value === 'LIBRARY') void loadLibrary(); });
watch([() => props.state, () => props.busy, () => props.submissionError], () => {
  if (awaiting.value && props.busy) submissionStarted = true;
  const accepted = submissionSource === 'AI' ? props.state.generationAdditions?.some(r => r.id === requestId.value)
    : materialSelectionAccepted(submissionStarted, props.busy, props.submissionError);
  if (!awaiting.value && !(requestId.value && submissionSource === 'AI' && accepted)) return;
  if (accepted) { if (submissionKey) sessionStorage.removeItem(submissionKey); emit('close'); }
  else if (!props.busy) { awaiting.value = false; submitError.value = props.submissionError || '提交状态暂未确认，请稍后重试。已选内容会保留。'; }
});
function addReadyCarousel() {
  const id = localSelected.value?.carouselId;
  if (localCarousel.value && id && !locked.value && !uploadAttachError.value && !addedAssetIds.value.includes(id)) attachUploads('CAROUSEL', [id]);
}
watch([localSelected, () => carouselForm.value.processing, () => props.busy, uploadBlocked], addReadyCarousel, { flush: 'post' });
function retryCarouselAdd() { uploadAttachError.value = ''; addReadyCarousel(); }
function continueCarousel() { if (!locked.value && carouselForm.value.added) { localSelected.value = undefined; uploadAttachError.value = ''; uploader.value?.resetLocalCarousel(); } }
function editCarousel() { if (!locked.value) { localSelected.value = undefined; uploadAttachError.value = ''; uploader.value?.editLocalCarousel(); } }
watch(() => carouselForm.value.started, async () => { await nextTick(); uploader.value?.$el.closest('.reference-modal__body')?.scrollTo({ top: 0, behavior: 'instant' }); });
function close() { if (!locked.value) { if (addedAssetIds.value.length) emit('uploadBatchFinished'); emit('close'); } }
function submit() {
  if (locked.value || !canSubmit.value) return;
  awaiting.value = true; submitError.value = ''; submissionStarted = false; submissionSource = source.value;
  if (source.value === 'AI') {
    const body = { productId: props.productId, type: type.value, description: '' };
    submissionKey = `creation-addition:${props.state.planId}:${JSON.stringify(body)}`;
    const pending = sessionStorage.getItem(submissionKey);
    requestId.value = pending && !props.state.generationAdditions?.some(r => r.id === pending) ? pending : crypto.randomUUID();
    sessionStorage.setItem(submissionKey, requestId.value); emit('generate', { ...body, requestId: requestId.value });
  } else {
    const item = selected.value!;
    emit('add', { productId: props.productId, type: type.value, addedVia: source.value, ...(type.value === 'VIDEO' ? { videoAssetId: item.assetId } : type.value === 'IMAGE' ? { imageAssetIds: [item.assetId!] } : { carouselId: item.carouselId }) });
  }
}
onUnmounted(() => { alive = false; ++loadNumber; });
</script>
<style scoped>
.add-material-dialog{--add-material-scale:.85}
.video-upload-dialog :deep(footer),.carousel-upload-dialog :deep(footer){border-top:1px solid #dedaff;padding-top:16px}
.carousel-summary{display:flex;align-items:center;gap:9px;margin-right:auto;min-width:0;font-size:12px;line-height:1.6;color:#8781aa}.carousel-summary svg{width:18px;height:18px;flex-shrink:0}.carousel-upload-dialog .primary{min-width:185px}
.carousel-upload-dialog .carousel-more{border-color:transparent;color:#5342ff;min-width:0}
.video-upload-dialog .upload-more-link{border-color:transparent;color:#5342ff;min-width:0}
.add-material-dialog :deep(.reference-modal__box){zoom:var(--add-material-scale);display:flex;flex-direction:column;overflow:hidden;box-sizing:border-box;padding:38px 46px 32px;border-radius:24px}
.video-upload-dialog :deep(.reference-modal__box),.compact-upload-dialog :deep(.reference-modal__box){max-height:calc((100dvh - 48px) / var(--add-material-scale))}
.add-material-dialog :deep(.reference-modal__body){overflow-y:auto;min-height:0;overscroll-behavior:contain;margin:34px 0 32px}
.add-material-dialog :deep(header),.add-material-dialog :deep(footer){flex-shrink:0}
.add-material-dialog :deep(header){gap:34px;align-items:center}
.add-material-dialog :deep(header h2){font-size:26px;font-weight:700;white-space:nowrap;padding:24px 34px 24px 0;border-right:1px solid #d9d5ff}
.add-material-dialog :deep(header button){align-self:flex-start;margin:12px 0 0 auto;color:#625e93}
.add-material-dialog :deep(header button svg){width:26px;height:26px}
.current-product{display:flex;align-items:center;gap:20px;flex:1;min-width:0;font-size:14px;color:#8884aa}
.current-product img{width:98px;height:90px;object-fit:contain;border:1px solid #dedaff;border-radius:10px;flex-shrink:0;background:#fff}
.current-product span{min-width:0}.current-product strong{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;color:#565180;font-size:16px;font-weight:500;line-height:1.6;margin-top:5px;max-width:400px}
.format-tabs{display:flex;padding:4px;background:#f1efff;border-radius:12px}
.format-tabs button{flex:1;min-height:58px;padding:10px 5px;border:1px solid transparent;border-radius:9px;color:#565180;background:transparent;font-size:18px}
.format-tabs button[aria-selected=true]{border-color:#5b48ff;background:#fff;color:#171830;font-weight:600;box-shadow:0 0 0 3px #fff}
.source-row{display:flex;align-items:center;gap:44px;margin:38px 0;color:#373455;font-size:18px}
.source-row>span{flex-shrink:0;width:126px;font-weight:600}.source-options{display:flex;align-items:center;gap:100px;flex:1}
.source-options button{display:inline-flex;align-items:center;gap:16px;color:#565180;white-space:nowrap;background:none;font-size:18px}
.source-options button[aria-pressed=true]{color:#5344ff;font-weight:600}.radio-dot{box-sizing:border-box;width:29px;height:29px;border:2px solid #a7a0ca;border-radius:50%;flex-shrink:0}.source-options button[aria-pressed=true] .radio-dot{border:9px solid #5846ff;background:white}
.ai-source{display:flex;align-items:center;gap:24px;padding:24px 30px;border-radius:12px;background:linear-gradient(110deg,#f2f0ff,#eeecff);color:#38316a}
.ai-source>svg{width:38px;height:38px;flex-shrink:0;color:#5b48ff}.ai-source strong{font-size:17px;font-weight:600;line-height:1.7}.ai-source p{margin:5px 0 0;color:#7b73a7;font-size:16px;line-height:1.6}
.hint{font-size:12px;line-height:1.7;color:#878aa5;margin:10px 0}.library-search{display:flex;align-items:center;gap:10px;margin:12px 0}.library-search input{min-width:0;flex:1;padding:9px 12px;border:1px solid #dedff2;border-radius:7px;font:inherit;font-size:12px}.library-search select{max-width:132px;border:1px solid #dedff2;border-radius:7px;padding:8px;font:inherit;font-size:12px;color:#555978;background:white}.library-search button{display:flex;align-items:center;gap:4px;white-space:nowrap;color:#5b55ff;font-size:12px}.library-search svg{width:14px;height:14px}.asset-grid{--asset-preview-height:clamp(112px,22dvh,192px);display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.asset-grid article{position:relative;min-width:0;border:1px solid #dedff2;border-radius:9px;background:#fff;overflow:hidden}.asset-grid article.selected{border-color:#5b55ff;background:#f6f5ff}.asset-select{width:100%;height:100%;padding:8px;text-align:left;color:#30344e;background:transparent}.asset-preview{position:relative;display:block;height:var(--asset-preview-height);border-radius:5px;overflow:hidden;background:#f5f5fa}.asset-select strong{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:12px;margin-top:8px;line-height:20px}.asset-select small{display:block;font-size:11px;line-height:18px;color:#878aa5}.check-mark{position:absolute;top:12px;right:12px;border:1px solid #bdbfcc;border-radius:50%;width:15px;height:15px;line-height:15px;text-align:center;background:white;font-size:10px}.selected .check-mark{background:#5b55ff;border-color:#5b55ff;color:white}.preview-button{position:absolute;right:12px;top:calc(var(--asset-preview-height) - 20px);width:24px;height:24px;background:#30344e99;color:#fff;border-radius:50%;display:grid;place-items:center}.preview-button svg{width:16px;height:16px}.library-state{min-height:210px;display:flex;align-items:center;justify-content:center;gap:12px;flex-wrap:wrap;align-content:center;color:#878aa5;font-size:13px}.library-state p{width:100%;text-align:center}.library-state button{color:#5b55ff;padding:8px;border:1px solid #dcd9ff;border-radius:6px}.library-pager{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-top:14px;font-size:12px;color:#878aa5}.library-pager>div{display:flex;gap:12px}.library-pager button{color:#5b55ff}.selection-count{margin-right:auto;font-size:12px;color:#555978;align-self:center}.primary,.secondary{min-height:62px;padding:12px 34px;border-radius:11px;border:1px solid #dcd8fb;font-size:18px}.primary{min-width:200px}.secondary{min-width:130px}.primary{color:white;background:#5342ff;border-color:#5342ff}.secondary{color:#555978;background:white}button:disabled{opacity:.45;cursor:not-allowed}.error{color:#dc5966;font-size:12px;line-height:1.7}.preview-panel{padding:14px;border:1px solid #e5e5f3;border-radius:10px;margin-top:16px}.preview-panel>div{display:flex;justify-content:space-between;align-items:center;gap:12px}.preview-panel strong{font-size:12px;overflow-wrap:anywhere}.preview-panel button{flex-shrink:0;color:#5b55ff}.preview-panel video,.preview-panel img{display:block;width:100%;max-height:280px;object-fit:contain;background:#f5f5fa;margin-top:10px}input:focus-visible,select:focus-visible,textarea:focus-visible,button:focus-visible{outline:2px solid #8584ff;outline-offset:2px}
.preview-button--carousel{width:30px;height:30px;top:calc(var(--asset-preview-height) - 26px);border:1px solid #dcd9ff;border-radius:7px;background:#fff;color:#5b55ff;box-shadow:0 2px 6px #30344e1a}.preview-button--carousel svg{width:19px;height:19px}
/* Cancel inherited zoom for native video controls while preserving the visible player size. */
.preview-panel video{zoom:calc(1 / var(--add-material-scale));max-height:calc(280px * var(--add-material-scale));margin-top:calc(10px * var(--add-material-scale))}
.preview-panel video:fullscreen{zoom:1;width:100%;height:100%;max-height:none;margin:0}
@media(max-width:980px){.source-options{gap:24px}.add-material-dialog :deep(header){gap:16px}.add-material-dialog :deep(header h2){font-size:20px;padding:15px 16px 15px 0}.current-product{gap:12px}.current-product img{width:64px;height:64px}.current-product strong{font-size:14px}.source-row{gap:18px;font-size:16px}.source-row>span{width:auto}.source-options button{font-size:15px;gap:8px}.radio-dot{width:22px;height:22px}.source-options button[aria-pressed=true] .radio-dot{border-width:7px}.ai-source{padding:20px;gap:14px}.ai-source strong{font-size:15px}.ai-source p{font-size:14px}.primary,.secondary{min-height:48px;min-width:0;font-size:15px}.format-tabs button{font-size:16px;min-height:48px}}
@media(min-width:640px){
  .compact-upload-dialog{--add-material-scale:clamp(.6,calc((100dvh - 48px) / 840px),.8)}
  .compact-upload-dialog :deep(.reference-modal__box){padding:26px 32px 22px}
  .compact-upload-dialog :deep(.reference-modal__body){margin:16px 0}
  .compact-upload-dialog :deep(header){gap:24px}
  .compact-upload-dialog :deep(header h2){padding:16px 26px 16px 0}
  .compact-upload-dialog .current-product img{width:72px;height:66px}
  .compact-upload-dialog .format-tabs button{min-height:50px}
  .compact-upload-dialog .source-row{margin:16px 0}
  .compact-upload-dialog .primary,.compact-upload-dialog .secondary{min-height:48px;padding:10px 24px}
  .compact-upload-dialog :deep(.drop-zone--file){min-height:260px;padding:20px;gap:12px}
  .compact-upload-dialog :deep(.drop-zone--file>svg){width:52px;height:52px}
  .compact-upload-dialog :deep(.drop-zone--file strong){font-size:18px}
  .compact-upload-dialog :deep(.file-select-button){min-height:44px;font-size:16px;padding:10px 22px}
  .compact-upload-dialog :deep(.carousel-local-upload .form-field){margin-bottom:16px}
  .compact-upload-dialog :deep(.carousel-local-upload .audio-field){margin-bottom:0}
  .compact-upload-dialog :deep(.carousel-local-upload .field-heading){margin-bottom:8px}
  .compact-upload-dialog :deep(.carousel-local-upload .counted-field){margin-top:8px}
  .compact-upload-dialog :deep(.carousel-local-upload .counted-field input){height:40px}
  .compact-upload-dialog :deep(.carousel-local-upload .counted-field textarea){height:76px;min-height:64px}
  .compact-upload-dialog :deep(.carousel-local-upload .empty-images){height:72px}
  .compact-upload-dialog :deep(.carousel-local-upload .requirements){margin-top:8px}
}
@media(max-width:639px){.carousel-upload-dialog :deep(footer){flex-wrap:wrap}.carousel-summary{flex-basis:100%;font-size:11px}.carousel-upload-dialog .primary{min-width:0}.current-product img{width:48px;height:48px}.add-material-dialog :deep(header){gap:12px;flex-wrap:wrap}.add-material-dialog :deep(header h2){border:0;padding:0}.current-product{order:3;flex-basis:100%}.add-material-dialog :deep(header button){margin:0 0 0 auto}.add-material-dialog :deep(.reference-modal__body){margin:24px 0}.source-options{justify-content:flex-start;flex-wrap:wrap}.ai-source>svg{width:28px;height:28px}.add-material-dialog :deep(.reference-modal__box){padding:20px 16px}.source-row{gap:12px;flex-wrap:wrap}.source-options{gap:15px}.library-search{flex-wrap:wrap;gap:8px}.library-search input{min-width:140px}.asset-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.library-pager{justify-content:center}.selection-count{font-size:11px}.primary,.secondary{padding:10px 12px}}
</style>
