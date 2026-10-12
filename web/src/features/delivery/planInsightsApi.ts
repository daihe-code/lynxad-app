import { apiGet, apiPost } from '@/api/http';
import type { PlatformProductPrice } from '@/features/catalog/productPrice';
import type { StatisticsRange } from './statisticsRange';
export type InsightMetrics = Partial<Record<'cost' | 'orders' | 'gmv' | 'roi' | 'netRoi' | 'netGmv' | 'settlementRate' | 'refundRate', number | null>>;
export interface InsightPoint { time: string; metrics: InsightMetrics }
export interface InsightStatistics { summary: InsightMetrics; trend: InsightPoint[]; timeDimension?: string; fetchedAt: string; warning?: string }
export interface InsightPage<T> { records: T[]; total: number; page: number; pageSize: number }
export interface InsightLog { id: string; time: string; actor: string; operation: string; result?: string; detail?: string; details?: string[] }
export interface GuaranteeObservation { id: string; time: string; status: string; reason?: string; url?: string }
export interface InsightGuarantee { status?: string; reason?: string; url?: string; fetchedAt: string; warning?: string; history: InsightPage<GuaranteeObservation> }
export interface DailySpending { startDate: string; endDate: string; totalCost: number | null; days: { date: string; cost: number | null }[]; fetchedAt: string; warning?: string }
const options = (range: StatisticsRange, extra = {}) => ({ timeout: 60000, headers: { 'X-Quiet-Request': 'true' }, params: { startDate: range[0], endDate: range[1], ...extra } });
export const insightData = (id: string, range: StatisticsRange, refresh = false) => apiGet<InsightStatistics>(`/api/v1/delivery/plans/${id}/insights/data`, options(range, { refresh }));
export const insightLogs = (id: string, range: StatisticsRange, source: string, page: number) => apiGet<InsightPage<InsightLog>>(`/api/v1/delivery/plans/${id}/insights/logs`, options(range, { source, page }));
export const insightGuarantee = (id: string, range: StatisticsRange, page: number) => apiGet<InsightGuarantee>(`/api/v1/delivery/plans/${id}/insights/guarantee`, options(range, { page }));
export const insightDailySpending = (id: string, range: StatisticsRange) => apiGet<DailySpending>(`/api/v1/delivery/plans/${id}/insights/guarantee/daily-spending`, options(range));
export type ProductMetrics = Partial<Record<'shows' | 'clicks' | 'ctr' | 'cvr' | 'cost' | 'orders' | 'gmv' | 'roi', number | null>>;
export interface PlanProduct { id: string; name: string; imageUrl?: string; auditStatus?: string; metrics: ProductMetrics; deletable: boolean; priceInfo?: PlatformProductPrice | null }
export interface ProductChange { requestKey: string; productId: string; status: 'PENDING' | 'DONE'; message: string }
export interface PlanProducts { records: PlanProduct[]; summary: ProductMetrics; dataProductCount: number; shared: boolean; fetchedAt: string; warning?: string; pending?: ProductChange }
export const insightProducts = (id: string, range: StatisticsRange, refreshPrices = false) => apiGet<PlanProducts>(`/api/v1/delivery/plans/${id}/products`, options(range, { refreshPrices }));
export type MaterialMetrics = ProductMetrics & Partial<Record<'orderCost' | 'netRoi' | 'netGmv' | 'settlementRate' | 'refundRate', number | null>>;
export interface PlanMaterial { id: string; type: string; name: string; text?: string; previewUrl?: string; posterUrl?: string; imageUrls: string[]; musicUrl?: string; products: { id: string; name: string; imageUrl?: string }[]; auditStatus?: string; status?: string; deleted: boolean; aigc?: boolean; tags: string[]; createTime?: string; reasons: string[]; metrics: MaterialMetrics }
export interface PlanMaterials { records: PlanMaterial[]; fetchedAt: string; warning?: string }
export const insightMaterials = (id: string, range: StatisticsRange, type: string) => apiGet<PlanMaterials>(`/api/v1/delivery/plans/${id}/insights/materials`, options(range, { type }));
export const deletePlanProduct = (id: string, product: string, requestKey: string, expectedQcPlanId: string) => apiPost<ProductChange>(`/api/v1/delivery/plans/${id}/products/${product}/delete`, { requestKey, expectedQcPlanId }, { timeout: 180000, headers: { 'X-Quiet-Request': 'true' } });
