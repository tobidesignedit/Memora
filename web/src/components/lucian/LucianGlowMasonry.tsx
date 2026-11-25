/* Lucian UX/UI Prompt #228 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#228 */
import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import './lucian-228.css';

interface LucianGlowMasonryProps {
  children: ReactNode;
  className?: string;
  ariaLabel?: string;
}

export function LucianGlowMasonry({
  children,
  className = 'memora-masonry',
  ariaLabel = 'Memory gallery',
}: LucianGlowMasonryProps) {
  const rootRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;

    const rm = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let pointer = { x: -500, y: -500 };
    let opacity = 0;
    let targetOpacity = 0;
    let raf = 0;
    let last = 0;
    let alive = true;

    const apply = () => {
      const cards = root.querySelectorAll<HTMLElement>('.u228-card');
      cards.forEach((el) => {
        const r = el.getBoundingClientRect();
        const lx = pointer.x - r.left;
        const ly = pointer.y - r.top;
        el.style.setProperty('--cx', `${lx.toFixed(1)}px`);
        el.style.setProperty('--cy', `${ly.toFixed(1)}px`);
        el.style.setProperty('--o', opacity.toFixed(3));
      });
    };

    const frame = (now: number) => {
      raf = 0;
      if (!alive) return;
      const dt = Math.min(0.05, Math.max(0, (now - last) / 1000));
      last = now;
      opacity += (targetOpacity - opacity) * (rm ? 1 : Math.min(1, dt * (targetOpacity ? 8 : 2.5)));
      apply();
      if (Math.abs(targetOpacity - opacity) > 0.005) {
        raf = requestAnimationFrame(frame);
      }
    };

    const kick = () => {
      if (!raf) {
        last = performance.now();
        raf = requestAnimationFrame(frame);
      }
    };

    const onMove = (e: PointerEvent) => {
      pointer = { x: e.clientX, y: e.clientY };
      targetOpacity = 1;
      apply();
      kick();
    };

    const onLeave = () => {
      targetOpacity = 0;
      kick();
    };

    root.addEventListener('pointermove', onMove, { passive: true });
    root.addEventListener('pointerleave', onLeave);

    return () => {
      alive = false;
      cancelAnimationFrame(raf);
      root.removeEventListener('pointermove', onMove);
      root.removeEventListener('pointerleave', onLeave);
    };
  }, []);

  return (
    <section ref={rootRef} className={`ux u228-scope ${className}`} aria-label={ariaLabel}>
      {children}
    </section>
  );
}
