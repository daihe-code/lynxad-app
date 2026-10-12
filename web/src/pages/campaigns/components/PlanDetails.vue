<template>
  <div class="plan-details">
    <div class="marketing-summary"><span>营销目标 <strong>{{ marketingGoal }}</strong></span><span>广告类型 <strong>{{ plan.deliveryPlatform === 'ALL' ? '通投广告' : plan.deliveryPlatform === 'STANDARD' ? '标准广告' : '平台未提供' }}</strong></span></div>
    <div class="settings-grid">
      <section aria-label="投放设置">
        <h3><router-link :to="{ path: `/campaigns/${plan.planId}/edit`, query: { step: '2' } }">投放设置 <span aria-hidden="true">›</span></router-link></h3>
        <dl>
          <dt>当前选择的抖音号</dt><dd class="aweme"><img v-if="account?.avatar" :src="account.avatar" alt="抖音号头像" /><span>{{ account?.awemeName || plan.awemeId || '—' }}</span></dd>
          <dt>商品</dt><dd><div v-if="products[0]" class="product"><img v-if="products[0].imageUrl" :src="products[0].imageUrl" alt="商品图片" /><span :title="products[0].name">{{ products[0].name }}</span></div><span v-else>暂无商品</span><button type="button" class="all-products" @click="emit('products')">全部商品 <span v-if="products.length">（{{ products.length }}）</span> ›</button></dd>
          <dt>优化目标</dt><dd>{{ optimizationGoal }}</dd>
          <dt>投放方式</dt><dd>{{ BID_TYPE[plan.smartBidType || ''] || '—' }}</dd>
          <dt>出价方式</dt><dd>{{ delivery.pricing_type || '平台未提供' }}</dd>
          <dt>日预算</dt><dd>每日 {{ yuan(plan.budget) }}</dd>
          <template v-if="plan.smartBidType === 'SMART_BID_CUSTOM'"><dt>净成交 ROI 目标</dt><dd>{{ roiText(plan.roi2Goal) }}</dd></template>
          <dt>投放日期</dt><dd>{{ scheduleLabel }}<small v-if="scheduleLabel !== dateRange">{{ dateRange }}</small></dd>
          <dt>广告账户</dt><dd>{{ accountName || plan.accountId || '—' }}</dd>
        </dl>
      </section>
      <section aria-label="创意设置">
        <h3>创意设置</h3>
        <dl>
          <dt>自选投放素材</dt><dd>{{ selectedMaterialLabel }}</dd>
          <dt>智能优选素材</dt><dd>{{ switchLabel(creative.smart_select_material) }}</dd>
          <dt>素材可见性</dt><dd>{{ visibilityLabel }}</dd>
          <dt>创建时间</dt><dd>{{ timeText(plan.createdAt) }}</dd>
          <template v-if="plan.rejectReason"><dt>平台反馈</dt><dd>{{ plan.rejectReason }}</dd></template>
        </dl>
        <p class="config-note" v-if="creative.smart_select_material == null">平台未返回智能优选素材开关。</p>
      </section>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { PlanVo } from '@/features/delivery/deliveryApi';
import type { CreationState } from '@/features/delivery/creationApi';
import { BID_TYPE, yuan, roiText } from '@/utils/labels';
const props = defineProps<{ plan: PlanVo; creation?: CreationState; account?: { awemeName: string; avatar?: string }; accountName?: string }>();
const emit = defineEmits<{ products: [] }>();
const native = computed(() => props.plan.platformDetail || {});
const delivery = computed(() => (native.value.delivery_setting || {}) as Record<string, any>);
const creative = computed(() => (native.value.creative_setting || {}) as Record<string, any>);
const products = computed(() => props.plan.products || []);
const marketingGoal = computed(() => ({ VIDEO_PROM_GOODS: '推商品', LIVE_PROM_GOODS: '推直播间' } as Record<string, string>)[String(native.value.marketing_goal)] || '平台未提供');
const optimizationGoal = computed(() => ({ AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI: '净成交 ROI', AD_CONVERT_TYPE_LIVE_PAY_ROI: '支付 ROI' } as Record<string, string>)[String(delivery.value.deep_external_action)] || delivery.value.deep_external_action || '平台未提供');
const dateRange = computed(() => `${props.plan.startDate || '—'} ～ ${props.plan.endDate || '—'}`);
const scheduleLabel = computed(() => delivery.value.video_schedule_type === 'SCHEDULE_FROM_NOW' ? '从计划开始日起长期投放' : dateRange.value);
const switchLabel = (value: unknown) => value === true ? '开启' : value === false ? '关闭' : '平台未提供';
const selectedMaterialLabel = computed(() => {
  const list = native.value.multi_product_creative_list;
  if (Array.isArray(list)) return list.some(p => ['video_material', 'image_material', 'carousel_material'].some(key => Array.isArray(p[key]) && p[key].length > 0)) ? '开启' : '未选择';
  return '平台未提供';
});
const visibilityLabel = computed(() => {
  const hidden = creative.value.hide_in_aweme ?? props.creation?.hideInAweme;
  return hidden === true ? '抖音主页单次可见' : hidden === false ? '抖音主页始终可见' : '平台未提供';
});
function timeText(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—'; }
</script>
<style scoped>
.marketing-summary{display:flex;flex-wrap:wrap;gap:18px 32px;background:#f6f7fb;border-radius:8px;padding:16px;color:#8b90a5;font-size:13px}.marketing-summary strong{margin-left:8px;color:#272b42;font-weight:500}.settings-grid{display:grid;grid-template-columns:1fr 1fr;gap:22px;margin-top:16px}.settings-grid section{min-width:0}.settings-grid h3{background:#f6f7fb;margin:0;font-size:14px;font-weight:600;border-radius:8px}.settings-grid h3>a{display:flex;justify-content:space-between;color:inherit;text-decoration:none;padding:16px;border-radius:8px}.settings-grid h3>a:hover{color:#5b57ff;background:#f0f0ff}.settings-grid section:nth-child(2) h3{padding:16px}.settings-grid dl{display:grid;grid-template-columns:128px minmax(0,1fr);gap:22px 8px;font-size:13px;line-height:1.8;padding:10px 16px}.settings-grid dt{color:#8b90a5}.settings-grid dd{margin:0;overflow-wrap:anywhere}.aweme{display:flex;align-items:center;gap:8px}.aweme img{width:28px;height:28px;border-radius:50%;object-fit:cover}.product{display:flex;align-items:center;gap:8px;min-width:0}.product img{width:32px;height:32px;object-fit:cover;border-radius:4px;flex:none}.product span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.all-products{display:block;border:0;background:none;padding:8px 0 0;color:#5b57ff;cursor:pointer;font:inherit}.settings-grid dd small{display:block;color:#8b90a5}.config-note{font-size:12px;color:#8b90a5;padding:0 16px}a:focus-visible,button:focus-visible{outline:2px solid #5b57ff;outline-offset:3px}@media(max-width:900px){.settings-grid{grid-template-columns:1fr}.settings-grid dl{grid-template-columns:110px minmax(0,1fr);padding:8px;gap:18px 8px}}
</style>
