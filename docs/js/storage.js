const DB_NAME = 'dolabim_web_db';
const DB_VERSION = 1;
const PHOTO_STORE = 'photos';

const STATE_KEY = 'dolabim_state_v1';
const GEMINI_KEY_STORAGE = 'dolabim_gemini_key_v1';

let dbPromise = null;

function openDb() {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    if (!window.indexedDB) {
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
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

export async function savePhotoDataUrl(id, dataUrl) {
  if (!id || !dataUrl) return;
  try {
    const db = await openDb();
    await new Promise((resolve, reject) => {
      const tx = db.transaction(PHOTO_STORE, 'readwrite');
      tx.objectStore(PHOTO_STORE).put(dataUrl, id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // Fallback in-memory session cache if storage quota restricted
    memoryPhotos.set(id, dataUrl);
  }
}

const memoryPhotos = new Map();

export async function getPhotoDataUrl(id) {
  if (!id) return null;
  if (memoryPhotos.has(id)) return memoryPhotos.get(id);
  try {
    const db = await openDb();
    return await new Promise((resolve) => {
      const tx = db.transaction(PHOTO_STORE, 'readonly');
      const req = tx.objectStore(PHOTO_STORE).get(id);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

export async function deletePhotoDataUrl(id) {
  if (!id) return;
  memoryPhotos.delete(id);
  try {
    const db = await openDb();
    await new Promise((resolve) => {
      const tx = db.transaction(PHOTO_STORE, 'readwrite');
      tx.objectStore(PHOTO_STORE).delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
    });
  } catch {
    // ignore
  }
}

export async function loadAllPhotoMap(ids) {
  const unique = [...new Set((ids || []).filter(Boolean))];
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
  if (window.crypto?.randomUUID) {
    return `${prefix}_${crypto.randomUUID()}`;
  }
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
}

const DEFAULT_STATE = {
  items: [],
  outfits: [],
  themeMode: 'MODERN',
  language: 'TR',
  genderPref: 'UNISEX',
  genderUserSet: false,
  selectedCityId: 'ISTANBUL',
  customLocationLabel: null,
  likedLookIds: [],
  dislikedLookIds: []
};

export function loadAppState() {
  try {
    const raw = localStorage.getItem(STATE_KEY);
    if (!raw) return { ...DEFAULT_STATE };
    const parsed = JSON.parse(raw);
    return {
      ...DEFAULT_STATE,
      ...parsed,
      items: Array.isArray(parsed.items) ? parsed.items : [],
      outfits: Array.isArray(parsed.outfits) ? parsed.outfits : [],
      likedLookIds: Array.isArray(parsed.likedLookIds) ? parsed.likedLookIds : [],
      dislikedLookIds: Array.isArray(parsed.dislikedLookIds) ? parsed.dislikedLookIds : []
    };
  } catch {
    return { ...DEFAULT_STATE };
  }
}

export function saveAppState(state) {
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify(state));
  } catch {
    // ignore quota error
  }
}

export function getGeminiApiKey() {
  try {
    return (localStorage.getItem(GEMINI_KEY_STORAGE) || '').trim();
  } catch {
    return '';
  }
}

export function setGeminiApiKey(key) {
  try {
    const clean = (key || '').trim();
    if (!clean) localStorage.removeItem(GEMINI_KEY_STORAGE);
    else localStorage.setItem(GEMINI_KEY_STORAGE, clean);
  } catch {
    // ignore
  }
}

export async function exportFullBackupJson(state) {
  const photoIds = new Set(['user_face_photo', 'user_body_photo']);
  (state.items || []).forEach((it) => {
    (it.photoFileNames || []).forEach((id) => photoIds.add(id));
    (it.originalPhotoFileNames || []).forEach((id) => photoIds.add(id));
  });
  const photos = {};
  for (const id of photoIds) {
    const dataUrl = await getPhotoDataUrl(id);
    if (dataUrl) photos[id] = dataUrl;
  }
  return JSON.stringify({
    app: 'Dolabim-Web',
    version: '1.43.2',
    exportedAt: new Date().toISOString(),
    state,
    photos
  });
}

export async function importFullBackupJson(jsonText) {
  const parsed = JSON.parse(jsonText);
  if (!parsed || typeof parsed !== 'object' || !parsed.state) {
    throw new Error('Geçersiz yedek dosyası');
  }
  const photos = parsed.photos || {};
  for (const [id, dataUrl] of Object.entries(photos)) {
    if (typeof dataUrl === 'string' && dataUrl.startsWith('data:image/')) {
      await savePhotoDataUrl(id, dataUrl);
    }
  }
  const merged = { ...DEFAULT_STATE, ...parsed.state };
  saveAppState(merged);
  return merged;
}
