import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { onAuthStateChanged, type User } from 'firebase/auth';
import {
  ArrowRight,
  AudioLines,
  Bell,
  FolderOpen,
  GitBranch,
  Layers,
  Loader2,
  Plus,
  RefreshCw,
  Search,
  Upload,
  User as UserIcon,
  X,
} from 'lucide-react';
import { LucianSlidingNavTabs } from './components/lucian/LucianSlidingNavTabs';
import { LucianThemeSwitch } from './components/lucian/LucianThemeSwitch';
import { LucianLiquidButton } from './components/lucian/LucianLiquidButton';
import { LucianGlowMasonry } from './components/lucian/LucianGlowMasonry';
import { LucianToastStack, type ToastMessageItem } from './components/lucian/LucianToastStack';
import {
  isDirectoryPickerSupported,
  loadIndexedFolderKeys,
  persistIndexedFolderKeys,
  pickLocalScreenshotsFolder,
  scanConnectedFolder,
  type DirectoryHandleRef,
  type WebFolderScope,
} from './services/folderAutoIndexer';
import {
  auth,
  DEFAULT_REMOTE_CONFIG,
  isFirebaseConfigured,
  loadRemoteConfigValues,
  signInGuest,
} from './services/firebase';
import {
  getAllBenchmarks,
  getAllLocalMemories,
  getAllLocalReminders,
  getAllLocalThreads,
  saveMediaBlob,
} from './services/localVault';
import {
  createImageThumbnail,
  probeChromeOnDeviceCapability,
  understandCapture,
  type OnDeviceCapability,
} from './services/hybridAi';
import {
  annotateReminderConflicts,
  flushPendingSync,
  reconcileMemoryThreads,
  removeMemory,
  removeReminder,
  saveMemory,
  saveReminder,
  saveThread,
  subscribeToCloudVault,
} from './services/memorySync';
import { buildDemoBundle } from './data/seedSamples';
import { MemoryCard } from './components/MemoryCard';
import { MemoryDetailDrawer } from './components/MemoryDetailDrawer';
import { CaptureModal, type CaptureSubmission } from './components/CaptureModal';
import { HybridTelemetryDrawer } from './components/HybridTelemetryDrawer';
import { SearchAssistantView } from './components/SearchAssistantView';
import { ThreadsView } from './components/ThreadsView';
import { RemindersView } from './components/RemindersView';
import type {
  ActiveTab,
  BenchmarkEntry,
  MemoryItem,
  MemoryThread,
  PrivacyRoutingMode,
  ReminderCategory,
  ReminderItem,
  RemoteConfigValues,
} from './types/memora';

const FILTER_CHIPS = [
  { id: 'all', label: 'Everything' },
  { id: 'visual', label: 'Visuals' },
  { id: 'voice', label: 'Voice' },
  { id: 'receipt', label: 'Receipts' },
  { id: 'link', label: 'Links' },
  { id: 'note', label: 'Notes' },
  { id: 'local_only', label: 'Private Vault' },
] as const;

function MemoraMark() {
  return (
    <svg viewBox="0 0 32 32" className="w-7 h-7 shrink-0" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="#111318" />
      <circle cx="11.5" cy="12.5" r="3.6" fill="#6366F1" />
      <circle cx="21" cy="11.5" r="2.3" fill="#10B981" />
      <circle cx="17.5" cy="21.5" r="2.7" fill="#FFFFFF" />
      <path
        d="M11.5 12.5L21 11.5M11.5 12.5L17.5 21.5M21 11.5L17.5 21.5"
        stroke="#FFFFFF"
        strokeOpacity="0.45"
        strokeWidth="1.25"
        strokeLinecap="round"
      />
    </svg>
  );
}

