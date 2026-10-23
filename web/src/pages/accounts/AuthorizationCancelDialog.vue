<template>
  <AccountModal class="authorization-cancel-modal" :title="title" expanded :busy="busy" @close="emit('close')">
    <template #title>
      <span class="cancel-title-icon" aria-hidden="true"><AccountIcon name="warn-fill" /></span>
      <span>{{ title }}</span>
    </template>

    <p class="cancel-intro">取消后，我将无法继续为该账户提供以下服务：</p>
    <ul class="cancel-impacts">
      <li v-for="item in impacts" :key="item.title">
        <span class="cancel-impacts__icon" aria-hidden="true"><AccountIcon :name="item.icon" /></span>
        <div><h3>{{ item.title }}</h3><p>{{ item.description }}</p></div>
      </li>
    </ul>

    <aside class="cancel-notice" aria-label="广告投放提醒">
      <AccountIcon name="warn-fill" />
      <p><strong>取消授权不会自动停止广告投放。</strong><span>已在千川投放的广告仍可能产生费用，需要你自行查看和管理。</span></p>
    </aside>
    <p class="cancel-recommendation">建议保留授权，让我继续帮你制作素材、盯住投放、及时提醒异常。</p>
    <p v-if="error" class="account-error cancel-error" role="alert">{{ error }}</p>

    <template #footer>
      <button class="btn-modal-ghost" type="button" :disabled="busy" @click="emit('confirm')">{{ busy ? '取消中…' : '仍要取消' }}</button>
      <button ref="keepButton" class="btn-modal-primary" type="button" :disabled="busy" @click="emit('close')">保留授权</button>
    </template>
  </AccountModal>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AccountIcon from './AccountIcon.vue';
import AccountModal from './AccountModal.vue';

const props = defineProps<{ accountName: string; busy: boolean; error?: string }>();
const emit = defineEmits<{ close: []; confirm: [] }>();
const title = computed(() => `确定取消「${props.accountName}」的授权吗？`);
const keepButton = ref<HTMLButtonElement>();
const impacts = [
  { icon: 'video', title: '素材智能生成', description: '无法继续为该账户智能生成投放素材' },
  { icon: 'chart', title: '智能盯盘与优化', description: '无法持续监控投放表现、调整优化策略' },
  { icon: 'bell', title: '异常提醒', description: '消耗异常、投产下滑等情况，将无法及时发现并通知你' },
];
onMounted(() => { keepButton.value?.focus(); });
</script>

<style scoped>
.authorization-cancel-modal :deep(.modal__box.modal__box--detail) { max-width: 680px; padding: 28px 32px; }
.authorization-cancel-modal :deep(.modal__title) { display: flex; align-items: center; gap: 16px; min-width: 0; font-size: 20px; line-height: 1.5; overflow-wrap: anywhere; }
.authorization-cancel-modal :deep(.modal__head) { gap: 12px; }
.authorization-cancel-modal :deep(.modal__close) { flex: none; margin-top: 0; }
.authorization-cancel-modal :deep(.modal__body--scroll) { padding-right: 0; }
.authorization-cancel-modal :deep(.modal__foot) { margin-top: 24px; }
.authorization-cancel-modal :deep(.modal__foot button) { height: 40px; padding-inline: 20px; font-size: 14px; border-radius: 7px; }
.cancel-title-icon { display: grid; place-items: center; flex: none; width: 44px; height: 44px; border-radius: 50%; background: #fff0d8; color: #df951d; }
.cancel-title-icon svg { width: 27px; height: 27px; }
.cancel-intro { margin: 0; font-size: 15px; line-height: 1.7; color: #747996; }
.cancel-impacts { list-style: none; margin: 22px 0 24px; padding: 0; }
.cancel-impacts li { display: flex; align-items: center; gap: 22px; }
.cancel-impacts li + li { margin-top: 24px; }
.cancel-impacts__icon { display: grid; place-items: center; flex: none; width: 50px; height: 50px; border-radius: 50%; background: #f3f1ff; color: #6258ff; }
.cancel-impacts__icon svg { width: 28px; height: 28px; }
.cancel-impacts li > div { min-width: 0; }
.cancel-impacts h3 { margin: 0; font-size: 16px; font-weight: 600; line-height: 1.6; color: #25283f; }
.cancel-impacts p { margin: 3px 0 0; font-size: 14px; line-height: 1.7; color: #747996; }
.cancel-notice { display: flex; align-items: flex-start; gap: 16px; padding: 15px 18px; border-radius: 12px; background: #fff6e6; color: #8b7356; }
.cancel-notice > svg { flex: none; width: 27px; height: 27px; margin-top: 2px; color: #df951d; }
.cancel-notice p { margin: 0; font-size: 13px; line-height: 1.7; }
.cancel-notice strong { display: block; color: #704317; font-size: 14px; font-weight: 600; }
.cancel-recommendation { margin: 18px 0 0; color: #747996; font-size: 14px; line-height: 1.7; }
.cancel-error { margin-top: 12px; }
@media (max-width: 639px) {
  .authorization-cancel-modal { padding: 16px; }
  .authorization-cancel-modal :deep(.modal__box.modal__box--detail) { padding: 20px; max-height: calc(100dvh - 32px); }
  .authorization-cancel-modal :deep(.modal__title) { font-size: 17px; gap: 10px; }
  .authorization-cancel-modal :deep(.modal__foot) { gap: 10px; }
  .authorization-cancel-modal :deep(.modal__foot button) { padding-inline: 15px; }
  .cancel-title-icon { width: 36px; height: 36px; }
  .cancel-title-icon svg { width: 23px; height: 23px; }
  .cancel-intro { font-size: 14px; }
  .cancel-impacts li { gap: 14px; }
  .cancel-impacts__icon { width: 44px; height: 44px; }
  .cancel-impacts__icon svg { width: 25px; height: 25px; }
  .cancel-impacts h3 { font-size: 15px; }
  .cancel-impacts p, .cancel-recommendation { font-size: 13px; }
  .cancel-notice { padding: 14px; gap: 10px; }
  .cancel-notice > svg { width: 23px; height: 23px; }
}
</style>
