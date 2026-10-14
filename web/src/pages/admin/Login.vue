<template>
  <main class="admin-login">
    <section class="login-card">
      <span class="admin-brand">灵投智营 · 平台管理</span><h1>管理员登录</h1><p>仅限平台管理员访问用户资料与套餐管理。</p>
      <el-alert v-if="route.query.expired" title="管理员会话已失效，请重新登录" type="info" :closable="false" />
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-form label-position="top" @submit.prevent="login">
        <el-form-item label="管理员账号"><el-input v-model="username" autocomplete="username" maxlength="64" placeholder="输入管理员登录名" :disabled="busy" /></el-form-item>
        <el-form-item label="管理员密码"><el-input v-model="password" type="password" autocomplete="current-password" show-password :disabled="busy" placeholder="输入管理员密码" /></el-form-item>
        <el-button class="login-button" type="primary" native-type="submit" :loading="busy" :disabled="!username.trim() || !password">登录管理后台</el-button>
      </el-form>
    </section>
  </main>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { adminLogin } from '@/features/admin/adminApi';
const router = useRouter(), route = useRoute();
const username = ref(''), password = ref(''), busy = ref(false), error = ref('');
async function login() {
  if (busy.value || !username.value.trim() || !password.value) return;
  busy.value = true; error.value = '';
  try { await adminLogin(username.value.trim(), password.value); password.value = ''; await router.replace('/admin'); }
  catch (err) { error.value = err instanceof Error ? err.message : '登录失败'; }
  finally { busy.value = false; }
}
</script>
<style scoped>
.admin-login{min-height:100dvh;display:grid;place-items:center;padding:24px;background:linear-gradient(135deg,#f1f2fb,#fbfcff);box-sizing:border-box}.login-card{width:min(420px,100%);padding:36px;box-sizing:border-box;border:1px solid #e7e8f4;border-radius:18px;background:#fff;box-shadow:0 14px 50px #242d5910}.admin-brand{font-size:14px;color:#5b55ef;font-weight:600}.login-card h1{font-size:25px;color:#20263d;margin:25px 0 10px}.login-card>p{font-size:13px;line-height:1.7;color:#777f94;margin-bottom:25px}.login-card .el-alert{margin-bottom:18px}.login-button{width:100%;margin-top:8px}@media(max-width:480px){.login-card{padding:25px}.admin-login{padding:16px}}
</style>
