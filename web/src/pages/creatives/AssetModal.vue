<template>
  <div class="modal" role="dialog" aria-modal="true" :aria-labelledby="kind === 'detail' ? 'asset-detail-title' : 'asset-modal-title'" @keydown="onKey">
    <div class="modal__mask" @click="requestClose"></div>
    <div ref="box" class="modal__box" :class="`modal__box--${kind === 'upload' ? 'md' : kind === 'preview' ? 'lg' : 'drawer'}`" tabindex="-1">
      <template v-if="kind === 'upload'">
        <div class="modal__head"><h2 id="asset-modal-title" class="modal__title">上传素材</h2><button class="modal__close" type="button" aria-label="关闭" :disabled="closeBlocked" @click="requestClose"><AssetIcon name="close" /></button></div>
        <CarouselUploadPanel v-if="libraryType === 'CAROUSEL'" @close="requestClose" @close-blocked="uploadCloseBlocked = $event" @changed="emit('changed')" />
        <AssetUploadPanel v-else :library-type="libraryType" @close="requestClose" @close-blocked="uploadCloseBlocked = $event" @changed="emit('changed')" />
      </template>
      <template v-else-if="item">
        <template v-if="item.carouselId">
          <div class="modal__head"><h2 :id="kind === 'detail' ? 'asset-detail-title' : 'asset-modal-title'" class="modal__title">{{ item.filename }}</h2><button class="modal__close" type="button" aria-label="关闭" @click="emit('close')"><AssetIcon name="close" /></button></div>
          <CarouselPreview :id="item.carouselId" @changed="emit('changed')" />
          <div class="modal__foot"><button class="btn-modal-ghost" type="button" @click="emit('close')">关闭</button></div>
        </template>
        <template v-else>
        <template v-if="kind === 'preview'">
          <div class="modal__head"><h2 id="asset-modal-title" class="modal__title">{{ item.filename }}</h2><button class="modal__close" type="button" aria-label="关闭" @click="emit('close')"><AssetIcon name="close" /></button></div>
          <div class="ac-pmedia ac-t0" :class="{ 'asset-text-media': item.type === 'TEXT' }"><pre v-if="item.type === 'TEXT'">{{ uploaded?.textContent || detailError || '正在读取文本…' }}</pre><audio v-else-if="item.type === 'AUDIO' && item.playUrl" :src="assetUrl(item.playUrl)" controls preload="metadata" @error="mediaError = true"></audio><video v-else-if="item.type === 'VIDEO' && previewUrls?.playUrl" :key="item.id" :src="assetUrl(previewUrls.playUrl)" :poster="assetUrl(previewUrls.posterUrl)" controls playsinline preload="metadata" @loadedmetadata="readDimensions" @error="previewFailed"></video><img v-else-if="previewUrls?.playUrl || previewUrls?.posterUrl" :src="assetUrl(previewUrls.playUrl || previewUrls.posterUrl)" :alt="item.filename" @load="readDimensions" @error="previewFailed" /><span v-else class="asset-media-error">{{ previewUrls?.message || '暂无可预览的文件' }}</span><span v-if="mediaError" class="asset-media-error" role="status">{{ previewUrls?.message || '素材文件暂不可播放或预览' }}</span></div>
          <div class="ac-pinfo"><span>规格 <b>{{ dimensions }} · {{ assetRatio(item) }}</b></span><span>大小 <b>{{ fileSize(item.sizeBytes) }}</b></span><span v-if="item.type === 'VIDEO'">时长 <b>{{ duration(item.durationSec) }}</b></span><span>引用计划 <b>—</b></span><span>状态 <b>{{ assetStatus(item).label }}</b></span></div>
          <div class="modal__foot"><button v-if="item.type === 'TEXT' && uploaded?.textContent" class="btn-modal-ghost" type="button" @click="copyText">复制文本</button><button class="btn-modal-ghost" type="button" @click="emit('close')">关闭</button><button class="btn-modal-primary" type="button" @click="emit('switch', 'detail')">查看素材详情</button></div>
        </template>
        <template v-else>
          <div class="ac-dmedia ac-t0"><AssetThumbnail :item="item" /><span class="ac-src" :class="{ 'ac-src--plat': item.source === 'EXTERNAL' }">{{ sourceLabel(item) }}</span><button class="ac-hbtn asset-detail-play" type="button" aria-label="预览该素材" @click="emit('switch', 'preview')"><AssetIcon name="play" />{{ item.type === 'VIDEO' ? '试播' : '预览' }}</button></div>
          <div class="ac-dbody">
            <div class="modal__head"><h2 id="asset-detail-title" class="ac-dtitle">{{ item.filename }}</h2><button class="modal__close" type="button" aria-label="关闭" @click="emit('close')"><AssetIcon name="close" /></button></div>
            <div class="ac-dmeta"><span>{{ item.materialId || item.objectId || item.id }}</span><span>{{ { VIDEO: '视频库', IMAGE: '图片库', CAROUSEL: '图文库', AUDIO: '音频库', TEXT: '文本库' }[item.type] }}</span><span class="ac-use" :class="assetStatus(item).cls"><i></i>{{ assetStatus(item).label }}</span></div>
            <div class="ac-tags"><span v-if="item.category" class="ac-tag">{{ item.category }}</span></div><p class="ac-dnote">{{ item.reviewBadge === 'REJECTED' ? '素材审核未通过，请检查素材内容后重新提交审核。' : item.reviewBadge === 'REVIEWING' ? '素材正在审核中，请等待平台审核结果。' : '素材信息来自当前素材库，预览以实际文件为准。' }}</p>
            <div class="ac-dl"><div v-for="spec in specs" :key="spec[0]"><p class="ac-dl__k">{{ spec[0] }}</p><p class="ac-dl__v">{{ spec[1] }}</p></div></div>
            <div v-if="item.assetId" class="ac-dsec asset-sync-detail"><div class="ac-dsec__t"><span>平台入库与千川同步</span></div><p v-if="detailError" role="alert">{{ detailError }} <button type="button" @click="loadDetail">重新加载</button></p><p>平台存储：{{ uploaded?.storageStatus === 'STORED' ? '已入库' : item.storageStatus }}</p><p v-if="item.type === 'TEXT'">{{ uploaded?.aiReview?.status === 'NOT_REQUIRED' ? '引擎文案已登记，无需上传审核。' : '文本已入库，须通过 AI 审核后使用。' }}文本不作为独立千川文件上传。</p><p v-else-if="uploaded && !uploaded.syncs.length">上传时未选择千川同步账户，仅保存在本平台。</p><div v-for="s in uploaded?.syncs || item.syncs || []" :key="s.id" class="asset-sync-row"><div><b>{{ s.advertiserId ? `广告账户 ${s.advertiserId}` : `授权记录 ${s.accountId}` }}</b><p>{{ syncLabel(s.status, s.reviewStatus) }}</p><small>{{ s.errorMessage }}</small><p v-if="s.objectId">文件 ID：{{ s.objectId }}<br>素材 ID：{{ s.materialId }}</p></div><button v-if="['FAILED', 'WAITING_AUTH', 'PAUSED', 'UNCERTAIN'].includes(s.status)" type="button" :disabled="retrying === s.id || !['APPROVED','NOT_REQUIRED'].includes(uploaded?.aiReview?.status || '')" @click="retrySync(s)">重试同步</button></div></div>
            <AssetReviewStatus v-if="item.assetId" :review="uploaded?.aiReview || item.aiReview" :retrying="reviewRetrying" @retry="retryReview" />
            <div class="ac-dsec"><div class="ac-dsec__t"><span>投放效果</span><span class="ac-dsec__h">近 7 天 · 净成交口径</span></div><div class="ac-dempty">当前接口尚未返回该素材的投放效果数据。</div></div>
            <div class="ac-dsec"><div class="ac-dsec__t"><span>被引用的投放计划</span><span class="ac-dsec__h">{{ item.used ? '引用中' : '未被引用' }}</span></div><div class="ac-refs"><div class="ac-dempty">{{ item.used ? '该素材已被投放计划引用，引用计划明细待接口提供。' : '尚未被任何投放计划引用。' }}</div></div></div>
          </div>
        </template>
        </template>
      </template>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { LibraryItemVo } from "@/features/creative/creativeApi";
