<template>
  <div class="gen" role="status" aria-live="polite">
    <div class="gauge" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100" aria-label="素材生成总进度">
      <svg class="gauge__svg" viewBox="0 0 104 104" aria-hidden="true"><circle class="gauge__track" cx="52" cy="52" r="46" /><circle class="gauge__arc" cx="52" cy="52" r="46" stroke-dasharray="289" :stroke-dashoffset="289 * (1 - percent / 100)" /></svg>
      <span class="gauge__mid"><span class="gauge__pct">{{ percent }}<span class="gauge__unit">%</span></span><span class="gauge__cap">{{ syncing ? '同步中' : '生成中' }}</span></span>
    </div>
    <p class="gen__title">{{ syncing ? '素材已生成，正在登记素材并同步千川' : '正在努力生成素材中，素材越多耗时越长' }}</p>
    <p class="gen__desc">{{ state.generationMessage || '正在提交素材生成任务…' }}</p>
    <p class="gen__desc">任务会在后台继续执行，可前往素材中心查看历史素材。</p>
    <div class="gencards">
      <article v-for="card in cards" :key="card.type" class="gencard" :class="{ 'gencard--done': card.done }">
        <div class="gencard__top"><span class="gencard__icon" :class="`gencard__icon--${card.icon}`" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><template v-if="card.type === 'VIDEO'"><rect x="3" y="5" width="13" height="14" rx="3" /><path d="m16 9 5-3v12l-5-3" /></template><template v-else-if="card.type === 'IMAGE'"><rect x="3" y="3" width="18" height="18" rx="3" /><circle cx="8" cy="8" r="1.5" /><path d="m4 17 5-5 4 4 3-3 5 5" /></template><template v-else><rect x="4" y="3" width="16" height="18" rx="3" /><path d="M8 7h8M8 11h8M8 15h5" /></template></svg></span><span><span class="gencard__name">{{ card.label }}</span><span class="gencard__count"><b>{{ card.completed }}</b><template v-if="card.total !== null">/{{ card.total }}</template> 条已生成</span></span></div>
        <div class="gencard__bar" role="progressbar" :aria-label="`${card.label}生成进度`" :aria-valuenow="card.total === null ? undefined : card.percent" aria-valuemin="0" aria-valuemax="100"><span class="gencard__fill" :class="[`gencard__fill--${card.icon}`, { 'is-indeterminate': card.total === null }]" :style="{ width: card.total === null ? '35%' : `${card.percent}%` }" /></div>
        <p class="gencard__foot"><span v-if="card.done" aria-hidden="true">✓</span><span v-else class="generation-spinner" aria-hidden="true" /><span>{{ card.done ? '生成完成' : card.total === null ? '正在分析商品并生成素材…' : '正在生成素材…' }}</span></p>
      </article>
    </div>
    <p class="gen__hint"><router-link class="gen__link" to="/creatives">前往素材中心</router-link></p>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { CreationState } from '@/features/delivery/creationApi';
const props = defineProps<{ state: CreationState }>();
const percent = computed(() => Math.max(0, Math.min(100, props.state.progress || 0)));
const syncing = computed(() => props.state.generationStatus === 'IMPORTING');
const cards = computed(() => ([{ type: 'VIDEO', label: '短视频', icon: 'video' }, { type: 'IMAGE', label: '图片', icon: 'image' }, { type: 'CAROUSEL', label: '图文', icon: 'article' }] as const).map(card => {
  const count = props.state.generationCounts?.find(c => c.type === card.type);
  const completed = count?.completed ?? 0, total = count?.total ?? null;
  return { ...card, completed, total, done: total !== null && completed >= total,
    percent: total === null ? 0 : total === 0 ? 100 : Math.min(100, Math.round(100 * completed / total)) };
}));
</script>
<style scoped>
.gen { padding: 16px 0 54px; scroll-margin-top: 100px; }
.gauge__svg { transform: rotate(-90deg); }
.gencard { border-style: solid; }
.gencard__fill { display: block; }
.generation-spinner { width: 12px; height: 12px; border: 1.5px solid #dfddff; border-top-color: #575cf6; border-radius: 50%; animation: generation-spin 1s linear infinite; }
.is-indeterminate { animation: generation-slide 1.8s ease-in-out infinite alternate; }
@keyframes generation-spin { to { transform: rotate(360deg); } }
@keyframes generation-slide { to { transform: translateX(185%); } }
@media (prefers-reduced-motion: reduce) { .generation-spinner,.is-indeterminate { animation: none; } }
@media (max-width: 700px) { .gencards { grid-template-columns: 1fr; } }
</style>
