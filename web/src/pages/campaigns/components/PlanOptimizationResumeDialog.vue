<template>
  <el-dialog :model-value="modelValue"
    :title="autoPause ? '当前消耗已达到保护金额' : '恢复盯盘优化？'"
    width="min(540px, calc(100vw - 32px))" align-center append-to-body class="optimization-resume-dialog"
    :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy"
    @update:model-value="!busy && emit('update:modelValue', $event)">
    <div v-if="loading" class="resume-loading" role="status">正在核对当天消耗和计划状态…</div>
    <template v-else-if="preview">
      <div v-if="autoPause || remind" class="resume-warning">
        <el-icon><InfoFilled /></el-icon>
        <div><strong>{{ autoPause ? '恢复后将立即提交暂停本广告计划的请求' : '今日消耗已达保护金额，恢复后将提醒你处理' }}</strong><small v-if="autoPause">保存的处理方式：允许小灵自动暂停本计划</small></div>
      </div>
      <el-tag v-else size="small" :type="preview.planStatus === 'DELIVERING' ? 'success' : 'info'">
        广告计划：{{ planStatusDisplay(preview.planStatus).label }}
      </el-tag>
      <div class="resume-metrics">
        <div><small>今天已消耗</small><strong>{{ money(preview.spend) }}</strong></div>
        <div><small>当天消耗保护金额</small><strong>{{ money(preview.rules.spendProtection) }}</strong></div>
      </div>
      <div v-if="!autoPause && !remind" class="resume-rules">
        <div><small>目标ROI保护</small>净成交ROI不低于 {{ preview.rules.roiFloor }}</div>
        <div><small>消耗提醒 / 保护</small>{{ money(preview.rules.spendReminder) }} / {{ money(preview.rules.spendProtection) }}</div>
        <div><small>跑飞提醒</small>最近{{ preview.rules.runawayMinutes }}分钟消耗达到{{ money(preview.rules.runawaySpend) }}</div>
        <div><small>追加预算</small>每次{{ money(preview.rules.maxAddition) }} · 每日累计{{ money(preview.rules.dailyAddition) }}</div>
      </div>
      <div v-if="!autoPause" class="resume-method"><span>当前保存的处理方式</span><strong>{{ automatic ? '允许小灵自动暂停本计划' : '提醒我确认暂停' }}</strong></div>
      <p v-if="autoPause" class="resume-copy">请确认本次暂停，或先修改保护规则。提交后会继续核实暂停结果。</p>
      <p v-else-if="remind" class="resume-copy"><strong>本次只恢复盯盘优化，广告仍会继续投放并产生消耗。</strong><br>需要暂停广告时，由你单独确认。</p>
      <p v-else-if="preview.outcome === 'PLAN_NOT_DELIVERING'" class="resume-copy">仅恢复盯盘优化。恢复广告投放需单独确认。</p>
      <p v-else-if="automatic" class="resume-copy">当天消耗达到 {{ money(preview.rules.spendProtection) }} 后，将按已保存的授权暂停本计划。</p>
      <div v-if="autoPause && runningTaskCount" class="resume-warning">
        <span>{{ runningTaskCount }} 项独立调控任务仍在运行，暂停主计划不会自动停止这些任务。</span>
        <el-button link type="primary" :disabled="busy" @click="emit('tasks')">查看任务</el-button>
      </div>
      <div class="resume-note">
        当天消耗和追加额度继续累计。<template v-if="Number(preview.committed) > 0">当日已追加 {{ money(preview.committed) }}，计入每日 {{ money(preview.rules.dailyAddition) }} 额度。</template>
        <template v-if="remind">本次不变更自动执行授权。</template><template v-else-if="!autoPause">旧建议已失效，恢复后按最新数据重新判断。</template>
      </div>
      <el-button v-if="remind" link type="primary" :disabled="busy" @click="emit('edit')">先修改规则 ›</el-button>
      <small class="resume-time">数据截至 {{ fetchedAt }}</small>
    </template>
    <p v-if="error" class="resume-error" role="alert">{{ error }}</p>
    <template #footer>
      <el-button :disabled="busy" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="autoPause && !error" :disabled="busy" @click="emit('edit')">先修改规则</el-button>
      <el-button v-if="error" type="primary" @click="emit('refresh')">重新核对</el-button>
      <el-button v-else type="primary" :loading="busy" :disabled="loading || !preview" @click="emit('confirm')">
        {{ autoPause ? '明确确认：恢复盯盘并暂停本计划' : remind ? '恢复盯盘优化，仅提醒' : '确认恢复盯盘优化' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { InfoFilled } from '@element-plus/icons-vue';
import type { OptimizationResumePreview } from '@/features/delivery/optimizationApi';
import { planStatusDisplay } from '@/features/delivery/planStatus';

const props = defineProps<{ modelValue: boolean; preview?: OptimizationResumePreview; loading: boolean; busy: boolean; error?: string; runningTaskCount: number }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: []; refresh: []; edit: []; tasks: [] }>();
const autoPause = computed(() => props.preview?.outcome === 'LIMIT_PAUSE');
const remind = computed(() => props.preview?.outcome === 'LIMIT_REMINDER');
const automatic = computed(() => props.preview?.rules.protectionAction === 'AUTO_PAUSE');
const fetchedAt = computed(() => props.preview ? new Date(props.preview.fetchedAt).toLocaleString('zh-CN', {
  timeZone: 'Asia/Shanghai', hour12: false,
}) : '');
const amount = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', currencyDisplay: 'narrowSymbol' });
const money = (value: string | number) => amount.format(Number(value));
</script>

<style scoped>
.resume-loading,.resume-copy,.resume-time{color:#68708e;font-size:12px;line-height:1.7}
.resume-copy{margin:10px 0}
.resume-warning{display:flex;align-items:center;gap:8px;padding:10px 12px;border:1px solid #ffdbaf;border-radius:8px;background:#fff8ed;color:#985411;font-size:13px}
.resume-warning .el-icon{flex:none}
.resume-warning small{display:block;margin-top:4px;color:#68708e}
.resume-method{display:flex;justify-content:space-between;gap:8px;align-items:center;padding:14px 12px;border:1px solid #e6e7f4;border-radius:8px;color:#24263f;font-size:12px}
.resume-metrics,.resume-rules{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:10px 0}
.resume-metrics>div,.resume-rules>div,.resume-note{padding:10px 12px;background:#f5f5fc;border-radius:8px;color:#24263f;font-size:12px}
.resume-metrics small,.resume-rules small{display:block;color:#68708e;margin-bottom:4px}
.resume-metrics strong{font-size:20px}
.resume-note{margin:12px 0 8px;background:#f2efff;line-height:1.7}
.resume-time{display:block;margin-top:8px}
.resume-error{color:#d53d50;font-size:13px}
</style>
<style>
.optimization-resume-dialog.el-dialog{padding:22px;border-radius:14px}
.optimization-resume-dialog .el-dialog__header{padding:0 28px 16px 0;margin:0}
.optimization-resume-dialog .el-dialog__title{font-size:18px;font-weight:600;color:#24263f}
.optimization-resume-dialog .el-dialog__headerbtn{top:13px;right:12px}
.optimization-resume-dialog .el-dialog__body{padding:0}
.optimization-resume-dialog .el-dialog__footer{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:8px;margin-top:16px;padding:14px 0 0;border-top:1px solid #eceef7}
.optimization-resume-dialog .el-button+.el-button{margin-left:0}
</style>
