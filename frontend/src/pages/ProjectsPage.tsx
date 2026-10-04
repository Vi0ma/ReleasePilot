import { useState } from 'react';
import { projectsApi } from '../api/resources';
import { useResource } from '../hooks/useResource';
import { EmptyState, ErrorState, Loading } from '../components/Feedback';
import { Icon } from '../components/Icon';
import { CreateProjectModal } from '../features/projects/CreateProjectModal';
import { ProjectCard } from '../features/projects/ProjectCard';
export function ProjectsPage() {
  const resource = useResource('all-projects', signal => projectsApi.list(signal));
  const [query, setQuery] = useState('');
  const [create, setCreate] = useState(false);
  if (resource.loading) return <Loading label="Loading projects…" />;
  if (resource.error) return <ErrorState error={resource.error} retry={resource.reload} />;
  const projects = resource.data ?? [];
  const filtered = projects.filter(project => `${project.name} ${project.key}`.toLowerCase().includes(query.toLowerCase()));
  return <><div className="page-heading"><div><span className="eyebrow">WORKSPACE</span><h1>Projects <span className="count-badge">{projects.length}</span></h1><p>Organize your features. Control every environment.</p></div><button className="button primary" onClick={() => setCreate(true)}><Icon name="plus" size={16} />Create project</button></div>
    {projects.length > 0 && <div className="list-toolbar"><div className="search-field"><Icon name="search" size={17} /><input aria-label="Search projects" value={query} onChange={event => setQuery(event.target.value)} placeholder="Search projects by name or key…" /></div><span>{filtered.length} {filtered.length === 1 ? 'project' : 'projects'}</span></div>}
    {!projects.length ? <EmptyState title="No projects yet" description="Create a project to start managing feature flags and environment API keys." action={<button className="button primary" onClick={() => setCreate(true)}>Create project</button>} /> : !filtered.length ? <EmptyState icon="search" title="No matches" description="Try another project name or key." /> : <div className="project-grid">{filtered.map(project => <ProjectCard key={project.id} project={project} />)}</div>}
    {create && <CreateProjectModal onClose={() => setCreate(false)} />}
  </>;
}
