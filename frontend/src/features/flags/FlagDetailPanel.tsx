import { useState } from 'react';
import type { Environment } from '../../types/api';
import { flagsApi } from '../../api/resources';
import { useResource } from '../../hooks/useResource';
import { EmptyState, ErrorState, Loading } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { ResourceForm } from '../../components/ResourceForm';
import { ConfirmDialog } from '../../components/Modal';
import { useToast } from '../../components/ToastProvider';
import { Link, navigate } from '../../utils/router';
import { ConfigurationCard } from './ConfigurationCard';
export function FlagDetailPanel({ projectId, flagId, environments }: { projectId: number; flagId: number; environments: Environment[] }) {
  const resource = useResource(`flag:${projectId}:${flagId}`, async signal => {
    const [flag, configurations] = await Promise.all([flagsApi.get(projectId, flagId, signal), flagsApi.configurations(projectId, flagId, signal)]);
    return { flag, configurations };
  });
  const [edit, setEdit] = useState(false);
  const [remove, setRemove] = useState(false);
  const notify = useToast();
  if (resource.loading) return <Loading label="Loading flag configuration…" />;
  if (resource.error) return <ErrorState error={resource.error} retry={resource.reload} />;
  if (!resource.data) return null;
  const { flag, configurations } = resource.data;
  return <><Link className="text-link back-link" href={`/projects/${projectId}/flags`}>← All feature flags</Link><div className="section-heading flag-detail-heading"><div><span className="flag-key"><Icon name="flag" /><code>{flag.key}</code></span><h2>{flag.name}</h2><p>{flag.description || 'Manage availability and rollout across your environments.'}</p></div><div className="button-group"><button className="button secondary" onClick={() => setEdit(true)}><Icon name="edit" size={15} />Edit flag</button><button className="icon-button destructive" aria-label="Delete flag" onClick={() => setRemove(true)}><Icon name="trash" /></button></div></div>
    {configurations.length ? <div className="configuration-grid">{configurations.map(configuration => {
      const environment = environments.find(item => item.id === configuration.environmentId);
      return environment ? <ConfigurationCard key={`${flag.id}:${configuration.id}`} configuration={configuration} environment={environment} /> : <ErrorState key={configuration.id} error={new Error('Missing environment')} retry={resource.reload} />;
    })}</div> : <EmptyState icon="globe" title="No configurations available" description="Reload the flag to check its environments." action={<button className="button secondary" onClick={resource.reload}>Reload</button>} />}
    <div className="info-callout compact"><Icon name="shield" /><p>The same user stays in the same rollout bucket for this flag. Changes are applied after the server confirms them.</p></div>
    {edit && <ResourceForm title="Edit feature flag" submitLabel="Save changes" initial={flag} onClose={() => setEdit(false)} onSubmit={async input => { await flagsApi.update(projectId, flagId, { name: input.name, description: input.description }); notify('Flag details updated.'); resource.reload(); }} />}
    {remove && <ConfirmDialog title="Delete feature flag?" description={`Permanently delete ${flag.key} and all its environment configurations. Existing evaluations will return FLAG_NOT_FOUND. The audit history is kept.`} confirmLabel="Delete flag" onClose={() => setRemove(false)} onConfirm={async () => { await flagsApi.delete(projectId, flagId); notify('Feature flag deleted.'); navigate(`/projects/${projectId}/flags`); }} />}
  </>;
}
