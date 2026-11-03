<template>
  <section class="carousel-library-preview" aria-label="图文内容预览">
    <p v-if="loading" class="preview-status" role="status">正在加载图文…</p>
    <p v-else-if="loadError" class="preview-error" role="alert">{{ loadError }} <button type="button" @click="load">重试</button></p>
    <template v-else-if="detail">
      <div v-if="images.length" class="carousel-gallery" tabindex="0" aria-label="图文图片" @keydown.left.prevent="previous" @keydown.right.prevent="next">
        <img v-if="imageUrl && !imageError" :key="currentImage!.id" :src="assetUrl(imageUrl)" :alt="`第 ${imageIndex + 1} 张图文图片`" @error="imageFailed(currentImage!)" />
        <p v-else class="preview-error" role="alert">图片暂不可预览。<button type="button" @click="load">重新加载</button></p>
      </div>
      <nav v-if="images.length > 1" class="carousel-navigation" aria-label="图文图片切换">
        <button type="button" aria-label="上一张图片" :disabled="imageIndex === 0" @click="previous"><ReferenceIcon name="arrow-left" /></button>
        <span aria-live="polite">{{ imageIndex + 1 }} / {{ images.length }}</span>
        <button type="button" aria-label="下一张图片" :disabled="imageIndex === images.length - 1" @click="next"><ReferenceIcon name="forward" /></button>
      </nav>
      <audio v-if="audio?.url" ref="audioElement" :key="audio.id" :src="assetUrl(audio.url)" controls preload="metadata" aria-label="图文音频" @error="audioError = true" />
      <p v-if="audioError || !audio?.url" class="preview-error" role="alert">音频暂不可播放。<button type="button" @click="load">重新加载</button></p>
      <p v-if="description" class="carousel-description">{{ description }}</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { getCarousel, type CarouselDetail, type UploadedAsset } from '@/features/creative/creativeApi';
import { assetPreview, clearAssetPreviews, resolveAssetPreview, type PreviewItem } from '@/features/creative/assetPreviews';
import { assetUrl } from '@/pages/creatives/assetFormat';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
const props = defineProps<{ id?: string }>();
const emit = defineEmits<{ ready: [] }>();
const detail = ref<CarouselDetail>(), loading = ref(false), loadError = ref(''), imageIndex = ref(0), audioError = ref(false);
const imageErrors = ref(new Set<string>()), audioElement = ref<HTMLAudioElement>();
const images = computed(() => detail.value?.parts.filter(part => part.role === 'IMAGE').sort((a, b) => a.position - b.position).map(part => part.asset) || []);
const currentImage = computed(() => images.value[imageIndex.value]);
const audio = computed(() => detail.value?.parts.find(part => part.role === 'AUDIO')?.asset);
const description = computed(() => detail.value?.parts.filter(part => part.role === 'TEXT').sort((a, b) => a.position - b.position).map(part => part.asset.textContent?.trim()).filter(Boolean).join('\n') || '');
function imageItem(asset: UploadedAsset): PreviewItem { return { id: asset.id, assetId: asset.id, type: 'IMAGE', posterUrl: asset.url, playUrl: asset.url }; }
const imageUrl = computed(() => currentImage.value ? assetPreview(imageItem(currentImage.value)).posterUrl || assetPreview(imageItem(currentImage.value)).playUrl : '');
const imageError = computed(() => !!currentImage.value && imageErrors.value.has(currentImage.value.id));
let request = 0, alive = true;
function stopAudio() { const media = audioElement.value; if (media) { media.pause(); media.removeAttribute('src'); media.load(); } }
async function load() {
  const version = ++request, id = props.id;
  stopAudio(); clearAssetPreviews(images.value.map(imageItem)); detail.value = undefined;
  imageIndex.value = 0; imageErrors.value.clear(); audioError.value = false; loadError.value = ''; loading.value = true;
  try {
    if (!id) throw new Error('missing carousel');
    const result = await getCarousel(id);
    if (!alive || version !== request) return;
    detail.value = result;
  } catch { if (alive && version === request) loadError.value = '暂时无法加载图文，请重试。'; }
  finally { if (alive && version === request) { loading.value = false; await nextTick(); emit('ready'); } }
}
async function imageFailed(asset: UploadedAsset) {
  const version = request;
  if (assetPreview(imageItem(asset)).source === 'ORIGINAL' && await resolveAssetPreview(imageItem(asset))) return;
  if (alive && version === request) imageErrors.value.add(asset.id);
}
function previous() { if (imageIndex.value > 0) imageIndex.value--; }
function next() { if (imageIndex.value < images.value.length - 1) imageIndex.value++; }
watch(() => props.id, load, { immediate: true });
onBeforeUnmount(() => { alive = false; ++request; stopAudio(); });
</script>
<style scoped>
.carousel-library-preview{margin-top:12px;min-width:0}.carousel-gallery{display:grid;grid-template:minmax(0,1fr)/minmax(0,1fr);place-items:center;height:clamp(160px,32dvh,260px);border-radius:8px;background:#f5f5fa;overflow:hidden}.carousel-gallery img{display:block;min-width:0;min-height:0;width:100%;height:100%;object-fit:contain;margin:0}.carousel-navigation{display:flex;align-items:center;justify-content:center;gap:20px;margin:8px 0;color:#7c7f99;font-size:12px}.carousel-navigation button{display:grid;place-items:center;border:1px solid #dedaff;border-radius:6px;padding:5px;color:#5b55ff;background:white}.carousel-navigation svg{width:16px;height:16px}.carousel-navigation button:disabled{opacity:.35;cursor:not-allowed}.carousel-library-preview audio{display:block;width:100%;height:40px;margin-top:10px}.carousel-description{margin:12px 0 0;color:#555978;font-size:13px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}.preview-error{color:#dc5966;font-size:12px;line-height:1.7}.preview-error button{color:#5b55ff}.preview-status{color:#878aa5;font-size:13px}.carousel-gallery:focus-visible,.carousel-navigation button:focus-visible{outline:2px solid #8584ff;outline-offset:2px}
</style>
