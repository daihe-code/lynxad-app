<template>
  <section class="carousel-creation-progress" aria-label="图文创建进度" aria-live="polite">
    <div class="creation-heading"><span class="status-icon" :class="added ? 'PASS' : failed ? 'FAIL' : 'WAITING'" aria-hidden="true">{{ added ? '✓' : failed ? '!' : '' }}</span><h3>{{ added ? '1 套图文已添加' : failed ? '图文创建未完成' : '正在创建图文' }}</h3><p>{{ added ? '系统检测、打标与同步已完成。' : '系统检测、打标并同步，完成后自动加入当前商品。' }}</p></div>
    <div class="creation-summary"><img :src="coverUrl" alt="图文封面" /><div><strong>图文名称：{{ name }}</strong><p>{{ progress.imageCount }}/{{ assetFileRules.carouselMaxImages }}张图片 · 1段音频 · {{ hasDescription ? '已填写作品描述' : '未填写作品描述' }}</p></div></div>
    <dl class="creation-checks"><div v-for="row in progress.rows" :key="row.key" :class="[row.status, { 'whole-carousel': row.key === 'sync' }]" :data-check="row.key"><dt>{{ row.label }}</dt><dd><span class="status-icon" :class="row.status" aria-hidden="true">{{ row.status === 'PASS' ? '✓' : row.status === 'FAIL' ? '!' : row.status === 'SKIP' ? '—' : '' }}</span>{{ row.text }}</dd></div></dl>
    <slot />
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { assetFileRules } from '@/features/creative/assetUploadTypes';
import { carouselCreationProgress } from '@/features/creative/carouselCreationProgress';
import type { CarouselDetail } from '@/features/creative/creativeApi';
const props = defineProps<{ name: string; coverUrl: string; hasDescription: boolean; imageCount: number; result?: CarouselDetail; accountId: string; added: boolean; attachError?: string; createError: string }>();
const progress = computed(() => {
  const value = carouselCreationProgress(props.result, props.accountId, props.hasDescription, props.imageCount, props.added, props.attachError);
  if (props.createError && !props.result) value.rows[value.rows.length - 1] = { key: 'sync', label: '整套图文创建未完成', status: 'FAIL', text: '' };
  return value;
});
const failed = computed(() => !!props.createError || !!props.attachError || progress.value.blocked);
</script>
<style scoped>
.carousel-creation-progress{background:linear-gradient(125deg,#f4f2ff,#efedff);border-radius:10px;margin-top:20px;padding:28px 60px;color:#29254f}.creation-heading{text-align:center;margin:0 0 24px}.creation-heading>.status-icon{width:34px;height:34px;font-size:23px;margin:0 auto 13px}.creation-heading h3{font-size:26px;line-height:1.4;margin:0 0 10px;font-weight:700;color:#181632}.creation-heading p,.creation-summary p{font-size:14px;color:#7f77ae;line-height:1.6;margin:0}.creation-summary{display:flex;align-items:center;gap:20px;background:#ffffffed;border:1px solid #e2ddfa;border-radius:9px;padding:12px 18px;margin-bottom:14px}.creation-summary img{width:64px;height:64px;border:1px solid #e2ddfa;border-radius:7px;object-fit:contain;background:#fff;flex-shrink:0}.creation-summary>div{min-width:0}.creation-summary strong{display:block;font-size:16px;line-height:1.6;overflow-wrap:anywhere;margin-bottom:3px}.creation-checks{display:grid;gap:7px;margin:0}.creation-checks>div{display:grid;grid-template-columns:minmax(160px,28%) minmax(0,1fr);align-items:center;gap:16px;min-height:44px;padding:9px 18px;background:#ffffffed;border:1px solid #e2ddfa;border-radius:8px;font-size:14px}.creation-checks dt{font-weight:600;overflow-wrap:anywhere}.creation-checks dd{display:flex;align-items:center;gap:12px;margin:0;color:#827bad;line-height:1.5}.status-icon{box-sizing:border-box;width:21px;height:21px;flex-shrink:0;display:grid;place-items:center;border-radius:50%;font-size:15px;font-weight:600}.status-icon.PASS{color:white;background:#00b981}.status-icon.FAIL{color:white;background:#e35b6b}.status-icon.SKIP{color:#9b95b5;background:#f1eff7}.status-icon.WAITING{border:4px solid #dbd5ff;border-left-color:#5b3cff;border-bottom-color:#5b3cff;animation:creation-spin 1s linear infinite}.creation-checks>.whole-carousel{min-height:52px;background:#e8e4ff}.creation-checks>.whole-carousel.PASS{background:#dbf7e9;border-color:#ccf0dd}.creation-checks>.whole-carousel.FAIL{background:#fff4f5;border-color:#f2d3d8}.creation-checks>.FAIL dd{color:#d65565}@keyframes creation-spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.status-icon.WAITING{animation:none}}@media(max-width:639px){.carousel-creation-progress{padding:22px 12px}.creation-heading h3{font-size:21px}.creation-heading p,.creation-summary p{font-size:12px}.creation-summary{gap:10px;padding:10px}.creation-summary img{width:48px;height:48px}.creation-summary strong{font-size:14px}.creation-checks>div{grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:10px;font-size:12px;padding:9px 10px}.creation-checks dd{gap:7px}.creation-checks>.whole-carousel{grid-template-columns:minmax(0,1fr) 24px}}
</style>
