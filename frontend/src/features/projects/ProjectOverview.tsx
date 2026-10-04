import type { Environment, Project } from '../../types/api';
import { flagsApi } from '../../api/resources';
import { useResource } from '../../hooks/useResource';
import { ErrorState } from '../../components/Feedback';
import { EnvironmentsPanel } from '../environments/EnvironmentsPanel';
import { Link } from '../../utils/router';
import { Icon } from '../../components/Icon';
import { dateTime } from '../../utils/format';
export function ProjectOverview({ project, environments }: { project: Project; environments: Environment[] }) {
  const flags = useResource(`overview-flags:${project.id}`, signal => flagsApi.list(project.id, signal));
  return <><div className="section-heading"><div><h2>Project overview</h2><p>One project, a clear path from development to production.</p></div></div><div className="overview-stats"><div className="stat-card"><Icon name="flag" /><span>Feature flags</span>{flags.loading ? <span role="status">Loading…</span> : flags.error ? <ErrorState error={flags.error} retry={flags.reload} /> : <strong>{flags.data?.length ?? 0}</strong>}<Link href={`/projects/${project.id}/flags`} className="text-link">Manage flags <Icon name="arrow" size={14} /></Link></div><div className="stat-card"><Icon name="globe" /><span>Environments</span><strong>{environments.length}</strong><span>Separate configuration and credentials</span></div><div className="stat-card"><Icon name="activity" /><span>Created</span><strong className="stat-date">{dateTime(project.createdAt)}</strong><Link href={`/projects/${project.id}/audit`} className="text-link">View audit <Icon name="arrow" size={14} /></Link></div></div><div className="section-heading"><div><h2>Your environments</h2><p>Configuration is scoped to an environment. API keys never cross that boundary.</p></div></div><EnvironmentsPanel environments={environments} /><div className="info-callout"><Icon name="shield" /><div><strong>Start safely. Release deliberately.</strong><p>New flags default to OFF and 0% rollout in every environment. Enable them when you're ready.</p></div><Link className="button secondary" href={`/projects/${project.id}/flags`}>Manage flags <Icon name="arrow" size={14} /></Link></div></>;
}
