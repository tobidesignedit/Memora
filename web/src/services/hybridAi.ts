import {
  getGenerativeModel,
  InferenceMode,
  InferenceSource,
  ResponseModality,
  type Part,
} from 'firebase/ai';
import { ai, DEFAULT_REMOTE_CONFIG, isFirebaseConfigured } from './firebase';
import { saveBenchmark } from './localVault';
import type {
  DetectedReminderCandidate,
  ExtractedEntities,
  InferenceRouteSource,
  MemoryItem,
  MemoryKind,
  PrivacyRoutingMode,
  ReminderCategory,
  RemoteConfigValues,
  SearchAnswer,
  SideBySideBenchmarkResult,
  SideBySideLegResult,
} from '../types/memora';

export interface OnDeviceCapability {
  supported: boolean;
  status: 'available' | 'downloadable' | 'downloading' | 'unavailable';
  detail: string;
}

export interface CaptureUnderstandingResult {
  kind: MemoryKind;
  title: string;
  summary: string;
  extractedText: string;
  category: string;
  tags: string[];
  entities: ExtractedEntities;
  actionItems: string[];
  reminders: DetectedReminderCandidate[];
  threadTopic: string;
  importance: 'high' | 'normal' | 'low';
  language: string;
  inferenceSource: InferenceRouteSource;
  modelUsed: string;
  latencyMs: number;
}

export interface ThreadSynthesisResult {
  summary: string;
  keyInsights: string[];
  nextActions: string[];
  inferenceSource: InferenceRouteSource;
  modelUsed: string;
  latencyMs: number;
}

export async function probeChromeOnDeviceCapability(): Promise<OnDeviceCapability> {
  if (typeof window === 'undefined') {
    return { supported: false, status: 'unavailable', detail: 'Non-browser environment' };
  }
  const win = window as unknown as Record<string, unknown>;
  try {
    // Chrome Built-in AI Prompt API (2025-2026 standard LanguageModel global)
    const LM = win.LanguageModel as
      | { availability?: (opts?: unknown) => Promise<string> }
      | undefined;
    if (LM && typeof LM.availability === 'function') {
      const state = await LM.availability({ expectedInputs: [{ type: 'text' }] });
      if (state === 'available' || state === 'readily') {
        return {
          supported: true,
          status: 'available',
          detail: 'Chrome Built-in Gemini Nano is ready on this device.',
        };
      }
      if (state === 'downloadable' || state === 'after-download') {
        return {
          supported: true,
          status: 'downloadable',
          detail: 'Gemini Nano is supported and ready to download locally.',
        };
      }
      if (state === 'downloading') {
        return {
          supported: true,
          status: 'downloading',
          detail: 'Gemini Nano model is downloading in Chrome.',
        };
      }
    }

    // Legacy window.ai.languageModel check
    const legacyAi = win.ai as
      | { languageModel?: { capabilities?: () => Promise<{ available?: string }> } }
      | undefined;
    if (legacyAi?.languageModel?.capabilities) {
      const cap = await legacyAi.languageModel.capabilities();
      if (cap.available === 'readily') {
        return {
          supported: true,
          status: 'available',
          detail: 'Chrome Built-in AI model is ready on-device.',
        };
      }
      if (cap.available === 'after-download') {
        return {
          supported: true,
          status: 'downloadable',
          detail: 'Chrome Built-in AI can be downloaded on-device.',
        };
      }
    }
  } catch (err) {
    console.warn('[MEMORA] On-device capability probe warning:', err);
  }

  return {
    supported: false,
    status: 'unavailable',
    detail:
      'Chrome Built-in Gemini Nano is unavailable here — Firebase AI Logic automatically routes to Cloud Gemini.',
  };
}

function mapMode(mode: PrivacyRoutingMode) {
  switch (mode) {
    case 'only_on_device':
      return InferenceMode.ONLY_ON_DEVICE;
    case 'prefer_in_cloud':
      return InferenceMode.PREFER_IN_CLOUD;
    default:
      return InferenceMode.PREFER_ON_DEVICE;
  }
}

export async function warmUpOnDeviceModel(
  onProgress?: (progress: number) => void,
  rc: RemoteConfigValues = DEFAULT_REMOTE_CONFIG
): Promise<{ ok: boolean; message: string }> {
  if (!ai) {
    // Even without Firebase project env configured yet, attempt direct Chrome Prompt API warmup if supported
    const win = window as unknown as Record<string, unknown>;
    const LM = win.LanguageModel as
      | {
          create?: (opts?: {
            monitor?: (m: {
              addEventListener: (ev: string, cb: (e: { loaded: number }) => void) => void;
            }) => void;
          }) => Promise<{ destroy?: () => void }>;
        }
      | undefined;
    if (LM?.create) {
      try {
        const session = await LM.create({
          monitor(m) {
            m.addEventListener('downloadprogress', (e) => {
              onProgress?.(Math.min(1, Math.max(0, e.loaded)));
            });
          },
        });
        session.destroy?.();
        onProgress?.(1);
        return { ok: true, message: 'On-device Gemini Nano initialized in Chrome.' };
      } catch (err) {
        return {
          ok: false,
          message: err instanceof Error ? err.message : 'Unable to initialize Chrome built-in AI.',
        };
      }
    }
    return { ok: false, message: 'Configure Firebase or enable Chrome Built-in AI flags first.' };
  }

  try {
    const model = getGenerativeModel(ai, {
      mode: InferenceMode.PREFER_ON_DEVICE,
      inCloudParams: { model: rc.cloudModelFast },
    });
    await model.initializeDeviceModel((p) => onProgress?.(p));
    onProgress?.(1);
    return { ok: true, message: 'Gemini Nano is ready for local private inference.' };
  } catch (err) {
    return {
      ok: false,
      message:
        err instanceof Error
          ? err.message
          : 'On-device model unavailable on this browser; cloud fallback remains active.',
    };
  }
}

