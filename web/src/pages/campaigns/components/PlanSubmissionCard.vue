<template>
  <PlanPublishFailureCard v-if="phase === 'FAILED'" :recovery="recovery" :error="error" :busy="busy" @recover="emit('recover', $event)" @back="emit('back')" />
  <section v-else class="submission-card" :class="`submission-card--${phase.toLowerCase()}`" aria-label="投放计划状态">
    <header class="submission-card__head" role="status" aria-live="polite">
      <span v-if="phase === 'SUBMITTING'" class="submission-card__spinner" aria-hidden="true" />
      <span v-else class="submission-card__icon" aria-hidden="true">
        <ReferenceIcon :name="phase === 'DELIVERING' ? 'play' : 'info'" />
      </span>
      <h2>{{ message.title }}</h2>
      <p>{{ message.description }}</p>
    </header>

    <div v-if="error || $slots.recovery" class="submission-card__recovery">
      <p v-if="error" class="submission-card__error" role="alert">{{ error }}</p>
      <slot name="recovery" />
    </div>
    <p class="submission-card__hint"><ReferenceIcon name="info" />{{ message.hint }}</p>

    <div class="submission-card__summary">
      <h3>投放计划摘要</h3>
      <div class="submission-card__columns">
        <div v-for="column in summary" :key="column.title" class="submission-card__column">
          <h4>{{ column.title }}</h4>
          <dl>
            <div v-for="[label, value] in column.rows" :key="label" class="submission-card__row">
              <dt>{{ label }}</dt><dd>{{ value }}</dd>
            </div>
          </dl>
        </div>
      </div>
    </div>

    <button type="button" class="submission-card__back" @click="emit('back')">返回投放列表<ReferenceIcon name="forward" /></button>
    <p v-if="message.note" class="submission-card__note">{{ message.note }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { planSubmissionMessage, type PlanSubmissionPhase } from '@/features/delivery/planSubmissionPresentation';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import PlanPublishFailureCard from './PlanPublishFailureCard.vue';
import type { PublishFailureTarget } from '@/features/delivery/planPublishFailure';

const props = defineProps<{
  phase: PlanSubmissionPhase;
  summary: { title: string; rows: string[][] }[];
  error?: string;
  recovery?: string | null;
  busy?: boolean;
}>();
const emit = defineEmits<{ back: []; recover: [target: PublishFailureTarget] }>();
const message = computed(() => planSubmissionMessage(props.phase));
</script>

<style scoped>
.submission-card { padding: 28px 20px 20px; border: 1px solid #eeeff8; border-radius: 18px; background: #fff; color: #20213b; text-align: center; box-shadow: 0 4px 20px #575cf605; }
.submission-card__head { display: flex; flex-direction: column; align-items: center; gap: 10px; margin-bottom: 20px; }
.submission-card__head h2 { margin: 5px 0 0; font-size: 24px; font-weight: 700; line-height: 1.4; }
.submission-card__head p { margin: 0; color: #7c7f9b; font-size: 15px; line-height: 1.6; }
.submission-card__spinner { width: 40px; height: 40px; border: 6px solid #eeedff; border-top-color: #5b55f9; border-right-color: #5b55f9; border-radius: 50%; animation: submission-spin 1s linear infinite; }
.submission-card__icon { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 50%; color: #5755f8; background: #efedff; }
.submission-card__icon svg { width: 24px; height: 24px; }
.submission-card--uncertain .submission-card__icon { color: #bd7c18; background: #fff6e5; }
.submission-card__summary { margin-bottom: 16px; padding: 22px 12px 12px; background: #f7f7fd; border-radius: 14px; text-align: left; }
.submission-card__summary h3 { margin: 0 18px 12px; padding-left: 12px; border-left: 4px solid #5b55f9; font-size: 18px; font-weight: 700; line-height: 1.3; }
.submission-card__columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0; }
.submission-card__column { min-width: 0; }
.submission-card__column h4 { margin: 0; padding: 0 18px 10px; color: #8587a3; font-size: 13px; font-weight: 600; }
.submission-card__column dl { height: calc(100% - 30px); margin: 0; padding: 0 18px; background: white; }
.submission-card__column:first-child dl { border-radius: 10px 0 0 10px; border-right: 1px solid #eeeff7; }
.submission-card__column:last-child dl { border-radius: 0 10px 10px 0; }
.submission-card__row { display: flex; justify-content: space-between; align-items: baseline; gap: 20px; padding: 8px 0; border-bottom: 1px solid #ebeef5; font-size: 14px; line-height: 1.5; }
.submission-card__row:last-child { border-bottom: 0; }
.submission-card__row dt { flex-shrink: 0; color: #7c7f9b; }
.submission-card__row dd { min-width: 0; margin: 0; font-weight: 600; text-align: right; overflow-wrap: anywhere; }
.submission-card__hint { display: flex; align-items: center; justify-content: center; gap: 8px; margin: 16px 0 14px; color: #7c7f9b; font-size: 13px; line-height: 1.6; }
.submission-card__hint svg { flex-shrink: 0; width: 16px; height: 16px; }
.submission-card__back { display: inline-flex; align-items: center; justify-content: center; gap: 10px; min-width: 180px; padding: 11px 24px; border: 0; border-radius: 8px; background: #5b55f9; box-shadow: 0 3px 10px #5755f81c; color: white; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.submission-card__back:hover { background: #4c46ee; }
.submission-card__back:focus-visible { outline: 2px solid #5b55f9; outline-offset: 3px; }
.submission-card__back svg { width: 16px; height: 16px; }
.submission-card__note { margin: 10px 0 0; color: #8587a3; font-size: 12px; }
.submission-card__recovery { margin: 16px auto; max-width: 640px; font-size: 14px; }
.submission-card__error { color: #b54750; line-height: 1.6; overflow-wrap: anywhere; }
@keyframes submission-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .submission-card__spinner { animation: none; } }
@media (max-width: 767px) {
  .submission-card { padding: 24px 12px; }
  .submission-card__head h2 { font-size: 22px; }
  .submission-card__summary { padding: 18px 8px 8px; }
  .submission-card__summary h3 { margin-left: 8px; }
  .submission-card__columns { grid-template-columns: 1fr; gap: 18px; }
  .submission-card__column h4 { padding-left: 10px; }
  .submission-card__column dl { height: auto; padding: 0 10px; border-radius: 8px !important; border-right: 0 !important; }
  .submission-card__row { gap: 12px; font-size: 13px; }
  .submission-card__hint { align-items: flex-start; text-align: left; }
  .submission-card__hint svg { margin-top: 3px; }
}
</style>
