import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Icon } from './Icon';
import { FormError } from './Feedback';
export function Modal({ title, children, onClose, busy = false, wide = false }: { title: string; children: ReactNode; onClose: () => void; busy?: boolean; wide?: boolean }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const id = useId();
  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => element?.close();
  }, []);
  return <dialog ref={dialog} className={`modal ${wide ? 'wide' : ''}`} aria-labelledby={id} onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="modal-heading"><h2 id={id}>{title}</h2><button className="icon-button" aria-label="Close dialog" onClick={onClose} disabled={busy}><Icon name="close" /></button></div>{children}
  </dialog>;
}
export function ConfirmDialog({ title, description, confirmLabel, onConfirm, onClose }: { title: string; description: string; confirmLabel: string; onConfirm: () => Promise<void>; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  async function confirm() {
    if (busy) return;
    setBusy(true); setError(null);
    try { await onConfirm(); onClose(); } catch (failure) { setError(failure); setBusy(false); }
  }
  return <Modal title={title} onClose={onClose} busy={busy}><p className="modal-description">{description}</p><FormError error={error} /><div className="modal-actions"><button className="button secondary" onClick={onClose} disabled={busy} autoFocus>Cancel</button><button className="button danger" onClick={confirm} disabled={busy}>{busy ? 'Working…' : confirmLabel}</button></div></Modal>;
}
