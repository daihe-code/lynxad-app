import test from 'node:test';
import assert from 'node:assert/strict';
import { persistImageBatch } from '../src/features/creative/imageUploadBatch.ts';

test('each product card uses one image and chains the server revision, including duplicate selections', async () => {
  const writes = [], receipts = [];
  const state = await persistImageBatch({ revision: 7 }, ['a', 'b', 'a', 'c'], async (current, id) => {
    writes.push([current.revision, id]);
    return { revision: current.revision + 1 };
  }, (id, next) => receipts.push([id, next.revision]));
  assert.deepEqual(writes, [[7, 'a'], [8, 'b'], [9, 'c']]);
  assert.deepEqual(receipts, [['a', 8], ['b', 9], ['c', 10]]);
  assert.equal(state.revision, 10);
});

test('a partial failure preserves successful receipts; retry only writes the remaining images', async () => {
  let latest = { revision: 1 };
  const added = new Set(), writes = [];
  const accepted = (id, state) => { added.add(id); latest = state; };
  await assert.rejects(persistImageBatch(latest, ['a', 'b', 'c'], async (state, id) => {
    writes.push(id);
    if (id === 'b') throw new Error('offline');
    return { revision: state.revision + 1 };
  }, accepted), /offline/);
  assert.deepEqual([...added], ['a']);
  assert.equal(latest.revision, 2);
  await persistImageBatch(latest, ['a', 'b', 'c'].filter(id => !added.has(id)), async (state, id) => {
    writes.push(id); return { revision: state.revision + 1 };
  }, accepted);
  assert.deepEqual(writes, ['a', 'b', 'b', 'c']);
  assert.deepEqual([...added], ['a', 'b', 'c']);
  assert.equal(latest.revision, 4);
});
