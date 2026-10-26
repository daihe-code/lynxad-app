interface Product { productId: string; qcProductId: string; name: string }
interface Material { productId: string }
export function selectionImpact(before: Product[], after: Product[], materials: Material[]) {
  const old = new Set(before.map(p => p.qcProductId)), next = new Set(after.map(p => p.qcProductId));
  const added = after.filter(p => !old.has(p.qcProductId)), removed = before.filter(p => !next.has(p.qcProductId));
  const retained = before.filter(p => next.has(p.qcProductId));
  const removedKeys = new Set(removed.flatMap(p => [p.productId,p.qcProductId]));
  return { added, removed, retained, removedMaterials: materials.filter(m => removedKeys.has(m.productId)).length,
    retainedMaterials: materials.filter(m => !removedKeys.has(m.productId)).length, changed: !!(added.length || removed.length) };
}

export function selectionImpactText(impact: ReturnType<typeof selectionImpact>, published = false) {
  const rows = [];
  if (impact.added.length) rows.push(`新增 ${impact.added.length} 款商品：${impact.added.map(p => p.name).join('、')}。新增商品可单独智能生成素材，也可从素材库选择或本地上传。`);
  if (impact.retained.length) rows.push(`保留 ${impact.retained.length} 款原商品及其 ${impact.retainedMaterials} 条素材，原有预览继续保留，无需重复生成。`);
  if (impact.removed.length) rows.push(`移出 ${impact.removed.length} 款商品：${impact.removed.map(p => p.name).join('、')}。对应的 ${impact.removedMaterials} 条素材将从本计划预览和待投放内容中移出，素材中心文件不会因此删除。`);
  rows.push('生成用量提醒：已发生的生成用量不会因调整商品而撤销；如再次生成，将产生新的生成用量。');
  if (published) rows.push('本次先调整修改中的预览；点击“保存修改”并同步成功后，才会更新原投放计划。');
  return rows.join('\n\n');
}
