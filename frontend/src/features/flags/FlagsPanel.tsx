import { useState } from 'react';
import { flagsApi } from '../../api/resources';
import { useResource } from '../../hooks/useResource';
import { EmptyState, ErrorState, Loading } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { ResourceForm } from '../../components/ResourceForm';
import { useToast } from '../../components/ToastProvider';
import { Link, navigate } from '../../utils/router';
import { dateOnly } from '../../utils/format';
export function FlagsPanel({ projectId }: { projectId: number }) {
  const resource = useResource(`flags:${projectId}`, signal => flagsApi.list(projectId, signal));
  const [query, setQuery] = useState('');
  const [create, setCreate] = useState(false);
  const notify = useToast();
  if (resource.loading) return <Loading label="Loading feature flags…" />;
  if (resource.error) return <ErrorState error={resource.error} retry={resource.reload} />;
  const flags = resource.data ?? [];
  const filtered = flags.filter(flag => `${flag.name} ${flag.key}`.toLowerCase().includes(query.toLowerCase()));
  return <><div className="section-heading"><div><h2>Feature flags <span className="count-badge">{flags.length}</span></h2><p>One flag. Independent controls for every environment.</p></div><button className="button primary" onClick={() => setCreate(true)}><Icon name="plus" size={16} />Create flag</button></div>
    {flags.length > 0 && <div className="list-toolbar"><div className="search-field"><Icon name="search" size={17} /><input aria-label="Search flags" value={query} onChange={event => setQuery(event.target.value)} placeholder="Search by flag name or key…" /></div><span>{filtered.length} flags</span></div>}
    {!flags.length ? <EmptyState icon="flag" title="Your first flag starts here" description="Create a feature flag. All environments start safely at OFF with 0% rollout." action={<button className="button primary" onClick={() => setCreate(true)}>Create flag</button>} /> : !filtered.length ? <EmptyState icon="search" title="No matching flags" description="Try another flag name or key." /> : <div className="table-wrapper"><table className="data-table"><thead><tr><th>Flag</th><th>Description</th><th>Created</th><th><span className="sr-only">Open flag</span></th></tr></thead><tbody>{filtered.map(flag => <tr key={flag.id}><td><Link className="resource-name" href={`/projects/${projectId}/flags/${flag.id}`}><span className="small-icon"><Icon name="flag" size={16} /></span><span><strong>{flag.name}</strong><code>{flag.key}</code></span></Link></td><td className="description-cell">{flag.description || '—'}</td><td>{dateOnly(flag.createdAt)}</td><td><Link className="icon-button" href={`/projects/${projectId}/flags/${flag.id}`} aria-label={`Configure ${flag.name}`}><Icon name="chevron" size={16} /></Link></td></tr>)}</tbody></table></div>}
    {create && <ResourceForm title="Create feature flag" submitLabel="Create flag" onClose={() => setCreate(false)} onSubmit={async input => { const flag = await flagsApi.create(projectId, input); notify('Flag created. All environments start at OFF / 0%.'); navigate(`/projects/${projectId}/flags/${flag.id}`); }} />}
  </>;
}
