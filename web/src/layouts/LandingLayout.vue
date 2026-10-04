<template>
  <div class="landing">
    <header class="header">
      <div class="bar">
        <router-link class="brand" to="/#top" aria-label="灵投智营 · 返回首页">
          <svg viewBox="0 0 1300 935" width="24" height="17" aria-hidden="true">
            <use href="#lynx-mark" />
          </svg>
          <span>灵投智营</span>
        </router-link>
        <nav class="nav" aria-label="主导航">
          <router-link to="/#features">产品功能</router-link>
          <router-link to="/#industries">解决方案</router-link>
          <router-link to="/#pricing">定价</router-link>
          <router-link to="/#faq">帮助中心</router-link>
        </nav>
        <div class="actions">
          <router-link class="btn btn--ghost" to="/login">登录</router-link>
          <button class="btn btn--primary" type="button" @click="goTrial">免费试用</button>
          <button class="menu" type="button" :aria-expanded="menuOpen" aria-controls="mobile-nav" aria-label="展开导航" @click="menuOpen = !menuOpen">
            <span /><span /><span />
          </button>
        </div>
      </div>
      <nav v-show="menuOpen" id="mobile-nav" class="mobile" aria-label="移动端导航">
        <router-link to="/#features" @click="menuOpen = false">产品功能</router-link>
        <router-link to="/#industries" @click="menuOpen = false">解决方案</router-link>
        <router-link to="/#pricing" @click="menuOpen = false">定价</router-link>
        <router-link to="/#faq" @click="menuOpen = false">帮助中心</router-link>
        <router-link to="/login" @click="menuOpen = false">登录</router-link>
      </nav>
    </header>

    <slot />

    <div v-if="visible" class="modal" role="dialog" aria-modal="true" aria-labelledby="contact-title">
      <button class="modal__mask" type="button" aria-label="关闭" @click="close" />
      <div class="modal__card">
        <div class="modal__head">
          <h2 id="contact-title">联系我们</h2>
          <button type="button" aria-label="关闭弹窗" @click="close">×</button>
        </div>
        <div class="modal__copy">
          <p v-if="info.phone || info.email">
            <template v-if="info.phone">可直接拨打客服电话<span>{{ info.phone }}</span></template>
            <template v-if="info.phone && info.email">或发邮件至</template>
          </p>
          <p v-if="info.email">
            <a :href="`mailto:${info.email}`">{{ info.email }}</a>联系我们。
          </p>
          <p>留下您的联系方式：</p>
        </div>
        <form @submit.prevent="submit">
          <label class="sr" for="contact-phone">您的联系电话</label>
          <input id="contact-phone" v-model="form.phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="11" placeholder="请输入您的联系电话" />
          <p class="hint">我们将在12小时内给您回复。</p>
          <p v-if="phoneError" class="error">{{ phoneError }}</p>
          <div class="modal__foot">
            <button class="btn btn--primary" type="submit" :disabled="submitting">确定</button>
          </div>
        </form>
      </div>
    </div>

    <svg xmlns="http://www.w3.org/2000/svg" class="sprite" aria-hidden="true">
      <symbol id="lynx-mark" viewBox="0 0 1300 935">
        <g fill="none" stroke="currentColor" stroke-linecap="round">
          <path d="M1223.8 855.9C1219.3 837.9 1208.2 783 1196.8 747.6C1185.4 712.3 1170.6 677.8 1155.6 643.8C1140.7 609.7 1124.4 576.1 1107.2 543.1C1090 510.1 1072 477.5 1052.5 445.8C1033 414.1 1012.4 382.9 990.4 353C968.3 323 945.2 293.5 920.4 265.8C895.7 238.1 870.2 210.1 841.8 186.6C813.4 163.1 781.9 143.6 750 125C718 106.4 683.3 75 650 75C616.7 75 582 106.4 550 125C518.1 143.6 486.6 163.1 458.2 186.6C429.8 210.1 404.3 238.1 379.6 265.8C354.8 293.5 331.7 323 309.6 353C287.6 382.9 267 414.1 247.5 445.8C228 477.5 210 510.1 192.8 543.1C175.6 576.1 159.3 609.7 144.4 643.8C129.4 677.8 114.6 712.3 103.2 747.6C91.8 783 80.7 837.9 76.2 855.9" stroke-width="150" />
          <path d="M82.8 175.9L285 455" stroke-width="58" />
          <path d="M1217.2 175.9L1015 455" stroke-width="58" />
        </g>
        <circle cx="440" cy="559" r="82.5" fill="currentColor" />
        <circle cx="860" cy="559" r="82.5" fill="currentColor" />
      </symbol>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";
