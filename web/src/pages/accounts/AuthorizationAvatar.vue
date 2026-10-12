<template>
  <span class="authorization-avatar" :title="src ? failed ? '头像暂时无法加载' : name : '平台未返回头像'">
    <img v-if="src && !failed" :src="src" :alt="`${name}的头像`" referrerpolicy="no-referrer" @error="failed = true" />
    <AccountIcon v-else name="douyin" />
  </span>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import AccountIcon from './AccountIcon.vue';
const props = defineProps<{ src?: string | null; name: string }>();
const failed = ref(false);
watch(() => props.src, () => { failed.value = false; });
</script>
<style scoped>
.authorization-avatar{display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;width:52px;height:52px;border-radius:14px;background:#f0eeff;color:#6158ff;overflow:hidden}.authorization-avatar img{width:100%;height:100%;object-fit:cover}.authorization-avatar :deep(svg){width:28px;height:28px}
</style>
