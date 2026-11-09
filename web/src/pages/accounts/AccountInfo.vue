<template>
  <p v-if="legalError" class="account-error" role="alert">{{ legalError }} <button @click="loadLegal">重新加载</button></p>
  <section class="acct-card">
    <div class="acct-card__hd"><h2 class="acct-card__t">订阅信息</h2><router-link class="acct-card__a" to="/subscription">变更套餐 <AccountIcon name="arrow-right" /></router-link></div>
    <div class="acct-card__b subbar">
      <span class="subbar__badge">{{ user.me?.displayPlanName || '—' }}</span>
      <div class="min-w-0"><p class="subbar__n">灵投 LynxAd {{ user.me?.displayPlanName || '—' }}</p><p class="subbar__m">{{ user.me?.subscriptionPlanCode ? `计费周期：${date(user.me.periodStart)} ~ ${date(user.me.periodEnd)}` : `${user.me?.trialStatusLabel || '试用'} · 到期时间：${date(user.me?.trialExpiresAt)}` }}</p></div>
      <div class="subbar__r"><div><p class="subbar__v">{{ user.me?.subscriptionPlanCode ? remainingDays : user.me?.remainingTrialDays ?? 0 }}</p><p class="subbar__k">剩余天数</p></div><div><p class="subbar__v subbar__v--ok">{{ user.me?.subscriptionPlanCode ? user.me.autoRenew ? '自动续费' : '已关闭' : user.me?.trialStatusLabel || '—' }}</p><p class="subbar__k">{{ user.me?.subscriptionPlanCode ? '续费状态' : '试用状态' }}</p></div></div><router-link class="btn-modal-primary account-upgrade" :to="{ path: '/subscription', query: user.me?.displayPlanCode === 'FLAGSHIP' ? {} : { plan: 'flagship' } }">{{ user.me?.displayPlanCode === 'FLAGSHIP' ? '查看套餐' : '升级到旗舰版' }}</router-link>
    </div>
  </section>
  <section class="acct-card">
    <div class="acct-card__hd"><h2 class="acct-card__t">基本信息</h2><button class="acct-card__a" :disabled="!canManage || legalLoading || !!legalError" @click="open('org')">编辑 <AccountIcon name="arrow-right" /></button></div>
    <dl class="acct-card__b acct-kv"><div v-for="row in orgRows" :key="String(row[0])" class="acct-kv__i"><dt class="acct-kv__k">{{ row[0] }}</dt><dd class="acct-kv__v">{{ row[1] || '—' }}</dd></div></dl>
  </section>
  <section class="acct-card">
    <div class="acct-card__hd"><h2 class="acct-card__t">发票信息</h2><button class="acct-card__a" :disabled="!canManage || legalLoading || !!legalError || legal?.verificationStatus !== 'APPROVED'" @click="open('invoice')">编辑 <AccountIcon name="arrow-right" /></button></div>
    <dl class="acct-card__b acct-kv"><div v-for="row in invoiceRows" :key="String(row[0])" class="acct-kv__i"><dt class="acct-kv__k">{{ row[0] }}</dt><dd class="acct-kv__v">{{ row[1] || '—' }}</dd></div></dl>
    <p class="acct-hint">企业人工审核通过后可保存发票资料，抬头与税号须与核验企业一致。增值税专用发票按月处理（每月1日）；此处仅保存资料，不发起开票。</p>
  </section>
  <section class="acct-card">
    <h2 class="acct-card__t">安全设置</h2>
    <div class="mt-[6px]"><div v-for="item in security" :key="item.key" class="secrow"><span class="secrow__ic"><AccountIcon :name="item.key === 'pwd' ? 'lock' : 'phone'" /></span><div class="min-w-0"><p class="secrow__t">{{ item.title }}</p><p class="secrow__d">{{ item.description }}</p></div><div class="secrow__r"><span class="badge-ok">{{ item.status }}</span><button class="link-act" @click="open(item.key)">{{ item.action }}</button></div></div></div>
  </section>
  <AccountModal v-if="modal" :key="modal" :title="titles[modal] || ''" :description="descriptions[modal]" :busy="saving" @close="close">
    <form :id="formId" class="account-form-grid" @submit.prevent="save">
      <template v-if="modal === 'org'">
        <label class="mf mt-0 block account-field-wide"><span class="mf__l">企业名称 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="profile.companyName" class="minput" maxlength="200" required autocomplete="organization" :disabled="legal?.verificationStatus === 'PENDING'" /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">统一社会信用代码 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="profile.creditCode" class="minput" required pattern="[A-Za-z0-9]{18}" minlength="18" maxlength="18" :disabled="legal?.verificationStatus === 'PENDING'" /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">所属行业 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><select v-model="profile.industry" class="minput" required><option value="" disabled>请选择</option><option v-if="profile.industry && !industries.includes(profile.industry)">{{ profile.industry }}</option><option v-for="item in industries" :key="item">{{ item }}</option></select></span></label>
        <label class="mf mt-0 block"><span class="mf__l">企业规模</span><span class="mf__i"><select v-model="profile.companyScale" class="minput"><option value="">请选择</option><option v-for="item in COMPANY_SCALES" :key="item">{{ item }}</option></select></span></label>
        <label class="mf mt-0 block"><span class="mf__l">联系人 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="profile.nickname" class="minput" maxlength="64" required autocomplete="name" /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">联系电话</span><span class="mf__i"><input :value="maskedPhone(user.me?.phone)" class="minput" disabled /></span><span class="acct-hint block">通过安全设置更换手机号</span></label>
        <label class="mf mt-0 block"><span class="mf__l">注册邮箱</span><span class="mf__i"><input v-model.trim="profile.email" class="minput" type="email" maxlength="200" autocomplete="email" /></span></label>
      </template>
      <template v-else-if="modal === 'invoice'">
        <label class="mf mt-0 block account-field-wide"><span class="mf__l">发票抬头 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="draft.title" class="minput" maxlength="200" required /></span></label>
        <label class="mf mt-0 block"><span class="mf__l">发票类型</span><span class="mf__i"><select v-model="draft.type" class="minput"><option>增值税专用发票</option></select></span></label>
        <label class="mf mt-0 block"><span class="mf__l">纳税人识别号 <i class="mreq" aria-hidden="true">*</i></span><span class="mf__i"><input v-model.trim="draft.taxNo" class="minput" pattern="[A-Za-z0-9]{18}" maxlength="18" required /></span></label>
        <label class="mf mt-0 block account-field-wide"><span class="mf__l">开户行及账号</span><span class="mf__i"><input v-model.trim="draft.bank" class="minput" maxlength="200" required /></span></label>
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
    <template #footer><button class="btn-modal-ghost" :disabled="saving" @click="close">取消</button><button class="btn-modal-primary" :form="formId" type="submit" :disabled="saving">{{ saving ? '保存中…' : modal === 'phone' ? '确认更换' : modal === 'invoice' ? '保存发票资料' : modal === 'org' ? '保存企业资料' : '保存' }}</button></template>
  </AccountModal>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, useId, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { sendSms, updatePassword, updatePhone } from '@/features/tenant/authApi';
