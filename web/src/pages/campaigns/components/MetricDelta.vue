<template>
  <el-tooltip :content="description" popper-class="campaign-tooltip" placement="top">
    <span class="metric-delta" :class="[tone, { compact }]" :aria-label="description">
      <template v-if="known"><span v-if="!compact" class="delta-basis">{{ prefix }}</span><span class="delta-trend"><svg v-if="direction !== 'flat'" viewBox="0 0 16 16" aria-hidden="true"><path v-if="direction === 'up'" d="M8 13V3M4 7l4-4 4 4" /><path v-else d="M8 3v10M4 9l4 4 4-4" /></svg><span class="delta-value">{{ compact && direction !== 'flat' ? direction === 'down' ? '-' : '+' : '' }}{{ formatted }}%</span></span></template><template v-else>{{ compact || zeroBaseline ? '—' : '暂无对比' }}</template>
    </span>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ value?: number | null; current?: number | null; previous?: number | null; lowerIsBetter?: boolean; previousTs?: string; label: string; basis?: string; previousStartDate?: string | null; previousEndDate?: string | null; unavailableReason?: string | null; compact?: boolean }>();
const zeroBaseline = computed(() => props.previous === 0 && props.current != null);
const known = computed(() => !zeroBaseline.value && props.value != null && Number.isFinite(Number(props.value)));
const direction = computed(() => !known.value || Number(props.value) === 0 ? 'flat' : Number(props.value) > 0 ? 'up' : 'down');
const tone = computed(() => props.lowerIsBetter && direction.value !== 'flat' ? direction.value === 'up' ? 'down' : 'up' : direction.value);
const prefix = computed(() => props.basis === 'YESTERDAY_FULL_DAY' ? '较昨日' : props.basis === 'PREVIOUS_DAY' ? '较前日' : props.basis === 'PREVIOUS_PERIOD' ? '环比' : '较上次');
const formatted = computed(() => Math.abs(Number(props.value)).toFixed(props.compact ? 2 : 1));
const description = computed(() => {
  const basis = props.basis === 'YESTERDAY_FULL_DAY' ? '昨日全天（今日为截至当前的累计数据）' : props.basis === 'PREVIOUS_DAY' ? '前一日全天' : props.basis === 'PREVIOUS_PERIOD' ? '前一个等长区间' : '上次同步';
  const period = props.previousStartDate && props.previousEndDate ? `（${props.previousStartDate} 至 ${props.previousEndDate}）` : props.previousTs ? `（${new Date(props.previousTs).toLocaleString('zh-CN')}）` : '';
  if (zeroBaseline.value) return `${props.label}：对比值为0，无法计算百分比；对比${basis}${period}`;
  if (!known.value) return `${props.label}：${props.unavailableReason || '该指标暂无可比较的数据'}；对比${basis}${period}`;
  return `${props.label}较${basis}${period}${direction.value === 'flat' ? '无变化' : direction.value === 'up' ? '增加' : '减少'} ${formatted.value}%`;
});
</script>

<style scoped>
.metric-delta { display: inline-flex; flex-wrap: wrap; column-gap: 3px; align-items: center; max-width: 100%; font-size: 12px; line-height: 20px; font-weight: 400; font-variant-numeric: tabular-nums; color: #7c7f99; vertical-align: baseline; cursor: help; }
.delta-basis { white-space: nowrap; }.delta-trend { display: inline-flex; align-items: center; gap: 3px; min-width: 0; max-width: 100%; }.delta-value { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.metric-delta svg { width: 12px; height: 12px; fill: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; flex-shrink: 0; }
.up { color: #0ba05f; }.down { color: #e5484d; }
</style>
