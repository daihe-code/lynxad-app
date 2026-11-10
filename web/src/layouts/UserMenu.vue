<template>
  <el-tooltip content="灵投账户" :trigger="['hover', 'focus']" :show-after="200" placement="bottom" :disabled="open">
    <span class="user-menu-hint">
  <el-popover v-model:visible="open" placement="bottom-end" :width="236" :offset="24" trigger="click" popper-class="lynx-user-popover" :show-arrow="false">
    <template #reference>
      <button ref="trigger" type="button" class="user-menu-trigger" :class="{ 'is-open': open, 'user-menu-trigger--home': appearance === 'home' }" aria-label="用户菜单" aria-haspopup="menu" :aria-expanded="open" aria-controls="user-menu-panel" @keydown="triggerKey">
        <span class="user-menu-avatar" aria-hidden="true"><el-icon><UserFilled /></el-icon></span><span class="user-menu-name">{{ name }}</span>
      </button>
    </template>
    <section id="user-menu-panel" ref="panel" class="um" role="menu" aria-label="用户菜单" @keydown="menuKey">
      <div class="um__hd"><span class="um__av" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8.2" r="3.7"/><path d="M4.5 20.5c1.2-3.7 4-5.6 7.5-5.6s6.3 1.9 7.5 5.6"/></svg></span><span class="um__who"><b :title="name">{{ name }}</b><i>{{ subtitle }}</i></span></div>
      <div class="um__list">
        <router-link v-for="item in entries" :key="item.to" :to="item.to" class="um__it" role="menuitem" @click="open = false"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><template v-if="item.icon === 'user'"><circle cx="12" cy="8" r="3.4"/><path d="M5 20c1-3.4 3.7-5.2 7-5.2s6 1.8 7 5.2"/></template><template v-else-if="item.icon === 'card'"><rect x="3" y="6" width="18" height="13" rx="2.5"/><path d="M3 10.5h18"/></template><template v-else-if="item.icon === 'shield'"><path d="M12 3.5 5 6v6c0 4.2 2.9 7.4 7 8.5 4.1-1.1 7-4.3 7-8.5V6l-7-2.5Z"/><path d="m9.2 12 2 2 3.6-4"/></template><template v-else><circle cx="12" cy="12" r="8.6"/><path d="M12 7.6V12l3 1.8"/></template></svg>{{ item.label }}</router-link>
      </div>
      <div class="um__ft"><button type="button" class="um__out" role="menuitem" @click="logout"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 17 5-5-5-5M20 12H9M11 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5"/></svg>退出登录</button></div>
    </section>
  </el-popover>
    </span>
  </el-tooltip>
</template>

<script setup lang="ts">
import { workspaceAccountLabel } from '@/features/tenant/workspaceApi';
import { accountNavigationForRole } from '@/features/tenant/accountNavigation';
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { UserFilled } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';

const props = withDefaults(defineProps<{ appearance?: 'default' | 'home'; fallbackName?: string }>(), { appearance: 'default' });
const user = useUserStore(), route = useRoute(), router = useRouter();
const open = ref(false), trigger = ref<HTMLButtonElement>(), panel = ref<HTMLElement>();
const name = computed(() => user.me?.nickname || props.fallbackName || user.me?.phone || '用户');
const subtitle = computed(() => {
  const role = workspaceAccountLabel(user.me?.memberRole);
  return [user.me?.displayPlanName || user.me?.subscriptionPlanName, role].filter(Boolean).join(' · ') || '账户信息';
});
const entries = computed(() => accountNavigationForRole(user.me?.memberRole));
watch(() => route.fullPath, () => { open.value = false; });
function close() { open.value = false; trigger.value?.focus(); }
async function triggerKey(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); close(); }
  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
  event.preventDefault(); open.value = true;
  await nextTick();
  const items = panel.value?.querySelectorAll<HTMLElement>('[role="menuitem"]');
  items?.[event.key === 'ArrowUp' ? items.length - 1 : 0]?.focus();
}
function menuKey(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.stopPropagation(); event.preventDefault(); close(); return; }
  if (event.key === 'Tab') { open.value = false; return; }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const items = Array.from(panel.value?.querySelectorAll<HTMLElement>('[role="menuitem"]') || []);
  const index = items.indexOf(document.activeElement as HTMLElement);
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
  items[next]?.focus();
}
async function logout() { open.value = false; await user.logout(); void router.push('/login'); }
</script>

