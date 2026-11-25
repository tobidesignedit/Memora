import {
  ArrowUpRight,
  Bell,
  Check,
  Globe,
  Lock,
  Mic,
  Pin,
  Receipt,
  Star,
  WifiOff,
} from 'lucide-react';
import type { MemoryItem, ReminderItem } from '../types/memora';

interface MemoryCardProps {
  memory: MemoryItem;
  linkedReminder?: ReminderItem;
  onOpen: (memory: MemoryItem) => void;
  onConfirmReminder?: (reminder: ReminderItem) => void;
  onTogglePin?: (memory: MemoryItem) => void;
}

/** Deterministic acoustic waveform bars unique to each voice memory */
function getWaveformBars(seed: string): number[] {
  let hash = 2166136261;
  for (let i = 0; i < seed.length; i++) {
    hash ^= seed.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  const bars: number[] = [];
  for (let i = 0; i < 28; i++) {
    hash ^= hash << 13;
    hash ^= hash >>> 17;
    hash ^= hash << 5;
    const norm = (Math.abs(hash) % 100) / 100;
    const envelope = Math.sin((i / 27) * Math.PI);
    const height = Math.round(18 + (norm * 0.62 + envelope * 0.38) * 76);
    bars.push(Math.min(96, Math.max(18, height)));
  }
  return bars;
}

function getHostname(url?: string): string {
  if (!url) return '';
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url.replace(/^https?:\/\//, '').split('/')[0] || url;
  }
}

function formatShortDate(ts: number): string {
  return new Date(ts).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function formatDueChip(iso: string, time?: string): string {
  const d = new Date(`${iso}T12:00:00`);
  const day = Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  return time ? `${day} · ${time}` : day;
}

/**
 * Quiet, editorial provenance signature used in Detail Drawer & cards.
 * Zero heavy badge clutter — a single crisp dot + label.
 */
export function InferenceBadge({
  source,
  privacyMode,
  syncStatus,
}: {
  source: MemoryItem['inferenceSource'];
  privacyMode: MemoryItem['privacyMode'];
  syncStatus: MemoryItem['syncStatus'];
}) {
  if (privacyMode === 'only_on_device') {
    return (
      <span
        className="inline-flex items-center gap-1.5 text-[11px] font-medium"
        style={{ color: 'var(--semantic-ondevice)' }}
      >
        <Lock className="w-3 h-3" />
        <span>Local Vault</span>
      </span>
    );
  }
  if (syncStatus === 'pending') {
    return (
      <span
        className="inline-flex items-center gap-1.5 text-[11px] font-medium"
        style={{ color: 'var(--text-muted)' }}
      >
        <WifiOff className="w-3 h-3" />
        <span>Queued Offline</span>
      </span>
    );
  }
  if (source === 'on_device') {
    return (
      <span
        className="inline-flex items-center gap-1.5 text-[11px] font-medium"
        style={{ color: 'var(--text-secondary)' }}
      >
        <span
          className="w-1.5 h-1.5 rounded-full shrink-0"
          style={{ background: 'var(--semantic-ondevice)' }}
        />
        <span>On-Device</span>
      </span>
    );
  }
  if (source === 'in_cloud') {
    return (
      <span
        className="inline-flex items-center gap-1.5 text-[11px] font-medium"
        style={{ color: 'var(--text-secondary)' }}
      >
        <span
          className="w-1.5 h-1.5 rounded-full shrink-0"
          style={{ background: 'var(--accent-primary)' }}
        />
        <span>Cloud Gemini</span>
      </span>
    );
  }
  return (
    <span
      className="inline-flex items-center gap-1.5 text-[11px] font-medium"
      style={{ color: 'var(--text-muted)' }}
    >
      <span
        className="w-1.5 h-1.5 rounded-full shrink-0"
        style={{ background: 'var(--text-muted)' }}
      />
      <span>Local Index</span>
    </span>
  );
}

function ReminderStrip({
  reminder,
  onConfirm,
}: {
  reminder: ReminderItem;
  onConfirm?: (r: ReminderItem) => void;
}) {
  if (reminder.status === 'completed') return null;
  const isSuggested = reminder.status === 'suggested';
  return (
    <div
      className="mt-3 pt-2.5 border-t flex items-center justify-between gap-2.5"
      style={{ borderColor: 'var(--border-subtle)' }}
      onClick={(e) => e.stopPropagation()}
    >
      <div className="min-w-0 flex items-center gap-2">
        <Bell
          className="w-3.5 h-3.5 shrink-0"
          style={{
            color: isSuggested ? 'var(--accent-spark)' : 'var(--semantic-ondevice)',
          }}
        />
        <span
          className="font-mono-data text-[11.5px] truncate"
          style={{ color: 'var(--text-primary)' }}
        >
          {formatDueChip(reminder.dueDateIso, reminder.dueTime)}
        </span>
      </div>

      {isSuggested && onConfirm ? (
        <button
          type="button"
          onClick={() => onConfirm(reminder)}
          className="memora-press shrink-0 px-2.5 py-1 rounded-full text-[11px] font-semibold"
          style={{
            background: 'var(--accent-primary)',
            color: 'var(--on-accent)',
          }}
        >
          Confirm
        </button>
      ) : (
        <span
          className="shrink-0 inline-flex items-center gap-1 text-[11px] font-medium"
          style={{ color: 'var(--semantic-ondevice)' }}
        >
          <Check className="w-3 h-3" />
          Set
        </span>
      )}
    </div>
  );
}

function CardFooter({ memory }: { memory: MemoryItem }) {
  return (
    <div
      className="mt-3 pt-2.5 border-t flex items-center justify-between gap-2"
      style={{ borderColor: 'var(--border-subtle)' }}
    >
      <InferenceBadge
        source={memory.inferenceSource}
        privacyMode={memory.privacyMode}
        syncStatus={memory.syncStatus}
      />
      <span className="font-mono-data text-[11px]" style={{ color: 'var(--text-muted)' }}>
        {memory.inferenceLatencyMs}ms · {formatShortDate(memory.createdAt)}
      </span>
    </div>
  );
}

export function MemoryCard({
  memory,
  linkedReminder,
  onOpen,
  onConfirmReminder,
  onTogglePin,
}: MemoryCardProps) {
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onOpen(memory);
    }
  };

  const pinAction = onTogglePin ? (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        onTogglePin(memory);
      }}
      className={`memora-press w-7 h-7 rounded-full flex items-center justify-center transition-opacity ${
        memory.pinned ? 'opacity-100' : 'opacity-0 group-hover:opacity-100 focus:opacity-100'
      }`}
      style={{
        background: memory.mediaThumbnail ? '#111318' : 'var(--bg-soft)',
        color: memory.pinned
          ? memory.mediaThumbnail
            ? '#FFFFFF'
            : 'var(--accent-primary)'
          : memory.mediaThumbnail
            ? '#F4F5F7'
            : 'var(--text-muted)',
      }}
      aria-label={memory.pinned ? 'Unpin memory' : 'Pin memory'}
      title={memory.pinned ? 'Unpin' : 'Pin'}
    >
      <Pin className="w-3.5 h-3.5" />
    </button>
  ) : null;

  // 1. VOICE THOUGHT ARTIFACT — Sculpted acoustic card with unique SVG waveform
  if (memory.kind === 'voice' && !memory.mediaThumbnail) {
    const bars = getWaveformBars(memory.id);
    return (
      <article
        className="memora-card u228-card overflow-hidden cursor-pointer group animate-memora-enter p-5"
        style={{ background: 'var(--bg-voice)' }}
        onClick={() => onOpen(memory)}
        role="button"
        tabIndex={0}
        onKeyDown={handleKeyDown}
      >
        <div className="flex items-center justify-between gap-2 mb-3.5">
          <div className="flex items-center gap-2">
            <span
              className="w-7 h-7 rounded-full flex items-center justify-center"
              style={{ background: 'var(--accent-primary)', color: 'var(--on-accent)' }}
            >
              <Mic className="w-3.5 h-3.5" />
            </span>
            <span className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
              Voice Memo
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            {memory.favorite && (
              <Star className="w-3.5 h-3.5 fill-current" style={{ color: 'var(--accent-spark)' }} />
            )}
            {pinAction}
          </div>
        </div>

        {/* Authored SVG Waveform Fingerprint */}
        <div
          className="w-full h-11 flex items-center gap-[3px] px-1 mb-4"
          aria-hidden="true"
        >
          {bars.map((h, i) => (
            <span
              key={i}
              className="flex-1 rounded-full transition-colors duration-300"
              style={{
                height: `${h}%`,
                background:
                  i < 11
                    ? 'var(--accent-primary)'
                    : 'color-mix(in srgb, var(--text-muted) 42%, transparent)',
              }}
            />
          ))}
        </div>

        <h3
          className="font-display text-[20px] font-medium leading-snug mb-2"
          style={{ color: 'var(--text-primary)' }}
        >
          {memory.title}
        </h3>

        <p
          className="font-editorial italic text-[15.5px] leading-relaxed line-clamp-3"
          style={{ color: 'var(--text-secondary)' }}
        >
          &ldquo;{memory.summary}&rdquo;
        </p>

        {linkedReminder && (
          <ReminderStrip reminder={linkedReminder} onConfirm={onConfirmReminder} />
        )}
        <CardFooter memory={memory} />
      </article>
    );
  }

  // 2. WEB MONOGRAPH / BOOKMARK ARTIFACT — Clean magazine clipping
  if (memory.kind === 'link' && !memory.mediaThumbnail) {
    const host = getHostname(memory.url);
    return (
      <article
        className="memora-card u228-card overflow-hidden cursor-pointer group animate-memora-enter p-5"
        onClick={() => onOpen(memory)}
        role="button"
        tabIndex={0}
        onKeyDown={handleKeyDown}
      >
        <div className="flex items-center justify-between gap-2 mb-3">
          <a
            href={memory.url}
            target="_blank"
            rel="noreferrer"
            onClick={(e) => e.stopPropagation()}
            className="inline-flex items-center gap-1.5 text-xs font-medium min-w-0 hover:underline"
            style={{ color: 'var(--accent-primary)' }}
          >
            <Globe className="w-3.5 h-3.5 shrink-0" />
            <span className="truncate">{host || 'Saved Link'}</span>
            <ArrowUpRight className="w-3 h-3 shrink-0 opacity-70" />
          </a>
          {pinAction}
        </div>

        <h3
          className="font-display text-[21px] font-medium leading-[1.24] mb-2.5"
          style={{ color: 'var(--text-primary)' }}
        >
          {memory.title}
        </h3>

        <p
          className="text-[13.5px] leading-relaxed line-clamp-4"
          style={{ color: 'var(--text-secondary)' }}
        >
          {memory.summary}
        </p>

        {memory.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mt-3.5">
            {memory.tags.slice(0, 3).map((tag) => (
              <span
                key={tag}
                className="text-[11px] font-medium px-2 py-0.5 rounded-md"
                style={{ background: 'var(--bg-soft)', color: 'var(--text-muted)' }}
              >
                {tag}
              </span>
            ))}
          </div>
        )}

        {linkedReminder && (
          <ReminderStrip reminder={linkedReminder} onConfirm={onConfirmReminder} />
        )}
        <CardFooter memory={memory} />
      </article>
    );
  }

  // 3. RECEIPT / FINANCIAL LEDGER (without image — thermal paper ledger look)
  if (memory.kind === 'receipt' && !memory.mediaThumbnail) {
    const primaryAmount = memory.entities.amounts[0];
    const org = memory.entities.organizations[0];
    return (
      <article
        className="memora-card u228-card overflow-hidden cursor-pointer group animate-memora-enter p-5"
        style={{ background: 'var(--bg-note)' }}
        onClick={() => onOpen(memory)}
        role="button"
        tabIndex={0}
        onKeyDown={handleKeyDown}
      >
        <div className="flex items-center justify-between gap-2 mb-3">
          <span
            className="inline-flex items-center gap-1.5 text-xs font-medium"
            style={{ color: 'var(--text-muted)' }}
          >
            <Receipt className="w-3.5 h-3.5" />
            <span>{org || 'Receipt'}</span>
          </span>
          {pinAction}
        </div>

        {primaryAmount && (
          <div
            className="font-display text-3xl font-medium tabular-nums mb-1"
            style={{ color: 'var(--text-primary)' }}
          >
            {primaryAmount}
          </div>
        )}

        <h3
          className="text-[15.5px] font-semibold leading-snug mb-2"
          style={{ color: 'var(--text-primary)' }}
        >
          {memory.title}
        </h3>

        <div
          className="my-3 border-t border-dashed"
          style={{ borderColor: 'var(--border-strong)' }}
        />

        <p
          className="text-[13.5px] leading-relaxed line-clamp-3"
          style={{ color: 'var(--text-secondary)' }}
        >
          {memory.summary}
        </p>

        {linkedReminder && (
          <ReminderStrip reminder={linkedReminder} onConfirm={onConfirmReminder} />
        )}
        <CardFooter memory={memory} />
      </article>
    );
  }

  // 4. FINE STATIONERY NOTE ARTIFACT (no media thumbnail)
  if (!memory.mediaThumbnail) {
    return (
      <article
        className="memora-card u228-card overflow-hidden cursor-pointer group animate-memora-enter p-5"
        style={{ background: 'var(--bg-note)' }}
        onClick={() => onOpen(memory)}
        role="button"
        tabIndex={0}
        onKeyDown={handleKeyDown}
      >
        <div className="flex items-start justify-between gap-2 mb-2.5">
          <h3
            className="font-display text-[22px] font-medium leading-[1.22]"
            style={{ color: 'var(--text-primary)' }}
          >
            {memory.title}
          </h3>
          {pinAction}
        </div>

        <p
          className="font-editorial text-[16px] leading-[1.64] line-clamp-5"
          style={{ color: 'var(--text-secondary)' }}
        >
          {memory.summary}
        </p>

        {memory.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mt-3.5">
            {memory.tags.slice(0, 3).map((tag) => (
              <span
                key={tag}
                className="text-[11px] font-medium px-2 py-0.5 rounded-md"
                style={{ background: 'var(--bg-soft)', color: 'var(--text-muted)' }}
              >
                {tag}
              </span>
            ))}
          </div>
        )}

        {linkedReminder && (
          <ReminderStrip reminder={linkedReminder} onConfirm={onConfirmReminder} />
        )}
        <CardFooter memory={memory} />
      </article>
    );
  }

  // 5. VISUAL ARTIFACT CARD (Flyers, Screenshots, Timetables, Receipt Prints, Photos)
  // Full-bleed edge-to-edge imagery at natural aspect ratio — zero forced crop distortion.
  const primaryAmount = memory.entities.amounts[0];
  return (
    <article
      className="memora-card u228-card overflow-hidden cursor-pointer group animate-memora-enter flex flex-col"
      onClick={() => onOpen(memory)}
      role="button"
      tabIndex={0}
      onKeyDown={handleKeyDown}
    >
      <div
        className="relative w-full overflow-hidden"
        style={{ background: 'var(--bg-soft)' }}
      >
        <img
          src={memory.mediaThumbnail}
          alt={memory.title}
          className="w-full h-auto max-h-[380px] object-cover block transition-transform duration-500 ease-out group-hover:scale-[1.025]"
          loading="lazy"
        />

        {/* Quiet solid corner overlay ONLY when meaningful (Local Vault lock / pin) */}
        <div className="absolute top-3 inset-x-3 flex items-center justify-between gap-2 pointer-events-none">
          {memory.privacyMode === 'only_on_device' ? (
            <span
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-medium"
              style={{
                background: '#111318',
                color: '#F4F5F7',
              }}
            >
              <Lock className="w-3 h-3" style={{ color: '#10B981' }} />
              Private
            </span>
          ) : (
            <span />
          )}

          <div className="pointer-events-auto flex items-center gap-1.5">
            {memory.favorite && (
              <span
                className="w-7 h-7 rounded-full flex items-center justify-center"
                style={{
                  background: '#111318',
                  color: '#F59E0B',
                }}
                aria-label="Favorite memory"
              >
                <Star className="w-3.5 h-3.5 fill-current" />
              </span>
            )}
            {pinAction}
          </div>
        </div>
      </div>

      <div className="p-4 flex flex-col">
        <div className="flex items-baseline justify-between gap-2">
          <h3
            className="text-[15.5px] font-semibold leading-snug line-clamp-2"
            style={{ color: 'var(--text-primary)' }}
          >
            {memory.title}
          </h3>
          {primaryAmount && (
            <span
              className="font-mono-data text-xs font-medium shrink-0"
              style={{ color: 'var(--accent-primary)' }}
            >
              {primaryAmount}
            </span>
          )}
        </div>

        <p
          className="text-[13.5px] leading-relaxed line-clamp-2 mt-1.5"
          style={{ color: 'var(--text-secondary)' }}
        >
          {memory.summary}
        </p>

        {linkedReminder && (
          <ReminderStrip reminder={linkedReminder} onConfirm={onConfirmReminder} />
        )}
        <CardFooter memory={memory} />
      </div>
    </article>
  );
}
