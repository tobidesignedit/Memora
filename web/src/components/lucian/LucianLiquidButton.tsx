/* Lucian UX/UI Prompt #001 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#001 */
import { useRef } from 'react';
import type { ReactNode } from 'react';
import './lucian-001.css';

interface LucianLiquidButtonProps {
  label: string;
  doneLabel?: string;
  icon?: ReactNode;
  state?: 'idle' | 'load' | 'ok';
  type?: 'button' | 'submit';
  disabled?: boolean;
  onClick?: () => void;
}

export function LucianLiquidButton({
  label,
  doneLabel = 'Saved',
  icon,
  state = 'idle',
  type = 'button',
  disabled = false,
  onClick,
}: LucianLiquidButtonProps) {
  const magRef = useRef<HTMLDivElement>(null);
  const btnRef = useRef<HTMLButtonElement>(null);

  return (
    <div
      className="ux u001"
      data-state={state}
      onPointerMove={(e) => {
        const btn = btnRef.current;
        const mag = magRef.current;
        if (!btn || !mag || disabled) return;
        const rm = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        const r = btn.getBoundingClientRect();
        const dx = e.clientX - (r.left + r.width / 2);
        const dy = e.clientY - (r.top + r.height / 2);
        const pull = rm ? 0 : Math.max(0, 1 - Math.hypot(dx, dy) / 170);
        mag.style.translate = `${(dx * 0.14 * pull).toFixed(1)}px ${(dy * 0.14 * pull).toFixed(1)}px`;
        btn.style.setProperty('--mx', `${(((e.clientX - r.left) / r.width) * 100).toFixed(1)}%`);
        btn.style.setProperty('--my', `${(((e.clientY - r.top) / r.height) * 100).toFixed(1)}%`);
      }}
      onPointerLeave={() => {
        if (magRef.current) magRef.current.style.translate = '0px 0px';
      }}
    >
      <div ref={magRef} className="u001-mag">
        <button
          ref={btnRef}
          className="u001-btn"
          type={type}
          disabled={disabled || state === 'load'}
          aria-busy={state === 'load'}
          onClick={onClick}
        >
          <span className="u001-sheen" aria-hidden="true" />
          <span className="u001-txt">
            {icon}
            {label}
          </span>
          <span className="u001-spin" aria-hidden="true">
            <svg viewBox="0 0 30 30">
              <circle cx="15" cy="15" r="10" />
            </svg>
          </span>
          <span className="u001-done" aria-hidden="true">
            <b>
              <svg viewBox="0 0 16 16">
                <path d="M3.5 8.4l3 3 6-6.4" />
              </svg>
            </b>
            {doneLabel}
          </span>
        </button>
      </div>
    </div>
  );
}
