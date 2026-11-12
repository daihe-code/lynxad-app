<template>
  <div class="login-page">
    <svg xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true">
      <symbol id="lynx-mark" viewBox="0 0 1300 935">
        <g fill="none" stroke="currentColor" stroke-linecap="round">
          <path d="M1223.8 855.9C1219.3 837.9 1208.2 783 1196.8 747.6C1185.4 712.3 1170.6 677.8 1155.6 643.8C1140.7 609.7 1124.4 576.1 1107.2 543.1C1090 510.1 1072 477.5 1052.5 445.8C1033 414.1 1012.4 382.9 990.4 353C968.3 323 945.2 293.5 920.4 265.8C895.7 238.1 870.2 210.1 841.8 186.6C813.4 163.1 781.9 143.6 750 125C718 106.4 683.3 75 650 75C616.7 75 582 106.4 550 125C518.1 143.6 486.6 163.1 458.2 186.6C429.8 210.1 404.3 238.1 379.6 265.8C354.8 293.5 331.7 323 309.6 353C287.6 382.9 267 414.1 247.5 445.8C228 477.5 210 510.1 192.8 543.1C175.6 576.1 159.3 609.7 144.4 643.8C129.4 677.8 114.6 712.3 103.2 747.6C91.8 783 80.7 837.9 76.2 855.9" stroke-width="150"/>
          <path d="M82.8 175.9L285 455" stroke-width="58"/>
          <path d="M1217.2 175.9L1015 455" stroke-width="58"/>
        </g>
        <circle cx="440" cy="559" r="82.5" fill="currentColor"/>
        <circle cx="860" cy="559" r="82.5" fill="currentColor"/>
      </symbol>
      <symbol id="ic-shield" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 2.9 4.8 5.9v6.2c0 4.3 3 8 7.2 9.1 4.2-1.1 7.2-4.8 7.2-9.1V5.9L12 2.9Z"/>
        <path d="m9.1 12 2 2 3.9-3.9"/>
      </symbol>
      <symbol id="ic-media" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 20H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2v6"/>
        <circle cx="8" cy="8" r="1.5"/>
        <path d="m3 16 4-4 4 4 3-3"/>
        <circle cx="18" cy="18" r="5"/>
        <path d="m17 15.8 3.2 2.2-3.2 2.2Z" fill="currentColor" stroke="none"/>
      </symbol>
      <symbol id="ic-bell" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <path d="M18.2 8.8a6.2 6.2 0 1 0-12.4 0c0 5.3-2.1 6.9-2.1 6.9h16.6s-2.1-1.6-2.1-6.9Z"/>
        <path d="M13.7 19.2a1.9 1.9 0 0 1-3.4 0"/>
      </symbol>
      <symbol id="ic-calendar" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <rect x="3" y="4.5" width="18" height="17" rx="2"/>
        <path d="M7 2v5M17 2v5M3 10h18M8 14h3v3H8zM14 14h3v3h-3z"/>
      </symbol>
      <symbol id="ic-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <path d="M2.6 12S6.1 5.9 12 5.9 21.4 12 21.4 12 17.9 18.1 12 18.1 2.6 12 2.6 12Z"/>
        <circle cx="12" cy="12" r="3.1"/>
      </symbol>
      <symbol id="ic-eye-off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <path d="M10.6 6.1A9 9 0 0 1 12 6c5.9 0 9.4 6 9.4 6a17.4 17.4 0 0 1-3.4 4"/>
        <path d="M6.2 7.9A17.4 17.4 0 0 0 2.6 12s3.5 6 9.4 6a9.4 9.4 0 0 0 4.4-1.1"/>
        <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>
        <path d="m3.6 3.6 16.8 16.8"/>
      </symbol>
      <symbol id="ic-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="m9.6 6 6 6-6 6"/>
      </symbol>
      <symbol id="ic-alert" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="9"/>
        <path d="M12 7.8v4.7M12 16.2h.01"/>
      </symbol>
    </svg>

    <main class="auth">

      <aside class="auth__brand" aria-label="坤境智营 产品介绍">
        <div class="auth__brand-inner">

          <div class="auth-brand">
            <span class="auth-brand__mark">
              <svg class="h-[26px] w-[26px]" viewBox="0 0 1300 935" aria-hidden="true"><use href="#lynx-mark"/></svg>
            </span>
            <span class="auth-brand__name">坤境智营</span>
            <p class="auth-brand__slogan">让每一位商家都能获得专家级投放效果</p>
          </div>

          <ul class="auth-features">
            <li class="auth-feature">
              <span class="auth-feature__icon">
                <svg class="h-8 w-8" aria-hidden="true"><use href="#ic-media"/></svg>
              </span>
              <div>
                <h3 class="auth-feature__title">素材制作，投放管理更省心</h3>
                <p class="auth-feature__desc">坤境智能生成投放素材，协助完成投放设置与计划管理，减少重复操作。</p>
              </div>
            </li>
            <li class="auth-feature">
              <span class="auth-feature__icon">
                <svg class="h-8 w-8" aria-hidden="true"><use href="#ic-bell"/></svg>
              </span>
              <div>
                <h3 class="auth-feature__title">全天盯盘，异常及时提醒</h3>
                <p class="auth-feature__desc">持续关注消耗与投放表现，发现异常及时提醒，让日常投放管理更省心。</p>
              </div>
            </li>
            <li class="auth-feature">
              <span class="auth-feature__icon">
                <svg class="h-8 w-8" aria-hidden="true"><use href="#ic-shield"/></svg>
              </span>
              <div>
                <h3 class="auth-feature__title">预算自主，关键操作先确认</h3>
                <p class="auth-feature__desc">投入多少，由你决定。关键操作先确认，调整有依据，过程可查看。</p>
              </div>
            </li>
            <li class="auth-feature">
              <span class="auth-feature__icon">
                <svg class="h-8 w-8" aria-hidden="true"><use href="#ic-calendar"/></svg>
              </span>
              <div>
                <h3 class="auth-feature__title">免费体验，满意再选订阅</h3>
                <p class="auth-feature__desc">平台服务免费体验 3 天，体验后自主选择是否订阅。广告费仍由各媒体平台收取。</p>
              </div>
            </li>
          </ul>

        </div>

        <p class="auth__copyright">© 2026 坤境 LynxAd. All rights reserved.</p>
      </aside>

      <div class="auth__panel">
        <div class="auth-card">

          <router-link class="auth-back" to="/">
            <svg class="h-4 w-4 rotate-180" aria-hidden="true"><use href="#ic-chevron"/></svg>
            <span>返回首页</span>
          </router-link>

          <h1 class="auth-title">{{ invitation ? '激活子账户' : '登录/注册' }}</h1>
          <p class="auth-subtitle">开启智能广告投放新体验</p>
          <p v-if="resetSuccess" class="auth-subtitle" role="status">密码已重置，请使用新密码登录。</p>

          <div v-show="!resetting" class="auth-tabs" role="tablist" aria-label="登录方式">
            <button class="auth-tab" type="button" role="tab" id="tab-code"
                    :aria-selected="tab === 'sms'" :tabindex="tab === 'sms' ? 0 : -1" aria-controls="panel-code" @click="switchTab('sms')" @keydown="onTabKeydown($event, 'sms')">
              验证码登录/注册
            </button>
            <button v-if="!invitation" class="auth-tab" type="button" role="tab" id="tab-password"
                    :aria-selected="tab === 'password'" :tabindex="tab === 'password' ? 0 : -1" aria-controls="panel-password" @click="switchTab('password')" @keydown="onTabKeydown($event, 'password')">
              密码登录
            </button>
          </div>

          <form class="auth-form" id="panel-code" role="tabpanel" aria-labelledby="tab-code"
                novalidate :hidden="resetting || tab !== 'sms'" @submit.prevent="onSmsLogin">

            <div class="field" :data-invalid="errors.smsPhone ? 'true' : undefined" data-field="phone">
              <label class="field__label" for="code-phone">手机号</label>
              <div class="field__control">
                <span class="input-affix" aria-hidden="true">+86</span>
                <input class="input tnum" id="code-phone" v-model="phone" :readonly="loading" :aria-invalid="errors.smsPhone ? true : undefined" @input="clearFieldError('smsPhone')" @blur="validateOnBlur('smsPhone')" name="phone" type="tel" inputmode="numeric"
                       autocomplete="tel" maxlength="11" placeholder="请输入手机号"
                       aria-describedby="code-phone-error">
              </div>
              <p class="field__error" id="code-phone-error" role="alert">
                <svg class="h-4 w-4 shrink-0" aria-hidden="true"><use href="#ic-alert"/></svg>
                <span>{{ errors.smsPhone }}</span>
              </p>
            </div>

            <div class="field" :data-invalid="errors.code ? 'true' : undefined" data-field="sms">
              <label class="field__label" for="code-sms">验证码</label>
              <div class="field__control">
                <input class="input tnum" id="code-sms" v-model="code" :readonly="loading" :aria-invalid="errors.code ? true : undefined" @input="clearFieldError('code')" @blur="validateOnBlur('code')" name="sms" type="text" inputmode="numeric"
                       autocomplete="one-time-code" maxlength="6" placeholder="请输入验证码"
                       aria-describedby="code-sms-error">
                <button class="btn-code" type="button" @click="onSend" :disabled="cooldown > 0 || sending || loading">{{ sending ? "发送中…" : cooldown > 0 ? `${cooldown}s 后重新获取` : "获取验证码" }}</button>
              </div>
              <p class="field__error" :class="{ 'field__error--visible': sendError }" id="code-sms-error" role="alert">
                <svg class="h-4 w-4 shrink-0" aria-hidden="true"><use href="#ic-alert"/></svg>
                <span>{{ sendError || errors.code }}</span>
              </p>
            </div>

            <button class="btn-brand" type="submit" :disabled="loading" :data-loading="loading">{{ loading ? "登录中…" : "登录/注册" }}</button>

            <div class="auth-legal" :data-attention="legalAttention ? 'true' : undefined">
              <label class="auth-legal__control" for="code-agree"><input class="checkbox" id="code-agree" v-model="agreed" name="agree" type="checkbox" @change="legalError = ''"></label>
              <label for="code-agree" class="auth-legal__text cursor-pointer">
                我已阅读并同意
                <router-link class="auth-legal__link" to="/legal/terms" target="_blank" rel="noopener">《用户服务协议》</router-link>
                和
                <router-link class="auth-legal__link" to="/legal/privacy" target="_blank" rel="noopener">《隐私政策》</router-link>
              </label>
              <p v-if="legalError" class="legal-error" role="alert"><svg width="16" height="16" aria-hidden="true"><use href="#ic-alert"/></svg><span>{{ legalError }}</span></p>
            </div>
          </form>

          <form class="auth-form" id="panel-password" role="tabpanel" aria-labelledby="tab-password"
                novalidate :hidden="resetting || tab !== 'password'" @submit.prevent="onPasswordLogin">

            <div class="field" :data-invalid="errors.pwdPhone ? 'true' : undefined" data-field="phone">
              <label class="field__label" for="pwd-phone">手机号</label>
              <div class="field__control">
                <input class="input tnum" id="pwd-phone" v-model="pwdPhone" :readonly="loading" :aria-invalid="errors.pwdPhone ? true : undefined" @input="clearFieldError('pwdPhone')" @blur="validateOnBlur('pwdPhone')" name="phone" type="tel" inputmode="numeric"
                       autocomplete="tel" maxlength="11" placeholder="请输入手机号"
                       aria-describedby="pwd-phone-error">
              </div>
              <p class="field__error" id="pwd-phone-error" role="alert">
                <svg class="h-4 w-4 shrink-0" aria-hidden="true"><use href="#ic-alert"/></svg>
                <span>{{ errors.pwdPhone }}</span>
              </p>
            </div>

            <div class="field" :data-invalid="errors.password ? 'true' : undefined" data-field="password">
              <label class="field__label" for="pwd-password">密码</label>
              <div class="field__control">
                <input class="input pr-14" id="pwd-password" v-model="password" :readonly="loading" :aria-invalid="errors.password ? true : undefined" @input="clearFieldError('password')" @blur="validateOnBlur('password')" name="password" :type="showPassword ? 'text' : 'password'"
                       autocomplete="current-password" maxlength="20" placeholder="请输入密码"
                       aria-describedby="pwd-password-error">
                <button class="input-icon-btn" type="button" @click="showPassword = !showPassword"
                        :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword">
                  <svg class="h-[22px] w-[22px]" aria-hidden="true"><use :href="showPassword ? '#ic-eye-off' : '#ic-eye'"/></svg>
                </button>
              </div>
              <p class="field__error" id="pwd-password-error" role="alert">
                <svg class="h-4 w-4 shrink-0" aria-hidden="true"><use href="#ic-alert"/></svg>
                <span>{{ errors.password }}</span>
              </p>
            </div>

            <a class="auth-forgot" href="#" @click.prevent="openPasswordReset">忘记密码？</a>

            <button class="btn-brand" type="submit" :disabled="loading" :data-loading="loading">{{ loading ? "登录中…" : "登录" }}</button>

            <div class="auth-legal" :data-attention="legalAttention ? 'true' : undefined">
              <label class="auth-legal__control" for="pwd-agree"><input class="checkbox" id="pwd-agree" v-model="agreed" name="agree" type="checkbox" @change="legalError = ''"></label>
              <label for="pwd-agree" class="auth-legal__text cursor-pointer">
                我已阅读并同意
                <router-link class="auth-legal__link" to="/legal/terms" target="_blank" rel="noopener">《用户服务协议》</router-link>
                和
                <router-link class="auth-legal__link" to="/legal/privacy" target="_blank" rel="noopener">《隐私政策》</router-link>
              </label>
              <p v-if="legalError" class="legal-error" role="alert"><svg width="16" height="16" aria-hidden="true"><use href="#ic-alert"/></svg><span>{{ legalError }}</span></p>
            </div>
          </form>

          <form v-if="resetting" class="auth-form" novalidate aria-label="重置密码" @submit.prevent="onResetPassword">
            <div class="field" :data-invalid="errors.smsPhone ? 'true' : undefined">
              <label class="field__label" for="reset-phone">手机号</label>
              <div class="field__control">
                <input id="reset-phone" v-model="phone" class="input tnum" name="phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="11" placeholder="请输入手机号" :readonly="loading" :aria-invalid="errors.smsPhone ? true : undefined" aria-describedby="reset-phone-error" @input="clearFieldError('smsPhone')" @blur="validateOnBlur('smsPhone')">
              </div>
              <p id="reset-phone-error" class="field__error" role="alert"><span>{{ errors.smsPhone }}</span></p>
            </div>
            <div class="field" :data-invalid="errors.code ? 'true' : undefined">
              <label class="field__label" for="reset-code">验证码</label>
              <div class="field__control">
                <input id="reset-code" v-model="code" class="input tnum" name="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="请输入验证码" :readonly="loading" :aria-invalid="errors.code ? true : undefined" aria-describedby="reset-code-error" @input="clearFieldError('code')" @blur="validateOnBlur('code')">
                <button class="btn-code" type="button" :disabled="cooldown > 0 || sending || loading" @click="onSend">{{ sending ? "发送中…" : cooldown > 0 ? `${cooldown}s 后重新获取` : "获取验证码" }}</button>
              </div>
              <p id="reset-code-error" class="field__error" :class="{ 'field__error--visible': sendError }" role="alert"><span>{{ sendError || errors.code }}</span></p>
            </div>
            <div class="field" :data-invalid="errors.newPassword ? 'true' : undefined">
              <label class="field__label" for="reset-password">新密码</label>
              <div class="field__control">
                <input id="reset-password" v-model="newPassword" class="input" name="newPassword" type="password" autocomplete="new-password" placeholder="8–20 位，包含字母和数字" :readonly="loading" :aria-invalid="errors.newPassword ? true : undefined" aria-describedby="reset-password-error" @input="clearFieldError('newPassword'); clearFieldError('confirmPassword')" @blur="validateOnBlur('newPassword')">
              </div>
              <p id="reset-password-error" class="field__error" role="alert"><span>{{ errors.newPassword }}</span></p>
            </div>
            <div class="field" :data-invalid="errors.confirmPassword ? 'true' : undefined">
              <label class="field__label" for="reset-confirm">确认新密码</label>
              <div class="field__control">
                <input id="reset-confirm" v-model="confirmPassword" class="input" name="confirmPassword" type="password" autocomplete="new-password" placeholder="请再次输入新密码" :readonly="loading" :aria-invalid="errors.confirmPassword ? true : undefined" aria-describedby="reset-confirm-error" @input="clearFieldError('confirmPassword')" @blur="validateOnBlur('confirmPassword')">
              </div>
              <p id="reset-confirm-error" class="field__error" role="alert"><span>{{ errors.confirmPassword }}</span></p>
            </div>
            <button class="btn-brand" type="submit" :disabled="loading" :data-loading="loading">{{ loading ? '重置中…' : '重置密码' }}</button>
            <button class="auth-forgot" type="button" :disabled="loading" @click="switchTab('password', true)">返回密码登录</button>
          </form>

        </div>
      </div>

    </main>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { resetPassword, sendSms } from "@/features/tenant/authApi";