import { useContactDialog } from "@/features/notify/useContactDialog";

const router = useRouter();
const user = useUserStore();
const menuOpen = ref(false);
const { visible, submitting, phoneError, info, form, close, submit } = useContactDialog();

function goTrial() {
  router.push(user.isLoggedIn ? "/subscription" : "/login");
}
</script>

<style scoped>
.sprite { position: absolute; width: 0; height: 0; overflow: hidden; }
.landing { min-height: 100vh; color: #4e5069; background: #fff; }
.header {
  position: sticky; top: 0; z-index: 40; border-bottom: 1px solid rgba(230, 232, 242, 0.7);
  background: rgba(255, 255, 255, 0.92); backdrop-filter: blur(12px);
}
.bar { display: flex; align-items: center; height: 60px; max-width: 1200px; margin: 0 auto; padding: 0 24px; }
.brand { display: flex; align-items: center; gap: 8px; color: #1a1b2e; font-size: 18px; font-weight: 600; text-decoration: none; }
.brand svg { color: #575cf6; }
.nav { display: none; gap: 28px; margin-left: 48px; }
.nav a { color: #2b2e45; font-size: 15px; font-weight: 500; text-decoration: none; }
.nav a:hover { color: #4348db; }
.actions { display: flex; align-items: center; gap: 12px; margin-left: auto; }
.btn {
  display: inline-flex; align-items: center; justify-content: center; height: 36px; padding: 0 16px;
  border-radius: 8px; border: 0; font-size: 14px; font-weight: 600; cursor: pointer; text-decoration: none;
}
.btn--ghost { border: 1px solid #e6e8f2; background: #fff; color: #1a1b2e; }
.btn--primary { background: #575cf6; color: #fff; }
.menu { display: flex; flex-direction: column; justify-content: center; gap: 4px; width: 36px; height: 36px; border: 1px solid #e6e8f2; border-radius: 8px; background: #fff; cursor: pointer; }
.menu span { display: block; height: 1.5px; background: #1a1b2e; }
.mobile { display: flex; flex-direction: column; max-width: 1200px; margin: 0 auto; padding: 8px 24px 16px; border-top: 1px solid #e6e8f2; }
.mobile a { padding: 12px 0; color: #2b2e45; font-size: 15px; font-weight: 500; text-decoration: none; }
@media (min-width: 1024px) {
  .nav { display: flex; }
  .menu, .mobile { display: none; }
}
.modal { position: fixed; inset: 0; z-index: 50; display: grid; place-items: center; padding: 20px; }
.modal__mask { position: absolute; inset: 0; border: 0; background: rgba(10, 10, 30, 0.45); cursor: pointer; }
.modal__card { position: relative; width: min(458px, 100%); padding: 24px; border-radius: 16px; background: #fff; box-shadow: 0 24px 60px rgba(26, 27, 46, 0.18); }
.modal__head, .modal__foot { display: flex; align-items: center; justify-content: space-between; }
.modal__head h2 { margin: 0; color: #1a1b2e; font-size: 16px; }
.modal__head button { border: 0; background: transparent; color: #7c7f99; font-size: 22px; cursor: pointer; }
.modal__copy { margin-top: 20px; color: #4e5069; font-size: 14px; line-height: 1.9; }
.modal__copy p { margin: 0; }
.modal__copy span, .modal__copy a { color: #4348db; font-weight: 600; }
.modal form input {
  width: 100%; height: 38px; margin-top: 16px; padding: 0 14px; border: 1px solid #e6e8f2; border-radius: 10px; color: #1a1b2e;
}
.hint { margin: 16px 0 0; color: #4e5069; font-size: 14px; }
.error { margin: 8px 0 0; color: #e5484d; font-size: 12px; }
.modal__foot { justify-content: flex-end; margin-top: 20px; }
.sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
</style>
