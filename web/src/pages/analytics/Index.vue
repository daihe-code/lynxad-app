<template>
  <div class="page">
    <div class="title-row">
      <div>
        <h1>数据分析</h1>
        <p class="muted">账户汇总与计划明细均读取最新快照，金额已是元。</p>
      </div>
      <div class="ops">
        <el-select v-model="selectedPlanIds" multiple clearable collapse-tags placeholder="按计划筛选" style="min-width: 240px">
          <el-option v-for="row in planOptions" :key="row.id" :label="row.name" :value="row.id" />
        </el-select>
        <el-button @click="onExport">导出报表</el-button>
      </div>
    </div>

    <section class="page-card">
      <h2>账户汇总</h2>
      <el-table :data="accountRows" v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="消耗" width="120">
          <template #default="{ row }">{{ yuan(row.cost) }}</template>
        </el-table-column>
        <el-table-column label="成交" width="120">
          <template #default="{ row }">{{ yuan(row.gmv) }}</template>
        </el-table-column>
        <el-table-column label="ROI" width="90">
          <template #default="{ row }">{{ roiText(row.roi) }}</template>
        </el-table-column>
        <el-table-column prop="impression" label="展示" width="90" />
        <el-table-column prop="click" label="点击" width="80" />
        <el-table-column prop="convert" label="转化" width="80" />
        <el-table-column prop="snapshotTs" label="快照时间" min-width="180" />
      </el-table>
    </section>

    <section class="page-card">
      <h2>计划明细</h2>
      <el-table :data="planRows" v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="消耗" width="120">
          <template #default="{ row }">{{ yuan(row.cost) }}</template>
        </el-table-column>
        <el-table-column label="成交" width="120">
          <template #default="{ row }">{{ yuan(row.gmv) }}</template>
        </el-table-column>
        <el-table-column label="ROI" width="90">
          <template #default="{ row }">{{ roiText(row.roi) }}</template>
        </el-table-column>
        <el-table-column prop="impression" label="展示" width="90" />
        <el-table-column prop="click" label="点击" width="80" />
        <el-table-column prop="convert" label="转化" width="80" />
        <el-table-column prop="snapshotTs" label="快照时间" min-width="180" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { exportInsightReports, listInsightReports, type ReportRowVo } from "@/features/insight/insightApi";
import { roiText, yuan } from "@/utils/labels";

const loading = ref(false);
const accountRows = ref<ReportRowVo[]>([]);
const planRows = ref<ReportRowVo[]>([]);
const planOptions = ref<ReportRowVo[]>([]);
const selectedPlanIds = ref<string[]>([]);

async function load() {
  loading.value = true;
  try {
    const adIds = selectedPlanIds.value.length > 0 ? selectedPlanIds.value : undefined;
    const [accounts, plans] = await Promise.all([
      listInsightReports("account", adIds),
      listInsightReports("plan", adIds),
    ]);
    accountRows.value = accounts || [];
    planRows.value = plans || [];
    if (planOptions.value.length === 0) {
      planOptions.value = plans || [];
    }
  } finally {
    loading.value = false;
  }
}

async function onExport() {
  await exportInsightReports();
}

watch(selectedPlanIds, () => {
  void load();
});

onMounted(load);
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.title-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.ops {
  display: flex;
  gap: 8px;
  align-items: center;
}
.page-card {
  padding: 16px 20px;
}
.page-card h2 {
  margin: 0 0 12px;
  font-size: 16px;
}
.muted {
  color: var(--color-muted);
  margin: 4px 0 0;
}
</style>
