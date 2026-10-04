import { createReleasePilotClient } from './releasePilotClient.mjs';
import { createApp } from './app.mjs';

const flagKey = process.env.RELEASEPILOT_FLAG_KEY || 'new_checkout';
if (!/^[a-z0-9_-]{1,100}$/.test(flagKey)) throw new Error('Invalid RELEASEPILOT_FLAG_KEY');
const client = createReleasePilotClient({
  baseUrl: process.env.RELEASEPILOT_BASE_URL || 'http://localhost:8080',
  apiKey: process.env.RELEASEPILOT_API_KEY,
});
const server = createApp({ client, flagKey });
server.requestTimeout = 5000;
const host = process.env.DEMO_BFF_HOST || '127.0.0.1';
server.listen(3001, host, () => console.log(`Demo Store BFF listening on ${host}:3001`));
