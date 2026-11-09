<template>
  <section v-if="team">
    <div class="heading">
      <h3>子账户 <small>{{ team.used }} / {{ team.limit }}</small></h3>
      <el-button type="primary" :disabled="team.used >= team.limit" @click="open">创建子账户</el-button>
    </div>
    <el-table :data="[...team.members, ...team.invites]" empty-text="暂无子账户">
      <el-table-column prop="phone" label="手机号" min-width="125" />
      <el-table-column label="权限" min-width="130">
        <template #default="{ row }">
          <el-select v-if="team.members.includes(row) && roles.includes(row.role)" :model-value="row.role" :disabled="busy"
            aria-label="子账户权限" @change="(value: string) => update(row, value, row.status === 'ACTIVE')">
            <el-option v-for="option in roles" :key="option" :label="workspaceRoles[option]" :value="option" />
          </el-select>
          <span v-else>{{ workspaceRoles[row.role] || row.role }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }">{{ status[row.status] || row.status }}</template></el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" text :disabled="busy" @click="revoke(row)">撤销邀请</el-button>
          <el-button v-else-if="team.members.includes(row)" text :disabled="busy" @click="update(row, row.role, row.status !== 'ACTIVE')">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="创建子账户" width="min(440px, 92vw)" :close-on-click-modal="false">
      <el-form v-if="!link" label-position="top" @submit.prevent="invite">
        <el-form-item label="手机号" :error="phoneError"><el-input v-model="phone" type="tel" maxlength="11" autocomplete="off" @input="phoneError = ''" /></el-form-item>
        <el-form-item label="权限"><el-select v-model="role"><el-option v-for="option in roles" :key="option" :label="workspaceRoles[option]" :value="option" /></el-select></el-form-item>
      </el-form>
      <el-input v-else :model-value="link" aria-label="激活链接" readonly />
      <template #footer>
        <el-button @click="dialog = false">{{ link ? '关闭' : '取消' }}</el-button>
        <el-button type="primary" :loading="busy" @click="link ? copy() : invite()">{{ link ? '复制激活链接' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { isCnMobile } from '@/utils/phone';
import { workspaceTeam, inviteChild, changeChild, revokeChildInvite, workspaceRoles, type ChildRole, type WorkspaceChild, type WorkspaceTeam } from '@/features/tenant/workspaceApi';

const router = useRouter();
const team = ref<WorkspaceTeam>();
const dialog = ref(false), busy = ref(false), phone = ref(''), phoneError = ref(''), link = ref('');
const roles: ChildRole[] = ['OPERATOR', 'FINANCE', 'VIEWER'];
const role = ref<ChildRole>('OPERATOR');
const status: Record<string, string> = { ACTIVE: '已启用', DISABLED: '已停用', PENDING: '待激活' };
async function load() { team.value = await workspaceTeam(); }
function open() { phone.value = ''; role.value = 'OPERATOR'; link.value = ''; phoneError.value = ''; dialog.value = true; }
async function invite() {
  if (busy.value || link.value) return;
  if (!isCnMobile(phone.value.trim())) { phoneError.value = '请输入正确的手机号'; return; }
  busy.value = true;
  try {
    const result = await inviteChild(phone.value.trim(), role.value);
    link.value = new URL(router.resolve({ path: '/login', hash: '#invite=' + encodeURIComponent(result.token) }).href, window.location.origin).href;
    await load();
  } catch { /* API shows the failure. */ } finally { busy.value = false; }
}
async function copy() {
  try { await navigator.clipboard.writeText(link.value); ElMessage.success('激活链接已复制'); }
  catch { ElMessage.info('请手动复制激活链接'); }
}
async function update(row: WorkspaceChild, nextRole: string, active: boolean) {
  if (busy.value) return;
  if (!active && row.status === 'ACTIVE') {
    try { await ElMessageBox.confirm(`停用子账户 ${row.phone}？`, '停用子账户', { type: 'warning' }); } catch { return; }
  }
  busy.value = true;
  try { await changeChild(row.id, nextRole, active); await load(); } catch { /* API shows the failure. */ }
  finally { busy.value = false; }
}
async function revoke(row: WorkspaceChild) {
  if (busy.value) return;
  busy.value = true;
  try { await revokeChildInvite(row.id); await load(); } catch { /* API shows the failure. */ }
  finally { busy.value = false; }
}
onMounted(() => { void load().catch(() => {}); });
</script>

<style scoped>
.heading{display:flex;align-items:center;justify-content:space-between;margin-top:20px}.heading h3{font-size:16px}.heading small{margin-left:8px;color:#777b95;font-weight:400}
</style>
