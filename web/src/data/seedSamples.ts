import type { MemoryItem, ReminderItem } from '../types/memora';

function addDaysIso(days: number): string {
  const d = new Date(Date.now() + days * 86_400_000);
  return d.toISOString().slice(0, 10);
}

function formatPrettyDate(iso: string): string {
  const d = new Date(`${iso}T12:00:00`);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }).toUpperCase();
}

/**
 * High-craft bespoke SVG artifacts with distinct aspect ratios so the
 * masonry gallery showcases authentic print flyers, timetables, and invoices.
 */
function makeTimetableSheet(examDate: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="420" viewBox="0 0 640 420">
    <rect width="640" height="420" fill="#F4F0E8"/>
    <rect x="28" y="28" width="584" height="364" rx="14" fill="#FFFFFF" stroke="#E2DDD2" stroke-width="1.5"/>
    <rect x="28" y="28" width="584" height="68" rx="14" fill="#161513"/>
    <rect x="28" y="76" width="584" height="20" fill="#161513"/>
    <text x="54" y="69" font-family="Georgia, serif" font-size="22" font-weight="600" fill="#F5F3EE">Final Examination Schedule</text>
    <text x="586" y="68" text-anchor="end" font-family="monospace" font-size="13" fill="#E66A3E">FALL 2026</text>

    <text x="54" y="132" font-family="monospace" font-size="11" fill="#827C73">COURSE</text>
    <text x="290" y="132" font-family="monospace" font-size="11" fill="#827C73">DATE &amp; TIME</text>
    <text x="480" y="132" font-family="monospace" font-size="11" fill="#827C73">HALL</text>
    <line x1="54" y1="146" x2="586" y2="146" stroke="#E5E1D8" stroke-width="1"/>

    <rect x="44" y="158" width="552" height="58" rx="10" fill="#FFF4EF" stroke="#C85028" stroke-opacity="0.32" stroke-width="1"/>
    <text x="58" y="183" font-family="sans-serif" font-size="15" font-weight="700" fill="#161513">CSC 412 · Distributed Systems</text>
    <text x="58" y="203" font-family="sans-serif" font-size="12" fill="#C85028">Bring Student ID &amp; Calculator</text>
    <text x="290" y="185" font-family="monospace" font-size="13" font-weight="600" fill="#161513">${examDate}</text>
    <text x="290" y="203" font-family="monospace" font-size="12" fill="#57534C">09:00 AM</text>
    <text x="480" y="191" font-family="sans-serif" font-size="13" font-weight="600" fill="#161513">Eng. Hall B</text>

    <text x="58" y="254" font-family="sans-serif" font-size="14" font-weight="600" fill="#57534C">CSC 408 · Compiler Design</text>
    <text x="290" y="254" font-family="monospace" font-size="13" fill="#827C73">${addDaysIso(6)} · 02:00 PM</text>
    <text x="480" y="254" font-family="sans-serif" font-size="13" fill="#827C73">Science Aud.</text>
    <line x1="54" y1="274" x2="586" y2="274" stroke="#EFECE4" stroke-width="1"/>

    <text x="58" y="308" font-family="sans-serif" font-size="14" font-weight="600" fill="#57534C">MTH 401 · Optimization</text>
    <text x="290" y="308" font-family="monospace" font-size="13" fill="#827C73">${addDaysIso(9)} · 10:00 AM</text>
    <text x="480" y="308" font-family="sans-serif" font-size="13" fill="#827C73">Hall A</text>

    <text x="54" y="366" font-family="monospace" font-size="11" fill="#827C73">DEPT. OF COMPUTER SCIENCE · VERIFIED TIMETABLE</text>
  </svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function makeSwissMeetupPoster(summitDate: string): string {
  const pretty = formatPrettyDate(summitDate);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="520" height="620" viewBox="0 0 520 620">
    <rect width="520" height="620" fill="#17231D"/>
    <circle cx="420" cy="110" r="145" fill="#C85028" fill-opacity="0.88"/>
    <circle cx="110" cy="480" r="170" fill="#2A4A3A" fill-opacity="0.72"/>
    <line x1="42" y1="54" x2="478" y2="54" stroke="#F5F3EE" stroke-opacity="0.25" stroke-width="1"/>
    <text x="42" y="42" font-family="monospace" font-size="12" letter-spacing="1.5" fill="#F5F3EE" fill-opacity="0.82">COMMUNITY SYMPOSIUM</text>
    <text x="478" y="42" text-anchor="end" font-family="monospace" font-size="12" fill="#F5F3EE">${pretty}</text>

    <text x="42" y="218" font-family="Georgia, serif" font-size="56" font-weight="600" fill="#F5F3EE">Hybrid AI</text>
    <text x="42" y="280" font-family="Georgia, serif" font-size="56" font-style="italic" fill="#F5F3EE">Builders</text>
    <text x="42" y="340" font-family="Georgia, serif" font-size="56" font-weight="600" fill="#F5F3EE">Meetup</text>

    <line x1="42" y1="392" x2="478" y2="392" stroke="#F5F3EE" stroke-opacity="0.24" stroke-width="1"/>
    <text x="42" y="434" font-family="sans-serif" font-size="17" font-weight="600" fill="#F5F3EE">Live Demos · Gemini Nano 4 + Cloud</text>
    <text x="42" y="464" font-family="sans-serif" font-size="15" fill="#D5D2CA">On-device inference, privacy-first mobile UX, and zero-latency search.</text>

    <rect x="42" y="506" width="436" height="72" rx="14" fill="#F5F3EE" fill-opacity="0.1" stroke="#F5F3EE" stroke-opacity="0.2"/>
    <text x="64" y="537" font-family="monospace" font-size="14" font-weight="600" fill="#F5F3EE">${summitDate} · 06:00 PM</text>
    <text x="64" y="560" font-family="sans-serif" font-size="13" fill="#D5D2CA">Civic Innovation Hub · Main Atrium · Free RSVP</text>
  </svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function makeInvoiceSlip(billDate: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="520" height="440" viewBox="0 0 520 440">
    <rect width="520" height="440" fill="#ECE7DC"/>
    <rect x="44" y="32" width="432" height="376" rx="12" fill="#FFFDF9" stroke="#DDD6C7" stroke-width="1.5"/>
    <text x="72" y="76" font-family="sans-serif" font-size="15" font-weight="700" fill="#161513">METROFIBER ISP</text>
    <text x="448" y="76" text-anchor="end" font-family="monospace" font-size="12" fill="#827C73">#INV-88412</text>
    <line x1="72" y1="96" x2="448" y2="96" stroke="#E5DFD2" stroke-width="1" stroke-dasharray="5 5"/>

    <text x="72" y="138" font-family="monospace" font-size="11" fill="#827C73">TOTAL AMOUNT DUE</text>
    <text x="72" y="190" font-family="Georgia, serif" font-size="52" font-weight="600" fill="#161513">$58.00</text>

    <rect x="72" y="216" width="376" height="48" rx="8" fill="#FDF3E7"/>
    <text x="90" y="245" font-family="monospace" font-size="13" font-weight="600" fill="#B4690E">PAYMENT DEADLINE: ${billDate} · 17:00</text>

    <text x="72" y="302" font-family="sans-serif" font-size="13" fill="#57534C">Plan</text>
    <text x="448" y="302" text-anchor="end" font-family="sans-serif" font-size="13" font-weight="600" fill="#161513">1 Gbps Symmetric Fiber</text>
    <text x="72" y="330" font-family="sans-serif" font-size="13" fill="#57534C">Grace Period</text>
    <text x="448" y="330" text-anchor="end" font-family="sans-serif" font-size="13" fill="#57534C">Auto-pause after due date</text>
    <line x1="72" y1="354" x2="448" y2="354" stroke="#E5DFD2" stroke-width="1" stroke-dasharray="5 5"/>
    <text x="72" y="382" font-family="monospace" font-size="11" fill="#1C7C54">STORED STRICTLY IN LOCAL VAULT</text>
  </svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

export function buildDemoBundle(uid: string): {
  memories: MemoryItem[];
  reminders: ReminderItem[];
} {
  const now = Date.now();
  const examDate = addDaysIso(3);
  const summitDate = addDaysIso(5);
  const billDate = addDaysIso(2);

  const examThumb = makeTimetableSheet(examDate);
  const summitThumb = makeSwissMeetupPoster(summitDate);
  const receiptThumb = makeInvoiceSlip(billDate);

  const memories: MemoryItem[] = [
    {
      id: 'demo-builders-flyer',
      uid,
      kind: 'flyer',
      title: 'Hybrid AI Builders Meetup',
      summary: `Community symposium on on-device Gemini Nano 4 + cloud synthesis happening ${summitDate} at 6:00 PM in Civic Innovation Hub.`,
      rawText: `Hybrid AI Builders Meetup — ${summitDate} at 18:00, Civic Innovation Hub. Live demos of Gemini Nano 4 and Firebase AI Logic.`,
      extractedText: `EVENT FLYER | Hybrid AI Builders Meetup | Date: ${summitDate} | Time: 06:00 PM | Venue: Civic Innovation Hub | Free RSVP`,
      mediaThumbnail: summitThumb,
      mediaMimeType: 'image/svg+xml',
      tags: ['event', 'ai', 'meetup', 'firebase'],
      category: 'Events',
      entities: {
        people: [],
        organizations: ['Civic Innovation Hub'],
        dates: [summitDate],
        amounts: [],
        locations: ['Civic Innovation Hub'],
        contacts: [],
      },
      actionItems: ['RSVP for Hybrid AI Builders Meetup', 'Prepare 2-minute live MEMORA demo'],
      threadTopic: 'MEMORA Launch',
      importance: 'normal',
      language: 'en',
      inferenceSource: 'in_cloud',
      inferenceModel: 'gemini-3.5-flash-lite',
      inferenceLatencyMs: 640,
      privacyMode: 'prefer_on_device',
      syncStatus: 'synced',
      pinned: false,
      favorite: true,
      archived: false,
      createdAt: now - 3_600_000 * 1.5,
      updatedAt: now - 3_600_000 * 1.5,
    },
    {
      id: 'demo-exam-timetable',
      uid,
      kind: 'timetable',
      title: 'CSC 412 Distributed Systems Final',
      summary: `Scheduled for ${examDate} at 09:00 AM in Engineering Hall B. Remember to bring student ID and scientific calculator.`,
      rawText: `CSC 412 Distributed Systems Final Exam — ${examDate} at 09:00 AM, Engineering Hall B. Bring student ID and scientific calculator.`,
      extractedText: `CSC 412 Distributed Systems Final Exam | Date: ${examDate} | Time: 09:00 AM | Venue: Engineering Hall B | Requirements: Student ID`,
      mediaThumbnail: examThumb,
      mediaMimeType: 'image/svg+xml',
      tags: ['exam', 'university', 'distributed-systems'],
      category: 'Study & Exams',
      entities: {
        people: [],
        organizations: ['Computer Science Dept'],
        dates: [examDate],
        amounts: [],
        locations: ['Engineering Hall B'],
        contacts: [],
      },
      actionItems: ['Review Raft consensus & CAP theorem notes', 'Pack student ID the night before'],
      threadTopic: 'Semester Finals',
      importance: 'high',
      language: 'en',
      inferenceSource: 'on_device',
      inferenceModel: 'gemini-nano-4',
      inferenceLatencyMs: 214,
      privacyMode: 'prefer_on_device',
      syncStatus: 'synced',
      pinned: true,
      favorite: true,
      archived: false,
      createdAt: now - 3_600_000 * 2.5,
      updatedAt: now - 3_600_000 * 2.5,
    },
    {
      id: 'demo-study-voice',
      uid,
      kind: 'voice',
      title: 'Distributed Systems Revision Plan',
      summary:
        'Focus heavily on Raft leader election, Paxos differences, and vector clocks — meet Tobi at the library tomorrow at 4 PM for 2024 past questions.',
      rawText:
        'Quick voice memo for CSC 412 finals: focus heavily on Raft leader election, Paxos differences, and vector clocks. Meet Tobi at the library tomorrow at 4pm to solve 2024 past questions.',
      extractedText:
        'Quick voice memo for CSC 412 finals: focus heavily on Raft leader election, Paxos differences, and vector clocks. Meet Tobi at the library tomorrow at 4pm to solve 2024 past questions.',
      tags: ['voice', 'study', 'revision'],
      category: 'Study & Exams',
      entities: {
        people: ['Tobi'],
        organizations: [],
        dates: [addDaysIso(1)],
        amounts: [],
        locations: ['Main Library'],
        contacts: [],
      },
      actionItems: ['Solve 2024 CSC 412 past questions with Tobi at 4pm'],
      threadTopic: 'Semester Finals',
      importance: 'normal',
      language: 'en',
      inferenceSource: 'on_device',
      inferenceModel: 'gemini-nano-4',
      inferenceLatencyMs: 186,
      privacyMode: 'prefer_on_device',
      syncStatus: 'synced',
      pinned: false,
      favorite: false,
      archived: false,
      createdAt: now - 3_600_000 * 4,
      updatedAt: now - 3_600_000 * 4,
    },
    {
      id: 'demo-payment-notice',
      uid,
      kind: 'receipt',
      title: 'MetroFiber Internet Renewal',
      summary: `Invoice #INV-88412 for $58.00 due by ${billDate}. Stored privately in Local Vault so financial bills never leave your device.`,
      rawText: `Fiber Internet Renewal Notice. Invoice #INV-88412. Amount Due: $58.00. Payment Deadline: ${billDate}.`,
      extractedText: `PAYMENT NOTICE | Fiber Internet Renewal | Amount Due: $58.00 | Due Date: ${billDate} | Reference: INV-88412`,
      mediaThumbnail: receiptThumb,
      mediaMimeType: 'image/svg+xml',
      tags: ['receipt', 'payment', 'bill'],
      category: 'Finance',
      entities: {
        people: [],
        organizations: ['MetroFiber ISP'],
        dates: [billDate],
        amounts: ['$58.00'],
        locations: [],
        contacts: [],
      },
      actionItems: [`Pay $58.00 invoice #INV-88412 before ${billDate}`],
      threadTopic: 'Monthly Bills',
      importance: 'high',
      language: 'en',
      inferenceSource: 'on_device',
      inferenceModel: 'gemini-nano-4',
      inferenceLatencyMs: 198,
      privacyMode: 'only_on_device',
      syncStatus: 'local_only',
      pinned: false,
      favorite: false,
      archived: false,
      createdAt: now - 3_600_000 * 6,
      updatedAt: now - 3_600_000 * 6,
    },
    {
      id: 'demo-design-note',
      uid,
      kind: 'note',
      title: 'Why the best tools disappear',
      summary:
        'A personal archive should never ask you to pick a folder at 11 PM. Capture first, understand quietly on-device, and surface the right artifact at the exact moment it matters.',
      rawText:
        'A personal archive should never ask you to pick a folder at 11 PM. Capture first, understand quietly on-device, and surface the right artifact at the exact moment it matters.',
      extractedText:
        'A personal archive should never ask you to pick a folder at 11 PM. Capture first, understand quietly on-device, and surface the right artifact at the exact moment it matters.',
      tags: ['product', 'philosophy', 'design'],
      category: 'Notes',
      entities: {
        people: [],
        organizations: [],
        dates: [],
        amounts: [],
        locations: [],
        contacts: [],
      },
      actionItems: [],
      threadTopic: 'MEMORA Launch',
      importance: 'normal',
      language: 'en',
      inferenceSource: 'on_device',
      inferenceModel: 'gemini-nano-4',
      inferenceLatencyMs: 142,
      privacyMode: 'prefer_on_device',
      syncStatus: 'synced',
      pinned: false,
      favorite: false,
      archived: false,
      createdAt: now - 3_600_000 * 10,
      updatedAt: now - 3_600_000 * 10,
    },
    {
      id: 'demo-launch-link',
      uid,
      kind: 'link',
      title: 'Firebase AI Logic Hybrid Inference Guide',
      summary:
        'Official reference for PREFER_ON_DEVICE, ONLY_ON_DEVICE, App Check attestation, and seamless Gemini Nano 4 to Cloud fallback across Android and Chrome.',
      rawText:
        'Saved reference for MEMORA launch architecture: Firebase AI Logic hybrid inference across Android AICore and Chrome Built-in AI.',
      extractedText:
        'https://firebase.google.com/docs/ai-logic/hybrid — Hybrid inference runs Gemini Nano locally when available and falls back cleanly to cloud Gemini.',
      url: 'https://firebase.google.com/docs/ai-logic/hybrid',
      tags: ['firebase', 'hybrid-ai', 'architecture'],
      category: 'Reading & Links',
      entities: {
        people: [],
        organizations: ['Google Firebase'],
        dates: [],
        amounts: [],
        locations: [],
        contacts: [],
      },
      actionItems: ['Verify App Check debug tokens before demo recording'],
      threadTopic: 'MEMORA Launch',
      importance: 'normal',
      language: 'en',
      inferenceSource: 'in_cloud',
      inferenceModel: 'gemini-3.5-flash-lite',
      inferenceLatencyMs: 520,
      privacyMode: 'prefer_on_device',
      syncStatus: 'synced',
      pinned: false,
      favorite: false,
      archived: false,
      createdAt: now - 3_600_000 * 16,
      updatedAt: now - 3_600_000 * 16,
    },
  ];

  const reminders: ReminderItem[] = [
    {
      id: 'rem-demo-payment',
      uid,
      memoryId: 'demo-payment-notice',
      memoryTitle: 'MetroFiber Internet Renewal',
      memoryThumbnail: receiptThumb,
      title: 'Pay MetroFiber Invoice ($58.00)',
      dueDateIso: billDate,
      dueTime: '17:00',
      organizer: 'MetroFiber ISP',
      category: 'payment',
      priority: 'high',
      status: 'suggested',
      recurrence: 'none',
      notes: 'Detected from payment slip (Ref #INV-88412).',
      createdAt: now - 3_600_000 * 6,
      updatedAt: now - 3_600_000 * 6,
    },
    {
      id: 'rem-demo-exam',
      uid,
      memoryId: 'demo-exam-timetable',
      memoryTitle: 'CSC 412 Distributed Systems Final',
      memoryThumbnail: examThumb,
      title: 'CSC 412 Distributed Systems Exam',
      dueDateIso: examDate,
      dueTime: '09:00',
      venue: 'Engineering Hall B',
      organizer: 'Computer Science Dept',
      category: 'exam',
      priority: 'high',
      status: 'confirmed',
      recurrence: 'none',
      notes: 'Bring Student ID and scientific calculator.',
      createdAt: now - 3_600_000 * 2.5,
      updatedAt: now - 3_600_000 * 2.5,
    },
    {
      id: 'rem-demo-meetup',
      uid,
      memoryId: 'demo-builders-flyer',
      memoryTitle: 'Hybrid AI Builders Meetup',
      memoryThumbnail: summitThumb,
      title: 'Hybrid AI Builders Meetup',
      dueDateIso: summitDate,
      dueTime: '18:00',
      venue: 'Civic Innovation Hub',
      organizer: 'Civic Innovation Hub',
      category: 'event',
      priority: 'medium',
      status: 'suggested',
      recurrence: 'none',
      notes: 'Detected automatically from saved event poster.',
      createdAt: now - 3_600_000 * 1.5,
      updatedAt: now - 3_600_000 * 1.5,
    },
  ];

  return { memories, reminders };
}
