<template>
  <div class="app-shell" :class="{ 'campaign-shell': route.path === '/campaigns' || isReference, 'create-shell': isCreate, 'asset-shell': isAsset, 'account-shell': isAccount, 'reference-content': isReference, 'reference-header': usesReferenceHeader }">
    <header class="topbar">
      <div class="topbar-inner">
      <div class="left">
        <router-link :to="usesReferenceHeader ? '/' : '/campaigns'" class="brand">
          <svg v-if="usesReferenceHeader" viewBox="0 0 1300 935" aria-hidden="true">
            <g fill="none" stroke="currentColor" stroke-linecap="round">
              <path d="M1223.8 855.9C1219.3 837.9 1208.2 783 1196.8 747.6C1185.4 712.3 1170.6 677.8 1155.6 643.8C1140.7 609.7 1124.4 576.1 1107.2 543.1C1090 510.1 1072 477.5 1052.5 445.8C1033 414.1 1012.4 382.9 990.4 353C968.3 323 945.2 293.5 920.4 265.8C895.7 238.1 870.2 210.1 841.8 186.6C813.4 163.1 781.9 143.6 750 125C718 106.4 683.3 75 650 75C616.7 75 582 106.4 550 125C518.1 143.6 486.6 163.1 458.2 186.6C429.8 210.1 404.3 238.1 379.6 265.8C354.8 293.5 331.7 323 309.6 353C287.6 382.9 267 414.1 247.5 445.8C228 477.5 210 510.1 192.8 543.1C175.6 576.1 159.3 609.7 144.4 643.8C129.4 677.8 114.6 712.3 103.2 747.6C91.8 783 80.7 837.9 76.2 855.9" stroke-width="150" />
              <path d="M82.8 175.9L285 455" stroke-width="58" />
              <path d="M1217.2 175.9L1015 455" stroke-width="58" />
            </g>
            <circle cx="440" cy="559" r="82.5" fill="currentColor" />
            <circle cx="860" cy="559" r="82.5" fill="currentColor" />
          </svg>
          {{ usesReferenceHeader ? '灵投智营' : '灵投' }}
        </router-link>
        <el-menu id="app-navigation" mode="horizontal" :ellipsis="false" :default-active="active" @select="navOpen = false" :class="{ 'mobile-menu-open': usesReferenceHeader && navOpen }" router>
          <el-menu-item index="/campaigns">广告投放</el-menu-item>
          <el-menu-item index="/monitoring">全店盯盘</el-menu-item>
          <el-menu-item index="/creatives">素材中心</el-menu-item>
          <el-menu-item v-if="!usesReferenceHeader" index="/analytics">数据分析</el-menu-item>
          <el-menu-item index="/accounts">账户管理<svg v-if="usesReferenceHeader" class="account-caret" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9.6 6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg></el-menu-item>
        </el-menu>
      </div>
      <div class="right">
        <button v-if="usesReferenceHeader" class="nav-toggle" type="button" :aria-label="navOpen ? '收起导航' : '展开导航'" aria-controls="app-navigation" :aria-expanded="navOpen" @click="navOpen = !navOpen"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg></button>
        <NotificationCenter />
        <UserMenu v-if="usesReferenceHeader" />
        <template v-else><span class="who">{{ user.me?.nickname || user.me?.phone || "用户" }}</span><el-button text @click="onLogout">退出</el-button></template>
      </div>
      </div>
    </header>
    <div v-if="user.demoMode" class="demo-watermark">演示数据</div>
    <main class="main">
      <router-view />
    </main>
    <div v-if="route.path === '/campaigns'" class="campaign-reference reference-footer-only"><ReferenceFooter /></div>
    <footer v-if="!isReference && route.path !== '/campaigns'" class="foot">
      <span>© 2026 灵投 LynxAd · 智能投放平台</span>
      <span>
        <a href="#" @click.prevent="onHelp">帮助中心</a>
        ·
        <router-link to="/legal/terms">使用指南</router-link>
        ·
        <router-link to="/legal/privacy">隐私政策</router-link>
      </span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import UserMenu from "./UserMenu.vue";
import NotificationCenter from "./NotificationCenter.vue";
import { useUserStore } from "@/stores/user";
import "@/pages/campaigns/campaign-ui.css";
import ReferenceFooter from "@/pages/campaigns/reference/ReferenceFooter.vue";
import "@/pages/campaigns/reference/reference.css";

const user = useUserStore();
const route = useRoute();
const isCreate = computed(() => route.path === "/campaigns/create");
const isAsset = computed(() => route.path === "/creatives");
const isAccount = computed(() => route.path === "/accounts" || route.path === "/subscription");
const isReference = computed(() => isCreate.value || isAsset.value || isAccount.value || route.path.startsWith('/campaigns/') || route.path.startsWith('/authorization') || route.path === '/oauth/callback' || route.path === '/analytics');
// Navigation styling is shared independently of each page’s content layout.
const usesReferenceHeader = computed(() => isReference.value || route.path.startsWith("/campaigns") || route.path.startsWith('/monitoring'));
const navOpen = ref(false);
watch(() => route.fullPath, () => { navOpen.value = false; });
const router = useRouter();
const active = computed(() => {
  if (route.path.startsWith("/monitoring")) return "/monitoring";
  if (route.path.startsWith("/creatives")) return "/creatives";
  if (route.path.startsWith("/analytics")) return "/campaigns";
  if (route.path.startsWith("/accounts") || route.path.startsWith("/subscription")) return "/accounts";
  return "/campaigns";
});

