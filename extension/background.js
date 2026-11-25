const MEMORA_WEB_URL = 'https://memora-hybrid-ai-2026.web.app';

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'memora-save-page',
    title: 'Save page to MEMORA',
    contexts: ['page'],
  });
  chrome.contextMenus.create({
    id: 'memora-save-selection',
    title: 'Save highlighted text to MEMORA',
    contexts: ['selection'],
  });
  chrome.contextMenus.create({
    id: 'memora-save-link',
    title: 'Save link to MEMORA',
    contexts: ['link'],
  });
});

chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  const url = info.linkUrl || info.pageUrl || tab?.url || '';
  const text = info.selectionText || tab?.title || '';
  const payload = {
    url,
    text,
    savedAt: Date.now(),
    source: 'chrome-extension',
  };

  // Store locally in chrome.storage.local for offline resilience
  const { pendingCaptures = [] } = await chrome.storage.local.get('pendingCaptures');
  pendingCaptures.unshift(payload);
  await chrome.storage.local.set({ pendingCaptures: pendingCaptures.slice(0, 50) });

  const target = new URL(MEMORA_WEB_URL);
  target.searchParams.set('capture', '1');
  if (url) target.searchParams.set('url', url);
  if (text) target.searchParams.set('text', text.slice(0, 1500));
  chrome.tabs.create({ url: target.toString() });
});
