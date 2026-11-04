<template>
  <div class="asset-center">
    <div class="ad-main mg-main">
      <div class="container-page">
        <div class="mg-title">
          <div class="mg-title__l"><h1 class="mg-title__h">素材中心</h1><span class="mg-title__bar" aria-hidden="true"></span><span class="mg-title__tag"><AssetIcon name="mountain" />抖音·巨量千川</span></div>
          <div class="mg-title__r">
            <el-popover v-model:visible="calendarOpen" trigger="click" :width="350" placement="bottom-end">
              <template #reference><button class="mg-range" type="button" aria-haspopup="dialog" aria-label="按上传日期筛选素材" @click="draftRange = range ? [...range] : defaultRange()"><AssetIcon class="mg-range__ic" name="calendar" /><span>{{ range ? `${range[0]} ~ ${range[1]}` : '全部日期' }}</span><AssetIcon class="mg-range__ic" name="caret" /></button></template>
              <div class="asset-calendar"><label>开始日期<input v-model="draftRange[0]" type="date" aria-label="开始日期" /></label><label>结束日期<input v-model="draftRange[1]" type="date" aria-label="结束日期" /></label><p v-if="dateError" role="alert">{{ dateError }}</p><div><el-button size="small" @click="allDates">全部日期</el-button><el-button size="small" @click="quickRange(1)">今天</el-button><el-button size="small" @click="quickRange(7)">近 7 天</el-button><el-button size="small" @click="quickRange(30)">近 30 天</el-button><el-button size="small" type="primary" @click="applyRange">确定</el-button></div></div>
            </el-popover>
            <button class="mg-ibtn" type="button" aria-label="刷新素材数据" :data-busy="loading" :disabled="loading" @click="reload"><AssetIcon name="refresh" /></button>
          </div>
        </div>
        <section class="mg-panel" aria-label="素材库" :aria-busy="loading">
          <div class="mg-panel__pad pb-[26px]">
            <div class="ac-tabs" role="tablist" aria-label="素材库分组"><button v-for="tab in tabs" :id="`asset-tab-${tab.key}`" :key="tab.key" class="ac-tab" type="button" role="tab" :aria-selected="type === tab.key" aria-controls="asset-results" :tabindex="type === tab.key ? 0 : -1" @click="pickTab(tab.key)" @keydown="tabKey($event, tab.key)"><AssetIcon :name="tab.icon" />{{ tab.label }}<span class="ac-tab__n">{{ data.tabCounts[tab.count] }}</span></button></div>
            <div class="ac-bar">
              <div class="lsearch"><AssetIcon class="lsearch__ic h-[16px] w-[16px]" name="search" /><input v-model="q" class="lsearch__input" type="search" placeholder="搜索素材名称 / ID" aria-label="搜索素材名称或 ID" @input="searchChanged" @keydown.enter="resetAndReload" /></div>
              <div class="ac-chips" role="group" aria-label="按使用状态筛选"><button v-for="chip in statuses" :key="chip.key" class="ac-chip" type="button" :aria-pressed="status === chip.key" @click="pickStatus(chip.key)">{{ chip.label }}<span class="ac-chip__n">{{ data.statusCounts[chip.count] ?? '—' }}</span></button></div>
              <div class="ac-bar__r"><button class="btn-create" type="button" @click="openModal('upload')"><AssetIcon name="upload" />上传素材</button></div>
            </div>
            <div class="ac-batch" :data-on="picked.size > 0"><label class="check"><input class="checkbox" type="checkbox" aria-label="全选本页素材" :checked="allPicked" :indeterminate="somePicked && !allPicked" :disabled="!data.items.length || loading || deleting" @change="pickAll" /><span class="check__label">全选本页</span></label><span class="ac-batch__n">已选 <b>{{ picked.size }}</b> 项素材</span><span class="ac-batch__hint">勾选素材后即可批量删除</span><div class="ac-batch__ops"><button class="ltool__btn ltool__btn--danger" type="button" :disabled="!picked.size || loading || deleting" @click="openDelete"><AssetIcon name="trash" />批量删除</button></div></div>
            <p v-if="!data.qianchuanBound" class="asset-notice">尚未绑定巨量千川账户，当前展示本平台素材。<router-link to="/accounts?tab=oauth">前往绑定</router-link></p>
            <p v-if="data.platformWarning" class="asset-notice">{{ data.platformWarning }}</p>
            <p v-if="failMsg" class="asset-notice asset-notice--error" role="alert">{{ failMsg }} <button type="button" @click="reload">重试</button></p>
            <div id="asset-results" class="ac-grid" role="tabpanel" :aria-labelledby="`asset-tab-${type}`">
              <div v-if="!data.items.length" class="nodata" style="grid-column: 1 / -1" role="status"><span class="nodata__ic"><AssetIcon name="search" /></span><p class="nodata__t">{{ loading ? '正在加载素材' : failMsg ? '素材加载失败' : '没有匹配的素材' }}</p><p class="nodata__d">{{ loading ? '请稍候…' : '试着换个关键词或放宽筛选条件；也可以在新建计划时让灵投按商品信息智能生成素材。' }}</p></div>
              <AssetCard v-for="(item, index) in data.items" :key="item.id" :item="item" :tone="index % 8" :picked="picked.has(item.id)" :busy="loading" @pick="pickOne(item.id)" @preview="openModal('preview', item)" @detail="openModal('detail', item)" />
            </div>
            <div class="ac-pager pager"><span class="tnum">共 {{ data.pageInfo.total }} 条素材</span><button class="pager__btn" type="button" aria-label="上一页" :disabled="page <= 1 || loading" @click="goPage(page - 1)"><AssetIcon class="h-4 w-4 asset-flip" name="chevron-right" /></button><div class="flex items-center gap-3"><template v-for="n in visiblePages" :key="n"><span v-if="typeof n === 'string'">…</span><button v-else class="pager__num" :class="{ 'pager__num--on': n === page }" type="button" :aria-current="n === page ? 'page' : undefined" :disabled="loading" @click="goPage(n)">{{ n }}</button></template></div><button class="pager__btn" type="button" aria-label="下一页" :disabled="page >= totalPages || loading" @click="goPage(page + 1)"><AssetIcon class="h-4 w-4" name="chevron-right" /></button></div>
          </div>
        </section>
      </div>
    </div>
    <footer class="mg-foot"><div class="container-page mg-foot__in"><p class="mg-foot__c">© 2026 灵投智营 · 广告智能投放平台</p><nav class="mg-foot__l" aria-label="页脚导航"><a class="mg-foot__a" href="#" @click.prevent="ElMessage.info('帮助中心即将上线')">帮助中心</a><router-link class="mg-foot__a" to="/legal/terms">使用指南</router-link><router-link class="mg-foot__a" to="/legal/privacy">隐私政策</router-link></nav></div></footer>
    <AssetModal v-if="modal" :kind="modal" :item="current" :library-type="type" @close="closeModal" @switch="switchModal" @changed="reload" />
    <AssetDeleteDialog v-if="deleteItems.length" :items="deleteItems" :busy="deleting" :error="deleteError" @close="closeDelete" @confirm="confirmDelete" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from 'pinia';
