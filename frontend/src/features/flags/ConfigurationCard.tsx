import { useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import type { Environment, FlagConfiguration } from '../../types/api';
import { flagsApi } from '../../api/resources';
import { EnvironmentBadge, FormError } from '../../components/Feedback';
import { Switch } from '../../components/Switch';
import { useToast } from '../../components/ToastProvider';
import { dateTime } from '../../utils/format';
import { shouldCommitRollout } from './rollout';

export function ConfigurationCard({ configuration, environment }: { configuration: FlagConfiguration; environment: Environment }) {
  const [confirmed, setConfirmed] = useState(configuration);
  const current = useRef(configuration);
  const [draft, setDraft] = useState(configuration.rolloutPercentage);
  const [saving, setSaving] = useState(false);
  const savingLock = useRef(false);
  const [error, setError] = useState<unknown>(null);
  const notify = useToast();
  async function update(enabled: boolean, percentage: number) {
    if (savingLock.current || (enabled === current.current.enabled && percentage === current.current.rolloutPercentage)) return;
    savingLock.current = true; setSaving(true); setError(null);
    try {
      const response = await flagsApi.configure(current.current.featureFlagId, environment.id, enabled, percentage);
      current.current = response; setConfirmed(response); setDraft(response.rolloutPercentage);
      notify(`${environment.name} configuration saved.`);
    } catch (failure) { setDraft(current.current.rolloutPercentage); setError(failure); }
    finally { savingLock.current = false; setSaving(false); }
  }
  function commit(value: number) {
    if (shouldCommitRollout(current.current.rolloutPercentage, value)) void update(current.current.enabled, value);
  }
  const rangeId = `rollout-${environment.id}`;
  return <article className={`configuration-card ${confirmed.enabled ? 'enabled' : ''}`} aria-busy={saving}><div className="configuration-heading"><EnvironmentBadge name={environment.name} environmentKey={environment.key} /><span className={`state-badge ${confirmed.enabled ? 'on' : ''}`}>{confirmed.enabled ? 'ON' : 'OFF'}</span></div><div className="toggle-row"><div><strong>Feature enabled</strong><span>{confirmed.enabled ? 'Available for rollout' : 'Disabled for this environment'}</span></div><Switch checked={confirmed.enabled} disabled={saving} label={`Enable feature in ${environment.name}`} onChange={() => void update(!current.current.enabled, current.current.rolloutPercentage)} /></div><div className="rollout-heading"><label htmlFor={rangeId}>Percentage rollout</label><output htmlFor={rangeId} className="percentage-value">{draft}<span>%</span></output></div><input id={rangeId} className="rollout-slider" type="range" min={0} max={100} step={1} value={draft} disabled={saving} style={{ '--percentage': `${draft}%` } as CSSProperties} aria-describedby={`${rangeId}-hint`} onChange={event => setDraft(Number(event.target.value))} onPointerUp={event => commit(Number(event.currentTarget.value))} onPointerCancel={() => setDraft(current.current.rolloutPercentage)} onKeyUp={event => { if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End', 'PageUp', 'PageDown'].includes(event.key)) commit(Number(event.currentTarget.value)); }} onBlur={event => commit(Number(event.currentTarget.value))} />
    <div className="range-markers" aria-hidden="true"><span>0%</span><span>25%</span><span>50%</span><span>75%</span><span>100%</span></div><p className="configuration-hint" id={`${rangeId}-hint`}>{!confirmed.enabled ? 'OFF overrides rollout. Enable the feature to release it.' : confirmed.rolloutPercentage === 0 ? 'Enabled, but no users are included at 0%.' : confirmed.rolloutPercentage === 100 ? 'Released to every user in this environment.' : `Released to approximately ${confirmed.rolloutPercentage}% of users, deterministically.`}</p><FormError error={error} /><div className="configuration-footer" role="status">{saving ? <><span className="spinner small" />Saving configuration…</> : draft !== confirmed.rolloutPercentage ? 'Release the slider to save' : <>Updated {dateTime(confirmed.updatedAt)}</>}</div>
  </article>;
}
