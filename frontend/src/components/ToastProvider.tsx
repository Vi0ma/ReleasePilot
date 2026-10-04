import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Icon } from './Icon';
type Notice = { id: number; message: string; kind: 'success' | 'error' };
const ToastContext = createContext<((message: string, kind?: Notice['kind']) => void) | null>(null);
export function ToastProvider({ children }: { children: ReactNode }) {
  const [notices, setNotices] = useState<Notice[]>([]);
  const sequence = useRef(0);
  const timers = useRef(new Set<number>());
  const notify = useCallback((message: string, kind: Notice['kind'] = 'success') => {
    const id = ++sequence.current;
    setNotices(previous => [...previous.slice(-3), { id, message, kind }]);
    const timer = window.setTimeout(() => { setNotices(previous => previous.filter(notice => notice.id !== id)); timers.current.delete(timer); }, 4500);
    timers.current.add(timer);
  }, []);
  useEffect(() => () => { timers.current.forEach(window.clearTimeout); }, []);
  return <ToastContext.Provider value={notify}>{children}<div className="toast-region" aria-live="polite" aria-atomic="false">{notices.map(notice => <div key={notice.id} className={`toast ${notice.kind}`}><Icon name={notice.kind === 'success' ? 'check' : 'close'} /><span>{notice.message}</span><button aria-label="Dismiss notification" className="icon-button" onClick={() => setNotices(previous => previous.filter(item => item.id !== notice.id))}><Icon name="close" size={14} /></button></div>)}</div></ToastContext.Provider>;
}
export function useToast() {
  const toast = useContext(ToastContext);
  if (!toast) throw new Error('ToastProvider is required');
  return toast;
}
