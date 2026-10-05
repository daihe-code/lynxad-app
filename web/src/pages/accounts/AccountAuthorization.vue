<template>
  <section class="acct-card" :aria-busy="loading">
    <div class="acct-card__hd"><div class="authstat"><span class="authstat__i"><i class="authstat__d authstat__d--ok" />已授权 <b>{{ activeCount }}</b> 个账户</span><span class="authstat__i"><i class="authstat__d authstat__d--off" />已失效 <b>{{ expiredCount }}</b> 个</span><span class="authstat__i"><i class="authstat__d authstat__d--off" />已取消 <b>{{ cancelledCount }}</b> 个</span></div><button class="btn-modal-primary account-add-auth" @click="open({ modal: 'add' })"><AccountIcon name="plus" /> 添加新授权</button></div>
    <div v-if="loading" class="nodata" role="status">正在加载授权账户…</div>
    <div v-else-if="loadError" class="nodata"><p>{{ loadError }}</p><button class="obtn mt-[18px]" @click="load">重新加载</button></div>
    <div v-else-if="!accounts.length" class="nodata"><span class="nodata__ic"><AccountIcon name="douyin" /></span><p class="nodata__t">暂无广告账户授权</p><p class="nodata__d">添加授权后即可管理账户与投放计划</p></div>
    <div v-else class="mt-[4px]">
      <div v-for="(account, index) in accounts" :key="account.accountId" class="authrow">
        <span class="authrow__ic" :class="!active(account) ? 'authrow__ic--pink' : index % 2 ? 'authrow__ic--teal' : 'authrow__ic--orange'"><AccountIcon name="douyin" /></span>
        <div class="min-w-0"><div class="authrow__hd"><p class="authrow__n">{{ account.accountName }}</p><span class="authbadge" :class="active(account) ? 'authbadge--valid' : 'authbadge--expired'">{{ status(account) }}</span></div><p class="authrow__m">账号ID：{{ account.advertiserId }} · {{ scope(account) }}</p></div>
        <div class="account-auth-meta"><div class="authrow__d"><p class="authrow__dv">{{ date(account.authorizedAt) }}</p><p class="authrow__dk">授权时间</p></div><div class="authrow__d"><p class="authrow__dv">{{ date(account.refreshExpiresAt) }}</p><p class="authrow__dk">到期时间</p></div><div class="authrow__ops">
          <button class="obtn" @click="open({ detail: account.accountId })">详情</button>
          <button v-if="active(account)" class="obtn" :disabled="!!busyId" @click="refresh(account)">{{ busyId === account.accountId ? '刷新中' : '刷新' }}</button><button v-else class="obtn obtn--primary" @click="open({ modal: 'add' })">重新授权</button>
          <button class="obtn obtn--danger" :disabled="!!busyId" @click="open(active(account) ? { cancel: account.accountId } : { del: account.accountId })">{{ active(account) ? '取消' : '删除' }}</button>
        </div></div>
      </div>
    </div>
  </section>
  <section class="acct-card"><h2 class="acct-card__t">授权权限说明</h2><dl class="acct-card__b scopegrid"><div v-for="row in scopes" :key="row.title" class="scope" :class="{ 'scope--danger': row.danger }"><dt class="scope__t">{{ row.title }}</dt><dd class="scope__d">{{ row.description }}</dd></div></dl></section>
  <AccountModal v-if="route.query.modal === 'add'" title="添加新授权" description="请选择广告平台，前往平台完成授权。账户名称、ID及授权权限以平台返回的数据为准。" :busy="authorizing" @close="close">
    <form :id="formId" class="account-form-grid" @submit.prevent="authorize(false)">
      <label class="mf mt-0 block account-field-wide"><span class="mf__l">账户名称</span><span class="mf__i"><input class="minput" disabled placeholder="授权后由平台自动同步" /></span></label>
      <label class="mf mt-0 block"><span class="mf__l">广告账户 ID</span><span class="mf__i"><input class="minput" disabled placeholder="在千川授权页面选择账户" /></span></label>
      <label class="mf mt-0 block"><span class="mf__l">授权权限</span><span class="mf__i"><select class="minput" aria-label="授权权限"><option>巨量千川 · 全域投放</option><option disabled>本地推 · 即将上线</option></select></span></label>
      <label class="mf mt-0 block account-field-wide"><span class="mf__l">授权有效期</span><span class="mf__i"><input class="minput" disabled placeholder="以平台实际授予的有效期为准" /></span></label>
      <p v-if="error" class="account-error account-field-wide" role="alert">{{ error }}</p>
    </form>
    <template #footer><button class="btn-modal-ghost" :disabled="authorizing" @click="close">取消</button><button v-if="user.me?.mockQianchuan && user.trialActive" class="btn-modal-ghost" :disabled="authorizing" @click="authorize(true)">演示授权</button><button class="btn-modal-primary" :form="formId" type="submit" :disabled="authorizing">{{ authorizing ? '处理中…' : '前往授权' }}</button></template>
  </AccountModal>
  <AccountModal v-else-if="detail" title="授权详情" :busy="!!busyId" @close="close">
    <div class="authdet__hd"><span class="authrow__ic authrow__ic--orange"><AccountIcon name="douyin" /></span><div class="min-w-0"><p class="authrow__n">{{ detail.accountName }}</p><p class="authrow__m">{{ scope(detail) }}</p></div><span class="authbadge ml-auto" :class="active(detail) ? 'authbadge--valid' : 'authbadge--expired'">{{ status(detail) }}</span></div>
    <dl class="authdet__kv"><div v-for="row in detailRows" :key="row[0]"><dt class="acct-kv__k">{{ row[0] }}</dt><dd class="acct-kv__v">{{ row[1] }}</dd></div></dl>
    <p class="modal__desc">权限由平台授权结果决定，刷新不会扩大权限。已取消的账户需要重新完成平台授权。</p>
    <template #footer><button class="btn-modal-ghost" :disabled="!!busyId" @click="close">关闭</button><button v-if="active(detail)" class="btn-modal-primary" :disabled="!!busyId" @click="refresh(detail)">{{ busyId ? '刷新中…' : '刷新授权状态' }}</button><button v-else class="btn-modal-primary" @click="open({ modal: 'add' })">重新授权</button></template>
  </AccountModal>
  <AccountModal v-else-if="target" :title="route.query.cancel ? '取消广告账户授权' : '删除授权记录'" :description="route.query.cancel ? `确定取消「${target.accountName}」的授权？取消后该账户将无法继续创建投放计划。` : `确定删除「${target.accountName}」的失效授权记录？历史投放与日志将保留，重新使用需再次授权。`" small :busy="!!busyId" @close="close">
    <p v-if="error" class="account-error" role="alert">{{ error }}</p>
    <template #footer><button class="btn-modal-ghost" :disabled="!!busyId" @click="close">返回</button><button class="btn-modal-primary" :disabled="!!busyId" @click="confirm">{{ busyId ? '处理中…' : route.query.cancel ? '确认取消' : '确认删除' }}</button></template>
  </AccountModal>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, useId, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { listAccounts, mockBind, refreshAccount, unbindAccount, removeAccount, type AccountVo } from '@/features/identity/oauthApi';
