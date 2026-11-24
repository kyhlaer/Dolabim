const FIREBASE_PROJECT_ID = 'dolabim-stats';
const FIREBASE_API_KEY = 'AIzaSyDcAnCMY6oXLShrR36brMLr6_d7Dhu1DGQ';
const APP_VERSION = '1.43.274';
const STORAGE_KEY = 'dolabim_telemetry_v1';
const MIN_VALID_EPOCH_MS = 1700000000001;
const MAX_VALID_EPOCH_MS = 4102444800000;
const UID_REGEX = /^[A-Za-z0-9_-]{1,128}$/;

let started = false;
let isOnline = false;
let sessionStartedAtMs = 0;
let lastActiveAtMs = 0;
let sessionId = '';
let baseLifetimeSec = 0;
let entrySource = 'web_load';
let currentTab = 'wardrobe';
let lastTabSwitchAtMs = 0;
let currentTheme = 'MODERN';
let currentLanguage = 'TR';
let wardrobeCounts = { items: 0, wishlist: 0, outfits: 0 };
let heartbeatTimer = null;
let soonFlushTimer = null;
let flushInFlight = false;
let flushQueued = false;
let recoveredUid403Once = false;
let memStore = {};

const sessionFeatureCounts = Object.create(null);
const sessionTabSeconds = Object.create(null);
const sessionFeatureSeconds = Object.create(null);
const featureTimers = new Map();
const recentEvents = [];

function isLoopbackHost() {
  const h = (window.location?.hostname || '').toLowerCase();
  return h === 'localhost' || h === '127.0.0.1' || h === '[::1]';
}

function clampStr(v, maxLen) {
  return String(v ?? '').slice(0, maxLen);
}

function clampInt(v, minVal, maxVal) {
  const n = Math.trunc(Number(v) || 0);
  return Math.max(minVal, Math.min(maxVal, n));
}

function readLocalStore() {
  try {
    const raw = window.localStorage?.getItem(STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    if (parsed && typeof parsed === 'object') {
      memStore = { ...memStore, ...parsed };
    }
  } catch {
    // Safari Private / restricted WebViews fallback cleanly to memStore
  }
  return { ...memStore };
}

function writeLocalStore(data) {
  memStore = { ...(data && typeof data === 'object' ? data : {}) };
  try {
    window.localStorage?.setItem(STORAGE_KEY, JSON.stringify(memStore));
  } catch {
    // ignore storage quota / Safari private errors
  }
}

export function isAnalyticsEnabled() {
  const st = readLocalStore();
  return st.enabled !== false;
}

export function setAnalyticsEnabled(enabled) {
  const st = readLocalStore();
  st.enabled = Boolean(enabled);
  writeLocalStore(st);
  if (!st.enabled) {
    stopWebTelemetry();
  } else if (!started) {
    startWebTelemetry({
      theme: currentTheme,
      language: currentLanguage,
      wardrobeCounts
    });
  }
}

export function getStoredUid() {
  const st = readLocalStore();
  return UID_REGEX.test(st.uid || '') ? st.uid : '';
}

export async function ensureFirebaseAuth() {
  return ensureAnonAuth();
}

export function updateTelemetryProfile({ username, displayName, avatarThumb } = {}) {
  const st = readLocalStore();
  if (typeof username === 'string') st.profileUsername = clampStr(username.trim(), 32);
  if (typeof displayName === 'string') st.profileDisplayName = clampStr(displayName.trim(), 60);
  if (typeof avatarThumb === 'string') {
    st.profileAvatar = (avatarThumb.startsWith('data:image/') && avatarThumb.length <= 35000) ? avatarThumb : '';
  }
  writeLocalStore(st);
  if (started && isOnline && !isLoopbackHost()) {
    flushNow(true, false);
  }
}

export function updateTelemetryContext({ theme, language, itemsCount, wishlistCount, outfitsCount } = {}) {
  if (typeof theme === 'string' && theme) currentTheme = clampStr(theme, 32);
  if (typeof language === 'string' && language) currentLanguage = clampStr(language, 12);
  if (Number.isFinite(itemsCount)) wardrobeCounts.items = clampInt(itemsCount, 0, 100000);
  if (Number.isFinite(wishlistCount)) wardrobeCounts.wishlist = clampInt(wishlistCount, 0, 100000);
  if (Number.isFinite(outfitsCount)) wardrobeCounts.outfits = clampInt(outfitsCount, 0, 100000);
}

function detectPlatform() {
  try {
    if (window.matchMedia?.('(display-mode: standalone)')?.matches || window.navigator?.standalone === true) {
      return 'web_pwa';
    }
  } catch {
    // ignore
  }
  return 'web_browser';
}

function detectDeviceSummary() {
  const ua = navigator.userAgent || '';
  let model = 'Web Browser';
  if (/iPhone/i.test(ua)) model = 'iPhone';
  else if (/iPad/i.test(ua)) model = 'iPad';
  else if (/Android/i.test(ua)) {
    const m = ua.match(/Android[^;]*;\s*([^)]+)\)/);
    model = m ? m[1].split('Build/')[0].trim() : 'Android Web';
  } else if (/Windows/i.test(ua)) model = 'Windows PC';
  else if (/Macintosh/i.test(ua)) model = 'Mac';
  else if (/Linux/i.test(ua)) model = 'Linux';

  let browser = 'Browser';
  if (/Edg\//i.test(ua)) browser = 'Edge';
  else if (/CriOS|Chrome/i.test(ua)) browser = 'Chrome';
  else if (/Safari/i.test(ua) && !/Chrome/i.test(ua)) browser = 'Safari';
  else if (/Firefox/i.test(ua)) browser = 'Firefox';

  let tz = '';
  try {
    tz = Intl.DateTimeFormat().resolvedOptions().timeZone || '';
  } catch {
    tz = '';
  }
  let netType = 'web';
  try {
    netType = navigator.onLine === false ? 'offline' : (navigator.connection?.effectiveType || 'web');
  } catch {
    netType = 'web';
  }

  return {
    deviceModel: clampStr(`${model} (${browser})`, 120),
    osInfo: clampStr(navigator.platform || browser, 100),
    screenSize: clampStr(`${window.screen?.width || window.innerWidth || 0}x${window.screen?.height || window.innerHeight || 0}`, 32),
    timezone: clampStr(tz, 64),
    networkType: clampStr(netType, 32)
  };
}

