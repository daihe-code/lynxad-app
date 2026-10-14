import axios from 'axios';
import { reactive } from 'vue';
export interface AdminMe { id: string; username: string; displayName: string; lastLoginAt?: string }
export const adminSession = reactive<{ me?: AdminMe }>({});
const tokenKey = 'lynx.admin.token';
export function hasAdminToken() { return !!sessionStorage.getItem(tokenKey); }
export function clearAdminSession() { sessionStorage.removeItem(tokenKey); adminSession.me = undefined; }
const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/lynx-ad', timeout: 20000 });
http.interceptors.request.use(config => {
  const token = sessionStorage.getItem(tokenKey);
  if (token && !config.url?.endsWith('/auth/login')) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
http.interceptors.response.use(response => {
  if (response.data?.code && response.data.code !== 0) {
    if ([401, 403].includes(response.data.code)) expired();
    return Promise.reject(new Error(response.data.message || '管理请求失败'));
  }
  return response;
}, error => {
  if ([401, 403].includes(error.response?.status)) expired();
  return Promise.reject(new Error(error.response?.data?.message || '无法连接管理服务，请稍后重试'));
});
function expired() {
  clearAdminSession();
  if (location.pathname.startsWith('/admin') && location.pathname !== '/admin/login') location.assign('/admin/login?expired=1');
}
const get = async <T>(path: string, params?: Record<string, unknown>) => (await http.get<{ data: T }>(`/api/v1/admin${path}`, { params })).data.data;
const post = async <T>(path: string, data: unknown) => (await http.post<{ data: T }>(`/api/v1/admin${path}`, data)).data.data;
export async function adminLogin(username: string, password: string) {
  const result = await post<{ token: string; expiresIn: number; admin: AdminMe }>('/auth/login', { username, password });
  sessionStorage.setItem(tokenKey, result.token); adminSession.me = result.admin;
}
export async function loadAdminMe() { const me = await get<AdminMe>('/auth/me'); adminSession.me = me; return me; }
export async function adminLogout() { try { await post('/auth/logout', {}); } finally { clearAdminSession(); } }
export async function changeAdminPassword(oldPassword: string, password: string) { await post('/auth/password', { oldPassword, password }); clearAdminSession(); }
export interface AdminPage<T> { records: T[]; total: number; pageNum: number; pageSize: number }
export interface AdminUser { id: string; phone: string; nickname?: string; email?: string; status: string; registeredAt: string; hasPassword: boolean; companyName?: string; industry?: string; tenantId?: string; tenantName?: string; memberRole?: string; packageType: string; packageStatus: string; planCode?: string; expiresAt?: string; lastLoginAt?: string; loginCount: number }
export interface AdminPackage { planCode: string; status: string; startedAt: string; expiresAt: string; autoRenew?: boolean; payMethod?: string; nextPlanCode?: string }
export interface AdminUserDetail { user: AdminUser; profile?: { avatarUrl?: string; companyScale?: string; position?: string; monthlyBudget?: string; adExperience?: string; coreDemand?: string; completedAt?: string }; subscription?: AdminPackage; trial?: AdminPackage; memberCount: number; adAccounts: number; adPlans: number }
export interface AdminLoginLog { id: string; userId?: string; phone?: string; nickname?: string; method: string; result: string; failureReason?: string; ip?: string; userAgent?: string; createdAt: string }
export interface PackageAdjustment { requestId: string; actorName: string; userId: string; tenantId: string; type: string; planCode?: string; days: number; beforeEnd?: string; afterEnd: string; reason: string; createdAt: string }
export interface AdminAudit { id: string; actorName: string; action: string; result: string; userId?: string; tenantId?: string; description: string; ip?: string; userAgent?: string; createdAt: string }
export interface AdminOverview { users: number; accounts: number; activePackages: number; activeTrials: number; todayLogins: number }
export interface ExtendPackage { requestId: string; type: 'TRIAL' | 'SUBSCRIPTION'; planCode?: string; days: number; expectedEnd: string | null; reason: string }
export const getAdminOverview = () => get<AdminOverview>('/overview');
export const getAdminUsers = (params: Record<string, unknown>) => get<AdminPage<AdminUser>>('/users', params);
export const getAdminUser = (id: string) => get<AdminUserDetail>(`/users/${encodeURIComponent(id)}`);
export const getAdminLogins = (params: Record<string, unknown>) => get<AdminPage<AdminLoginLog>>('/login-logs', params);
export const getPackageAdjustments = (params: Record<string, unknown>) => get<AdminPage<PackageAdjustment>>('/package-adjustments', params);
export const getAdminAudit = (params: Record<string, unknown>) => get<AdminPage<AdminAudit>>('/audit-logs', params);
export const extendPackage = (id: string, body: ExtendPackage) => post<PackageAdjustment>(`/users/${encodeURIComponent(id)}/package/extend`, body);
