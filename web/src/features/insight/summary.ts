import type { PlanListVo } from "@/features/delivery/deliveryApi";

/** Backend already returns Yuan / ROI. UI must never divide again. */
export function sumPlanMetrics(rows: PlanListVo[]) {
  const costYuan = rows.reduce((s, r) => s + Number(r.costYuan || 0), 0);
  const gmvYuan = rows.reduce((s, r) => s + Number(r.gmvYuan || 0), 0);
  return { costYuan, gmvYuan, roi: costYuan > 0 ? gmvYuan / costYuan : 0 };
}
