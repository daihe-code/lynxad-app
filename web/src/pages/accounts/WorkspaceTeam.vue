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
    <el-dialog v-model="dialog" :title="dialogTitle" width="min(440px, 92vw)"
      :close-on-click-modal="false" :close-on-press-escape="!dialogBusy" :show-close="!dialogBusy" @closed="clearForm">
      <el-form id="workspace-team-form" label-position="top" @submit.prevent="mode === 'permissions' ? savePermissions() : activate()">
        <el-form-item v-if="mode === 'create'" label="登录手机号" :error="phoneError">
          <el-input v-model="phone" class="team-phone-input" type="tel" maxlength="11" autocomplete="off"
            :disabled="dialogBusy" :readonly="!!activationId" @input="phoneError = ''" />
        </el-form-item>
        <p v-else class="role-description">{{ mode === 'activate' ? `登录手机号 ${phone}` : phone }}</p>
        <template v-if="mode !== 'activate'">
          <el-form-item label="权限" :error="formError">
            <el-checkbox-group v-model="selectedRoles" class="team-permissions" :disabled="dialogBusy" @change="formError = ''">
              <el-checkbox v-for="option in roles" :key="option" :value="option">{{ workspaceRoles[option] }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <p v-for="option in selectedRoles" :key="option" class="role-description">{{ childRoleDescriptions[option] }}</p>
        </template>
        <template v-if="mode !== 'permissions'">
          <el-form-item label="短信验证码" :error="codeError">
            <div class="code-row">
              <el-input v-model="code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" :disabled="busy" @input="codeError = ''" />
              <el-button :loading="sending" :disabled="dialogBusy || remaining > 0 || (!activationId && !selectedRoles.length)"
                @click="sendCode">{{ remaining > 0 ? `${remaining} 秒后重发` : '获取验证码' }}</el-button>
            </div>
          </el-form-item>
          <el-form-item label="登录密码" :error="passwordError">
            <el-input v-model="password" type="password" show-password maxlength="20" autocomplete="new-password"
              placeholder="8–20 位，包含字母和数字" :disabled="busy" @input="passwordError = ''" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button :disabled="dialogBusy" @click="dialog = false">取消</el-button>
        <el-button class="team-primary" type="primary" native-type="submit" form="workspace-team-form" :loading="busy"
          :disabled="dialogBusy || (mode !== 'activate' && !selectedRoles.length) || (mode !== 'permissions' && (!activationId || !code || !password))">
          {{ mode === 'create' ? '创建并激活' : mode === 'permissions' ? '保存权限' : '激活' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { requestFailure } from '@/api/apiErrors';
import { isCnMobile } from '@/utils/phone';
import { workspaceTeam, inviteChild, changeChild, changeChildInviteRole, revokeChildInvite, sendChildActivationSms, activateChild, workspaceRoles, childRoleDescriptions, type ChildRole, type WorkspaceChild, type WorkspaceTeam } from '@/features/tenant/workspaceApi';

const team = ref<WorkspaceTeam>();
const dialog = ref(false), busy = ref(false), loading = ref(true);
const phone = ref(''), phoneError = ref(''), formError = ref('');
const activationId = ref(''), code = ref(''), password = ref('');
const codeError = ref(''), passwordError = ref(''), sending = ref(false), remaining = ref(0);
let timer: ReturnType<typeof setInterval> | undefined;
const loadError = ref(''), actionError = ref('');
type DialogMode = 'create' | 'permissions' | 'activate';
const mode = ref<DialogMode>('create');
const dialogTitle = computed(() => ({ create: '创建子账户', permissions: '设置权限', activate: '激活子账户' })[mode.value]);
const dialogBusy = computed(() => busy.value || sending.value);
const editing = ref<WorkspaceChild>();
const roles = Object.keys(childRoleDescriptions) as ChildRole[];
const selectedRoles = ref<ChildRole[]>([]);
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
  if (dialogBusy.value || !row || !selected.length) return;
  busy.value = true;
  formError.value = '';
  try {
    if (row.status === 'PENDING') await changeChildInviteRole(row.id, selected);
    else await changeChild(row.id, { roles: selected });
    dialog.value = false;
    await load();
  } catch (error) {
    const failure = requestFailure(error, { method: 'PUT' });
    formError.value = failure.message;
    if (row.status === 'PENDING' && failure.code === 1911) await load();
  } finally { busy.value = false; }
}
async function sendCode() {
  if (dialogBusy.value || remaining.value) return;
  const creatingInvite = mode.value === 'create' && !activationId.value;
  const number = phone.value.trim();
  const selected = [...selectedRoles.value];
  if (creatingInvite) {
    if (!isCnMobile(number)) { phoneError.value = '请输入正确的手机号'; return; }
    if (!selected.length) return;
  } else if (!activationId.value) return;
  sending.value = true;
  codeError.value = '';
  formError.value = '';
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
    remaining.value = result.cooldownSec;
    clearInterval(timer);
    if (remaining.value > 0) {
      const end = Date.now() + remaining.value * 1000;
      timer = setInterval(() => {
        remaining.value = Math.max(0, Math.ceil((end - Date.now()) / 1000));
        if (remaining.value === 0) clearInterval(timer);
      }, 250);
    }
    ElMessage.success('验证码已发送');
  } catch (error) {
    if (dialog.value && sending.value) {
      const failure = requestFailure(error, { method: 'POST' });
      if (creatingInvite && !activationId.value && [1001, 1911].includes(failure.code ?? 0)) phoneError.value = failure.message;
      else codeError.value = failure.message;
    }
  } finally {
    if (creatingInvite && dialog.value && sending.value) await load();
    sending.value = false;
  }
}
async function activate() {
  const id = activationId.value;
  const selected = [...selectedRoles.value];
  if (dialogBusy.value || !id || (mode.value === 'create' && !selected.length)) return;
  const smsCode = code.value, initialPassword = password.value;
  if (!/^\d{6}$/.test(smsCode)) { codeError.value = '请输入 6 位验证码'; return; }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(initialPassword)) { passwordError.value = '密码须为 8–20 位且同时包含字母和数字'; return; }
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
.team-phone-input :deep(.el-input__inner:focus-visible) { box-shadow: none; }
.team-permissions :deep(:where(.el-checkbox__inner)) { border: 1px solid var(--el-color-primary-light-3); }
.team-permissions :deep(.el-checkbox__inner::after) { border-width: 0 1px 1px 0; }
.code-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.quota, .role-description { color: #676b84; font-size: 14px; }
.role-description { margin: 0 0 12px; }
.load-error, .action-error { color: #c23c45; font-size: 14px; }
</style>
