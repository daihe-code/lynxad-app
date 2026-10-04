import { apiGet, apiPost } from "@/api/http";

export interface InboxMessage {
  id: string;
  title: string;
  body: string;
  bizType?: string;
  bizId?: string;
  unread: boolean;
  sentAt: string;
}

export function listInbox() {
  return apiGet<InboxMessage[]>("/api/v1/tenant/notifications");
}

export function readInbox(id: string) {
  return apiPost<void>(`/api/v1/tenant/notifications/${id}/read`);
}
