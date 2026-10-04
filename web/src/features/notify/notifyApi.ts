/** 联系工单调用封装；须对齐 001 OpenAPI `notify` 路径，禁止 `any`。 */
import { apiGet, apiPost } from "@/api/http";

export interface ContactInfo {
  phone: string;
  email: string;
}

export interface ContactTicket {
  ticketId: string;
}

export function fetchContactInfo() {
  return apiGet<ContactInfo>("/api/v1/notify/contact/info");
}

export function submitContact(phone: string) {
  return apiPost<ContactTicket>("/api/v1/notify/contact", { phone });
}
