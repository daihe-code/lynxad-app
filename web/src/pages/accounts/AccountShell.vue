<template>
  <div class="account-pages acct" :class="{ 'acct--log': active === 'logs' }">
    <div class="acct-shell">
      <aside class="acct-side">
        <div class="acct-side__brand"><span class="acct-side__ic"><AccountIcon name="grid" /></span><span class="acct-side__t">账户管理</span></div>
        <nav class="acct-menu" aria-label="账户管理导航">
          <router-link v-for="item in menus" :key="item.key" :to="item.to" class="acct-menu__i" :aria-current="active === item.key ? 'page' : undefined">{{ item.label }}</router-link>
        </nav>
      </aside>
      <section class="acct-main"><div class="acct-wrap">
        <header class="acct-head"><h1 class="acct-head__h">{{ current?.label }}</h1><p class="acct-head__d">{{ current?.description }}</p></header>
        <slot />
      </div></section>
    </div>
    <footer class="mg-foot"><div class="container-page mg-foot__in"><span class="mg-foot__c">© 2026 灵投智营 · 广告智能投放平台</span><div class="mg-foot__l"><button class="mg-foot__a" type="button" @click="ElMessage.info('帮助中心即将上线')">帮助中心</button><router-link class="mg-foot__a" to="/legal/terms">使用指南</router-link><router-link class="mg-foot__a" to="/legal/privacy">隐私政策</router-link></div></div></footer>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { ElMessage } from 'element-plus';
import AccountIcon from './AccountIcon.vue';
const props = defineProps<{
    active: string;
}>();
const menus = [
    { key: 'info', label: '账户信息', description: '管理企业信息、安全设置与发票信息', to: '/accounts' },
    { key: 'oauth', label: '广告投放授权', description: '管理已授权的广告账户与投放权限', to: '/accounts?tab=oauth' },
    { key: 'billing', label: '订阅与账单', description: '查看当前套餐、对比方案与账单记录', to: '/subscription' },
    { key: 'logs', label: '操作日志', description: '查看账户操作记录，保障投放安全与可追溯', to: '/accounts?tab=logs' },
];
const current = computed(() => menus.find(item => item.key === props.active));
</script>
<style src="./account.css"></style>
