/* Lucian UX/UI Prompt #458 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#458 */
import { useEffect, useId, useRef } from 'react';
import './lucian-458.css';

export type VoiceOrbState = 'idle' | 'listen' | 'think' | 'speak';

interface LucianVoiceOrbProps {
  state: VoiceOrbState;
  label?: string;
  onClick: () => void;
}

const PARAMS: Record<
  VoiceOrbState,
  { sp: number; br: number; pu: number; base: number; gl: number; amp: number }
> = {
  idle: { sp: 0.95, br: 0.025, pu: 0, base: 1, gl: 0.52, amp: 2.3 },
  listen: { sp: 2.1, br: 0.015, pu: 0.045, base: 1.05, gl: 0.88, amp: 3.8 },
  think: { sp: 4.2, br: 0.01, pu: 0.015, base: 0.96, gl: 0.72, amp: 4.4 },
  speak: { sp: 2.4, br: 0.01, pu: 0.095, base: 1.03, gl: 0.92, amp: 4.1 },
};

const DEFAULT_LABELS: Record<VoiceOrbState, string> = {
  idle: 'Tap Memora to record a voice thought',
  listen: 'Listening — speak naturally',
  think: 'Understanding on-device…',
  speak: 'Speaking recap…',
};

export function LucianVoiceOrb({ state, label, onClick }: LucianVoiceOrbProps) {
  const orbRef = useRef<HTMLSpanElement>(null);
  const glowRef = useRef<HTMLSpanElement>(null);
  const n1Ref = useRef<SVGCircleElement>(null);
  const n2Ref = useRef<SVGCircleElement>(null);
  const n3Ref = useRef<SVGCircleElement>(null);
  const l12Ref = useRef<SVGLineElement>(null);
  const l13Ref = useRef<SVGLineElement>(null);
  const l23Ref = useRef<SVGLineElement>(null);
  const ringRef = useRef<SVGCircleElement>(null);
  const stateRef = useRef<VoiceOrbState>(state);
  const statusId = useId();
  stateRef.current = state;

  useEffect(() => {
    const orb = orbRef.current;
    const gw = glowRef.current;
    if (!orb || !gw) return;

    const rm = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const cur = { ...PARAMS[stateRef.current] };
    let ang = 0;
    let last = 0;
    let raf = 0;
    let alive = true;

    // Base Memora constellation coordinates in 64x64 viewBox
    const b1 = { x: 23, y: 25 };
    const b2 = { x: 42, y: 23 };
    const b3 = { x: 35, y: 43 };

    const setCoords = (
      x1: number,
      y1: number,
      r1: number,
      x2: number,
      y2: number,
      r2: number,
      x3: number,
      y3: number,
      r3: number,
    ) => {
      n1Ref.current?.setAttribute('cx', x1.toFixed(2));
      n1Ref.current?.setAttribute('cy', y1.toFixed(2));
      n1Ref.current?.setAttribute('r', r1.toFixed(2));

      n2Ref.current?.setAttribute('cx', x2.toFixed(2));
      n2Ref.current?.setAttribute('cy', y2.toFixed(2));
      n2Ref.current?.setAttribute('r', r2.toFixed(2));

      n3Ref.current?.setAttribute('cx', x3.toFixed(2));
      n3Ref.current?.setAttribute('cy', y3.toFixed(2));
      n3Ref.current?.setAttribute('r', r3.toFixed(2));

      l12Ref.current?.setAttribute('x1', x1.toFixed(2));
      l12Ref.current?.setAttribute('y1', y1.toFixed(2));
      l12Ref.current?.setAttribute('x2', x2.toFixed(2));
      l12Ref.current?.setAttribute('y2', y2.toFixed(2));

      l13Ref.current?.setAttribute('x1', x1.toFixed(2));
      l13Ref.current?.setAttribute('y1', y1.toFixed(2));
      l13Ref.current?.setAttribute('x2', x3.toFixed(2));
      l13Ref.current?.setAttribute('y2', y3.toFixed(2));

      l23Ref.current?.setAttribute('x1', x2.toFixed(2));
      l23Ref.current?.setAttribute('y1', y2.toFixed(2));
      l23Ref.current?.setAttribute('x2', x3.toFixed(2));
      l23Ref.current?.setAttribute('y2', y3.toFixed(2));
    };

    const draw = (t: number, dt: number) => {
      const sNow = stateRef.current;
      const tg = PARAMS[sNow];
      const f = rm ? 1 : 1 - Math.exp(-dt * 3.5);
      cur.sp += (tg.sp - cur.sp) * f;
      cur.br += (tg.br - cur.br) * f;
      cur.pu += (tg.pu - cur.pu) * f;
      cur.base += (tg.base - cur.base) * f;
      cur.gl += (tg.gl - cur.gl) * f;
      cur.amp += (tg.amp - cur.amp) * f;
      ang += cur.sp * dt;

      const breathe = Math.sin(t * 2.1) * cur.br;
      const env = Math.max(0, Math.sin(t * 9.4)) * (0.55 + 0.45 * Math.sin(t * 2.3 + 0.7));
      const hum = Math.max(0, Math.sin(t * 6.1) * Math.sin(t * 1.9));
      const pulse = cur.pu * (sNow === 'listen' ? hum : env);
      const scale = cur.base + breathe + pulse;

      orb.style.transform = `scale(${scale.toFixed(4)})`;
      gw.style.opacity = Math.min(1, cur.gl + pulse * 2.2).toFixed(3);
      gw.style.transform = `scale(${(scale + pulse * 1.3 + breathe).toFixed(4)})`;

      if (rm) {
        setCoords(b1.x, b1.y, 7.2, b2.x, b2.y, 4.8, b3.x, b3.y, 5.6);
        return;
      }

      // Active orbiting + breathing movement for the 3 Memora constellation nodes
      const spin = sNow === 'think' ? ang * 1.35 : Math.sin(ang * 0.55) * 0.24;
      const cosS = Math.cos(spin);
      const sinS = Math.sin(spin);
      const rotate = (x: number, y: number) => {
        const dx = x - 32;
        const dy = y - 32;
        return {
          x: 32 + dx * cosS - dy * sinS,
          y: 32 + dx * sinS + dy * cosS,
        };
      };

      const p1 = rotate(
        b1.x + Math.cos(ang * 1.15) * cur.amp,
        b1.y + Math.sin(ang * 1.45) * cur.amp,
      );
      const p2 = rotate(
        b2.x + Math.cos(ang * 1.35 + 2.1) * cur.amp,
        b2.y + Math.sin(ang * 1.1 + 1.2) * cur.amp,
      );
      const p3 = rotate(
        b3.x + Math.cos(ang * 1.05 + 4.2) * cur.amp,
        b3.y + Math.sin(ang * 1.3 + 3.4) * cur.amp,
      );

      const beat = pulse * 16;
      const r1 = 7.2 + Math.sin(ang * 1.8) * 0.7 + beat * 0.55;
      const r2 = 4.8 + Math.sin(ang * 2.1 + 1.7) * 0.65 + beat * 0.45;
      const r3 = 5.6 + Math.sin(ang * 1.6 + 3.1) * 0.65 + beat * 0.5;

      setCoords(p1.x, p1.y, r1, p2.x, p2.y, r2, p3.x, p3.y, r3);

      if (ringRef.current) {
        ringRef.current.style.transform = `rotate(${(ang * 34).toFixed(1)}deg)`;
      }
    };

    const frame = (now: number) => {
      if (!alive) return;
      const dt = last ? Math.min(0.05, (now - last) / 1000) : 0.016;
      last = now;
      draw(now / 1000, dt);
      raf = requestAnimationFrame(frame);
    };

    if (rm) {
      draw(0, 1);
    } else {
      raf = requestAnimationFrame(frame);
    }

    return () => {
      alive = false;
      cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <div className="ux u458" data-s={state}>
      <button
        type="button"
        className="u458-b"
        aria-label="Voice assistant orb"
        aria-describedby={statusId}
        onClick={onClick}
      >
        <span ref={glowRef} className="u458-gw" aria-hidden="true" />
        <span className="u458-rp" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
        <span ref={orbRef} className="u458-o" aria-hidden="true">
          <svg viewBox="0 0 64 64" className="u458-logo">
            <circle
              ref={ringRef}
              className="u458-orbit"
              cx="32"
              cy="32"
              r="24.5"
            />
            <line ref={l12Ref} className="u458-edge" x1="23" y1="25" x2="42" y2="23" />
            <line ref={l13Ref} className="u458-edge" x1="23" y1="25" x2="35" y2="43" />
            <line ref={l23Ref} className="u458-edge" x1="42" y1="23" x2="35" y2="43" />
            <circle ref={n1Ref} className="u458-n1" cx="23" cy="25" r="7.2" />
            <circle ref={n2Ref} className="u458-n2" cx="42" cy="23" r="4.8" />
            <circle ref={n3Ref} className="u458-n3" cx="35" cy="43" r="5.6" />
          </svg>
        </span>
      </button>
      <p id={statusId} className="u458-st font-mono-data" role="status">
        {label || DEFAULT_LABELS[state]}
      </p>
    </div>
  );
}
