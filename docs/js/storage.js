import {
  APP_LANGUAGES,
  CATEGORIES,
  GARMENT_COLORS,
  GENDER_PREFS,
  SEASONS,
  STYLES,
  SUBCATEGORIES,
  THEME_MODES,
  TURKISH_CITIES
} from './constants.js';
import {
  decryptSecret,
  deleteKeystoreDb,
  encryptSecret,
  isSafeImageDataUrl,
  isSafePhotoKey,
  isValidGeminiApiKey,
  isValidHfToken,
  safeJsonParse,
  sanitizeAppState
} from './security.js';

const DB_NAME = 'dolabim_web_db';
const DB_VERSION = 1;
const PHOTO_STORE = 'photos';

const STATE_KEY = 'dolabim_state_v1';
const LEGACY_GEMINI_KEY_STORAGE = 'dolabim_gemini_key_v1';
const LEGACY_HF_TOKEN_STORAGE = 'dolabim_hf_token_v1';
const SEC_GEMINI_KEY_STORAGE = 'dolabim_sec_gemini_v2';
const SEC_HF_TOKEN_STORAGE = 'dolabim_sec_hf_v2';
const SESS_GEMINI_KEY_STORAGE = 'dolabim_sess_gemini';
const SESS_HF_TOKEN_STORAGE = 'dolabim_sess_hf';

const VALID_ENUMS = {
  validThemes: new Set(THEME_MODES.map((m) => m.id)),
  validLanguages: new Set(APP_LANGUAGES.map((l) => l.code)),
  validGenders: new Set(GENDER_PREFS.map((g) => g.id)),
  validCities: new Set(TURKISH_CITIES.map((c) => c.id)),
  validCategories: new Set(CATEGORIES.map((c) => c.id)),
  validSubCategories: new Set(SUBCATEGORIES.map((s) => s.id)),
  validStyles: new Set(STYLES.map((s) => s.id)),
  validSeasons: new Set(SEASONS.map((s) => s.id)),
  validColors: new Set(GARMENT_COLORS.map((c) => c.id))
};

let dbPromise = null;
const memoryPhotos = new Map();

function openDb() {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    if (!window.indexedDB) {
      dbPromise = null;
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(PHOTO_STORE)) {
        db.createObjectStore(PHOTO_STORE);
      }
    };
    req.onsuccess = () => {
      const db = req.result;
      db.onversionchange = () => {
        try { db.close(); } catch {}
        dbPromise = null;
      };
      resolve(db);
    };
    req.onerror = () => {
      dbPromise = null;
      reject(req.error);
    };
  });
  return dbPromise;
}

export async function savePhotoDataUrl(id, dataUrl) {
  if (!isSafePhotoKey(id) || !isSafeImageDataUrl(dataUrl)) return;
  try {
    const db = await openDb();
    await new Promise((resolve, reject) => {
      const tx = db.transaction(PHOTO_STORE, 'readwrite');
      tx.objectStore(PHOTO_STORE).put(dataUrl, id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error || new Error('IDB_TX_ABORTED'));
    });
    memoryPhotos.delete(id);
  } catch {
    // Fallback in-memory session cache if storage quota restricted
    memoryPhotos.set(id, dataUrl);
  }
}

export async function getPhotoDataUrl(id) {
  if (!isSafePhotoKey(id)) return null;
  if (memoryPhotos.has(id)) {
    const memUrl = memoryPhotos.get(id);
    return isSafeImageDataUrl(memUrl) ? memUrl : null;
  }
  try {
    const db = await openDb();
    const raw = await new Promise((resolve) => {
      const tx = db.transaction(PHOTO_STORE, 'readonly');
      const req = tx.objectStore(PHOTO_STORE).get(id);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
      tx.onabort = () => resolve(null);
    });
    return isSafeImageDataUrl(raw) ? raw : null;
  } catch {
    return null;
  }
}

export async function deletePhotoDataUrl(id) {
  if (!isSafePhotoKey(id)) return;
  memoryPhotos.delete(id);
  try {
    const db = await openDb();
    await new Promise((resolve) => {
      const tx = db.transaction(PHOTO_STORE, 'readwrite');
      tx.objectStore(PHOTO_STORE).delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
      tx.onabort = () => resolve();
    });
  } catch {
    // ignore
  }
}

export async function loadAllPhotoMap(ids) {
  const unique = [...new Set((ids || []).filter((id) => isSafePhotoKey(id)))];
  const map = new Map();
  await Promise.all(
    unique.map(async (id) => {
      const url = await getPhotoDataUrl(id);
      if (url) map.set(id, url);
    })
  );
  return map;
}

