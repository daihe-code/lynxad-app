<template>
  <div ref="container" class="asset-thumbnail" :data-state="ready ? 'ready' : failed ? 'error' : 'loading'">
    <img v-if="imageUrl && !imageFailed" :src="imageUrl" :alt="item.filename" loading="lazy" @load="ready = true" @error="imageFailed = true; ready = false" />
    <video v-else-if="item.type === 'VIDEO' && item.playUrl && visible && !videoFailed"
      ref="video" :src="assetUrl(item.playUrl)" :class="{ 'is-ready': ready }"
      muted playsinline preload="metadata" tabindex="-1" aria-hidden="true"
      @loadedmetadata="seekFrame" @loadeddata="frameReady" @seeked="frameReady" @error="videoFailed = true; ready = false"></video>
    <AssetIcon v-if="!ready" :name="item.type === 'VIDEO' ? 'video' : item.type === 'AUDIO' ? 'audio' : item.type === 'TEXT' ? 'note' : 'image'" class="asset-placeholder" />
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { LibraryItemVo } from '@/features/creative/creativeApi';
import AssetIcon from './AssetIcon.vue';
import { assetUrl } from './assetFormat';

const props = defineProps<{ item: LibraryItemVo }>();
const container = ref<HTMLElement>();
const video = ref<HTMLVideoElement>();
const visible = ref(false);
const ready = ref(false);
const imageFailed = ref(false);
const videoFailed = ref(false);
const imageUrl = computed(() => assetUrl(props.item.posterUrl || (props.item.type === 'IMAGE' ? props.item.playUrl : '')));
const failed = computed(() => props.item.type === 'VIDEO' ? videoFailed.value : imageFailed.value);
let observer: IntersectionObserver | undefined;

function seekFrame(event: Event) {
  const media = event.target as HTMLVideoElement;
  // Seek near the beginning to decode a still frame; never start playback.
  if (Number.isFinite(media.duration) && media.duration > 0) media.currentTime = Math.min(0.1, media.duration / 2);
}
function frameReady(event: Event) {
  const media = event.target as HTMLVideoElement;
  if (!media.seeking && media.readyState >= 2 && media.videoWidth > 0) ready.value = true;
}
watch(() => [props.item.id, props.item.posterUrl, props.item.playUrl], () => {
  ready.value = false; imageFailed.value = false; videoFailed.value = false;
});
onMounted(() => {
  if (!('IntersectionObserver' in window)) { visible.value = true; return; }
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) {
      visible.value = true;
      observer?.disconnect();
    }
  }, { rootMargin: '100px' });
  if (container.value) observer.observe(container.value);
});
onBeforeUnmount(() => {
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
.asset-thumbnail img, .asset-thumbnail video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.asset-thumbnail video { opacity: 0; }
.asset-thumbnail video.is-ready { opacity: 1; }
.asset-placeholder { width: 44px; height: 44px; color: #7c7f99; }
</style>
