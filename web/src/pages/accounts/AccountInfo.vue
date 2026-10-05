<template>
  <section class="acct-card">
    <div class="acct-card__hd"><h2 class="acct-card__t">订阅信息</h2><router-link class="acct-card__a" to="/subscription">变更套餐 <AccountIcon name="arrow-right" /></router-link></div>
    <div class="acct-card__b subbar">
      <span class="subbar__badge">{{ user.me?.displayPlanName || '—' }}</span>
      <div class="min-w-0"><p class="subbar__n">灵投 LynxAd {{ user.me?.displayPlanName || '—' }}</p><p class="subbar__m">{{ user.me?.subscriptionPlanCode ? `计费周期：${date(user.me.periodStart)} ~ ${date(user.me.periodEnd)}` : `${user.me?.trialStatusLabel || '试用'} · 到期时间：${date(user.me?.trialExpiresAt)}` }}</p></div>
      <div class="subbar__r"><div><p class="subbar__v">{{ user.me?.subscriptionPlanCode ? remainingDays : user.me?.remainingTrialDays ?? 0 }}</p><p class="subbar__k">剩余天数</p></div><div><p class="subbar__v subbar__v--ok">{{ user.me?.subscriptionPlanCode ? user.me.autoRenew ? '自动续费' : '已关闭' : user.me?.trialStatusLabel || '—' }}</p><p class="subbar__k">{{ user.me?.subscriptionPlanCode ? '续费状态' : '试用状态' }}</p></div></div><router-link class="btn-modal-primary account-upgrade" :to="{ path: '/subscription', query: user.me?.displayPlanCode === 'FLAGSHIP' ? {} : { plan: 'flagship' } }">{{ user.me?.displayPlanCode === 'FLAGSHIP' ? '查看套餐' : '升级到旗舰版' }}</router-link>
    </div>
  </section>
  <section class="acct-card">
    <div class="acct-card__hd"><h2 class="acct-card__t">基本信息</h2><button class="acct-card__a" @click="open('org')">编辑 <AccountIcon name="arrow-right" /></button></div>
    <dl class="acct-card__b acct-kv"><div v-for="row in orgRows" :key="row[0]" class="acct-kv__i"><dt class="acct-kv__k">{{ row[0] }}</dt><dd class="acct-kv__v">{{ row[1] || '—' }}</dd></div></dl>
  </section>
  <section class="acct-card">
    <div class="acct-card__hd"><h2 class="acct-card__t">发票信息</h2><button class="acct-card__a" @click="open('invoice')">编辑 <AccountIcon name="arrow-right" /></button></div>
    <dl class="acct-card__b acct-kv"><div v-for="row in invoiceRows" :key="row[0]" class="acct-kv__i"><dt class="acct-kv__k">{{ row[0] }}</dt><dd class="acct-kv__v">{{ row[1] || '—' }}</dd></div></dl>
    <p class="acct-hint">发票信息暂存为本浏览器会话草稿，正式开票与同步保存待接入。</p>
  </section>
  <section class="acct-card">
    <h2 class="acct-card__t">安全设置</h2>
    <div class="mt-[6px]"><div v-for="item in security" :key="item.key" class="secrow"><span class="secrow__ic"><AccountIcon :name="item.key === 'pwd' ? 'lock' : 'phone'" /></span><div class="min-w-0"><p class="secrow__t">{{ item.title }}</p><p class="secrow__d">{{ item.description }}</p></div><div class="secrow__r"><span class="badge-ok">{{ item.status }}</span><button class="link-act" @click="open(item.key)">{{ item.action }}</button></div></div></div>
  </section>
  <AccountModal v-if="modal" :key="modal" :title="titles[modal] || ''" :description="descriptions[modal]" :busy="saving" @close="close">
    <form :id="formId" class="account-form-grid" @submit.prevent="save">
      <template v-if="modal === 'org'">
        <label class="mf mt-0 block account-field-wide"><span class="mf__l">企业名称 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="profile.companyName" class="minput" maxlength="200" required autocomplete="organization" /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">统一社会信用代码</span><span class="mf__i"><input class="minput" disabled placeholder="待接入企业认证" /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">所属行业 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><select v-model="profile.industry" class="minput" required><option value="" disabled>请选择</option><option v-if="profile.industry && !industries.includes(profile.industry)">{{ profile.industry }}</option><option v-for="item in industries" :key="item">{{ item }}</option></select></span></label>
        <label class="mf mt-0 block"><span class="mf__l">企业规模</span><span class="mf__i"><select v-model="profile.companyScale" class="minput"><option value="">请选择</option><option v-for="item in COMPANY_SCALES" :key="item">{{ item }}</option></select></span></label>
        <label class="mf mt-0 block"><span class="mf__l">联系人 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="profile.nickname" class="minput" maxlength="64" required autocomplete="name" /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">联系电话</span><span class="mf__i"><input :value="maskedPhone(user.me?.phone)" class="minput" disabled /></span><span class="acct-hint block">通过安全设置更换手机号</span></label>
        <label class="mf mt-0 block"><span class="mf__l">注册邮箱</span><span class="mf__i"><input v-model.trim="profile.email" class="minput" type="email" maxlength="200" autocomplete="email" /></span></label>
      </template>
      <template v-else-if="modal === 'invoice'">
        <label class="mf mt-0 block account-field-wide"><span class="mf__l">发票抬头 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="draft.title" class="minput" maxlength="200" required /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">发票类型</span><span class="mf__i"><select v-model="draft.type" class="minput"><option>电子普票</option><option>增值税专用发票</option><option>增值税普通发票</option></select></span></label>
        <label class="mf mt-0 block"><span class="mf__l">纳税人识别号 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="draft.taxNo" class="minput" pattern="[A-Za-z0-9]{15,20}" maxlength="20" required /></span></label>
        <label class="mf mt-0 block account-field-wide"><span class="mf__l">开户行及账号</span><span class="mf__i"><input v-model.trim="draft.bank" class="minput" maxlength="200" /></span></label>
      </template>
      <template v-else-if="modal === 'pwd'">
        <label v-if="user.me?.hasPassword" class="mf mt-0 block account-field-wide"><span class="mf__l">当前密码 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model="password.old" type="password" class="minput" required autocomplete="current-password" /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">新密码 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model="password.next" aria-label="新密码" type="password" class="minput" required maxlength="20" autocomplete="new-password" /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">确认新密码 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model="password.confirm" aria-label="确认新密码" type="password" class="minput" required maxlength="20" autocomplete="new-password" /></span></label>
      </template>
      <template v-else-if="modal === 'phone'">
        <label class="mf mt-0 block account-field-wide"><span class="mf__l">当前手机号</span><span class="mf__i"><input :value="maskedPhone(user.me?.phone)" class="minput" disabled /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">新手机号 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="phone.number" type="tel" class="minput" required maxlength="11" pattern="1[3-9][0-9]{9}" autocomplete="tel" /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">验证码 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="phone.code" aria-label="验证码" class="minput account-code" required maxlength="6" pattern="[0-9]{6}" inputmode="numeric" autocomplete="one-time-code" /><button type="button" class="account-send-code" :disabled="sending || cooldown > 0" @click="sendCode">{{ sending ? '发送中' : cooldown ? `${cooldown}s` : '获取验证码' }}</button></span></label>
      </template>
      <p v-if="error" class="account-error account-field-wide" role="alert">{{ error }}</p>
    </form>
    <template #footer><button class="btn-modal-ghost" :disabled="saving" @click="close">取消</button><button class="btn-modal-primary" :form="formId" type="submit" :disabled="saving">{{ saving ? '保存中…' : modal === 'phone' ? '确认更换' : modal === 'invoice' ? '保存草稿' : '保存' }}</button></template>
  </AccountModal>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, useId, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { sendSms, updatePassword, updatePhone, updateProfile } from '@/features/tenant/authApi';