function accumulateTabSec(nowMs) {
  if (lastTabSwitchAtMs <= 0) {
    lastTabSwitchAtMs = nowMs;
    return;
  }
  const delta = clampInt((nowMs - lastTabSwitchAtMs) / 1000, 0, 7200);
  lastTabSwitchAtMs = nowMs;
  if (delta <= 0) return;
  sessionTabSeconds[currentTab] = (sessionTabSeconds[currentTab] || 0) + delta;
  const st = readLocalStore();
  st.lifeTabSecs = st.lifeTabSecs || {};
  if (Object.keys(st.lifeTabSecs).length < 10 || currentTab in st.lifeTabSecs) {
    st.lifeTabSecs[currentTab] = (Number(st.lifeTabSecs[currentTab]) || 0) + delta;
    writeLocalStore(st);
  }
}

function appendEvent(kind, name, detail = '') {
  const entry = clampStr(`${Date.now()}|${kind}|${name}|${String(detail).replace(/\|/g, '_')}`, 160);
  if (recentEvents.length >= 40) recentEvents.shift();
  recentEvents.push(entry);
}

function scheduleSoonFlush(delayMs = 2000) {
  if (!started || !isOnline || isLoopbackHost()) return;
  if (soonFlushTimer) clearTimeout(soonFlushTimer);
  soonFlushTimer = setTimeout(() => {
    soonFlushTimer = null;
    if (started && isOnline) flushNow(true, false);
  }, delayMs);
}

export function trackTabView(tabName) {
  if (!isAnalyticsEnabled()) return;
  const clean = clampStr(tabName || 'wardrobe', 32);
  if (currentTab === clean) return;
  accumulateTabSec(Date.now());
  currentTab = clean;
  appendEvent('tab', clean);
  scheduleSoonFlush(2000);
}