export function App() {
  const [tab, setTab] = useState<ActiveTab>('vault');
  const [dark, setDark] = useState(() => localStorage.getItem('memora-theme') === 'dark');
  const [online, setOnline] = useState(() => navigator.onLine);
  const [user, setUser] = useState<User | null>(null);
  const [remoteConfig, setRemoteConfig] = useState<RemoteConfigValues>(DEFAULT_REMOTE_CONFIG);
  const [defaultMode, setDefaultMode] = useState<PrivacyRoutingMode>('prefer_on_device');
  const [capability, setCapability] = useState<OnDeviceCapability>({
    supported: false,
    status: 'unavailable',
    detail: 'Checking Chrome Built-in Gemini Nano capability…',
  });

  const [memories, setMemories] = useState<MemoryItem[]>([]);
  const [reminders, setReminders] = useState<ReminderItem[]>([]);
  const [threads, setThreads] = useState<MemoryThread[]>([]);
  const [benchmarks, setBenchmarks] = useState<BenchmarkEntry[]>([]);

  const [filterChip, setFilterChip] = useState<(typeof FILTER_CHIPS)[number]['id']>('all');
  const [quickFilter, setQuickFilter] = useState('');
  const [selectedMemory, setSelectedMemory] = useState<MemoryItem | null>(null);
  const [captureOpen, setCaptureOpen] = useState(false);
  const [captureTabHint, setCaptureTabHint] = useState<'visual' | 'voice' | 'note' | 'link'>('visual');
  const [captureBusy, setCaptureBusy] = useState(false);
  const [droppedFile, setDroppedFile] = useState<File | null>(null);
  const [prefillUrl, setPrefillUrl] = useState<string>('');
  const [prefillText, setPrefillText] = useState<string>('');
  const [globalDrag, setGlobalDrag] = useState(false);
  const [telemetryOpen, setTelemetryOpen] = useState(false);
  const [highlightThread, setHighlightThread] = useState<string | undefined>(undefined);
  const [toasts, setToasts] = useState<ToastMessageItem[]>([]);

  const mindBarInputRef = useRef<HTMLInputElement | null>(null);

  // Permission-gated Local Screenshots Folder Auto-Indexing (File System Access API)
  const [folderHandle, setFolderHandle] = useState<DirectoryHandleRef | null>(null);
  const [folderScope, setFolderScope] = useState<WebFolderScope>('all_in_folder');
  const [folderScanning, setFolderScanning] = useState(false);
  const [folderIndexedCount, setFolderIndexedCount] = useState(() => loadIndexedFolderKeys().size);
  const indexedFolderKeysRef = useRef<Set<string>>(loadIndexedFolderKeys());
  const folderBusyRef = useRef(false);
  const folderApiSupported = useMemo(() => isDirectoryPickerSupported(), []);

  const showToast = useCallback((msg: string) => {
    const id = `toast-${crypto.randomUUID()}`;
    setToasts((prev) => [...prev.slice(-4), { id, text: msg }]);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  // Theme sync + data-theme attribute (for Lucian UX/UI light-dark() tokens) + browser theme-color meta
  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    localStorage.setItem('memora-theme', dark ? 'dark' : 'light');
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', dark ? '#0B0C0F' : '#FAFAFA');
  }, [dark]);

  const focusMindBar = useCallback(() => {
    if (tab !== 'vault') setTab('vault');
    window.setTimeout(() => mindBarInputRef.current?.focus(), 40);
  }, [tab]);

  // Silent Cmd/Ctrl+K keyboard shortcut to focus Mind Bar
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k' && !e.altKey && !e.shiftKey) {
        e.preventDefault();
        focusMindBar();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [focusMindBar]);

  // Connectivity listener + offline sync flush
  useEffect(() => {
    const onOnline = async () => {
      setOnline(true);
      const count = await flushPendingSync(user?.uid || null);
      if (count > 0) {
        showToast(`Back online — synced ${count} queued ${count === 1 ? 'memory' : 'memories'}.`);
      }
    };
    const onOffline = () => {
      setOnline(false);
      showToast('Offline mode active — Memora continues capturing and searching locally.');
    };
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, [user, showToast]);

  const refreshLocalState = useCallback(async () => {
    const [mems, rems, thrs, bench] = await Promise.all([
      getAllLocalMemories(),
      getAllLocalReminders(),
      getAllLocalThreads(),
      getAllBenchmarks(),
    ]);
    setMemories(mems);
    setReminders(annotateReminderConflicts(rems));
    setThreads(thrs);
    setBenchmarks(bench);
  }, []);

  const refreshCapability = useCallback(async () => {
    const cap = await probeChromeOnDeviceCapability();
    setCapability(cap);
  }, []);

  // Initial load: IndexedDB local vault + Chrome Nano probe + Remote Config + Chrome Extension handoff
  useEffect(() => {
    void refreshLocalState();
    void refreshCapability();
    loadRemoteConfigValues().then((rc) => {
      setRemoteConfig(rc);
      setDefaultMode(rc.defaultInferenceMode);
    });
    const params = new URLSearchParams(window.location.search);
    if (params.get('capture') === '1') {
      setPrefillUrl(params.get('url') || '');
      setPrefillText(params.get('text') || '');
      setCaptureOpen(true);
      window.history.replaceState({}, '', window.location.pathname);
    }
  }, [refreshLocalState, refreshCapability]);

  // Firebase Auth + real-time Firestore cross-device sync
  useEffect(() => {
    if (!auth || !isFirebaseConfigured) return;
    const unsubAuth = onAuthStateChanged(auth, async (u) => {
      setUser(u);
      if (!u) {
        try {
          await signInGuest();
        } catch {
          // User can sign in manually from Hybrid & Privacy drawer
        }
      }
    });
    return () => unsubAuth();
  }, []);

  useEffect(() => {
    if (!user?.uid || !isFirebaseConfigured) return;
    void flushPendingSync(user.uid);
    const unsub = subscribeToCloudVault(user.uid, {
      onMemories: (items) => setMemories(items),
      onReminders: (items) => setReminders(annotateReminderConflicts(items)),
      onThreads: (items) => setThreads(items),
    });
    return () => unsub();
  }, [user?.uid]);

  // Global drag-and-drop capture anywhere on viewport
  useEffect(() => {
    const handleDragOver = (e: DragEvent) => {
      if (e.dataTransfer?.types.includes('Files')) {
        e.preventDefault();
        setGlobalDrag(true);
      }
    };
    const handleDragLeave = (e: DragEvent) => {
      if (e.relatedTarget === null) setGlobalDrag(false);
    };
    const handleDrop = (e: DragEvent) => {
      e.preventDefault();
      setGlobalDrag(false);
      const file = e.dataTransfer?.files?.[0];
      if (file) {
        setDroppedFile(file);
        setCaptureOpen(true);
      }
    };
    window.addEventListener('dragover', handleDragOver);
    window.addEventListener('dragleave', handleDragLeave);
    window.addEventListener('drop', handleDrop);
    return () => {
      window.removeEventListener('dragover', handleDragOver);
      window.removeEventListener('dragleave', handleDragLeave);
      window.removeEventListener('drop', handleDrop);
    };
  }, []);

  async function handleCaptureSubmit(sub: CaptureSubmission) {
    setCaptureBusy(true);
    try {
      const uid = user?.uid || 'local';
      const id = `mem-${crypto.randomUUID()}`;
      let mediaLocalKey: string | undefined;
      let mediaThumbnail: string | undefined;

      if (sub.file) {
        mediaLocalKey = `blob-${id}`;
        await saveMediaBlob(mediaLocalKey, sub.file, sub.file.type || 'application/octet-stream', sub.fileName);
        mediaThumbnail = await createImageThumbnail(sub.file);
      }

      const aiResult = await understandCapture({
        rawText: sub.rawText,
        url: sub.url,
        file: sub.file,
        fileName: sub.fileName,
        kindHint: sub.kindHint,
        mode: sub.mode,
        remoteConfig,
      });

      const memory: MemoryItem = {
        id,
        uid,
        kind: aiResult.kind,
        title: aiResult.title,
        summary: aiResult.summary,
        rawText: sub.rawText,
        extractedText: aiResult.extractedText,
        url: sub.url,
        mediaMimeType: sub.file?.type,
        mediaFileName: sub.fileName,
        mediaLocalKey,
        mediaThumbnail,
        tags: aiResult.tags,
        category: aiResult.category,
        entities: aiResult.entities,
        actionItems: aiResult.actionItems,
        threadTopic: aiResult.threadTopic,
        importance: aiResult.importance,
        language: aiResult.language,
        inferenceSource: aiResult.inferenceSource,
        inferenceModel: aiResult.modelUsed,
        inferenceLatencyMs: aiResult.latencyMs,
        privacyMode: sub.mode,
        syncStatus: sub.mode === 'only_on_device' ? 'local_only' : online ? 'synced' : 'pending',
        pinned: false,
        favorite: false,
        archived: false,
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const saved = await saveMemory(memory, user?.uid || null);
      const nextMemories = [saved, ...memories.filter((m) => m.id !== saved.id)];
      setMemories(nextMemories);

      let createdReminders = 0;
      const addedRems: ReminderItem[] = [];
      for (const cand of aiResult.reminders) {
        const rem: ReminderItem = {
          id: `rem-${crypto.randomUUID()}`,
          uid,
          memoryId: saved.id,
          memoryTitle: saved.title,
          memoryThumbnail: saved.mediaThumbnail,
          title: cand.title,
          dueDateIso: cand.dueDateIso,
          dueTime: cand.dueTime,
          venue: cand.venue,
          organizer: cand.organizer,
          category: cand.category,
          priority: cand.priority,
          status: 'suggested',
          recurrence: 'none',
          notes: cand.notes,
          createdAt: Date.now(),
          updatedAt: Date.now(),
        };
        const savedRem = await saveReminder(rem, user?.uid || null);
        addedRems.push(savedRem);
        createdReminders += 1;
      }

      if (addedRems.length > 0) {
        setReminders((prev) => annotateReminderConflicts([...addedRems, ...prev]));
      }

      const updatedThreads = await reconcileMemoryThreads(nextMemories, threads, user?.uid || null);
      setThreads(updatedThreads);
      setBenchmarks(await getAllBenchmarks());

      const sourceLabel =
        aiResult.inferenceSource === 'on_device'
          ? 'On-Device Gemini Nano'
          : aiResult.inferenceSource === 'in_cloud'
            ? 'Cloud Gemini'
            : 'Local Offline Engine';

      showToast(
        createdReminders > 0
          ? `Saved via ${sourceLabel} (${aiResult.latencyMs}ms) · ${createdReminders} reminder detected.`
          : `Saved via ${sourceLabel} (${aiResult.latencyMs}ms).`
      );
    } finally {
      setCaptureBusy(false);
      setDroppedFile(null);
    }
  }

  const scanLocalFolder = useCallback(
    async (handle: DirectoryHandleRef, scopeOverride?: WebFolderScope, silent = false) => {
      if (folderBusyRef.current) return;
      folderBusyRef.current = true;
      setFolderScanning(true);
      try {
        const candidates = await scanConnectedFolder({
          dirHandle: handle,
          scope: scopeOverride || folderScope,
          alreadyIndexedKeys: indexedFolderKeysRef.current,
          maxBatch: 4,
        });
        if (candidates.length === 0) {
          if (!silent) {
            showToast(`No new unindexed images in "${handle.name}".`);
          }
          return;
        }

        const uid = user?.uid || 'local';
        let added = 0;
        let reminderHits = 0;
        let runningMems = [...memories];
        const addedRems: ReminderItem[] = [];

        for (const cand of candidates) {
          const id = `mem-${crypto.randomUUID()}`;
          const mediaLocalKey = `blob-${id}`;
          await saveMediaBlob(mediaLocalKey, cand.file, cand.file.type || 'image/png', cand.relativeName);
          const mediaThumbnail = await createImageThumbnail(cand.file);

          const aiResult = await understandCapture({
            rawText: '',
            file: cand.file,
            fileName: cand.relativeName,
            kindHint: cand.isScreenshot ? 'screenshot' : 'photo',
            mode: defaultMode,
            remoteConfig,
          });

          const memory: MemoryItem = {
            id,
            uid,
            kind: aiResult.kind,
            title: aiResult.title,
            summary: aiResult.summary,
            rawText: '',
            extractedText: aiResult.extractedText,
            sourceApp: `Local Folder · ${handle.name}`,
            mediaMimeType: cand.file.type || 'image/png',
            mediaFileName: cand.relativeName,
            mediaLocalKey,
            mediaThumbnail,
            tags: aiResult.tags,
            category: aiResult.category,
            entities: aiResult.entities,
            actionItems: aiResult.actionItems,
            threadTopic: aiResult.threadTopic,
            importance: aiResult.importance,
            language: aiResult.language,
            inferenceSource: aiResult.inferenceSource,
            inferenceModel: aiResult.modelUsed,
            inferenceLatencyMs: aiResult.latencyMs,
            privacyMode: defaultMode,
            syncStatus: defaultMode === 'only_on_device' ? 'local_only' : online ? 'synced' : 'pending',
            pinned: false,
            favorite: false,
            archived: false,
            createdAt: cand.file.lastModified || Date.now(),
            updatedAt: Date.now(),
          };

          const saved = await saveMemory(memory, user?.uid || null);
          runningMems = [saved, ...runningMems.filter((m) => m.id !== saved.id)];
          indexedFolderKeysRef.current.add(cand.dedupeKey);
          persistIndexedFolderKeys(indexedFolderKeysRef.current);
          setFolderIndexedCount(indexedFolderKeysRef.current.size);
          added += 1;

          for (const rcand of aiResult.reminders) {
            const rem: ReminderItem = {
              id: `rem-${crypto.randomUUID()}`,
              uid,
              memoryId: saved.id,
              memoryTitle: saved.title,
              memoryThumbnail: saved.mediaThumbnail,
              title: rcand.title,
              dueDateIso: rcand.dueDateIso,
              dueTime: rcand.dueTime,
              venue: rcand.venue,
              organizer: rcand.organizer,
              category: rcand.category,
              priority: rcand.priority,
              status: 'suggested',
              recurrence: 'none',
              notes: rcand.notes,
              createdAt: Date.now(),
              updatedAt: Date.now(),
            };
            const savedRem = await saveReminder(rem, user?.uid || null);
            addedRems.push(savedRem);
            reminderHits += 1;
          }
        }

        setMemories(runningMems);
        if (addedRems.length > 0) {
          setReminders((prev) => annotateReminderConflicts([...addedRems, ...prev]));
        }
        const updatedThreads = await reconcileMemoryThreads(runningMems, threads, user?.uid || null);
        setThreads(updatedThreads);
        setBenchmarks(await getAllBenchmarks());

        showToast(
          reminderHits > 0
            ? `Auto-indexed ${added} new ${added === 1 ? 'screenshot' : 'screenshots'} from "${handle.name}" · ${reminderHits} reminder detected.`
            : `Auto-indexed ${added} new ${added === 1 ? 'screenshot' : 'screenshots'} from "${handle.name}".`
        );
      } catch (err) {
        if (!silent) {
          showToast(err instanceof Error ? err.message : 'Unable to scan local folder.');
        }
      } finally {
        folderBusyRef.current = false;
        setFolderScanning(false);
      }
    },
    [defaultMode, folderScope, memories, online, remoteConfig, showToast, threads, user]
  );

  async function handleConnectScreenshotsFolder() {
    if (!folderApiSupported) {
      showToast('File System Access API requires Chrome or Edge desktop — use Drag & Drop instead.');
      return;
    }
    try {
      const handle = await pickLocalScreenshotsFolder();
      if (!handle) return;
      setFolderHandle(handle);
      showToast(`Connected "${handle.name}" — scanning privately…`);
      await scanLocalFolder(handle, folderScope, false);
    } catch {
      showToast('Folder permission was not granted.');
    }
  }

  useEffect(() => {
    if (!folderHandle) return;
    const onFocus = () => void scanLocalFolder(folderHandle, folderScope, true);
    window.addEventListener('focus', onFocus);
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        void scanLocalFolder(folderHandle, folderScope, true);
      }
    }, 12_000);
    return () => {
      window.removeEventListener('focus', onFocus);
      window.clearInterval(interval);
    };
  }, [folderHandle, folderScope, scanLocalFolder]);

  async function handleLoadDemoData() {
    const uid = user?.uid || 'local';
    const bundle = buildDemoBundle(uid);
    for (const m of bundle.memories) {
      await saveMemory(m, user?.uid || null);
    }
    for (const r of bundle.reminders) {
      await saveReminder(r, user?.uid || null);
    }
    const allMems = await getAllLocalMemories();
    const allRems = await getAllLocalReminders();
    const updatedThreads = await reconcileMemoryThreads(allMems, threads, user?.uid || null);
    setMemories(allMems);
    setReminders(annotateReminderConflicts(allRems));
    setThreads(updatedThreads);
    showToast('Loaded sample gallery, Visual Intelligence Reminders & Memory Threads.');
  }

  async function handleConfirmReminder(rem: ReminderItem) {
    const updated = await saveReminder({ ...rem, status: 'confirmed' }, user?.uid || null);
    setReminders((prev) =>
      annotateReminderConflicts(prev.map((r) => (r.id === updated.id ? updated : r)))
    );
    showToast(`Reminder confirmed for ${updated.dueDateIso}.`);
  }

  async function handleCompleteReminder(rem: ReminderItem) {
    const updated = await saveReminder({ ...rem, status: 'completed' }, user?.uid || null);
    setReminders((prev) =>
      annotateReminderConflicts(prev.map((r) => (r.id === updated.id ? updated : r)))
    );
  }

  async function handleSnoozeReminder(rem: ReminderItem) {
    const nextDate = new Date(new Date(rem.dueDateIso).getTime() + 86_400_000)
      .toISOString()
      .slice(0, 10);
    const updated = await saveReminder(
      { ...rem, dueDateIso: nextDate, status: 'snoozed' },
      user?.uid || null
    );
    setReminders((prev) =>
      annotateReminderConflicts(prev.map((r) => (r.id === updated.id ? updated : r)))
    );
    showToast(`Snoozed to ${nextDate}.`);
  }

  async function handleCreateManualReminder(draft: {
    title: string;
    dueDateIso: string;
    dueTime: string;
    venue: string;
    category: ReminderCategory;
  }) {
    const rem: ReminderItem = {
      id: `rem-${crypto.randomUUID()}`,
      uid: user?.uid || 'local',
      title: draft.title,
      dueDateIso: draft.dueDateIso,
      dueTime: draft.dueTime || undefined,
      venue: draft.venue || undefined,
      category: draft.category,
      priority: 'medium',
      status: 'confirmed',
      recurrence: 'none',
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
    const saved = await saveReminder(rem, user?.uid || null);
    setReminders((prev) => annotateReminderConflicts([saved, ...prev]));
    showToast('Reminder scheduled.');
  }

  async function handleUpdateMemory(updated: MemoryItem) {
    const saved = await saveMemory(updated, user?.uid || null);
    setMemories((prev) => prev.map((m) => (m.id === saved.id ? saved : m)));
    setSelectedMemory(saved);
  }

  async function handleDeleteMemory(id: string) {
    await removeMemory(id, user?.uid || null);
    const next = memories.filter((m) => m.id !== id);
    setMemories(next);
    const updatedThreads = await reconcileMemoryThreads(next, threads, user?.uid || null);
    setThreads(updatedThreads);
    showToast('Memory removed.');
  }

  const filteredMemories = useMemo(() => {
    const q = quickFilter.trim().toLowerCase();
    return memories
      .filter((m) => {
        if (m.archived) return false;
        if (filterChip === 'visual' && !['screenshot', 'photo', 'flyer', 'timetable', 'ticket'].includes(m.kind)) {
          return false;
        }
        if (filterChip === 'voice' && m.kind !== 'voice') return false;
        if (filterChip === 'receipt' && m.kind !== 'receipt') return false;
        if (filterChip === 'link' && m.kind !== 'link') return false;
        if (filterChip === 'note' && m.kind !== 'note' && m.kind !== 'document') return false;
        if (filterChip === 'local_only' && m.privacyMode !== 'only_on_device') return false;
        if (!q) return true;
        return (
          m.title.toLowerCase().includes(q) ||
          m.summary.toLowerCase().includes(q) ||
          m.extractedText.toLowerCase().includes(q) ||
          m.tags.some((t) => t.toLowerCase().includes(q)) ||
          m.category.toLowerCase().includes(q)
        );
      })
      .sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.createdAt - a.createdAt);
  }, [memories, filterChip, quickFilter]);

  const reminderByMemoryId = useMemo(() => {
    const map = new Map<string, ReminderItem>();
    for (const r of reminders) {
      if (r.memoryId && r.status !== 'completed' && !map.has(r.memoryId)) {
        map.set(r.memoryId, r);
      }
    }
    return map;
  }, [reminders]);

  const suggestedRemindersCount = reminders.filter((r) => r.status === 'suggested').length;
  const pendingRemindersCount = reminders.filter((r) => r.status === 'suggested' || r.status === 'confirmed').length;
  const pendingSyncCount = memories.filter((m) => m.syncStatus === 'pending').length;
  const localOnlyCount = memories.filter((m) => m.privacyMode === 'only_on_device').length;

  const greeting = useMemo(() => {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 18) return 'Good afternoon';
    return 'Good evening';
  }, []);

  const openCapture = (hint: 'visual' | 'voice' | 'note' | 'link' = 'visual') => {
    setDroppedFile(null);
    setCaptureTabHint(hint);
    setCaptureOpen(true);
  };

  return (
    <div className="min-h-screen flex flex-col pb-24 md:pb-14">
      {/* Crisp Solid Sticky Studio Header (Genesis × Block Canvas) */}
      <header
        className="memora-glass sticky top-0 z-40 border-b"
        style={{ borderColor: 'var(--border-subtle)' }}
      >
        <div className="max-w-[1440px] mx-auto px-4 sm:px-8 h-[60px] flex items-center justify-between gap-4">
          {/* Left: Minimal Wordmark */}
          <button
            type="button"
            onClick={() => setTab('vault')}
            className="flex items-center gap-2.5 text-left"
            style={{ color: 'var(--text-primary)' }}
          >
            <MemoraMark />
            <span className="font-display text-[20px] font-bold tracking-tight">Memora</span>
          </button>

          {/* Center/Right: Lucian UX/UI Prompt #039 Spring-Sliding Pill Navigation Tabs */}
          <div className="hidden md:block">
            <LucianSlidingNavTabs
              activeId={tab}
              onSelect={setTab}
              items={[
                { id: 'vault', label: 'Vault', icon: <Layers className="w-3.5 h-3.5" /> },
                { id: 'search', label: 'Ask', icon: <Search className="w-3.5 h-3.5" /> },
                { id: 'threads', label: 'Threads', icon: <GitBranch className="w-3.5 h-3.5" />, count: threads.length },
                { id: 'reminders', label: 'Reminders', icon: <Bell className="w-3.5 h-3.5" />, count: pendingRemindersCount },
              ]}
            />
          </div>

          {/* Right: Account/Sync Icon + Lucian #447 Day-Night Sky Toggle */}
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={() => setTelemetryOpen(true)}
              className="memora-press w-8 h-8 rounded-full flex items-center justify-center border"
              style={{
                background: 'var(--bg-surface)',
                borderColor: 'var(--border-subtle)',
                color: user && !user.isAnonymous ? 'var(--semantic-ondevice)' : 'var(--text-secondary)',
              }}
              aria-label="Account and Hybrid AI Sync"
              title={user && !user.isAnonymous ? `Synced as ${user.email || 'Google Account'}` : 'Sign in & Hybrid AI Sync'}
            >
              <UserIcon className="w-4 h-4" />
            </button>
            <LucianThemeSwitch dark={dark} onToggle={() => setDark((d) => !d)} />
          </div>
        </div>
      </header>

      {/* Main Gallery / Studio Canvas */}
      <main className="flex-1 w-full max-w-[1440px] mx-auto px-4 sm:px-8 pt-6 sm:pt-8">
        {tab === 'vault' && (
          <div className="flex flex-col gap-7">
            {/* Compact Editorial Greeting + Unified Mind Bar (Genesis × Block Canvas) */}
            <section className="max-w-[740px] w-full mx-auto flex flex-col items-center text-center gap-3.5">
              <h1 className="font-display text-sm sm:text-base font-semibold leading-snug tracking-tight">
                {greeting}.{' '}
                <span className="font-normal" style={{ color: 'var(--text-secondary)' }}>
                  Everything worth keeping.
                </span>
              </h1>

              {/* Unified Mind Bar Omnibox */}
              <div className="memora-omnibox w-full rounded-full p-1.5 pl-5 flex items-center gap-2.5">
                <Search className="w-4 h-4 shrink-0" style={{ color: 'var(--text-muted)' }} />
                <input
                  ref={mindBarInputRef}
                  type="search"
                  value={quickFilter}
                  onChange={(e) => setQuickFilter(e.target.value)}
                  placeholder="Search screenshots, receipts, voice notes — or press Enter to ask…"
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && quickFilter.trim()) {
                      setTab('search');
                    }
                  }}
                  className="flex-1 bg-transparent border-0 text-[15px] py-2 focus:outline-none min-w-0"
                  style={{ color: 'var(--text-primary)' }}
                />

                {quickFilter.trim() ? (
                  <button
                    type="button"
                    onClick={() => setTab('search')}
                    className="memora-press inline-flex items-center gap-1 px-4 py-2 rounded-full text-xs font-semibold shrink-0"
                    style={{ background: 'var(--accent-primary)', color: 'var(--on-accent)' }}
                  >
                    <span>Ask Memora</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => openCapture('voice')}
                    className="memora-press w-11 h-11 rounded-full flex items-center justify-center shrink-0"
                    style={{ background: 'var(--accent-primary)', color: 'var(--on-accent)' }}
                    title="Record a voice thought"
                    aria-label="Record voice thought"
                  >
                    <AudioLines className="w-5 h-5" strokeWidth={2.2} />
                  </button>
                )}
              </div>

              {/* Whisper-Quiet Filter Strip + Local Auto-Index Control */}
              <div className="w-full flex flex-wrap items-center justify-center gap-2 pt-1">
                <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar max-w-full py-0.5">
                  {FILTER_CHIPS.map((chip) => {
                    const active = filterChip === chip.id;
                    return (
                      <button
                        key={chip.id}
                        type="button"
                        onClick={() => setFilterChip(chip.id)}
                        className="memora-press shrink-0 px-3.5 py-1.5 rounded-full text-xs font-medium border"
                        style={{
                          background: active ? 'var(--text-primary)' : 'transparent',
                          borderColor: active ? 'var(--text-primary)' : 'var(--border-subtle)',
                          color: active ? 'var(--bg-canvas)' : 'var(--text-secondary)',
                        }}
                      >
                        {chip.label}
                      </button>
                    );
                  })}
                </div>

                <span
                  className="hidden sm:inline-block h-4 w-[1px] mx-0.5"
                  style={{ background: 'var(--border-strong)' }}
                  aria-hidden="true"
                />

                {folderHandle ? (
                  <div
                    className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium border"
                    style={{
                      background: 'var(--semantic-ondevice-soft)',
                      borderColor: 'var(--border-subtle)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    <span className="w-1.5 h-1.5 rounded-full" style={{ background: 'var(--semantic-ondevice)' }} />
                    <span>{folderHandle.name}</span>
                    <button
                      type="button"
                      disabled={folderScanning}
                      onClick={() => void scanLocalFolder(folderHandle, folderScope, false)}
                      className="memora-press inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px]"
                      style={{ background: 'var(--bg-surface)', color: 'var(--text-primary)' }}
                    >
                      {folderScanning ? (
                        <Loader2 className="w-3 h-3 animate-spin" />
                      ) : (
                        <RefreshCw className="w-3 h-3" />
                      )}
                      Scan
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setFolderHandle(null);
                        showToast('Disconnected local screenshots folder.');
                      }}
                      aria-label="Disconnect folder"
                      style={{ color: 'var(--text-muted)' }}
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => void handleConnectScreenshotsFolder()}
                    className="memora-press inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-medium border"
                    style={{
                      background: 'var(--bg-surface)',
                      borderColor: 'var(--border-subtle)',
                      color: 'var(--text-secondary)',
                    }}
                  >
                    <FolderOpen className="w-3.5 h-3.5" style={{ color: 'var(--accent-primary)' }} />
                    <span>Auto-Index Folder</span>
                  </button>
                )}

                <button
                  type="button"
                  onClick={() => openCapture('visual')}
                  className="memora-press inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-medium border"
                  style={{
                    background: 'var(--bg-surface)',
                    borderColor: 'var(--border-subtle)',
                    color: 'var(--text-primary)',
                  }}
                >
                  <Plus className="w-3.5 h-3.5" style={{ color: 'var(--accent-primary)' }} />
                  <span>Add Memory</span>
                </button>
              </div>
            </section>

            {/* Subtle Visual Intelligence Reminder Pill Strip (when unconfirmed captures exist) */}
            {suggestedRemindersCount > 0 && (
              <div className="max-w-3xl w-full mx-auto">
                <div
                  className="rounded-full px-5 py-2.5 border flex flex-wrap items-center justify-between gap-3"
                  style={{
                    background: 'var(--bg-surface)',
                    borderColor: 'var(--border-subtle)',
                    boxShadow: 'var(--shadow-card)',
                  }}
                >
                  <div className="flex items-center gap-2.5 text-xs sm:text-[13px]">
                    <span
                      className="w-2 h-2 rounded-full shrink-0"
                      style={{ background: 'var(--accent-primary)' }}
                    />
                    <span style={{ color: 'var(--text-secondary)' }}>
                      <strong style={{ color: 'var(--text-primary)' }}>
                        {suggestedRemindersCount} upcoming{' '}
                        {suggestedRemindersCount === 1 ? 'date' : 'dates'}
                      </strong>{' '}
                      extracted from your saved captures and ready to confirm.
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setTab('reminders')}
                    className="memora-press inline-flex items-center gap-1 text-xs font-semibold"
                    style={{ color: 'var(--accent-primary)' }}
                  >
                    <span>Review</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}

            {/* True Asymmetric Masonry Memory Gallery */}
            {filteredMemories.length === 0 ? (
              <section
                className="max-w-xl w-full mx-auto rounded-3xl border border-dashed px-8 py-16 text-center flex flex-col items-center gap-4"
                style={{
                  background: 'var(--bg-surface)',
                  borderColor: 'var(--border-strong)',
                }}
              >
                <div
                  className="w-12 h-12 rounded-2xl flex items-center justify-center"
                  style={{ background: 'var(--bg-soft)', color: 'var(--text-muted)' }}
                >
                  <Upload className="w-5 h-5" />
                </div>
                <h2 className="font-display text-3xl font-medium">
                  {memories.length === 0 ? 'Your private gallery is waiting' : 'No matching artifacts'}
                </h2>
                <p className="text-sm max-w-md leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                  {memories.length === 0
                    ? 'Drop any screenshot, event flyer, receipt, or voice thought anywhere on this canvas. Memora understands it locally first — even offline.'
                    : 'Clear the filter above or press Enter to ask Memora in natural language.'}
                </p>
                <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
                  {/* Lucian UX/UI Prompt #001 Magnetic Liquid Glass CTA */}
                  <LucianLiquidButton
                    label="Capture First Memory"
                    icon={<Plus className="w-4 h-4" />}
                    onClick={() => openCapture('visual')}
                  />
                </div>
              </section>
            ) : (
              /* Lucian UX/UI Prompt #228 Cursor-Lit Glowing Card Borders over Asymmetric Masonry */
              <LucianGlowMasonry className="memora-masonry" ariaLabel="Memory gallery">
                {filteredMemories.map((mem) => (
                  <div key={mem.id} className="memora-masonry-item">
                    <MemoryCard
                      memory={mem}
                      linkedReminder={reminderByMemoryId.get(mem.id)}
                      onOpen={setSelectedMemory}
                      onConfirmReminder={(r) => void handleConfirmReminder(r)}
                      onTogglePin={(m) => void handleUpdateMemory({ ...m, pinned: !m.pinned })}
                    />
                  </div>
                ))}
              </LucianGlowMasonry>
            )}
          </div>
        )}

        {tab === 'search' && (
          <SearchAssistantView
            memories={memories}
            defaultMode={defaultMode}
            remoteConfig={remoteConfig}
            initialQuery={quickFilter}
            onOpenMemory={setSelectedMemory}
            onBenchmarksChanged={() => void getAllBenchmarks().then(setBenchmarks)}
          />
        )}

        {tab === 'threads' && (
          <ThreadsView
            threads={threads}
            memories={memories}
            defaultMode={defaultMode}
            remoteConfig={remoteConfig}
            highlightTopic={highlightThread}
            onUpdateThread={async (t) => {
              const saved = await saveThread(t, user?.uid || null);
              setThreads((prev) => prev.map((x) => (x.id === saved.id ? saved : x)));
            }}
            onOpenMemory={setSelectedMemory}
            onBenchmarksChanged={() => void getAllBenchmarks().then(setBenchmarks)}
          />
        )}

        {tab === 'reminders' && (
          <RemindersView
            reminders={reminders}
            memories={memories}
            onConfirm={handleConfirmReminder}
            onComplete={handleCompleteReminder}
            onSnooze={handleSnoozeReminder}
            onDelete={async (id) => {
              await removeReminder(id, user?.uid || null);
              setReminders((prev) => annotateReminderConflicts(prev.filter((r) => r.id !== id)));
            }}
            onCreateManual={handleCreateManualReminder}
            onOpenMemory={setSelectedMemory}
          />
        )}
      </main>

      {/* Mobile Crisp Solid Floating Navigation Dock (Zero Bleed-Through) */}
      <nav
        className="md:hidden fixed bottom-4 inset-x-4 z-40 rounded-full border px-2 py-1.5 flex items-center justify-between"
        style={{
          background: 'var(--bg-surface)',
          borderColor: 'var(--border-strong)',
          boxShadow: 'var(--shadow-modal)',
        }}
        aria-label="Mobile navigation"
      >
        {(
          [
            { id: 'vault', label: 'Vault', icon: Layers },
            { id: 'search', label: 'Ask', icon: Search },
            { id: 'threads', label: 'Threads', icon: GitBranch },
            { id: 'reminders', label: 'Remind', icon: Bell },
          ] as const
        ).map(({ id, label, icon: Icon }) => {
          const active = tab === id;
          return (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              className="memora-press flex-1 flex flex-col items-center justify-center py-1.5 rounded-full text-[10.5px] font-semibold"
              style={{
                background: active ? 'var(--bg-soft)' : 'transparent',
                color: active ? 'var(--accent-primary)' : 'var(--text-muted)',
              }}
            >
              <Icon className="w-4 h-4 mb-0.5" />
              <span>{label}</span>
            </button>
          );
        })}

        <button
          type="button"
          onClick={() => openCapture('visual')}
          className="memora-press ml-1 w-11 h-11 rounded-full flex items-center justify-center shrink-0"
          style={{ background: 'var(--accent-primary)', color: 'var(--on-accent)' }}
          aria-label="Capture memory"
        >
          <Plus className="w-5 h-5" />
        </button>
      </nav>

      {/* Full-Viewport Clean Drop Curtain */}
      {globalDrag && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-6 pointer-events-none"
          style={{ background: 'rgba(17, 19, 24, 0.52)' }}
        >
          <div
            className="rounded-3xl border border-dashed px-12 py-10 text-center"
            style={{
              background: 'var(--bg-surface)',
              borderColor: 'var(--accent-primary)',
              boxShadow: 'var(--shadow-modal)',
            }}
          >
            <Upload className="w-9 h-9 mx-auto mb-3" style={{ color: 'var(--accent-primary)' }} />
            <p className="font-display text-3xl font-medium">Drop into your vault</p>
            <p className="text-sm mt-1.5" style={{ color: 'var(--text-secondary)' }}>
              Analyzed privately on-device first — screenshots, flyers, receipts, PDFs, or audio.
            </p>
          </div>
        </div>
      )}

      {/* Lucian UX/UI Prompt #435 Stacking Swipe-to-Dismiss Toast Deck */}
      <LucianToastStack toasts={toasts} onDismiss={dismissToast} />

      {/* Drawers & Modals */}
      <CaptureModal
        open={captureOpen}
        defaultMode={defaultMode}
        busy={captureBusy}
        initialTab={captureTabHint}
        initialFile={droppedFile}
        initialUrl={prefillUrl}
        initialText={prefillText}
        onClose={() => {
          setCaptureOpen(false);
          setDroppedFile(null);
          setPrefillUrl('');
          setPrefillText('');
        }}
        onSubmit={handleCaptureSubmit}
      />

      <MemoryDetailDrawer
        memory={selectedMemory}
        reminders={reminders}
        remoteConfig={remoteConfig}
        onClose={() => setSelectedMemory(null)}
        onUpdate={(m) => void handleUpdateMemory(m)}
        onDelete={(id) => void handleDeleteMemory(id)}
        onConfirmReminder={(r) => void handleConfirmReminder(r)}
        onJumpToThread={(topic) => {
          setHighlightThread(topic);
          setTab('threads');
        }}
      />

      <HybridTelemetryDrawer
        open={telemetryOpen}
        onClose={() => setTelemetryOpen(false)}
        user={user}
        online={online}
        pendingCount={pendingSyncCount}
        localOnlyCount={localOnlyCount}
        capability={capability}
        defaultMode={defaultMode}
        remoteConfig={remoteConfig}
        benchmarks={benchmarks}
        onChangeDefaultMode={setDefaultMode}
        onSyncNow={async () => {
          const n = await flushPendingSync(user?.uid || null);
          showToast(n > 0 ? `Synced ${n} pending memories.` : 'Everything is already up to date.');
          await refreshLocalState();
        }}
        onRefreshCapability={refreshCapability}
        onLoadDemoData={handleLoadDemoData}
        connectedFolderName={folderHandle?.name || null}
        folderScope={folderScope}
        folderIndexedCount={folderIndexedCount}
        folderScanning={folderScanning}
        folderApiSupported={folderApiSupported}
        onConnectFolder={handleConnectScreenshotsFolder}
        onDisconnectFolder={() => setFolderHandle(null)}
        onChangeFolderScope={(s) => {
          setFolderScope(s);
          if (folderHandle) void scanLocalFolder(folderHandle, s, false);
        }}
        onScanFolderNow={async () => {
          if (folderHandle) await scanLocalFolder(folderHandle, folderScope, false);
        }}
        onBenchmarkComplete={async () => {
          await refreshLocalState();
          showToast('Live side-by-side Hybrid AI benchmark recorded.');
        }}
      />
    </div>
  );
}

export default App;