import { COMPANY_SCALES, displayCompanyScale } from '@/utils/companyScale';
import { isValidPassword, PASSWORD_HINT } from '@/utils/password';
import { isCnMobile, PHONE_ERROR } from '@/utils/phone';
import { date, maskedPhone } from './accountFormat';
import AccountIcon from './AccountIcon.vue';
import AccountModal from './AccountModal.vue';
const user = useUserStore(), route = useRoute(), router = useRouter(), formId = useId();
const modal = computed(() => ['org', 'invoice', 'pwd', 'phone'].includes(String(route.query.modal)) ? String(route.query.modal) : '');
const titles: Record<string, string> = { org: '编辑基本信息', invoice: '编辑发票信息', pwd: '修改登录密码', phone: '更换手机号' };
const descriptions: Record<string, string> = { org: '完善企业与联系人资料，企业认证资料待接入。', invoice: '填写开票资料，保存为本浏览器会话草稿。', pwd: PASSWORD_HINT, phone: '更换后请使用新手机号登录，验证码将发送至新手机号。' };
const industries = ['电子商务', '烘焙食品', '美妆个护', '服饰鞋包', '综合电商', '本地生活', '其他'];
const profile = reactive({ companyName: '', nickname: '', industry: '', companyScale: '', email: '' });
const invoice = reactive({ title: '', type: '电子普票', taxNo: '', bank: '' }), draft = reactive({ ...invoice });
const password = reactive({ old: '', next: '', confirm: '' }), phone = reactive({ number: '', code: '' });
const saving = ref(false), sending = ref(false), cooldown = ref(0), error = ref('');
let timer: ReturnType<typeof setInterval> | undefined;
const remainingDays = computed(() => user.me?.periodEnd ? Math.max(0, Math.ceil((new Date(user.me.periodEnd).getTime() - Date.now()) / 86400000)) : 0);
const orgRows = computed(() => [['企业名称', user.me?.companyName || user.me?.tenantName], ['统一社会信用代码', '—'], ['所属行业', user.me?.industry], ['企业规模', displayCompanyScale(user.me?.companyScale)], ['联系人', user.me?.nickname], ['联系电话', maskedPhone(user.me?.phone)], ['注册邮箱', user.me?.email], ['注册时间', date(user.me?.registeredAt)]]);
const invoiceRows = computed(() => [['发票抬头', invoice.title], ['发票类型', invoice.type], ['纳税人识别号', invoice.taxNo], ['开户行及账号', invoice.bank]]);
const security = computed(() => [{ key: 'pwd', title: '登录密码', description: '定期更换密码保障账户安全', status: user.me?.hasPassword ? '已设置' : '未设置', action: user.me?.hasPassword ? '修改' : '设置' }, { key: 'phone', title: '手机验证', description: `已绑定 ${maskedPhone(user.me?.phone)}`, status: user.me?.phone ? '已验证' : '未绑定', action: '更换' }]);
function invoiceKey() { return `lynx-ad-invoice:${user.me?.tenantId || user.me?.userId}`; }
function prepare() { error.value = ''; Object.assign(profile, { companyName: user.me?.companyName || user.me?.tenantName || '', nickname: user.me?.nickname || '', industry: user.me?.industry || '', companyScale: user.me?.companyScale || '', email: user.me?.email || '' }); Object.assign(draft, invoice); Object.assign(password, { old: '', next: '', confirm: '' }); Object.assign(phone, { number: '', code: '' }); }
function open(kind: string) { void router.push({ query: { ...route.query, modal: kind } }); }
function close() { if (!saving.value)
    void router.push({ query: { ...route.query, modal: undefined } }); }
