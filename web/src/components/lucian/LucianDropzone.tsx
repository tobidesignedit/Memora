/* Lucian UX/UI Prompt #432 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#432 */
import { useEffect, useRef, useState } from 'react';
import { Camera } from 'lucide-react';
import './lucian-432.css';

interface LucianDropzoneProps {
  onFileSelected: (file: File) => void;
}

const EXT_COLORS: Record<string, string> = {
  pdf: '#E5484D',
  png: '#8E4EC6',
  jpg: '#0090FF',
  jpeg: '#0090FF',
  webp: '#0EA5E9',
  doc: '#2B63D9',
  docx: '#2B63D9',
  mp3: '#D97706',
  webm: '#D97706',
};

export function LucianDropzone({ onFileSelected }: LucianDropzoneProps) {
  const [dragOver, setDragOver] = useState(false);
  const [animFile, setAnimFile] = useState<{ name: string; ext: string; pct: number; done: boolean } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const rafRef = useRef(0);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      cancelAnimationFrame(rafRef.current);
      if (timerRef.current) window.clearTimeout(timerRef.current);
    };
  }, []);

  const ingestFile = (f: File) => {
    const ext = (/\.([a-z0-9]+)$/i.exec(f.name)?.[1] || 'file').toLowerCase();
    const rm = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (rm) {
      onFileSelected(f);
      return;
    }
    setAnimFile({ name: f.name, ext, pct: 0, done: false });
    const start = performance.now() + 180;
    const dur = 520;
    cancelAnimationFrame(rafRef.current);
    const step = (now: number) => {
      const k = Math.max(0, Math.min(1, (now - start) / dur));
      const eased = k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;
      setAnimFile({ name: f.name, ext, pct: eased, done: k >= 1 });
      if (k < 1) {
        rafRef.current = requestAnimationFrame(step);
      } else {
        timerRef.current = window.setTimeout(() => {
          setAnimFile(null);
          onFileSelected(f);
        }, 420);
      }
    };
    rafRef.current = requestAnimationFrame(step);
  };

  return (
    <div
      className={`ux u432 ${dragOver ? 'is-drag' : ''} ${animFile ? 'is-up' : ''} ${animFile?.done ? 'is-done' : ''}`}
    >
      <div
        className="u432-dz"
        aria-label="Drop or select a screenshot, receipt, event flyer, or PDF"
        onClick={(e) => {
          if ((e.target as HTMLElement).closest('button')) return;
          fileInputRef.current?.click();
        }}
        onDragEnter={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragOver={(e) => {
          e.preventDefault();
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          const f = e.dataTransfer.files?.[0];
          if (f) ingestFile(f);
        }}
      >
        <div className="u432-idle">
          <span className="u432-ar" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="M12 16V5M7 9.5 12 4.5l5 5" />
              <path d="M5 15v2.5A2.5 2.5 0 0 0 7.5 20h9a2.5 2.5 0 0 0 2.5-2.5V15" />
            </svg>
          </span>
          <b>{dragOver ? 'Let go to capture into vault' : 'Drop screenshot, flyer, receipt, or PDF'}</b>
          <small>Memora reads text, amounts &amp; upcoming dates privately</small>
          <div className="flex flex-wrap justify-center gap-2 mt-3">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="memora-press px-4 py-1.5 rounded-full text-xs font-medium border"
              style={{
                background: 'var(--bg-surface)',
                borderColor: 'var(--border-strong)',
                color: 'var(--text-primary)',
              }}
            >
              Browse Files
            </button>
            <button
              type="button"
              onClick={() => cameraInputRef.current?.click()}
              className="memora-press inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-xs font-medium border"
              style={{
                background: 'var(--bg-surface)',
                borderColor: 'var(--border-strong)',
                color: 'var(--text-primary)',
              }}
            >
              <Camera className="w-3.5 h-3.5" />
              Camera
            </button>
          </div>
        </div>

        <div className="u432-up" aria-hidden={!animFile}>
          <div className="u432-st">
            <svg className="u432-ring" viewBox="0 0 100 100">
              <circle className="tk" cx="50" cy="50" r="46" />
              <circle
                className="pg"
                cx="50"
                cy="50"
                r="46"
                style={{ strokeDashoffset: (289 * (1 - (animFile?.pct || 0))).toFixed(1) }}
              />
            </svg>
            <svg className="u432-doc" viewBox="0 0 50 62">
              <path className="pp" d="M6 1h26l15 15v39a6 6 0 0 1-6 6H6a6 6 0 0 1-6-6V7a6 6 0 0 1 6-6z" />
              <path className="fd" d="M32 1v10a5 5 0 0 0 5 5h10z" />
              <rect className="ln" x="9" y="22" width="24" height="3" rx="1.5" />
              <rect className="ln" x="9" y="29" width="30" height="3" rx="1.5" />
              <rect
                className="tg"
                x="-4"
                y="40"
                width="28"
                height="14"
                rx="3.5"
                style={{ ['--c' as string]: EXT_COLORS[animFile?.ext || ''] || '#67C6FF' }}
              />
              <text x="10" y="50" textAnchor="middle">
                {(animFile?.ext || 'IMG').slice(0, 4).toUpperCase()}
              </text>
            </svg>
            <span className="u432-ok">
              <svg viewBox="0 0 24 24">
                <path d="m5 12.5 4.5 4.5L19 7.5" />
              </svg>
            </span>
          </div>
          <p className="u432-nm">{animFile?.name || ''}</p>
        </div>

        <input
          ref={fileInputRef}
          type="file"
          accept="image/*,application/pdf,audio/*"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) ingestFile(f);
            e.target.value = '';
          }}
        />
        <input
          ref={cameraInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) ingestFile(f);
            e.target.value = '';
          }}
        />
      </div>
    </div>
  );
}
