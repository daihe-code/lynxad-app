<template>
  <div class="modal" role="dialog" aria-modal="true" :aria-labelledby="kind === 'detail' ? 'asset-detail-title' : 'asset-modal-title'" @keydown="onKey">
    <div class="modal__mask" @click="requestClose"></div>
    <div ref="box" class="modal__box" :class="[`modal__box--${kind === 'upload' ? 'md' : kind === 'preview' ? 'lg' : 'drawer'}`, { 'modal__box--file-upload': kind === 'upload' && ['VIDEO', 'IMAGE', 'CAROUSEL'].includes(libraryType), 'modal__box--carousel-preview': kind === 'preview' && item?.type === 'CAROUSEL' }]" tabindex="-1">
      <VideoLibraryUpload v-if="kind === 'upload' && libraryType === 'VIDEO'" :account-id="accountId" @close="requestClose" @close-blocked="uploadCloseBlocked = $event" @changed="emit('changed')" />
      <ImageLibraryUpload v-else-if="kind === 'upload' && libraryType === 'IMAGE'" :account-id="accountId" @close="requestClose" @close-blocked="uploadCloseBlocked = $event" @changed="emit('changed')" />
      <CarouselUploadPanel v-else-if="kind === 'upload' && libraryType === 'CAROUSEL'" :account-id="accountId" @close="requestClose" @close-blocked="uploadCloseBlocked = $event" @changed="emit('changed')" />
      <template v-else-if="kind === 'upload'">
        <div class="modal__head"><h2 id="asset-modal-title" class="modal__title">上传素材</h2><button class="modal__close" type="button" aria-label="关闭" :disabled="closeBlocked" @click="requestClose"><AssetIcon name="close" /></button></div>
        <AssetUploadPanel :library-type="libraryType" @close="requestClose" @close-blocked="uploadCloseBlocked = $event" @changed="emit('changed')" />
      </template>
      <template v-else-if="item">
        <AssetStatistics v-if="kind === 'detail'" :item="item" :account-id="accountId" @close="emit('close')" @preview="emit('switch', 'preview')" />
        <template v-else-if="item.type === 'CAROUSEL'">
          <div class="modal__head"><h2 id="asset-modal-title" class="modal__title">{{ item.filename }} · 图文预览</h2><button class="modal__close" type="button" aria-label="关闭" @click="emit('close')"><AssetIcon name="close" /></button></div>
          <CarouselPreview :item="item" />
          <div class="modal__foot"><button class="btn-modal-ghost" type="button" @click="emit('close')">关闭</button><button class="btn-modal-primary" type="button" @click="emit('switch', 'detail')">查看素材详情</button></div>
        </template>
        <template v-else>
        <template v-if="kind === 'preview'">
          <div class="modal__head"><h2 id="asset-modal-title" class="modal__title">{{ item.filename }}</h2><button class="modal__close" type="button" aria-label="关闭" @click="emit('close')"><AssetIcon name="close" /></button></div>
          <div class="ac-pmedia ac-t0" :class="{ 'asset-text-media': item.type === 'TEXT', 'asset-media-frame': geometry.ratio }" :style="mediaStyle"><pre v-if="item.type === 'TEXT'">{{ uploaded?.textContent || detailError || '正在读取文本…' }}</pre><audio v-else-if="item.type === 'AUDIO' && item.playUrl" :src="assetUrl(item.playUrl)" controls preload="metadata" @error="mediaError = true"></audio><video v-else-if="item.type === 'VIDEO' && previewUrls?.playUrl" :key="item.id" :src="assetUrl(previewUrls.playUrl)" :poster="assetUrl(previewUrls.posterUrl)" controls playsinline preload="metadata" @loadedmetadata="readDimensions" @error="previewFailed"></video><img v-else-if="previewUrls?.playUrl || previewUrls?.posterUrl" :src="assetUrl(previewUrls.playUrl || previewUrls.posterUrl)" :alt="item.filename" @load="readDimensions" @error="previewFailed" /><span v-else class="asset-media-error">{{ previewUrls?.message || '暂无可预览的文件' }}</span><span v-if="mediaError" class="asset-media-error" role="status">{{ previewUrls?.message || '素材文件暂不可播放或预览' }}</span></div>
          <div class="ac-pinfo"><span v-if="geometry.label">画面 <b>{{ geometry.label }}</b></span><span>规格 <b>{{ dimensions }} · {{ displayItem ? assetRatio(displayItem) : '—' }}</b></span><span>大小 <b>{{ fileSize(item.sizeBytes) }}</b></span><span v-if="item.type === 'VIDEO'">时长 <b>{{ duration(item.durationSec) }}</b></span><span>引用计划 <b>{{ referenceLabel(item) }}</b></span><span v-if="assetStatus(item).label">状态 <b>{{ assetStatus(item).label }}</b></span></div>
          <div class="modal__foot"><button v-if="item.type === 'TEXT' && uploaded?.textContent" class="btn-modal-ghost" type="button" @click="copyText">复制文本</button><button class="btn-modal-ghost" type="button" @click="emit('close')">关闭</button><button class="btn-modal-primary" type="button" @click="emit('switch', 'detail')">查看素材详情</button></div>
        </template>
        </template>
      </template>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type { LibraryItemVo } from "@/features/creative/creativeApi";
