<template>
  <el-tooltip content="广告账户" :trigger="['hover', 'focus']" :show-after="200" placement="bottom">
    <span class="campaign-account-trigger">
      <el-select :model-value="scope.accountId" class="campaign-account-select" placeholder="全部广告账户" aria-label="广告账户" popper-class="campaign-dropdown campaign-account-dropdown" :fit-input-width="false" placement="bottom-end" :loading="scope.loading" :disabled="!scope.ready" @change="scope.selectAccount">
        <template v-if="scope.selectedAccount" #prefix><span class="campaign-account-avatar campaign-account-avatar--selected" aria-hidden="true">{{ scope.selectedAccount.accountName.slice(0, 1) }}</span></template>
        <el-option label="全部广告账户" value="" />
        <el-option v-for="account in scope.accounts" :key="account.accountId" :label="account.accountName" :value="account.accountId">
          <div class="campaign-account-option"><span class="campaign-account-avatar" aria-hidden="true">{{ account.accountName.slice(0, 1) }}</span><span class="campaign-account-details"><strong>{{ account.accountName }}</strong><small>千川ID：{{ account.advertiserId }}</small></span></div>
        </el-option>
      </el-select>
    </span>
  </el-tooltip>
</template>
<script setup lang="ts">
import { useAdvertisingAccountsStore } from '@/stores/advertisingAccounts';
const scope = useAdvertisingAccountsStore();
</script>
<style scoped>
.campaign-account-trigger { display: inline-flex; }
.campaign-account-select { width: 176px; }
.campaign-account-select :deep(.el-select__wrapper) { min-height: 36px; padding: 6px 10px; border-radius: 8px; background: #f7f7ff; box-shadow: none; font-size: 13px; }
.campaign-account-select :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px #575cf6 inset; }
.campaign-account-option { display: flex; align-items: center; gap: 10px; width: 100%; min-width: 0; }
.campaign-account-avatar { display: grid; place-items: center; flex: none; width: 32px; height: 32px; border-radius: 50%; background: #eeedff; color: #575cf6; font-size: 15px; font-weight: 600; line-height: 1; }
.campaign-account-avatar--selected { width: 24px; height: 24px; font-size: 12px; }
.campaign-account-details { display: grid; flex: 1; gap: 4px; min-width: 0; line-height: 20px; }
.campaign-account-details strong { font-size: 13px; font-weight: 500; }
.campaign-account-details small { color: #7c7f99; font-size: 12px; }
.campaign-account-details strong, .campaign-account-details small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
:global(.campaign-account-dropdown) { min-width: 280px; max-width: calc(100vw - 32px); }
/* Account options have two lines; keep generic single-line dropdown sizing from overriding them. */
:global(.campaign-dropdown.campaign-account-dropdown .el-select-dropdown__item) { height: auto; min-height: 40px; padding: 10px 12px; line-height: 20px; }
@media (max-width: 639px) { .campaign-account-select { width: 128px; } }
@media (max-width: 479px) { .campaign-account-select { width: 112px; } }
</style>
