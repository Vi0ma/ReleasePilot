import test from 'node:test';
import assert from 'node:assert/strict';
import { createApiClient } from '../src/api/client.ts';
import { ApiRequestError, parseApiError } from '../src/api/errors.ts';

const json = (status, body) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
test('dashboard requests send the token, JSON and omit cookies', async () => {
  let call;
  const client = createApiClient({ baseUrl: 'http://localhost:8080/', getToken: () => 'test-token', onUnauthorized() {}, fetcher: async (url, config) => { call = { url, config }; return json(200, { id: 1 }); } });
  assert.deepEqual(await client.post('/api/v1/projects', { name: 'Project' }), { id: 1 });
  assert.equal(call.url, 'http://localhost:8080/api/v1/projects');
  assert.equal(call.config.headers.Authorization, 'Bearer test-token');
  assert.equal(call.config.headers['Content-Type'], 'application/json');
  assert.equal(call.config.credentials, 'omit');
  assert.equal(call.config.cache, 'no-store');
});
test('login 401 does not end another session and public calls never send JWT', async () => {
  let notified = 0;
  const client = createApiClient({ baseUrl: '', getToken: () => 'token', onUnauthorized() { notified++; }, fetcher: async (_, config) => { assert.equal(config.headers.Authorization, undefined); return json(401, { message: 'Invalid credentials' }); } });
  await assert.rejects(client.post('/api/v1/auth/login', {}, { public: true }), error => error.status === 401 && error.message === 'Invalid credentials');
  assert.equal(notified, 0);
});
test('protected 401 invokes central session invalidation', async () => {
  let notified = 0;
  const client = createApiClient({ baseUrl: '', getToken: () => 'token', onUnauthorized() { notified++; }, fetcher: async () => json(401, {}) });
  await assert.rejects(client.get('/api/v1/projects'), ApiRequestError);
  assert.equal(notified, 1);
});
test('a late 401 from an old token does not clear a new session', async () => {
  let token = 'old';
  let notified = 0;
  const client = createApiClient({ baseUrl: '', getToken: () => token, onUnauthorized() { notified++; }, fetcher: async () => { token = 'new'; return json(401, {}); } });
  await assert.rejects(client.get('/api/v1/projects'), ApiRequestError);
  assert.equal(notified, 0);
});
test('204 deletion is handled without attempting to parse JSON', async () => {
  const client = createApiClient({ baseUrl: '', getToken: () => null, onUnauthorized() {}, fetcher: async () => new Response(null, { status: 204 }) });
  assert.equal(await client.delete('/api/v1/projects/1'), undefined);
});
test('backend validation fields are parsed without leaking arbitrary values', () => {
  const error = parseApiError(400, { message: 'Invalid request fields', fieldErrors: { name: 'must not be blank', nested: { private: true } } });
  assert.equal(error.message, 'Invalid request fields');
  assert.deepEqual(error.fieldErrors, { name: 'must not be blank' });
});
test('malformed and internal errors receive safe messages', () => {
  assert.match(parseApiError(409, null).message, /could not/);
  const error = parseApiError(500, { message: 'Stacktrace and secrets', fieldErrors: { password: 'secret' } });
  assert.doesNotMatch(error.message, /Stacktrace|secrets/);
  assert.deepEqual(error.fieldErrors, {});
});
test('invalid success JSON is reported as an API error', async () => {
  const client = createApiClient({ baseUrl: '', getToken: () => null, onUnauthorized() {}, fetcher: async () => new Response('not JSON', { status: 200 }) });
  await assert.rejects(client.get('/api/v1/projects'), error => error.status === 502);
});
test('network failures do not expose the underlying exception', async () => {
  const client = createApiClient({ baseUrl: '', getToken: () => null, onUnauthorized() {}, fetcher: async () => { throw new Error('internal-secret'); } });
  await assert.rejects(client.get('/api/v1/projects'), error => error.status === 0 && !error.message.includes('internal-secret'));
});
