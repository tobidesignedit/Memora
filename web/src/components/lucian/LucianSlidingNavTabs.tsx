/* Lucian UX/UI Prompt #039 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#039 */
import { useEffect, useLayoutEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import './lucian-039.css';

export interface SlidingTabItem<T extends string> {
  id: T;
  label: string;
  icon: ReactNode;
  count?: number;
}

interface LucianSlidingNavTabsProps<T extends string> {
  items: ReadonlyArray<SlidingTabItem<T>>;
  activeId: T;
  onSelect: (id: T) => void;
  ariaLabel?: string;
}

export function LucianSlidingNavTabs<T extends string>({
  items,
  activeId,
  onSelect,
  ariaLabel = 'Primary navigation',
}: LucianSlidingNavTabsProps<T>) {
  const pillRef = useRef<HTMLSpanElement>(null);
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const firstRunRef = useRef(true);

  const syncPill = (animate: boolean) => {
    const idx = Math.max(0, items.findIndex((item) => item.id === activeId));
    const el = tabRefs.current[idx];
    const pill = pillRef.current;
    if (!el || !pill) return;
    if (!animate) pill.style.transition = 'none';
    pill.style.setProperty('--x', `${el.offsetLeft}px`);
    pill.style.setProperty('--w', `${el.offsetWidth}px`);
    if (!animate) {
      void pill.offsetWidth;
      pill.style.transition = '';
    }
  };

  useLayoutEffect(() => {
    syncPill(!firstRunRef.current);
    firstRunRef.current = false;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId, items.map((i) => i.count).join(',')]);

  useEffect(() => {
    const onResize = () => syncPill(false);
    window.addEventListener('resize', onResize);
    if (document.fonts && document.fonts.ready) {
      document.fonts.ready.then(() => syncPill(false));
    }
    return () => window.removeEventListener('resize', onResize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId]);

  return (
    <div className="ux u039-nav" role="tablist" aria-label={ariaLabel}>
      <span ref={pillRef} className="u039-pill" aria-hidden="true" />
      {items.map((item, idx) => {
        const selected = item.id === activeId;
        return (
          <button
            key={item.id}
            ref={(el) => {
              tabRefs.current[idx] = el;
            }}
            type="button"
            role="tab"
            className="u039-tab"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onSelect(item.id)}
            onKeyDown={(e) => {
              const dir = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
              if (!dir) return;
              e.preventDefault();
              const nextIdx = (idx + dir + items.length) % items.length;
              onSelect(items[nextIdx].id);
              tabRefs.current[nextIdx]?.focus();
            }}
          >
            {item.icon}
            <span>{item.label}</span>
            {item.count !== undefined && item.count > 0 && (
              <span className="u039-count">{item.count}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}
