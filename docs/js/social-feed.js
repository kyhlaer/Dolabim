// Social Feed, Chunked Videos, 15-Min Caption Editing, Rich Comments (+GIFs), and Notifications (Web/PWA)
// Matches DolabimAdmin/firestore.rules and Android SocialFeedService.kt 1:1.

import { localizedText } from './constants.js';
import { ensureFirebaseAuth, getStoredUid, trackFeature } from './analytics.js';
import { isSafeGifDataUrl, isSafeExternalGifUrl } from './gif-library.js';
import { cleanGarmentTitleForName } from './recommendation-engine.js';
import { isSafeImageDataUrl } from './security.js';
import { fetchPublicProfileHeader } from './social.js';

const FIREBASE_PROJECT_ID = 'dolabim-stats';
const FS_BASE = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/(default)/documents`;
const PROFILE_STORAGE_KEY = 'dolabim_profile_v1';
const SHARE_BASE_URL = 'https://kyhlaer.github.io/Dolabim/';
const UID_REGEX = /^[A-Za-z0-9_-]{1,128}$/;
const POST_ID_REGEX = /^[A-Za-z0-9_-]{8,48}$/;
const MIN_VALID_EPOCH_MS = 1700000000001;
const MAX_VALID_EPOCH_MS = 4102444800000;
const VIDEO_CHUNK_BYTES = 500000;
const MAX_VIDEO_BYTES = 7500000;
const MAX_VIDEO_CHUNKS = 16;

export const POST_EDIT_WINDOW_MS = 900000; // 15 minutes

export const SOCIAL_POST_KINDS = [
  { key: 'outfit', tr: 'Kombin', en: 'Outfit' },
  { key: 'new_piece', tr: 'Yeni Parça', en: 'New Piece' },
  { key: 'look', tr: 'Look / Stil', en: 'Look' },
  { key: 'photo', tr: 'Fotoğraf', en: 'Photo' },
  { key: 'video', tr: 'Video', en: 'Video' },
  { key: 'question', tr: 'Fikir / Soru', en: 'Ask' }
];

function safeNowMs() {
  const n = Date.now();
  if (!Number.isFinite(n) || n < MIN_VALID_EPOCH_MS) return MIN_VALID_EPOCH_MS;
  if (n > MAX_VALID_EPOCH_MS - 5000) return MAX_VALID_EPOCH_MS - 5000;
  return Math.trunc(n);
}

function randomId(prefix = '') {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789_-';
  const arr = new Uint32Array(14);
  if (window.crypto?.getRandomValues) window.crypto.getRandomValues(arr);
  else for (let i = 0; i < 14; i++) arr[i] = Math.floor(Math.random() * 1e9);
  return (prefix + Array.from(arr, (n) => chars[n % chars.length]).join('')).slice(0, 44);
}

function readLocalProfileFallback() {
  try {
    const raw = localStorage.getItem(PROFILE_STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function isValidFeedUsername(u) {
  const n = String(u || '').trim().replace(/^@+/, '').toLowerCase();
  return /^[a-z0-9][a-z0-9_.]{1,22}[a-z0-9]$/.test(n) && !n.includes('..');
}

function throwFeedUsernameRequired(msg) {
  const err = new Error(msg || 'Devam etmek için önce kullanıcı adı oluşturmalısın');
  err.code = 'USERNAME_REQUIRED';
  throw err;
}

function cleanOwnerMeta(profile) {
  const eff = profile && (profile.username || profile.displayName) ? profile : readLocalProfileFallback();
  const cleanUser = String(eff?.username || '').trim().replace(/^@+/, '').toLowerCase().slice(0, 24);
  const cleanDisplay = String(eff?.displayName || '').trim().slice(0, 50) || cleanUser;
  let cleanAvatar = '';
  if (isSafeImageDataUrl(eff?.avatarThumbBase64 || '', 39000)) cleanAvatar = eff.avatarThumbBase64;
  else if (isSafeImageDataUrl(eff?.avatarBase64 || '', 39000)) cleanAvatar = eff.avatarBase64;
  return { cleanUser, cleanDisplay, cleanAvatar, isPrivate: Boolean(eff?.isPrivate) };
}

const strVal = (v) => ({ stringValue: String(v ?? '') });
const intVal = (v) => ({ integerValue: String(Math.trunc(Number(v) || 0)) });
const boolVal = (v) => ({ booleanValue: Boolean(v) });
const strArrVal = (arr) => ({ arrayValue: { values: (arr || []).map((x) => strVal(x)) } });

const fsStr = (f, k) => f?.[k]?.stringValue ?? '';
const fsInt = (f, k) => Number(f?.[k]?.integerValue ?? 0) || 0;
const fsBool = (f, k, def = false) => (f?.[k] && 'booleanValue' in f[k] ? Boolean(f[k].booleanValue) : def);
const fsStrArr = (f, k) => (f?.[k]?.arrayValue?.values || []).map((v) => v?.stringValue ?? '').filter(Boolean);

async function execFs(method, url, idToken, bodyObj = null) {
  const parsed = new URL(url);
  if (parsed.hostname !== 'firestore.googleapis.com') throw new Error('Disallowed host');
  const headers = { Authorization: `Bearer ${idToken}` };
  let effectiveMethod = method;
  if (method === 'PATCH') {
    effectiveMethod = 'POST';
    headers['X-HTTP-Method-Override'] = 'PATCH';
  }
  if (bodyObj != null) headers['Content-Type'] = 'application/json; charset=UTF-8';
  const res = await fetch(url, {
    method: effectiveMethod,
    headers,
    body: bodyObj != null ? JSON.stringify(bodyObj) : undefined
  });
  const text = await res.text().catch(() => '');
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  return { ok: res.ok, status: res.status, data, text };
}

function formatFeedFsError(status, dataOrText, actionLabel) {
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
  else if (status === 429) friendly = 'Okuma kotasına ulaşıldı (429). Daha sonra tekrar dene.';
  else if (msg) friendly = msg.slice(0, 120);
  return `${actionLabel}: ${friendly}`;
}

async function fetchFeedBlockedUids(idToken, myUid) {
  const [byMeDocs, againstMeDocs] = await Promise.all([
    queryBySingleField(idToken, 'blocks', 'blockerUid', myUid),
    queryBySingleField(idToken, 'blocks', 'blockedUid', myUid)
  ]);
  const blocked = new Set();
  byMeDocs.forEach((d) => { const u = fsStr(d?.fields, 'blockedUid'); if (u) blocked.add(u); });
  againstMeDocs.forEach((d) => { const u = fsStr(d?.fields, 'blockerUid'); if (u) blocked.add(u); });
  return blocked;
}

async function listCollectionPath(idToken, path) {
  const res = await execFs('GET', `${FS_BASE}/${path}?pageSize=120`, idToken);
  if (!res.ok || !res.data) throw new Error(formatFeedFsError(res.status, res.data, 'Gönderiler yüklenemedi'));
  return res.data.documents || [];
}

async function queryBySingleField(idToken, collectionId, fieldPath, stringValue, { limit = 200 } = {}) {
  const res = await execFs('POST', `${FS_BASE}:runQuery`, idToken, {
    structuredQuery: {
      from: [{ collectionId }],
      where: {
        fieldFilter: {
          field: { fieldPath },
          op: 'EQUAL',
          value: strVal(stringValue)
        }
      },
      ...(limit ? { limit } : {})
    }
  });
  const error = Array.isArray(res.data) && res.data.find((row) => row.error)?.error;
  if (!res.ok || !Array.isArray(res.data) || error) {
    throw new Error(formatFeedFsError(error?.code || res.status, res.data, 'Sosyal veriler yüklenemedi'));
  }
  return res.data.map((r) => r?.document).filter(Boolean);
}

function postCollectionPath(ownerUid, isPrivatePost) {
  return isPrivatePost
    ? `public_profiles/${encodeURIComponent(ownerUid)}/private_posts`
    : 'public_posts';
}

function parsePostDoc(doc, isPrivatePost = false) {
  const f = doc?.fields;
  if (!f) return null;
  const docId = (doc.name || '').split('/').pop() || '';
  const postId = fsStr(f, 'postId') || docId;
  const ownerUid = fsStr(f, 'ownerUid');
  if (!postId || !ownerUid) return null;
  return {
    postId,
    ownerUid,
    ownerUsername: fsStr(f, 'ownerUsername'),
    ownerDisplayName: fsStr(f, 'ownerDisplayName'),
    ownerAvatar: fsStr(f, 'ownerAvatar'),
    postKind: fsStr(f, 'postKind') || 'outfit',
    caption: fsStr(f, 'caption'),
    mediaType: fsStr(f, 'mediaType') || 'text',
    photos: fsStrArr(f, 'photos'),
    posterBase64: fsStr(f, 'posterBase64'),
    chunkCount: fsInt(f, 'chunkCount'),
    videoMime: fsStr(f, 'videoMime'),
    videoDurationSec: fsInt(f, 'videoDurationSec'),
    attachedItemName: fsStr(f, 'attachedItemName'),
    attachedCategory: fsStr(f, 'attachedCategory'),
    attachedPriceText: fsStr(f, 'attachedPriceText'),
    attachedProductUrl: fsStr(f, 'attachedProductUrl'),
    createdAtMs: fsInt(f, 'createdAtMs'),
    editedAtMs: fsInt(f, 'editedAtMs'),
    isPrivatePost
  };
}

function buildPostFields(p) {
  return {
    postId: strVal(p.postId),
    ownerUid: strVal(p.ownerUid),
    ownerUsername: strVal(p.ownerUsername),
    ownerDisplayName: strVal(p.ownerDisplayName),
    ownerAvatar: strVal(p.ownerAvatar),
    postKind: strVal(p.postKind),
    caption: strVal(p.caption),
    mediaType: strVal(p.mediaType),
    photos: strArrVal((p.photos || []).slice(0, 4)),
    posterBase64: strVal(p.posterBase64 || ''),
    chunkCount: intVal(p.chunkCount || 0),
    videoMime: strVal(p.videoMime || ''),
    videoDurationSec: intVal(p.videoDurationSec || 0),
    attachedItemName: strVal(p.attachedItemName || ''),
    attachedCategory: strVal(p.attachedCategory || ''),
    attachedPriceText: strVal(p.attachedPriceText || ''),
    attachedProductUrl: strVal(p.attachedProductUrl || ''),
    createdAtMs: intVal(p.createdAtMs),
    editedAtMs: intVal(p.editedAtMs || 0)
  };
}

export function canEditPostCaption(post, nowMs = Date.now()) {
  if (!post || !post.createdAtMs) return false;
  return nowMs <= post.createdAtMs + POST_EDIT_WINDOW_MS;
}

export function editRemainingMinutes(post, nowMs = Date.now()) {
  const rem = (Number(post?.createdAtMs || 0) + POST_EDIT_WINDOW_MS) - nowMs;
  if (rem <= 0) return 0;
  return Math.max(1, Math.min(15, Math.ceil(rem / 60000)));
}

export function buildSharePostUrl(post) {
  const base = `${SHARE_BASE_URL}?post=${encodeURIComponent(post.postId)}`;
  return post.isPrivatePost && post.ownerUid ? `${base}&owner=${encodeURIComponent(post.ownerUid)}` : base;
}

export async function compressDataUrlForCloud(dataUrl, maxChars = 205000, maxSide = 960, { forceJpeg = false } = {}) {
  if (typeof dataUrl !== 'string' || !dataUrl.startsWith('data:image/')) return '';
  if (!forceJpeg && dataUrl.length <= maxChars) return dataUrl;
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      try {
        const longest = Math.max(img.naturalWidth, img.naturalHeight, 1);
        const canvas = document.createElement('canvas');
        const context = canvas.getContext('2d');
        let scale = Math.min(1, maxSide / longest);
        for (let attempt = 0; attempt < 5; attempt += 1) {
          canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
          canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
          context.drawImage(img, 0, 0, canvas.width, canvas.height);
          for (const quality of [0.84, 0.72, 0.60, 0.48, 0.36]) {
            const candidate = canvas.toDataURL('image/jpeg', quality);
            if (candidate.length <= maxChars && candidate.startsWith('data:image/jpeg;')) {
              resolve(candidate);
              return;
            }
          }
          scale *= 0.75;
        }
      } catch {}
      resolve('');
    };
    img.onerror = () => resolve('');
    img.src = dataUrl;
  });
}

async function extractVideoPosterAndDuration(file) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const v = document.createElement('video');
    v.muted = true;
    v.playsInline = true;
    v.preload = 'metadata';
    const done = (poster, dur) => {
      URL.revokeObjectURL(url);
      resolve({ posterBase64: poster, durationSec: dur });
    };
    v.onloadeddata = () => {
      try {
        v.currentTime = Math.min(0.2, (v.duration || 0) * 0.1);
      } catch {
        // ignore seek issue
      }
    };
    v.onseeked = () => {
      try {
        const dur = Math.max(0, Math.min(60, Math.round(v.duration || 0)));
        const maxSide = 600;
        const vw = v.videoWidth || 480;
        const vh = v.videoHeight || 640;
        const sc = Math.min(1, maxSide / Math.max(vw, vh));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(64, Math.round(vw * sc));
        canvas.height = Math.max(64, Math.round(vh * sc));
        canvas.getContext('2d').drawImage(v, 0, 0, canvas.width, canvas.height);
        const poster = canvas.toDataURL('image/jpeg', 0.72).slice(0, 150000);
        done(poster, dur);
      } catch {
        done('', 0);
      }
    };
    v.onerror = () => done('', 0);
    setTimeout(() => done('', Math.max(0, Math.min(60, Math.round(v.duration || 0)))), 3500);
    v.src = url;
  });
}

function uint8ToBase64(u8) {
  let bin = '';
  for (let i = 0; i < u8.length; i += 8192) {
    bin += String.fromCharCode(...u8.subarray(i, Math.min(u8.length, i + 8192)));
  }
  return btoa(bin);
}

export async function fetchFeedPosts({ followingOnly = false } = {}) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('İnternet bağlantısı kurulamadı');
  const { uid: myUid, idToken } = auth;

  const [followDocs, blockedUids] = await Promise.all([
    queryBySingleField(idToken, 'follows', 'followerUid', myUid),
    fetchFeedBlockedUids(idToken, myUid)
  ]);
  const acceptedFollowingUids = new Set(
    followDocs
      .map((d) => d?.fields)
      .filter((f) => fsStr(f, 'status') === 'accepted')
      .map((f) => fsStr(f, 'targetUid'))
      .filter((u) => Boolean(u) && !blockedUids.has(u))
  );

  const map = new Map();
  const pubDocs = await listCollectionPath(idToken, 'public_posts');
  pubDocs.map((d) => parsePostDoc(d, false))
    .filter((p) => p && !blockedUids.has(p.ownerUid))
    .forEach((p) => map.set(p.postId, p));

  const privOwners = [myUid, ...acceptedFollowingUids]
    .filter((u) => UID_REGEX.test(u) && (u === myUid || !blockedUids.has(u)))
    .slice(0, 40);
  await Promise.all(
    privOwners.map(async (ownerUid) => {
      const pDocs = await listCollectionPath(idToken, `public_profiles/${encodeURIComponent(ownerUid)}/private_posts`);
      pDocs.map((d) => parsePostDoc(d, true))
        .filter((p) => p && !blockedUids.has(p.ownerUid))
        .forEach((p) => map.set(p.postId, p));
    })
  );

  return [...map.values()]
    .filter((p) => !blockedUids.has(p.ownerUid) && (!followingOnly || p.ownerUid === myUid || acceptedFollowingUids.has(p.ownerUid)))
    .sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
}

export async function fetchSinglePost(postId, ownerUid = '') {
  if (!POST_ID_REGEX.test(postId)) return null;
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  const blockedUids = await fetchFeedBlockedUids(idToken, myUid);
  if (ownerUid && UID_REGEX.test(ownerUid)) {
    if (ownerUid !== myUid && blockedUids.has(ownerUid)) return null;
    const pRes = await execFs('GET', `${FS_BASE}/public_profiles/${encodeURIComponent(ownerUid)}/private_posts/${encodeURIComponent(postId)}`, idToken);
    const parsedPriv = pRes.ok ? parsePostDoc(pRes.data, true) : null;
    if (parsedPriv && !blockedUids.has(parsedPriv.ownerUid)) return parsedPriv;
  }
  const pubRes = await execFs('GET', `${FS_BASE}/public_posts/${encodeURIComponent(postId)}`, idToken);
  if (pubRes.ok) {
    const parsedPub = parsePostDoc(pubRes.data, false);
    if (parsedPub && !blockedUids.has(parsedPub.ownerUid)) return parsedPub;
  }
  return null;
}

export async function createSocialPost({
  profile,
  postKind,
  caption,
  mediaType, // 'photo' | 'video' | 'text'
  photoDataUrls = [],
  videoFile = null,
  attachedItem = null,
  lang = 'TR'
}) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error(localizedText(lang, 'Sunucuya bağlanılamadı', 'Connection failed'));
  const { uid: myUid, idToken } = auth;
  const { cleanUser, cleanDisplay, cleanAvatar, isPrivate } = cleanOwnerMeta(profile);
  if (!isValidFeedUsername(cleanUser)) {
    throwFeedUsernameRequired('Gönderi paylaşmak için önce bir kullanıcı adı oluşturmalısın');
  }
  const validKind = SOCIAL_POST_KINDS.some((k) => k.key === postKind) ? postKind : 'outfit';
  const cleanCaption = String(caption || '').trim().slice(0, 800);
  const postId = randomId('p_');
  const nowMs = safeNowMs();

  let cleanPhotos = [];
  if (mediaType === 'photo') {
    for (const raw of (photoDataUrls || []).slice(0, 4)) {
      const comp = await compressDataUrlForCloud(raw, 205000, 960);
      if (comp) cleanPhotos.push(comp);
    }
    if (!cleanPhotos.length) throw new Error('En az 1 fotoğraf gerekli');
  } else if (mediaType === 'text' && !cleanCaption) {
    throw new Error('Açıklama boş bırakılamaz');
  }

  let posterBase64 = '';
  let chunkCount = 0;
  let videoMime = '';
  let videoDurationSec = 0;

  if (mediaType === 'video') {
    if (!videoFile || !videoFile.size) throw new Error('Video dosyası bulunamadı');
    if (videoFile.size > MAX_VIDEO_BYTES) throw new Error('Video boyutu en fazla 7.5 MB olabilir');
    const meta = await extractVideoPosterAndDuration(videoFile);
    posterBase64 = meta.posterBase64;
    videoDurationSec = meta.durationSec;
    videoMime = (videoFile.type || 'video/mp4').slice(0, 40);

    const buf = new Uint8Array(await videoFile.arrayBuffer());
    const chunks = [];
    for (let pos = 0; pos < buf.length && chunks.length < MAX_VIDEO_CHUNKS; pos += VIDEO_CHUNK_BYTES) {
      chunks.push(buf.subarray(pos, Math.min(buf.length, pos + VIDEO_CHUNK_BYTES)));
    }
    if (!chunks.length) throw new Error('Video parça limiti aşıldı');
    const colPath = postCollectionPath(myUid, isPrivate);
    for (let i = 0; i < chunks.length; i += 1) {
      const cRes = await execFs('PATCH', `${FS_BASE}/${colPath}/${encodeURIComponent(postId)}/media_chunks/${i}`, idToken, {
        fields: {
          ownerUid: strVal(myUid),
          index: intVal(i),
          b64: strVal(uint8ToBase64(chunks[i]))
        }
      });
      if (!cRes.ok) throw new Error(formatFeedFsError(cRes.status, cRes.data, 'Video yüklenemedi'));
    }
    chunkCount = chunks.length;
  }

  const itemName = attachedItem ? String(attachedItem.name || cleanGarmentTitleForName(attachedItem.colors?.[0], attachedItem.subCategory, lang)).slice(0, 120) : '';
  const postObj = {
    postId,
    ownerUid: myUid,
    ownerUsername: cleanUser,
    ownerDisplayName: cleanDisplay,
    ownerAvatar: cleanAvatar,
    postKind: validKind,
    caption: cleanCaption,
    mediaType,
    photos: cleanPhotos,
    posterBase64,
    chunkCount,
    videoMime,
    videoDurationSec,
    attachedItemName: itemName,
    attachedCategory: attachedItem ? String(attachedItem.category || 'TOP').slice(0, 32) : '',
    attachedPriceText: attachedItem ? String(attachedItem.priceText || '').slice(0, 40) : '',
    attachedProductUrl: attachedItem ? String(attachedItem.productUrl || '').slice(0, 1000) : '',
    createdAtMs: nowMs,
    editedAtMs: 0,
    isPrivatePost: isPrivate
  };

  const colPath = postCollectionPath(myUid, isPrivate);
  const res = await execFs('PATCH', `${FS_BASE}/${colPath}/${encodeURIComponent(postId)}`, idToken, {
    fields: buildPostFields(postObj)
  });
  if (!res.ok) throw new Error(formatFeedFsError(res.status, res.data, 'Gönderi paylaşılamadı'));
  trackFeature('post_create', `${validKind}/${mediaType}`);
  return postObj;
}

const videoBlobCache = new Map();

export async function loadPostVideoBlobUrl(post) {
  if (videoBlobCache.has(post.postId)) return videoBlobCache.get(post.postId);
  if (!post.chunkCount) throw new Error('Video parçası yok');
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { idToken } = auth;
  const colPath = postCollectionPath(post.ownerUid, post.isPrivatePost);

  const byteParts = [];
  for (let i = 0; i < post.chunkCount; i += 1) {
    const res = await execFs('GET', `${FS_BASE}/${colPath}/${encodeURIComponent(post.postId)}/media_chunks/${i}`, idToken);
    if (!res.ok) throw new Error(`Video indirilemedi (${res.status})`);
    const b64 = fsStr(res.data?.fields, 'b64');
    const bin = atob(b64);
    const arr = new Uint8Array(bin.length);
    for (let j = 0; j < bin.length; j += 1) arr[j] = bin.charCodeAt(j);
    byteParts.push(arr);
  }
  const blob = new Blob(byteParts, { type: post.videoMime || 'video/mp4' });
  const blobUrl = URL.createObjectURL(blob);
  videoBlobCache.set(post.postId, blobUrl);
  return blobUrl;
}

export async function editPostCaption(post, newCaption) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  if (post.ownerUid !== myUid) throw new Error('Sadece kendi gönderini düzenleyebilirsin');
  const nowMs = safeNowMs();
  if (!canEditPostCaption(post, nowMs)) throw new Error('15 dakikalık düzenleme süresi doldu');
  const cleanCap = String(newCaption || '').trim().slice(0, 800);
  if (post.mediaType === 'text' && !cleanCap) throw new Error('Yazı gönderisi boş olamaz');

  const updated = { ...post, caption: cleanCap, editedAtMs: nowMs };
  const colPath = postCollectionPath(post.ownerUid, post.isPrivatePost);
  const res = await execFs('PATCH', `${FS_BASE}/${colPath}/${encodeURIComponent(post.postId)}`, idToken, {
    fields: buildPostFields(updated)
  });
  if (!res.ok) {
    throw new Error(res.status === 403 ? '15 dakikalık düzenleme süresi dolmuş' : formatFeedFsError(res.status, res.data, 'Düzenlenemedi'));
  }
  trackFeature('post_edit');
  return updated;
}

export async function deleteSocialPost(post) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  if (post.ownerUid !== myUid) throw new Error('Yetkisiz');
  const colPath = postCollectionPath(post.ownerUid, post.isPrivatePost);
  const cDocs = await listCollectionPath(idToken, `${colPath}/${encodeURIComponent(post.postId)}/comments`).catch(() => []);
  for (const c of cDocs) {
    const cid = (c.name || '').split('/').pop();
    if (cid) await execFs('DELETE', `${FS_BASE}/${colPath}/${encodeURIComponent(post.postId)}/comments/${encodeURIComponent(cid)}`, idToken).catch(() => {});
  }
  for (let i = 0; i < (post.chunkCount || 0); i += 1) {
    await execFs('DELETE', `${FS_BASE}/${colPath}/${encodeURIComponent(post.postId)}/media_chunks/${i}`, idToken).catch(() => {});
  }
  const res = await execFs('DELETE', `${FS_BASE}/${colPath}/${encodeURIComponent(post.postId)}`, idToken);
  if (!res.ok && res.status !== 404) throw new Error(`Silinemedi (${res.status})`);
  trackFeature('post_delete');
}

export async function migrateMyPostsForPrivacy(myUid, nextIsPrivate) {
  const auth = await ensureFirebaseAuth();
  if (!auth) return;
  const { idToken } = auth;
  const srcDocs = nextIsPrivate
    ? await queryBySingleField(idToken, 'public_posts', 'ownerUid', myUid)
    : await listCollectionPath(idToken, `public_profiles/${encodeURIComponent(myUid)}/private_posts`);

  for (const doc of srcDocs) {
    const post = parsePostDoc(doc, !nextIsPrivate);
    if (!post) continue;
    const srcCol = postCollectionPath(myUid, !nextIsPrivate);
    const dstCol = postCollectionPath(myUid, nextIsPrivate);
    for (let i = 0; i < (post.chunkCount || 0); i += 1) {
      const cRes = await execFs('GET', `${FS_BASE}/${srcCol}/${encodeURIComponent(post.postId)}/media_chunks/${i}`, idToken);
      if (cRes.ok) {
        const b64 = fsStr(cRes.data?.fields, 'b64');
        if (b64) {
          await execFs('PATCH', `${FS_BASE}/${dstCol}/${encodeURIComponent(post.postId)}/media_chunks/${i}`, idToken, {
            fields: { ownerUid: strVal(myUid), index: intVal(i), b64: strVal(b64) }
          }).catch(() => {});
        }
      }
    }
    const wRes = await execFs('PATCH', `${FS_BASE}/${dstCol}/${encodeURIComponent(post.postId)}`, idToken, {
      fields: buildPostFields(post)
    });
    if (wRes.ok) {
      for (let i = 0; i < (post.chunkCount || 0); i += 1) {
        await execFs('DELETE', `${FS_BASE}/${srcCol}/${encodeURIComponent(post.postId)}/media_chunks/${i}`, idToken).catch(() => {});
      }
      await execFs('DELETE', `${FS_BASE}/${srcCol}/${encodeURIComponent(post.postId)}`, idToken).catch(() => {});
    }
  }
}

export async function fetchPostLikeState(postId) {
  const auth = await ensureFirebaseAuth();
  if (!auth) return { count: 0, liked: false };
  const myUid = auth.uid || getStoredUid();
  const uids = await fetchPostLikeUids(auth.idToken, postId);
  return { count: uids.length, liked: Boolean(myUid && uids.includes(myUid)) };
}

async function fetchPostLikeUids(idToken, postId) {
  const docs = await queryBySingleField(idToken, 'item_likes', 'targetKey', `post_${postId}`, { limit: null });
  const likes = docs.map(({ fields }) => ({ uid: fsStr(fields, 'uid'), time: fsInt(fields, 'createdAtMs') }))
    .filter(({ uid }) => uid)
    .sort((a, b) => b.time - a.time || (a.uid < b.uid ? -1 : a.uid > b.uid ? 1 : 0));
  return [...new Set(likes.map(({ uid }) => uid))];
}

export async function fetchPostLikers(postId) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const uids = await fetchPostLikeUids(auth.idToken, postId);
  if (!uids.length) return [];
  const blocked = await fetchFeedBlockedUids(auth.idToken, auth.uid);
  const visible = uids.filter((uid) => !blocked.has(uid));
  const people = [];
  for (let index = 0; index < visible.length; index += 8) {
    const batch = visible.slice(index, index + 8);
    people.push(...await Promise.all(batch.map(async (uid) => {
      const profile = await fetchPublicProfileHeader(auth.idToken, uid);
      return profile || { uid, username: '', displayName: '', avatarBase64: '' };
    })));
  }
  return people;
}

export async function togglePostLike(post, myProfile) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  const targetKey = `post_${post.postId}`;
  const likeId = `${myUid}_${targetKey}`;
  const gRes = await execFs('GET', `${FS_BASE}/item_likes/${encodeURIComponent(likeId)}`, idToken);
  if (gRes.ok) {
    await execFs('DELETE', `${FS_BASE}/item_likes/${encodeURIComponent(likeId)}`, idToken);
    return false;
  }
  const { cleanUser } = cleanOwnerMeta(myProfile);
  if (!isValidFeedUsername(cleanUser)) {
    throwFeedUsernameRequired('Gönderi beğenmek için önce bir kullanıcı adı oluşturmalısın');
  }
  const pRes = await execFs('PATCH', `${FS_BASE}/item_likes/${encodeURIComponent(likeId)}`, idToken, {
    fields: {
      uid: strVal(myUid),
      targetKey: strVal(targetKey),
      createdAtMs: intVal(safeNowMs())
    }
  });
  if (!pRes.ok) throw new Error(`Beğenilemedi (${pRes.status})`);
  trackFeature('post_like', post.postKind);
  if (post.ownerUid !== myUid) {
    await sendNotificationBestEffort({
      targetUid: post.ownerUid,
      type: 'post_like',
      profile: myProfile,
      postId: post.postId,
      postOwnerUid: post.ownerUid,
      previewText: (post.caption || 'Gönderini beğendi').slice(0, 120)
    }).catch(() => {});
  }
  return true;
}

export async function fetchPostComments(post) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const blockedUids = await fetchFeedBlockedUids(auth.idToken, auth.uid);
  const colPath = postCollectionPath(post.ownerUid, post.isPrivatePost);
  const docs = await listCollectionPath(auth.idToken, `${colPath}/${encodeURIComponent(post.postId)}/comments`);
  return docs.map((doc) => {
    const f = doc.fields;
    if (!f) return null;
    const commentId = fsStr(f, 'commentId') || (doc.name || '').split('/').pop() || '';
    const postId = fsStr(f, 'postId');
    const authorUid = fsStr(f, 'authorUid');
    if (!commentId || !postId || !authorUid || blockedUids.has(authorUid)) return null;
    return {
      commentId,
      postId,
      authorUid,
      authorUsername: fsStr(f, 'authorUsername'),
      authorDisplayName: fsStr(f, 'authorDisplayName'),
      authorAvatar: fsStr(f, 'authorAvatar'),
      text: fsStr(f, 'text'),
      mediaType: fsStr(f, 'mediaType') || 'none',
      mediaBase64: fsStr(f, 'mediaBase64'),
      mediaUrl: fsStr(f, 'mediaUrl'),
      createdAtMs: fsInt(f, 'createdAtMs')
    };
  }).filter(Boolean).sort((a, b) => a.createdAtMs - b.createdAtMs);
}

export async function addPostComment({ post, myProfile, text = '', mediaType = 'none', mediaBase64 = '', mediaUrl = '' }) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const { uid: myUid, idToken } = auth;
  const { cleanUser, cleanDisplay, cleanAvatar } = cleanOwnerMeta(myProfile);
  if (!isValidFeedUsername(cleanUser)) {
    throwFeedUsernameRequired('Yorum yapmak için önce bir kullanıcı adı oluşturmalısın');
  }
  const cleanText = String(text || '').trim().slice(0, 500);
  let cleanB64 = '';
  let cleanUrl = '';
  let finalType = 'none';

  if (mediaType === 'image' && mediaBase64) {
    cleanB64 = await compressDataUrlForCloud(mediaBase64, 240000, 720);
    if (cleanB64) finalType = 'image';
  } else if (mediaType === 'gif') {
    if (isSafeGifDataUrl(mediaBase64, 255000)) {
      cleanB64 = mediaBase64;
      finalType = 'gif';
    } else if (isSafeExternalGifUrl(mediaUrl)) {
      cleanUrl = mediaUrl.trim().slice(0, 600);
      finalType = 'gif';
    }
  }
  if (!cleanText && !cleanB64 && !cleanUrl) {
    throw new Error('Yorum yazısı, fotoğraf veya GIF seçmelisin');
  }

  const commentId = randomId('c_');
  const nowMs = safeNowMs();
  const commentObj = {
    commentId,
    postId: post.postId,
    authorUid: myUid,
    authorUsername: cleanUser,
    authorDisplayName: cleanDisplay,
    authorAvatar: cleanAvatar,
    text: cleanText,
    mediaType: finalType,
    mediaBase64: cleanB64,
    mediaUrl: cleanUrl,
    createdAtMs: nowMs
  };

  const colPath = postCollectionPath(post.ownerUid, post.isPrivatePost);
  const res = await execFs('PATCH', `${FS_BASE}/${colPath}/${encodeURIComponent(post.postId)}/comments/${encodeURIComponent(commentId)}`, idToken, {
    fields: {
      commentId: strVal(commentId),
      postId: strVal(post.postId),
      authorUid: strVal(myUid),
      authorUsername: strVal(cleanUser),
      authorDisplayName: strVal(cleanDisplay),
      authorAvatar: strVal(cleanAvatar),
      text: strVal(cleanText),
      mediaType: strVal(finalType),
      mediaBase64: strVal(cleanB64),
      mediaUrl: strVal(cleanUrl),
      createdAtMs: intVal(nowMs)
    }
  });
  if (!res.ok) throw new Error(formatFeedFsError(res.status, res.data, 'Yorum gönderilemedi'));
  trackFeature('post_comment', finalType);
  if (post.ownerUid !== myUid) {
    await sendNotificationBestEffort({
      targetUid: post.ownerUid,
      type: 'post_comment',
      profile: myProfile,
      postId: post.postId,
      postOwnerUid: post.ownerUid,
      previewText: (cleanText || (finalType === 'gif' ? '[GIF gönderdi]' : '[Fotoğraf gönderdi]')).slice(0, 160)
    }).catch(() => {});
  }
  return commentObj;
}

export async function deletePostComment(post, commentId) {
  const auth = await ensureFirebaseAuth();
  if (!auth) throw new Error('Bağlantı kurulamadı');
  const colPath = postCollectionPath(post.ownerUid, post.isPrivatePost);
  const res = await execFs('DELETE', `${FS_BASE}/${colPath}/${encodeURIComponent(post.postId)}/comments/${encodeURIComponent(commentId)}`, auth.idToken);
  if (!res.ok && res.status !== 404) throw new Error(`Yorum silinemedi (${res.status})`);
}

export async function sendNotificationBestEffort({
  targetUid,
  type,
  profile,
  postId = '',
  postOwnerUid = '',
  previewText = ''
}) {
  if (!UID_REGEX.test(targetUid)) return;
  const auth = await ensureFirebaseAuth();
  if (!auth || auth.uid === targetUid) return;
  const { uid: myUid, idToken } = auth;
  const { cleanUser, cleanDisplay, cleanAvatar } = cleanOwnerMeta(profile);
  const notifId = type === 'follow_request' ? `fr_${myUid}` : randomId('n_');
  const nowMs = safeNowMs();
  await execFs('PATCH', `${FS_BASE}/notifications/${encodeURIComponent(targetUid)}/items/${encodeURIComponent(notifId)}`, idToken, {
    fields: {
      notifId: strVal(notifId),
      targetUid: strVal(targetUid),
      type: strVal(type),
      fromUid: strVal(myUid),
      fromUsername: strVal(cleanUser),
      fromDisplayName: strVal(cleanDisplay),
      fromAvatar: strVal(cleanAvatar),
      postId: strVal(String(postId).slice(0, 48)),
      postOwnerUid: strVal(String(postOwnerUid).slice(0, 128)),
      previewText: strVal(String(previewText).slice(0, 160)),
      read: boolVal(false),
      createdAtMs: intVal(nowMs)
    }
  }).catch(() => {});
}

export async function fetchMyNotifications() {
  const auth = await ensureFirebaseAuth();
  if (!auth) return [];
  const { uid: myUid, idToken } = auth;
  const docs = await listCollectionPath(idToken, `notifications/${encodeURIComponent(myUid)}/items`);
  return docs.map((doc) => {
    const f = doc.fields;
    if (!f) return null;
    const notifId = fsStr(f, 'notifId') || (doc.name || '').split('/').pop() || '';
    const targetUid = fsStr(f, 'targetUid');
    const fromUid = fsStr(f, 'fromUid');
    if (!notifId || !targetUid || !fromUid) return null;
    return {
      notifId,
      targetUid,
      type: fsStr(f, 'type'),
      fromUid,
      fromUsername: fsStr(f, 'fromUsername'),
      fromDisplayName: fsStr(f, 'fromDisplayName'),
      fromAvatar: fsStr(f, 'fromAvatar'),
      postId: fsStr(f, 'postId'),
      postOwnerUid: fsStr(f, 'postOwnerUid'),
      previewText: fsStr(f, 'previewText'),
      read: fsBool(f, 'read', false),
      createdAtMs: fsInt(f, 'createdAtMs')
    };
  }).filter(Boolean).sort((a, b) => b.createdAtMs - a.createdAtMs);
}

export async function markNotificationRead(n) {
  if (!n || n.read) return;
  const auth = await ensureFirebaseAuth();
  if (!auth || auth.uid !== n.targetUid) return;
  await execFs('PATCH', `${FS_BASE}/notifications/${encodeURIComponent(auth.uid)}/items/${encodeURIComponent(n.notifId)}`, auth.idToken, {
    fields: {
      notifId: strVal(n.notifId),
      targetUid: strVal(n.targetUid),
      type: strVal(n.type),
      fromUid: strVal(n.fromUid),
      fromUsername: strVal(n.fromUsername),
      fromDisplayName: strVal(n.fromDisplayName),
      fromAvatar: strVal(n.fromAvatar),
      postId: strVal(n.postId || ''),
      postOwnerUid: strVal(n.postOwnerUid || ''),
      previewText: strVal(n.previewText || ''),
      read: boolVal(true),
      createdAtMs: intVal(n.createdAtMs)
    }
  }).catch(() => {});
}

export async function markAllNotificationsRead(list) {
  for (const n of (list || []).filter((x) => !x.read).slice(0, 30)) {
    await markNotificationRead(n);
  }
}