function stripJsonFences(raw: string): string {
  const trimmed = raw.trim();
  const fenceMatch = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenceMatch) return fenceMatch[1].trim();
  const start = trimmed.indexOf('{');
  const end = trimmed.lastIndexOf('}');
  if (start !== -1 && end > start) {
    return trimmed.slice(start, end + 1);
  }
  return trimmed;
}

function safeArray(val: unknown): string[] {
  if (!Array.isArray(val)) return [];
  return val
    .map((v) => String(v ?? '').trim())
    .filter(Boolean)
    .slice(0, 12);
}

export async function fileToInlinePart(file: File | Blob, mimeType?: string): Promise<Part> {
  const base64 = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const res = String(reader.result || '');
      const comma = res.indexOf(',');
      resolve(comma >= 0 ? res.slice(comma + 1) : res);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
  return {
    type: 'inlineData',
    inlineData: {
      mimeType: mimeType || file.type || 'application/octet-stream',
      data: base64,
    },
  };
}

export async function createImageThumbnail(file: File | Blob): Promise<string | undefined> {
  if (!file.type.startsWith('image/')) return undefined;
  return new Promise((resolve) => {
    const img = new Image();
    const objectUrl = URL.createObjectURL(file);
    img.onload = () => {
      try {
        const maxDim = 640;
        const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(img.width * scale));
        canvas.height = Math.max(1, Math.round(img.height * scale));
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          URL.revokeObjectURL(objectUrl);
          resolve(undefined);
          return;
        }
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        const dataUrl = canvas.toDataURL('image/jpeg', 0.72);
        URL.revokeObjectURL(objectUrl);
        resolve(dataUrl);
      } catch {
        URL.revokeObjectURL(objectUrl);
        resolve(undefined);
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      resolve(undefined);
    };
    img.src = objectUrl;
  });
}

function normalizeReminderCategory(value: unknown): ReminderCategory {
  const s = String(value || '').toLowerCase();
  const allowed: ReminderCategory[] = [
    'event',
    'exam',
    'payment',
    'travel',
    'meeting',
    'deadline',
    'personal',
  ];
  return (allowed.find((a) => s.includes(a)) ?? 'event') as ReminderCategory;
}

function normalizeKind(value: unknown, fallback: MemoryKind): MemoryKind {
  const s = String(value || '').toLowerCase();
  const allowed: MemoryKind[] = [
    'screenshot',
    'photo',
    'voice',
    'note',
    'link',
    'document',
    'receipt',
    'flyer',
    'ticket',
    'timetable',
  ];
  return (allowed.find((a) => s === a) ?? fallback) as MemoryKind;
}

/**
 * Deterministic local offline extractor used when offline AND Chrome Nano is unavailable.
 * Ensures MEMORA remains genuinely useful with zero connectivity.
 */
