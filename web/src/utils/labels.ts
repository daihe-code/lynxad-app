export const PLAN_STATUS: Record<string, string> = {
  DRAFT: "草稿",
  SUBMITTING: "提交中",
  PENDING_AUDIT: "审核中",
  DELIVERING: "投放中",
  PAUSED: "已暂停",
  FINISHED: "已结束",
  REJECTED: "审核失败",
  UNKNOWN: "状态待核查",
};
export const GUARANTEE_STATUS: Record<string, string> = {
  IN_EFFECT: '成本保障生效中', INVALID: '成本保障已失效', CONFIRMING: '确认中', PAID: '已到账', ENDED: '已结束', DEFAULT: '暂无保障状态',
};
export function planStatusLabel(status: string, qcStatus?: string | null) {
  if (status !== 'UNKNOWN' || !qcStatus) return PLAN_STATUS[status] || status;
  return ({ SYSTEM_DISABLE: '系统暂停', ROI2_DISABLE: 'ROI 目标限制', QUOTA_DISABLE: '配额限制',
    OFFLINE_BALANCE: '余额不足', OFFLINE_SHARED_WALLET_BALANCE: '共享钱包余额不足', OFFLINE_BUDGET: '预算不足',
    ADVERTISER_OFFLINE_BUDGET: '账户预算不足', CAMPAIGN_OFFLINE_BUDGET: '项目预算不足', CAMPAIGN_DISABLE: '项目暂停',
    TIME_NO_REACH: '未到投放时间', NO_SCHEDULE: '不在投放时段', PRE_ONLINE: '待投放',
    FROZEN: '已冻结', ERROR: '投放异常', AWEME_ACCOUNT_DISABLED: '抖音号不可用',
    DAILY_DELIVERY_SECONDS_DONE: '今日投放时长已结束', LIVE_ROOM_OFF: '直播间已关闭',
  } as Record<string,string>)[qcStatus] || `平台状态：${qcStatus}`;
}

export const AWEME_TYPE: Record<string, string> = {
  OFFICIAL: "官方账号",
  SELF: "自运营",
  COOP: "合作达人",
  COOP_AGENCY: "合作机构",
  ALLIANCE: "联盟",
};

export const BID_TYPE: Record<string, string> = {
  SMART_BID_CONSERVATIVE: "放量投放",
  SMART_BID_CUSTOM: "控成本投放",
};

export const PUBLISHED_BID_MODE_HINT = '首次转投需设置预算或ROI；已有两套计划时，直接暂停当前计划并启用另一套，沿用原有设置。';

export function yuan(value?: number | string | null) {
  if (value === undefined || value === null || value === "") return "—";
  const n = typeof value === "number" ? value : Number(value);
  if (Number.isNaN(n)) return String(value);
  return `¥${n.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function roiText(value?: number | string | null) {
  if (value === undefined || value === null || value === "") return "—";
  const n = typeof value === "number" ? value : Number(value);
  if (Number.isNaN(n)) return String(value);
  return n.toFixed(2);
}
