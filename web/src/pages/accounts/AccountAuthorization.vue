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
      <p class="modal__desc account-field-wide">当前授权平台为巨量千川。巨量营销将使用独立应用与授权凭证，暂未开放。</p>
      <p v-if="error" class="account-error account-field-wide" role="alert">{{ error }}</p>
    </form>
    <template #footer><button class="btn-modal-ghost" :disabled="authorizing" @click="close">取消</button><button v-if="user.me?.mockQianchuan && user.trialActive" class="btn-modal-ghost" :disabled="authorizing" @click="authorize(true)">演示授权</button><button class="btn-modal-primary" :form="formId" type="submit" :disabled="authorizing">{{ authorizing ? '处理中…' : '前往授权' }}</button></template>
  </AccountModal>
  <AccountModal v-else-if="detail" title="授权详情" expanded :busy="!!busyId" @close="close">
    <div class="authdet__hd"><span class="authrow__ic authrow__ic--orange"><AccountIcon name="douyin" /></span><div class="min-w-0"><p class="authrow__n">{{ detail.accountName }}</p><p class="authrow__m">{{ scope(detail) }}</p></div><span class="authbadge ml-auto" :class="active(detail) ? 'authbadge--valid' : 'authbadge--expired'">{{ status(detail) }}</span></div>
    <p v-if="detailLoading" class="authorization-detail-loading" role="status">正在读取授权身份信息…</p>
    <div v-if="detailError" class="account-error" role="alert">{{ detailError }} <button class="obtn" @click="loadDetail(String(route.query.detail))">重试</button></div>
    <dl class="authdet__kv authorization-detail-grid"><div v-for="row in detailRows" :key="row.label"><dt class="acct-kv__k">{{ row.label }}</dt><dd class="acct-kv__v" :class="{ 'authorization-id': row.copy }"><span>{{ row.value }}</span><button v-if="row.copy && row.value" type="button" class="authorization-copy" :aria-label="`复制${row.label}`" @click="copy(row.label, row.value)">复制</button></dd></div></dl>
    <section class="authorization-awemes" aria-label="已授权抖音号">
      <h3>已授权抖音号 <span>{{ detail.awemes.length }} 个</span></h3>
      <div v-for="aweme in detail.awemes" :key="aweme.awemeId" class="authorization-aweme">
        <AuthorizationAvatar :src="aweme.avatar" :name="aweme.name" />
        <div class="authorization-aweme__info"><div class="authorization-aweme__head"><strong>{{ aweme.name || '平台未返回名称' }}</strong><span class="authorization-aweme__tag">{{ aweme.sceneHint || '已授权抖音号' }}</span><span v-if="aweme.isPrimary" class="authorization-aweme__tag">主号</span></div><div class="authorization-aweme__id"><span>抖音号 ID（aweme_id）：{{ aweme.awemeId }}</span><button class="authorization-copy" :aria-label="`复制${aweme.name}的抖音号ID`" @click="copy('抖音号 ID', aweme.awemeId)">复制</button></div><small v-if="!aweme.avatar">平台未返回头像</small></div>
      </div>
      <p v-if="!detail.awemes.length" class="authorization-awemes__empty">{{ detail.catalogError || '平台暂未返回关联抖音号，可点击刷新授权状态重新同步。' }}</p>
    </section>
    <section v-if="detailInfo" class="authorization-credentials" aria-label="授权凭证获取情况"><h3>授权凭证获取情况</h3><p><i :class="{ received: detailInfo.accessTokenStored }" />访问凭证：{{ detailInfo.accessTokenStored ? '已获取并加密保存' : '未获取真实凭证' }}</p><p><i :class="{ received: detailInfo.refreshTokenStored }" />刷新凭证：{{ detailInfo.refreshTokenStored ? '已获取并加密保存' : '未获取真实凭证' }}</p></section>
    <p v-if="error" class="account-error" role="alert">{{ error }}</p>
    <p class="modal__desc">权限由平台授权结果决定，刷新不会扩大权限。已取消的账户需要重新完成平台授权。</p>
    <template #footer><button class="btn-modal-ghost" :disabled="!!busyId" @click="close">关闭</button><button v-if="active(detail)" class="btn-modal-primary" :disabled="!!busyId" @click="refresh(detail)">{{ busyId ? '刷新中…' : '刷新授权状态' }}</button><button v-else class="btn-modal-primary" @click="open({ modal: 'add' })">重新授权</button></template>
  </AccountModal>
  <AccountModal v-else-if="target" :title="route.query.cancel ? '取消广告账户授权' : '删除授权记录'" :description="route.query.cancel ? `确定解除「${target.accountName}」在灵投平台的绑定？解除后将停止使用该账户的授权凭证。千川平台的授权记录需在官方页面管理。` : `确定删除「${target.accountName}」的失效授权记录？历史投放与日志将保留，重新使用需再次授权。`" small :busy="!!busyId" @close="close">
    <p v-if="error" class="account-error" role="alert">{{ error }}</p>
    <template #footer><button class="btn-modal-ghost" :disabled="!!busyId" @click="close">返回</button><button class="btn-modal-primary" :disabled="!!busyId" @click="confirm">{{ busyId ? '处理中…' : route.query.cancel ? '确认取消' : '确认删除' }}</button></template>
  </AccountModal>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, useId, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { listAccounts, mockBind, oauthUrl, refreshAccount, unbindAccount, removeAccount, accountAuthorizationDetail, type AccountVo, type AccountAuthorizationDetail } from '@/features/identity/oauthApi';
