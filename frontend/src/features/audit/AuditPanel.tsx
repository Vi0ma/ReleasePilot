import { useState } from 'react';
import type { AuditAction, AuditEvent, Environment } from '../../types/api';
import { auditApi } from '../../api/resources';
import { useAuth } from '../../auth/AuthProvider';
import { useResource } from '../../hooks/useResource';
import { EmptyState, ErrorState, Loading } from '../../components/Feedback';
import { dateTime, initials } from '../../utils/format';
const labels: Record<AuditAction, string> = {
  PROJECT_CREATED: 'Created project', PROJECT_UPDATED: 'Updated project', PROJECT_DELETED: 'Deleted project',
  FLAG_CREATED: 'Created feature flag', FLAG_UPDATED: 'Updated feature flag', FLAG_DELETED: 'Deleted feature flag',
  FLAG_CONFIGURATION_UPDATED: 'Updated flag configuration', API_KEY_CREATED: 'Generated API key', API_KEY_REVOKED: 'Revoked API key',
};
function object(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : {};
}
function details(event: AuditEvent, environments: Environment[]) {
  const resource = object(event.metadata.resource);
  const before = object(event.metadata.before);
  const after = object(event.metadata.after);
  const name = typeof resource.key === 'string' ? resource.key : typeof after.key === 'string' ? after.key : typeof event.metadata.name === 'string' ? event.metadata.name : `${event.entityType.toLowerCase().replace(/_/g, ' ')} #${event.entityId}`;
  const environment = environments.find(item => item.id === event.metadata.environmentId);
  const changes: string[] = [];
  if (typeof before.enabled === 'boolean' && typeof after.enabled === 'boolean' && before.enabled !== after.enabled) changes.push(`${before.enabled ? 'ON' : 'OFF'} → ${after.enabled ? 'ON' : 'OFF'}`);
  if (typeof before.rolloutPercentage === 'number' && typeof after.rolloutPercentage === 'number' && before.rolloutPercentage !== after.rolloutPercentage) changes.push(`Rollout ${before.rolloutPercentage}% → ${after.rolloutPercentage}%`);
  return { name, environment, changes };
}
export function AuditPanel({ projectId, environments }: { projectId: number; environments: Environment[] }) {
  const [page, setPage] = useState(0);
  const resource = useResource(`audit:${projectId}:${page}`, signal => auditApi.list(projectId, page, signal));
  const { user } = useAuth();
  const result = resource.data;
  return <><div className="section-heading"><div><h2>Audit log</h2><p>A clear history of configuration and credential changes.</p></div><button className="button secondary small" onClick={resource.reload} disabled={resource.loading}>Refresh</button></div>
    {resource.loading ? <Loading label="Loading audit log…" /> : resource.error ? <ErrorState error={resource.error} retry={resource.reload} /> : !result?.content.length ? <EmptyState icon="activity" title="No events on this page" description="Your project changes will appear here." /> : <div className="audit-list">{result.content.map(event => {
      const detail = details(event, environments);
      const actor = event.actorUserId === user?.id ? user.name : `User #${event.actorUserId}`;
      return <article className="audit-event" key={event.id}><span className="avatar">{initials(actor)}</span><div className="audit-event-body"><div className="audit-title"><strong>{actor}</strong><span>{labels[event.action] ?? event.action}</span></div><div className="audit-detail"><code>{detail.name}</code>{detail.environment && <span className="subtle-pill">{detail.environment.name}</span>}{detail.changes.map(change => <span className="audit-change" key={change}>{change}</span>)}</div></div><time dateTime={event.createdAt} title={dateTime(event.createdAt)}>{dateTime(event.createdAt)}</time></article>;
    })}</div>}
    <div className="pagination"><span>{result ? `${result.totalElements} events · Page ${result.page + 1} of ${Math.max(1, Math.ceil(result.totalElements / result.size))}` : `Page ${page + 1}`}</span><div className="button-group"><button className="button secondary small" onClick={() => setPage(value => value - 1)} disabled={page === 0 || resource.loading}>Previous</button><button className="button secondary small" onClick={() => setPage(value => value + 1)} disabled={!result || resource.loading || (result.page + 1) * result.size >= result.totalElements}>Next</button></div></div>
  </>;
}
