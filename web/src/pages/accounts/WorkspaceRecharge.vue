<template>
  <section class="acct-card recharge-page" v-loading="loading">
    <div v-if="balance != null" class="recharge-balance">
      <span>可用积分</span>
      <strong>{{ number(balance) }}</strong>
    </div>
    <div v-if="loadError" class="recharge-state">
      <p class="amount-error" role="alert">{{ loadError }}</p>
      <el-button @click="load">重新加载</el-button>
    </div>
    <div v-else-if="!loading && availability !== 'AVAILABLE' && !order" class="recharge-state">
      <p>{{ availability === 'PAID_SUBSCRIPTION_REQUIRED' ? '购买并开通套餐后可充值积分。' : '积分充值暂未开放。' }}</p>
      <router-link v-if="availability === 'PAID_SUBSCRIPTION_REQUIRED'" to="/subscription" class="obtn obtn--primary">查看套餐</router-link>
    </div>
    <template v-else-if="!loading && !order">
      <h2 id="recharge-amount" class="acct-card__t">选择充值金额</h2>
      <el-radio-group v-model="selected" class="options" aria-labelledby="recharge-amount" :disabled="busy">
        <el-radio v-for="option in options" :key="option.code" :value="option.code" border>
          <span class="option-amount">¥{{ number(option.priceFen / 100) }}</span>
          <span class="option-points">{{ number(option.points) }} 积分</span>
        </el-radio>
        <el-radio value="custom" border>自定义金额</el-radio>
      </el-radio-group>
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
      <div class="recharge-actions">
        <el-button type="primary" :disabled="!selectedOption" :loading="busy" @click="checkout">
          {{ selectedOption ? `支付 ¥${number(selectedOption.priceFen / 100)}` : '去支付' }}
        </el-button>
      </div>
    </template>
    <div v-else-if="!loading && order" class="checkout">
      <h2 class="acct-card__t">待支付充值订单</h2>
      <p>应付 ¥{{ order.payableYuan }}</p>
      <p v-if="order.discountLabel">{{ order.discountLabel }}</p>
      <template v-if="qr">
        <img :src="qr" width="220" height="220" alt="微信支付二维码" />
        <p>请使用微信扫码支付</p>
      </template>
      <el-button v-else-if="payment?.mockReady" :loading="busy" @click="completeMock">确认演示支付</el-button>
      <p v-if="availability === 'PAID_SUBSCRIPTION_REQUIRED'">恢复有效的付费套餐后可继续支付。</p>
      <p v-else-if="availability === 'DISABLED'">积分充值暂未开放。</p>
      <div class="recharge-actions">
        <el-button :disabled="busy" @click="cancel">取消订单</el-button>
        <el-button v-if="!payment?.mockReady" :disabled="busy" @click="check">查询支付结果</el-button>
        <el-button v-if="!payment && availability === 'AVAILABLE'" type="primary" :loading="busy" @click="checkout">继续支付</el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { requestFailure } from '@/api/apiErrors';
import { workspaceOverview } from '@/features/tenant/workspaceApi';
import { alipayCheckoutUrl, checkOrderPayment, closeRecharge, createPayment, createRecharge, getRechargeCatalog, mockComplete, quoteRecharge, rechargeStatus, type OrderView, type PaymentView, type RechargeCatalog, type RechargeOption } from '@/features/billing/billingApi';
const router = useRouter();
const options = ref<RechargeOption[]>([]), order = ref<OrderView>(), payment = ref<PaymentView>();
const busy = ref(false), selected = ref(''), qr = ref('');
const availability = ref<RechargeCatalog['availability']>(), balance = ref<number>();
const loading = ref(true), loadError = ref('');
const amount = ref(''), amountError = ref(''), customQuote = ref<RechargeOption>();
let requestKey: string | undefined;
let requestCode: string | undefined;
let active = true;
const selectedOption = computed(() => selected.value === 'custom' ? customQuote.value : options.value.find(item => item.code === selected.value));
const number = (value: number) => value.toLocaleString('zh-CN', { maximumFractionDigits: 4 });
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
  if (busy.value || availability.value !== 'AVAILABLE' || (!code && !order.value)) return;
  busy.value = true;
  try {
    if (!order.value && code) {
      if (requestCode !== code) requestKey = undefined;
      requestCode = code;
      requestKey ??= crypto.randomUUID();
      order.value = await createRecharge(code, requestKey);
    }
    if (!order.value || !active) return;
    const result = await createPayment(order.value.orderId);
    if (!active) return;
    if (result.channel === 'ALIPAY') {
      const checkoutUrl = alipayCheckoutUrl(result);
      if (checkoutUrl) window.location.assign(checkoutUrl);
      else await router.push({ name: 'payment-result', query: { order: order.value.orderId, kind: 'recharge' } });
      return;
    }
    if (result.channel === 'WECHAT' && result.codeUrl?.startsWith('weixin://wxpay/')) {
      qr.value = await (await import('qrcode')).toDataURL(result.codeUrl, { width: 256 });
    }
    payment.value = result;
  } catch { /* API shows the failure. */ } finally { busy.value = false; }
}
function handleStatus(updated: OrderView) {
  if (updated.status === 'PAID') {
    clearOrder();
    void refreshBalance();
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
  if (!active || busy.value || order.value?.orderId !== id) return;
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
async function refreshBalance() {
  try {
    const overview = await workspaceOverview();
    if (active) balance.value = overview.wallet?.available;
  } catch { /* The checkout can still show the server catalog. */ }
}
async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    const catalog = await getRechargeCatalog();
    if (!active) return;
    availability.value = catalog.availability;
    options.value = catalog.options;
    selected.value = options.value[0]?.code || '';
    order.value = catalog.pending ?? undefined;
  } catch (error) {
    loadError.value = requestFailure(error).message;
  } finally {
    loading.value = false;
  }
}
onMounted(() => {
  void load();
  void refreshBalance();
});
onBeforeUnmount(() => { active = false; });
</script>

<style scoped>
.recharge-page{min-height:155px}.recharge-balance{display:flex;align-items:baseline;gap:14px;padding:16px 20px;margin-bottom:24px;border-radius:12px;background:#f4f2ff}
.recharge-balance strong{color:#5149d7;font-size:24px}.options{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px;margin:16px 0}
.options :deep(.el-radio){height:auto;min-height:68px;margin:0;padding:10px 12px}.options :deep(.el-radio__label){white-space:normal}
.option-amount,.option-points{display:block}.option-amount{font-weight:600}.option-points{margin-top:4px;color:var(--el-text-color-secondary)}
.recharge-state,.checkout{display:grid;gap:12px;justify-items:start}.recharge-state p,.checkout p,.custom-points{margin:0}
.recharge-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:12px}.recharge-actions :deep(.el-button){margin-left:0}
.custom-amount{display:flex;gap:8px;max-width:420px}.amount-error{color:var(--el-color-danger);margin:8px 0 0}
</style>
