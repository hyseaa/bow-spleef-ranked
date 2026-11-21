// Spec 041: tests of cleanup-images.mjs (the same file in ../api/deploy/ and ../bot/deploy/).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { planCleanup } from './cleanup-images.mjs';

const at = (minutes) => new Date(Date.UTC(2026, 10, 1) + minutes * 60_000).toISOString();
// commit c<n> is the n-th commit of main: the history is newest first
const history = (n) => Array.from({ length: n }, (_, i) => `c${n - i}`);
const image = (id, minutes, ...tags) => ({ id, created_at: at(minutes), metadata: { container: { tags } } });
const part = (id, minutes) => ({ id, created_at: at(minutes), metadata: { container: { tags: [] } } });
const ids = (list) => list.map((v) => v.id).sort((a, b) => a - b);

test('REQ-041-02: with no more images than kept, nothing is deleted', () => {
  const versions = [image(1, 0, 'c1'), part(2, 0), image(3, 100, 'c2')];
  assert.deepEqual(planCleanup(versions, 10, history(2)).remove, []);
});

test('REQ-041-02: the images of the most recent commits are kept, the older ones deleted', () => {
  const versions = Array.from({ length: 13 }, (_, i) => image(i + 1, i * 60, `c${i + 1}`));
  const { keep, remove } = planCleanup(versions, 10, history(13));
  assert.deepEqual(ids(remove), [1, 2, 3]);
  assert.equal(keep.length, 10);
});

test('REQ-041-02: the untagged parts of a kept image index stay, those of a deleted one go', () => {
  // images pushed before spec 041: an index (tagged) and its image and attestation (untagged), within a few seconds
  const versions = [
    image(1, 0, 'c1'), part(2, 0), part(3, 0),
    image(4, 600, 'c2'), part(5, 600), part(6, 600),
    image(7, 1200, 'c3'),
    image(8, 1800, 'c4'),
  ];
  const { keep, remove } = planCleanup(versions, 3, history(4));
  assert.deepEqual(ids(remove), [1, 2, 3]);
  assert.deepEqual(ids(keep), [4, 5, 6, 7, 8]);
});

test('REQ-041-02: an old version that got the newest tag (the same image pushed again) is kept as the newest', () => {
  // GHCR keeps one version per digest: an identical image pushed later adds its tag to the old version, whose dates
  // do not change (the bot's package, 2026-11-21)
  const versions = [image(1, 0, 'c1', 'c5'), image(2, 1000, 'c2'), image(3, 2000, 'c3'), image(4, 3000, 'c4')];
  const { keep, remove } = planCleanup(versions, 2, history(5));
  assert.deepEqual(ids(keep), [1, 4]);
  assert.deepEqual(ids(remove), [2, 3]);
});

test('REQ-041-02: a version whose tags are no commit of main is never deleted', () => {
  const versions = [image(1, 0, 'manual-tag'), image(2, 10, 'c1'), image(3, 20, 'c2')];
  const { keep, remove } = planCleanup(versions, 1, history(2));
  assert.deepEqual(ids(keep), [1, 3]);
  assert.deepEqual(ids(remove), [2]);
});

test('REQ-041-02: KEEP and the history are required', () => {
  assert.throws(() => planCleanup([], 0, history(1)), /at least 1/);
  assert.throws(() => planCleanup([], 1, []), /history of main/);
});
