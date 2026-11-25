import { useState } from 'react';
import {
  AlertTriangle,
  CalendarPlus,
  Check,
  Clock,
  MapPin,
  Plus,
  RotateCcw,
  Trash2,
} from 'lucide-react';
import { LucianReminderPrimeCard } from './lucian/LucianReminderPrimeCard';
import type { MemoryItem, ReminderCategory, ReminderItem } from '../types/memora';

interface RemindersViewProps {
  reminders: ReminderItem[];
  memories: MemoryItem[];
  onConfirm: (reminder: ReminderItem) => Promise<void>;
  onComplete: (reminder: ReminderItem) => Promise<void>;
  onSnooze: (reminder: ReminderItem) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
  onCreateManual: (draft: {
    title: string;
    dueDateIso: string;
    dueTime: string;
    venue: string;
    category: ReminderCategory;
  }) => Promise<void>;
  onOpenMemory: (memory: MemoryItem) => void;
}

function parseDateParts(iso: string): { month: string; day: string; weekday: string } {
  const d = new Date(`${iso}T12:00:00`);
  if (Number.isNaN(d.getTime())) return { month: 'DATE', day: '—', weekday: '' };
  return {
    month: d.toLocaleDateString('en-US', { month: 'short' }).toUpperCase(),
    day: String(d.getDate()).padStart(2, '0'),
    weekday: d.toLocaleDateString('en-US', { weekday: 'short' }),
  };
}

