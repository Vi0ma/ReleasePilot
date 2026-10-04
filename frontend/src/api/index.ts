import { createApiClient } from './client';
import { clearSession, readSession } from '../auth/session';

export const SESSION_EXPIRED_EVENT = 'releasepilot:session-expired';
export const apiClient = createApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  getToken: () => readSession(window.sessionStorage),
  onUnauthorized: () => {
    clearSession(window.sessionStorage);
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  },
});
