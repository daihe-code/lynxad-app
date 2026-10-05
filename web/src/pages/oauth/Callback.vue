<template>
  <div class="cb page-card">
    <template v-if="ok">
      <h1>授权成功</h1>
      <p>灵投已获得广告投放授权。浏览器不会保存千川访问令牌。</p>
      <el-button type="primary" @click="$router.push('/accounts?tab=oauth')">返回广告投放授权</el-button>
      <el-button @click="$router.push('/campaigns/create')">立即开始投放</el-button>
    </template>
    <template v-else>
      <h1>授权失败</h1>
      <p>{{ message }}</p>
      <el-button type="primary" @click="$router.push('/accounts?tab=oauth&modal=add')">重新授权</el-button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const ok = computed(() => route.query.status !== "fail" && route.query.ok !== "0");
const message = computed(() => String(route.query.message || route.query.error || "授权未完成，请返回账户管理重试"));
</script>

<style scoped>
.cb {
  max-width: 560px;
  margin: 80px auto;
  padding: 40px;
  text-align: center;
}
</style>