import { getLegalProfile, saveCompanyProfile, saveInvoiceProfile, type LegalProfile } from '@/features/tenant/accountLegalApi';
import { COMPANY_SCALES, displayCompanyScale } from '@/utils/companyScale';
import { isValidPassword, PASSWORD_HINT } from '@/utils/password';
import { isCnMobile, PHONE_ERROR } from '@/utils/phone';
import { date, maskedPhone } from './accountFormat';
import AccountIcon from './AccountIcon.vue';
import AccountModal from './AccountModal.vue';
const user = useUserStore(), route = useRoute(), router = useRouter(), formId = useId();
const modal = computed(() => ['org', 'invoice', 'pwd', 'phone'].includes(String(route.query.modal)) ? String(route.query.modal) : '');
const titles: Record<string, string> = { org: '编辑基本信息', invoice: '编辑发票信息', pwd: '修改登录密码', phone: '更换手机号' };
const descriptions: Record<string, string> = { org: '企业资料提交后由平台人工审核，预计1个工作日。审核中企业名称和信用代码锁定，联系人可更新。', invoice: '保存到企业账户，发票抬头与税号须与已核验企业一致。', pwd: PASSWORD_HINT, phone: '更换后请使用新手机号登录，验证码将发送至新手机号。' };
const industries = ['电子商务', '烘焙食品', '美妆个护', '服饰鞋包', '综合电商', '本地生活', '其他'];
const profile = reactive({ companyName: '', creditCode: '', nickname: '', industry: '', companyScale: '', email: '' });
const invoice = reactive({ title: '', type: '增值税专用发票', taxNo: '', bank: '' }), draft = reactive({ ...invoice });
const password = reactive({ old: '', next: '', confirm: '' }), phone = reactive({ number: '', code: '' });
const saving = ref(false), sending = ref(false), cooldown = ref(0), error = ref('');
let timer: ReturnType<typeof setInterval> | undefined;
const remainingDays = computed(() => user.me?.periodEnd ? Math.max(0, Math.ceil((new Date(user.me.periodEnd).getTime() - Date.now()) / 86400000)) : 0);
const legal = ref<LegalProfile>();
const legalLoading = ref(true), legalError = ref('');
const canManage = computed(() => user.me?.memberRole === 'OWNER');
const verificationLabels = { UNSUBMITTED: '未提交', PENDING: '待人工审核', APPROVED: '审核通过', REJECTED: '已驳回' };
const orgRows = computed<(string | null | undefined)[][]>(() => [['企业名称', legal.value?.companyName || user.me?.companyName], ['统一社会信用代码', legal.value?.creditCode], ['资质核验状态', legal.value ? verificationLabels[legal.value.verificationStatus] : '加载中'], ['审核说明', legal.value?.reviewReason], ['所属行业', legal.value?.industry], ['企业规模', displayCompanyScale(legal.value?.companyScale)], ['联系人', legal.value?.nickname], ['联系电话', maskedPhone(legal.value?.contactPhone)], ['注册邮箱', legal.value?.email], ['注册时间', date(user.me?.registeredAt)]]);
const invoiceRows = computed(() => [['发票抬头', invoice.title], ['发票类型', invoice.type], ['纳税人识别号', invoice.taxNo], ['开户行及账号', invoice.bank]]);
const security = computed(() => [{ key: 'pwd', title: '登录密码', description: '定期更换密码保障账户安全', status: user.me?.hasPassword ? '已设置' : '未设置', action: user.me?.hasPassword ? '修改' : '设置' }, { key: 'phone', title: '手机验证', description: `已绑定 ${maskedPhone(user.me?.phone)}`, status: user.me?.phone ? '已验证' : '未绑定', action: '更换' }]);
async function loadLegal() {
  legalLoading.value = true; legalError.value = '';
  try { applyLegal(await getLegalProfile()); if (modal.value) prepare(); }
  catch { legalError.value = '企业及发票资料加载失败，请重试'; }
  finally { legalLoading.value = false; }
}
function applyLegal(value: LegalProfile) {
  legal.value = value;
  Object.assign(invoice, value.invoice || { title: '', type: '增值税专用发票', taxNo: '', bank: '' });
}
function prepare() { error.value = ''; Object.assign(profile, { companyName: legal.value?.companyName || user.me?.companyName || '', creditCode: legal.value?.creditCode || '', nickname: legal.value?.nickname || '', industry: legal.value?.industry || '', companyScale: legal.value?.companyScale || '', email: legal.value?.email || '' }); Object.assign(draft, invoice.title ? invoice : { ...invoice, title: legal.value?.companyName || '', taxNo: legal.value?.creditCode || '' }); Object.assign(password, { old: '', next: '', confirm: '' }); Object.assign(phone, { number: '', code: '' }); }
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
    if (['org', 'invoice'].includes(modal.value) && (!canManage.value || legalLoading.value || legalError.value)) { error.value = '请先加载企业资料，并使用主账户操作'; return; }
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
            applyLegal(await saveCompanyProfile({ ...profile, companyScale: profile.companyScale || null }));
        if (kind === 'pwd')
            await updatePassword({ oldPassword: user.me?.hasPassword ? password.old : undefined, newPassword: password.next });
        if (kind === 'phone')
            await updatePhone(phone.number, phone.code);
        if (kind === 'invoice') {
            applyLegal(await saveInvoiceProfile({ ...draft }));
        }
        else
            await user.loadMe();
        if (kind === 'phone')
            await loadLegal();
        ElMessage.success(kind === 'invoice' ? '发票资料已保存到企业账户' : kind === 'org' && legal.value?.verificationStatus === 'PENDING' ? '已保存，等待人工审核' : '已保存');
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
onMounted(loadLegal);
onUnmounted(() => { if (timer)
    clearInterval(timer); });
</script>
