<template>
  <el-button v-if="options.length || order" class="recharge-button" type="primary" plain @click="dialog = true">充值积分</el-button>
  <el-dialog v-model="dialog" title="充值积分" width="min(440px, 92vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @closed="reset">
    <div v-if="!order" class="options">
      <el-radio v-for="option in options" :key="option.code" v-model="selected" :value="option.code" :disabled="busy" border>
        ¥{{ number(option.priceFen / 100) }} · {{ number(option.points) }} 积分
      </el-radio>
      <el-radio v-model="selected" value="custom" :disabled="busy" border>自定义金额</el-radio>
      <template v-if="selected === 'custom'">
        <div class="custom-amount">
          <el-input v-model="amount" aria-label="自定义充值金额" inputmode="decimal" placeholder="最低 ¥99" :disabled="busy" @input="clearQuote" @keyup.enter="calculate">
            <template #prepend>¥</template>
          </el-input>
          <el-button :disabled="!amount" :loading="busy" @click="calculate">计算积分</el-button>
        </div>
        <p v-if="amountError" class="amount-error" role="alert">{{ amountError }}</p>
        <p v-else-if="customQuote" class="custom-points" aria-live="polite">到账 {{ number(customQuote.points) }} 积分</p>
      </template>
    </div>
    <div v-else class="checkout">
      <p>应付 ¥{{ order.payableYuan }}</p>
      <p v-if="order.discountLabel">{{ order.discountLabel }}</p>
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
      <el-button v-if="!payment" type="primary" :disabled="!order && !selectedOption" :loading="busy" @click="checkout">{{ order ? '继续支付' : '去支付' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { alipayCheckoutUrl, checkOrderPayment, closeRecharge, createPayment, createRecharge, mockComplete, pendingRecharge, quoteRecharge, rechargeOptions, rechargeStatus, type OrderView, type PaymentView, type RechargeOption } from '@/features/billing/billingApi';
const emit = defineEmits<{ paid: [] }>();
const options = ref<RechargeOption[]>([]), order = ref<OrderView>(), payment = ref<PaymentView>();
const dialog = ref(false), busy = ref(false), selected = ref(''), qr = ref('');
const amount = ref(''), amountError = ref(''), customQuote = ref<RechargeOption>();
let requestKey: string | undefined;
let requestCode: string | undefined;
const alipayUrl = computed(() => alipayCheckoutUrl(payment.value));
const selectedOption = computed(() => selected.value === 'custom' ? customQuote.value : options.value.find(item => item.code === selected.value));
const number = (value: number) => value.toLocaleString('zh-CN');
function clearQuote() {
  customQuote.value = undefined;
  amountError.value = '';
}
async function calculate() {
  if (busy.value) return;
  clearQuote();
  const match = /^(\d{1,8})(?:\.(\d{1,2}))?$/.exec(amount.value.trim());
  const fen = match ? Number(match[1]) * 100 + Number((match[2] ?? '').padEnd(2, '0')) : 0;
  if (fen < 9900 || fen > 2147483647) {
    amountError.value = '请输入至少 ¥99 的有效金额，最多保留两位小数';
    return;
  }
  busy.value = true;
  try { customQuote.value = await quoteRecharge(fen); }
  catch { /* API shows the failure. */ } finally { busy.value = false; }
}
function reset() {
  payment.value = undefined;
  qr.value = '';
}
function clearOrder() {
  order.value = undefined;
  requestKey = undefined;
  requestCode = undefined;
  reset();
}
async function checkout() {
  const code = selectedOption.value?.code;
  if (busy.value || (!code && !order.value)) return;
  busy.value = true;
  try {
    if (!order.value && code) {
      if (requestCode !== code) requestKey = undefined;
      requestCode = code;
      requestKey ??= crypto.randomUUID();
      order.value = await createRecharge(code, requestKey);
    }
    if (!order.value) return;
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
.custom-amount{display:flex;gap:8px}.amount-error{color:var(--el-color-danger);margin:0}.custom-points{margin:0}
</style>
