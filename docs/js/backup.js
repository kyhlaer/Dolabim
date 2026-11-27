// Full Local Backup & Restore for Dolabım Web/PWA (1:1 schema with Android BackupManager.kt)
import { APP_VERSION } from './constants.js';
import { loadAppState, saveAppState, savePhotoDataUrl } from './storage.js';
import { loadLocalProfile, saveLocalProfile } from './social.js';
import { isSafeImageDataUrl, isSafePhotoKey } from './security.js';

export const BACKUP_FORMAT = 'dolabim_backup_v1';
const DB_NAME = 'dolabim_web_db';
const PHOTO_STORE = 'photos';
const DM_MUTED_KEY = 'dolabim_dm_muted_v1';
const TELEMETRY_KEY = 'dolabim_telemetry_v1';

async function readAllIndexedDbPhotos() {
  const result = {};
  if (!window.indexedDB) return result;
  let db = null;
  try {
    db = await new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = () => {
        if (!req.result.objectStoreNames.contains(PHOTO_STORE)) {
          req.result.createObjectStore(PHOTO_STORE);
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
    if (!db.objectStoreNames.contains(PHOTO_STORE)) return result;
    await new Promise((resolve) => {
      const tx = db.transaction(PHOTO_STORE, 'readonly');
      const store = tx.objectStore(PHOTO_STORE);
      const curReq = store.openCursor();
      curReq.onsuccess = () => {
        const cur = curReq.result;
        if (cur) {
          const k = String(cur.key || '');
          const v = String(cur.value || '');
          if (k && isSafeImageDataUrl(v)) result[k] = v;
          cur.continue();
        } else {
          resolve();
        }
      };
      curReq.onerror = () => resolve();
      tx.onabort = () => resolve();
    });
  } catch {
    // ignore
  } finally {
    try { db?.close?.(); } catch {}
  }
  return result;
}

export async function buildWebBackupObject({ includeAccount = false } = {}) {
  const state = loadAppState();
  const photos = await readAllIndexedDbPhotos();
  let muted = [];
  try {
    const parsed = JSON.parse(localStorage.getItem(DM_MUTED_KEY) || '[]');
    if (Array.isArray(parsed)) muted = parsed.filter((x) => typeof x === 'string' && x);
  } catch {
    muted = [];
  }

  const backup = {
    format: BACKUP_FORMAT,
    exportedAtMs: Date.now(),
    appVersion: String(APP_VERSION || '1.43.276').replace(/^v/, ''),
    platform: 'web',
    includeAccount: Boolean(includeAccount),
    settings: {
      themeMode: state.themeMode || 'MODERN',
      language: state.language || 'TR',
      genderPref: state.genderPref || 'UNISEX',
      genderUserSet: Boolean(state.genderUserSet),
      lookGender: state.lookGender || '',
      cityId: state.selectedCityId || 'ISTANBUL',
      likedLookIds: Array.isArray(state.likedLookIds) ? state.likedLookIds : [],
      dislikedLookIds: Array.isArray(state.dislikedLookIds) ? state.dislikedLookIds : [],
      mutedPartnerUids: muted
    },
    wardrobe: {
      items: Array.isArray(state.items) ? state.items : [],
      outfits: Array.isArray(state.outfits) ? state.outfits : []
    },
    photos
  };

  if (includeAccount) {
    const profile = loadLocalProfile();
    let uid = '';
    let refreshToken = '';
    try {
      const tel = JSON.parse(localStorage.getItem(TELEMETRY_KEY) || '{}');
      uid = typeof tel.uid === 'string' ? tel.uid : '';
      refreshToken = typeof tel.refreshToken === 'string' ? tel.refreshToken : '';
    } catch {
      // ignore
    }
    backup.account = {
      profile,
      auth: { uid, refreshToken }
    };
  }

  return backup;
}

export async function exportWebBackupDownload({ includeAccount = false } = {}) {
  const obj = await buildWebBackupObject({ includeAccount });
  const jsonStr = JSON.stringify(obj, null, 2);
  const blob = new Blob([jsonStr], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const dateTag = new Date().toISOString().slice(0, 10);
  const kindTag = includeAccount ? 'hesapli' : 'hesapsiz';
  const a = document.createElement('a');
  a.href = url;
  a.download = `dolabim-yedek-${kindTag}-${dateTag}.json`;
  document.body.appendChild(a);
  a.click();
  setTimeout(() => {
    a.remove();
    URL.revokeObjectURL(url);
  }, 500);
  return {
    itemCount: obj.wardrobe.items.length,
    outfitCount: obj.wardrobe.outfits.length,
    photoCount: Object.keys(obj.photos).length,
    includeAccount: obj.includeAccount
  };
}

export async function parseWebBackupFile(file) {
  const text = await file.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error('Geçersiz JSON dosyası');
  }
  if (!parsed || parsed.format !== BACKUP_FORMAT) {
    throw new Error('Geçersiz Dolabım yedek dosyası');
  }
  const hasAccount = Boolean(parsed.includeAccount && parsed.account);
  return {
    raw: parsed,
    appVersion: String(parsed.appVersion || ''),
    exportedAtMs: Number(parsed.exportedAtMs) || 0,
    includeAccount: hasAccount,
    accountUsername: String(parsed.account?.profile?.username || ''),
    itemCount: Array.isArray(parsed.wardrobe?.items) ? parsed.wardrobe.items.length : 0,
    outfitCount: Array.isArray(parsed.wardrobe?.outfits) ? parsed.wardrobe.outfits.length : 0,
    photoCount: parsed.photos && typeof parsed.photos === 'object' ? Object.keys(parsed.photos).length : 0
  };
}

export async function restoreWebBackupObject(raw, { restoreAccount = false } = {}) {
  if (!raw || raw.format !== BACKUP_FORMAT) {
    throw new Error('Geçersiz Dolabım yedek dosyası');
  }
  const cur = loadAppState();
  const s = raw.settings || {};
  const w = raw.wardrobe || {};
  const nextState = {
    ...cur,
    themeMode: s.themeMode || cur.themeMode,
    language: s.language || cur.language,
    genderPref: s.genderPref || cur.genderPref,
    genderUserSet: typeof s.genderUserSet === 'boolean' ? s.genderUserSet : cur.genderUserSet,
    lookGender: s.lookGender || cur.lookGender,
    selectedCityId: s.cityId || s.selectedCityId || cur.selectedCityId,
    likedLookIds: Array.isArray(s.likedLookIds) ? s.likedLookIds : (cur.likedLookIds || []),
    dislikedLookIds: Array.isArray(s.dislikedLookIds) ? s.dislikedLookIds : (cur.dislikedLookIds || []),
    items: Array.isArray(w.items) ? w.items : [],
    outfits: Array.isArray(w.outfits) ? w.outfits : []
  };
  saveAppState(nextState);

  if (Array.isArray(s.mutedPartnerUids)) {
    try {
      localStorage.setItem(DM_MUTED_KEY, JSON.stringify(s.mutedPartnerUids.filter((x) => typeof x === 'string' && x)));
    } catch {}
  }

  let photoCount = 0;
  const photos = raw.photos && typeof raw.photos === 'object' ? raw.photos : {};
  for (const [k, v] of Object.entries(photos)) {
    if (isSafePhotoKey(k) && isSafeImageDataUrl(v)) {
      await savePhotoDataUrl(k, v);
      photoCount += 1;
    }
  }

  let accountRestored = false;
  if (restoreAccount && raw.account) {
    if (raw.account.profile && typeof raw.account.profile === 'object') {
      saveLocalProfile(raw.account.profile);
      accountRestored = true;
    }
    const uid = String(raw.account.auth?.uid || '').trim();
    const refreshToken = String(raw.account.auth?.refreshToken || '').trim();
    if (uid && refreshToken) {
      try {
        const prev = JSON.parse(localStorage.getItem(TELEMETRY_KEY) || '{}');
        localStorage.setItem(TELEMETRY_KEY, JSON.stringify({
          ...prev,
          uid,
          refreshToken,
          idToken: '',
          expiresAtMs: 0
        }));
        accountRestored = true;
      } catch {}
    }
  }

  return {
    itemCount: nextState.items.length,
    outfitCount: nextState.outfits.length,
    photoCount,
    accountRestored
  };
}