import { assetGeometry } from '@/features/creative/assetGeometry';
import AssetStatistics from "./AssetStatistics.vue";
import VideoLibraryUpload from './VideoLibraryUpload.vue';
import ImageLibraryUpload from './ImageLibraryUpload.vue';
import { referenceLabel } from "@/features/creative/assetReferences";
import AssetIcon from "./AssetIcon.vue";
import AssetUploadPanel from "./AssetUploadPanel.vue";
import CarouselUploadPanel from './CarouselUploadPanel.vue';
import CarouselPreview from './CarouselPreview.vue';
import { getUploadedAsset, type UploadedAsset } from "@/features/creative/creativeApi";
import { assetPreview, resolveAssetPreview } from '@/features/creative/assetPreviews';
import { assetUrl, duration, fileSize, assetStatus, assetRatio, assetDimensions } from "./assetFormat";
const props = withDefaults(defineProps<{ kind: "upload" | "detail" | "preview"; item: LibraryItemVo | null; libraryType?: LibraryItemVo['type']; accountId?: string }>(), { libraryType: 'VIDEO' });
const emit = defineEmits<{ close: []; switch: [kind: "detail" | "preview"]; changed: [] }>();
const uploadCloseBlocked = ref(false);
const closeBlocked = computed(() => props.kind === 'upload' && uploadCloseBlocked.value);
function requestClose() { if (!closeBlocked.value) emit('close'); }
const box = ref<HTMLElement>();
const uploaded = ref<UploadedAsset>(), detailError = ref("");
const mediaError = ref(false), observed = ref<{width:number;height:number}>();
const geometry = computed(() => props.item ? assetGeometry(props.item, observed.value) : {orientation:'unknown',label:''} as ReturnType<typeof assetGeometry>);
const displayItem = computed(() => props.item && geometry.value.width ? {...props.item,width:geometry.value.width,height:geometry.value.height} : props.item);
const dimensions = computed(() => displayItem.value ? assetDimensions(displayItem.value) : '—');
const mediaStyle = computed(() => geometry.value.ratio ? {aspectRatio:geometry.value.ratio,'--asset-ratio':geometry.value.ratioValue} : undefined);
watch(() => props.item?.id, () => { observed.value = undefined; });
const previewUrls = computed(() => props.item ? assetPreview(props.item) : undefined);
async function previewFailed() {
  const item = props.item; if (!item) return;
  if (await resolveAssetPreview(item, assetPreview(item).source === 'QIANCHUAN')) return;
  if (props.item?.id === item.id) mediaError.value = true;
}
watch(() => [props.kind, props.item?.id], () => {
  const item = props.item;
  if (item?.id.startsWith('EXT:') && item.type !== 'CAROUSEL' && props.kind === 'preview') void resolveAssetPreview(item, true);
}, { immediate: true });
let previousOverflow = "";
let disposed = false, detailRequestNo = 0;
async function loadDetail() {
  const id = props.item?.assetId; if (!id || props.kind !== 'preview' || props.item?.type !== 'TEXT') return;
  const current = ++detailRequestNo;
  try { const result = await getUploadedAsset(id); if (!disposed && current === detailRequestNo && props.item?.assetId === id) { uploaded.value = result; detailError.value = ''; } }
  catch { if (!disposed && current === detailRequestNo && props.item?.assetId === id) detailError.value = '素材详情暂时无法加载'; }
}
async function copyText() { try { await navigator.clipboard.writeText(uploaded.value?.textContent || ''); ElMessage.success('文本已复制'); } catch { ElMessage.warning('无法自动复制，请选中文本后复制'); } }
watch(() => props.item?.assetId, () => { uploaded.value = undefined; detailError.value = ''; void loadDetail(); });
function readDimensions(event: Event) {
  const target = event.target as HTMLImageElement | HTMLVideoElement;
  if (props.item?.type === 'VIDEO' && !(target instanceof HTMLVideoElement)) return;
  const width = target instanceof HTMLVideoElement ? target.videoWidth : target.naturalWidth;
  const height = target instanceof HTMLVideoElement ? target.videoHeight : target.naturalHeight;
  if (width > 0 && height > 0) observed.value = {width,height};
}
function onKey(event: KeyboardEvent) {
  if (event.key === "Escape") { event.preventDefault(); requestClose(); return; }
  if (event.key !== "Tab") return;
  const elements = Array.from(box.value?.querySelectorAll<HTMLElement>('button:not(:disabled), [href], input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex="0"], video[controls], audio[controls]') || []).filter(e => e.getClientRects().length && e.tabIndex >= 0);
  const first = elements[0], last = elements.at(-1);
  if (!first || !last) { event.preventDefault(); return; }
  if (event.shiftKey && (document.activeElement === first || document.activeElement === box.value)) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && (document.activeElement === last || document.activeElement === box.value)) { event.preventDefault(); first.focus(); }
}
watch(() => props.kind, async () => { mediaError.value = false; await nextTick(); box.value?.focus(); if (props.kind === 'preview') void loadDetail(); });
onMounted(() => { previousOverflow = document.body.style.overflow; document.body.style.overflow = "hidden"; box.value?.focus(); void loadDetail(); });
onUnmounted(() => { disposed = true; ++detailRequestNo; document.body.style.overflow = previousOverflow; });
</script>
<style scoped>
.modal { z-index: 200; }
.modal__box { max-height:calc(100dvh - 48px); overflow-y:auto; }
.modal__box.modal__box--file-upload { max-width:640px; padding:0; display:flex; flex-direction:column; overflow:hidden; }
.modal__box.modal__box--carousel-preview { display:flex; flex-direction:column; overflow:hidden; }
.modal__box--carousel-preview>.modal__head,.modal__box--carousel-preview>.modal__foot { flex:none; }
.modal__box.modal__box--drawer { width:min(1200px,calc(100vw - 64px)); max-width:none; height:100dvh; max-height:100dvh; overflow:hidden; }
@media(max-width:600px){.modal__box.modal__box--drawer{width:100%;border-radius:0;}}
.modal__close:disabled { opacity:.45; cursor:not-allowed; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.ac-pmedia video, .ac-pmedia > img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }
.ac-pmedia.asset-media-frame { height:auto; width:min(100%,calc(60dvh * var(--asset-ratio))); margin-inline:auto; }
.asset-media-error { position: absolute; left: 0; right: 0; bottom: 20px; text-align: center; font-size: 13px; color: #4e5069; background: rgba(255,255,255,.9); padding: 8px; }
.modal__title { overflow-wrap: anywhere; }
.ac-pmedia audio { position:absolute; width:calc(100% - 48px); top:50%; left:24px; transform:translateY(-50%); }
.asset-text-media { aspect-ratio:auto; max-height:420px; min-height:200px; overflow:auto; background:#f7f7fc; }
.asset-text-media pre { padding:24px; white-space:pre-wrap; overflow-wrap:anywhere; font-family:inherit; font-size:14px; line-height:1.8; color:#50536f; }
</style>
