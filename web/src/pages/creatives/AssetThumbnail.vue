<template>
  <div ref="container" class="asset-thumbnail" :style="{ '--asset-thumbnail-fit': fit }" :data-state="ready ? 'ready' : failed ? 'error' : 'loading'">
    <img v-if="imageUrl && !imageFailed" :key="imageUrl" :src="imageUrl" :alt="item.filename" loading="lazy" @load="ready = true; attempts = 0" @error="imageFailed = true; ready = false" />
    <video v-else-if="item.type === 'VIDEO' && urls.playUrl && visible && !videoFailed"
      ref="video" :key="urls.playUrl" :src="assetUrl(urls.playUrl)" :class="{ 'is-ready': ready }"
      muted playsinline preload="metadata" tabindex="-1" aria-hidden="true"
      @loadedmetadata="seekFrame" @loadeddata="frameReady" @seeked="frameReady" @error="videoFailed = true; ready = false"></video>
    <AssetIcon v-if="!ready" :name="item.type === 'VIDEO' ? 'video' : item.type === 'AUDIO' ? 'audio' : item.type === 'TEXT' ? 'note' : 'image'" class="asset-placeholder" />
    <span v-if="failed && !resolving" class="preview-error" :title="urls.message || '素材内容暂不可读取，请检查存储服务后刷新'">预览暂不可用</span>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { LibraryItemVo } from '@/features/creative/creativeApi';
import AssetIcon from './AssetIcon.vue';
import { assetUrl } from './assetFormat';
import { assetPreview, previewNeedsRefresh, resolveAssetPreview } from '@/features/creative/assetPreviews';

const props = withDefaults(defineProps<{ item: LibraryItemVo; fit?: 'cover' | 'contain' }>(), { fit: 'cover' });
const container = ref<HTMLElement>();
const video = ref<HTMLVideoElement>();
const visible = ref(false);
const ready = ref(false);
const imageFailed = ref(false);
const videoFailed = ref(false);
const urls = computed(() => assetPreview(props.item));
const imageUrl = computed(() => assetUrl(urls.value.posterUrl || (props.item.type === 'IMAGE' ? urls.value.playUrl : '')));
const failed = computed(() => ['VIDEO', 'IMAGE', 'CAROUSEL'].includes(props.item.type) &&
  (props.item.type === 'VIDEO' ? (videoFailed.value || !urls.value.playUrl && (imageFailed.value || !imageUrl.value)) : imageFailed.value || !imageUrl.value));
const resolving = ref(false);
let attempts = 0, alive = true;
let observer: IntersectionObserver | undefined;
function show() {
  visible.value = true;
  if (previewNeedsRefresh(props.item)) void resolveAssetPreview(props.item);
}

function seekFrame(event: Event) {
  const media = event.target as HTMLVideoElement;
  // Seek near the beginning to decode a still frame; never start playback.
  if (Number.isFinite(media.duration) && media.duration > 0) media.currentTime = Math.min(0.1, media.duration / 2);
}
function frameReady(event: Event) {
  const media = event.target as HTMLVideoElement;
  if (!media.seeking && media.readyState >= 2 && media.videoWidth > 0) { ready.value = true; attempts = 0; }
}
watch(() => JSON.stringify([props.item.id, props.item.posterUrl, props.item.playUrl]), () => {
  attempts = 0;
});
watch(imageUrl, () => {
  ready.value = false; imageFailed.value = false; videoFailed.value = false;
});
watch(() => urls.value.playUrl, () => {
  videoFailed.value = false;
  if (!imageUrl.value || imageFailed.value) ready.value = false;
});
watch([failed, visible], async () => {
  if (!failed.value || !visible.value || attempts >= 2 || resolving.value) return;
  const refresh = assetPreview(props.item).source === 'QIANCHUAN';
  attempts++; resolving.value = true;
  await resolveAssetPreview(props.item, refresh);
  if (alive) resolving.value = false;
}, { immediate: true });
onMounted(() => {
  if (!('IntersectionObserver' in window)) { show(); return; }
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) {
      show();
      observer?.disconnect();
    }
  }, { rootMargin: '100px' });
  if (container.value) observer.observe(container.value);
});
onBeforeUnmount(() => {
  alive = false;
  observer?.disconnect();
  if (video.value) {
    video.value.pause();
    video.value.removeAttribute('src');
    video.value.load();
  }
});
</script>
<style scoped>
.asset-thumbnail { position: absolute; inset: 0; display: grid; place-items: center; overflow: hidden; pointer-events: none; }
.asset-thumbnail img, .asset-thumbnail video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: var(--asset-thumbnail-fit, cover); }
.asset-thumbnail video { opacity: 0; }
.asset-thumbnail video.is-ready { opacity: 1; }
.asset-placeholder { width: 44px; height: 44px; color: #7c7f99; }
.preview-error { position: absolute; bottom: 5px; color: #7c7f99; font-size: 11px; }
</style>
