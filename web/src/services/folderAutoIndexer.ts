const DEDUPE_STORAGE_KEY = 'memora-indexed-folder-files-v1';

export type WebFolderScope = 'all_in_folder' | 'screenshots_only';

export interface LocalFolderFileCandidate {
  dedupeKey: string;
  file: File;
  relativeName: string;
  isScreenshot: boolean;
}

interface FsDirectoryHandle {
  name: string;
  entries(): AsyncIterableIterator<[string, FsHandle]>;
  queryPermission?: (desc?: { mode?: 'read' | 'readwrite' }) => Promise<PermissionState>;
  requestPermission?: (desc?: { mode?: 'read' | 'readwrite' }) => Promise<PermissionState>;
}

interface FsFileHandle {
  kind: 'file';
  name: string;
  getFile(): Promise<File>;
}

type FsHandle = FsFileHandle | ({ kind: 'directory' } & FsDirectoryHandle);

export type DirectoryHandleRef = FsDirectoryHandle;

const IMAGE_EXTENSIONS = /\.(png|jpe?g|webp|gif|bmp)$/i;
const SCREENSHOT_NAME = /(screenshot|screen[\s_-]?shot|snip|capture)/i;

export function isDirectoryPickerSupported(): boolean {
  return typeof window !== 'undefined' && 'showDirectoryPicker' in window;
}

export function loadIndexedFolderKeys(): Set<string> {
  try {
    const raw = localStorage.getItem(DEDUPE_STORAGE_KEY);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? new Set(arr.map(String)) : new Set();
  } catch {
    return new Set();
  }
}

export function persistIndexedFolderKeys(keys: Set<string>): void {
  try {
    // Cap stored keys to most recent 1500 entries
    const list = Array.from(keys).slice(-1500);
    localStorage.setItem(DEDUPE_STORAGE_KEY, JSON.stringify(list));
  } catch {
    // ignore storage quota warnings
  }
}

export async function pickLocalScreenshotsFolder(): Promise<DirectoryHandleRef | null> {
  if (!isDirectoryPickerSupported()) return null;
  const picker = (
    window as unknown as {
      showDirectoryPicker: (opts?: {
        id?: string;
        mode?: 'read' | 'readwrite';
        startIn?: string;
      }) => Promise<DirectoryHandleRef>;
    }
  ).showDirectoryPicker;

  try {
    return await picker({
      id: 'memora-screenshots',
      mode: 'read',
      startIn: 'pictures',
    });
  } catch (err) {
    // User canceled picker
    if (err instanceof DOMException && err.name === 'AbortError') return null;
    throw err;
  }
}

export async function scanConnectedFolder(params: {
  dirHandle: DirectoryHandleRef;
  scope: WebFolderScope;
  alreadyIndexedKeys: Set<string>;
  maxBatch?: number;
}): Promise<LocalFolderFileCandidate[]> {
  const { dirHandle, scope, alreadyIndexedKeys, maxBatch = 5 } = params;

  if (typeof dirHandle.queryPermission === 'function') {
    let state = await dirHandle.queryPermission({ mode: 'read' });
    if (state !== 'granted' && typeof dirHandle.requestPermission === 'function') {
      state = await dirHandle.requestPermission({ mode: 'read' });
    }
    if (state !== 'granted') return [];
  }

  const found: Array<LocalFolderFileCandidate & { lastModified: number }> = [];
  const folderLooksLikeScreenshots = SCREENSHOT_NAME.test(dirHandle.name);

  for await (const [name, handle] of dirHandle.entries()) {
    if (handle.kind !== 'file') continue;
    if (!IMAGE_EXTENSIONS.test(name)) continue;

    const file = await handle.getFile();
    if (file.size <= 0 || file.size > 18 * 1024 * 1024) continue;

    const isScreenshot = folderLooksLikeScreenshots || SCREENSHOT_NAME.test(name);
    if (scope === 'screenshots_only' && !isScreenshot) continue;

    const dedupeKey = `fs:${dirHandle.name}/${name}:${file.size}:${file.lastModified}`;
    if (alreadyIndexedKeys.has(dedupeKey)) continue;

    found.push({
      dedupeKey,
      file,
      relativeName: name,
      isScreenshot,
      lastModified: file.lastModified,
    });
  }

  found.sort((a, b) => b.lastModified - a.lastModified);
  return found.slice(0, maxBatch).map(({ lastModified: _lm, ...rest }) => rest);
}
