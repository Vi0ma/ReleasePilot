import { test } from 'node:test';
import { createServer } from 'node:http';
import assert from 'node:assert/strict';
import { createReleasePilotClient } from '../server/releasePilotClient.mjs';
import { createApp } from '../server/app.mjs';
import { checkoutVersion, evaluateVisitor } from '../src/evaluation.ts';
import { getVisitor, newVisitor } from '../src/visitor.ts';
import { cartTotal } from '../src/catalog.ts';

const config = { baseUrl: 'http://localhost:8080', apiKey: 'server-only-secret' };
const decision = { flagKey: 'new_checkout', enabled: true, reason: 'ROLLOUT_MATCH' };
test('server sends X-API-Key and exact flagKey/userKey; projects only public fields', async () => {
  const client = createReleasePilotClient({ ...config, fetchImpl: async (url, options) => {
    assert.equal(url.href, 'http://localhost:8080/api/v1/evaluate');
    assert.equal(options.headers['X-API-Key'], config.apiKey);
    assert.equal(options.method, 'POST');
    assert.equal(options.redirect, 'error');
    assert.deepEqual(JSON.parse(options.body), { flagKey: 'new_checkout', userKey: 'demo-user-a' });
    return Response.json({ ...decision, apiKey: config.apiKey, secret: config.apiKey });
  } });
  assert.deepEqual(await client.evaluate('new_checkout', 'demo-user-a'), decision);
});
test('true selects V2; false selects V1', () => {
  assert.equal(checkoutVersion(decision), 'V2');
  assert.equal(checkoutVersion({ ...decision, enabled: false }), 'V1');
});
for (const [name, fetchImpl] of [
  ['unauthorized', async () => new Response('secret upstream diagnostic', { status: 401 })],
  ['unavailable', async () => { throw new Error(config.apiKey); }],
  ['malformed JSON', async () => new Response('not json')],
  ['wrong flag', async () => Response.json({ ...decision, flagKey: 'other' })],
  ['invalid enabled', async () => Response.json({ ...decision, enabled: 'true' })],
  ['invalid reason', async () => Response.json({ ...decision, reason: config.apiKey })],
  ['contradictory decision', async () => Response.json({ ...decision, reason: 'FLAG_DISABLED' })],
]) test(`${name} gives safe V1 without leaking credentials`, async () => {
  const result = await createReleasePilotClient({ ...config, fetchImpl }).evaluate('new_checkout', 'demo-user-a');
  assert.deepEqual(result, { flagKey: 'new_checkout', enabled: false, reason: 'RELEASEPILOT_UNAVAILABLE' });
  assert.equal(checkoutVersion(result), 'V1');
});
test('real HTTP timeout gives fallback V1', async t => {
  const server = createServer((_request, response) => { setTimeout(() => { response.writeHead(200, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(decision)); }, 150); });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); server.close(); });
  const client = createReleasePilotClient({ ...config, baseUrl: `http://127.0.0.1:${server.address().port}`, timeoutMs: 25 });
  assert.equal(checkoutVersion(await client.evaluate('new_checkout', 'demo-user-timeout')), 'V1');
});
test('BFF keeps key server-side, ignores upstream extra fields, validates input', async t => {
  const server = createApp({ flagKey: 'new_checkout', client: { evaluate: async (flagKey, userKey) => {
    assert.equal(flagKey, 'new_checkout'); assert.equal(userKey, 'demo-user-test');
    return { ...decision, apiKey: config.apiKey };
  } } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); server.close(); });
  const url = `http://127.0.0.1:${server.address().port}/api/feature-evaluation`;
  const post = body => fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const response = await post({ userKey: 'demo-user-test' });
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await response.json(), decision);
  for (const body of [{ userKey: '' }, { userKey: 'a'.repeat(257) }, { userKey: 'demo-user-test', apiKey: 'override' }, null, []]) assert.equal((await post(body)).status, 400);
  assert.equal((await fetch(url)).status, 405);
});
test('visitor persists across calls and new visitor gets a distinct ID', () => {
  const map = new Map(); const storage = { getItem: key => map.get(key), setItem: (key, value) => map.set(key, value) };
  assert.equal(getVisitor(storage, () => 'first'), 'demo-user-first');
  assert.equal(getVisitor(storage, () => 'ignored'), 'demo-user-first');
  assert.equal(newVisitor(storage, () => 'second'), 'demo-user-second');
  assert.equal(getVisitor(storage), 'demo-user-second');
});
test('blocked storage still generates a usable visitor', () => {
  assert.equal(getVisitor({ getItem() { throw Error(); }, setItem() { throw Error(); } }, () => 'session'), 'demo-user-session');
});
test('browser sends only visitor ID and uses fallback when BFF fails', async t => {
  const original = globalThis.fetch; t.after(() => { globalThis.fetch = original; });
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/feature-evaluation');
    assert.deepEqual(JSON.parse(options.body), { userKey: 'demo-user-browser' });
    assert.equal(options.headers['X-API-Key'], undefined);
    return Response.json(decision);
  };
  assert.deepEqual(await evaluateVisitor('demo-user-browser'), decision);
  globalThis.fetch = async () => { throw Error(); };
  assert.equal(checkoutVersion(await evaluateVisitor('demo-user-browser')), 'V1');
});
test('cart total uses actual selected quantities', () => { assert.equal(cartTotal({ bag: 2, cup: 1 }), 16400); });
