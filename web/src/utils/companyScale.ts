/** 与后端 CompanyScale 一致；连字符为 U+2013。 */
export const COMPANY_SCALES = ["1–10 人", "11–50 人", "51–200 人", "200 人以上"] as const;

export type CompanyScaleValue = (typeof COMPANY_SCALES)[number];

export function displayCompanyScale(raw?: string | null): string {
  if (!raw) return "—";
  return (COMPANY_SCALES as readonly string[]).includes(raw) ? raw : "—";
}