function downloadReminderIcs(rem: ReminderItem) {
  const compactDate = rem.dueDateIso.replace(/-/g, '');
  const timePart = rem.dueTime ? `${rem.dueTime.replace(':', '')}00` : '090000';
  const dtStart = `${compactDate}T${timePart}`;
  const ics = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Memora//Visual Intelligence Reminders//EN',
    'BEGIN:VEVENT',
    `UID:${rem.id}@memora.app`,
    `DTSTART:${dtStart}`,
    `SUMMARY:${rem.title.replace(/[\r\n]/g, ' ')}`,
    rem.venue ? `LOCATION:${rem.venue.replace(/[\r\n]/g, ' ')}` : '',
    rem.notes ? `DESCRIPTION:${rem.notes.replace(/[\r\n]/g, ' ')}` : '',
    'END:VEVENT',
    'END:VCALENDAR',
  ]
    .filter(Boolean)
    .join('\r\n');

  const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${rem.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 40)}.ics`;
  a.click();
  URL.revokeObjectURL(url);
}

export function RemindersView({
  reminders,
  memories,
  onConfirm,
  onComplete,
  onSnooze,
  onDelete,
  onCreateManual,
  onOpenMemory,
}: RemindersViewProps) {
  const [showAdd, setShowAdd] = useState(false);
  const [title, setTitle] = useState('');
  const [dueDateIso, setDueDateIso] = useState(
    new Date(Date.now() + 86_400_000).toISOString().slice(0, 10)
  );
  const [dueTime, setDueTime] = useState('09:00');
  const [venue, setVenue] = useState('');
  const [category, setCategory] = useState<ReminderCategory>('event');
  const [notifPermission, setNotifPermission] = useState<NotificationPermission | 'unsupported'>(
    typeof window !== 'undefined' && 'Notification' in window
      ? Notification.permission
      : 'unsupported'
  );

  const memoryMap = new Map(memories.map((m) => [m.id, m]));
  const suggested = reminders.filter((r) => r.status === 'suggested');
  const upcoming = reminders.filter((r) => r.status === 'confirmed' || r.status === 'snoozed');
  const completed = reminders.filter((r) => r.status === 'completed');

  async function requestNotifications() {
    if (typeof window === 'undefined' || !('Notification' in window)) return;
    const perm = await Notification.requestPermission();
    setNotifPermission(perm);
    if (perm === 'granted') {
      new Notification('Memora Reminders Active', {
        body: 'Visual Intelligence Reminders will alert you right on time.',
      });
    }
  }

  async function handleManualSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    await onCreateManual({
      title: title.trim(),
      dueDateIso,
      dueTime,
      venue: venue.trim(),
      category,
    });
    setTitle('');
    setVenue('');
    setShowAdd(false);
  }

  function renderReminderRow(rem: ReminderItem) {
    const linkedMem = rem.memoryId ? memoryMap.get(rem.memoryId) : undefined;
    const thumb = rem.memoryThumbnail || linkedMem?.mediaThumbnail;
    const parts = parseDateParts(rem.dueDateIso);
    const isOverdue =
      rem.status !== 'completed' && rem.dueDateIso < new Date().toISOString().slice(0, 10);

    return (
      <article
        key={rem.id}
        className="memora-card p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center gap-4 animate-memora-enter"
        style={{
          borderColor: rem.conflictWithId ? 'var(--semantic-warning)' : undefined,
        }}
      >
        {/* Physical Calendar Date Tile */}
        <div className="flex items-center gap-3.5 shrink-0">
          <div
            className="w-14 h-16 rounded-2xl border flex flex-col items-center justify-center shrink-0"
            style={{
              background:
                rem.status === 'suggested' ? 'var(--accent-primary-soft)' : 'var(--bg-soft)',
              borderColor:
                rem.status === 'suggested' ? 'var(--accent-primary)' : 'var(--border-subtle)',
            }}
          >
            <span
              className="font-mono-data text-[10px] font-semibold tracking-wider"
              style={{
                color:
                  rem.status === 'suggested' ? 'var(--accent-primary)' : 'var(--text-muted)',
              }}
            >
              {parts.month}
            </span>
            <span
              className="font-display text-2xl font-medium leading-none mt-0.5 tabular-nums"
              style={{ color: 'var(--text-primary)' }}
            >
              {parts.day}
            </span>
          </div>

          {thumb && (
            <button
              type="button"
              onClick={() => linkedMem && onOpenMemory(linkedMem)}
              className="memora-press w-16 h-16 rounded-2xl overflow-hidden shrink-0 border"
              style={{ borderColor: 'var(--border-subtle)' }}
              title="Open original captured image"
            >
              <img src={thumb} alt={rem.title} className="w-full h-full object-cover" />
            </button>
          )}
        </div>

        {/* Editorial Details */}
        <div className="flex-1 min-w-0 flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3
              className={`text-[16px] font-semibold leading-snug ${
                rem.status === 'completed' ? 'line-through opacity-55' : ''
              }`}
            >
              {rem.title}
            </h3>
            {isOverdue && (
              <span
                className="text-[11px] font-medium px-2 py-0.5 rounded-full"
                style={{ background: 'var(--semantic-danger-soft)', color: 'var(--semantic-danger)' }}
              >
                Overdue
              </span>
            )}
            {rem.conflictWithId && (
              <span
                className="inline-flex items-center gap-1 text-[11px] font-medium px-2 py-0.5 rounded-full"
                style={{ background: 'var(--accent-spark-soft)', color: 'var(--accent-spark)' }}
              >
                <AlertTriangle className="w-3 h-3" />
                Time conflict
              </span>
            )}
          </div>

          <div
            className="flex flex-wrap items-center gap-x-3.5 gap-y-1 text-xs"
            style={{ color: 'var(--text-secondary)' }}
          >
            <span className="capitalize">{rem.category}</span>
            {rem.dueTime && (
              <span className="font-mono-data inline-flex items-center gap-1">
                <Clock className="w-3.5 h-3.5 opacity-60" />
                {rem.dueTime}
              </span>
            )}
            {rem.venue && (
              <span className="inline-flex items-center gap-1">
                <MapPin className="w-3.5 h-3.5 opacity-60" />
                {rem.venue}
              </span>
            )}
          </div>

          {rem.notes && (
            <p className="text-xs mt-0.5 line-clamp-2" style={{ color: 'var(--text-muted)' }}>
              {rem.notes}
            </p>
          )}
        </div>

        {/* Crisp Action Cluster */}
        <div className="flex items-center gap-1.5 shrink-0 self-end sm:self-center">
          {rem.status === 'suggested' && (
            <button
              type="button"
              onClick={() => void onConfirm(rem)}
              className="memora-press px-4 py-2 rounded-full text-xs font-semibold"
              style={{ background: 'var(--accent-primary)', color: 'var(--on-accent)' }}
            >
              Confirm
            </button>
          )}

          {(rem.status === 'confirmed' || rem.status === 'snoozed') && (
            <>
              <button
                type="button"
                onClick={() => void onComplete(rem)}
                className="memora-press inline-flex items-center gap-1 px-3.5 py-2 rounded-full text-xs font-medium"
                style={{
                  background: 'var(--semantic-ondevice-soft)',
                  color: 'var(--semantic-ondevice)',
                }}
              >
                <Check className="w-3.5 h-3.5" />
                Done
              </button>
              <button
                type="button"
                onClick={() => void onSnooze(rem)}
                className="memora-press inline-flex items-center gap-1 px-3 py-2 rounded-full text-xs font-medium border"
                style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-secondary)' }}
                title="Snooze by 1 day"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                +1d
              </button>
            </>
          )}

          <button
            type="button"
            onClick={() => downloadReminderIcs(rem)}
            className="memora-press p-2 rounded-full border"
            style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-secondary)' }}
            title="Export to Calendar (.ics)"
            aria-label="Export to Calendar"
          >
            <CalendarPlus className="w-3.5 h-3.5" />
          </button>

          <button
            type="button"
            onClick={() => void onDelete(rem.id)}
            className="memora-press p-2 rounded-full"
            style={{ color: 'var(--text-muted)' }}
            aria-label="Delete reminder"
            title="Delete"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </article>
    );
  }

  return (
    <div className="max-w-4xl mx-auto flex flex-col gap-8 pb-8">
      <header className="flex flex-wrap items-end justify-between gap-4 pt-2">
        <div>
          <h1 className="font-display text-3xl sm:text-[40px] font-medium leading-[1.12]">
            Visual Reminders
          </h1>
          <p className="text-sm sm:text-[15px] mt-2 max-w-xl" style={{ color: 'var(--text-secondary)' }}>
            Event flyers, exam timetables, payment deadlines, and spoken tasks surface here automatically — linked straight back to the original capture.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setShowAdd((v) => !v)}
            className="memora-press inline-flex items-center gap-1.5 px-4 py-2 rounded-full text-xs font-semibold"
            style={{ background: 'var(--text-primary)', color: 'var(--bg-canvas)' }}
          >
            <Plus className="w-3.5 h-3.5" />
            New Reminder
          </button>
        </div>
      </header>

      <LucianReminderPrimeCard permission={notifPermission} onEnable={requestNotifications} />

      {showAdd && (
        <form
          onSubmit={handleManualSubmit}
          className="memora-card p-6 grid sm:grid-cols-2 gap-3.5 animate-memora-enter"
        >
          <input
            type="text"
            required
            placeholder="Reminder title…"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="sm:col-span-2 rounded-xl px-4 py-2.5 text-sm border"
            style={{
              background: 'var(--bg-soft)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
            }}
          />
          <input
            type="date"
            required
            value={dueDateIso}
            onChange={(e) => setDueDateIso(e.target.value)}
            className="rounded-xl px-4 py-2.5 text-sm border font-mono-data"
            style={{
              background: 'var(--bg-soft)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
            }}
          />
          <input
            type="time"
            value={dueTime}
            onChange={(e) => setDueTime(e.target.value)}
            className="rounded-xl px-4 py-2.5 text-sm border font-mono-data"
            style={{
              background: 'var(--bg-soft)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
            }}
          />
          <input
            type="text"
            placeholder="Venue or location (optional)"
            value={venue}
            onChange={(e) => setVenue(e.target.value)}
            className="rounded-xl px-4 py-2.5 text-sm border"
            style={{
              background: 'var(--bg-soft)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
            }}
          />
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as ReminderCategory)}
            className="rounded-xl px-4 py-2.5 text-sm border"
            style={{
              background: 'var(--bg-soft)',
              borderColor: 'var(--border-subtle)',
              color: 'var(--text-primary)',
            }}
          >
            <option value="event">Event</option>
            <option value="exam">Exam</option>
            <option value="payment">Payment</option>
            <option value="travel">Travel</option>
            <option value="meeting">Meeting</option>
            <option value="deadline">Deadline</option>
            <option value="personal">Personal</option>
          </select>
          <div className="sm:col-span-2 flex justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={() => setShowAdd(false)}
              className="memora-press px-4 py-2 rounded-full text-xs font-medium border"
              style={{ borderColor: 'var(--border-strong)', color: 'var(--text-secondary)' }}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="memora-press px-5 py-2 rounded-full text-xs font-semibold"
              style={{ background: 'var(--accent-primary)', color: 'var(--on-accent)' }}
            >
              Save Reminder
            </button>
          </div>
        </form>
      )}

      {suggested.length > 0 && (
        <section className="flex flex-col gap-3.5">
          <div className="flex items-center justify-between">
            <h2 className="font-display text-xl font-medium">
              Detected from Captures
            </h2>
            <span className="font-mono-data text-xs" style={{ color: 'var(--accent-primary)' }}>
              {suggested.length} awaiting confirmation
            </span>
          </div>
          <div className="flex flex-col gap-3">{suggested.map(renderReminderRow)}</div>
        </section>
      )}

      <section className="flex flex-col gap-3.5">
        <div className="flex items-center justify-between">
          <h2 className="font-display text-xl font-medium">Upcoming Schedule</h2>
          <span className="font-mono-data text-xs" style={{ color: 'var(--text-muted)' }}>
            {upcoming.length} scheduled
          </span>
        </div>
        {upcoming.length === 0 ? (
          <div
            className="rounded-3xl border border-dashed p-12 text-center text-sm"
            style={{
              background: 'var(--bg-surface)',
              borderColor: 'var(--border-strong)',
              color: 'var(--text-muted)',
            }}
          >
            No confirmed reminders yet. Capture a flyer, exam timetable, invoice, or voice note and Memora detects upcoming dates automatically.
          </div>
        ) : (
          <div className="flex flex-col gap-3">{upcoming.map(renderReminderRow)}</div>
        )}
      </section>

      {completed.length > 0 && (
        <section className="flex flex-col gap-3.5">
          <h2 className="font-display text-xl font-medium" style={{ color: 'var(--text-muted)' }}>
            Completed ({completed.length})
          </h2>
          <div className="flex flex-col gap-3">{completed.map(renderReminderRow)}</div>
        </section>
      )}
    </div>
  );
}
