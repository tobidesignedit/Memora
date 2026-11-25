import {
  collection,
  deleteDoc,
  doc,
  onSnapshot,
  setDoc,
  type Unsubscribe,
} from 'firebase/firestore';
import { db, isFirebaseConfigured } from './firebase';
import {
  deleteLocalMemory,
  deleteLocalReminder,
  getAllLocalMemories,
  getAllLocalReminders,
  getAllLocalThreads,
  putLocalMemory,
  putLocalReminder,
  putLocalThread,
} from './localVault';
import type { MemoryItem, MemoryThread, ReminderItem } from '../types/memora';

function cleanForFirestore<T extends Record<string, unknown>>(obj: T): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v !== undefined) {
      out[k] = v;
    }
  }
  return out;
}

export async function saveMemory(memory: MemoryItem, uid: string | null): Promise<MemoryItem> {
  const isLocalOnly = memory.privacyMode === 'only_on_device';
  const canSync = Boolean(db && isFirebaseConfigured && uid && !isLocalOnly);

  const prepared: MemoryItem = {
    ...memory,
    uid: uid || 'local',
    syncStatus: isLocalOnly ? 'local_only' : canSync && navigator.onLine ? 'synced' : 'pending',
    updatedAt: Date.now(),
  };

  await putLocalMemory(prepared);

  if (canSync && db && uid) {
    try {
      await setDoc(
        doc(db, 'users', uid, 'memories', prepared.id),
        cleanForFirestore({ ...prepared, syncStatus: 'synced' } as unknown as Record<string, unknown>),
        { merge: true }
      );
      prepared.syncStatus = 'synced';
      await putLocalMemory(prepared);
    } catch (err) {
      console.warn('[MEMORA] Saved offline to local queue; Firestore sync pending:', err);
      prepared.syncStatus = 'pending';
      await putLocalMemory(prepared);
    }
  }
  return prepared;
}

export async function removeMemory(id: string, uid: string | null): Promise<void> {
  await deleteLocalMemory(id);
  if (db && isFirebaseConfigured && uid) {
    try {
      await deleteDoc(doc(db, 'users', uid, 'memories', id));
    } catch (err) {
      console.warn('[MEMORA] Cloud memory delete deferred:', err);
    }
  }
}

export async function saveReminder(reminder: ReminderItem, uid: string | null): Promise<ReminderItem> {
  const updated: ReminderItem = {
    ...reminder,
    uid: uid || 'local',
    updatedAt: Date.now(),
  };
  await putLocalReminder(updated);
  if (db && isFirebaseConfigured && uid) {
    try {
      await setDoc(
        doc(db, 'users', uid, 'reminders', updated.id),
        cleanForFirestore(updated as unknown as Record<string, unknown>),
        { merge: true }
      );
    } catch (err) {
      console.warn('[MEMORA] Reminder saved offline; cloud sync pending:', err);
    }
  }
  return updated;
}

export async function removeReminder(id: string, uid: string | null): Promise<void> {
  await deleteLocalReminder(id);
  if (db && isFirebaseConfigured && uid) {
    try {
      await deleteDoc(doc(db, 'users', uid, 'reminders', id));
    } catch (err) {
      console.warn('[MEMORA] Reminder cloud delete deferred:', err);
    }
  }
}

export async function saveThread(thread: MemoryThread, uid: string | null): Promise<MemoryThread> {
  const updated: MemoryThread = {
    ...thread,
    uid: uid || 'local',
    updatedAt: Date.now(),
  };
  await putLocalThread(updated);
  if (db && isFirebaseConfigured && uid) {
    try {
      await setDoc(
        doc(db, 'users', uid, 'threads', updated.id),
        cleanForFirestore(updated as unknown as Record<string, unknown>),
        { merge: true }
      );
    } catch (err) {
      console.warn('[MEMORA] Thread saved locally; cloud sync pending:', err);
    }
  }
  return updated;
}

/**
 * Automatically groups related memories into Memory Threads by normalized topic/category/shared tags.
 */
export async function reconcileMemoryThreads(
  memories: MemoryItem[],
  existingThreads: MemoryThread[],
  uid: string | null
): Promise<MemoryThread[]> {
  const active = memories.filter((m) => !m.archived);
  const buckets = new Map<string, MemoryItem[]>();

  for (const m of active) {
    const rawKey = (m.threadTopic || m.category || 'General').trim();
    const normKey = rawKey.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    if (!normKey) continue;
    const list = buckets.get(normKey) || [];
    list.push(m);
    buckets.set(normKey, list);
  }

  const byId = new Map(existingThreads.map((t) => [t.id, t]));
  const result: MemoryThread[] = [];

  for (const [slug, items] of buckets.entries()) {
    if (items.length < 2) continue;
    const threadId = `thread-${slug}`;
    const existing = byId.get(threadId);
    const title = items[0].threadTopic || items[0].category || slug;
    const memoryIds = items.map((i) => i.id);
    const sameMembers =
      existing &&
      existing.memoryIds.length === memoryIds.length &&
      existing.memoryIds.every((id) => memoryIds.includes(id));

    if (existing && sameMembers) {
      result.push(existing);
    } else {
      const thread: MemoryThread = {
        id: threadId,
        uid: uid || 'local',
        title,
        topic: slug,
        summary:
          existing?.summary ||
          `${items.length} connected memories across ${Array.from(new Set(items.map((i) => i.kind))).join(', ')} relating to ${title}.`,
        memoryIds,
        keyInsights:
          existing?.keyInsights && existing.keyInsights.length > 0
            ? existing.keyInsights
            : items.slice(0, 3).map((i) => i.summary),
        nextActions:
          existing?.nextActions && existing.nextActions.length > 0
            ? existing.nextActions
            : Array.from(new Set(items.flatMap((i) => i.actionItems))).slice(0, 4),
        autoGenerated: true,
        inferenceSource: existing?.inferenceSource,
        updatedAt: Date.now(),
      };
      await saveThread(thread, uid);
      result.push(thread);
    }
  }

  return result.sort((a, b) => b.updatedAt - a.updatedAt);
}

