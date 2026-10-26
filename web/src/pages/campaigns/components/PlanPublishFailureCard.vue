<template>
  <section class="publish-failure" aria-label="投放计划提交失败">
    <header class="publish-failure__head" role="status" aria-live="polite">
      <span class="publish-failure__icon" aria-hidden="true">!</span>
      <h2>{{ failure.title }}</h2>
      <p>{{ failure.description }}</p>
    </header>
    <dl class="publish-failure__guidance">
      <div><dt>需要处理</dt><dd>{{ failure.handling }}</dd></div>
      <div><dt>下一步</dt><dd>{{ failure.next }}</dd></div>
    </dl>
    <p class="publish-failure__retention"><ReferenceIcon name="info" />{{ failure.retention }}</p>
    <details class="publish-failure__details">
      <summary>查看错误详情<ReferenceIcon name="caret" /></summary>
      <p>{{ failure.detail }}</p>
    </details>
    <div class="publish-failure__actions">
      <button type="button" class="publish-failure__primary" :disabled="busy" @click="emit('recover')">{{ busy ? '正在更新…' : failure.action }}<ReferenceIcon name="forward" /></button>
      <button type="button" class="publish-failure__back" :disabled="busy" @click="emit('back')">返回投放列表</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { planPublishFailure } from '@/features/delivery/planPublishFailure';
import ReferenceIcon from '../reference/ReferenceIcon.vue';

const props = defineProps<{ recovery?: string | null; error?: string; busy?: boolean }>();
const emit = defineEmits<{ recover: []; back: [] }>();
const failure = computed(() => planPublishFailure(props.recovery, props.error));
</script>

<style scoped>
.publish-failure { padding: 40px 28px 44px; border: 1px solid #eeeff8; border-radius: 18px; background: white; color: #20213b; text-align: center; box-shadow: 0 4px 20px #575cf605; }
.publish-failure__head { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.publish-failure__icon { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 50%; background: #ffab00; color: white; font-size: 32px; font-weight: 700; line-height: 1; }
.publish-failure__head h2 { margin: 2px 0 0; font-size: 26px; font-weight: 700; line-height: 1.4; }
.publish-failure__head p { margin: 0; color: #74799e; font-size: 16px; line-height: 1.7; }
.publish-failure__guidance { max-width: 850px; margin: 24px auto 0; padding: 12px 28px; border-radius: 12px; background: #f5f6fd; text-align: left; }
.publish-failure__guidance > div { display: grid; grid-template-columns: 116px minmax(0, 1fr); gap: 24px; padding: 16px 0; font-size: 15px; line-height: 1.6; }
.publish-failure__guidance > div + div { border-top: 1px solid #dfe2f5; }
.publish-failure__guidance dt { color: #74799e; font-weight: 500; }
.publish-failure__guidance dd { margin: 0; font-weight: 500; overflow-wrap: anywhere; }
.publish-failure__retention { display: flex; align-items: center; justify-content: center; gap: 8px; margin: 24px 0 0; color: #7c7f9b; font-size: 14px; line-height: 1.6; }
.publish-failure__retention svg { flex-shrink: 0; width: 17px; height: 17px; }
.publish-failure__details { max-width: 850px; margin: 22px auto 0; font-size: 14px; }
.publish-failure__details summary { display: inline-flex; align-items: center; gap: 6px; padding: 4px; color: #5755f9; cursor: pointer; list-style: none; }
.publish-failure__details summary::-webkit-details-marker { display: none; }
.publish-failure__details summary svg { width: 15px; height: 15px; transition: transform .15s; }
.publish-failure__details[open] summary svg { transform: rotate(180deg); }
.publish-failure__details p { margin: 14px 0 0; padding: 16px 20px; border: 1px solid #e8e9f4; border-radius: 10px; background: #fafaff; color: #656b87; text-align: left; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.publish-failure__actions { display: flex; justify-content: center; flex-wrap: wrap; gap: 16px; margin-top: 28px; }
.publish-failure__actions button { display: inline-flex; align-items: center; justify-content: center; gap: 10px; min-height: 44px; padding: 10px 24px; border: 1px solid #5b55f9; border-radius: 8px; font: inherit; font-size: 15px; font-weight: 600; line-height: 1.5; cursor: pointer; }
.publish-failure__primary { background: #5b55f9; box-shadow: 0 3px 10px #5755f81c; color: white; }
.publish-failure__back { background: white; color: #5755f9; }
.publish-failure__actions button svg { width: 17px; height: 17px; }
.publish-failure__actions button:disabled { opacity: .5; cursor: wait; }
.publish-failure button:focus-visible, .publish-failure summary:focus-visible { outline: 2px solid #5b55f9; outline-offset: 3px; border-radius: 8px; }
@media (max-width: 767px) {
  .publish-failure { padding: 28px 16px; }
  .publish-failure__head h2 { font-size: 22px; }
  .publish-failure__head p { font-size: 14px; }
  .publish-failure__guidance { padding: 6px 16px; }
  .publish-failure__guidance > div { grid-template-columns: 64px minmax(0, 1fr); gap: 16px; font-size: 14px; }
  .publish-failure__retention { align-items: flex-start; text-align: left; font-size: 13px; }
  .publish-failure__retention svg { margin-top: 2px; }
  .publish-failure__actions { flex-direction: column; gap: 12px; margin-top: 24px; }
}
</style>
