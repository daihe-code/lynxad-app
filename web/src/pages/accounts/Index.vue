<template>
  <div class="accounts">
    <aside class="side page-card">
      <h3>账户管理</h3>
      <button v-for="item in menus" :key="item.key" :class="{ active: tab === item.key }" @click="tab = item.key">
        {{ item.label }}
      </button>
    </aside>
    <section class="main page-card">
      <template v-if="tab === 'info'">
        <h2>账户信息</h2>

        <article class="trial">
          <div>
            <p class="eyebrow">当前订阅</p>
            <h3>{{ user.me?.displayPlanName || "专业版" }}</h3>
            <p v-if="user.me?.subscriptionStatus">
              {{ user.me.subscriptionStatus === "EXPIRED" ? "订阅已过期" : "订阅中" }}
              · 到期 {{ formatTime(user.me.periodEnd) }}
            </p>
            <p v-else>
              {{ user.me?.trialStatusLabel || (user.trialActive ? "试用中" : "试用已结束") }} · 剩余
              {{ user.me?.remainingTrialDays ?? 0 }} 天
            </p>
            <p v-if="!user.me?.subscriptionStatus" class="muted">到期 {{ formatTime(user.me?.trialExpiresAt) }}</p>
          </div>
          <div class="trial-ops">
            <el-button @click="goSubscription">变更套餐</el-button>
            <el-button type="primary" @click="goSubscription">升级到旗舰版</el-button>
          </div>
        </article>

        <article class="block">
          <div class="block-head">
            <h3>基本信息</h3>
            <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存</el-button>
          </div>
          <el-form label-position="top" class="profile-grid">
            <el-form-item label="企业名称">
              <el-input v-model="profile.companyName" />
            </el-form-item>
            <el-form-item label="所属行业">
              <el-select v-model="profile.industry" placeholder="请选择" clearable>
                <el-option v-for="opt in industries" :key="opt" :label="opt" :value="opt" />
              </el-select>
            </el-form-item>
            <el-form-item label="联系人">
              <el-input v-model="profile.nickname" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profile.email" />
            </el-form-item>
            <el-form-item label="企业规模" class="span-2">
              <el-radio-group v-model="profile.companyScale">
                <el-radio v-for="scale in COMPANY_SCALES" :key="scale" :value="scale">{{ scale }}</el-radio>
              </el-radio-group>
              <p class="hint">未选择时展示为 —</p>
            </el-form-item>
            <el-form-item label="联系电话">
              <el-input :model-value="user.me?.phone || '—'" disabled />
            </el-form-item>
            <el-form-item label="注册时间">
              <el-input :model-value="formatTime(user.me?.registeredAt)" disabled />
            </el-form-item>
          </el-form>
        </article>

        <article class="block">
          <div class="block-head">
            <h3>发票信息</h3>
            <el-button @click="saveInvoice">保存</el-button>
          </div>
          <el-form label-position="top" class="profile-grid">
            <el-form-item label="发票抬头">
              <el-input v-model="invoice.title" />
            </el-form-item>
            <el-form-item label="纳税人识别号">
              <el-input v-model="invoice.taxNo" />
            </el-form-item>
            <el-form-item label="发票类型">
              <el-radio-group v-model="invoice.type">
                <el-radio value="电子普票">电子普票</el-radio>
                <el-radio value="电子专票">电子专票</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="开户行及账号">
              <el-input v-model="invoice.bank" placeholder="请自行补充" />
            </el-form-item>
          </el-form>
        </article>

        <article class="block">
          <h3>安全设置</h3>
          <div class="security-row">
            <div>
              <strong>登录密码</strong>
              <p>{{ user.me?.hasPassword ? "已设置登录密码" : "尚未设置登录密码" }}</p>
            </div>
            <el-button @click="openPassword">{{ user.me?.hasPassword ? "修改" : "设置" }}</el-button>
          </div>
          <div class="security-row">
            <div>
              <strong>手机验证</strong>
              <p>已绑定 {{ user.me?.phone || "—" }}</p>
            </div>
            <el-button @click="openPhone">更换</el-button>
          </div>
        </article>
      </template>

      <template v-else-if="tab === 'oauth'">
        <div class="head">
          <div>
            <h2>广告投放授权</h2>
            <p>管理巨量千川广告账户授权。本地推即将上线。</p>
            <p class="meta">
              <span class="ok">已授权 {{ activeCount }} 个账户</span>
            </p>
          </div>
          <el-button type="primary" :disabled="!user.trialActive" @click="showAdd = true">+ 添加新授权</el-button>
        </div>

        <el-empty v-if="accounts.length === 0" description="尚未授权广告账户" />
        <article v-for="acc in accounts" :key="acc.accountId" class="acc">
          <div>
            <strong>{{ acc.accountName }}</strong>
            <el-tag :type="acc.status === 'ACTIVE' ? 'success' : 'warning'" size="small">{{ acc.status === "ACTIVE" ? "授权有效" : acc.status }}</el-tag>
            <p>账户 ID：{{ acc.advertiserId }} · {{ acc.platform === "LOCAL" ? "本地推" : "巨量千川" }}</p>
            <div class="awemes">
              <el-tag v-for="aw in acc.awemes" :key="aw.awemeId" size="small" :effect="aw.isPrimary ? 'dark' : 'plain'">
                {{ aw.name }} · {{ typeLabel(aw.type) }}{{ aw.isPrimary ? " · 主号" : "" }}
              </el-tag>
            </div>
          </div>
          <div class="ops">
            <el-button text @click="onRefresh(acc.accountId)">刷新</el-button>
            <el-button text type="danger" @click="onUnbind(acc.accountId)">取消</el-button>
          </div>
        </article>

        <div class="perm">
          <h3>授权权限说明</h3>
          <p>可以：读取投放数据、管理素材、创建与启停计划。</p>
          <p class="danger">不会：查看或操作账户余额、转账、修改支付信息。</p>
        </div>
      </template>

      <template v-else-if="tab === 'billing'">
        <SubscriptionBilling />
      </template>

      <template v-else>
        <div class="block-head">
          <h2>操作日志</h2>
        </div>
        <el-form inline class="filters">
          <el-form-item label="类型">
            <el-select v-model="audit.actionType" clearable placeholder="全部" style="width: 160px" @change="loadAudit">
              <el-option v-for="t in AUDIT_ACTION_TYPES" :key="t" :label="AUDIT_ACTION_LABEL[t]" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="时间">
            <el-date-picker
              v-model="audit.range"
              type="datetimerange"
              start-placeholder="开始"
              end-placeholder="结束"
              @change="loadAudit"
            />
          </el-form-item>
          <el-form-item label="搜索">
            <el-input v-model="audit.q" placeholder="操作人 / 对象" clearable @keyup.enter="loadAudit" @clear="loadAudit" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadAudit">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="audit.list" v-loading="audit.loading" empty-text="暂无操作记录">
          <el-table-column label="操作时间" min-width="180">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="actorName" label="操作用户" min-width="120" />
          <el-table-column label="操作类型" width="120">
            <template #default="{ row }">{{ actionLabel(row.actionType) }}</template>
          </el-table-column>
          <el-table-column prop="objectLabel" label="操作对象" min-width="160" />
          <el-table-column prop="ipMasked" label="源 IP" width="140" />
        </el-table>
        <div class="pager">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :total="audit.total"
            :page-size="audit.pageSize"
            :current-page="audit.pageNum"
            @current-change="onAuditPage"
          />
        </div>
      </template>
    </section>
  </div>

  <el-dialog v-model="showAdd" title="授权抖音广告投放账号" width="720px">
    <div class="auth-cards">
      <article class="pick">
        <el-tag effect="dark" size="small">推荐</el-tag>
        <h3>巨量千川</h3>
        <p>电商推广 · 全域投放</p>
        <el-button type="primary" :disabled="!user.trialActive" :loading="authorizing" @click="onOauthAuthorize">
          授权巨量千川
        </el-button>
        <el-button
          v-if="user.me?.mockQianchuan === true"
          :disabled="!user.trialActive"
          :loading="binding"
          @click="onMockBind"
        >
          演示授权
        </el-button>
      </article>
      <article class="pick disabled">
        <el-tag size="small">即将上线</el-tag>
        <h3>巨量本地推</h3>
        <p>本地生活 · 到店服务</p>
        <el-button disabled>点击授权巨量本地推</el-button>
      </article>
    </div>
  </el-dialog>

  <el-dialog v-model="pwdOpen" :title="user.me?.hasPassword ? '修改登录密码' : '设置登录密码'" width="420px">
    <el-form label-position="top">
      <el-form-item v-if="user.me?.hasPassword" label="当前密码">
        <el-input v-model="pwdForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" :error="pwdError">
        <el-input v-model="pwdForm.newPassword" type="password" show-password :placeholder="PASSWORD_HINT" />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="pwdForm.confirm" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdOpen = false">取消</el-button>
      <el-button type="primary" :loading="pwdSaving" @click="savePassword">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="phoneOpen" title="更换手机号" width="420px">
    <el-form label-position="top">
      <el-form-item label="新手机号" :error="phoneError">
        <el-input v-model="phoneForm.phone" maxlength="11" />
      </el-form-item>
      <el-form-item label="验证码" :error="phoneCodeError">
        <div class="code-row">
          <el-input v-model="phoneForm.code" maxlength="6" />
          <el-button :disabled="phoneCooldown > 0" @click="sendPhoneCode">
            {{ phoneCooldown > 0 ? `${phoneCooldown}s` : "获取验证码" }}
          </el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="phoneOpen = false">取消</el-button>
      <el-button type="primary" :loading="phoneSaving" @click="savePhone">确认更换</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserStore } from "@/stores/user";
