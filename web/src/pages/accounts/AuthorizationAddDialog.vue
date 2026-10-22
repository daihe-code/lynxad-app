<template>
  <AccountModal class="authorization-add-modal" :class="{ 'authorization-add-modal--guide': guideOpen }" :title="guideOpen ? '查看授权页面操作示例' : '添加广告投放账户'" expanded :busy="busy" @close="emit('close')">
    <form v-if="!guideOpen" :id="formId" class="add-authorization" @submit.prevent="emit('authorize', false)">
      <p class="add-intro">授权后，我就能为你的账户生成投放素材、持续盯盘并提供优化建议，发现投放异常时及时提醒你。</p>

      <section class="add-platform" aria-label="授权平台">
        <h3>授权平台</h3>
        <p><span class="add-platform__logo" aria-hidden="true"><AccountIcon name="douyin" /></span><strong>巨量千川 · 商品全域投放</strong></p>
      </section>

      <section class="add-benefits" aria-labelledby="add-benefits-title">
        <h3 id="add-benefits-title">授权后，我可以帮你</h3>
        <ul>
          <li v-for="item in benefits" :key="item.title">
            <span class="add-benefits__icon" aria-hidden="true"><AccountIcon :name="item.icon" /></span>
            <div><h4>{{ item.title }}</h4><p>{{ item.description }}</p></div>
          </li>
        </ul>
      </section>

      <aside class="add-safety" aria-label="安全说明">
        <AccountIcon name="shield" />
        <div><h3>安全说明</h3><p>通过巨量千川官方页面完成授权，我不会获取你的账号密码，也不会进行充值或转账。授权可随时取消。</p></div>
      </aside>

      <section class="add-instructions" aria-label="操作指引">
        <h3>操作指引</h3>
        <p>接下来，请在千川官方页面选择授权账户，并勾选「允许开发者管理敏感物料」。完成后，账户信息和授权有效期将自动同步，无需手动填写。</p>
        <button ref="guideLink" type="button" class="add-guide-link" :disabled="busy" @click="showGuide">
          <AccountIcon name="book-open" /><span>查看授权页面操作示例</span><AccountIcon name="arrow-right" />
        </button>
      </section>
    </form>

    <section v-else ref="guideContent" class="add-guide" tabindex="-1" aria-label="千川授权操作示例">
      <QianchuanAuthorizationGuide />
    </section>
    <p v-if="error" class="account-error add-error" role="alert">{{ error }}</p>

    <template #footer>
      <template v-if="guideOpen">
        <p class="add-guide-footer-note"><AccountIcon name="info-line" /><span>仅展示操作示例，实际授权需在千川官方页面完成</span></p>
        <button class="btn-modal-ghost" type="button" :disabled="busy" @click="hideGuide">返回授权说明</button>
        <button class="btn-modal-primary" type="button" :disabled="busy" @click="emit('authorize', false)">{{ busy ? '处理中…' : '前往千川授权' }}</button>
      </template>
      <template v-else>
        <button class="btn-modal-ghost" type="button" :disabled="busy" @click="emit('close')">暂不添加</button>
        <button v-if="canDemo" class="btn-modal-ghost" type="button" :disabled="busy" @click="emit('authorize', true)">演示授权</button>
        <button class="btn-modal-primary" :form="formId" type="submit" :disabled="busy">{{ busy ? '处理中…' : '前往千川授权' }}</button>
      </template>
    </template>
  </AccountModal>
</template>

<script setup lang="ts">
import { nextTick, ref, useId } from 'vue';
import AccountIcon from './AccountIcon.vue';
import AccountModal from './AccountModal.vue';
import QianchuanAuthorizationGuide from './QianchuanAuthorizationGuide.vue';

const props = defineProps<{ busy: boolean; error?: string; canDemo?: boolean }>();
const emit = defineEmits<{ close: []; authorize: [demo: boolean] }>();
const formId = useId(), guideOpen = ref(false);
const guideLink = ref<HTMLButtonElement>(), guideContent = ref<HTMLElement>();
const benefits = [
  { icon: 'chart-bars', title: '同步投放数据', description: '查看账户、商品及投放表现，为分析和优化提供依据。' },
  { icon: 'video-document', title: '制作与管理素材', description: '智能生成投放素材，并上传、管理广告素材。' },
  { icon: 'tune', title: '管理投放计划', description: '按你确认的方案或已授予的权限，创建、调整和启停计划。' },
];
async function showGuide() {
  if (props.busy) return;
  guideOpen.value = true;
  await nextTick();
  const container = guideContent.value?.parentElement;
  if (container) container.scrollTop = 0;
  guideContent.value?.focus({ preventScroll: true });
}
async function hideGuide() {
  if (props.busy) return;
  guideOpen.value = false;
  await nextTick();
  guideLink.value?.focus();
}
</script>

