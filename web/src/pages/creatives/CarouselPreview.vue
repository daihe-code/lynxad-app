<template>
  <div class="carousel-detail">
    <p v-if="error" class="error" role="alert">{{ error }} <button type="button" @click="load">重新加载</button></p>
    <p v-else-if="!detail">正在读取图文素材…</p>
    <template v-if="detail">
      <AssetReviewStatus :review="detail.aiReview" composite />
      <p class="note">文本、全部图片和音频共同质检。任意一项不合格，整条图文不合格。</p>
      <section v-for="part in detail.parts" :key="`${part.role}:${part.position}`">
        <h3>{{ part.role === 'TEXT' ? '文本' : part.role === 'AUDIO' ? '音频' : `图片 ${part.position + 1}` }}<small>{{ part.asset.filename }}</small></h3>
        <pre v-if="part.role === 'TEXT'">{{ part.asset.textContent }}</pre>
        <template v-else-if="part.role === 'IMAGE'"><img v-if="imagePreview(part.asset).posterUrl" :src="assetUrl(imagePreview(part.asset).posterUrl)" :alt="part.asset.filename" loading="lazy" @error="imageFailed(part.asset)" /><p v-if="mediaErrors[part.asset.id] || imagePreview(part.asset).source === 'UNAVAILABLE'" class="note" role="status">{{ imagePreview(part.asset).message || '图片暂不可预览，请检查存储服务后重新加载。' }}</p></template>
        <template v-else><audio :src="assetUrl(part.asset.url)" controls preload="metadata" @error="mediaErrors[part.asset.id] = true"></audio><p v-if="mediaErrors[part.asset.id]" class="note" role="status">音频暂不可读取，请恢复存储服务后重新加载。</p></template>
        <AssetReviewStatus :review="part.asset.aiReview" :retrying="retrying === part.asset.id" @retry="retry(part.asset.id)" />
        <p v-for="sync in part.asset.syncs.filter(s => detail!.accountIds.includes(s.accountId))" :key="sync.id" class="note">{{ sync.advertiserId || sync.accountId }}：{{ syncLabel(sync.status, sync.reviewStatus) }}<span v-if="sync.errorMessage"> · {{ sync.errorMessage }}</span></p>
      </section>
      <p class="note">{{ detail.accountIds.length ? '整条图文质检合格后，才会同步图片和音频组件到所选千川账户。' : '仅保存在本平台，未选择千川同步账户。' }}</p>
    </template>
  </div>
</template>
<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { getCarousel, retryAssetReview, type CarouselDetail, type UploadedAsset } from '@/features/creative/creativeApi';
import { assetPreview, resolveAssetPreview, type PreviewItem } from '@/features/creative/assetPreviews';
import { carouselPending } from '@/features/creative/carouselUploads';
import { syncLabel } from '@/features/creative/assetUploads';
import AssetReviewStatus from './AssetReviewStatus.vue';
import { assetUrl } from './assetFormat';
const props = defineProps<{ id: string }>();
const emit = defineEmits<{ changed: [] }>();
const detail = ref<CarouselDetail>(), error = ref(''), retrying = ref('');
const mediaErrors = ref<Record<string, boolean>>({});
function imageItem(asset: UploadedAsset): PreviewItem { return { id: asset.id, assetId: asset.id, type: 'IMAGE', posterUrl: asset.url, playUrl: asset.url }; }
function imagePreview(asset: UploadedAsset) { return assetPreview(imageItem(asset)); }
async function imageFailed(asset: UploadedAsset) {
  if (imagePreview(asset).source === 'ORIGINAL' && await resolveAssetPreview(imageItem(asset))) return;
  mediaErrors.value[asset.id] = true;
}
let timer: ReturnType<typeof setInterval> | undefined, loading = false;
async function load() {
  const id = props.id; if (loading) return; loading = true;
  try { const result = await getCarousel(id); if (props.id === id) { detail.value = result; error.value = ''; } }
  catch { error.value = '图文详情暂时无法加载'; } finally { loading = false; }
}
async function retry(id: string) { retrying.value = id; try { await retryAssetReview(id); await load(); emit('changed'); } finally { retrying.value = ''; } }
watch(() => props.id, () => { detail.value = undefined; void load(); });
onMounted(() => { void load(); timer = setInterval(() => { if (detail.value && carouselPending(detail.value)) void load(); }, 4000); });
onUnmounted(() => clearInterval(timer));
</script>
<style scoped>
.carousel-detail { color:#50536f; font-size:13px; }.carousel-detail section { border:1px solid #e4e5f2; border-radius:12px; padding:16px; margin:12px 0; }.carousel-detail h3 { color:#1c1d32; font-size:15px; font-weight:700; margin-bottom:12px; }.carousel-detail h3 small { display:block; color:#7c7f99; font-size:12px; font-weight:400; margin-top:5px; overflow-wrap:anywhere; }.carousel-detail pre { white-space:pre-wrap; overflow-wrap:anywhere; font:inherit; line-height:1.8; }.carousel-detail img { display:block; max-width:100%; max-height:300px; object-fit:contain; border-radius:8px; }.carousel-detail audio { width:100%; }.note { color:#7c7f99; line-height:1.7; margin:10px 0; }.error { color:#e5484d; }.error button { color:#5b55ff; }
</style>
