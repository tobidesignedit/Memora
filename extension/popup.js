const MEMORA_WEB_URL = 'https://memora-hybrid-ai-2026.web.app';

const urlInput = document.getElementById('page-url');
const noteInput = document.getElementById('page-note');
const statusEl = document.getElementById('status');
const recentWrap = document.getElementById('recent-wrap');
const recentList = document.getElementById('recent-list');

async function hydrateCurrentTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (tab?.url) urlInput.value = tab.url;

  let selection = '';
  if (tab?.id) {
    try {
      const [{ result }] = await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        func: () => window.getSelection()?.toString() || '',
      });
      selection = result || '';
    } catch {
      // Restricted pages like chrome:// cannot be scripted
    }
  }
  noteInput.value = selection
    ? `${tab?.title || ''}\n\n"${selection.trim()}"`
    : tab?.title || '';

  await renderRecent();
}

async function renderRecent() {
  const { pendingCaptures = [] } = await chrome.storage.local.get('pendingCaptures');
  if (!pendingCaptures.length) {
    recentWrap.style.display = 'none';
    return;
  }
  recentWrap.style.display = 'block';
  recentList.innerHTML = '';
  pendingCaptures.slice(0, 4).forEach((item) => {
    const div = document.createElement('div');
    div.className = 'recent-item';
    div.textContent = item.text || item.url;
    recentList.appendChild(div);
  });
}

async function saveCapture(openWeb) {
  const url = urlInput.value.trim();
  const text = noteInput.value.trim();
  if (!url && !text) return;

  const { pendingCaptures = [] } = await chrome.storage.local.get('pendingCaptures');
  pendingCaptures.unshift({ url, text, savedAt: Date.now(), source: 'chrome-extension' });
  await chrome.storage.local.set({ pendingCaptures: pendingCaptures.slice(0, 50) });

  if (openWeb) {
    const target = new URL(MEMORA_WEB_URL);
    target.searchParams.set('capture', '1');
    if (url) target.searchParams.set('url', url);
    if (text) target.searchParams.set('text', text.slice(0, 1500));
    chrome.tabs.create({ url: target.toString() });
    window.close();
  } else {
    statusEl.textContent = 'Queued safely in local browser storage.';
    await renderRecent();
  }
}

document.getElementById('save-open').addEventListener('click', () => saveCapture(true));
document.getElementById('save-offline').addEventListener('click', () => saveCapture(false));

hydrateCurrentTab();
