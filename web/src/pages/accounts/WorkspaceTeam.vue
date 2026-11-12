<template>
  <section v-loading="loading" class="workspace-team" aria-label="子账户列表">
    <div class="heading">
      <span v-if="team" class="quota">
        已用名额 {{ team.used }} / {{ team.limit }}
        <span v-if="team.limit === 0"> · 当前套餐无子账户名额</span>
      </span>
      <el-button class="team-primary" type="primary" :disabled="!team || team.used >= team.limit || actionsDisabled" @click="open('create')">创建子账户</el-button>
    </div>
    <div v-if="loadError" class="load-error" role="status">
      {{ loadError }} <el-button text type="primary" :disabled="loading" @click="load">重试</el-button>
    </div>
    <el-table v-if="team" :data="[...team.members, ...team.invites]" row-key="id" empty-text="暂无子账户">
      <el-table-column prop="phone" label="手机号" min-width="125" />
      <el-table-column label="权限" min-width="130">
        <template #default="{ row }">{{ (row.roles || [row.role]).map((role: string) => workspaceRoles[role] || role).join('、') }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }">{{ status[row.status] || row.status }}</template></el-table-column>
      <el-table-column label="操作" width="246">
        <template #default="{ row }">
          <el-button class="team-action" text type="primary" :disabled="actionsDisabled" @click="open('permissions', row)">设置权限</el-button>
          <template v-if="row.status === 'PENDING'">
            <el-button class="team-action" text type="primary" :disabled="actionsDisabled" @click="open('activate', row)">激活</el-button>
            <el-button class="team-action" text type="primary" :disabled="actionsDisabled" @click="revoke(row)">撤销邀请</el-button>
          </template>
          <el-button v-else text :disabled="actionsDisabled" @click="toggle(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
    <el-dialog v-model="dialog" :title="dialogTitle" width="min(680px, calc(100vw - 24px))"
      class="workspace-team-dialog" align-center append-to-body
      :close-on-click-modal="false" :close-on-press-escape="!dialogBusy" :show-close="!dialogBusy" @closed="clearForm">
      <template #header="{ titleId }">
        <div class="team-dialog-title">
          <span class="team-dialog-icon" aria-hidden="true"><el-icon><User /></el-icon></span>
          <h2 :id="titleId">{{ dialogTitle }}</h2>
          <span v-if="mode === 'create' && team" class="team-seat-count">已用名额 {{ team.used }} / {{ team.limit }}</span>
        </div>
      </template>
      <el-form id="workspace-team-form" label-position="top" @submit.prevent="mode === 'permissions' ? savePermissions() : activate()">
        <section class="team-dialog-section" aria-labelledby="team-account-heading">
          <h3 id="team-account-heading">账户信息</h3>
          <el-form-item v-if="mode === 'create'" label="登录手机号" required :error="phoneError">
            <div class="team-input-row">
              <el-input ref="phoneInput" v-model="phone" type="tel" maxlength="11" inputmode="numeric" autocomplete="off"
                placeholder="请输入 11 位手机号" :disabled="dialogBusy" :readonly="!!activationId" @input="phoneError = ''">
                <template #prefix><span class="team-dialing-code">+86</span></template>
              </el-input>
              <el-button v-if="activationId" class="team-secondary" type="primary" plain :disabled="dialogBusy" @click="changePhone">更换手机号</el-button>
            </div>
          </el-form-item>
          <div v-else class="team-account-summary">
            <div><span>登录手机号</span><strong>{{ phone }}</strong></div>
            <div v-if="mode === 'activate' && selectedRoles.length"><span>已分配权限</span><strong>{{ selectedRoles.map(role => workspaceRoles[role]).join('、') }}</strong></div>
            <el-tag v-if="editing" effect="light" :type="editing.status === 'ACTIVE' ? 'success' : editing.status === 'PENDING' ? 'warning' : 'info'">{{ status[editing.status] || editing.status }}</el-tag>
          </div>
        </section>
        <template v-if="mode !== 'activate'">
          <section class="team-dialog-section" aria-labelledby="team-permissions-heading">
            <div class="team-section-heading">
              <h3 id="team-permissions-heading">权限设置</h3>
              <span aria-live="polite">可多选 · 已选 {{ selectedRoles.length }} 项</span>
            </div>
            <el-form-item :error="formError">
              <el-checkbox-group v-model="selectedRoles" aria-label="子账户权限" class="team-permissions" :disabled="dialogBusy"
                @change="formError = selectedRoles.length ? '' : '请至少选择一项权限'">
                <el-checkbox v-for="option in roles" :key="option" :value="option" class="team-permission-card">
                  <span class="team-permission-name">{{ workspaceRoles[option] }}</span>
                  <span class="team-permission-description">{{ childRoleDescriptions[option] }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </section>
        </template>
        <template v-if="mode !== 'permissions'">
          <section class="team-dialog-section" aria-labelledby="team-activation-heading">
            <h3 id="team-activation-heading">验证与激活</h3>
            <div class="team-activation-fields">
              <el-form-item label="短信验证码" required :error="codeError">
                <div class="team-input-row team-sms-row">
                  <el-input ref="codeInput" v-model="code" maxlength="6" inputmode="numeric" autocomplete="one-time-code"
                    placeholder="输入 6 位验证码" :disabled="dialogBusy" @input="codeError = ''" />
                  <el-button class="team-sms-button" type="primary" :loading="sending" :disabled="dialogBusy || remaining > 0 || (mode === 'create' && !selectedRoles.length)"
                    @click="sendCode">{{ remaining > 0 ? `${remaining} 秒后重发` : '获取验证码' }}</el-button>
                </div>
                <span v-if="remaining > 0 && !codeError" class="team-code-status" role="status"><el-icon><CircleCheck /></el-icon>验证码已发送</span>
              </el-form-item>
              <el-form-item label="登录密码" required :error="passwordError">
                <el-input v-model="password" type="password" show-password maxlength="20" autocomplete="new-password"
                  placeholder="设置初始密码" :disabled="dialogBusy" @input="passwordError = ''" />
                <div class="team-password-rules">
                  <span :class="{ 'rule-met': passwordLengthValid }"><el-icon v-if="passwordLengthValid" aria-hidden="true"><Check /></el-icon>8–20 位</span>
                  <span :class="{ 'rule-met': passwordMixValid }"><el-icon v-if="passwordMixValid" aria-hidden="true"><Check /></el-icon>包含字母和数字</span>
                </div>
              </el-form-item>
            </div>
          </section>
        </template>
      </el-form>
      <template #footer>
        <el-button :disabled="dialogBusy" @click="dialog = false">取消</el-button>
        <el-button class="team-primary" type="primary" native-type="submit" form="workspace-team-form" :loading="busy"
          :disabled="dialogBusy || (mode !== 'activate' && !selectedRoles.length) || (mode === 'permissions' ? !permissionsChanged : (!activationId || !code || !password))">
          {{ mode === 'create' ? '创建并激活' : mode === 'permissions' ? '保存权限' : '激活' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox, type InputInstance } from 'element-plus';
import { Check, CircleCheck, User } from '@element-plus/icons-vue';
import { requestFailure } from '@/api/apiErrors';
import { isCnMobile } from '@/utils/phone';
import { workspaceTeam, inviteChild, changeChild, changeChildInviteRole, revokeChildInvite, sendChildActivationSms, activateChild, workspaceRoles, childRoleDescriptions, type ChildRole, type WorkspaceChild, type WorkspaceTeam } from '@/features/tenant/workspaceApi';

const team = ref<WorkspaceTeam>();
const dialog = ref(false), busy = ref(false), loading = ref(true);
const phone = ref(''), phoneError = ref(''), formError = ref('');
const activationId = ref(''), code = ref(''), password = ref('');
const phoneInput = ref<InputInstance>(), codeInput = ref<InputInstance>();
const codeError = ref(''), passwordError = ref(''), sending = ref(false), remaining = ref(0);
const passwordLengthValid = computed(() => /^.{8,20}$/.test(password.value));
const passwordMixValid = computed(() => /[A-Za-z]/.test(password.value) && /\d/.test(password.value));
let timer: ReturnType<typeof setInterval> | undefined;
const loadError = ref(''), actionError = ref('');
type DialogMode = 'create' | 'permissions' | 'activate';
const mode = ref<DialogMode>('create');
const dialogTitle = computed(() => ({ create: '创建子账户', permissions: '设置权限', activate: '激活子账户' })[mode.value]);
const dialogBusy = computed(() => busy.value || sending.value);
const editing = ref<WorkspaceChild>();
const roles = Object.keys(childRoleDescriptions) as ChildRole[];
const selectedRoles = ref<ChildRole[]>([]);
const permissionsChanged = computed(() => {
  const saved = editing.value?.roles ?? [editing.value?.role];
  return selectedRoles.value.length !== saved.length || selectedRoles.value.some(role => !saved.includes(role));
});
let savedInviteRoles: ChildRole[] = [];
const actionsDisabled = computed(() => dialogBusy.value || loading.value || !!loadError.value);
const status: Record<string, string> = { ACTIVE: '已启用', DISABLED: '已停用', PENDING: '待激活' };
async function load() {
  loading.value = true;
  loadError.value = '';
  try { team.value = await workspaceTeam(); }
  catch (error) { loadError.value = requestFailure(error).message; }
  finally { loading.value = false; }
}
function open(nextMode: DialogMode, row?: WorkspaceChild) {
  clearForm();
  mode.value = nextMode;
  editing.value = row;
  phone.value = row?.phone || '';
  selectedRoles.value = row ? roles.filter(value => row.roles?.includes(value) ?? value === row.role) : ['OPERATOR'];
  if (nextMode === 'activate' && row) activationId.value = row.id;
  actionError.value = '';
  dialog.value = true;
}
function clearForm() {
  clearInterval(timer);
  activationId.value = '';
  savedInviteRoles = [];
  code.value = '';
  password.value = '';
  codeError.value = '';
  passwordError.value = '';
  phoneError.value = '';
  formError.value = '';
  sending.value = false;
  remaining.value = 0;
}
async function savePermissions() {
  const row = editing.value;
  const selected = [...selectedRoles.value];
  if (dialogBusy.value || !row || !selected.length || !permissionsChanged.value) return;
  busy.value = true;
  formError.value = '';
  try {
    if (row.status === 'PENDING') await changeChildInviteRole(row.id, selected);
    else await changeChild(row.id, { roles: selected });
    dialog.value = false;
    ElMessage.success('权限已保存');
    await load();
  } catch (error) {
    const failure = requestFailure(error, { method: 'PUT' });
    formError.value = failure.message;
    if (row.status === 'PENDING' && failure.code === 1911) await load();
  } finally { busy.value = false; }
}
async function changePhone() {
  const id = activationId.value;
  if (dialogBusy.value || mode.value !== 'create' || !id) return;
  try {
    await ElMessageBox.confirm(`更换手机号将撤销 ${phone.value} 的待激活邀请。`, '更换登录手机号', {
      confirmButtonText: '确认更换', cancelButtonText: '取消',
    });
  } catch { return; }
  if (!dialog.value || dialogBusy.value || activationId.value !== id) return;
  busy.value = true;
  try {
    await revokeChildInvite(id);
    clearForm();
    phone.value = '';
    await load();
  } catch (error) {
    phoneError.value = requestFailure(error, { method: 'DELETE' }).message;
  } finally { busy.value = false; }
  if (dialog.value && !activationId.value) {
    await nextTick();
    phoneInput.value?.focus();
  }
}
async function sendCode() {
  if (dialogBusy.value || remaining.value) return;
  const creatingInvite = mode.value === 'create' && !activationId.value;
  const number = phone.value.trim();
  const selected = [...selectedRoles.value];
  if (mode.value === 'create' && !selected.length) return;
  if (creatingInvite) {
    if (!isCnMobile(number)) { phoneError.value = '请输入正确的手机号'; return; }
  } else if (!activationId.value) return;
  sending.value = true;
  codeError.value = '';
  formError.value = '';
  let sent = false;
  try {
    if (creatingInvite) {
      const invitation = await inviteChild(number, selected);
      if (!dialog.value || !sending.value) return;
      activationId.value = invitation.id;
      savedInviteRoles = selected;
    }
    const id = activationId.value;
    const result = await sendChildActivationSms(id);
    if (!dialog.value || activationId.value !== id) return;
    code.value = '';
    remaining.value = result.cooldownSec;
    clearInterval(timer);
    if (remaining.value > 0) {
      const end = Date.now() + remaining.value * 1000;
      timer = setInterval(() => {
        remaining.value = Math.max(0, Math.ceil((end - Date.now()) / 1000));
        if (remaining.value === 0) clearInterval(timer);
      }, 250);
    }
    sent = true;
  } catch (error) {
    if (dialog.value && sending.value) {
      const failure = requestFailure(error, { method: 'POST' });
      if (creatingInvite && !activationId.value && [1001, 1911].includes(failure.code ?? 0)) phoneError.value = failure.message;
      else codeError.value = failure.message;
    }
  } finally {
    if (creatingInvite && dialog.value && sending.value) await load();
    sending.value = false;
    if (sent && dialog.value) {
      await nextTick();
      codeInput.value?.focus();
    }
  }
}
async function activate() {
  const id = activationId.value;
  const selected = [...selectedRoles.value];
  if (dialogBusy.value || !id || (mode.value === 'create' && !selected.length)) return;
  const smsCode = code.value, initialPassword = password.value;
  if (!/^\d{6}$/.test(smsCode)) { codeError.value = '请输入 6 位验证码'; return; }
  if (!passwordLengthValid.value || !passwordMixValid.value) { passwordError.value = '密码须为 8–20 位且同时包含字母和数字'; return; }
  busy.value = true;
  formError.value = '';
  codeError.value = '';
  passwordError.value = '';
  let updatingRoles = mode.value === 'create' &&
    (selected.length !== savedInviteRoles.length || selected.some(role => !savedInviteRoles.includes(role)));
  try {
    if (updatingRoles) {
      await changeChildInviteRole(id, selected);
      savedInviteRoles = selected;
      updatingRoles = false;
    }
    const result = await activateChild(id, smsCode, initialPassword);
    password.value = '';
    code.value = '';
    dialog.value = false;
    ElMessage.success(result.initialPasswordSet ? '子账户已激活' : '子账户已激活，请使用原登录方式');
    await load();
  } catch (error) {
    const failure = requestFailure(error, { method: updatingRoles ? 'PUT' : 'POST' });
    if (updatingRoles) formError.value = failure.message;
    else if (failure.code === 1002) passwordError.value = failure.message;
    else codeError.value = failure.message;
    if (failure.action === 'VERIFY' || failure.code === 1910 || failure.code === 1911) await load();
  } finally { busy.value = false; }
}
async function toggle(row: WorkspaceChild) {
  if (actionsDisabled.value) return;
  const active = row.status !== 'ACTIVE';
  if (!active) {
    try { await ElMessageBox.confirm(`停用子账户 ${row.phone}？`, '停用子账户', { type: 'warning' }); } catch { return; }
  }
  await runAction(() => changeChild(row.id, { active }));
}
function revoke(row: WorkspaceChild) {
  return runAction(() => revokeChildInvite(row.id));
}
async function runAction(action: () => Promise<unknown>) {
  if (actionsDisabled.value) return;
  busy.value = true;
  actionError.value = '';
  try { await action(); await load(); }
  catch (error) { actionError.value = requestFailure(error).message; }
  finally { busy.value = false; }
}
onMounted(load);
onBeforeUnmount(clearForm);
</script>

<style scoped>
.workspace-team { min-height: 160px; }
.heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.heading .el-button { margin-left: auto; }
.el-button.team-primary { background: #575cf6; border: 1px solid #575cf6; color: white; padding-inline: 16px; }
.el-button.team-primary:hover:not(:disabled) { background: #494de2; border-color: #494de2; color: white; }
.el-button.team-primary:disabled { background: #bfc0f6; border-color: #bfc0f6; color: white; }
.el-button.team-action { color: #575cf6; }
.el-button.team-action:disabled { opacity: 0.55; }
.quota { color: #676b84; font-size: 14px; }
.load-error, .action-error { color: #c23c45; font-size: 14px; }

.team-dialog-title, .team-section-heading { display: flex; align-items: center; gap: 12px; }
.team-dialog-title { flex-wrap: wrap; }
.team-dialog-title h2 { margin: 0; color: #1f2333; font-size: 20px; font-weight: 650; }
.team-dialog-icon { display: grid; flex: none; width: 40px; height: 40px; place-items: center; border-radius: 12px; background: var(--color-primary-soft); color: var(--color-primary); font-size: 21px; }
.team-seat-count { padding: 4px 9px; border-radius: 999px; background: #f0f1fc; color: #525884; font-size: 12px; font-variant-numeric: tabular-nums; }
.team-dialog-section { padding: 14px 16px 16px; border: 1px solid #e6e7f3; border-radius: 12px; background: #f8f9fe; }
.team-dialog-section + .team-dialog-section { margin-top: 12px; }
.team-dialog-section h3 { margin: 0 0 12px; color: #242740; font-size: 15px; line-height: 20px; }
.team-section-heading { justify-content: space-between; margin-bottom: 12px; }
.team-section-heading h3 { margin: 0; }
.team-section-heading > span { color: #62698b; font-size: 12px; }
.team-dialog-section :deep(.el-form-item) { margin: 0; }
.team-dialog-section :deep(.el-form-item.is-error) { margin-bottom: 16px; }
.team-dialog-section :deep(.el-form-item__error) { line-height: 1.3; }
.team-dialog-section :deep(.el-form-item__label) { margin-bottom: 6px; color: #373b56; font-size: 13px; }
.team-dialog-section :deep(.el-input__wrapper) { box-sizing: border-box; min-height: 38px; border-radius: 8px; }
.team-dialog-section :deep(.el-input__inner:focus) { outline: none; box-shadow: none; }
.team-input-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.team-input-row .el-input { min-width: 0; }
.team-input-row .team-secondary { flex: none; height: 38px; padding-inline: 12px; border-radius: 8px; }
.team-sms-row { gap: 10px; max-width: 300px; }
.team-sms-row .el-input { flex: 1; width: auto; }
.el-button.team-sms-button {
  --el-button-border-color: var(--color-primary-dark);
  flex: none;
  min-width: 104px;
  height: 38px;
  padding-inline: 10px;
  border: 1px solid var(--el-button-border-color);
  border-radius: 8px;
}
.team-dialing-code { padding-right: 8px; border-right: 1px solid #e2e4f1; color: #565d79; font-size: 13px; line-height: 16px; }
.team-account-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 12px 24px; }
.team-account-summary > div { display: grid; gap: 4px; }
.team-account-summary span { color: #62698b; font-size: 12px; }
.team-account-summary strong { color: #242740; font-size: 14px; font-variant-numeric: tabular-nums; }
.team-account-summary .el-tag { margin-inline-start: auto; }
.team-permissions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; width: 100%; }
.team-permission-card { box-sizing: border-box; display: flex; align-items: flex-start; height: auto; min-width: 0; min-height: 78px; margin: 0; padding: 10px; border: 1px solid #dfe2f1; border-radius: 9px; background: #fff; white-space: normal; transition: border-color 150ms, background-color 150ms; }
.team-permission-card:hover:not(.is-disabled) { border-color: var(--el-color-primary-light-3); }
.team-permission-card.is-checked { border-color: var(--color-primary); background: var(--color-primary-soft); }
.team-permission-card :deep(.el-checkbox__input) { margin-top: 2px; }
.team-permission-card :deep(.el-checkbox__inner) { border: 1px solid var(--el-color-primary-light-3); }
.team-permission-card :deep(.el-checkbox__inner::after) { border-width: 0 1px 1px 0; }
.team-permission-card :deep(.el-checkbox__label) { display: grid; gap: 4px; min-width: 0; line-height: 1.4; white-space: normal; }
.team-permission-name { font-weight: 600; }
.team-permission-description { color: #565d79; font-size: 12px; }
.team-activation-fields { display: grid; grid-template-columns: 1.08fr 1fr; align-items: start; gap: 12px; }
.team-activation-fields .el-form-item { min-width: 0; }
.team-password-rules { display: flex; flex-wrap: wrap; gap: 4px 12px; margin-top: 7px; color: #62698b; }
.team-password-rules span, .team-code-status { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; line-height: 17px; }
.team-code-status { margin-top: 7px; }
.team-password-rules .rule-met, .team-code-status { color: #147750; }

@media (max-width: 590px) {
  .team-activation-fields { grid-template-columns: 1fr; }
  .team-permissions { grid-template-columns: 1fr; }
  .team-permission-card { min-height: 0; }
  .team-permission-card :deep(.el-checkbox__label) { display: flex; flex-wrap: wrap; align-items: baseline; gap: 2px 8px; }
  .team-dialog-section { padding: 12px; }
}
</style>

<style>
.workspace-team-dialog.el-dialog { display: flex; flex-direction: column; max-height: calc(100dvh - 24px); margin: auto; padding: 0; border-radius: 18px; box-shadow: 0 20px 64px #27245330; }
.workspace-team-dialog .el-dialog__header { margin: 0; padding: 16px 52px 16px 20px; border-bottom: 1px solid #eceef7; }
.workspace-team-dialog .el-dialog__headerbtn { top: 10px; right: 8px; }
.workspace-team-dialog .el-dialog__body { min-height: 0; overflow: auto; padding: 14px 20px; }
.workspace-team-dialog .el-dialog__footer { padding: 12px 20px; border-top: 1px solid #eceef7; }
.workspace-team-dialog .el-dialog__footer .el-button { min-height: 38px; border-radius: 8px; }
@media (max-width: 590px) {
  .workspace-team-dialog .el-dialog__header { padding-inline-start: 14px; }
  .workspace-team-dialog .el-dialog__body, .workspace-team-dialog .el-dialog__footer { padding-inline: 14px; }
}
</style>
