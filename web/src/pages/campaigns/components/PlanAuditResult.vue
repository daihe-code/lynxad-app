<template>
  <section v-if="notice" class="audit-result" aria-label="投放审核结果">
    <header class="audit-result__heading">
      <span class="audit-result__icon" aria-hidden="true"><ReferenceIcon name="hourglass" /></span>
      <h2>审核中</h2>
      <p>广告计划已提交至平台审核，审核通过后将按投放设置开始投放。</p>
      <p>审核结果以千川返回为准，等待期间你可以正常进行其他操作。</p>
    </header>

    <details :key="plan.planId" class="audit-summary">
      <summary>
        <h3>投放计划摘要</h3>
        <span class="audit-summary__brief">{{ brief }}</span>
        <ReferenceIcon class="audit-summary__caret" name="caret" />
      </summary>
      <div class="audit-summary__columns">
        <section v-for="column in summary" :key="column.title">
          <h4>{{ column.title }}</h4>
          <dl><div v-for="[label, value] in column.rows" :key="label"><dt>{{ label }}</dt><dd>{{ value }}</dd></div></dl>
        </section>
      </div>
    </details>

    <section class="audit-progress" aria-label="审批进度">
      <header class="audit-progress__heading">
        <h3>审批进度</h3>
        <div class="audit-progress__tools">
          <span>最近刷新 {{ reviewTime(plan.review?.lastCheckedAt, '尚未查询') }}</span>
          <button type="button" :disabled="refreshing || notice.cooldown > 0" @click="refresh"><ReferenceIcon name="refresh" />{{ refreshing ? '查询中…' : notice.cooldown > 0 ? `${notice.cooldown} 秒后可刷新` : '刷新审核进度' }}</button>
        </div>
      </header>
      <ol class="audit-timeline" aria-label="平台审核阶段">
        <li v-for="node in nodes" :key="node.title" :class="`audit-timeline__item--${node.state}`" :aria-current="node.state === 'current' ? 'step' : undefined">
          <span class="audit-timeline__dot" aria-hidden="true" />
          <div><div class="audit-timeline__title"><h4>{{ node.title }}</h4><span>{{ node.time }}</span></div><p>{{ node.description }}</p></div>
        </li>
      </ol>
      <p v-if="notice.delayed || notice.unavailable" class="audit-progress__notice" role="status"><strong>{{ notice.title }}</strong>：{{ notice.message }} 查询失败不会将计划改为审核未通过。</p>
      <p class="audit-progress__note">{{ notice.waiting }}。时间从系统首次记录本轮审核起计算。</p>
      <p v-if="feedback" class="audit-progress__feedback" role="status">{{ feedback }}</p>
    </section>

    <footer class="audit-result__footer">
      <button type="button" :disabled="!user.trialActive" @click="createAnother"><ReferenceIcon name="plus" />重建一条计划</button>
      <router-link to="/campaigns">查看所有投放计划</router-link>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import type { PlanVo, PlanListVo } from '@/features/delivery/deliveryApi';
import { reviewTimeline, reviewTime } from '@/features/delivery/planReview';
import { planSubmissionSummary } from '@/features/delivery/planSubmissionPresentation';
import { usePlanReview } from '@/features/delivery/usePlanReview';
import { useUserStore } from '@/stores/user';
import { useWizardStore } from '@/stores/wizard';
import ReferenceIcon from '../reference/ReferenceIcon.vue';

const props = defineProps<{ plan: PlanListVo; awemeName?: string }>();
const emit = defineEmits<{ updated: [plan: PlanVo] }>();
const { notice, refreshing, feedback, refresh } = usePlanReview(() => props.plan, plan => emit('updated', plan), () => true);
const nodes = computed(() => reviewTimeline(props.plan));
const summary = computed(() => planSubmissionSummary(props.plan, props.awemeName));
const brief = computed(() => {
  const fields = Object.fromEntries(summary.value.flatMap(column => column.rows));
  return [fields['投放商品'] === '—' ? '商品数量待同步' : `${fields['投放商品']}商品`,
    `日预算 ${fields['日预算']?.replace(' / 天', '')}`,
    fields['净成交ROI目标'] === '无需设置' ? '放量投放' : `净成交 ROI 目标 ${fields['净成交ROI目标']}`].join(' · ');
});
const router = useRouter(), wizard = useWizardStore(), user = useUserStore();
function createAnother() {
  if (!user.trialActive) return;
  wizard.reset(); void router.push('/campaigns/create');
}
</script>

