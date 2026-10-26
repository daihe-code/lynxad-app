<template>
  <article class="material-summary" :class="{ 'material-summary--preview': preview }">
    <p v-if="label" class="material-summary__label">{{ label }}</p>
    <video v-if="preview && material.type === 'VIDEO' && material.previewUrl" :src="material.previewUrl" :poster="poster" controls playsinline preload="metadata" :aria-label="`${label || ''}视频预览`" />
    <img v-else-if="poster && !broken" :src="poster" alt="" @error="broken = true" />
    <span v-else class="material-summary__placeholder" aria-hidden="true">▧</span>
    <div class="material-summary__copy"><strong :title="material.displayName || material.title">{{ material.displayName || material.title || '未命名素材' }}</strong><p>{{ metadata }}</p></div>
    <span v-if="!preview" class="material-summary__source">{{ material.engineMaterialId ? '智能生成' : '手动添加' }}</span>
  </article>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import type { CreationMaterial } from '@/features/delivery/creationApi';
const props = defineProps<{ material: CreationMaterial; label?: string; preview?: boolean }>();
const broken = ref(false);
const poster = computed(() => props.material.posterUrl || props.material.imageUrls?.[0]);
const metadata = computed(() => {
  const m = props.material, parts = [m.type === 'VIDEO' ? '视频' : m.type === 'IMAGE' ? '图片' : '图文'];
  if (m.type === 'VIDEO' && m.durationSec && m.durationSec > 0) parts.push(`${Math.round(m.durationSec)}秒`);
  if (m.width && m.height) parts.push(m.width === m.height ? '方屏' : m.width > m.height ? '横屏' : '竖屏');
  return parts.join(' · ');
});
</script>
<style scoped>
.material-summary{display:flex;align-items:center;gap:12px;padding:12px;border-radius:10px;background:#f7f6fc;min-width:0}.material-summary>img,.material-summary__placeholder{width:64px;height:58px;object-fit:contain;border-radius:6px;flex-shrink:0;background:#eeedf6}.material-summary__placeholder{display:grid;place-items:center;color:#9691b9;font-size:24px}.material-summary__copy{min-width:0;flex:1}.material-summary strong{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;font-size:13px;line-height:1.6;color:#242438}.material-summary__copy p{margin:5px 0 0;font-size:12px;line-height:1.5;color:#86849b}.material-summary__source,.material-summary__label{font-size:11px;color:#6355db;background:#ece8ff;border-radius:4px;padding:3px 6px;white-space:nowrap}.material-summary--preview{flex-direction:column;align-items:stretch;gap:8px}.material-summary__label{align-self:flex-start;margin:0}.material-summary--preview>video,.material-summary--preview>img{width:100%;height:110px;object-fit:contain;background:#19191f;border-radius:6px}.material-summary--preview .material-summary__placeholder{width:100%;height:110px}.material-summary--preview strong{font-size:12px}
</style>
