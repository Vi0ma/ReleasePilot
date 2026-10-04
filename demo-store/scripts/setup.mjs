import { randomBytes, randomUUID } from 'node:crypto';
import { mkdir, writeFile, access } from 'node:fs/promises';

// Optional local fixture provisioning. Never overwrite an existing secret file.
try { await access('.env'); throw new Error('Existing .env: configure it manually; setup never overwrites it.'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const baseUrl = process.env.RELEASEPILOT_BASE_URL || 'http://localhost:8080';
async function request(path, body, token) {
  const response = await fetch(new URL(path, baseUrl), {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body), signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) throw new Error(`Fixture setup failed (${response.status}) at ${path}`);
  return response.json();
}
const email = `demo-store-${randomUUID()}@example.test`;
const password = randomBytes(24).toString('base64url');
await request('/api/v1/auth/register', { name: 'Demo Store local demo', email, password });
const auth = await request('/api/v1/auth/login', { email, password });
const project = await request('/api/v1/projects', { name: 'Demo Store', key: 'demo-store', description: 'Dedicated Phase 5 demonstration; safe to modify during smoke tests.' }, auth.accessToken);
const environmentsResponse = await fetch(new URL(`/api/v1/projects/${project.id}/environments`, baseUrl), { headers: { Authorization: `Bearer ${auth.accessToken}` } });
if (!environmentsResponse.ok) throw new Error('Environment setup failed');
const production = (await environmentsResponse.json()).find(environment => environment.key === 'production');
if (!production) throw new Error('Production environment missing');
const flag = await request(`/api/v1/projects/${project.id}/flags`, { key: 'new_checkout', name: 'New checkout', description: 'Switch Demo Store between classic V1 and compact V2.' }, auth.accessToken);
const key = await request(`/api/v1/projects/${project.id}/environments/${production.id}/api-keys`, { name: 'Demo Store BFF local' }, auth.accessToken);
await mkdir('.local', { recursive: true });
await writeFile('.local/fixture.json', JSON.stringify({ email, password, projectId: project.id, environmentId: production.id, flagId: flag.id }, null, 2), { flag: 'wx' });
await writeFile('.env', `RELEASEPILOT_BASE_URL=${baseUrl}\nRELEASEPILOT_API_KEY=${key.apiKey}\nRELEASEPILOT_FLAG_KEY=new_checkout\n`, { flag: 'wx' });
console.log(`Dedicated Demo Store created (project ${project.id}, Production ${production.id}, flag ${flag.id}).`);
console.log('Server key saved to ignored .env. Demo account credentials saved to ignored .local/fixture.json. No secrets printed.');
