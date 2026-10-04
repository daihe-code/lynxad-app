import { apiGet, apiPost } from "@/api/http";

export interface ProductVo {
  productId: string;
  qcProductId: string;
  name: string;
  price: number;
  userPrice: number;
  imageUrl?: string;
}

export function listProducts(awemeId: string, keyword?: string) {
  return apiGet<ProductVo[]>("/api/v1/catalog/products", { params: { awemeId, keyword } });
}

export function selectProducts(awemeId: string, productIds: string[]) {
  return apiPost<{ products: ProductVo[]; analysis?: string; warn?: string }>(
    "/api/v1/catalog/products/select",
    { awemeId, productIds },
  );
}
