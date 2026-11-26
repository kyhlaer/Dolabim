// Social Profile, Garment/Profile Deep-Link Sharing, Showcase, Follows & Likes
// Uses Firebase Auth + Cloud Firestore REST API (identical schema to Android SocialProfileService.kt)

import {
  CATEGORIES,
  GARMENT_COLORS,
  localizedText
} from './constants.js';
import {
  cleanGarmentTitleForName,
  displaySubCategory,
  parsePrice
} from './recommendation-engine.js';
import {
  clampText,
  isSafeImageDataUrl,
  isSafePublicHttpsUrl,
  safeImgSrc
} from './security.js';
import {
  ensureFirebaseAuth,
  getStoredUid,
  trackFeature,
  updateTelemetryProfile
} from './analytics.js';
import {
  migrateMyPostsForPrivacy,
  sendNotificationBestEffort
} from './social-feed.js';

const FIREBASE_PROJECT_ID = 'dolabim-stats';
const FS_BASE = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/(default)/documents`;
const PROFILE_STORAGE_KEY = 'dolabim_profile_v1';
const SHARE_BASE_URL = 'https://kyhlaer.github.io/Dolabim/';

const USERNAME_REGEX = /^[a-z0-9][a-z0-9_.]{1,22}[a-z0-9]$/;
const SHARE_ID_REGEX = /^[A-Za-z0-9_-]{8,48}$/;
const UID_REGEX = /^[A-Za-z0-9_-]{1,128}$/;

const MIN_VALID_EPOCH_MS = 1700000000001;
const MAX_VALID_EPOCH_MS = 4102444800000;

export const STYLE_VIBE_PRESETS = [
  'Minimalist',
  'Streetwear',
  'Casual',
  'Smart Casual',
  'Old Money',
  'Vintage',
  'Sporty',
  'Chic',
  'Grunge',
  'Bohem',
  'Y2K',
  'Klasik',
  'Monokrom',
  'Oversize',
  'Athleisure',
  'Preppy',
  'Techwear',
  'Retro',
  'Goth / Dark',
  'Avant-Garde'
];

export const DEFAULT_STATS_VISIBILITY = {
  showTotalItems: true,
  showCategoryBreakdown: true,
  showFavoriteCount: true,
  showWishlistCount: true,
  showOutfitCount: true,
  showTopColors: true,
  showColorPalette: true,
  showTopSubCategories: true,
  showEstimatedValue: false
};

export function safeNowMs() {
  const n = Date.now();
  if (!Number.isFinite(n) || n < MIN_VALID_EPOCH_MS) return MIN_VALID_EPOCH_MS;
  if (n > MAX_VALID_EPOCH_MS - 5000) return MAX_VALID_EPOCH_MS - 5000;
  return Math.trunc(n);
}

export function normalizeUsername(raw) {
  return String(raw || '')
    .trim()
    .replace(/^@+/, '')
    .replace(/[çÇ]/g, 'c')
    .replace(/[ğĞ]/g, 'g')
    .replace(/[ıİ]/g, 'i')
    .replace(/[öÖ]/g, 'o')
    .replace(/[şŞ]/g, 's')
    .replace(/[üÜ]/g, 'u')
    .toLowerCase()
    .replace(/\u0307/g, '')
    .replace(/\s+/g, '_')
    .replace(/[^a-z0-9_.]/g, '');
}

export function isValidUsername(raw) {
  const norm = normalizeUsername(raw);
  if (!USERNAME_REGEX.test(norm)) return false;
  if (norm.includes('..')) return false;
  return true;
}

export function buildShareItemUrl(shareId) {
  return `${SHARE_BASE_URL}?item=${encodeURIComponent(shareId)}`;
}

export function buildShareOutfitUrl(shareId) {
  return `${SHARE_BASE_URL}?outfit=${encodeURIComponent(shareId)}`;
}

export function buildShareProfileUrl(username, uid) {
  const cleanUser = normalizeUsername(username);
  if (cleanUser && isValidUsername(cleanUser)) {
    return `${SHARE_BASE_URL}?u=${encodeURIComponent(cleanUser)}`;
  }
  return `${SHARE_BASE_URL}?uid=${encodeURIComponent(uid || getStoredUid())}`;
}

export function parseDeepLink(urlStr) {
  if (!urlStr) return null;
  try {
    const parsed = new URL(urlStr, SHARE_BASE_URL);
    if (parsed.protocol === 'dolabim:') {
      const host = parsed.hostname.toLowerCase();
      const seg = parsed.pathname.replace(/^\/+/, '').split('/')[0] || '';
      if (host === 'item' && SHARE_ID_REGEX.test(seg)) {
        return { kind: 'item', shareId: seg };
      }
      if (host === 'outfit' && SHARE_ID_REGEX.test(seg)) {
        return { kind: 'outfit', shareId: seg };
      }
      if (host === 'post' && SHARE_ID_REGEX.test(seg)) {
        return { kind: 'post', postId: seg, ownerUid: '' };
      }
      if ((host === 'u' || host === 'user' || host === 'profile') && seg) {
        return { kind: 'profile', target: normalizeUsername(seg), isUid: false };
      }
      if (host === 'uid' && UID_REGEX.test(seg)) {
        return { kind: 'profile', target: seg, isUid: true };
      }
    }
    const post = (parsed.searchParams.get('post') || '').trim();
    if (SHARE_ID_REGEX.test(post)) {
      const owner = (parsed.searchParams.get('owner') || '').trim();
      return { kind: 'post', postId: post, ownerUid: UID_REGEX.test(owner) ? owner : '' };
    }
    const item = (parsed.searchParams.get('item') || '').trim();
    if (SHARE_ID_REGEX.test(item)) return { kind: 'item', shareId: item };
    const outfit = (parsed.searchParams.get('outfit') || '').trim();
    if (SHARE_ID_REGEX.test(outfit)) return { kind: 'outfit', shareId: outfit };
    const u = (parsed.searchParams.get('u') || '').trim();
    if (u) return { kind: 'profile', target: normalizeUsername(u), isUid: false };
    const uid = (parsed.searchParams.get('uid') || '').trim();
    if (UID_REGEX.test(uid)) return { kind: 'profile', target: uid, isUid: true };
    return null;
  } catch {
    return null;
  }
}

export function loadLocalProfile() {
  const base = {
    username: '',
    displayName: '',
    bio: '',
    avatarBase64: '',
    avatarThumbBase64: '',
    styleTags: [],
    isPrivate: false,
    statsVisibility: { ...DEFAULT_STATS_VISIBILITY },
    showcaseSections: ['favorites', 'wishlist', 'selected', 'custom'],
    selectedShowcaseItemIds: [],
    customShowcaseItems: [],
    lastSyncedAtMs: 0
  };
  try {
    const raw = localStorage.getItem(PROFILE_STORAGE_KEY);
    if (!raw) return base;
    const p = JSON.parse(raw);
    if (!p || typeof p !== 'object') return base;
    return {
      username: typeof p.username === 'string' ? p.username.slice(0, 24) : '',
      displayName: typeof p.displayName === 'string' ? p.displayName.slice(0, 50) : '',
      bio: typeof p.bio === 'string' ? p.bio.slice(0, 220) : '',
      avatarBase64: isSafeImageDataUrl(p.avatarBase64 || '', 90000) ? p.avatarBase64 : '',
      avatarThumbBase64: isSafeImageDataUrl(p.avatarThumbBase64 || '', 40000) ? p.avatarThumbBase64 : '',
      styleTags: Array.isArray(p.styleTags) ? p.styleTags.map((t) => clampText(t, 24)).filter(Boolean).slice(0, 4) : [],
      isPrivate: Boolean(p.isPrivate),
      statsVisibility: {
        ...DEFAULT_STATS_VISIBILITY,
        ...(p.statsVisibility && typeof p.statsVisibility === 'object' ? {
          showTotalItems: p.statsVisibility.showTotalItems !== false,
          showCategoryBreakdown: p.statsVisibility.showCategoryBreakdown !== false,
          showFavoriteCount: p.statsVisibility.showFavoriteCount !== false,
          showWishlistCount: p.statsVisibility.showWishlistCount !== false,
          showOutfitCount: p.statsVisibility.showOutfitCount !== false,
          showTopColors: p.statsVisibility.showTopColors !== false,
          showColorPalette: p.statsVisibility.showColorPalette !== false,
          showTopSubCategories: p.statsVisibility.showTopSubCategories !== false,
          showEstimatedValue: Boolean(p.statsVisibility.showEstimatedValue)
        } : {})
      },
      showcaseSections: Array.isArray(p.showcaseSections)
        ? p.showcaseSections.filter((s) => ['favorites', 'wishlist', 'selected', 'custom'].includes(s))
        : ['favorites', 'wishlist', 'selected', 'custom'],
      selectedShowcaseItemIds: Array.isArray(p.selectedShowcaseItemIds)
        ? p.selectedShowcaseItemIds.map(String).filter(Boolean).slice(0, 20)
        : [],
      customShowcaseItems: Array.isArray(p.customShowcaseItems)
        ? p.customShowcaseItems.filter((c) => c && typeof c === 'object' && isSafeImageDataUrl(c.photoBase64 || '', 260000)).slice(0, 12)
        : [],
      lastSyncedAtMs: Number(p.lastSyncedAtMs) || 0
    };
  } catch {
    return base;
  }
}

export function saveLocalProfile(profile) {
  try {
    localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile));
  } catch {
    // ignore storage quota errors
  }
  updateTelemetryProfile({
    username: normalizeUsername(profile?.username || ''),
    displayName: (profile?.displayName || '').trim().slice(0, 60),
    avatarThumb: profile?.avatarThumbBase64 || (profile?.avatarBase64?.length <= 35000 ? profile.avatarBase64 : '')
  });
}

export function syncTelemetryIdentityFromLocalProfile() {
  const p = loadLocalProfile();
  if (p.username || p.displayName || p.avatarThumbBase64 || p.avatarBase64) {
    updateTelemetryProfile({
      username: normalizeUsername(p.username || ''),
      displayName: (p.displayName || '').trim().slice(0, 60),
      avatarThumb: p.avatarThumbBase64 || (p.avatarBase64?.length <= 35000 ? p.avatarBase64 : '')
    });
  }
}

function loadImg(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Image load failed'));
    img.src = src;
  });
}

function compressCanvasJpeg(canvas, maxChars, startQuality = 0.82) {
  let q = startQuality;
  let cur = canvas;
  for (let i = 0; i < 6; i++) {
    const url = cur.toDataURL('image/jpeg', q);
    if (url.length <= maxChars) return url;
    q = Math.max(0.34, q - 0.12);
    if (q <= 0.46 && cur.width > 340) {
      const next = document.createElement('canvas');
      next.width = Math.max(64, Math.round(cur.width * 0.78));
      next.height = Math.max(64, Math.round(cur.height * 0.78));
      const ctx = next.getContext('2d');
      ctx.fillStyle = '#FFFFFF';
      ctx.fillRect(0, 0, next.width, next.height);
      ctx.drawImage(cur, 0, 0, next.width, next.height);
      cur = next;
    }
  }
  const fallback = cur.toDataURL('image/jpeg', 0.34);
  return fallback.length <= maxChars ? fallback : '';
}

export async function compressAvatarPair(dataUrl) {
  const safe = safeImgSrc(dataUrl);
  if (!safe) return { avatarBase64: '', avatarThumbBase64: '' };
  const img = await loadImg(safe);
  const side = Math.min(img.naturalWidth || img.width || 1, img.naturalHeight || img.height || 1);
  const sx = Math.max(0, Math.floor(((img.naturalWidth || img.width) - side) / 2));
  const sy = Math.max(0, Math.floor(((img.naturalHeight || img.height) - side) / 2));

  const cFull = document.createElement('canvas');
  cFull.width = 200;
  cFull.height = 200;
  const ctxFull = cFull.getContext('2d');
  ctxFull.fillStyle = '#FFFFFF';
  ctxFull.fillRect(0, 0, 200, 200);
  ctxFull.drawImage(img, sx, sy, side, side, 0, 0, 200, 200);
  const avatarBase64 = compressCanvasJpeg(cFull, 70000, 0.82);

  const cThumb = document.createElement('canvas');
  cThumb.width = 88;
  cThumb.height = 88;
  const ctxThumb = cThumb.getContext('2d');
  ctxThumb.fillStyle = '#FFFFFF';
  ctxThumb.fillRect(0, 0, 88, 88);
  ctxThumb.drawImage(cFull, 0, 0, 88, 88);
  const avatarThumbBase64 = compressCanvasJpeg(cThumb, 32000, 0.72);
  return { avatarBase64, avatarThumbBase64 };
}

export async function compressGarmentPhotoForCloud(dataUrl, maxSide = 680, maxChars = 195000) {
  const safe = safeImgSrc(dataUrl);
  if (!safe) return '';
  const img = await loadImg(safe);
  const w = img.naturalWidth || img.width || 1;
  const h = img.naturalHeight || img.height || 1;
  const longest = Math.max(w, h);
  const ratio = longest > maxSide ? maxSide / longest : 1;
  const tw = Math.max(1, Math.round(w * ratio));
  const th = Math.max(1, Math.round(h * ratio));
  const canvas = document.createElement('canvas');
  canvas.width = tw;
  canvas.height = th;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, tw, th);
  ctx.drawImage(img, 0, 0, tw, th);
  return compressCanvasJpeg(canvas, maxChars, 0.78);
}

export function computeFilteredWardrobeSummary(items = [], outfits = [], vis = DEFAULT_STATS_VISIBILITY) {
  const closet = items.filter((i) => !i.isWishlist);
  const wishlist = items.filter((i) => i.isWishlist);
  const out = {};

  if (vis.showTotalItems) {
    out.totalItems = closet.length;
    out.totalPhotos = closet.reduce((acc, it) => acc + ((it.photoFileNames || []).length), 0);
  }
  if (vis.showFavoriteCount) {
    out.favoriteCount = items.filter((i) => i.isFavorite).length;
  }
  if (vis.showWishlistCount) {
    out.wishlistCount = wishlist.length;
  }
  if (vis.showOutfitCount) {
    out.outfitCount = outfits.length;
  }
  if (vis.showCategoryBreakdown) {
    const counts = CATEGORIES
      .map((c) => [c.id, closet.filter((it) => it.category === c.id).length])
      .filter(([, cnt]) => cnt > 0)
      .sort((a, b) => b[1] - a[1]);
    out.categoryCounts = counts.map(([k, v]) => `${k}:${v}`).join(',');
  }
  if (vis.showTopColors) {
    const colCounts = GARMENT_COLORS
      .map((c) => [c.id, closet.filter((it) => (it.colors || []).includes(c.id)).length])
      .filter(([, cnt]) => cnt > 0)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 6);
    out.topColors = colCounts.map(([k, v]) => `${k}:${v}`).join(',');
  }
  if (vis.showColorPalette !== false) {
    const palCounts = GARMENT_COLORS
      .map((c) => [c.id, closet.filter((it) => (it.colors || []).includes(c.id)).length])
      .filter(([, cnt]) => cnt > 0)
      .sort((a, b) => b[1] - a[1]);
    if (palCounts.length) {
      out.colorPalette = palCounts.map(([k, v]) => `${k}:${v}`).join(',');
    }
  }
  if (vis.showTopSubCategories) {
    const subMap = new Map();
    closet.forEach((it) => {
      if (it.subCategory) subMap.set(it.subCategory, (subMap.get(it.subCategory) || 0) + 1);
    });
    const sorted = [...subMap.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6);
    out.topSubCategories = sorted.map(([k, v]) => `${k}:${v}`).join(',');
  }
  if (vis.showEstimatedValue) {
    const wishVal = wishlist.reduce((acc, it) => acc + (parsePrice(it.priceText) || 0), 0);
    const closetVal = closet.reduce((acc, it) => acc + (parsePrice(it.priceText) || 0), 0);
    out.estimatedWishlistValue = Math.round(wishVal);
    out.estimatedWardrobeValue = Math.round(closetVal);
  }
  return out;
}

// ==================== Firestore Value Helpers ====================

const strVal = (v) => ({ stringValue: String(v ?? '') });
const intVal = (v) => ({ integerValue: String(Math.trunc(Number(v) || 0)) });
const boolVal = (v) => ({ booleanValue: Boolean(v) });
const strArrVal = (arr) => ({ arrayValue: { values: (arr || []).map((x) => strVal(x)) } });

const fsStr = (fields, k) => fields?.[k]?.stringValue ?? '';
const fsInt = (fields, k) => Number.parseInt(fields?.[k]?.integerValue ?? '0', 10) || 0;
const fsBool = (fields, k, def = false) =>
  fields?.[k] && 'booleanValue' in fields[k] ? Boolean(fields[k].booleanValue) : def;
const fsStrArr = (fields, k) =>
  (fields?.[k]?.arrayValue?.values || []).map((v) => v?.stringValue ?? '').filter(Boolean);

async function execFs(method, url, token, bodyObj = null) {
  const isPatch = method === 'PATCH';
  const res = await fetch(url, {
    method: isPatch ? 'POST' : method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(isPatch ? { 'X-HTTP-Method-Override': 'PATCH' } : {}),
      ...(bodyObj ? { 'Content-Type': 'application/json' } : {})
    },
    ...(bodyObj ? { body: JSON.stringify(bodyObj) } : {})
  });
  let data = null;
  try {
    data = await res.json();
  } catch {
    data = null;
  }
  return { ok: res.ok, status: res.status, data };
}

export function formatFsError(status, dataOrText, actionLabel) {
  let msg = '';
  if (dataOrText && typeof dataOrText === 'object') {
    msg = String(dataOrText?.error?.message || '').trim();
  } else if (typeof dataOrText === 'string') {
    try { msg = String(JSON.parse(dataOrText)?.error?.message || '').trim(); } catch { msg = ''; }
  }
  let friendly = `Kod ${status}`;
  if (!status) friendly = 'Sunucuya bağlanılamadı';
  else if (status === 403 || /insufficient permissions/i.test(msg)) friendly = 'Sunucu erişim izni reddedildi (403)';
  else if (status === 404) friendly = 'Kayıt bulunamadı (404)';
  else if (status === 429) friendly = 'Çok fazla istek gönderildi, biraz bekle';
  else if (msg) friendly = msg.slice(0, 120);
  return `${actionLabel}: ${friendly}`;
}

async function listCollectionDocs(token, path) {
  const docs = [];
  let pageToken = '';
  for (let i = 0; i < 5; i++) {
    const qs = pageToken ? `&pageToken=${encodeURIComponent(pageToken)}` : '';
    const res = await execFs('GET', `${FS_BASE}/${path}?pageSize=200${qs}`, token);
    if (!res.ok) break;
    const arr = res.data?.documents || [];
    docs.push(...arr);
    pageToken = res.data?.nextPageToken || '';
    if (!pageToken) break;
  }
  return docs;
}

async function queryBySingleField(token, collectionId, fieldPath, stringValue) {
  const res = await execFs('POST', `${FS_BASE}:runQuery`, token, {
    structuredQuery: {
      from: [{ collectionId }],
      where: {
        fieldFilter: {
          field: { fieldPath },
          op: 'EQUAL',
          value: strVal(stringValue)
        }
      },
      limit: 200
    }
  });
  const error = Array.isArray(res.data) && res.data.find((row) => row.error)?.error;
  if (!res.ok || !Array.isArray(res.data) || error) {
    const code = error?.code || res.status;
    throw new Error(code === 429 ? 'Okuma kotasına ulaşıldı (429). Daha sonra tekrar dene.' : `Takip bilgileri yüklenemedi (${code})`);
  }
  return res.data.map((row) => row?.document).filter(Boolean);
}

export async function fetchPublicProfileHeader(idToken, uid) {
  const response = await execFs('GET', `${FS_BASE}/public_profiles/${encodeURIComponent(uid)}`, idToken);
  return response.ok ? parsePublicProfileDoc(response.data) : null;
}

function parsePublicProfileDoc(doc) {
  const f = doc?.fields;
  if (!f) return null;
  const docUid = (doc.name || '').split('/').pop() || '';
  const uid = fsStr(f, 'uid') || docUid;
  if (!uid) return null;
  const username = fsStr(f, 'username');
  return {
    uid,
    username,
    usernameLower: fsStr(f, 'usernameLower') || normalizeUsername(username),
    displayName: fsStr(f, 'displayName'),
    bio: fsStr(f, 'bio'),
    avatarBase64: fsStr(f, 'avatarBase64'),
    styleTags: fsStrArr(f, 'styleTags'),
    isPrivate: fsBool(f, 'isPrivate', false),
    updatedAtMs: fsInt(f, 'updatedAtMs')
  };
}

function parseProfileContentDoc(doc) {
  const f = doc?.fields;
  if (!f) return null;
  const visF = f.statsVisibility?.mapValue?.fields || {};
  const sumF = f.wardrobeSummary?.mapValue?.fields || {};
  const wardrobeSummary = {};
  for (const [k, obj] of Object.entries(sumF)) {
    if ('integerValue' in obj) wardrobeSummary[k] = Number.parseInt(obj.integerValue, 10) || 0;
    else if ('stringValue' in obj) wardrobeSummary[k] = obj.stringValue;
  }
  return {
    ownerUid: fsStr(f, 'ownerUid'),
    statsVisibility: {
      showTotalItems: fsBool(visF, 'showTotalItems', true),
      showCategoryBreakdown: fsBool(visF, 'showCategoryBreakdown', true),
      showFavoriteCount: fsBool(visF, 'showFavoriteCount', true),
      showWishlistCount: fsBool(visF, 'showWishlistCount', true),
      showOutfitCount: fsBool(visF, 'showOutfitCount', true),
      showTopColors: fsBool(visF, 'showTopColors', true),
      showColorPalette: fsBool(visF, 'showColorPalette', true),
      showTopSubCategories: fsBool(visF, 'showTopSubCategories', true),
      showEstimatedValue: fsBool(visF, 'showEstimatedValue', false)
    },
    wardrobeSummary,
    showcaseSections: fsStrArr(f, 'showcaseSections'),
    updatedAtMs: fsInt(f, 'updatedAtMs')
  };
}

function parseShowcaseDoc(doc) {
  const f = doc?.fields;
  if (!f) return null;
  const docId = (doc.name || '').split('/').pop() || '';
  const itemId = fsStr(f, 'itemId') || docId;
  const name = fsStr(f, 'name');
  if (!itemId || !name) return null;
  return {
    ownerUid: fsStr(f, 'ownerUid'),
    itemId,
    section: fsStr(f, 'section') || 'selected',
    name,
    category: fsStr(f, 'category'),
    subCategory: fsStr(f, 'subCategory'),
    styleTag: fsStr(f, 'styleTag'),
    seasons: fsStrArr(f, 'seasons'),
    colors: fsStrArr(f, 'colors'),
    priceText: fsStr(f, 'priceText'),
    productUrl: fsStr(f, 'productUrl'),
    notes: fsStr(f, 'notes'),
    photoBase64: fsStr(f, 'photoBase64'),
    sortOrder: fsInt(f, 'sortOrder'),
    createdAtMs: fsInt(f, 'createdAtMs')
  };
}

function parseFollowDoc(doc) {
  const f = doc?.fields;
  if (!f) return null;
  const followId = (doc.name || '').split('/').pop() || '';
  const followerUid = fsStr(f, 'followerUid');
  const targetUid = fsStr(f, 'targetUid');
  if (!followerUid || !targetUid) return null;
  return {
    followId,
    followerUid,
    targetUid,
    status: fsStr(f, 'status'),
    createdAtMs: fsInt(f, 'createdAtMs'),
    updatedAtMs: fsInt(f, 'updatedAtMs')
  };
}

function randomShareId() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789_-';
  const arr = new Uint32Array(12);
  if (window.crypto?.getRandomValues) {
    window.crypto.getRandomValues(arr);
  } else {
    for (let i = 0; i < 12; i++) arr[i] = Math.floor(Math.random() * 1e9);
  }
  return Array.from(arr, (n) => chars[n % chars.length]).join('');
}

// ==================== Public Cloud Operations ====================

export async function checkUsernameAvailable(raw) {
  const norm = normalizeUsername(raw);
  if (!isValidUsername(norm)) return false;
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Sunucuya bağlanılamadı');
  const res = await execFs('GET', `${FS_BASE}/usernames/${encodeURIComponent(norm)}`, auth.idToken);
  if (res.status === 404) return true;
  if (res.ok) return fsStr(res.data?.fields, 'uid') === auth.uid;
  throw new Error(formatFsError(res.status, res.data, 'Kontrol edilemedi'));
}

function itemCloudPhotoSrc(item, photoMap) {
  const ids = item.useOriginalPhotos && item.originalPhotoFileNames?.length
    ? item.originalPhotoFileNames
    : (item.useCatalogPhotos && item.catalogPhotoFileNames?.some(Boolean)
      ? (item.photoFileNames || []).map((c, i) => item.catalogPhotoFileNames[i] || c)
      : (item.photoFileNames || []));
  for (const id of ids) {
    const src = photoMap.get(id);
    if (src) return src;
  }
  return '';
}

function allItemCloudPhotoSrcs(item, photoMap) {
  const ids = item.useOriginalPhotos && item.originalPhotoFileNames?.length
    ? item.originalPhotoFileNames
    : (item.useCatalogPhotos && item.catalogPhotoFileNames?.some(Boolean)
      ? (item.photoFileNames || []).map((c, i) => item.catalogPhotoFileNames[i] || c)
      : (item.photoFileNames || []));
  return ids.slice(0, 3).map((id) => photoMap.get(id)).filter(Boolean);
}

export async function saveAndSyncProfile(draft, items, outfits, photoMap, lang = 'TR') {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error(localizedText(lang, 'Sunucuya bağlanılamadı', 'Connection failed'));
  const { uid, idToken } = auth;
  const prev = loadLocalProfile();

  const cleanUser = normalizeUsername(draft.username);
  if (cleanUser && !isValidUsername(cleanUser)) {
    throw new Error(localizedText(lang, 'Kullanıcı adı 3-24 karakter olmalı (a-z, 0-9, _, .)', 'Username must be 3-24 chars (a-z, 0-9, _, .)'));
  }
  const cleanDisplay = (draft.displayName || '').trim().slice(0, 50) || cleanUser;
  const cleanBio = (draft.bio || '').trim().slice(0, 220);
  const cleanTags = [...new Set((draft.styleTags || []).map((t) => String(t).trim().slice(0, 24)).filter(Boolean))].slice(0, 4);
  const nowMs = safeNowMs();

  if (cleanUser) {
    const check = await execFs('GET', `${FS_BASE}/usernames/${encodeURIComponent(cleanUser)}`, idToken);
    if (check.ok) {
      const owner = fsStr(check.data?.fields, 'uid');
      if (owner !== uid) {
        throw new Error(localizedText(lang, `Bu kullanıcı adı (@${cleanUser}) başka biri tarafından alınmış`, `Username @${cleanUser} is already taken`));
      }
    }
    const uRes = await execFs('PATCH', `${FS_BASE}/usernames/${encodeURIComponent(cleanUser)}`, idToken, {
      fields: {
        uid: strVal(uid),
        username: strVal(cleanUser),
        updatedAtMs: intVal(nowMs)
      }
    });
    if (!uRes.ok) {
      throw new Error(formatFsError(uRes.status, uRes.data, localizedText(lang, 'Kullanıcı adı kaydedilemedi', 'Username save failed')));
    }
  }
  const oldUser = normalizeUsername(prev.username);
  if (oldUser && oldUser !== cleanUser) {
    await execFs('DELETE', `${FS_BASE}/usernames/${encodeURIComponent(oldUser)}`, idToken).catch(() => {});
  }

  const avatarForPublic = isSafeImageDataUrl(draft.avatarBase64 || '', 75000) ? draft.avatarBase64 : '';
  const avatarThumb = isSafeImageDataUrl(draft.avatarThumbBase64 || '', 35000)
    ? draft.avatarThumbBase64
    : (avatarForPublic.length <= 35000 ? avatarForPublic : '');

  const pubRes = await execFs('PATCH', `${FS_BASE}/public_profiles/${encodeURIComponent(uid)}`, idToken, {
    fields: {
      uid: strVal(uid),
      username: strVal(cleanUser),
      usernameLower: strVal(cleanUser),
      displayName: strVal(cleanDisplay),
      displayNameLower: strVal(cleanDisplay.toLowerCase().replace(/\u0307/g, '').slice(0, 50)),
      bio: strVal(cleanBio),
      avatarBase64: strVal(avatarForPublic),
      styleTags: strArrVal(cleanTags),
      isPrivate: boolVal(Boolean(draft.isPrivate)),
      updatedAtMs: intVal(nowMs)
    }
  });
  if (!pubRes.ok) {
    throw new Error(formatFsError(pubRes.status, pubRes.data, localizedText(lang, 'Profil kaydedilemedi', 'Profile save failed')));
  }

  const vis = { ...DEFAULT_STATS_VISIBILITY, ...(draft.statsVisibility || {}) };
  const visFields = {};
  Object.entries(vis).forEach(([k, v]) => { visFields[k] = boolVal(Boolean(v)); });

  const sumRaw = computeFilteredWardrobeSummary(items, outfits, vis);
  const sumFields = {};
  Object.entries(sumRaw).forEach(([k, v]) => {
    sumFields[k] = typeof v === 'number' ? intVal(v) : strVal(String(v));
  });

  const cleanSections = [...new Set((draft.showcaseSections || []).filter((s) => ['favorites', 'wishlist', 'selected', 'custom'].includes(s)))];
  const contentRes = await execFs('PATCH', `${FS_BASE}/public_profiles/${encodeURIComponent(uid)}/content/main`, idToken, {
    fields: {
      ownerUid: strVal(uid),
      statsVisibility: { mapValue: { fields: visFields } },
      wardrobeSummary: { mapValue: { fields: sumFields } },
      showcaseSections: strArrVal(cleanSections),
      updatedAtMs: intVal(nowMs)
    }
  });
  if (!contentRes.ok) {
    throw new Error(formatFsError(contentRes.status, contentRes.data, localizedText(lang, 'Profil içeriği kaydedilemedi', 'Profile content save failed')));
  }

  // Reconcile showcase items
  const desired = [];
  const usedIds = new Set();
  let order = 0;

  const pushWardrobe = async (it, section) => {
    if (desired.length >= 24) return;
    const docId = `${section}_${String(it.id)}`.slice(0, 76).replace(/[^A-Za-z0-9_-]/g, '_');
    if (usedIds.has(docId)) return;
    usedIds.add(docId);
    const rawSrc = itemCloudPhotoSrc(it, photoMap);
    const photoBase64 = rawSrc ? await compressGarmentPhotoForCloud(rawSrc, 680, 190000).catch(() => '') : '';
    const itemName = (it.name || displaySubCategory(it.subCategory, lang) || 'Kıyafet').trim().slice(0, 120) || 'Kıyafet';
    desired.push({
      ownerUid: uid,
      itemId: docId,
      section,
      name: itemName,
      category: String(it.category || 'TOP').slice(0, 32),
      subCategory: String(it.subCategory || '').slice(0, 32),
      styleTag: String(it.styleTag || '').slice(0, 32),
      seasons: (it.seasons || ['ALL']).map((s) => String(s).slice(0, 32)).filter(Boolean).slice(0, 5),
      colors: (it.colors || []).map((c) => String(c).slice(0, 32)).filter(Boolean).slice(0, 8),
      priceText: String(it.priceText || '').slice(0, 40),
      productUrl: String(it.productUrl || '').slice(0, 1000),
      notes: String(it.notes || '').slice(0, 320),
      photoBase64: photoBase64.slice(0, 235000),
      sortOrder: order++,
      createdAtMs: Math.max(MIN_VALID_EPOCH_MS, Number(it.createdAt) || nowMs)
    });
  };

  if (cleanSections.includes('selected')) {
    const sel = new Set(draft.selectedShowcaseItemIds || []);
    for (const it of items.filter((x) => sel.has(x.id)).slice(0, 12)) await pushWardrobe(it, 'selected');
  }
  if (cleanSections.includes('favorites')) {
    for (const it of items.filter((x) => x.isFavorite).slice(0, 12)) await pushWardrobe(it, 'favorites');
  }
  if (cleanSections.includes('wishlist')) {
    for (const it of items.filter((x) => x.isWishlist).slice(0, 10)) await pushWardrobe(it, 'wishlist');
  }
  if (cleanSections.includes('custom')) {
    for (const cu of (draft.customShowcaseItems || []).slice(0, 8)) {
      if (desired.length >= 28) break;
      const docId = `custom_${String(cu.id || 'item')}`.slice(0, 76).replace(/[^A-Za-z0-9_-]/g, '_');
      if (usedIds.has(docId)) continue;
      usedIds.add(docId);
      const rawCreated = Number(cu.createdAtMs) || nowMs;
      desired.push({
        ownerUid: uid,
        itemId: docId,
        section: 'custom',
        name: String(cu.name || '').trim().slice(0, 120) || 'Özel Parça',
        category: String(cu.category || 'TOP').slice(0, 32),
        subCategory: '',
        styleTag: '',
        seasons: ['ALL'],
        colors: [],
        priceText: String(cu.priceText || '').slice(0, 40),
        productUrl: String(cu.productUrl || '').slice(0, 1000),
        notes: String(cu.notes || '').slice(0, 320),
        photoBase64: String(cu.photoBase64 || '').slice(0, 230000),
        sortOrder: order++,
        createdAtMs: (rawCreated >= MIN_VALID_EPOCH_MS && rawCreated <= nowMs) ? rawCreated : nowMs
      });
    }
  }

  const existingDocs = await listCollectionDocs(idToken, `public_profiles/${encodeURIComponent(uid)}/showcase`);
  for (const doc of existingDocs) {
    const docId = (doc.name || '').split('/').pop() || '';
    if (docId && !usedIds.has(docId)) {
      await execFs('DELETE', `${FS_BASE}/public_profiles/${encodeURIComponent(uid)}/showcase/${encodeURIComponent(docId)}`, idToken).catch(() => {});
    }
  }
  for (const sc of desired) {
    await execFs('PATCH', `${FS_BASE}/public_profiles/${encodeURIComponent(uid)}/showcase/${encodeURIComponent(sc.itemId)}`, idToken, {
      fields: {
        ownerUid: strVal(sc.ownerUid),
        itemId: strVal(sc.itemId),
        section: strVal(sc.section),
        name: strVal(sc.name),
        category: strVal(sc.category),
        subCategory: strVal(sc.subCategory),
        styleTag: strVal(sc.styleTag),
        seasons: strArrVal(sc.seasons),
        colors: strArrVal(sc.colors),
        priceText: strVal(sc.priceText),
        productUrl: strVal(sc.productUrl),
        notes: strVal(sc.notes),
        photoBase64: strVal(sc.photoBase64),
        sortOrder: intVal(sc.sortOrder),
        createdAtMs: intVal(sc.createdAtMs)
      }
    }).catch(() => {});
  }

  const finalProfile = {
    ...draft,
    username: cleanUser,
    displayName: cleanDisplay,
    bio: cleanBio,
    avatarBase64: avatarForPublic,
    avatarThumbBase64: avatarThumb,
    styleTags: cleanTags,
    isPrivate: Boolean(draft.isPrivate),
    statsVisibility: vis,
    showcaseSections: cleanSections,
    lastSyncedAtMs: nowMs
  };
  saveLocalProfile(finalProfile);
  if (Boolean(prev.isPrivate) !== Boolean(finalProfile.isPrivate)) {
    await migrateMyPostsForPrivacy(uid, Boolean(finalProfile.isPrivate)).catch(() => {});
  }
  trackFeature('profile_save', finalProfile.isPrivate ? 'private' : 'public');
  return finalProfile;
}

export async function shareGarment(item, profile, photoMap, lang = 'TR') {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error(localizedText(lang, 'Sunucuya bağlanılamadı', 'Connection failed'));
  const { uid, idToken } = auth;

  const rawSrcs = allItemCloudPhotoSrcs(item, photoMap);
  const photos = [];
  for (const src of rawSrcs) {
    const c = await compressGarmentPhotoForCloud(src, 680, 195000).catch(() => '');
    if (c) photos.push(c);
  }
  if (photos.length === 0) {
    throw new Error(localizedText(lang, 'Paylaşılacak kıyafet fotoğrafı bulunamadı', 'No garment photo found to share'));
  }

  const shareId = randomShareId();
  const nowMs = safeNowMs();
  const cleanUser = normalizeUsername(profile?.username || '');
  const cleanDisplay = (profile?.displayName || '').trim().slice(0, 50) || cleanUser;
  const cleanAvatar = isSafeImageDataUrl(profile?.avatarThumbBase64 || '', 39000) ? profile.avatarThumbBase64 : '';
  const itemName = (item.name || displaySubCategory(item.subCategory, lang) || 'Kıyafet').trim().slice(0, 120) || 'Kıyafet';

  const res = await execFs('PATCH', `${FS_BASE}/shared_items/${encodeURIComponent(shareId)}`, idToken, {
    fields: {
      shareId: strVal(shareId),
      ownerUid: strVal(uid),
      ownerUsername: strVal(cleanUser),
      ownerDisplayName: strVal(cleanDisplay),
      ownerAvatar: strVal(cleanAvatar),
      name: strVal(itemName),
      category: strVal(String(item.category || 'TOP').slice(0, 32)),
      subCategory: strVal(String(item.subCategory || '').slice(0, 32)),
      styleTag: strVal(String(item.styleTag || '').slice(0, 32)),
      seasons: strArrVal((item.seasons || ['ALL']).map((s) => String(s).slice(0, 32)).filter(Boolean).slice(0, 5)),
      colors: strArrVal((item.colors || []).map((c) => String(c).slice(0, 32)).filter(Boolean).slice(0, 8)),
      notes: strVal(String(item.notes || '').slice(0, 600)),
      priceText: strVal(String(item.priceText || '').slice(0, 40)),
      productUrl: strVal(String(item.productUrl || '').slice(0, 1000)),
      isWishlist: boolVal(Boolean(item.isWishlist)),
      photos: strArrVal(photos),
      createdAtMs: intVal(nowMs)
    }
  });
  if (!res.ok) {
    throw new Error(formatFsError(res.status, res.data, localizedText(lang, 'Paylaşım oluşturulamadı', 'Share creation failed')));
  }
  trackFeature('garment_share', item.category || 'TOP');
  return {
    shareId,
    ownerUid: uid,
    ownerUsername: cleanUser,
    ownerDisplayName: cleanDisplay,
    ownerAvatar: cleanAvatar,
    name: itemName,
    category: item.category || 'TOP',
    subCategory: item.subCategory || '',
    styleTag: item.styleTag || '',
    seasons: item.seasons || ['ALL'],
    colors: item.colors || [],
    notes: item.notes || '',
    priceText: item.priceText || '',
    productUrl: item.productUrl || '',
    isWishlist: Boolean(item.isWishlist),
    photos,
    createdAtMs: nowMs
  };
}

export async function updateSharedItemOwner(shared, profile) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Sunucuya bağlanılamadı');
  const { uid, idToken } = auth;
  const cleanUser = normalizeUsername(profile?.username || '');
  const cleanDisplay = (profile?.displayName || '').trim().slice(0, 50) || cleanUser;
  const cleanAvatar = isSafeImageDataUrl(profile?.avatarThumbBase64 || '', 39000) ? profile.avatarThumbBase64 : '';

  const res = await execFs('PATCH', `${FS_BASE}/shared_items/${encodeURIComponent(shared.shareId)}`, idToken, {
    fields: {
      shareId: strVal(shared.shareId),
      ownerUid: strVal(uid),
      ownerUsername: strVal(cleanUser),
      ownerDisplayName: strVal(cleanDisplay),
      ownerAvatar: strVal(cleanAvatar),
      name: strVal(String(shared.name || '').slice(0, 120)),
      category: strVal(String(shared.category || 'TOP').slice(0, 32)),
      subCategory: strVal(String(shared.subCategory || '').slice(0, 32)),
      styleTag: strVal(String(shared.styleTag || '').slice(0, 32)),
      seasons: strArrVal((shared.seasons || []).slice(0, 5)),
      colors: strArrVal((shared.colors || []).slice(0, 8)),
      notes: strVal(String(shared.notes || '').slice(0, 600)),
      priceText: strVal(String(shared.priceText || '').slice(0, 40)),
      productUrl: strVal(String(shared.productUrl || '').slice(0, 1000)),
      isWishlist: boolVal(Boolean(shared.isWishlist)),
      photos: strArrVal((shared.photos || []).slice(0, 3)),
      createdAtMs: intVal(shared.createdAtMs)
    }
  });
  if (!res.ok) throw new Error(formatFsError(res.status, res.data, 'Paylaşım güncellenemedi'));
  return { ...shared, ownerUsername: cleanUser, ownerDisplayName: cleanDisplay, ownerAvatar: cleanAvatar };
}

export async function fetchSharedItem(shareId) {
  if (!SHARE_ID_REGEX.test(shareId)) return null;
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const res = await execFs('GET', `${FS_BASE}/shared_items/${encodeURIComponent(shareId)}`, auth.idToken);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(formatFsError(res.status, res.data, 'Kıyafet alınamadı'));
  const f = res.data?.fields;
  if (!f) return null;
  trackFeature('shared_item_open');
  return {
    shareId: fsStr(f, 'shareId') || shareId,
    ownerUid: fsStr(f, 'ownerUid'),
    ownerUsername: fsStr(f, 'ownerUsername'),
    ownerDisplayName: fsStr(f, 'ownerDisplayName'),
    ownerAvatar: fsStr(f, 'ownerAvatar'),
    name: fsStr(f, 'name'),
    category: fsStr(f, 'category'),
    subCategory: fsStr(f, 'subCategory'),
    styleTag: fsStr(f, 'styleTag'),
    seasons: fsStrArr(f, 'seasons'),
    colors: fsStrArr(f, 'colors'),
    notes: fsStr(f, 'notes'),
    priceText: fsStr(f, 'priceText'),
    productUrl: fsStr(f, 'productUrl'),
    isWishlist: fsBool(f, 'isWishlist', false),
    photos: fsStrArr(f, 'photos'),
    createdAtMs: fsInt(f, 'createdAtMs')
  };
}

async function fetchBlockedUidSets(idToken, myUid) {
  const [byMeDocs, againstMeDocs] = await Promise.all([
    queryBySingleField(idToken, 'blocks', 'blockerUid', myUid),
    queryBySingleField(idToken, 'blocks', 'blockedUid', myUid)
  ]);
  const blockedByMe = new Set(byMeDocs.map((d) => fsStr(d?.fields, 'blockedUid')).filter(Boolean));
  const blockedMe = new Set(againstMeDocs.map((d) => fsStr(d?.fields, 'blockerUid')).filter(Boolean));
  return { blockedByMe, blockedMe };
}

export async function fetchBlockedAccounts() {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  const { blockedByMe } = await fetchBlockedUidSets(idToken, myUid);
  const profiles = await Promise.all([...blockedByMe].map(async (otherUid) => {
    const pRes = await execFs('GET', `${FS_BASE}/public_profiles/${encodeURIComponent(otherUid)}`, idToken);
    if (pRes.ok) {
      const p = parsePublicProfileDoc(pRes.data);
      if (p) return p;
    }
    return { uid: otherUid, username: '', usernameLower: '', displayName: 'Kullanıcı', bio: '', avatarBase64: '', styleTags: [], isPrivate: false, updatedAtMs: 0 };
  }));
  return profiles;
}

export async function blockUser(targetUid) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  if (!targetUid || myUid === targetUid) throw new Error('Kendini engelleyemezsin');
  const blockId = `${myUid}_${targetUid}`;
  const existRes = await execFs('GET', `${FS_BASE}/blocks/${encodeURIComponent(blockId)}`, idToken);
  if (!existRes.ok) {
    const patchRes = await execFs(
      'PATCH',
      `${FS_BASE}/blocks/${encodeURIComponent(blockId)}?currentDocument.exists=false`,
      idToken,
      {
        fields: {
          blockerUid: strVal(myUid),
          blockedUid: strVal(targetUid),
          createdAtMs: intVal(safeNowMs())
        }
      }
    );
    if (!patchRes.ok && patchRes.status !== 409) {
      throw new Error(formatFsError(patchRes.status, patchRes.data, 'Kullanıcı engellenemedi'));
    }
  }
  await Promise.all([
    execFs('DELETE', `${FS_BASE}/follows/${encodeURIComponent(`${myUid}_${targetUid}`)}`, idToken).catch(() => {}),
    execFs('DELETE', `${FS_BASE}/follows/${encodeURIComponent(`${targetUid}_${myUid}`)}`, idToken).catch(() => {})
  ]);
  trackFeature('user_block');
}

export async function unblockUser(targetUid) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  if (!targetUid || myUid === targetUid) return;
  const blockId = `${myUid}_${targetUid}`;
  const res = await execFs('DELETE', `${FS_BASE}/blocks/${encodeURIComponent(blockId)}`, idToken);
  if (!res.ok && res.status !== 404) {
    throw new Error(formatFsError(res.status, res.data, 'Engel kaldırılamadı'));
  }
  trackFeature('user_unblock');
}

export async function searchProfiles(query = '') {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  if (String(query).trim()) trackFeature('user_search');
  const { blockedByMe, blockedMe } = await fetchBlockedUidSets(auth.idToken, auth.uid);
  const docs = await listCollectionDocs(auth.idToken, 'public_profiles');
  const qNorm = normalizeUsername(query);
  const rawQ = String(query || '').trim().toLowerCase();
  return docs
    .map(parsePublicProfileDoc)
    .filter((p) => p && !blockedByMe.has(p.uid) && !blockedMe.has(p.uid))
    .filter((p) => (p.username || p.displayName))
    .filter((p) => {
      if (!rawQ) return Boolean(p.username);
      return p.usernameLower.includes(qNorm) || p.displayName.toLowerCase().includes(rawQ);
    })
    .sort((a, b) => (b.updatedAtMs || 0) - (a.updatedAtMs || 0))
    .slice(0, 40);
}

export async function fetchProfileBundle(targetUidOrUsername, isUsername = false) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;

  let resolvedUid = targetUidOrUsername;
  if (isUsername) {
    const norm = normalizeUsername(targetUidOrUsername);
    const uRes = await execFs('GET', `${FS_BASE}/usernames/${encodeURIComponent(norm)}`, idToken);
    if (uRes.ok) {
      resolvedUid = fsStr(uRes.data?.fields, 'uid');
    } else {
      const docs = await listCollectionDocs(idToken, 'public_profiles');
      const found = docs.map(parsePublicProfileDoc).find((p) => p && p.usernameLower === norm);
      resolvedUid = found?.uid || '';
    }
  }
  if (!resolvedUid) return null;

  const pRes = await execFs('GET', `${FS_BASE}/public_profiles/${encodeURIComponent(resolvedUid)}`, idToken);
  if (pRes.status === 404) return null;
  if (!pRes.ok) throw new Error(formatFsError(pRes.status, pRes.data, 'Profil yüklenemedi'));
  const header = parsePublicProfileDoc(pRes.data);
  if (!header) return null;

  const { blockedByMe, blockedMe } = await fetchBlockedUidSets(idToken, myUid);
  const isBlockedByMe = resolvedUid !== myUid && blockedByMe.has(resolvedUid);
  const hasBlockedMe = resolvedUid !== myUid && blockedMe.has(resolvedUid);
  if (isBlockedByMe || hasBlockedMe) {
    return {
      myUid,
      header,
      canViewContent: false,
      followStatus: null,
      followersCount: 0,
      followingCount: 0,
      pendingRequestUids: [],
      content: null,
      showcaseItems: [],
      isBlockedByMe,
      hasBlockedMe
    };
  }

  const [followersRaw, followingRaw] = await Promise.all([
    queryBySingleField(idToken, 'follows', 'targetUid', resolvedUid).then((d) => d.map(parseFollowDoc).filter(Boolean)),
    queryBySingleField(idToken, 'follows', 'followerUid', resolvedUid).then((d) => d.map(parseFollowDoc).filter(Boolean))
  ]);
  const followersList = followersRaw.filter((r) => !blockedByMe.has(r.followerUid) && !blockedMe.has(r.followerUid));
  const followingList = followingRaw.filter((r) => !blockedByMe.has(r.targetUid) && !blockedMe.has(r.targetUid));

  const followersCount = followersList.filter((r) => r.status === 'accepted').length;
  const followingCount = followingList.filter((r) => r.status === 'accepted').length;
  const pendingRequestUids = resolvedUid === myUid
    ? followersList.filter((r) => r.status === 'pending').map((r) => r.followerUid)
    : [];
  const myRel = resolvedUid === myUid ? null : followersList.find((r) => r.followerUid === myUid);
  const canViewContent = (resolvedUid === myUid) || !header.isPrivate || (myRel?.status === 'accepted');

  let content = null;
  let showcaseItems = [];
  if (canViewContent) {
    const [cRes, scDocs] = await Promise.all([
      execFs('GET', `${FS_BASE}/public_profiles/${encodeURIComponent(resolvedUid)}/content/main`, idToken),
      listCollectionDocs(idToken, `public_profiles/${encodeURIComponent(resolvedUid)}/showcase`)
    ]);
    if (cRes.ok) content = parseProfileContentDoc(cRes.data);
    showcaseItems = scDocs.map(parseShowcaseDoc).filter(Boolean).sort((a, b) => a.sortOrder - b.sortOrder);
  }

  return {
    myUid,
    header,
    canViewContent,
    followStatus: myRel?.status || null,
    followersCount,
    followingCount,
    pendingRequestUids,
    content,
    showcaseItems,
    isBlockedByMe: false,
    hasBlockedMe: false
  };
}

function requireLocalUsernameOrThrow() {
  const me = loadLocalProfile();
  if (!isValidUsername(me?.username || '')) {
    const err = new Error('Devam etmek için önce bir kullanıcı adı oluşturmalısın');
    err.code = 'USERNAME_REQUIRED';
    throw err;
  }
  return me;
}

export async function followUser(targetProfile) {
  requireLocalUsernameOrThrow();
  if (!isValidUsername(targetProfile?.username || '')) {
    throw new Error('Bu kullanıcı henüz kullanıcı adı oluşturmamış');
  }
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  if (myUid === targetProfile.uid) throw new Error('Kendini takip edemezsin');
  const { blockedByMe, blockedMe } = await fetchBlockedUidSets(idToken, myUid);
  if (blockedByMe.has(targetProfile.uid) || blockedMe.has(targetProfile.uid)) {
    throw new Error('Engellenen kullanıcı takip edilemez');
  }
  const followId = `${myUid}_${targetProfile.uid}`;
  const status = targetProfile.isPrivate ? 'pending' : 'accepted';
  const nowMs = safeNowMs();
  const res = await execFs('PATCH', `${FS_BASE}/follows/${encodeURIComponent(followId)}`, idToken, {
    fields: {
      followerUid: strVal(myUid),
      targetUid: strVal(targetProfile.uid),
      status: strVal(status),
      createdAtMs: intVal(nowMs),
      updatedAtMs: intVal(nowMs)
    }
  });
  if (!res.ok) throw new Error(formatFsError(res.status, res.data, 'Takip işlemi başarısız'));
  trackFeature('user_follow', status);
  sendNotificationBestEffort({
    targetUid: targetProfile.uid,
    type: status === 'pending' ? 'follow_request' : 'new_follower',
    profile: loadLocalProfile(),
    previewText: status === 'pending' ? 'seni takip etmek istedi' : 'seni takip etmeye başladı'
  }).catch(() => {});
  return { followId, followerUid: myUid, targetUid: targetProfile.uid, status, createdAtMs: nowMs, updatedAtMs: nowMs };
}

export async function unfollowOrCancel(followerUid, targetUid) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const followId = `${followerUid}_${targetUid}`;
  const res = await execFs('DELETE', `${FS_BASE}/follows/${encodeURIComponent(followId)}`, auth.idToken);
  if (!res.ok && res.status !== 404) throw new Error(formatFsError(res.status, res.data, 'İşlem başarısız'));
}

export async function acceptFollowRequest(followerUid) {
  requireLocalUsernameOrThrow();
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  const followId = `${followerUid}_${myUid}`;
  const gRes = await execFs('GET', `${FS_BASE}/follows/${encodeURIComponent(followId)}`, idToken);
  if (!gRes.ok) throw new Error('İstek bulunamadı');
  const createdAtMs = Math.max(MIN_VALID_EPOCH_MS, fsInt(gRes.data?.fields, 'createdAtMs'));
  const nowMs = Math.max(createdAtMs, safeNowMs());
  const pRes = await execFs('PATCH', `${FS_BASE}/follows/${encodeURIComponent(followId)}`, idToken, {
    fields: {
      followerUid: strVal(followerUid),
      targetUid: strVal(myUid),
      status: strVal('accepted'),
      createdAtMs: intVal(createdAtMs),
      updatedAtMs: intVal(nowMs)
    }
  });
  if (!pRes.ok) throw new Error(formatFsError(pRes.status, pRes.data, 'Onaylanamadı'));
  trackFeature('follow_accept');
  sendNotificationBestEffort({
    targetUid: followerUid,
    type: 'follow_accepted',
    profile: loadLocalProfile(),
    previewText: 'takip isteğini kabul etti'
  }).catch(() => {});
}

export async function fetchPendingFollowRequests() {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  const { blockedByMe, blockedMe } = await fetchBlockedUidSets(idToken, myUid);
  const rels = (await queryBySingleField(idToken, 'follows', 'targetUid', myUid))
    .map(parseFollowDoc)
    .filter((r) => r && r.status === 'pending' && !blockedByMe.has(r.followerUid) && !blockedMe.has(r.followerUid));
  const out = [];
  for (const r of rels) {
    const pRes = await execFs('GET', `${FS_BASE}/public_profiles/${encodeURIComponent(r.followerUid)}`, idToken);
    if (pRes.ok) {
      const p = parsePublicProfileDoc(pRes.data);
      if (p) out.push(p);
    } else {
      out.push({ uid: r.followerUid, username: '', usernameLower: '', displayName: 'Kullanıcı', bio: '', avatarBase64: '', styleTags: [], isPrivate: false, updatedAtMs: r.createdAtMs });
    }
  }
  return out;
}

export async function fetchFollowList(targetUid, followers = true) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  const { blockedByMe, blockedMe } = await fetchBlockedUidSets(idToken, myUid);
  const rels = (await queryBySingleField(idToken, 'follows', followers ? 'targetUid' : 'followerUid', targetUid))
    .map(parseFollowDoc)
    .filter((r) => r && r.status === 'accepted');
  const profiles = await Promise.all(rels.map(async (rel) => {
    const otherUid = followers ? rel.followerUid : rel.targetUid;
    if (blockedByMe.has(otherUid) || blockedMe.has(otherUid)) return null;
    const pRes = await execFs('GET', `${FS_BASE}/public_profiles/${encodeURIComponent(otherUid)}`, idToken);
    if (pRes.status === 404) return null;
    if (!pRes.ok) throw new Error(formatFsError(pRes.status, pRes.data, 'Takip listesi yüklenemedi'));
    return parsePublicProfileDoc(pRes.data);
  }));
  return profiles.filter(Boolean);
}

export async function fetchLikeInfo(targetKey) {
  const auth = await ensureFirebaseAuth();
  if (!auth) return { liked: false, count: 0 };
  const docs = await queryBySingleField(auth.idToken, 'item_likes', 'targetKey', targetKey);
  const uids = docs.map((d) => fsStr(d?.fields, 'uid')).filter(Boolean);
  return { liked: uids.includes(auth.uid), count: uids.length };
}

export async function toggleLike(targetKey) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const cleanKey = String(targetKey || '').trim().slice(0, 120);
  if (cleanKey.length < 4) throw new Error('Geçersiz beğeni anahtarı');
  const likeId = `${auth.uid}_${cleanKey}`;
  const gRes = await execFs('GET', `${FS_BASE}/item_likes/${encodeURIComponent(likeId)}`, auth.idToken);
  if (gRes.ok) {
    await execFs('DELETE', `${FS_BASE}/item_likes/${encodeURIComponent(likeId)}`, auth.idToken);
  } else {
    requireLocalUsernameOrThrow();
    await execFs('PATCH', `${FS_BASE}/item_likes/${encodeURIComponent(likeId)}`, auth.idToken, {
      fields: {
        uid: strVal(auth.uid),
        targetKey: strVal(cleanKey),
        createdAtMs: intVal(safeNowMs())
      }
    });
  }
  return fetchLikeInfo(cleanKey);
}

export async function importRemoteGarmentLocally({
  itemData,
  asWishlist,
  appState,
  savePhotoDataUrl,
  createId,
  commitState,
  syncPhotosAndPersonalProfile,
  lang = 'TR'
}) {
  const photoIds = [];
  const rawPhotos = Array.isArray(itemData.photos) ? itemData.photos : (itemData.photoBase64 ? [itemData.photoBase64] : []);
  for (const dataUrl of rawPhotos.slice(0, 3)) {
    if (!isSafeImageDataUrl(dataUrl || '', 260000)) continue;
    const pid = createId('orig');
    await savePhotoDataUrl(pid, dataUrl);
    photoIds.push(pid);
  }
  const safeUrl = isSafePublicHttpsUrl(itemData.productUrl || '') ? itemData.productUrl : '';
  const colors = Array.isArray(itemData.colors) && itemData.colors.length ? itemData.colors : ['BLACK'];
  const newItem = {
    id: createId('item'),
    name: clampText(itemData.name || cleanGarmentTitleForName(colors[0], itemData.subCategory || 'TSHIRT', lang), 120),
    category: itemData.category || 'TOP',
    subCategory: itemData.subCategory || 'TSHIRT',
    styleTag: itemData.styleTag || 'CASUAL',
    seasons: Array.isArray(itemData.seasons) && itemData.seasons.length ? itemData.seasons : ['ALL'],
    colors,
    photoFileNames: [...photoIds],
    originalPhotoFileNames: [...photoIds],
    catalogPhotoFileNames: [],
    useOriginalPhotos: photoIds.length > 0,
    useCatalogPhotos: false,
    notes: clampText(itemData.notes || '', 600, true),
    isFavorite: false,
    wearCount: 0,
    isWishlist: Boolean(asWishlist),
    priceText: asWishlist ? clampText(itemData.priceText || '', 40) : '',
    productUrl: asWishlist ? safeUrl : '',
    createdAt: Date.now()
  };
  appState.items = [...(appState.items || []), newItem];
  commitState();
  await syncPhotosAndPersonalProfile();
  return newItem;
}

function buildSharedOutfitMetaJson(pieces, stripExtras = false) {
  const arr = (pieces || []).map((p) => ({
    name: String(p.name || 'Parça').slice(0, 120),
    category: String(p.category || 'TOP').slice(0, 32),
    subCategory: String(p.subCategory || 'TSHIRT').slice(0, 32),
    styleTag: String(p.styleTag || 'CASUAL').slice(0, 32),
    seasons: (Array.isArray(p.seasons) && p.seasons.length ? p.seasons : ['ALL']).map((s) => String(s).slice(0, 32)).filter(Boolean).slice(0, 5),
    colors: (Array.isArray(p.colors) ? p.colors : []).map((c) => String(c).slice(0, 32)).filter(Boolean).slice(0, 8),
    priceText: String(p.priceText || '').slice(0, 40),
    productUrl: stripExtras ? '' : String(p.productUrl || '').slice(0, 400),
    notes: stripExtras ? '' : String(p.notes || '').slice(0, 160),
    photoIndex: Number.isInteger(p.photoIndex) ? p.photoIndex : -1
  }));
  const raw = JSON.stringify(arr);
  if (!stripExtras && raw.length > 11500) return buildSharedOutfitMetaJson(pieces, true);
  return raw.length >= 2 ? raw.slice(0, 11900) : '[]';
}

function buildSharedOutfitFields({
  shareId,
  ownerUid,
  ownerUsername,
  ownerDisplayName,
  ownerAvatar,
  title,
  weatherLabel,
  harmonyScore,
  harmonyNote,
  notes,
  pieces,
  createdAtMs
}) {
  const cleanPieces = (pieces || []).slice(0, 8);
  const pieceNames = cleanPieces.map((p) => String(p.name || 'Parça').trim().slice(0, 120) || 'Parça');
  const pieceCategories = cleanPieces.map((p) => String(p.category || 'TOP').trim().slice(0, 32) || 'TOP');
  const piecePhotos = cleanPieces.map((p) => String(p.photoBase64 || '').slice(0, 138000)).filter(Boolean).slice(0, 6);
  const indexedPieces = cleanPieces.map((p) => {
    const ph = String(p.photoBase64 || '').slice(0, 138000);
    return { ...p, photoIndex: ph ? piecePhotos.indexOf(ph) : -1 };
  });
  const metaJson = buildSharedOutfitMetaJson(indexedPieces);
  return {
    shareId: strVal(shareId),
    ownerUid: strVal(ownerUid),
    ownerUsername: strVal(String(ownerUsername || '').slice(0, 24)),
    ownerDisplayName: strVal(String(ownerDisplayName || '').slice(0, 50)),
    ownerAvatar: strVal(String(ownerAvatar || '').slice(0, 39000)),
    title: strVal(String(title || 'Kombinim').trim().slice(0, 120) || 'Kombinim'),
    weatherLabel: strVal(String(weatherLabel || '').trim().slice(0, 60)),
    harmonyScore: intVal(Math.max(0, Math.min(100, Math.round(Number(harmonyScore) || 0)))),
    harmonyNote: strVal(String(harmonyNote || '').trim().slice(0, 140)),
    notes: strVal(String(notes || '').trim().slice(0, 600)),
    pieceNames: strArrVal(pieceNames),
    pieceCategories: strArrVal(pieceCategories),
    pieceMetaJson: strVal(metaJson),
    piecePhotos: strArrVal(piecePhotos),
    createdAtMs: intVal(Math.max(MIN_VALID_EPOCH_MS, Number(createdAtMs) || safeNowMs()))
  };
}

export async function shareOutfit({
  title,
  pieces,
  harmonyScore = 80,
  harmonyNote = '',
  weatherLabel = '',
  notes = '',
  profile,
  photoMap,
  lang = 'TR'
}) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error(localizedText(lang, 'Sunucuya bağlanılamadı', 'Connection failed'));
  const { uid, idToken } = auth;
  const capped = (pieces || []).slice(0, 8);
  if (!capped.length) {
    throw new Error(localizedText(lang, 'Paylaşılacak kombin parçası bulunamadı', 'No outfit pieces to share'));
  }

  const shareId = randomShareId();
  const nowMs = safeNowMs();
  const cleanUser = normalizeUsername(profile?.username || '');
  const cleanDisplay = (profile?.displayName || '').trim().slice(0, 50) || cleanUser;
  const cleanAvatar = isSafeImageDataUrl(profile?.avatarThumbBase64 || '', 39000) ? profile.avatarThumbBase64 : '';
  const cleanTitle = (title || localizedText(lang, 'Kombinim', 'My Outfit')).trim().slice(0, 120) || 'Kombinim';

  let photoCount = 0;
  const sharedPieces = [];
  for (const item of capped) {
    let photoStr = '';
    if (photoCount < 6) {
      const rawSrc = itemCloudPhotoSrc(item, photoMap);
      if (rawSrc) {
        const compressed = await compressGarmentPhotoForCloud(rawSrc, 520, 130000).catch(() => '');
        if (compressed) {
          photoStr = compressed;
          photoCount += 1;
        }
      }
    }
    sharedPieces.push({
      name: (item.name || displaySubCategory(item.subCategory, lang) || 'Parça').trim().slice(0, 120) || 'Parça',
      category: String(item.category || 'TOP').slice(0, 32),
      subCategory: String(item.subCategory || 'TSHIRT').slice(0, 32),
      styleTag: String(item.styleTag || 'CASUAL').slice(0, 32),
      seasons: (item.seasons || ['ALL']).map((s) => String(s).slice(0, 32)).filter(Boolean).slice(0, 5),
      colors: (item.colors || []).map((c) => String(c).slice(0, 32)).filter(Boolean).slice(0, 8),
      priceText: String(item.priceText || '').slice(0, 40),
      productUrl: String(item.productUrl || '').slice(0, 400),
      notes: String(item.notes || '').slice(0, 160),
      photoIndex: photoStr ? photoCount - 1 : -1,
      photoBase64: photoStr
    });
  }

  const fields = buildSharedOutfitFields({
    shareId,
    ownerUid: uid,
    ownerUsername: cleanUser,
    ownerDisplayName: cleanDisplay,
    ownerAvatar: cleanAvatar,
    title: cleanTitle,
    weatherLabel,
    harmonyScore,
    harmonyNote,
    notes,
    pieces: sharedPieces,
    createdAtMs: nowMs
  });

  const res = await execFs('PATCH', `${FS_BASE}/shared_outfits/${encodeURIComponent(shareId)}`, idToken, { fields });
  if (!res.ok) {
    throw new Error(formatFsError(res.status, res.data, localizedText(lang, 'Kombin paylaşılamadı', 'Outfit share failed')));
  }
  trackFeature('outfit_share');
  return {
    shareId,
    ownerUid: uid,
    ownerUsername: cleanUser,
    ownerDisplayName: cleanDisplay,
    ownerAvatar: cleanAvatar,
    title: cleanTitle,
    weatherLabel: String(weatherLabel || '').trim().slice(0, 60),
    harmonyScore: Math.max(0, Math.min(100, Math.round(Number(harmonyScore) || 0))),
    harmonyNote: String(harmonyNote || '').trim().slice(0, 140),
    notes: String(notes || '').trim().slice(0, 600),
    pieces: sharedPieces,
    createdAtMs: nowMs
  };
}

export async function updateSharedOutfitOwner(shared, profile) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Sunucuya bağlanılamadı');
  const { uid, idToken } = auth;
  const cleanUser = normalizeUsername(profile?.username || '');
  const cleanDisplay = (profile?.displayName || '').trim().slice(0, 50) || cleanUser;
  const cleanAvatar = isSafeImageDataUrl(profile?.avatarThumbBase64 || '', 39000) ? profile.avatarThumbBase64 : '';

  const fields = buildSharedOutfitFields({
    ...shared,
    ownerUid: uid,
    ownerUsername: cleanUser,
    ownerDisplayName: cleanDisplay,
    ownerAvatar: cleanAvatar
  });

  const res = await execFs('PATCH', `${FS_BASE}/shared_outfits/${encodeURIComponent(shared.shareId)}`, idToken, { fields });
  if (!res.ok) throw new Error(formatFsError(res.status, res.data, 'Kombin paylaşımı güncellenemedi'));
  return { ...shared, ownerUsername: cleanUser, ownerDisplayName: cleanDisplay, ownerAvatar: cleanAvatar };
}

export async function fetchSharedOutfit(shareId) {
  if (!SHARE_ID_REGEX.test(shareId)) return null;
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const res = await execFs('GET', `${FS_BASE}/shared_outfits/${encodeURIComponent(shareId)}`, auth.idToken);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(formatFsError(res.status, res.data, 'Kombin alınamadı'));
  const f = res.data?.fields;
  if (!f) return null;

  const pieceNames = fsStrArr(f, 'pieceNames');
  const pieceCats = fsStrArr(f, 'pieceCategories');
  const piecePhotos = fsStrArr(f, 'piecePhotos');
  let metaArr = null;
  try {
    metaArr = JSON.parse(fsStr(f, 'pieceMetaJson') || '[]');
  } catch {
    metaArr = null;
  }
  const pieces = Array.isArray(metaArr) && metaArr.length > 0
    ? metaArr.map((obj, idx) => {
      const pIdx = Number.isInteger(obj?.photoIndex) ? obj.photoIndex : -1;
      return {
        name: String(obj?.name || pieceNames[idx] || 'Parça'),
        category: String(obj?.category || pieceCats[idx] || 'TOP'),
        subCategory: String(obj?.subCategory || 'TSHIRT'),
        styleTag: String(obj?.styleTag || 'CASUAL'),
        seasons: Array.isArray(obj?.seasons) ? obj.seasons : ['ALL'],
        colors: Array.isArray(obj?.colors) ? obj.colors : [],
        priceText: String(obj?.priceText || ''),
        productUrl: String(obj?.productUrl || ''),
        notes: String(obj?.notes || ''),
        photoIndex: pIdx,
        photoBase64: (pIdx >= 0 && piecePhotos[pIdx]) ? piecePhotos[pIdx] : ''
      };
    })
    : pieceNames.map((pName, idx) => ({
      name: pName,
      category: pieceCats[idx] || 'TOP',
      subCategory: 'TSHIRT',
      styleTag: 'CASUAL',
      seasons: ['ALL'],
      colors: [],
      priceText: '',
      productUrl: '',
      notes: '',
      photoIndex: idx < piecePhotos.length ? idx : -1,
      photoBase64: piecePhotos[idx] || ''
    }));

  trackFeature('shared_outfit_open');
  return {
    shareId: fsStr(f, 'shareId') || shareId,
    ownerUid: fsStr(f, 'ownerUid'),
    ownerUsername: fsStr(f, 'ownerUsername'),
    ownerDisplayName: fsStr(f, 'ownerDisplayName'),
    ownerAvatar: fsStr(f, 'ownerAvatar'),
    title: fsStr(f, 'title'),
    weatherLabel: fsStr(f, 'weatherLabel'),
    harmonyScore: Math.max(0, Math.min(100, fsInt(f, 'harmonyScore'))),
    harmonyNote: fsStr(f, 'harmonyNote'),
    notes: fsStr(f, 'notes'),
    pieces,
    createdAtMs: fsInt(f, 'createdAtMs')
  };
}