import SubscriptionBilling from "@/pages/subscription/SubscriptionBilling.vue";
import { sendSms, updatePassword, updatePhone, updateProfile, verifySms } from "@/features/tenant/authApi";
import { listAccounts, mockBind, oauthUrl, refreshAccount, unbindAccount, type AccountVo } from "@/features/identity/oauthApi";
import {
  AUDIT_ACTION_LABEL,
  AUDIT_ACTION_TYPES,
  listAuditRecords,
  type AuditRecord,
} from "@/features/audit/auditApi";
import { AWEME_TYPE } from "@/utils/labels";
import { COMPANY_SCALES } from "@/utils/companyScale";
import { isValidPassword, PASSWORD_HINT } from "@/utils/password";
import { isCnMobile, PHONE_ERROR } from "@/utils/phone";

const user = useUserStore();
const router = useRouter();
const route = useRoute();
const tab = ref((route.query.tab as string) || "info");
const menus = [
  { key: "info", label: "账户信息" },
  { key: "oauth", label: "广告投放授权" },
  { key: "billing", label: "订阅与账单" },
  { key: "logs", label: "操作日志" },
];
const industries = ["烘焙食品", "美妆个护", "服饰鞋包", "综合电商", "本地生活", "其他"];
const accounts = ref<AccountVo[]>([]);
const showAdd = ref(false);
const binding = ref(false);
const authorizing = ref(false);
const savingProfile = ref(false);
const activeCount = computed(() => accounts.value.filter((a) => a.status === "ACTIVE").length);

