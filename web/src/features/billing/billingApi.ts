import { apiGet, apiPatch, apiPost } from "@/api/http";

export interface PlanItem {
  code: "BASIC" | "PROFESSIONAL" | "FLAGSHIP";
  name: string;
  listPriceFen: number;
  listPriceYuan: number;
  adAccountLimit: number;
  childAccountLimit: number;
  periodPoints: number;
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
  paymentExpiresAt?: string | null;
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
  return apiPost<OrderView>(`/api/v1/billing/orders/${encodeURIComponent(orderId)}/close`, {},
    { headers: { 'X-Quiet-Request': 'true' } });
}

export function createPayment(orderId: string) {
  return apiPost<PaymentView>("/api/v1/billing/payments", { orderId, deferAlipayCheckout: true });
}

export function openAlipayCheckout(orderId: string, signal: AbortSignal) {
  return apiPost<PaymentView>('/api/v1/billing/payments/alipay/checkout', { orderId },
    { signal, headers: { 'X-Quiet-Request': 'true' } });
}

export function checkOrderPayment(orderId: string, signal?: AbortSignal) {
  return apiPost<OrderView>(`/api/v1/billing/orders/${encodeURIComponent(orderId)}/check-payment`, {},
    signal ? { signal, headers: { 'X-Quiet-Request': 'true' } } : undefined);
}

export const isBillingOrderId = (id: unknown): id is string =>
  typeof id === 'string' && /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(id);

export function alipayCheckoutUrl(payment?: PaymentView) {
  if (payment?.channel !== 'ALIPAY' || !payment.codeUrl) return undefined;
  if (payment.codeUrl.startsWith('/subscription/alipay-checkout?')) {
    const url = new URL(payment.codeUrl, window.location.origin);
    const kind = url.searchParams.get('kind');
    if (url.origin === window.location.origin && url.pathname === '/subscription/alipay-checkout' && !url.hash
        && isBillingOrderId(url.searchParams.get('order')) && (kind === 'package' || kind === 'recharge')) return url.href;
    return undefined;
  }
  return alipayGatewayUrl(payment);
}

export function alipayGatewayUrl(payment?: PaymentView) {
  if (payment?.channel !== 'ALIPAY' || !payment.codeUrl) return undefined;
  try {
    const url = new URL(payment.codeUrl);
    const gateways = ['openapi.alipay.com', 'openapi.alipaydev.com', 'openapi-sandbox.dl.alipaydev.com'];
    if (url.protocol === 'https:' && !url.username && !url.password
        && gateways.includes(url.hostname) && url.pathname === '/gateway.do') return url.href;
  } catch {
    return undefined;
  }
}

export function mockComplete(paymentId: string, success: boolean, nonce: string) {
  return apiPost<PaymentView>(`/api/v1/billing/payments/${paymentId}/mock-complete`, { success, nonce });
}

export function listBills() {
  return apiGet<{ items: BillItem[] }>("/api/v1/billing/bills");
}

export interface GenerationAccess {
  enabled: boolean;
  balanceStatus: 'SUFFICIENT' | 'LOW' | 'EMPTY' | null;
}
export function pointsBalanceWarning(access?: GenerationAccess) {
  if (!access?.enabled) return '';
  if (access.balanceStatus === 'LOW') return '可用积分即将用完。余额不足时，智能生成将暂停。';
  return access.balanceStatus === 'EMPTY' ? '可用积分已用完，智能生成已暂停。' : '';
}
export const getGenerationAccess = (signal?: AbortSignal) =>
  apiGet<GenerationAccess>('/api/v1/billing/points/generation-access', { signal, headers: { 'X-Quiet-Request': 'true' } });

export interface RechargeOption { code: string; priceFen: number; points: number }
export interface RechargeCatalog {
  availability: 'AVAILABLE' | 'PAID_SUBSCRIPTION_REQUIRED' | 'DISABLED';
  options: RechargeOption[];
  pending: OrderView | null;
}
export const getRechargeCatalog = () => apiGet<RechargeCatalog>('/api/v1/billing/points/recharge-catalog', { headers: { 'X-Quiet-Request': 'true' } });
export const quoteRecharge = (amountFen: number) => apiGet<RechargeOption>('/api/v1/billing/points/recharge-quote', {
  params: { amountFen }, headers: { 'X-Quiet-Request': 'true' },
});
export const createRecharge = (code: string, key: string) =>
  apiPost<OrderView>('/api/v1/billing/points/orders', { code }, { headers: { 'Idempotency-Key': key } });
export const rechargeStatus = (id: string) => apiGet<OrderView>(`/api/v1/billing/points/orders/${encodeURIComponent(id)}`);
export const closeRecharge = (id: string) => apiPost<OrderView>(`/api/v1/billing/points/orders/${encodeURIComponent(id)}/close`, {}, {
  headers: { 'X-Quiet-Request': 'true' },
});