export function trackFeature(name, detail = '') {
  if (!isAnalyticsEnabled()) return;
  const clean = clampStr(String(name || '').replace(/\|/g, '_'), 40);
  if (!clean) return;
  sessionFeatureCounts[clean] = (sessionFeatureCounts[clean] || 0) + 1;
  const st = readLocalStore();
  st.lifeFeatCounts = st.lifeFeatCounts || {};
  if (Object.keys(st.lifeFeatCounts).length < 50 || clean in st.lifeFeatCounts) {
    st.lifeFeatCounts[clean] = (Number(st.lifeFeatCounts[clean]) || 0) + 1;
    writeLocalStore(st);
  }
  appendEvent('feature', clean, detail);
  scheduleSoonFlush(2200);
}

export function startFeatureTimer(name) {
  const clean = clampStr(name || '', 40);
  if (clean) featureTimers.set(clean, Date.now());
}

export function endFeatureTimer(name) {
  if (!isAnalyticsEnabled()) return;
  const clean = clampStr(name || '', 40);
  const s = featureTimers.get(clean);
  if (!s) return;
  featureTimers.delete(clean);
  const delta = clampInt((Date.now() - s) / 1000, 0, 7200);
  if (delta <= 0) return;
  sessionFeatureSeconds[clean] = (sessionFeatureSeconds[clean] || 0) + delta;
  const st = readLocalStore();
  st.lifeFeatSecs = st.lifeFeatSecs || {};
  if (Object.keys(st.lifeFeatSecs).length < 30 || clean in st.lifeFeatSecs) {
    st.lifeFeatSecs[clean] = (Number(st.lifeFeatSecs[clean]) || 0) + delta;
    writeLocalStore(st);
  }
}

export function trackTelemetryError(tag, message) {
  if (!isAnalyticsEnabled()) return;
  const cleanMsg = clampStr(`${String(tag || 'err').slice(0, 30)}: ${String(message || '').replace(/[\r\n\t]+/g, ' ')}`, 180);
  const st = readLocalStore();
  st.errorCount = clampInt((Number(st.errorCount) || 0) + 1, 0, 100000);
  st.lastError = cleanMsg;
  writeLocalStore(st);
  trackFeature('error', String(tag || 'err').slice(0, 32));
}

function intVal(v) {
  return { integerValue: String(Math.trunc(Number(v) || 0)) };
}

function strVal(v) {
  return { stringValue: String(v ?? '') };
}

function intMapVal(obj, maxEntries) {
  const fields = {};
  let cnt = 0;
  for (const [k, v] of Object.entries(obj || {})) {
    if (cnt >= maxEntries) break;
    const ck = clampStr(k, 40);
    if (!ck) continue;
    fields[ck] = intVal(Math.max(0, Math.trunc(Number(v) || 0)));
    cnt += 1;
  }
  return { mapValue: { fields } };
}

