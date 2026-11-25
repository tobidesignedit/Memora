/* Lucian UX/UI Prompt #224 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#224 */
import { useMemo, useRef, useState } from 'react';
import type { MemoryItem, SearchCitation } from '../../types/memora';
import './lucian-224.css';

interface LucianTraceableAnswerProps {
  answer: string;
  citations: SearchCitation[];
  memoryMap: Map<string, MemoryItem>;
  onOpenMemory: (memory: MemoryItem) => void;
}

export function LucianTraceableAnswer({
  answer,
  citations,
  memoryMap,
  onOpenMemory,
}: LucianTraceableAnswerProps) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const [activeCitIdx, setActiveCitIdx] = useState<number | null>(null);
  const [activeSentenceIdx, setActiveSentenceIdx] = useState<number | null>(null);
  const [popoverPos, setPopoverPos] = useState<{ left: number; top: number } | null>(null);

  // Split answer into natural sentences and map each sentence to its closest citation
  const sentences = useMemo(() => {
    const raw = answer
      .split(/(?<=[.!?])\s+|\n+/)
      .map((s) => s.trim())
      .filter(Boolean);
    if (raw.length === 0) return [{ text: answer, citIndexes: citations.length > 0 ? [0] : [] }];
    return raw.map((text, idx) => {
      if (citations.length === 0) return { text, citIndexes: [] as number[] };
      const lower = text.toLowerCase();
      const matched: number[] = [];
      citations.forEach((cit, cIdx) => {
        const mem = memoryMap.get(cit.memoryId);
        const keywords = `${cit.title} ${mem?.category || ''} ${mem?.tags.join(' ') || ''}`
          .toLowerCase()
          .split(/\W+/)
          .filter((w) => w.length > 3);
        if (keywords.some((kw) => lower.includes(kw))) {
          matched.push(cIdx);
        }
      });
      if (matched.length === 0 && idx < citations.length) {
        matched.push(idx);
      } else if (matched.length === 0 && idx === raw.length - 1) {
        matched.push(0);
      }
      return { text, citIndexes: matched.slice(0, 2) };
    });
  }, [answer, citations, memoryMap]);

  const activeCit = activeCitIdx !== null ? citations[activeCitIdx] : null;
  const activeMem = activeCit ? memoryMap.get(activeCit.memoryId) : undefined;

  const showPreview = (btn: HTMLElement, sentenceIdx: number, citIdx: number) => {
    setActiveCitIdx(citIdx);
    setActiveSentenceIdx(sentenceIdx);
    const wrap = wrapRef.current;
    if (!wrap) return;
    const wr = wrap.getBoundingClientRect();
    const br = btn.getBoundingClientRect();
    const left = Math.max(140, Math.min(wr.width - 140, br.left + br.width / 2 - wr.left));
    const top = Math.max(8, br.top - wr.top);
    setPopoverPos({ left, top });
  };

  const clearPreview = () => {
    setActiveCitIdx(null);
    setActiveSentenceIdx(null);
    setPopoverPos(null);
  };

  return (
    <div ref={wrapRef} className="ux u224-wrap">
      {activeCit && popoverPos && (
        <div
          className="u224-pv"
          role="tooltip"
          style={{ left: `${popoverPos.left}px`, top: `${popoverPos.top}px` }}
        >
          <header>
            <i>{(activeCitIdx ?? 0) + 1}</i>
            <span>{activeMem?.category || 'Vault Memory'}</span>
          </header>
          <b>{activeCit.title || activeMem?.title}</b>
          <p>
            <mark>{activeCit.excerpt || activeMem?.summary.slice(0, 120)}</mark>
          </p>
        </div>
      )}

      <p className="u224-an">
        {sentences.map((seg, sIdx) => {
          const highlighted =
            activeSentenceIdx === sIdx ||
            (activeCitIdx !== null && seg.citIndexes.includes(activeCitIdx));
          return (
            <span key={sIdx}>
              <span className={`u224-seg ${highlighted ? 'is-hl' : ''}`}>{seg.text}</span>
              {seg.citIndexes.map((cIdx) => {
                const cit = citations[cIdx];
                const mem = cit ? memoryMap.get(cit.memoryId) : undefined;
                return (
                  <button
                    key={`${sIdx}-${cIdx}`}
                    type="button"
                    className={`u224-ct ${activeCitIdx === cIdx ? 'is-on' : ''}`}
                    aria-label={`Source ${cIdx + 1}: ${cit?.title || mem?.title || 'Memory'}`}
                    onPointerEnter={(e) => showPreview(e.currentTarget, sIdx, cIdx)}
                    onFocus={(e) => showPreview(e.currentTarget, sIdx, cIdx)}
                    onPointerLeave={clearPreview}
                    onBlur={clearPreview}
                    onClick={() => mem && onOpenMemory(mem)}
                  >
                    {cIdx + 1}
                  </button>
                );
              })}{' '}
            </span>
          );
        })}
      </p>

      {citations.length > 0 && (
        <div className="pt-4 mt-4 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
          <span className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>
            Traceable Sources ({citations.length})
          </span>
          <div className="u224-sl">
            {citations.map((cit, cIdx) => {
              const mem = memoryMap.get(cit.memoryId);
              return (
                <button
                  key={cit.memoryId}
                  type="button"
                  className={`u224-sc ${activeCitIdx === cIdx ? 'is-on' : ''}`}
                  onPointerEnter={() => {
                    setActiveCitIdx(cIdx);
                    setActiveSentenceIdx(null);
                    setPopoverPos(null);
                  }}
                  onFocus={() => {
                    setActiveCitIdx(cIdx);
                    setActiveSentenceIdx(null);
                    setPopoverPos(null);
                  }}
                  onPointerLeave={clearPreview}
                  onBlur={clearPreview}
                  onClick={() => mem && onOpenMemory(mem)}
                >
                  <header>
                    <i>{cIdx + 1}</i>
                    {mem?.mediaThumbnail && (
                      <img
                        src={mem.mediaThumbnail}
                        alt=""
                        className="w-5 h-5 rounded object-cover ml-auto"
                      />
                    )}
                  </header>
                  <b>{cit.title || mem?.title}</b>
                  <small>{mem?.category || 'Saved memory'}</small>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
