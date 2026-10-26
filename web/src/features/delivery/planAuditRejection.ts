export interface AuditMaterialReason {
  id: string; type: string; name: string; auditStatus?: string | null;
  deleted?: boolean; reasons?: readonly string[] | null;
}
export type AuditIssueCategory = 'QUALIFICATION' | 'MATERIAL' | 'AUTHORIZATION' | 'PLATFORM';
export interface AuditRejectionIssue {
  key: string; title: string; category: AuditIssueCategory; categoryLabel: string;
  reasons: string[]; guidance: string; materialId?: string;
}

const labels = { QUALIFICATION: '资质类', MATERIAL: '素材类', AUTHORIZATION: '授权类', PLATFORM: '平台审核' };
const guidance = {
  QUALIFICATION: '根据平台审核意见确认需补充的商品或行业资质，在巨量千川补齐资料并核对审核状态，再重新提交审核。',
  MATERIAL: '按审核意见检查素材文案、画面、商品信息及相关使用授权，修改完成后重新提交审核。',
  AUTHORIZATION: '核对当前广告账户、抖音号和投放授权状态；如授权失效，完成重新授权后再处理计划审核。',
  PLATFORM: '先核对千川返回的具体审核意见，再处理对应问题并重新提交；未取得明确原因时可在千川后台查看。',
};
function category(reason: string, material = false): AuditIssueCategory {
  if (/资质|许可证|营业执照|行业准入|生产许可|经营许可|\bSC\b/i.test(reason)) return 'QUALIFICATION';
  if (/版权|肖像|商标|知识产权|侵权/.test(reason)) return 'MATERIAL';
  if (/(账号|账户|抖音号).*(授权|异常|失效|过期)|投放授权|授权.*(失效|过期|撤销)/.test(reason)) return 'AUTHORIZATION';
  if (material || /素材|创意|视频|图片|图文|标题|文案|画面|违禁词|绝对化/.test(reason)) return 'MATERIAL';
  return 'PLATFORM';
}
const cleanReasons = (values?: readonly string[] | null) => [...new Set((Array.isArray(values) ? values : []).filter(v => typeof v === 'string').map(v => v.trim()).filter(Boolean))];
const rejected = (status?: string | null) => ['REJECT', 'REJECTED', 'AUDIT_REJECT', 'AUDIT_REJECTED', 'AUDIT_FAIL'].includes(status?.toUpperCase() || '');

export function auditRejectionIssues(plan: { rejectReason?: string | null }, materials: readonly AuditMaterialReason[] = []): AuditRejectionIssue[] {
  const unique = new Map<string, AuditMaterialReason>();
  for (const material of materials) {
    if (!material || typeof material.id !== 'string' || !material.id.trim() || material.deleted || !rejected(material.auditStatus)) continue;
    const key = `${material.type}:${material.id}`, previous = unique.get(key);
    unique.set(key, { ...material, reasons: cleanReasons([...cleanReasons(previous?.reasons), ...cleanReasons(material.reasons)]) });
  }
  const issues: AuditRejectionIssue[] = [...unique.values()].map(material => {
    const reasons = cleanReasons(material.reasons);
    const kind = category(reasons.join('；'), true);
    return { key: `material:${material.type}:${material.id}`, materialId: material.id,
      title: `素材「${material.name?.trim() || material.id}」审核未通过`, category: kind, categoryLabel: labels[kind],
      reasons: reasons.length ? reasons : ['平台已标记该素材审核未通过，详细原因暂未返回，请在千川核对。'], guidance: guidance[kind] };
  });
  const reason = plan.rejectReason?.trim();
  const generic = !reason || /^(审核拒绝|审核未通过([，,].*(前往千川|查看具体原因).*)?)$/.test(reason);
  if (!generic || !issues.length) {
    const kind = category(reason || '');
    const title = { QUALIFICATION: '商品或行业资质问题', MATERIAL: '素材创意审核未通过', AUTHORIZATION: '广告账户授权问题', PLATFORM: '投放计划审核未通过' }[kind];
    issues.unshift({ key: 'plan', title, category: kind, categoryLabel: labels[kind],
      reasons: [reason || '平台暂未返回详细拒审原因，请在千川后台查看本计划的审核意见。'], guidance: guidance[kind] });
  }
  return issues;
}
