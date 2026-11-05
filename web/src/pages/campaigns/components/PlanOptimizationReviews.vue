<template>
  <section v-if="reviews.length" class="review-panel">
    <h3>处理后的效果复评</h3>
    <article v-for="item in reviews" :key="item.id">
      <div class="review-heading"><strong>{{ label(item.workflow) }}</strong><span>{{ reviewStatus(item.status) }}</span></div>
      <small>{{ time(item.actionAt || item.createdAt) }}</small>
      <template v-if="item.status === 'COMPARED' && item.result?.before_roi != null && item.result?.after_roi != null">
        <div class="review-metrics"><div>调整前 ROI<strong>{{ number(item.result.before_roi) }}</strong></div><div>调整后 ROI<strong>{{ number(item.result.after_roi) }}</strong></div><div>变化<strong>{{ change(item.result.roi_change) }}</strong></div></div>
        <details><summary>查看比较依据</summary><p>{{ time(item.result.before_window?.window_start) }}—{{ time(item.result.before_window?.window_end) }}<br>{{ time(item.result.after_window?.window_start) }}—{{ time(item.result.after_window?.window_end) }}</p><p>前后消耗 ¥{{ number(item.result.before_spend) }} / ¥{{ number(item.result.after_spend) }}</p></details>
        <small>同口径成熟窗口的观察变化，不代表全部由本次调整带来。</small>
      </template>
      <p v-else-if="item.result?.reason">{{ item.result.reason }}</p>
    </article>
  </section>
</template>
<script setup lang="ts">
import type { OptimizationReview } from '@/features/delivery/optimizationApi';
import { reviewStatus } from '@/features/delivery/optimizationDecisions';
defineProps<{ reviews: OptimizationReview[] }>();
const label = (value: string) => ({ materials: '素材调整', budget: '预算调整', plan: '计划调整', data: '投放整改' }[value] || '投放调整');
const number = (value: unknown) => value != null && Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) : '—';
const change = (value: unknown) => value == null ? '—' : `${Number(value) > 0 ? '+' : ''}${number(value)}`;
const time = (value?: string | null) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—';
</script>
<style scoped>
.review-panel{border:1px solid #e1e5f5;border-radius:8px;padding:16px;margin:12px 0}.review-panel h3{font-size:16px;margin:4px 0 14px}.review-panel article{padding:12px 0;border-bottom:1px solid #eef0fa}.review-panel article:last-child{border-bottom:0}.review-heading{display:flex;justify-content:space-between;gap:12px;font-size:13px}.review-heading span,small,p{font-size:12px;color:#7782a8}small{display:block;line-height:1.8}.review-metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:12px 0;padding:12px;background:#f5f4ff;border-radius:6px;font-size:12px}.review-metrics strong{display:block;font-size:20px;margin-top:5px}details{font-size:12px;margin:8px 0}summary{color:#5947ff;cursor:pointer}p{line-height:1.8}
</style>
