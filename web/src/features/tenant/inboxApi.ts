import { apiGet, apiPost } from "@/api/http";

export interface InboxMessage {
  id: string;
  title: string;
  body: string;
  bizType?: string;
  bizId?: string;
  unread: boolean;
  sentAt: string;
  result?: { outcome: 'PARTIAL' | 'FAILED' | 'GENERATED' | 'NEW_VERSION'; planName: string; creationPlanId: string; jobId?: string; operationId?: string; materialId?: string; materialType?: string; produced: number } | null;
}

export function listInbox(background = false) {
  return apiGet<InboxMessage[]>("/api/v1/tenant/notifications", { headers: background ? { 'X-Quiet-Request': 'true' } : {} });
}

export function readInbox(id: string) {
  return apiPost<void>(`/api/v1/tenant/notifications/${encodeURIComponent(id)}/read`);
}

export function readAllInbox(before: string) {
  return apiPost<number>('/api/v1/tenant/notifications/read-all', { before });
}
