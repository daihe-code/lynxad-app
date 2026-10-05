<template>
  <div class="modal" role="dialog" aria-modal="true" :aria-labelledby="kind === 'detail' ? 'asset-detail-title' : 'asset-modal-title'" @keydown="onKey">
    <div class="modal__mask" @click="emit('close')"></div>
    <div ref="box" class="modal__box" :class="`modal__box--${kind === 'upload' ? 'md' : kind === 'preview' ? 'lg' : 'drawer'}`" tabindex="-1">
      <template v-if="kind === 'upload'">
        <div class="modal__head"><h2 id="asset-modal-title" class="modal__title">上传素材</h2><button class="modal__close" type="button" aria-label="关闭" @click="emit('close')"><AssetIcon name="close" /></button></div>
        <input ref="fileInput" class="sr-only" type="file" aria-label="选择上传文件" accept=".mp4,.mov,.jpg,.jpeg,.png" multiple tabindex="-1" @change="fileChanged" />
        <div class="ac-drop" role="button" tabindex="0" aria-label="选择本地文件上传" @click="fileInput?.click()" @keydown.enter="fileInput?.click()" @keydown.space.prevent="fileInput?.click()" @dragover.prevent @drop.prevent="addFiles($event.dataTransfer?.files)"><span class="ac-drop__ic"><AssetIcon name="upload" /></span><span class="ac-drop__t">点击选择文件，或将文件拖拽到此处</span><span class="ac-drop__d">支持 MP4 / MOV / JPG / PNG，单个文件不超过 500MB<br>可先选择文件；独立上传及合规预检接口待接入</span><span class="ac-drop__btn"><AssetIcon name="folder" />浏览本地文件</span></div>
        <div v-if="files.length" class="ac-files"><div v-for="(file, index) in files" :key="`${file.name}-${file.lastModified}`" class="ac-file"><span class="ac-file__ic"><AssetIcon :name="/\.(mp4|mov)$/i.test(file.name) ? 'video' : 'image'" /></span><div class="ac-file__b"><span class="ac-file__n">{{ file.name }}</span><span class="ac-file__m">{{ fileSize(file.size) }} · 待上传</span></div><button class="ac-file__del" type="button" :aria-label="`移除 ${file.name}`" @click="files.splice(index, 1)"><AssetIcon name="close" /></button></div></div>
        <div class="ac-rules"><span v-for="rule in rules" :key="rule" class="ac-rule"><AssetIcon name="check-circle" />{{ rule }}</span></div>
        <div class="modal__foot"><button class="btn-modal-ghost" type="button" @click="emit('close')">取消</button><span title="独立上传接口尚未接入"><button class="btn-modal-primary" type="button" disabled>开始上传</button></span></div>
      </template>
      <template v-else-if="item">
        <template v-if="kind === 'preview'">
          <div class="modal__head"><h2 id="asset-modal-title" class="modal__title">{{ item.filename }}</h2><button class="modal__close" type="button" aria-label="关闭" @click="emit('close')"><AssetIcon name="close" /></button></div>
          <div class="ac-pmedia ac-t0"><video v-if="item.type === 'VIDEO' && item.playUrl" :key="item.id" :src="assetUrl(item.playUrl)" :poster="assetUrl(item.posterUrl)" controls playsinline preload="metadata" @loadedmetadata="readDimensions" @error="mediaError = true"></video><img v-else-if="item.playUrl || item.posterUrl" :src="assetUrl(item.playUrl || item.posterUrl)" :alt="item.filename" @load="readDimensions" @error="mediaError = true" /><span v-else class="asset-media-error">暂无可预览的文件</span><span v-if="mediaError" class="asset-media-error" role="status">素材文件暂不可播放或预览</span></div>
          <div class="ac-pinfo"><span>规格 <b>{{ dimensions }} · {{ assetRatio(item) }}</b></span><span>大小 <b>{{ fileSize(item.sizeBytes) }}</b></span><span v-if="item.type === 'VIDEO'">时长 <b>{{ duration(item.durationSec) }}</b></span><span>引用计划 <b>—</b></span><span>状态 <b>{{ assetStatus(item).label }}</b></span></div>
          <div class="modal__foot"><button class="btn-modal-ghost" type="button" @click="emit('close')">关闭</button><button class="btn-modal-primary" type="button" @click="emit('switch', 'detail')">查看素材详情</button></div>
        </template>
        <template v-else>
          <div class="ac-dmedia ac-t0"><img v-if="item.posterUrl && !mediaError" :src="assetUrl(item.posterUrl)" :alt="item.filename" @error="mediaError = true" /><AssetIcon v-else class="asset-placeholder" :name="item.type === 'VIDEO' ? 'video' : 'image'" /><span class="ac-src" :class="{ 'ac-src--plat': item.source === 'EXTERNAL' }">{{ sourceLabel(item) }}</span><button class="ac-hbtn asset-detail-play" type="button" aria-label="预览该素材" @click="emit('switch', 'preview')"><AssetIcon name="play" />{{ item.type === 'VIDEO' ? '试播' : '预览' }}</button></div>
          <div class="ac-dbody">
            <div class="modal__head"><h2 id="asset-detail-title" class="ac-dtitle">{{ item.filename }}</h2><button class="modal__close" type="button" aria-label="关闭" @click="emit('close')"><AssetIcon name="close" /></button></div>
            <div class="ac-dmeta"><span>{{ item.materialId || item.objectId || item.id }}</span><span>{{ { VIDEO: '视频库', IMAGE: '图片库', CAROUSEL: '图文库' }[item.type] }}</span><span class="ac-use" :class="assetStatus(item).cls"><i></i>{{ assetStatus(item).label }}</span></div>
            <div class="ac-tags"><span v-if="item.category" class="ac-tag">{{ item.category }}</span></div><p class="ac-dnote">{{ item.reviewBadge === 'REJECTED' ? '素材审核未通过，请检查素材内容后重新提交审核。' : item.reviewBadge === 'REVIEWING' ? '素材正在审核中，请等待平台审核结果。' : '素材信息来自当前素材库，预览以实际文件为准。' }}</p>
            <div class="ac-dl"><div v-for="spec in specs" :key="spec[0]"><p class="ac-dl__k">{{ spec[0] }}</p><p class="ac-dl__v">{{ spec[1] }}</p></div></div>
            <div class="ac-dsec"><div class="ac-dsec__t"><span>投放效果</span><span class="ac-dsec__h">近 7 天 · 净成交口径</span></div><div class="ac-dempty">当前接口尚未返回该素材的投放效果数据。</div></div>
            <div class="ac-dsec"><div class="ac-dsec__t"><span>被引用的投放计划</span><span class="ac-dsec__h">{{ item.used ? '引用中' : '未被引用' }}</span></div><div class="ac-refs"><div class="ac-dempty">{{ item.used ? '该素材已被投放计划引用，引用计划明细待接口提供。' : '尚未被任何投放计划引用。' }}</div></div></div>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type { LibraryItemVo } from "@/features/creative/creativeApi";
