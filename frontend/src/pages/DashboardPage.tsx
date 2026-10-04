import { useState } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { projectsApi } from '../api/resources';
import { useResource } from '../hooks/useResource';
import { EmptyState, ErrorState, Loading } from '../components/Feedback';
import { Icon } from '../components/Icon';
import { CreateProjectModal } from '../features/projects/CreateProjectModal';
import { ProjectCard } from '../features/projects/ProjectCard';
import { Link } from '../utils/router';
export function DashboardPage() {
  const { user } = useAuth();
  const resource = useResource('dashboard-projects', signal => projectsApi.list(signal));
  const [create, setCreate] = useState(false);
  if (resource.loading) return <Loading label="Loading dashboard…" />;
  if (resource.error) return <ErrorState error={resource.error} retry={resource.reload} />;
  const projects = resource.data ?? [];
  const recent = [...projects].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id).slice(0, 6);
  return <><div className="page-heading"><div><span className="eyebrow">OVERVIEW</span><h1>Welcome back, {user?.name.split(' ')[0]}<span className="accent">.</span></h1><p>Your next release starts here.</p></div><button className="button primary" onClick={() => setCreate(true)}><Icon name="plus" size={16} />Create project</button></div>
    <section className="dashboard-hero"><div><span className="pill"><Icon name="bolt" size={13} />RELEASE CONTROL</span><h2>Ship the code.<br /><span>Choose the moment.</span></h2><p>Manage flags across environments and release features progressively, with a clear history of every change.</p><Link className="text-link" href="/projects">Open your projects <Icon name="arrow" size={15} /></Link></div><div className="hero-project-count"><Icon name="folder" size={28} /><strong>{projects.length}</strong><span>{projects.length === 1 ? 'project in your workspace' : 'projects in your workspace'}</span></div></section>
    <div className="section-heading"><div><h2>Recent projects</h2><p>Your most recently created workspaces.</p></div>{projects.length > 0 && <Link className="text-link" href="/projects">View all <Icon name="arrow" size={14} /></Link>}</div>
    {projects.length ? <div className="project-grid">{recent.map(project => <ProjectCard key={project.id} project={project} />)}</div> : <EmptyState title="No projects yet" description="Create your first project. Development, staging and production will be ready automatically." action={<button className="button primary" onClick={() => setCreate(true)}><Icon name="plus" size={16} />Create your first project</button>} />}
    <div className="workflow-grid"><div className="workflow-card"><Icon name="globe" /><h3>Separate environments</h3><p>Keep development, staging and production configurations independent.</p></div><div className="workflow-card"><Icon name="flag" /><h3>Release progressively</h3><p>Control ON/OFF and deterministic percentage rollout for each flag.</p></div><div className="workflow-card"><Icon name="activity" /><h3>Keep changes in view</h3><p>Review configuration updates and key management in your project audit.</p></div></div>
    {create && <CreateProjectModal onClose={() => setCreate(false)} />}
  </>;
}
