import test from 'node:test';
import assert from 'node:assert/strict';
import { shouldCommitRollout } from '../src/features/flags/rollout.ts';
test('rollout commits only changes, including exact 0 and 100 edges', () => {
  assert.equal(shouldCommitRollout(30, 30), false);
  assert.equal(shouldCommitRollout(30, 0), true);
  assert.equal(shouldCommitRollout(30, 100), true);
  assert.equal(shouldCommitRollout(0, 30), true);
});
test('out of range, fractional or non-finite percentages are not sent', () => {
  for (const value of [-1, 101, 30.5, NaN, Infinity]) assert.equal(shouldCommitRollout(0, value), false);
});
