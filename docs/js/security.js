/**
 * Central defense-in-depth cybersecurity & cryptography helpers for Dolabim Web/PWA.
 * Protects against XSS, CSS injection, prototype pollution, SSRF, unsafe redirects,
 * polyglot/oversized uploads, SVG script execution, clickjacking, and plaintext secret storage.
 */

export const ALLOWED_LOOKBOOK_HOSTS = new Set([
  'images.unsplash.com',
  'images.pexels.com'
]);

export const ALLOWED_HF_SPACE_HOSTS = new Set([
  'black-forest-labs-flux-2-dev.hf.space',
  'black-forest-labs-flux-2-klein-9b.hf.space',
  'qwen-qwen-image-edit-2511.hf.space',
  'fireredteam-firered-image-edit-1-1.hf.space',
  'black-forest-labs-flux-1-kontext-dev.hf.space'
]);

export function escHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/`/g, '&#96;');
}

export function sanitizeHexColor(val, fallback = '#8E908C') {
  if (typeof val !== 'string') return fallback;
  const trimmed = val.trim();
  if (/^#[0-9a-fA-F]{6}$/.test(trimmed)) return trimmed.toUpperCase();
  const m3 = /^#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])$/.exec(trimmed);
  if (m3) return `#${m3[1]}${m3[1]}${m3[2]}${m3[2]}${m3[3]}${m3[3]}`.toUpperCase();
  return fallback;
}

export function escapeCssAttr(val) {
  const s = String(val ?? '');
  if (typeof window !== 'undefined' && window.CSS?.escape) {
    return window.CSS.escape(s);
  }
  return s.replace(/[^A-Za-z0-9_-]/g, '');
}

function isDisallowedHostLiteral(rawHost) {
  const host = String(rawHost || '').trim().toLowerCase().replace(/\.$/, '');
  if (!host) return true;
  if (
    host === 'localhost' ||
    host.endsWith('.localhost') ||
    host.endsWith('.local') ||
    host.endsWith('.internal') ||
    host.endsWith('.home.arpa') ||
    host.endsWith('.arpa')
  ) {
    return true;
  }

  // IPv6 literal checks (URL.hostname preserves brackets "[::1]")
  const cleanV6 = host.replace(/^\[/, '').replace(/\]$/, '');
  if (cleanV6.includes(':')) {
    if (
      cleanV6 === '::' ||
      cleanV6 === '::1' ||
      /^0+:/.test(cleanV6) ||
      cleanV6.startsWith('::ffff:') ||
      /^0*:0*:0*:0*:0*:ffff:/i.test(cleanV6) ||
      /^0*64:0*ff9b:(?:0*1)?:/i.test(cleanV6) ||
      /^0*100:(?:(?:0*:){0,2}:|0+:0+:0+:)/i.test(cleanV6) ||
      /^2001:(?:0*[:]|0*2:|0*db8:)/i.test(cleanV6) ||
      /^2002:/i.test(cleanV6) ||
      /^fe[89abcdef][0-9a-f]?:/i.test(cleanV6) ||
      /^f[cd][0-9a-f]{0,2}:/i.test(cleanV6) ||
      /^ff[0-9a-f]{0,2}:/i.test(cleanV6)
    ) {
      return true;
    }
    return false;
  }

  // Reject single-label intranet hostnames and decimal/hex/octal obfuscated IP integers
  if (!host.includes('.')) return true;
  if (/^(?:0x[0-9a-f]+|\d+)$/i.test(host)) return true;

  // Reject octal/hex dotted IPv4 bypass notation (e.g. 0177.0.0.1 or 0x7f.0.0.1)
  const parts = host.split('.');
  if (parts.length <= 4 && parts.every((p) => /^(?:0x[0-9a-f]+|0[0-7]+|\d+)$/i.test(p))) {
    if (parts.some((p) => p.startsWith('0x') || (p.length > 1 && p.startsWith('0')))) {
      return true;
    }
    if (parts.length === 4) {
      const nums = parts.map((p) => Number(p));
      if (nums.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) return true;
      const [b0, b1] = nums;
      if (b0 === 0 || b0 === 10 || b0 === 127) return true;
      if (b0 === 169 && b1 === 254) return true;
      if (b0 === 172 && b1 >= 16 && b1 <= 31) return true;
      if (b0 === 192 && b1 === 168) return true;
      if (b0 === 100 && b1 >= 64 && b1 <= 127) return true;
      if (b0 >= 224) return true;
    }
  }

  return false;
}

