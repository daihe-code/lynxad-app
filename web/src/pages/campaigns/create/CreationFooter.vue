<template>
  <footer class="creation-footer" aria-label="投放计划操作栏">
    <div class="creation-footer__inner">
      <button class="creation-footer__back" type="button" :disabled="busy" @click="emit('back')"><ReferenceIcon name="arrow-left" />返回计划列表</button>
      <div class="creation-footer__summary">
        <div class="creation-footer__name"><span>计划名称</span><strong :title="planName || undefined">{{ planName || '保存草稿后自动生成' }}</strong></div>
        <p class="creation-footer__status">计划状态：<span>草稿</span></p>
      </div>
      <div class="creation-footer__actions">
        <p v-if="!publishingStep" class="creation-footer__hint">本步骤不会发布广告</p>
        <div class="creation-footer__buttons">
          <slot name="previous" />
          <button class="creation-footer__save" type="button" :disabled="busy || saveDisabled" @click="emit('save')">保存草稿</button>
          <slot />
        </div>
      </div>
    </div>
  </footer>
</template>
<script setup lang="ts">
import ReferenceIcon from '../reference/ReferenceIcon.vue';
defineProps<{ planName?: string; busy: boolean; saveDisabled?: boolean; publishingStep?: boolean }>();
const emit = defineEmits<{ back: []; save: [] }>();
</script>
<style scoped>
.creation-footer{position:fixed;bottom:0;left:0;right:0;z-index:35;border-top:1px solid #e9e9f4;background:#fff;color:#24263f}
.creation-footer__inner{box-sizing:border-box;display:flex;align-items:center;gap:24px;max-width:1248px;min-height:92px;margin:0 auto;padding:12px 24px}
.creation-footer__back{display:inline-flex;align-items:center;gap:8px;flex-shrink:0;min-height:44px;padding:0 24px 0 0;border:0;border-right:1px solid #e5e5f0;background:transparent;color:#575cf6;font:inherit;font-size:14px;white-space:nowrap;cursor:pointer}
.creation-footer__back :deep(svg){width:17px;height:17px;flex-shrink:0}
.creation-footer__summary{display:flex;align-items:center;gap:24px;flex:1;min-width:0}
.creation-footer__name{display:flex;flex-direction:column;gap:6px;min-width:0}
.creation-footer__name>span,.creation-footer__status{color:#8588a5;font-size:12px}
.creation-footer__name strong{display:block;max-width:320px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px;font-weight:600}
.creation-footer__status{display:flex;align-items:center;flex-shrink:0;gap:6px;margin:0;white-space:nowrap}
.creation-footer__status span{padding:4px 12px;border-radius:20px;background:#eeebff;color:#7a5ee8;font-weight:600}
.creation-footer__actions{display:flex;flex-direction:column;align-items:flex-end;gap:6px;flex-shrink:0}
.creation-footer__hint{margin:0;color:#8588a5;font-size:12px;line-height:16px}
.creation-footer__buttons{display:flex;align-items:center;gap:12px}
.creation-footer__save,.creation-footer__buttons :deep(.btn-prev),.creation-footer__buttons :deep(.btn-next),.creation-footer__buttons :deep(.btn-next-primary){box-sizing:border-box;display:inline-flex;align-items:center;justify-content:center;gap:8px;flex-shrink:0;min-width:96px;height:44px;padding:0 18px;border:1px solid #e2e3f1;border-radius:10px;background:#fff;color:#25263c;font:inherit;font-size:14px;font-weight:500;line-height:1;white-space:nowrap;cursor:pointer}
.creation-footer__buttons :deep(.btn-next),.creation-footer__buttons :deep(.btn-next-primary){min-width:112px;border-color:#575cf6;background:#575cf6;color:#fff;font-weight:600;box-shadow:0 4px 12px #575cf61f}
.creation-footer__buttons :deep(svg){width:15px;height:15px}
.creation-footer__back:hover:not(:disabled){color:#343be0}
.creation-footer__save:hover:not(:disabled),.creation-footer__buttons :deep(.btn-prev:hover:not(:disabled)){border-color:#575cf6;color:#575cf6}
.creation-footer button:focus-visible,.creation-footer__buttons :deep(button:focus-visible){outline:2px solid #575cf6;outline-offset:3px}
.creation-footer button:disabled,.creation-footer__buttons :deep(button:disabled){opacity:.45;cursor:not-allowed;box-shadow:none}
.creation-footer__buttons :deep(.btn-next:disabled),.creation-footer__buttons :deep(.btn-next-primary:disabled){opacity:1;border-color:#cbcbd9;background:#cbcbd9;color:#fff}
@media(max-width:1023px){.creation-footer__inner,.creation-footer__summary{gap:16px}.creation-footer__summary{align-items:flex-start;flex-direction:column;gap:8px}.creation-footer__back{padding-right:16px}.creation-footer__name strong{max-width:260px}}
@media(max-width:767px){.creation-footer__inner{flex-wrap:wrap;gap:10px 16px;padding:12px 16px}.creation-footer__back{min-height:36px;padding-right:12px;font-size:13px}.creation-footer__summary{flex-direction:row;align-items:center;justify-content:space-between;gap:12px;flex-basis:calc(100% - 145px)}.creation-footer__name strong{max-width:100%;font-size:12px}.creation-footer__status{font-size:0;gap:0}.creation-footer__status span{font-size:11px;padding:4px 8px}.creation-footer__actions{width:100%;gap:4px}.creation-footer__buttons{width:100%;gap:8px}.creation-footer__save,.creation-footer__buttons :deep(.btn-prev),.creation-footer__buttons :deep(.btn-next),.creation-footer__buttons :deep(.btn-next-primary){flex:1;min-width:0;padding:0 12px}.creation-footer__hint{font-size:11px}}
</style>
