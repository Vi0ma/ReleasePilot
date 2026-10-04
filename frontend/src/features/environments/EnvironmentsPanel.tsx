import type { Environment } from '../../types/api';
import { EnvironmentBadge } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { dateOnly } from '../../utils/format';
export function EnvironmentsPanel({ environments }: { environments: Environment[] }) {
  return <section className="environment-grid">{environments.map(environment => <article className="environment-card" key={environment.id}><Icon name="globe" size={20} /><EnvironmentBadge name={environment.name} environmentKey={environment.key} /><code>{environment.key}</code><p>Independent flag configurations and API keys.</p><small>Created {dateOnly(environment.createdAt)}</small></article>)}</section>;
}
