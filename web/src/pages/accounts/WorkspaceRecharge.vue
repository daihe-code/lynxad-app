<template>
  <div class="recharge-page" v-loading="loading">
    <section v-if="balance != null" class="acct-card recharge-balance" aria-label="工作空间积分余额">
      <span>可用积分</span>
      <strong>{{ number(balance) }}</strong>
      <span class="balance-scope">主账户和子账户共享</span>
    </section>
    <section v-if="loadError" class="acct-card recharge-state">
      <p class="amount-error" role="alert">{{ loadError }}</p>
      <el-button @click="load">重新加载</el-button>
    </section>
    <section v-if="!loading && !loadError && order" class="acct-card checkout">
      <h2 class="acct-card__t">待支付充值订单</h2>
      <p>应付 ¥{{ number(order.payableYuan) }}</p>
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
    </section>
    <section v-if="!loadError && options.length" class="acct-card" aria-labelledby="recharge-amount">
      <h2 id="recharge-amount" class="acct-card__t">充值套餐</h2>
      <div class="recharge-grid">
        <div class="recharge-options">
          <el-radio-group v-model="selected" class="options" aria-labelledby="recharge-amount" :disabled="busy">
            <el-radio v-for="option in options" :key="option.code" :value="option.code" border>
              <span class="option-amount">¥{{ number(option.priceFen / 100) }}</span>
              <span class="option-points">{{ number(option.points) }} 积分</span>
              <span class="option-rate">约 {{ pointsPerHundred(option) }} 积分 / ¥100</span>
            </el-radio>
            <el-radio value="custom" border>
              <span class="option-amount option-custom">自定义金额</span>
              <span class="option-rate">¥99 起</span>
            </el-radio>
          </el-radio-group>
          <template v-if="selected === 'custom'">
            <div class="custom-amount">
              <el-input v-model="amount" class="recharge-amount-input" size="large" aria-label="自定义充值金额" :aria-invalid="!!amountError" :aria-describedby="amountError ? 'recharge-amount-error' : undefined"
                inputmode="decimal" placeholder="最低 ¥99" :disabled="busy" @input="clearQuote" @keyup.enter="calculate">
                <template #prepend>¥</template>
              </el-input>
              <el-button class="recharge-calculate" type="primary" size="large" :disabled="!amount || busy" :loading="quoting" @click="calculate">计算积分</el-button>
            </div>
            <p v-if="amountError" id="recharge-amount-error" class="amount-error" role="alert">{{ amountError }}</p>
          </template>
        </div>
        <div class="recharge-summary">
          <h3>订单预览</h3>
          <dl aria-live="polite">
            <div><dt>应付金额</dt><dd>{{ selectedOption ? `¥${number(selectedOption.priceFen / 100)}` : '—' }}</dd></div>
            <div><dt>到账积分</dt><dd class="summary-points">{{ selectedOption ? number(selectedOption.points) : '—' }}</dd></div>
          </dl>
          <div class="summary-action">
            <p v-if="order" class="payment-note">请先处理上方的待支付订单。</p>
            <p v-else-if="availability === 'PAID_SUBSCRIPTION_REQUIRED'" class="payment-note">有生效的实付订阅后可支付。</p>
            <el-button type="primary" :disabled="!selectedOption || !!order || availability !== 'AVAILABLE' || quoting"
              :loading="busy && !quoting && !order" @click="checkout">去支付</el-button>
          </div>
        </div>
      </div>
    </section>
    <section v-else-if="!loading && !loadError && !order && availability === 'DISABLED'" class="acct-card recharge-state">
      <p>积分充值暂未开放。</p>
    </section>
    <section class="acct-card recharge-history">
      <h2 class="acct-card__t">充值记录</h2>
      <PointLedgerTable :fetch-page="workspacePointHistory" :refresh-key="ledgerRefresh" show-date-filter recharge-only />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { requestFailure } from '@/api/apiErrors';