const profile = reactive({
  nickname: "",
  email: "",
  companyName: "",
  industry: "",
  companyScale: "" as string,
});

const invoice = reactive({
  title: "",
  taxNo: "",
  type: "电子普票",
  bank: "",
});

const pwdOpen = ref(false);
const pwdSaving = ref(false);
const pwdError = ref("");
const pwdForm = reactive({ oldPassword: "", newPassword: "", confirm: "" });

const phoneOpen = ref(false);
const phoneSaving = ref(false);
const phoneError = ref("");
const phoneCodeError = ref("");
const phoneCooldown = ref(0);
const phoneForm = reactive({ phone: "", code: "" });
let phoneTimer: ReturnType<typeof setInterval> | undefined;

const audit = reactive({
  actionType: "",
  q: "",
  range: null as [Date, Date] | null,
  list: [] as AuditRecord[],
  total: 0,
  pageNum: 1,
  pageSize: 20,
  loading: false,
});

function formatTime(raw?: string | null) {
  if (!raw) return "—";
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  return d.toLocaleString("zh-CN");
}

function typeLabel(type: string) {
  return AWEME_TYPE[type] || type;
}

function actionLabel(type: string) {
  return AUDIT_ACTION_LABEL[type as keyof typeof AUDIT_ACTION_LABEL] || type;
}