export function isSafePublicHttpsUrl(raw, maxLen = 1000) {
  if (!raw || typeof raw !== 'string') return false;
  const trimmed = raw.trim();
  if (trimmed.length === 0 || trimmed.length > maxLen) return false;
  // Reject whitespace or ASCII control chars anywhere in URL
  if (/[\s\x00-\x1F\x7F]/.test(trimmed)) return false;

  let u;
  try {
    u = new URL(trimmed);
  } catch {
    return false;
  }

  if (u.protocol !== 'https:') return false;
  if (u.username || u.password) return false;
  if (u.port !== '' && u.port !== '443') return false;
  if (isDisallowedHostLiteral(u.hostname)) return false;
  return true;
}

export function isSafeImageDataUrl(url, maxLen = 25 * 1024 * 1024) {
  if (typeof url !== 'string' || url.length === 0 || url.length > maxLen) return false;
  return /^data:image\/(?:jpeg|jpg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(url);
}

export function safeImgSrc(url) {
  return isSafeImageDataUrl(url) ? url : '';
}

export function safeLookbookImgSrc(url) {
  if (!isSafePublicHttpsUrl(url, 1000)) return '';
  try {
    const host = new URL(url.trim()).hostname.toLowerCase().replace(/\.$/, '');
    if (!ALLOWED_LOOKBOOK_HOSTS.has(host)) return '';
    return escHtml(url.trim());
  } catch {
    return '';
  }
}

const DISALLOWED_UPLOAD_MIMES = new Set([
  'text/html',
  'application/xhtml+xml',
  'text/javascript',
  'application/javascript',
  'application/x-javascript',
  'application/x-msdownload',
  'application/x-sh'
]);

export async function validateUploadedImageFile(file) {
  if (!file || typeof file.size !== 'number' || typeof file.slice !== 'function') {
    throw new Error('INVALID_IMAGE_FILE');
  }
  if (file.size <= 0) {
    throw new Error('INVALID_IMAGE_FILE');
  }

  const mime = String(file.type || '').trim().toLowerCase();
  if (mime && DISALLOWED_UPLOAD_MIMES.has(mime)) {
    throw new Error('INVALID_IMAGE_FILE');
  }

  return true;
}

export function assertSafeImageDimensions(img) {
  const w = Number(img?.naturalWidth || img?.width || 0);
  const h = Number(img?.naturalHeight || img?.height || 0);
  if (!Number.isFinite(w) || !Number.isFinite(h) || w <= 0 || h <= 0) {
    throw new Error('INVALID_IMAGE_FILE');
  }
  return true;
}

export function safeJsonParse(raw) {
  if (typeof raw !== 'string' || !raw) return null;
  return JSON.parse(raw, (key, value) => {
    if (key === '__proto__' || key === 'constructor' || key === 'prototype') {
      return undefined;
    }
    return value;
  });
}

export function isSafeId(val, maxLen = 96) {
  if (typeof val !== 'string' || val.length === 0 || val.length > maxLen) return false;
  return /^[A-Za-z0-9_-]+$/.test(val);
}

export function clampText(val, maxLen = 200, allowNewlines = false) {
  if (typeof val !== 'string') return '';
  const stripped = allowNewlines
    ? val.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '')
    : val.replace(/[\x00-\x1F\x7F\r\n]+/g, ' ');
  return stripped.trim().slice(0, maxLen);
}

