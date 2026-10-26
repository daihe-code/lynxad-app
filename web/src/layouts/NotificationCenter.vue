<template>
  <el-popover v-model:visible="open" placement="bottom-end" :width="400" :offset="12" trigger="click" popper-class="lynx-notification-popover" :show-arrow="false">
    <template #reference>
      <el-badge :value="unread" :max="99" :hidden="unread === 0"><el-button ref="bell" circle :aria-label="`消息通知，${unread} 条未读`" aria-haspopup="dialog" :aria-expanded="open"><el-icon><Bell /></el-icon></el-button></el-badge>
    </template>
    <section ref="panel" class="ntc" role="dialog" aria-label="通知中心" tabindex="-1" @keydown.esc.stop.prevent="close">
      <div class="ntc__hd"><span class="ntc__av" aria-hidden="true"><svg viewBox="0 0 1300 935"><g fill="none" stroke="currentColor" stroke-linecap="round"><path d="M1223.8 855.9C1170 610 970 300 750 125C718 106 683 75 650 75C617 75 582 106 550 125C330 300 130 610 76.2 855.9" stroke-width="150"/><path d="M82.8 175.9 285 455M1217.2 175.9 1015 455" stroke-width="58"/></g><circle cx="440" cy="559" r="82.5" fill="currentColor"/><circle cx="860" cy="559" r="82.5" fill="currentColor"/></svg></span><span class="ntc__who"><b>小灵</b><i>{{ watchedPlans === null ? '为你及时送达投放动态' : `正在替你盯着 ${watchedPlans} 个投放计划` }}</i></span><button class="ntc__close" type="button" aria-label="关闭消息面板" @click="close">×</button></div>
      <div class="ntc__bar"><div class="ntc__tabs" role="tablist" aria-label="通知筛选"><button v-for="value in ['all', 'unread'] as const" :key="value" :id="`notification-${value}`" class="ntc__tab" :class="{ 'is-on': tab === value }" type="button" role="tab" :aria-selected="tab === value" aria-controls="notification-list" :tabindex="tab === value ? 0 : -1" @click="tab = value" @keydown="tabKey($event)">{{ value === 'all' ? '全部' : '未读' }}<i class="ntc__tab-n">{{ value === 'all' ? inbox.length : unread }}</i></button></div><button class="ntc__allread" type="button" title="将所有类别的消息全部标为已读" aria-description="覆盖所有类别，不受当前筛选影响" :disabled="!unread || readingAll || pending.size > 0" @click="markAll">{{ readingAll ? '正在标记…' : '全部标为已读' }}</button></div>
      <div class="ntc__filters"><label for="notification-category">消息类别</label><select id="notification-category" v-model="category" aria-label="消息类别"><option value="all">全部类别</option><option value="system">系统通知</option><option value="warning">投放预警</option><option value="approval">审批提醒</option><option value="optimization">优化建议</option></select></div>
      <div v-if="failed" class="ntc__error" role="status">{{ inbox.length ? '消息更新失败，当前显示已有消息' : '消息暂时加载失败' }}<button type="button" @click="refresh(false)">重试</button></div>
      <div id="notification-list" class="ntc__list" role="tabpanel" :aria-labelledby="`notification-${tab}`" :aria-busy="loading">
        <div v-if="!visible.length" class="ntc__empty"><el-icon><Bell /></el-icon><p>{{ loading ? '正在加载消息…' : failed ? '请稍后重试' : category !== 'all' ? (tab === 'unread' ? '该类别暂无未读消息' : '该类别暂无消息') : tab === 'unread' ? '暂无未读消息' : '暂无站内消息' }}</p></div>
        <article v-for="{ item, view } in visible" :key="item.id" class="ntc__item" :class="{ 'is-unread': item.unread }"><span class="ntc__dot" :aria-label="item.unread ? '未读' : '已读'"/><div class="ntc__main"><div class="ntc__meta"><span class="ntc__kind" :class="`ntc__kind--${view.tone}`">{{ view.category }}</span><span v-if="view.outcome" class="ntc__outcome" :class="`ntc__outcome--${view.outcome}`" :aria-label="view.outcome === 'FAILED' ? '失败' : view.outcome === 'PARTIAL' ? '部分完成' : '已生成'" role="img">{{ view.outcome === 'FAILED' ? '×' : view.outcome === 'PARTIAL' ? '!' : '✓' }}</span><time :datetime="item.sentAt" :title="new Date(item.sentAt).toLocaleString()">{{ inboxTime(item.sentAt) }}</time></div><button class="ntc__message" type="button" :disabled="pending.has(item.id) || readingAll" :aria-label="`${view.title}，${item.unread ? '标为已读' : '已读'}`" @click="mark(item)"><h3 class="ntc__title">{{ view.title }}</h3><p v-if="view.planName" class="ntc__plan" :title="view.planName">计划：{{ view.planName }}</p><p class="ntc__txt"><template v-for="(part, index) in inboxBody(view.body)" :key="index"><b v-if="part.emphasis">{{ part.text }}</b><template v-else>{{ part.text }}</template></template></p></button><div v-if="view.action && (item.bizId || view.destination !== 'plan')" class="ntc__ops"><button class="ntc__op" type="button" :disabled="pending.has(item.id) || readingAll" @click="visit(item)">{{ view.action }} <span aria-hidden="true">→</span></button></div></div></article>
      </div>
      <div class="ntc__ft"><span>{{ loading ? '正在更新消息…' : `共 ${inbox.length} 条消息 · ${unread} 条未读` }}</span></div>
    </section>
  </el-popover>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Bell } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { listInbox, readAllInbox, readInbox, type InboxMessage } from '@/features/tenant/inboxApi';