export function buildDeviceFirestoreDoc(input) {
  const first = clampInt(input.firstSeenAtMs, MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS);
  const last = Math.max(first, clampInt(input.lastSeenAtMs, MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS));
  const platform = ['android', 'web_pwa', 'web_browser'].includes(input.platform) ? input.platform : 'web_browser';
  const fields = {
    uid: strVal(clampStr(input.uid, 128)),
    anonTag: strVal(clampStr(input.anonTag || 'W-USER', 24) || 'W-USER'),
    platform: strVal(platform),
    appVersion: strVal(clampStr(input.appVersion || APP_VERSION, 32) || APP_VERSION),
    deviceModel: strVal(clampStr(input.deviceModel, 120)),
    osInfo: strVal(clampStr(input.osInfo, 100)),
    screenSize: strVal(clampStr(input.screenSize, 32)),
    language: strVal(clampStr(input.language || 'TR', 12) || 'TR'),
    theme: strVal(clampStr(input.theme || 'MODERN', 32) || 'MODERN'),
    timezone: strVal(clampStr(input.timezone, 64)),
    country: strVal(clampStr(input.country, 64)),
    city: strVal(clampStr(input.city, 64)),
    region: strVal(clampStr(input.region, 64)),
    firstSeenAtMs: intVal(first),
    lastSeenAtMs: intVal(last),
    sessionCount: intVal(clampInt(input.sessionCount, 1, 1000000)),
    totalDurationSec: intVal(clampInt(input.totalDurationSec, 0, 1000000000)),
    activeSessionId: strVal(clampStr(input.activeSessionId, 160)),
    lastTab: strVal(clampStr(input.lastTab, 32)),
    isOnline: { booleanValue: Boolean(input.isOnline) },
    wardrobeItemCount: intVal(clampInt(input.wardrobeItemCount, 0, 100000)),
    wishlistCount: intVal(clampInt(input.wishlistCount, 0, 100000)),
    outfitCount: intVal(clampInt(input.outfitCount, 0, 100000)),
    lifetimeFeatureCounts: intMapVal(input.lifetimeFeatureCounts, 50),
    lifetimeTabSeconds: intMapVal(input.lifetimeTabSeconds, 10),
    lifetimeFeatureSeconds: intMapVal(input.lifetimeFeatureSeconds, 30),
    errorCount: intVal(clampInt(input.errorCount, 0, 100000)),
    lastError: strVal(clampStr(input.lastError, 180))
  };
  const profUser = clampStr(input.profileUsername || '', 32);
  const profDisp = clampStr(input.profileDisplayName || '', 60);
  const profAv = typeof input.profileAvatar === 'string' && input.profileAvatar.startsWith('data:image/') && input.profileAvatar.length <= 35000
    ? input.profileAvatar
    : '';
  if (profUser) fields.profileUsername = strVal(profUser);
  if (profDisp) fields.profileDisplayName = strVal(profDisp);
  if (profAv) fields.profileAvatar = strVal(profAv);
  return { fields };
}

export function buildSessionFirestoreDoc(input) {
  const start = clampInt(input.startedAtMs, MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS);
  const last = Math.max(start, clampInt(input.lastActiveAtMs, MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS));
  const platform = ['android', 'web_pwa', 'web_browser'].includes(input.platform) ? input.platform : 'web_browser';
  const evValues = (Array.isArray(input.recentEvents) ? input.recentEvents : [])
    .slice(-50)
    .map((e) => strVal(clampStr(e, 180)));
  const fields = {
    uid: strVal(clampStr(input.uid, 128)),
    sessionId: strVal(clampStr(input.sessionId, 160)),
    anonTag: strVal(clampStr(input.anonTag || 'W-USER', 24) || 'W-USER'),
    platform: strVal(platform),
    appVersion: strVal(clampStr(input.appVersion || APP_VERSION, 32) || APP_VERSION),
    deviceModel: strVal(clampStr(input.deviceModel, 120)),
    osInfo: strVal(clampStr(input.osInfo, 100)),
    screenSize: strVal(clampStr(input.screenSize, 32)),
    language: strVal(clampStr(input.language || 'TR', 12) || 'TR'),
    theme: strVal(clampStr(input.theme || 'MODERN', 32) || 'MODERN'),
    timezone: strVal(clampStr(input.timezone, 64)),
    country: strVal(clampStr(input.country, 64)),
    city: strVal(clampStr(input.city, 64)),
    region: strVal(clampStr(input.region, 64)),
    entrySource: strVal(clampStr(input.entrySource || 'web_load', 32) || 'web_load'),
    networkType: strVal(clampStr(input.networkType, 32)),
    startedAtMs: intVal(start),
    lastActiveAtMs: intVal(last),
    durationSec: intVal(clampInt(input.durationSec, 0, 172800)),
    activeTab: strVal(clampStr(input.activeTab, 32)),
    isOnline: { booleanValue: Boolean(input.isOnline) },
    tabSeconds: intMapVal(input.tabSeconds, 10),
    featureCounts: intMapVal(input.featureCounts, 50),
    featureSeconds: intMapVal(input.featureSeconds, 30),
    recentEvents: { arrayValue: { values: evValues } },
    wardrobeStats: intMapVal(input.wardrobeStats, 10)
  };
  const profUser = clampStr(input.profileUsername || '', 32);
  const profDisp = clampStr(input.profileDisplayName || '', 60);
  if (profUser) fields.profileUsername = strVal(profUser);
  if (profDisp) fields.profileDisplayName = strVal(profDisp);
  return { fields };
}

