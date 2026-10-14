<template>
  <span class="optimization-label"><span>{{ hint.label }}</span>
    <el-tooltip :visible="hovered || focused" placement="top" :show-arrow="true" popper-class="optimization-help">
      <template #content><span class="help-content">{{ hint.detail }}</span></template>
      <button type="button" :aria-label="`${hint.label}说明`" @mouseenter="hovered = true" @mouseleave="hovered = false" @focus="focused = true" @blur="focused = false" @keydown.esc.stop.prevent="closeHelp">?</button>
    </el-tooltip>
  </span>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { optimizationFields, type OptimizationFieldName } from '@/features/delivery/optimizationLabels';
const props = defineProps<{ field: OptimizationFieldName }>();
const hint = computed(() => optimizationFields[props.field]);
const hovered = ref(false), focused = ref(false);
function closeHelp(event: KeyboardEvent) { hovered.value = false; focused.value = false; (event.target as HTMLButtonElement).blur(); }
</script>
<style scoped>
.optimization-label{display:inline-flex;align-items:flex-start;gap:6px;line-height:1.55}.optimization-label button{flex-shrink:0;width:15px;height:15px;padding:0;margin-top:3px;border:0;border-radius:50%;font-size:11px;line-height:15px;text-align:center;background:#a6a8bd;color:#fff;cursor:help}.optimization-label button:hover,.optimization-label button:focus-visible{background:var(--el-color-primary);outline:2px solid var(--el-color-primary-light-7);outline-offset:2px}.help-content{display:block;max-width:min(320px,calc(100vw - 48px));line-height:1.8;font-size:12px;white-space:normal;overflow-wrap:anywhere}
</style>
