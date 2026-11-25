/* Lucian UX/UI Prompt #435 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#435 */
import { useEffect, useRef, useState } from 'react';
import './lucian-435.css';

export interface ToastMessageItem {
  id: string;
  text: string;
}

interface LucianToastStackProps {
  toasts: ToastMessageItem[];
  onDismiss: (id: string) => void;
}

const H = 56;
const GAP = 8;
const LIFE_MS = 4500;
const MAX = 3;

export function LucianToastStack({ toasts, onDismiss }: LucianToastStackProps) {
  const [fanned, setFanned] = useState(false);
  const [dragState, setDragState] = useState<Record<string, number>>({});
  const timersRef = useRef<Map<string, number>>(new Map());
  const onDismissRef = useRef(onDismiss);
  onDismissRef.current = onDismiss;

  const visible = toasts.slice(-MAX);

  useEffect(() => {
    if (fanned) {
      timersRef.current.forEach((tm) => window.clearTimeout(tm));
      timersRef.current.clear();
      return;
    }
    visible.forEach((t) => {
      if (!timersRef.current.has(t.id)) {
        const tm = window.setTimeout(() => {
          timersRef.current.delete(t.id);
          onDismissRef.current(t.id);
        }, LIFE_MS);
        timersRef.current.set(t.id, tm);
      }
    });
    const activeIds = new Set(visible.map((t) => t.id));
    timersRef.current.forEach((tm, id) => {
      if (!activeIds.has(id)) {
        window.clearTimeout(tm);
        timersRef.current.delete(id);
      }
    });
  }, [visible, fanned]);

  useEffect(() => {
    return () => {
      timersRef.current.forEach((tm) => window.clearTimeout(tm));
      timersRef.current.clear();
    };
  }, []);

  if (visible.length === 0) return null;

  const regionHeight = fanned && visible.length > 1 ? visible.length * H + (visible.length - 1) * GAP : H + 18;

  return (
    <div className="ux u435-host">
      <section
        className={`u435-rg ${fanned ? 'is-paused' : ''}`}
        style={{ height: `${regionHeight}px` }}
        aria-label="Notifications"
        onPointerEnter={() => setFanned(true)}
        onPointerLeave={() => setFanned(false)}
        onFocus={() => setFanned(true)}
        onBlur={(e) => {
          if (!e.currentTarget.contains(e.relatedTarget as Node)) setFanned(false);
        }}
      >
        {visible.map((item, idx) => {
          const depth = visible.length - 1 - idx;
          const y = fanned ? -depth * (H + GAP) : -depth * 11;
          const s = fanned ? 1 : 1 - depth * 0.055;
          const dx = dragState[item.id] || 0;
          const opacity = dx ? Math.max(0.35, 1 - Math.abs(dx) / 280) : 1;

          return (
            <div
              key={item.id}
              role="status"
              className={`u435-t ${!fanned && depth > 0 ? 'is-back' : ''} ${dx ? 'is-drag' : ''}`}
              style={{
                ['--x' as string]: `${dx}px`,
                ['--y' as string]: `${y}px`,
                ['--s' as string]: s.toFixed(3),
                ['--o' as string]: opacity,
                zIndex: 20 - depth,
              }}
              onPointerDown={(e) => {
                if (e.button !== 0 || (e.target as HTMLElement).closest('button')) return;
                const startX = e.clientX;
                const el = e.currentTarget;
                try {
                  el.setPointerCapture(e.pointerId);
                } catch {
                  // ignore
                }
                let latestDx = 0;
                const onMove = (mv: PointerEvent) => {
                  latestDx = mv.clientX - startX;
                  setDragState((prev) => ({ ...prev, [item.id]: latestDx }));
                };
                const onEnd = () => {
                  el.removeEventListener('pointermove', onMove);
                  el.removeEventListener('pointerup', onEnd);
                  el.removeEventListener('pointercancel', onEnd);
                  setDragState((prev) => {
                    const next = { ...prev };
                    delete next[item.id];
                    return next;
                  });
                  if (Math.abs(latestDx) > 80) {
                    onDismiss(item.id);
                  }
                };
                el.addEventListener('pointermove', onMove);
                el.addEventListener('pointerup', onEnd);
                el.addEventListener('pointercancel', onEnd);
              }}
            >
              <span className="u435-ic" aria-hidden="true">
                <span>
                  <svg viewBox="0 0 24 24">
                    <path d="m6 12.5 4 4 8-9" />
                  </svg>
                </span>
              </span>
              <span className="u435-tx">{item.text}</span>
              <button
                type="button"
                className="u435-x"
                aria-label="Dismiss notification"
                onClick={() => onDismiss(item.id)}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M6 6l12 12M18 6 6 18" />
                </svg>
              </button>
              <span className="u435-pb" aria-hidden="true">
                <i />
              </span>
            </div>
          );
        })}
      </section>
    </div>
  );
}
