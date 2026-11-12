import { createContext, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import type { User } from './types';
import { get, request, clearCsrf, ApiError } from './api';
type Toast = { id: number; message: string; kind: 'success' | 'error' };
type AppState = {
  user: User | null;
  authLoading: boolean;
  setUser: (user: User | null) => void;
  revision: number;
  refresh: () => void;
  toast: (message: string, kind?: Toast['kind']) => void;
  errorToast: Toast | null;
};
const Context = createContext<AppState>(null!);
export function AppProvider({ children }: { children: ReactNode }) {
  const authEpoch = useRef(0);
  const [user, setUser] = useState<User | null>(null),
    [authLoading, setAuthLoading] = useState(true),
    [revision, setRevision] = useState(0),
    [toasts, setToasts] = useState<Toast[]>([]);
  useEffect(() => {
    const epoch = authEpoch.current;
    get<User>('/auth/me')
      .then((u) => {
        if (authEpoch.current === epoch) setUser(u);
      })
      .catch(() => {
        if (authEpoch.current === epoch) setUser(null);
      })
      .finally(() => setAuthLoading(false));
    const expire = () => {
      authEpoch.current++;
      setUser(null);
      clearCsrf();
    };
    window.addEventListener('session-expired', expire);
    return () => window.removeEventListener('session-expired', expire);
  }, []);
  function toast(message: string, kind: Toast['kind'] = 'success') {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, message, kind }]);
    window.setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 7000);
  }
  return (
    <Context.Provider
      value={{
        user,
        authLoading,
        setUser: (u) => {
          authEpoch.current++;
          setUser(u);
        },
        revision,
        refresh: () => setRevision((v) => v + 1),
        toast,
        errorToast: toasts.filter((t) => t.kind === 'error').at(-1) ?? null,
      }}
    >
      {children}
      <div className="toast-stack" aria-live="polite">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`toast ${t.kind}`}
            role={t.kind === 'error' ? 'alert' : 'status'}
          >
            {t.message}
            <button
              aria-label="Đóng thông báo"
              onClick={() => setToasts((x) => x.filter((v) => v.id !== t.id))}
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </Context.Provider>
  );
}
export const useApp = () => useContext(Context);
export function useQuery<T>(path: string | null) {
  const { revision } = useApp();
  const [data, setData] = useState<T | null>(null),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(!!path);
  useEffect(() => {
    if (!path) {
      setData(null);
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError('');
    get<T>(path, controller.signal)
      .then((d) => {
        if (!controller.signal.aborted) setData(d);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [path, revision]);
  return { data, error, loading };
}
export function useCommand() {
  const { refresh, toast } = useApp();
  const [busy, setBusy] = useState(false);
  const keys = useRef(new Map<string, string>());
  const running = useRef(false);
  async function run<T = unknown>(
    path: string,
    body?: unknown,
    success = 'Đã lưu thay đổi.',
    method = 'POST',
  ) {
    if (running.current) return { ok: false };
    running.current = true;
    setBusy(true);
    const fingerprint =
      path +
      ':' +
      (body instanceof FormData
        ? JSON.stringify(
            [...body.entries()].map(([k, v]) => [
              k,
              v instanceof File ? { name: v.name, size: v.size, lastModified: v.lastModified } : v,
            ]),
          )
        : JSON.stringify(body));
    const key = keys.current.get(fingerprint) ?? crypto.randomUUID();
    keys.current.set(fingerprint, key);
    try {
      const data = await request<T>(path, { method, body, key });
      keys.current.delete(fingerprint);
      refresh();
      toast(success);
      return { ok: true, data };
    } catch (e) {
      const error = e instanceof Error ? e.message : 'Không xử lý được yêu cầu.';
      toast(error, 'error');
      return { ok: false, error, status: e instanceof ApiError ? e.status : undefined };
    } finally {
      running.current = false;
      setBusy(false);
    }
  }
  return { run, busy };
}
