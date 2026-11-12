<template>
  <div class="gen" :class="{'gen--compact':compact}" role="status" aria-live="polite">
    <p class="gen__title">{{ cards.some(card=>card.failed) && !busy ? '部分素材尚未完成' : state.generationResultStatus === 'UNCERTAIN' ? '本次生成提交结果待确认' : state.generationStatus === 'REVISING' ? '正在处理本次重新生成' : syncing ? state.generationResultStatus === 'SUCCEEDED' ? '素材已生成，正在登记素材并同步千川' : '正在登记已有产物并同步千川' : ['QUEUED','SUBMITTING'].includes(state.generationStatus) ? '正在准备素材' : '正在生成投放素材' }}</p>
    <div v-if="state.generationResultStatus !== 'UNCERTAIN'" class="gauge" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100" aria-label="素材生成总进度">
      <svg class="gauge__svg" viewBox="0 0 104 104" aria-hidden="true"><circle class="gauge__track" cx="52" cy="52" r="46" /><circle class="gauge__arc" cx="52" cy="52" r="46" stroke-dasharray="289" :stroke-dashoffset="289 * (1 - percent / 100)" /></svg>
      <span class="gauge__mid"><span class="gauge__pct">{{ percent }}<span class="gauge__unit">%</span></span><span class="gauge__cap">{{ syncing ? '同步中' : '生成中' }}</span></span>
    </div>
    <p class="gen__desc">{{ reconnecting ? '暂时无法更新进度，正在重新连接。' : syncing ? '生成结果已保留，正在完成登记、检查和千川同步。' : ['QUEUED','SUBMITTING'].includes(state.generationStatus) ? '正在读取商品信息并提交生成任务。' : `已完成 ${cards.reduce((sum,card)=>sum+card.completed,0)}${cards.every(card=>card.total !== null) ? '/' + cards.reduce((sum,card)=>sum+(card.total || 0),0) : ''} 条，其余素材正在处理中。` }}</p>
    <p class="gen__desc">{{ state.generationResultStatus === 'UNCERTAIN' ? '本次请求不会自动重发，现有素材仍可使用，请核对引擎任务记录。' : '你可以先离开，生成任务会继续。完成后可从消息通知或本计划查看结果。' }}</p>
    <div v-if="state.generationResultStatus !== 'UNCERTAIN'" class="gencards" :class="{ 'gencards--single': cards.length === 1 }">
      <article v-for="card in cards" :key="card.type" class="gencard" :class="{ 'gencard--done': card.done }">
        <div class="gencard__top"><span class="gencard__icon" :class="`gencard__icon--${card.icon}`" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><template v-if="card.type === 'VIDEO'"><rect x="3" y="5" width="13" height="14" rx="3" /><path d="m16 9 5-3v12l-5-3" /></template><template v-else-if="card.type === 'IMAGE'"><rect x="3" y="3" width="18" height="18" rx="3" /><circle cx="8" cy="8" r="1.5" /><path d="m4 17 5-5 4 4 3-3 5 5" /></template><template v-else><rect x="4" y="3" width="16" height="18" rx="3" /><path d="M8 7h8M8 11h8M8 15h5" /></template></svg></span><span><span class="gencard__name">{{ card.label }}</span><span class="gencard__count"><b>{{ card.completed }}</b><template v-if="card.total !== null">/{{ card.total }}</template> 条已生成</span></span></div>
        <div class="gencard__bar" role="progressbar" :aria-label="`${card.label}生成进度`" :aria-valuenow="card.total === null ? undefined : card.percent" aria-valuemin="0" aria-valuemax="100"><span class="gencard__fill" :class="[`gencard__fill--${card.icon}`, { 'is-indeterminate': card.total === null }]" :style="{ width: card.total === null ? '35%' : `${card.percent}%` }" /></div>
        <p class="gencard__foot"><span v-if="card.done && card.total !== 0" aria-hidden="true">✓</span><span v-else-if="card.total !== 0 && !card.failed" class="generation-spinner" aria-hidden="true" /><span>{{ card.total === 0 ? '本次未请求' : card.done ? '生成完成' : card.failed ? '已有结果已保留' : card.total === null ? '正在分析商品并生成素材…' : '正在生成素材…' }}</span></p>
        <p v-if="card.failed" class="generation-failure">{{ card.failed }} 条{{ card.label }}生成失败，其他素材可继续使用。<button v-if="canRetry !== false" type="button" :disabled="busy" @click="emit('retry', card.type)">重试失败项</button></p>
      </article>
    </div>

  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { CreationState } from '@/features/delivery/creationApi';
import { activeGenerationAddition } from '@/features/delivery/generationSelection';
const props = defineProps<{ state: CreationState; compact?: boolean; reconnecting?: boolean; busy?: boolean; canRetry?: boolean }>();
const emit=defineEmits<{retry:[type:'VIDEO'|'IMAGE'|'CAROUSEL']}>();
const percent = computed(() => Math.max(0, Math.min(100, props.state.progress || 0)));
const syncing = computed(() => props.state.generationStatus === 'IMPORTING');
const addition = computed(() => activeGenerationAddition(props.state));
const cards = computed(() => ([{ type: 'VIDEO', label: '短视频', icon: 'video' }, { type: 'IMAGE', label: '图片', icon: 'image' }, { type: 'CAROUSEL', label: '图文', icon: 'article' }] as const).filter(card => !addition.value || card.type === addition.value.type).map(card => {
  const count = props.state.generationCounts?.find(c => c.type === card.type);
  const completed = count?.completed ?? 0, total = count?.total ?? null;
  return { ...card, completed, total, failed:count?.failed || 0, done: total !== null && completed >= total,
    percent: total === null ? 0 : total === 0 ? 100 : Math.min(100, Math.round(100 * completed / total)) };
}));
</script>
<style scoped>
.gen { padding: 16px 0 54px; scroll-margin-top: 100px; }
.gen .gen__title{margin:0 0 24px;font-size:18px;line-height:26px}
.gen .gauge + .gen__desc{margin-top:24px}
.gen--compact{padding:0 0 20px}.gen--compact .gauge,.gen--compact .gen__title,.gen--compact>.gen__desc,.gen--compact .gen__hint{display:none}.gen--compact .gencards{margin-top:12px}.generation-failure{font-size:12px;line-height:1.7;color:#a45b28;margin-top:10px}.generation-failure button{display:block;margin-top:8px;color:#575cf6;background:white;border:1px solid #dad8f6;border-radius:8px;padding:6px 10px;cursor:pointer}.generation-failure button:disabled{opacity:.5;cursor:default}
.gauge__svg { transform: rotate(-90deg); }
.gencard { border-style: solid; }
.gen .gencards--single { grid-template-columns: minmax(0, 360px); justify-content: center; }
.gencard__fill { display: block; }
.generation-spinner { width: 12px; height: 12px; border: 1.5px solid #dfddff; border-top-color: #575cf6; border-radius: 50%; animation: generation-spin 1s linear infinite; }
.is-indeterminate { animation: generation-slide 1.8s ease-in-out infinite alternate; }
@keyframes generation-spin { to { transform: rotate(360deg); } }
@keyframes generation-slide { to { transform: translateX(185%); } }
@media (prefers-reduced-motion: reduce) { .generation-spinner,.is-indeterminate { animation: none; } }
@media (max-width: 700px) { .gencards { grid-template-columns: 1fr; } }
</style>
