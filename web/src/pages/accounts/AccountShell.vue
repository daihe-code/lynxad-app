<template>
  <div class="accounts">
    <aside class="side page-card">
      <h3>账户管理</h3>
      <button
        v-for="item in menus"
        :key="item.key"
        :class="{ active: active === item.key }"
        @click="onMenu(item.key)"
      >
        {{ item.label }}
      </button>
    </aside>
    <section class="main page-card">
      <slot />
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

defineProps<{ active: string }>();
const router = useRouter();
const menus = [
  { key: "info", label: "账户信息" },
  { key: "oauth", label: "广告投放授权" },
  { key: "billing", label: "订阅与账单" },
  { key: "logs", label: "操作日志" },
];

function onMenu(key: string) {
  if (key === "billing") {
    router.push("/subscription");
    return;
  }
  router.push({ path: "/accounts", query: key === "info" ? {} : { tab: key } });
}
</script>

<style scoped>
.accounts {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
}
.side button {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 10px 8px;
  cursor: pointer;
}
.side button.active {
  color: #5b4dff;
  font-weight: 600;
}
</style>
