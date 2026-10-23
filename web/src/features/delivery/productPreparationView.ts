import type { ProductAnalysis } from './creationApi';
import type { AnalysisFact, ProductSelectionAnalysis } from './productSelectionAnalysis';

export function analysisCoverage(report?: ProductSelectionAnalysis): string {
  if (!report) return '当前依据：已选商品信息；其他分析依据请核对保存的报告。';
  const usable = report.coverage.usable_dimensions.join('、') || '已选商品基础信息';
  const missing = report.coverage.missing_dimensions.join('、');
  return `当前依据：${usable}。${missing ? `暂缺：${missing}。` : ''}`;
}

export function combinationHighlights(report?: ProductSelectionAnalysis) {
  return [
    {icon:'grid' as const,title:'核对需求与场景',description:report?.combination.hypotheses.map(h=>h.need_or_scene).join('；') || '逐商品确认适用需求与使用场景，再判断是否合投。'},
    {icon:'image' as const,title:'建议逐商品准备素材',description:[...new Set(report?.product_analyses.flatMap(p=>p.creative_directions.map(d=>d.title)) || [])].slice(0,3).join('、') || '根据各商品的特点，分别准备对应的素材方向。'},
    {icon:'settings' as const,title:'预算设置后再确认分组',description:report?.handoff.budget.grouping_considerations[0] || report?.combination.unanswered_questions[0] || '结合经营目标、历史表现和测试预算，再决定是否拆分计划。'},
  ];
}

export function marketOverview(report?: ProductSelectionAnalysis): string {
  const usable = report?.market_evidence.filter(row=>row.availability !== 'unavailable') || [];
  const inferred = marketInferences(report);
  if (usable.length) return (usable.flatMap(row=>row.observations.map(o=>o.statement)).join('；') || '本次取得部分市场参考，请结合数据范围和来源查看。')
    + (inferred.length ? '；缺失部分已补充商品信息的 AI 推断，可查看分析依据。' : '');
  if (inferred.length) return 'AI 推断：' + inferred.flatMap(row=>[row.competition,row.demand,row.price_positioning].filter(Boolean)).slice(0,3).join('；');
  return '暂未获取可核验的同类竞争与近7天趋势数据，当前不据此判断商品优劣。';
}

export function marketInferences(report?: ProductSelectionAnalysis) {
  return report?.market_inferences?.filter(row=>row.source === 'product_semantics' && row.is_estimate === true
    && [row.competition,row.demand,row.price_positioning].some(Boolean)) || [];
}

export function safeSourceLink(value?: string | null): string | undefined {
  try {const url=new URL(value || '');if(['https:','http:'].includes(url.protocol)&&!url.username&&!url.password)return url.href;} catch { /* A platform record need not have a public link. */ }
}

export function readableFact(fact: AnalysisFact, input?: ProductAnalysis['analysis_input']): string {
  const record=input?.evidence.find(row=>fact.evidence_ids.includes(row.evidence_id)&&row.statement===fact.statement);
  const value=record?.value;
  if (record?.field==='price'&&value&&typeof value==='object') {
    const price=value as {min?:number|null;max?:number|null};
    if (price.min != null && price.max != null) return `商品价格：${price.min===price.max ? price.min : `${price.min}–${price.max}`}元${price.min!==price.max?'，推广规格与成交价格待确认':''}`;
  }
  if (record?.field==='availability'&&value&&typeof value==='object') {
    const state=value as {status?:string;reasons?:string[]};
    return `可投状态：${state.status==='available'?'可投':state.status==='unavailable'?'暂不可投':'待确认'}${state.reasons?.length?`（${state.reasons.join('、')}）`:''}`;
  }
  return fact.statement;
}
