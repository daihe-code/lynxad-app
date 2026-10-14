<template>
  <el-popover placement="top" :width="320" trigger="click">
    <template #reference><button class="profile-button" type="button" @click.stop>{{ material.creativeProfileStatus === 'PENDING' ? '场景分析中' : material.creativeProfileStatus === 'FAILED' ? '场景待分析' : profile ? '场景与风格' : '场景未分析' }}</button></template>
    <div class="creative-profile">
      <strong>素材场景档案</strong><p class="profile-note">{{ profile?.source === 'VISUAL_ANALYSIS' ? '依据实际素材画面分析' : profile ? '生成设计方案，实际画面分析待完成' : '素材就绪后分析实际画面，未知内容不推测。' }}</p>
      <dl v-if="profile"><template v-for="(label, key) in labels" :key="key"><dt>{{ label }}</dt><dd>{{ display(profile[key]) }}</dd></template></dl>
      <p v-if="material.generationDescription">生成要求：{{ material.generationDescription }}</p>
      <p class="profile-note">档案会关联投放效果。达到成熟样本与亏损阈值的方向，会在自动追加素材时规避。</p>
      <button v-if="canRetry && material.status === 'READY' && (!material.creativeProfileStatus || material.creativeProfileStatus === 'FAILED')" class="profile-button" type="button" :disabled="busy" @click="emit('retry')">{{ material.creativeProfileStatus === 'FAILED' ? '重新分析场景' : '分析场景' }}</button>
    </div>
  </el-popover>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { CreationMaterial } from '@/features/delivery/creationApi';
const props = defineProps<{ material: CreationMaterial; canRetry?: boolean; busy?: boolean }>();
const emit = defineEmits<{ retry: [] }>();
const profile = computed(() => props.material.creativeProfile);
const labels = { scenes: '使用场景', visual_style: '素材风格', creative_angle: '商品展示角度', audience: '目标人群', selling_points: '表达卖点', hook: '开头 / 首图', narration_style: '讲述方式', music_direction: '音乐方向' };
const display = (v: unknown) => Array.isArray(v) ? v.join('、') || '未确定' : typeof v === 'string' && v ? v : '未确定';
</script>
<style scoped>
.profile-button{border:0;background:none;color:var(--el-color-primary);padding:4px;font-size:12px;cursor:pointer;white-space:nowrap}.profile-button:disabled{opacity:.5}.creative-profile{font-size:12px;line-height:1.6}.creative-profile dl{display:grid;grid-template-columns:90px 1fr;gap:8px;margin:12px 0}.creative-profile dd{margin:0;overflow-wrap:anywhere}.creative-profile dt,.profile-note{color:var(--el-text-color-secondary)}
</style>