import { resetPasswordError } from "./loginRecovery";
import { requestFailure } from "@/api/apiErrors";
import { useUserStore } from "@/stores/user";
import { isCnMobile } from "@/utils/phone";

const route = useRoute(), router = useRouter(), user = useUserStore();
const invitation = new URLSearchParams(route.hash.slice(1)).get('invite') || undefined;
type LoginTab = "sms" | "password";
type FieldKey = "smsPhone" | "code" | "pwdPhone" | "password" | "newPassword" | "confirmPassword";
const tab = ref<LoginTab>(!invitation && route.query.tab === "password" ? "password" : "sms");
const phone = ref(""), code = ref(""), pwdPhone = ref(""), password = ref("");
const agreed = ref(false), showPassword = ref(false);
const resetting = ref(false), resetSuccess = ref(false), newPassword = ref(""), confirmPassword = ref("");
const loading = ref(false), sending = ref(false), cooldown = ref(0);
const sendError = ref("");
const errors = reactive<Record<FieldKey, string>>({ smsPhone: "", code: "", pwdPhone: "", password: "", newPassword: "", confirmPassword: "" });
const legalAttention = ref(false), legalError = ref("");
const fieldIds: Record<FieldKey, string> = { smsPhone: "code-phone", code: "code-sms", pwdPhone: "pwd-phone", password: "pwd-password", newPassword: "reset-password", confirmPassword: "reset-confirm" };
let timer: number | undefined, attentionTimer: number | undefined;
let previousTitle = "";
let disposed = false;