function deterministicOfflineUnderstand(input: {
  rawText: string;
  url?: string;
  kindHint: MemoryKind;
  fileName?: string;
}): CaptureUnderstandingResult {
  const started = performance.now();
  const text = [input.rawText, input.url, input.fileName].filter(Boolean).join('\n').trim();
  const lower = text.toLowerCase();

  let kind: MemoryKind = input.kindHint;
  if (lower.includes('receipt') || lower.includes('subtotal') || lower.includes('invoice')) {
    kind = 'receipt';
  } else if (lower.includes('exam') || lower.includes('timetable') || lower.includes('syllabus')) {
    kind = 'timetable';
  } else if (lower.includes('flight') || lower.includes('boarding') || lower.includes('ticket')) {
    kind = 'ticket';
  } else if (lower.includes('flyer') || lower.includes('summit') || lower.includes('conference')) {
    kind = 'flyer';
  } else if (input.url) {
    kind = 'link';
  }

  const amounts = Array.from(
    new Set(text.match(/(?:[$€£₦]|USD|NGN|EUR|GBP)\s?\d[\d,]*(?:\.\d{2})?/gi) || [])
  ).slice(0, 6);

  const dateMatches = Array.from(
    new Set(
      text.match(
        /\b(?:\d{4}-\d{2}-\d{2}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+\d{4})?|\d{1,2}\/\d{1,2}\/\d{2,4})\b/gi
      ) || []
    )
  ).slice(0, 6);

  const timeMatches = text.match(/\b\d{1,2}:\d{2}\s?(?:AM|PM)?\b/gi) || [];
  const emails = text.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi) || [];
  const phones = text.match(/(?:\+?\d[\d\s\-()]{7,16}\d)/g) || [];

  const firstLine =
    text
      .split(/\r?\n/)
      .map((l) => l.trim())
      .find((l) => l.length > 2) ||
    input.fileName ||
    'Saved Memory';
  const title = firstLine.length > 68 ? `${firstLine.slice(0, 65)}...` : firstLine;

  const summary =
    text.length > 180
      ? `${text.slice(0, 177).trim()}...`
      : text || `Captured ${kind} saved privately to your local vault.`;

  const stopWords = new Set([
    'about',
    'after',
    'again',
    'below',
    'could',
    'every',
    'first',
    'found',
    'great',
    'house',
    'large',
    'might',
    'never',
    'other',
    'place',
    'right',
    'small',
    'sound',
    'still',
    'study',
    'their',
    'there',
    'these',
    'thing',
    'think',
    'three',
    'water',
    'where',
    'which',
    'world',
    'would',
    'write',
    'https',
  ]);
  const words = lower
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length >= 4 && !stopWords.has(w));
  const freq = new Map<string, number>();
  for (const w of words) freq.set(w, (freq.get(w) || 0) + 1);
  const topTags = Array.from(freq.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 4)
    .map(([w]) => w);

  const tags = Array.from(new Set([kind, ...topTags])).slice(0, 5);

  // Detect candidate upcoming reminder from parsed dates or relative keywords ("tomorrow", "Friday", "due")
  const reminders: DetectedReminderCandidate[] = [];
  const todayIso = new Date().toISOString().slice(0, 10);
  const parseDateToIso = (raw: string): string | null => {
    const parsed = new Date(raw);
    if (!Number.isNaN(parsed.getTime())) {
      if (parsed.getFullYear() < 2020) {
        parsed.setFullYear(new Date().getFullYear());
      }
      return parsed.toISOString().slice(0, 10);
    }
    return null;
  };

  for (const d of dateMatches) {
    const iso = parseDateToIso(d);
    if (iso && iso >= todayIso) {
      const cat: ReminderCategory =
        kind === 'timetable'
          ? 'exam'
          : kind === 'receipt' || lower.includes('pay') || lower.includes('due')
            ? 'payment'
            : kind === 'ticket'
              ? 'travel'
              : lower.includes('meeting') || lower.includes('call')
                ? 'meeting'
                : lower.includes('deadline') || lower.includes('submit')
                  ? 'deadline'
                  : 'event';
      reminders.push({
        title,
        dueDateIso: iso,
        dueTime: timeMatches[0],
        category: cat,
        priority: cat === 'payment' || cat === 'exam' || cat === 'deadline' ? 'high' : 'medium',
        isUpcoming: true,
        notes: 'Detected offline via local date intelligence.',
      });
    }
  }

  if (reminders.length === 0 && /\b(tomorrow|deadline|due|remind|exam|flight|meeting)\b/i.test(text)) {
    const tomorrow = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
    reminders.push({
      title,
      dueDateIso: tomorrow,
      dueTime: timeMatches[0] || '09:00',
      category: lower.includes('pay') || lower.includes('bill') ? 'payment' : 'personal',
      priority: 'medium',
      isUpcoming: true,
      notes: 'Suggested from captured intent — confirm or edit the exact date.',
    });
  }

  const category =
    kind === 'receipt'
      ? 'Finance'
      : kind === 'timetable'
        ? 'Study & Exams'
        : kind === 'ticket'
          ? 'Travel'
          : kind === 'flyer'
            ? 'Events'
            : kind === 'voice'
              ? 'Voice Notes'
              : kind === 'link'
                ? 'Reading & Links'
                : 'Personal Vault';

  const threadTopic = topTags[0]
    ? `${topTags[0].charAt(0).toUpperCase()}${topTags[0].slice(1)} Notes`
    : category;

  return {
    kind,
    title,
    summary,
    extractedText: text,
    category,
    tags,
    entities: {
      people: [],
      organizations: [],
      dates: dateMatches,
      amounts,
      locations: [],
      contacts: [...emails, ...phones].slice(0, 5),
    },
    actionItems: reminders.length > 0 ? [`Follow up: ${title}`] : [],
    reminders,
    threadTopic,
    importance: reminders.length > 0 ? 'high' : 'normal',
    language: 'en',
    inferenceSource: 'offline_heuristic',
    modelUsed: 'local-offline-parser',
    latencyMs: Math.max(4, Math.round(performance.now() - started)),
  };
}

