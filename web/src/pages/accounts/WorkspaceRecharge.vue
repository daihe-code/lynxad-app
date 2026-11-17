<template>
  <el-button v-if="options.length || order" class="recharge-button" type="primary" plain @click="dialog = true">充值积分</el-button>
  <el-dialog v-model="dialog" title="充值积分" width="min(440px, 92vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @closed="reset">
    <div v-if="!order" class="options">
      <el-radio v-for="option in options" :key="option.code" v-model="selected" :value="option.code" border>
        ¥{{ option.priceFen / 100 }} · {{ option.points.toLocaleString('zh-CN') }} 积分
      </el-radio>
    </div>
    <div v-else class="checkout">
      <p>应付 ¥{{ order.payableYuan }}</p>
      <a v-if="alipayUrl" class="btn-modal-primary account-alipay-link" :href="alipayUrl" target="_blank" rel="noopener noreferrer">前往支付宝扫码</a>
      <p v-else-if="payment?.channel === 'ALIPAY'">支付链接不可用，请查询订单状态。</p>
      <template v-else-if="qr">
        <img :src="qr" width="220" height="220" alt="微信支付二维码" />
        <p>请使用微信扫码支付</p>
      </template>
      <el-button v-else-if="payment?.mockReady" :loading="busy" @click="completeMock">确认演示支付</el-button>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="dialog = false">关闭</el-button>
      <el-button v-if="order" :disabled="busy" @click="cancel">取消订单</el-button>
      <el-button v-if="order && !payment?.mockReady" :disabled="busy" @click="check">查询支付结果</el-button>
      <el-button v-if="!payment" type="primary" :disabled="!order && !selected" :loading="busy" @click="checkout">{{ order ? '继续支付' : '去支付' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { alipayCheckoutUrl, checkOrderPayment, closeRecharge, createPayment, createRecharge, mockComplete, pendingRecharge, rechargeOptions, rechargeStatus, type OrderView, type PaymentView, type RechargeOption } from '@/features/billing/billingApi';
const emit = defineEmits<{ paid: [] }>();
const options = ref<RechargeOption[]>([]), order = ref<OrderView>(), payment = ref<PaymentView>();
const dialog = ref(false), busy = ref(false), selected = ref(''), qr = ref('');
let requestKey: string | undefined;
const alipayUrl = computed(() => alipayCheckoutUrl(payment.value));
function reset() {
  payment.value = undefined;
  qr.value = '';
}
function clearOrder() {
  order.value = undefined;
  requestKey = undefined;
  reset();
}
async function checkout() {
  if (busy.value || (!selected.value && !order.value)) return;
  busy.value = true;
  try {
    requestKey ??= crypto.randomUUID();
    order.value ??= await createRecharge(selected.value, requestKey);
    const result = await createPayment(order.value.orderId);
    if (result.channel === 'WECHAT' && result.codeUrl?.startsWith('weixin://wxpay/')) {
      qr.value = await (await import('qrcode')).toDataURL(result.codeUrl, { width: 256 });
    }
    payment.value = result;
  } catch { /* API shows the failure. */ } finally { busy.value = false; }
}
function handleStatus(updated: OrderView) {
  if (updated.status === 'PAID') {
    clearOrder();
    dialog.value = false;
    emit('paid');
    ElMessage.success('充值成功');
  } else if (updated.status === 'CLOSED') {
    clearOrder();
    ElMessage.info('订单已关闭');
  } else {
    ElMessage.info('尚未收到支付结果');
  }
}
async function check() {
  if (!order.value || busy.value) return;
  busy.value = true;
  try { handleStatus(await checkOrderPayment(order.value.orderId)); }
  catch { /* API shows the failure. */ } finally { busy.value = false; }
}
async function cancel() {
  const id = order.value?.orderId;
  if (!id || busy.value) return;
  try { await ElMessageBox.confirm('确定取消这笔积分充值订单？', '取消订单', { type: 'warning' }); }
  catch { return; }
  busy.value = true;
  try { handleStatus(await closeRecharge(id)); }
  catch { /* API shows the failure. */ } finally { busy.value = false; }
}
async function completeMock() {
  if (!payment.value?.mockNonce || !order.value) return;
  busy.value = true;
  try {
    await mockComplete(payment.value.paymentId, true, payment.value.mockNonce);
    handleStatus(await rechargeStatus(order.value.orderId));
  }
  catch { /* API shows the failure. */ } finally { busy.value = false; }
}
onMounted(async () => {
  try {
    const [available, pending] = await Promise.all([rechargeOptions(), pendingRecharge()]);
    options.value = available;
    selected.value = options.value[0]?.code || '';
    order.value = pending ?? undefined;
  } catch { /* API shows the failure. */ }
});
</script>

<style scoped>
.recharge-button{margin-top:16px}.options{display:grid;gap:12px}.options :deep(.el-radio){margin:0}.checkout{text-align:center}
.account-alipay-link{display:inline-flex;margin-top:12px;text-decoration:none}
</style>
