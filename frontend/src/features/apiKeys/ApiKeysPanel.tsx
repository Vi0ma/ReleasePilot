import { useState, type FormEvent } from 'react';
import type { ApiKey, Environment } from '../../types/api';
import { keysApi } from '../../api/resources';
import { useResource } from '../../hooks/useResource';
import { EmptyState, EnvironmentBadge, ErrorState, FieldError, FormError, Loading } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { ConfirmDialog, Modal } from '../../components/Modal';
import { useToast } from '../../components/ToastProvider';
import { dateTime } from '../../utils/format';
export function ApiKeysPanel({ projectId, environments }: { projectId: number; environments: Environment[] }) {
  const [environmentId, setEnvironmentId] = useState(environments.find(environment => environment.key === 'production')?.id ?? environments[0]?.id);
  if (!environmentId) return <EmptyState icon="globe" title="No environments available" description="This project has no environment available for API keys." />;
  return <><div className="section-heading"><div><h2>Environment API keys</h2><p>Give your application access to evaluation in exactly one environment.</p></div></div><div className="environment-selector"><label htmlFor="keys-environment">Environment</label><select id="keys-environment" value={environmentId} onChange={event => setEnvironmentId(Number(event.target.value))}>{environments.map(environment => <option key={environment.id} value={environment.id}>{environment.name}</option>)}</select></div><EnvironmentKeys key={environmentId} projectId={projectId} environment={environments.find(environment => environment.id === environmentId)!} /></>;
}
function EnvironmentKeys({ projectId, environment }: { projectId: number; environment: Environment }) {
  const resource = useResource(`keys:${projectId}:${environment.id}`, signal => keysApi.list(projectId, environment.id, signal));
  const [create, setCreate] = useState(false);
  const [revoke, setRevoke] = useState<ApiKey | null>(null);
  const [secret, setSecret] = useState<string | null>(null);
  const notify = useToast();
  return <><div className="list-toolbar"><EnvironmentBadge name={environment.name} environmentKey={environment.key} /><button className="button primary" onClick={() => setCreate(true)}><Icon name="plus" size={16} />Generate API key</button></div>
    {resource.loading ? <Loading label="Loading API keys…" /> : resource.error ? <ErrorState error={resource.error} retry={resource.reload} /> : !resource.data?.length ? <EmptyState icon="key" title="No API keys yet" description={`Generate a key to evaluate flags from your application in ${environment.name}.`} action={<button className="button primary" onClick={() => setCreate(true)}>Generate API key</button>} /> : <div className="table-wrapper"><table className="data-table"><thead><tr><th>Name / public prefix</th><th>Status</th><th>Created</th><th>Action</th></tr></thead><tbody>{resource.data.map(key => <tr key={key.id}><td><div className="resource-name"><span className="small-icon"><Icon name="key" size={16} /></span><span><strong>{key.name}</strong><code>{key.prefix}</code></span></div></td><td><span className={`state-badge ${key.revokedAt ? '' : 'on'}`}>{key.revokedAt ? 'Revoked' : 'Active'}</span>{key.revokedAt && <small className="cell-detail">{dateTime(key.revokedAt)}</small>}</td><td>{dateTime(key.createdAt)}</td><td><button className="button small secondary" disabled={!!key.revokedAt} onClick={() => setRevoke(key)}>Revoke</button></td></tr>)}</tbody></table></div>}
    <div className="info-callout compact"><Icon name="shield" /><p>Keys are scoped to {environment.name}. Keep them on your application server. The full secret is shown only at creation.</p></div>
    {create && <CreateKeyModal projectId={projectId} environment={environment} onClose={() => setCreate(false)} onCreated={value => { setCreate(false); setSecret(value); resource.reload(); }} />}
    {secret !== null && <SecretModal secret={secret} onClose={() => setSecret(null)} />}
    {revoke && <ConfirmDialog title="Revoke API key?" description={`Revoke ${revoke.name} in ${environment.name}? Applications using this key will immediately lose access to new evaluation requests. This cannot be undone.`} confirmLabel="Revoke key" onClose={() => setRevoke(null)} onConfirm={async () => { await keysApi.revoke(projectId, environment.id, revoke.id); notify('API key revoked.'); resource.reload(); }} />}
  </>;
}
function CreateKeyModal({ projectId, environment, onClose, onCreated }: { projectId: number; environment: Environment; onClose: () => void; onCreated: (secret: string) => void }) {
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    if (!name.trim()) { setError(new Error('Enter a key name.')); return; }
    setBusy(true); setError(null);
    try { const response = await keysApi.create(projectId, environment.id, name.trim()); onCreated(response.apiKey); }
    catch (failure) { setError(failure); setBusy(false); }
  }
  return <Modal title="Generate API key" busy={busy} onClose={onClose}><p className="modal-description">This key will only evaluate flags in {environment.name}.</p><form onSubmit={submit}><FormError error={error} /><label htmlFor="key-name">Key name</label><input id="key-name" name="name" value={name} onChange={event => setName(event.target.value)} required maxLength={100} disabled={busy} placeholder="e.g. Production checkout service" autoFocus /><FieldError error={error} field="name" /><div className="modal-actions"><button className="button secondary" type="button" disabled={busy} onClick={onClose}>Cancel</button><button className="button primary" disabled={busy} type="submit">{busy ? 'Generating…' : 'Generate key'}</button></div></form></Modal>;
}
function SecretModal({ secret, onClose }: { secret: string; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function copy() {
    try { await navigator.clipboard.writeText(secret); setCopied(true); setError(null); }
    catch { setError('Clipboard access is unavailable. Select the key and copy it manually.'); }
  }
  return <Modal title="API key created" onClose={onClose} wide><p className="modal-description">Copy this key and store it securely. <strong>This key will not be shown again.</strong></p><div className="secret-box"><code data-secret="true">{secret}</code><button className="button secondary" onClick={copy}><Icon name={copied ? 'check' : 'copy'} size={16} />{copied ? 'Copied' : 'Copy'}</button></div>{error && <p className="field-error" role="alert">{error}</p>}<p className="field-hint">Closing this dialog removes the secret from the interface. It is never saved in browser storage.</p><div className="modal-actions"><button className="button primary" onClick={onClose}>Done, I saved the key</button></div></Modal>;
}
