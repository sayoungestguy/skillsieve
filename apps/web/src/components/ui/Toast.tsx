import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

const AUTO_DISMISS_MS = 4000;

interface ToastMessage {
  text: string;
  busy?: boolean;
  link?: { to: string; label: string };
}

const ToastContext = createContext<(t: ToastMessage) => void>(() => undefined);

export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const show = useCallback((t: ToastMessage) => setToast(t), []);

  useEffect(() => {
    if (!toast) return;
    const id = setTimeout(() => setToast(null), AUTO_DISMISS_MS);
    return () => clearTimeout(id);
  }, [toast]);

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div
        role="status"
        className="pointer-events-none fixed inset-x-0 bottom-24 z-50 flex justify-center px-4 lg:bottom-8"
      >
        {toast && (
          <div className="pointer-events-auto flex items-center gap-3 rounded-card bg-ink px-4 py-3 text-sm font-medium text-white shadow-modal">
            {toast.busy && (
              <span className="spinner border-slate-500 border-t-white" aria-hidden="true" />
            )}
            {toast.text}
            {toast.link && (
              <Link to={toast.link.to} className="font-semibold text-indigo-200 hover:text-white">
                {toast.link.label}
              </Link>
            )}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}
