<template>
  <div class="preview feed-preview">
    <p class="preview__title">投放效果预览</p>
    <div class="plats" role="tablist" aria-label="预览平台">
      <button v-for="p in platforms" :key="p.id" class="plat" type="button" role="tab" :aria-selected="platform === p.id" :aria-label="p.name" :title="`${p.name}效果示意`" @click="platform = p.id">
        <span class="plat__i" :class="`plat--${p.id}`"><span v-if="p.id === 'toutiao'" class="plat__txt">头条</span><ReferenceIcon v-else :name="`plat-${p.id}`" /></span>
      </button>
    </div>
    <div class="feed-phone" :class="{ 'feed-phone--landscape': landscape && material?.type === 'VIDEO', 'feed-phone--gallery': material?.type !== 'VIDEO', 'feed-phone--carousel': material?.type === 'CAROUSEL', 'feed-phone--landscape-carousel': imageLandscape && material?.type === 'CAROUSEL', 'feed-phone--product-card': material?.type === 'IMAGE', 'feed-phone--title': !!title }">
      <div class="feed-screen">
        <div class="feed-status" aria-hidden="true"><span>9:41</span><span class="feed-status-icons"><FeedPreviewIcon name="signal" /><FeedPreviewIcon name="wifi" /><span class="feed-battery" /></span></div>
        <div class="feed-top" aria-hidden="true"><FeedPreviewIcon name="plus-circle" /><span>商城</span><span>关注</span><b>推荐</b><FeedPreviewIcon name="search" /></div>
        <div class="feed-stage" @touchstart="touchStart" @touchend="touchEnd">
          <div class="feed-visual">
            <video v-if="material?.type === 'VIDEO' && playbackUrl" ref="videoElement" :key="videoSource" :src="playbackUrl" :poster="material.posterUrl" :muted="muted" playsinline preload="metadata" aria-label="投放视频" @click="toggleVideo" @loadedmetadata="videoMetadata" @timeupdate="updateTime" @play="playing = true" @pause="playing = false" @ended="playing = false" @error="videoError" />
            <img v-else-if="imageUrl" :key="imageUrl" :src="imageUrl" :alt="material?.type === 'IMAGE' ? '商品卡图片预览' : `图文第 ${imageIndex + 1} 张图片`" @load="imageMetadata" @error="error = '图片暂时无法加载，请刷新后重试'" />
            <p v-else class="preview-empty"><b v-if="!material && product" class="preview-empty__product">当前商品：{{ product.name }}</b>{{ material ? material.status === 'PROCESSING' ? '素材正在登记，完成后自动显示预览' : '该素材尚无可预览的文件，请重新检查素材' : product ? '当前商品在此分类下暂无素材。可切换商品或素材类型查看已有结果，也可通过“新增素材”补齐。' : '选择素材或标题查看预览' }}</p>
            <button v-if="material?.type === 'VIDEO' && material.previewUrl && !playing" type="button" class="feed-play" :disabled="playPending" aria-label="播放视频" @click="toggleVideo"><FeedPreviewIcon name="play" solid /><span v-if="playPending">加载中…</span></button>
            <button v-if="landscape && material?.type === 'VIDEO' && material.previewUrl" class="feed-fullscreen" type="button" @click="fullscreen"><FeedPreviewIcon name="fullscreen" />全屏观看</button>
            <span v-if="material && material.type !== 'VIDEO'" class="feed-format">{{ material.type === 'CAROUSEL' ? `${imageIndex + 1} / ${material.imageUrls?.length || 1}` : '商品卡' }}</span>
            <template v-if="(material?.imageUrls?.length || 0) > 1">
              <button class="feed-image-arrow feed-image-arrow--prev" type="button" aria-label="上一张图片" :disabled="imageIndex === 0" @click="imageIndex--">‹</button>
              <button class="feed-image-arrow feed-image-arrow--next" type="button" aria-label="下一张图片" :disabled="imageIndex >= material!.imageUrls!.length - 1" @click="imageIndex++">›</button>
            </template>
          </div>
          <div v-if="material" class="feed-actions">
            <span class="feed-avatar"><img v-if="account?.avatar && !avatarBroken" :src="account.avatar" alt="抖音号头像" @error="avatarBroken = true" /><span v-else>{{ accountName.slice(0, 1) }}</span><i aria-hidden="true">+</i></span>
            <button type="button" :class="{ 'is-liked': liked }" :aria-pressed="liked" aria-label="点赞预览" @click="liked = !liked"><FeedPreviewIcon name="heart" solid /><span>点赞</span></button>
            <button type="button" aria-label="评论预览" @click="panel = 'comments'"><FeedPreviewIcon name="comment-fill" /><span>评论</span></button>
            <button type="button" :class="{ 'is-saved': saved }" :aria-pressed="saved" aria-label="收藏预览" @click="saved = !saved"><FeedPreviewIcon name="save" solid /><span>收藏</span></button>
            <button type="button" aria-label="分享预览" @click="panel = 'share'"><FeedPreviewIcon name="share" solid /><span>分享</span></button>
            <span class="feed-record" :class="{ 'is-playing': playing }" aria-hidden="true"><FeedPreviewIcon name="music" /></span>
          </div>
          <div v-if="material && material.type !== 'CAROUSEL'" class="feed-caption">
            <p class="feed-author"><b>@ {{ accountName }}</b><span>广告</span><button v-if="landscape && material.type === 'VIDEO' && product && !title" class="feed-product-entry" type="button" aria-label="查看商品预览" @click="panel = 'product'"><FeedPreviewIcon name="bag" /></button></p>
            <button class="feed-copy" :class="{ expanded: captionExpanded }" type="button" :aria-expanded="captionExpanded" aria-label="展开或收起广告文案" @click="captionExpanded = !captionExpanded"><span>{{ displayTitle }}</span></button>
            <div v-if="product" class="feed-product">
              <button class="feed-product-info" type="button" aria-label="查看商品预览" @click="panel = 'product'"><img v-if="productImage && !productImageBroken" :src="productImage" alt="商品缩略图" @error="productImageBroken = true" /><FeedPreviewIcon v-else name="bag" /><span><b>{{ product.name }}</b><em v-if="product.price != null">¥{{ product.price }}</em><em v-else>查看商品</em></span></button>
              <button class="feed-buy" type="button" aria-label="立即购买效果预览" @click="panel = 'product'">立即购买</button>
            </div>
          </div>
          <audio v-if="material?.type === 'CAROUSEL' && playbackUrl" ref="audioElement" :key="videoSource" :src="playbackUrl" :muted="muted" preload="metadata" @loadedmetadata="audioMetadata" @timeupdate="updateTime" @play="playing = true" @pause="playing = false" @ended="playing = false" @error="error = '音频暂时无法播放，请刷新后重试'" />
        </div>
        <div class="feed-bottom" :aria-hidden="material?.type !== 'CAROUSEL' || (material.imageUrls?.length || 0) <= 1"><div v-if="material?.type === 'CAROUSEL' && (material.imageUrls?.length || 0) > 1" class="feed-dots" aria-label="图文分页"><button v-for="(_, i) in material.imageUrls" :key="i" type="button" :class="{ active: i === imageIndex }" :aria-label="`查看第 ${i + 1} 张图片`" :aria-current="i === imageIndex ? 'true' : undefined" @click="imageIndex = i" /></div><b>首页</b><span>朋友</span><span class="feed-create">+</span><span>消息</span><span>我</span></div>
        <div v-if="panel" class="feed-sheet-mask" @click.self="panel = null">
          <section class="feed-sheet" :aria-label="panel === 'product' ? '商品详情预览' : panel === 'comments' ? '评论预览' : '分享预览'"><div class="feed-sheet-header"><b>{{ panel === 'product' ? '商品详情' : panel === 'comments' ? '评论' : '分享' }}</b><button type="button" aria-label="关闭预览浮层" @click="panel = null"><FeedPreviewIcon name="close" /></button></div>
            <template v-if="panel === 'product' && product"><img v-if="productImage && !productImageBroken" :src="productImage" alt="商品详情图片" @error="productImageBroken = true" /><p>{{ product.name }}</p><strong v-if="product.price != null">¥{{ product.price }}</strong></template>
            <p v-else>{{ panel === 'comments' ? '暂无评论' : '分享入口展示示意' }}</p><small>当前为投放效果预览</small>
          </section>
        </div>
      </div>
    </div>
    <p v-if="material?.type === 'CAROUSEL' && title" class="feed-title-preview">标题预览：{{ title }}</p>
    <div v-if="hasMedia" class="feed-player-controls">
      <div class="feed-player-row"><button type="button" :disabled="playPending" :aria-label="playing ? '暂停播放' : material?.type === 'VIDEO' ? '播放视频' : '播放图文配音'" @click="toggleVideo"><ReferenceIcon :name="playing ? 'pause' : 'play'" /></button><span>{{ material?.type === 'CAROUSEL' ? '图文配音 · ' : '' }}{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span><button type="button" :aria-label="muted ? '开启声音' : '静音'" :aria-pressed="muted" @click="muted = !muted"><FeedPreviewIcon :name="muted ? 'muted' : 'volume'" /></button></div>
      <input type="range" :min="0" :max="duration || 1" :value="currentTime" :disabled="!duration" step="0.1" aria-label="播放进度" @input="seek" />
    </div>
    <p v-if="error" class="preview-error" role="alert">{{ error }}</p>
    <p class="preview__note">仅供效果预览，互动与购买为展示示意，最终效果以平台为准。</p>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import FeedPreviewIcon from './FeedPreviewIcon.vue';
import type { CreationMaterial, CreationProduct } from '@/features/delivery/creationApi';
const props = defineProps<{ material?: CreationMaterial; product?: CreationProduct; title?: string; account?: { awemeName: string; avatar?: string } }>();
const emit = defineEmits<{ metadata: [id: string, data: { width: number; height: number; duration: number }] }>();
const platforms = [{ id: 'douyin', name: '抖音' }, { id: 'toutiao', name: '今日头条' }, { id: 'xigua', name: '西瓜视频' }, { id: 'huoshan', name: '抖音火山版' }, { id: 'jisu', name: '抖音极速版' }];
const platform = ref('douyin'), imageIndex = ref(0), error = ref('');
const videoElement = ref<HTMLVideoElement | null>(null), audioElement = ref<HTMLAudioElement | null>(null), playing = ref(false), playPending = ref(false);
const muted = ref(false), currentTime = ref(0), duration = ref(0), landscape = ref(false), imageLandscape = ref(false);
const liked = ref(false), saved = ref(false), avatarBroken = ref(false), productImageBroken = ref(false), captionExpanded = ref(false);
const panel = ref<'product' | 'comments' | 'share' | null>(null);
const imageUrl = computed(() => props.material?.imageUrls?.[imageIndex.value] || props.material?.posterUrl);
const productImage = computed(() => props.product?.imageUrl || props.material?.posterUrl || props.material?.imageUrls?.[0]);
const accountName = computed(() => props.account?.awemeName || '商品推广');
const activeMedia = computed(() => props.material?.type === 'VIDEO' ? videoElement.value : props.material?.type === 'CAROUSEL' ? audioElement.value : null);
const hasMedia = computed(() => !!props.material?.previewUrl && (props.material.type === 'VIDEO' || props.material.type === 'CAROUSEL'));
// Renewing an OSS signature does not mean the underlying material changed.
const objectPath = (url?: string) => url?.split('?')[0] || '';
const videoSource = computed(() => `${props.material?.id || ''}:${objectPath(props.material?.previewUrl)}:${objectPath(props.material?.posterUrl)}`);
// A stable key alone does not stop the browser reloading when src changes.
// Keep the loaded URL until the selected material or its underlying file changes.
const playbackUrl = ref('');
const displayTitle = computed(() => props.title || props.material?.titles[0] || props.material?.title || props.product?.name || '');
watch(videoSource, () => {
  videoElement.value?.pause(); audioElement.value?.pause(); imageIndex.value = 0; error.value = ''; playing.value = false; playPending.value = false;
  playbackUrl.value = props.material?.previewUrl || '';
  currentTime.value = 0; duration.value = 0; landscape.value = false; captionExpanded.value = false; panel.value = null; liked.value = false; saved.value = false;
}, { immediate: true });
watch(imageUrl, () => { error.value = ''; imageLandscape.value = false; });
watch(productImage, () => { productImageBroken.value = false; });
watch(() => props.account?.avatar, () => { avatarBroken.value = false; });
watch(() => props.material?.imageUrls?.length, length => { if (imageIndex.value >= (length || 0)) imageIndex.value = 0; });
onBeforeUnmount(() => { videoElement.value?.pause(); audioElement.value?.pause(); });
function videoMetadata(event: Event) {
  const video = event.target as HTMLVideoElement;
  landscape.value = video.videoWidth > video.videoHeight;
  duration.value = Number.isFinite(video.duration) ? video.duration : 0;
  if (props.material && duration.value) emit('metadata', props.material.id, { width: video.videoWidth, height: video.videoHeight, duration: video.duration });
}
function audioMetadata(event: Event) { const media = event.target as HTMLAudioElement; duration.value = Number.isFinite(media.duration) ? media.duration : 0; }
function imageMetadata(event: Event) { const image = event.target as HTMLImageElement; imageLandscape.value = image.naturalWidth > image.naturalHeight; }
function updateTime(event: Event) { currentTime.value = (event.target as HTMLMediaElement).currentTime; }
function formatTime(value: number) { const seconds = Math.floor(value || 0); return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`; }
function seek(event: Event) { if (activeMedia.value && duration.value) activeMedia.value.currentTime = Number((event.target as HTMLInputElement).value); }
async function toggleVideo() {
  const media = activeMedia.value;
  if (!media || playPending.value) return;
  if (!media.paused) { media.pause(); return; }
  error.value = ''; playPending.value = true;
  try { await media.play(); }
  catch (cause) { if (media === activeMedia.value && !(cause instanceof DOMException && cause.name === 'AbortError')) error.value = '素材暂时无法播放，请刷新后重试'; }
  finally { if (media === activeMedia.value) playPending.value = false; }
}
function videoError() { playing.value = false; playPending.value = false; error.value = '视频暂时无法播放，请刷新后重试'; }
async function fullscreen() {
  try { await videoElement.value?.requestFullscreen(); }
  catch { error.value = '浏览器暂不支持全屏，可在预览中直接播放'; }
}
let touchX: number | null = null;
function touchStart(event: TouchEvent) {
  const target = event.target as HTMLElement;
  touchX = target.closest('.feed-visual') && !target.closest('button') ? event.changedTouches[0]?.clientX ?? null : null;
}
function touchEnd(event: TouchEvent) {
  if (touchX == null) return;
  const delta = (event.changedTouches[0]?.clientX || 0) - touchX;
  touchX = null;
  if (props.material?.type === 'CAROUSEL' && Math.abs(delta) > 40) imageIndex.value = Math.max(0, Math.min((props.material.imageUrls?.length || 1) - 1, imageIndex.value + (delta < 0 ? 1 : -1)));
}
</script>
<style scoped src="/src/pages/campaigns/create/feed-preview.css" />
