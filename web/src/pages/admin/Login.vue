<template>
  <main class="admin-login">
    <section class="login-card">
      <span class="admin-brand">坤境智营 · 平台管理</span><h1>管理员登录</h1><p>仅限平台管理员访问用户资料与套餐管理。</p>
      <el-alert v-if="route.query.expired" title="管理员会话已失效，请重新登录" type="info" :closable="false" />
      <el-form label-position="top" @submit.prevent="login">
        <el-form-item label="管理员手机号" :error="phoneError">
          <el-input v-model="phone" type="tel" autocomplete="tel-national" inputmode="numeric" maxlength="11" placeholder="输入管理员手机号" :disabled="busy || sending" />
        </el-form-item>
        <el-form-item label="短信验证码" :error="codeError">
          <div class="code-row">
            <el-input v-model="code" autocomplete="one-time-code" inputmode="numeric" maxlength="6" placeholder="输入六位验证码" :disabled="busy" />
            <el-button :loading="sending" :disabled="!validPhone || busy || cooldown > 0" @click="sendCode">
              {{ cooldown > 0 ? `${cooldown}s 后重新获取` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-button class="login-button" type="primary" native-type="submit" :loading="busy" :disabled="sending || !validPhone || !/^\d{6}$/.test(code)">登录管理后台</el-button>
      </el-form>
    </section>
  </main>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { adminLogin, sendAdminSms } from '@/features/admin/adminApi';
const router = useRouter(), route = useRoute();
const phone = ref(''), code = ref(''), phoneError = ref(''), codeError = ref('');
const busy = ref(false), sending = ref(false), cooldown = ref(0);
const validPhone = computed(() => /^1[3-9]\d{9}$/.test(phone.value.trim()));
let timer: number | undefined;
watch(phone, () => { phoneError.value = ''; codeError.value = ''; code.value = ''; });
watch(code, () => { codeError.value = ''; });
onUnmounted(() => window.clearInterval(timer));

async function sendCode() {
  if (!validPhone.value || sending.value || busy.value || cooldown.value > 0) return;
  phoneError.value = '';
  sending.value = true;
  try {
    const { cooldownSec } = await sendAdminSms(phone.value.trim());
    code.value = '';
    codeError.value = '';
    const deadline = Date.now() + cooldownSec * 1000;
    cooldown.value = cooldownSec;
    window.clearInterval(timer);
    timer = window.setInterval(() => {
      cooldown.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      if (!cooldown.value) window.clearInterval(timer);
    }, 1000);
  } catch (err) { phoneError.value = err instanceof Error ? err.message : '验证码发送失败'; }
  finally { sending.value = false; }
}

async function login() {
  if (busy.value || sending.value || !validPhone.value || !/^\d{6}$/.test(code.value)) return;
  busy.value = true;
  codeError.value = '';
  phoneError.value = '';
  try { await adminLogin(phone.value.trim(), code.value); await router.replace('/admin'); }
  catch (err) { codeError.value = err instanceof Error ? err.message : '登录失败'; }
  finally { busy.value = false; }
}
</script>
<style scoped>
.code-row{display:flex;gap:10px;width:100%}.code-row .el-input{min-width:0}
.admin-login{min-height:100dvh;display:grid;place-items:center;padding:24px;background:linear-gradient(135deg,#f1f2fb,#fbfcff);box-sizing:border-box}.login-card{width:min(420px,100%);padding:36px;box-sizing:border-box;border:1px solid #e7e8f4;border-radius:18px;background:#fff;box-shadow:0 14px 50px #242d5910}.admin-brand{font-size:14px;color:#5b55ef;font-weight:600}.login-card h1{font-size:25px;color:#20263d;margin:25px 0 10px}.login-card>p{font-size:13px;line-height:1.7;color:#777f94;margin-bottom:25px}.login-card .el-alert{margin-bottom:18px}.login-button{width:100%;margin-top:8px}@media(max-width:480px){.login-card{padding:25px}.admin-login{padding:16px}}
</style>
