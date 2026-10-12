<template>
  <span class="product-price">
    <span :title="productPriceHint(priceInfo)">{{ productPriceText(priceInfo, price) }}</span>
    <small v-if="priceInfo?.stale" class="price-stale">上次价格</small>
    <small v-if="userPrice != null" class="calculation-price" title="已保存的测算价格，平台价格刷新时保留">测算 ¥{{ userPrice.toFixed(2) }}</small>
  </span>
</template>
<script setup lang="ts">
import { productPriceText, productPriceHint, type PlatformProductPrice } from '@/features/catalog/productPrice';
defineProps<{ priceInfo?: PlatformProductPrice | null; price?: number | null; userPrice?: number | null }>();
</script>
<style scoped>
.product-price{display:inline-flex;flex-wrap:wrap;align-items:baseline;gap:4px 6px;font-variant-numeric:tabular-nums}.product-price>span{color:#f05462;font-weight:600}.price-stale{color:#a7771e;font-size:11px;font-weight:400}.calculation-price{color:#7d829d;font-size:11px;font-weight:400}
</style>