function goSubscription() {
  router.push("/subscription");
}

function syncProfile() {
  const me = user.me;
  profile.nickname = me?.nickname || "";
  profile.email = me?.email || "";
  profile.companyName = me?.companyName || me?.tenantName || "";
  profile.industry = me?.industry || "";
  profile.companyScale = me?.companyScale || "";
}

function invoiceKey() {
  return `lynx-ad-invoice:${user.me?.tenantId || user.me?.userId || "anon"}`;
}

function loadInvoice() {
  try {
    const raw = sessionStorage.getItem(invoiceKey());
    if (raw) {
      Object.assign(invoice, JSON.parse(raw));
      return;
    }
  } catch {
    /* ignore */
  }
  invoice.title = user.me?.companyName || user.me?.tenantName || "";
  invoice.taxNo = "";
  invoice.type = "电子普票";
  invoice.bank = "";
}

function saveInvoice() {
  sessionStorage.setItem(invoiceKey(), JSON.stringify({ ...invoice }));
  ElMessage.success("已保存（演示，正式开票后续提供）");
}

async function saveProfile() {
  savingProfile.value = true;
  try {
    await updateProfile({
      nickname: profile.nickname,
      email: profile.email,
      companyName: profile.companyName,
      industry: profile.industry,
      companyScale: profile.companyScale || null,
    });
    await user.loadMe();
    syncProfile();
    ElMessage.success("资料已更新");
  } catch {
    /* interceptor */
  } finally {
    savingProfile.value = false;
  }
}

function openPassword() {
  pwdForm.oldPassword = "";
  pwdForm.newPassword = "";
  pwdForm.confirm = "";
  pwdError.value = "";
  pwdOpen.value = true;
}

async function savePassword() {
  if (!isValidPassword(pwdForm.newPassword)) {
    pwdError.value = PASSWORD_HINT;
    return;
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning("两次密码不一致");
    return;
  }
  pwdError.value = "";
  pwdSaving.value = true;
  try {
    await updatePassword({
      oldPassword: user.me?.hasPassword ? pwdForm.oldPassword : undefined,
      newPassword: pwdForm.newPassword,
    });
    await user.loadMe();
    pwdOpen.value = false;
    ElMessage.success("密码已更新");
  } catch {
    /* interceptor */
  } finally {
    pwdSaving.value = false;
  }
}

function openPhone() {
  phoneForm.phone = "";
  phoneForm.code = "";
  phoneError.value = "";
  phoneCodeError.value = "";
  phoneOpen.value = true;
}

async function sendPhoneCode() {
  if (!isCnMobile(phoneForm.phone)) {
    phoneError.value = PHONE_ERROR;
    return;
  }
  phoneError.value = "";
  try {
    const result = await sendSms(phoneForm.phone.trim());
    phoneCooldown.value = result.cooldownSec ?? result.ttlSec ?? 90;
    if (phoneTimer) clearInterval(phoneTimer);
    phoneTimer = setInterval(() => {
      phoneCooldown.value -= 1;
      if (phoneCooldown.value <= 0 && phoneTimer) {
        clearInterval(phoneTimer);
        phoneTimer = undefined;
      }
    }, 1000);
  } catch {
    /* 1112 不进入倒计时 */
  }
}

watch(
  () => phoneForm.code,
  async (value) => {
    phoneForm.code = value.replace(/\D/g, "").slice(0, 6);
    phoneCodeError.value = "";
    if (phoneForm.code.length !== 6 || !isCnMobile(phoneForm.phone)) return;
    try {
      const peek = await verifySms(phoneForm.phone.trim(), phoneForm.code);
      phoneCodeError.value = peek.matched ? "" : "请输入正确的验证码";
    } catch {
      /* ignore */
    }
  },
);

