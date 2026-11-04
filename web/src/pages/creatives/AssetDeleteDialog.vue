<template>
  <el-dialog :model-value="true" title="删除素材" class="asset-delete-dialog" align-center append-to-body
    :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy"
    @update:model-value="!$event && emit('close')">
    <div class="asset-delete-content">
      <span class="asset-delete-icon"><AssetIcon name="trash" /></span>
      <div class="asset-delete-body">
        <h3>确认删除选中的 {{ items.length }} 项素材？</h3>
        <p class="asset-delete-note">删除后，不影响已使用该素材的在投创意和计划。</p>
        <p class="asset-delete-hint">所选素材将不再在素材中心展示。</p>
        <div class="asset-delete-list">
          <div v-for="item in items" :key="item.id" class="asset-delete-item">
            <AssetIcon :name="item.type === 'VIDEO' ? 'video' : item.type === 'IMAGE' ? 'image' : 'note'" />
            <div><p class="asset-delete-name" :title="item.filename">{{ item.filename }}</p><p class="asset-delete-meta">{{ { VIDEO: '视频', IMAGE: '图片', CAROUSEL: '图文', AUDIO: '音频', TEXT: '文本' }[item.type] }}<template v-if="item.type === 'VIDEO'"> · {{ duration(item.durationSec) }}</template></p></div>
          </div>
        </div>
        <p v-if="error" class="asset-delete-error" role="alert">{{ error }}</p>
      </div>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="emit('close')">取消</el-button>
      <el-button type="danger" :loading="busy" @click="emit('confirm')">确认删除</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import type { LibraryItemVo } from '@/features/creative/creativeApi';
import AssetIcon from './AssetIcon.vue';
import { duration } from './assetFormat';
defineProps<{ items: LibraryItemVo[]; busy: boolean; error: string }>();
const emit = defineEmits<{ close: []; confirm: [] }>();
</script>
<style>
.asset-delete-dialog.el-dialog { width:min(600px,calc(100vw - 32px)); border-radius:18px; --el-dialog-padding-primary:26px; }
.asset-delete-dialog .el-dialog__title { color:#1a1b2e; font-size:21px; font-weight:700; }
.asset-delete-dialog .el-dialog__header { margin-bottom:24px; padding-right:28px; }
.asset-delete-dialog .el-dialog__footer { margin-top:24px; padding-top:18px; border-top:1px solid #e4e5f2; }
.asset-delete-dialog .el-dialog__footer .el-button { min-width:90px; height:40px; border-radius:10px; }
.asset-delete-dialog .el-button--danger { --el-button-bg-color:#fa5358; --el-button-border-color:#fa5358; }
.asset-delete-content { display:flex; gap:20px; color:#1a1b2e; }
.asset-delete-icon { display:grid; place-items:center; flex:none; width:54px; height:54px; border-radius:12px; background:#fff0f1; color:#f34e57; }
.asset-delete-icon svg { width:29px; height:29px; }
.asset-delete-body { flex:1; min-width:0; }
.asset-delete-body h3 { font-size:20px; font-weight:700; line-height:1.5; margin:0 0 8px; }
.asset-delete-note { font-size:16px; font-weight:600; line-height:1.7; margin:0; }
.asset-delete-hint { color:#7c7f99; font-size:15px; line-height:1.7; margin:3px 0 14px; }
.asset-delete-list { max-height:240px; overflow:auto; display:grid; gap:8px; }
.asset-delete-item { display:flex; align-items:center; gap:16px; padding:10px 16px; border-radius:10px; background:#f4f3fa; }
.asset-delete-item > svg { flex:none; width:26px; height:26px; color:#7c7f99; }
.asset-delete-item > div { min-width:0; }
.asset-delete-name { font-size:15px; font-weight:500; line-height:1.5; overflow-wrap:anywhere; margin:0; }
.asset-delete-meta { color:#7c7f99; font-size:13px; line-height:1.5; margin:3px 0 0; }
.asset-delete-error { color:#e5484d; font-size:13px; line-height:1.6; margin:12px 0 0; }
@media(max-width:480px) { .asset-delete-content { gap:12px; }.asset-delete-icon { width:40px; height:40px; }.asset-delete-body h3 { font-size:18px; }.asset-delete-note { font-size:14px; }.asset-delete-dialog.el-dialog { --el-dialog-padding-primary:20px; } }
</style>
