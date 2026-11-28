const CACHE_PREFIX = 'dolabim-pwa-';
const CACHE_NAME = `${CACHE_PREFIX}v1.43.284`;
const APP_SHELL_URLS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './css/styles.css',
  './data/tr-places.txt',
  './js/security.js',
  './js/boot-guard.js',
  './js/icons.js',
  './js/constants.js',
  './js/lookbook-data.js',
  './js/winter-theme.js',
  './js/storage.js',
  './js/backup.js',
  './js/price-tracker.js',
  './js/studio-processor.js',
  './js/recommendation-engine.js',
  './js/pwa-install.js',
  './js/cropper-modal.js',
  './js/tryon3d-modal.js',
  './js/analytics.js',
  './js/social.js',
  './js/gif-library.js',
  './js/social-feed.js',
  './js/profile-ui.js',
  './js/feed-ui.js',
  './js/dm-service.js',
  './js/dm-ui.js',
  './js/app.js',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-192.png?v=2',
  './icons/icon-maskable-512.png?v=2',
  './icons/apple-touch-icon.png'
];

const LAZY_DECODER_URLS = [
  './js/vendor/libheif.js',
  './js/vendor/utif.js'
];

const ALLOWED_SHELL_PATHS = new Set(
  [...APP_SHELL_URLS, ...LAZY_DECODER_URLS].map((u) => new URL(u, self.location.href).pathname)
);

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(
      APP_SHELL_URLS.map((url) => new Request(url, { cache: 'reload' }))
    ))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME)
        .map((key) => caches.delete(key)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  let url;
  try {
    url = new URL(req.url);
  } catch {
    return;
  }

  if (url.origin !== self.location.origin || !ALLOWED_SHELL_PATHS.has(url.pathname)) {
    return;
  }

  // Network-first strictly for allowlisted local shell assets
  event.respondWith(
    fetch(req, { cache: 'no-cache' })
      .then((res) => {
        if (res && res.status === 200 && res.type === 'basic' && !res.redirected) {
          const clone = res.clone();
          caches.open(CACHE_NAME).then((c) => c.put(req, clone)).catch(() => {});
        }
        return res;
      })
      .catch(() => caches.match(req))
  );
});

// Page timers and periodic sync share one checkpoint, including across open tabs.
let inboxWork = Promise.resolve();
function queueInbox(event, sessionUpdate) {
  const run = inboxWork.then(() => checkInbox(sessionUpdate));
  inboxWork = run.catch(() => {});
  event.waitUntil(run);
}

self.addEventListener('message', event => {
  if (event.data?.type === 'inbox-session') queueInbox(event, event.data);
});
self.addEventListener('periodicsync', event => {
  if (event.tag === 'social-inbox') queueInbox(event);
});

async function openInboxStore() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open('dolabim_inbox', 1);
    req.onupgradeneeded = () => req.result.createObjectStore('state');
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
function inboxState(db, value) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction('state', value === undefined ? 'readonly' : 'readwrite');
    const store = tx.objectStore('state');
    const req = value === undefined ? store.get('current') : store.put(value, 'current');
    tx.oncomplete = () => resolve(req.result);
    tx.onabort = () => reject(tx.error);
  });
}

async function checkInbox(update) {
  if (self.Notification?.permission !== 'granted') return;
  const db = await openInboxStore();
  try {
    let state = await inboxState(db);
    if (update) {
      const session = update.session;
      if (!session) { await inboxState(db, null); return; }
      if (state?.uid !== session.uid) state = { uid: session.uid, time: Date.now(), next: 0 };
      if (!state.auth || session.expiresAtMs >= state.auth.expiresAtMs) state.auth = session;
      state.muted = update.muted;
      await inboxState(db, state);
    }
    if (!state?.auth || Date.now() < state.next) return;
    const auth = state.auth;
    // Reserve the next attempt before doing I/O, even if the network goes away.
    state.next = Date.now() + 120000;
    await inboxState(db, state);
    if (!auth.idToken || !(Date.now() < Number(auth.expiresAtMs))) {
      const response = await fetch('https://securetoken.googleapis.com/v1/token?key=' + encodeURIComponent(auth.apiKey), {
        method: 'POST', body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: auth.refreshToken }),
        signal: AbortSignal.timeout(12000)
      });
      if (!response.ok) return;
      const token = await response.json();
      if (token.user_id !== state.uid || !token.id_token) return;
      Object.assign(auth, { idToken: token.id_token, refreshToken: token.refresh_token || auth.refreshToken,
        expiresAtMs: Date.now() + (Number(token.expires_in) - 90) * 1000 });
      await inboxState(db, state);
    }
    const fields = ['createdAtMs', 'type', 'fromUid', 'fromUsername', 'fromDisplayName', 'read', 'previewText'];
    const values = [{ integerValue: String(state.time) }];
    if (state.name) values.push({ referenceValue: state.name });
    const response = await fetch('https://firestore.googleapis.com/v1/projects/dolabim-stats/databases/(default)/documents/notifications/' +
      encodeURIComponent(state.uid) + ':runQuery', {
      method: 'POST', headers: { Authorization: 'Bearer ' + auth.idToken, 'Content-Type': 'application/json' },
      signal: AbortSignal.timeout(12000),
      body: JSON.stringify({ structuredQuery: {
        from: [{ collectionId: 'items' }], select: { fields: fields.map(fieldPath => ({ fieldPath })) },
        orderBy: ['createdAtMs', '__name__'].map(fieldPath => ({ field: { fieldPath }, direction: 'ASCENDING' })),
        startAt: { values, before: false }, limit: 32
      } })
    });
    if (response.status === 429) {
      state.next = Date.now() + 60 * 60000;
      await inboxState(db, state);
    }
    if (!response.ok) return;
    for (const { document: doc } of await response.json()) {
      if (!doc) continue;
      const f = doc.fields;
      const type = f.type?.stringValue || '';
      const dm = type.startsWith('dm_');
      const uid = f.fromUid?.stringValue;
      if (!f.read?.booleanValue && !(dm && state.muted?.includes(uid))) {
        const titles = { dm_request: 'Yeni mesaj isteği', dm_message: 'Yeni mesaj', dm_post_reply: 'Gönderine özel yanıt',
          follow_request: 'Yeni takip isteği', follow_accepted: 'Takip isteği onaylandı', new_follower: 'Yeni takipçi',
          post_like: 'Yeni beğeni', post_comment: 'Yeni yorum' };
        await self.registration.showNotification(titles[type] || 'Dolabım', {
          body: [f.fromUsername?.stringValue || f.fromDisplayName?.stringValue, f.previewText?.stringValue].filter(Boolean).join(' · '),
          tag: doc.name, icon: new URL('./icons/icon-192.png', self.registration.scope).href,
          data: { inbox: dm ? 'dm-' + uid : 'activity' }
        });
      }
      const ts = Number(f?.createdAtMs?.integerValue || f?.createdAtMs?.doubleValue || 0);
      if (ts > 0) {
        state.time = ts;
        state.name = doc.name;
      }
    }
    await inboxState(db, state);
  } finally {
    db.close();
  }
}

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const url = new URL('./', self.registration.scope);
  if (event.notification.data?.inbox) url.searchParams.set('inbox', event.notification.data.inbox);
  event.waitUntil((async () => {
    const windows = await clients.matchAll({ type: 'window' });
    const tab = windows.find(client => client.url.startsWith(self.registration.scope));
    if (tab) {
      try {
        const navigated = await tab.navigate(url.href);
        if (navigated) return navigated.focus();
      } catch {}
    }
    return clients.openWindow(url.href);
  })());
});