/**
 * Reconciles pending offline captures to Firestore once connectivity returns.
 */
export async function flushPendingSync(uid: string | null): Promise<number> {
  if (!db || !isFirebaseConfigured || !uid || !navigator.onLine) return 0;
  const localMemories = await getAllLocalMemories();
  const pending = localMemories.filter(
    (m) => m.syncStatus === 'pending' && m.privacyMode !== 'only_on_device'
  );
  let synced = 0;
  for (const item of pending) {
    try {
      const updated: MemoryItem = { ...item, uid, syncStatus: 'synced', updatedAt: Date.now() };
      await setDoc(
        doc(db, 'users', uid, 'memories', item.id),
        cleanForFirestore(updated as unknown as Record<string, unknown>),
        { merge: true }
      );
      await putLocalMemory(updated);
      synced += 1;
    } catch {
      // keep pending
    }
  }
  return synced;
}

/**
 * Subscribes to live Firestore collections (synced instantaneously with MEMORA Android)
 * while preserving local-only (`ONLY_ON_DEVICE`) memories from IndexedDB.
 */
export function subscribeToCloudVault(
  uid: string,
  callbacks: {
    onMemories: (items: MemoryItem[]) => void;
    onReminders: (items: ReminderItem[]) => void;
    onThreads: (items: MemoryThread[]) => void;
  }
): () => void {
  if (!db || !isFirebaseConfigured) return () => {};

  const unsubs: Unsubscribe[] = [];

  unsubs.push(
    onSnapshot(collection(db, 'users', uid, 'memories'), async (snap) => {
      const cloudMap = new Map<string, MemoryItem>();
      for (const d of snap.docs) {
        const data = d.data() as MemoryItem;
        const item: MemoryItem = { ...data, id: d.id, syncStatus: 'synced' };
        cloudMap.set(item.id, item);
        await putLocalMemory(item);
      }
      const localAll = await getAllLocalMemories();
      // Merge cloud items + local_only / pending items
      const merged = new Map<string, MemoryItem>();
      for (const l of localAll) {
        if (l.privacyMode === 'only_on_device' || l.syncStatus === 'pending') {
          merged.set(l.id, l);
        }
      }
      for (const [id, c] of cloudMap.entries()) {
        merged.set(id, c);
      }
      const sorted = Array.from(merged.values()).sort(
        (a, b) => Number(b.pinned) - Number(a.pinned) || b.createdAt - a.createdAt
      );
      callbacks.onMemories(sorted);
    })
  );

  unsubs.push(
    onSnapshot(collection(db, 'users', uid, 'reminders'), async (snap) => {
      const cloudMap = new Map<string, ReminderItem>();
      for (const d of snap.docs) {
        const item = { ...(d.data() as ReminderItem), id: d.id };
        cloudMap.set(item.id, item);
        await putLocalReminder(item);
      }
      const localAll = await getAllLocalReminders();
      const merged = new Map<string, ReminderItem>();
      for (const l of localAll) merged.set(l.id, l);
      for (const [id, c] of cloudMap.entries()) merged.set(id, c);
      callbacks.onReminders(
        Array.from(merged.values()).sort((a, b) => a.dueDateIso.localeCompare(b.dueDateIso))
      );
    })
  );

  unsubs.push(
    onSnapshot(collection(db, 'users', uid, 'threads'), async (snap) => {
      const cloudMap = new Map<string, MemoryThread>();
      for (const d of snap.docs) {
        const item = { ...(d.data() as MemoryThread), id: d.id };
        cloudMap.set(item.id, item);
        await putLocalThread(item);
      }
      const localAll = await getAllLocalThreads();
      const merged = new Map<string, MemoryThread>();
      for (const l of localAll) merged.set(l.id, l);
      for (const [id, c] of cloudMap.entries()) merged.set(id, c);
      callbacks.onThreads(
        Array.from(merged.values()).sort((a, b) => b.updatedAt - a.updatedAt)
      );
    })
  );

  return () => {
    for (const u of unsubs) u();
  };
}

export function annotateReminderConflicts(reminders: ReminderItem[]): ReminderItem[] {
  const active = reminders.filter((r) => r.status !== 'completed');
  return reminders.map((rem) => {
    if (rem.status === 'completed') return { ...rem, conflictWithId: undefined };
    const conflict = active.find(
      (other) =>
        other.id !== rem.id &&
        other.dueDateIso === rem.dueDateIso &&
        Boolean(other.dueTime && rem.dueTime && other.dueTime === rem.dueTime)
    );
    return { ...rem, conflictWithId: conflict?.id };
  });
}
