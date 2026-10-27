import test from 'node:test';
import assert from 'node:assert/strict';
import { campaignTrendScope, metricPlot, smoothMetricPath } from '../src/features/delivery/campaignMetricTrend.ts';

test('trend scope follows dates, template and all business filters but ignores sorting', () => {
  const f = { startDate: '2026-10-09', endDate: '2026-10-09', metricTemplate: 'NET', accountId: 'a', keyword: '商品', status: 'PAUSED', platform: 'ALL', bidType: 'CUSTOM', createdWithin: 7 };
  assert.equal(campaignTrendScope(f), campaignTrendScope({ ...f, sortField: 'netRoi', descending: true }));
  for (const [key, value] of Object.entries(f)) assert.notEqual(campaignTrendScope(f), campaignTrendScope({ ...f, [key]: typeof value === 'number' ? value + 1 : value + 'x' }));
});
test('missing buckets break a line while true zero remains a plotted value', () => {
  const plot = metricPlot([{ time: '2026-10-09 00:00:00', metrics: { cost: 0 } }, { time: '2026-10-09 01:00:00', metrics: { cost: null } }, { time: '2026-10-09 02:00:00', metrics: { cost: 20 } }], 'cost');
  assert.equal(plot.hasData, true); assert.equal(plot.values[0].y, 208); assert.equal(plot.values[1].y, null);
  assert.equal((plot.path.match(/M/g) || []).length, 2); assert.equal(plot.path.includes('L'), false);
  assert.equal(plot.values[0].x, 64); assert.equal(plot.values[2].x, 600);
});
test('unavailable net data is empty even when payment figures exist', () => {
  const plot = metricPlot([{ time: '2026-10-09', metrics: { gmv: 1000, roi: 10 } }], 'netGmv');
  assert.equal(plot.hasData, false); assert.equal(plot.path.trim(), ''); assert.equal(plot.values[0].value, null);
});
test('single and all-zero series remain visible with a nonzero axis range', () => {
  const plot = metricPlot([{ time: '2026-10-09', metrics: { refundRate: 0 } }], 'refundRate');
  assert.equal(plot.hasData, true); assert.equal(plot.values[0].x, 332); assert.equal(plot.values[0].y, 208);
  assert.equal(plot.ticks.at(-1).value, 1); assert.equal(plot.path.includes('NaN'), false);
});

test('smooth curves pass through original points and keep control points within adjacent values', () => {
  const points=[{x:0,y:208},{x:20,y:208},{x:30,y:30},{x:50,y:30},{x:60,y:208},{x:90,y:208},{x:100,y:200}];
  const path=smoothMetricPath(points),commands=[...path.matchAll(/C([\d., -]+)/g)];
  assert.equal(commands.length,points.length-1);
  for(let i=0;i<commands.length;i++) {
    const values=commands[i][1].trim().split(/[ ,]+/).map(Number),previous=points[i],next=points[i+1];
    assert.deepEqual(values.slice(4),[next.x,next.y]);
    for(const y of [values[1],values[3]])assert.ok(y>=Math.min(previous.y,next.y)-.001&&y<=Math.max(previous.y,next.y)+.001);
    for(const x of [values[0],values[2]])assert.ok(x>=previous.x&&x<=next.x);
  }
});

test('missing values stay separate and isolated real points remain visible', () => {
  const plot=metricPlot([0,null,30,30,0,null,2].map((cost,i)=>({time:`2026-10-${String(i+1).padStart(2,'0')}`,metrics:{cost}})),'cost');
  assert.equal((plot.path.match(/M/g)||[]).length,3);
  assert.equal((plot.path.match(/C/g)||[]).length,2);
  assert.deepEqual(plot.values.filter(p=>p.isolated).map(p=>p.value),[0,2]);
  assert.equal(plot.path.includes('NaN'),false);
});

test('flat and irregularly spaced samples do not overshoot or generate invalid paths', () => {
  for(const points of [[{x:1,y:5}],[{x:0,y:5},{x:2,y:5},{x:100,y:5}],[{x:1,y:0},{x:1,y:5},{x:2,y:10}]]) {
    const path=smoothMetricPath(points);assert.ok(path.startsWith('M'));assert.equal(/NaN|Infinity/.test(path),false);
  }
});