export async function understandCapture(params: {
  rawText: string;
  url?: string;
  file?: File | Blob;
  fileName?: string;
  kindHint: MemoryKind;
  mode: PrivacyRoutingMode;
  remoteConfig: RemoteConfigValues;
}): Promise<CaptureUnderstandingResult> {
  const started = performance.now();
  const todayIso = new Date().toISOString().slice(0, 10);

  const extractionPrompt = `You are MEMORA, a private personal memory AI.
Today's date is ${todayIso}.
Analyze the captured content (text, URL, image, audio, or document) and return STRICT JSON ONLY (no markdown prose outside JSON) matching this schema:
{
  "kind": "screenshot|photo|voice|note|link|document|receipt|flyer|ticket|timetable",
  "title": "Concise, memorable human title (max 60 chars)",
  "summary": "Clear 1-2 sentence summary of what matters here",
  "extractedText": "Full OCR text, transcript, or key extracted text",
  "category": "Finance|Events|Study & Exams|Travel|Work & Projects|Reading & Links|Health & Care|Shopping|Personal Vault",
  "tags": ["3 to 6 short lowercase tags"],
  "threadTopic": "A clean 2-3 word topic/project name so related memories automatically cluster together",
  "importance": "high|normal|low",
  "language": "ISO code e.g. en",
  "entities": {
    "people": [],
    "organizations": [],
    "dates": [],
    "amounts": [],
    "locations": [],
    "contacts": []
  },
  "actionItems": ["concrete tasks or follow-ups found in the content"],
  "reminders": [
    {
      "title": "Specific reminder title",
      "dueDateIso": "YYYY-MM-DD",
      "dueTime": "HH:mm or empty string",
      "venue": "location or empty string",
      "organizer": "person/org or empty string",
      "category": "event|exam|payment|travel|meeting|deadline|personal",
      "priority": "high|medium|low",
      "isUpcoming": true,
      "notes": "why this reminder matters"
    }
  ]
}
Rules for Visual Intelligence Reminders:
- Distinguish historical/past dates (< ${todayIso}) from upcoming events/deadlines (>= ${todayIso}). Only include upcoming or actionable reminders.
- If an image is an exam timetable or flyer with multiple upcoming dates, include each distinct upcoming reminder.
- Never fabricate dates if none exist.

User provided text: ${params.rawText || '(none)'}
User provided URL: ${params.url || '(none)'}
File name: ${params.fileName || '(none)'}`;

  if (!ai || !isFirebaseConfigured || (!navigator.onLine && params.mode === 'prefer_in_cloud')) {
    const fallback = deterministicOfflineUnderstand({
      rawText: params.rawText,
      url: params.url,
      kindHint: params.kindHint,
      fileName: params.fileName,
    });
    await saveBenchmark({
      id: crypto.randomUUID(),
      operation: 'capture_understand',
      source: fallback.inferenceSource,
      modelUsed: fallback.modelUsed,
      latencyMs: fallback.latencyMs,
      timestamp: Date.now(),
    });
    return fallback;
  }

  try {
    const hasHeavyMedia = Boolean(
      params.file &&
        (params.file.type === 'application/pdf' || params.file.type.startsWith('audio/'))
    );
    const chosenCloudModel = hasHeavyMedia
      ? params.remoteConfig.cloudModelPro
      : params.remoteConfig.cloudModelFast;

    // Firebase AI Logic Hybrid Inference SDK call
    const model = getGenerativeModel(ai, {
      mode: mapMode(params.mode),
      inCloudParams: {
        model: chosenCloudModel,
        generationConfig: {
          temperature: 0.2,
          responseMimeType: 'application/json',
        },
      },
    });

    const parts: Array<string | Part> = [extractionPrompt];
    if (params.file) {
      parts.push(await fileToInlinePart(params.file));
    }

    const result = await model.generateContent(parts);
    const response = result.response;
    const rawOut = response.text();
    const latencyMs = Math.max(1, Math.round(performance.now() - started));
    const source: InferenceRouteSource =
      response.inferenceSource === InferenceSource.ON_DEVICE ? 'on_device' : 'in_cloud';
    const modelUsed = source === 'on_device' ? 'gemini-nano-4 (on-device)' : chosenCloudModel;

    const parsed = JSON.parse(stripJsonFences(rawOut)) as Record<string, unknown>;
    const rawEntities = (parsed.entities || {}) as Record<string, unknown>;
    const rawReminders = Array.isArray(parsed.reminders) ? parsed.reminders : [];

    const reminders: DetectedReminderCandidate[] = [];
    for (const r of rawReminders) {
      const item = r as Record<string, unknown>;
      const dueDateIso = String(item.dueDateIso || '').slice(0, 10);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(dueDateIso)) continue;
      const isUpcoming = item.isUpcoming !== false && dueDateIso >= todayIso;
      if (!isUpcoming) continue;
      reminders.push({
        title: String(item.title || parsed.title || 'Upcoming Reminder').trim(),
        dueDateIso,
        dueTime: item.dueTime ? String(item.dueTime).trim() : undefined,
        venue: item.venue ? String(item.venue).trim() : undefined,
        organizer: item.organizer ? String(item.organizer).trim() : undefined,
        category: normalizeReminderCategory(item.category),
        priority:
          item.priority === 'high' || item.priority === 'low' ? item.priority : 'medium',
        isUpcoming: true,
        notes: item.notes ? String(item.notes).trim() : undefined,
      });
    }

    await saveBenchmark({
      id: crypto.randomUUID(),
      operation: 'capture_understand',
      source,
      modelUsed,
      latencyMs,
      timestamp: Date.now(),
    });

    return {
      kind: normalizeKind(parsed.kind, params.kindHint),
      title: String(parsed.title || params.fileName || 'Untitled Memory').trim().slice(0, 80),
      summary: String(parsed.summary || params.rawText || 'Saved to MEMORA vault.').trim(),
      extractedText: String(parsed.extractedText || params.rawText || '').trim(),
      category: String(parsed.category || 'Personal Vault').trim(),
      tags: safeArray(parsed.tags),
      entities: {
        people: safeArray(rawEntities.people),
        organizations: safeArray(rawEntities.organizations),
        dates: safeArray(rawEntities.dates),
        amounts: safeArray(rawEntities.amounts),
        locations: safeArray(rawEntities.locations),
        contacts: safeArray(rawEntities.contacts),
      },
      actionItems: safeArray(parsed.actionItems),
      reminders,
      threadTopic: String(parsed.threadTopic || parsed.category || 'General Memory').trim(),
      importance:
        parsed.importance === 'high' || parsed.importance === 'low' ? parsed.importance : 'normal',
      language: String(parsed.language || 'en'),
      inferenceSource: source,
      modelUsed,
      latencyMs,
    };
  } catch (err) {
    console.warn('[MEMORA] Hybrid inference fallback triggered:', err);
    if (params.mode === 'only_on_device') {
      const offline = deterministicOfflineUnderstand({
        rawText: params.rawText,
        url: params.url,
        kindHint: params.kindHint,
        fileName: params.fileName,
      });
      await saveBenchmark({
        id: crypto.randomUUID(),
        operation: 'capture_understand',
        source: 'offline_heuristic',
        modelUsed: 'local-only-fallback (Nano unavailable)',
        latencyMs: offline.latencyMs,
        timestamp: Date.now(),
      });
      return offline;
    }

    const offline = deterministicOfflineUnderstand({
      rawText: params.rawText,
      url: params.url,
      kindHint: params.kindHint,
      fileName: params.fileName,
    });
    await saveBenchmark({
      id: crypto.randomUUID(),
      operation: 'capture_understand',
      source: 'offline_heuristic',
      modelUsed: 'offline-fallback',
      latencyMs: offline.latencyMs,
      timestamp: Date.now(),
    });
    return offline;
  }
}

