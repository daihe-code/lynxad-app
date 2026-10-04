<template>
  <div class="page">
    <div class="title-row">
      <h1>广告投放管理</h1>
      <div class="tabs">
        <button class="chip active">抖音·巨量千川广告</button>
        <button class="chip disabled" disabled>抖音·巨量本地推广告 · 即将上线</button>
      </div>
    </div>

    <el-alert v-if="!user.trialActive" type="warning" show-icon :closable="false" title="试用已结束，工作台只读。仍可查看历史计划与报表。" />

    <div class="metrics">
      <article class="page-card metric">
        <span>总广告费用</span>
        <strong>{{ yuan(totals.cost) }}</strong>
      </article>
      <article class="page-card metric">
        <span>总成交金额</span>
        <strong>{{ yuan(totals.gmv) }}</strong>
      </article>
      <article class="page-card metric">
        <span>综合支付 ROI</span>
        <strong>{{ roiText(totals.roi) }}</strong>
      </article>
      <article class="page-card metric">
        <span>计划数</span>
        <strong>{{ page.total }}</strong>
      </article>
    </div>

    <div class="page-card table-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" :disabled="!user.trialActive" @click="$router.push('/campaigns/create')">+ 创建投放计划</el-button>
          <el-button text @click="$router.push('/analytics')">数据分析</el-button>
        </div>
        <el-input v-model="keyword" clearable placeholder="搜索计划名称 / 计划 ID" style="width: 280px" />
      </div>
      <el-table :data="filtered" v-loading="loading">
        <el-table-column label="计划信息" min-width="240">
          <template #default="{ row }">
            <div class="plan-name">
              <strong>{{ row.name }}</strong>
              <el-tag v-if="row.lingtou" size="small" effect="dark">灵投</el-tag>
              <el-tag v-if="row.source === 'EXTERNAL'" size="small">外部</el-tag>
            </div>
            <p class="muted">ID {{ row.qcPlanId || row.planId }}</p>
          </template>
        </el-table-column>
        <el-table-column label="计划状态" width="110">
          <template #default="{ row }">{{ PLAN_STATUS[row.status] || row.status }}</template>
        </el-table-column>
        <el-table-column label="消耗" width="120">
          <template #default="{ row }">{{ yuan(row.costYuan) }}</template>
        </el-table-column>
        <el-table-column label="成交金额" width="120">
          <template #default="{ row }">{{ yuan(row.gmvYuan) }}</template>
        </el-table-column>
        <el-table-column label="支付 ROI" width="100">
          <template #default="{ row }">{{ roiText(row.roi) }}</template>
        </el-table-column>
        <el-table-column label="告警" min-width="160">
          <template #default="{ row }">
            <span
              v-if="alertsOf(row.planId).length"
              class="alert-link"
              @click="$router.push(`/campaigns/${row.planId}`)"
            >
              <el-tag v-for="t in alertsOf(row.planId)" :key="t" size="small" type="danger" class="alert-tag">
                {{ ruleLabel(t) }}
              </el-tag>
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button text @click="$router.push(`/campaigns/${row.planId}`)">查看详情</el-button>
            <el-button text disabled v-if="row.source === 'EXTERNAL'">不可改</el-button>
            <el-button
              v-if="row.status === 'DELIVERING' && user.trialActive && row.source !== 'EXTERNAL'"
              text
              type="danger"
              @click="onAct(row.planId, 'DISABLE')"
            >
              暂停
            </el-button>
            <el-button
              v-if="row.status === 'PAUSED' && user.trialActive && row.source !== 'EXTERNAL'"
              text
              @click="onAct(row.planId, 'ENABLE')"
            >
              恢复
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="page.total"
          :page-size="page.pageSize"
          :current-page="page.pageNum"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { actPlan, listPlans, type PlanListVo } from "@/features/delivery/deliveryApi";
import { listActions, ruleLabel } from "@/features/agentops/agentopsApi";
import { useUserStore } from "@/stores/user";
import { listInsightReports, sumAccountRows } from "@/features/insight/insightApi";
import { PLAN_STATUS, roiText, yuan } from "@/utils/labels";

const user = useUserStore();
const loading = ref(false);
const keyword = ref("");
const records = ref<PlanListVo[]>([]);
const openByPlan = ref<Record<string, string[]>>({});
const page = reactive({ total: 0, pageNum: 1, pageSize: 20 });

function alertsOf(planId: string): string[] {
  return openByPlan.value[planId] || [];
}

const filtered = computed(() => {
  const q = keyword.value.trim();
  if (!q) return records.value;
  return records.value.filter((r) => r.name.includes(q) || r.planId.includes(q) || (r.qcPlanId || "").includes(q));
});

const cardTotals = ref({ cost: 0, gmv: 0, roi: 0 });
const totals = computed(() => cardTotals.value);

async function load(pageNum = 1) {
  loading.value = true;
  try {
    const [res, actions] = await Promise.all([listPlans(pageNum, page.pageSize), listActions().catch(() => [])]);
    records.value = res.records || [];
    page.total = res.total || 0;
    page.pageNum = res.pageNum || pageNum;
    page.pageSize = res.pageSize || 20;
    const grouped: Record<string, string[]> = {};
    for (const row of actions || []) {
      if (!row.open) continue;
      const list = grouped[row.planId] || [];
      if (!list.includes(row.type)) list.push(row.type);
      grouped[row.planId] = list;
    }
    openByPlan.value = grouped;
    const accounts = await listInsightReports("account");
    cardTotals.value = sumAccountRows(accounts || []);
  } finally {
    loading.value = false;
  }
}

async function onAct(id: string, action: "ENABLE" | "DISABLE" | "FINISH") {
  await actPlan(id, action);
  ElMessage.success("已更新");
  await load(page.pageNum);
}

onMounted(() => load(1));
</script>

<style scoped>
.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.tabs {
  display: flex;
  gap: 8px;
}
.chip.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin: 16px 0;
}
.metric {
  padding: 16px 20px;
}
.metric span {
  color: var(--color-muted);
}
.metric strong {
  display: block;
  font-size: 24px;
  margin-top: 6px;
}
.table-card {
  padding: 16px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}
.toolbar-left {
  display: flex;
  gap: 8px;
  align-items: center;
}
.plan-name {
  display: flex;
  gap: 6px;
  align-items: center;
}
.muted {
  color: #9ca3af;
  margin: 4px 0 0;
  font-size: 12px;
}
.alert-link {
  cursor: pointer;
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
}
.alert-tag {
  margin-right: 0;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
@media (max-width: 900px) {
  .metrics {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