export function sanitizeAppState(raw, enums) {
  const fallback = {
    items: [],
    outfits: [],
    themeMode: 'MODERN',
    language: 'TR',
    genderPref: 'UNISEX',
    genderUserSet: false,
    selectedCityId: 'ISTANBUL',
    customLocationLabel: null,
    customLocationLat: null,
    customLocationLon: null,
    likedLookIds: [],
    dislikedLookIds: []
  };

  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return fallback;
  }

  const validThemes = enums?.validThemes || new Set(['MODERN', 'DARK', 'LIGHT', 'HIGH_CONTRAST']);
  const validLanguages = enums?.validLanguages || new Set(['TR', 'EN', 'DE', 'FR', 'ES', 'ZH']);
  const validGenders = enums?.validGenders || new Set(['UNISEX', 'MALE', 'FEMALE']);
  const validCities = enums?.validCities || new Set(['ISTANBUL']);
  const validCategories = enums?.validCategories || new Set(['TOP', 'BOTTOM', 'OUTER', 'SHOES', 'ACCESSORY', 'FULL']);
  const validSubCategories = enums?.validSubCategories || new Set(['TSHIRT']);
  const validStyles = enums?.validStyles || new Set(['CASUAL']);
  const validSeasons = enums?.validSeasons || new Set(['ALL', 'SPRING', 'SUMMER', 'AUTUMN', 'WINTER']);
  const validColors = enums?.validColors || new Set();

  const itemsIn = Array.isArray(raw.items) ? raw.items.slice(0, 1000) : [];
  const cleanItems = [];
  for (const it of itemsIn) {
    if (!it || typeof it !== 'object' || Array.isArray(it)) continue;
    const id = isSafeId(it.id) ? it.id : null;
    if (!id) continue;

    const category = validCategories.has(it.category) ? it.category : 'TOP';
    const subCategory = validSubCategories.has(it.subCategory) ? it.subCategory : 'TSHIRT';
    const styleTag = validStyles.has(it.styleTag) ? it.styleTag : 'CASUAL';
    const seasons = Array.isArray(it.seasons)
      ? [...new Set(it.seasons.filter((s) => validSeasons.has(s)))].slice(0, 5)
      : ['ALL'];
    const colors = Array.isArray(it.colors)
      ? [...new Set(it.colors.filter((c) => validColors.size === 0 ? isSafeId(c, 40) : validColors.has(c)))].slice(0, 8)
      : [];
    const extractedHexes = Array.isArray(it.extractedHexes)
      ? it.extractedHexes
          .filter((hx) => typeof hx === 'string' && /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(hx.trim()))
          .map((hx) => sanitizeHexColor(hx))
          .slice(0, 5)
      : [];
    const photoFileNames = Array.isArray(it.photoFileNames)
      ? it.photoFileNames.filter((p) => isSafeId(p)).slice(0, 10)
      : [];
    const originalPhotoFileNames = Array.isArray(it.originalPhotoFileNames)
      ? it.originalPhotoFileNames.filter((p) => isSafeId(p)).slice(0, 10)
      : [];
    const catalogPhotoFileNames = Array.isArray(it.catalogPhotoFileNames)
      ? it.catalogPhotoFileNames.slice(0, 10).map((p) => (p === '' ? '' : (isSafeId(p) ? p : '')))
      : [];

    const rawUrl = typeof it.productUrl === 'string' ? it.productUrl.trim() : '';
    const productUrl = rawUrl && isSafePublicHttpsUrl(rawUrl, 1000) ? rawUrl : '';
    const wearCount = Number.isFinite(Number(it.wearCount))
      ? Math.max(0, Math.min(99999, Math.trunc(Number(it.wearCount))))
      : 0;
    const lastCheckedPrice = Number.isFinite(Number(it.lastCheckedPrice))
      ? Math.max(0, Math.min(1_000_000_000, Number(it.lastCheckedPrice)))
      : 0;
    const previousPrice = Number.isFinite(Number(it.previousPrice))
      ? Math.max(0, Math.min(1_000_000_000, Number(it.previousPrice)))
      : 0;
    const lastWornAt = Number.isFinite(Number(it.lastWornAt)) ? Math.max(0, Number(it.lastWornAt)) : 0;
    const createdAt = Number.isFinite(Number(it.createdAt)) && Number(it.createdAt) > 0
      ? Number(it.createdAt)
      : Date.now();

    cleanItems.push({
      id,
      name: clampText(it.name, 120),
      category,
      subCategory,
      styleTag,
      seasons: seasons.length > 0 ? seasons : ['ALL'],
      colors,
      extractedHexes,
      photoFileNames,
      originalPhotoFileNames,
      useOriginalPhotos: Boolean(it.useOriginalPhotos),
      catalogPhotoFileNames,
      useCatalogPhotos: Boolean(it.useCatalogPhotos),
      notes: clampText(it.notes, 600, true),
      isFavorite: Boolean(it.isFavorite),
      isWishlist: Boolean(it.isWishlist),
      priceText: Boolean(it.isWishlist) ? clampText(it.priceText, 40) : '',
      productUrl: Boolean(it.isWishlist) ? productUrl : '',
      lastCheckedPrice,
      previousPrice,
      priceDropped: Boolean(it.priceDropped),
      wearCount,
      lastWornAt,
      createdAt
    });
  }

  const outfitsIn = Array.isArray(raw.outfits) ? raw.outfits.slice(0, 500) : [];
  const cleanOutfits = [];
  for (const ofItem of outfitsIn) {
    if (!ofItem || typeof ofItem !== 'object' || Array.isArray(ofItem)) continue;
    const id = isSafeId(ofItem.id) ? ofItem.id : null;
    if (!id) continue;
    const itemIds = Array.isArray(ofItem.itemIds)
      ? [...new Set(ofItem.itemIds.filter((x) => isSafeId(x)))].slice(0, 20)
      : [];
    const harmonyScore = Number.isFinite(Number(ofItem.harmonyScore))
      ? Math.max(0, Math.min(100, Math.round(Number(ofItem.harmonyScore))))
      : 80;
    cleanOutfits.push({
      id,
      title: clampText(ofItem.title, 120) || 'Kombin',
      itemIds,
      season: validSeasons.has(ofItem.season) ? ofItem.season : 'ALL',
      harmonyScore,
      harmonyNote: clampText(ofItem.harmonyNote, 180),
      weatherTag: clampText(ofItem.weatherTag, 80),
      notes: clampText(ofItem.notes, 300, true),
      createdAt: Number.isFinite(Number(ofItem.createdAt)) && Number(ofItem.createdAt) > 0
        ? Number(ofItem.createdAt)
        : Date.now()
    });
  }

  const likedLookIds = Array.isArray(raw.likedLookIds)
    ? [...new Set(raw.likedLookIds.filter((x) => isSafeId(x, 64)))].slice(0, 500)
    : [];
  const dislikedLookIds = Array.isArray(raw.dislikedLookIds)
    ? [...new Set(raw.dislikedLookIds.filter((x) => isSafeId(x, 64)))].slice(0, 500)
    : [];

  return {
    items: cleanItems,
    outfits: cleanOutfits,
    themeMode: validThemes.has(raw.themeMode) ? raw.themeMode : 'MODERN',
    language: validLanguages.has(raw.language) ? raw.language : 'TR',
    genderPref: validGenders.has(raw.genderPref) ? raw.genderPref : 'UNISEX',
    genderUserSet: Boolean(raw.genderUserSet),
    selectedCityId: validCities.has(raw.selectedCityId) ? raw.selectedCityId : 'ISTANBUL',
    customLocationLabel: raw.customLocationLabel ? clampText(raw.customLocationLabel, 80) : null,
    customLocationLat: Number.isFinite(Number(raw.customLocationLat)) && Math.abs(Number(raw.customLocationLat)) <= 90
      ? Number(raw.customLocationLat)
      : null,
    customLocationLon: Number.isFinite(Number(raw.customLocationLon)) && Math.abs(Number(raw.customLocationLon)) <= 180
      ? Number(raw.customLocationLon)
      : null,
    likedLookIds,
    dislikedLookIds
  };
}