export async function searchMemoriesWithAI(params: {
  query: string;
  memories: MemoryItem[];
  mode: PrivacyRoutingMode;
  remoteConfig: RemoteConfigValues;
}): Promise<SearchAnswer> {
  const started = performance.now();
  const qLower = params.query.toLowerCase();
  const qTokens = qLower
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length >= 2);

  // Rank memories locally first so we stay inside context window and support instant offline retrieval
  const scored = params.memories
    .filter((m) => !m.archived)
    .map((m) => {
      const hay = [
        m.title,
        m.summary,
        m.extractedText,
        m.rawText,
        m.category,
        m.kind,
        m.threadTopic || '',
        m.tags.join(' '),
        m.entities.people.join(' '),
        m.entities.organizations.join(' '),
        m.entities.amounts.join(' '),
        m.entities.dates.join(' '),
        m.entities.locations.join(' '),
      ]
        .join(' ')
        .toLowerCase();
      let score = 0;
      for (const t of qTokens) {
        if (hay.includes(t)) score += 3;
      }
      if (hay.includes(qLower)) score += 6;
      if (m.pinned) score += 1;
      return { memory: m, score };
    })
    .sort((a, b) => b.score - a.score || b.memory.createdAt - a.memory.createdAt);

  const topContext = scored.slice(0, params.remoteConfig.maxContextMemories).map((s) => s.memory);

  const localFallbackAnswer = (): SearchAnswer => {
    const matches = scored.filter((s) => s.score > 0).slice(0, 5);
    const latencyMs = Math.max(3, Math.round(performance.now() - started));
    if (matches.length === 0) {
      return {
        query: params.query,
        answer:
          'No exact local matches were found for that query yet. Try broadening your terms or capturing a related screenshot, note, or voice memo.',
        citations: [],
        suggestedFollowUps: [
          'Show all upcoming deadlines',
          'Find receipts and payments',
          'Summarize my recent saves',
        ],
        source: 'offline_heuristic',
        modelUsed: 'local-semantic-index',
        latencyMs,
      };
    }
    const bullets = matches
      .map((m, idx) => `${idx + 1}. **${m.memory.title}** (${m.memory.category}): ${m.memory.summary}`)
      .join('\n');
    return {
      query: params.query,
      answer: `Found ${matches.length} relevant ${matches.length === 1 ? 'memory' : 'memories'} in your vault:\n\n${bullets}`,
      citations: matches.map((m) => ({
        memoryId: m.memory.id,
        title: m.memory.title,
        excerpt: m.memory.summary,
      })),
      suggestedFollowUps: [
        `Turn these into a checklist`,
        `Are there any deadlines across these memories?`,
      ],
      source: 'offline_heuristic',
      modelUsed: 'local-semantic-index',
      latencyMs,
    };
  };

  if (!ai || !isFirebaseConfigured || topContext.length === 0) {
    const ans = localFallbackAnswer();
    await saveBenchmark({
      id: crypto.randomUUID(),
      operation: 'natural_search',
      source: ans.source,
      modelUsed: ans.modelUsed,
      latencyMs: ans.latencyMs,
      timestamp: Date.now(),
    });
    return ans;
  }

  try {
    // Respect local-only memories: never include `only_on_device` items in cloud-bound prompts unless local
    const allowCloud = params.mode !== 'only_on_device';
    const safeContext = allowCloud
      ? topContext.filter((m) => m.privacyMode !== 'only_on_device')
      : topContext;

    const contextPayload = safeContext.map((m) => ({
      id: m.id,
      kind: m.kind,
      title: m.title,
      category: m.category,
      summary: m.summary,
      extractedText: m.extractedText.slice(0, 500),
      tags: m.tags,
      entities: m.entities,
      actionItems: m.actionItems,
      savedDate: new Date(m.createdAt).toISOString().slice(0, 10),
    }));

    const searchPrompt = `You are MEMORA, the user's personal memory assistant.
Answer the user's question accurately using ONLY their saved memories below. Cite specific memory IDs and titles.
Return STRICT JSON ONLY:
{
  "answer": "Helpful, direct, well-structured answer in plain prose or concise bullets",
  "citations": [
    { "memoryId": "exact id from list", "title": "exact title", "excerpt": "short relevant detail" }
  ],
  "suggestedFollowUps": ["2 or 3 natural follow-up questions"]
}

User question: "${params.query}"

Saved memories JSON:
${JSON.stringify(contextPayload)}`;

    const cloudModel =
      params.query.length > 80 || safeContext.length > 10
        ? params.remoteConfig.cloudModelPro
        : params.remoteConfig.cloudModelFast;

    const model = getGenerativeModel(ai, {
      mode: mapMode(params.mode),
      inCloudParams: {
        model: cloudModel,
        generationConfig: {
          temperature: 0.25,
          responseMimeType: 'application/json',
        },
      },
    });

    const result = await model.generateContent(searchPrompt);
    const response = result.response;
    const latencyMs = Math.max(1, Math.round(performance.now() - started));
    const source: InferenceRouteSource =
      response.inferenceSource === InferenceSource.ON_DEVICE ? 'on_device' : 'in_cloud';
    const modelUsed = source === 'on_device' ? 'gemini-nano-4 (on-device)' : cloudModel;

    const parsed = JSON.parse(stripJsonFences(response.text())) as Record<string, unknown>;
    const validIds = new Set(safeContext.map((m) => m.id));
    const citations = Array.isArray(parsed.citations)
      ? parsed.citations
          .map((c) => {
            const obj = c as Record<string, unknown>;
            const memoryId = String(obj.memoryId || '');
            if (!validIds.has(memoryId)) return null;
            return {
              memoryId,
              title: String(obj.title || ''),
              excerpt: String(obj.excerpt || ''),
            };
          })
          .filter((c): c is { memoryId: string; title: string; excerpt: string } => Boolean(c))
      : [];

    await saveBenchmark({
      id: crypto.randomUUID(),
      operation: 'natural_search',
      source,
      modelUsed,
      latencyMs,
      timestamp: Date.now(),
    });

    return {
      query: params.query,
      answer: String(parsed.answer || 'Here is what I found in your saved memories.'),
      citations,
      suggestedFollowUps: safeArray(parsed.suggestedFollowUps).slice(0, 3),
      source,
      modelUsed,
      latencyMs,
    };
  } catch (err) {
    console.warn('[MEMORA] AI search fell back to local index:', err);
    return localFallbackAnswer();
  }
}

