<template>
  <el-form-item :label="hint.label" class="optimization-field" :data-field="field">
    <template #label><OptimizationLabel :field="field" /></template>
    <slot />
    <p class="field-hint">{{ hint.brief }}</p>
  </el-form-item>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { optimizationFields, type OptimizationFieldName } from '@/features/delivery/optimizationLabels';
import OptimizationLabel from './OptimizationLabel.vue';
const props = defineProps<{ field: OptimizationFieldName }>();
const hint = computed(() => optimizationFields[props.field]);
</script>
<style scoped>
.field-hint{flex-basis:100%;margin:7px 0 0;font-size:12px;line-height:1.6;color:var(--el-text-color-secondary)}
.optimization-field :deep(.el-form-item__label){height:auto;line-height:1.55;margin-bottom:8px}
</style>
