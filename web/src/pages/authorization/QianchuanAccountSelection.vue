<template>
  <section class="qc-binding" :aria-busy="busy">
    <div class="qc-binding__head"><span class="qc-binding__badge">巨量千川</span><h1>选择要绑定的广告账户</h1><p>授权凭证已安全保存。请选择本灵投账户需要管理的千川广告账户。</p></div>
    <p class="qc-binding__hint">当前套餐最多绑定 {{ data.accountLimit }} 个广告账户。重新授权会更新原绑定，保留历史投放计划。</p>
    <p v-if="error || data.message" class="qc-binding__error" role="alert">{{ error || data.message }}</p>
    <div v-if="data.status === 'DISCOVERY_FAILED'" class="qc-binding__empty"><p>账户信息暂未同步完成，点击重试即可继续，无需重复授权。</p><button class="qc-primary" :disabled="busy" @click="retry">{{ busy ? '同步中…' : '重新同步账户' }}</button></div>
    <div v-else class="qc-binding__accounts">
      <label v-for="account in data.accounts" :key="account.advertiserId" class="qc-binding__account" :class="{ selected: selected.includes(account.advertiserId) }"><input v-model="selected" type="checkbox" :value="account.advertiserId" :disabled="busy" /><span><strong>{{ account.accountName }}</strong><small>广告账户 ID：{{ account.advertiserId }}</small></span></label>
    </div>
    <div class="qc-binding__footer"><button class="qc-secondary" :disabled="busy" @click="router.push('/accounts?tab=oauth')">返回账户管理</button><button v-if="data.status === 'SELECTING'" class="qc-primary" :disabled="busy || !selected.length" @click="bind">{{ busy ? '正在绑定…' : `确认绑定${selected.length ? ` ${selected.length} 个账户` : ''}` }}</button></div>
  </section>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { bindAuthorizationAccounts, retryAuthorizationSession, type AuthorizationSession } from '@/features/identity/oauthApi';
const props = defineProps<{ session: AuthorizationSession }>();
const emit = defineEmits<{ completed: [] }>();
const router = useRouter(), data = ref(props.session), selected = ref<string[]>([]), busy = ref(false), error = ref('');
async function retry() {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try { data.value = await retryAuthorizationSession(data.value.sessionId); if (data.value.status === 'COMPLETED') emit('completed'); }
  catch (e) { error.value = (e as Error).message || '同步失败，请稍后重试'; }
  finally { busy.value = false; }
}
async function bind() {
  if (busy.value || !selected.value.length) return;
  busy.value = true; error.value = '';
  try { await bindAuthorizationAccounts(data.value.sessionId, selected.value); emit('completed'); }
  catch (e) { error.value = (e as Error).message || '绑定失败，请重试'; }
  finally { busy.value = false; }
}
</script>
<style scoped>
.qc-binding{max-width:900px;margin:48px auto;padding:40px;background:white;border:1px solid #e6e6f3;border-radius:24px;color:#1c1c30;font-family:inherit}.qc-binding__head h1{font-size:28px;margin:18px 0 12px;font-weight:700}.qc-binding__head p,.qc-binding__hint{color:#7b7fa1;font-size:15px;line-height:1.7}.qc-binding__badge{color:#5b54ff;background:#efedff;border-radius:8px;padding:7px 12px}.qc-binding__hint{background:#f7f7fc;padding:16px;border-radius:12px;margin:24px 0}.qc-binding__accounts{display:grid;gap:12px;max-height:420px;overflow:auto}.qc-binding__account{display:flex;align-items:center;gap:16px;padding:20px;border:1px solid #e0e0ef;border-radius:12px;cursor:pointer}.qc-binding__account.selected{border-color:#5b54ff;background:#f8f7ff}.qc-binding__account input{accent-color:#5b54ff;width:18px;height:18px}.qc-binding__account strong{display:block;font-size:17px}.qc-binding__account small{display:block;color:#7b7fa1;margin-top:8px}.qc-binding__footer{display:flex;justify-content:flex-end;gap:12px;margin-top:28px}.qc-primary,.qc-secondary{border:1px solid #e0e0ef;border-radius:10px;padding:12px 22px;font:inherit;cursor:pointer}.qc-primary{background:#5b54ff;color:white;border-color:#5b54ff}.qc-secondary{background:white;color:#555872}button:disabled{opacity:.5;cursor:not-allowed}.qc-binding__error{color:#d94747;background:#fff4f4;padding:14px;border-radius:10px}.qc-binding__empty{padding:20px 0;color:#7b7fa1}@media(max-width:700px){.qc-binding{margin:24px 16px;padding:24px}.qc-binding__head h1{font-size:23px}}
</style>