export async function synthesizeMemoryThread(params: {
  threadTitle: string;
  memories: MemoryItem[];
  mode: PrivacyRoutingMode;
  remoteConfig: RemoteConfigValues;
}): Promise<ThreadSynthesisResult> {
  const started = performance.now();
  const fallback = (): ThreadSynthesisResult => ({
    summary: `Connected thread spanning ${params.memories.length} memories around "${params.threadTitle}".`,
    keyInsights: params.memories.slice(0, 4).map((m) => `${m.title}: ${m.summary}`),
    nextActions: params.memories.flatMap((m) => m.actionItems).slice(0, 5),
    inferenceSource: 'offline_heuristic',
    modelUsed: 'local-thread-aggregator',
    latencyMs: Math.max(2, Math.round(performance.now() - started)),
  });

  if (!ai || !isFirebaseConfigured || params.memories.length === 0) return fallback();

  try {
    const prompt = `Synthesize this connected MEMORA Memory Thread titled "${params.threadTitle}".
Return STRICT JSON ONLY:
{
  "summary": "2-3 sentence executive synthesis connecting all items together",
  "keyInsights": ["3 to 5 key takeaways or comparisons across the memories"],
  "nextActions": ["3 to 5 concrete recommended next steps"]
}
Memories:
${JSON.stringify(
  params.memories.map((m) => ({
    kind: m.kind,
    title: m.title,
    summary: m.summary,
    extractedText: m.extractedText.slice(0, 400),
    entities: m.entities,
    actionItems: m.actionItems,
  }))
)}`;

    const model = getGenerativeModel(ai, {
      mode: mapMode(params.mode),
      inCloudParams: {
        model: params.remoteConfig.cloudModelPro,
        generationConfig: {
          temperature: 0.3,
          responseMimeType: 'application/json',
        },
      },
    });
    const res = await model.generateContent(prompt);
    const latencyMs = Math.max(1, Math.round(performance.now() - started));
    const source: InferenceRouteSource =
      res.response.inferenceSource === InferenceSource.ON_DEVICE ? 'on_device' : 'in_cloud';
    const modelUsed =
      source === 'on_device' ? 'gemini-nano-4 (on-device)' : params.remoteConfig.cloudModelPro;
    const parsed = JSON.parse(stripJsonFences(res.response.text())) as Record<string, unknown>;

    await saveBenchmark({
      id: crypto.randomUUID(),
      operation: 'thread_synthesis',
      source,
      modelUsed,
      latencyMs,
      timestamp: Date.now(),
    });

    return {
      summary: String(parsed.summary || fallback().summary),
      keyInsights: safeArray(parsed.keyInsights),
      nextActions: safeArray(parsed.nextActions),
      inferenceSource: source,
      modelUsed,
      latencyMs,
    };
  } catch {
    return fallback();
  }
}

/**
 * Spoken Recap via Firebase AI Logic TTS (`gemini-3.1-flash-tts-preview`) when online,
 * gracefully falling back to browser SpeechSynthesis when offline.
 */
