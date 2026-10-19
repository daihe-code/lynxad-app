/**
 * 租户认证/账户调用封装。字段须与
 * `specs/001-closed-loop-saas/contracts/openapi.yaml` 及 `web/src/api/schema.d.ts` 对齐，禁止 `any`。
 */
import { apiGet, apiPost, apiPut } from "@/api/http";

export interface LoginResult {
  token: string;
  expiresIn: number;
  needOnboarding: boolean;
  demoMode: boolean;
}

export interface CurrentUser {
  userId: string;
  phone: string;
  nickname?: string;
  tenantId?: string;
  tenantName?: string;
  memberRole?: string;
  trialExpiresAt?: string;
  trialActive: boolean;
  needOnboarding: boolean;
  demoMode: boolean;
  /** 等于演示投放开关，不是令牌，不由 demoMode 推导。 */
  mockQianchuan: boolean;
  email?: string;
  companyName?: string;
  industry?: string;
  companyScale?: string | null;
  registeredAt?: string;
  hasPassword?: boolean;
  remainingTrialDays?: number;
  displayPlanCode?: string;
  displayPlanName?: string;
  trialStatusLabel?: string;
  subscriptionPlanCode?: string | null;
  subscriptionPlanName?: string | null;
  periodStart?: string | null;
  periodEnd?: string | null;
  autoRenew?: boolean | null;
  payMethod?: string | null;
  subscriptionStatus?: string | null;
}

export interface SendSmsResult {
  ttlSec: number;
  cooldownSec: number;
}

export interface PeekSmsResult {
  matched: boolean;
}

export interface UpdateProfilePayload {
  nickname?: string;
  email?: string;
  companyName?: string;
  industry?: string;
  companyScale?: string | null;
}

export function sendSms(phone: string) {
  return apiPost<SendSmsResult>("/api/v1/tenant/auth/sms/send", { phone });
}

export function verifySms(phone: string, code: string) {
  return apiPost<PeekSmsResult>("/api/v1/tenant/auth/sms/verify", { phone, code });
}

export function smsLogin(phone: string, code: string) {
  return apiPost<LoginResult>("/api/v1/tenant/auth/sms/login", { phone, code });
}

export function passwordLogin(phone: string, password: string) {
  return apiPost<LoginResult>("/api/v1/tenant/auth/password/login", { phone, password });
}

export function resetPassword(phone: string, code: string, password: string) {
  return apiPost<void>("/api/v1/tenant/auth/password/reset", { phone, code, password });
}

export function logoutApi() {
  return apiPost<void>("/api/v1/tenant/auth/logout");
}

export interface OnboardingPayload {
  nickname: string;
  email: string;
  companyName: string;
  industry: string;
  position: string;
  monthlyBudget: string;
  adExperience: string;
  coreDemand: string;
  password: string;
  agreeTerms: boolean;
  agreePrivacy: boolean;
}

export interface MemberVo {
  phone: string;
  role: string;
  joinedAt?: string;
}

export function currentUser() {
  return apiGet<CurrentUser>("/api/v1/tenant/users/me");
}

export function fetchMe() {
  return currentUser();
}

export function completeOnboarding(body: OnboardingPayload) {
  return apiPut<CurrentUser>("/api/v1/tenant/users/me/onboarding", body);
}

export function updateProfile(body: UpdateProfilePayload) {
  return apiPut<CurrentUser>("/api/v1/tenant/users/me/profile", body);
}

export function updatePassword(body: { oldPassword?: string; newPassword: string }) {
  return apiPut<void>("/api/v1/tenant/users/me/password", body);
}

export function updatePhone(phone: string, code: string) {
  return apiPut<CurrentUser>("/api/v1/tenant/users/me/phone", { phone, code });
}

export function listMembers() {
  return apiGet<MemberVo[]>("/api/v1/tenant/members");
}

export function inviteMember(phone: string) {
  return apiPost<unknown>("/api/v1/tenant/members/invite", { phone });
}
