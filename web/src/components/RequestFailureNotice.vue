<template>
  <aside v-if="failure && failure.kind !== 'CANCELLED'" class="request-failure" role="alert">
    <p v-if="context">{{ context }}</p>
    <p>{{ failure.message }}</p>
    <p v-if="failure.attempts > 1">查询已自动重试 {{ failure.attempts - 1 }} 次，仍未成功。</p>
    <router-link v-if="failure.action === 'LOGIN'" to="/login">重新登录</router-link>
    <router-link v-else-if="failure.action === 'ACCOUNTS'" to="/accounts">前往账户管理</router-link>
    <button v-else-if="['RETRY_READ','REFRESH','VERIFY'].includes(failure.action)" type="button" :disabled="busy" @click="$emit('retry')">{{ busy ? '正在查询…' : recoveryLabel(failure) }}</button>
    <p v-else-if="failure.action === 'EDIT'">请修改对应填写项后再次提交。</p>
  </aside>
</template>
<script setup lang="ts">
import { recoveryLabel, type ApiRequestError } from '@/api/apiErrors';
defineProps<{ failure?: ApiRequestError; context?: string; busy?: boolean }>();
defineEmits<{ retry: [] }>();
</script>
<style scoped>
.request-failure{margin:12px 0;padding:12px 16px;border:1px solid #f1d5aa;border-radius:10px;background:#fff9ef;color:#8d5c23;font-size:13px;line-height:1.6;overflow-wrap:anywhere;text-align:left}.request-failure p{margin:0 0 4px}.request-failure button,.request-failure a{display:inline-block;margin-top:6px;border:0;background:transparent;color:var(--color-primary,#575cf6);font:inherit;text-decoration:underline;cursor:pointer}.request-failure button:disabled{opacity:.5;cursor:wait}
</style>