export async function playSpokenRecap(
  text: string,
  remoteConfig: RemoteConfigValues = DEFAULT_REMOTE_CONFIG
): Promise<{ source: InferenceRouteSource; audioUrl?: string; stop: () => void }> {
  const started = performance.now();
  const cleanText = text.replace(/[*#`]/g, '').slice(0, 900);

  if (ai && isFirebaseConfigured && navigator.onLine && remoteConfig.enableTtsRecap) {
    try {
      const ttsModel = getGenerativeModel(ai, {
        model: remoteConfig.ttsModel,
        generationConfig: {
          responseModalities: [ResponseModality.AUDIO],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: { voiceName: 'Kore' },
            },
          },
        },
      });
      const result = await ttsModel.generateContent(
        `Read this personal memory briefing calmly, clearly, and naturally:\n\n${cleanText}`
      );
      const inlineParts = result.response.inlineDataParts();
      if (inlineParts && inlineParts.length > 0) {
        const audioPart = inlineParts[0].inlineData;
        const audioBytes = Uint8Array.from(atob(audioPart.data), (c) => c.charCodeAt(0));
        const blob = buildPlayableAudioBlob(audioBytes, audioPart.mimeType);
        const audioUrl = URL.createObjectURL(blob);
        const audio = new Audio(audioUrl);
        await audio.play();
        await saveBenchmark({
          id: crypto.randomUUID(),
          operation: 'spoken_recap',
          source: 'in_cloud',
          modelUsed: remoteConfig.ttsModel,
          latencyMs: Math.round(performance.now() - started),
          timestamp: Date.now(),
        });
        return {
          source: 'in_cloud',
          audioUrl,
          stop: () => {
            audio.pause();
            URL.revokeObjectURL(audioUrl);
          },
        };
      }
    } catch (err) {
      console.warn('[MEMORA] Cloud TTS unavailable, falling back to offline Web Speech:', err);
    }
  }

  // Offline / fallback Web Speech Synthesis
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const utter = new SpeechSynthesisUtterance(cleanText);
    utter.rate = 1.0;
    utter.pitch = 1.0;
    window.speechSynthesis.speak(utter);
    await saveBenchmark({
      id: crypto.randomUUID(),
      operation: 'spoken_recap',
      source: 'on_device',
      modelUsed: 'device-speech-synthesis',
      latencyMs: Math.max(5, Math.round(performance.now() - started)),
      timestamp: Date.now(),
    });
    return {
      source: 'on_device',
      stop: () => window.speechSynthesis.cancel(),
    };
  }

  return { source: 'offline_heuristic', stop: () => {} };
}

function buildPlayableAudioBlob(bytes: Uint8Array, mimeType: string): Blob {
  // Gemini TTS often returns raw PCM (audio/L16;rate=24000). Wrap PCM in a standard WAV header so <audio> plays natively.
  if (mimeType.toLowerCase().includes('l16') || mimeType.toLowerCase().includes('pcm')) {
    const rateMatch = mimeType.match(/rate=(\d+)/i);
    const sampleRate = rateMatch ? Number(rateMatch[1]) : 24000;
    const numChannels = 1;
    const bitsPerSample = 16;
    const dataSize = bytes.byteLength;
    const buffer = new ArrayBuffer(44 + dataSize);
    const view = new DataView(buffer);
    const writeStr = (offset: number, str: string) => {
      for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i));
    };
    writeStr(0, 'RIFF');
    view.setUint32(4, 36 + dataSize, true);
    writeStr(8, 'WAVE');
    writeStr(12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, numChannels, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, (sampleRate * numChannels * bitsPerSample) / 8, true);
    view.setUint16(32, (numChannels * bitsPerSample) / 8, true);
    view.setUint16(34, bitsPerSample, true);
    writeStr(36, 'data');
    view.setUint32(40, dataSize, true);
    new Uint8Array(buffer, 44).set(bytes);
    return new Blob([buffer], { type: 'audio/wav' });
  }
  return new Blob([bytes.buffer as ArrayBuffer], { type: mimeType || 'audio/mpeg' });
}

const BENCHMARK_SAMPLE_PROMPT = `Extract structured memory JSON from this captured flyer notice. Return STRICT JSON ONLY: {"title":"...","category":"...","dueDateIso":"YYYY-MM-DD","amount":"..."}.
Notice: "AI Builders Summit 2026 — Nov 28, 2026 at 10:00 AM, Landmark Centre. Early-bird pass $45 due by Nov 27, 2026."`;

function summarizeBenchmarkPreview(rawText: string): string {
  try {
    const parsed = JSON.parse(stripJsonFences(rawText)) as Record<string, unknown>;
    const parts = [
      parsed.title ? String(parsed.title) : null,
      parsed.dueDateIso ? `Due ${String(parsed.dueDateIso)}` : null,
      parsed.amount ? String(parsed.amount) : null,
    ].filter(Boolean);
    if (parts.length > 0) return parts.join(' · ').slice(0, 90);
  } catch {
    // Fallback to trimmed raw output
  }
  return rawText.replace(/\s+/g, ' ').trim().slice(0, 90);
}

/**
 * Runs the exact same structured flyer-extraction prompt back-to-back through
 * ONLY_ON_DEVICE (Chrome Gemini Nano 4) and ONLY_IN_CLOUD (Cloud Gemini) via
 * Firebase AI Logic, recording genuine measured millisecond latencies.
 */
