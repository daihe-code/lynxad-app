import { apiDelete, apiGet, apiPost } from "@/api/http";

export interface AwemeVo {
  awemeId: string;
  name: string;
  avatar?: string;
  type: string;
  isPrimary: boolean;
  sceneHint?: string;
  authTypes?: string[];
  isAllowMall?: boolean | null;
}

export interface AccountVo {
  accountId: string;
  advertiserId: string;
  accountName: string;
  platform: string;
  status: string;
  awemes: AwemeVo[];
  authorizedByUserId?: string | null;
  authorizedAt?: string | null;
  tokenExpiresAt?: string | null;
  refreshExpiresAt?: string | null;
  applicationId?: string | null;
  credentialSource?: 'LIVE' | 'LEGACY' | 'MOCK';
  catalogError?: string | null;
}

export function listAccounts() {
  return apiGet<AccountVo[]>("/api/v1/identity/oauth/accounts");
}

export function mockBind() {
  return apiPost<AccountVo>("/api/v1/identity/oauth/accounts/mock-bind");
}

export function oauthUrl(platform = "QIANCHUAN", returnPath = '/campaigns') {
  return apiGet<{ url: string }>("/api/v1/identity/oauth/url", { params: { platform, returnPath } });
}

export interface AuthorizationSession {
  sessionId: string;
  platform: 'QIANCHUAN';
  status: 'SELECTING' | 'COMPLETED' | 'DISCOVERY_FAILED' | 'FAILED' | 'DISCOVERING';
  accounts: { advertiserId: string; accountName: string; accountRole: string; parentAccountId?: string }[];
  boundAccountIds: string[];
  accountLimit: number;
  returnPath: string;
  message?: string;
}
export function authorizationSession(id: string) { return apiGet<AuthorizationSession>(`/api/v1/identity/oauth/sessions/${encodeURIComponent(id)}`); }
export function retryAuthorizationSession(id: string) { return apiPost<AuthorizationSession>(`/api/v1/identity/oauth/sessions/${encodeURIComponent(id)}/retry`); }
export function bindAuthorizationAccounts(id: string, advertiserIds: string[]) { return apiPost<AccountVo[]>(`/api/v1/identity/oauth/sessions/${encodeURIComponent(id)}/bind`, { advertiserIds }); }

export function refreshAccount(id: string) {
  return apiPost<AccountVo>(`/api/v1/identity/accounts/${id}/refresh`);
}
export interface AccountAuthorizationDetail {
  account: AccountVo;
  tenantId: string;
  appId?: string;
  accountRole?: string;
  parentAccountId?: string;
  authorizedByName?: string;
  accessTokenStored: boolean;
  refreshTokenStored: boolean;
}
export function accountAuthorizationDetail(id: string) {
  return apiGet<AccountAuthorizationDetail>(`/api/v1/identity/accounts/${encodeURIComponent(id)}`);
}

export function unbindAccount(id: string) {
  return apiDelete<void>(`/api/v1/identity/accounts/${id}`);
}

/** Removes an inactive authorization record while retaining historical references. */
export function removeAccount(id: string) {
  return apiPost<void>(`/api/v1/identity/accounts/${id}/remove`);
}

function isAuthorizedQianchuanAccount(account: AccountVo): boolean {
  return account.platform === "QIANCHUAN"
    && account.credentialSource !== 'MOCK'
    && account.status === "ACTIVE"
    && (!account.refreshExpiresAt || Date.parse(account.refreshExpiresAt) > Date.now());
}

export function hasQianchuanAuthorization(accounts: AccountVo[] | undefined): boolean {
  return (accounts ?? []).some(isAuthorizedQianchuanAccount);
}

export function hasUsableAweme(accounts: AccountVo[] | undefined) {
  return (accounts ?? []).some((a) => isAuthorizedQianchuanAccount(a) && a.awemes?.length);
}

/** Authorization takes priority over any destination supplied by a login entry. */
export async function loginLandingPath(): Promise<"/campaigns" | "/authorization"> {
  try {
    return hasQianchuanAuthorization(await listAccounts()) ? "/campaigns" : "/authorization";
  } catch {
    return "/authorization";
  }
}
