<template>
  <div class="product-adjustment-notice">
    <p class="adjustment-intro">保存商品变更后，将按组合更新以下内容：</p>
    <div class="adjustment-cards">
      <section class="adjustment-card">
        <span class="adjustment-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><rect x="3" y="12" width="3" height="9" rx="1"/><rect x="10" y="7" width="3" height="14" rx="1"/><rect x="17" y="3" width="3" height="18" rx="1"/></svg></span>
        <div><h3>分析与投放目标 <span class="adjustment-conditional">如已完成</span></h3><p>{{ notice.analysisGoal }}</p></div>
      </section>
      <section class="adjustment-card">
        <span class="adjustment-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m12 3 9 5-9 5-9-5 9-5ZM3 12l9 5 9-5M3 16l9 5 9-5"/></svg></span>
        <div><h3>素材处理</h3><p>{{ notice.materials }}</p><p>{{ notice.added }}</p></div>
      </section>
    </div>
    <p class="adjustment-usage"><PreparationIcon name="info" />{{ notice.usage }}</p>
    <p class="adjustment-effective"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 8 3v6c0 4-3 7-8 9-5-2-8-5-8-9V6l8-3Z"/><path d="m8 12 3 3 5-6"/></svg>{{ notice.effective }}</p>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import PreparationIcon from '@/components/PreparationIcon.vue';
import { productAdjustmentNotice } from '@/features/delivery/selectionImpact';
const props = defineProps<{ published?: boolean }>();
const notice = computed(() => productAdjustmentNotice(props.published));
</script>
<style scoped>
.product-adjustment-notice{color:#292b3e;font-size:14px;line-height:1.7}
.product-adjustment-notice p{margin:0;white-space:normal}
.adjustment-intro{margin-bottom:12px!important}
.adjustment-cards{padding:0 14px;border-radius:12px;background:#f3f2fa}
.adjustment-card{display:flex;align-items:flex-start;gap:14px;padding:15px 0}
.adjustment-card+.adjustment-card{border-top:1px solid #e2e1ed}
.adjustment-icon{display:grid;place-items:center;flex:none;width:36px;height:36px;border-radius:10px;background:#e9e5ff;color:#6652ef}
.adjustment-icon svg,.adjustment-effective svg{width:21px;height:21px;fill:none;stroke:currentColor;stroke-width:1.6;stroke-linecap:round;stroke-linejoin:round}
.adjustment-card>div{min-width:0}
.adjustment-card h3{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin:0 0 3px;font-size:15px;font-weight:600;line-height:1.6}
.adjustment-conditional{padding:1px 9px;border-radius:8px;background:#e7e6ee;color:#6d6c7e;font-size:12px;font-weight:400}
.adjustment-card p{font-size:13px;line-height:1.7}
.product-adjustment-notice .adjustment-usage{display:flex;align-items:flex-start;gap:9px;margin:13px 0;color:#80818e;font-size:12px;line-height:1.7}
.adjustment-usage svg{flex:none;width:16px;height:16px;margin-top:2px}
.adjustment-effective{display:flex;align-items:flex-start;gap:9px;padding:10px 12px;border-radius:9px;background:#edf2ff;color:#5264d8;font-size:12px;line-height:1.7}
.adjustment-effective svg{flex:none;width:18px;height:18px;margin-top:1px}
@media(max-width:520px){.adjustment-cards{padding:0 12px}.adjustment-card{gap:10px;padding:13px 0}.adjustment-icon{width:30px;height:30px;border-radius:8px}.adjustment-card h3{font-size:14px;gap:6px}.product-adjustment-notice{font-size:13px}}
</style>
