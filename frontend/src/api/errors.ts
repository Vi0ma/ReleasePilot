export class ApiRequestError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;
  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}
export function parseApiError(status: number, body: unknown): ApiRequestError {
  const fallback = status === 401 ? 'Your session has expired. Please sign in again.'
    : status === 404 ? 'This resource could not be found or is unavailable to your account.'
    : status >= 500 ? 'ReleasePilot could not complete this request. Please try again.' : 'The request could not be completed.';
  if (typeof body !== 'object' || body === null || status >= 500) return new ApiRequestError(status, fallback);
  const data = body as Record<string, unknown>;
  const fields: Record<string, string> = {};
  if (typeof data.fieldErrors === 'object' && data.fieldErrors !== null) {
    for (const [key, value] of Object.entries(data.fieldErrors)) if (typeof value === 'string') fields[key] = value;
  }
  return new ApiRequestError(status, typeof data.message === 'string' ? data.message : fallback, fields);
}
export function errorMessage(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : 'Could not connect to ReleasePilot. Check the backend connection and try again.';
}