watch(modal, prepare, { immediate: true });
watch(() => route.query.confirm, value => { if (value === 'upgrade')
    void router.replace({ path: '/subscription', query: { plan: 'flagship' } }); }, { immediate: true });
async function sendCode() {
    if (sending.value || cooldown.value)
        return;
    if (!isCnMobile(phone.number)) {
        error.value = PHONE_ERROR;
        return;
    }
    if (phone.number === user.me?.phone) {
        error.value = '请输入新的手机号';
        return;
    }
    sending.value = true;
    error.value = '';
    try {
        const result = await sendSms(phone.number);
        cooldown.value = result.cooldownSec ?? 90;
        if (timer)
            clearInterval(timer);
        timer = setInterval(() => { if (--cooldown.value <= 0) {
            cooldown.value = 0;
            clearInterval(timer);
        } }, 1000);
        ElMessage.success('验证码已发送');
    }
    catch {
        error.value = '验证码发送失败，请重试';
    }
    finally {
        sending.value = false;
    }
}
async function save() {
    if (saving.value)
        return;
    error.value = '';
    if (modal.value === 'pwd' && !isValidPassword(password.next)) {
        error.value = PASSWORD_HINT;
        return;
    }
    if (modal.value === 'pwd' && password.next !== password.confirm) {
        error.value = '两次密码不一致';
        return;
    }
    if (modal.value === 'phone' && (!isCnMobile(phone.number) || !/^\d{6}$/.test(phone.code))) {
        error.value = '请输入有效手机号和六位验证码';
        return;
    }
    const kind = modal.value;
    saving.value = true;
    try {
        if (kind === 'org')
            await updateProfile({ ...profile, companyScale: profile.companyScale || null });
        if (kind === 'pwd')
            await updatePassword({ oldPassword: user.me?.hasPassword ? password.old : undefined, newPassword: password.next });
        if (kind === 'phone')
            await updatePhone(phone.number, phone.code);
        if (kind === 'invoice') {
            sessionStorage.setItem(invoiceKey(), JSON.stringify(draft));
            Object.assign(invoice, draft);
        }
        else
            await user.loadMe();
        ElMessage.success(kind === 'invoice' ? '已保存本地草稿' : '已保存');
        if (modal.value === kind)
            await router.push({ query: { ...route.query, modal: undefined } });
    }
    catch {
        error.value = '保存失败，请检查输入后重试';
    }
    finally {
        saving.value = false;
    }
}
onMounted(() => { invoice.title = user.me?.companyName || user.me?.tenantName || ''; try {
    const raw = JSON.parse(sessionStorage.getItem(invoiceKey()) || '{}') as Record<string, unknown>;
    for (const key of ['title', 'type', 'taxNo', 'bank'] as const)
        if (typeof raw[key] === 'string')
            invoice[key] = raw[key];
}
catch { /* Invalid local draft stays empty. */ } if (modal.value === 'invoice')
    Object.assign(draft, invoice); });
onUnmounted(() => { if (timer)
    clearInterval(timer); });
</script>
