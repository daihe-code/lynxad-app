<template>
  <div class="plan-content-summary">
    <el-popover placement="bottom-start" :width="260" :trigger="['hover', 'focus', 'click']" :show-after="150" popper-class="campaign-popover">
      <template #reference><button class="aweme-trigger" type="button" :aria-label="`查看抖音号：${aweme?.name || plan.awemeId || '暂未提供'}`"><span class="content-thumb aweme-avatar"><el-image v-if="aweme?.avatar" :src="aweme.avatar" fit="cover"><template #error><el-icon><User /></el-icon></template></el-image><el-icon v-else><User /></el-icon></span></button></template>
      <div class="aweme-details"><strong>{{ aweme?.name || '抖音号' }}</strong><span>抖音号 ID：{{ plan.awemeId || '暂未提供' }}</span><small v-if="account?.accountName">广告账户：{{ account.accountName }}</small></div>
    </el-popover>
    <span class="content-divider" aria-hidden="true" />
    <el-popover placement="bottom-start" :width="340" :trigger="['hover', 'focus']" :show-after="150" :hide-after="100" popper-class="campaign-popover plan-content-popover">
      <template #reference>
        <component :is="disabled ? 'button' : RouterLink" :to="disabled ? undefined : productsLink" class="content-entry" :aria-label="`查看${plan.name}的 ${products.length} 款商品`">
          <span class="content-thumb"><el-image v-if="productCover" :src="productCover" fit="cover"><template #error><el-icon><Goods /></el-icon></template></el-image><el-icon v-else><Goods /></el-icon><b v-if="products.length" class="product-count">{{ products.length }}</b></span><span>商品</span><el-icon v-if="!disabled" class="content-arrow"><ArrowRight /></el-icon>
        </component>
      </template>
      <div class="content-popup-heading"><strong>投放商品</strong><span>{{ products.length }} 款</span></div>
      <div v-if="products.length" class="content-products"><div v-for="product in products" :key="product.id" class="content-product"><span class="content-product-image"><el-image v-if="product.imageUrl" :src="product.imageUrl" fit="cover"><template #error><el-icon><Goods /></el-icon></template></el-image><el-icon v-else><Goods /></el-icon></span><div><strong>{{ product.name }}</strong><small>商品 ID：{{ product.id }}</small></div></div></div>
      <p v-else class="content-empty">暂未提供商品信息</p>
      <router-link v-if="!disabled" class="content-popup-link" :to="productsLink">查看全部商品 <el-icon><ArrowRight /></el-icon></router-link>
    </el-popover>
    <component v-if="materialCover" :is="disabled ? 'span' : RouterLink" :to="disabled ? undefined : materialsLink" class="content-entry" :aria-label="`查看${plan.name}的素材`">
      <span class="content-thumb material-thumb"><el-image :src="materialCover" fit="cover"><template #error><el-icon><VideoPlay /></el-icon></template></el-image></span><span>素材</span><el-icon v-if="!disabled" class="content-arrow"><ArrowRight /></el-icon>
    </component>
    <span v-if="plan.source === 'EXTERNAL'" class="content-source">外部</span>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink, type RouteLocationRaw } from 'vue-router';
import { ArrowRight, Goods, User, VideoPlay } from '@element-plus/icons-vue';
import type { PlanListVo } from '@/features/delivery/deliveryApi';
import type { AccountVo } from '@/features/identity/oauthApi';
const props=defineProps<{plan:PlanListVo;account?:AccountVo;productsLink:RouteLocationRaw;materialsLink:RouteLocationRaw;disabled?:boolean}>();
const aweme=computed(()=>props.account?.awemes.find(item=>item.awemeId===props.plan.awemeId));
const products=computed(()=>[...new Map((props.plan.products||[]).map(product=>[product.id,product])).values()]);
const productCover=computed(()=>products.value.find(product=>product.imageUrl)?.imageUrl);
const materialCover=computed(()=>props.plan.materialCoverUrl?.trim());
</script>
<style scoped>
:global(.plan-content-popover){max-width:calc(100vw - 24px);box-sizing:border-box}
.plan-content-summary{display:flex;align-items:center;gap:12px;min-height:38px;margin-top:9px;white-space:nowrap}.aweme-trigger,.content-entry{display:inline-flex;align-items:center;gap:6px;margin:0;padding:0;border:0;background:none;color:#555b72;font:inherit;font-size:12px;text-decoration:none;line-height:20px}.aweme-trigger,button.content-entry{cursor:pointer}.content-entry:is(a):hover,.content-entry:is(button):hover{color:#575cf6}.aweme-trigger:focus-visible,.content-entry:focus-visible{outline:2px solid #575cf6;outline-offset:3px;border-radius:6px}.content-thumb{position:relative;display:grid;place-items:center;width:36px;height:36px;flex:none;border:1px solid #e9eaf2;border-radius:6px;background:#f6f7fb;color:#9b9fb4;overflow:hidden}.content-thumb .el-image{width:100%;height:100%}.content-thumb>.el-icon{font-size:20px}.aweme-avatar{border-radius:50%}.content-divider{height:24px;width:1px;background:#ececf4;flex:none}.content-arrow{font-size:11px;color:#9a9eb0}.product-count{position:absolute;right:0;bottom:0;min-width:14px;padding:0 2px;border-radius:4px 0 0;background:#666b7dcc;color:#fff;font-size:10px;font-weight:500;line-height:15px;text-align:center}.content-source{padding:0 4px;border-radius:3px;color:#8d90a4;background:#f1f1f7;font-size:10px;line-height:18px}.aweme-details{display:flex;flex-direction:column;gap:7px;font-size:13px;overflow-wrap:anywhere}.aweme-details span,.aweme-details small{color:#8a8fa4;font-size:12px}.content-popup-heading{display:flex;align-items:center;justify-content:space-between;padding-bottom:9px;border-bottom:1px solid #eeeff5;font-size:13px}.content-popup-heading>span{color:#9397aa;font-size:12px}.content-products{max-height:280px;overflow:auto}.content-product{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid #f0f1f7}.content-product-image{display:grid;place-items:center;width:44px;height:44px;flex:none;border-radius:6px;overflow:hidden;background:#f6f7fb;color:#a1a5b8}.content-product-image .el-image{width:100%;height:100%}.content-product>div{min-width:0}.content-product strong{display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical;font-size:12px;font-weight:500;line-height:19px;overflow-wrap:anywhere}.content-product small{display:block;margin-top:4px;font-size:11px;color:#969aad;overflow-wrap:anywhere}.content-popup-link{display:flex;align-items:center;justify-content:center;gap:5px;margin-top:10px;color:#575cf6;font-size:12px;text-decoration:none}.content-empty{margin:12px 0;color:#9296aa;font-size:12px}
</style>
