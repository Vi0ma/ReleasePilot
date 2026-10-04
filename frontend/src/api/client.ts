import { ApiRequestError, parseApiError } from './errors.ts';

interface ClientOptions {
  baseUrl: string;
  getToken: () => string | null;
  onUnauthorized: () => void;
  fetcher?: typeof fetch;
}
interface RequestOptions { signal?: AbortSignal; public?: boolean }
export function createApiClient(options: ClientOptions) {
  const baseUrl = options.baseUrl.replace(/\/$/, '');
  async function request<T>(method: string, path: string, body?: unknown, config: RequestOptions = {}): Promise<T> {
    const token = config.public ? null : options.getToken();
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (token) headers.Authorization = `Bearer ${token}`;
    let response: Response;
    try {
      response = await (options.fetcher ?? fetch)(`${baseUrl}${path}`, {
        method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        signal: config.signal, credentials: 'omit', cache: 'no-store',
      });
    } catch (error) {
      if (config.signal?.aborted) throw error;
      throw new ApiRequestError(0, 'Could not connect to ReleasePilot. Check the backend connection and try again.');
    }
    let data: unknown;
    if (response.status !== 204) {
      try { data = await response.json(); } catch { data = undefined; }
    }
    if (!response.ok) {
      // A delayed failure from an old session must not log out a newly signed-in account.
      if (response.status === 401 && token && options.getToken() === token) options.onUnauthorized();
      throw parseApiError(response.status, data);
    }
    if (response.status !== 204 && data === undefined) throw new ApiRequestError(502, 'The server returned an unreadable response. Please try again.');
    return data as T;
  }
  return {
    get: <T>(path: string, config?: RequestOptions) => request<T>('GET', path, undefined, config),
    post: <T>(path: string, body: unknown, config?: RequestOptions) => request<T>('POST', path, body, config),
    put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
    patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
    delete: (path: string) => request<void>('DELETE', path),
  };
}
