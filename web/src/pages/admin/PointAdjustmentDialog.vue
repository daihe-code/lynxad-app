<template>
  <el-dialog :model-value="true" title="调整积分" width="min(480px, 95vw)" append-to-body
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @close="emit('close')">
    <p class="target">{{ target.tenantName || target.tenantId }} · {{ target.nickname || target.phone }}</p>
    <p class="target-note">同一坤境账户下的成员共享积分。</p>
    <div v-if="wallet" class="balance">
      <div>可用积分 <strong>{{ points(wallet.available) }}</strong></div>
      <div>预占积分 <strong>{{ points(wallet.frozen) }}</strong></div>
    </div>
    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError" />
    <el-button v-if="loadError" type="primary" link :loading="loading" @click="load">重新加载余额</el-button>
    <el-form v-else v-loading="loading" label-position="top" :disabled="saving || loading || !wallet" @submit.prevent="save">
      <el-form-item label="调整方式">
        <el-radio-group v-model="direction" aria-label="调整方式">
          <el-radio-button value="ADD">增加积分</el-radio-button>
          <el-radio-button value="DEDUCT">扣减积分</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="调整数量" :error="amountError || saveError" required>
        <el-input v-model="amount" inputmode="decimal" maxlength="21" placeholder="输入积分数量" />
      </el-form-item>
      <el-form-item label="操作原因" :error="reasonError" required>
        <el-input v-model="reason" type="textarea" :rows="3" maxlength="300" placeholder="填写调整原因" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!wallet || loading || !!loadError || !!amountError || !!reasonError"
        @click="save">确认调整</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { AdminApiError, adjustAdminPoints, getAdminPointWallet, type AdjustPoints, type AdminPointWallet, type AdminUser } from '@/features/admin/adminApi';

const props = defineProps<{ target: AdminUser }>();
const emit = defineEmits<{ close: [] }>();
const wallet = ref<AdminPointWallet>();
const loading = ref(false), saving = ref(false), loadError = ref(''), saveError = ref('');
const direction = ref<AdjustPoints['direction']>('ADD'), amount = ref(''), reason = ref('');
const submitted = ref(false);
let requestId = crypto.randomUUID();
const points = (value: number) => value.toLocaleString('zh-CN', { maximumFractionDigits: 4 });
const message = (error: unknown) => error instanceof Error ? error.message : '积分服务暂不可用，请重试';
const amountError = computed(() => {
  if (!wallet.value) return '';
  const entered = amount.value.trim();
  if (!entered) return submitted.value ? '请输入积分数量' : '';
  const value = Number(entered);
  if (!/^(?:\d{1,16}(?:\.\d{1,4})?|\.\d{1,4})$/.test(entered) || value <= 0) return '请输入大于零的积分，最多四位小数';
  if (direction.value === 'DEDUCT' && value > wallet.value.available) return '扣减积分不能超过可用积分';
  return '';
});
const reasonError = computed(() => submitted.value && Array.from(reason.value.trim()).length < 2 ? '请填写至少2字的操作原因' : '');
watch([direction, amount, reason], () => { requestId = crypto.randomUUID(); saveError.value = ''; }, { flush: 'sync' });

async function load() {
  if (loading.value || !props.target.tenantId) return;
  loading.value = true;
  loadError.value = '';
  try {
    wallet.value = await getAdminPointWallet(props.target.id, props.target.tenantId);
    requestId = crypto.randomUUID();
  } catch (error) { loadError.value = message(error); }
  finally { loading.value = false; }
}

async function save() {
  if (saving.value || loading.value || loadError.value || !wallet.value || !props.target.tenantId) return;
  submitted.value = true;
  if (amountError.value || reasonError.value) return;
  saving.value = true;
  saveError.value = '';
  try {
    const updated = await adjustAdminPoints(props.target.id, {
      requestId, tenantId: props.target.tenantId, direction: direction.value, amount: amount.value.trim(),
      expectedVersion: wallet.value.version, reason: reason.value.trim(),
    });
    ElMessage.success(`积分已调整，当前可用 ${points(updated.available)} 积分`);
    emit('close');
  } catch (error) {
    if (error instanceof AdminApiError && error.code === 409) await load();
    saveError.value = message(error);
  } finally { saving.value = false; }
}
onMounted(load);
</script>

<style scoped>
.target { margin: 0 0 6px; font-weight: 600; }
.target-note { margin: 0 0 16px; color: #70788d; }
.balance { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.balance div { background: #f5f5fc; padding: 10px 12px; border-radius: 8px; }
.balance strong { display: block; margin-top: 4px; font-size: 18px; overflow-wrap: anywhere; }
</style>
