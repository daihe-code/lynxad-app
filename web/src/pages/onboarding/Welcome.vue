<template>
  <div class="welcome-page">
    <header class="welcome-header">
      <div class="welcome-header__inner">
        <router-link to="/" class="welcome-brand" aria-label="坤境智营 · 返回首页">
          <img src="/lynxad-favicon.svg" alt="" width="28" height="22">
          <span>坤境智营</span>
        </router-link>
        <UserMenu fallback-name="新用户" />
      </div>
    </header>

    <main class="welcome-content" aria-labelledby="welcome-title">
      <section class="welcome-introduction">
        <p class="welcome-eyebrow">注册成功 · 欢迎加入坤境智营</p>
        <div class="welcome-mark" aria-hidden="true"><img src="/lynxad-favicon.svg" alt="" width="48" height="36"></div>
        <h1 id="welcome-title">你好，我是小灵<span>你的智能广告投放专家</span></h1>
        <p class="welcome-intro">我会帮你推荐投放商品、生成广告素材，持续关注投放表现，发现异常及时提醒，<br class="desktop-break">让日常投放更省心。</p>
        <p class="welcome-assurance">预算和目标由你决定，关键操作先请你确认，处理过程随时可查。</p>
        <p class="welcome-support">当前支持：巨量千川 · 商品全域投放</p>
      </section>

      <section class="welcome-start" aria-labelledby="welcome-steps-title">
        <h2 id="welcome-steps-title">跟着这 3 步，开始第一次投放</h2>
        <ol class="welcome-steps">
          <li v-for="(step, index) in steps" :key="step.title" class="welcome-step" :class="{ 'welcome-step--current': index === 0 }" :aria-current="index === 0 ? 'step' : undefined">
            <span v-if="index === 0" class="welcome-step__badge">当前步骤</span>
            <div class="welcome-step__heading">
              <span class="welcome-step__icon" aria-hidden="true"><component :is="step.icon" /></span>
              <div><span class="welcome-step__number">0{{ index + 1 }}</span><h3>{{ step.title }}</h3></div>
            </div>
            <p>{{ step.description }}</p>
            <ArrowRight v-if="index < steps.length - 1" class="welcome-step__arrow" aria-hidden="true" />
          </li>
        </ol>
      </section>

      <div class="welcome-actions" :aria-busy="busy">
        <p v-if="error" class="welcome-error" role="alert">{{ error }}</p>
        <button class="welcome-primary" type="button" :disabled="busy" @click="continueTo('/authorization')">
          {{ busy ? '正在保存…' : '先授权广告账户' }}<ArrowRight aria-hidden="true" />
        </button>
        <button class="welcome-skip" type="button" :disabled="busy" @click="continueTo('/campaigns')">暂时跳过，进入工作台</button>
      </div>

      <aside class="welcome-note" aria-label="授权说明">
        <span class="welcome-note__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 4.5 6v6c0 4.2 3.1 7.4 7.5 9 4.4-1.6 7.5-4.8 7.5-9V6L12 3Z"/><path d="m8.5 12 2.3 2.3 4.7-4.6"/></svg></span>
        <p><strong>授权不会立即启动广告投放。</strong><span>你将在千川官方页面完成授权，无需向我提供账号密码。首次发布投放前，我会请你确认。</span></p>
      </aside>

      <details class="welcome-guide">
        <summary>还没有千川广告账户？<span>查看开通指引</span></summary>
        <div class="welcome-guide__content">
          <h2>开通与授权步骤</h2>
          <ol>
            <li>先在巨量千川开通广告账户，按平台要求完善主体资料。</li>
            <li>确认你使用的账号拥有相应广告账户的管理权限。</li>
            <li>返回本页点击“先授权广告账户”，按千川官方页面提示完成授权。</li>
          </ol>
        </div>
      </details>
    </main>
    <footer class="welcome-footer">© 2026 坤境智营</footer>
  </div>
</template>
<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ArrowRight, Document, Histogram, Link } from '@element-plus/icons-vue';
import UserMenu from '@/layouts/UserMenu.vue';
import { useUserStore } from '@/stores/user';
const user = useUserStore(), router = useRouter();
const busy = ref(false), error = ref('');
const steps = [
  { title: '授权广告账户', icon: Link, description: '先接入你的巨量千川账户，我才能同步商品和投放数据，为后续分析与管理做准备。' },
  { title: '创建投放计划', icon: Document, description: '选定商品，确认预算和目标，再由我准备投放素材与计划，交给你确认。' },
  { title: '查看投放与提醒', icon: Histogram, description: '计划上线后，我会持续盯盘、提供优化建议，需要你决定时及时提醒。' },
];
let previousTitle = '';
onMounted(() => { previousTitle = document.title; document.title = '欢迎加入坤境智营'; });
onUnmounted(() => { document.title = previousTitle; });

async function continueTo(path: '/authorization' | '/campaigns') {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await user.finishWelcome();
    await router.replace(path);
  } catch {
    error.value = '引导状态暂未保存，请重试。';
  } finally {
    busy.value = false;
  }
}
</script>
<style scoped src="./welcome.css"></style>
