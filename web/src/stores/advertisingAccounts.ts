import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';
import { listAccounts, type AccountVo } from '@/features/identity/oauthApi';
import { campaignAccountPreferenceKey, resolveCampaignAccount } from '@/features/delivery/campaignAccountPreference';
import { useUserStore } from './user';

export const useAdvertisingAccountsStore = defineStore('advertisingAccounts', () => {
  const user = useUserStore();
  const accounts = ref<AccountVo[]>([]), accountId = ref(''), ready = ref(false), loading = ref(false), error = ref('');
  const preferenceKey = computed(() => campaignAccountPreferenceKey(user.me?.tenantId, user.me?.userId));
  const selectedAccount = computed(() => accounts.value.find(account => account.accountId === accountId.value));
  let version = 0, pending: Promise<void> | undefined;
  watch(preferenceKey, () => {
    version++; pending = undefined; accounts.value = []; accountId.value = ''; ready.value = false; loading.value = false; error.value = '';
  }, { flush: 'sync' });

  async function load() {
    if (!preferenceKey.value) return;
    if (pending) return pending;
    const request = ++version, key = preferenceKey.value;
    loading.value = true; error.value = '';
    pending = (async () => {
      try {
        const result = await listAccounts();
        if (request !== version) return;
        const available = result.filter(account => account.platform === 'QIANCHUAN' && account.credentialSource !== 'MOCK');
        let remembered: string | null = ready.value ? accountId.value : null;
        if (!ready.value) { try { remembered = localStorage.getItem(key); } catch { /* In-memory selection remains usable without storage. */ } }
        accounts.value = available;
        accountId.value = resolveCampaignAccount(available, user.me?.userId, remembered);
        ready.value = true;
      } catch { if (request === version) error.value = '广告账户加载失败，请重试'; }
      finally { if (request === version) { loading.value = false; pending = undefined; } }
    })();
    return pending;
  }
  function selectAccount(value: string) {
    if (!ready.value || value !== '' && !accounts.value.some(account => account.accountId === value)) return;
    accountId.value = value;
    try { if (preferenceKey.value) localStorage.setItem(preferenceKey.value, value); } catch { /* Keep this session's selection when storage is unavailable. */ }
  }
  return { accounts, accountId, selectedAccount, ready, loading, error, preferenceKey, load, selectAccount };
});
