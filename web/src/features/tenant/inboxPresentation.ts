import type { InboxMessage } from './inboxApi';
import { WATCHER_TYPES } from '@/features/agentops/agentopsApi';
import { generationNotice } from './inboxGeneration';

export type InboxFilterCategory = 'system' | 'warning' | 'approval' | 'optimization';

export function filterCategory(item: InboxMessage): InboxFilterCategory {
  const type = item.bizType || '';
  if (WATCHER_TYPES.includes(type as (typeof WATCHER_TYPES)[number]) || type === 'OPTIMIZATION_ERROR') return 'warning';
  if (type === 'AUDIT_PASS' || type === 'AUDIT_REJECT') return 'approval';
  if (type.startsWith('OPTIMIZATION_') || type.startsWith('CREATIVE_')) return 'optimization';
  return 'system';
}

export function filterInbox(items: InboxMessage[], tab: 'all' | 'unread', category: InboxFilterCategory | 'all') {
  return items.filter(item => (tab === 'all' || item.unread) && (category === 'all' || filterCategory(item) === category));
}

export function inboxPresentation(item: InboxMessage) {
  const type = item.bizType || '';
  const generation = generationNotice(item);
  if(generation)return {category:'素材',tone:'material',action:generation.action,destination:type === 'CREATION_EDIT_GENERATED' ? 'contentEdit' as const : 'draft' as const};
  if (type.startsWith('OPTIMIZATION_')) return { category: '自动盯盘', tone: 'warning', action: '查看盯盘', destination: 'optimization' as const };
  if (type.startsWith('CREATIVE_')) return { category: '素材', tone: 'material', action: '查看素材', destination: 'creative' as const };
  if (type === 'TOKEN_EXPIRED') return { category: '授权', tone: 'authorization', action: '重新授权', destination: 'authorization' as const };
  if (type === 'DAILY_REPORT' || type === 'INSIGHT_REPORT' || type === 'PLAN_DAILY_REPORT') return { category: '日报', tone: '', action: '查看日报', destination: 'report' as const };
  if (WATCHER_TYPES.includes(type as (typeof WATCHER_TYPES)[number])) return { category: '盯盘提醒', tone: 'warning', action: '查看计划', destination: 'plan' as const };
  if (['AUDIT_PASS', 'AUDIT_REJECT', 'PLAN_PUBLISHED'].includes(type)) return { category: '投放计划', tone: '', action: '查看计划', destination: 'plan' as const };
  return { category: '系统消息', tone: '', action: '', destination: null };
}

export function inboxTime(value: string) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '时间未知';
  const now = new Date(), yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
  const sameDay = (a: Date, b: Date) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
  const pad = (n: number) => String(n).padStart(2, '0');
  const day = sameDay(date, now) ? '今天' : sameDay(date, yesterday) ? '昨天' : `${date.getFullYear() === now.getFullYear() ? '' : `${date.getFullYear()}-`}${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function inboxBody(value: string) {
  return value.split(/([¥￥]\s*[\d,]+(?:\.\d+)?|\d+(?:\.\d+)?%|\d+\s*(?:笔|单))/g).filter(Boolean).map(text => ({ text, emphasis: /^[¥￥]|^\d+(?:\.\d+)?(?:%|\s*(?:笔|单))$/.test(text) }));
}