import PointLedgerTable from '@/components/PointLedgerTable.vue';
import { workspaceOverview, workspacePointHistory } from '@/features/tenant/workspaceApi';
import { alipayCheckoutUrl, checkOrderPayment, closeRecharge, createPayment, createRecharge, getRechargeCatalog, mockComplete, quoteRecharge, rechargeStatus, type OrderView, type PaymentView, type RechargeCatalog, type RechargeOption } from '@/features/billing/billingApi';
const router = useRouter();
const options = ref<RechargeOption[]>([]), order = ref<OrderView>(), payment = ref<PaymentView>();
const busy = ref(false), selected = ref(''), qr = ref('');
const availability = ref<RechargeCatalog['availability']>(), balance = ref<number>();
const loading = ref(true), loadError = ref('');
const amount = ref(''), amountError = ref(''), customQuote = ref<RechargeOption>();
const quoting = ref(false), ledgerRefresh = ref(0);
let requestKey: string | undefined;
let requestCode: string | undefined;
let active = true;
const selectedOption = computed(() => selected.value === 'custom' ? customQuote.value : options.value.find(item => item.code === selected.value));
const number = (value: number) => value.toLocaleString('zh-CN', { maximumFractionDigits: 4 });
const pointsPerHundred = (option: RechargeOption) => (option.points * 10_000 / option.priceFen)
  .toLocaleString('zh-CN', { maximumFractionDigits: 0 });
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
  quoting.value = busy.value = true;
  try { customQuote.value = await quoteRecharge(fen); }
  catch (error) { amountError.value = requestFailure(error).message; }
  finally { quoting.value = busy.value = false; }
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
    ledgerRefresh.value++;
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
.recharge-page { min-height: 155px; }
.recharge-page .recharge-balance {
  display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px 14px;
  padding-block: 18px; background: rgb(var(--c-primary-soft) / .65);
}
.recharge-balance strong { color: rgb(var(--c-primary-ink)); font-size: 26px; font-variant-numeric: tabular-nums; }
.balance-scope { margin-left: auto; color: var(--el-text-color-secondary); font-size: 13px; }
.recharge-grid { display: grid; gap: 20px; margin-top: 20px; }
.options { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); align-items: stretch; gap: 12px; }
.options :deep(.el-radio) {
  display: flex; align-items: flex-start; height: auto; min-height: 100px; margin: 0; padding: 13px 11px;
  border-radius: 12px; white-space: normal;
}
.options :deep(.el-radio.is-checked) { background: rgb(var(--c-primary-soft) / .55); }
.options :deep(.el-radio__label) { min-width: 0; padding-left: 7px; overflow-wrap: anywhere; }
.option-amount, .option-points, .option-rate { display: block; }
.option-amount { font-size: 18px; font-weight: 700; line-height: 1.2; }
.option-custom { font-size: 16px; }
.option-points { margin-top: 7px; font-weight: 600; }
.option-rate { margin-top: 5px; font-size: 12px; color: var(--el-text-color-secondary); }
.recharge-summary {
  display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: end; gap: 12px 20px;
  padding: 16px 20px; border: 1px solid var(--el-border-color-lighter); border-radius: 14px; background: #f8f7fe;
}
.recharge-summary h3 { grid-column: 1 / -1; margin: 0; font-size: 15px; font-weight: 700; }
.recharge-summary dl { display: flex; flex-wrap: wrap; gap: 12px 36px; margin: 0; }
.recharge-summary dl div { display: grid; gap: 4px; }
.recharge-summary dt, .payment-note { color: var(--el-text-color-secondary); font-size: 13px; }
.recharge-summary dd { margin: 0; font-size: 19px; font-weight: 700; font-variant-numeric: tabular-nums; }
.summary-points { color: rgb(var(--c-primary-ink)); }
.summary-action { display: grid; justify-items: end; gap: 8px; }
.summary-action .el-button { min-width: 140px; }
.payment-note { margin: 0; line-height: 1.5; }
.recharge-state, .checkout { display: grid; gap: 12px; justify-items: start; }
.recharge-state p, .checkout p { margin: 0; }
.recharge-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.recharge-actions :deep(.el-button) { margin-left: 0; }
.custom-amount { display: flex; gap: 8px; max-width: 420px; margin-top: 14px; }
.recharge-amount-input { min-width: 0; --el-input-focus-border-color: #78829b; }
.recharge-amount-input :deep(.el-input__inner:focus-visible) { outline: none; box-shadow: none; }
.recharge-calculate {
  padding-inline: 16px; border-radius: 10px; font-size: 14px; font-weight: 600;
  background-color: var(--el-button-bg-color); color: var(--el-button-text-color);
}
.recharge-calculate:not(:disabled):hover { background-color: var(--el-button-hover-bg-color); }
.recharge-calculate:disabled { background-color: var(--el-button-disabled-bg-color); }
.amount-error { color: var(--el-color-danger); margin: 8px 0 0; font-size: 13px; }
.recharge-history h2 { margin-bottom: 16px; }
.recharge-history :deep(.el-date-editor) { flex: 0 1 280px; }
@media (max-width: 740px) {
  .options { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .balance-scope { margin-left: 0; }
}
@media (max-width: 520px) {
  .recharge-summary { grid-template-columns: minmax(0, 1fr); }
  .summary-action { justify-items: stretch; }
}
</style>
