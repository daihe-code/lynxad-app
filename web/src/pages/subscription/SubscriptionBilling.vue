<template>
  <div v-if="loading" class="acct-card nodata" role="status">正在加载订阅信息…</div>
  <div v-else-if="loadError" class="acct-card nodata"><p class="account-error">{{ loadError }}</p><button class="obtn mt-[18px]" @click="reload">重新加载</button></div>
  <template v-else>
    <section class="acct-card">
      <div class="acct-card__hd"><h2 class="acct-card__t">当前订阅</h2><button class="acct-card__a" @click="scrollPlans">变更套餐 <AccountIcon name="arrow-right" /></button></div>
      <div class="acct-card__b planhero"><p class="planhero__en">CURRENT PLAN</p><p class="planhero__t">{{ sub?.planName || '—' }} {{ englishName(sub?.planCode) }}</p><p class="planhero__p"><span class="planhero__num">{{ money(sub?.listPriceYuan) }}</span><span class="planhero__unit">/ 月</span><span class="planhero__off">{{ !sub?.hasPaidSubscription ? trialLabel : sub?.discountLabel }}</span></p><div class="planhero__m"><div><p class="planhero__k">{{ sub?.hasPaidSubscription ? '计费周期' : '试用到期' }}</p><p class="planhero__v">{{ sub?.hasPaidSubscription ? `${date(sub.periodStart)} ~ ${date(sub.periodEnd)}` : date(user.me?.trialExpiresAt) }}</p></div><div><p class="planhero__k">剩余天数</p><p class="planhero__v">{{ sub?.remainingDays ?? 0 }} 天</p></div><div><p class="planhero__k">自动续费</p><p class="planhero__v"><button v-if="sub?.hasPaidSubscription" class="account-renew" :disabled="renewing" @click="toggleRenew">{{ renewing ? '更新中…' : sub.autoRenew ? '已开启' : '已关闭' }} <AccountIcon name="caret" /></button><span v-else>试用期不扣费</span></p></div><div><p class="planhero__k">支付方式</p><p class="planhero__v">{{ payMethod(sub?.payMethod) }}</p></div></div></div>
      <div v-if="pendingOrder" class="account-pending-order"><p>有一笔待支付订单，应付 {{ money(pendingOrder.payableYuan) }}</p><div><button class="obtn obtn--primary" :disabled="busy" @click="resumePayment">继续支付</button><button class="obtn" :disabled="busy" @click="cancelOpen = true">取消订单</button></div></div>
    </section>
    <section ref="plansRef" class="acct-card"><h2 class="acct-card__t">套餐方案对比</h2><div class="acct-card__b plancards"><article v-for="p in plans" :key="p.code" class="plancard" :class="{ 'plancard--cur': p.current }"><span v-if="p.current" class="plancard__tag">当前套餐</span><h3 class="plancard__t">{{ p.name }}</h3><p class="plancard__p"><span class="plancard__num">{{ money(p.listPriceYuan) }}</span><span class="plancard__unit">/月</span></p><p class="plancard__d">{{ p.suitableFor }}</p><ul class="plancard__ul"><li v-for="benefit in p.benefits" :key="benefit" class="plancard__li"><AccountIcon name="check" /><span>{{ benefit }}</span></li></ul><button class="planbtn" :class="p.action === 'CURRENT' ? 'planbtn--cur' : p.action === 'DOWNGRADE' ? 'planbtn--ghost' : 'planbtn--primary'" :disabled="p.action === 'CURRENT' || busy" @click="onPlan(p)">{{ actionLabel(p) }}</button></article></div><p class="acct-hint">套餐按月计费，升级差价以订单结算为准；降级于当前计费周期结束后生效。</p></section>
    <section class="acct-card"><div class="acct-card__hd"><h2 class="acct-card__t">账单记录</h2><button class="ltool__btn" :disabled="!bills.length" @click="exportCsv"><AccountIcon name="download" /> 导出账单</button></div><div class="acct-card__b overflow-x-auto"><table class="btable"><thead><tr><th scope="col">账单日期</th><th scope="col">套餐项目</th><th scope="col">金额</th><th scope="col">支付方式</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead><tbody><tr v-for="bill in bills" :key="bill.id"><td class="account-nowrap">{{ bill.billDate }}</td><td>{{ bill.title }}</td><td><span class="btable__num" :class="{ 'btable__num--off': !!bill.amountNote }">{{ money(bill.amountYuan) }}</span><span v-if="bill.amountNote" class="btable__note">{{ bill.amountNote }}</span></td><td>{{ payMethod(bill.payMethod) }}</td><td><span class="bstat" :class="bill.status === 'PAID' ? 'bstat--paid' : 'bstat--pending'">{{ bill.status === 'PAID' ? '已支付' : '待扣款' }}</span></td><td><button v-if="bill.canDownloadInvoice" class="obtn" @click="onInvoice">下载发票</button><span v-else>—</span></td></tr></tbody></table><div v-if="!bills.length" class="nodata"><p class="nodata__t">暂无账单记录</p><p class="nodata__d">完成订阅支付后将在此展示账单</p></div></div></section>
  </template>
  <AccountModal v-if="selectedPlan && !payOpen && !cancelOpen" :title="selectedPlan.action === 'DOWNGRADE' ? `降级至${selectedPlan.name}` : `${selectedPlan.action === 'SUBSCRIBE' ? '订阅' : '升级至'}${selectedPlan.name}`" :description="selectedPlan.action === 'DOWNGRADE' ? `当前套餐可使用至 ${date(sub?.periodEnd)}，降级将在本周期结束后生效，不退还当前周期费用。请确认目标套餐的广告账户上限与权益。` : `目标套餐 ${money(selectedPlan.listPriceYuan)} / 月。提交后将生成订单，实际应付金额以订单为准；支付完成后生效。`" small :busy="busy" @close="closePlan">
    <p v-if="error" class="account-error" role="alert">{{ error }}</p><template #footer><button class="btn-modal-ghost" :disabled="busy" @click="closePlan">取消</button><button class="btn-modal-primary" :disabled="busy" @click="confirmPlan">{{ busy ? '处理中…' : selectedPlan.action === 'DOWNGRADE' ? '确认降级' : '确认并支付' }}</button></template>
  </AccountModal>
  <AccountModal v-if="payOpen" title="确认支付" :description="payment?.mockReady ? '演示支付，不会产生真实扣款。' : '请通过订单支付渠道完成支付。'" small :busy="busy" @close="closePayment">
    <p class="account-payment-amount">应付 {{ money(pendingYuan) }}</p><p v-if="payment?.codeUrl && !payment.mockReady" class="acct-hint">请打开支付链接完成支付：<a class="link-act" :href="safePaymentUrl" target="_blank" rel="noopener noreferrer">前往支付</a></p><p v-else-if="!payment?.mockReady" class="acct-hint">当前支付通道尚未提供支付链接，请稍后重试。</p><p v-if="error" class="account-error" role="alert">{{ error }}</p>
    <template #footer><button class="btn-modal-ghost" :disabled="busy" @click="closePayment">关闭</button><template v-if="payment?.mockReady && payment.mockNonce"><button class="btn-modal-ghost" :disabled="busy" @click="confirmPay(false)">模拟失败</button><button class="btn-modal-primary" :disabled="busy" @click="confirmPay(true)">{{ busy ? '处理中…' : '确认演示支付' }}</button></template><button v-else class="btn-modal-primary" :disabled="busy" @click="checkPayment">刷新支付状态</button></template>
  </AccountModal>
  <AccountModal v-if="cancelOpen && pendingOrder" title="取消待支付订单" description="确定取消这笔订单？取消后可重新选择套餐，已支付的订阅不受影响。" small :busy="busy" @close="cancelOpen = false"><p v-if="error" class="account-error" role="alert">{{ error }}</p><template #footer><button class="btn-modal-ghost" :disabled="busy" @click="cancelOpen = false">返回</button><button class="btn-modal-primary" :disabled="busy" @click="cancelPending">{{ busy ? '处理中…' : '确认取消订单' }}</button></template></AccountModal>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { createOrder, createPayment, getSubscription, listBills, listPlans, mockComplete, patchAutoRenew, getPendingOrder, closeOrder, type OrderView, type BillItem, type PlanItem, type SubscriptionView, type PaymentView } from '@/features/billing/billingApi';
