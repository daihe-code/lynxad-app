<template>
  <section v-loading="loading" class="workspace-team" aria-label="子账户列表">
    <div class="heading">
      <span v-if="team" class="quota">
        已用名额 {{ team.used }} / {{ team.limit }}
        <span v-if="team.limit === 0"> · 当前套餐无子账户名额</span>
      </span>
      <el-button type="primary" :disabled="!team || team.used >= team.limit || actionsDisabled" @click="open()">创建子账户</el-button>
    </div>
    <div v-if="loadError" class="load-error" role="status">
      {{ loadError }} <el-button text type="primary" :disabled="loading" @click="load">重试</el-button>
    </div>
    <el-table v-if="team" :data="[...team.members, ...team.invites]" row-key="id" empty-text="暂无子账户">
      <el-table-column prop="phone" label="手机号" min-width="125" />
      <el-table-column label="权限" min-width="130">
        <template #default="{ row }">{{ workspaceRoles[row.role] || row.role }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }">{{ status[row.status] || row.status }}</template></el-table-column>
      <el-table-column label="操作" width="190">
        <template #default="{ row }">
          <el-button text type="primary" :disabled="actionsDisabled" @click="open(row)">设置权限</el-button>
          <el-button v-if="row.status === 'PENDING'" text :disabled="actionsDisabled" @click="revoke(row)">撤销邀请</el-button>
          <el-button v-else text :disabled="actionsDisabled" @click="toggle(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
    <el-dialog v-model="dialog" :title="editing ? '设置权限' : link ? '子账户激活链接' : '创建子账户'"
      width="min(440px, 92vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
      <el-form v-if="!link" label-position="top" @submit.prevent="save">
        <el-form-item label="手机号" :error="phoneError">
          <el-input v-model="phone" type="tel" maxlength="11" autocomplete="off" :readonly="!!editing" :disabled="busy" @input="phoneError = ''" />
        </el-form-item>
        <el-form-item label="权限" :error="formError">
          <el-radio-group v-model="role" :disabled="busy" @change="formError = ''">
            <el-radio v-for="option in roles" :key="option" :value="option">{{ workspaceRoles[option] }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <p v-if="role" class="role-description">{{ childRoleDescriptions[role] }}</p>
      </el-form>
      <template v-else>
        <p class="role-description">请将链接交给子账户，用对应手机号验证激活。</p>
        <el-input :model-value="link" aria-label="激活链接" readonly />
      </template>
      <template #footer>
        <el-button :disabled="busy" @click="dialog = false">{{ link ? '关闭' : '取消' }}</el-button>
        <el-button type="primary" :loading="busy" :disabled="!link && !role" @click="link ? copy() : save()">{{ link ? '复制激活链接' : editing ? '保存权限' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { requestFailure } from '@/api/apiErrors';
import { isCnMobile } from '@/utils/phone';
import { workspaceTeam, inviteChild, changeChild, changeChildInviteRole, revokeChildInvite, workspaceRoles, childRoleDescriptions, type ChildRole, type WorkspaceChild, type WorkspaceTeam } from '@/features/tenant/workspaceApi';

const router = useRouter();
const team = ref<WorkspaceTeam>();
const dialog = ref(false), busy = ref(false), loading = ref(true);
const phone = ref(''), phoneError = ref(''), formError = ref(''), link = ref('');
const loadError = ref(''), actionError = ref('');
const editing = ref<WorkspaceChild>();
const roles = Object.keys(childRoleDescriptions) as ChildRole[];
const role = ref<ChildRole>();
const actionsDisabled = computed(() => busy.value || loading.value || !!loadError.value);
const status: Record<string, string> = { ACTIVE: '已启用', DISABLED: '已停用', PENDING: '待激活' };
async function load() {
  loading.value = true;
  loadError.value = '';
  try { team.value = await workspaceTeam(); }
  catch (error) { loadError.value = requestFailure(error).message; }
  finally { loading.value = false; }
}
function open(row?: WorkspaceChild) {
  editing.value = row;
  phone.value = row?.phone || '';
  role.value = row ? roles.find(value => value === row.role) : 'OPERATOR';
  link.value = '';
  phoneError.value = '';
  formError.value = '';
  actionError.value = '';
  dialog.value = true;
}
async function save() {
  const selectedRole = role.value;
  if (busy.value || link.value || !selectedRole) return;
  const row = editing.value;
  if (!row && !isCnMobile(phone.value.trim())) { phoneError.value = '请输入正确的手机号'; return; }
  busy.value = true;
  try {
    if (row) {
      if (row.status === 'PENDING') await changeChildInviteRole(row.id, selectedRole);
      else await changeChild(row.id, { role: selectedRole });
      dialog.value = false;
    } else {
      const result = await inviteChild(phone.value.trim(), selectedRole);
      link.value = new URL(router.resolve({ path: '/login', hash: '#invite=' + encodeURIComponent(result.token) }).href, window.location.origin).href;
    }
    await load();
  } catch (error) {
    const failure = requestFailure(error, { method: row ? 'PUT' : 'POST' });
    if (!row && [1001, 1911].includes(failure.code ?? 0)) phoneError.value = failure.message;
    else formError.value = failure.message;
    if (failure.code === 1910 || (row?.status === 'PENDING' && failure.code === 1911)) await load();
  } finally { busy.value = false; }
}
async function copy() {
  try { await navigator.clipboard.writeText(link.value); ElMessage.success('激活链接已复制'); }
  catch { ElMessage.info('请手动复制激活链接'); }
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
</script>

<style scoped>
.workspace-team { min-height: 160px; }
.heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.heading .el-button { margin-left: auto; }
.quota, .role-description { color: #676b84; font-size: 14px; }
.role-description { margin: 0 0 12px; }
.load-error, .action-error { color: #c23c45; font-size: 14px; }
</style>
