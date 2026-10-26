import type { AccountVo } from '../identity/oauthApi';

export function campaignAccountPreferenceKey(tenantId?: string, userId?: string) {
  return tenantId && userId ? `lynx.campaign.account:${JSON.stringify([tenantId, userId])}` : null;
}

export function resolveCampaignAccount(accounts: AccountVo[], userId: string | undefined, remembered: string | null) {
  if (remembered !== null && (remembered === '' || accounts.some(account => account.accountId === remembered))) return remembered;
  const active = accounts.filter(account => account.status === 'ACTIVE');
  return (active.find(account => account.authorizedByUserId === userId) || active[0] || accounts[0])?.accountId || '';
}
