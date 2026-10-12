<template>
  <div class="trend-chart">
    <div class="trend-legend"><span><i />{{ primaryLabel }}</span><span><i class="secondary" />{{ secondaryLabel }}</span></div>
    <svg viewBox="0 0 980 280" role="img" :aria-label="`${primaryLabel}与${secondaryLabel}趋势；详细数值见下方表格`">
      <g v-for="i in 5" :key="i"><line x1="58" x2="920" :y1="yTick(i)" :y2="yTick(i)" stroke="#e4e6f2" /><text x="48" :y="yTick(i) + 4" text-anchor="end">{{ tick(primaryMax, i) }}</text><text x="930" :y="yTick(i) + 4">{{ tick(secondaryMax, i) }}</text></g>
      <path :d="primaryPath" fill="none" stroke="#5b57ff" stroke-width="2.5" /><path :d="secondaryPath" fill="none" stroke="#13bab5" stroke-width="2.5" />
      <g v-for="(p, i) in points" :key="p.time">
        <text v-if="i % labelStep === 0 || i === points.length - 1" :x="x(i)" y="266" text-anchor="middle">{{ shortTime(p.time) }}</text>
        <circle v-if="valid(p.metrics[primary])" :cx="x(i)" :cy="y(p.metrics[primary]!, primaryMax)" r="4" fill="#5b57ff"><title>{{ p.time }} · {{ primaryLabel }}：{{ p.metrics[primary] }}</title></circle>
        <circle v-if="valid(p.metrics[secondary])" :cx="x(i)" :cy="y(p.metrics[secondary]!, secondaryMax)" r="3" fill="#13bab5"><title>{{ p.time }} · {{ secondaryLabel }}：{{ p.metrics[secondary] }}</title></circle>
      </g>
    </svg>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ points: { time: string; metrics: Record<string, number | null | undefined> }[]; primary: string; secondary: string; primaryLabel: string; secondaryLabel: string }>();
const valid = (v: unknown): v is number => v != null && Number.isFinite(Number(v));
const maximum = (field: string) => Math.max(1, ...props.points.filter(p => valid(p.metrics[field])).map(p => Number(p.metrics[field])));
const primaryMax = computed(() => maximum(props.primary)), secondaryMax = computed(() => maximum(props.secondary));
const labelStep = computed(() => Math.max(1, Math.ceil(props.points.length / 7)));
const timestamps = computed(() => props.points.map(p => Date.parse(p.time.replace(' ', 'T'))));
const x = (i: number) => { const values = timestamps.value, first = values[0]!, last = values[values.length - 1]!; return values.length <= 1 || last === first ? 489 : 58 + (values[i]! - first) * 862 / (last - first); };
const y = (v: number, max: number) => 238 - Number(v) / max * 216;
const yTick = (i: number) => 22 + (i - 1) * 54;
const tick = (max: number, i: number) => (max * (5 - i) / 4).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
function path(field: string, max: number) {
  let joined = false;
  return props.points.map((p, i) => { const v = p.metrics[field]; if (!valid(v)) { joined = false; return ''; } const segment = `${joined ? 'L' : 'M'}${x(i)},${y(v, max)}`; joined = true; return segment; }).join(' ');
}
const primaryPath = computed(() => path(props.primary, primaryMax.value)), secondaryPath = computed(() => path(props.secondary, secondaryMax.value));
function shortTime(value: string) { return value.length > 10 ? value.slice(11, 16) : value.slice(5); }
</script>
<style scoped>
.trend-chart svg{width:100%;min-height:180px;display:block}.trend-chart text{font:12px sans-serif;fill:#82869b}.trend-legend{display:flex;justify-content:flex-end;gap:24px;font-size:12px;margin:12px 0}.trend-legend i{display:inline-block;width:9px;height:9px;background:#5b57ff;border-radius:2px;margin-right:6px}.trend-legend i.secondary{background:#13bab5}
</style>
