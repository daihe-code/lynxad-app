<template>
  <div class="metric-trend">
    <div class="trend-heading"><strong>{{ column.label }}趋势</strong><span>{{ range.startDate }}{{ range.startDate === range.endDate ? '' : ` 至 ${range.endDate}` }} · {{ hourly ? '每小时' : '每日' }}</span></div>
    <div v-if="loading" class="trend-state" role="status">正在加载趋势数据…</div>
    <div v-else-if="error" class="trend-state trend-error"><span>{{ error }}</span><el-button link type="primary" @click="$emit('retry')">重新加载</el-button></div>
    <template v-else>
      <div class="point-value" aria-live="polite"><span>{{ selected?.time || '—' }}</span><b>{{ metricText(selected?.value, column.kind) }}</b></div>
      <svg viewBox="0 0 640 250" role="img" tabindex="0" :aria-label="`${column.label}趋势，${selected?.time || ''}：${metricText(selected?.value, column.kind)}。可用左右方向键查看时间点`" @pointermove="movePoint" @pointerleave="activeIndex = null" @keydown.left.prevent="step(-1)" @keydown.right.prevent="step(1)">
        <g v-for="tick in plot.ticks" :key="tick.y"><line x1="64" x2="600" :y1="tick.y" :y2="tick.y" stroke="#eceef5" /><text x="54" :y="tick.y + 4" text-anchor="end">{{ tickText(tick.value) }}</text></g>
        <path v-if="plot.hasData" class="trend-curve" :d="plot.path" fill="none" stroke="#575cf6" stroke-width="3" stroke-linejoin="round" stroke-linecap="round" />
        <g v-for="(point, i) in plot.values" :key="point.time">
          <text v-if="i % labelStep === 0 || i === plot.values.length - 1" :x="point.x" y="236" :text-anchor="i === 0 ? 'start' : i === plot.values.length - 1 ? 'end' : 'middle'">{{ hourly ? point.time.slice(11, 16) : point.time.slice(5, 10) }}</text>
          <circle v-if="point.isolated" :cx="point.x" :cy="point.y!" r="3" fill="#575cf6" />
        </g>
        <line v-if="selected && plot.hasData" :x1="selected.x" :x2="selected.x" y1="28" y2="208" stroke="#b6b4ee" stroke-dasharray="4 4" />
        <circle v-if="selected?.y != null" :cx="selected.x" :cy="selected.y" r="4" fill="#575cf6" stroke="white" stroke-width="2" />
        <text v-if="!plot.hasData" x="332" y="120" text-anchor="middle">暂无该指标的趋势数据</text>
      </svg>
      <p v-if="warning" class="trend-warning">{{ warning }}</p>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { CampaignStatisticsTrend } from '@/features/delivery/deliveryApi';
import { metricPlot } from '@/features/delivery/campaignMetricTrend';
import { metricText, type MetricColumn } from '@/features/delivery/campaignMetricTemplates';
const props = defineProps<{ column: MetricColumn; points: CampaignStatisticsTrend['points']; timeDimension?: string; range: { startDate: string; endDate: string }; loading: boolean; error: string; warning?: string | null }>();
defineEmits<{ retry: [] }>();
const activeIndex = ref<number | null>(null);
const plot = computed(() => metricPlot(props.points, props.column.key));
const hourly = computed(() => props.timeDimension ? props.timeDimension === 'stat_time_hour' : props.range.startDate === props.range.endDate);
const labelStep = computed(() => Math.max(1, Math.ceil((plot.value.values.length - 1) / 4)));
const selected = computed(() => activeIndex.value == null ? plot.value.values.filter(p => p.value != null).at(-1) : plot.value.values[activeIndex.value]);
watch(() => props.points, () => { activeIndex.value = null; });
function tickText(value: number) { return value.toLocaleString('zh-CN', { maximumFractionDigits: value !== 0 && Math.abs(value) < 0.01 ? 4 : 2 }) + (props.column.kind === 'rate' ? '%' : ''); }
function movePoint(event: PointerEvent) {
  const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
  const x = (event.clientX - rect.left) / rect.width * 640;
  let nearest = 0;
  for (let i = 1; i < plot.value.values.length; i++) if (Math.abs(plot.value.values[i]!.x - x) < Math.abs(plot.value.values[nearest]!.x - x)) nearest = i;
  activeIndex.value = nearest;
}
function step(direction: number) {
  const lastKnown = plot.value.values.map((point, index) => point.value != null ? index : -1).filter(index => index >= 0).at(-1) ?? 0;
  const start = activeIndex.value ?? lastKnown;
  activeIndex.value = Math.max(0, Math.min(plot.value.values.length - 1, start + direction));
}
</script>
<style scoped>
.metric-trend{color:#303044}.trend-heading{display:flex;justify-content:space-between;gap:12px;align-items:center}.trend-heading strong{font-size:14px}.trend-heading span{font-size:11px;color:#8b8ba1}.point-value{display:flex;align-items:center;gap:12px;min-height:36px;margin-top:8px;font-size:12px;color:#7c7f99}.point-value b{color:#575cf6;font-size:16px;font-variant-numeric:tabular-nums}.metric-trend svg{width:100%;display:block;touch-action:pan-y}.metric-trend svg:focus-visible{outline:2px solid #575cf6;border-radius:6px}.metric-trend svg text{font:11px sans-serif;fill:#8b8ba1}.trend-state{min-height:240px;display:flex;align-items:center;justify-content:center;gap:12px;color:#8b8ba1;font-size:13px}.trend-error{color:#c45058}.trend-warning{font-size:11px;color:#8b8ba1;margin:4px 0 0}.trend-error span{max-width:80%;overflow-wrap:anywhere}@media(max-width:600px){.trend-heading{align-items:flex-start;flex-direction:column;gap:5px}.trend-state{min-height:170px}}
</style>
