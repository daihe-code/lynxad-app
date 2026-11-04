<template>
  <section class="carousel-preview" aria-label="图文内容预览">
    <p v-if="loading" class="preview-message" role="status">正在加载图文…</p>
    <p v-else-if="loadError" class="preview-error" role="alert">{{ loadError }} <button type="button" @click="load">重试</button></p>
    <template v-else-if="content">
      <div ref="stage" class="carousel-stage" tabindex="0" aria-label="图文图片" @keydown.left.prevent="move(-1)" @keydown.right.prevent="move(1)">
        <button v-if="images.length > 1" class="carousel-arrow previous" type="button" aria-label="上一张图片" :disabled="index === 0" @click="move(-1)"><AssetIcon name="chevron-right" /></button>
        <div class="carousel-picture" :style="{ '--ratio': geometry.ratioValue || 1 }">
          <img v-if="imageUrl && !imageError" :key="currentImage?.id" :src="assetUrl(imageUrl)" :alt="`第 ${index + 1} 张图文图片`" @load="imageLoaded" @error="imageFailed" />
          <p v-else class="preview-message" role="status">图片暂不可预览。<button type="button" @click="load">重试</button></p>
          <button v-if="content.audioUrl" class="carousel-speaker" type="button" :aria-label="playing ? '暂停图文音频' : '播放图文音频'" :aria-pressed="playing" :disabled="playPending" @click="toggleAudio"><AssetIcon name="volume" /><AssetIcon class="speaker-state" :name="playing ? 'pause' : 'play'" /></button>
        </div>
        <button v-if="images.length > 1" class="carousel-arrow next" type="button" aria-label="下一张图片" :disabled="index === images.length - 1" @click="move(1)"><AssetIcon name="chevron-right" /></button>
      </div>
      <p v-if="images.length" class="carousel-page" aria-live="polite">{{ index + 1 }} / {{ images.length }}</p>
      <audio v-if="content.audioUrl" ref="audio" :src="assetUrl(content.audioUrl)" preload="none" @play="playing = true" @pause="playing = false" @ended="playing = false" @error="audioFailed" />
      <p v-if="audioError" class="preview-error" role="alert">{{ audioError }} <button type="button" @click="load">重试</button></p>
      <div class="carousel-description"><h3>图文描述</h3><p>{{ content.description || '—' }}</p></div>
      <div class="ac-pinfo carousel-info"><span>图片 <b>{{ images.length }}张</b></span><span>规格 <b>{{ dimensions }}<template v-if="geometry.ratioLabel"> · {{ geometry.ratioLabel }}</template></b></span><span>大小 <b>{{ fileSize(item.sizeBytes) }}</b></span><span>引用计划 <b>{{ referenceLabel(item) }}</b></span><span v-if="item.usageKnown !== false">状态 <b>{{ item.used ? '已使用' : '未使用' }}</b></span></div>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { getCarousel, getLibraryCarouselPreview, type CarouselMedia, type LibraryItemVo } from '@/features/creative/creativeApi';
