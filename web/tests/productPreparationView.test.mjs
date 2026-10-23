import test from 'node:test';
import assert from 'node:assert/strict';
import { analysisCoverage, combinationHighlights, marketOverview, readableFact, safeSourceLink } from '../src/features/delivery/productPreparationView.ts';

const report={coverage:{usable_dimensions:['商品基础信息'],missing_dimensions:['近7天趋势']},combination:{hypotheses:[{need_or_scene:'外出便携与居家使用需分别测试'}],unanswered_questions:['确认推广规格']},product_analyses:[{creative_directions:[{title:'展示收纳分区'},{title:'外出使用演示'}]}],handoff:{budget:{grouping_considerations:['测试预算是否足以覆盖两个场景']}}};

test('组合摘要取自本次模型报告，数据覆盖与缺口分别说明',()=>{
  assert.equal(analysisCoverage(report),'当前依据：商品基础信息。暂缺：近7天趋势。');
  const highlights=combinationHighlights(report);
  assert.equal(highlights[0].description,'外出便携与居家使用需分别测试');
  assert.equal(highlights[1].description,'展示收纳分区、外出使用演示');
  assert.equal(highlights[2].description,'测试预算是否足以覆盖两个场景');
});

test('有市场证据展示原始观察，无数据只给合并说明，不制造指标',()=>{
  assert.equal(marketOverview({market_evidence:[{availability:'limited',observations:[{statement:'平台近7天搜索指数8200，适用自行车类目'}]}]}),'平台近7天搜索指数8200，适用自行车类目');
  assert.match(marketOverview({market_evidence:[{availability:'unavailable',observations:[]}]}),/暂未获取可核验/);
});

test('无查询结果时展示按商品生成的AI推断，有实测数据时保留实测结果',()=>{
  const inference={product_id:'bike',source:'product_semantics',is_estimate:true,competition:'可从折叠收纳与通勤用途比较替代商品',demand:'可测试通勤接驳与小空间收纳场景',price_positioning:null};
  assert.match(marketOverview({market_evidence:[],market_inferences:[inference]}),/^AI 推断：可从折叠收纳/);
  const mixed={market_evidence:[{availability:'limited',observations:[{statement:'平台样本12款'}]}],market_inferences:[inference]};
  assert.match(marketOverview(mixed),/^平台样本12款；缺失部分已补充/);
  assert.match(marketOverview({market_evidence:[],market_inferences:[{...inference,is_estimate:false}]}),/暂未获取可核验/);
});

test('商品价格与可投状态显示业务文字，零价不丢失，其他事实保持证据原文',()=>{
  const input={evidence:[{evidence_id:'p:price',field:'price',statement:'原始价格记录',value:{min:0,max:99}},{evidence_id:'p:availability',field:'availability',statement:'原始状态记录',value:{status:'available',reasons:[]}}]};
  assert.equal(readableFact({statement:'原始价格记录',evidence_ids:['p:price']},input),'商品价格：0–99元，推广规格与成交价格待确认');
  assert.equal(readableFact({statement:'原始状态记录',evidence_ids:['p:availability']},input),'可投状态：可投');
  assert.equal(readableFact({statement:'未经匹配的价格宣称',evidence_ids:['p:price']},input),'未经匹配的价格宣称');
});

test('来源仅允许无嵌入凭据的网页地址',()=>{
  assert.equal(safeSourceLink('https://example.com/report'),'https://example.com/report');
  for(const url of ['javascript:alert(1)','data:text/html,abc','https://user:password@example.com','file:///etc/passwd'])assert.equal(safeSourceLink(url),undefined);
});
