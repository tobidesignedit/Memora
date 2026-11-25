import { useEffect, useRef, useState } from 'react';
import {
  Cloud,
  Cpu,
  FileText,
  Image as ImageIcon,
  Link2,
  Lock,
  Mic,
  X,
} from 'lucide-react';
import { LucianDropzone } from './lucian/LucianDropzone';
import { LucianVoiceOrb } from './lucian/LucianVoiceOrb';
import { LucianLiquidButton } from './lucian/LucianLiquidButton';
import type { MemoryKind, PrivacyRoutingMode } from '../types/memora';

export interface CaptureSubmission {
  kindHint: MemoryKind;
  rawText: string;
  url?: string;
  file?: File | Blob;
  fileName?: string;
  mode: PrivacyRoutingMode;
}

interface CaptureModalProps {
  open: boolean;
  defaultMode: PrivacyRoutingMode;
  busy: boolean;
  initialTab?: 'visual' | 'voice' | 'note' | 'link';
  initialFile?: File | null;
  initialUrl?: string;
  initialText?: string;
  onClose: () => void;
  onSubmit: (submission: CaptureSubmission) => Promise<void>;
}

type CaptureTab = 'visual' | 'voice' | 'note' | 'link';

export function CaptureModal({
  open,
  defaultMode,
  busy,
  initialTab,
  initialFile,
  initialUrl,
  initialText,
  onClose,
  onSubmit,
}: CaptureModalProps) {
  const [tab, setTab] = useState<CaptureTab>(initialTab || 'visual');
  const [text, setText] = useState('');
  const [url, setUrl] = useState('');
  const [file, setFile] = useState<File | Blob | null>(null);
  const [fileName, setFileName] = useState<string>('');
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [mode, setMode] = useState<PrivacyRoutingMode>(defaultMode);
  const [recording, setRecording] = useState(false);
  const [recordSeconds, setRecordSeconds] = useState(0);
  const [saveDone, setSaveDone] = useState(false);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const speechRef = useRef<{ stop?: () => void } | null>(null);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    setMode(defaultMode);
  }, [defaultMode]);

  useEffect(() => {
    if (open && initialTab && !initialFile && !initialUrl && !initialText) {
      setTab(initialTab);
    }
  }, [open, initialTab, initialFile, initialUrl, initialText]);

  useEffect(() => {
    if (initialFile && open) {
      attachFile(initialFile);
      setTab(initialFile.type.startsWith('audio/') ? 'voice' : 'visual');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialFile, open]);

  useEffect(() => {
    if (!open) return;
    if (initialUrl) {
      setUrl(initialUrl);
      setTab('link');
    }
    if (initialText) {
      setText(initialText);
      if (!initialUrl && !initialFile) setTab('note');
    }
  }, [initialUrl, initialText, initialFile, open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, busy, onClose]);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      if (timerRef.current) window.clearInterval(timerRef.current);
      speechRef.current?.stop?.();
    };
  }, [previewUrl]);

  if (!open) return null;

  function attachFile(f: File) {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setFile(f);
    setFileName(f.name);
    if (f.type.startsWith('image/')) {
      setPreviewUrl(URL.createObjectURL(f));
    } else {
      setPreviewUrl(null);
    }
  }

  async function startVoiceRecording() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      chunksRef.current = [];
      recorder.ondataavailable = (ev) => {
        if (ev.data.size > 0) chunksRef.current.push(ev.data);
      };
      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType || 'audio/webm' });
        setFile(blob);
        setFileName(`voice-note-${new Date().toISOString().slice(11, 19).replace(/:/g, '-')}.webm`);
        stream.getTracks().forEach((t) => t.stop());
      };
      recorder.start();
      mediaRecorderRef.current = recorder;
      setRecording(true);
      setRecordSeconds(0);
      timerRef.current = window.setInterval(() => setRecordSeconds((s) => s + 1), 1000);

      const win = window as unknown as Record<string, unknown>;
      const SR = (win.SpeechRecognition || win.webkitSpeechRecognition) as
        | (new () => {
            continuous: boolean;
            interimResults: boolean;
            lang: string;
            onresult: (e: { results: ArrayLike<{ 0: { transcript: string } }> }) => void;
            start: () => void;
            stop: () => void;
          })
        | undefined;
      if (SR) {
        const sr = new SR();
        sr.continuous = true;
        sr.interimResults = true;
        sr.lang = 'en-US';
        sr.onresult = (e) => {
          const transcript = Array.from(e.results)
            .map((r) => r[0].transcript)
            .join(' ')
            .trim();
          if (transcript) setText(transcript);
        };
        sr.start();
        speechRef.current = sr;
      }
    } catch {
      alert('Microphone permission is required to record a voice note. You can also type or upload an audio file.');
    }
  }

  function stopVoiceRecording() {
    mediaRecorderRef.current?.stop();
    speechRef.current?.stop?.();
    if (timerRef.current) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    setRecording(false);
  }

  async function handlePasteClipboard() {
    try {
      const clip = await navigator.clipboard.readText();
      if (clip) {
        if (/^https?:\/\//i.test(clip.trim())) {
          setUrl(clip.trim());
          setTab('link');
        } else {
          setText((prev) => (prev ? `${prev}\n${clip}` : clip));
        }
      }
    } catch {
      // Clipboard API requires user permission
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!text.trim() && !url.trim() && !file) return;

    const kindHint: MemoryKind =
      tab === 'voice'
        ? 'voice'
        : tab === 'link'
          ? 'link'
          : file
            ? file.type === 'application/pdf'
              ? 'document'
              : 'screenshot'
            : 'note';

    await onSubmit({
      kindHint,
      rawText: text.trim(),
      url: url.trim() || undefined,
      file: file || undefined,
      fileName: fileName || undefined,
      mode,
    });

    setSaveDone(true);
    window.setTimeout(() => {
      setSaveDone(false);
      setText('');
      setUrl('');
      setFile(null);
      setFileName('');
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      setPreviewUrl(null);
      onClose();
    }, 520);
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-5"
      style={{ background: 'rgba(17, 19, 24, 0.52)' }}
      onClick={onClose}
    >
      <div
        className="memora-sheet w-full sm:max-w-xl rounded-t-3xl sm:rounded-3xl p-6 sm:p-7 max-h-[92vh] overflow-y-auto animate-memora-enter"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Universal Capture"
      >
        <div className="flex items-center justify-between gap-3 mb-5">
          <h2 className="font-display text-2xl sm:text-[28px] font-medium leading-none">
            Capture to Vault
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="memora-press w-8 h-8 rounded-full flex items-center justify-center"
            style={{ background: 'var(--bg-soft)', color: 'var(--text-secondary)' }}
            aria-label="Close capture dialog"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Sculpted Segmented Control */}
        <div
          className="grid grid-cols-4 gap-1 p-1 rounded-full mb-5"
          style={{ background: 'var(--bg-soft)' }}
        >
          {(
            [
              { id: 'visual', label: 'Visual', icon: ImageIcon },
              { id: 'voice', label: 'Voice', icon: Mic },
              { id: 'note', label: 'Note', icon: FileText },
              { id: 'link', label: 'Link', icon: Link2 },
            ] as const
          ).map(({ id, label, icon: Icon }) => {
            const active = tab === id;
            return (
              <button
                key={id}
                type="button"
                onClick={() => setTab(id)}
                className="memora-press flex items-center justify-center gap-1.5 py-2 px-2 rounded-full text-xs font-medium"
                style={{
                  background: active ? 'var(--bg-raised)' : 'transparent',
                  color: active ? 'var(--text-primary)' : 'var(--text-muted)',
                }}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{label}</span>
              </button>
            );
          })}
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          {tab === 'visual' && (
            previewUrl ? (
              <div
                className="rounded-2xl border p-4 flex flex-col items-center gap-3"
                style={{ background: 'var(--bg-soft)', borderColor: 'var(--border-subtle)' }}
              >
                <img
                  src={previewUrl}
                  alt="Capture preview"
                  className="max-h-52 rounded-xl object-contain"
                />
                <div className="flex items-center gap-3 text-xs" style={{ color: 'var(--text-secondary)' }}>
                  <span className="truncate max-w-xs font-mono-data">{fileName}</span>
                  <button
                    type="button"
                    onClick={() => {
                      setFile(null);
                      setFileName('');
                      setPreviewUrl(null);
                    }}
                    className="font-medium hover:underline"
                    style={{ color: 'var(--semantic-danger)' }}
                  >
                    Remove
                  </button>
                </div>
              </div>
            ) : file ? (
              <div
                className="rounded-2xl border px-4 py-3.5 flex items-center justify-between"
                style={{ background: 'var(--bg-soft)', borderColor: 'var(--border-subtle)' }}
              >
                <div className="flex items-center gap-2 text-sm font-medium min-w-0">
                  <FileText className="w-5 h-5 shrink-0" style={{ color: 'var(--accent-primary)' }} />
                  <span className="truncate">{fileName}</span>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setFile(null);
                    setFileName('');
                  }}
                  className="text-xs font-medium shrink-0"
                  style={{ color: 'var(--semantic-danger)' }}
                >
                  Remove
                </button>
              </div>
            ) : (
              /* Lucian UX/UI Prompt #432 animated file dropzone */
              <LucianDropzone onFileSelected={attachFile} />
            )
          )}

          {tab === 'voice' && (
            <div
              className="rounded-2xl p-4 border flex flex-col items-center text-center"
              style={{ background: 'var(--bg-voice)', borderColor: 'var(--border-subtle)' }}
            >
              {/* Lucian UX/UI Prompt #458 AI Voice Orb */}
              <LucianVoiceOrb
                state={recording ? 'listen' : busy ? 'think' : 'idle'}
                label={
                  recording
                    ? `Recording · 00:${String(recordSeconds).padStart(2, '0')} — tap to stop`
                    : file
                      ? `Captured: ${fileName}`
                      : 'Tap orb to record a voice thought'
                }
                onClick={recording ? stopVoiceRecording : () => void startVoiceRecording()}
              />
            </div>
          )}

          {tab === 'link' && (
            <div className="flex flex-col gap-1.5">
              <label htmlFor="capture-url" className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
                Website, Article, or Video URL
              </label>
              <input
                id="capture-url"
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://..."
                className="w-full rounded-xl px-4 py-2.5 text-sm border"
                style={{
                  background: 'var(--bg-soft)',
                  borderColor: 'var(--border-subtle)',
                  color: 'var(--text-primary)',
                }}
              />
            </div>
          )}

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <label htmlFor="capture-text" className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
                {tab === 'voice'
                  ? 'Transcript'
                  : tab === 'visual'
                    ? 'Context (optional — AI reads image automatically)'
                    : 'Note or pasted excerpt'}
              </label>
              <button
                type="button"
                onClick={handlePasteClipboard}
                className="text-xs font-medium hover:underline"
                style={{ color: 'var(--accent-primary)' }}
              >
                Paste Clipboard
              </button>
            </div>
            <textarea
              id="capture-text"
              rows={tab === 'note' ? 5 : 3}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder={
                tab === 'visual'
                  ? 'Add optional context or leave blank…'
                  : 'Write freely — ideas, quotes, deadlines, meeting takeaways…'
              }
              className="w-full rounded-xl px-4 py-3 text-sm border resize-none"
              style={{
                background: 'var(--bg-soft)',
                borderColor: 'var(--border-subtle)',
                color: 'var(--text-primary)',
              }}
            />
          </div>

          <div className="flex flex-col gap-2">
            <span className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
              Privacy &amp; Inference Routing
            </span>
            <div className="grid grid-cols-3 gap-2">
              {(
                [
                  { id: 'prefer_on_device', label: 'Smart Hybrid', sub: 'Local first', icon: Cpu },
                  { id: 'only_on_device', label: 'Local Vault', sub: 'Stays on device', icon: Lock },
                  { id: 'prefer_in_cloud', label: 'Cloud Deep', sub: 'Gemini Pro', icon: Cloud },
                ] as const
              ).map(({ id, label, sub, icon: Icon }) => {
                const selected = mode === id;
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setMode(id)}
                    className="memora-press p-3 rounded-2xl border text-left"
                    style={{
                      background: selected
                        ? id === 'only_on_device'
                          ? 'var(--semantic-ondevice-soft)'
                          : 'var(--accent-primary-soft)'
                        : 'var(--bg-soft)',
                      borderColor: selected
                        ? id === 'only_on_device'
                          ? 'var(--semantic-ondevice)'
                          : 'var(--accent-primary)'
                        : 'var(--border-subtle)',
                    }}
                  >
                    <div className="flex items-center gap-1.5 text-xs font-semibold">
                      <Icon
                        className="w-3.5 h-3.5"
                        style={{
                          color:
                            id === 'only_on_device'
                              ? 'var(--semantic-ondevice)'
                              : selected
                                ? 'var(--accent-primary)'
                                : 'var(--text-muted)',
                        }}
                      />
                      <span>{label}</span>
                    </div>
                    <div className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
                      {sub}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="memora-press px-4 py-2.5 rounded-full text-xs font-medium border"
              style={{ borderColor: 'var(--border-strong)', color: 'var(--text-secondary)' }}
            >
              Cancel
            </button>
            {/* Lucian UX/UI Prompt #001 Liquid glass CTA */}
            <LucianLiquidButton
              type="submit"
              label="Save & Understand"
              doneLabel="Saved to Vault"
              state={busy ? 'load' : saveDone ? 'ok' : 'idle'}
              disabled={!text.trim() && !url.trim() && !file}
            />
          </div>
        </form>
      </div>
    </div>
  );
}
