export type Decision = { flagKey: string; enabled: boolean; reason: string };
export function checkoutVersion(decision: Decision): 'V1' | 'V2' { return decision.enabled ? 'V2' : 'V1'; }
export async function evaluateVisitor(userKey: string, signal?: AbortSignal): Promise<Decision> {
  try {
    const response = await fetch('/api/feature-evaluation', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userKey }), signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(5000)]) : AbortSignal.timeout(5000),
    });
    if (!response.ok) throw new Error('Evaluation failed');
    const data: unknown = await response.json();
    if (typeof data !== 'object' || !data || !('flagKey' in data) || typeof data.flagKey !== 'string'
      || !('enabled' in data) || typeof data.enabled !== 'boolean' || !('reason' in data) || typeof data.reason !== 'string') throw new Error('Invalid decision');
    return { flagKey: data.flagKey, enabled: data.enabled, reason: data.reason };
  } catch {
    return { flagKey: 'new_checkout', enabled: false, reason: 'RELEASEPILOT_UNAVAILABLE' };
  }
}