export async function safeFetch(
  url,
  options = {},
  { allowedHosts, timeoutMs = 15000, maxResponseBytes = 15 * 1024 * 1024, redirect = 'error' } = {}
) {
  if (!(allowedHosts instanceof Set) || allowedHosts.size === 0) {
    throw new Error('ALLOWED_HOSTS_REQUIRED');
  }
  if (!isSafePublicHttpsUrl(url, 2048)) {
    throw new Error('UNSAFE_URL');
  }
  const host = new URL(url.trim()).hostname.toLowerCase().replace(/\.$/, '');
  if (!allowedHosts.has(host)) {
    throw new Error('UNTRUSTED_HOST');
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url.trim(), {
      ...options,
      signal: controller.signal,
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      mode: 'cors',
      cache: 'no-store',
      redirect
    });
    const cl = res.headers?.get?.('content-length');
    if (cl != null && /^\d+$/.test(cl.trim())) {
      const size = Number(cl.trim());
      if (size > maxResponseBytes) {
        controller.abort();
        throw new Error('RESPONSE_TOO_LARGE');
      }
    }
    return res;
  } finally {
    clearTimeout(timer);
  }
}

export function isValidGeminiApiKey(val) {
  return typeof val === 'string' && /^(?:AIza[0-9A-Za-z_-]{20,120}|AQ\.[0-9A-Za-z._~=-]{16,512})$/.test(val.trim());
}

