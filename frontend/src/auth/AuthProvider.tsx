import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { authApi } from '../api/resources';
import { SESSION_EXPIRED_EVENT } from '../api';
import { clearSession, expirationTime, readSession, saveSession } from './session';
import { navigate } from '../utils/router';
import type { User } from '../types/api';
import { ApiRequestError, errorMessage } from '../api/errors';

interface AuthState { status: 'loading' | 'authenticated' | 'anonymous'; user: User | null }
interface AuthContextValue extends AuthState { error: string | null; login: (email: string, password: string) => Promise<void>; logout: () => void; retry: () => void }
const AuthContext = createContext<AuthContextValue | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null });
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const generation = useRef(0);
  const logout = useCallback(() => {
    generation.current++;
    clearSession(window.sessionStorage);
    setState({ status: 'anonymous', user: null });
    setError(null);
    navigate('/login', true);
  }, []);
  useEffect(() => {
    window.addEventListener(SESSION_EXPIRED_EVENT, logout);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, logout);
  }, [logout]);
  useEffect(() => {
    const controller = new AbortController();
    const current = ++generation.current;
    const token = readSession(window.sessionStorage);
    if (!token) { setState({ status: 'anonymous', user: null }); return () => controller.abort(); }
    setState({ status: 'loading', user: null });
    setError(null);
    authApi.me(controller.signal).then(user => {
      if (!controller.signal.aborted && current === generation.current) setState({ status: 'authenticated', user });
    }).catch((failure: unknown) => {
      if (controller.signal.aborted || current !== generation.current) return;
      if (failure instanceof ApiRequestError && failure.status === 401) logout();
      else setError(errorMessage(failure));
    });
    return () => controller.abort();
  }, [version, logout]);
  useEffect(() => {
    if (state.status !== 'authenticated') return;
    const token = readSession(window.sessionStorage);
    const expiration = token ? expirationTime(token) : null;
    if (expiration === null) return;
    const timeout = window.setTimeout(logout, Math.max(0, expiration - Date.now()));
    return () => window.clearTimeout(timeout);
  }, [state.status, logout]);
  async function login(email: string, password: string) {
    const current = ++generation.current;
    const response = await authApi.login(email, password);
    if (current !== generation.current) return;
    saveSession(window.sessionStorage, response.accessToken);
    try {
      const user = await authApi.me();
      if (current !== generation.current) return;
      setState({ status: 'authenticated', user });
      setError(null);
      navigate('/dashboard', true);
    } catch (failure) {
      if (current === generation.current) { clearSession(window.sessionStorage); setState({ status: 'anonymous', user: null }); }
      throw failure;
    }
  }
  return <AuthContext.Provider value={{ ...state, error, login, logout, retry: () => setVersion(value => value + 1) }}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('AuthProvider is required');
  return auth;
}
