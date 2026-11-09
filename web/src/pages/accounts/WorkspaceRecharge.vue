<template>
  <el-button v-if="options.length" class="recharge-button" type="primary" plain @click="dialog = true">充值积分</el-button>
  <el-dialog v-model="dialog" title="充值积分" width="min(440px, 92vw)" :close-on-click-modal="false" @closed="reset">
    <div v-if="!payment" class="options">
      <el-radio v-for="option in options" :key="option.code" v-model="selected" :value="option.code" border :disabled="!!order">
        ¥{{ option.priceFen / 100 }} · {{ option.points.toLocaleString('zh-CN') }} 积分
      </el-radio>
    </div>
    <div v-else class="checkout">
      <template v-if="payment.codeUrl">
        <img v-if="qr" :src="qr" width="220" height="220" alt="微信支付二维码" />
        <p>请使用微信扫码支付 ¥{{ order?.payableYuan }}</p>
      </template>
      <el-button v-else-if="payment.mockReady" :loading="busy" @click="completeMock">确认演示支付</el-button>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="dialog = false">关闭</el-button>
      <el-button v-if="payment && !payment.mockReady" type="primary" :loading="busy" @click="check">查询支付结果</el-button>
      <el-button v-else-if="!payment" type="primary" :disabled="!selected" :loading="busy" @click="checkout">去支付</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { createPayment, createRecharge, mockComplete, rechargeOptions, rechargeStatus, type OrderView, type PaymentView, type RechargeOption } from '@/features/billing/billingApi';
const emit = defineEmits<{ paid: [] }>();
const options = ref<RechargeOption[]>([]), order = ref<OrderView>(), payment = ref<PaymentView>();
const dialog = ref(false), busy = ref(false), selected = ref(''), qr = ref('');
let requestKey: string | undefined;
function reset() { order.value = undefined; payment.value = undefined; qr.value = ''; requestKey = undefined; }
async function checkout() {
  if (busy.value || !selected.value) return;
  busy.value = true;
  try {
    requestKey ??= crypto.randomUUID();
    order.value ??= await createRecharge(selected.value, requestKey);
    const result = await createPayment(order.value.orderId);
    if (result.codeUrl) qr.value = await (await import('qrcode')).toDataURL(result.codeUrl, { width: 256 });
    payment.value = result;
  } catch { /* API shows the failure. */ } finally { busy.value = false; }
}
async function refreshStatus() {
  if (!order.value) return;
  if ((await rechargeStatus(order.value.orderId)).status === 'PAID') {
    dialog.value = false; emit('paid'); ElMessage.success('充值成功');
  } else ElMessage.info('尚未收到支付结果');
}
async function check() {
  busy.value = true;
  try { await refreshStatus(); } catch { /* API shows the failure. */ } finally { busy.value = false; }
}
async function completeMock() {
  if (!payment.value?.mockNonce) return;
  busy.value = true;
  try { await mockComplete(payment.value.paymentId, true, payment.value.mockNonce); await refreshStatus(); }
  catch { /* API shows the failure. */ } finally { busy.value = false; }
}
onMounted(async () => { try { options.value = await rechargeOptions(); selected.value = options.value[0]?.code || ''; } catch { /* API shows the failure. */ } });
</script>

<style scoped>
.recharge-button{margin-top:16px}.options{display:grid;gap:12px}.options :deep(.el-radio){margin:0}.checkout{text-align:center}
</style>
