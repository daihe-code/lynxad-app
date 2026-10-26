import type { InboxMessage } from './inboxApi';

export function generationNotice(item: InboxMessage) {
  if (!['CREATION_GENERATED', 'CREATION_EDIT_GENERATED'].includes(item.bizType || '')) return null;
  const produced = item.result?.produced ?? Number(item.body?.match(/(?:已产出|已生成)\s*(\d+)\s*条/)?.[1] ?? NaN);
  const planName = item.result?.planName || item.body?.match(/计划\s*[「\[]([^」\]]+)[」\]]/)?.[1] || '';
  const outcome = Number.isFinite(produced) && produced === 0 ? 'FAILED' : item.result?.outcome
    || (/新版本/.test(item.title) ? 'NEW_VERSION' : /部分|未完成/.test(item.title) ? 'PARTIAL' : /失败/.test(item.title) ? 'FAILED' : 'GENERATED');
  const title = outcome === 'FAILED' ? '素材生成失败' : outcome === 'PARTIAL' ? '部分素材生成完成' : outcome === 'NEW_VERSION' ? '新版本已生成，待确认' : '素材生成完成';
  const body = outcome === 'FAILED' ? '本次未生成素材，原有素材保持不变。请查看失败原因。'
    : outcome === 'PARTIAL' ? `已生成${Number.isFinite(produced) ? ` ${produced} 条` : '部分'}素材，部分任务未完成。可查看结果和失败原因。`
    : outcome === 'NEW_VERSION' ? '新素材已生成，请预览后确认使用。'
    : `素材已生成${Number.isFinite(produced) ? ` ${produced} 条` : ''}，可查看结果及登记、检查和同步状态。`;
  return { title, planName, body, outcome, action: outcome === 'FAILED' ? '查看失败原因' : outcome === 'NEW_VERSION' ? '查看新版本' : '查看素材结果' };
}

export function generationNoticeRoute(item: InboxMessage) {
  if (!generationNotice(item)) return null;
  const id = item.result?.creationPlanId || item.bizId;
  if (!id) return null;
  // Published composition drafts have a creation snapshot but no ad_plan row.
  return item.bizType === 'CREATION_EDIT_GENERATED'
    ? { path: '/campaigns/create', query: { draftId: id, step: '3', notice: item.id } }
    : { path: `/campaigns/${encodeURIComponent(id)}`, query: { step: '3', notice: item.id } };
}