function onHelp() {
  ElMessage.info("帮助中心即将上线");
}

function onLogout() {
  user.logout();
  router.push("/login");
}


</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.campaign-shell { background: #f7f7ff; --el-color-primary: #575cf6; }
.reference-footer-only { min-height: 0; }
.campaign-shell .main { padding: 20px 24px 40px; }
.campaign-shell .foot { background: #1a1a32; font-size: 12px; padding: 24px; color: #a7a2bc; }
@media (max-width: 950px) {
  .campaign-shell .main { padding: 16px 12px 28px; }
  .campaign-shell .foot { gap: 16px; flex-wrap: wrap; padding: 20px 16px; }
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  flex-shrink: 0;
  height: 64px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid #eef0f6;
}
.left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.brand {
  font-weight: 800;
  font-size: 22px;
  color: var(--color-primary);
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.who {
  color: #4b5563;
}
.main {
  flex: 1;
  padding: 20px 24px 40px;
}
.foot {
  background: #111827;
  color: #9ca3af;
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
}
.foot a {
  color: #d1d5db;
}
:deep(.el-menu) {
  border-bottom: none;
}
.topbar-inner { display: contents; }
:is(.create-shell, .asset-shell, .account-shell) { font-family: system-ui, -apple-system, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif; }
.reference-header .topbar { height: 60px; padding: 0; box-sizing: border-box; font-family: system-ui, -apple-system, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif; }
.reference-header .left { flex: 1; }
.reference-header .topbar-inner { box-sizing: border-box; position: relative; display: flex; align-items: center; width: 100%; max-width: 1128px; height: 100%; margin: auto; padding: 0 24px; }
.reference-header .brand { display: flex; align-items: center; white-space: nowrap; gap: 8px; font-size: 17px; font-weight: 600; letter-spacing: -.025em; color: #1a1b2e; }
.reference-header .brand svg { display: block; flex-shrink: 0; width: 23.3px; height: 16.8px; color: #575cf6; }
.reference-header :deep(.el-menu) { position: absolute; left: 50%; transform: translateX(-50%); height: auto; --el-menu-horizontal-height: 31px; --el-menu-active-color: #575cf6; --el-menu-hover-bg-color: #f7f8fe; gap: 4px; }
.reference-header :deep(.el-menu-item) { height: 31px; padding: 8px 12px; font-size: 15px; font-weight: 500; line-height: 1; border-bottom: 0; border-radius: 8px; color: #2b2e45; }
.reference-header :deep(.el-menu-item.is-active) { color: #4348db; font-weight: 500; background: transparent; }
.reference-header .right { gap: 12px; font-size: 12px; }
.reference-header .right :deep(.el-button.is-circle) { width: 36px; height: 36px; border: 0; background: transparent; color: #7c7f99; font-size: 21px; }
.reference-header .account-caret { margin-left: 4px; width: 14px; height: 14px; color: #7c7f99; }
:is(.create-shell, .asset-shell, .account-shell, .reference-content) .main { padding: 0; }
@media (max-width: 950px) {
  :is(.create-shell, .asset-shell, .account-shell) .main { padding: 0; }
}

:is(.asset-shell, .account-shell) .main { display: flex; flex-direction: column; }
.reference-header .topbar { z-index: 40; background: rgba(255,255,255,.9); backdrop-filter: blur(12px); }
:is(.asset-shell, .account-shell) .main > * { flex: 1; }
.nav-toggle { display: none; }
@media (max-width: 1023px) {
  .reference-header .topbar { height: 60px; min-height: 60px; padding: 0; }
  .reference-header .topbar-inner { padding: 0 24px; flex-wrap: nowrap; }
  .reference-header .left { display: flex; }
  .reference-header .nav-toggle { display: grid; place-items: center; padding: 0; border: 0; background: transparent; width: 36px; height: 36px; color: #7c7f99; cursor: pointer; }
  .nav-toggle svg { width: 20px; height: 20px; }
  .reference-header :deep(.el-menu) { display: none; position: absolute; left: 0; top: 60px; transform: none; width: 100%; height: auto; padding: 12px 24px; box-sizing: border-box; background: #fff; border-bottom: 1px solid #e6e8f2; z-index: 1; }
  .reference-header :deep(.el-menu.mobile-menu-open) { display: flex; flex-direction: column; align-items: stretch; }
  .reference-header :deep(.el-menu-item) { justify-content: flex-start; width: 100%; }
}
@media (max-width: 639px) { .reference-header .right { gap: 4px; } }
</style>