async function savePhone() {
  if (!isCnMobile(phoneForm.phone)) {
    phoneError.value = PHONE_ERROR;
    return;
  }
  phoneSaving.value = true;
  try {
    await updatePhone(phoneForm.phone.trim(), phoneForm.code);
    await user.loadMe();
    phoneOpen.value = false;
    ElMessage.success("手机号已更换");
  } catch {
    /* interceptor */
  } finally {
    phoneSaving.value = false;
  }
}

async function loadAccounts() {
  accounts.value = await listAccounts();
}

async function loadAudit() {
  audit.loading = true;
  try {
    const page = await listAuditRecords({
      actionType: audit.actionType || undefined,
      q: audit.q || undefined,
      from: audit.range?.[0]?.toISOString(),
      to: audit.range?.[1]?.toISOString(),
      pageNum: audit.pageNum,
      pageSize: audit.pageSize,
    });
    audit.list = page.list || [];
    audit.total = page.total || 0;
    audit.pageNum = page.pageNum || audit.pageNum;
  } catch {
    audit.list = [];
    audit.total = 0;
  } finally {
    audit.loading = false;
  }
}

function onAuditPage(page: number) {
  audit.pageNum = page;
  loadAudit();
}

async function onOauthAuthorize() {
  authorizing.value = true;
  try {
    const res = await oauthUrl();
    window.location.assign(res.url);
  } finally {
    authorizing.value = false;
  }
}

async function onMockBind() {
  binding.value = true;
  try {
    await mockBind();
    ElMessage.success("授权成功");
    showAdd.value = false;
    await loadAccounts();
  } finally {
    binding.value = false;
  }
}

async function onRefresh(id: string) {
  await refreshAccount(id);
  ElMessage.success("已刷新");
  await loadAccounts();
}

async function onUnbind(id: string) {
  await unbindAccount(id);
  ElMessage.success("已取消授权");
  await loadAccounts();
}

watch(
  tab,
  (key) => {
    if (key === "billing") {
      router.replace("/subscription");
      return;
    }
    if (key === "logs") {
      audit.pageNum = 1;
      loadAudit();
    }
  },
  { immediate: true },
);

onMounted(async () => {
  if (!user.me) {
    await user.loadMe();
  }
  syncProfile();
  loadInvoice();
  await loadAccounts();
});

onUnmounted(() => {
  if (phoneTimer) clearInterval(phoneTimer);
});
</script>

<style scoped>
.accounts {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
}
.side {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.side h3 {
  margin: 0 0 12px;
}
.side button {
  text-align: left;
  border: 0;
  background: transparent;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #4b5563;
}
.side button.active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}
.main {
  padding: 24px;
}
.head,
.block-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.block {
  border: 1px solid #eef0f6;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
}
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.span-2 {
  grid-column: 1 / -1;
}
.hint,
.muted {
  color: var(--color-muted);
  font-size: 12px;
  margin: 4px 0 0;
}
.security-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
}
.security-row p {
  margin: 4px 0 0;
  color: var(--color-muted);
}
.acc {
  display: flex;
  justify-content: space-between;
  border: 1px solid #eef0f6;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
}
.awemes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.ok {
  color: #16a34a;
}
.perm {
  margin-top: 24px;
  background: #f8fafc;
  padding: 16px;
  border-radius: 12px;
}
.danger {
  color: #dc2626;
}
.trial {
  background: linear-gradient(90deg, #575cf6, #7c3aed);
  color: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
}
.eyebrow {
  margin: 0;
  opacity: 0.85;
  font-size: 12px;
}
.trial-ops {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.plans {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.plan {
  border: 1px solid #eef0f6;
  border-radius: 12px;
  padding: 16px;
}
.price {
  font-size: 28px;
  font-weight: 800;
}
.price span {
  font-size: 13px;
  color: #6b7280;
}
.auth-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.pick {
  border: 1px solid var(--color-primary);
  border-radius: 12px;
  padding: 16px;
}
.pick.disabled {
  border-color: #e5e7eb;
  opacity: 0.6;
}
.code-row {
  display: flex;
  gap: 8px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.filters {
  margin-bottom: 8px;
}
@media (max-width: 900px) {
  .accounts,
  .plans,
  .auth-cards,
  .profile-grid,
  .trial {
    grid-template-columns: 1fr;
    display: grid;
  }
  .trial {
    display: block;
  }
}
</style>
