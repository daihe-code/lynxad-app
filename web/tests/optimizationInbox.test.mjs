import test from 'node:test';
import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';

let failure = false;
registerHooks({ resolve(specifier, context, next) {
  if (specifier === '@/api/http') return { url: 'data:text/javascript,export const apiGet=()=>{};export const apiPost=()=>{};export const apiPut=(...args)=>globalThis.monitoringSaveFixture(...args);', shortCircuit: true };
  if (specifier === '@/features/tenant/inboxRefresh') return { url: new URL('../src/features/tenant/inboxRefresh.ts', import.meta.url).href, shortCircuit: true };
  return next(specifier, context);
} });
globalThis.window = new EventTarget();
globalThis.monitoringSaveFixture = async () => { if (failure) throw new Error('Save rejected'); return { version: 2 }; };
const { saveOptimizationRules } = await import('../src/features/delivery/optimizationApi.ts');
const { INBOX_CHANGED } = await import('../src/features/tenant/inboxRefresh.ts');

test('only a successful rules save requests an immediate inbox refresh', async () => {
  let refreshes = 0;
  window.addEventListener(INBOX_CHANGED, () => refreshes++);
  assert.deepEqual(await saveOptimizationRules('plan', {}, true, 1), { version: 2 });
  assert.equal(refreshes, 1);
  failure = true;
  await assert.rejects(saveOptimizationRules('plan', {}, true, 1), /Save rejected/);
  assert.equal(refreshes, 1);
});