import { assetPreview, clearAssetPreviews, resolveAssetPreview, type PreviewItem } from '@/features/creative/assetPreviews';
import { assetGeometry } from '@/features/creative/assetGeometry';
import { referenceLabel } from '@/features/creative/assetReferences';
import { assetUrl, fileSize } from './assetFormat';
import AssetIcon from './AssetIcon.vue';
const props = defineProps<{ item: LibraryItemVo }>();
const content = ref<CarouselMedia>(), loading = ref(false), loadError = ref(''), index = ref(0);
const audio = ref<HTMLAudioElement>(), playing = ref(false), playPending = ref(false), audioError = ref('');
const stage = ref<HTMLElement>();
const imageErrors = ref(new Set<string>()), observed = ref<Record<string, { width: number; height: number }>>({});
const images = computed(() => content.value?.images || []), currentImage = computed(() => images.value[index.value]);
type Image = CarouselMedia['images'][number];
function previewItem(image: Image): PreviewItem { return { id: image.id, assetId: image.assetId, type: 'IMAGE', posterUrl: image.url, playUrl: image.url }; }
const imageUrl = computed(() => currentImage.value ? assetPreview(previewItem(currentImage.value)).posterUrl || assetPreview(previewItem(currentImage.value)).playUrl : '');
const imageError = computed(() => !!currentImage.value && imageErrors.value.has(currentImage.value.id));
const geometry = computed(() => assetGeometry({ type: 'IMAGE', width: currentImage.value?.width, height: currentImage.value?.height }, observed.value[currentImage.value?.id || '']));
const dimensions = computed(() => geometry.value.width && geometry.value.height ? `${geometry.value.width}×${geometry.value.height}` : '—');
let request = 0, alive = true, controller: AbortController | undefined;
function stopAudio() { const media = audio.value; if (media) { media.pause(); media.removeAttribute('src'); media.load(); } playing.value = false; playPending.value = false; }
async function load() {
  const version = ++request, item = props.item;
  controller?.abort(); controller = new AbortController(); stopAudio();
  clearAssetPreviews(images.value.map(previewItem)); content.value = undefined;
  index.value = 0; imageErrors.value.clear(); observed.value = {}; audioError.value = ''; loadError.value = ''; loading.value = true;
  try {
    let result: CarouselMedia;
    if (item.carouselId) {
      const detail = await getCarousel(item.carouselId, controller.signal);
      result = {
        images: detail.parts.filter(part => part.role === 'IMAGE').sort((a, b) => a.position - b.position).map(part => ({ id: part.asset.id, assetId: part.asset.id, url: part.asset.url, width: part.asset.width, height: part.asset.height })),
        audioUrl: detail.parts.find(part => part.role === 'AUDIO')?.asset.url || '',
        description: detail.parts.filter(part => part.role === 'TEXT').sort((a, b) => a.position - b.position).map(part => part.asset.textContent?.trim()).filter(Boolean).join('\n'),
      };
    } else result = await getLibraryCarouselPreview(item.id, controller.signal);
    if (alive && version === request) content.value = result;
  } catch { if (alive && version === request) loadError.value = '暂时无法加载图文，请重试。'; }
  finally {
    if (alive && version === request) {
      loading.value = false; await nextTick();
      if (alive && version === request && document.activeElement === document.body) stage.value?.focus({ preventScroll: true });
    }
  }
}
function move(offset: number) { index.value = Math.max(0, Math.min(index.value + offset, images.value.length - 1)); }
function imageLoaded(event: Event) { const image = event.target as HTMLImageElement, id = currentImage.value?.id; if (id && image.naturalWidth && image.naturalHeight) observed.value[id] = { width: image.naturalWidth, height: image.naturalHeight }; }
async function imageFailed() {
  const image = currentImage.value, version = request;
  if (!image) return;
  if (image.assetId && assetPreview(previewItem(image)).source === 'ORIGINAL' && await resolveAssetPreview(previewItem(image))) return;
  if (alive && version === request) imageErrors.value.add(image.id);
}
function audioFailed() { playing.value = false; audioError.value = '音频暂不可播放，请重试。'; }
async function toggleAudio() {
  const media = audio.value, version = request;
  if (!media || playPending.value) return;
  audioError.value = '';
  if (!media.paused) { media.pause(); return; }
  playPending.value = true;
  try { await media.play(); } catch { if (alive && version === request) audioFailed(); }
  finally { if (alive && version === request) playPending.value = false; }
}
watch(() => props.item.id, load, { immediate: true });
onBeforeUnmount(() => { alive = false; ++request; controller?.abort(); stopAudio(); });
</script>
<style scoped>
.carousel-preview{color:#26263f;font-size:13px;margin-top:16px;min-width:0;min-height:0;overflow-y:auto}
.carousel-stage{position:relative;display:flex;justify-content:center;align-items:center;padding:0 48px;min-height:120px}
.carousel-picture{position:relative;flex:none;--picture-height:min(52dvh,calc(100dvh - 380px));width:min(100%,calc(var(--picture-height) * var(--ratio)));aspect-ratio:var(--ratio);border-radius:12px;background:#f7f7fa;overflow:hidden}
.carousel-picture>img{position:absolute;inset:0;width:100%;height:100%;object-fit:contain}
.carousel-arrow{position:absolute;top:50%;transform:translateY(-50%);display:grid;place-items:center;width:36px;height:36px;border:0;border-radius:50%;background:#efedff;color:#6654ff;cursor:pointer}
.carousel-arrow.previous{left:0}.carousel-arrow.next{right:0}.carousel-arrow.previous svg{transform:rotate(180deg)}
.carousel-arrow svg{width:20px;height:20px}.carousel-arrow:disabled{opacity:.3;cursor:default}
.carousel-speaker{position:absolute;top:10px;right:10px;display:grid;place-items:center;width:36px;height:36px;border-radius:50%;border:0;background:#f8f6ffed;color:#6153bd;cursor:pointer}
.carousel-speaker>svg{width:23px;height:23px}.carousel-speaker .speaker-state{position:absolute;width:13px;height:13px;right:-2px;bottom:-1px;border-radius:50%;padding:1px;background:#6354ff;color:white}
.carousel-speaker:disabled{opacity:.5}.carousel-page{text-align:center;margin:10px 0 0;font-size:13px;font-weight:600;font-variant-numeric:tabular-nums}
.carousel-preview audio{display:none}.carousel-description{margin-top:14px}.carousel-description h3{font-size:14px;font-weight:600;margin:0 0 5px}
.carousel-description p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7;margin:0;max-height:130px;overflow-y:auto}
.carousel-info{padding-top:14px;border-top:1px solid #efedf5;line-height:1.5}
.preview-message{display:flex;align-items:center;justify-content:center;min-height:120px;color:#8a87a0;font-size:13px;line-height:1.7;padding:12px;text-align:center}
.preview-message button,.preview-error button{color:#5b55ff;background:none;border:0;margin-left:8px;cursor:pointer}
.preview-error{color:#df5c65;font-size:13px;line-height:1.7;margin:12px 0}
.carousel-stage:focus-visible,.carousel-arrow:focus-visible,.carousel-speaker:focus-visible{outline:2px solid #8c80ff;outline-offset:3px}
@media(max-width:600px){.carousel-stage{padding:0 32px}.carousel-arrow{width:28px;height:28px}.carousel-arrow svg{width:17px;height:17px}.carousel-speaker{top:7px;right:7px;width:31px;height:31px}.carousel-description p{max-height:110px}}
</style>
