export interface PlatformProductPrice {
  minPrice: number; maxPrice: number; marketPrice?: number | null;
  source: string; fetchedAt: string; stale: boolean;
}

const known = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value > 0;
export function productPriceText(info?: PlatformProductPrice | null, fallback?: number | null) {
  const low = info?.minPrice ?? fallback, high = info?.maxPrice ?? low;
  if (!known(low) || !known(high) || high < low) return '暂未获取';
  const money = (value: number) => value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return low === high ? `¥${money(low)}` : `¥${money(low)}–${money(high)}`;
}
export function productPriceHint(info?: PlatformProductPrice | null) {
  if (!info) return '平台暂未提供商品价格';
  const at = new Date(info.fetchedAt);
  const time = Number.isNaN(at.getTime()) ? '' : ` · ${at.toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })}`;
  return `${info.stale ? '刷新未成功，显示上次获取的价格' : '千川参考价格'}${time}；具体规格及优惠价格以商品页面为准`;
}
