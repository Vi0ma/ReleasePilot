import type { Project } from '../../types/api';
import { Link } from '../../utils/router';
import { dateOnly, initials } from '../../utils/format';
import { Icon } from '../../components/Icon';
export function ProjectCard({ project }: { project: Project }) {
  return <Link href={`/projects/${project.id}`} className="project-card"><div className="project-card-top"><span className="project-avatar">{initials(project.name)}</span><Icon name="arrow" size={17} /></div><h3>{project.name}</h3><code>{project.key}</code><p>{project.description || 'A workspace for your next release.'}</p><div className="project-card-footer"><span><Icon name="folder" size={13} />Project</span><span>{dateOnly(project.createdAt)}</span></div></Link>;
}
