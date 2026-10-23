import { planStatusDisplay } from '../features/delivery/planStatus';

export const PLAN_STATUS: Record<string, string> = {
  DRAFT: "草稿",
  SUBMITTING: "提交中",
  PENDING_AUDIT: "审核中",
  DELIVERING: "投放中",
  PAUSED: "已暂停",
  FINISHED: "已结束",
  REJECTED: "审核不通过",
  DELETED: "已删除",
  COMPLETED: "已完成",
  TERMINATED: "已终止",
  UNKNOWN: "状态待核查",
};
export const GUARANTEE_STATUS: Record<string, string> = {
  IN_EFFECT: '成本保障生效中', INVALID: '成本保障已失效', CONFIRMING: '确认中', PAID: '已到账', ENDED: '已结束', DEFAULT: '暂无保障状态',
};
export function planStatusLabel(status: string, qcStatus?: string | null) {
  return planStatusDisplay(status,qcStatus).label;
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

export const PUBLISHED_BID_MODE_HINT = '首次转投需设置预算或 ROI。\n已有两套计划时，直接暂停当前计划并启用另一套，沿用原有设置。';

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
