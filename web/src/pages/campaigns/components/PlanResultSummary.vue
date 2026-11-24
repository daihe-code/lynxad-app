<template>
  <details :key="plan.planId" class="plan-result-summary">
    <summary><h3>投放计划摘要</h3><span class="plan-result-summary__brief">{{ brief }}</span><ReferenceIcon class="plan-result-summary__caret" name="caret" /></summary>
    <div class="plan-result-summary__columns">
      <section v-for="column in summary" :key="column.title"><h4>{{ column.title }}</h4><dl><div v-for="[label, value] in column.rows" :key="label"><dt>{{ label }}</dt><dd>{{ value }}</dd></div></dl></section>
    </div>
  </details>
  <details v-if="instances.length || historyError" class="plan-result-summary" :open="plan.qcStatus === 'TRANSFER_ARCHIVED' || undefined">
    <summary><h3>历史投放数据</h3><span class="plan-result-summary__brief">{{ instances.length }} 次投放</span><ReferenceIcon class="plan-result-summary__caret" name="caret" /></summary>
    <div class="transfer-history">
      <p v-if="historyError" role="status">历史投放数据暂未读取。<button type="button" @click="loadHistory">重试</button></p>
      <table v-if="instances.length">
        <caption>初始计划 ID <strong>{{ initialPlanId }}</strong></caption>
        <thead><tr><th>转投时间</th><th>统计日期</th><th>消耗</th><th>成交金额</th><th>ROI</th><th>订单数</th></tr></thead>
        <tbody><tr v-for="instance in instances" :key="instance.qcPlanId">
          <td>{{ reviewTime(instance.archivedAt) }}</td><td>{{ instance.statisticsStartDate && instance.statisticsStartDate !== instance.statisticsDate ? instance.statisticsStartDate + ' 至 ' : '' }}{{ instance.statisticsDate || '—' }}</td>
          <td>{{ money(instance.costYuan) }}</td><td>{{ money(instance.gmvYuan) }}</td><td>{{ instance.roi ?? '—' }}</td><td>{{ instance.orders ?? '—' }}</td>
        </tr></tbody>
      </table>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { listPlanTransferHistory, type PlanListVo, type PlanTransferHistory } from '@/features/delivery/deliveryApi';
import { planSubmissionSummary } from '@/features/delivery/planSubmissionPresentation';
import { reviewTime } from '@/features/delivery/planReview';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
const props = defineProps<{ plan: PlanListVo; awemeName?: string }>();
const instances = ref<PlanTransferHistory[]>([]), historyError = ref(false);
// History is returned newest first within this logical plan.
const initialPlanId = computed(() => instances.value.at(-1)?.qcPlanId);
const money = (value?: number | null) => value == null ? '—' : `¥${Number(value).toFixed(2)}`;
let historyRead = 0;
async function loadHistory() {
  const request = ++historyRead;
  instances.value = []; historyError.value = false;
  if (props.plan.source !== 'LINGTOU' || (!props.plan.qcPlanId && props.plan.qcStatus !== 'TRANSFER_ARCHIVED')) return;
  try {
    const history = await listPlanTransferHistory(props.plan.planId);
    if (request === historyRead && Array.isArray(history)) instances.value = history;
  } catch { if (request === historyRead) historyError.value = true; }
}
watch(() => [props.plan.planId, props.plan.qcPlanId, props.plan.qcStatus], loadHistory, { immediate: true });
const summary = computed(() => planSubmissionSummary(props.plan, props.awemeName));
const brief = computed(() => {
  const fields = Object.fromEntries(summary.value.flatMap(column => column.rows));
  return [fields['投放商品'] === '—' ? '商品数量待同步' : `${fields['投放商品']}商品`, `日预算 ${fields['日预算']?.replace(' / 天', '')}`,
    fields['净成交ROI目标'] === '无需设置' ? '放量投放' : `净成交 ROI 目标 ${fields['净成交ROI目标']}`].join(' · ');
});
</script>

<style scoped>
.plan-result-summary{border:1px solid #e9eaf4;border-radius:22px;background:#fff;box-shadow:0 4px 16px #262c6b05;color:#20213b}
.plan-result-summary summary{display:flex;align-items:center;gap:14px;min-height:64px;padding:20px 32px;box-sizing:border-box;cursor:pointer;list-style:none}
.plan-result-summary summary::-webkit-details-marker{display:none}
.plan-result-summary summary:focus-visible{outline:2px solid #575cf6;outline-offset:3px;border-radius:22px}
.plan-result-summary h3{flex:none;margin:0;padding-left:13px;border-left:4px solid #5b55ff;font-size:17px;font-weight:650;line-height:22px}
.plan-result-summary:not([open]) h3{border-color:#c9c9d6}
.plan-result-summary__brief{margin-left:auto;text-align:right;color:#81849f;font-size:12px;line-height:1.7}
.plan-result-summary__caret{flex:none;width:17px;height:17px;color:#81849f}
.plan-result-summary[open] .plan-result-summary__caret{transform:rotate(180deg)}
.plan-result-summary__columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px;padding:24px 32px 30px;border-top:1px solid #eeeff6}
.plan-result-summary__columns section{min-width:0}
.plan-result-summary h4{margin:0 0 12px;color:#81849f;font-size:13px;font-weight:500}
.plan-result-summary dl{margin:0}
.plan-result-summary dl div{display:flex;align-items:baseline;gap:16px;justify-content:space-between;padding:10px 0;border-bottom:1px solid #eeeff6;font-size:14px;line-height:1.5}
.plan-result-summary dl div:last-child{border-bottom:0}
.plan-result-summary dt{flex:none;color:#81849f}
.plan-result-summary dd{margin:0;text-align:right;font-weight:600;overflow-wrap:anywhere}
.transfer-history{overflow:auto;padding:0 24px 20px}
.transfer-history table{width:100%;border-collapse:collapse;text-align:left;font-size:13px;white-space:nowrap}
.transfer-history caption{padding:0 8px 12px;text-align:left;color:#646881}
.transfer-history th,.transfer-history td{padding:10px 8px;border-top:1px solid #eeeff6}
.transfer-history th{color:#646881;font-weight:600}
.transfer-history button{margin-left:8px;color:#575cf6}
@media(max-width:767px){.plan-result-summary summary{padding:18px;flex-wrap:wrap;gap:10px}.plan-result-summary h3{flex:1}.plan-result-summary__brief{order:3;width:100%;text-align:left;margin-left:17px}.plan-result-summary__columns{grid-template-columns:1fr;padding:20px 18px;gap:22px}.plan-result-summary dl div{font-size:13px;gap:10px}}
</style>
