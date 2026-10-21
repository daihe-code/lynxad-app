<template>
  <div class="sync-action">
    <p v-if="sync.verification?.message" role="status">{{ sync.verification.message }}<small v-if="sync.verification.checkedAt">核查时间：{{ new Date(sync.verification.checkedAt).toLocaleString('zh-CN') }}</small></p>
    <button v-if="sync.status === 'UNCERTAIN'" type="button" :disabled="busy || !canVerifyAssetSync(sync.status, sync.verification?.checkedAt, now)" @click="emit('verify')">{{ busy ? '正在核查…' : canVerifyAssetSync(sync.status, sync.verification?.checkedAt, now) ? '核查上传结果' : '请稍后再次核查' }}</button>
    <button v-else-if="canRetryAssetSync(sync.status)" type="button" :disabled="busy || !canRetry" @click="emit('retry')">重试同步</button>
  </div>
</template>
<script setup lang="ts">
import { onUnmounted, ref } from 'vue';
import type { AssetSync } from '@/features/creative/creativeApi';
import { canRetryAssetSync, canVerifyAssetSync } from '@/features/creative/assetReconciliation';
defineProps<{ sync: AssetSync; busy: boolean; canRetry: boolean }>();
const emit = defineEmits<{ verify: []; retry: [] }>();
const now = ref(Date.now());
const timer = setInterval(() => { now.value = Date.now(); }, 1000);
onUnmounted(() => clearInterval(timer));
</script>
<style scoped>
.sync-action { max-width:420px; font-size:12px; line-height:1.6; overflow-wrap:anywhere; }
.sync-action p { color:#7c7f99; margin:0 0 6px; }.sync-action small { display:block; }
.sync-action button { color:#5b55ff; font-size:12px; white-space:normal; }.sync-action button:disabled { opacity:.5; cursor:not-allowed; }
</style>
