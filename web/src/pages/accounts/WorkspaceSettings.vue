<template>
  <div v-loading="loading" class="workspace-settings">
    <template v-if="overview">
      <div class="workspace-heading"><h2>{{ overview.name }}</h2><el-tag>{{ workspaceAccountLabel(overview.role) }}</el-tag><span>千川账户 {{ overview.accountsUsed }} / {{ overview.accountLimit }}</span></div>
      <WorkspaceRecharge v-if="overview.role === 'OWNER'" @paid="load" />
      <section v-if="overview.wallet" class="wallet-grid" aria-label="工作空间积分">
        <div><span>可用积分</span><strong>{{ number(overview.wallet.available) }}</strong></div>
        <div><span>预占积分</span><strong>{{ number(overview.wallet.frozen) }}</strong></div>
        <div><span>已消耗积分</span><strong>{{ number(overview.wallet.consumed) }}</strong></div>
      </section>
      <WorkspaceTeam v-if="overview.role === 'OWNER'" />
      <div class="section-heading"><h3>客户分组</h3><el-button v-if="canManage" text type="primary" @click="editClient()">新增分组</el-button></div>
      <div class="client-groups"><span v-for="client in overview.clientSpaces" :key="client.id"><b>{{ client.name }}</b><template v-if="canManage"><el-button text size="small" @click="editClient(client)">重命名</el-button><el-button v-if="!client.is_default" text size="small" type="danger" @click="removeClient(client)">删除</el-button></template></span></div>
      <h3>账户绑定</h3>
      <el-table :data="accounts" empty-text="暂无绑定的广告账户">
        <el-table-column prop="account_name" label="广告账户" min-width="150" />
        <el-table-column prop="advertiser_id" label="广告账户 ID" min-width="180" />
        <el-table-column label="绑定状态" width="110"><template #default="{ row }">{{ bindingStatus[row.status] || row.status }}</template></el-table-column>
        <el-table-column label="授权" width="110"><template #default="{ row }">{{ row.authorization_status === 'ACTIVE' ? '已授权' : '未生效' }}</template></el-table-column>
        <el-table-column prop="authorization_paths" label="授权路径" width="95" />
        <el-table-column label="客户分组" min-width="170"><template #default="{ row }"><el-select v-if="row.bound_user_id === user.me?.userId && row.status === 'ACTIVE'" :model-value="row.client_space_id" @change="(id: string) => assign(row, id)"><el-option v-for="client in overview.clientSpaces" :key="client.id" :label="client.name" :value="client.id" /></el-select><span v-else>{{ overview.clientSpaces.find(c => c.id === row.client_space_id)?.name || '—' }}</span></template></el-table-column>
      </el-table>
      <template v-if="overview.wallet"><h3>积分流水</h3><el-table :data="entries" empty-text="暂无积分记录"><el-table-column prop="created_at" label="时间" min-width="180" /><el-table-column label="类型"><template #default="{ row }">{{ entryTypes[row.event_type] || row.event_type }}</template></el-table-column><el-table-column label="积分"><template #default="{ row }">{{ number(row.amount) }}</template></el-table-column></el-table><div class="ledger-pages"><el-button :disabled="page === 1" @click="loadEntries(page - 1)">上一页</el-button><span>{{ page }}</span><el-button :disabled="entries.length < 20" @click="loadEntries(page + 1)">下一页</el-button></div></template>
    </template>
    <el-button v-else-if="!loading" @click="load">重新加载</el-button>
    <el-dialog v-model="clientDialog" :title="editing ? '重命名分组' : '新增分组'" width="420px"><el-input v-model="clientName" maxlength="128" placeholder="分组名称" /><p v-if="clientError" class="field-error">{{ clientError }}</p><template #footer><el-button @click="clientDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveClient">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores/user';
import WorkspaceTeam from './WorkspaceTeam.vue';
import WorkspaceRecharge from './WorkspaceRecharge.vue';
import { workspaceOverview, workspaceAccounts, pointEntries, saveClientSpace, deleteClientSpace, assignClientSpace, workspaceAccountLabel, type WorkspaceOverview, type AccountBinding, type ClientSpace, type PointEntry } from '@/features/tenant/workspaceApi';
const user = useUserStore(), overview = ref<WorkspaceOverview>(), accounts = ref<AccountBinding[]>([]), entries = ref<PointEntry[]>([]), page = ref(1), loading = ref(true);
const canManage = computed(() => ['OWNER', 'ADMIN'].includes(overview.value?.role || ''));
const bindingStatus: Record<string, string> = { ACTIVE: '已绑定', REVIEW_REQUIRED: '待核验', RELEASED: '已解绑' };
const entryTypes: Record<string, string> = { CREDIT: '入账', RESERVE: '预占', SETTLE: '结算', RELEASE: '释放', REFUND: '退回' };
const number = (value: number) => Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 });
async function loadEntries(next: number) { try { entries.value = await pointEntries(next); page.value = next; } catch { /* Keep current page on failure. */ } }
async function load() { loading.value = true; try { overview.value = await workspaceOverview(); accounts.value = await workspaceAccounts(); if (overview.value.wallet) await loadEntries(1); } finally { loading.value = false; } }
const clientDialog = ref(false), clientName = ref(''), clientError = ref(''), editing = ref<string>(), saving = ref(false);
function editClient(client?: ClientSpace) { editing.value = client?.id; clientName.value = client?.name || ''; clientError.value = ''; clientDialog.value = true; }
async function saveClient() { if (!clientName.value.trim()) { clientError.value = '请输入分组名称'; return; } saving.value = true; try { await saveClientSpace(clientName.value.trim(), editing.value); clientDialog.value = false; await load(); } finally { saving.value = false; } }
async function removeClient(client: ClientSpace) { try { await ElMessageBox.confirm(`删除分组“${client.name}”？`, '删除分组', { type: 'warning' }); } catch { return; } await deleteClientSpace(client.id); await load(); ElMessage.success({ message: '删除成功', duration: 3000 }); }
async function assign(account: AccountBinding, client: string) { await assignClientSpace(account.id, client); account.client_space_id = client; }
onMounted(() => { void load().catch(() => {}); });
</script>
<style scoped>
.workspace-settings{color:#22243d}.workspace-heading,.section-heading{display:flex;align-items:center;gap:14px}.workspace-heading h2{font-size:20px;margin:0}.workspace-heading>span:last-child{margin-left:auto;color:#777b95;font-size:14px}.wallet-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin:24px 0}.wallet-grid>div{padding:20px;border-radius:14px;background:#f5f3ff;display:grid;gap:12px}.wallet-grid span{color:#777b95;font-size:14px}.wallet-grid strong{font-size:25px}.section-heading{justify-content:space-between}.client-groups{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:28px}.client-groups>span{border:1px solid #e2e2f2;border-radius:10px;padding:8px 12px}.client-groups b{font-size:14px;font-weight:500}.ledger-pages{display:flex;justify-content:flex-end;align-items:center;gap:14px;margin-top:16px}h3{font-size:16px;margin-top:28px}.field-error{font-size:13px;color:#f56c6c}@media(max-width:700px){.wallet-grid{grid-template-columns:1fr}.workspace-heading{flex-wrap:wrap}.workspace-heading>span:last-child{margin-left:0}}
</style>
