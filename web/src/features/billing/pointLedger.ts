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
export interface PointLedgerEntry {
  id: string;
  kind: PointKind;
  amount: string;
  availableChange: string | null;
  account: PointLedgerAccount | null;
  reference: string | null;
  createdAt: string;
}
export interface PointLedgerPage { records: PointLedgerEntry[]; accounts: PointLedgerAccount[]; page: number; hasMore: boolean }
export interface PointLedgerQuery { page: number; account?: string; kind?: PointKind }

export function formatLedgerPoints(value: string | null, signed = false): string {
  if (value === null) return '—';
  const [whole, fraction] = value.replace(/^-/, '').split('.');
  const digits = fraction?.replace(/0+$/, '') || '';
  const prefix = value.startsWith('-') ? '−' : signed && (whole !== '0' || digits) ? '+' : '';
  return prefix + BigInt(whole).toLocaleString('zh-CN') + (digits ? `.${digits}` : '');
}