<style scoped>
.user-menu-hint { display: inline-flex; }
.user-menu-trigger { display: flex; align-items: center; gap: 8px; border: 0; background: transparent; border-radius: 999px; padding: 4px 12px 4px 4px; font-family: inherit; font-size: 15px; font-weight: 500; line-height: 1; color: #1a1b2e; cursor: pointer; }
.user-menu-trigger:hover, .user-menu-trigger.is-open { background: #f7f8fe; }
.user-menu-avatar { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; flex: none; border-radius: 50%; color: #fff; background: linear-gradient(135deg,#575cf6,#a165ef); }
.user-menu-name { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-menu-trigger--home { height: 40px; padding: 0 14px 0 4px; background: rgb(var(--c-primary-soft,238 236 255)); }
.user-menu-trigger--home:hover, .user-menu-trigger--home.is-open { background: rgb(var(--c-primary-soft,238 236 255)); }
.user-menu-trigger--home .user-menu-avatar { width: 32px; height: 32px; }
.user-menu-trigger--home .user-menu-avatar :deep(svg) { width: 15px; height: 15px; }
.user-menu-trigger--home .user-menu-name { font-size: 14px; max-width: 96px; }
.um { text-align: left; color: #4e5069; }
.um__hd { display: flex; align-items: center; gap: 9px; padding: 13px 14px; border-bottom: 1px solid #e6e8f2; }
.um__av { width: 30px; height: 30px; flex: none; border-radius: 50%; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg,#575cf6,#a165ef); }
.um__av svg { width: 16px; height: 16px; display: block; }
.um__who { min-width: 0; }
.um__who b { display: block; font-size: 13px; font-weight: 600; color: #1a1b2e; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.um__who i { display: block; font-style: normal; font-size: 11.5px; color: #7c7f99; margin-top: 2px; }
.um__list { padding: 6px; }
.um__it, .um__out { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 9px; font-size: 13px; line-height: 1.5; color: #4e5069; text-decoration: none; transition: background-color .15s,color .15s; }
.um__it:hover, .um__it:focus-visible { background: #f7f8fe; color: #4348db; }
.um__it svg, .um__out svg { width: 15px; height: 15px; flex: none; color: #7c7f99; }
.um__it:hover svg, .um__it:focus-visible svg { color: #575cf6; }
.um__ft { padding: 6px; border-top: 1px solid #e6e8f2; }
.um__out { width: 100%; border: 0; background: transparent; font-family: inherit; cursor: pointer; text-align: left; }
.um__out:hover, .um__out:focus-visible { background: #fdecec; color: #e5484d; }
.um__out:hover svg, .um__out:focus-visible svg { color: #e5484d; }
.user-menu-trigger:focus-visible, .um__it:focus-visible, .um__out:focus-visible { outline: 2px solid #575cf6; outline-offset: -2px; }
@media (max-width: 639px) { .user-menu-name { max-width: 70px; } }
</style>
<style>
.el-popover.el-popper.lynx-user-popover { padding: 0; border: 1px solid #e6e8f2; border-radius: 14px; overflow: hidden; max-width: calc(100vw - 32px); box-shadow: 0 20px 48px -14px rgba(26,27,46,.24),0 3px 10px rgba(26,27,46,.06); font-family: system-ui,-apple-system,"Segoe UI",Roboto,"PingFang SC","Hiragino Sans GB","Microsoft YaHei","Helvetica Neue",Arial,sans-serif; }
@media (max-width: 639px) { .el-popover.el-popper.lynx-user-popover { position: fixed !important; top: 72px !important; right: 16px !important; left: auto !important; transform: none !important; } }
</style>
