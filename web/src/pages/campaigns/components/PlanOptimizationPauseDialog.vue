<template>
  <el-dialog :model-value="modelValue" title="暂停盯盘优化？" width="min(520px, calc(100vw - 32px))" align-center append-to-body class="optimization-pause-dialog" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @update:model-value="!busy && emit('update:modelValue', $event)">
    <div class="pause-content">
      <div class="pause-warning"><el-icon><InfoFilled /></el-icon><strong>广告计划仍会继续投放并产生消耗</strong></div>
      <p class="pause-description">暂停后，目标ROI保护、消耗保护和增长机会洞察将停止。已保存的规则和授权范围保留，<strong>自动保护暂不生效。</strong></p>
      <dl class="pause-effects">
        <div><dt>基础异常监控</dt><dd><span class="pause-continues">继续</span></dd></div>
        <div><dt>尚未执行的建议</dt><dd>失效，不再执行</dd></div>
        <div><dt>已经发出的操作</dt><dd>继续核实结果</dd></div>
      </dl>
      <div v-if="tasksLoading" class="pause-tasks" role="status">正在核对调控任务…</div>
      <div v-else-if="runningTaskMessage || tasksUnavailable" class="pause-tasks">
        <span>{{ runningTaskMessage || '调控任务状态暂未核实，本操作不会停止独立任务。' }}</span>
        <button type="button" :disabled="busy" @click="emit('tasks')">查看调控任务</button>
      </div>
      <p v-if="error" class="pause-error" role="alert">{{ error }}</p>
    </div>
    <template #footer><el-button :disabled="busy" @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="busy" @click="emit('confirm')">暂停盯盘优化</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { InfoFilled } from '@element-plus/icons-vue';

const props = defineProps<{ modelValue: boolean; busy: boolean; error?: string; tasks: { kind: string; task_status?: string }[]; tasksLoading: boolean; tasksUnavailable: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: []; tasks: [] }>();
const runningTaskMessage = computed(() => {
  const counts = new Map<string, number>();
  for (const task of props.tasks) if (task.task_status === 'PROCESSING') counts.set(task.kind, (counts.get(task.kind) || 0) + 1);
  if (!counts.size) return '';
  const count = [...counts.values()].reduce((sum, value) => sum + value, 0);
  return `本计划有${[...counts].map(([kind, value]) => `${value}个${kind}任务`).join('、')}正在运行，本操作不会停止${count === 1 ? '该任务' : '这些任务'}。`;
});
</script>

<style scoped>
.pause-content{color:#24263f;font-size:13px}.pause-warning{display:flex;align-items:center;gap:8px;padding:11px 12px;border:1px solid #ffdbaf;border-radius:8px;background:#fff8ed;color:#a75b0d}.pause-warning .el-icon{flex:none;font-size:16px}.pause-description{margin:12px 0;color:#727999;font-size:12px;line-height:1.8}.pause-effects{margin:0}.pause-effects>div{display:flex;justify-content:space-between;align-items:center;gap:16px;border-top:1px solid #eceef7;padding:10px 0;font-size:12px}.pause-effects dt,.pause-effects dd{margin:0}.pause-effects dd{text-align:right}.pause-continues{display:inline-block;padding:3px 7px;border-radius:4px;color:#149570;background:#e6f7ef}.pause-tasks{margin-top:10px;padding:9px 11px;background:#fff8ed;border-radius:6px;color:#a75b0d;font-size:11px;line-height:1.8}.pause-tasks button{border:0;padding:0;margin-left:6px;background:none;color:#6650ff;font:inherit;cursor:pointer}.pause-tasks button:disabled{opacity:.5;cursor:not-allowed}.pause-error{margin:12px 0 0;color:#e34d59;font-size:12px}
</style>
<style>
.optimization-pause-dialog.el-dialog{padding:22px;border-radius:14px}.optimization-pause-dialog .el-dialog__header{padding:0 28px 18px 0;margin:0}.optimization-pause-dialog .el-dialog__title{font-size:18px;font-weight:600;color:#24263f}.optimization-pause-dialog .el-dialog__headerbtn{top:13px;right:12px}.optimization-pause-dialog .el-dialog__body{padding:0}.optimization-pause-dialog .el-dialog__footer{margin-top:18px;padding:15px 0 0;border-top:1px solid #eceef7}.optimization-pause-dialog .el-button{border-radius:7px;font-size:12px}
</style>
