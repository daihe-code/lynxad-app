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
        <div class="account-auth-meta"><div class="authrow__d"><p class="authrow__dv">{{ time(account.authorizedAt) }}</p><p class="authrow__dk">授权时间</p></div><div class="authrow__ops">
          <button class="obtn" @click="open({ detail: account.accountId })">详情</button>
          <button v-if="active(account)" class="obtn" :disabled="!!busyId" @click="refresh(account)">{{ busyId === account.accountId ? '刷新中' : '刷新' }}</button><button v-else class="obtn obtn--primary" @click="open({ modal: 'add' })">重新授权</button>
          <button class="obtn obtn--danger" :disabled="!!busyId" @click="open(active(account) ? { cancel: account.accountId } : { del: account.accountId })">{{ active(account) ? '取消' : '删除' }}</button>
        </div></div>
      </div>
    </div>
  </section>
  <section class="acct-card authorization-boundaries" aria-labelledby="authorization-boundaries-title">
    <h2 class="acct-card__t" id="authorization-boundaries-title">授权范围与操作边界</h2>
    <p class="authorization-boundaries__intro">授权后，我会在平台授予的权限和你确认的托管范围内，为你管理广告投放。</p>
    <dl class="authorization-scopes">
      <div v-for="row in scopes" :key="row.title" class="authorization-scope">
        <dt><span class="authorization-scope__icon"><AccountIcon :name="row.icon" /></span>{{ row.title }}</dt>
        <dd><strong v-if="row.emphasis">{{ row.emphasis }}</strong>{{ row.description }}</dd>
      </div>
    </dl>
    <p class="authorization-boundaries__note"><AccountIcon name="info-line" /><span>你可以随时取消授权。取消后，灵投将停止该账户的投放管理；平台上的广告仍可能继续运行并产生费用，需要你自行查看和管理。</span></p>
  </section>
  <AuthorizationAddDialog v-if="route.query.modal === 'add'" :busy="authorizing" :error="error" :can-demo="user.me?.mockQianchuan && user.trialActive" @close="close" @authorize="authorize" />
  <AccountModal v-else-if="detail" title="授权详情" expanded :busy="!!busyId" @close="close">
    <div class="authdet__hd"><span class="authrow__ic authrow__ic--orange"><AccountIcon name="douyin" /></span><div class="min-w-0"><p class="authrow__n">{{ detail.accountName }}</p><p class="authrow__m">{{ scope(detail) }}</p></div><span class="authbadge ml-auto" :class="active(detail) ? 'authbadge--valid' : 'authbadge--expired'">{{ status(detail) }}</span></div>
    <p v-if="detailLoading" class="authorization-detail-loading" role="status">正在读取授权身份信息…</p>
    <div v-if="detailError" class="account-error" role="alert">{{ detailError }} <button class="obtn" @click="loadDetail(String(route.query.detail))">重试</button></div>
    <dl class="authdet__kv authorization-detail-grid"><div v-for="row in detailRows" :key="row.label"><dt class="acct-kv__k">{{ row.label }}</dt><dd class="acct-kv__v" :class="{ 'authorization-id': row.copy }"><span>{{ row.value }}</span><button v-if="row.copy && row.value" type="button" class="authorization-copy" :aria-label="`复制${row.label}`" @click="copy(row.label, row.value)">复制</button></dd></div></dl>
    <section class="authorization-awemes" aria-label="已授权抖音号">
      <h3>已授权抖音号 <span>{{ detail.awemes.length }} 个</span></h3>
      <div v-for="aweme in detail.awemes" :key="aweme.awemeId" class="authorization-aweme">
        <AuthorizationAvatar :src="aweme.avatar" :name="aweme.name" />
        <div class="authorization-aweme__info"><div class="authorization-aweme__head"><strong>{{ aweme.name || '平台未返回名称' }}</strong><span class="authorization-aweme__tag">{{ aweme.sceneHint || '已授权抖音号' }}</span><span v-if="aweme.isPrimary" class="authorization-aweme__tag">主号</span></div><div class="authorization-aweme__id"><span>抖音号 ID：{{ aweme.awemeId }}</span><button class="authorization-copy" :aria-label="`复制${aweme.name}的抖音号ID`" @click="copy('抖音号 ID', aweme.awemeId)">复制</button></div><small v-if="!aweme.avatar">平台未返回头像</small></div>
      </div>
      <p v-if="!detail.awemes.length" class="authorization-awemes__empty">{{ detail.catalogError || '平台暂未返回关联抖音号，可点击刷新授权状态重新同步。' }}</p>
    </section>
    <section v-if="detailInfo" class="authorization-credentials" aria-label="授权凭证获取情况"><h3>授权凭证获取情况</h3><p><i :class="{ received: detailInfo.accessTokenStored }" />访问凭证：{{ detailInfo.accessTokenStored ? '已获取并加密保存' : '未获取真实凭证' }}</p><p><i :class="{ received: detailInfo.refreshTokenStored }" />刷新凭证：{{ detailInfo.refreshTokenStored ? '已获取并加密保存' : '未获取真实凭证' }}</p></section>
    <p v-if="error" class="account-error" role="alert">{{ error }}</p>
    <p class="modal__desc">权限由平台授权结果决定，刷新不会扩大权限。已取消的账户需要重新完成平台授权。</p>
    <template #footer><button class="btn-modal-ghost" :disabled="!!busyId" @click="close">关闭</button><button v-if="active(detail)" class="btn-modal-primary" :disabled="!!busyId" @click="refresh(detail)">{{ busyId ? '刷新中…' : '刷新授权状态' }}</button><button v-else class="btn-modal-primary" @click="open({ modal: 'add' })">重新授权</button></template>
  </AccountModal>
  <AuthorizationCancelDialog v-else-if="target && route.query.cancel" :account-name="target.accountName" :busy="!!busyId" :error="error" @close="close" @confirm="confirm" />
  <AccountModal v-else-if="target" title="删除授权记录" :description="`确定删除「${target.accountName}」的失效授权记录？该记录将从列表移除，历史投放与日志仍保留。此操作不会删除千川账户或广告计划，重新使用需再次授权。`" small :busy="!!busyId" @close="close">
    <p v-if="error" class="account-error" role="alert">{{ error }}</p>
    <template #footer><button class="btn-modal-ghost" :disabled="!!busyId" @click="close">返回</button><button class="btn-modal-primary" :disabled="!!busyId" @click="confirm">{{ busyId ? '处理中…' : '确认删除' }}</button></template>
  </AccountModal>

