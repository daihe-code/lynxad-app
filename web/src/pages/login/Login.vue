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
      <symbol id="ic-trend-up" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3.6 16.6 9.2 11l3.4 3.4 7.8-7.8"/>
        <path d="M15.4 6.6h5v5"/>
      </symbol>
      <symbol id="ic-bell" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <path d="M18.2 8.8a6.2 6.2 0 1 0-12.4 0c0 5.3-2.1 6.9-2.1 6.9h16.6s-2.1-1.6-2.1-6.9Z"/>
        <path d="M13.7 19.2a1.9 1.9 0 0 1-3.4 0"/>
      </symbol>
      <symbol id="ic-headset" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3.9 15.2v-3.1a8.1 8.1 0 0 1 16.2 0v3.1"/>
        <rect x="2.6" y="12.9" width="4.5" height="6.4" rx="2.2"/>
        <rect x="16.9" y="12.9" width="4.5" height="6.4" rx="2.2"/>
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

      <aside class="auth__brand" aria-label="灵投智营 产品介绍">
        <div class="auth__brand-inner">

          <div class="auth-brand">
            <span class="auth-brand__mark">
              <svg class="h-[26px] w-[26px]" viewBox="0 0 1300 935" aria-hidden="true"><use href="#lynx-mark"/></svg>
            </span>
            <span class="auth-brand__name">灵投智营</span>
            <p class="auth-brand__slogan">让每一位商家都能获得专家级投放效果</p>
          </div>

          <ul class="auth-features">
            <li class="auth-feature">
              <span class="auth-feature__icon">
                <svg class="h-8 w-8" aria-hidden="true"><use href="#ic-shield"/></svg>
              </span>
              <div>
                <h3 class="auth-feature__title">持续盯盘，及时发现异常</h3>
                <p class="auth-feature__desc">持续关注投放表现，发现异常及时提醒，并在你确认的托管范围内执行优化。</p>
              </div>
            </li>
            <li class="auth-feature">
              <span class="auth-feature__icon">
                <svg class="h-8 w-8" aria-hidden="true"><use href="#ic-trend-up"/></svg>
              </span>
              <div>
                <h3 class="auth-feature__title">平台服务免费体验 7 天</h3>
                <p class="auth-feature__desc">先体验素材准备、投放管理与盯盘能力；广告费用仍由媒体平台收取。</p>
              </div>
            </li>
            <li class="auth-feature">
              <span class="auth-feature__icon">
                <svg class="h-8 w-8" aria-hidden="true"><use href="#ic-bell"/></svg>
              </span>
              <div>
                <h3 class="auth-feature__title">确认后再启动投放</h3>
                <p class="auth-feature__desc">授权不会立即投放，商品、预算、目标和素材均由你确认。</p>
              </div>
            </li>
            <li class="auth-feature">
              <span class="auth-feature__icon">
                <svg class="h-8 w-8" aria-hidden="true"><use href="#ic-headset"/></svg>
              </span>
              <div>
                <h3 class="auth-feature__title">每步操作可追溯</h3>
                <p class="auth-feature__desc">操作可解释、可干预、可回滚，灵投每一步决策都有据可查，透明投放更放心。</p>
              </div>
            </li>
          </ul>

        </div>

        <p class="auth__copyright">© 2026 灵投 LynxAd. All rights reserved.</p>
      </aside>

      <div class="auth__panel">
        <div class="auth-card">

          <router-link class="auth-back" to="/">
            <svg class="h-4 w-4 rotate-180" aria-hidden="true"><use href="#ic-chevron"/></svg>
            <span>返回首页</span>
          </router-link>

          <h1 class="auth-title">登录/注册</h1>
          <p class="auth-subtitle">开启智能广告投放新体验</p>

          <div class="auth-tabs" role="tablist" aria-label="登录方式">
            <button class="auth-tab" type="button" role="tab" id="tab-code"
                    :aria-selected="tab === 'sms'" :tabindex="tab === 'sms' ? 0 : -1" aria-controls="panel-code" @click="switchTab('sms')" @keydown="onTabKeydown($event, 'sms')">
              验证码登录/注册
            </button>
            <button class="auth-tab" type="button" role="tab" id="tab-password"
                    :aria-selected="tab === 'password'" :tabindex="tab === 'password' ? 0 : -1" aria-controls="panel-password" @click="switchTab('password')" @keydown="onTabKeydown($event, 'password')">
              密码登录
            </button>
          </div>

          <form class="auth-form" id="panel-code" role="tabpanel" aria-labelledby="tab-code"
                novalidate :hidden="tab !== 'sms'" @submit.prevent="onSmsLogin">

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
              <p class="field__error" id="code-sms-error" role="alert">
                <svg class="h-4 w-4 shrink-0" aria-hidden="true"><use href="#ic-alert"/></svg>
                <span>{{ errors.code }}</span>
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
                novalidate :hidden="tab !== 'password'" @submit.prevent="onPasswordLogin">

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

            <a class="auth-forgot" href="#" @click.prevent="useSmsInstead">忘记密码？</a>

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

        </div>
      </div>

    </main>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { sendSms } from "@/features/tenant/authApi";
