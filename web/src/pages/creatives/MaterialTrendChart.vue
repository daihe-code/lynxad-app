<template>
  <div ref="chart" class="material-trend">
    <div class="chart-labels"><span>{{ label(primary) }}</span><span>{{ label(secondary) }}</span></div>
    <svg :viewBox="`0 0 ${chartWidth} 310`" role="img" :aria-label="`${label(primary)}与${label(secondary)}每日趋势`">
      <g v-for="i in 5" :key="i">
        <line x1="60" :x2="chartWidth - 60" :y1="yTick(i)" :y2="yTick(i)" stroke="#e6e7f0" />
        <text x="50" :y="yTick(i) + 4" text-anchor="end">{{ tick(primary, primaryMax, i) }}</text>
        <text :x="chartWidth - 50" :y="yTick(i) + 4">{{ tick(secondary, secondaryMax, i) }}</text>
      </g>
      <path :d="path(primary, primaryMax)" fill="none" stroke="#5c55ff" stroke-width="2.5" />
      <path :d="path(secondary, secondaryMax)" fill="none" stroke="#23bec8" stroke-width="2.5" />
      <g v-for="(point, i) in points" :key="point.date">
        <text v-if="i % labelStep === 0 || i === points.length - 1" :x="x(i)" y="294" text-anchor="middle">{{ point.date.slice(5) }}</text>
        <template v-for="(field, index) in [primary, secondary]" :key="field">
          <circle v-if="valid(point.metrics[field])" :cx="x(i)" :cy="y(point.metrics[field]!, index ? secondaryMax : primaryMax)" r="4" :fill="index ? '#23bec8' : '#5c55ff'" tabindex="0">
            <title>{{ point.date }} · {{ label(field) }}：{{ materialMetricValue(field, point.metrics[field]) }}</title>
          </circle>
        </template>
      </g>
    </svg>
    <div v-if="empty && emptyLabel" class="chart-empty">{{ emptyLabel }}</div>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { materialMetrics, materialMetricValue } from '@/features/creative/materialStatistics';
const props = defineProps<{ points: { date: string; metrics: Record<string, number | null> }[]; primary: string; secondary: string; emptyLabel: string }>();
const chart = ref<HTMLElement>(), chartWidth = ref(960);
let observer: ResizeObserver | undefined;
onMounted(() => { observer = new ResizeObserver(entries => { chartWidth.value = Math.max(300, Math.round(entries[0]!.contentRect.width)); }); observer.observe(chart.value!); });
onUnmounted(() => observer?.disconnect());
const label = (key: string) => materialMetrics.find(metric => metric.key === key)?.label || key;
const valid = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
const maximum = (key: string) => Math.max(1, ...props.points.map(point => point.metrics[key]).filter(valid));
const primaryMax = computed(() => maximum(props.primary)), secondaryMax = computed(() => maximum(props.secondary));
const empty = computed(() => !props.points.some(point => valid(point.metrics[props.primary]) || valid(point.metrics[props.secondary])));
const labelStep = computed(() => Math.max(1, Math.ceil(props.points.length / (chartWidth.value < 500 ? 4 : 7))));
const x = (i: number) => props.points.length < 2 ? chartWidth.value / 2 : 60 + i * (chartWidth.value - 120) / (props.points.length - 1);
const y = (value: number, max: number) => 262 - value * 234 / max;
const yTick = (i: number) => 28 + (i - 1) * 58.5;
const tick = (key: string, max: number, i: number) => (max * (5 - i) / 4).toLocaleString('zh-CN', { maximumFractionDigits: 2, notation: max >= 10000 ? 'compact' : 'standard' }) + (['ctr', 'cvr'].includes(key) ? '%' : '');
function path(key: string, max: number) {
  let joined = false;
  return props.points.map((point, i) => { const value = point.metrics[key]; if (!valid(value)) { joined = false; return ''; } const segment = `${joined ? 'L' : 'M'}${x(i)},${y(value, max)}`; joined = true; return segment; }).join(' ');
}
</script>
<style scoped>
.material-trend{position:relative}.chart-labels{display:flex;justify-content:space-between;color:#9696a7;font-size:12px;margin:24px 0 0}.material-trend svg{display:block;width:100%;min-height:210px}.material-trend text{font:11px sans-serif;fill:#9494a6}.material-trend circle:focus{outline:2px solid #5c55ff;outline-offset:3px}.chart-empty{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);padding:10px 16px;color:#9292a5;background:rgba(255,255,255,.9);font-size:13px;text-align:center}
</style>