export function isVertexExpressApiKey(val) {
  return typeof val === 'string' && val.trim().startsWith('AQ.');
}

export function isValidHfToken(val) {
  return typeof val === 'string' && /^hf_[0-9A-Za-z]{16,128}$/.test(val.trim());
}

export function maskSecret(val) {
  if (typeof val !== 'string' || !val) return '';
  const clean = val.trim();
  if (clean.length <= 8) return '••••';
  return `${clean.slice(0, 4)}••••${clean.slice(-4)}`;
}

// ==========================================
// Non-extractable Web Crypto AES-256-GCM Keystore
// ==========================================
const KEYSTORE_DB = 'dolabim_keystore_v1';
const KEYSTORE_STORE = 'keys';
const MASTER_KEY_ID = 'master_aes_gcm';
let masterKeyPromise = null;

function openKeystoreDb() {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const req = indexedDB.open(KEYSTORE_DB, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(KEYSTORE_STORE)) {
        db.createObjectStore(KEYSTORE_STORE);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function getOrCreateMasterKey() {
  if (!window.crypto?.subtle) {
    throw new Error('WEB_CRYPTO_UNAVAILABLE');
  }
  if (masterKeyPromise) return masterKeyPromise;
  masterKeyPromise = (async () => {
    const db = await openKeystoreDb();
    try {
      const existing = await new Promise((resolve) => {
        try {
          const tx = db.transaction(KEYSTORE_STORE, 'readonly');
          const req = tx.objectStore(KEYSTORE_STORE).get(MASTER_KEY_ID);
          req.onsuccess = () => resolve(req.result || null);
          req.onerror = () => resolve(null);
        } catch {
          resolve(null);
        }
      });
      if (existing) return existing;

      const generated = await crypto.subtle.generateKey(
        { name: 'AES-GCM', length: 256 },
        false, // Non-extractable key material
        ['encrypt', 'decrypt']
      );
      await new Promise((resolve, reject) => {
        const tx = db.transaction(KEYSTORE_STORE, 'readwrite');
        tx.objectStore(KEYSTORE_STORE).put(generated, MASTER_KEY_ID);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });
      return generated;
    } finally {
      try { db.close(); } catch {}
    }
  })().catch((err) => {
    masterKeyPromise = null;
    throw err;
  });
  return masterKeyPromise;
}

function bytesToBase64(bytes) {
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}

function base64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export async function encryptSecret(plaintext) {
  const clean = String(plaintext || '').trim();
  if (!clean) return '';
  if (!window.crypto?.subtle) return null;
  const key = await getOrCreateMasterKey();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(clean);
  const cipherBuf = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, encoded);
  return JSON.stringify({
    v: 2,
    alg: 'AES-GCM',
    iv: bytesToBase64(iv),
    ct: bytesToBase64(new Uint8Array(cipherBuf))
  });
}

export async function decryptSecret(serialized) {
  if (!serialized || typeof serialized !== 'string') return '';
  if (!window.crypto?.subtle) return '';
  try {
    const parsed = safeJsonParse(serialized);
    if (!parsed || parsed.v !== 2 || parsed.alg !== 'AES-GCM' || !parsed.iv || !parsed.ct) {
      return '';
    }
    const key = await getOrCreateMasterKey();
    const iv = base64ToBytes(parsed.iv);
    const ct = base64ToBytes(parsed.ct);
    const plainBuf = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, ct);
    return new TextDecoder().decode(plainBuf).trim();
  } catch {
    return '';
  }
}

