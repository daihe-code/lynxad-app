import { apiGet, apiPost, apiPut, apiDelete } from '@/api/http';

export interface Workspace { tenantId: string; name: string; membershipId: string; role: string }
export interface ClientSpace { id: string; name: string; is_default: boolean }
export interface Wallet { available: number; frozen: number; consumed: number }
export interface WorkspaceOverview extends Workspace { accountLimit: number; accountsUsed: number; clientSpaces: ClientSpace[]; wallet?: Wallet }
export interface AccountBinding { id: string; bound_user_id: string; client_space_id: string; status: string; advertiser_id: string; account_name: string; authorization_status: string; authorization_paths: number }
export interface PointEntry { event_type: string; amount: number; binding_id?: string; created_at: string }
const base = '/api/v1/tenant/workspaces';
export const listWorkspaces = () => apiGet<Workspace[]>(base);
export const switchWorkspace = (id: string) => apiPost<{ token: string }>(`${base}/${encodeURIComponent(id)}/switch`, {});
export const workspaceOverview = () => apiGet<WorkspaceOverview>(`${base}/current`);
export const workspaceAccounts = () => apiGet<AccountBinding[]>(`${base}/current/accounts`);
export const pointEntries = (page: number) => apiGet<PointEntry[]>(`${base}/current/points`, { params: { page, pageSize: 20 } });
export const saveClientSpace = (name: string, id?: string) => id ? apiPut(`${base}/current/clients/${encodeURIComponent(id)}`, { name }) : apiPost(`${base}/current/clients`, { name });
export const deleteClientSpace = (id: string) => apiDelete(`${base}/current/clients/${encodeURIComponent(id)}`);
export const assignClientSpace = (id: string, clientSpaceId: string) => apiPut(`${base}/current/accounts/${encodeURIComponent(id)}/client`, { clientSpaceId });
export const workspaceRoles: Record<string, string> = { OWNER: '所有者', ADMIN: '管理员', FINANCE: '财务', OPERATOR: '运营', MEMBER: '成员', VIEWER: '查看者' };