import { useAdvertisingAccountsStore } from '@/stores/advertisingAccounts';
import { ElMessage } from "element-plus";
import { deleteLibraryItems, listLibraryFull, type LibraryItemVo, type LibraryResponse } from "@/features/creative/creativeApi";
import AssetIcon from "./AssetIcon.vue";
import AssetCard from "./AssetCard.vue";
import AssetModal from "./AssetModal.vue";
import AssetDeleteDialog from './AssetDeleteDialog.vue';
import { uploadRows, reviewPending } from '@/features/creative/assetUploads';
import { carouselDraft } from '@/features/creative/carouselUploads';

type AssetType = LibraryItemVo["type"];
type Status = "ALL" | "USED" | "UNUSED" | "REVIEWING" | "REJECTED";
type Modal = "upload" | "detail" | "preview";
const tabs = [{ key: "VIDEO", label: "视频库", icon: "video", count: "video" }, { key: "IMAGE", label: "图片库", icon: "image", count: "image" }, { key: "CAROUSEL", label: "图文库", icon: "note", count: "carousel" }] as const;
const statuses = [{ key: "ALL", label: "全部", count: "all" }, { key: "USED", label: "已使用", count: "used" }, { key: "UNUSED", label: "未使用", count: "unused" }, { key: "REVIEWING", label: "审核中", count: "reviewing" }, { key: "REJECTED", label: "审核未通过", count: "rejected" }] as const;
function defaultRange(days = 7): [string, string] {
  const end = new Date(), start = new Date(); start.setDate(end.getDate() - days + 1);
  const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  return [fmt(start), fmt(end)];
}
const range = ref<[string, string] | null>(null), draftRange = ref<[string, string]>(defaultRange());
const { accountId } = storeToRefs(useAdvertisingAccountsStore());
const calendarOpen = ref(false), dateError = ref("");
const type = ref<AssetType>("VIDEO"), status = ref<Status>("ALL"), q = ref("");
const data = ref<LibraryResponse>({ qianchuanBound: true, tabCounts: { video: 0, image: 0, carousel: 0, audio: 0, text: 0 }, statusCounts: { all: 0, used: 0, unused: 0, reviewing: 0, rejected: 0 }, pageInfo: { page: 1, pageSize: 8, total: 0 }, items: [] });
const page = ref(1), loading = ref(false), failMsg = ref("");
const picked = ref(new Set<string>()), modal = ref<Modal | null>(null), current = ref<LibraryItemVo | null>(null);
const deleteItems = ref<LibraryItemVo[]>([]), deleting = ref(false), deleteError = ref('');
let deleteAccount: string | undefined, alive = true;
let searchTimer: ReturnType<typeof setTimeout> | undefined, requestId = 0, previousFocus: HTMLElement | null = null;
let pollTimer: ReturnType<typeof setInterval> | undefined;
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.pageInfo.total / 8)));
const visiblePages = computed(() => {
  const result: (number | string)[] = []; let last = 0;
  for (let n = 1; n <= totalPages.value; n++) if (n === 1 || n === totalPages.value || Math.abs(n - page.value) <= 1) { if (n - last > 1) result.push(`gap-${n}`); result.push(n); last = n; }
  return result;
});
const allPicked = computed(() => data.value.items.length > 0 && data.value.items.every(i => picked.value.has(i.id)));
const somePicked = computed(() => data.value.items.some(i => picked.value.has(i.id)));
watch(() => uploadRows.map(r => `${r.result?.id}:${r.result?.aiReview?.status}:${r.result?.syncs.map(s => s.status).join(',')}`).join(';'), () => { void reload(); });
watch(() => carouselDraft.result, () => { void reload(); });
async function reload() {
  clearTimeout(searchTimer); const id = ++requestId; loading.value = true; failMsg.value = "";
  try {
    const result = await listLibraryFull({ type: type.value, platform: "QIANCHUAN", accountId: accountId.value || undefined, from: range.value?.[0], to: range.value?.[1], status: status.value, q: q.value.trim(), page: page.value, pageSize: 8 });
    if (id !== requestId) return;
    data.value = result;
    if (current.value) current.value = result.items.find(item => item.id === current.value?.id) || current.value;
    if (page.value > totalPages.value) { page.value = totalPages.value; await reload(); }
  } catch (error) {
    if (id !== requestId) return;
    failMsg.value = error instanceof Error ? error.message : "素材库查询失败";
    data.value = { ...data.value, items: [], pageInfo: { page: page.value, pageSize: 8, total: 0 } }; picked.value.clear();
  } finally { if (id === requestId) loading.value = false; }
}
function resetAndReload() { clearTimeout(searchTimer); page.value = 1; picked.value.clear(); void reload(); }
watch(accountId, () => { closeModal(); deleteItems.value = []; deleteError.value = ''; resetAndReload(); });
function searchChanged() { clearTimeout(searchTimer); ++requestId; searchTimer = setTimeout(resetAndReload, 300); }
function pickTab(key: AssetType) { type.value = key; status.value = "ALL"; resetAndReload(); }
function pickStatus(key: Status) { status.value = key; resetAndReload(); }
function goPage(n: number) { page.value = Math.max(1, Math.min(n, totalPages.value)); void reload(); }
function pickOne(id: string) { if (picked.value.has(id)) picked.value.delete(id); else picked.value.add(id); }
function pickAll() { const remove = allPicked.value; data.value.items.forEach(i => remove ? picked.value.delete(i.id) : picked.value.add(i.id)); }
function applyRange() { const [from, to] = draftRange.value; if (!from || !to || from > to) { dateError.value = "请选择有效日期，开始日期不能晚于结束日期"; return; } range.value = [...draftRange.value]; dateError.value = ""; calendarOpen.value = false; resetAndReload(); }
function quickRange(days: number) { draftRange.value = defaultRange(days); applyRange(); }
function allDates() { range.value = null; dateError.value = ""; calendarOpen.value = false; resetAndReload(); }
async function tabKey(event: KeyboardEvent, key: AssetType) {
  const index = tabs.findIndex(t => t.key === key); let next = index;
  if (event.key === "ArrowRight") next = (index + 1) % tabs.length; else if (event.key === "ArrowLeft") next = (index + tabs.length - 1) % tabs.length; else if (event.key === "Home") next = 0; else if (event.key === "End") next = tabs.length - 1; else return;
  event.preventDefault(); pickTab(tabs[next]!.key); await nextTick(); document.getElementById(`asset-tab-${tabs[next]!.key}`)?.focus();
}
function openModal(kind: Modal, item?: LibraryItemVo) { previousFocus = document.activeElement as HTMLElement; current.value = item || null; modal.value = kind; }
function switchModal(kind: Modal) { modal.value = kind; }
async function closeModal() { modal.value = null; current.value = null; await nextTick(); if (previousFocus?.isConnected) previousFocus.focus(); }
function openDelete() {
  if (loading.value || deleting.value) return;
  deleteAccount = accountId.value || undefined;
  deleteItems.value = data.value.items.filter(item => picked.value.has(item.id));
  deleteError.value = '';
}
function closeDelete() { if (!deleting.value) { deleteItems.value = []; deleteError.value = ''; } }
async function confirmDelete() {
  if (deleting.value || !deleteItems.value.length) return;
  deleting.value = true; deleteError.value = '';
  const items = [...deleteItems.value], owner = deleteAccount;
  try {
    const result = await deleteLibraryItems(items.map(item => item.id), owner);
    if (!alive || owner !== (accountId.value || undefined)) return;
    const removed = new Set(result.deletedIds);
    removed.forEach(id => picked.value.delete(id));
    deleteItems.value = items.filter(item => !removed.has(item.id));
    if (deleteItems.value.length) {
      deleteError.value = removed.size ? `已删除 ${removed.size} 项，剩余 ${deleteItems.value.length} 项暂时无法删除，请重试。`
        : result.failures[0]?.message || '暂时无法删除，请重试。';
    } else ElMessage.success({ message: '删除成功', duration: 3000 });
    await reload();
  } catch (error) {
    if (alive && owner === (accountId.value || undefined)) deleteError.value = error instanceof Error ? error.message : '暂时无法删除，请重试。';
  } finally { if (alive) deleting.value = false; }
}
onMounted(() => { void reload(); pollTimer = setInterval(() => { if (!loading.value && (data.value.usagePending || uploadRows.some(r => r.busy) || data.value.items.some(i => i.usageKnown === false || reviewPending(i.aiReview?.status) || i.syncs?.some(s => ['QUEUED', 'RUNNING'].includes(s.status))))) void reload(); }, 5000); });
onUnmounted(() => { alive = false; ++requestId; clearTimeout(searchTimer); clearInterval(pollTimer); });
</script>

<style src="./asset-center.css"></style>
<style scoped>
.asset-flip { transform: rotate(180deg); }
.asset-notice { margin-top: 14px; padding: 10px 14px; border-radius: 10px; background: #f7f8fe; font-size: 13px; }
.asset-notice a, .asset-notice button { color: #4348db; }
.asset-notice--error { color: #e5484d; background: #fff4f4; }
</style>
<style>
.asset-calendar { font-family: system-ui, "Microsoft YaHei", sans-serif; font-size: 13px; }
.asset-calendar label { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.asset-calendar input { padding: 6px; border: 1px solid #e6e8f2; border-radius: 6px; color: #1a1b2e; }
.asset-calendar p { color: #e5484d; margin: 8px 0; }
.asset-calendar > div { display: flex; flex-wrap: wrap; gap: 6px; }
.asset-calendar .el-button + .el-button { margin-left: 0; }
</style>