export async function deleteKeystoreDb() {
  masterKeyPromise = null;
  if (typeof window === 'undefined' || !window.indexedDB) return;
  await new Promise((resolve) => {
    let finished = false;
    const finish = () => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      resolve();
    };
    const timer = setTimeout(finish, 500);
    try {
      const req = indexedDB.deleteDatabase(KEYSTORE_DB);
      req.onsuccess = finish;
      req.onerror = finish;
      req.onblocked = finish;
    } catch {
      finish();
    }
  });
}

export function installClickjackingGuard() {
  try {
    if (window.self !== window.top) {
      try {
        window.top.location = window.self.location.href;
      } catch {
        // Cross-origin frame blocked top navigation
      }
      if (document.body) {
        document.body.textContent = '';
        const box = document.createElement('div');
        box.style.cssText = 'padding:32px;font-family:sans-serif;text-align:center;';
        box.textContent = 'Güvenlik nedeniyle bu uygulama iframe içinde açılamaz.';
        document.body.appendChild(box);
      }
      throw new Error('FRAMING_BLOCKED');
    }
  } catch (err) {
    if (err?.message === 'FRAMING_BLOCKED') throw err;
  }
}

export function sanitizeErrorMessage(err, fallback = 'İşlem tamamlanamadı') {
  const raw = typeof err === 'string' ? err : (err?.message || '');
  const scrubbed = String(raw)
    .replace(/AIza[0-9A-Za-z_-]+/g, '[KEY]')
    .replace(/AQ\.[0-9A-Za-z._~=-]+/g, '[KEY]')
    .replace(/hf_[0-9A-Za-z]+/g, '[TOKEN]')
    .replace(/Bearer\s+[A-Za-z0-9._-]+/gi, 'Bearer [REDACTED]')
    .replace(/https?:\/\/[^\s]+/gi, '[URL]')
    .replace(/[\x00-\x1F\x7F]+/g, ' ')
    .trim();
  if (!scrubbed) return fallback;
  return scrubbed.length > 140 ? `${scrubbed.slice(0, 137)}...` : scrubbed;
}

export function safeOpenExternalUrl(url) {
  if (!isSafePublicHttpsUrl(url, 1000)) return false;
  const win = window.open(url.trim(), '_blank', 'noopener,noreferrer');
  if (win) {
    try {
      win.opener = null;
    } catch {
      // Cross-origin opener nullification best effort
    }
  }
  return true;
}
