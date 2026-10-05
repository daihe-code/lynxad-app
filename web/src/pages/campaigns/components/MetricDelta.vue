<template>
  <el-tooltip :content="description" popper-class="campaign-tooltip" placement="top">
    <span class="metric-delta" :class="direction" :aria-label="description">
      <template v-if="known"><svg viewBox="0 0 16 16" aria-hidden="true"><path v-if="direction === 'up'" d="M8 13V3M4 7l4-4 4 4" /><path v-else d="M8 3v10M4 9l4 4 4-4" /></svg>{{ formatted }}{{ percent ? '%' : '' }}</template><template v-else>—</template>
    </span>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ value?: number | null; percent?: boolean; previousTs?: string; label: string }>();
const known = computed(() => props.value != null && Number.isFinite(Number(props.value)));
const direction = computed(() => !known.value ? 'flat' : Number(props.value) >= 0 ? 'up' : 'down');
const formatted = computed(() => { const n = Math.abs(Number(props.value)); return props.percent ? n.toFixed(1) : n.toFixed(2).replace(/\.?0+$/, ''); });
const description = computed(() => !known.value ? `${props.label}暂无可比较的历史数据` : `${props.label}较上次同步${Number(props.value) === 0 ? '无变化' : direction.value === 'up' ? '增加' : '减少'} ${formatted.value}${props.percent ? '%' : ''}${props.previousTs ? `（${new Date(props.previousTs).toLocaleString('zh-CN')}）` : ''}`);
</script>

<style scoped>
.metric-delta { display: inline-flex; gap: 3px; align-items: center; font-size: 12px; line-height: 20px; font-weight: 400; font-variant-numeric: tabular-nums; white-space: nowrap; color: #7c7f99; vertical-align: baseline; cursor: help; }
.metric-delta svg { width: 12px; height: 12px; fill: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; flex-shrink: 0; }
.up { color: #0ba05f; }.down { color: #e5484d; }
</style>
