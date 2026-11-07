<template>
  <div class="authorization-recovery">
    <div v-if="!canCheck" class="notice unavailable" role="alert">
      <el-icon><Warning /></el-icon>
      <div><strong>暂无法恢复：广告账户授权已失效</strong><p>目前无法获取最新投放状态，自动保护暂不生效。</p></div>
    </div>
    <section class="recovery-card">
      <h3>恢复前检查</h3>
      <dl>
        <div><dt>账户访问权限</dt><dd><span :class="canCheck ? 'pending' : 'unavailable'">{{ canCheck ? '待核对' : '需重新授权' }}</span></dd></div>
        <div><dt>最新消耗及广告计划状态</dt><dd><span class="pending">待更新</span></dd></div>
        <div><dt>已保存规则</dt><dd>保留</dd></div>
        <div><dt>自动保护权限</dt><dd><span class="limited">未生效</span></dd></div>
      </dl>
    </section>
    <section class="recovery-card">
      <h3>{{ canCheck ? '核对最新数据' : '先恢复账户访问' }}</h3>
      <p>授权完成后，先核对计划状态、当天消耗和在途任务，再由你确认恢复。旧建议已失效。</p>
      <div class="recovery-actions">
        <el-button type="primary" tag="a" :href="authorizationUrl" target="_blank" rel="noopener" @click="emit('authorize')">重新授权账户</el-button>
        <el-button :disabled="!canCheck" :loading="checking" @click="emit('check')">{{ canCheck ? '重新检查' : '待重新授权后检查' }}</el-button>
      </div>
      <p v-if="error" class="recovery-error" role="alert">{{ error }}</p>
    </section>
    <div v-if="!canCheck" class="notice limited">
      <el-icon><InfoFilled /></el-icon>
      <div><strong>基础异常检查当前受限</strong><p>暂时无法核实广告状态，可前往千川查看。</p></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { InfoFilled, Warning } from '@element-plus/icons-vue';

defineProps<{ authorizationUrl: string; canCheck: boolean; checking: boolean; error: string }>();
const emit = defineEmits<{ authorize: []; check: [] }>();
</script>

<style scoped>
.authorization-recovery { display: grid; gap: 10px; font-size: 13px; }
.notice { display: flex; gap: 8px; align-items: flex-start; padding: 10px 12px; border: 1px solid; border-radius: 8px; }
.notice .el-icon { margin-top: 2px; }
.unavailable { background: #fef3f3; color: #b52d2c; }
.notice.unavailable { border-color: #f8d5d2; }
.limited { background: #fff6e9; color: #925408; }
.notice.limited { border-color: #f7d9ad; }
.pending { background: #f0f1f8; color: #59607e; }
.notice p { margin: 4px 0 0; }
.recovery-card { padding: 12px; border: 1px solid #e3e7f6; border-radius: 8px; }
h3 { margin: 0 0 8px; font-size: 14px; }
p { margin: 0; color: #687194; font-size: 12px; line-height: 1.65; }
dl { margin: 0; }
dl > div { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0; }
dl > div + div { border-top: 1px solid #eef0fa; }
dd { margin: 0; white-space: nowrap; }
dd span { padding: 3px 7px; border-radius: 4px; font-size: 11px; }
.recovery-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px; }
.recovery-actions .el-button { margin: 0; }
.recovery-error { color: #b52d2c; margin-top: 8px; }
</style>
