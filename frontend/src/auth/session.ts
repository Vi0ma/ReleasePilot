export const SESSION_KEY = 'releasepilot.access-token';
export function readSession(storage: Pick<Storage, 'getItem'>): string | null {
  try { return storage.getItem(SESSION_KEY); } catch { return null; }
}
export function saveSession(storage: Pick<Storage, 'setItem'>, token: string): void {
  try { storage.setItem(SESSION_KEY, token); }
  catch { throw new Error('Browser session storage is unavailable. Enable it to sign in.'); }
}
export function clearSession(storage: Pick<Storage, 'removeItem'>): void {
  try { storage.removeItem(SESSION_KEY); } catch { /* No stored token can be read when storage is disabled. */ }
}
export function expirationTime(token: string): number | null {
  try {
    const encoded = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload: unknown = JSON.parse(atob(encoded));
    if (typeof payload !== 'object' || payload === null || !('exp' in payload)) return null;
    return typeof payload.exp === 'number' && Number.isFinite(payload.exp) ? payload.exp * 1000 : null;
  } catch { return null; }
}
export function routeAccess(status: 'loading' | 'authenticated' | 'anonymous', isPublic: boolean) {
  if (status === 'loading') return 'loading';
  if (isPublic) return status === 'authenticated' ? 'dashboard' : 'allow';
  return status === 'authenticated' ? 'allow' : 'login';
}
