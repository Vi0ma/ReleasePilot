const reasons = new Set(['FLAG_DISABLED', 'FLAG_NOT_FOUND', 'ROLLOUT_MATCH', 'ROLLOUT_MISS']);

// This module is only imported by the server, never by the Vite application.
export function createReleasePilotClient({ baseUrl, apiKey, timeoutMs = 2500, fetchImpl = fetch }) {
  return {
    async evaluate(flagKey, userKey) {
      const fallback = { flagKey, enabled: false, reason: 'RELEASEPILOT_UNAVAILABLE' };
      try {
        if (!apiKey || apiKey === 'replace-me') return fallback;
        const url = new URL('/api/v1/evaluate', baseUrl);
        if (!['http:', 'https:'].includes(url.protocol)) return fallback;
        const response = await fetchImpl(url, {
          method: 'POST', redirect: 'error',
          headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-API-Key': apiKey },
          body: JSON.stringify({ flagKey, userKey }),
          signal: AbortSignal.timeout(timeoutMs),
        });
        if (!response.ok) return fallback;
        const data = await response.json();
        if (!data || data.flagKey !== flagKey || typeof data.enabled !== 'boolean' || !reasons.has(data.reason)
          || (data.enabled !== (data.reason === 'ROLLOUT_MATCH'))) return fallback;
        // Explicit projection: never forward upstream headers or extra fields.
        return { flagKey, enabled: data.enabled, reason: data.reason };
      } catch {
        // Do not log upstream bodies, configuration, errors or credentials.
        return fallback;
      }
    },
  };
}
