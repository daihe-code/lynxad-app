import { apiGet, apiPost, apiPut, apiDelete } from '@/api/http';

export interface Workspace { tenantId: string; name: string; membershipId: string; role: string }
export interface ClientSpace { id: string; name: string; is_default: boolean }
export interface Wallet { available: number; frozen: number; consumed: number }
export interface WorkspaceOverview extends Workspace { accountLimit: number; accountsUsed: number; clientSpaces: ClientSpace[]; wallet?: Wallet }
export interface AccountBinding { id: string; bound_user_id: string; client_space_id: string; status: string; advertiser_id: string; account_name: string; authorization_status: string; authorization_paths: number }
export interface PointEntry { event_type: string; amount: number; binding_id?: string; created_at: string }
const base = '/api/v1/tenant/workspaces';
const teamPath = `${base}/current/team`;
export const listWorkspaces = () => apiGet<Workspace[]>(base);
export const switchWorkspace = (id: string) => apiPost<{ token: string }>(`${base}/${encodeURIComponent(id)}/switch`, {});
export const workspaceOverview = () => apiGet<WorkspaceOverview>(`${base}/current`);
export const workspaceAccounts = () => apiGet<AccountBinding[]>(`${base}/current/accounts`);
export const pointEntries = (page: number) => apiGet<PointEntry[]>(`${base}/current/points`, { params: { page, pageSize: 20 } });
export const saveClientSpace = (name: string, id?: string) => id ? apiPut(`${base}/current/clients/${encodeURIComponent(id)}`, { name }) : apiPost(`${base}/current/clients`, { name });
export const deleteClientSpace = (id: string) => apiDelete(`${base}/current/clients/${encodeURIComponent(id)}`);
export const assignClientSpace = (id: string, clientSpaceId: string) => apiPut(`${base}/current/accounts/${encodeURIComponent(id)}/client`, { clientSpaceId });
export const workspaceRoles: Record<string, string> = { OWNER: '主账户', ADMIN: '团队管理员', FINANCE: '财务', OPERATOR: '运营', MEMBER: '成员', VIEWER: '查看者' };
export function workspaceAccountLabel(role?: string): string {
  if (!role || !Object.hasOwn(workspaceRoles, role)) return '';
  return role === 'OWNER' ? workspaceRoles.OWNER : `子账户 · ${workspaceRoles[role]}`;
}
export const childRoleDescriptions = {
  OPERATOR: '绑定千川账户，管理本人负责的投放',
  FINANCE: '查看订阅、账单及积分',
  VIEWER: '查看投放计划与数据',
};
export type ChildRole = keyof typeof childRoleDescriptions;
export interface WorkspaceChild { id: string; phone: string; role: string; status: string; expiresAt?: string }
export interface WorkspaceTeam { used: number; limit: number; members: WorkspaceChild[]; invites: WorkspaceChild[] }
const quietTeamRequest = { headers: { 'X-Quiet-Request': 'true' } };
export const workspaceTeam = () => apiGet<WorkspaceTeam>(teamPath, quietTeamRequest);
export const inviteChild = (phone: string, role: ChildRole) => apiPost<{ token: string; expiresAt: string }>(`${teamPath}/invitations`, { phone, role }, quietTeamRequest);
export const changeChild = (id: string, change: { role: ChildRole } | { active: boolean }) =>
  apiPut(`${teamPath}/members/${encodeURIComponent(id)}`, change, quietTeamRequest);
export const changeChildInviteRole = (id: string, role: ChildRole) => apiPut(`${teamPath}/invitations/${encodeURIComponent(id)}`, { role }, quietTeamRequest);
export const revokeChildInvite = (id: string) => apiDelete(`${teamPath}/invitations/${encodeURIComponent(id)}`, quietTeamRequest);
