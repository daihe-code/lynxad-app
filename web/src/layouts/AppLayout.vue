<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="left">
        <router-link to="/campaigns" class="brand">灵投</router-link>
        <el-menu mode="horizontal" :ellipsis="false" :default-active="active" router>
          <el-menu-item index="/campaigns">广告投放</el-menu-item>
          <el-menu-item index="/creatives">素材中心</el-menu-item>
          <el-menu-item index="/analytics">数据分析</el-menu-item>
          <el-menu-item index="/accounts">账户管理</el-menu-item>
        </el-menu>
      </div>
      <div class="right">
        <el-popover placement="bottom-end" :width="320" trigger="click">
          <template #reference>
            <el-badge :value="unread" :hidden="unread === 0">
              <el-button circle>
                <el-icon><Bell /></el-icon>
              </el-button>
            </el-badge>
          </template>
          <div v-if="inbox.length === 0" class="empty">暂无站内信</div>
          <div v-for="item in inbox" :key="item.id" class="mail" @click="mark(item.id)">
            <strong>{{ item.title }}</strong>
            <p>{{ item.body }}</p>
          </div>
        </el-popover>
        <span class="who">{{ user.me?.nickname || user.me?.phone || "用户" }}</span>
        <el-button text @click="onLogout">退出</el-button>
      </div>
    </header>
    <div v-if="user.demoMode" class="demo-watermark">演示数据</div>
    <main class="main">
      <router-view />
    </main>
    <footer class="foot">
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
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Bell } from "@element-plus/icons-vue";
import { useUserStore } from "@/stores/user";
import { listInbox, readInbox, type InboxMessage } from "@/features/tenant/inboxApi";
import { WATCHER_TYPES } from "@/features/agentops/agentopsApi";
import { getPlan } from "@/features/delivery/deliveryApi";

const user = useUserStore();
const route = useRoute();
const router = useRouter();
const inbox = ref<InboxMessage[]>([]);
const unread = computed(() => inbox.value.filter((i) => i.unread).length);
const active = computed(() => {
  if (route.path.startsWith("/creatives")) return "/creatives";
  if (route.path.startsWith("/analytics")) return "/analytics";
  if (route.path.startsWith("/accounts") || route.path.startsWith("/subscription")) return "/accounts";
  return "/campaigns";
});

async function loadInbox() {
  try {
    inbox.value = await listInbox();
  } catch {
    inbox.value = [];
  }
}

async function mark(id: string) {
  const item = inbox.value.find((i) => i.id === id);
  await readInbox(id);
  await loadInbox();
  const bizType = item?.bizType || "";
  const bizId = item?.bizId;
  if (!WATCHER_TYPES.includes(bizType as (typeof WATCHER_TYPES)[number]) || !bizId) {
    return;
  }
  try {
    await getPlan(bizId);
    await router.push("/campaigns/" + bizId);
  } catch {
    ElMessage.warning("计划不可见");
    await router.push("/campaigns");
  }
}

function onHelp() {
  ElMessage.info("帮助中心即将上线");
}

function onLogout() {
  user.logout();
  router.push("/login");
}

onMounted(loadInbox);
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.topbar {
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
.mail {
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}
.mail p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 12px;
}
.empty {
  color: #9ca3af;
  text-align: center;
  padding: 12px 0;
}
:deep(.el-menu) {
  border-bottom: none;
}
</style>
