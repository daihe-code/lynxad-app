<template>
  <el-popover :visible="open && !disabled" :width="640" placement="top-start" :show-arrow="false" :persistent="false" popper-class="campaign-trend-popover">
    <template #reference>
      <article class="metric" :class="tone" tabindex="0" :aria-label="`${column.label}，查看趋势`" @mouseenter="enterCard" @mouseleave="leaveCard" @focus="focusCard" @blur="blurCard" @keydown.esc.stop="close" @keydown.enter.prevent="showChart">
        <div class="metric-heading"><el-tooltip :content="column.description" :trigger="['hover', 'focus']" :show-after="200" :enterable="false" effect="light" placement="top-start" popper-class="campaign-metric-tooltip"><span class="metric-label" tabindex="0" @mouseenter="explain" @mouseleave="endExplain" @focus="explain" @blur="endExplain">{{ column.label }}</span></el-tooltip><el-icon aria-hidden="true"><TrendCharts /></el-icon></div>
        <div class="metric-value"><strong>{{ metricText(value, column.kind) }}</strong><span @mouseenter="explain" @mouseleave="endExplain"><MetricDelta v-bind="metricDelta({ comparison }, column)" :label="column.label" :lower-is-better="column.key === 'cost' || column.kind === 'rate'" compact /></span></div>
      </article>
    </template>
    <div @mouseenter="keepOpen" @mouseleave="leavePopup" @focusin="focusPopup" @focusout="blurPopup" @keydown.esc.stop="close"><CampaignMetricTrend :column="column" :points="trend?.points || []" :time-dimension="trend?.timeDimension" :range="range" :loading="loading" :error="error" :warning="trend?.warning" @retry="$emit('requestTrend', true)" /></div>
  </el-popover>
</template>
<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue';
import { TrendCharts } from '@element-plus/icons-vue';
import type { CampaignStatisticsTrend, PlanListVo } from '@/features/delivery/deliveryApi';
import { metricText, metricDelta, type MetricColumn } from '@/features/delivery/campaignMetricTemplates';
import CampaignMetricTrend from './CampaignMetricTrend.vue';
import MetricDelta from './MetricDelta.vue';
const props = defineProps<{ column: MetricColumn; value?: number | null; comparison?: PlanListVo['comparison']; tone: string; trend?: CampaignStatisticsTrend; range: { startDate: string; endDate: string }; loading: boolean; error: string; disabled: boolean }>();
const emit = defineEmits<{ requestTrend: [refresh: boolean] }>();
const open = ref(false);let inside = false, explaining = false, popupInside = false, popupFocused = false, cardFocused = false;
let showTimer: ReturnType<typeof setTimeout> | undefined, hideTimer: ReturnType<typeof setTimeout> | undefined;
function close() { clearTimeout(showTimer); clearTimeout(hideTimer); open.value = false; popupInside = false; popupFocused = false; }
function showChart() { clearTimeout(showTimer); if (props.disabled || explaining) return; clearTimeout(hideTimer); if (open.value) return; open.value = true; emit('requestTrend', false); }
function enterCard() { inside = true; clearTimeout(hideTimer); clearTimeout(showTimer); showTimer = setTimeout(showChart, 300); }
function leaveCard() { inside = false; clearTimeout(showTimer); hideLater(); }
function hideLater() { clearTimeout(hideTimer); if (!popupInside && !popupFocused && !cardFocused) hideTimer = setTimeout(close, 160); }
function keepOpen() { popupInside = true; clearTimeout(hideTimer); }
function leavePopup() { popupInside = false; hideLater(); }
function focusCard() { cardFocused = true; showChart(); }
function blurCard() { cardFocused = false; hideLater(); }
function focusPopup() { popupFocused = true; clearTimeout(hideTimer); }
function blurPopup(event: FocusEvent) { if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) { popupFocused = false; hideLater(); } }
function explain() { explaining = true; close(); }
function endExplain() { explaining = false; if (inside) enterCard(); }
watch(() => props.disabled, value => { if (value) close(); });
onUnmounted(close);
</script>
<style scoped>
.metric{border-radius:14px;padding:16px 22px;min-width:0;cursor:default}.metric:focus-visible{outline:2px solid #575cf6;outline-offset:3px}.metric-value{display:flex;align-items:baseline;flex-wrap:wrap;gap:4px 10px}.metric-value :deep(.metric-delta){font-size:13px;font-weight:600}.metric-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;color:#7c7f99;font-size:13px;line-height:18px}.metric-heading>.el-icon{color:#7772e0;font-size:18px}.metric-label{text-decoration:underline dashed #aaa8ba;text-underline-offset:4px;cursor:help}.metric-label:focus-visible{outline:2px solid #575cf6;outline-offset:4px;border-radius:2px}.metric strong{display:block;line-height:30px;font-size:25px;font-weight:750;color:#222234;margin:5px 0;font-variant-numeric:tabular-nums}.metric-cost{background:#ebecfe}.metric-orders{background:#f5f0f8}.metric-gmv{background:#eaf0fd}.metric-roi{background:#f4eef6}:global(.campaign-trend-popover.el-popover){max-width:calc(100vw - 32px);max-height:80vh;overflow:auto;box-sizing:border-box;border:1px solid #e9e8f3;border-radius:12px;padding:18px 20px;box-shadow:0 8px 30px #2420401c}
@media(max-width:1100px){.metric{padding:16px}.metric strong{font-size:22px}}@media(max-width:700px){.metric strong{font-size:20px}}
</style>
