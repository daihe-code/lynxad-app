<template>
  <div class="soon page-card">
    <p class="tag">即将上线</p>
    <h1>{{ title }}</h1>
    <p>该能力本轮不开放，避免半成品支付或运营后台。你仍可继续投放、查看素材与账户授权。</p>
    <el-button type="primary" @click="$router.push('/campaigns')">返回广告投放</el-button>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title?: string }>(), { title: "即将上线" });
</script>

<style scoped>
.soon {
  max-width: 640px;
  margin: 40px auto;
  padding: 40px;
  text-align: center;
}
.tag {
  color: var(--color-primary);
  font-weight: 600;
}
</style>
