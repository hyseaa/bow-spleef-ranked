// Spec 041: tests of cleanup-images.mjs (the same file in ../api/deploy/ and ../bot/deploy/).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { planCleanup } from './cleanup-images.mjs';

const at = (minutes) => new Date(Date.UTC(2026, 10, 1) + minutes * 60_000).toISOString();
const image = (id, minutes, tag = `sha${id}`) => ({ id, created_at: at(minutes), metadata: { container: { tags: [tag] } } });
const part = (id, minutes) => ({ id, created_at: at(minutes), metadata: { container: { tags: [] } } });
const ids = (list) => list.map((v) => v.id).sort((a, b) => a - b);

test('REQ-041-02: with no more images than kept, nothing is deleted', () => {
  const versions = [image(1, 0), part(2, 0), image(3, 100)];
  assert.deepEqual(planCleanup(versions, 10).remove, []);
});

test('REQ-041-02: the most recent images are kept, the older ones deleted', () => {
  const versions = Array.from({ length: 13 }, (_, i) => image(i + 1, i * 60));
  const { keep, remove } = planCleanup(versions, 10);
  assert.deepEqual(ids(remove), [1, 2, 3]);
  assert.equal(keep.length, 10);
});

test('REQ-041-02: the untagged parts of a kept image index stay, those of a deleted one go', () => {
  // images pushed before spec 041: an index (tagged) and its image and attestation (untagged), within a few seconds
  const versions = [
    image(1, 0), part(2, 0), part(3, 0),
    image(4, 600), part(5, 600), part(6, 600),
    image(7, 1200),
    image(8, 1800),
  ];
  const { keep, remove } = planCleanup(versions, 3);
  assert.deepEqual(ids(remove), [1, 2, 3]);
  assert.deepEqual(ids(keep), [4, 5, 6, 7, 8]);
});

test('REQ-041-02: a part pushed just before its index is kept with it', () => {
  const versions = [image(1, 0), part(2, 299), image(3, 300), image(4, 900)];
  assert.deepEqual(ids(planCleanup(versions, 2).remove), [1]);
});

test('REQ-041-02: KEEP must be at least 1', () => {
  assert.throws(() => planCleanup([], 0), /at least 1/);
});
