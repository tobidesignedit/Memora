import { useState } from 'react';
import {
  ArrowUpRight,
  CheckCircle2,
  FileText,
  GitBranch,
  Globe,
  Image as ImageIcon,
  Loader2,
  Mic,
  Receipt,
  Sparkles,
} from 'lucide-react';
import { synthesizeMemoryThread } from '../services/hybridAi';
import type {
  MemoryItem,
  MemoryThread,
  PrivacyRoutingMode,
  RemoteConfigValues,
} from '../types/memora';

interface ThreadsViewProps {
  threads: MemoryThread[];
  memories: MemoryItem[];
  defaultMode: PrivacyRoutingMode;
  remoteConfig: RemoteConfigValues;
  highlightTopic?: string;
  onUpdateThread: (thread: MemoryThread) => Promise<void>;
  onOpenMemory: (memory: MemoryItem) => void;
  onBenchmarksChanged: () => void;
}

export function ThreadsView({
  threads,
  memories,
  defaultMode,
  remoteConfig,
  highlightTopic,
  onUpdateThread,
  onOpenMemory,
  onBenchmarksChanged,
}: ThreadsViewProps) {
  const [synthesizingId, setSynthesizingId] = useState<string | null>(null);
  const memoryMap = new Map(memories.map((m) => [m.id, m]));

  async function handleSynthesize(thread: MemoryThread) {
    const items = thread.memoryIds
      .map((id) => memoryMap.get(id))
      .filter((m): m is MemoryItem => Boolean(m));
    if (items.length === 0) return;

    setSynthesizingId(thread.id);
    try {
      const res = await synthesizeMemoryThread({
        threadTitle: thread.title,
        memories: items,
        mode: defaultMode,
        remoteConfig,
      });
      await onUpdateThread({
        ...thread,
        summary: res.summary,
        keyInsights: res.keyInsights,
        nextActions: res.nextActions,
        inferenceSource: res.inferenceSource,
        updatedAt: Date.now(),
      });
      onBenchmarksChanged();
    } finally {
      setSynthesizingId(null);
    }
  }

  return (
    <div className="max-w-5xl mx-auto flex flex-col gap-8 pb-8">
      <header className="pt-2">
        <h1 className="font-display text-3xl sm:text-[40px] font-medium leading-[1.12]">
          Memory Threads
        </h1>
        <p className="text-sm sm:text-[15px] mt-2 max-w-xl" style={{ color: 'var(--text-secondary)' }}>
          Related screenshots, voice memos, flyers, and saved links assemble automatically into living knowledge dossiers.
        </p>
      </header>

      {threads.length === 0 ? (
        <div
          className="rounded-3xl border border-dashed p-14 text-center flex flex-col items-center gap-3"
          style={{ background: 'var(--bg-surface)', borderColor: 'var(--border-strong)' }}
        >
          <GitBranch className="w-7 h-7" style={{ color: 'var(--text-muted)' }} />
          <h2 className="font-display text-2xl font-medium">No threads connected yet</h2>
          <p className="text-sm max-w-md" style={{ color: 'var(--text-secondary)' }}>
            Capture two or more memories around the same exam, project, bill, or event and Memora weaves them together automatically.
          </p>
        </div>
      ) : (
        <div className="grid lg:grid-cols-2 gap-6">
          {threads.map((thread) => {
            const linked = thread.memoryIds
              .map((id) => memoryMap.get(id))
              .filter((m): m is MemoryItem => Boolean(m));
            const visualThumbs = linked.filter((m) => m.mediaThumbnail).slice(0, 3);
            const isHighlighted =
              Boolean(highlightTopic) &&
              thread.title.toLowerCase() === highlightTopic?.toLowerCase();
            const isBusy = synthesizingId === thread.id;

            return (
              <article
                key={thread.id}
                className="memora-card overflow-hidden flex flex-col animate-memora-enter"
                style={{
                  borderColor: isHighlighted ? 'var(--accent-primary)' : undefined,
                }}
              >
                {/* Visual Collage Mosaic Header when linked captures include imagery */}
                {visualThumbs.length > 0 && (
                  <div
                    className="grid gap-[2px] h-40 overflow-hidden border-b"
                    style={{
                      gridTemplateColumns:
                        visualThumbs.length === 1
                          ? '1fr'
                          : visualThumbs.length === 2
                            ? '1fr 1fr'
                            : '1.35fr 1fr',
                      borderColor: 'var(--border-subtle)',
                      background: 'var(--bg-soft)',
                    }}
                  >
                    {visualThumbs.length === 3 ? (
                      <>
                        <img
                          src={visualThumbs[0].mediaThumbnail}
                          alt={visualThumbs[0].title}
                          className="w-full h-full object-cover"
                        />
                        <div className="grid grid-rows-2 gap-[2px] h-full">
                          <img
                            src={visualThumbs[1].mediaThumbnail}
                            alt={visualThumbs[1].title}
                            className="w-full h-full object-cover"
                          />
                          <img
                            src={visualThumbs[2].mediaThumbnail}
                            alt={visualThumbs[2].title}
                            className="w-full h-full object-cover"
                          />
                        </div>
                      </>
                    ) : (
                      visualThumbs.map((m) => (
                        <img
                          key={m.id}
                          src={m.mediaThumbnail}
                          alt={m.title}
                          className="w-full h-full object-cover"
                        />
                      ))
                    )}
                  </div>
                )}

                <div className="p-6 flex flex-col gap-5 flex-1">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h2 className="font-display text-2xl font-medium leading-tight">
                        {thread.title}
                      </h2>
                      <p className="font-mono-data text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
                        {linked.length} connected {linked.length === 1 ? 'artifact' : 'artifacts'}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => void handleSynthesize(thread)}
                      disabled={isBusy}
                      className="memora-press inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-medium border shrink-0"
                      style={{
                        background: 'var(--bg-soft)',
                        borderColor: 'var(--border-subtle)',
                        color: 'var(--text-primary)',
                      }}
                    >
                      {isBusy ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <Sparkles className="w-3.5 h-3.5" style={{ color: 'var(--accent-primary)' }} />
                      )}
                      <span>{isBusy ? 'Synthesizing' : 'Synthesize'}</span>
                    </button>
                  </div>

                  <p className="text-[14.5px] leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                    {thread.summary}
                  </p>

                  {thread.keyInsights.length > 0 && (
                    <div className="flex flex-col gap-1.5">
                      <span className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>
                        Key Connections
                      </span>
                      <ul className="space-y-1.5">
                        {thread.keyInsights.slice(0, 3).map((ins, i) => (
                          <li
                            key={`${ins}-${i}`}
                            className="text-[13.5px] leading-relaxed pl-3 relative"
                            style={{ color: 'var(--text-secondary)' }}
                          >
                            <span
                              className="absolute left-0 top-[9px] w-1 h-1 rounded-full"
                              style={{ background: 'var(--accent-primary)' }}
                            />
                            {ins}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {thread.nextActions.length > 0 && (
                    <div className="flex flex-col gap-1.5">
                      <span className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>
                        Next Actions
                      </span>
                      <ul className="space-y-1.5">
                        {thread.nextActions.slice(0, 3).map((act, i) => (
                          <li
                            key={`${act}-${i}`}
                            className="text-[13.5px] flex items-start gap-2"
                            style={{ color: 'var(--text-primary)' }}
                          >
                            <CheckCircle2
                              className="w-4 h-4 mt-0.5 shrink-0"
                              style={{ color: 'var(--semantic-ondevice)' }}
                            />
                            <span>{act}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {/* Clean Divider List of Linked Memories — zero nested card boxes */}
                  <div
                    className="mt-auto pt-4 border-t flex flex-col divide-y"
                    style={{ borderColor: 'var(--border-subtle)' }}
                  >
                    {linked.map((m) => (
                      <button
                        key={m.id}
                        type="button"
                        onClick={() => onOpenMemory(m)}
                        className="py-2.5 flex items-center justify-between gap-3 text-left group transition-colors"
                        style={{ borderColor: 'var(--border-subtle)' }}
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          {m.mediaThumbnail ? (
                            <img
                              src={m.mediaThumbnail}
                              alt=""
                              className="w-9 h-9 rounded-lg object-cover shrink-0"
                            />
                          ) : (
                            <span
                              className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0 border"
                              style={{
                                background: 'var(--bg-soft)',
                                borderColor: 'var(--border-subtle)',
                                color: 'var(--text-secondary)',
                              }}
                              aria-hidden="true"
                            >
                              {m.kind === 'voice' ? (
                                <Mic className="w-4 h-4" />
                              ) : m.kind === 'link' ? (
                                <Globe className="w-4 h-4" />
                              ) : m.kind === 'receipt' ? (
                                <Receipt className="w-4 h-4" />
                              ) : m.kind === 'note' || m.kind === 'document' ? (
                                <FileText className="w-4 h-4" />
                              ) : (
                                <ImageIcon className="w-4 h-4" />
                              )}
                            </span>
                          )}
                          <div className="min-w-0">
                            <div className="text-[13.5px] font-medium truncate group-hover:underline">
                              {m.title}
                            </div>
                            <div className="font-mono-data text-[11px] capitalize" style={{ color: 'var(--text-muted)' }}>
                              {m.kind} · {m.category} · {new Date(m.createdAt).toLocaleDateString()}
                            </div>
                          </div>
                        </div>
                        <ArrowUpRight
                          className="w-4 h-4 shrink-0 opacity-35 group-hover:opacity-100 transition-opacity"
                          style={{ color: 'var(--accent-primary)' }}
                        />
                      </button>
                    ))}
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