<style scoped>
.audit-result { padding: 12px 0 112px; color: #20213b; }
.audit-result__heading { text-align: center; margin: 0 auto 30px; }
.audit-result__icon { display: grid; place-items: center; width: 56px; height: 56px; margin: 0 auto 14px; border-radius: 50%; color: #5b55ff; background: #eeecff; }
.audit-result__icon svg { width: 25px; height: 25px; }
.audit-result__heading h2 { margin: 0 0 8px; font-size: 22px; line-height: 1.5; font-weight: 700; }
.audit-result__heading p { margin: 3px 0; color: #81849f; font-size: 14px; line-height: 1.7; }
.audit-summary,.audit-progress { border: 1px solid #e9eaf4; border-radius: 22px; background: #fff; box-shadow: 0 4px 16px #262c6b05; }
.audit-summary { margin-bottom: 30px; }
.audit-summary summary { display: flex; align-items: center; gap: 14px; min-height: 64px; padding: 16px 32px; box-sizing: border-box; cursor: pointer; list-style: none; }
.audit-summary summary::-webkit-details-marker { display: none; }
.audit-summary summary:focus-visible { outline: 2px solid #575cf6; outline-offset: 3px; border-radius: 22px; }
.audit-summary h3,.audit-progress h3 { flex: none; margin: 0; padding-left: 13px; border-left: 4px solid #5b55ff; font-size: 17px; font-weight: 650; line-height: 22px; }
.audit-summary:not([open]) h3 { border-color: #c9c9d6; }
.audit-summary__brief { margin-left: auto; text-align: right; color: #81849f; font-size: 12px; line-height: 1.7; }
.audit-summary__caret { flex: none; width: 17px; height: 17px; color: #81849f; }
.audit-summary[open] .audit-summary__caret { transform: rotate(180deg); }
.audit-summary__columns { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 24px; padding: 24px 32px 30px; border-top: 1px solid #eeeff6; }
.audit-summary__columns section { min-width: 0; }
.audit-summary h4 { margin: 0 0 12px; color: #81849f; font-size: 13px; font-weight: 500; }
.audit-summary dl { margin: 0; }
.audit-summary dl div { display: flex; align-items: baseline; gap: 16px; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #eeeff6; font-size: 14px; line-height: 1.5; }
.audit-summary dl div:last-child { border-bottom: 0; }
.audit-summary dt { flex: none; color: #81849f; }
.audit-summary dd { margin: 0; text-align: right; font-weight: 600; overflow-wrap: anywhere; }
.audit-progress { padding: 32px; }
.audit-progress__heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 28px; }
.audit-progress__tools { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 10px; color: #81849f; font-size: 12px; }
.audit-progress__tools button { display: inline-flex; align-items: center; gap: 6px; padding: 8px 12px; border: 1px solid #e2e3ef; border-radius: 8px; background: #fff; color: #53566f; font: inherit; cursor: pointer; }
.audit-progress__tools svg { width: 14px; height: 14px; }
.audit-progress__tools button:disabled { opacity: .55; cursor: default; }
.audit-progress__tools button:focus-visible,.audit-result__footer :focus-visible { outline: 2px solid #575cf6; outline-offset: 3px; }
.audit-timeline { margin: 0; padding: 0; list-style: none; }
.audit-timeline li { position: relative; display: flex; gap: 20px; padding: 0 0 24px; }
.audit-timeline li:not(:last-child)::before { content: ''; position: absolute; top: 16px; bottom: 0; left: 6px; border-left: 2px solid #f0f0f8; }
.audit-timeline__dot { position: relative; flex: none; width: 14px; height: 14px; margin-top: 4px; border-radius: 50%; background: #5b55ff; }
.audit-timeline__item--current .audit-timeline__dot { box-shadow: 0 0 0 4px #e5e1ff; outline: 2px solid #fff; }
.audit-timeline__item--waiting .audit-timeline__dot { background: #ececf4; }
.audit-timeline__title { display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; }
.audit-timeline h4 { margin: 0; font-size: 14px; line-height: 22px; font-weight: 600; }
.audit-timeline__title span { color: #81849f; font-size: 12px; }
.audit-timeline p { margin: 5px 0 0; color: #656a85; font-size: 13px; line-height: 1.8; }
.audit-timeline__item--current h4 { color: #5755ee; }
.audit-timeline__item--waiting h4,.audit-timeline__item--waiting p { color: #9295ad; }
.audit-progress__note,.audit-progress__feedback { margin: 0 0 0 34px; color: #81849f; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
.audit-progress__feedback { margin-top: 8px; color: #5755bb; }
.audit-progress__notice { margin: 0 0 14px 34px; padding: 10px 12px; border-radius: 8px; background: #fff8ed; color: #956320; font-size: 13px; line-height: 1.8; }
.audit-result__footer { position: fixed; z-index: 40; bottom: 0; left: 0; right: 0; display: flex; align-items: center; justify-content: center; gap: 24px; padding: 14px 24px; border-top: 1px solid #eeeff7; background: #fffffffa; }
.audit-result__footer button,.audit-result__footer a { box-sizing: border-box; display: inline-flex; justify-content: center; align-items: center; gap: 9px; min-width: 182px; padding: 13px 24px; border: 1px solid #e0e2f1; border-radius: 10px; background: white; color: #252842; font: inherit; font-size: 14px; font-weight: 600; line-height: 22px; text-decoration: none; cursor: pointer; }
.audit-result__footer button:disabled { opacity: .5; cursor: default; }
.audit-result__footer svg { width: 16px; height: 16px; }
.audit-result__footer a { background: #5b55f5; border-color: #5b55f5; box-shadow: 0 4px 14px #5755ef22; color: #fff; }
@media (max-width: 767px) {
  .audit-result { padding-top: 4px; }
  .audit-result__heading p { font-size: 13px; }
  .audit-summary summary { padding: 18px; flex-wrap: wrap; gap: 10px; }
  .audit-summary h3 { flex: 1; }
  .audit-summary__brief { order: 3; width: 100%; text-align: left; margin-left: 17px; }
  .audit-summary__columns { grid-template-columns: 1fr; padding: 20px 18px; gap: 22px; }
  .audit-summary dl div { font-size: 13px; gap: 10px; }
  .audit-progress { padding: 24px 18px; }
  .audit-progress__heading { align-items: flex-start; flex-direction: column; gap: 14px; }
  .audit-progress__tools { justify-content: flex-start; }
  .audit-timeline li { gap: 14px; }
  .audit-progress__note,.audit-progress__feedback,.audit-progress__notice { margin-left: 28px; }
  .audit-result__footer { gap: 10px; padding: 12px; }
  .audit-result__footer button,.audit-result__footer a { min-width: 0; padding: 11px 12px; font-size: 13px; }
}
</style>
