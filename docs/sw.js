const CACHE_NAME = 'dolabim-pwa-v1.43.261-r2';
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
  './js/studio-processor.js',
  './js/recommendation-engine.js',
  './js/pwa-install.js',
  './js/cropper-modal.js',
  './js/tryon3d-modal.js',
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
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL_URLS))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
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
    fetch(req)
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
