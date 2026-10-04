import { createServer } from 'node:http';

export function createApp({ client, flagKey }) {
  return createServer(async (request, response) => {
    const send = (status, data) => {
      response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
      response.end(JSON.stringify(data));
    };
    if (request.url === '/api/health' && request.method === 'GET') return send(200, { status: 'UP' });
    if (request.url !== '/api/feature-evaluation') return send(404, { error: 'Not found' });
    if (request.method !== 'POST') return send(405, { error: 'Method not allowed' });
    if (request.headers['content-type']?.split(';')[0] !== 'application/json') return send(415, { error: 'Expected application/json' });
    try {
      const chunks = [];
      let bytes = 0;
      for await (const chunk of request) {
        bytes += chunk.length;
        if (bytes > 4096) return send(413, { error: 'Request too large' });
        chunks.push(chunk);
      }
      const data = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      if (!data || Array.isArray(data) || Object.keys(data).length !== 1 || typeof data.userKey !== 'string'
          || !data.userKey.trim() || data.userKey.length > 256) return send(400, { error: 'Provide only a non-empty userKey (maximum 256 characters)' });
      const result = await client.evaluate(flagKey, data.userKey);
      return send(200, { flagKey, enabled: result.enabled, reason: result.reason });
    } catch {
      return send(400, { error: 'Invalid request' });
    }
  });
}
