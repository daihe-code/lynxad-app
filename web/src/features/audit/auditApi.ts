/** 操作日志查询封装；须对齐 001 OpenAPI `audit/records`，禁止导出客户端与 `any`。 */
import { apiGet } from "@/api/http";

export const AUDIT_ACTION_TYPES = [
  "LOGIN",
  "PLAN",
  "CREATIVE",
  "OAUTH",
  "ACCOUNT",
  "SYSTEM_NOTICE",
] as const;

export type AuditActionType = (typeof AUDIT_ACTION_TYPES)[number];

export const AUDIT_ACTION_LABEL: Record<AuditActionType, string> = {
  LOGIN: "登录",
  PLAN: "投放计划",
  CREATIVE: "素材",
  OAUTH: "授权",
  ACCOUNT: "账户设置",
  SYSTEM_NOTICE: "系统通知",
};

export interface AuditRecord {
  id: string;
  createdAt: string;
  actorName: string;
  actionType: AuditActionType | string;
  objectLabel: string;
  ipMasked: string;
}

export interface AuditPage {
  list: AuditRecord[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export interface AuditQuery {
  actionType?: string;
  from?: string;
  to?: string;
  q?: string;
  pageNum?: number;
  pageSize?: number;
}

export function listAuditRecords(query: AuditQuery = {}) {
  const params: Record<string, string | number> = {
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20,
  };
  if (query.actionType) params.actionType = query.actionType;
  if (query.from) params.from = query.from;
  if (query.to) params.to = query.to;
  if (query.q) params.q = query.q;
  return apiGet<AuditPage>("/api/v1/audit/records", { params });
}
