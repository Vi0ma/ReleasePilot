import test from 'node:test';
import assert from 'node:assert/strict';
import { readSession, saveSession, clearSession, expirationTime, routeAccess, SESSION_KEY } from '../src/auth/session.ts';
test('only JWT is stored and logout removes it', () => {
  const values = new Map();
  const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
  assert.equal(readSession(storage), null);
  saveSession(storage, 'test-jwt');
  assert.deepEqual([...values.keys()], [SESSION_KEY]);
  assert.equal(readSession(storage), 'test-jwt');
  clearSession(storage);
  assert.equal(readSession(storage), null);
});
test('blocked storage never pretends a session was persisted', () => {
  const storage = { getItem() { throw new Error('denied'); }, setItem() { throw new Error('denied'); }, removeItem() { throw new Error('denied'); } };
  assert.equal(readSession(storage), null);
  assert.throws(() => saveSession(storage, 'test-jwt'), /storage is unavailable/);
  assert.doesNotThrow(() => clearSession(storage));
});
test('protected routes wait for restoration and reject anonymous users', () => {
  assert.equal(routeAccess('loading', false), 'loading');
  assert.equal(routeAccess('anonymous', false), 'login');
  assert.equal(routeAccess('authenticated', false), 'allow');
});
test('auth pages are public and authenticated users return to dashboard', () => {
  assert.equal(routeAccess('anonymous', true), 'allow');
  assert.equal(routeAccess('authenticated', true), 'dashboard');
});
test('expiration is read without treating client claims as verified identity', () => {
  const token = `header.${btoa(JSON.stringify({ exp: 1000 }))}.signature`;
  assert.equal(expirationTime(token), 1000000);
  assert.equal(expirationTime('invalid'), null);
  assert.equal(expirationTime(`header.${btoa('{}')}.signature`), null);
});
