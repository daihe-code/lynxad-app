<template>
  <section class="checkout-status" role="status">正在打开支付宝收银台…</section>
</template>

<script setup lang="ts">
import { watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { alipayGatewayUrl, isBillingOrderId, openAlipayCheckout } from '@/features/billing/billingApi';

const route = useRoute();
const router = useRouter();

watch(() => route.query.order, async (order, _, onCleanup) => {
  const query = { order, kind: route.query.kind === 'recharge' ? 'recharge' : 'package' };
  const controller = new AbortController();
  const { signal } = controller;
  onCleanup(() => controller.abort());

  if (isBillingOrderId(order)) {
    try {
      const payment = await openAlipayCheckout(order, signal);
      if (signal.aborted) return;
      const url = alipayGatewayUrl(payment);
      if (url) {
        window.location.replace(url);
        return;
      }
    } catch { /* The result page checks whether the order was closed or paid. */ }
  }
  if (!signal.aborted) await router.replace({ name: 'payment-result', query });
}, { immediate: true });
</script>

<style scoped>
.checkout-status { max-width: 530px; margin: 48px auto; padding: 32px; border: 1px solid #e2e3f3; border-radius: 18px; background: #fff; }
</style>