</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { listAccounts, mockBind, oauthUrl, refreshAccount, unbindAccount, removeAccount, accountAuthorizationDetail, type AccountVo, type AccountAuthorizationDetail } from '@/features/identity/oauthApi';
import AccountIcon from './AccountIcon.vue';
import AccountModal from './AccountModal.vue';
import AuthorizationAddDialog from './AuthorizationAddDialog.vue';
import AuthorizationCancelDialog from './AuthorizationCancelDialog.vue';
import AuthorizationAvatar from './AuthorizationAvatar.vue';
import { time } from './accountFormat';
const route = useRoute(), router = useRouter(), user = useUserStore();
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
const roleNames: Record<string, string> = { ADVERTISER: '广告主直接授权', CUSTOMER_ADMIN: '工作台账户授权', PLATFORM_ROLE_CUSTOMER_ADMIN: '工作台账户授权', PLATFORM_ROLE_ENTERPRISE_BP_ADMIN: '工作台账户授权', PLATFORM_ROLE_SHOP_ACCOUNT: '店铺账户授权' };
const detailRows = computed(() => {
  const account = detail.value, info = detailInfo.value;
  if (!account) return [];
  const row = (label: string, value: string | null | undefined, copy = false) => ({ label, value: value || '平台未返回', copy: copy && !!value });
  return [row('广告平台', account.platform === 'QIANCHUAN' ? '巨量千川' : scope(account)), row('账户名称', account.accountName),
    row('广告账户 ID', account.advertiserId, true),
    row('授权来源', info?.accountRole ? `巨量引擎${roleNames[info.accountRole] || '账户授权'}` : '巨量引擎账户授权'),
    row('授权操作人', info?.authorizedByName),row('授权状态', status(account)), row('授权时间', time(account.authorizedAt))];
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
const scopes = [
  { icon: 'chart', title: '投放数据分析', description: '查看已授权账户的投放、商品和素材数据，用于分析表现、发现异常并提供优化建议。' },
  { icon: 'tune', title: '投放计划管理', description: '按你确认的方案或已授予的托管权限，创建、调整和启停计划；需要你确认的操作，会先征求你的同意。' },
  { icon: 'video', title: '素材生成与管理', description: '为已授权账户智能生成、上传和管理投放素材。' },
  { icon: 'wallet', title: '资金与预算边界', emphasis: '不进行充值或转账。', description: '广告投放仍会产生费用，由媒体平台收取；我会按你确认的预算和托管规则执行。' },
];
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

<style scoped>
.authorization-boundaries .acct-card__t { font-size: 18px; line-height: 1.4; }
.authorization-boundaries__intro { margin: 6px 0 0; font-size: 13px; line-height: 1.8; color: #83889e; }
.authorization-scopes { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 32px; row-gap: 26px; margin: 26px 0 24px; }
.authorization-scope { position: relative; min-width: 0; min-height: 48px; padding-left: 64px; }
.authorization-scope dt { font-size: 14px; font-weight: 600; line-height: 1.6; color: #25283f; }
.authorization-scope__icon { position: absolute; left: 0; top: 0; display: grid; place-items: center; width: 48px; height: 48px; border-radius: 12px; color: #6258ff; background: #f4f2ff; }
.authorization-scope__icon svg { width: 28px; height: 28px; }
.authorization-scope dd { margin: 4px 0 0; font-size: 13px; line-height: 1.8; color: #83889e; }
.authorization-scope dd strong { font-weight: 600; color: #25283f; }
.authorization-boundaries__note { display: flex; align-items: flex-start; gap: 8px; margin: 0; padding-top: 16px; border-top: 1px solid #e6e8f2; font-size: 12px; line-height: 1.8; color: #8c91a8; }
.authorization-boundaries__note svg { flex: none; width: 16px; height: 16px; margin-top: 3px; }
.account-auth-meta .authrow__dv{white-space:nowrap;font-size:13px}.authorization-credentials{display:none}
@media (max-width: 767px) {
  .authorization-scopes { grid-template-columns: 1fr; gap: 22px; margin-top: 22px; }
}
@media (max-width: 639px) {
  .authorization-scope { padding-left: 56px; }
  .authorization-scope__icon { width: 42px; height: 42px; border-radius: 11px; }
  .authorization-scope__icon svg { width: 25px; height: 25px; }
}
</style>