import AccountIcon from './AccountIcon.vue';
import AccountModal from './AccountModal.vue';
import AuthorizationAvatar from './AuthorizationAvatar.vue';
import { date, time } from './accountFormat';
const route = useRoute(), router = useRouter(), user = useUserStore(), formId = useId();
const accounts = ref<AccountVo[]>([]), loading = ref(true), loadError = ref(''), error = ref(''), busyId = ref(''), authorizing = ref(false);
const detailInfo = ref<AccountAuthorizationDetail | null>(null), detailLoading = ref(false), detailError = ref('');
let detailRequest = 0;
function active(account: AccountVo) { return account.credentialSource !== 'MOCK' && account.status === 'ACTIVE' && (!account.refreshExpiresAt || new Date(account.refreshExpiresAt).getTime() > Date.now()); }
function status(account: AccountVo) { return account.credentialSource === 'MOCK' ? '演示账户' : account.status === 'UNBOUND' ? '已取消' : active(account) ? '授权有效' : account.status === 'ACTIVE' ? '已过期' : '已失效'; }
function scope(account: AccountVo) { return account.platform === 'LOCAL' ? '本地推' : '全域投放'; }
const activeCount = computed(() => accounts.value.filter(active).length);
const cancelledCount = computed(() => accounts.value.filter(a => a.credentialSource !== 'MOCK' && a.status === 'UNBOUND').length);
const expiredCount = computed(() => accounts.value.filter(a => a.credentialSource !== 'MOCK' && a.status !== 'UNBOUND' && !active(a)).length);
const detail = computed(() => detailInfo.value && detailInfo.value.account.accountId === route.query.detail ? detailInfo.value.account : accounts.value.find(a => a.accountId === route.query.detail));
const target = computed(() => accounts.value.find(a => a.accountId === (route.query.cancel || route.query.del)));
const roleNames: Record<string, string> = { ADVERTISER: '广告主直接授权', CUSTOMER_ADMIN: '工作台账户授权', PLATFORM_ROLE_CUSTOMER_ADMIN: '工作台账户授权', PLATFORM_ROLE_ENTERPRISE_BP_ADMIN: 'EBP 组织授权', PLATFORM_ROLE_SHOP_ACCOUNT: '店铺账户授权' };
const detailRows = computed(() => {
  const account = detail.value, info = detailInfo.value;
  if (!account) return [];
  const row = (label: string, value: string | null | undefined, copy = false) => ({ label, value: value || '平台未返回', copy: copy && !!value });
  return [row('广告平台', account.platform === 'QIANCHUAN' ? '巨量千川' : scope(account)), row('账户名称', account.accountName),
    row('广告账户 ID（advertiser_id）', account.advertiserId, true), row('授权应用 AppID', info?.appId, true),
    row('授权来源', info?.accountRole ? roleNames[info.accountRole] || info.accountRole : null),
    row(info?.accountRole === 'PLATFORM_ROLE_SHOP_ACCOUNT' ? '来源店铺 ID' : info?.accountRole === 'PLATFORM_ROLE_ENTERPRISE_BP_ADMIN' ? '来源 EBP 组织 ID' : '来源工作台账户 ID', info?.parentAccountId, true),
    row('灵投账户 ID', info?.tenantId, true), row('本平台绑定记录 ID', account.accountId, true),
    row('授权操作人', info?.authorizedByName), row('授权操作人 ID', account.authorizedByUserId, true),
    row('授权状态', status(account)), row('授权时间', time(account.authorizedAt)),
    row('访问凭证到期时间', account.tokenExpiresAt ? time(account.tokenExpiresAt) : '平台未返回'), row('刷新凭证到期时间', account.refreshExpiresAt ? time(account.refreshExpiresAt) : '平台未返回')];
});
async function loadDetail(id: string) {
  const request = ++detailRequest;
  detailInfo.value = null; detailError.value = ''; detailLoading.value = !!id;
  if (!id) return;
  try { const result = await accountAuthorizationDetail(id); if (request === detailRequest) detailInfo.value = result; }
  catch (e) { if (request === detailRequest) detailError.value = (e as Error).message || '授权详情读取失败'; }
  finally { if (request === detailRequest) detailLoading.value = false; }
}
watch(() => String(route.query.detail || ''), id => { void loadDetail(id); }, { immediate: true });
async function copy(label: string, value: string) {
  try { await navigator.clipboard.writeText(value); ElMessage.success(`${label}已复制`); }
  catch { ElMessage.warning('复制失败，请手动选中内容复制'); }
}
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
    if (route.query.detail === account.accountId) await loadDetail(account.accountId);
    ElMessage.success('授权状态已刷新');
}
catch (e) {
    error.value = (e as Error).message || '刷新暂时失败，请稍后重试';
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
        const result = await oauthUrl('QIANCHUAN', '/accounts?tab=oauth');
        const url = new URL(result.url);
        if (url.protocol !== 'https:' || url.hostname !== 'qianchuan.jinritemai.com') throw new Error('千川授权链接无效');
        window.location.assign(url.href);
    }
}
catch (e) {
    error.value = (e as Error).message || '授权未完成，请检查配置后重试';
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
    ElMessage.success(cancel ? '已解除灵投平台绑定' : '已删除授权记录');
}
catch {
    error.value = '操作未完成，请重试';
}
finally {
    busyId.value = '';
} }
onMounted(load);
</script>
<style scoped>
.authorization-detail-loading{margin-top:18px;color:#7e82a2;font-size:14px}.authorization-detail-grid .acct-kv__v{display:flex;align-items:flex-start;gap:8px;overflow-wrap:anywhere;line-height:1.6}.authorization-detail-grid .acct-kv__v span{min-width:0}.authorization-id{font-variant-numeric:tabular-nums;font-size:13px}.authorization-copy{flex-shrink:0;border:0;padding:2px 0;background:none;color:#5d54ff;font-size:12px;cursor:pointer}.authorization-awemes{border-top:1px solid #e8e8f4;margin-top:24px;padding-top:20px}.authorization-awemes h3,.authorization-credentials h3{margin:0 0 14px;font-size:16px;font-weight:600;color:#1c1c30}.authorization-awemes h3 span{font-size:13px;font-weight:400;color:#8589a6;margin-left:8px}.authorization-aweme{display:flex;align-items:center;gap:14px;padding:16px;border:1px solid #e7e7f3;border-radius:12px;margin-top:10px;background:#fafaff}.authorization-aweme__info{min-width:0;flex:1}.authorization-aweme__head{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.authorization-aweme__head strong{font-size:15px;overflow-wrap:anywhere}.authorization-aweme__tag{font-size:11px;padding:3px 7px;border-radius:5px;color:#6158ff;background:#eeecff}.authorization-aweme__id{display:flex;gap:8px;align-items:flex-start;margin-top:8px;font-size:12px;color:#737797;overflow-wrap:anywhere}.authorization-aweme small,.authorization-awemes__empty{color:#8589a6;font-size:13px;line-height:1.6}.authorization-credentials{background:#f4fbf7;border:1px solid #ddf0e6;padding:16px;border-radius:12px;margin:20px 0}.authorization-credentials p{margin:8px 0 0;font-size:13px;color:#5c6470;display:flex;align-items:center;gap:8px}.authorization-credentials i{width:7px;height:7px;border-radius:50%;background:#bfc3d1}.authorization-credentials i.received{background:#0bb77a}@media(max-width:639px){.authorization-aweme{padding:12px;gap:10px}.authorization-aweme__id{flex-wrap:wrap}}
</style>
