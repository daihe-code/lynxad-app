<template>
  <main class="welcome-page">
    <router-link to="/" class="welcome-brand">灵投智营</router-link>
    <section class="welcome-card">
      <span class="welcome-mark" aria-hidden="true">✦</span>
      <p class="welcome-eyebrow">欢迎来到灵投</p>
      <h1>你好，我是小灵，你的智能投放助手</h1>
      <p class="welcome-intro">我会帮你准备素材、管理投放并持续关注异常。商品、预算和关键操作，由你确认。</p>
      <ol class="welcome-steps">
        <li><b>1</b><div><h2>授权广告账户</h2><p>连接千川账户，同步商品和投放数据。</p></div></li>
        <li><b>2</b><div><h2>创建投放计划</h2><p>选择商品，确认目标、预算和素材后再发布。</p></div></li>
        <li><b>3</b><div><h2>查看效果与提醒</h2><p>关注投放表现，按确认的托管范围执行优化。</p></div></li>
      </ol>
      <p class="welcome-note">授权不会立即启动广告投放。广告费用由媒体平台收取，灵投不会进行充值或转账。</p>
      <p v-if="error" class="welcome-error" role="alert">{{ error }}</p>
      <div class="welcome-actions"><button :disabled="busy" @click="continueTo('/accounts?tab=oauth&modal=add')">{{ busy ? '正在保存…' : '先授权广告账户' }}</button><button class="welcome-skip" :disabled="busy" @click="continueTo('/campaigns')">暂时跳过，进入工作台</button></div>
    </section>
  </main>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
const user=useUserStore(), router=useRouter(), busy=ref(false), error=ref('');
async function continueTo(path: string) {
  if(busy.value)return;busy.value=true;error.value='';
  try { await user.finishWelcome(); await router.replace(path); }
  catch { error.value='引导状态暂未保存，请重试。'; }
  finally { busy.value=false; }
}
</script>
<style scoped>
.welcome-page{min-height:100vh;background:linear-gradient(135deg,#f2f0ff,#f8fbff);padding:28px 24px 60px;color:#202238}.welcome-brand{font-size:20px;font-weight:700;color:#575cf6}.welcome-card{max-width:820px;margin:42px auto 0;background:white;padding:44px 52px;border-radius:28px;box-shadow:0 12px 60px #4545a012}.welcome-mark{display:grid;place-items:center;width:52px;height:52px;background:#efedff;color:#575cf6;font-size:30px;border-radius:16px}.welcome-eyebrow{margin-top:20px;color:#575cf6;font-size:14px}.welcome-card h1{font-size:28px;line-height:1.5;margin:8px 0 12px;font-weight:700}.welcome-intro,.welcome-steps p{font-size:14px;line-height:1.8;color:#73778e}.welcome-steps{list-style:none;padding:0;margin:30px 0}.welcome-steps li{display:flex;gap:16px;margin:20px 0}.welcome-steps li>b{flex-shrink:0;display:grid;place-items:center;width:32px;height:32px;border-radius:50%;background:#efedff;color:#575cf6}.welcome-steps h2{font-size:16px;margin:0 0 4px;font-weight:600}.welcome-steps p{margin:0}.welcome-note{padding:16px;background:#f6f5ff;border-radius:12px;font-size:13px;line-height:1.8;color:#62667e}.welcome-actions{display:flex;gap:18px;align-items:center;flex-wrap:wrap;margin-top:28px}.welcome-actions button{border:0;border-radius:10px;background:#575cf6;color:white;padding:13px 24px;font:inherit;font-size:14px;cursor:pointer}.welcome-actions .welcome-skip{background:transparent;color:#6b6f89}.welcome-actions button:disabled{opacity:.5}.welcome-error{font-size:13px;color:#c4424e}@media(max-width:640px){.welcome-page{padding:20px 16px}.welcome-card{margin-top:26px;padding:28px 22px}.welcome-card h1{font-size:23px}}
</style>