import AssetIcon from "./AssetIcon.vue";
import AssetThumbnail from "./AssetThumbnail.vue";
import AssetUploadPanel from "./AssetUploadPanel.vue";
import CarouselUploadPanel from './CarouselUploadPanel.vue';
import CarouselPreview from './CarouselPreview.vue';
import AssetReviewStatus from './AssetReviewStatus.vue';
import { getUploadedAsset, retryAssetSync, retryAssetReview, type AssetSync, type UploadedAsset } from "@/features/creative/creativeApi";
import { syncLabel, reviewPending } from "@/features/creative/assetUploads";
import { assetPreview, resolveAssetPreview } from '@/features/creative/assetPreviews';
const reviewRetrying = ref(false);
import { assetUrl, duration, fileSize, sourceLabel, assetStatus, assetRatio, assetDimensions } from "./assetFormat";
const props = withDefaults(defineProps<{ kind: "upload" | "detail" | "preview"; item: LibraryItemVo | null; libraryType?: LibraryItemVo['type'] }>(), { libraryType: 'VIDEO' });
const emit = defineEmits<{ close: []; switch: [kind: "detail" | "preview"]; changed: [] }>();
const uploadCloseBlocked = ref(false);
const closeBlocked = computed(() => props.kind === 'upload' && uploadCloseBlocked.value);
function requestClose() { if (!closeBlocked.value) emit('close'); }
const box = ref<HTMLElement>();
const uploaded = ref<UploadedAsset>(), detailError = ref(""), retrying = ref("");
const mediaError = ref(false), dimensions = ref(props.item ? assetDimensions(props.item) : "—");
const previewUrls = computed(() => props.item ? assetPreview(props.item) : undefined);
async function previewFailed() {
  const item = props.item; if (!item) return;
  if (assetPreview(item).source === 'ORIGINAL' && await resolveAssetPreview(item)) return;
  if (props.item?.id === item.id) mediaError.value = true;
}
let previousOverflow = "";
let detailTimer: ReturnType<typeof setInterval> | undefined;
async function loadDetail() {
  const id = props.item?.assetId; if (!id) return;
  try { const result = await getUploadedAsset(id); if (props.item?.assetId === id) { uploaded.value = result; detailError.value = ''; } }
  catch { detailError.value = '素材详情暂时无法加载'; }
}
async function copyText() { try { await navigator.clipboard.writeText(uploaded.value?.textContent || ''); ElMessage.success('文本已复制'); } catch { ElMessage.warning('无法自动复制，请选中文本后复制'); } }
async function retryReview() {
  if (!props.item?.assetId) return;
  reviewRetrying.value = true;
  try { uploaded.value = await retryAssetReview(props.item.assetId); emit('changed'); }
  finally { reviewRetrying.value = false; }
}
async function retrySync(sync: AssetSync) {
  if (!props.item?.assetId) return;
  if (sync.status === 'UNCERTAIN') { try { await ElMessageBox.confirm('上次上传可能已被千川接收，请先核对平台结果，避免重复上传。', '确认重试', { confirmButtonText: '已核对，重新上传', cancelButtonText: '取消' }); } catch { return; } }
  retrying.value = sync.id;
  try { uploaded.value = await retryAssetSync(props.item.assetId, sync.accountId, sync.status === 'UNCERTAIN'); emit('changed'); }
  finally { retrying.value = ''; }
}
watch(() => props.item?.assetId, () => { uploaded.value = undefined; detailError.value = ''; void loadDetail(); });
const specs = computed(() => {
  const a = props.item; if (!a) return [];
  const values = [["素材规格", dimensions.value], ["画幅比例", assetRatio(a)], ["文件大小", fileSize(a.sizeBytes)]];
  if (a.type === "VIDEO") values.push(["视频时长", duration(a.durationSec)]);
  return [...values, ["所属类目", a.category || "—"], ["上传时间", a.createdAt ? new Date(a.createdAt).toLocaleString("zh-CN") : "—"], ["素材来源", sourceLabel(a)], ["入库状态", assetStatus(a).label]];
});
function readDimensions(event: Event) { const target = event.target as HTMLImageElement | HTMLVideoElement; const w = target instanceof HTMLVideoElement ? target.videoWidth : target.naturalWidth; const h = target instanceof HTMLVideoElement ? target.videoHeight : target.naturalHeight; if (w && h) dimensions.value = `${w}×${h}`; }
function onKey(event: KeyboardEvent) {
  if (event.key === "Escape") { event.preventDefault(); requestClose(); return; }
  if (event.key !== "Tab") return;
  const elements = Array.from(box.value?.querySelectorAll<HTMLElement>('button:not(:disabled), [href], input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex="0"], video[controls], audio[controls]') || []).filter(e => e.getClientRects().length && e.tabIndex >= 0);
  const first = elements[0], last = elements.at(-1);
  if (!first || !last) { event.preventDefault(); return; }
  if (event.shiftKey && (document.activeElement === first || document.activeElement === box.value)) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && (document.activeElement === last || document.activeElement === box.value)) { event.preventDefault(); first.focus(); }
}
watch(() => props.kind, async () => { mediaError.value = false; await nextTick(); box.value?.focus(); });
onMounted(() => { previousOverflow = document.body.style.overflow; document.body.style.overflow = "hidden"; box.value?.focus(); void loadDetail(); detailTimer = setInterval(() => { if (reviewPending(uploaded.value?.aiReview?.status) || uploaded.value?.syncs.some(s => ['QUEUED', 'RUNNING'].includes(s.status))) void loadDetail(); }, 4000); });
onUnmounted(() => { document.body.style.overflow = previousOverflow; clearInterval(detailTimer); });
</script>
<style scoped>
.modal { z-index: 200; }
.modal__box { max-height:calc(100dvh - 48px); overflow-y:auto; }
.modal__close:disabled { opacity:.45; cursor:not-allowed; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.ac-file__ic svg { width: 18px; height: 18px; }
.ac-pmedia video, .ac-pmedia > img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }
.asset-detail-play { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); }
.asset-media-error { position: absolute; left: 0; right: 0; bottom: 20px; text-align: center; font-size: 13px; color: #4e5069; background: rgba(255,255,255,.9); padding: 8px; }
.ac-dmeta span:first-child, .ac-dl__v, .ac-dtitle, .modal__title { overflow-wrap: anywhere; }
.ac-pmedia audio { position:absolute; width:calc(100% - 48px); top:50%; left:24px; transform:translateY(-50%); }
.asset-text-media { aspect-ratio:auto; max-height:420px; min-height:200px; overflow:auto; background:#f7f7fc; }
.asset-text-media pre { padding:24px; white-space:pre-wrap; overflow-wrap:anywhere; font-family:inherit; font-size:14px; line-height:1.8; color:#50536f; }
.asset-sync-detail { font-size:13px; line-height:1.8; }.asset-sync-detail > p { margin-top:10px; }.asset-sync-detail button { color:#5b55ff; }.asset-sync-row { display:flex; gap:16px; justify-content:space-between; border-bottom:1px solid #eeeef7; padding:12px 0; overflow-wrap:anywhere; }.asset-sync-row small { color:#8b8ea6; }.asset-sync-row button { flex:none; align-self:center; }
</style>