<style scoped>
.authorization-add-modal :deep(.modal__box.modal__box--detail) { max-width: 720px; padding: 28px 30px 22px; }
.authorization-add-modal--guide :deep(.modal__box.modal__box--detail) { max-width: 1164px; }
.authorization-add-modal :deep(.modal__title) { font-size: 24px; line-height: 1.4; }
.authorization-add-modal :deep(.modal__close) { flex: none; margin-top: 1px; }
.authorization-add-modal :deep(.modal__body--scroll) { margin-top: 12px; padding-right: 4px; }
.authorization-add-modal :deep(.modal__foot) { border-top: 1px solid #e7e9f4; margin-top: 18px; padding-top: 15px; }
.authorization-add-modal :deep(.modal__foot button) { height: 40px; padding-inline: 18px; border-radius: 7px; font-size: 14px; }
.add-authorization h3 { margin: 0; color: #25283f; font-size: 15px; font-weight: 600; line-height: 1.6; }
.add-intro, .add-instructions > p { margin: 0; color: #747996; font-size: 14px; line-height: 1.7; }
.add-platform { margin-top: 18px; }
.add-platform > p { display: flex; align-items: center; gap: 12px; margin: 8px 0 0; }
.add-platform strong { font-size: 17px; font-weight: 600; line-height: 1.5; color: #25283f; }
.add-platform__logo { display: grid; place-items: center; flex: none; width: 34px; height: 34px; border-radius: 8px; background: #11111d; color: #fff; }
.add-platform__logo svg { width: 23px; height: 23px; filter: drop-shadow(-1px -1px 0 #25f4ee) drop-shadow(1px 1px 0 #fe2c55); }
.add-benefits { margin-top: 22px; }
.add-benefits ul { margin: 12px 0 0; padding: 0; list-style: none; }
.add-benefits li { display: flex; align-items: center; gap: 22px; }
.add-benefits li + li { margin-top: 16px; }
.add-benefits__icon { display: grid; place-items: center; flex: none; width: 50px; height: 50px; border-radius: 12px; background: #f4f2ff; color: #6258ff; }
.add-benefits__icon svg { width: 29px; height: 29px; }
.add-benefits li > div { min-width: 0; }
.add-benefits h4 { margin: 0; font-size: 16px; font-weight: 600; line-height: 1.6; color: #25283f; }
.add-benefits p { margin: 2px 0 0; font-size: 14px; line-height: 1.7; color: #747996; }
.add-safety { display: flex; align-items: flex-start; gap: 18px; margin-top: 22px; padding: 15px 18px; border-radius: 12px; background: #f5f3ff; }
.add-safety > svg { flex: none; width: 30px; height: 30px; margin-top: 2px; color: #6258ff; }
.add-safety p { margin: 2px 0 0; font-size: 13px; line-height: 1.7; color: #747996; }
.add-instructions { margin-top: 18px; }
.add-instructions > p { margin-top: 3px; }
.add-guide-link { display: inline-flex; align-items: center; gap: 8px; padding: 8px 0 0; border: 0; background: transparent; color: #6258ff; font: inherit; font-size: 14px; font-weight: 500; line-height: 1.6; text-align: left; cursor: pointer; }
.add-guide-link svg { flex: none; width: 20px; height: 20px; }
.add-guide-link svg:last-child { width: 18px; height: 18px; }
.add-guide-link:hover span { text-decoration: underline; text-underline-offset: 3px; }
.add-guide-link:focus-visible { outline: 2px solid #a6a0ff; outline-offset: 3px; border-radius: 3px; }
.add-guide:focus { outline: none; }
.add-guide-footer-note { display: flex; align-items: center; gap: 7px; margin: 0 auto 0 0; color: #858aa5; font-size: 12px; line-height: 1.6; }
.add-guide-footer-note svg { flex: none; width: 15px; height: 15px; }
.add-error { margin-top: 12px; }
@media (max-width: 639px) {
  .authorization-add-modal { padding: 16px; }
  .authorization-add-modal :deep(.modal__box.modal__box--detail) { padding: 20px; max-height: calc(100dvh - 32px); }
  .authorization-add-modal :deep(.modal__title) { font-size: 21px; }
  .authorization-add-modal :deep(.modal__foot) { gap: 10px; flex-wrap: wrap; }
  .authorization-add-modal :deep(.modal__foot button) { padding-inline: 13px; }
  .add-platform strong { font-size: 15px; }
  .add-benefits li { gap: 14px; }
  .add-benefits__icon { width: 44px; height: 44px; }
  .add-benefits__icon svg { width: 26px; height: 26px; }
  .add-benefits h4 { font-size: 15px; }
  .add-benefits p, .add-intro, .add-instructions > p { font-size: 13px; }
  .add-safety { gap: 12px; padding: 14px; }
  .add-safety > svg { width: 26px; height: 26px; }
  .add-guide-link { font-size: 13px; gap: 6px; }
  .add-guide-footer-note { flex-basis: 100%; font-size: 11px; }
}
</style>