onMounted(() => {
  previousTitle = document.title;
  document.title = "登录坤境智营 · 让每一位商家都能获得专家级投放效果";
});
onUnmounted(() => {
  disposed = true;
  document.title = previousTitle;
  if (timer) window.clearInterval(timer);
  if (attentionTimer) window.clearTimeout(attentionTimer);
});

function valueOf(key: FieldKey) {
  return { smsPhone: phone.value.trim(), code: code.value.trim(), pwdPhone: pwdPhone.value.trim(), password: password.value, newPassword: newPassword.value, confirmPassword: confirmPassword.value }[key];
}
function validateField(key: FieldKey) {
  const value = valueOf(key);
  if (key === "newPassword") {
    errors[key] = resetPasswordError(value);
    return !errors[key];
  }
  if (key === "confirmPassword") {
    errors[key] = value && value === newPassword.value ? "" : "两次输入的密码不一致";
    return !errors[key];
  }
  const valid = key === "code" ? /^\d{6}$/.test(value) : key === "password" ? value.length >= 6 && value.length <= 20 : isCnMobile(value);
  errors[key] = valid ? "" : key === "code" ? "请输入 6 位数字验证码" : key === "password" ? "密码长度需为 6–20 位" : "请输入 11 位有效手机号";
  return valid;
}
function clearFieldError(key: FieldKey) {
  if (key === "smsPhone" || key === "code") sendError.value = "";
  if (errors[key]) validateField(key);
}
function validateOnBlur(key: FieldKey) { if (valueOf(key)) validateField(key); }