import AccountIcon from './AccountIcon.vue';
import AccountModal from './AccountModal.vue';
import { date } from './accountFormat';
const route = useRoute(), router = useRouter(), user = useUserStore(), formId = useId();
const accounts = ref<AccountVo[]>([]), loading = ref(true), loadError = ref(''), error = ref(''), busyId = ref(''), authorizing = ref(false);
function active(account: AccountVo) { return account.status === 'ACTIVE' && (!account.refreshExpiresAt || new Date(account.refreshExpiresAt).getTime() > Date.now()); }
function status(account: AccountVo) { return account.status === 'UNBOUND' ? '已取消' : active(account) ? '授权有效' : account.status === 'ACTIVE' ? '已过期' : '已失效'; }
function scope(account: AccountVo) { return account.platform === 'LOCAL' ? '本地推' : '全域投放'; }
const activeCount = computed(() => accounts.value.filter(active).length);
const cancelledCount = computed(() => accounts.value.filter(a => a.status === 'UNBOUND').length);
const expiredCount = computed(() => accounts.value.filter(a => a.status !== 'UNBOUND' && !active(a)).length);
const detail = computed(() => accounts.value.find(a => a.accountId === route.query.detail));
const target = computed(() => accounts.value.find(a => a.accountId === (route.query.cancel || route.query.del)));
const detailRows = computed(() => detail.value ? [['广告账户ID', detail.value.advertiserId], ['投放权限', scope(detail.value)], ['授权时间', date(detail.value.authorizedAt)], ['到期时间', date(detail.value.refreshExpiresAt)], ['关联抖音号', detail.value.awemes.map(a => a.name).join('、') || '—'], ['授权状态', status(detail.value)]] : []);
const scopes = [{ title: '数据读取', description: '查看投放计划、素材、账户数据和报表' }, { title: '计划管理', description: '创建、编辑、启停广告投放计划' }, { title: '素材管理', description: '上传、管理广告素材' }, { title: '资金操作', description: '不含：不会进行充值 / 转账操作', danger: true }];
watch(() => route.fullPath, () => { error.value = ''; });
function open(query: Record<string, string>) { void router.push({ path: '/accounts', query: { tab: 'oauth', ...query } }); }
function close() { if (!busyId.value && !authorizing.value)
    void router.push({ path: '/accounts', query: { tab: 'oauth' } }); }
async function load() { loading.value = true; loadError.value = ''; try {
    accounts.value = await listAccounts();
}
catch {
    loadError.value = '授权账户加载失败，请重试';
}
finally {
    loading.value = false;
} }
async function refresh(account: AccountVo) { if (busyId.value || !active(account))
    return; busyId.value = account.accountId; try {
    await refreshAccount(account.accountId);
    await load();
    ElMessage.success('授权状态已刷新');
}
catch {
    error.value = '刷新失败，请检查授权状态或重新授权';
}
finally {
    busyId.value = '';
} }
async function authorize(demo: boolean) { if (authorizing.value)
    return; authorizing.value = true; error.value = ''; try {
    if (demo) {
        await mockBind();
        await load();
        authorizing.value = false;
        close();
        ElMessage.success('演示授权成功');
    }
    else {
        await router.push("/authorization/authorizing?start=1");
    }
}
catch {
    error.value = '授权未完成，请检查配置后重试';
}
finally {
    authorizing.value = false;
} }
async function confirm() { const account = target.value; if (!account || busyId.value)
    return; const cancel = !!route.query.cancel; busyId.value = account.accountId; try {
    if (cancel)
        await unbindAccount(account.accountId);
    else
        await removeAccount(account.accountId);
    await load();
    busyId.value = '';
    close();
    ElMessage.success(cancel ? '已取消授权' : '已删除授权记录');
}
catch {
    error.value = '操作未完成，请重试';
}
finally {
    busyId.value = '';
} }
onMounted(load);
</script>
