const CACHE_PREFIX = 'dolabim-pwa-';
const CACHE_NAME = `${CACHE_PREFIX}v1.43.279`;
const APP_SHELL_URLS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './css/styles.css',
  './js/security.js',
  './js/boot-guard.js',
  './js/icons.js',
  './js/constants.js',
  './js/lookbook-data.js',
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
          caches.open(CACHE_NAME).then((c) => c.put(req, clone));
        }
        return res;
      })
      .catch(() => caches.match(req))
  );
});
