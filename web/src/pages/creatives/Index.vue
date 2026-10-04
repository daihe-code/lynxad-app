<template>
  <div class="page">
    <div class="title-row">
      <div class="left">
        <h1>素材中心</h1>
        <button class="chip active" type="button">抖音·巨量千川</button>
      </div>
      <div class="right">
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始"
          end-placeholder="结束"
          :disabled-date="() => false"
        />
        <p v-if="dateError" class="err">{{ dateError }}</p>
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>
    <div class="page-card body">
      <el-tabs v-model="type" @tab-change="onType">
        <el-tab-pane :label="`视频库 ${data.tabCounts.video}`" name="VIDEO" />
        <el-tab-pane :label="`图片库 ${data.tabCounts.image}`" name="IMAGE" />
        <el-tab-pane :label="`图文库 ${data.tabCounts.carousel}`" name="CAROUSEL" />
      </el-tabs>
      <div class="filters">
        <el-input v-model="q" placeholder="搜索名称或ID" clearable @keyup.enter="reload" @input="debounced" />
        <el-radio-group v-model="status" @change="reload">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="USED">使用中 {{ data.statusCounts.used }}</el-radio-button>
          <el-radio-button value="UNUSED">未使用 {{ data.statusCounts.unused }}</el-radio-button>
        </el-radio-group>
      </div>
      <el-alert
        v-if="!data.qianchuanBound"
        type="warning"
        show-icon
        :closable="false"
        class="hint"
      >
        尚未绑定巨量千川，平台素材不会出现。
        <router-link to="/accounts">前往账户管理授权</router-link>
      </el-alert>
      <el-alert v-if="failMsg" type="error" :title="failMsg" show-icon :closable="false" class="hint" />
      <el-empty v-else-if="data.items.length === 0" description="暂无素材。可在创建计划第三步生成，或调整筛选条件。" />
      <div v-else class="grid">
        <article v-for="item in data.items" :key="item.id" class="card" @click="preview(item)">
          <div class="thumb">
            <img v-if="item.posterUrl" :src="abs(item.posterUrl)" alt="" />
            <span class="badge src">{{ item.source === "LYNX" ? "灵投" : "非灵投" }}</span>
            <span v-if="item.reviewBadge !== 'NONE'" class="badge rev">{{ item.reviewBadge === "REVIEWING" ? "审核中" : "未通过" }}</span>
            <span v-if="item.durationSec" class="dur">{{ formatDur(item.durationSec) }}</span>
          </div>
          <strong>{{ item.filename }}</strong>
          <p>{{ formatSize(item.sizeBytes) }} · {{ item.category }} · {{ formatDay(item.createdAt) }}</p>
        </article>
      </div>
      <div class="pager">
        <el-pagination
          layout="prev, pager, next"
          :page-size="8"
          :current-page="data.pageInfo.page"
          :total="data.pageInfo.total"
          @current-change="onPage"
        />
      </div>
    </div>
    <el-dialog v-model="open" title="预览" width="480px">
      <video v-if="current?.type === 'VIDEO' && current.playUrl" :src="abs(current.playUrl)" controls style="width: 100%" />
      <img v-else-if="current?.playUrl" :src="abs(current.playUrl)" alt="" style="width: 100%" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { listLibraryFull, type LibraryItemVo, type LibraryResponse } from "@/features/creative/creativeApi";

const empty: LibraryResponse = {
  qianchuanBound: true,
  tabCounts: { video: 0, image: 0, carousel: 0 },
  statusCounts: { all: 0, used: 0, unused: 0 },
  pageInfo: { page: 1, pageSize: 8, total: 0 },
  items: [],
};

function defaultRange(): [string, string] {
  const to = new Date();
  const from = new Date();
  from.setDate(to.getDate() - 6);
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  return [fmt(from), fmt(to)];
}

const range = ref<[string, string]>(defaultRange());
const type = ref("VIDEO");
const status = ref("ALL");
const q = ref("");
const data = ref<LibraryResponse>(empty);
const failMsg = ref("");
const dateError = ref("");
const open = ref(false);
const current = ref<LibraryItemVo | null>(null);
let timer: number | undefined;

function abs(url: string) {
  if (!url) return url;
  if (url.startsWith("http") || url.startsWith("blob:")) return url;
  return (import.meta.env.VITE_API_BASE || "/lynx-ad") + url.replace(/^\/lynx-ad/, "");
}

function formatDur(sec: number) {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

function formatSize(n: number) {
  if (n < 1024) return `${n}B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)}KB`;
  return `${(n / 1024 / 1024).toFixed(1)}MB`;
}

function formatDay(iso: string) {
  const d = new Date(iso);
  const now = new Date();
  if (d.getFullYear() === now.getFullYear()) return `${d.getMonth() + 1}/${d.getDate()}`;
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`;
}

async function reload() {
  const [from, to] = range.value || [];
  if (from && to && from > to) {
    dateError.value = "开始时间不能晚于结束时间";
    return;
  }
  dateError.value = "";
  failMsg.value = "";
  try {
    data.value = await listLibraryFull({
      type: type.value,
      platform: "QIANCHUAN",
      from,
      to,
      status: status.value,
      q: q.value,
      page: data.value.pageInfo.page,
      pageSize: 8,
    });
  } catch (err: unknown) {
    const msg = typeof err === "object" && err && "message" in err ? String((err as { message: string }).message) : "查询失败";
    failMsg.value = msg;
    data.value = { ...empty, qianchuanBound: data.value.qianchuanBound };
  }
}

function onType() {
  data.value.pageInfo.page = 1;
  reload();
}

function onPage(p: number) {
  data.value.pageInfo.page = p;
  reload();
}

function debounced() {
  window.clearTimeout(timer);
  timer = window.setTimeout(() => {
    data.value.pageInfo.page = 1;
    reload();
  }, 300);
}

function preview(item: LibraryItemVo) {
  current.value = item;
  open.value = true;
}

watch(range, () => {
  data.value.pageInfo.page = 1;
  reload();
});

onMounted(reload);
</script>

<style scoped>
.page {
  padding: 8px 4px 32px;
}
.title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.left,
.right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
h1 {
  margin: 0;
  font-size: 22px;
}
.chip {
  border: 1px solid #c9c6ff;
  background: #eef0ff;
  color: #4f46e5;
  border-radius: 999px;
  padding: 4px 12px;
}
.body {
  padding: 16px 20px 28px;
  margin-top: 16px;
}
.filters {
  display: flex;
  gap: 16px;
  align-items: center;
  margin: 8px 0 16px;
}
.hint {
  margin-bottom: 12px;
}
.err {
  color: #dc2626;
  font-size: 12px;
  margin: 0;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.card {
  border: 1px solid #eef0f6;
  border-radius: 12px;
  padding: 10px;
  cursor: pointer;
  background: #fff;
}
.thumb {
  position: relative;
  height: 160px;
  border-radius: 8px;
  overflow: hidden;
  background: #f3f4f6;
  margin-bottom: 8px;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.badge {
  position: absolute;
  top: 8px;
  font-size: 11px;
  border-radius: 4px;
  padding: 1px 6px;
  color: #fff;
}
.src {
  left: 8px;
  background: #6366f1;
}
.rev {
  right: 8px;
  background: #f59e0b;
}
.dur {
  position: absolute;
  right: 8px;
  bottom: 8px;
  color: #fff;
  font-size: 12px;
}
.card p {
  color: #9ca3af;
  margin: 4px 0 0;
  font-size: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
