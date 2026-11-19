<template>
  <section class="point-ledger" aria-label="积分流水">
    <div class="ledger-filters">
      <el-select v-if="!subjectAccount" v-model="account" clearable placeholder="全部归属账户" aria-label="按归属账户筛选">
        <el-option v-for="item in accounts" :key="item.userId" :label="accountLabel(item)" :value="item.userId" />
      </el-select>
      <el-select v-model="kind" clearable placeholder="全部流水类型" aria-label="按流水类型筛选">
        <el-option v-for="(label, value) in pointKinds" :key="value" :label="label" :value="value" />
      </el-select>
      <el-date-picker v-if="showDateFilter" v-model="range" type="daterange" value-format="YYYY-MM-DD"
        start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" :editable="false" aria-label="按日期筛选" />
      <el-button :loading="loading" @click="load(page)">刷新</el-button>
      <span v-if="netConsumed != null" aria-live="polite">{{ range ? '期间净消耗' : '累计净消耗' }} <strong>{{ formatLedgerPoints(netConsumed) }}</strong> 积分</span>
    </div>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <el-table v-loading="loading" :data="records" row-key="id" empty-text="暂无积分记录">
      <el-table-column label="时间（北京时间）" width="180">
        <template #default="{ row }">{{ time.format(new Date(row.createdAt)) }}</template>
      </el-table-column>
      <el-table-column label="类型" min-width="105">
        <template #default="{ row }">{{ pointKinds[row.kind as PointKind] || '其他记录' }}</template>
      </el-table-column>
      <el-table-column v-if="!subjectAccount" label="归属账户" min-width="165" show-overflow-tooltip>
        <template #default="{ row }">{{ sourceLabel(row) }}</template>
      </el-table-column>
      <el-table-column label="业务明细" min-width="235">
        <template #default="{ row }">
          <div v-if="ledgerTaskName(row.reference)">{{ ledgerTaskName(row.reference) }}</div>
          <div v-if="row.advertiserId" class="ledger-context">广告账户 ID {{ row.advertiserId }}</div>
          <div v-for="item in row.items" :key="item.unit" class="ledger-context">{{ ledgerItemLabel(item) }}</div>
          <span v-if="!ledgerTaskName(row.reference) && !row.advertiserId && !row.items?.length">—</span>
        </template>
      </el-table-column>
      <el-table-column label="积分数量" min-width="100" align="right">
        <template #default="{ row }">{{ formatLedgerPoints(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="可用积分变动" min-width="115" align="right">
        <template #default="{ row }">{{ formatLedgerPoints(row.availableChange, true) }}</template>
      </el-table-column>
      <el-table-column label="订单 / 任务编号" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reference || '—' }}</template>
      </el-table-column>
    </el-table>
    <div v-if="page > 1 || hasMore" class="ledger-pages">
      <el-button :disabled="page === 1 || loading" @click="load(page - 1)">上一页</el-button>
      <span>第 {{ page }} 页</span>
      <el-button :disabled="!hasMore || loading" @click="load(page + 1)">下一页</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { formatLedgerPoints, ledgerItemLabel, ledgerTaskName, pointKinds, type PointKind, type PointLedgerAccount, type PointLedgerEntry, type PointLedgerPage, type PointLedgerQuery } from '@/features/billing/pointLedger';

const props = defineProps<{ fetchPage: (query: PointLedgerQuery) => Promise<PointLedgerPage>; refreshKey?: number; subjectAccount?: string; showDateFilter?: boolean }>();
const account = ref<string>();
const kind = ref<PointKind>();
const range = ref<[string, string] | null>(null);
const netConsumed = ref<string | null>(null);
const accounts = ref<PointLedgerAccount[]>([]);
const records = ref<PointLedgerEntry[]>([]);
const loading = ref(false);
const error = ref('');
const page = ref(1);
const hasMore = ref(false);
let sequence = 0;

const time = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Shanghai' });

function accountLabel(item: PointLedgerAccount) {
  const role = item.role === 'OWNER' ? '主账户' : item.role ? '子账户' : '历史成员';
  const identity = [item.name, item.phoneSuffix && `尾号 ${item.phoneSuffix}`].filter(Boolean).join(' · ');
  return `${role} · ${identity || item.userId.slice(0, 8)}`;
}

function sourceLabel(row: PointLedgerEntry) {
  if (row.account) return accountLabel(row.account);
  if (row.kind === 'ADMIN_CREDIT' || row.kind === 'ADMIN_DEBIT') return '平台管理员';
  if (row.kind === 'GRANT') return '系统';
  return '—';
}

async function load(next: number) {
  const request = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const result = await props.fetchPage({ page: next, account: props.subjectAccount || account.value || undefined,
      kind: kind.value || undefined, from: range.value?.[0], to: range.value?.[1] });
    if (request !== sequence) return;
    accounts.value = result.accounts;
    records.value = result.records;
    page.value = result.page;
    hasMore.value = result.hasMore;
    netConsumed.value = result.netConsumed ?? null;
  } catch (cause) {
    if (request === sequence) error.value = cause instanceof Error ? cause.message : '积分流水加载失败，请重试';
  } finally {
    if (request === sequence) loading.value = false;
  }
}

watch([account, kind, range, () => props.subjectAccount, () => props.refreshKey], () => {
  records.value = [];
  netConsumed.value = null;
  page.value = 1;
  hasMore.value = false;
  void load(1);
}, { immediate: true });
onBeforeUnmount(() => { sequence++; });
</script>

<style scoped>
.ledger-filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-bottom: 12px; }
.ledger-filters .el-select { width: 220px; max-width: 100%; }
.ledger-filters :deep(.el-date-editor) { max-width: 100%; }
.ledger-context { color: var(--el-text-color-secondary); font-size: 12px; }
.ledger-pages { display: flex; justify-content: flex-end; align-items: center; gap: 12px; margin-top: 16px; }
.point-ledger :deep(.el-alert) { margin-bottom: 12px; }
</style>
