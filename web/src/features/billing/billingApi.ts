import { apiGet, apiPatch, apiPost } from "@/api/http";

export interface PlanItem {
  code: "BASIC" | "PROFESSIONAL" | "FLAGSHIP";
  name: string;
  listPriceFen: number;
  listPriceYuan: number;
  adAccountLimit: number;
  suitableFor: string;
  benefits: string[];
  current: boolean;
  action: "CURRENT" | "UPGRADE" | "DOWNGRADE" | "SUBSCRIBE";
}

export interface SubscriptionView {
  hasPaidSubscription: boolean;
  trialMapped: boolean;
  planCode: string;
  planName: string;
  listPriceYuan: number;
  discountLabel?: string | null;
  periodStart?: string | null;
  periodEnd?: string | null;
  remainingDays: number;
  autoRenew?: boolean | null;
  payMethod?: string | null;
  status?: string | null;
}

export interface OrderView {
  orderId: string;
  type: string;
  planCode: string;
  fromPlanCode?: string | null;
  payableFen: number;
  payableYuan: number;
  discountLabel?: string | null;
  status: string;
}

export interface PaymentView {
  paymentId: string;
  channel: string;
  codeUrl?: string | null;
  mockReady: boolean;
  mockNonce?: string | null;
  status: string;
}

export interface BillItem {
  id: string;
  billDate: string;
  title: string;
  amountFen: number;
  amountYuan: number;
  amountNote?: string | null;
  payMethod: string;
  status: "PAID" | "PENDING_DEBIT";
  canDownloadInvoice: boolean;
}

export function listPlans() {
  return apiGet<{ items: PlanItem[] }>("/api/v1/billing/plans");
}

export function getSubscription() {
  return apiGet<SubscriptionView>("/api/v1/billing/subscription");
}

export function patchAutoRenew(autoRenew: boolean) {
  return apiPatch<SubscriptionView>("/api/v1/billing/subscription", { autoRenew });
}

export function createOrder(planCode: string) {
  return apiPost<OrderView>("/api/v1/billing/orders", { planCode });
}

export function getPendingOrder() {
  return apiGet<OrderView | null>("/api/v1/billing/orders/pending");
}

export function closeOrder(orderId: string) {
  return apiPost<OrderView>(`/api/v1/billing/orders/${orderId}/close`, {});
}

export function createPayment(orderId: string) {
  return apiPost<PaymentView>("/api/v1/billing/payments", { orderId });
}

export function mockComplete(paymentId: string, success: boolean, nonce: string) {
  return apiPost<PaymentView>(`/api/v1/billing/payments/${paymentId}/mock-complete`, { success, nonce });
}

export function listBills() {
  return apiGet<{ items: BillItem[] }>("/api/v1/billing/bills");
}
