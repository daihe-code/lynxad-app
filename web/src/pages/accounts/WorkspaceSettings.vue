<template>
  <div v-loading="loading" class="workspace-settings">
    <template v-if="overview">
      <section class="acct-card workspace-overview" aria-labelledby="workspace-name">
        <header class="workspace-heading">
          <span class="workspace-mark"><AccountIcon name="grid" /></span>
          <div class="workspace-identity">
            <h2 id="workspace-name">{{ overview.name }}</h2>
            <el-tag v-if="roleLabel" class="workspace-role" effect="light" size="small">{{ roleLabel }}</el-tag>
          </div>
          <div class="workspace-quota">
            <AccountIcon name="douyin" />
            <span>千川账户</span>
            <strong>{{ overview.accountsUsed }} / {{ overview.accountLimit }}</strong>
          </div>
        </header>
        <section v-if="overview.wallet || isOwner" class="wallet-grid" aria-label="工作空间积分">
          <div class="wallet-available">
            <div class="wallet-value">
              <span><AccountIcon name="wallet" />可用积分</span>
              <strong>{{ overview.wallet ? number(overview.wallet.available) : '—' }}</strong>
            </div>
            <router-link v-if="isOwner" :to="pointRechargePath" class="workspace-recharge-link">
              <AccountIcon name="plus" />积分充值
            </router-link>
          </div>
          <dl v-if="overview.wallet" class="wallet-usage">
            <div><dt>预占积分</dt><dd>{{ number(overview.wallet.frozen) }}</dd></div>
            <div><dt>已消耗积分</dt><dd>{{ number(overview.wallet.consumed) }}</dd></div>
          </dl>
        </section>
        <el-alert v-if="pointsWarning" class="wallet-warning" :title="pointsWarning" type="warning" show-icon :closable="false" />
      </section>

      <section class="acct-card" aria-labelledby="workspace-groups-heading">
        <header class="section-heading">
          <h2 id="workspace-groups-heading" class="acct-card__t">客户分组</h2>
          <button v-if="canManage" type="button" class="workspace-add-group" @click="editClient()">
            <AccountIcon name="plus" />新增分组
          </button>
        </header>
        <ul v-if="overview.clientSpaces.length" class="client-groups">
          <li v-for="client in overview.clientSpaces" :key="client.id" class="client-group">
            <div class="client-group-title">
              <span class="group-mark"><AccountIcon name="folder" /></span>
              <strong>{{ client.name }}</strong>
              <span v-if="client.is_default" class="group-default">默认</span>
            </div>
            <div v-if="canManage" class="group-actions">
              <button type="button" @click="editClient(client)">重命名</button>
              <button v-if="!client.is_default" type="button" class="group-remove" @click="removeClient(client)">删除</button>
            </div>
          </li>
        </ul>
        <p v-else class="workspace-empty">暂无客户分组</p>
      </section>

      <section class="acct-card" aria-labelledby="workspace-bindings-heading">
        <header class="section-heading"><h2 id="workspace-bindings-heading" class="acct-card__t">账户绑定</h2></header>
        <el-table :data="accounts" row-key="id" class="account-bindings" empty-text="暂无绑定的广告账户">
          <el-table-column label="广告账户" min-width="180">
            <template #default="{ row }">
              <strong class="binding-name">{{ row.account_name }}</strong>
              <span class="binding-id">ID {{ row.advertiser_id }}</span>
            </template>
          </el-table-column>
          <el-table-column label="绑定状态" min-width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="light" :type="bindingStatus[row.status]?.type || 'info'">
                {{ bindingStatus[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="授权" min-width="90">
            <template #default="{ row }">
              <el-tag size="small" effect="light" :type="row.authorization_status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.authorization_status === 'ACTIVE' ? '已授权' : '未生效' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="authorization_paths" label="授权路径" min-width="90" />
          <el-table-column label="客户分组" min-width="145">
            <template #default="{ row }">
              <el-select v-if="row.bound_user_id === user.me?.userId && row.status === 'ACTIVE'" :model-value="row.client_space_id"
                :aria-label="`${row.account_name}的客户分组`" @change="(id: string) => assign(row, id)">
                <el-option v-for="client in overview.clientSpaces" :key="client.id" :label="client.name" :value="client.id" />
              </el-select>
              <span v-else>{{ overview.clientSpaces.find(client => client.id === row.client_space_id)?.name || '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="overview.wallet" class="acct-card workspace-ledger">
        <header class="section-heading"><h2 class="acct-card__t">积分流水</h2></header>
        <PointLedgerTable :key="overview.tenantId" :fetch-page="workspacePointHistory" :refresh-key="ledgerRefresh" show-date-filter />
      </section>
    </template>
    <el-button v-else-if="!loading" @click="load">重新加载</el-button>
    <el-dialog v-model="clientDialog" :title="editing ? '重命名分组' : '新增分组'" width="min(420px, calc(100vw - 32px))">
      <el-input v-model="clientName" maxlength="128" placeholder="分组名称" aria-label="分组名称" />
      <p v-if="clientError" class="field-error" role="alert">{{ clientError }}</p>
      <template #footer>
        <el-button @click="clientDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveClient">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores/user';
import PointLedgerTable from '@/components/PointLedgerTable.vue';
import AccountIcon from './AccountIcon.vue';
import { pointRechargePath } from '@/features/tenant/accountNavigation';
import { getGenerationAccess, pointsBalanceWarning } from '@/features/billing/billingApi';
import { workspaceOverview, workspaceAccounts, workspacePointHistory, saveClientSpace, deleteClientSpace, assignClientSpace, workspaceAccountLabel, type WorkspaceOverview, type AccountBinding, type ClientSpace } from '@/features/tenant/workspaceApi';
const user = useUserStore(), overview = ref<WorkspaceOverview>(), accounts = ref<AccountBinding[]>([]), loading = ref(true);
const ledgerRefresh = ref(0);
const isOwner = computed(() => overview.value?.role === 'OWNER');
const roleLabel = computed(() => workspaceAccountLabel(overview.value?.role));
const canManage = computed(() => ['OWNER', 'ADMIN'].includes(overview.value?.role || ''));
const bindingStatus: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
  ACTIVE: { label: '已绑定', type: 'success' },
  REVIEW_REQUIRED: { label: '待核验', type: 'warning' },
  RELEASED: { label: '已解绑', type: 'info' },
};
const number = (value: number) => Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 });
const pointsWarning = ref('');
async function load() {
  loading.value = true;
  try {
    const next = await workspaceOverview();
    if (overview.value?.tenantId === next.tenantId) ledgerRefresh.value++;
    overview.value = next;
    if (overview.value.wallet) {
      const access = await getGenerationAccess().catch(() => undefined);
      pointsWarning.value = pointsBalanceWarning(access);
    } else {
      pointsWarning.value = '';
    }
    accounts.value = await workspaceAccounts();
  } finally {
    loading.value = false;
  }
}
const clientDialog = ref(false), clientName = ref(''), clientError = ref(''), editing = ref<string>(), saving = ref(false);
function editClient(client?: ClientSpace) { editing.value = client?.id; clientName.value = client?.name || ''; clientError.value = ''; clientDialog.value = true; }
async function saveClient() { if (!clientName.value.trim()) { clientError.value = '请输入分组名称'; return; } saving.value = true; try { await saveClientSpace(clientName.value.trim(), editing.value); clientDialog.value = false; await load(); } finally { saving.value = false; } }
async function removeClient(client: ClientSpace) { try { await ElMessageBox.confirm(`删除分组“${client.name}”？`, '删除分组', { type: 'warning' }); } catch { return; } await deleteClientSpace(client.id); await load(); ElMessage.success({ message: '删除成功', duration: 3000 }); }
async function assign(account: AccountBinding, client: string) { await assignClientSpace(account.id, client); account.client_space_id = client; }
onMounted(() => { void load().catch(() => {}); });
</script>
<style scoped>
.workspace-settings { min-width: 0; color: rgb(var(--c-text-1)); }
.workspace-heading, .section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.workspace-heading { flex-wrap: wrap; }
.workspace-mark, .group-mark {
  display: inline-grid; flex: none; place-items: center; color: rgb(var(--c-primary-ink));
  background: rgb(var(--c-primary-soft)); border-radius: 12px;
}
.workspace-mark { width: 44px; height: 44px; }
.workspace-mark svg { width: 22px; height: 22px; }
.workspace-identity { display: flex; flex: 1 1 140px; align-items: center; flex-wrap: wrap; min-width: 0; gap: 8px; }
.workspace-identity h2 { margin: 0; font-size: 21px; font-weight: 700; line-height: 1.3; overflow-wrap: anywhere; }
.workspace-role { border: 0; border-radius: 6px; }
.workspace-quota {
  display: inline-flex; flex: none; align-items: center; gap: 7px; padding: 8px 10px;
  border: 1px solid rgb(var(--c-border)); border-radius: 10px; color: rgb(var(--c-text-3)); font-size: 13px;
}
.workspace-quota svg { width: 16px; height: 16px; color: rgb(var(--c-primary-ink)); }
.workspace-quota strong { color: rgb(var(--c-text-1)); font-variant-numeric: tabular-nums; }
.wallet-grid { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr); gap: 12px; margin-top: 20px; }
.wallet-available {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px;
  padding: 18px; border: 1px solid rgb(var(--c-primary) / .13); border-radius: 14px;
  background: linear-gradient(110deg, #efedff, #f8f7ff);
}
.wallet-available:only-child { grid-column: 1 / -1; }
.wallet-value { display: grid; min-width: 0; gap: 5px; }
.wallet-value span { display: inline-flex; gap: 7px; align-items: center; color: rgb(var(--c-primary-ink)); font-size: 14px; font-weight: 600; }
.wallet-value span svg { width: 16px; height: 16px; }
.wallet-value strong { font-size: 32px; line-height: 1.2; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.workspace-recharge-link {
  display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 44px; padding: 9px 16px;
  border-radius: 10px; background: rgb(var(--c-primary)); color: #fff; font-size: 14px; font-weight: 700;
  box-shadow: 0 4px 10px rgb(var(--c-primary) / .22); white-space: nowrap;
}
.workspace-recharge-link:hover { background: rgb(var(--c-primary-hover)); }
.workspace-recharge-link svg { width: 17px; height: 17px; }
.wallet-usage {
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: center;
  margin: 0; padding: 13px 0; border: 1px solid rgb(var(--c-border)); border-radius: 14px; background: #fafafe;
}
.wallet-usage > div { display: grid; gap: 7px; padding: 4px 15px; }
.wallet-usage > div + div { border-left: 1px solid rgb(var(--c-border)); }
.wallet-usage dt { color: rgb(var(--c-text-3)); font-size: 13px; }
.wallet-usage dd { margin: 0; font-size: 21px; font-weight: 700; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.wallet-warning { margin-top: 14px; }
.section-heading { flex-wrap: wrap; margin-bottom: 16px; }
.section-heading h2 { margin: 0; }
.workspace-add-group {
  display: inline-flex; align-items: center; gap: 5px; min-height: 34px; padding: 5px 10px;
  border: 1px solid rgb(var(--c-border)); border-radius: 8px; color: rgb(var(--c-primary-ink)); font-size: 13px; font-weight: 600;
}
.workspace-add-group:hover { background: rgb(var(--c-primary-soft-3)); }
.workspace-add-group svg { width: 15px; height: 15px; }
.client-groups { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 210px), 1fr)); gap: 12px; margin: 0; padding: 0; list-style: none; }
.client-group { padding: 12px 14px; border: 1px solid rgb(var(--c-border)); border-radius: 12px; background: #fafafe; }
.client-group-title { display: flex; align-items: center; gap: 8px; }
.group-mark { width: 32px; height: 32px; border-radius: 8px; }
.group-mark svg { width: 17px; height: 17px; }
.client-group-title strong { font-size: 14px; font-weight: 600; overflow-wrap: anywhere; }
.group-default { flex: none; padding: 2px 5px; border-radius: 5px; background: rgb(var(--c-primary-soft)); color: rgb(var(--c-primary-ink)); font-size: 11px; }
.group-actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 8px; }
.group-actions button { min-height: 28px; color: rgb(var(--c-primary-ink)); font-size: 13px; }
.group-actions .group-remove { color: var(--el-color-danger); }
.group-actions button:hover { text-decoration: underline; text-underline-offset: 3px; }
.workspace-empty { margin: 0; color: rgb(var(--c-text-3)); font-size: 14px; }
.account-bindings { --el-table-header-bg-color: #f7f8fc; --el-table-border-color: #eceef6; border-radius: 10px; }
.account-bindings :deep(.el-table__cell) { padding-block: 10px; }
.account-bindings :deep(.el-tag--success) { --el-tag-text-color: #1a6b4c; }
.account-bindings :deep(.el-tag--warning) { --el-tag-text-color: #885019; }
.account-bindings :deep(.el-tag--info) { --el-tag-text-color: #616781; }
.binding-name, .binding-id { display: block; overflow-wrap: anywhere; }
.binding-name { color: rgb(var(--c-text-1)); font-weight: 600; }
.binding-id { margin-top: 3px; color: rgb(var(--c-text-3)); font-size: 12px; font-variant-numeric: tabular-nums; }
.workspace-ledger :deep(.ledger-filters .el-select) { width: 178px; }
.workspace-ledger :deep(.ledger-filters .el-date-editor) { flex: 0 1 258px; }
.workspace-ledger :deep(.ledger-filters .el-button) {
  padding-inline: 12px; border: 1px solid rgb(var(--c-border)); border-radius: 8px; font-size: 13px;
}
.workspace-ledger :deep(.ledger-filters .el-button:hover) { color: rgb(var(--c-primary-ink)); }
.field-error { margin-top: 8px; color: var(--el-color-danger); font-size: 13px; }
@media (max-width: 700px) {
  .wallet-grid { grid-template-columns: minmax(0, 1fr); }
  .wallet-value strong { font-size: 27px; }
}
@media (max-width: 400px) {
  .wallet-available { padding: 13px; gap: 8px; }
  .workspace-recharge-link { padding-inline: 11px; }
}
</style>