export function createId(prefix = 'id') {
  const cleanPrefix = String(prefix || 'id').replace(/[^A-Za-z0-9_-]/g, '') || 'id';
  if (window.crypto?.randomUUID) {
    return `${cleanPrefix}_${crypto.randomUUID()}`;
  }
  const bytes = new Uint8Array(12);
  if (window.crypto?.getRandomValues) {
    crypto.getRandomValues(bytes);
  }
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${cleanPrefix}_${Date.now()}_${hex}`;
}

export function loadAppState() {
  try {
    const raw = localStorage.getItem(STATE_KEY);
    if (!raw) return sanitizeAppState(null, VALID_ENUMS);
    const parsed = safeJsonParse(raw);
    return sanitizeAppState(parsed, VALID_ENUMS);
  } catch {
    return sanitizeAppState(null, VALID_ENUMS);
  }
}

export function saveAppState(state) {
  try {
    const clean = sanitizeAppState(state, VALID_ENUMS);
    localStorage.setItem(STATE_KEY, JSON.stringify(clean));
  } catch {
    // ignore quota error
  }
}

async function readEncryptedOrSessionSecret(secKey, sessKey, validator) {
  try {
    const sess = (sessionStorage.getItem(sessKey) || '').trim();
    if (sess && validator(sess)) return sess;
  } catch {
    // ignore
  }
  try {
    const raw = localStorage.getItem(secKey);
    if (!raw) return '';
    const plain = await decryptSecret(raw);
    return validator(plain) ? plain : '';
  } catch {
    return '';
  }
}

async function writeEncryptedOrSessionSecret(secKey, sessKey, legacyKey, val, validator, invalidErrorCode) {
  const clean = String(val || '').trim();
  try {
    localStorage.removeItem(legacyKey);
  } catch {
    // ignore
  }
  if (!clean) {
    try {
      localStorage.removeItem(secKey);
      sessionStorage.removeItem(sessKey);
    } catch {
      // ignore
    }
    return;
  }
  if (!validator(clean)) {
    throw new Error(invalidErrorCode);
  }
  const encrypted = await encryptSecret(clean);
  if (encrypted) {
    localStorage.setItem(secKey, encrypted);
    try {
      sessionStorage.removeItem(sessKey);
    } catch {
      // ignore
    }
  } else {
    // Web Crypto subtle unavailable in insecure non-localhost HTTP context: store ephemeral session-only
    sessionStorage.setItem(sessKey, clean);
  }
}

export async function migrateLegacySecrets() {
  try {
    const legG = (localStorage.getItem(LEGACY_GEMINI_KEY_STORAGE) || '').trim();
    localStorage.removeItem(LEGACY_GEMINI_KEY_STORAGE);
    if (legG && isValidGeminiApiKey(legG)) {
      await setGeminiApiKey(legG).catch(() => {});
    }
  } catch {
    // ignore
  }
  try {
    const legH = (localStorage.getItem(LEGACY_HF_TOKEN_STORAGE) || '').trim();
    localStorage.removeItem(LEGACY_HF_TOKEN_STORAGE);
    if (legH && isValidHfToken(legH)) {
      await setHfToken(legH).catch(() => {});
    }
  } catch {
    // ignore
  }
}

export async function getGeminiApiKey() {
  return readEncryptedOrSessionSecret(SEC_GEMINI_KEY_STORAGE, SESS_GEMINI_KEY_STORAGE, isValidGeminiApiKey);
}

export async function setGeminiApiKey(key) {
  await writeEncryptedOrSessionSecret(
    SEC_GEMINI_KEY_STORAGE,
    SESS_GEMINI_KEY_STORAGE,
    LEGACY_GEMINI_KEY_STORAGE,
    key,
    isValidGeminiApiKey,
    'INVALID_GEMINI_KEY'
  );
}

export async function getHfToken() {
  return readEncryptedOrSessionSecret(SEC_HF_TOKEN_STORAGE, SESS_HF_TOKEN_STORAGE, isValidHfToken);
}

export async function setHfToken(token) {
  await writeEncryptedOrSessionSecret(
    SEC_HF_TOKEN_STORAGE,
    SESS_HF_TOKEN_STORAGE,
    LEGACY_HF_TOKEN_STORAGE,
    token,
    isValidHfToken,
    'INVALID_HF_TOKEN'
  );
}

export async function clearAllLocalData() {
  memoryPhotos.clear();
  try {
    if (dbPromise) {
      const db = await dbPromise.catch(() => null);
      db?.close?.();
      dbPromise = null;
    }
  } catch {
    dbPromise = null;
  }

  if ('serviceWorker' in navigator) {
    try {
      const regs = await navigator.serviceWorker.getRegistrations();
      await Promise.all(regs.map((r) => r.unregister()));
    } catch {}
  }

  const deleteIdbSafe = (name) => {
    if (!window.indexedDB) return Promise.resolve();
    return new Promise((resolve) => {
      let finished = false;
      const finish = () => {
        if (finished) return;
        finished = true;
        clearTimeout(timer);
        resolve();
      };
      const timer = setTimeout(finish, 500);
      try {
        const req = indexedDB.deleteDatabase(name);
        req.onsuccess = finish;
        req.onerror = finish;
        req.onblocked = finish;
      } catch {
        finish();
      }
    });
  };

  await deleteIdbSafe(DB_NAME);
  await deleteIdbSafe('dolabim_inbox');
  await deleteKeystoreDb();

  try { localStorage.clear(); } catch {}
  try { sessionStorage.clear(); } catch {}

  if ('caches' in window) {
    try {
      const keys = await caches.keys();
      await Promise.all(keys.map((k) => caches.delete(k)));
    } catch {}
  }
}