async function signUpNewAnonUid() {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${encodeURIComponent(FIREBASE_API_KEY)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ returnSecureToken: true })
  });
  if (!res.ok) return null;
  const d = await res.json();
  if (!UID_REGEX.test(d.localId || '') || !d.idToken) return null;
  const st = readLocalStore();
  const now = clampInt(Date.now(), MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS - 10000);
  st.uid = d.localId;
  st.idToken = d.idToken;
  st.refreshToken = d.refreshToken || '';
  st.expiresAtMs = now + Math.max(60, Number(d.expiresIn || 3600) - 90) * 1000;
  if (!st.firstSeenAtMs) st.firstSeenAtMs = now;
  if (!st.lastSentSeenAtMs) st.lastSentSeenAtMs = now;
  writeLocalStore(st);
  return { uid: st.uid, idToken: st.idToken };
}

async function ensureAnonAuth(forceRefresh = false) {
  const st = readLocalStore();
  if (!forceRefresh && UID_REGEX.test(st.uid || '') && st.idToken && Date.now() < (Number(st.expiresAtMs) || 0)) {
    return { uid: st.uid, idToken: st.idToken };
  }
  if (UID_REGEX.test(st.uid || '') && st.refreshToken) {
    try {
      const res = await fetch(`https://securetoken.googleapis.com/v1/token?key=${encodeURIComponent(FIREBASE_API_KEY)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `grant_type=refresh_token&refresh_token=${encodeURIComponent(st.refreshToken)}`
      });
      if (res.ok) {
        const d = await res.json();
        if (d.id_token && UID_REGEX.test(d.user_id || st.uid)) {
          st.uid = d.user_id || st.uid;
          st.idToken = d.id_token;
          st.refreshToken = d.refresh_token || st.refreshToken;
          st.expiresAtMs = Date.now() + Math.max(60, Number(d.expires_in || 3600) - 90) * 1000;
          writeLocalStore(st);
          return { uid: st.uid, idToken: st.idToken };
        }
      }
    } catch {
      // fall through to new anonymous sign-up
    }
  }
  return signUpNewAnonUid();
}

async function fetchCoarseGeoIfStale() {
  const st = readLocalStore();
  if (Date.now() - (Number(st.geoFetchedMs) || 0) < 24 * 3600_000) return false;
  let timerId = null;
  try {
    const ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    if (ctrl) timerId = setTimeout(() => ctrl.abort(), 3500);
    const res = await fetch('https://ipwho.is/', ctrl ? { signal: ctrl.signal } : undefined);
    if (!res.ok) return false;
    const j = await res.json();
    if (j?.success) {
      st.geoCountry = clampStr(j.country || '', 64);
      st.geoCity = clampStr(j.city || '', 64);
      st.geoRegion = clampStr(j.region || '', 64);
      st.geoFetchedMs = Date.now();
      writeLocalStore(st);
      return true;
    }
    return false;
  } catch {
    return false;
  } finally {
    if (timerId) clearTimeout(timerId);
  }
}

export function getLocalTelemetrySnapshot(onlineState = isOnline, overrideUid = '', stripProfileFields = false) {
  const st = readLocalStore();
  const uid = (overrideUid && UID_REGEX.test(overrideUid))
    ? overrideUid
    : (UID_REGEX.test(st.uid || '') ? st.uid : 'local_preview_uid');
  const now = clampInt(Date.now(), MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS);
  const firstSeenAtMs = clampInt(st.firstSeenAtMs || sessionStartedAtMs || now, MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS);
  const startMs = clampInt(sessionStartedAtMs || now, MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS);
  const effectiveNow = Math.max(now, firstSeenAtMs, Number(st.lastSentSeenAtMs) || firstSeenAtMs, startMs);
  lastActiveAtMs = Math.max(lastActiveAtMs, effectiveNow);
  const sessSec = clampInt((lastActiveAtMs - startMs) / 1000, 0, 172800);
  const totalDur = clampInt(baseLifetimeSec + sessSec, 0, 1000000000);
  const curSessId = sessionId && sessionId.startsWith(`${uid}_`) ? sessionId : `${uid}_${startMs}`;
  const env = detectDeviceSummary();
  const platform = detectPlatform();
  const anonTag = clampStr(`W-${uid.slice(0, 6).toUpperCase()}`, 24);
  const pUser = stripProfileFields ? '' : (st.profileUsername || '');
  const pDisp = stripProfileFields ? '' : (st.profileDisplayName || '');
  const pAv = stripProfileFields ? '' : (st.profileAvatar || '');

  const devDoc = buildDeviceFirestoreDoc({
    uid,
    anonTag,
    platform,
    appVersion: APP_VERSION,
    deviceModel: env.deviceModel,
    osInfo: env.osInfo,
    screenSize: env.screenSize,
    language: currentLanguage,
    theme: currentTheme,
    timezone: env.timezone,
    country: st.geoCountry || '',
    city: st.geoCity || '',
    region: st.geoRegion || '',
    firstSeenAtMs,
    lastSeenAtMs: lastActiveAtMs,
    sessionCount: Math.max(1, Number(st.sessionCount) || 1),
    totalDurationSec: totalDur,
    activeSessionId: onlineState ? curSessId : '',
    lastTab: currentTab,
    isOnline: onlineState,
    wardrobeItemCount: wardrobeCounts.items,
    wishlistCount: wardrobeCounts.wishlist,
    outfitCount: wardrobeCounts.outfits,
    lifetimeFeatureCounts: st.lifeFeatCounts || {},
    lifetimeTabSeconds: st.lifeTabSecs || {},
    lifetimeFeatureSeconds: st.lifeFeatSecs || {},
    errorCount: Number(st.errorCount) || 0,
    lastError: st.lastError || '',
    profileUsername: pUser,
    profileDisplayName: pDisp,
    profileAvatar: pAv
  });

  const sessDoc = buildSessionFirestoreDoc({
    uid,
    sessionId: curSessId,
    anonTag,
    platform,
    appVersion: APP_VERSION,
    deviceModel: env.deviceModel,
    osInfo: env.osInfo,
    screenSize: env.screenSize,
    language: currentLanguage,
    theme: currentTheme,
    timezone: env.timezone,
    country: st.geoCountry || '',
    city: st.geoCity || '',
    region: st.geoRegion || '',
    entrySource,
    networkType: env.networkType,
    startedAtMs: startMs,
    lastActiveAtMs,
    durationSec: sessSec,
    activeTab: currentTab,
    isOnline: onlineState,
    tabSeconds: sessionTabSeconds,
    featureCounts: sessionFeatureCounts,
    featureSeconds: sessionFeatureSeconds,
    recentEvents,
    wardrobeStats: {
      items: wardrobeCounts.items,
      wishlist: wardrobeCounts.wishlist,
      outfits: wardrobeCounts.outfits
    },
    profileUsername: pUser,
    profileDisplayName: pDisp
  });

  return { uid, sessionId: curSessId, devDoc, sessDoc, lastActiveAtMs, totalDur };
}

async function patchTelemetryPair(auth, snap, useKeepalive) {
  const base = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/(default)/documents`;
  const headers = {
    Authorization: `Bearer ${auth.idToken}`,
    'Content-Type': 'application/json'
  };
  const [devRes, sessRes] = await Promise.all([
    fetch(`${base}/telemetry_devices/${encodeURIComponent(snap.uid)}`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify(snap.devDoc),
      keepalive: Boolean(useKeepalive)
    }),
    fetch(`${base}/telemetry_sessions/${encodeURIComponent(snap.sessionId)}`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify(snap.sessDoc),
      keepalive: Boolean(useKeepalive)
    })
  ]);
  return { devStatus: devRes.status, sessStatus: sessRes.status };
}

async function flushNow(onlineState, useKeepalive = false) {
  if (!isAnalyticsEnabled() || isLoopbackHost()) return;
  if (!useKeepalive && flushInFlight) {
    flushQueued = true;
    return;
  }
  flushInFlight = true;
  try {
    let auth = await ensureAnonAuth(false);
    if (!auth) return;
    let stripProfile = false;
    let snap = getLocalTelemetrySnapshot(onlineState, auth.uid, stripProfile);
    let { devStatus, sessStatus } = await patchTelemetryPair(auth, snap, useKeepalive);

    if ((devStatus === 401 || sessStatus === 401) && !useKeepalive) {
      auth = await ensureAnonAuth(true);
      if (!auth) return;
      snap = getLocalTelemetrySnapshot(onlineState, auth.uid, stripProfile);
      ({ devStatus, sessStatus } = await patchTelemetryPair(auth, snap, false));
    }

    if ((devStatus === 403 || sessStatus === 403) && !stripProfile && !useKeepalive) {
      stripProfile = true;
      snap = getLocalTelemetrySnapshot(onlineState, auth.uid, true);
      ({ devStatus, sessStatus } = await patchTelemetryPair(auth, snap, false));
    }

    if (devStatus === 403 && !recoveredUid403Once && !useKeepalive) {
      recoveredUid403Once = true;
      const freshAuth = await signUpNewAnonUid();
      if (freshAuth) {
        auth = freshAuth;
        snap = getLocalTelemetrySnapshot(onlineState, auth.uid, stripProfile);
        ({ devStatus, sessStatus } = await patchTelemetryPair(auth, snap, false));
      }
    }

    if (devStatus >= 200 && devStatus < 300) {
      const st = readLocalStore();
      st.lastSentSeenAtMs = snap.lastActiveAtMs;
      st.totalDurationSec = snap.totalDur;
      writeLocalStore(st);
    }
  } catch {
    // Never interrupt app UX
  } finally {
    flushInFlight = false;
    if (flushQueued && !useKeepalive) {
      flushQueued = false;
      flushNow(isOnline, false);
    }
  }
}

export function stopWebTelemetry() {
  isOnline = false;
  started = false;
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
  if (soonFlushTimer) {
    clearTimeout(soonFlushTimer);
    soonFlushTimer = null;
  }
  accumulateTabSec(Date.now());
  flushNow(false, true);
}

export function startWebTelemetry({ theme, language, wardrobeCounts: wc } = {}) {
  updateTelemetryContext({
    theme,
    language,
    itemsCount: wc?.items,
    wishlistCount: wc?.wishlist,
    outfitsCount: wc?.outfits
  });
  if (!isAnalyticsEnabled() || started) return;
  started = true;
  isOnline = true;
  const now = clampInt(Date.now(), MIN_VALID_EPOCH_MS, MAX_VALID_EPOCH_MS - 10000);
  sessionStartedAtMs = now;
  lastActiveAtMs = now;
  lastTabSwitchAtMs = now;
  entrySource = detectPlatform() === 'web_pwa' ? 'pwa_launch' : 'web_load';

  const st = readLocalStore();
  st.sessionCount = clampInt((Number(st.sessionCount) || 0) + 1, 1, 1000000);
  if (!st.firstSeenAtMs) st.firstSeenAtMs = now;
  baseLifetimeSec = clampInt(st.totalDurationSec || 0, 0, 1000000000);
  if (UID_REGEX.test(st.uid || '')) sessionId = `${st.uid}_${now}`;
  writeLocalStore(st);

  appendEvent('session', entrySource, currentTab);

  if (!isLoopbackHost()) {
    // Send initial visit IMMEDIATELY without blocking on third-party geo
    flushNow(true, false);
    fetchCoarseGeoIfStale().then((updated) => {
      if (updated && started) flushNow(isOnline, false);
    });
    setTimeout(() => {
      if (started && isOnline) flushNow(true, false);
    }, 3500);
  }

  heartbeatTimer = setInterval(() => {
    if (!started || document.visibilityState === 'hidden') return;
    accumulateTabSec(Date.now());
    flushNow(true, false);
  }, 30_000);

  document.addEventListener('visibilitychange', () => {
    if (!started) return;
    if (document.visibilityState === 'hidden') {
      accumulateTabSec(Date.now());
      flushNow(false, true);
    } else {
      lastTabSwitchAtMs = Date.now();
      flushNow(true, false);
    }
  });
  window.addEventListener('pagehide', () => {
    if (!started) return;
    accumulateTabSec(Date.now());
    flushNow(false, true);
  });
}
