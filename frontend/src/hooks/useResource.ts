import { useCallback, useEffect, useRef, useState } from 'react';
export function useResource<T>(key: string, load: (signal: AbortSignal) => Promise<T>) {
  const loader = useRef(load);
  loader.current = load;
  const [version, setVersion] = useState(0);
  const [state, setState] = useState<{ key: string; data: T | null; loading: boolean; error: unknown }>({ key, data: null, loading: true, error: null });
  const reload = useCallback(() => setVersion(value => value + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    setState({ key, data: null, loading: true, error: null });
    loader.current(controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ key, data, loading: false, error: null });
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) setState({ key, data: null, loading: false, error });
    });
    return () => controller.abort();
  }, [key, version]);
  return { ...(state.key === key ? state : { key, data: null, loading: true, error: null }), reload };
}
