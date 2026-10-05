<template>
  <div class="analytics-page">
    <div class="plan-selector">
      <label for="analytics-plan">投放效果数据分析</label
      ><select
        id="analytics-plan"
        :value="selected"
        aria-label="选择分析计划"
        @change="pick(($event.target as HTMLSelectElement).value)"
      >
        <option value="">请选择投放计划</option>
        <option v-for="p in plans" :key="p.planId" :value="p.planId">
          {{ p.name }}
        </option>
      </select>
    </div>
    <PlanAnalysis v-if="selected" :plan-id="selected" />
    <div v-else class="analysis-empty">
      <p>
        {{
          failed
            ? "计划列表加载失败"
            : loading
              ? "正在加载计划列表…"
              : "暂无可分析的投放计划"
        }}
      </p>
      <button v-if="failed" @click="load">重试</button
      ><router-link v-else-if="!loading" to="/campaigns/manage/create"
        >创建投放计划</router-link
      >
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { listPlans, type PlanListVo } from "@/features/delivery/deliveryApi";
import PlanAnalysis from "@/pages/campaigns/reference/PlanAnalysis.vue";
const route = useRoute(),
  router = useRouter(),
  plans = ref<PlanListVo[]>([]),
  loading = ref(false),
  failed = ref(false);
const selected = computed(() =>
  plans.value.some((p) => p.planId === route.query.plan)
    ? String(route.query.plan)
    : "",
);
function pick(value: string) {
  void router.replace({ query: { ...route.query, plan: value } });
}
async function load() {
  loading.value = true;
  failed.value = false;
  try {
    const all: PlanListVo[] = [];
    for (let page = 1; page <= 100; page++) {
      const result = await listPlans(page, 100);
      all.push(...result.records.filter((p) => p.source !== "EXTERNAL"));
      if (page * 100 >= result.total) break;
    }
    plans.value = all;
    if (!selected.value && all[0]) pick(all[0].planId);
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<style scoped>
.analytics-page {
  background: #f7f7ff;
  min-height: calc(100vh - 60px);
}
.plan-selector {
  max-width: 1128px;
  padding: 28px 24px 0;
  margin: auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  color: #1a1b2e;
}
.plan-selector label {
  font-size: 18px;
  font-weight: 600;
}
.plan-selector select {
  border: 1px solid #e6e8f2;
  border-radius: 8px;
  padding: 10px 12px;
  font: inherit;
  font-size: 14px;
  background: #fff;
  max-width: 480px;
  width: 50%;
}
.analysis-empty {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 20px;
  color: #7c7f99;
}
.analysis-empty a {
  color: #575cf6;
}
@media (max-width: 639px) {
  .plan-selector {
    flex-wrap: wrap;
  }
  .plan-selector select {
    width: 100%;
  }
}
</style>
