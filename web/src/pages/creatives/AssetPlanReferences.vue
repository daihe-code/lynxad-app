<template>
  <div class="ac-dsec">
    <div class="ac-dsec__t"><span>被引用的投放计划</span><span class="ac-dsec__h">{{ referenceLabel(item) }}</span></div>
    <div class="ac-refs">
      <router-link v-for="plan in item.references || []" :key="plan.planId" class="asset-plan-reference" :to="{ path: '/campaigns', query: { plan: plan.planId, tab: 'data' } }">
        <span>{{ plan.name }}</span><small>{{ statusLabel(plan.status) }}</small>
      </router-link>
      <div v-if="!item.references?.length" class="ac-dempty">{{ item.usageKnown === false ? '计划素材引用关系正在同步，请稍后查看。' : item.used ? '已确认被投放计划引用。' : '尚未被投放计划引用。' }}</div>
      <p v-else-if="item.usageKnown === false" class="ac-dempty">其余计划的引用关系仍在同步。</p>
    </div>
  </div>
</template>
<script setup lang="ts">
import type { LibraryItemVo } from '@/features/creative/creativeApi';
import { referenceLabel } from '@/features/creative/assetReferences';
defineProps<{ item: LibraryItemVo }>();
function statusLabel(status: string) { return ({ DELIVERING:'投放中', PAUSED:'已暂停', FINISHED:'已完成', PENDING_AUDIT:'审核中', REJECTED:'审核未通过', SUBMITTING:'提交中', UNKNOWN:'状态同步中' } as Record<string,string>)[status] || status; }
</script>
<style scoped>
.asset-plan-reference { display:flex; justify-content:space-between; align-items:center; gap:16px; padding:14px 0; border-bottom:1px solid #eeeef6; color:#5c55ff; }
.asset-plan-reference span { min-width:0; overflow-wrap:anywhere; }
.asset-plan-reference small { flex-shrink:0; color:#84849d; }
</style>
