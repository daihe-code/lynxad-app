interface Product { productId: string; qcProductId: string; name: string }
interface Material { productId: string }
interface AdjustmentContext { materialCount: number; hasAnalysis: boolean; hasGoal: boolean; published?: boolean }

export function productAdjustmentText(context: AdjustmentContext) {
  const rows = ['实际增删或替换商品后，将产生以下影响：'];
  if (context.materialCount > 0) {
    rows.push(`当前计划已有 ${context.materialCount} 条可用素材。删除或替换商品后，被移出商品的素材不会保留在本计划预览和待投放内容中；素材中心原文件仍保留。`);
    rows.push('增加商品时，保留商品已生成的素材继续保留，可继续使用，也可重新生成；新增商品需要生成或添加素材。');
    rows.push('已发生的生成用量不会退回；重新生成会产生新的生成用量。');
  } else rows.push('当前计划暂无可用素材，调整商品后可为所选商品生成或添加素材。');
  if (context.hasAnalysis) rows.push('原智能分析结果将失效，需要按调整后的商品组合重新分析。');
  if (context.hasGoal) rows.push(context.published
    ? '原计划的预算和 ROI 目标会保留，需要按新的商品组合重新核对；如需修改，请在计划的目标设置中调整。'
    : '已设置的投放目标将清空，需要重新设置预算、ROI 等投放目标。');
  if (context.published) rows.push('点击“保存修改”并同步成功后，才会更新原投放计划。');
  rows.push('点击确定仅进入商品调整，尚未增删或替换商品时，当前商品、素材和设置不会改变。');
  return rows.join('\n\n');
}

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