export async function runSideBySideBenchmark(
  remoteConfig: RemoteConfigValues = DEFAULT_REMOTE_CONFIG
): Promise<SideBySideBenchmarkResult> {
  const promptLabel = 'Structured Event Flyer + Deadline Extraction';

  // 1. On-Device Leg (ONLY_ON_DEVICE)
  let onDevice: SideBySideLegResult;
  if (!ai || !isFirebaseConfigured) {
    onDevice = {
      status: 'unavailable',
      latencyMs: null,
      modelUsed: 'gemini-nano-4 (on-device)',
      detail: 'Firebase AI Logic not initialized in this environment.',
    };
  } else {
    const localStart = performance.now();
    try {
      const localModel = getGenerativeModel(ai, {
        mode: InferenceMode.ONLY_ON_DEVICE,
        inCloudParams: { model: remoteConfig.cloudModelFast },
      });
      const res = await localModel.generateContent(BENCHMARK_SAMPLE_PROMPT);
      const latencyMs = Math.max(1, Math.round(performance.now() - localStart));
      const text = res.response.text();
      await saveBenchmark({
        id: crypto.randomUUID(),
        operation: 'side_by_side_benchmark',
        source: 'on_device',
        modelUsed: 'gemini-nano-4 (on-device)',
        latencyMs,
        timestamp: Date.now(),
      });
      onDevice = {
        status: 'ok',
        latencyMs,
        modelUsed: 'gemini-nano-4 (on-device)',
        detail: '0 KB sent to cloud · $0.00 cost',
        preview: summarizeBenchmarkPreview(text),
      };
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'On-device Gemini Nano unavailable';
      onDevice = {
        status: 'unavailable',
        latencyMs: null,
        modelUsed: 'gemini-nano-4 (on-device)',
        detail: msg.includes('not available') || msg.includes('unavailable') || msg.includes('device')
          ? 'Gemini Nano not ready in this browser — PREFER_ON_DEVICE auto-routes to Cloud.'
          : msg.slice(0, 110),
      };
    }
  }

  // 2. Cloud Leg (ONLY_IN_CLOUD)
  let inCloud: SideBySideLegResult;
  if (!navigator.onLine) {
    inCloud = {
      status: 'offline',
      latencyMs: null,
      modelUsed: remoteConfig.cloudModelFast,
      detail: 'Browser is offline — cloud model unreachable.',
    };
  } else if (!ai || !isFirebaseConfigured) {
    inCloud = {
      status: 'unavailable',
      latencyMs: null,
      modelUsed: remoteConfig.cloudModelFast,
      detail: 'Firebase AI Logic not configured.',
    };
  } else {
    const cloudStart = performance.now();
    try {
      const cloudModel = getGenerativeModel(ai, {
        mode: InferenceMode.ONLY_IN_CLOUD,
        inCloudParams: {
          model: remoteConfig.cloudModelFast,
          generationConfig: {
            temperature: 0.1,
            responseMimeType: 'application/json',
          },
        },
      });
      const res = await cloudModel.generateContent(BENCHMARK_SAMPLE_PROMPT);
      const latencyMs = Math.max(1, Math.round(performance.now() - cloudStart));
      const text = res.response.text();
      await saveBenchmark({
        id: crypto.randomUUID(),
        operation: 'side_by_side_benchmark',
        source: 'in_cloud',
        modelUsed: remoteConfig.cloudModelFast,
        latencyMs,
        timestamp: Date.now(),
      });
      inCloud = {
        status: 'ok',
        latencyMs,
        modelUsed: remoteConfig.cloudModelFast,
        detail: 'Firebase AI Logic · App Check secured',
        preview: summarizeBenchmarkPreview(text),
      };
    } catch (err) {
      inCloud = {
        status: 'error',
        latencyMs: null,
        modelUsed: remoteConfig.cloudModelFast,
        detail: err instanceof Error ? err.message.slice(0, 110) : 'Cloud request failed.',
      };
    }
  }

  // 3. Honest comparison verdict
  let verdict: string;
  if (onDevice.status === 'ok' && inCloud.status === 'ok' && onDevice.latencyMs && inCloud.latencyMs) {
    if (onDevice.latencyMs <= inCloud.latencyMs) {
      const ratio = (inCloud.latencyMs / onDevice.latencyMs).toFixed(1);
      verdict = `On-Device ran ${ratio}× faster (${onDevice.latencyMs}ms vs ${inCloud.latencyMs}ms) · 0 KB uploaded · $0 cloud cost.`;
    } else {
      const ratio = (onDevice.latencyMs / inCloud.latencyMs).toFixed(1);
      verdict = `Cloud ran ${ratio}× faster (${inCloud.latencyMs}ms vs ${onDevice.latencyMs}ms); On-Device stayed 100% private at $0 cost.`;
    }
  } else if (onDevice.status === 'ok' && inCloud.status === 'offline') {
    verdict = `Offline Resilience Verified: On-Device succeeded in ${onDevice.latencyMs}ms while Cloud was offline.`;
  } else if (inCloud.status === 'ok' && onDevice.status !== 'ok') {
    verdict = `Cloud Fallback Verified: ${remoteConfig.cloudModelFast} completed in ${inCloud.latencyMs}ms while local Nano is unavailable here.`;
  } else {
    verdict = 'Both paths unavailable right now (offline without local Gemini Nano — deterministic offline parser remains active).';
  }

  return {
    ranAt: Date.now(),
    promptLabel,
    onDevice,
    inCloud,
    verdict,
  };
}