import { filterInbox, inboxBody, inboxPresentation, inboxTime, type InboxFilterCategory } from '@/features/tenant/inboxPresentation';
import { getPlan, listPlans } from '@/features/delivery/deliveryApi';
import { generationNotice, generationNoticeRoute } from '@/features/tenant/inboxGeneration';
const route = useRoute(), router = useRouter();
const open = ref(false), tab = ref<'all' | 'unread'>('all'), inbox = ref<InboxMessage[]>([]);
const loading = ref(false), failed = ref(false), readingAll = ref(false), pending = ref(new Set<string>());
const watchedPlans = ref<number | null>(null), panel = ref<HTMLElement>(), bell = ref<{ $el: HTMLElement }>();
const category = ref<InboxFilterCategory | 'all'>('all');
const unread = computed(() => inbox.value.filter(item => item.unread).length);
const visible = computed(() => filterInbox(inbox.value, tab.value, category.value).map(item => ({ item,
  view: { title: item.title || '系统消息', body: item.body || '', planName: '', outcome: '', ...inboxPresentation(item), ...generationNotice(item) },
})));
let disposed = false, epoch = 0, pollTimer: number | undefined;
async function refresh(background = true) {
  if (loading.value || disposed || readingAll.value || pending.value.size) return;
  loading.value = true;
  const version = epoch;
  try {
    const next = await listInbox(background);
    if (disposed || epoch !== version) return;
    inbox.value = next.sort((a, b) => new Date(b.sentAt).getTime() - new Date(a.sentAt).getTime());
    failed.value = false;
  } catch { if (!disposed && epoch === version) failed.value = true; }
  finally { loading.value = false; }
}
async function refreshPlans() {
  try { const result = await listPlans(1, 1, { status: 'DELIVERING' }); if (!disposed) watchedPlans.value = result.total; }
  catch { /* Keep the last known count without inventing a plan total. */ }
}
async function mark(item: InboxMessage) {
  if (!item.unread) return true;
  if (pending.value.has(item.id) || readingAll.value) return false;
  epoch++;
  pending.value.add(item.id);
  try {
    await readInbox(item.id);
    if (!disposed) { item.unread = false; const current = inbox.value.find(message => message.id === item.id); if (current) current.unread = false; }
    return true;
  } catch { return false; }
  finally { pending.value.delete(item.id); }
}
async function markAll() {
  if (!unread.value || readingAll.value || pending.value.size || !inbox.value[0]) return;
  const before = inbox.value[0].sentAt;
  const ids = new Set(inbox.value.map(item => item.id));
  epoch++; readingAll.value = true;
  try {
    await readAllInbox(before);
    if (!disposed) { inbox.value.forEach(item => { if (ids.has(item.id)) item.unread = false; }); ElMessage.success('已全部标为已读'); }
  } catch { /* Preserve unread status on failure. */ }
  finally { readingAll.value = false; }
  await refresh();
}
async function visit(item: InboxMessage) {
  if (!await mark(item) || disposed) return;
  const generation = generationNoticeRoute(item);
  if (generation) { open.value = false; await router.push(generation); return; }
  const target = inboxPresentation(item).destination;
  if(target === 'contentEdit' && item.bizId){open.value=false;await router.push({path:`/campaigns/${encodeURIComponent(item.bizId)}/edit`});return;}
  if(target === 'draft' && item.bizId){open.value=false;await router.push({path:'/campaigns/create',query:{draftId:item.bizId}});return;}
  if (target === 'optimization') { open.value = false; await router.push({ path: '/monitoring', query: { tab: 'history', ...(item.bizId ? { planId: item.bizId } : {}) } }); return; }
  if (target === 'plan' && item.bizId) {
    try { await getPlan(item.bizId); if (!disposed) { open.value = false; await router.push(`/campaigns/${encodeURIComponent(item.bizId)}`); } }
    catch { if (!disposed) { ElMessage.warning('关联计划已不存在或无权查看'); open.value = false; await router.push('/campaigns'); } }
  } else if (target) {
    open.value = false;
    await router.push(target === 'creative' ? '/creatives' : target === 'authorization' ? '/accounts?tab=oauth' : item.bizType === 'PLAN_DAILY_REPORT' && item.bizId ? `/analytics?plan=${encodeURIComponent(item.bizId)}` : '/analytics');
  }
}
function close() { open.value = false; bell.value?.$el.focus(); }
async function tabKey(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault(); tab.value = event.key === 'Home' ? 'all' : event.key === 'End' ? 'unread' : tab.value === 'all' ? 'unread' : 'all';
  await nextTick(); document.getElementById(`notification-${tab.value}`)?.focus();
}
function onVisibility() { if (!document.hidden) { void refresh(); if (open.value) void refreshPlans(); } }
watch(open, async value => { if (value) { void refresh(); void refreshPlans(); await nextTick(); panel.value?.focus(); } });
watch(() => route.fullPath, () => { open.value = false; void refresh(); });
onMounted(() => { void refresh(); pollTimer = window.setInterval(() => { if (!document.hidden) void refresh(); }, 30000); document.addEventListener('visibilitychange', onVisibility); });
onUnmounted(() => { disposed = true; window.clearInterval(pollTimer); document.removeEventListener('visibilitychange', onVisibility); });
</script>
<style>
.el-popover.el-popper.lynx-notification-popover { box-sizing: border-box; width: min(400px, calc(100vw - 32px)) !important; padding: 0; border: 1px solid #e6e8f2; border-radius: 16px; overflow: hidden; box-shadow: 0 20px 48px -14px #1a1b2e3d, 0 3px 10px #1a1b2e0f; font-family: system-ui, -apple-system, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', sans-serif; }
@media (max-width: 639px) {
  .el-popover.el-popper.lynx-notification-popover { position: fixed !important; top: 72px !important; left: 16px !important; right: 16px !important; transform: none !important; }
}
</style>
<style scoped>
.ntc { text-align: left; color: #1a1b2e; background: #fff; outline: none; }
.ntc *, .ntc *::before, .ntc *::after { box-sizing: border-box; }
.ntc__hd { display: flex; align-items: center; gap: 9px; padding: 14px 16px 11px; }
.ntc__av { width: 28px; height: 28px; flex: none; border-radius: 50%; display: grid; place-items: center; color: white; background: linear-gradient(135deg,#575cf6,#a165ef); }
.ntc__av svg { width: 16px; height: 16px; display: block; }
.ntc__who b { display: block; font-size: 13.5px; font-weight: 600; line-height: 1.3; }
.ntc__who i { display: block; font-style: normal; font-size: 11.5px; color: #7c7f99; margin-top: 2px; }
.ntc__bar { display: flex; align-items: center; gap: 8px; padding: 0 16px 10px; border-bottom: 1px solid #e6e8f2; }
.ntc__tabs { display: flex; gap: 3px; background: #f7f8fe; border-radius: 9px; padding: 3px; }
.ntc__tab { border: 0; background: transparent; border-radius: 7px; padding: 5px 13px; font: inherit; font-size: 13px; font-weight: 500; color: #4e5069; cursor: pointer; line-height: 1.4; display: inline-flex; align-items: center; gap: 5px; }
.ntc__tab.is-on { background: white; color: #4348db; font-weight: 600; box-shadow: 0 1px 3px #1a1b2e1f; }
.ntc__tab-n { font-size: 11px; font-weight: 600; font-style: normal; line-height: 1; padding: 2px 5px; border-radius: 999px; background: #575cf6; color: white; }
.ntc__allread { margin-left: auto; min-height: 28px; padding: 4px 0; border: 0; border-radius: 8px; background: transparent; color: #575cf6; font:inherit; font-size:12px; cursor: pointer; }
.ntc__allread svg { width: 17px; height: 17px; }
.ntc__allread:disabled { color: #c4c5cd; cursor: default; }
.ntc__filters { display: flex; align-items: center; gap: 8px; padding: 10px 16px; border-bottom: 1px solid #e6e8f2; font-size: 12px; color: #4e5069; }
.ntc__filters select { min-width: 0; flex: 1; border: 1px solid #e6e8f2; border-radius: 7px; padding: 5px 8px; background: white; color: #4e5069; font: inherit; }
.ntc__filters select:focus-visible { outline: 2px solid #575cf6; outline-offset: 2px; }
.ntc__list { max-height: min(580px, calc(100dvh - 270px)); overflow-y: auto; overscroll-behavior: contain; scrollbar-width: thin; }
.ntc__item { display: flex; gap: 9px; padding: 13px 16px; }
.ntc__item:hover { background: #f7f8fe; }
.ntc__item + .ntc__item { border-top: 1px solid #e6e8f2; }
.ntc__dot { width: 6px; height: 6px; border-radius: 50%; flex: none; margin-top: 7px; background: transparent; }
.is-unread .ntc__dot { background: #575cf6; }
.ntc__main { min-width: 0; flex: 1; }
.ntc__meta { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; }
.ntc__meta b { font-size: 12.5px; font-weight: 600; }
.ntc__kind { font-size: 11px; line-height: 1.5; padding: 1px 6px; border-radius: 5px; font-weight: 500; background: #eeeefe; color: #4348db; }
.ntc__kind--material { background: #fff2e6; color: #c2701a; }
.ntc__kind--authorization { background: #e8f5fe; color: #1b7fd4; }
.ntc__kind--warning { background: #e6f7ee; color: #0d7a4c; }
.ntc__meta time { margin-left: auto; font-size: 11.5px; color: #7c7f99; flex: none; }
.ntc__message { display: block; width: 100%; padding: 0; border: 0; background: transparent; text-align: left; font: inherit; cursor: pointer; }
.ntc__txt { margin: 5px 0 0; font-size: 12.5px; line-height: 1.72; color: #4e5069; overflow-wrap: anywhere; }
.ntc__txt b { color: #1a1b2e; font-weight: 600; }
.ntc__ops { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 9px; }
.ntc__op { display: inline-flex; align-items: center; gap:5px; min-height: 26px; padding: 0; border: 0; background: transparent; font: inherit; font-size: 12px; font-weight: 500; color: #575cf6; cursor: pointer; }
.ntc__op--primary { background: #575cf6; border-color: #575cf6; color: white; }
.ntc__op:hover { border-color: #575cf6; color: #4348db; background: #f7f8fe; }
.ntc__op--primary:hover { background: #4a4fee; color: white; }
.ntc__empty { padding: 40px 16px; text-align: center; }
.ntc__empty .el-icon { font-size: 30px; color: #c4c5cd; margin-bottom: 10px; }
.ntc__empty p { margin: 0; font-size: 13px; color: #7c7f99; line-height: 1.7; }
.ntc__ft { border-top: 1px solid #e6e8f2; padding: 9px 16px; text-align: center; color: #7c7f99; font-size: 11.5px; }
.ntc__error { padding: 9px 16px; font-size: 12px; color: #c2701a; background: #fff2e6; }
.ntc__error button { border: 0; background: transparent; color: #4348db; font: inherit; margin-left: 8px; cursor: pointer; }
.ntc button:focus-visible { outline: 2px solid #575cf6; outline-offset: 2px; }
.ntc button:disabled { opacity: .6; cursor: not-allowed; }
.ntc__close{margin-left:auto;align-self:flex-start;border:0;background:transparent;color:#7c7f99;font:inherit;font-size:22px;line-height:24px;width:28px;height:28px;cursor:pointer}
.ntc__title{margin:6px 0 0;font-size:14px;font-weight:650;line-height:1.6;color:#1a1b2e}
.ntc__plan{margin:3px 0 0;color:#7c7f99;font-size:12px;line-height:1.6;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow-wrap:anywhere}
.ntc__outcome{display:inline-grid;place-items:center;border:1.5px solid currentColor;border-radius:50%;width:15px;height:15px;font-size:11px;line-height:1;font-weight:650;color:#17a865}
.ntc__outcome--FAILED{color:#e74e5f}.ntc__outcome--PARTIAL{color:#d58b13}
</style>
