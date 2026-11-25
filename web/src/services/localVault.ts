import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import type {
  BenchmarkEntry,
  MemoryItem,
  MemoryThread,
  ReminderItem,
} from '../types/memora';

interface MemoraDBSchema extends DBSchema {
  memories: {
    key: string;
    value: MemoryItem;
    indexes: { 'by-created': number };
  };
  reminders: {
    key: string;
    value: ReminderItem;
    indexes: { 'by-due': string };
  };
  threads: {
    key: string;
    value: MemoryThread;
  };
  mediaBlobs: {
    key: string;
    value: {
      key: string;
      blob: Blob;
      mimeType: string;
      fileName?: string;
      createdAt: number;
    };
  };
  benchmarks: {
    key: string;
    value: BenchmarkEntry;
  };
}

let dbPromise: Promise<IDBPDatabase<MemoraDBSchema>> | null = null;

function getLocalDb() {
  if (!dbPromise) {
    dbPromise = openDB<MemoraDBSchema>('memora-local-vault-v1', 1, {
      upgrade(db) {
        const memStore = db.createObjectStore('memories', { keyPath: 'id' });
        memStore.createIndex('by-created', 'createdAt');
        const remStore = db.createObjectStore('reminders', { keyPath: 'id' });
        remStore.createIndex('by-due', 'dueDateIso');
        db.createObjectStore('threads', { keyPath: 'id' });
        db.createObjectStore('mediaBlobs', { keyPath: 'key' });
        db.createObjectStore('benchmarks', { keyPath: 'id' });
      },
    });
  }
  return dbPromise;
}

export async function getAllLocalMemories(): Promise<MemoryItem[]> {
  const db = await getLocalDb();
  const list = await db.getAll('memories');
  return list.sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.createdAt - a.createdAt);
}

export async function putLocalMemory(memory: MemoryItem): Promise<void> {
  const db = await getLocalDb();
  await db.put('memories', memory);
}

export async function deleteLocalMemory(id: string): Promise<void> {
  const db = await getLocalDb();
  const existing = await db.get('memories', id);
  if (existing?.mediaLocalKey) {
    await db.delete('mediaBlobs', existing.mediaLocalKey);
  }
  await db.delete('memories', id);
}

export async function getAllLocalReminders(): Promise<ReminderItem[]> {
  const db = await getLocalDb();
  const list = await db.getAll('reminders');
  return list.sort((a, b) => a.dueDateIso.localeCompare(b.dueDateIso));
}

export async function putLocalReminder(reminder: ReminderItem): Promise<void> {
  const db = await getLocalDb();
  await db.put('reminders', reminder);
}

export async function deleteLocalReminder(id: string): Promise<void> {
  const db = await getLocalDb();
  await db.delete('reminders', id);
}

export async function getAllLocalThreads(): Promise<MemoryThread[]> {
  const db = await getLocalDb();
  const list = await db.getAll('threads');
  return list.sort((a, b) => b.updatedAt - a.updatedAt);
}

export async function putLocalThread(thread: MemoryThread): Promise<void> {
  const db = await getLocalDb();
  await db.put('threads', thread);
}

export async function deleteLocalThread(id: string): Promise<void> {
  const db = await getLocalDb();
  await db.delete('threads', id);
}

export async function saveMediaBlob(
  key: string,
  blob: Blob,
  mimeType: string,
  fileName?: string
): Promise<void> {
  const db = await getLocalDb();
  await db.put('mediaBlobs', {
    key,
    blob,
    mimeType,
    fileName,
    createdAt: Date.now(),
  });
}

export async function getMediaBlobUrl(key: string): Promise<string | null> {
  const db = await getLocalDb();
  const record = await db.get('mediaBlobs', key);
  if (!record) return null;
  return URL.createObjectURL(record.blob);
}

export async function saveBenchmark(entry: BenchmarkEntry): Promise<void> {
  const db = await getLocalDb();
  await db.put('benchmarks', entry);
}

export async function getAllBenchmarks(): Promise<BenchmarkEntry[]> {
  const db = await getLocalDb();
  const all = await db.getAll('benchmarks');
  return all.sort((a, b) => b.timestamp - a.timestamp).slice(0, 40);
}

export async function exportVaultJson(): Promise<string> {
  const [memories, reminders, threads] = await Promise.all([
    getAllLocalMemories(),
    getAllLocalReminders(),
    getAllLocalThreads(),
  ]);
  return JSON.stringify(
    {
      exportedAt: new Date().toISOString(),
      app: 'MEMORA',
      version: '1.0.0',
      memories,
      reminders,
      threads,
    },
    null,
    2
  );
}
