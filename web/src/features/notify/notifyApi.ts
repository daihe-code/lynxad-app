/** 联系工单调用封装；须对齐 001 OpenAPI `notify` 路径，禁止 `any`。 */
import { apiGet, apiPost } from "@/api/http";

export const CONTACT_EMAIL = "lingtou@lynxadai.cn";

export interface ContactInfo {
  phone: string;
  email: string;
}

export interface ContactTicket {
  ticketId: string;
}

export async function fetchContactInfo() {
  const info = await apiGet<ContactInfo>("/api/v1/notify/contact/info");
  return { ...info, email: CONTACT_EMAIL };
}

export function submitContact(phone: string) {
  return apiPost<ContactTicket>("/api/v1/notify/contact", { phone });
}