async function switchTab(next: LoginTab, focus = false) {
  if (loading.value) return;
  if (next !== tab.value || resetting.value) {
    const retainedPhone = resetting.value || tab.value === "sms" ? phone.value : pwdPhone.value;
    phone.value = retainedPhone;
    pwdPhone.value = retainedPhone;
    clearSensitiveFields();
  }
  resetting.value = false;
  resetSuccess.value = false;
  tab.value = next;
  legalError.value = "";
  if (focus) { await nextTick(); document.getElementById(next === "sms" ? "code-phone" : "pwd-phone")?.focus(); }
}
function onTabKeydown(event: KeyboardEvent, current: LoginTab) {
  if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  event.preventDefault();
  switchTab(event.key === "Home" ? "sms" : event.key === "End" ? "password" : current === "sms" ? "password" : "sms", true);
}
function clearSensitiveFields() {
  sendError.value = "";
  password.value = "";
  code.value = "";
  newPassword.value = "";
  confirmPassword.value = "";
  showPassword.value = false;
  for (const key of Object.keys(errors) as FieldKey[]) errors[key] = "";
  legalError.value = "";
  legalAttention.value = false;
}
async function openPasswordReset() {
  if (loading.value) return;
  phone.value = pwdPhone.value;
  clearSensitiveFields();
  resetSuccess.value = false;
  resetting.value = true;
  await nextTick();
  document.getElementById("reset-phone")?.focus();
}
function focusField(key: FieldKey) {
  const id = resetting.value && key === "smsPhone" ? "reset-phone" : resetting.value && key === "code" ? "reset-code" : fieldIds[key];
  document.getElementById(id)?.focus();
}

