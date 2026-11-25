import { useEffect, useState } from 'react';
import {
  ArrowUpRight,
  Bell,
  CheckCircle2,
  GitBranch,
  Pin,
  Square,
  Star,
  Trash2,
  Volume2,
  X,
} from 'lucide-react';
import { getMediaBlobUrl } from '../services/localVault';
import { playSpokenRecap } from '../services/hybridAi';
import { InferenceBadge } from './MemoryCard';
import type { MemoryItem, ReminderItem, RemoteConfigValues } from '../types/memora';

interface MemoryDetailDrawerProps {
  memory: MemoryItem | null;
  reminders: ReminderItem[];
  remoteConfig: RemoteConfigValues;
  onClose: () => void;
  onUpdate: (updated: MemoryItem) => void;
  onDelete: (id: string) => void;
  onConfirmReminder: (reminder: ReminderItem) => void;
  onJumpToThread?: (topic: string) => void;
}

export function MemoryDetailDrawer({
  memory,
  reminders,
  remoteConfig,
  onClose,
  onUpdate,
  onDelete,
  onConfirmReminder,
  onJumpToThread,
}: MemoryDetailDrawerProps) {
  const [mediaUrl, setMediaUrl] = useState<string | null>(null);
  const [noteDraft, setNoteDraft] = useState('');
  const [speaking, setSpeaking] = useState(false);
  const [stopSpeech, setStopSpeech] = useState<(() => void) | null>(null);

  useEffect(() => {
    if (!memory) return;
    setNoteDraft(memory.personalNote || '');
    let active = true;
    let createdUrl: string | null = null;

    if (memory.mediaLocalKey) {
      getMediaBlobUrl(memory.mediaLocalKey).then((url) => {
        if (!active) {
          if (url) URL.revokeObjectURL(url);
          return;
        }
        createdUrl = url;
        setMediaUrl(url);
      });
    } else {
      setMediaUrl(null);
    }

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);

    return () => {
      active = false;
      window.removeEventListener('keydown', onKey);
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [memory, onClose]);

  useEffect(() => {
    return () => {
      stopSpeech?.();
    };
  }, [stopSpeech]);

  if (!memory) return null;

  const memoryReminders = reminders.filter((r) => r.memoryId === memory.id);

  async function handleSpeak() {
    if (!memory) return;
    if (speaking) {
      stopSpeech?.();
      setSpeaking(false);
      setStopSpeech(null);
      return;
    }
    setSpeaking(true);
    const script = `${memory.title}. ${memory.summary}${
      memory.actionItems.length > 0
        ? ` Action items: ${memory.actionItems.join('. ')}.`
        : ''
    }`;
    const res = await playSpokenRecap(script, remoteConfig);
    setStopSpeech(() => res.stop);
    window.setTimeout(() => setSpeaking(false), 14_000);
  }

  function handleSaveNote() {
    if (!memory) return;
    onUpdate({
      ...memory,
      personalNote: noteDraft.trim(),
      updatedAt: Date.now(),
    });
  }

  const displayImage =
    (mediaUrl && memory.mediaMimeType?.startsWith('image/') ? mediaUrl : null) ||
    memory.mediaThumbnail;
  const displayAudio =
    mediaUrl && memory.mediaMimeType?.startsWith('audio/') ? mediaUrl : null;

  const allEntities = [
    ...memory.entities.dates,
    ...memory.entities.amounts,
    ...memory.entities.organizations,
    ...memory.entities.people,
    ...memory.entities.locations,
    ...memory.entities.contacts,
  ];

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end"
      style={{ background: 'rgba(17, 19, 24, 0.45)' }}
      onClick={onClose}
    >
      <aside
        className="memora-sheet w-full max-w-xl h-full overflow-y-auto p-6 sm:p-8 flex flex-col gap-6 animate-memora-enter"
        onClick={(e) => e.stopPropagation()}
        aria-label="Memory details"
      >
        {/* Minimal Studio Toolbar */}
        <div className="flex items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5">
            <InferenceBadge
              source={memory.inferenceSource}
              privacyMode={memory.privacyMode}
              syncStatus={memory.syncStatus}
            />
            <span className="font-mono-data text-[11px]" style={{ color: 'var(--text-muted)' }}>
              {memory.inferenceModel || 'hybrid'} · {memory.inferenceLatencyMs}ms
            </span>
          </div>

          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={handleSpeak}
              className="memora-press w-8 h-8 rounded-full flex items-center justify-center"
              style={{
                background: speaking ? 'var(--accent-primary-soft)' : 'var(--bg-soft)',
                color: speaking ? 'var(--accent-primary)' : 'var(--text-secondary)',
              }}
              title={speaking ? 'Stop spoken recap' : 'Listen to spoken recap'}
              aria-label="Listen to spoken recap"
            >
              {speaking ? <Square className="w-3.5 h-3.5 fill-current" /> : <Volume2 className="w-4 h-4" />}
            </button>
            <button
              type="button"
              onClick={() => onUpdate({ ...memory, pinned: !memory.pinned })}
              className="memora-press w-8 h-8 rounded-full flex items-center justify-center"
              style={{
                background: 'var(--bg-soft)',
                color: memory.pinned ? 'var(--accent-primary)' : 'var(--text-secondary)',
              }}
              title={memory.pinned ? 'Unpin' : 'Pin'}
              aria-label="Toggle pin"
            >
              <Pin className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => onUpdate({ ...memory, favorite: !memory.favorite })}
              className="memora-press w-8 h-8 rounded-full flex items-center justify-center"
              style={{
                background: 'var(--bg-soft)',
                color: memory.favorite ? 'var(--accent-spark)' : 'var(--text-secondary)',
              }}
              title={memory.favorite ? 'Remove favorite' : 'Favorite'}
              aria-label="Toggle favorite"
            >
              <Star className={`w-4 h-4 ${memory.favorite ? 'fill-current' : ''}`} />
            </button>
            <button
              type="button"
              onClick={() => {
                onDelete(memory.id);
                onClose();
              }}
              className="memora-press w-8 h-8 rounded-full flex items-center justify-center"
              style={{ background: 'var(--bg-soft)', color: 'var(--semantic-danger)' }}
              title="Delete memory"
              aria-label="Delete memory"
            >
              <Trash2 className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={onClose}
              className="memora-press w-8 h-8 rounded-full flex items-center justify-center"
              style={{ background: 'var(--bg-soft)', color: 'var(--text-secondary)' }}
              aria-label="Close detail"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Display Headline */}
        <div>
          <h2 className="font-display text-3xl font-medium leading-[1.15]">
            {memory.title}
          </h2>
          <p className="font-mono-data text-xs mt-2" style={{ color: 'var(--text-muted)' }}>
            {memory.category} · {new Date(memory.createdAt).toLocaleString()}
          </p>
        </div>

        {/* High-Resolution Artifact Preview */}
        {displayImage && (
          <div
            className="rounded-2xl overflow-hidden border"
            style={{ borderColor: 'var(--border-subtle)', background: 'var(--bg-soft)' }}
          >
            <img
              src={displayImage}
              alt={memory.title}
              className="w-full max-h-[420px] object-contain"
            />
          </div>
        )}

        {displayAudio && (
          <div
            className="rounded-2xl p-3.5 border"
            style={{ background: 'var(--bg-soft)', borderColor: 'var(--border-subtle)' }}
          >
            <audio controls src={displayAudio} className="w-full" />
          </div>
        )}

        {memory.url && (
          <a
            href={memory.url}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center justify-between gap-2 text-sm font-medium px-4 py-3 rounded-2xl border hover:border-[var(--accent-primary)] transition-colors"
            style={{
              background: 'var(--bg-soft)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--accent-primary)',
            }}
          >
            <span className="truncate">{memory.url}</span>
            <ArrowUpRight className="w-4 h-4 shrink-0" />
          </a>
        )}

        <section>
          <p className="text-[15.5px] leading-[1.68]" style={{ color: 'var(--text-primary)' }}>
            {memory.summary}
          </p>
        </section>

        {memoryReminders.length > 0 && (
          <section className="flex flex-col gap-2.5 pt-4 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <h3 className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>
              Visual Intelligence Reminders
            </h3>
            {memoryReminders.map((rem) => (
              <div
                key={rem.id}
                className="rounded-2xl p-4 flex items-center justify-between gap-3 border"
                style={{
                  background: 'var(--accent-spark-soft)',
                  borderColor: 'var(--border-subtle)',
                }}
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-1.5 font-medium text-sm">
                    <Bell className="w-4 h-4 shrink-0" style={{ color: 'var(--accent-spark)' }} />
                    <span className="truncate">{rem.title}</span>
                  </div>
                  <div className="font-mono-data text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
                    {rem.dueDateIso}
                    {rem.dueTime ? ` · ${rem.dueTime}` : ''}
                    {rem.venue ? ` · ${rem.venue}` : ''}
                  </div>
                </div>
                {rem.status === 'suggested' ? (
                  <button
                    type="button"
                    onClick={() => onConfirmReminder(rem)}
                    className="memora-press px-3.5 py-1.5 rounded-full text-xs font-semibold shrink-0"
                    style={{ background: 'var(--accent-primary)', color: 'var(--on-accent)' }}
                  >
                    Confirm
                  </button>
                ) : (
                  <span
                    className="inline-flex items-center gap-1 text-xs font-medium shrink-0"
                    style={{ color: 'var(--semantic-ondevice)' }}
                  >
                    <CheckCircle2 className="w-4 h-4" />
                    {rem.status}
                  </span>
                )}
              </div>
            ))}
          </section>
        )}

        {memory.actionItems.length > 0 && (
          <section className="pt-4 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <h3 className="text-xs font-medium mb-2.5" style={{ color: 'var(--text-muted)' }}>
              Action Items
            </h3>
            <ul className="space-y-2">
              {memory.actionItems.map((item) => (
                <li key={item} className="text-sm flex items-start gap-2.5" style={{ color: 'var(--text-primary)' }}>
                  <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" style={{ color: 'var(--semantic-ondevice)' }} />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {memory.threadTopic && (
          <div
            className="flex items-center justify-between rounded-2xl px-4 py-3 border"
            style={{ borderColor: 'var(--border-subtle)', background: 'var(--bg-soft)' }}
          >
            <div className="flex items-center gap-2 text-sm min-w-0">
              <GitBranch className="w-4 h-4 shrink-0" style={{ color: 'var(--accent-primary)' }} />
              <span className="truncate">
                Thread · <strong>{memory.threadTopic}</strong>
              </span>
            </div>
            {onJumpToThread && (
              <button
                type="button"
                onClick={() => {
                  onJumpToThread(memory.threadTopic || '');
                  onClose();
                }}
                className="text-xs font-semibold shrink-0 hover:underline"
                style={{ color: 'var(--accent-primary)' }}
              >
                Open Thread
              </button>
            )}
          </div>
        )}

        {allEntities.length > 0 && (
          <section className="pt-4 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <h3 className="text-xs font-medium mb-2.5" style={{ color: 'var(--text-muted)' }}>
              Extracted Entities
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {allEntities.map((val, i) => (
                <span
                  key={`${val}-${i}`}
                  className="font-mono-data text-xs px-2.5 py-1 rounded-lg"
                  style={{ background: 'var(--bg-soft)', color: 'var(--text-primary)' }}
                >
                  {val}
                </span>
              ))}
            </div>
          </section>
        )}

        {memory.extractedText && (
          <section className="pt-4 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <h3 className="text-xs font-medium mb-2" style={{ color: 'var(--text-muted)' }}>
              Extracted OCR / Transcript
            </h3>
            <pre
              className="text-xs whitespace-pre-wrap rounded-2xl p-4 border leading-relaxed"
              style={{
                background: 'var(--bg-soft)',
                borderColor: 'var(--border-subtle)',
                color: 'var(--text-secondary)',
                fontFamily: 'inherit',
              }}
            >
              {memory.extractedText}
            </pre>
          </section>
        )}

        <section className="pt-4 border-t flex flex-col gap-2.5" style={{ borderColor: 'var(--border-subtle)' }}>
          <label htmlFor="personal-annotation" className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>
            Personal Margin Note
          </label>
          <textarea
            id="personal-annotation"
            rows={3}
            value={noteDraft}
            onChange={(e) => setNoteDraft(e.target.value)}
            placeholder="Add private thoughts, context, or follow-up notes…"
            className="w-full rounded-2xl px-4 py-3 text-sm border resize-none"
            style={{
              background: 'var(--bg-soft)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
            }}
          />
          <div className="flex justify-end">
            <button
              type="button"
              onClick={handleSaveNote}
              className="memora-press px-4 py-2 rounded-full text-xs font-semibold"
              style={{ background: 'var(--text-primary)', color: 'var(--bg-canvas)' }}
            >
              Save Note
            </button>
          </div>
        </section>
      </aside>
    </div>
  );
}
