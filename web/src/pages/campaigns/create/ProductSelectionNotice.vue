<template><aside v-if="message" class="product-selection-notice" role="status"><strong>商品与素材调整提醒</strong><p>{{ message }}</p></aside></template>
<script setup lang="ts">defineProps<{ message?: string }>();</script>
<style scoped>.product-selection-notice{margin:16px 0;padding:16px 20px;border:1px solid #e9d9ab;border-radius:12px;background:#fffbef;color:#755d26;font-size:13px;line-height:1.8}.product-selection-notice strong{font-weight:600}.product-selection-notice p{margin-top:6px;white-space:pre-line;overflow-wrap:anywhere}</style>
