import { useEffect, useRef, useState } from 'react';
import {
  ArrowUp,
  ArrowUpRight,
  Loader2,
  Mic,
  Square,
  Volume2,
} from 'lucide-react';
import { playSpokenRecap, searchMemoriesWithAI } from '../services/hybridAi';
import { LucianVoiceOrb } from './lucian/LucianVoiceOrb';
import { LucianTraceableAnswer } from './lucian/LucianTraceableAnswer';
import type {
  MemoryItem,
  PrivacyRoutingMode,
  RemoteConfigValues,
  SearchAnswer,
} from '../types/memora';

interface SearchAssistantViewProps {
  memories: MemoryItem[];
  defaultMode: PrivacyRoutingMode;
  remoteConfig: RemoteConfigValues;
  initialQuery?: string;
  onOpenMemory: (memory: MemoryItem) => void;
  onBenchmarksChanged: () => void;
}

const DEFAULT_PROMPTS = [
  'Find any upcoming payment deadlines or bills',
  'When and where is my next exam?',
  'Summarize everything saved for my launch or meetup',
  'What action items are still pending across my vault?',
];

export function SearchAssistantView({
  memories,
  defaultMode,
  remoteConfig,
  initialQuery = '',
  onOpenMemory,
  onBenchmarksChanged,
}: SearchAssistantViewProps) {
  const [query, setQuery] = useState(initialQuery);
  const [loading, setLoading] = useState(false);
  const [history, setHistory] = useState<SearchAnswer[]>([]);
  const [listening, setListening] = useState(false);
  const [speakingIdx, setSpeakingIdx] = useState<number | null>(null);
  const stopAudioRef = useRef<(() => void) | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const autoRanRef = useRef(false);

  const memoryMap = new Map(memories.map((m) => [m.id, m]));

  async function runQuery(qText: string) {
    const trimmed = qText.trim();
    if (!trimmed || loading) return;
    setQuery(trimmed);
    setLoading(true);
    try {
      const answer = await searchMemoriesWithAI({
        query: trimmed,
        memories,
        mode: defaultMode,
        remoteConfig,
      });
      setHistory((prev) => [answer, ...prev]);
      onBenchmarksChanged();
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (initialQuery.trim() && !autoRanRef.current) {
      autoRanRef.current = true;
      void runQuery(initialQuery);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialQuery]);

  useEffect(() => {
    return () => {
      stopAudioRef.current?.();
    };
  }, []);

  function startVoiceQuestion() {
    const win = window as unknown as Record<string, unknown>;
    const SR = (win.SpeechRecognition || win.webkitSpeechRecognition) as
      | (new () => {
          lang: string;
          onresult: (e: { results: ArrayLike<{ 0: { transcript: string } }> }) => void;
          onend: () => void;
          start: () => void;
        })
      | undefined;
    if (!SR) {
      alert('Voice search is not supported in this browser — type your question instead.');
      return;
    }
    const sr = new SR();
    sr.lang = 'en-US';
    setListening(true);
    sr.onresult = (e) => {
      const text = e.results[0]?.[0]?.transcript;
      if (text) {
        setQuery(text);
        void runQuery(text);
      }
    };
    sr.onend = () => setListening(false);
    sr.start();
  }

  async function handleListen(idx: number, text: string) {
    if (speakingIdx === idx) {
      stopAudioRef.current?.();
      stopAudioRef.current = null;
      setSpeakingIdx(null);
      return;
    }
    stopAudioRef.current?.();
    setSpeakingIdx(idx);
    const res = await playSpokenRecap(text, remoteConfig);
    stopAudioRef.current = res.stop;
    onBenchmarksChanged();
    window.setTimeout(() => {
      setSpeakingIdx((curr) => (curr === idx ? null : curr));
    }, 18_000);
  }

  const orbState = listening
    ? 'listen'
    : loading
      ? 'think'
      : speakingIdx !== null
        ? 'speak'
        : 'idle';

  return (
    <div className="max-w-3xl mx-auto flex flex-col gap-7 pb-8">
      {/* Calm Editorial Header + Lucian #458 Interactive AI Voice Orb */}
      <header className="text-center pt-1 flex flex-col items-center gap-3">
        <LucianVoiceOrb
          state={orbState}
          label={
            listening
              ? 'Listening to your question…'
              : loading
                ? `Searching ${memories.length} memories…`
                : speakingIdx !== null
                  ? 'Playing Gemini Spoken Recap — tap to stop'
                  : 'Tap orb to ask Memora by voice'
          }
          onClick={() => {
            if (speakingIdx !== null) {
              stopAudioRef.current?.();
              stopAudioRef.current = null;
              setSpeakingIdx(null);
            } else {
              startVoiceQuestion();
            }
          }}
        />
        <h1 className="font-display text-3xl sm:text-[42px] font-medium leading-[1.12]">
          What are you trying to <span className="italic">recall?</span>
        </h1>
        <p className="text-sm sm:text-[15px] max-w-xl mx-auto" style={{ color: 'var(--text-secondary)' }}>
          Ask across your screenshots, receipts, voice notes, and links. Hover any citation badge to preview the exact source.
        </p>
      </header>

      {/* Sculpted Frosted Omnibox Composer */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void runQuery(query);
        }}
        className="memora-omnibox rounded-full p-2 pl-5 flex items-center gap-2"
      >
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Ask your vault — e.g. 'Where is the screenshot with my payment deadline?'"
          className="flex-1 bg-transparent border-0 text-[15px] py-2 focus:outline-none min-w-0"
          style={{ color: 'var(--text-primary)' }}
        />
        <button
          type="button"
          onClick={startVoiceQuestion}
          className="memora-press w-10 h-10 rounded-full flex items-center justify-center shrink-0"
          style={{
            background: listening ? 'var(--semantic-danger)' : 'var(--bg-soft)',
            color: listening ? '#FFFFFF' : 'var(--text-secondary)',
          }}
          title="Ask by voice"
          aria-label="Ask by voice"
        >
          <Mic className="w-4 h-4" />
        </button>
        <button
          type="submit"
          disabled={loading || !query.trim()}
          className="memora-press w-10 h-10 rounded-full flex items-center justify-center shrink-0 disabled:opacity-40"
          style={{ background: 'var(--text-primary)', color: 'var(--bg-canvas)' }}
          aria-label="Ask Memora"
        >
          {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <ArrowUp className="w-4 h-4" />}
        </button>
      </form>

      {/* Editorial Suggested Prompts */}
      {history.length === 0 && !loading && (
        <section className="grid sm:grid-cols-2 gap-3">
          {DEFAULT_PROMPTS.map((prompt) => (
            <button
              key={prompt}
              type="button"
              onClick={() => void runQuery(prompt)}
              className="memora-card memora-press text-left p-4 flex items-start justify-between gap-3 group"
              style={{
                borderRadius: '20px',
                color: 'var(--text-primary)',
              }}
            >
              <span className="text-[14px] leading-snug">{prompt}</span>
              <ArrowUpRight
                className="w-4 h-4 shrink-0 mt-0.5 opacity-45 group-hover:opacity-100 transition-opacity"
                style={{ color: 'var(--accent-primary)' }}
              />
            </button>
          ))}
        </section>
      )}

      {/* Traceable Source-Linked AI Answer Cards (Lucian UX/UI Prompt #224) */}
      <div className="flex flex-col gap-6">
        {history.map((item, idx) => {
          const isSpeaking = speakingIdx === idx;
          return (
            <article
              key={`${item.query}-${idx}`}
              className="memora-card p-6 sm:p-7 flex flex-col gap-5 animate-memora-enter"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h2 className="font-display text-2xl font-medium leading-snug max-w-xl">
                  {item.query}
                </h2>
                <button
                  type="button"
                  onClick={() => void handleListen(idx, item.answer)}
                  className="memora-press inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-medium border shrink-0"
                  style={{
                    background: isSpeaking ? 'var(--accent-primary-soft)' : 'var(--bg-soft)',
                    borderColor: isSpeaking ? 'var(--accent-primary)' : 'var(--border-subtle)',
                    color: isSpeaking ? 'var(--accent-primary)' : 'var(--text-primary)',
                  }}
                >
                  {isSpeaking ? (
                    <>
                      <span className="flex items-end gap-[2px] h-3" aria-hidden="true">
                        <span className="w-[2px] h-full rounded-full animate-wave-bar" style={{ background: 'var(--accent-primary)' }} />
                        <span className="w-[2px] h-full rounded-full animate-wave-bar" style={{ background: 'var(--accent-primary)', animationDelay: '180ms' }} />
                        <span className="w-[2px] h-full rounded-full animate-wave-bar" style={{ background: 'var(--accent-primary)', animationDelay: '360ms' }} />
                      </span>
                      <Square className="w-3 h-3 fill-current" />
                      <span>Stop Recap</span>
                    </>
                  ) : (
                    <>
                      <Volume2 className="w-3.5 h-3.5" />
                      <span>Spoken Recap</span>
                    </>
                  )}
                </button>
              </div>

              <LucianTraceableAnswer
                answer={item.answer}
                citations={item.citations}
                memoryMap={memoryMap}
                onOpenMemory={onOpenMemory}
              />

              {/* Suggested Follow-Ups + Quiet Provenance */}
              <div
                className="pt-4 border-t flex flex-wrap items-center justify-between gap-3"
                style={{ borderColor: 'var(--border-subtle)' }}
              >
                <div className="flex flex-wrap gap-1.5">
                  {item.suggestedFollowUps.slice(0, 3).map((fup) => (
                    <button
                      key={fup}
                      type="button"
                      onClick={() => void runQuery(fup)}
                      className="memora-press text-xs px-3 py-1 rounded-full border transition-colors hover:border-[var(--accent-primary)]"
                      style={{
                        borderColor: 'var(--border-subtle)',
                        color: 'var(--text-secondary)',
                      }}
                    >
                      {fup}
                    </button>
                  ))}
                </div>

                <div className="inline-flex items-center gap-2 text-[11px] font-mono-data" style={{ color: 'var(--text-muted)' }}>
                  <span
                    className="w-1.5 h-1.5 rounded-full"
                    style={{
                      background:
                        item.source === 'on_device'
                          ? 'var(--semantic-ondevice)'
                          : item.source === 'in_cloud'
                            ? 'var(--accent-primary)'
                            : 'var(--text-muted)',
                    }}
                  />
                  <span>
                    {item.source === 'on_device'
                      ? 'On-Device'
                      : item.source === 'in_cloud'
                        ? 'Cloud Gemini'
                        : 'Local Index'}{' '}
                    · {item.latencyMs}ms
                  </span>
                </div>
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
