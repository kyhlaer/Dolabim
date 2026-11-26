// Instagram-Style Direct Messaging Service over Firestore REST API (v1.43.275)

import { ensureFirebaseAuth, getStoredUid } from './analytics.js';
import {
  isValidUsername,
  loadLocalProfile,
  normalizeUsername
} from './social.js';
import { compressDataUrlForCloud } from './social-feed.js';

const FS_BASE = 'https://firestore.googleapis.com/v1/projects/dolabim-stats/databases/(default)/documents';
const MUTED_KEY = 'dolabim_dm_muted_v1';
const CHUNK_SIZE_CHARS = 480000;
const MAX_CHUNKS = 16;
const DM_EDIT_WINDOW_MS = 15 * 60 * 1000;

export const DM_CHAT_STATES = ['idle', 'typing', 'capturing_photo', 'uploading_photo', 'recording_audio'];

export function pairConvId(uidA, uidB) {
  const a = String(uidA || '').trim();
  const b = String(uidB || '').trim();
  return a <= b ? `${a}_${b}` : `${b}_${a}`;
}

export function canEditDmMessage(msg, nowMs = Date.now()) {
  if (!msg || msg.isDeleted) return false;
  if (!['text', 'post_reply', 'shared_post'].includes(msg.msgType)) return false;
  return Number(msg.createdAtMs) > 0 && (nowMs - Number(msg.createdAtMs)) <= DM_EDIT_WINDOW_MS;
}

export function dmEditRemainingMinutes(msg, nowMs = Date.now()) {
  const rem = Math.max(0, DM_EDIT_WINDOW_MS - (nowMs - Number(msg?.createdAtMs || 0)));
  return Math.max(1, Math.ceil(rem / 60000));
}

export function isConversationMuted(partnerUid) {
  if (!partnerUid) return false;
  try {
    const raw = JSON.parse(localStorage.getItem(MUTED_KEY) || '[]');
    return Array.isArray(raw) && raw.includes(partnerUid);
  } catch {
    return false;
  }
}

export function setConversationMuted(partnerUid, muted) {
  if (!partnerUid) return;
  try {
    const raw = JSON.parse(localStorage.getItem(MUTED_KEY) || '[]');
    const set = new Set(Array.isArray(raw) ? raw : []);
    if (muted) set.add(partnerUid);
    else set.delete(partnerUid);
    localStorage.setItem(MUTED_KEY, JSON.stringify([...set]));
  } catch {}
}

function encPath(path) {
  return String(path || '').split('/').map((s) => encodeURIComponent(s)).join('/');
}

const fsStr = (v) => ({ stringValue: String(v ?? '') });
const fsInt = (v) => ({ integerValue: String(Math.round(Number(v) || 0)) });
const fsBool = (v) => ({ booleanValue: Boolean(v) });
const fsArr = (arr) => ({ arrayValue: { values: (arr || []).map((x) => fsStr(x)) } });

const sVal = (f, k) => f?.[k]?.stringValue || '';
const nVal = (f, k) => Number(f?.[k]?.integerValue || 0);
const bVal = (f, k) => Boolean(f?.[k]?.booleanValue);
const arrVal = (f, k) => (f?.[k]?.arrayValue?.values || []).map((x) => x.stringValue).filter(Boolean);