function startCooldown(seconds: number) {
  if (timer) window.clearInterval(timer);
  const deadline = Date.now() + seconds * 1000;
  const update = () => {
    cooldown.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
    if (cooldown.value === 0 && timer) { window.clearInterval(timer); timer = undefined; }
  };
  update();
  timer = window.setInterval(update, 1000);
}
async function onSend() {
  if (sending.value || cooldown.value > 0 || loading.value) return;
  sendError.value = "";
  if (!validateField("smsPhone")) {
    errors.smsPhone = "手机号错误，验证码发送失败";
    focusField("smsPhone");
    return;
  }
  const recipient = phone.value.trim();
  sending.value = true;
  try {
    const result = await sendSms(recipient, { headers: { "X-Quiet-Request": "true" } });
    if (disposed) return;
    const seconds = result.cooldownSec ?? result.ttlSec;
    if (Number.isFinite(seconds) && seconds > 0) startCooldown(seconds);
  } catch (error) {
    if (disposed || recipient !== phone.value.trim()) return;
    const failure = requestFailure(error, { method: "POST" });
    if (failure.kind === "CANCELLED") return;
    if (failure.code === 1001) {
      errors.smsPhone = failure.message;
      focusField("smsPhone");
    } else sendError.value = failure.message;
  }
  finally { sending.value = false; }
}