import AccountIcon from '@/pages/accounts/AccountIcon.vue';
import AccountModal from '@/pages/accounts/AccountModal.vue';
import { csvDownload, date } from '@/pages/accounts/accountFormat';
const user = useUserStore(), route = useRoute(), router = useRouter();
const sub = ref<SubscriptionView | null>(null), plans = ref<PlanItem[]>([]), bills = ref<BillItem[]>([]), plansRef = ref<HTMLElement>();
const payOpen = ref(false), busy = ref(false), renewing = ref(false), loading = ref(true), loadError = ref(''), error = ref(''), pendingYuan = ref(0), payment = ref<PaymentView>();
const pendingOrder = ref<OrderView | null>(null), cancelOpen = ref(false);
const aliases: Record<string, string> = { basic: 'BASIC', pro: 'PROFESSIONAL', flagship: 'FLAGSHIP' };
const selectedPlan = computed(() => plans.value.find(p => p.code === (aliases[String(route.query.plan)] || route.query.plan) && p.action !== 'CURRENT'));
const trialLabel = computed(() => user.me?.trialStatusLabel || (user.trialActive ? '试用中' : '试用已结束'));
const safePaymentUrl = computed(() => { try {
    const url = new URL(payment.value?.codeUrl || '');
    return ['http:', 'https:', 'weixin:'].includes(url.protocol) ? url.href : undefined;
}
catch {
    return undefined;
} });
function money(value?: number) { return value == null ? '—' : `¥${value.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`; }
function englishName(code?: string) { return ({ BASIC: 'Basic', PROFESSIONAL: 'Pro', FLAGSHIP: 'Flagship' } as Record<string, string>)[code || ''] || ''; }
function payMethod(raw?: string | null) { return raw === 'WECHAT' || raw === 'WX' ? '微信支付' : raw || '—'; }
function actionLabel(p: PlanItem) { return p.action === 'CURRENT' ? '当前使用' : `${p.action === 'DOWNGRADE' ? '降级至' : p.action === 'UPGRADE' ? '升级至' : '订阅'}${p.name}`; }
async function reload() { loading.value = true; loadError.value = ''; try {
    const [s, p, b, o] = await Promise.all([getSubscription(), listPlans(), listBills(), getPendingOrder()]);
    sub.value = s;
    plans.value = p.items;
    bills.value = b.items;
    pendingOrder.value = o;
    await user.loadMe();
}
catch {
    loadError.value = '订阅与账单加载失败，请重试';
}
finally {
    loading.value = false;
} }
function scrollPlans() { plansRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
function onPlan(p: PlanItem) { error.value = ''; void router.push({ query: { ...route.query, plan: p.code } }); }
function closePlan() { if (!busy.value)
    void router.push({ query: { ...route.query, plan: undefined } }); }
function closePayment() { if (!busy.value) {
    payOpen.value = false;
    payment.value = undefined;
    closePlan();
} }
async function toggleRenew() { if (!sub.value?.hasPaidSubscription || renewing.value)
    return; renewing.value = true; try {
    await patchAutoRenew(!sub.value.autoRenew);
    const fresh = await getSubscription();
    sub.value = fresh;
    await user.loadMe();
    ElMessage.success(fresh.autoRenew ? '已开启自动续费' : '已关闭自动续费');
}
catch { /* API interceptor reports the failure. */ }
finally {
    renewing.value = false;
} }
async function confirmPlan() { const p = selectedPlan.value; if (!p || busy.value)
    return; busy.value = true; error.value = ''; try {
    if (pendingOrder.value && pendingOrder.value.planCode !== p.code) {
        error.value = '请先继续支付或取消已有待支付订单';
        return;
    }
    const order = pendingOrder.value || await createOrder(p.code);
    if (p.action === 'DOWNGRADE') {
        ElMessage.success('已预约降级');
        await reload();
        busy.value = false;
        closePlan();
        return;
    }
    pendingOrder.value = order;
    pendingYuan.value = order.payableYuan;
    payment.value = await createPayment(order.orderId);
    payOpen.value = true;
}
catch {
    error.value = '订单或支付创建失败，请重试';
}
finally {
    busy.value = false;
} }
async function confirmPay(success: boolean) { const pay = payment.value; if (!pay?.mockReady || !pay.mockNonce || busy.value)
    return; busy.value = true; error.value = ''; try {
    await mockComplete(pay.paymentId, success, pay.mockNonce);
    await reload();
    busy.value = false;
    closePayment();
    ElMessage[success ? 'success' : 'info'](success ? '演示支付成功' : '支付未完成，订单仍待支付');
}
catch {
    error.value = '支付确认失败，请重试';
}
finally {
    busy.value = false;
} }
async function resumePayment() { if (!pendingOrder.value || busy.value)
    return; busy.value = true; error.value = ''; try {
    pendingYuan.value = pendingOrder.value.payableYuan;
    payment.value = await createPayment(pendingOrder.value.orderId);
    payOpen.value = true;
}
catch {
    ElMessage.error('支付创建失败，请重试');
}
finally {
    busy.value = false;
} }
async function cancelPending() { if (!pendingOrder.value || busy.value)
    return; busy.value = true; error.value = ''; try {
    await closeOrder(pendingOrder.value.orderId);
    cancelOpen.value = false;
    payOpen.value = false;
    await reload();
    ElMessage.success('订单已取消');
}
catch {
    error.value = '取消订单失败，请重试';
}
finally {
    busy.value = false;
} }
async function checkPayment() { busy.value = true; try {
    await reload();
    if (!pendingOrder.value) {
        busy.value = false;
        closePayment();
    }
    ElMessage.info('订阅与账单状态已更新');
}
finally {
    busy.value = false;
} }
function onInvoice() { ElMessage.info('正式发票下载接口尚未接入'); }
function exportCsv() { csvDownload('订阅账单.csv', [['账单日期', '套餐项目', '金额（元）', '备注', '支付方式', '状态'], ...bills.value.map(b => [b.billDate, b.title, b.amountYuan, b.amountNote, payMethod(b.payMethod), b.status === 'PAID' ? '已支付' : '待扣款'])]); }
watch(() => route.query.plan, () => { error.value = ''; if (!route.query.plan && !busy.value) {
    payOpen.value = false;
    payment.value = undefined;
} });
onMounted(reload);
</script>
