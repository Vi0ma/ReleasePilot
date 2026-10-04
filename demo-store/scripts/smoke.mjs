import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createApp } from '../server/app.mjs';
import { createReleasePilotClient } from '../server/releasePilotClient.mjs';

const fixture = JSON.parse(await readFile('.local/fixture.json', 'utf8'));
const baseUrl = process.env.RELEASEPILOT_BASE_URL;
const flagKey = process.env.RELEASEPILOT_FLAG_KEY;
async function api(path, method, body, token) {
  const response = await fetch(new URL(path, baseUrl), {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(10000),
  });
  assert.ok(response.ok, `ReleasePilot HTTP ${response.status} at ${path}`);
  return response.json();
}
const auth = await api('/api/v1/auth/login', 'POST', { email: fixture.email, password: fixture.password });
const project = await api(`/api/v1/projects/${fixture.projectId}`, 'GET', undefined, auth.accessToken);
assert.equal(project.key, 'demo-store');
console.log('Login and dedicated Demo Store project verified.');
const path = `/api/v1/flags/${fixture.flagId}/environments/${fixture.environmentId}`;
const original = await api(path, 'GET', undefined, auth.accessToken);
const update = (enabled, rolloutPercentage) => api(path, 'PATCH', { enabled, rolloutPercentage }, auth.accessToken);
async function evaluate(userKey, origin = process.env.DEMO_STORE_ORIGIN || 'http://127.0.0.1:5174') {
  const response = await fetch(`${origin}/api/feature-evaluation`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userKey }), signal: AbortSignal.timeout(6000),
  });
  assert.equal(response.status, 200);
  const text = await response.text();
  assert.ok(!text.includes(process.env.RELEASEPILOT_API_KEY));
  const result = JSON.parse(text);
  assert.deepEqual(Object.keys(result).sort(), ['enabled', 'flagKey', 'reason']);
  assert.equal(result.flagKey, flagKey);
  return result;
}
try {
  await update(false, 100);
  assert.equal((await evaluate('demo-user-smoke-a')).reason, 'FLAG_DISABLED');
  console.log('A OFF: DISABLED / FLAG_DISABLED -> V1');
  await update(true, 100);
  for (let i = 0; i < 12; i++) assert.equal((await evaluate(`demo-user-smoke-${i}`)).enabled, true);
  console.log('B ON/100: 12/12 visitors ENABLED -> V2');
  await update(true, 30);
  let enabled = 0; let disabled = 0;
  for (let i = 0; i < 40; i++) {
    const userKey = `demo-user-smoke-${i}`;
    const first = await evaluate(userKey);
    assert.deepEqual(await evaluate(userKey), first);
    if (first.enabled) { enabled++; assert.equal(first.reason, 'ROLLOUT_MATCH'); }
    else { disabled++; assert.equal(first.reason, 'ROLLOUT_MISS'); }
  }
  assert.ok(enabled > 0 && disabled > 0);
  console.log(`C ON/30: ${enabled} V2, ${disabled} V1; 40/40 repeated visitors stable`);
  await update(false, 30);
  assert.equal((await evaluate('demo-user-smoke-a')).reason, 'FLAG_DISABLED');
  console.log('D OFF again: V1 without restarting or rebuilding Demo Store');
  const audit = await api(`/api/v1/projects/${fixture.projectId}/audit?page=0&size=100`, 'GET', undefined, auth.accessToken);
  assert.ok(audit.content.some(event => event.action === 'FLAG_CONFIGURATION_UPDATED'));
  assert.ok(!JSON.stringify(audit).includes(process.env.RELEASEPILOT_API_KEY));
  console.log('Audit contains real configuration changes and no API key.');
  const invalid = createApp({ flagKey, client: createReleasePilotClient({ baseUrl, apiKey: 'rp_invalid.invalid' }) });
  await new Promise(resolve => invalid.listen(0, '127.0.0.1', resolve));
  try {
    const result = await evaluate('demo-user-smoke-a', `http://127.0.0.1:${invalid.address().port}`);
    assert.equal(result.enabled, false); assert.equal(result.reason, 'RELEASEPILOT_UNAVAILABLE');
    console.log('E invalid key: HTTP 200 / safe V1 fallback, no secret and no crash');
  } finally { invalid.closeAllConnections(); await new Promise(resolve => invalid.close(resolve)); }
} finally {
  await update(original.enabled, original.rolloutPercentage);
  console.log('Dedicated fixture configuration restored. Existing user projects untouched.');
}