function genId(prefix) {
  const bytes = new Uint8Array(4);
  window.crypto?.getRandomValues?.(bytes);
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${prefix}_${Date.now()}_${hex}`;
}

async function fsReq(method, url, token, body = null, keepalive = false) {
  const opts = {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json; charset=UTF-8'
    }
  };
  if (keepalive) opts.keepalive = true;
  if (body != null) opts.body = JSON.stringify(body);
  const resp = await fetch(url, opts);
  const text = await resp.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch {}
  return { ok: resp.ok, status: resp.status, text, json };
}

async function queryBySingleField(token, collectionId, fieldName, op, valueObj, limit = 60) {
  const res = await fsReq('POST', `${FS_BASE}:runQuery`, token, {
    structuredQuery: {
      from: [{ collectionId }],
      where: {
        fieldFilter: {
          field: { fieldPath: fieldName },
          op,
          value: valueObj
        }
      },
      limit
    }
  });
  const error = Array.isArray(res.json) && res.json.find((row) => row.error)?.error;
  if (!res.ok || !Array.isArray(res.json) || error) {
    const code = error?.code || res.status;
    throw new Error(code === 429 ? 'Okuma kotasına ulaşıldı (429). Daha sonra tekrar dene.' : `Sohbet bilgileri yüklenemedi (${code})`);
  }
  return res.json.map((r) => r.document).filter(Boolean);
}

async function listSubcollection(token, path, pageSize = 100) {
  const res = await fsReq('GET', `${FS_BASE}/${encPath(path)}?pageSize=${pageSize}`, token);
  if (!res.ok || !res.json) throw new Error(`Sohbet verisi yüklenemedi (${res.status})`);
  return Array.isArray(res.json.documents) ? res.json.documents : [];
}

function parseConv(doc) {
  const f = doc?.fields || {};
  const idFromPath = String(doc?.name || '').split('/').pop() || '';
  return {
    convId: sVal(f, 'convId') || idFromPath,
    participantUids: arrVal(f, 'participantUids'),
    uid1: sVal(f, 'uid1'),
    uid2: sVal(f, 'uid2'),
    status: sVal(f, 'status') || 'accepted',
    requesterUid: sVal(f, 'requesterUid'),
    recipientUid: sVal(f, 'recipientUid'),
    requestMessageCount: nVal(f, 'requestMessageCount'),
    lastMessageText: sVal(f, 'lastMessageText'),
    lastMessageType: sVal(f, 'lastMessageType') || 'none',
    lastMessageSenderUid: sVal(f, 'lastMessageSenderUid'),
    lastMessageAtMs: nVal(f, 'lastMessageAtMs'),
    uid1LastReadAtMs: nVal(f, 'uid1LastReadAtMs'),
    uid2LastReadAtMs: nVal(f, 'uid2LastReadAtMs'),
    createdAtMs: nVal(f, 'createdAtMs'),
    updatedAtMs: nVal(f, 'updatedAtMs')
  };
}

function convToFields(c) {
  return {
    convId: fsStr(c.convId),
    participantUids: fsArr(c.participantUids),
    uid1: fsStr(c.uid1),
    uid2: fsStr(c.uid2),
    status: fsStr(c.status),
    requesterUid: fsStr(c.requesterUid),
    recipientUid: fsStr(c.recipientUid),
    requestMessageCount: fsInt(c.requestMessageCount),
    lastMessageText: fsStr(String(c.lastMessageText || '').slice(0, 200)),
    lastMessageType: fsStr(c.lastMessageType || 'none'),
    lastMessageSenderUid: fsStr(c.lastMessageSenderUid || ''),
    lastMessageAtMs: fsInt(c.lastMessageAtMs),
    uid1LastReadAtMs: fsInt(c.uid1LastReadAtMs),
    uid2LastReadAtMs: fsInt(c.uid2LastReadAtMs),
    createdAtMs: fsInt(c.createdAtMs),
    updatedAtMs: fsInt(c.updatedAtMs)
  };
}

function parseMsg(doc) {
  const f = doc?.fields || {};
  const idFromPath = String(doc?.name || '').split('/').pop() || '';
  return {
    msgId: sVal(f, 'msgId') || idFromPath,
    convId: sVal(f, 'convId'),
    senderUid: sVal(f, 'senderUid'),
    senderUsername: sVal(f, 'senderUsername'),
    senderDisplayName: sVal(f, 'senderDisplayName'),
    senderAvatar: sVal(f, 'senderAvatar'),
    msgType: sVal(f, 'msgType') || 'text',
    text: sVal(f, 'text'),
    mediaBase64: sVal(f, 'mediaBase64'),
    mediaUrl: sVal(f, 'mediaUrl'),
    mediaMime: sVal(f, 'mediaMime'),
    mediaDurationSec: nVal(f, 'mediaDurationSec'),
    chunkCount: nVal(f, 'chunkCount'),
    sharedPostId: sVal(f, 'sharedPostId'),
    sharedPostOwnerUid: sVal(f, 'sharedPostOwnerUid'),
    sharedPostOwnerUsername: sVal(f, 'sharedPostOwnerUsername'),
    sharedPostCaption: sVal(f, 'sharedPostCaption'),
    sharedPostThumb: sVal(f, 'sharedPostThumb'),
    isDeleted: bVal(f, 'isDeleted'),
    createdAtMs: nVal(f, 'createdAtMs'),
    editedAtMs: nVal(f, 'editedAtMs')
  };
}

function msgToFields(m) {
  return {
    msgId: fsStr(m.msgId),
    convId: fsStr(m.convId),
    senderUid: fsStr(m.senderUid),
    senderUsername: fsStr(String(m.senderUsername || '').slice(0, 24)),
    senderDisplayName: fsStr(String(m.senderDisplayName || '').slice(0, 50)),
    senderAvatar: fsStr(String(m.senderAvatar || '').slice(0, 40000)),
    msgType: fsStr(m.msgType),
    text: fsStr(String(m.text || '').slice(0, 1000)),
    mediaBase64: fsStr(String(m.mediaBase64 || '').slice(0, 320000)),
    mediaUrl: fsStr(String(m.mediaUrl || '').slice(0, 600)),
    mediaMime: fsStr(String(m.mediaMime || '').slice(0, 64)),
    mediaDurationSec: fsInt(Math.max(0, Math.min(120, Math.round(Number(m.mediaDurationSec) || 0)))),
    chunkCount: fsInt(Math.max(0, Math.min(MAX_CHUNKS, Math.round(Number(m.chunkCount) || 0)))),
    sharedPostId: fsStr(String(m.sharedPostId || '').slice(0, 48)),
    sharedPostOwnerUid: fsStr(String(m.sharedPostOwnerUid || '').slice(0, 128)),
    sharedPostOwnerUsername: fsStr(String(m.sharedPostOwnerUsername || '').slice(0, 24)),
    sharedPostCaption: fsStr(String(m.sharedPostCaption || '').slice(0, 220)),
    sharedPostThumb: fsStr(String(m.sharedPostThumb || '').slice(0, 140000)),
    isDeleted: fsBool(Boolean(m.isDeleted)),
    createdAtMs: fsInt(m.createdAtMs),
    editedAtMs: fsInt(m.editedAtMs)
  };
}

export function convPartnerUid(conv, myUid) {
  return conv.uid1 === myUid ? conv.uid2 : conv.uid1;
}

export function convMyLastRead(conv, myUid) {
  return conv.uid1 === myUid ? conv.uid1LastReadAtMs : conv.uid2LastReadAtMs;
}

export function convPartnerLastRead(conv, myUid) {
  return conv.uid1 === myUid ? conv.uid2LastReadAtMs : conv.uid1LastReadAtMs;
}

export function convHasUnread(conv, myUid) {
  return Boolean(conv.lastMessageSenderUid && conv.lastMessageSenderUid !== myUid && conv.lastMessageAtMs > convMyLastRead(conv, myUid));
}

export function isConvRequestLocked(conv, myUid) {
  return conv.status === 'request' && conv.requesterUid === myUid && conv.requestMessageCount >= 1;
}

async function fetchProfileHeaderRaw(token, uid) {
  const res = await fsReq('GET', `${FS_BASE}/${encPath(`public_profiles/${uid}`)}`, token);
  if (!res.ok || !res.json?.fields) return { uid, username: '', displayName: 'Kullanıcı', avatarBase64: '', isPrivate: false };
  const f = res.json.fields;
  return {
    uid: sVal(f, 'uid') || uid,
    username: sVal(f, 'username'),
    displayName: sVal(f, 'displayName') || sVal(f, 'username') || 'Kullanıcı',
    avatarBase64: sVal(f, 'avatarThumbBase64') || sVal(f, 'avatarBase64'),
    isPrivate: bVal(f, 'isPrivate')
  };
}

async function fetchBlockedSets(token, myUid) {
  const [byDocs, agDocs] = await Promise.all([
    queryBySingleField(token, 'blocks', 'blockerUid', 'EQUAL', fsStr(myUid), 80),
    queryBySingleField(token, 'blocks', 'blockedUid', 'EQUAL', fsStr(myUid), 80)
  ]);
  return {
    byMe: new Set(byDocs.map((d) => sVal(d.fields, 'blockedUid')).filter(Boolean)),
    againstMe: new Set(agDocs.map((d) => sVal(d.fields, 'blockerUid')).filter(Boolean))
  };
}

async function checkFollowAcceptedEither(token, uidA, uidB) {
  for (const fid of [`${uidA}_${uidB}`, `${uidB}_${uidA}`]) {
    const res = await fsReq('GET', `${FS_BASE}/${encPath(`follows/${fid}`)}`, token);
    if (res.ok && sVal(res.json?.fields, 'status') === 'accepted') return true;
  }
  return false;
}

export async function fetchUserPresence(uid) {
  const auth = await ensureFirebaseAuth().catch(() => null);
  if (!auth?.idToken || !uid) return { uid, isOnline: false, lastOnlineAtMs: 0, effectiveOnline: false };
  const res = await fsReq('GET', `${FS_BASE}/${encPath(`dm_presence/${uid}`)}`, auth.idToken);
  if (!res.ok || !res.json?.fields) return { uid, isOnline: false, lastOnlineAtMs: 0, effectiveOnline: false };
  const f = res.json.fields;
  const isOnline = bVal(f, 'isOnline');
  const lastOnlineAtMs = nVal(f, 'lastOnlineAtMs');
  const effectiveOnline = isOnline && lastOnlineAtMs > 0 && (Date.now() - lastOnlineAtMs) < 75000;
  return { uid, isOnline, lastOnlineAtMs, effectiveOnline };
}

export async function setMyOnlinePresence(isOnline, { keepalive = false } = {}) {
  const auth = await ensureFirebaseAuth().catch(() => null);
  const uid = auth?.uid || getStoredUid();
  if (!uid || !auth?.idToken) return;
  await fsReq(
    'PATCH',
    `${FS_BASE}/${encPath(`dm_presence/${uid}`)}`,
    auth.idToken,
    { fields: { uid: fsStr(uid), isOnline: fsBool(isOnline), lastOnlineAtMs: fsInt(Date.now()) } },
    keepalive
  ).catch(() => {});
}

export async function setChatActivityState(convId, state = 'idle') {
  if (!convId) return;
  const auth = await ensureFirebaseAuth().catch(() => null);
  if (!auth?.uid || !auth?.idToken) return;
  const safeState = DM_CHAT_STATES.includes(state) ? state : 'idle';
  await fsReq(
    'PATCH',
    `${FS_BASE}/${encPath(`dm_conversations/${convId}/presence/${auth.uid}`)}`,
    auth.idToken,
    { fields: { uid: fsStr(auth.uid), state: fsStr(safeState), updatedAtMs: fsInt(Date.now()) } }
  ).catch(() => {});
}

export async function fetchPartnerActivityState(convId, partnerUid) {
  if (!convId || !partnerUid) return 'idle';
  const auth = await ensureFirebaseAuth().catch(() => null);
  if (!auth?.idToken) return 'idle';
  const res = await fsReq('GET', `${FS_BASE}/${encPath(`dm_conversations/${convId}/presence/${partnerUid}`)}`, auth.idToken);
  if (!res.ok || !res.json?.fields) return 'idle';
  const updated = nVal(res.json.fields, 'updatedAtMs');
  if (Date.now() - updated > 8500) return 'idle';
  return sVal(res.json.fields, 'state') || 'idle';
}

export async function fetchMyConversations() {
  const auth = await ensureFirebaseAuth();
  if (!auth?.uid || !auth?.idToken) throw new Error('Sunucuya bağlanılamadı');
  const myUid = auth.uid;
  const token = auth.idToken;
  const [docs, blocked] = await Promise.all([
    queryBySingleField(token, 'dm_conversations', 'participantUids', 'ARRAY_CONTAINS', fsStr(myUid), 60),
    fetchBlockedSets(token, myUid)
  ]);

  const rows = await Promise.all(docs.map(async (doc) => {
    const conv = parseConv(doc);
    if (!conv.convId) return null;
    if (conv.status === 'declined' && conv.recipientUid === myUid) return null;
    const partnerUid = convPartnerUid(conv, myUid);
    if (!partnerUid) return null;
    const [partner, presence] = await Promise.all([
      fetchProfileHeaderRaw(token, partnerUid),
      fetchUserPresence(partnerUid)
    ]);
    const isMuted = isConversationMuted(partnerUid);
    return {
      conversation: conv,
      partner,
      partnerPresence: presence,
      isBlockedByMe: blocked.byMe.has(partnerUid),
      hasBlockedMe: blocked.againstMe.has(partnerUid),
      isMuted,
      isUnread: convHasUnread(conv, myUid) && !isMuted
    };
  }));
  return rows.filter(Boolean).sort((a, b) => b.conversation.updatedAtMs - a.conversation.updatedAtMs);
}

export async function countUnreadConversations() {
  try {
    const auth = await ensureFirebaseAuth().catch(() => null);
    if (!auth?.uid || !auth?.idToken) return 0;
    const docs = await queryBySingleField(auth.idToken, 'dm_conversations', 'participantUids', 'ARRAY_CONTAINS', fsStr(auth.uid), 50);
    return docs.filter((d) => {
      const c = parseConv(d);
      const partnerUid = convPartnerUid(c, auth.uid);
      return !(c.status === 'declined' && c.recipientUid === auth.uid) &&
        !isConversationMuted(partnerUid) &&
        convHasUnread(c, auth.uid);
    }).length;
  } catch {
    return 0;
  }
}

export async function getOrCreateConversation(partnerUid) {
  const local = loadLocalProfile();
  if (!isValidUsername(local.username)) {
    const err = new Error('USERNAME_REQUIRED:Sohbet başlatmak için önce kullanıcı adı oluşturmalısın.');
    err.code = 'USERNAME_REQUIRED';
    throw err;
  }
  const auth = await ensureFirebaseAuth();
  if (!auth?.uid || !auth?.idToken) throw new Error('Sunucuya bağlanılamadı');
  const myUid = auth.uid;
  const token = auth.idToken;
  if (!partnerUid || partnerUid === myUid) throw new Error('Geçersiz kullanıcı');

  const [partner, blocked] = await Promise.all([
    fetchProfileHeaderRaw(token, partnerUid),
    fetchBlockedSets(token, myUid)
  ]);
  if (!isValidUsername(partner.username)) {
    throw new Error('Bu kullanıcının henüz kullanıcı adı yok.');
  }
  const isBlockedByMe = blocked.byMe.has(partnerUid);
  const hasBlockedMe = blocked.againstMe.has(partnerUid);
  const convId = pairConvId(myUid, partnerUid);

  const getRes = await fsReq('GET', `${FS_BASE}/${encPath(`dm_conversations/${convId}`)}`, token);
  let conv = null;
  if (getRes.ok && getRes.json?.fields) {
    conv = parseConv(getRes.json);
  } else {
    if (isBlockedByMe || hasBlockedMe) throw new Error('Engellenen bir hesapla sohbet başlatılamaz.');
    const uid1 = myUid < partnerUid ? myUid : partnerUid;
    const uid2 = myUid < partnerUid ? partnerUid : myUid;
    const linked = await checkFollowAcceptedEither(token, myUid, partnerUid);
    const now = Date.now();
    const next = {
      convId,
      participantUids: [uid1, uid2],
      uid1,
      uid2,
      status: linked ? 'accepted' : 'request',
      requesterUid: myUid,
      recipientUid: partnerUid,
      requestMessageCount: 0,
      lastMessageText: '',
      lastMessageType: 'none',
      lastMessageSenderUid: '',
      lastMessageAtMs: 0,
      uid1LastReadAtMs: uid1 === myUid ? now : 0,
      uid2LastReadAtMs: uid2 === myUid ? now : 0,
      createdAtMs: now,
      updatedAtMs: now
    };
    const putRes = await fsReq(
      'PATCH',
      `${FS_BASE}/${encPath(`dm_conversations/${convId}`)}?currentDocument.exists=false`,
      token,
      { fields: convToFields(next) }
    );
    if (putRes.ok) {
      conv = next;
    } else {
      const retry = await fsReq('GET', `${FS_BASE}/${encPath(`dm_conversations/${convId}`)}`, token);
      if (retry.ok && retry.json?.fields) conv = parseConv(retry.json);
      else throw new Error(`Sohbet oluşturulamadı (${putRes.status})`);
    }
  }
  const presence = await fetchUserPresence(partnerUid);
  const isMuted = isConversationMuted(partnerUid);
  return {
    conversation: conv,
    partner,
    partnerPresence: presence,
    isBlockedByMe,
    hasBlockedMe,
    isMuted,
    isUnread: convHasUnread(conv, myUid) && !isMuted
  };
}

export async function refreshConversation(convId) {
  const auth = await ensureFirebaseAuth().catch(() => null);
  if (!auth?.idToken) return null;
  const res = await fsReq('GET', `${FS_BASE}/${encPath(`dm_conversations/${convId}`)}`, auth.idToken);
  return res.ok && res.json?.fields ? parseConv(res.json) : null;
}

export async function updateMessageRequestStatus(conv, nextStatus) {
  const auth = await ensureFirebaseAuth();
  if (!auth?.uid || !auth?.idToken) throw new Error('Bağlantı yok');
  if (conv.recipientUid !== auth.uid) throw new Error('Yalnızca alıcı yanıtlayabilir');
  const now = Math.max(Date.now(), conv.createdAtMs);
  const updated = {
    ...conv,
    status: nextStatus,
    uid1LastReadAtMs: conv.uid1 === auth.uid ? Math.max(conv.uid1LastReadAtMs, now) : conv.uid1LastReadAtMs,
    uid2LastReadAtMs: conv.uid2 === auth.uid ? Math.max(conv.uid2LastReadAtMs, now) : conv.uid2LastReadAtMs,
    updatedAtMs: now
  };
  const res = await fsReq('PATCH', `${FS_BASE}/${encPath(`dm_conversations/${conv.convId}`)}`, auth.idToken, {
    fields: convToFields(updated)
  });
  if (!res.ok) throw new Error(`İstek güncellenemedi (${res.status})`);
  return updated;
}

export async function markConversationRead(conv) {
  const auth = await ensureFirebaseAuth().catch(() => null);
  if (!auth?.uid || !auth?.idToken) return conv;
  const myUid = auth.uid;
  if (convMyLastRead(conv, myUid) >= conv.lastMessageAtMs) return conv;
  const now = Math.max(Date.now(), conv.createdAtMs);
  const updated = {
    ...conv,
    uid1LastReadAtMs: conv.uid1 === myUid ? Math.max(conv.uid1LastReadAtMs, now) : conv.uid1LastReadAtMs,
    uid2LastReadAtMs: conv.uid2 === myUid ? Math.max(conv.uid2LastReadAtMs, now) : conv.uid2LastReadAtMs,
    updatedAtMs: Math.max(now, conv.updatedAtMs)
  };
  const res = await fsReq('PATCH', `${FS_BASE}/${encPath(`dm_conversations/${conv.convId}`)}`, auth.idToken, {
    fields: convToFields(updated)
  });
  return res.ok ? updated : conv;
}

export async function fetchMessages(convId) {
  const auth = await ensureFirebaseAuth();
  if (!auth?.idToken) throw new Error('Bağlantı yok');
  const docs = await listSubcollection(auth.idToken, `dm_conversations/${convId}/messages`, 100);
  return docs.map(parseMsg).sort((a, b) => a.createdAtMs - b.createdAtMs);
}

const dmVideoBlobCache = new Map();

export async function loadDmVideoBlobUrl(convId, msgId, chunkCount, mime = 'video/mp4') {
  const key = `${convId}_${msgId}`;
  if (dmVideoBlobCache.has(key)) return dmVideoBlobCache.get(key);
  const bytes = await loadDmMediaChunks(convId, msgId, chunkCount);
  const url = URL.createObjectURL(new Blob([bytes], { type: mime || 'video/mp4' }));
  dmVideoBlobCache.set(key, url);
  return url;
}

async function loadDmMediaChunks(convId, msgId, chunkCount) {
  const auth = await ensureFirebaseAuth();
  if (!auth?.idToken) throw new Error('Bağlantı yok');
  const docs = await listSubcollection(auth.idToken, `dm_conversations/${convId}/messages/${msgId}/media_chunks`, 25);
  const count = chunkCount || docs.length;
  const parts = docs
    .map((d) => ({ index: nVal(d.fields, 'index'), b64: sVal(d.fields, 'b64') }))
    .sort((a, b) => a.index - b.index)
    .slice(0, count);
  if (!count || parts.length !== count || parts.some((part, index) => part.index !== index || !part.b64)) {
    throw new Error('Medya parçaları eksik');
  }
  return decodeMediaBase64(parts.map((p) => p.b64).join(''));
}

function decodeMediaBase64(payload) {
  return Uint8Array.from(atob(payload), (char) => char.charCodeAt(0));
}

export async function loadDmAudioBlob(convId, message) {
  const inline = String(message.mediaBase64 || '');
  const header = /^data:([^;,]+)(?:;[^,]+)?;base64,/i.exec(inline);
  const mime = (header?.[1] || message.mediaMime || 'audio/mp4').split(';')[0].toLowerCase();
  const bytes = message.chunkCount > 0
    ? await loadDmMediaChunks(convId, message.msgId, message.chunkCount)
    : decodeMediaBase64(inline.slice(header?.[0].length || 0));
  if (!bytes.length) throw new Error('Ses verisi boş');
  return new Blob([bytes], { type: ['audio/x-m4a', 'video/mp4'].includes(mime) ? 'audio/mp4' : mime });
}

async function fileToBase64Pure(file) {
  const buf = await file.arrayBuffer();
  const bytes = new Uint8Array(buf);
  let bin = '';
  const step = 0x8000;
  for (let i = 0; i < bytes.length; i += step) {
    bin += String.fromCharCode(...bytes.subarray(i, i + step));
  }
  return btoa(bin);
}

export async function preparePhotoDataUrlForDm(file) {
  const raw = await new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result || ''));
    r.onerror = reject;
    r.readAsDataURL(file);
  });
  const encoded = await compressDataUrlForCloud(raw, 290000, 960, { forceJpeg: true });
  if (!encoded) throw new Error('Fotoğraf işlenemedi');
  return encoded;
}

async function sendDmNotificationBestEffort(token, targetUid, fromUid, profile, type, postId, postOwnerUid, previewText) {
  if (!targetUid || targetUid === fromUid) return;
  const now = Date.now();
  const notifId = genId('dm').slice(0, 64);
  await fsReq('PATCH', `${FS_BASE}/${encPath(`notifications/${targetUid}/items/${notifId}`)}`, token, {
    fields: {
      notifId: fsStr(notifId),
      targetUid: fsStr(targetUid),
      type: fsStr(type),
      fromUid: fsStr(fromUid),
      fromUsername: fsStr(normalizeUsername(profile.username).slice(0, 24)),
      fromDisplayName: fsStr(String(profile.displayName || '').slice(0, 50)),
      fromAvatar: fsStr(String(profile.avatarThumbBase64 || '').slice(0, 40000)),
      postId: fsStr(String(postId || '').slice(0, 48)),
      postOwnerUid: fsStr(String(postOwnerUid || '').slice(0, 128)),
      previewText: fsStr(String(previewText || '').slice(0, 150)),
      read: fsBool(false),
      createdAtMs: fsInt(now)
    }
  }).catch(() => {});
}

export async function sendMessage({
  conv,
  myProfile = loadLocalProfile(),
  msgType,
  text = '',
  mediaBase64 = '',
  mediaUrl = '',
  mediaMime = '',
  mediaDurationSec = 0,
  videoFile = null,
  sharedPost = null
}) {
  if (!isValidUsername(myProfile?.username)) {
    const err = new Error('USERNAME_REQUIRED:Mesaj göndermek için önce kullanıcı adı oluşturmalısın.');
    err.code = 'USERNAME_REQUIRED';
    throw err;
  }
  const auth = await ensureFirebaseAuth();
  if (!auth?.uid || !auth?.idToken) throw new Error('Bağlantı yok');
  const myUid = auth.uid;
  const token = auth.idToken;

  const latest = (await refreshConversation(conv.convId)) || conv;
  if (latest.status === 'declined') throw new Error('Bu mesaj isteği reddedilmiş.');
  if (isConvRequestLocked(latest, myUid)) {
    throw new Error('Karşı taraf isteğini kabul edene kadar yalnızca 1 mesaj gönderebilirsin.');
  }
  const partnerUid = convPartnerUid(latest, myUid);
  const blocked = await fetchBlockedSets(token, myUid);
  if (blocked.byMe.has(partnerUid) || blocked.againstMe.has(partnerUid)) {
    throw new Error('Engellenmiş kullanıcıya mesaj gönderilemez.');
  }

  const now = Date.now();
  const msgId = latest.status === 'request' ? 'req_first' : genId('m');
  let finalChunkCount = 0;
  let finalMediaB64 = mediaBase64 || '';
  let finalMime = mediaMime || '';
  let finalDur = Number(mediaDurationSec) || 0;

  const chunkedAudio = msgType === 'audio' && finalMediaB64.length > 315000;
  if ((msgType === 'video' && videoFile) || chunkedAudio) {
    if (videoFile?.size > 7500000) throw new Error('Video boyutu en fazla 7.5 MB olabilir.');
    const b64All = chunkedAudio ? finalMediaB64.split(';base64,')[1] || '' : await fileToBase64Pure(videoFile);
    const totalChunks = Math.ceil(b64All.length / CHUNK_SIZE_CHARS);
    if (totalChunks < 1 || totalChunks > MAX_CHUNKS) throw new Error('Medya boyutu sınırı aşıldı.');
    for (let idx = 0; idx < totalChunks; idx += 1) {
      const part = b64All.slice(idx * CHUNK_SIZE_CHARS, (idx + 1) * CHUNK_SIZE_CHARS);
      const cRes = await fsReq(
        'PATCH',
        `${FS_BASE}/${encPath(`dm_conversations/${latest.convId}/messages/${msgId}/media_chunks/${idx}`)}`,
        token,
        { fields: { ownerUid: fsStr(myUid), index: fsInt(idx), b64: fsStr(part) } }
      );
      if (!cRes.ok) throw new Error(`Medya yüklenemedi (${cRes.status})`);
    }
    finalChunkCount = totalChunks;
    finalMime = chunkedAudio ? finalMime || 'audio/mp4' : videoFile.type || 'video/mp4';
    finalMediaB64 = '';
  } else if (msgType === 'photo' && finalMediaB64.length > 315000) {
    throw new Error('Medya boyutu sınırı aşıldı (~300 KB).');
  }

  const thumb = sharedPost ? String(sharedPost.photos?.[0] || sharedPost.posterBase64 || '').slice(0, 135000) : '';
  const msg = {
    msgId,
    convId: latest.convId,
    senderUid: myUid,
    senderUsername: normalizeUsername(myProfile.username),
    senderDisplayName: String(myProfile.displayName || '').slice(0, 50),
    senderAvatar: String(myProfile.avatarThumbBase64 || myProfile.avatarBase64 || '').slice(0, 40000),
    msgType,
    text: String(text || '').trim().slice(0, 1000),
    mediaBase64: finalMediaB64,
    mediaUrl: String(mediaUrl || '').slice(0, 600),
    mediaMime: String(finalMime || '').slice(0, 64),
    mediaDurationSec: Math.max(0, Math.min(120, Math.round(finalDur))),
    chunkCount: finalChunkCount,
    sharedPostId: String(sharedPost?.postId || '').slice(0, 48),
    sharedPostOwnerUid: String(sharedPost?.ownerUid || '').slice(0, 128),
    sharedPostOwnerUsername: String(sharedPost?.ownerUsername || '').slice(0, 24),
    sharedPostCaption: String(sharedPost?.caption || '').slice(0, 220),
    sharedPostThumb: thumb,
    isDeleted: false,
    createdAtMs: now,
    editedAtMs: 0
  };

  const mRes = await fsReq(
    'PATCH',
    `${FS_BASE}/${encPath(`dm_conversations/${latest.convId}/messages/${msgId}`)}?currentDocument.exists=false`,
    token,
    { fields: msgToFields(msg) }
  );
  if (!mRes.ok) throw new Error(`Mesaj gönderilemedi (${mRes.status})`);

  const previewMap = {
    text: msg.text.slice(0, 120),
    photo: '📷 Fotoğraf',
    video: '🎬 Video',
    audio: '🎤 Sesli mesaj',
    gif: 'GIF',
    shared_post: (msg.text || '📌 Gönderi paylaştı').slice(0, 120),
    post_reply: (msg.text || '↩ Gönderiye yanıt verdi').slice(0, 120)
  };
  const preview = previewMap[msgType] || 'Mesaj';

  const updatedConv = {
    ...latest,
    requestMessageCount: latest.status === 'request' ? 1 : latest.requestMessageCount,
    lastMessageText: preview.slice(0, 200),
    lastMessageType: msgType,
    lastMessageSenderUid: myUid,
    lastMessageAtMs: now,
    uid1LastReadAtMs: latest.uid1 === myUid ? Math.max(latest.uid1LastReadAtMs, now) : latest.uid1LastReadAtMs,
    uid2LastReadAtMs: latest.uid2 === myUid ? Math.max(latest.uid2LastReadAtMs, now) : latest.uid2LastReadAtMs,
    updatedAtMs: Math.max(now, latest.createdAtMs)
  };
  await fsReq('PATCH', `${FS_BASE}/${encPath(`dm_conversations/${latest.convId}`)}`, token, {
    fields: convToFields(updatedConv)
  });

  const notifType = latest.status === 'request' ? 'dm_request' : (msgType === 'post_reply' ? 'dm_post_reply' : 'dm_message');
  sendDmNotificationBestEffort(token, partnerUid, myUid, myProfile, notifType, msg.sharedPostId, msg.sharedPostOwnerUid, preview);

  return { conversation: updatedConv, message: msg };
}

export async function editDmMessage(conv, msg, newText) {
  const trimmed = String(newText || '').trim().slice(0, 1000);
  if (!trimmed) throw new Error('Mesaj boş olamaz');
  if (!canEditDmMessage(msg)) throw new Error('15 dakikalık düzenleme süresi doldu');
  const auth = await ensureFirebaseAuth();
  if (!auth?.uid || !auth?.idToken) throw new Error('Bağlantı yok');
  if (msg.senderUid !== auth.uid) throw new Error('Yalnızca kendi mesajını düzenleyebilirsin');

  const now = Math.max(Date.now(), msg.createdAtMs);
  const updated = { ...msg, text: trimmed, editedAtMs: now };
  const res = await fsReq('PATCH', `${FS_BASE}/${encPath(`dm_conversations/${conv.convId}/messages/${msg.msgId}`)}`, auth.idToken, {
    fields: msgToFields(updated)
  });
  if (!res.ok) throw new Error(`Düzenlenemedi (${res.status})`);
  if (conv.lastMessageAtMs <= msg.createdAtMs) {
    await fsReq('PATCH', `${FS_BASE}/${encPath(`dm_conversations/${conv.convId}`)}`, auth.idToken, {
      fields: convToFields({ ...conv, lastMessageText: trimmed.slice(0, 200), updatedAtMs: Math.max(Date.now(), conv.createdAtMs) })
    }).catch(() => {});
  }
  return updated;
}

export async function softDeleteDmMessage(conv, msg) {
  const auth = await ensureFirebaseAuth();
  if (!auth?.uid || !auth?.idToken) throw new Error('Bağlantı yok');
  if (msg.senderUid !== auth.uid) throw new Error('Yalnızca kendi mesajını silebilirsin');

  const now = Math.max(Date.now(), msg.createdAtMs);
  const tombstone = {
    ...msg,
    isDeleted: true,
    text: '',
    mediaBase64: '',
    mediaUrl: '',
    sharedPostThumb: '',
    editedAtMs: now
  };
  const res = await fsReq('PATCH', `${FS_BASE}/${encPath(`dm_conversations/${conv.convId}/messages/${msg.msgId}`)}`, auth.idToken, {
    fields: msgToFields(tombstone)
  });
  if (!res.ok) throw new Error(`Silinemedi (${res.status})`);
  if (conv.lastMessageAtMs <= msg.createdAtMs) {
    await fsReq('PATCH', `${FS_BASE}/${encPath(`dm_conversations/${conv.convId}`)}`, auth.idToken, {
      fields: convToFields({ ...conv, lastMessageType: 'deleted', lastMessageText: 'Bu mesaj silindi', updatedAtMs: Math.max(Date.now(), conv.createdAtMs) })
    }).catch(() => {});
  }
  return tombstone;
}
