import { useState, type FormEvent } from 'react';
import { FieldError, FormError } from './Feedback';
import { Modal } from './Modal';
import type { ResourceInput } from '../types/api';
interface Props { title: string; submitLabel: string; initial?: ResourceInput; onSubmit: (input: ResourceInput) => Promise<void>; onClose: () => void }
export function ResourceForm({ title, submitLabel, initial, onSubmit, onClose }: Props) {
  const [name, setName] = useState(initial?.name ?? '');
  const [key, setKey] = useState(initial?.key ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    if (!name.trim()) { setError(new Error('Enter a name.')); return; }
    setBusy(true); setError(null);
    try { await onSubmit({ name: name.trim(), key, description: description.trim() || null }); onClose(); }
    catch (failure) { setError(failure); setBusy(false); }
  }
  return <Modal title={title} onClose={onClose} busy={busy}><form onSubmit={submit}><FormError error={error} /><fieldset disabled={busy}>
    <label htmlFor="resource-name">Name</label><input id="resource-name" name="name" value={name} onChange={event => setName(event.target.value)} required maxLength={100} autoFocus placeholder="e.g. Checkout service" /><FieldError error={error} field="name" />
    <label htmlFor="resource-key">Stable key</label><input id="resource-key" className="mono" name="key" value={key} onChange={event => setKey(event.target.value)} required pattern="[a-z0-9_-]+" maxLength={100} readOnly={!!initial} placeholder="e.g. checkout-service" aria-describedby="key-hint" />
    <p className="field-hint" id="key-hint">{initial ? 'This identifier is permanent and cannot be changed.' : 'Lowercase letters, numbers, hyphens and underscores. Cannot be changed later.'}</p><FieldError error={error} field="key" />
    <label htmlFor="resource-description">Description <span className="optional">optional</span></label><textarea id="resource-description" name="description" value={description} onChange={event => setDescription(event.target.value)} maxLength={2000} rows={3} placeholder="What does this control?" /><FieldError error={error} field="description" />
  </fieldset><div className="modal-actions"><button className="button secondary" type="button" disabled={busy} onClick={onClose}>Cancel</button><button className="button primary" type="submit" disabled={busy}>{busy ? 'Saving…' : submitLabel}</button></div></form></Modal>;
}