function validateForm(keys: FieldKey[]) {
  keys.forEach(validateField);
  if (!agreed.value) {
    legalError.value = "请先勾选用户服务协议和隐私政策";
    legalAttention.value = true;
    if (attentionTimer) window.clearTimeout(attentionTimer);
    attentionTimer = window.setTimeout(() => { legalAttention.value = false; }, 400);
  } else legalError.value = "";
  const invalid = keys.find(key => errors[key]);
  if (invalid || !agreed.value) {
    document.getElementById(invalid ? fieldIds[invalid] : tab.value === "sms" ? "code-agree" : "pwd-agree")?.focus();
    return false;
  }
  return true;
}
async function onSmsLogin() {
  if (loading.value || !validateForm(["smsPhone", "code"])) return;
  loading.value = true;
  try {
    await user.loginBySms(phone.value.trim(), code.value.trim(), invitation);
    if (!disposed) await afterLogin();
  } catch { /* The API interceptor reports failed authentication. */ }
  finally { loading.value = false; }
}
async function onPasswordLogin() {
  if (loading.value || !validateForm(["pwdPhone", "password"])) return;
  loading.value = true;
  try {
    await user.loginByPassword(pwdPhone.value.trim(), password.value);
    if (!disposed) await afterLogin();
  } catch { /* Keep the form available for correction and retry. */ }
  finally { loading.value = false; }
}
async function onResetPassword() {
  if (loading.value) return;
  const keys: FieldKey[] = ["smsPhone", "code", "newPassword", "confirmPassword"];
  keys.forEach(validateField);
  const invalid = keys.find(key => errors[key]);
  if (invalid) { focusField(invalid); return; }
  loading.value = true;
  try {
    await resetPassword(phone.value.trim(), code.value.trim(), newPassword.value);
    if (disposed) return;
    loading.value = false;
    await switchTab("password", true);
    resetSuccess.value = true;
  } catch { /* The API interceptor reports reset failures and leaves the form available. */ }
  finally { loading.value = false; }
}
async function afterLogin() {
  const destination = await user.entryPath();
  if (!disposed) await router.replace(destination);
}
</script>

<style scoped src="./login.css"></style>
