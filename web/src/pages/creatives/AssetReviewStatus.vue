<template>
  <div class="asset-ai-review" :class="{ 'is-blocked': review && ['REJECTED', 'FAILED', 'MANUAL_REVIEW'].includes(review.status) }" aria-live="polite">
    <div class="review-heading"><b>{{ review?.status === 'NOT_REQUIRED' ? reviewLabel(review.status) : composite ? (review?.status === 'APPROVED' ? '图文质检合格' : review?.status === 'REJECTED' ? '图文不合格' : review?.status === 'FAILED' ? '图文质检失败' : '图文质检中') : reviewLabel(review?.status) }}</b><button v-if="review?.status === 'FAILED' && !composite" type="button" :disabled="retrying" @click="emit('retry')">{{ retrying ? '提交中…' : '重试审核' }}</button></div>
    <p v-if="review?.summary">{{ review.summary }}</p>
    <ul v-if="review?.reasons?.length"><li v-for="(reason, index) in review.reasons" :key="index">{{ reason }}</li></ul>
    <p v-if="review?.errorMessage" role="alert">{{ review.errorMessage }}</p>
    <p v-if="review?.status === 'FAILED'">素材上传已完成，当前失败发生在 AI 审核阶段。文件仍保留，重试审核即可，无需重复上传。</p>
    <p v-if="review?.status === 'NOT_REQUIRED'">使用引擎已有 OSS 文件直接同步千川，千川平台仍会独立审核。</p>
    <p v-else-if="review?.status === 'REJECTED'">请修改素材内容后重新上传；当前素材不会同步千川。</p>
    <p v-else-if="review?.status === 'MANUAL_REVIEW'">引擎未明确通过，需人工核查并修改素材后重新上传；当前素材不会同步千川。</p>
    <p v-else-if="review?.status === 'APPROVED'">已通过本平台 AI 审核，千川平台仍会独立审核。</p>
    <p v-else>文件已保存，AI 审核通过后才会同步到所选千川账户。</p>
  </div>
</template>
<script setup lang="ts">
import type { AssetReview } from '@/features/creative/creativeApi';
import { reviewLabel } from '@/features/creative/assetUploads';
defineProps<{ review?: AssetReview | null; retrying?: boolean; composite?: boolean }>();
const emit = defineEmits<{ retry: [] }>();
</script>
<style scoped>
.asset-ai-review { padding:12px; margin:10px 0; border-radius:10px; background:#f5f4ff; color:#50536f; font-size:12px; line-height:1.7; overflow-wrap:anywhere; }
.review-heading { display:flex; justify-content:space-between; gap:12px; }.review-heading button { color:#5b55ff; flex:none; }.review-heading button:disabled { opacity:.5; }.asset-ai-review p { margin:6px 0 0; }.asset-ai-review ul { padding-left:18px; margin:6px 0; }.is-blocked { background:#fff5f3; }.is-blocked b { color:#d9534f; }
</style>
