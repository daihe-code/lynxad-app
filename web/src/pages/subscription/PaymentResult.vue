<template>
  <section class="payment-result" aria-labelledby="payment-result-title">
    <h1 id="payment-result-title">{{ title }}</h1>
    <p v-if="order">{{ recharge ? '积分充值' : '套餐订单' }} · {{ amount }}</p>
    <p v-if="state === 'pending'" role="status">支付结果待确认，可稍后查询此订单。</p>
    <p v-if="state === 'error'" role="alert">暂时无法核对支付结果，请重试。</p>
    <div class="payment-actions">
      <el-button v-if="state === 'pending' || state === 'error' || state === 'checking'" type="primary"
        :loading="state === 'checking'" @click="retry">查询支付结果</el-button>
      <router-link :to="returnPath">
        {{ recharge ? '返回积分充值' : '返回套餐页面' }}
      </router-link>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { checkOrderPayment, isBillingOrderId, type OrderView } from '@/features/billing/billingApi';
import { pointRechargePath } from '@/features/tenant/accountNavigation';
import { useUserStore } from '@/stores/user';

const route = useRoute(), router = useRouter(), user = useUserStore();
const order = ref<OrderView>();
const state = ref<'checking' | 'pending' | 'paid' | 'closed' | 'error' | 'invalid'>('checking');
const retryToken = ref(0);
const retry = () => retryToken.value++;
const recharge = computed(() => order.value ? order.value.type === 'POINT_RECHARGE' : route.query.kind === 'recharge');
const returnPath = computed(() => recharge.value ? pointRechargePath : '/subscription');
const amount = computed(() => order.value ? `¥${order.value.payableYuan.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '');
const title = computed(() => ({
  checking: '正在核对支付结果…', pending: '等待支付确认', paid: recharge.value ? '充值成功' : '支付成功',
  closed: '订单已关闭', error: '支付结果待查询', invalid: '未找到要查询的订单',
}[state.value]));

watch([() => route.query.order, retryToken], ([id], previous, onCleanup) => {
  if (id !== previous?.[0]) order.value = undefined;
  if (!isBillingOrderId(id)) {
    state.value = 'invalid';
    return;
  }
  const controller = new AbortController();
  const { signal } = controller;
  let timer: ReturnType<typeof setTimeout> | undefined;
  onCleanup(() => { controller.abort(); clearTimeout(timer); });
  state.value = 'checking';

  async function refresh(orderId: string, attempts: number) {
    try {
      const result = await checkOrderPayment(orderId, signal);
      if (signal.aborted) return;
      order.value = result;
      if (result.status === 'PAID') {
        const failure = await router.replace(returnPath.value);
        if (!failure || signal.aborted) return;
        state.value = 'paid';
        void user.loadMe().catch(() => {});
      } else if (result.status === 'CLOSED') {
        state.value = 'closed';
      } else if (attempts > 1) {
        timer = setTimeout(() => void refresh(orderId, attempts - 1), 3000);
      } else {
        state.value = 'pending';
      }
    } catch {
      if (!signal.aborted) state.value = 'error';
    }
  }

  void refresh(id, 4);
}, { immediate: true });
</script>

<style scoped>
.payment-result { max-width: 530px; margin: 48px auto; padding: 32px; border: 1px solid #e2e3f3; border-radius: 18px; background: #fff; }
.payment-result h1 { margin: 0 0 14px; font-size: 22px; }
.payment-result p { margin: 8px 0; color: #595f79; }
.payment-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; margin-top: 22px; }
.payment-actions a { color: #4f45d6; }
</style>
