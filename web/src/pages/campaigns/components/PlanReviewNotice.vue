<template>
  <section v-if="notice" class="plan-review" :class="{'plan-review--delayed':notice.delayed || notice.unavailable}" aria-label="平台审核进度" aria-live="polite">
    <div class="plan-review__heading"><strong>{{ notice.title }}</strong><button type="button" :disabled="refreshing || notice.cooldown > 0" @click="refresh">{{ refreshing ? '查询中…' : notice.cooldown > 0 ? `${notice.cooldown} 秒后可刷新` : '刷新审核结果' }}</button></div>
    <p>{{ notice.message }}</p>
    <p>{{ notice.waiting }}。等待时间从系统首次记录本轮审核起计算。</p>
    <p>最近查询：{{ time(plan.review?.lastCheckedAt) }}<template v-if="plan.review?.nextCheckAt"> · 下次后台查询预计 {{ time(plan.review.nextCheckAt) }}</template></p>
    <p v-if="!plan.review?.pendingSince">历史草稿创建时间不计入本轮审核等待时间。</p>
    <p v-if="notice.unavailable">查询失败或未返回计划时，不会将计划改为审核拒绝。</p>
    <p v-if="feedback" role="status">{{ feedback }}</p>
  </section>
</template>
<script setup lang="ts">
import type { PlanVo } from '@/features/delivery/deliveryApi';
import { reviewTime } from '@/features/delivery/planReview';
import { usePlanReview } from '@/features/delivery/usePlanReview';
const props=defineProps<{plan:Pick<PlanVo,'planId'|'status'|'qcPlanId'|'review'>;autoRead?:boolean}>();
const emit=defineEmits<{updated:[plan:PlanVo]}>();
const { notice, refreshing, feedback, refresh } = usePlanReview(() => props.plan, plan => emit('updated', plan), () => !!props.autoRead);
const time = (value?: string | null) => reviewTime(value, '尚未查询');
</script>
<style scoped>
.plan-review{margin:16px 0;padding:14px 16px;border:1px solid #dedff7;border-radius:12px;background:#f5f5ff;color:#535a78;font-size:13px;line-height:1.7;overflow-wrap:anywhere;}
.plan-review--delayed{background:#fff8ed;border-color:#f5d9ac;}
.plan-review__heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;}
.plan-review__heading strong{font-size:14px;}
.plan-review p{margin:5px 0 0;font-size:13px;line-height:1.7;}
.plan-review button{border:1px solid #d4d4ef;border-radius:8px;padding:5px 10px;background:#fff;color:#5750d3;font-size:12px;cursor:pointer;}
.plan-review button:disabled{cursor:default;opacity:.55;}
</style>
