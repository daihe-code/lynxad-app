import type { LibraryItemVo } from './creativeApi';

export function referenceLabel(item: Pick<LibraryItemVo, 'used' | 'usageKnown' | 'references'>) {
  const count = item.references?.length || 0;
  if (count) return `${count} 个计划引用`;
  if (item.used) return '已引用';
  return item.usageKnown === false ? '引用待同步' : '未被引用';
}
