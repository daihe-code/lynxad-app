<template>
  <div class="auth">
    <aside class="auth__brand" aria-label="灵投智营 产品介绍">
      <div class="auth__brand-inner">
        <div class="auth-brand">
          <span class="auth-brand__mark" aria-hidden="true">
            <svg viewBox="0 0 1300 935" width="26" height="26">
              <use href="#lynx-mark" />
            </svg>
          </span>
          <span class="auth-brand__name">灵投智营</span>
          <p class="auth-brand__slogan">让中小微企业也能获得专家级广告投放效果</p>
        </div>
        <ul class="auth-features">
          <li v-for="item in features" :key="item.title" class="auth-feature">
            <span class="auth-feature__icon" v-html="item.icon" />
            <div>
              <h3 class="auth-feature__title">{{ item.title }}</h3>
              <p class="auth-feature__desc">{{ item.desc }}</p>
            </div>
          </li>
        </ul>
      </div>
      <p class="auth__copyright">© 2026 灵投 LynxAd. All rights reserved.</p>
    </aside>

    <div class="auth__panel">
      <div class="auth-card">
        <router-link class="auth-back" to="/">
          <span aria-hidden="true">‹</span>
          <span>返回首页</span>
        </router-link>
        <h1 class="auth-title">欢迎登录</h1>
        <p class="auth-subtitle">登录后即可开始智能广告投放</p>

        <div class="auth-tabs" role="tablist">
          <button
            type="button"
            class="auth-tab"
            role="tab"
            :aria-selected="tab === 'sms'"
            @click="tab = 'sms'"
          >
            验证码登录
          </button>
          <button
            type="button"
            class="auth-tab"
            role="tab"
            :aria-selected="tab === 'password'"
            @click="tab = 'password'"
          >
            密码登录
          </button>
        </div>

        <form v-if="tab === 'sms'" class="auth-form" @submit.prevent="onSmsLogin">
          <label class="field">
            <span class="field__label">手机号</span>
            <span class="field__control">
              <span class="input-affix">+86</span>
              <input v-model="phone" class="input" inputmode="numeric" maxlength="11" placeholder="请输入手机号" />
            </span>
          </label>
          <label class="field">
            <span class="field__label">验证码</span>
            <span class="field__control">
              <input v-model="code" class="input" inputmode="numeric" maxlength="8" placeholder="请输入验证码" />
              <button class="btn-code" type="button" :disabled="cooldown > 0 || sending" @click="onSend">
                {{ cooldown > 0 ? `${cooldown}s 后重新获取` : "获取验证码" }}
              </button>
            </span>
          </label>
          <button class="btn-brand" type="submit" :disabled="loading">登录</button>
          <label class="auth-legal">
            <input v-model="agreed" class="checkbox" type="checkbox" />
            <span>
              我已阅读并同意
              <router-link class="auth-legal__link" to="/legal/terms" target="_blank">用户服务协议</router-link>
              和
              <router-link class="auth-legal__link" to="/legal/privacy" target="_blank">隐私政策</router-link>
            </span>
          </label>
        </form>

        <form v-else class="auth-form" @submit.prevent="onPasswordLogin">
          <label class="field">
            <span class="field__label">手机号</span>
            <span class="field__control">
              <input v-model="pwdPhone" class="input" inputmode="numeric" maxlength="11" placeholder="请输入手机号" />
            </span>
          </label>
          <label class="field">
            <span class="field__label">密码</span>
            <span class="field__control">
              <input
                v-model="password"
                class="input input--secret"
                :type="showPassword ? 'text' : 'password'"
                placeholder="请输入密码"
              />
              <button class="input-icon-btn" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                {{ showPassword ? "隐" : "显" }}
              </button>
            </span>
          </label>
          <button class="auth-forgot" type="button" @click="useSmsInstead">忘记密码？使用验证码登录</button>
          <button class="btn-brand" type="submit" :disabled="loading">登录</button>
          <label class="auth-legal">
            <input v-model="agreed" class="checkbox" type="checkbox" />
            <span>
              我已阅读并同意
              <router-link class="auth-legal__link" to="/legal/terms" target="_blank">用户服务协议</router-link>
              和
              <router-link class="auth-legal__link" to="/legal/privacy" target="_blank">隐私政策</router-link>
            </span>
          </label>
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
import { onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { sendSms } from "@/features/tenant/authApi";
import { useUserStore } from "@/stores/user";
import { isCnMobile, PHONE_ERROR } from "@/utils/phone";

const HOME_TITLE = "灵投 LynxAd · 替你投出效果";
const PAGE_TITLE = "登录灵投智营 · 让中小微企业也能获得专家级广告投放效果";

const features = [
  {
    title: "7×24小时自主盯盘+优化",
    desc: "全天候监控投放状态并自主优化，自动处置异常，不需人工盯盘，保障账户安全稳健、投放效果最优。",
    icon: `<svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 2.9 4.8 5.9v6.2c0 4.3 3 8 7.2 9.1 4.2-1.1 7.2-4.8 7.2-9.1V5.9L12 2.9Z"/><path d="m9.1 12 2 2 3.9-3.9"/></svg>`,
  },
  {
    title: "先看效果再付费",
    desc: "7天免费体验全功能，效果到了再付费，降本增效看得见。",
    icon: `<svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3.6 16.6 9.2 11l3.4 3.4 7.8-7.8"/><path d="M15.4 6.6h5v5"/></svg>`,
  },
  {
    title: "1分钟极速启动",
    desc: "从注册到正式开始投放仅需1分钟，全自动化引导，零学习成本，上手快。",
    icon: `<svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M18.2 8.8a6.2 6.2 0 1 0-12.4 0c0 5.3-2.1 6.9-2.1 6.9h16.6s-2.1-1.6-2.1-6.9Z"/><path d="M13.7 19.2a1.9 1.9 0 0 1-3.4 0"/></svg>`,
  },
  {
    title: "每步操作可追溯",
    desc: "操作可解释、可干预、可回滚，灵投每一步决策都有据可查，透明投放更放心。",
    icon: `<svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3.9 15.2v-3.1a8.1 8.1 0 0 1 16.2 0v3.1"/><rect x="2.6" y="12.9" width="4.5" height="6.4" rx="2.2"/><rect x="16.9" y="12.9" width="4.5" height="6.4" rx="2.2"/></svg>`,
  },
];

const route = useRoute();
const router = useRouter();
const user = useUserStore();

const tab = ref<"sms" | "password">("sms");
const phone = ref("");
const code = ref("");
const pwdPhone = ref("");
const password = ref("");
const agreed = ref(false);
const showPassword = ref(false);
const loading = ref(false);
const sending = ref(false);
const cooldown = ref(0);
let timer: number | undefined;

onMounted(() => {
  document.title = PAGE_TITLE;
});

onUnmounted(() => {
  document.title = HOME_TITLE;
  if (timer) window.clearInterval(timer);
});

function startCooldown(sec: number) {
  cooldown.value = sec;
  if (timer) window.clearInterval(timer);
  timer = window.setInterval(() => {
    cooldown.value -= 1;
    if (cooldown.value <= 0 && timer) {
      window.clearInterval(timer);
      timer = undefined;
    }
  }, 1000);
}

function useSmsInstead() {
  tab.value = "sms";
  if (pwdPhone.value) phone.value = pwdPhone.value;
}

async function onSend() {
  if (!isCnMobile(phone.value)) {
    ElMessage.warning(PHONE_ERROR);
    return;
  }
  sending.value = true;
  try {
    const result = await sendSms(phone.value.trim());
    const sec = result.cooldownSec ?? result.ttlSec;
    if (sec && sec > 0) startCooldown(sec);
  } catch {
    /* interceptor already toasted; do not start a countdown */
  } finally {
    sending.value = false;
  }
}

async function onSmsLogin() {
  if (!agreed.value) {
    ElMessage.warning("请先勾选用户服务协议和隐私政策");
    return;
  }
  if (!isCnMobile(phone.value)) {
    ElMessage.warning(PHONE_ERROR);
    return;
  }
  if (!code.value.trim()) {
    ElMessage.warning("请输入验证码");
    return;
  }
  loading.value = true;
  try {
    await user.loginBySms(phone.value.trim(), code.value.trim());
    await afterLogin();
  } finally {
    loading.value = false;
  }
}

async function onPasswordLogin() {
  if (!isCnMobile(pwdPhone.value)) {
    ElMessage.warning(PHONE_ERROR);
    return;
  }
  if (!password.value) {
    ElMessage.warning("请输入密码");
    return;
  }
  loading.value = true;
  try {
    await user.loginByPassword(pwdPhone.value.trim(), password.value);
    await afterLogin();
  } finally {
    loading.value = false;
  }
}

async function afterLogin() {
  if (user.needOnboarding) {
    await router.replace("/onboarding");
    return;
  }
  const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/campaigns";
  await router.replace(redirect);
}
</script>

<style scoped>
.sprite { position: absolute; width: 0; height: 0; overflow: hidden; }
.auth { display: flex; min-height: 100vh; color: #4e5069; font-family: system-ui, "PingFang SC", "Microsoft YaHei", sans-serif; }
.auth__brand {
  display: none;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #596afe, #4a4ee3 52%, #3b3bbd);
}
.auth__brand::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle 372px at 2% 3%, hsla(0, 0%, 100%, 0.075), transparent 100%),
    radial-gradient(circle 318px at 43% 47%, hsla(0, 0%, 100%, 0.055), transparent 100%),
    radial-gradient(circle 190px at 90% 75%, hsla(0, 0%, 100%, 0.075), transparent 100%);
}
@media (min-width: 1024px) {
  .auth__brand { display: flex; width: 55%; flex-direction: column; align-items: center; justify-content: center; }
  .auth__panel { width: 45%; }
}
.auth__brand-inner { position: relative; z-index: 1; width: 100%; max-width: 434px; padding: 0 24px 88px; }
.auth__copyright { position: absolute; left: 0; right: 0; bottom: 46px; z-index: 1; text-align: center; font-size: 13px; color: hsla(0, 0%, 100%, 0.55); }
.auth-brand { display: flex; flex-direction: column; align-items: center; text-align: center; }
.auth-brand__mark {
  display: grid; place-items: center; width: 50px; height: 50px; border-radius: 14px;
  background: #fff; color: #575cf6; box-shadow: 0 10px 26px -10px rgba(10, 12, 60, 0.55);
}
.auth-brand__name { margin-top: 18px; color: #fff; font-size: 36px; font-weight: 700; line-height: 1; letter-spacing: -0.025em; }
.auth-brand__slogan { margin-top: 14px; color: hsla(0, 0%, 100%, 0.7); font-size: 15px; line-height: 1.6; }
.auth-features { margin-top: 86px; display: flex; flex-direction: column; gap: 38px; }
.auth-feature { display: flex; align-items: flex-start; gap: 16px; text-align: left; }
.auth-feature__icon {
  display: grid; place-items: center; width: 48px; height: 48px; flex-shrink: 0; margin-top: -2px;
  border-radius: 999px; border: 1px solid hsla(0, 0%, 100%, 0.25); background: hsla(0, 0%, 100%, 0.115); color: #fff;
}
.auth-feature__title { color: #fff; font-size: 18px; font-weight: 600; line-height: 1.4; }
.auth-feature__desc { margin-top: 8px; color: hsla(0, 0%, 100%, 0.6); font-size: 14px; line-height: 23px; }
.auth__panel { display: flex; width: 100%; align-items: center; justify-content: center; padding: 48px 20px; }
.auth-card { width: 100%; max-width: 480px; }
.auth-back { display: inline-flex; align-items: center; gap: 6px; color: #7c7f99; font-size: 14px; text-decoration: none; }
.auth-back:hover { color: #4348db; }
.auth-title { margin: 19px 0 0; color: #1a1b2e; font-size: 30px; font-weight: 700; line-height: 1.25; letter-spacing: -0.025em; }
.auth-subtitle { margin: 16px 0 0; color: #7c7f99; font-size: 16px; }
.auth-tabs { display: flex; margin-top: 44px; border-bottom: 3px solid #b4b4b4; }
.auth-tab {
  position: relative; flex: 1; padding: 0 0 13px; border: 0; background: transparent;
  color: #7c7f99; font-size: 18px; font-weight: 500; cursor: pointer;
}
.auth-tab[aria-selected="true"] { color: #575cf6; font-weight: 600; }
.auth-tab[aria-selected="true"]::after {
  content: ""; position: absolute; left: 0; right: 0; bottom: -3px; height: 3px; background: #575cf6;
}
.auth-form { display: flex; flex-direction: column; margin-top: 32px; }
.field { display: flex; flex-direction: column; }
.field + .field { margin-top: 21px; }
.field__label { color: #1a1b2e; font-size: 18px; font-weight: 600; }
.field__control { position: relative; display: flex; gap: 12px; margin-top: 8px; }
.input, .input-affix {
  height: 55px; border: 1px solid #ececec; border-radius: 10px; background: #fff; color: #1a1b2e; font-size: 16px;
}
.input { width: 100%; min-width: 0; padding: 0 16px; }
.input:focus { outline: none; border-color: #575cf6; box-shadow: 0 0 0 3px rgba(87, 92, 246, 0.14); }
.input--secret { padding-right: 56px; }
.input-affix { display: grid; place-items: center; width: 75px; flex-shrink: 0; }
.input-icon-btn {
  position: absolute; right: 6px; top: 50%; transform: translateY(-50%);
  width: 44px; height: 44px; border: 0; border-radius: 10px; background: transparent; color: #7c7f99; cursor: pointer;
}
.btn-code {
  width: 157px; flex-shrink: 0; border: 1px solid #575cf6; border-radius: 10px;
  background: #eaecff; color: #575cf6; font-size: 15px; cursor: pointer;
}
.btn-code:disabled { cursor: not-allowed; border-color: #e6e8f2; background: #fff; color: #7c7f99; }
.auth-forgot {
  align-self: flex-end; margin-top: 21px; border: 0; background: transparent;
  color: #575cf6; font-size: 15px; cursor: pointer;
}
.btn-brand {
  height: 55px; margin-top: 27px; border: 0; border-radius: 12px; cursor: pointer;
  background: linear-gradient(90deg, #575cf6, #3b82f6); color: #fff; font-size: 18px; font-weight: 600;
  box-shadow: 0 8px 22px -8px rgba(87, 92, 246, 0.5);
}
.btn-brand:disabled { cursor: not-allowed; opacity: 0.75; }
.auth-legal { display: flex; justify-content: center; gap: 10px; margin-top: 24px; color: #7c7f99; font-size: 14px; line-height: 1.55; }
.checkbox {
  width: 20px; height: 20px; margin-top: 3px; flex-shrink: 0; appearance: none; border: 1px solid #ececec;
  border-radius: 999px; background: #fff; cursor: pointer;
}
.checkbox:checked { border-color: #575cf6; background: #575cf6 url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 12 12'%3E%3Cpath fill='none' stroke='%23fff' stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='m1.6 6.3 2.9 2.8 5.9-6'/%3E%3C/svg%3E") center / 12px no-repeat; }
.auth-legal__link { color: #575cf6; text-decoration: none; }
</style>
