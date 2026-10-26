<template>
  <section v-if="noticeId" ref="panel" class="generation-notice-result" aria-label="消息对应的生成任务" tabindex="-1">
    <template v-if="message && view && target"><h3>{{ view.title }}</h3><p v-if="view.planName" class="notice-plan">计划：{{ view.planName }}</p><p>{{ view.body }}</p><p v-if="message.result?.jobId" class="notice-task">任务：{{ message.result.jobId }}</p><p>{{ target.note }}</p><p v-for="reason in target.reasons" :key="reason" class="notice-reason">失败原因：{{ reason }}</p></template>
    <p v-else-if="loading">正在定位消息对应的任务…</p>
    <p v-else role="status">{{ error || '该消息不属于当前计划，以下仅展示当前计划素材。' }}</p>
    <button v-if="error" type="button" @click="load">重新加载任务消息</button>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { listInbox, type InboxMessage } from '@/features/tenant/inboxApi';
import { generationNotice } from '@/features/tenant/inboxGeneration';
import { generationNoticeTarget } from '@/features/delivery/generationNoticeTarget';
import type { CreationState } from '@/features/delivery/creationApi';
const props = defineProps<{ state: CreationState }>();
const emit = defineEmits<{ focus: [target: NonNullable<ReturnType<typeof generationNoticeTarget>>] }>();
const route = useRoute(), panel = ref<HTMLElement>(), message = ref<InboxMessage>(), loading = ref(false), error = ref('');
const noticeId = computed(() => typeof route.query.notice === 'string' ? route.query.notice : '');
const view = computed(() => message.value ? generationNotice(message.value) : null);
const target = computed(() => message.value ? generationNoticeTarget(props.state, message.value) : null);
let request = 0;
async function load() {
  const id = noticeId.value, version = ++request; message.value = undefined; error.value = '';
  if (!id) return;
  loading.value = true;
  try { const rows = await listInbox(true); if (version !== request) return; message.value = rows.find(m => m.id === id); if (!message.value) error.value = '任务消息已不存在或无权查看。'; }
  catch { if (version === request) error.value = '任务消息暂时无法读取，当前素材仍可查看。'; }
  finally { if (version === request) { loading.value = false; await nextTick(); panel.value?.focus({ preventScroll: true }); panel.value?.scrollIntoView({ block: 'start' }); } }
}
watch([noticeId, () => props.state.planId], load, { immediate: true });
watch(target, value => { if (value) emit('focus', value); });
</script>
<style scoped>
.generation-notice-result{margin:20px 0;padding:16px 20px;border:1px solid #dedbff;border-radius:12px;background:#faf9ff;scroll-margin-top:96px;outline:none}
.generation-notice-result h3{font-size:16px;color:#242540;margin:0 0 6px}.generation-notice-result p{font-size:13px;line-height:1.7;color:#626781;margin:4px 0;overflow-wrap:anywhere}.generation-notice-result .notice-task,.generation-notice-result .notice-plan{color:#8589a2;font-size:12px}.generation-notice-result .notice-reason{color:#c83c4c}.generation-notice-result button{color:#575cf6;font:inherit;background:transparent;border:0;padding:6px 0;cursor:pointer}
</style>