import { useUserStore } from "@/stores/user";
import { isCnMobile } from "@/utils/phone";

const route = useRoute(), router = useRouter(), user = useUserStore();
type LoginTab = "sms" | "password";
type FieldKey = "smsPhone" | "code" | "pwdPhone" | "password";
const tab = ref<LoginTab>(route.query.tab === "password" ? "password" : "sms");
const phone = ref(""), code = ref(""), pwdPhone = ref(""), password = ref("");
const agreed = ref(false), showPassword = ref(false);
const loading = ref(false), sending = ref(false), cooldown = ref(0);
const errors = reactive<Record<FieldKey, string>>({ smsPhone: "", code: "", pwdPhone: "", password: "" });
const legalAttention = ref(false), legalError = ref("");
const fieldIds: Record<FieldKey, string> = { smsPhone: "code-phone", code: "code-sms", pwdPhone: "pwd-phone", password: "pwd-password" };
let timer: number | undefined, attentionTimer: number | undefined;
let previousTitle = "";
let disposed = false;

onMounted(() => {
  previousTitle = document.title;
  document.title = "登录灵投智营 · 让每一位商家都能获得专家级投放效果";
});
onUnmounted(() => {
  disposed = true;
  document.title = previousTitle;
  if (timer) window.clearInterval(timer);
  if (attentionTimer) window.clearTimeout(attentionTimer);
});

function valueOf(key: FieldKey) {
  return { smsPhone: phone.value.trim(), code: code.value.trim(), pwdPhone: pwdPhone.value.trim(), password: password.value }[key];
}
function validateField(key: FieldKey) {
  const value = valueOf(key);
  const valid = key === "code" ? /^\d{6}$/.test(value) : key === "password" ? value.length >= 6 && value.length <= 20 : isCnMobile(value);
  errors[key] = valid ? "" : key === "code" ? "请输入 6 位数字验证码" : key === "password" ? "密码长度需为 6–20 位" : "请输入 11 位有效手机号";
  return valid;
}
function clearFieldError(key: FieldKey) { if (errors[key]) validateField(key); }
function validateOnBlur(key: FieldKey) { if (valueOf(key)) validateField(key); }

async function switchTab(next: LoginTab, focus = false) {
  if (loading.value) return;
  tab.value = next;
  legalError.value = "";
  if (focus) { await nextTick(); document.getElementById(next === "sms" ? "code-phone" : "pwd-phone")?.focus(); }
}
function onTabKeydown(event: KeyboardEvent, current: LoginTab) {
  if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
  event.preventDefault();
  switchTab(event.key === "Home" ? "sms" : event.key === "End" ? "password" : current === "sms" ? "password" : "sms", true);
}
function useSmsInstead() { if (pwdPhone.value) phone.value = pwdPhone.value; switchTab("sms", true); }

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
  if (!validateField("smsPhone")) { document.getElementById("code-phone")?.focus(); return; }
  sending.value = true;
  try {
    const result = await sendSms(phone.value.trim());
    if (disposed) return;
    const seconds = result.cooldownSec ?? result.ttlSec;
    if (Number.isFinite(seconds) && seconds > 0) startCooldown(seconds);
  } catch { /* The API interceptor reports the error; no countdown on failure. */ }
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
    await user.loginBySms(phone.value.trim(), code.value.trim());
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
async function afterLogin() {
  const destination = await user.entryPath();
  if (!disposed) await router.replace(destination);
}
</script>

<style scoped src="./login.css"></style>
