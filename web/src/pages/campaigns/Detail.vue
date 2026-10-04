<template>
  <div class="page-card detail" v-loading="!plan">
    <el-button text @click="$router.push('/campaigns')">← 返回列表</el-button>
    <h1>{{ plan?.name }}</h1>
    <div class="tags">
      <el-tag v-if="plan?.lingtou">灵投</el-tag>
      <el-tag>{{ PLAN_STATUS[plan?.status || ""] || plan?.status }}</el-tag>
      <el-tag v-if="plan?.source === 'EXTERNAL'" type="info">外部计划不可改</el-tag>
    </div>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="计划 ID">{{ plan?.planId }}</el-descriptions-item>
      <el-descriptions-item label="千川计划">{{ plan?.qcPlanId || "—" }}</el-descriptions-item>
      <el-descriptions-item label="投放方式">{{ BID_TYPE[plan?.smartBidType || ""] || plan?.smartBidType || "—" }}</el-descriptions-item>
      <el-descriptions-item label="日预算">{{ yuan(plan?.budget) }}</el-descriptions-item>
      <el-descriptions-item label="抖音号">{{ plan?.awemeId }}</el-descriptions-item>
      <el-descriptions-item label="保障">{{ plan?.guarantee || "—" }}</el-descriptions-item>
      <el-descriptions-item label="消耗">{{ yuan(plan?.costYuan) }}</el-descriptions-item>
      <el-descriptions-item label="成交">{{ yuan(plan?.gmvYuan) }}</el-descriptions-item>
      <el-descriptions-item label="支付 ROI">{{ plan?.roi == null ? "—" : Number(plan.roi).toFixed(2) }}</el-descriptions-item>
      <el-descriptions-item label="拒审原因" :span="2">{{ plan?.rejectReason || "—" }}</el-descriptions-item>
    </el-descriptions>
    <div class="ops" v-if="plan && plan.source !== 'EXTERNAL' && user.trialActive">
      <el-button v-if="plan.status === 'DELIVERING'" type="danger" @click="act('DISABLE')">暂停</el-button>
      <el-button v-if="plan.status === 'PAUSED'" type="primary" @click="act('ENABLE')">恢复投放</el-button>
      <el-button v-if="plan.status === 'DELIVERING' || plan.status === 'PAUSED'" @click="act('FINISH')">结束计划</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { actPlan, getPlan, type PlanVo } from "@/features/delivery/deliveryApi";
import { useUserStore } from "@/stores/user";
import { BID_TYPE, PLAN_STATUS, yuan } from "@/utils/labels";

const route = useRoute();
const user = useUserStore();
const plan = ref<PlanVo | null>(null);

async function load() {
  plan.value = await getPlan(String(route.params.id));
}

async function act(action: "ENABLE" | "DISABLE" | "FINISH") {
  if (!plan.value) return;
  plan.value = await actPlan(plan.value.planId, action);
  ElMessage.success("已更新");
}

onMounted(load);
</script>

<style scoped>
.detail {
  padding: 24px;
}
.tags {
  display: flex;
  gap: 8px;
  margin: 12px 0 20px;
}
.ops {
  margin-top: 20px;
  display: flex;
  gap: 8px;
}
</style>
