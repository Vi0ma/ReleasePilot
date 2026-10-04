import type { ReactNode } from 'react';
import { ApiRequestError, errorMessage } from '../api/errors';
import { Icon, type IconName } from './Icon';
export function Loading({ label = 'Loading workspace…' }: { label?: string }) {
  return <div className="loading-state" role="status"><span className="spinner" /><span>{label}</span><div className="skeleton" /><div className="skeleton short" /></div>;
}
export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  return <div className="error-panel" role="alert"><strong>Something went wrong</strong><p>{errorMessage(error)}</p>{retry && <button className="button secondary" onClick={retry}>Try again</button>}</div>;
}
export function FormError({ error }: { error: unknown }) {
  if (!error) return null;
  return <div className="form-error" role="alert">{error instanceof Error && !(error instanceof ApiRequestError) ? error.message : errorMessage(error)}</div>;
}
export function FieldError({ error, field }: { error: unknown; field: string }) {
  const message = error instanceof ApiRequestError ? error.fieldErrors[field] : null;
  return message ? <span className="field-error" role="alert">{message}</span> : null;
}
export function EmptyState({ icon = 'folder', title, description, action }: { icon?: IconName; title: string; description: string; action?: ReactNode }) {
  return <div className="empty-state"><span className="empty-icon"><Icon name={icon} size={28} /></span><h2>{title}</h2><p>{description}</p>{action}</div>;
}
export function EnvironmentBadge({ name, environmentKey }: { name: string; environmentKey: string }) {
  return <span className={`environment-badge ${['development', 'staging', 'production'].includes(environmentKey) ? environmentKey : ''}`}><span className="status-dot" />{name}</span>;
}
