import { apiGet, apiPost } from "@/api/http";
import type { PlatformProductPrice } from './productPrice';

export interface ProductVo {
  productId: string;
  qcProductId: string;
  name: string;
  price: number | null;
  userPrice: number | null;
  priceInfo?: PlatformProductPrice | null;
  imageUrl?: string;
}

export function listProducts(awemeId: string, keyword?: string, refreshPrices = false) {
  return apiGet<ProductVo[]>("/api/v1/catalog/products", { timeout: 60000, params: { awemeId, keyword, refreshPrices } });
}

export function selectProducts(awemeId: string, productIds: string[]) {
  return apiPost<{ products: ProductVo[]; analysis?: string; warn?: string }>(
    "/api/v1/catalog/products/select",
    { awemeId, productIds },
  );
}