import AssetIcon from "./AssetIcon.vue";
import { assetUrl, duration, fileSize, sourceLabel, assetStatus, assetRatio, assetDimensions } from "./assetFormat";
const props = defineProps<{ kind: "upload" | "detail" | "preview"; item: LibraryItemVo | null }>();
const emit = defineEmits<{ close: []; switch: [kind: "detail" | "preview"] }>();
const rules = ["视频建议时长 9-30 秒，前 3 秒出现核心卖点", "竖版 9:16（1080×1920）优先适配商品全域投放", "图片内文字占比建议低于 20%，避免审核驳回", "重复文件只保留一次选择，上传后查重能力待接入"];
const box = ref<HTMLElement>(), fileInput = ref<HTMLInputElement>(), files = ref<File[]>([]);
const mediaError = ref(false), dimensions = ref(props.item ? assetDimensions(props.item) : "—");
let previousOverflow = "";
const specs = computed(() => {
  const a = props.item; if (!a) return [];
  const values = [["素材规格", dimensions.value], ["画幅比例", assetRatio(a)], ["文件大小", fileSize(a.sizeBytes)]];
  if (a.type === "VIDEO") values.push(["视频时长", duration(a.durationSec)]);
  return [...values, ["所属类目", a.category || "—"], ["上传时间", a.createdAt ? new Date(a.createdAt).toLocaleString("zh-CN") : "—"], ["素材来源", sourceLabel(a)], ["入库状态", assetStatus(a).label]];
});
function readDimensions(event: Event) { const target = event.target as HTMLImageElement | HTMLVideoElement; const w = target instanceof HTMLVideoElement ? target.videoWidth : target.naturalWidth; const h = target instanceof HTMLVideoElement ? target.videoHeight : target.naturalHeight; if (w && h) dimensions.value = `${w}×${h}`; }
function onKey(event: KeyboardEvent) {
  if (event.key === "Escape") { event.preventDefault(); emit("close"); return; }
  if (event.key !== "Tab") return;
  const elements = Array.from(box.value?.querySelectorAll<HTMLElement>('button:not(:disabled), [href], input:not(:disabled), [tabindex="0"], video[controls]') || []).filter(e => e.getClientRects().length && e.tabIndex >= 0);
  const first = elements[0], last = elements.at(-1);
  if (!first || !last) { event.preventDefault(); return; }
  if (event.shiftKey && (document.activeElement === first || document.activeElement === box.value)) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && (document.activeElement === last || document.activeElement === box.value)) { event.preventDefault(); first.focus(); }
}
function addFiles(list: FileList | null | undefined) {
  for (const file of Array.from(list || [])) {
    if (!/\.(mp4|mov|jpe?g|png)$/i.test(file.name) || file.size > 500 * 1024 * 1024 || file.size === 0) { ElMessage.warning(`${file.name}：请选择支持的非空文件，且不超过 500MB`); continue; }
    if (!files.value.some(f => f.name === file.name && f.size === file.size && f.lastModified === file.lastModified)) files.value.push(file);
  }
}
function fileChanged(event: Event) { const input = event.target as HTMLInputElement; addFiles(input.files); input.value = ""; }
watch(() => props.kind, async () => { mediaError.value = false; await nextTick(); box.value?.focus(); });
onMounted(() => { previousOverflow = document.body.style.overflow; document.body.style.overflow = "hidden"; box.value?.focus(); });
onUnmounted(() => { document.body.style.overflow = previousOverflow; });
</script>
<style scoped>
.modal { z-index: 200; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.ac-file__ic svg { width: 18px; height: 18px; }
.ac-pmedia video, .ac-pmedia > img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }
.ac-dmedia > img { width: 100%; height: 100%; object-fit: cover; }
.asset-placeholder { position: absolute; top: 50%; left: 50%; width: 44px; height: 44px; transform: translate(-50%, -50%); color: #7c7f99; }
.asset-detail-play { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); }
.asset-media-error { position: absolute; left: 0; right: 0; bottom: 20px; text-align: center; font-size: 13px; color: #4e5069; background: rgba(255,255,255,.9); padding: 8px; }
.ac-dmeta span:first-child, .ac-dl__v, .ac-dtitle, .modal__title { overflow-wrap: anywhere; }
</style>
