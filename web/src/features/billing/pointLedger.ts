export const pointKinds = {
  RECHARGE: '积分充值',
  SETTLE: '积分消耗',
  GRANT: '套餐赠送',
  ADMIN_CREDIT: '后台增加',
  ADMIN_DEBIT: '后台扣减',
  REFUND: '积分退回',
  RESERVE: '预占',
  RELEASE: '释放预占',
  CREDIT: '其他入账',
  OTHER: '其他记录',
};

export type PointKind = keyof typeof pointKinds;
export interface PointLedgerAccount { userId: string; name?: string | null; phoneSuffix?: string | null; role?: string | null }
export interface PointLedgerItem { unit: string; quantity: number; unitPoints: string; points: string }
export interface PointLedgerEntry {
  id: string;
  kind: PointKind;
  amount: string;
  availableChange: string | null;
  account: PointLedgerAccount | null;
  reference: string | null;
  createdAt: string;
  advertiserId?: string | null;
  items?: PointLedgerItem[];
}
export interface PointLedgerPage { records: PointLedgerEntry[]; accounts: PointLedgerAccount[]; page: number; hasMore: boolean; netConsumed?: string | null }
export interface PointLedgerQuery { page: number; account?: string; kind?: PointKind; from?: string; to?: string }

const taskNames: Record<string, string> = {
  analysis: '商品深度分析',
  remake: '爆款复刻',
  initial: '批量生成素材',
  addition: '新增素材',
  regenerate: '重新生成素材',
  retry: '重试生成素材',
  optimization: '自动生成素材',
  title: '生成标题',
};
export const ledgerTaskName = (reference: string | null) => reference ? taskNames[reference.split(':', 1)[0]] : undefined;

const units: Record<string, [string, string]> = {
  video: ['普通视频', '条'],
  video_second: ['普通视频', '秒'],
  remake_720p_second: ['720p 复刻', '秒'],
  remake_1080p_second: ['1080p 复刻', '秒'],
  image: ['图片', '张'],
  image_unit: ['图片计费', '单位'],
  native_image_text: ['图文', '套'],
  title: ['标题', '个'],
  product_analysis: ['深度分析', '次'],
};
export function ledgerItemLabel(item: PointLedgerItem) {
  const [name, unit] = units[item.unit] ?? ['用量', '单位'];
  return `${name} ${item.quantity} ${unit} × ${formatLedgerPoints(item.unitPoints)} = ${formatLedgerPoints(item.points)} 积分`;
}

export function formatLedgerPoints(value: string | null, signed = false): string {
  if (value === null) return '—';
  const [whole, fraction] = value.replace(/^-/, '').split('.');
  const digits = fraction?.replace(/0+$/, '') || '';
  const prefix = value.startsWith('-') ? '−' : signed && (whole !== '0' || digits) ? '+' : '';
  return prefix + BigInt(whole).toLocaleString('zh-CN') + (digits ? `.${digits}` : '');
}
