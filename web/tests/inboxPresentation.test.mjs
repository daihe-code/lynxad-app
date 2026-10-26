import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
import test from 'node:test';

// Load the real presentation and watcher definitions without starting HTTP/store infrastructure.
registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === './inboxGeneration') return { url: new URL('../src/features/tenant/inboxGeneration.ts', import.meta.url).href, shortCircuit: true };
    if (specifier === '@/features/agentops/agentopsApi') return { url: new URL('../src/features/agentops/agentopsApi.ts', import.meta.url).href, shortCircuit: true };
    if (specifier === '@/api/http') return { url: 'data:text/javascript,export function apiGet(){throw new Error("Unexpected HTTP call")}', shortCircuit: true };
    return nextResolve(specifier, context);
  },
});
const presentation = await import('../src/features/tenant/inboxPresentation.ts');
const message = (id, bizType, unread = true) => ({ id, bizType, unread, title: id, body: '', sentAt: '2026-10-19T10:00:00Z' });

test('PRD four categories classify watcher, approval, optimization and system events', () => {
  assert.equal(typeof presentation.filterCategory, 'function', 'four-category mapping must exist');
  const cases = [
    ['TOKEN_EXPIRED', 'system'], ['DAILY_REPORT', 'system'], ['INSIGHT_REPORT', 'system'], ['PLAN_DAILY_REPORT', 'system'], ['PLAN_PUBLISHED', 'system'], ['SYSTEM', 'system'], [undefined, 'system'], ['FUTURE_EVENT', 'system'],
    ['ROI_DROP', 'warning'], ['BUDGET_EXHAUST', 'warning'], ['QC_ABNORMAL_PAUSE', 'warning'], ['OPTIMIZATION_ERROR', 'warning'],
    ['AUDIT_PASS', 'approval'], ['AUDIT_REJECT', 'approval'],
    ['OPTIMIZATION_ADVICE', 'optimization'], ['OPTIMIZATION_EXECUTION', 'optimization'], ['OPTIMIZATION_TAKEOVER', 'optimization'], ['OPTIMIZATION_RECOVERED', 'optimization'], ['CREATIVE_DONE', 'optimization'], ['CREATIVE_READY', 'optimization'], ['CREATIVE_FAIL', 'optimization'],
  ];
  for (const [type, expected] of cases) assert.equal(presentation.filterCategory(message('id', type)), expected, String(type));
});

test('category and unread filters compose without mutating inbox or unread total', () => {
  assert.equal(typeof presentation.filterInbox, 'function', 'composable inbox filtering must exist');
  const inbox = [message('s', 'TOKEN_EXPIRED'), message('w', 'ROI_DROP'), message('a', 'AUDIT_PASS', false), message('o', 'OPTIMIZATION_ADVICE')];
  assert.deepEqual(presentation.filterInbox(inbox, 'all', 'all').map(i => i.id), ['s', 'w', 'a', 'o']);
  for (const [category, expected] of [['system', ['s']], ['warning', ['w']], ['approval', ['a']], ['optimization', ['o']]]) {
    assert.deepEqual(presentation.filterInbox(inbox, 'all', category).map(i => i.id), expected);
  }
  assert.deepEqual(presentation.filterInbox(inbox, 'unread', 'all').map(i => i.id), ['s', 'w', 'o']);
  assert.deepEqual(presentation.filterInbox(inbox, 'unread', 'approval'), []);
  assert.deepEqual(presentation.filterInbox(inbox, 'unread', 'warning').map(i => i.id), ['w']);
  assert.deepEqual(presentation.filterInbox([], 'all', 'system'), []);
  assert.equal(inbox.filter(i => i.unread).length, 3);
  assert.equal(inbox.length, 4);
});

test('filter categories preserve business labels and destinations', () => {
  assert.deepEqual(presentation.inboxPresentation(message('s', 'TOKEN_EXPIRED')), { category: '授权', tone: 'authorization', action: '重新授权', destination: 'authorization' });
  assert.equal(presentation.inboxPresentation(message('a', 'AUDIT_PASS')).destination, 'plan');
  assert.equal(presentation.inboxPresentation(message('o', 'OPTIMIZATION_ADVICE')).destination, 'optimization');
  assert.equal(presentation.inboxPresentation(message('c', 'CREATIVE_READY')).category, '素材');
});
