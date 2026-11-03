import test from 'node:test';
import assert from 'node:assert/strict';
import { persistUploadBatch, localUploadSelection } from '../src/features/creative/localUploadBatch.ts';

test('video and product-card batches each send one correctly typed material per file', async () => {
  for (const type of ['VIDEO', 'IMAGE']) {
    const writes = [];
    await persistUploadBatch({ revision: 1 }, ['first', 'second'], async (state, id) => {
      writes.push({ revision: state.revision, type, ...localUploadSelection(type, id) });
      return { revision: state.revision + 1 };
    }, () => {});
    assert.deepEqual(writes, type === 'VIDEO'
      ? [{ revision: 1, type, videoAssetId: 'first' }, { revision: 2, type, videoAssetId: 'second' }]
      : [{ revision: 1, type, imageAssetIds: ['first'] }, { revision: 2, type, imageAssetIds: ['second'] }]);
  }
});

test('each product card uses one image and chains the server revision, including duplicate selections', async () => {
  const writes = [], receipts = [];
  const state = await persistUploadBatch({ revision: 7 }, ['a', 'b', 'a', 'c'], async (current, id) => {
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
  await assert.rejects(persistUploadBatch(latest, ['a', 'b', 'c'], async (state, id) => {
    writes.push(id);
    if (id === 'b') throw new Error('offline');
    return { revision: state.revision + 1 };
  }, accepted), /offline/);
  assert.deepEqual([...added], ['a']);
  assert.equal(latest.revision, 2);
  await persistUploadBatch(latest, ['a', 'b', 'c'].filter(id => !added.has(id)), async (state, id) => {
    writes.push(id); return { revision: state.revision + 1 };
  }, accepted);
  assert.deepEqual(writes, ['a', 'b', 'b', 'c']);
  assert.deepEqual([...added], ['a', 'b', 'c']);
  assert.equal(latest.revision, 4);
});

test('carousel batches bind the composite ID once and acknowledge only a completed plan write', async () => {
  const receipts = [], writes = [];
  let finish;
  const pending = persistUploadBatch({ revision: 5 }, ['bundle', 'bundle'], async (state, id) => {
    writes.push({ revision: state.revision, type: 'CAROUSEL', ...localUploadSelection('CAROUSEL', id) });
    await new Promise(resolve => { finish = resolve; });
    return { revision: 6 };
  }, id => receipts.push(id));
  assert.deepEqual(writes, [{ revision: 5, type: 'CAROUSEL', carouselId: 'bundle' }]);
  assert.deepEqual(receipts, []);
  finish(); await pending;
  assert.deepEqual(receipts, ['bundle']);
});
