import { apiDelete, apiGet, apiPost } from "@/api/http";

export interface AwemeVo {
  awemeId: string;
  name: string;
  avatar?: string;
  type: string;
  isPrimary: boolean;
  sceneHint?: string;
}

export interface AccountVo {
  accountId: string;
  advertiserId: string;
  accountName: string;
  platform: string;
  status: string;
  awemes: AwemeVo[];
  authorizedByUserId?: string | null;
}

export function listAccounts() {
  return apiGet<AccountVo[]>("/api/v1/identity/oauth/accounts");
}

export function mockBind() {
  return apiPost<AccountVo>("/api/v1/identity/oauth/accounts/mock-bind");
}

export function oauthUrl(platform = "QIANCHUAN") {
  return apiGet<{ url: string }>("/api/v1/identity/oauth/url", { params: { platform } });
}

export function refreshAccount(id: string) {
  return apiPost<AccountVo>(`/api/v1/identity/accounts/${id}/refresh`);
}

export function unbindAccount(id: string) {
  return apiDelete<void>(`/api/v1/identity/accounts/${id}`);
}

export function hasUsableAweme(accounts: AccountVo[] | undefined) {
  return (accounts ?? []).some((a) => a.status === "ACTIVE" && a.awemes?.length);
}
