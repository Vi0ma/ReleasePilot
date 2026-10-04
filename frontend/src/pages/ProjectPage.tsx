import { useState } from 'react';
import { projectsApi } from '../api/resources';
import { useResource } from '../hooks/useResource';
import { ErrorState, Loading } from '../components/Feedback';
import { Icon } from '../components/Icon';
import { ResourceForm } from '../components/ResourceForm';
import { ConfirmDialog } from '../components/Modal';
import { useToast } from '../components/ToastProvider';
import { Link, navigate } from '../utils/router';
import { ProjectOverview } from '../features/projects/ProjectOverview';
import { FlagsPanel } from '../features/flags/FlagsPanel';
import { FlagDetailPanel } from '../features/flags/FlagDetailPanel';
import { ApiKeysPanel } from '../features/apiKeys/ApiKeysPanel';
import { AuditPanel } from '../features/audit/AuditPanel';
export type ProjectSection = 'overview' | 'flags' | 'api-keys' | 'audit';
export function ProjectPage({ projectId, section, flagId }: { projectId: number; section: ProjectSection; flagId?: number }) {
  const resource = useResource(`project:${projectId}`, async signal => {
    const [project, environments] = await Promise.all([projectsApi.get(projectId, signal), projectsApi.environments(projectId, signal)]);
    return { project, environments };
  });
  const [edit, setEdit] = useState(false);
  const [remove, setRemove] = useState(false);
  const notify = useToast();
  if (resource.loading) return <Loading label="Loading project workspace…" />;
  if (resource.error) return <ErrorState error={resource.error} retry={resource.reload} />;
  if (!resource.data) return null;
  const { project, environments } = resource.data;
  return <><Link className="text-link back-link" href="/projects">← All projects</Link><div className="page-heading project-heading"><div><span className="eyebrow">PROJECT WORKSPACE</span><h1>{project.name}</h1><div className="project-subtitle"><code>{project.key}</code><span>{project.description || 'Feature management for your application.'}</span></div></div><div className="button-group"><button className="button secondary" onClick={() => setEdit(true)}><Icon name="edit" size={15} />Edit project</button><button className="icon-button destructive" aria-label="Delete project" onClick={() => setRemove(true)}><Icon name="trash" /></button></div></div>
    <nav className="project-tabs" aria-label="Project navigation">{([['overview', 'Overview', 'grid'], ['flags', 'Feature flags', 'flag'], ['api-keys', 'API keys', 'key'], ['audit', 'Audit log', 'activity']] as const).map(([tab, label, icon]) => <Link key={tab} href={`/projects/${projectId}${tab === 'overview' ? '' : `/${tab}`}`} className={section === tab ? 'active' : ''} aria-current={section === tab ? 'page' : undefined}><Icon name={icon} size={16} />{label}</Link>)}</nav>
    {section === 'overview' && <ProjectOverview project={project} environments={environments} />}
    {section === 'flags' && (flagId ? <FlagDetailPanel key={flagId} projectId={projectId} flagId={flagId} environments={environments} /> : <FlagsPanel projectId={projectId} />)}
    {section === 'api-keys' && <ApiKeysPanel projectId={projectId} environments={environments} />}
    {section === 'audit' && <AuditPanel projectId={projectId} environments={environments} />}
    {edit && <ResourceForm title="Edit project" submitLabel="Save changes" initial={project} onClose={() => setEdit(false)} onSubmit={async input => { await projectsApi.update(projectId, { name: input.name, description: input.description }); notify('Project updated.'); resource.reload(); }} />}
    {remove && <ConfirmDialog title="Delete project?" description={`Permanently delete ${project.name}, all its flags, configurations, API keys and environments. Its audit history remains stored. This cannot be undone.`} confirmLabel="Delete project" onClose={() => setRemove(false)} onConfirm={async () => { await projectsApi.delete(projectId); notify('Project deleted.'); navigate('/projects'); }} />}
  </>;
}
