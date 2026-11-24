import {
  APP_LANGUAGES,
  APP_VERSION,
  CATEGORIES,
  GARMENT_COLORS,
  GENDER_PREFS,
  RELEASE_NOTES_HISTORY,
  SEASONS,
  STRINGS,
  STYLES,
  STUDIO_ECOMMERCE_PROMPT,
  SUBCATEGORIES,
  THEME_MODES,
  TURKISH_CITIES,
  WEATHER_CONDITIONS,
  localizedText
} from './constants.js';
import {
  INSPIRATION_CATEGORIES,
  VERIFIED_MEN_LOOKS,
  VERIFIED_WOMEN_LOOKS
} from './lookbook-data.js';
import {
  clearAllLocalData,
  createId,
  deletePhotoDataUrl,
  getGeminiApiKey,
  getHfToken,
  getPhotoDataUrl,
  loadAllPhotoMap,
  loadAppState,
  migrateLegacySecrets,
  saveAppState,
  savePhotoDataUrl,
  setGeminiApiKey,
  setHfToken
} from './storage.js';
import {
  escHtml,
  escapeCssAttr,
  installClickjackingGuard,
  isValidGeminiApiKey,
  isValidHfToken,
  maskSecret,
  safeImgSrc,
  safeLookbookImgSrc,
  safeOpenExternalUrl,
  sanitizeErrorMessage,
  sanitizeHexColor
} from './security.js';
import {
  GeminiApiError,
  StudioBusyError,
  StudioQuotaError,
  analyzeGarmentDataUrl,
  analyzeFaceSkinTone,
  fileToOrientedDataUrl,
  generateAiEcommerceFlatLay,
  toEcommerceFlatLayDataUrl
} from './studio-processor.js';
import {
  analyzeMissingCapsulePieces,
  buildStoreSearchLinks,
  calculateColorHarmony,
  cleanGarmentTitleForName,
  detectBrowserLocationAndForecast,
  displayCategory,
  displayColor,
  displaySeason,
  displayStyle,
  displaySubCategory,
  fetchForecastForCoords,
  formatPrice,
  generateFallbackForecast,
  generateTravelPlan,
  getColorHex,
  googleShoppingUrl,
  inferWardrobeGender,
  isSafePublicHttpsUrl,
  normalizeProductUrl,
  parsePrice,
  searchDestinations,
  sortTopToBottom,
  suggestMissingHarmoniousColors,
  suggestOutfits,
  trendyolUrl
} from './recommendation-engine.js';
import {
  initPwaInstall,
  isStandalonePwa,
  syncInstallBannerCopy,
  triggerInstallFlow
} from './pwa-install.js';
import { openCropperQueueModal, openSingleDataUrlCropModal } from './cropper-modal.js';
import { openVirtualTryOn3DModal } from './tryon3d-modal.js';
import { svgIcon } from './icons.js';
import {
  endFeatureTimer,
  isAnalyticsEnabled,
  setAnalyticsEnabled,
  startFeatureTimer,
  startWebTelemetry,
  trackFeature,
  trackTabView,
  trackTelemetryError,
  updateTelemetryContext
} from './analytics.js';
import {
  compressAvatarPair,
  fetchPendingFollowRequests,
  loadLocalProfile,
  normalizeUsername,
  parseDeepLink,
  saveAndSyncProfile,
  syncTelemetryIdentityFromLocalProfile
} from './social.js';
import {
  avatarCircleHtml,
  openHourlyWeatherModal,
  openProfileHubModal,
  openPublicProfileModal,
  openShareGarmentModal,
  openShareOutfitModal,
  openSharedItemViewerModal,
  openSharedOutfitViewerModal
} from './profile-ui.js';
import {
  openActivityHeartModal,
  openSinglePostModal,
  renderSocialFeedTab
} from './feed-ui.js';
import { fetchMyNotifications } from './social-feed.js';

function shuffleArray(list) {
  const arr = [...list];
  for (let i = arr.length - 1; i > 0; i -= 1) {
    let j;
    if (window.crypto?.getRandomValues) {
      const buf = new Uint32Array(1);
      window.crypto.getRandomValues(buf);
      j = buf[0] % (i + 1);
    } else {
      j = Math.floor(Math.random() * (i + 1));
    }
    const tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
  }
  return arr;
}

let appState = loadAppState();
let photoMap = new Map();
let personalColorProfile = null;
let currentTab = 0;
let isDraggingWardrobeCard = false;
let justDraggedWardrobeCard = false;
let shuffledMenLooks = shuffleArray(VERIFIED_MEN_LOOKS);
let shuffledWomenLooks = shuffleArray(VERIFIED_WOMEN_LOOKS);
let unreadActivityCount = 0;
const seenNotifIds = new Set();
let activityPollTimer = null;
let firstActivityPollDone = false;

// Ephemeral view states
let wFilterCat = null;
let wFilterSub = null;
let wFilterColor = null;
let wFilterSeason = null;
let wFilterStyle = null;
let wFavOnly = false;
let wWishOnly = false;
let wShowSearch = false;
let wSearchQuery = '';

let forecastDays = generateFallbackForecast(appState.language);
let selectedDayIdx = 0;
let manualWeatherOverride = null;
let outfitRefreshNonce = 0;
let currentOutfitSuggestions = [];

function resolveInitialLookGender() {
  if (appState?.lookGender === 'MEN' || appState?.lookGender === 'WOMEN') return appState.lookGender;
  return activeGenderPref() === 'FEMALE' ? 'WOMEN' : 'MEN';
}
let lookGender = resolveInitialLookGender();
function setLookGender(next) {
  if (next !== 'MEN' && next !== 'WOMEN') return;
  lookGender = next;
  if (appState) {
    appState.lookGender = next;
    commitState();
  }
}
let lookCategory = 'ALL';
let expandMissingPieces = false;
let expandMissingColors = false;
let expandUnworn = false;

const mainContent = document.getElementById('mainContent');
const modalRoot = document.getElementById('modalRoot');
const toastRoot = document.getElementById('toastRoot');

function getLang() { return appState.language || 'TR'; }
function S() { return STRINGS[getLang()] || STRINGS.TR; }
function isEnUi() { return getLang() !== 'TR'; }

const esc = escHtml;

function showToast(msg) {
  const el = document.createElement('div');
  el.className = 'toast-item';
  el.textContent = msg;
  toastRoot.appendChild(el);
  setTimeout(() => el.remove(), 2600);
}

function safeOpenExternal(url) {
  if (!safeOpenExternalUrl(url)) {
    showToast(localizedText(getLang(), 'Güvenli https:// linki gerekli', 'Requires safe https:// link'));
  }
}

const modalStack = [];
const tabBackStack = [];
const tabReturnScrollY = [0, 0, 0, 0, 0];
let programmaticScrollUntil = 0;
let modalSeq = 0;
let ignorePopCount = 0;
let lastExitPromptMs = 0;

function pushSentinelHistory(tab = currentTab) {
  try {
    history.pushState({ dolabim: true, kind: 'sentinel', tab }, '');
  } catch {
    // ignore history errors in restricted contexts
  }
}

function setModalScrollLock(locked) {
  document.documentElement.classList.toggle('modal-open', locked);
  document.body.classList.toggle('modal-open', locked);
}

function registerModalBackHandler(closeDomFn, replaceHistory = false) {
  const id = ++modalSeq;
  const entry = { id, closeDom: closeDomFn };
  modalStack.push(entry);
  setModalScrollLock(true);
  try {
    const stateObj = { dolabim: true, kind: 'modal', id };
    if (replaceHistory) history.replaceState(stateObj, '');
    else history.pushState(stateObj, '');
  } catch {
    // ignore
  }
  return (opts = {}) => {
    const idx = modalStack.findIndex((m) => m.id === id);
    if (idx >= 0) modalStack.splice(idx, 1);
    if (modalStack.length === 0) setModalScrollLock(false);
    closeDomFn();
    if (!opts?.skipHistoryBack && idx >= 0) {
      ignorePopCount++;
      try {
        history.back();
      } catch {
        ignorePopCount = Math.max(0, ignorePopCount - 1);
      }
    }
  };
}

function openModal({
  title,
  subtitle = '',
  headerLeftHtml = '',
  headerRightHtml = '',
  hideCloseX = false,
  bodyHtml,
  footerHtml = '',
  fullscreen = false,
  replaceHistory = false,
  onClose = null,
  onMount
}) {
  const backdrop = document.createElement('div');
  backdrop.className = fullscreen ? 'modal-backdrop fullscreen-backdrop' : 'modal-backdrop';
  backdrop.innerHTML = `
    <div class="modal-panel ${fullscreen ? 'fullscreen' : ''}">
      <div class="modal-header">
        <div style="display:flex; align-items:center; gap:8px; min-width:0; flex:1;">
          ${headerLeftHtml}
          <div style="min-width:0; flex:1;">
            <h3 class="modal-title" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(title)}</h3>
            ${subtitle ? `<div class="modal-subtitle" style="font-size:12px; color:var(--accent); margin-top:2px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(subtitle)}</div>` : ''}
          </div>
        </div>
        <div style="display:flex; align-items:center; gap:6px; flex-shrink:0;">
          ${headerRightHtml}
          ${hideCloseX ? '' : `<button type="button" class="btn-icon-ghost modal-close-x" aria-label="Kapat">${svgIcon('close', 20)}</button>`}
        </div>
      </div>
      <div class="modal-body">${bodyHtml}</div>
      ${footerHtml ? `<div class="modal-footer">${footerHtml}</div>` : ''}
    </div>
  `;
  const closeDom = () => {
    backdrop.remove();
    onClose?.();
  };
  const close = registerModalBackHandler(closeDom, replaceHistory);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) close(); });
  backdrop.querySelector('.modal-close-x')?.addEventListener('click', () => close());
  modalRoot.appendChild(backdrop);
  onMount?.(close, backdrop);
  return close;
}

function harmonyBadgeHtml(score, lang) {
  const tier = score >= 83 ? '' : score >= 64 ? 'mid' : 'low';
  const lbl = lang === 'EN' ? `${score}% Match` : `%${score} Uyum`;
  return `<span class="harmony-pill ${tier}">${svgIcon('autoAwesome', 13)} ${esc(lbl)}</span>`;
}

function activePhotoIds(item) {
  if (item.useOriginalPhotos && item.originalPhotoFileNames?.length === item.photoFileNames?.length && item.originalPhotoFileNames?.length > 0) {
    return item.originalPhotoFileNames;
  }
  if (item.useCatalogPhotos && Array.isArray(item.catalogPhotoFileNames) && item.catalogPhotoFileNames.length > 0) {
    return (item.photoFileNames || []).map((cut, i) => item.catalogPhotoFileNames[i] || cut);
  }
  return item.photoFileNames || [];
}

function coverUrlOf(item) {
  const pid = activePhotoIds(item)[0];
  return pid ? safeImgSrc(photoMap.get(pid)) : '';
}

function activeGenderPref() {
  if (appState.genderUserSet) return appState.genderPref || 'UNISEX';
  return inferWardrobeGender((appState.items || []).filter((i) => !i.isWishlist));
}

function commitState() {
  saveAppState(appState);
  const all = appState.items || [];
  updateTelemetryContext({
    theme: appState.themeMode || 'MODERN',
    language: getLang(),
    itemsCount: all.filter((i) => !i.isWishlist).length,
    wishlistCount: all.filter((i) => i.isWishlist).length,
    outfitsCount: (appState.outfits || []).length
  });
}

async function syncPhotosAndPersonalProfile() {
  const allIds = ['user_face_photo', 'user_body_photo'];
  (appState.items || []).forEach((it) => {
    (it.photoFileNames || []).forEach((id) => allIds.push(id));
    (it.originalPhotoFileNames || []).forEach((id) => allIds.push(id));
    (it.catalogPhotoFileNames || []).forEach((id) => { if (id) allIds.push(id); });
  });
  photoMap = await loadAllPhotoMap(allIds);
  const faceUrl = photoMap.get('user_face_photo');
  personalColorProfile = faceUrl ? await analyzeFaceSkinTone(faceUrl, getLang()) : null;
}

function applyThemeAndLanguage() {
  document.body.dataset.theme = appState.themeMode || 'MODERN';
  const langMeta = APP_LANGUAGES.find((l) => l.code === getLang());
  document.documentElement.lang = langMeta?.htmlLang || 'tr';
  const st = S();
  const labels = [st.appTitle, st.tabOutfits, st.tabSocial || 'Sosyal', st.tabInspiration, st.tabSummary];
  labels.forEach((lbl, i) => {
    const el = document.getElementById(`dockTab${i}`);
    if (el) {
      el.title = lbl;
      el.setAttribute('aria-label', lbl);
    }
  });
  const fab = document.getElementById('wardrobeFab');
  if (fab) {
    fab.title = st.pickFromGallery;
    fab.setAttribute('aria-label', st.pickFromGallery);
  }
  syncInstallBannerCopy(getLang(), st);
}

function switchTab(tabIdx, { fromHistory = false, swipeDir = null, fromDock = false } = {}) {
  const prevTab = currentTab;
  const next = Math.max(0, Math.min(4, Number(tabIdx) || 0));
  if (fromDock && next === prevTab) {
    const curY = Math.round(window.scrollY || document.documentElement.scrollTop || 0);
    if (curY > 12) {
      tabReturnScrollY[currentTab] = curY;
      programmaticScrollUntil = Date.now() + 950;
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } else {
      const savedY = tabReturnScrollY[currentTab] || 0;
      if (savedY > 12) {
        tabReturnScrollY[currentTab] = 0;
        programmaticScrollUntil = Date.now() + 950;
        window.scrollTo({ top: savedY, behavior: 'smooth' });
      }
    }
    return;
  }
  const effectiveSwipeDir = next === prevTab
    ? null
    : (swipeDir || (next > prevTab ? 'left' : 'right'));
  if (effectiveSwipeDir && mainContent) {
    const rect = mainContent.getBoundingClientRect();
    const exitLayer = document.createElement('div');
    exitLayer.className = `tab-exit-layer ${effectiveSwipeDir === 'left' ? 'tab-exit-left' : 'tab-exit-right'}`;
    exitLayer.style.top = '0px';
    exitLayer.style.left = `${rect.left}px`;
    exitLayer.style.width = `${rect.width}px`;
    exitLayer.style.height = `${window.innerHeight}px`;
    const inner = document.createElement('div');
    inner.style.transform = `translateY(${rect.top}px)`;
    inner.innerHTML = mainContent.innerHTML;
    exitLayer.appendChild(inner);
    document.body.appendChild(exitLayer);
    setTimeout(() => exitLayer.remove(), 350);
  }
  if (!fromHistory && next !== prevTab) {
    tabBackStack.push(prevTab);
    if (tabBackStack.length > 20) tabBackStack.shift();
    try {
      history.pushState({ dolabim: true, kind: 'tab', tab: next }, '');
    } catch {
      // ignore
    }
  }
  currentTab = next;
  if (next !== prevTab) {
    trackTabView(['wardrobe', 'outfits', 'social', 'inspiration', 'summary'][currentTab] || 'wardrobe');
  }
  if (currentTab === 3 && next !== prevTab) {
    shuffledMenLooks = shuffleArray(VERIFIED_MEN_LOOKS);
    shuffledWomenLooks = shuffleArray(VERIFIED_WOMEN_LOOKS);
  }
  tabReturnScrollY[next] = 0;
  const dockEl = document.getElementById('bottomDock');
  if (dockEl) dockEl.style.setProperty('--dock-idx', String(currentTab));
  document.querySelectorAll('.dock-btn').forEach((btn) => {
    btn.classList.toggle('active', Number(btn.dataset.tab) === currentTab);
  });
  renderCurrentTab();
  programmaticScrollUntil = Date.now() + 250;
  window.scrollTo({ top: 0, behavior: 'auto' });
  if (effectiveSwipeDir && mainContent) {
    const cls = effectiveSwipeDir === 'left' ? 'tab-enter-left' : 'tab-enter-right';
    mainContent.classList.remove('tab-enter-left', 'tab-enter-right');
    void mainContent.offsetWidth;
    mainContent.classList.add(cls);
  }
}

function installBackNavigationHandler() {
  try {
    history.replaceState({ dolabim: true, kind: 'root', tab: 0 }, '');
    pushSentinelHistory(0);
  } catch {
    // ignore
  }
  window.addEventListener('popstate', () => {
    if (ignorePopCount > 0) {
      ignorePopCount--;
      return;
    }
    if (modalStack.length > 0) {
      const top = modalStack.pop();
      if (modalStack.length === 0) setModalScrollLock(false);
      top?.closeDom?.();
      return;
    }
    if (currentTab === 0 && wShowSearch) {
      wShowSearch = false;
      wSearchQuery = '';
      renderWardrobeTab();
      pushSentinelHistory(0);
      return;
    }
    if (tabBackStack.length > 0) {
      const prevTab = tabBackStack.pop();
      switchTab(prevTab, { fromHistory: true });
      return;
    }
    if (currentTab !== 0) {
      switchTab(0, { fromHistory: true });
      return;
    }
    const now = Date.now();
    if (now - lastExitPromptMs > 2200) {
      lastExitPromptMs = now;
      showToast(localizedText(getLang(), 'Çıkmak için tekrar geri basın', 'Press back again to exit'));
      pushSentinelHistory(0);
    }
  });
}

const bounceReleaseTimers = new WeakMap();

function dampOverscroll(rawDelta, maxPx = 56) {
  if (!rawDelta) return 0;
  return Math.sign(rawDelta) * maxPx * (1 - Math.exp(-Math.abs(rawDelta) / 180));
}

function applyBounceTransform(el, tx, ty, sx = 1, sy = 1, origin = '50% 50%') {
  if (!el) return;
  const prevTimer = bounceReleaseTimers.get(el);
  if (prevTimer) {
    clearTimeout(prevTimer);
    bounceReleaseTimers.delete(el);
  }
  el.style.transition = 'none';
  el.style.willChange = 'transform';
  el.style.transformOrigin = origin;
  el.style.transform = `translate3d(${tx.toFixed(1)}px, ${ty.toFixed(1)}px, 0) scale(${sx.toFixed(4)}, ${sy.toFixed(4)})`;
}

function clearBounceImmediate(el) {
  if (!el) return;
  const prevTimer = bounceReleaseTimers.get(el);
  if (prevTimer) {
    clearTimeout(prevTimer);
    bounceReleaseTimers.delete(el);
  }
  el.style.transition = '';
  el.style.transform = '';
  el.style.transformOrigin = '';
  el.style.willChange = '';
}

function releaseBounce(el) {
  if (!el) return;
  const prevTimer = bounceReleaseTimers.get(el);
  if (prevTimer) clearTimeout(prevTimer);
  el.style.transition = 'transform 420ms cubic-bezier(0.22, 1.45, 0.36, 1)';
  el.style.transform = 'translate3d(0px, 0px, 0) scale(1, 1)';
  const timer = setTimeout(() => {
    bounceReleaseTimers.delete(el);
    el.style.transition = '';
    el.style.transform = '';
    el.style.transformOrigin = '';
    el.style.willChange = '';
  }, 440);
  bounceReleaseTimers.set(el, timer);
}

function installTabSwipeGestures() {
  if (!mainContent) return;
  let startX = 0, startY = 0, lastX = 0, lastY = 0, startTime = 0;
  let tracking = false;
  let lock = null; // null | 'x' | 'y'
  let rowScrollInfo = null; // { el, isInteractiveStage }
  let activeModalBody = null;
  let rawPullY = 0;
  let rawPullX = 0;
  let didBounceY = false;
  let didBounceX = false;
  let wheelPullY = 0;
  let wheelPullX = 0;
  let wheelReleaseTimer = null;

  const inspectHorizontalTarget = (target, rootBoundary) => {
    let el = target instanceof Element ? target : null;
    while (el && el !== rootBoundary && el !== document.documentElement) {
      if (el.matches('.swipe-stage, .canvas-stage, input, textarea, select, [data-no-tab-swipe]')) {
        return { el, isInteractiveStage: true };
      }
      if (el.scrollWidth > el.clientWidth + 4) {
        const ox = window.getComputedStyle(el).overflowX;
        if (ox === 'auto' || ox === 'scroll') return { el, isInteractiveStage: false };
      }
      el = el.parentElement;
    }
    return null;
  };

  window.addEventListener('scroll', () => {
    if (Date.now() > programmaticScrollUntil && (window.scrollY || 0) > 12) {
      tabReturnScrollY[currentTab] = 0;
    }
  }, { passive: true });

  document.addEventListener('touchstart', (e) => {
    if (isDraggingWardrobeCard) {
      tracking = false;
      return;
    }
    programmaticScrollUntil = 0;
    const modalOpen = modalStack.length > 0 || modalRoot.childElementCount > 0;
    if (e.touches.length !== 1) {
      tracking = false;
      return;
    }
    const targetEl = e.target instanceof Element ? e.target : null;
    if (modalOpen) {
      activeModalBody = targetEl ? targetEl.closest('.modal-body') : null;
      if (!activeModalBody || !targetEl || targetEl.closest('.swipe-stage, .canvas-stage, input, textarea, select')) {
        tracking = false;
        return;
      }
      rowScrollInfo = inspectHorizontalTarget(targetEl, activeModalBody);
    } else {
      activeModalBody = null;
      if (!targetEl || (!mainContent.contains(targetEl) && targetEl !== mainContent)) {
        tracking = false;
        return;
      }
      rowScrollInfo = inspectHorizontalTarget(targetEl, mainContent);
    }

    const t = e.touches[0];
    startX = t.clientX;
    startY = t.clientY;
    lastX = t.clientX;
    lastY = t.clientY;
    startTime = Date.now();
    tracking = true;
    lock = null;
    rawPullY = 0;
    rawPullX = 0;
    didBounceY = false;
    didBounceX = false;
  }, { passive: true });

  document.addEventListener('touchmove', (e) => {
    if (isDraggingWardrobeCard) {
      tracking = false;
      return;
    }
    if (!tracking || e.touches.length !== 1) return;
    const t = e.touches[0];
    const dx = t.clientX - startX;
    const dy = t.clientY - startY;
    const stepX = t.clientX - lastX;
    const stepY = t.clientY - lastY;
    lastX = t.clientX;
    lastY = t.clientY;

    if (!lock && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) {
      lock = Math.abs(dy) >= Math.abs(dx) ? 'y' : 'x';
    }
    if (!lock) return;

    if (lock === 'y') {
      if (activeModalBody) {
        const atTop = activeModalBody.scrollTop <= 1;
        const atBottom = activeModalBody.scrollTop + activeModalBody.clientHeight >= activeModalBody.scrollHeight - 2;
        if ((atTop && stepY > 0) || (atBottom && stepY < 0) || rawPullY !== 0) {
          const nextPull = rawPullY + stepY;
          if ((rawPullY > 0 && nextPull <= 0) || (rawPullY < 0 && nextPull >= 0)) {
            rawPullY = 0;
            clearBounceImmediate(activeModalBody);
            didBounceY = false;
          } else {
            rawPullY = Math.max(-240, Math.min(240, nextPull));
            const yOff = dampOverscroll(rawPullY, 46);
            const stretchY = 1 + (Math.abs(yOff) / 46) * 0.020;
            applyBounceTransform(
              activeModalBody,
              0,
              yOff * 0.65,
              1,
              stretchY,
              yOff >= 0 ? '50% 0%' : '50% 100%'
            );
            didBounceY = true;
          }
        }
        return;
      }

      const scrollTop = window.scrollY || document.documentElement.scrollTop || 0;
      const viewH = window.innerHeight || document.documentElement.clientHeight || 844;
      const docH = document.documentElement.scrollHeight || viewH;
      const atTop = scrollTop <= 1;
      const atBottom = scrollTop + viewH >= docH - 2;
      if ((atTop && stepY > 0) || (atBottom && stepY < 0) || rawPullY !== 0) {
        const nextPull = rawPullY + stepY;
        if ((rawPullY > 0 && nextPull <= 0) || (rawPullY < 0 && nextPull >= 0)) {
          rawPullY = 0;
          clearBounceImmediate(mainContent);
          didBounceY = false;
        } else {
          rawPullY = Math.max(-260, Math.min(260, nextPull));
          const yOff = dampOverscroll(rawPullY, 56);
          const stretchY = 1 + (Math.abs(yOff) / 56) * 0.022;
          applyBounceTransform(
            mainContent,
            0,
            yOff * 0.65,
            1,
            stretchY,
            yOff >= 0 ? '50% 0%' : '50% 100%'
          );
          didBounceY = true;
        }
      }
      return;
    }

    // lock === 'x'
    if (rowScrollInfo) {
      if (rowScrollInfo.isInteractiveStage) return;
      const hEl = rowScrollInfo.el;
      const atLeft = hEl.scrollLeft <= 1;
      const atRight = hEl.scrollLeft + hEl.clientWidth >= hEl.scrollWidth - 2;
      if ((atLeft && stepX > 0) || (atRight && stepX < 0) || rawPullX !== 0) {
        const nextPullX = rawPullX + stepX;
        if ((rawPullX > 0 && nextPullX <= 0) || (rawPullX < 0 && nextPullX >= 0)) {
          rawPullX = 0;
          clearBounceImmediate(hEl);
          didBounceX = false;
        } else {
          rawPullX = Math.max(-200, Math.min(200, nextPullX));
          const xOff = dampOverscroll(rawPullX, 42);
          const stretchX = 1 + (Math.abs(xOff) / 42) * 0.022;
          applyBounceTransform(
            hEl,
            xOff * 0.65,
            0,
            stretchX,
            1,
            xOff >= 0 ? '0% 50%' : '100% 50%'
          );
          didBounceX = true;
        }
      }
      return;
    }

    if (activeModalBody) return;

    // Horizontal bounce or preview pull on tabs
    const isOuterEdge = (currentTab === 0 && dx > 0) || (currentTab === 4 && dx < 0);
    const maxPx = isOuterEdge ? 52 : 42;
    const xOff = dampOverscroll(isOuterEdge ? dx : dx * 0.55, maxPx);
    const stretchX = isOuterEdge ? (1 + (Math.abs(xOff) / maxPx) * 0.020) : 1;
    applyBounceTransform(
      mainContent,
      xOff * 0.65,
      0,
      stretchX,
      1,
      xOff >= 0 ? '0% 50%' : '100% 50%'
    );
    didBounceX = true;
  }, { passive: true });

  const finishTouch = (e, cancelled = false) => {
    if (!tracking) return;
    const t = e.changedTouches?.[0];
    const dx = t ? t.clientX - startX : 0;
    const dy = t ? t.clientY - startY : 0;
    const dt = Date.now() - startTime;
    const wasLockedX = lock === 'x';
    const modalBodySnapshot = activeModalBody;
    const rowSnapshot = rowScrollInfo;
    const bouncedY = didBounceY;
    const bouncedX = didBounceX;

    tracking = false;
    lock = null;
    rowScrollInfo = null;
    activeModalBody = null;
    rawPullY = 0;
    rawPullX = 0;
    didBounceY = false;
    didBounceX = false;

    if (bouncedY) {
      releaseBounce(modalBodySnapshot || mainContent);
    }

    if (rowSnapshot && !rowSnapshot.isInteractiveStage) {
      if (bouncedX) releaseBounce(rowSnapshot.el);
      return;
    }

    if (!cancelled && !modalBodySnapshot && !rowSnapshot && wasLockedX && dt < 700 && Math.abs(dx) >= 56 && Math.abs(dx) > Math.abs(dy) * 1.5) {
      if (dx < 0 && currentTab < 4) {
        clearBounceImmediate(mainContent);
        switchTab(currentTab + 1, { swipeDir: 'left' });
        return;
      } else if (dx > 0 && currentTab > 0) {
        clearBounceImmediate(mainContent);
        switchTab(currentTab - 1, { swipeDir: 'right' });
        return;
      }
    }

    if (bouncedX) {
      releaseBounce(mainContent);
    }
  };

  document.addEventListener('touchend', (e) => finishTouch(e, false), { passive: true });
  document.addEventListener('touchcancel', (e) => finishTouch(e, true), { passive: true });

  // Desktop / trackpad wheel boundary bounce
  window.addEventListener('wheel', (e) => {
    programmaticScrollUntil = 0;
    if (modalStack.length > 0 || modalRoot.childElementCount > 0) return;

    const absY = Math.abs(e.deltaY || 0);
    const absX = Math.abs(e.deltaX || 0);
    let kicked = false;

    if (absY >= absX && absY > 2) {
      const scrollTop = window.scrollY || document.documentElement.scrollTop || 0;
      const viewH = window.innerHeight || document.documentElement.clientHeight || 844;
      const docH = document.documentElement.scrollHeight || viewH;
      const atTop = scrollTop <= 1;
      const atBottom = scrollTop + viewH >= docH - 2;
      if ((atTop && e.deltaY < 0) || (atBottom && e.deltaY > 0)) {
        wheelPullY = Math.max(-180, Math.min(180, wheelPullY - e.deltaY * 0.45));
        const yOff = dampOverscroll(wheelPullY, 42);
        const stretchY = 1 + (Math.abs(yOff) / 42) * 0.018;
        applyBounceTransform(mainContent, 0, yOff * 0.65, 1, stretchY, yOff >= 0 ? '50% 0%' : '50% 100%');
        kicked = true;
      }
    } else if (absX > absY && absX > 2) {
      if ((currentTab === 0 && e.deltaX < 0) || (currentTab === 4 && e.deltaX > 0)) {
        wheelPullX = Math.max(-180, Math.min(180, wheelPullX - e.deltaX * 0.45));
        const xOff = dampOverscroll(wheelPullX, 40);
        const stretchX = 1 + (Math.abs(xOff) / 40) * 0.018;
        applyBounceTransform(mainContent, xOff * 0.65, 0, stretchX, 1, xOff >= 0 ? '0% 50%' : '100% 50%');
        kicked = true;
      }
    }

    if (kicked) {
      clearTimeout(wheelReleaseTimer);
      wheelReleaseTimer = setTimeout(() => {
        wheelPullY = 0;
        wheelPullX = 0;
        releaseBounce(mainContent);
      }, 120);
    }
  }, { passive: true });
}

function renderCurrentTab() {
  document.getElementById('wardrobeFab')?.classList.toggle('hidden', currentTab !== 0);
  if (currentTab === 0) renderWardrobeTab();
  else if (currentTab === 1) renderOutfitsTab();
  else if (currentTab === 2) renderSocialFeed();
  else if (currentTab === 3) renderInspirationTab();
  else renderSummaryTab();
}

// ==========================================
// TAB 0: DOLABIM (WARDROBE)
// ==========================================
function renderWardrobeTab() {
  const st = S();
  const lang = getLang();
  const items = appState.items || [];
  const activeFilterCnt = [wFilterColor, wFilterSeason, wFilterStyle, wFilterSub].filter(Boolean).length;

  const q = wSearchQuery.trim().toLowerCase();
  const filtered = items.filter((it) => {
    if ((wWishOnly && !it.isWishlist) || (!wWishOnly && it.isWishlist)) return false;
    if (wFilterCat && it.category !== wFilterCat) return false;
    if (wFilterSub && it.subCategory !== wFilterSub) return false;
    if (wFavOnly && !it.isFavorite) return false;
    if (wFilterColor && !(it.colors || []).includes(wFilterColor)) return false;
    if (wFilterSeason && !(it.seasons || []).includes(wFilterSeason)) return false;
    if (wFilterStyle && it.styleTag !== wFilterStyle) return false;
    if (q) {
      const hay = [
        it.name || '',
        displaySubCategory(it.subCategory, lang),
        displayCategory(it.category, lang),
        displayStyle(it.styleTag, lang),
        ...(it.colors || []).map((c) => displayColor(c, lang))
      ].join(' ').toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });

  const wishCount = items.filter((i) => i.isWishlist).length;
  const subPool = (() => {
    const bucket = items.filter((i) => (wWishOnly ? i.isWishlist : !i.isWishlist) && (!wFilterCat || i.category === wFilterCat));
    const owned = [...new Set(bucket.map((i) => i.subCategory))];
    if (owned.length > 0) return owned;
    return wFilterCat
      ? SUBCATEGORIES.filter((s) => s.cat === wFilterCat).map((s) => s.id)
      : ['TSHIRT', 'SHIRT', 'HOODIE', 'SWEATER', 'CARDIGAN', 'JEANS', 'PANTS', 'SWEATPANTS', 'JACKET', 'COAT', 'SNEAKER'];
  })();

  mainContent.innerHTML = `
    <div class="screen-pad">
      <div class="page-header">
        <h2 class="page-title">${esc(st.appTitle)}</h2>
        <div class="header-actions">
          <button type="button" class="icon-btn" id="wSearchToggle" title="${esc(st.searchPlaceholder)}">${svgIcon('search', 22)}</button>
          <button type="button" class="icon-btn" id="wFilterBtn" title="Filter">
            ${svgIcon('tune', 22)}${activeFilterCnt > 0 ? `<span class="badge-count">${activeFilterCnt}</span>` : ''}
          </button>
          <button type="button" class="icon-btn" id="wSettingsBtn" title="${esc(st.settingsTitle)}">${svgIcon('settings', 22)}</button>
          <button type="button" class="icon-btn primary-circle" id="wCameraAddBtn" title="${esc(st.takePhotoAdd)}">${svgIcon('camera', 19)}</button>
        </div>
      </div>
      ${wShowSearch ? `<input id="wSearchInput" type="search" class="input-field" placeholder="${esc(st.searchPlaceholder)}" value="${esc(wSearchQuery)}" style="margin-bottom:10px;" />` : ''}
    </div>

    <div class="chip-row" id="wMainFilters">
      <button type="button" class="chip-pill ${!wFilterCat && !wFavOnly && !wWishOnly ? 'selected' : ''}" data-m="all">${esc(st.all)}</button>
      <button type="button" class="chip-pill ${wFavOnly ? 'selected' : ''}" data-m="fav">${esc(st.favorites)}</button>
      <button type="button" class="chip-pill ${wWishOnly ? 'selected' : ''}" data-m="wish">${wWishOnly ? `<span class="color-dot" style="background:var(--accent); border-color:transparent;"></span>` : ''}${esc(st.wishlist)}${wishCount > 0 ? ` (${wishCount})` : ''}</button>
      ${CATEGORIES.map((c) => `<button type="button" class="chip-pill ${wFilterCat === c.id ? 'selected' : ''}" data-cat="${esc(c.id)}">${esc(displayCategory(c.id, lang))}</button>`).join('')}
    </div>

    <div class="chip-row sub-row" id="wSubFilters">
      ${subPool.map((sid) => `<button type="button" class="chip-pill ${wFilterSub === sid ? 'selected' : ''}" data-sub="${esc(sid)}">${esc(displaySubCategory(sid, lang))}</button>`).join('')}
    </div>

    ${filtered.length === 0 ? (items.length === 0 ? `
      <div class="empty-card">
        <div class="empty-badge-circle">${svgIcon('tshirtAdd', 34)}</div>
        <h3 style="margin:0; font-size:20px; font-weight:600;">${esc(st.emptyWardrobeTitle)}</h3>
        <p class="muted-note" style="margin:0;">${esc(st.emptyWardrobeBody)}</p>
        <div class="empty-actions">
          <button type="button" class="btn-primary" id="wEmptyCamBtn">${svgIcon('camera', 18)} ${esc(st.takePhotoAdd)}</button>
          <button type="button" class="btn-secondary" id="wEmptyGalBtn">${svgIcon('photoLibrary', 18)} ${esc(st.pickFromGallery)}</button>
        </div>
      </div>
    ` : `
      <div class="empty-card">
        <span style="color:var(--accent);">${svgIcon(wWishOnly ? 'bookmarkBorder' : 'tune', 34)}</span>
        <h3 style="margin:0; font-size:17px;">${esc(wWishOnly ? localizedText(lang, 'İstek listen henüz boş', 'Your wishlist is empty') : st.noFilterMatch)}</h3>
        <button type="button" class="btn-secondary" id="wClearFiltersBtn">${esc(st.clearFilters)}</button>
      </div>
    `) : `
      <div class="grid-2" id="wGrid">
        ${filtered.map((it) => {
          const url = coverUrlOf(it);
          const photosCount = activePhotoIds(it).length;
          const dots = (it.colors || []).slice(0, 3).map((cid) => `<span class="color-dot" style="background:${sanitizeHexColor(getColorHex(cid))};"></span>`).join('');
          const topLeft = (it.isWishlist && (it.priceText || it.priceDropped))
            ? `<div class="price-badge-stack">
                ${it.priceText ? `<span class="price-badge">${esc(it.priceText)}</span>` : ''}
                ${it.priceDropped ? `<span class="price-badge drop">${lang === 'EN' ? '🔥 Price Drop' : '🔥 İndirim'}</span>` : ''}
              </div>`
            : (photosCount > 1 ? `<span class="multi-photo-badge">${svgIcon('collections', 11)} ${photosCount}</span>` : '');
          return `
            <article class="garment-card" data-item-id="${esc(it.id)}">
              <div class="garment-photo-box ${it.useOriginalPhotos ? 'original-bg' : ''}">
                ${url ? `<img src="${url}" alt="${esc(it.name)}" loading="lazy" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 36)}</span>`}
                ${topLeft}
                <button type="button" class="share-float" data-share-id="${esc(it.id)}" aria-label="${localizedText(lang, 'Paylaş', 'Share')}">${svgIcon('share', 13)}</button>
                <button type="button" class="fav-float ${it.isFavorite ? 'active' : ''}" data-fav-id="${esc(it.id)}" aria-label="Favori">${svgIcon(it.isFavorite ? 'favoriteFilled' : 'favoriteBorder', 14)}</button>
              </div>
              <div class="garment-card-body">
                <div class="garment-title">${esc(it.name || displaySubCategory(it.subCategory, lang))}</div>
                <div class="garment-meta">
                  <span>${esc(displaySubCategory(it.subCategory, lang))}</span>
                  <span class="dot-row">${dots}</span>
                </div>
              </div>
            </article>
          `;
        }).join('')}
      </div>
    `}
  `;

  document.getElementById('wSearchToggle')?.addEventListener('click', () => {
    wShowSearch = !wShowSearch;
    if (!wShowSearch) wSearchQuery = '';
    renderWardrobeTab();
    if (wShowSearch) document.getElementById('wSearchInput')?.focus();
  });
  document.getElementById('wSearchInput')?.addEventListener('input', (e) => {
    wSearchQuery = e.target.value;
    const caret = e.target.selectionStart;
    renderWardrobeTab();
    const el = document.getElementById('wSearchInput');
    if (el) { el.focus(); el.setSelectionRange(caret, caret); }
  });
  document.getElementById('wSettingsBtn')?.addEventListener('click', openSettingsModal);
  document.getElementById('wFilterBtn')?.addEventListener('click', openWardrobeFilterModal);
  document.getElementById('wCameraAddBtn')?.addEventListener('click', () => triggerNewGarmentPick('camera'));
  document.getElementById('wEmptyCamBtn')?.addEventListener('click', () => triggerNewGarmentPick('camera'));
  document.getElementById('wEmptyGalBtn')?.addEventListener('click', () => triggerNewGarmentPick('gallery'));
  document.getElementById('wClearFiltersBtn')?.addEventListener('click', () => {
    wFilterCat = null; wFilterSub = null; wFilterColor = null; wFilterSeason = null; wFilterStyle = null;
    wFavOnly = false; wWishOnly = false; wSearchQuery = '';
    renderWardrobeTab();
  });

  document.querySelectorAll('#wMainFilters [data-m]').forEach((b) => {
    b.addEventListener('click', () => {
      const m = b.dataset.m;
      if (m === 'all') { wFilterCat = null; wFilterSub = null; wFavOnly = false; wWishOnly = false; }
      if (m === 'fav') { wFavOnly = !wFavOnly; if (wFavOnly) wWishOnly = false; }
      if (m === 'wish') { wWishOnly = !wWishOnly; if (wWishOnly) wFavOnly = false; }
      renderWardrobeTab();
    });
  });
  document.querySelectorAll('#wMainFilters [data-cat]').forEach((b) => {
    b.addEventListener('click', () => {
      wFilterCat = wFilterCat === b.dataset.cat ? null : b.dataset.cat;
      wFilterSub = null;
      renderWardrobeTab();
    });
  });
  document.querySelectorAll('#wSubFilters [data-sub]').forEach((b) => {
    b.addEventListener('click', () => {
      wFilterSub = wFilterSub === b.dataset.sub ? null : b.dataset.sub;
      renderWardrobeTab();
    });
  });

  document.querySelectorAll('[data-share-id]').forEach((b) => {
    b.addEventListener('click', (e) => {
      e.stopPropagation();
      const target = (appState.items || []).find((x) => x.id === b.dataset.shareId);
      if (target) openShareItem(target);
    });
  });
  document.querySelectorAll('[data-fav-id]').forEach((b) => {
    b.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleFavoriteItem(b.dataset.favId);
    });
  });
  document.querySelectorAll('[data-item-id]').forEach((card) => {
    card.addEventListener('click', () => {
      if (justDraggedWardrobeCard) return;
      openItemDetailModal(card.dataset.itemId);
    });
  });

  if (document.getElementById('wGrid')) {
    attachWardrobeDragReorder();
  }
}

function attachWardrobeDragReorder() {
  const grid = document.getElementById('wGrid');
  if (!grid) return;

  let pressTimer = null;
  let activeCard = null;
  let activeItemId = null;
  let ghostEl = null;
  let startX = 0;
  let startY = 0;
  let grabOffsetX = 0;
  let grabOffsetY = 0;
  let lastClientX = 0;
  let lastClientY = 0;
  let autoScrollRaf = null;
  let lastAutoScrollTs = 0;
  let didReorder = false;
  let activeMode = null;

  const stopAutoScroll = () => {
    if (autoScrollRaf !== null) {
      cancelAnimationFrame(autoScrollRaf);
      autoScrollRaf = null;
    }
    lastAutoScrollTs = 0;
  };

  const clearPressTimer = () => {
    if (pressTimer !== null) {
      clearTimeout(pressTimer);
      pressTimer = null;
    }
  };

  const cleanupDrag = () => {
    stopAutoScroll();
    clearPressTimer();
    window.removeEventListener('mousemove', onMouseMove);
    window.removeEventListener('mouseup', onMouseUp);
    window.removeEventListener('touchend', onTouchEnd);
    window.removeEventListener('touchcancel', onTouchEnd);

    if (ghostEl) {
      ghostEl.remove();
      ghostEl = null;
    }
    if (activeCard) {
      activeCard.classList.remove('drag-placeholder');
      activeCard.style.touchAction = '';
      activeCard = null;
    }
    if (isDraggingWardrobeCard) {
      isDraggingWardrobeCard = false;
      justDraggedWardrobeCard = true;
      setTimeout(() => { justDraggedWardrobeCard = false; }, 260);
      if (didReorder) {
        trackFeature('drag_reorder');
        commitState();
        renderWardrobeTab();
      }
    }
    activeItemId = null;
    didReorder = false;
    activeMode = null;
  };

  const evaluateSwapAtGhost = () => {
    if (!isDraggingWardrobeCard || !ghostEl || !activeCard) return;
    const gRect = ghostEl.getBoundingClientRect();
    const gridRect = grid.getBoundingClientRect();
    const vh = window.innerHeight || document.documentElement.clientHeight || 800;
    const rawCx = gRect.left + gRect.width / 2;
    const rawCy = gRect.top + gRect.height / 2;
    const cx = Math.max(gridRect.left + 8, Math.min(gridRect.right - 8, rawCx));
    const minY = Math.max(4, gridRect.top + 8);
    const maxY = Math.max(minY, Math.min(vh - 4, gridRect.bottom - 8));
    const cy = Math.max(minY, Math.min(maxY, rawCy));

    const cards = Array.from(grid.querySelectorAll('.garment-card[data-item-id]'));
    for (const candidate of cards) {
      if (candidate === activeCard) continue;
      const r = candidate.getBoundingClientRect();
      if (cx >= r.left - 4 && cx <= r.right + 4 && cy >= r.top - 7 && cy <= r.bottom + 7) {
        const targetId = candidate.dataset.itemId;
        if (!targetId || targetId === activeItemId) break;
        const allItems = [...(appState.items || [])];
        const fromIdx = allItems.findIndex((x) => x.id === activeItemId);
        const toIdx = allItems.findIndex((x) => x.id === targetId);
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx !== toIdx) {
          const [moved] = allItems.splice(fromIdx, 1);
          allItems.splice(toIdx, 0, moved);
          appState.items = allItems;
          didReorder = true;
          const curDomOrder = cards.indexOf(activeCard);
          const targetDomOrder = cards.indexOf(candidate);
          if (curDomOrder < targetDomOrder) {
            candidate.after(activeCard);
          } else {
            candidate.before(activeCard);
          }
        }
        break;
      }
    }
  };

  const stepAutoScroll = (ts) => {
    if (!isDraggingWardrobeCard || !ghostEl || !activeCard) {
      stopAutoScroll();
      return;
    }
    const dt = lastAutoScrollTs ? Math.min(48, Math.max(4, ts - lastAutoScrollTs)) / 16.667 : 1;
    lastAutoScrollTs = ts;
    const vh = window.innerHeight || document.documentElement.clientHeight || 800;
    const topZone = Math.min(115, vh * 0.18);
    const bottomZone = Math.min(145, vh * 0.24);
    let dy = 0;
    if (lastClientY < topZone && topZone > 1) {
      const t = Math.max(0, Math.min(1, (topZone - lastClientY) / topZone));
      dy = -(2.4 + t * t * 10.5) * dt;
    } else if (lastClientY > vh - bottomZone && bottomZone > 1) {
      const t = Math.max(0, Math.min(1, (lastClientY - (vh - bottomZone)) / bottomZone));
      dy = (2.4 + t * t * 10.5) * dt;
    }
    if (dy !== 0) {
      const prevY = window.scrollY;
      window.scrollBy(0, dy);
      if (Math.abs(window.scrollY - prevY) > 0.1) {
        evaluateSwapAtGhost();
      }
    }
    autoScrollRaf = requestAnimationFrame(stepAutoScroll);
  };

  const beginDrag = (card, clientX, clientY) => {
    const rect = card.getBoundingClientRect();
    activeCard = card;
    activeCard.style.touchAction = 'none';
    activeItemId = card.dataset.itemId;
    grabOffsetX = clientX - rect.left;
    grabOffsetY = clientY - rect.top;
    lastClientX = clientX;
    lastClientY = clientY;
    isDraggingWardrobeCard = true;
    didReorder = false;

    if (typeof navigator.vibrate === 'function' && (!navigator.userActivation || navigator.userActivation.hasBeenActive)) {
      try { navigator.vibrate(18); } catch { /* ignore */ }
    }

    ghostEl = card.cloneNode(true);
    ghostEl.classList.add('garment-drag-ghost');
    ghostEl.style.width = `${rect.width}px`;
    ghostEl.style.height = `${rect.height}px`;
    ghostEl.style.left = `${rect.left}px`;
    ghostEl.style.top = `${rect.top}px`;
    document.body.appendChild(ghostEl);
    card.classList.add('drag-placeholder');

    stopAutoScroll();
    autoScrollRaf = requestAnimationFrame(stepAutoScroll);
  };

  const moveGhostAndSwap = (clientX, clientY) => {
    if (!isDraggingWardrobeCard || !ghostEl || !activeCard) return;
    lastClientX = clientX;
    lastClientY = clientY;
    const left = clientX - grabOffsetX;
    const top = clientY - grabOffsetY;
    ghostEl.style.left = `${left}px`;
    ghostEl.style.top = `${top}px`;
    evaluateSwapAtGhost();
  };

  function onTouchMove(e) {
    if (!activeCard || activeMode !== 'touch') return;
    if (e.touches.length !== 1) {
      cleanupDrag();
      return;
    }
    const t = e.touches[0];
    if (!isDraggingWardrobeCard) {
      if (Math.hypot(t.clientX - startX, t.clientY - startY) > 10) {
        cleanupDrag();
      }
      return;
    }
    e.preventDefault();
    moveGhostAndSwap(t.clientX, t.clientY);
  }

  function onTouchEnd() {
    cleanupDrag();
  }

  function onMouseMove(e) {
    if (!isDraggingWardrobeCard) {
      if (Math.hypot(e.clientX - startX, e.clientY - startY) > 10) {
        cleanupDrag();
      }
      return;
    }
    e.preventDefault();
    moveGhostAndSwap(e.clientX, e.clientY);
  }

  function onMouseUp() {
    cleanupDrag();
  }

  grid.addEventListener('touchmove', onTouchMove, { passive: false });

  grid.querySelectorAll('.garment-card[data-item-id]').forEach((card) => {
    card.addEventListener('contextmenu', (e) => {
      if (isDraggingWardrobeCard || pressTimer !== null) e.preventDefault();
    });

    card.addEventListener('touchstart', (e) => {
      if (e.touches.length !== 1) return;
      if (e.target instanceof Element && e.target.closest('button')) return;
      const t = e.touches[0];
      startX = t.clientX;
      startY = t.clientY;
      activeCard = card;
      activeMode = 'touch';
      window.addEventListener('touchend', onTouchEnd, { passive: true });
      window.addEventListener('touchcancel', onTouchEnd, { passive: true });
      clearPressTimer();
      pressTimer = setTimeout(() => {
        pressTimer = null;
        if (activeMode === 'touch') beginDrag(card, startX, startY);
      }, 360);
    }, { passive: false });

    card.addEventListener('mousedown', (e) => {
      if (e.button !== 0) return;
      if (e.target instanceof Element && e.target.closest('button')) return;
      startX = e.clientX;
      startY = e.clientY;
      activeMode = 'mouse';
      window.addEventListener('mousemove', onMouseMove);
      window.addEventListener('mouseup', onMouseUp);
      clearPressTimer();
      pressTimer = setTimeout(() => {
        pressTimer = null;
        if (activeMode === 'mouse') beginDrag(card, startX, startY);
      }, 360);
    });
  });
}

function openWardrobeFilterModal() {
  const lang = getLang();
  const st = S();
  const ownedSet = new Set();
  (appState.items || []).forEach((it) => (it.colors || []).forEach((c) => ownedSet.add(c)));
  const ownedColors = GARMENT_COLORS.filter((c) => ownedSet.has(c.id));
  const otherColors = GARMENT_COLORS.filter((c) => !ownedSet.has(c.id));
  const coreFilterIds = new Set(['BLACK', 'WHITE', 'GRAY', 'NAVY', 'BEIGE', 'BROWN']);
  const primaryColors = ownedColors.length > 0
    ? ownedColors
    : GARMENT_COLORS.filter((c) => coreFilterIds.has(c.id));
  const extraColors = ownedColors.length > 0
    ? otherColors
    : GARMENT_COLORS.filter((c) => !coreFilterIds.has(c.id));
  let expandAllColors = !!wFilterColor && extraColors.some((c) => c.id === wFilterColor);

  openModal({
    title: localizedText(lang, 'Detaylı Filtrele', 'Filter Wardrobe'),
    bodyHtml: `
      <div>
        <div style="display:flex; justify-content:space-between; align-items:center; gap:8px; margin-bottom:8px;">
          <div class="section-title" id="wfColorTitle" style="margin:0;"></div>
          ${extraColors.length > 0 ? `<button type="button" class="filter-expand-pill" id="wfExpandColorsBtn"></button>` : ''}
        </div>
        <div class="wrap-row" id="wfColors"></div>
      </div>
      <div>
        <div class="section-title">${localizedText(lang, 'Stil', 'Style')}</div>
        <div class="wrap-row" id="wfStyles">
          ${STYLES.map((s) => `<button type="button" class="chip-pill ${wFilterStyle === s.id ? 'selected' : ''}" data-s="${esc(s.id)}">${esc(displayStyle(s.id, lang))}</button>`).join('')}
        </div>
      </div>
      <div>
        <div class="section-title">${localizedText(lang, 'Mevsim', 'Season')}</div>
        <div class="wrap-row" id="wfSeasons">
          ${SEASONS.map((s) => `<button type="button" class="chip-pill ${wFilterSeason === s.id ? 'selected' : ''}" data-se="${esc(s.id)}">${esc(displaySeason(s.id, lang))}</button>`).join('')}
        </div>
      </div>
    `,
    footerHtml: `
      <button type="button" class="btn-secondary" id="wfClear" style="flex:1.4; min-width:0; padding:11px 10px; white-space:nowrap;">${esc(st.clearFilters)}</button>
      <button type="button" class="btn-primary" id="wfDone" style="flex:0.85; min-width:0; padding:11px 12px; white-space:nowrap;">${esc(st.ok)}</button>
    `,
    onMount: (close, modalEl) => {
      const renderColors = () => {
        const displayed = (expandAllColors || extraColors.length === 0)
          ? [...primaryColors, ...extraColors]
          : primaryColors;

        modalEl.querySelector('#wfColorTitle').textContent = localizedText(
          lang,
          `Renkler (${displayed.length})`,
          `Colors (${displayed.length})`
        );

        const expBtn = modalEl.querySelector('#wfExpandColorsBtn');
        if (expBtn) {
          expBtn.classList.toggle('expanded', expandAllColors);
          const lbl = expandAllColors
            ? st.collapse
            : localizedText(
                lang,
                `Diğer Renkler (+${extraColors.length})`,
                `Other Colors (+${extraColors.length})`,
                `Weitere Farben (+${extraColors.length})`,
                `Autres couleurs (+${extraColors.length})`,
                `Otros colores (+${extraColors.length})`,
                `其他颜色 (+${extraColors.length})`
              );
          expBtn.innerHTML = `<span>${esc(lbl)}</span>${svgIcon(expandAllColors ? 'arrowDropUp' : 'arrowDropDown', 18)}`;
        }

        const box = modalEl.querySelector('#wfColors');
        box.innerHTML = displayed.map((c) => `
          <button type="button" class="chip-pill ${wFilterColor === c.id ? 'selected' : ''}" data-c="${esc(c.id)}">
            <span class="color-dot" style="background:${sanitizeHexColor(c.hex)};"></span>${esc(displayColor(c.id, lang))}
          </button>
        `).join('');

        box.querySelectorAll('[data-c]').forEach((b) => b.addEventListener('click', () => {
          wFilterColor = wFilterColor === b.dataset.c ? null : b.dataset.c;
          box.querySelectorAll('[data-c]').forEach((x) => x.classList.toggle('selected', x.dataset.c === wFilterColor));
        }));
      };

      modalEl.querySelector('#wfExpandColorsBtn')?.addEventListener('click', () => {
        expandAllColors = !expandAllColors;
        renderColors();
      });

      modalEl.querySelectorAll('#wfStyles [data-s]').forEach((b) => b.addEventListener('click', () => {
        wFilterStyle = wFilterStyle === b.dataset.s ? null : b.dataset.s;
        modalEl.querySelectorAll('#wfStyles [data-s]').forEach((x) => x.classList.toggle('selected', x.dataset.s === wFilterStyle));
      }));
      modalEl.querySelectorAll('#wfSeasons [data-se]').forEach((b) => b.addEventListener('click', () => {
        wFilterSeason = wFilterSeason === b.dataset.se ? null : b.dataset.se;
        modalEl.querySelectorAll('#wfSeasons [data-se]').forEach((x) => x.classList.toggle('selected', x.dataset.se === wFilterSeason));
      }));
      modalEl.querySelector('#wfClear').addEventListener('click', () => {
        wFilterColor = null; wFilterStyle = null; wFilterSeason = null; wFilterSub = null;
        close(); renderWardrobeTab();
      });
      modalEl.querySelector('#wfDone').addEventListener('click', () => { close(); renderWardrobeTab(); });

      renderColors();
    }
  });
}

// ==========================================
// PHOTO PICKING + ADD / EDIT GARMENT
// ==========================================
function pickFilesFromInput(inputId) {
  return new Promise((resolve) => {
    const inp = document.getElementById(inputId);
    if (!inp) { resolve([]); return; }
    inp.value = '';
    let settled = false;
    const finish = (list) => {
      if (settled) return;
      settled = true;
      inp.onchange = null;
      inp.oncancel = null;
      resolve(list);
    };
    inp.onchange = () => finish(Array.from(inp.files || []));
    inp.oncancel = () => finish([]);
    inp.click();
  });
}

async function triggerNewGarmentPick(source) {
  const files = await pickFilesFromInput(source === 'camera' ? 'cameraInput' : 'galleryInput');
  if (!files || files.length === 0) return;

  openCropperQueueModal({
    files,
    lang: getLang(),
    modalRoot,
    showToast,
    registerModalBack: registerModalBackHandler,
    onCompleteAll: async (croppedDataUrls) => {
      showToast(localizedText(getLang(), 'Fotoğraf hazırlanıyor...', 'Preparing photo...'));
      const pairs = [];
      for (const origUrl of croppedDataUrls) {
        const uid = createId('ph');
        const origId = `orig_${uid}`;
        const studioId = `bgrm_${uid}`;
        const studioUrl = await toEcommerceFlatLayDataUrl(origUrl);
        await savePhotoDataUrl(origId, origUrl);
        await savePhotoDataUrl(studioId, studioUrl);
        photoMap.set(origId, origUrl);
        photoMap.set(studioId, studioUrl);
        pairs.push({ studioId, origId });
      }
      openAddEditBatch(pairs);
    }
  });
}

function openAddEditBatch(pairs) {
  const total = pairs.length;
  let step = 0;
  const openStep = (replaceHistory = false) => {
    const first = pairs[step];
    openAddEditItemModal({
      existingItem: null,
      initialPairs: first ? [first] : [],
      batchLabel: total > 1 ? `${step + 1}/${total}` : null,
      hasMoreInBatch: step + 1 < total,
      replaceHistory,
      onSavedContinue: (addAnother) => {
        step++;
        if (step < total) openStep(true);
        else if (addAnother) openAddEditItemModal({ existingItem: null, initialPairs: [], replaceHistory: true, onSavedContinue: () => {} });
      }
    });
  };
  openStep(false);
}

async function openAddEditItemModal({
  existingItem = null,
  initialPairs = [],
  batchLabel = null,
  hasMoreInBatch = false,
  replaceHistory = false,
  onSavedContinue = () => {}
}) {
  const lang = getLang();
  const st = S();
  const isEditing = !!existingItem;

  const studioPhotos = existingItem ? [...(existingItem.photoFileNames || [])] : initialPairs.map((p) => p.studioId);
  const originalPhotos = existingItem ? [...(existingItem.originalPhotoFileNames || [])] : initialPairs.map((p) => p.origId);
  const catalogPhotos = existingItem
    ? studioPhotos.map((_, idx) => existingItem.catalogPhotoFileNames?.[idx] || '')
    : initialPairs.map(() => '');
  let useOrig = existingItem ? !!existingItem.useOriginalPhotos : false;
  let useCatalog = existingItem ? !!existingItem.useCatalogPhotos : false;
  let isWish = existingItem ? !!existingItem.isWishlist : false;
  let priceText = existingItem?.priceText || '';
  let productUrl = existingItem?.productUrl || '';
  let nameVal = existingItem?.name || '';
  let userEditedName = isEditing;
  let autoPredictedBadge = false;
  let cat = existingItem?.category || 'TOP';
  let subCat = existingItem?.subCategory || 'TSHIRT';
  let styleTag = existingItem?.styleTag || 'CASUAL';
  const seasons = new Set(existingItem?.seasons?.length ? existingItem.seasons : ['ALL']);
  const colors = [...(existingItem?.colors || [])];
  let extractedHexes = [...(existingItem?.extractedHexes || [])];
  let notes = existingItem?.notes || '';

  if (!isEditing && studioPhotos[0]) {
    const u = photoMap.get(studioPhotos[0]);
    if (u) {
      const pred = await analyzeGarmentDataUrl(u, lang);
      extractedHexes = pred.extractedHexes;
      if (colors.length === 0) colors.push(...pred.detectedColors);
      subCat = pred.predictedSubCategory;
      cat = SUBCATEGORIES.find((s) => s.id === subCat)?.cat || cat;
      if (!userEditedName) {
        nameVal = pred.suggestedName;
        autoPredictedBadge = true;
      }
    }
  }

  const title = isEditing
    ? localizedText(lang, 'Kıyafeti Düzenle', 'Edit Piece')
    : (batchLabel ? localizedText(lang, `Yeni Kıyafet (${batchLabel})`, `Add Piece (${batchLabel})`) : localizedText(lang, 'Yeni Kıyafet Ekle', 'Add New Piece'));

  const modalClose = openModal({
    title,
    subtitle: batchLabel ? localizedText(lang, 'Seçilen her fotoğraf ayrı bir kıyafet olarak düzenleniyor', 'Each selected photo is saved as its own item') : '',
    fullscreen: true,
    replaceHistory,
    bodyHtml: `
      <div>
        <div class="ae-section-title"><span>${localizedText(lang, 'Bu parça nereye eklensin?', 'Where should this piece live?')}</span></div>
        <div class="btn-row" id="aeDestToggle">
          <button type="button" class="mode-select-btn ${!isWish ? 'selected' : ''}" data-w="0">${svgIcon('checkroom', 16)} ${esc(st.addToWardrobeLabel)}</button>
          <button type="button" class="mode-select-btn ${isWish ? 'selected' : ''}" data-w="1">${svgIcon('bookmarkBorder', 16)} ${esc(st.addToWishlistLabel)}</button>
        </div>
        <div id="aeWishFields" class="${isWish ? '' : 'hidden'}" style="display:flex; flex-direction:column; gap:10px; margin-top:10px;">
          <input type="text" class="input-pill" id="aePriceInput" placeholder="${esc(st.pricePlaceholder)}" value="${esc(priceText)}" />
          <input type="url" class="input-pill" id="aeUrlInput" placeholder="https://... (${localizedText(lang, 'Ürün linki (Fiyat düşünce bildirim için)', 'Product Link')})" value="${esc(productUrl)}" />
        </div>
      </div>

      <div>
        <div class="ae-section-title">
          <span>${localizedText(lang, 'Fotoğraflar', 'Photos')} (<span id="aePhotoCount">${studioPhotos.length}</span>)</span>
          <span id="aePhotoHint" class="muted-note ${studioPhotos.length > 0 ? '' : 'hidden'}" style="font-size:11px; font-weight:400;">${localizedText(lang, 'Ek açı fotoğrafları ekleyebilirsin', 'Tap camera/gallery to add angles')}</span>
        </div>
        <div id="aePhotoStrip" style="display:flex; gap:10px; overflow-x:auto; padding:2px 0; scrollbar-width:none;"></div>
        <div id="aePhotoControls" class="${studioPhotos.length > 0 ? '' : 'hidden'}">
          <div class="btn-row" style="margin-top:8px;">
            <button type="button" class="mode-select-btn" id="aeModeStudio">${svgIcon('autoFix', 15)} ${localizedText(lang, 'Arka Planı Kaldır', 'Remove BG', 'Hintergrund', 'Sans fond', 'Sin fondo', '去背景')}</button>
            <button type="button" class="mode-select-btn" id="aeModeOrig">${svgIcon('photoLibrary', 15)} ${localizedText(lang, 'Orijinal', 'Original', 'Original', 'Original', 'Original', '原图')}</button>
          </div>
          <button type="button" class="btn-accent-outline" id="aeFlatLayBtn" style="width:100%; margin-top:8px; border-radius:var(--btn-radius);"></button>
        </div>
        <div class="btn-row" style="margin-top:10px; gap:10px; flex-wrap:nowrap;">
          <button type="button" class="btn-primary photo-action-btn" id="aeAddCam"></button>
          <button type="button" class="btn-secondary photo-action-btn" id="aeAddGal"></button>
        </div>
      </div>

      <div>
        <div class="ae-section-title">
          <span>${localizedText(lang, 'Kıyafet Adı', 'Item Name')}</span>
          <span id="aeAutoBadge" class="chip-pill compact accent ${autoPredictedBadge && !userEditedName ? '' : 'hidden'}" style="border:none;">${localizedText(lang, 'Otomatik tahmin', 'Auto-predicted')}</span>
        </div>
        <input type="text" class="input-pill" id="aeNameInput" value="${esc(nameVal)}" placeholder="${localizedText(lang, 'Fotoğraf ekleyince tahmin edilir veya yazabilirsin', 'Auto-predicted from photo or type your own')}" />
      </div>

      <div>
        <div class="ae-section-title"><span>${localizedText(lang, 'Genel Kategori', 'Category')}</span></div>
        <div class="wrap-row" id="aeCatChips"></div>
      </div>

      <div>
        <div class="ae-section-title"><span>${localizedText(lang, 'Kıyafet Türü', 'Piece Type')}</span></div>
        <div class="wrap-row" id="aeSubChips"></div>
      </div>

      <div>
        <div class="ae-section-title">
          <span>${localizedText(lang, 'Renkler', 'Colors')}</span>
          ${extractedHexes.length > 0 ? `
            <span class="detected-row">
              ${localizedText(lang, 'Algılanan:', 'Detected:')}
              ${extractedHexes.slice(0, 3).map((hx) => `<span class="color-dot" style="background:${sanitizeHexColor(hx)}; width:13px; height:13px;"></span>`).join('')}
            </span>
          ` : ''}
        </div>
        <div class="color-swatch-row" id="aeColorChips"></div>
      </div>

      <div>
        <div class="ae-section-title"><span>${localizedText(lang, 'Stil', 'Style')}</span></div>
        <div class="wrap-row" id="aeStyleChips"></div>
      </div>

      <div>
        <div class="ae-section-title"><span>${localizedText(lang, 'Mevsim', 'Season')}</span></div>
        <div class="wrap-row" id="aeSeasonChips"></div>
      </div>

      <div>
        <div class="ae-section-title"><span>${localizedText(lang, 'Not (isteğe bağlı)', 'Notes (optional)')}</span></div>
        <textarea class="notes-textarea" id="aeNotesInput" placeholder="${localizedText(lang, 'Kumaş, kalıp, not...', 'Fabric, fit, brand or styling notes...')}">${esc(notes)}</textarea>
      </div>
    `,
    footerHtml: `
      ${!isEditing && !hasMoreInBatch ? `<button type="button" class="btn-secondary" id="aeSaveNewBtn" style="flex:1;">${localizedText(lang, 'Kaydet & Yeni', 'Save & New')}</button>` : ''}
      <button type="button" class="btn-primary" id="aeSaveBtn" style="flex:1;">${isEditing ? localizedText(lang, 'Güncelle', 'Update') : (hasMoreInBatch ? localizedText(lang, 'Kaydet & Sıradaki', 'Save & Next') : esc(st.save))}</button>
    `,
    onMount: (close, modalEl) => {
      const syncAutoBadge = () => {
        const badge = modalEl.querySelector('#aeAutoBadge');
        if (badge) badge.classList.toggle('hidden', !(autoPredictedBadge && !userEditedName));
      };

      let isAeTransforming = false;
      const catalogBtnDefaultHtml = (hasCat) => `${svgIcon('autoAwesome', 15)} ${hasCat
        ? localizedText(lang, 'Katalog Fotoğrafı Kullan', 'Use Catalog Photo', 'Katalogfoto nutzen', 'Utiliser photo catalogue', 'Usar foto de catálogo', '使用商品图')
        : localizedText(lang, 'Katalog Fotoğrafı Oluştur', 'Create Catalog Photo', 'Katalogfoto erstellen', 'Créer photo catalogue', 'Crear foto de catálogo', '生成商品图')}`;

      const renderPhotoStrip = () => {
        const hasCat = Boolean(catalogPhotos[0]);
        const catSelected = !useOrig && useCatalog && hasCat;
        const removeBgSelected = !useOrig && !catSelected;
        const list = useOrig && originalPhotos.length === studioPhotos.length
          ? originalPhotos
          : (useCatalog && catalogPhotos.length > 0
            ? studioPhotos.map((cut, i) => catalogPhotos[i] || cut)
            : studioPhotos);
        const hasPhotos = list.length > 0;
        modalEl.querySelector('#aePhotoCount').textContent = String(list.length);
        modalEl.querySelector('#aePhotoHint')?.classList.toggle('hidden', !hasPhotos);
        modalEl.querySelector('#aePhotoControls')?.classList.toggle('hidden', !hasPhotos);
        modalEl.querySelector('#aeSaveBtn').disabled = studioPhotos.length === 0;
        const saveNewBtn = modalEl.querySelector('#aeSaveNewBtn');
        if (saveNewBtn) saveNewBtn.disabled = studioPhotos.length === 0;

        modalEl.querySelector('#aeModeStudio')?.classList.toggle('selected', removeBgSelected);
        modalEl.querySelector('#aeModeOrig')?.classList.toggle('selected', useOrig);
        const flatBtn = modalEl.querySelector('#aeFlatLayBtn');
        if (flatBtn) {
          flatBtn.classList.toggle('selected', catSelected);
          if (!isAeTransforming) flatBtn.innerHTML = catalogBtnDefaultHtml(hasCat);
        }

        modalEl.querySelector('#aeAddCam').innerHTML = `${svgIcon('camera', 17)} ${hasPhotos ? localizedText(lang, 'Fotoğraf Ekle', 'Add Photo') : localizedText(lang, 'Fotoğraf Çek', 'Take Photo')}`;
        modalEl.querySelector('#aeAddGal').innerHTML = `${svgIcon('photoLibrary', 17)} ${hasPhotos ? localizedText(lang, 'Açı Ekle', 'Add Angle') : localizedText(lang, 'Galeriden Seç', 'Gallery')}`;

        const strip = modalEl.querySelector('#aePhotoStrip');
        strip.innerHTML = list.map((pid, idx) => `
          <div style="position:relative; width:138px; height:138px; border-radius:14px; background:${useOrig ? 'var(--photo-surface)' : '#fff'}; border:${idx === 0 ? '2px solid var(--accent)' : '1px solid var(--border)'}; flex-shrink:0; overflow:hidden;">
            <img src="${safeImgSrc(photoMap.get(pid))}" style="width:100%; height:100%; object-fit:${useOrig ? 'cover' : 'contain'}; padding:${useOrig ? '0' : '6px'};" />
            <button type="button" class="photo-edit-btn" data-edit-photo="${idx}" aria-label="${esc(localizedText(lang, 'Fotoğrafı Düzenle', 'Edit Photo'))}" title="${esc(localizedText(lang, 'Fotoğrafı Düzenle', 'Edit Photo'))}">${svgIcon('crop', 14)}</button>
            <button type="button" data-cover="${idx}" style="position:absolute; bottom:6px; left:6px; border:none; border-radius:999px; font-size:10px; font-weight:700; padding:3px 7px; display:inline-flex; align-items:center; gap:3px; background:${idx === 0 ? 'var(--btn-bg)' : 'rgba(255,255,255,0.92)'}; color:${idx === 0 ? 'var(--btn-fg)' : '#1F1F1D'}; cursor:pointer;">${idx === 0 ? `${svgIcon('starFilled', 11)} ${localizedText(lang, 'Kapak', 'Cover')}` : localizedText(lang, 'Kapak yap', 'Set Cover')}</button>
            <button type="button" data-del="${idx}" style="position:absolute; top:6px; right:6px; border:none; border-radius:50%; width:24px; height:24px; background:rgba(31,31,29,0.8); color:#fff; display:inline-flex; align-items:center; justify-content:center; padding:0; cursor:pointer;" aria-label="Remove">${svgIcon('close', 14)}</button>
          </div>
        `).join('');
        strip.querySelectorAll('[data-edit-photo]').forEach((b) => b.addEventListener('click', () => {
          const i = Number(b.dataset.editPhoto);
          const srcId = originalPhotos[i] || studioPhotos[i];
          const srcUrl = srcId ? photoMap.get(srcId) : null;
          if (!srcUrl) {
            showToast(localizedText(lang, 'Bu dosya görsel olarak açılamadı', 'Could not open this image'));
            return;
          }
          openSingleDataUrlCropModal({
            dataUrl: srcUrl,
            lang,
            modalRoot,
            showToast,
            registerModalBack: registerModalBackHandler,
            onComplete: async (croppedUrl) => {
              const uid = createId('ph');
              const sId = `bgrm_${uid}`;
              const oId = `orig_${uid}`;
              const sUrl = await toEcommerceFlatLayDataUrl(croppedUrl);
              await savePhotoDataUrl(oId, croppedUrl);
              await savePhotoDataUrl(sId, sUrl);
              photoMap.set(oId, croppedUrl);
              photoMap.set(sId, sUrl);
              studioPhotos[i] = sId;
              while (originalPhotos.length <= i) originalPhotos.push(studioPhotos[originalPhotos.length]);
              originalPhotos[i] = oId;
              while (catalogPhotos.length < studioPhotos.length) catalogPhotos.push('');
              catalogPhotos[i] = '';
              if (i === 0) useCatalog = false;
              renderPhotoStrip();
              showToast(localizedText(lang, 'Fotoğraf güncellendi', 'Photo updated'));
            }
          });
        }));
        strip.querySelectorAll('[data-cover]').forEach((b) => b.addEventListener('click', () => {
          const i = Number(b.dataset.cover);
          if (i > 0) {
            studioPhotos.unshift(...studioPhotos.splice(i, 1));
            if (i < originalPhotos.length) originalPhotos.unshift(...originalPhotos.splice(i, 1));
            while (catalogPhotos.length < studioPhotos.length) catalogPhotos.push('');
            if (i < catalogPhotos.length) catalogPhotos.unshift(...catalogPhotos.splice(i, 1));
            renderPhotoStrip();
          }
        }));
        strip.querySelectorAll('[data-del]').forEach((b) => b.addEventListener('click', () => {
          const i = Number(b.dataset.del);
          studioPhotos.splice(i, 1);
          if (i < originalPhotos.length) originalPhotos.splice(i, 1);
          if (i < catalogPhotos.length) catalogPhotos.splice(i, 1);
          renderPhotoStrip();
        }));
      };

      const colorStripEl = modalEl.querySelector('#aeColorChips');
      colorStripEl.innerHTML = GARMENT_COLORS.map((c) => `
        <button type="button" class="color-swatch-item ${colors.includes(c.id) ? 'selected' : ''}" data-col="${esc(c.id)}">
          <span class="color-swatch-circle" style="background:${sanitizeHexColor(c.hex)};"></span>
          <span class="color-swatch-label">${esc(displayColor(c.id, lang))}</span>
        </button>
      `).join('');
      colorStripEl.addEventListener('wheel', (e) => {
        if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
          e.preventDefault();
          colorStripEl.scrollLeft += e.deltaY;
        }
      }, { passive: false });
      colorStripEl.querySelectorAll('[data-col]').forEach((b) => b.addEventListener('click', () => {
        const id = b.dataset.col;
        const ix = colors.indexOf(id);
        if (ix >= 0) colors.splice(ix, 1);
        else colors.unshift(id);
        b.classList.toggle('selected', colors.includes(id));
        if (!userEditedName) modalEl.querySelector('#aeNameInput').value = cleanGarmentTitleForName(colors[0], subCat, lang);
      }));

      const renderTaxonomies = () => {
        modalEl.querySelector('#aeCatChips').innerHTML = CATEGORIES.map((c) => `
          <button type="button" class="chip-pill ${cat === c.id ? 'selected' : ''}" data-c="${esc(c.id)}">${esc(displayCategory(c.id, lang))}</button>
        `).join('');
        modalEl.querySelectorAll('#aeCatChips [data-c]').forEach((b) => b.addEventListener('click', () => {
          cat = b.dataset.c;
          const firstSub = SUBCATEGORIES.find((s) => s.cat === cat)?.id;
          if (firstSub && SUBCATEGORIES.find((s) => s.id === subCat)?.cat !== cat) subCat = firstSub;
          if (!userEditedName) modalEl.querySelector('#aeNameInput').value = cleanGarmentTitleForName(colors[0], subCat, lang);
          renderTaxonomies();
        }));

        modalEl.querySelector('#aeSubChips').innerHTML = SUBCATEGORIES.filter((s) => s.cat === cat).map((s) => `
          <button type="button" class="chip-pill ${subCat === s.id ? 'selected' : ''}" data-sub="${esc(s.id)}">${esc(displaySubCategory(s.id, lang))}</button>
        `).join('');
        modalEl.querySelectorAll('#aeSubChips [data-sub]').forEach((b) => b.addEventListener('click', () => {
          subCat = b.dataset.sub;
          if (!userEditedName) modalEl.querySelector('#aeNameInput').value = cleanGarmentTitleForName(colors[0], subCat, lang);
          renderTaxonomies();
        }));

        modalEl.querySelector('#aeStyleChips').innerHTML = STYLES.map((s) => `
          <button type="button" class="chip-pill ${styleTag === s.id ? 'selected' : ''}" data-st="${esc(s.id)}">${esc(displayStyle(s.id, lang))}</button>
        `).join('');
        modalEl.querySelectorAll('#aeStyleChips [data-st]').forEach((b) => b.addEventListener('click', () => {
          styleTag = b.dataset.st; renderTaxonomies();
        }));

        modalEl.querySelector('#aeSeasonChips').innerHTML = SEASONS.map((s) => `
          <button type="button" class="chip-pill ${seasons.has(s.id) ? 'selected' : ''}" data-se="${esc(s.id)}">${esc(displaySeason(s.id, lang))}</button>
        `).join('');
        modalEl.querySelectorAll('#aeSeasonChips [data-se]').forEach((b) => b.addEventListener('click', () => {
          const id = b.dataset.se;
          if (id === 'ALL') { seasons.clear(); seasons.add('ALL'); }
          else {
            seasons.delete('ALL');
            if (seasons.has(id)) seasons.delete(id); else seasons.add(id);
            if (seasons.size === 0) seasons.add('ALL');
          }
          renderTaxonomies();
        }));
      };

      modalEl.querySelectorAll('#aeDestToggle .mode-select-btn').forEach((b) => b.addEventListener('click', () => {
        isWish = b.dataset.w === '1';
        modalEl.querySelectorAll('#aeDestToggle .mode-select-btn').forEach((x) => x.classList.toggle('selected', (x.dataset.w === '1') === isWish));
        modalEl.querySelector('#aeWishFields').classList.toggle('hidden', !isWish);
      }));

      modalEl.querySelector('#aeNameInput').addEventListener('input', (e) => {
        nameVal = e.target.value;
        userEditedName = nameVal.trim().length > 0;
        syncAutoBadge();
      });
      modalEl.querySelector('#aeUrlInput').addEventListener('change', (e) => {
        e.target.value = normalizeProductUrl(e.target.value);
      });
      modalEl.querySelector('#aeModeStudio').addEventListener('click', async () => {
        if (isAeTransforming) return;
        trackFeature('remove_bg', 'add_edit');
        const modeBtn = modalEl.querySelector('#aeModeStudio');
        modeBtn.disabled = true;
        try {
          for (let i = 0; i < studioPhotos.length; i++) {
            const sId = studioPhotos[i];
            const oId = originalPhotos[i];
            const oUrl = oId ? photoMap.get(oId) : null;
            if (oUrl && (!sId || !sId.startsWith('bgrm_') || photoMap.get(sId) === oUrl)) {
              const cleanedUrl = await toEcommerceFlatLayDataUrl(oUrl);
              const newCutoutId = `bgrm_${createId('ph')}`;
              await savePhotoDataUrl(newCutoutId, cleanedUrl);
              photoMap.set(newCutoutId, cleanedUrl);
              studioPhotos[i] = newCutoutId;
            }
          }
          useOrig = false;
          useCatalog = false;
          renderPhotoStrip();
        } finally {
          modeBtn.disabled = false;
        }
      });
      modalEl.querySelector('#aeModeOrig').addEventListener('click', () => {
        if (isAeTransforming) return;
        useOrig = true;
        useCatalog = false;
        renderPhotoStrip();
      });

      modalEl.querySelector('#aeFlatLayBtn').addEventListener('click', async () => {
        if (isAeTransforming) return;
        if (catalogPhotos[0]) {
          trackFeature('catalog_ai', 'reuse');
          useOrig = false;
          useCatalog = true;
          renderPhotoStrip();
          return;
        }
        const srcId = originalPhotos[0] || studioPhotos[0];
        const srcUrl = srcId ? photoMap.get(srcId) : null;
        if (!srcUrl) return;
        trackFeature('catalog_ai', 'generate');
        startFeatureTimer('catalog_ai');
        const btn = modalEl.querySelector('#aeFlatLayBtn');
        isAeTransforming = true;
        btn.disabled = true;
        btn.innerHTML = `<span class="spinner"></span> ${localizedText(lang, 'Katalog fotoğrafı oluşturuluyor...', 'Creating catalog photo...', 'Katalogfoto wird erstellt...', 'Création en cours...', 'Creando foto de catálogo...', '正在生成商品图...')}`;
        try {
          const outUrl = await generateAiEcommerceFlatLay(srcUrl, {
            geminiApiKey: await getGeminiApiKey(),
            hfToken: await getHfToken()
          });
          const newCatId = `catalog_${createId('ph')}`;
          await savePhotoDataUrl(newCatId, outUrl);
          photoMap.set(newCatId, outUrl);
          while (catalogPhotos.length < studioPhotos.length) catalogPhotos.push('');
          catalogPhotos[0] = newCatId;
          useOrig = false;
          useCatalog = true;
          showToast(localizedText(lang, 'Katalog fotoğrafı hazır', 'Catalog photo ready', 'Katalogfoto bereit', 'Photo catalogue prête', 'Foto de catálogo lista', '商品图已生成'));
        } catch (err) {
          trackTelemetryError('catalog_ai', err?.message || err?.name || 'error');
          if (err instanceof GeminiApiError) {
            showToast(localizedText(lang, `Gemini API hatası: ${err.message}`, `Gemini API error: ${err.message}`));
          } else if (err instanceof StudioQuotaError) {
            const hasHf = Boolean(await getHfToken());
            showToast(hasHf
              ? localizedText(lang, 'Hugging Face ücretsiz ZeroGPU günlük limiti doldu veya sunucular yoğun. Biraz sonra tekrar dene veya Ayarlar\'dan Gemini anahtarı ekle.', 'Hugging Face free ZeroGPU daily limit reached or servers busy. Try again shortly or add a Gemini key in Settings.')
              : localizedText(lang, 'Ücretsiz AI günlük limiti doldu. Ayarlar\'dan ücretsiz Hugging Face tokenı veya Gemini anahtarı ekleyebilirsin.', 'Free daily AI limit reached. Add a free Hugging Face token or Gemini key in Settings.'));
          } else if (err instanceof StudioBusyError) {
            showToast(localizedText(lang, 'AI katalog sunucuları şu an yoğun. Biraz sonra tekrar dene veya Ayarlar\'dan Gemini anahtarı ekle.', 'Catalog AI servers are currently busy. Try again shortly or add a Gemini key in Settings.'));
          } else {
            showToast(localizedText(lang, 'Katalog fotoğrafı oluşturulamadı. İnternet bağlantını kontrol edip tekrar dene.', 'Could not create catalog photo. Check your connection and try again.'));
          }
        } finally {
          endFeatureTimer('catalog_ai');
          isAeTransforming = false;
          btn.disabled = false;
          renderPhotoStrip();
        }
      });

      const addMorePhotos = async (mode) => {
        const files = await pickFilesFromInput(mode === 'camera' ? 'cameraInput' : 'galleryInput');
        if (!files.length) return;
        openCropperQueueModal({
          files, lang, modalRoot, showToast,
          registerModalBack: registerModalBackHandler,
          onCompleteAll: async (urls) => {
            for (const oUrl of urls) {
              const uid = createId('ph');
              const sId = `bgrm_${uid}`, oId = `orig_${uid}`;
              const sUrl = await toEcommerceFlatLayDataUrl(oUrl);
              await savePhotoDataUrl(oId, oUrl);
              await savePhotoDataUrl(sId, sUrl);
              photoMap.set(oId, oUrl); photoMap.set(sId, sUrl);
              studioPhotos.push(sId); originalPhotos.push(oId); catalogPhotos.push('');
            }
            renderPhotoStrip();
          }
        });
      };
      modalEl.querySelector('#aeAddCam').addEventListener('click', () => addMorePhotos('camera'));
      modalEl.querySelector('#aeAddGal').addEventListener('click', () => addMorePhotos('gallery'));

      const doSave = (addAnother) => {
        if (studioPhotos.length === 0) {
          showToast(localizedText(lang, 'En az 1 fotoğraf ekle', 'Add at least 1 photo'));
          return;
        }
        const finalName = modalEl.querySelector('#aeNameInput').value.trim() || displaySubCategory(subCat, lang);
        const finalPrice = modalEl.querySelector('#aePriceInput').value.trim();
        const finalUrl = normalizeProductUrl(modalEl.querySelector('#aeUrlInput').value);
        if (isWish && finalUrl && !isSafePublicHttpsUrl(finalUrl)) {
          showToast(localizedText(lang, 'Ürün linki geçerli bir genel https:// adresi olmalı', 'Product link must be a valid public https:// URL'));
          return;
        }
        const finalNotes = modalEl.querySelector('#aeNotesInput').value.trim();

        const obj = {
          id: existingItem?.id || createId('item'),
          name: finalName,
          category: cat,
          subCategory: subCat,
          styleTag,
          seasons: [...seasons],
          colors: [...colors],
          extractedHexes,
          photoFileNames: studioPhotos,
          originalPhotoFileNames: originalPhotos,
          useOriginalPhotos: useOrig,
          catalogPhotoFileNames: catalogPhotos,
          useCatalogPhotos: useCatalog,
          notes: finalNotes,
          isFavorite: existingItem?.isFavorite || false,
          isWishlist: isWish,
          priceText: isWish ? finalPrice : '',
          productUrl: isWish ? finalUrl : '',
          lastCheckedPrice: isWish ? (parsePrice(finalPrice) || existingItem?.lastCheckedPrice || 0) : 0,
          previousPrice: existingItem?.previousPrice || 0,
          priceDropped: existingItem?.priceDropped || false,
          wearCount: existingItem?.wearCount || 0,
          lastWornAt: existingItem?.lastWornAt || 0,
          createdAt: existingItem?.createdAt || Date.now()
        };

        if (isEditing) {
          appState.items = appState.items.map((x) => (x.id === obj.id ? obj : x));
          trackFeature('edit_item', cat);
        } else {
          appState.items = [obj, ...(appState.items || [])];
          trackFeature('add_item', cat);
        }
        commitState();
        close({ skipHistoryBack: !!(hasMoreInBatch || addAnother) });
        renderCurrentTab();
        onSavedContinue(addAnother);
      };

      modalEl.querySelector('#aeSaveBtn').addEventListener('click', () => doSave(false));
      modalEl.querySelector('#aeSaveNewBtn')?.addEventListener('click', () => doSave(true));

      renderPhotoStrip();
      renderTaxonomies();
    }
  });
  return modalClose;
}

// ==========================================
// ITEM DETAIL & WEAR COUNT EDITOR MODALS
// ==========================================
function openItemDetailModal(itemId) {
  const item = (appState.items || []).find((i) => i.id === itemId);
  if (!item) return;
  const lang = getLang();
  const st = S();
  let pageIdx = 0;
  const relatedOutfits = (appState.outfits || []).filter((o) => (o.itemIds || []).includes(item.id));

  openModal({
    title: '',
    headerLeftHtml: `<button type="button" class="btn-icon-ghost" id="idBackBtn" aria-label="${localizedText(lang, 'Geri', 'Back')}">${svgIcon('arrowBack', 22)}</button>`,
    headerRightHtml: `
      <button type="button" class="btn-icon-ghost" id="idShareBtn" aria-label="${localizedText(lang, 'Paylaş', 'Share')}" style="color:var(--ink);">${svgIcon('share', 20)}</button>
      <button type="button" class="btn-icon-ghost" id="idFavBtn" aria-label="Favori" style="color:${item.isFavorite ? 'var(--accent)' : 'var(--ink)'};">${svgIcon(item.isFavorite ? 'favoriteFilled' : 'favoriteBorder', 21)}</button>
      <button type="button" class="btn-icon-ghost" id="idEditBtn" aria-label="${localizedText(lang, 'Düzenle', 'Edit')}" style="color:var(--ink);">${svgIcon('edit', 21)}</button>
      <button type="button" class="btn-icon-ghost" id="idDeleteBtn" aria-label="${localizedText(lang, 'Sil', 'Delete')}" style="color:var(--danger);">${svgIcon('deleteOutline', 21)}</button>
    `,
    hideCloseX: true,
    fullscreen: true,
    bodyHtml: `
      ${(() => {
        const initOrig = Boolean(item.useOriginalPhotos && !item.useCatalogPhotos);
        const initCatalog = Boolean(item.useCatalogPhotos && item.catalogPhotoFileNames?.[0] && photoMap.get(item.catalogPhotoFileNames[0]));
        const hasInitCatalogSlot = Boolean(item.catalogPhotoFileNames?.[0] && photoMap.get(item.catalogPhotoFileNames[0]));
        return `
      <div id="idStage" style="position:relative; width:100%; aspect-ratio:1/1; background:${initOrig ? 'var(--photo-surface)' : '#fff'}; border:1px solid var(--border); border-radius:var(--card-radius); overflow:hidden; display:flex; align-items:center; justify-content:center; cursor:zoom-in;">
        <img id="idMainImg" src="${coverUrlOf(item) || ''}" style="width:100%; height:100%; object-fit:${initOrig ? 'cover' : 'contain'}; padding:${initOrig ? '0' : '14px'};" />
        <span id="idCounter" style="position:absolute; bottom:12px; right:12px; background:rgba(31,31,29,0.8); color:#fff; padding:5px 10px; border-radius:999px; font-size:12px; font-weight:600; display:inline-flex; align-items:center; gap:5px;">${svgIcon('zoomIn', 14)} <span id="idCounterTxt">1 / ${Math.max(1, activePhotoIds(item).length)}</span></span>
      </div>
      <div class="btn-row" id="idModeRow">
        <button type="button" class="mode-select-btn ${!initOrig && !initCatalog ? 'selected' : ''}" id="idModeStudio">${svgIcon('autoFix', 15)} ${localizedText(lang, 'Arka Planı Kaldır', 'Remove BG', 'Hintergrund', 'Sans fond', 'Sin fondo', '去背景')}</button>
        <button type="button" class="mode-select-btn ${initOrig ? 'selected' : ''}" id="idModeOrig">${svgIcon('photoLibrary', 15)} ${localizedText(lang, 'Orijinal', 'Original', 'Original', 'Original', 'Original', '原图')}</button>
      </div>
      <button type="button" class="btn-accent-outline ${initCatalog ? 'selected' : ''}" id="idFlatLayBtn">${svgIcon('autoAwesome', 16)} ${hasInitCatalogSlot ? localizedText(lang, 'Katalog Fotoğrafı Kullan', 'Use Catalog Photo', 'Katalogfoto verwenden', 'Utiliser photo catalogue', 'Usar foto de catálogo', '使用商品图') : localizedText(lang, 'Katalog Fotoğrafı Oluştur', 'Create Catalog Photo', 'Katalogfoto erstellen', 'Créer photo catalogue', 'Crear foto de catálogo', '生成商品图')}</button>
      <div class="chip-row flush" id="idThumbs"></div>
      <div id="idPhotoActions" class="btn-row" style="display:none;"></div>

      <div class="card" style="padding:14px;">
        <div style="font-size:15px; font-weight:600; color:var(--ink); margin-bottom:10px;">${localizedText(lang, 'Bu kıyafete yeni fotoğraf ekle', 'Add more angles to this piece')}</div>
        <div class="btn-row" style="gap:10px; flex-wrap:nowrap;">
          <button type="button" class="btn-primary photo-action-btn" id="idAddCamBtn">${svgIcon('camera', 17)} ${localizedText(lang, 'Kamera', 'Camera')}</button>
          <button type="button" class="btn-secondary photo-action-btn" id="idAddGalBtn">${svgIcon('photoLibrary', 17)} ${localizedText(lang, 'Galeri', 'Gallery')}</button>
        </div>
      </div>`;
      })()}

      ${item.isWishlist ? `
        <div class="card" style="background:var(--accent-soft); border-color:var(--accent);">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <strong style="display:inline-flex; align-items:center; gap:6px;">${svgIcon('bookmarkBorder', 16)} ${esc(st.wishlist)}</strong>
            ${item.priceText ? `<span class="chip-pill compact selected">${esc(item.priceText)}</span>` : ''}
          </div>
          ${isSafePublicHttpsUrl(item.productUrl) ? `
            <div class="btn-row" style="margin-top:10px;">
              <button type="button" class="btn-secondary btn-sm" id="idOpenStore">${svgIcon('openInNew', 15)} ${localizedText(lang, 'Mağazada Aç', 'Open Store')}</button>
            </div>
          ` : ''}
          <button type="button" class="btn-primary" id="idMoveToCloset" style="width:100%; margin-top:10px;">${svgIcon('shoppingBag', 16)} ${esc(st.moveToWardrobeBtn)}</button>
        </div>
      ` : ''}

      <div class="card" style="padding:16px; display:flex; flex-direction:column; gap:12px;">
        <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
          <h3 style="margin:0; font-size:20px; font-weight:600; color:var(--ink); flex:1; min-width:0;">${esc(item.name || displaySubCategory(item.subCategory, lang))}</h3>
          ${!item.isWishlist ? `<button type="button" class="chip-pill" id="idWearBtn" title="Basılı tut: miktarı düzenle"><span style="color:var(--accent);">${svgIcon('checkCircle', 15)}</span> ${esc(st.wornBtn)}${item.wearCount > 0 ? ` (${item.wearCount})` : ''}</button>` : ''}
        </div>
        <div class="wrap-row">
          <span class="detail-tag-chip">${esc(displayCategory(item.category, lang))}</span>
          <span class="detail-tag-chip">${esc(displaySubCategory(item.subCategory, lang))}</span>
          <span class="detail-tag-chip">${esc(displayStyle(item.styleTag, lang))}</span>
          ${(item.seasons || []).map((se) => `<span class="detail-tag-chip">${esc(displaySeason(se, lang))}</span>`).join('')}
        </div>
        ${((item.colors || []).length > 0 || (item.extractedHexes || []).length > 0) ? `
          <div>
            <div style="font-size:12px; font-weight:500; color:var(--ink-muted); margin-bottom:6px;">${localizedText(lang, 'Renk Paleti', 'Color Palette')}</div>
            <div class="wrap-row" style="gap:10px 12px;">
              ${(item.colors || []).length > 0
                ? item.colors.map((c) => `<span style="display:inline-flex; align-items:center; gap:6px; font-size:14px; color:var(--ink);"><span class="color-dot" style="width:16px; height:16px; background:${sanitizeHexColor(getColorHex(c))};"></span>${esc(displayColor(c, lang))}</span>`).join('')
                : (item.extractedHexes || []).map((hx) => `<span class="color-dot" style="width:18px; height:18px; background:${sanitizeHexColor(hx)};"></span>`).join('')
              }
            </div>
          </div>
        ` : ''}
        ${item.notes ? `
          <div>
            <div style="font-size:12px; font-weight:500; color:var(--ink-muted); margin-bottom:4px;">${localizedText(lang, 'Notlar', 'Notes')}</div>
            <p style="margin:0; font-size:14px; color:var(--ink); line-height:1.45;">${esc(item.notes)}</p>
          </div>
        ` : ''}
        ${relatedOutfits.length > 0 ? `
          <div>
            <div style="font-size:12px; font-weight:500; color:var(--ink-muted); margin-bottom:4px;">${localizedText(lang, 'Kullanıldığı Kombinler', 'Featured in Outfits')}</div>
            <div style="display:flex; flex-direction:column; gap:4px;">
              ${relatedOutfits.map((o) => `<div style="font-size:14px; color:var(--ink);">• ${esc(o.title)}</div>`).join('')}
            </div>
          </div>
        ` : ''}
      </div>
    `,
    onMount: (close, modalEl) => {
      modalEl.querySelector('#idBackBtn')?.addEventListener('click', close);
      const getLatest = () => (appState.items || []).find((x) => x.id === itemId);
      const refresh = () => {
        const cur = getLatest();
        if (!cur) { close(); return; }
        const photos = activePhotoIds(cur);
        pageIdx = Math.min(pageIdx, Math.max(0, photos.length - 1));
        const hasCatalogAtSlot = Boolean(cur.catalogPhotoFileNames?.[pageIdx] && photoMap.get(cur.catalogPhotoFileNames[pageIdx]));
        const isOrig = Boolean(cur.useOriginalPhotos && !cur.useCatalogPhotos);
        const isCatalog = Boolean(cur.useCatalogPhotos && hasCatalogAtSlot);
        const imgEl = modalEl.querySelector('#idMainImg');
        imgEl.src = safeImgSrc(photoMap.get(photos[pageIdx]));
        imgEl.style.objectFit = isOrig ? 'cover' : 'contain';
        imgEl.style.padding = isOrig ? '0' : '14px';
        modalEl.querySelector('#idStage').style.background = isOrig ? 'var(--photo-surface)' : '#fff';
        modalEl.querySelector('#idCounterTxt').textContent = `${pageIdx + 1} / ${Math.max(1, photos.length)}`;

        const hasOrigPairs = (cur.originalPhotoFileNames?.length || 0) === (cur.photoFileNames?.length || 0) && (cur.originalPhotoFileNames?.length || 0) > 0;
        const modeRow = modalEl.querySelector('#idModeRow');
        if (modeRow) modeRow.style.display = hasOrigPairs ? 'flex' : 'none';
        modalEl.querySelector('#idModeStudio').classList.toggle('selected', !isOrig && !isCatalog);
        modalEl.querySelector('#idModeOrig').classList.toggle('selected', isOrig);
        const flatLayBtn = modalEl.querySelector('#idFlatLayBtn');
        if (flatLayBtn) {
          flatLayBtn.classList.toggle('selected', isCatalog);
          if (!flatLayBtn.disabled) {
            flatLayBtn.innerHTML = `${svgIcon('autoAwesome', 16)} ${hasCatalogAtSlot
              ? localizedText(lang, 'Katalog Fotoğrafı Kullan', 'Use Catalog Photo', 'Katalogfoto verwenden', 'Utiliser photo catalogue', 'Usar foto de catálogo', '使用商品图')
              : localizedText(lang, 'Katalog Fotoğrafı Oluştur', 'Create Catalog Photo', 'Katalogfoto erstellen', 'Créer photo catalogue', 'Crear foto de catálogo', '生成商品图')}`;
          }
        }

        const wBtn = modalEl.querySelector('#idWearBtn');
        if (wBtn) wBtn.innerHTML = `<span style="color:var(--accent);">${svgIcon('checkCircle', 15)}</span> ${esc(st.wornBtn)}${cur.wearCount > 0 ? ` (${cur.wearCount})` : ''}`;

        const thumbs = modalEl.querySelector('#idThumbs');
        const photoActions = modalEl.querySelector('#idPhotoActions');
        if (photos.length > 1) {
          thumbs.innerHTML = photos.map((pid, idx) => `
            <button type="button" data-p="${idx}" style="width:68px; height:68px; border-radius:10px; border:${idx === pageIdx ? '2px solid var(--accent)' : '1px solid var(--border)'}; background:${isOrig ? 'var(--photo-surface)' : '#fff'}; padding:4px; flex-shrink:0; cursor:pointer; overflow:hidden;">
              <img src="${safeImgSrc(photoMap.get(pid))}" style="width:100%; height:100%; object-fit:${isOrig ? 'cover' : 'contain'};" />
            </button>
          `).join('');
          thumbs.querySelectorAll('[data-p]').forEach((b) => b.addEventListener('click', () => {
            pageIdx = Number(b.dataset.p);
            refresh();
          }));
        } else {
          thumbs.innerHTML = '';
        }

        if (photos.length > 0) {
          photoActions.style.display = 'flex';
          photoActions.style.flexWrap = 'wrap';
          photoActions.innerHTML = `
            <button type="button" class="btn-secondary btn-sm" id="idEditPhotoBtn">${svgIcon('crop', 16)} ${localizedText(lang, 'Fotoğrafı Düzenle', 'Edit Photo', 'Foto bearbeiten', 'Modifier la photo', 'Editar foto', '编辑照片')}</button>
            ${photos.length > 1 && pageIdx !== 0 ? `<button type="button" class="btn-secondary btn-sm" id="idSetCoverBtn">${svgIcon('starOutline', 16)} ${localizedText(lang, 'Kapak Yap', 'Set Cover')}</button>` : ''}
            ${photos.length > 1 ? `<button type="button" class="btn-danger-outline btn-sm" id="idDelPhotoBtn">${svgIcon('deleteOutline', 16)} ${localizedText(lang, 'Fotoğrafı Sil', 'Delete Photo')}</button>` : ''}
          `;
          photoActions.querySelector('#idEditPhotoBtn')?.addEventListener('click', () => {
            const curItem = getLatest();
            if (!curItem) return;
            const sList = [...(curItem.photoFileNames || [])];
            const oList = [...(curItem.originalPhotoFileNames || [])];
            const cList = [...(curItem.catalogPhotoFileNames || [])];
            const srcId = oList[pageIdx] || sList[pageIdx];
            const srcUrl = srcId ? photoMap.get(srcId) : null;
            if (!srcUrl) {
              showToast(localizedText(lang, 'Bu dosya görsel olarak açılamadı', 'Could not open this image'));
              return;
            }
            openSingleDataUrlCropModal({
              dataUrl: srcUrl,
              lang,
              modalRoot,
              showToast,
              registerModalBack: registerModalBackHandler,
              onComplete: async (croppedUrl) => {
                const latest = getLatest();
                if (!latest) return;
                const nextS = [...(latest.photoFileNames || [])];
                const nextO = latest.originalPhotoFileNames?.length === nextS.length
                  ? [...latest.originalPhotoFileNames]
                  : [...nextS];
                const nextC = [...(latest.catalogPhotoFileNames || [])];
                while (nextC.length < nextS.length) nextC.push('');
                const uid = createId('ph');
                const sId = `bgrm_${uid}`;
                const oId = `orig_${uid}`;
                const sUrl = await toEcommerceFlatLayDataUrl(croppedUrl);
                await savePhotoDataUrl(oId, croppedUrl);
                await savePhotoDataUrl(sId, sUrl);
                photoMap.set(oId, croppedUrl);
                photoMap.set(sId, sUrl);
                const oldS = nextS[pageIdx];
                const oldO = nextO[pageIdx];
                const oldC = nextC[pageIdx];
                nextS[pageIdx] = sId;
                nextO[pageIdx] = oId;
                nextC[pageIdx] = '';
                appState.items = appState.items.map((x) => (
                  x.id === itemId
                    ? { ...x, photoFileNames: nextS, originalPhotoFileNames: nextO, catalogPhotoFileNames: nextC, useCatalogPhotos: false }
                    : x
                ));
                const kept = new Set([...nextS, ...nextO, ...nextC].filter(Boolean));
                [oldS, oldO, oldC].filter((id) => id && !kept.has(id)).forEach((id) => deletePhotoDataUrl(id));
                commitState();
                refresh();
                renderCurrentTab();
                showToast(localizedText(lang, 'Fotoğraf güncellendi', 'Photo updated'));
              }
            });
          });
          photoActions.querySelector('#idSetCoverBtn')?.addEventListener('click', () => {
            const curItem = getLatest();
            if (!curItem || pageIdx <= 0) return;
            const sList = [...(curItem.photoFileNames || [])];
            const oList = [...(curItem.originalPhotoFileNames || [])];
            const cList = [...(curItem.catalogPhotoFileNames || [])];
            while (cList.length < sList.length) cList.push('');
            if (pageIdx < sList.length) sList.unshift(...sList.splice(pageIdx, 1));
            if (pageIdx < oList.length) oList.unshift(...oList.splice(pageIdx, 1));
            if (pageIdx < cList.length) cList.unshift(...cList.splice(pageIdx, 1));
            appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, photoFileNames: sList, originalPhotoFileNames: oList, catalogPhotoFileNames: cList } : x));
            pageIdx = 0;
            commitState(); refresh(); renderCurrentTab();
          });
          photoActions.querySelector('#idDelPhotoBtn')?.addEventListener('click', () => {
            const curItem = getLatest();
            if (!curItem || (curItem.photoFileNames || []).length <= 1) return;
            const sList = [...(curItem.photoFileNames || [])];
            const oList = [...(curItem.originalPhotoFileNames || [])];
            const cList = [...(curItem.catalogPhotoFileNames || [])];
            const removedS = sList.splice(pageIdx, 1)[0];
            const removedO = pageIdx < oList.length ? oList.splice(pageIdx, 1)[0] : null;
            const removedC = pageIdx < cList.length ? cList.splice(pageIdx, 1)[0] : null;
            appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, photoFileNames: sList, originalPhotoFileNames: oList, catalogPhotoFileNames: cList } : x));
            if (removedS) deletePhotoDataUrl(removedS);
            if (removedO) deletePhotoDataUrl(removedO);
            if (removedC) deletePhotoDataUrl(removedC);
            pageIdx = Math.max(0, Math.min(pageIdx, sList.length - 1));
            commitState(); refresh(); renderCurrentTab();
          });
        } else {
          photoActions.style.display = 'none';
          photoActions.innerHTML = '';
        }
      };

      const addMorePhotosToItem = async (mode) => {
        const files = await pickFilesFromInput(mode === 'camera' ? 'cameraInput' : 'galleryInput');
        if (!files.length) return;
        openCropperQueueModal({
          files, lang, modalRoot, showToast,
          registerModalBack: registerModalBackHandler,
          onCompleteAll: async (urls) => {
            const curItem = getLatest();
            if (!curItem) return;
            const nextStudio = [...(curItem.photoFileNames || [])];
            const nextOrig = [...(curItem.originalPhotoFileNames || [])];
            const nextCatalog = [...(curItem.catalogPhotoFileNames || [])];
            while (nextCatalog.length < nextStudio.length) nextCatalog.push('');
            for (const oUrl of urls) {
              const uid = createId('ph');
              const sId = `bgrm_${uid}`;
              const oId = `orig_${uid}`;
              const sUrl = await toEcommerceFlatLayDataUrl(oUrl);
              await savePhotoDataUrl(oId, oUrl);
              await savePhotoDataUrl(sId, sUrl);
              photoMap.set(oId, oUrl);
              photoMap.set(sId, sUrl);
              nextStudio.push(sId);
              nextOrig.push(oId);
              nextCatalog.push('');
            }
            appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, photoFileNames: nextStudio, originalPhotoFileNames: nextOrig, catalogPhotoFileNames: nextCatalog } : x));
            pageIdx = Math.max(0, nextStudio.length - 1);
            commitState(); refresh(); renderCurrentTab();
            showToast(localizedText(lang, 'Fotoğraf eklendi', 'Photo added'));
          }
        });
      };
      modalEl.querySelector('#idAddCamBtn')?.addEventListener('click', () => addMorePhotosToItem('camera'));
      modalEl.querySelector('#idAddGalBtn')?.addEventListener('click', () => addMorePhotosToItem('gallery'));

      modalEl.querySelector('#idStage').addEventListener('click', () => {
        const cur = getLatest();
        const url = cur ? safeImgSrc(photoMap.get(activePhotoIds(cur)[pageIdx])) : '';
        if (!url) return;
        openModal({
          title: cur.name,
          fullscreen: true,
          bodyHtml: `<div style="display:flex; align-items:center; justify-content:center; height:100%;"><img src="${url}" style="max-width:100%; max-height:100%; object-fit:contain;" /></div>`,
          footerHtml: `<button type="button" class="btn-primary" id="fsCloseBtn" style="width:100%;">${esc(st.close)}</button>`,
          onMount: (c, m) => m.querySelector('#fsCloseBtn').addEventListener('click', c)
        });
      });

      modalEl.querySelector('#idModeStudio').addEventListener('click', async () => {
        const cur = getLatest();
        if (!cur) return;
        trackFeature('remove_bg', 'detail');
        const modeBtn = modalEl.querySelector('#idModeStudio');
        const nextStudio = [...(cur.photoFileNames || [])];
        const origList = cur.originalPhotoFileNames || [];
        const indicesToRefresh = [];
        for (let i = 0; i < nextStudio.length; i += 1) {
          const origId = origList[i];
          const studId = nextStudio[i];
          const origUrl = origId ? photoMap.get(origId) : null;
          if (origUrl && (!studId || !String(studId).startsWith('bgrm_') || studId === origId || photoMap.get(studId) === origUrl)) {
            indicesToRefresh.push({ i, origId, origUrl });
          }
        }
        if (indicesToRefresh.length > 0) {
          modeBtn.disabled = true;
          try {
            for (const entry of indicesToRefresh) {
              const cleanedUrl = await toEcommerceFlatLayDataUrl(entry.origUrl);
              const newId = `bgrm_${createId('ph')}`;
              await savePhotoDataUrl(newId, cleanedUrl);
              photoMap.set(newId, cleanedUrl);
              nextStudio[entry.i] = newId;
            }
          } finally {
            modeBtn.disabled = false;
          }
        }
        appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, photoFileNames: nextStudio, useOriginalPhotos: false, useCatalogPhotos: false } : x));
        commitState(); refresh(); renderCurrentTab();
      });
      modalEl.querySelector('#idModeOrig').addEventListener('click', () => {
        appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, useOriginalPhotos: true, useCatalogPhotos: false } : x));
        commitState(); refresh(); renderCurrentTab();
      });

      modalEl.querySelector('#idFlatLayBtn').addEventListener('click', async () => {
        const cur = getLatest();
        if (!cur) return;
        const existingCatalogId = cur.catalogPhotoFileNames?.[pageIdx];
        if (existingCatalogId && photoMap.get(existingCatalogId)) {
          trackFeature('catalog_ai', 'reuse');
          appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, useCatalogPhotos: true, useOriginalPhotos: false } : x));
          commitState(); refresh(); renderCurrentTab();
          return;
        }
        const srcId = cur.originalPhotoFileNames?.[pageIdx] || cur.photoFileNames?.[pageIdx];
        const srcUrl = srcId ? photoMap.get(srcId) : null;
        if (!srcUrl) return;
        trackFeature('catalog_ai', 'generate');
        startFeatureTimer('catalog_ai');
        const btn = modalEl.querySelector('#idFlatLayBtn');
        btn.disabled = true;
        btn.innerHTML = `<span class="spinner"></span> ${localizedText(lang, 'Katalog fotoğrafı oluşturuluyor...', 'Creating catalog photo...', 'Katalogfoto wird erstellt...', 'Création en cours...', 'Creando foto de catálogo...', '正在生成商品图...')}`;
        try {
          const outUrl = await generateAiEcommerceFlatLay(srcUrl, {
            geminiApiKey: await getGeminiApiKey(),
            hfToken: await getHfToken()
          });
          const newId = `catalog_${createId('ph')}`;
          await savePhotoDataUrl(newId, outUrl);
          photoMap.set(newId, outUrl);
          const nextCatalog = [...(cur.catalogPhotoFileNames || [])];
          const targetLen = Math.max(pageIdx + 1, (cur.photoFileNames || []).length);
          while (nextCatalog.length < targetLen) nextCatalog.push('');
          nextCatalog[pageIdx] = newId;
          appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, catalogPhotoFileNames: nextCatalog, useCatalogPhotos: true, useOriginalPhotos: false } : x));
          commitState(); renderCurrentTab();
          showToast(localizedText(lang, 'Katalog fotoğrafı hazır', 'Catalog photo ready', 'Katalogfoto bereit', 'Photo catalogue prête', 'Foto de catálogo lista', '商品图已生成'));
        } catch (err) {
          trackTelemetryError('catalog_ai', err?.message || err?.name || 'error');
          if (err instanceof GeminiApiError) {
            showToast(localizedText(lang, `Gemini API hatası: ${err.message}`, `Gemini API error: ${err.message}`));
          } else if (err instanceof StudioQuotaError) {
            const hasHf = Boolean(await getHfToken());
            showToast(hasHf
              ? localizedText(lang, 'Hugging Face ücretsiz ZeroGPU günlük limiti doldu veya sunucular yoğun. Biraz sonra tekrar dene veya Ayarlar\'dan Gemini anahtarı ekle.', 'Hugging Face free ZeroGPU daily limit reached or servers busy. Try again shortly or add a Gemini key in Settings.')
              : localizedText(lang, 'Ücretsiz AI günlük limiti doldu. Ayarlar\'dan ücretsiz Hugging Face tokenı veya Gemini anahtarı ekleyebilirsin.', 'Free daily AI limit reached. Add a free Hugging Face token or Gemini key in Settings.'));
          } else if (err instanceof StudioBusyError) {
            showToast(localizedText(lang, 'AI katalog sunucuları şu an yoğun. Biraz sonra tekrar dene veya Ayarlar\'dan Gemini anahtarı ekle.', 'Catalog AI servers are currently busy. Try again shortly or add a Gemini key in Settings.'));
          } else {
            showToast(localizedText(lang, 'Katalog fotoğrafı oluşturulamadı. İnternet bağlantını kontrol edip tekrar dene.', 'Could not create catalog photo. Check your connection and try again.'));
          }
        } finally {
          endFeatureTimer('catalog_ai');
          btn.disabled = false;
          refresh();
        }
      });

      const wearBtn = modalEl.querySelector('#idWearBtn');
      if (wearBtn) {
        attachTapAndLongPress(
          wearBtn,
          () => { markItemsWorn([itemId]); refresh(); renderCurrentTab(); },
          () => {
            const cur = getLatest();
            if (cur) openWearCountEditorModal([cur], () => { refresh(); renderCurrentTab(); });
          }
        );
      }

      modalEl.querySelector('#idOpenStore')?.addEventListener('click', () => safeOpenExternal(getLatest()?.productUrl));
      modalEl.querySelector('#idMoveToCloset')?.addEventListener('click', () => {
        appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, isWishlist: false } : x));
        commitState(); showToast(st.movedToWardrobeMsg); close(); renderCurrentTab();
      });
      modalEl.querySelector('#idShareBtn')?.addEventListener('click', () => {
        const cur = getLatest();
        if (cur) openShareItem(cur);
      });
      modalEl.querySelector('#idFavBtn').addEventListener('click', () => {
        toggleFavoriteItem(itemId);
        const fav = !!getLatest()?.isFavorite;
        const favBtn = modalEl.querySelector('#idFavBtn');
        favBtn.innerHTML = svgIcon(fav ? 'favoriteFilled' : 'favoriteBorder', 21);
        favBtn.style.color = fav ? 'var(--accent)' : 'var(--ink)';
      });
      modalEl.querySelector('#idEditBtn').addEventListener('click', () => {
        close({ skipHistoryBack: true });
        openAddEditItemModal({ existingItem: getLatest(), replaceHistory: true });
      });
      modalEl.querySelector('#idDeleteBtn').addEventListener('click', () => {
        if (!confirm(localizedText(lang, 'Bu kıyafet silinsin mi?', 'Delete this piece?'))) return;
        const target = getLatest();
        appState.items = appState.items.filter((x) => x.id !== itemId);
        appState.outfits = (appState.outfits || []).map((o) => ({ ...o, itemIds: o.itemIds.filter((i) => i !== itemId) })).filter((o) => o.itemIds.length > 0);
        commitState();
        (target?.photoFileNames || []).filter(Boolean).forEach(deletePhotoDataUrl);
        (target?.originalPhotoFileNames || []).filter(Boolean).forEach(deletePhotoDataUrl);
        (target?.catalogPhotoFileNames || []).filter(Boolean).forEach(deletePhotoDataUrl);
        close(); renderCurrentTab();
      });
      refresh();
    }
  });
}

function attachTapAndLongPress(el, onTap, onLongPress) {
  let timer = null;
  let firedLong = false;
  el.addEventListener('pointerdown', () => {
    firedLong = false;
    timer = setTimeout(() => { firedLong = true; onLongPress(); }, 460);
  });
  const clear = () => { if (timer) { clearTimeout(timer); timer = null; } };
  el.addEventListener('pointerup', clear);
  el.addEventListener('pointercancel', clear);
  el.addEventListener('contextmenu', (e) => { e.preventDefault(); clear(); firedLong = true; onLongPress(); });
  el.addEventListener('click', () => { if (!firedLong) onTap(); });
}

function openWearCountEditorModal(targetItems, onSaved) {
  const lang = getLang();
  const st = S();
  const counts = new Map(targetItems.map((it) => [it.id, it.wearCount || 0]));
  openModal({
    title: localizedText(lang, 'Giyilme Miktarını Düzenle', 'Edit Wear Count'),
    bodyHtml: `
      <p class="muted-note" style="margin:0;">${localizedText(lang, 'Eksi/Artı tuşlarıyla veya doğrudan değer girerek güncelleyebilirsin.', 'Use - / + buttons or type a number directly.')}</p>
      <div id="wceRows" style="display:flex; flex-direction:column; gap:10px;">
        ${targetItems.map((it) => `
          <div class="card" style="padding:10px; display:flex; align-items:center; justify-content:space-between; gap:8px;">
            <span style="font-size:13px; font-weight:600; flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${esc(it.name || displaySubCategory(it.subCategory, lang))}</span>
            <div style="display:flex; align-items:center; gap:6px;">
              <button type="button" class="btn-secondary btn-sm" data-dec="${esc(it.id)}">−</button>
              <input type="number" min="0" class="input-field" data-num="${esc(it.id)}" value="${it.wearCount || 0}" style="width:68px; text-align:center; padding:6px;" />
              <button type="button" class="btn-secondary btn-sm" data-inc="${esc(it.id)}">+</button>
            </div>
          </div>
        `).join('')}
      </div>
    `,
    footerHtml: `
      <button type="button" class="btn-secondary" id="wceCancel">${esc(st.close)}</button>
      <button type="button" class="btn-primary" id="wceSave">${esc(st.save)}</button>
    `,
    onMount: (close, modalEl) => {
      modalEl.querySelectorAll('[data-dec]').forEach((b) => b.addEventListener('click', () => {
        const id = b.dataset.dec;
        const inp = modalEl.querySelector(`[data-num="${escapeCssAttr(id)}"]`);
        if (!inp) return;
        const next = Math.max(0, (parseInt(inp.value, 10) || 0) - 1);
        inp.value = String(next); counts.set(id, next);
      }));
      modalEl.querySelectorAll('[data-inc]').forEach((b) => b.addEventListener('click', () => {
        const id = b.dataset.inc;
        const inp = modalEl.querySelector(`[data-num="${escapeCssAttr(id)}"]`);
        if (!inp) return;
        const next = (parseInt(inp.value, 10) || 0) + 1;
        inp.value = String(next); counts.set(id, next);
      }));
      modalEl.querySelectorAll('[data-num]').forEach((inp) => inp.addEventListener('input', () => {
        counts.set(inp.dataset.num, Math.max(0, parseInt(inp.value, 10) || 0));
      }));
      modalEl.querySelector('#wceCancel').addEventListener('click', close);
      modalEl.querySelector('#wceSave').addEventListener('click', () => {
        const now = Date.now();
        appState.items = appState.items.map((it) => {
          if (!counts.has(it.id)) return it;
          const v = counts.get(it.id);
          return { ...it, wearCount: v, lastWornAt: v > 0 ? (it.lastWornAt || now) : 0 };
        });
        commitState();
        close();
        onSaved?.();
        showToast(localizedText(lang, 'Giyilme sayısı güncellendi', 'Wear count updated'));
      });
    }
  });
}

function toggleFavoriteItem(id) {
  appState.items = (appState.items || []).map((x) => (x.id === id ? { ...x, isFavorite: !x.isFavorite } : x));
  commitState();
  renderCurrentTab();
}

function markItemsWorn(ids) {
  const idSet = new Set(ids);
  const now = Date.now();
  appState.items = (appState.items || []).map((x) => (idSet.has(x.id) ? { ...x, wearCount: (x.wearCount || 0) + 1, lastWornAt: now } : x));
  commitState();
  showToast(localizedText(getLang(), 'Bugün giyildi olarak işlendi', 'Marked as worn today'));
}

// ==========================================
// TAB 1: KOMBİNLER (OUTFITS & SMART SUITCASE)
// ==========================================
async function loadForecastAsync() {
  const lang = getLang();
  const hasSavedCustom = Boolean(
    appState.customLocationLabel &&
    Number.isFinite(Number(appState.customLocationLat)) &&
    Number.isFinite(Number(appState.customLocationLon))
  );
  const live = hasSavedCustom
    ? await fetchForecastForCoords(appState.customLocationLat, appState.customLocationLon, lang)
    : await (() => {
        const city = TURKISH_CITIES.find((c) => c.id === appState.selectedCityId) || TURKISH_CITIES[0];
        return fetchForecastForCoords(city.lat, city.lon, lang);
      })();
  if (live) {
    forecastDays = live;
    if (currentTab === 1) renderOutfitsTab();
  }
}

function renderOutfitsTab() {
  const st = S();
  const lang = getLang();
  const closet = (appState.items || []).filter((i) => !i.isWishlist);
  const activeCity = TURKISH_CITIES.find((c) => c.id === appState.selectedCityId) || TURKISH_CITIES[0];
  const day = forecastDays[selectedDayIdx] || forecastDays[0];
  const activeCond = manualWeatherOverride || day.condition;
  const condMeta = WEATHER_CONDITIONS[activeCond] || WEATHER_CONDITIONS.COOL;

  const flatSet = new Set(personalColorProfile?.flatteringColors || []);
  currentOutfitSuggestions = closet.length >= 2
    ? suggestOutfits({
        items: closet,
        weather: activeCond,
        targetDayLabel: day.label,
        count: 3,
        lang,
        genderPref: activeGenderPref(),
        flatteringColors: flatSet
      })
    : [];

  const weatherLabel = (w) => lang === 'TR' ? w.tr : lang === 'ZH' ? w.zh : w.en;
  const weatherHint = (w) => {
    if (w.id === 'SUNNY') return localizedText(lang, 'Hafif ve nefes alan kombin', 'Light & breathable look');
    if (w.id === 'MILD') return localizedText(lang, 'Günlük katmansız rahat stil', 'Relaxed everyday style');
    if (w.id === 'COOL') return localizedText(lang, 'İnce üst + ceket katmanı ideal', 'Light layer or jacket works best');
    if (w.id === 'RAINY') return localizedText(lang, 'Dış katman ve koyu tonlar öne çıkar', 'Outerwear & darker tones prioritized');
    return localizedText(lang, 'Kalın triko, kaban ve bot zamanı', 'Warm knits, coat & boots recommended');
  };

  mainContent.innerHTML = `
    <div class="screen-pad">
      <div class="page-header">
        <div style="min-width:0; flex:1;">
          <h2 class="page-title">${esc(st.tabOutfits)}</h2>
          <p class="page-subtitle">${esc(st.outfitsSubtitle)}</p>
        </div>
        <div style="display:flex; align-items:center; gap:8px; flex-shrink:0;">
          <button type="button" class="icon-btn" id="oSettingsBtn" title="${esc(st.settingsTitle)}">${svgIcon('settings', 22)}</button>
        </div>
      </div>
      <div style="display:flex; gap:8px; margin-bottom:10px;">
        <button type="button" class="btn-secondary btn-sm" id="o3DBtn" style="flex:1;"><span style="color:var(--accent);">${svgIcon('accessibilityNew', 17)}</span> ${esc(st.tryOn3D)}</button>
        <button type="button" class="btn-secondary btn-sm" id="oSuitcaseBtn" style="flex:0.9;"><span style="color:var(--accent);">${svgIcon('luggage', 17)}</span> ${localizedText(lang, 'Bavul', 'Pack')}</button>
        <button type="button" class="btn-primary btn-sm" id="oCreateBtn" style="flex:1;">${svgIcon('add', 17)} ${esc(st.create)}</button>
      </div>
    </div>

    <div class="stack">
      <div class="card" style="display:flex; flex-direction:column; gap:12px;">
        <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
          <button type="button" class="weather-detail-trigger" id="oWeatherDetailBtn" title="${localizedText(lang, '24 Saatlik Tahmini Aç', 'Open 24h Forecast')}">
            <span style="font-size:24px; line-height:1;">${condMeta.emoji}</span>
            <div style="min-width:0; flex:1;">
              <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap;">
                <span style="font-size:15px; font-weight:600; color:var(--ink);">${esc(weatherLabel(condMeta))} (${day.maxTempC}°C)</span>
                <span class="chip-pill compact accent" style="font-size:10px; padding:2px 7px;">24 ${localizedText(lang, 'Saat', 'Hr')} ▸</span>
              </div>
              <div class="muted-note" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(weatherHint(condMeta))}</div>
            </div>
          </button>
          <label class="city-select-pill">
            <span style="color:var(--accent);">${svgIcon('locationOn', 14)}</span>
            <select id="oCitySelect" aria-label="Şehir">
              ${appState.customLocationLabel ? `<option value="__CUSTOM__" selected>📍 ${esc(appState.customLocationLabel)}</option>` : ''}
              ${TURKISH_CITIES.map((c) => `<option value="${c.id}" ${!appState.customLocationLabel && c.id === activeCity.id ? 'selected' : ''}>${esc(c.label)}</option>`).join('')}
            </select>
          </label>
        </div>

        <div class="weather-row-mid">
          <button type="button" class="locate-pill" id="oGpsBtn">${svgIcon('myLocation', 14)} ${localizedText(lang, 'Konumum', 'Locate Me')}</button>
          <div class="forecast-strip" id="oForecastStrip">
            ${forecastDays.map((f, idx) => {
              const w = WEATHER_CONDITIONS[f.condition] || WEATHER_CONDITIONS.COOL;
              return `<button type="button" class="forecast-pill ${idx === selectedDayIdx ? 'active' : ''}" data-day="${idx}">${w.emoji} ${esc(f.label)} ${f.maxTempC}°</button>`;
            }).join('')}
          </div>
        </div>

        <div class="chip-row flush" id="oManualWeather">
          ${Object.values(WEATHER_CONDITIONS).map((w) => `
            <button type="button" class="chip-pill ${activeCond === w.id ? 'selected' : ''}" data-wc="${w.id}">${w.emoji} ${esc(weatherLabel(w))}</button>
          `).join('')}
        </div>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
        <div style="min-width:0; flex:1;">
          <div class="section-title" style="margin:0; font-size:19px;">${esc(day.label)} ${esc(st.suggestionsFor)}</div>
          <div class="muted-note">${localizedText(lang, 'Parça değiştirmek için kombin kartına dokun', 'Tap any outfit card to edit pieces & score')}</div>
        </div>
        <div style="display:flex; gap:6px; flex-shrink:0;">
          <button type="button" class="chip-pill compact accent" id="oSurpriseBtn">🎲 ${localizedText(lang, 'Sürpriz', 'Surprise')}</button>
          <button type="button" class="chip-pill compact" id="oShuffleBtn">${svgIcon('refresh', 14)} ${esc(st.suggestDifferent)}</button>
        </div>
      </div>

      ${closet.length < 2 ? `
        <div class="card" style="padding:22px;">
          <h3 style="margin:0 0 6px; font-size:16px;">${esc(st.needTwoItemsTitle)}</h3>
          <p class="muted-note" style="margin:0;">${esc(st.needTwoItemsBody)}</p>
        </div>
      ` : currentOutfitSuggestions.map((sug, sIdx) => `
        <div class="card" data-open-sug="${sIdx}" style="cursor:pointer;">
          <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
            <div style="min-width:0; flex:1;">
              <strong style="font-size:15px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(sug.title)}</strong>
              <div class="muted-note" style="margin-top:2px;">${esc(sug.harmonyNote)}</div>
            </div>
            <div style="display:flex; align-items:center; gap:6px; flex-shrink:0;">
              <button type="button" class="outfit-share-btn" data-share-sug="${sIdx}" aria-label="${localizedText(lang, 'Paylaş', 'Share')}" title="${localizedText(lang, 'Kombini Paylaş', 'Share Outfit')}">${svgIcon('share', 15)}</button>
              ${harmonyBadgeHtml(sug.harmonyScore, lang)}
            </div>
          </div>
          <div class="piece-thumb-row">
            ${sug.items.map((pi) => {
              const u = coverUrlOf(pi);
              return `
                <div class="piece-thumb" data-open-item="${esc(pi.id)}">
                  <div class="piece-thumb-box ${pi.useOriginalPhotos ? 'original-bg' : ''}">
                    ${u ? `<img src="${u}" alt="${esc(pi.name)}" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 24)}</span>`}
                  </div>
                  <span>${esc(pi.name || displaySubCategory(pi.subCategory, lang))}</span>
                </div>
              `;
            }).join('')}
          </div>
          <div class="btn-row">
            <button type="button" class="btn-secondary btn-sm" data-try="${sIdx}">${esc(st.try3DShort)}</button>
            <button type="button" class="btn-secondary btn-sm" data-worn="${sIdx}" title="Basılı tut: miktarı düzenle"><span style="color:var(--olive);">${svgIcon('checkCircle', 14)}</span> ${esc(st.wornBtn)}</button>
            <button type="button" class="btn-primary btn-sm" data-save-sug="${sIdx}">${svgIcon('bookmarkAdd', 15)} ${esc(st.save)}</button>
          </div>
        </div>
      `).join('')}

      ${(appState.outfits || []).length > 0 ? `
        <h3 class="section-title" style="margin:6px 0 0; font-size:19px;">${esc(st.savedOutfitsTitle)} (${appState.outfits.length})</h3>
        ${appState.outfits.map((of) => {
          const ofItems = sortTopToBottom(of.itemIds.map((id) => (appState.items || []).find((x) => x.id === id)).filter(Boolean));
          const liveHarm = calculateColorHarmony(ofItems, lang);
          return `
            <div class="card" data-open-outfit="${esc(of.id)}" style="cursor:pointer;">
              <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                <div style="min-width:0; flex:1;">
                  <strong style="font-size:15px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(of.title)}</strong>
                  <div class="muted-note" style="margin-top:2px;">${esc(liveHarm.note)}</div>
                </div>
                <div style="display:flex; align-items:center; gap:6px; flex-shrink:0;">
                  ${harmonyBadgeHtml(liveHarm.score, lang)}
                  <button type="button" class="outfit-share-btn" data-share-outfit="${esc(of.id)}" aria-label="${localizedText(lang, 'Paylaş', 'Share')}" title="${localizedText(lang, 'Kombini Paylaş', 'Share Outfit')}">${svgIcon('share', 15)}</button>
                  <button type="button" class="btn-icon-ghost" data-del-outfit="${esc(of.id)}" aria-label="${localizedText(lang, 'Sil', 'Delete')}" style="color:var(--danger);">${svgIcon('deleteOutline', 18)}</button>
                </div>
              </div>
              ${ofItems.length > 0 ? `
                <div class="piece-thumb-row">
                  ${ofItems.map((pi) => {
                    const u = coverUrlOf(pi);
                    return `
                      <div class="piece-thumb" data-open-item="${esc(pi.id)}">
                        <div class="piece-thumb-box ${pi.useOriginalPhotos ? 'original-bg' : ''}">
                          ${u ? `<img src="${u}" alt="${esc(pi.name)}" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 24)}</span>`}
                        </div>
                        <span>${esc(pi.name || displaySubCategory(pi.subCategory, lang))}</span>
                      </div>
                    `;
                  }).join('')}
                </div>
              ` : ''}
              <div class="btn-row">
                <button type="button" class="btn-secondary btn-sm" data-saved-try="${esc(of.id)}" ${ofItems.length === 0 ? 'disabled' : ''}>${esc(st.try3DShort)}</button>
                <button type="button" class="btn-primary btn-sm" data-saved-worn="${esc(of.id)}" ${ofItems.length === 0 ? 'disabled' : ''} title="Basılı tut: miktarı düzenle">${esc(st.wornBtn)}</button>
              </div>
            </div>
          `;
        }).join('')}
      ` : ''}
    </div>
  `;

  document.getElementById('oWeatherDetailBtn')?.addEventListener('click', () => {
    openHourlyWeatherModal({
      openModal,
      day,
      locationLabel: appState.customLocationLabel || activeCity.label,
      lang,
      S
    });
  });
  document.getElementById('oSettingsBtn')?.addEventListener('click', openSettingsModal);
  document.getElementById('o3DBtn')?.addEventListener('click', () => {
    trackFeature('tryon_3d', 'outfits');
    openTryOn3DWithItems([]);
  });
  document.getElementById('oSuitcaseBtn')?.addEventListener('click', () => {
    trackFeature('smart_suitcase');
    openSmartSuitcaseModal();
  });
  document.getElementById('oSurpriseBtn')?.addEventListener('click', () => {
    if (closet.length < 2) { showToast(st.needTwoItemsTitle); return; }
    trackFeature('surprise_outfit');
    const pick = suggestOutfits({ items: closet, weather: activeCond, targetDayLabel: localizedText(lang, 'Sürpriz', 'Surprise'), count: 1, lang, genderPref: activeGenderPref(), flatteringColors: flatSet })[0];
    if (pick) openOutfitBuilderModal({ title: pick.title, initialItems: pick.items, existingOutfitId: null });
  });
  document.getElementById('oCreateBtn')?.addEventListener('click', () => {
    if (closet.length === 0) { showToast(st.needTwoItemsTitle); return; }
    trackFeature('outfit_builder');
    openCreateOutfitModal();
  });
  document.getElementById('oCitySelect')?.addEventListener('change', (e) => {
    const val = e.target.value;
    if (val === '__CUSTOM__') return;
    appState.selectedCityId = val;
    appState.customLocationLabel = null;
    appState.customLocationLat = null;
    appState.customLocationLon = null;
    commitState();
    renderOutfitsTab();
    loadForecastAsync();
  });
  document.getElementById('oGpsBtn')?.addEventListener('click', async () => {
    showToast(localizedText(lang, 'Konum hava durumu alınıyor...', 'Fetching location weather...'));
    const res = await detectBrowserLocationAndForecast(lang);
    if (res) {
      appState.customLocationLabel = res.locationLabel;
      appState.customLocationLat = res.lat;
      appState.customLocationLon = res.lon;
      forecastDays = res.days;
      selectedDayIdx = 0;
      manualWeatherOverride = null;
      commitState();
      renderOutfitsTab();
      showToast(res.locationLabel);
    } else {
      showToast(localizedText(lang, 'Konum alınamadı, şehir seçebilirsin', 'Location unavailable'));
    }
  });
  document.querySelectorAll('#oForecastStrip [data-day]').forEach((b) => b.addEventListener('click', () => {
    selectedDayIdx = Number(b.dataset.day);
    manualWeatherOverride = null;
    renderOutfitsTab();
  }));
  document.querySelectorAll('#oManualWeather [data-wc]').forEach((b) => b.addEventListener('click', () => {
    manualWeatherOverride = manualWeatherOverride === b.dataset.wc ? null : b.dataset.wc;
    renderOutfitsTab();
  }));
  document.getElementById('oShuffleBtn')?.addEventListener('click', () => { outfitRefreshNonce++; renderOutfitsTab(); });

  document.querySelectorAll('[data-open-sug]').forEach((el) => el.addEventListener('click', () => {
    const sug = currentOutfitSuggestions[Number(el.dataset.openSug)];
    if (!sug) return;
    openOutfitBuilderModal({ title: sug.title, initialItems: sug.items, existingOutfitId: null });
  }));
  document.querySelectorAll('[data-try]').forEach((b) => b.addEventListener('click', (e) => {
    e.stopPropagation();
    const sug = currentOutfitSuggestions[Number(b.dataset.try)];
    if (sug) openTryOn3DWithItems(sug.items);
  }));
  document.querySelectorAll('[data-worn]').forEach((b) => {
    const sug = currentOutfitSuggestions[Number(b.dataset.worn)];
    if (!sug) return;
    b.addEventListener('click', (e) => e.stopPropagation());
    attachTapAndLongPress(
      b,
      () => markItemsWorn(sug.items.map((x) => x.id)),
      () => openWearCountEditorModal(sug.items, () => renderOutfitsTab())
    );
  });
  document.querySelectorAll('[data-share-sug]').forEach((b) => b.addEventListener('click', (e) => {
    e.stopPropagation();
    const sug = currentOutfitSuggestions[Number(b.dataset.shareSug)];
    if (sug) openShareOutfit(sug.title, sug.items, sug.harmonyScore, sug.harmonyNote, `${condMeta.tr} (${day.maxTempC}°C)`);
  }));
  document.querySelectorAll('[data-save-sug]').forEach((b) => b.addEventListener('click', (e) => {
    e.stopPropagation();
    const sug = currentOutfitSuggestions[Number(b.dataset.saveSug)];
    if (!sug) return;
    appState.outfits = [{
      id: createId('outfit'),
      title: sug.title,
      itemIds: sug.items.map((i) => i.id),
      season: 'ALL',
      harmonyScore: sug.harmonyScore,
      harmonyNote: sug.harmonyNote,
      weatherTag: condMeta.tr,
      notes: sug.weatherReason,
      createdAt: Date.now()
    }, ...(appState.outfits || [])];
    commitState();
    showToast(localizedText(lang, 'Kombin kaydedildi', 'Outfit saved'));
    renderOutfitsTab();
  }));
  document.querySelectorAll('[data-open-item]').forEach((el) => el.addEventListener('click', (e) => {
    e.stopPropagation();
    openItemDetailModal(el.dataset.openItem);
  }));
  document.querySelectorAll('[data-open-outfit]').forEach((el) => el.addEventListener('click', () => {
    const of = (appState.outfits || []).find((x) => x.id === el.dataset.openOutfit);
    if (!of) return;
    const ofItems = of.itemIds.map((id) => (appState.items || []).find((i) => i.id === id)).filter(Boolean);
    if (ofItems.length > 0) {
      openOutfitBuilderModal({ title: of.title, initialItems: ofItems, existingOutfitId: of.id });
    }
  }));
  document.querySelectorAll('[data-share-outfit]').forEach((b) => b.addEventListener('click', (e) => {
    e.stopPropagation();
    const of = (appState.outfits || []).find((x) => x.id === b.dataset.shareOutfit);
    if (!of) return;
    const ofItems = sortTopToBottom(of.itemIds.map((id) => (appState.items || []).find((i) => i.id === id)).filter(Boolean));
    if (ofItems.length > 0) {
      const liveHarm = calculateColorHarmony(ofItems, lang);
      openShareOutfit(of.title, ofItems, liveHarm.score, liveHarm.note, of.weatherTag || '');
    } else {
      showToast(localizedText(lang, 'Bu kombini oluşturan parçalar artık dolabında değil', 'The pieces in this outfit are no longer in your wardrobe'));
    }
  }));
  document.querySelectorAll('[data-del-outfit]').forEach((b) => b.addEventListener('click', (e) => {
    e.stopPropagation();
    const id = b.dataset.delOutfit;
    appState.outfits = (appState.outfits || []).filter((o) => o.id !== id);
    commitState();
    renderOutfitsTab();
  }));
  document.querySelectorAll('[data-saved-try]').forEach((b) => b.addEventListener('click', (e) => {
    e.stopPropagation();
    const of = (appState.outfits || []).find((x) => x.id === b.dataset.savedTry);
    if (!of) return;
    const ofItems = sortTopToBottom(of.itemIds.map((id) => (appState.items || []).find((i) => i.id === id)).filter(Boolean));
    if (ofItems.length > 0) openTryOn3DWithItems(ofItems);
  }));
  document.querySelectorAll('[data-saved-worn]').forEach((b) => {
    const of = (appState.outfits || []).find((x) => x.id === b.dataset.savedWorn);
    if (!of) return;
    const ofItems = sortTopToBottom(of.itemIds.map((id) => (appState.items || []).find((i) => i.id === id)).filter(Boolean));
    if (ofItems.length === 0) return;
    b.addEventListener('click', (e) => e.stopPropagation());
    attachTapAndLongPress(
      b,
      () => markItemsWorn(ofItems.map((x) => x.id)),
      () => openWearCountEditorModal(ofItems, () => renderOutfitsTab())
    );
  });
}

function openCreateOutfitModal() {
  const lang = getLang();
  const st = S();
  const closet = (appState.items || []).filter((i) => !i.isWishlist);
  const selectedIds = new Set();

  openModal({
    title: localizedText(lang, 'Yeni Kombin Oluştur', 'Create Custom Outfit'),
    headerRightHtml: `<span id="coHeaderBadge"></span>`,
    fullscreen: true,
    bodyHtml: `
      <div class="outlined-field-box">
        <span class="outlined-field-label">${localizedText(lang, 'Kombin Adı', 'Outfit Name')}</span>
        <input type="text" class="outlined-field-input" id="coTitleInput" value="${localizedText(lang, 'Özel Kombinim', 'Custom Outfit')}" />
      </div>
      <div id="coSub" class="muted-note"></div>
      <div id="coList" style="display:flex; flex-direction:column; gap:8px;"></div>
    `,
    footerHtml: `
      <button type="button" class="btn-secondary" id="coTryBtn" disabled style="flex:1;">${esc(st.tryOn3D)}</button>
      <button type="button" class="btn-primary" id="coSaveBtn" disabled style="flex:1;">${esc(st.saveOutfit)}</button>
    `,
    onMount: (close, modalEl) => {
      const renderCreator = () => {
        const picked = sortTopToBottom(closet.filter((it) => selectedIds.has(it.id)));
        const harm = calculateColorHarmony(picked, lang);
        const badgeEl = modalEl.querySelector('#coHeaderBadge');
        badgeEl.innerHTML = picked.length > 0 ? harmonyBadgeHtml(harm.score, lang) : '';

        modalEl.querySelector('#coSub').textContent = picked.length === 0
          ? localizedText(lang, 'Aşağıdan en az 2 parça seç:', 'Select at least 2 garments below:')
          : `${harm.note} · ${picked.length} ${localizedText(lang, 'seçili', 'selected')}`;

        modalEl.querySelector('#coTryBtn').disabled = picked.length === 0;
        modalEl.querySelector('#coSaveBtn').disabled = picked.length < 2;

        const listEl = modalEl.querySelector('#coList');
        listEl.innerHTML = closet.map((piece) => {
          const isPicked = selectedIds.has(piece.id);
          const u = coverUrlOf(piece);
          return `
            <button type="button" class="co-item-row ${isPicked ? 'selected' : ''}" data-co-id="${esc(piece.id)}">
              <div class="co-item-thumb ${piece.useOriginalPhotos ? 'original-bg' : ''}">
                ${u ? `<img src="${u}" alt="${esc(piece.name)}" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 20)}</span>`}
              </div>
              <div style="flex:1; min-width:0;">
                <strong style="font-size:14px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(piece.name || displaySubCategory(piece.subCategory, lang))}</strong>
                <span class="muted-note">${esc(displaySubCategory(piece.subCategory, lang))}</span>
              </div>
              ${isPicked ? `<span style="color:var(--accent); flex-shrink:0;">${svgIcon('checkCircle', 22)}</span>` : ''}
            </button>
          `;
        }).join('');

        listEl.querySelectorAll('[data-co-id]').forEach((row) => row.addEventListener('click', () => {
          const id = row.dataset.coId;
          if (selectedIds.has(id)) selectedIds.delete(id);
          else selectedIds.add(id);
          renderCreator();
        }));
      };

      modalEl.querySelector('#coTryBtn').addEventListener('click', () => {
        const picked = sortTopToBottom(closet.filter((it) => selectedIds.has(it.id)));
        if (picked.length === 0) return;
        close();
        openTryOn3DWithItems(picked);
      });

      modalEl.querySelector('#coSaveBtn').addEventListener('click', () => {
        const picked = sortTopToBottom(closet.filter((it) => selectedIds.has(it.id)));
        if (picked.length < 2) return;
        const harm = calculateColorHarmony(picked, lang);
        const rawName = modalEl.querySelector('#coTitleInput').value.trim();
        const finalName = rawName || localizedText(lang, 'Özel Kombinim', 'Custom Outfit');
        appState.outfits = [{
          id: createId('outfit'),
          title: finalName,
          itemIds: picked.map((p) => p.id),
          season: 'ALL',
          harmonyScore: harm.score,
          harmonyNote: harm.note,
          weatherTag: '',
          notes: '',
          createdAt: Date.now()
        }, ...(appState.outfits || [])];
        commitState();
        close();
        renderOutfitsTab();
        showToast(localizedText(lang, 'Kombin kaydedildi', 'Outfit saved'));
      });

      renderCreator();
    }
  });
}

function openOutfitBuilderModal({ title, initialItems, existingOutfitId }) {
  const lang = getLang();
  const st = S();
  const closet = (appState.items || []).filter((i) => !i.isWishlist);
  let chosen = [...initialItems];
  let outfitTitle = title;

  const openSwapPieceModal = (target, onSwapped) => {
    let showAllCats = false;
    openModal({
      title: `${st.swapPiece}: ${target.name || displaySubCategory(target.subCategory, lang)}`,
      bodyHtml: `
        <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
          <span class="muted-note">${localizedText(lang, 'Yeni uyum puanıyla seç', 'Tap an alternative to preview harmony')}</span>
          <button type="button" class="chip-pill compact" id="swToggleAll">${localizedText(lang, 'Tüm Türler', 'All Types')}</button>
        </div>
        <div id="swList" style="display:flex; flex-direction:column; gap:8px;"></div>
      `,
      onMount: (swClose, swEl) => {
        const renderCandidates = () => {
          const activeSet = new Set(chosen.map((c) => c.id));
          const list = closet.filter((it) => it.id !== target.id && !activeSet.has(it.id) && (showAllCats || it.category === target.category));
          swEl.querySelector('#swToggleAll').classList.toggle('selected', showAllCats);
          swEl.querySelector('#swToggleAll').textContent = showAllCats
            ? displayCategory(target.category, lang)
            : localizedText(lang, 'Tüm Türler', 'All Types');
          const box = swEl.querySelector('#swList');
          if (list.length === 0) {
            box.innerHTML = `<p class="muted-note" style="margin:8px 0;">${localizedText(lang, "Bu kategoride başka parça bulunamadı. 'Tüm Türler' tuşuna basabilirsin.", "No other pieces in this category. Tap 'All Types'.")}</p>`;
            return;
          }
          box.innerHTML = list.map((cand) => {
            const nextList = chosen.map((x) => (x.id === target.id ? cand : x));
            const sim = calculateColorHarmony(nextList, lang);
            const u = coverUrlOf(cand);
            return `
              <button type="button" class="co-item-row" data-sw-id="${esc(cand.id)}">
                <div class="co-item-thumb ${cand.useOriginalPhotos ? 'original-bg' : ''}">
                  ${u ? `<img src="${u}" alt="${esc(cand.name)}" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 20)}</span>`}
                </div>
                <div style="flex:1; min-width:0;">
                  <strong style="font-size:14px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(cand.name || displaySubCategory(cand.subCategory, lang))}</strong>
                  <span class="muted-note">${esc(displayCategory(cand.category, lang))} · ${esc(displaySubCategory(cand.subCategory, lang))}</span>
                </div>
                ${harmonyBadgeHtml(sim.score, lang)}
              </button>
            `;
          }).join('');
          box.querySelectorAll('[data-sw-id]').forEach((row) => row.addEventListener('click', () => {
            const picked = closet.find((x) => x.id === row.dataset.swId);
            if (picked) {
              chosen = chosen.map((x) => (x.id === target.id ? picked : x));
              swClose();
              onSwapped();
            }
          }));
        };
        swEl.querySelector('#swToggleAll').addEventListener('click', () => {
          showAllCats = !showAllCats;
          renderCandidates();
        });
        renderCandidates();
      }
    });
  };

  const openAddPieceModal = (onAdded) => {
    let filterCat = null;
    openModal({
      title: st.addPiece,
      bodyHtml: `
        <div class="chip-row flush" id="apCats"></div>
        <div id="apList" style="display:flex; flex-direction:column; gap:8px;"></div>
      `,
      onMount: (apClose, apEl) => {
        const renderAddable = () => {
          apEl.querySelector('#apCats').innerHTML = `
            <button type="button" class="chip-pill compact ${filterCat === null ? 'selected' : ''}" data-apcat="">${esc(st.all)}</button>
            ${CATEGORIES.map((c) => `<button type="button" class="chip-pill compact ${filterCat === c.id ? 'selected' : ''}" data-apcat="${esc(c.id)}">${esc(displayCategory(c.id, lang))}</button>`).join('')}
          `;
          apEl.querySelectorAll('[data-apcat]').forEach((b) => b.addEventListener('click', () => {
            const nextCat = b.dataset.apcat || null;
            filterCat = filterCat === nextCat ? null : nextCat;
            renderAddable();
          }));

          const activeSet = new Set(chosen.map((c) => c.id));
          const list = closet.filter((it) => !activeSet.has(it.id) && (filterCat === null || it.category === filterCat));
          const box = apEl.querySelector('#apList');
          if (list.length === 0) {
            box.innerHTML = `<p class="muted-note" style="margin:8px 0;">${esc(st.noFilterMatch)}</p>`;
            return;
          }
          box.innerHTML = list.map((cand) => {
            const sim = calculateColorHarmony([...chosen, cand], lang);
            const u = coverUrlOf(cand);
            return `
              <button type="button" class="co-item-row" data-ap-id="${esc(cand.id)}">
                <div class="co-item-thumb ${cand.useOriginalPhotos ? 'original-bg' : ''}">
                  ${u ? `<img src="${u}" alt="${esc(cand.name)}" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 20)}</span>`}
                </div>
                <div style="flex:1; min-width:0;">
                  <strong style="font-size:14px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(cand.name || displaySubCategory(cand.subCategory, lang))}</strong>
                  <span class="muted-note">${esc(displayCategory(cand.category, lang))} · ${esc(displaySubCategory(cand.subCategory, lang))}</span>
                </div>
                ${harmonyBadgeHtml(sim.score, lang)}
              </button>
            `;
          }).join('');
          box.querySelectorAll('[data-ap-id]').forEach((row) => row.addEventListener('click', () => {
            const picked = closet.find((x) => x.id === row.dataset.apId);
            if (picked) {
              chosen.push(picked);
              apClose();
              onAdded();
            }
          }));
        };
        renderAddable();
      }
    });
  };

  openModal({
    title: st.outfitDetailTitle,
    headerLeftHtml: `<button type="button" class="btn-icon-ghost" id="obBackBtn" aria-label="${localizedText(lang, 'Geri', 'Back')}">${svgIcon('arrowBack', 20)}</button>`,
    headerRightHtml: `<span id="obHeaderBadge"></span>`,
    hideCloseX: true,
    fullscreen: true,
    bodyHtml: `
      <div class="card" style="display:flex; flex-direction:column; gap:12px;">
        <div class="outlined-field-box">
          <span class="outlined-field-label">${localizedText(lang, 'Kombin Başlığı', 'Outfit Name')}</span>
          <input type="text" class="outlined-field-input" id="obTitleInput" value="${esc(outfitTitle)}" />
        </div>
        <div id="obHarmonySummary" style="display:flex; justify-content:space-between; align-items:center; gap:10px;"></div>
      </div>
      <div id="obLayersList" style="display:flex; flex-direction:column; gap:12px;"></div>
      <button type="button" class="btn-accent-dashed" id="obAddBtn">${svgIcon('add', 18)} ${esc(st.addPiece)}</button>
    `,
    footerHtml: `
      ${existingOutfitId ? `<button type="button" class="btn-danger-outline btn-sm" id="obDeleteBtn" aria-label="Sil">${svgIcon('deleteOutline', 17)}</button>` : ''}
      <button type="button" class="outfit-share-btn" id="obShareBtn" aria-label="${localizedText(lang, 'Paylaş', 'Share')}" title="${localizedText(lang, 'Kombini Paylaş', 'Share Outfit')}">${svgIcon('share', 16)}</button>
      <button type="button" class="btn-secondary btn-sm" id="obTryBtn" style="flex:0.85;">${svgIcon('accessibilityNew', 16)} ${esc(st.try3DShort)}</button>
      <button type="button" class="btn-secondary btn-sm" id="obWornBtn" style="flex:0.85;" title="Basılı tut: miktarı düzenle"><span style="color:var(--olive);">${svgIcon('checkCircle', 15)}</span> ${esc(st.wornBtn)}</button>
      <button type="button" class="btn-primary btn-sm" id="obSaveBtn" style="flex:1.1;">${svgIcon('bookmarkAdd', 16)} ${existingOutfitId ? localizedText(lang, 'Güncelle', 'Update') : esc(st.save)}</button>
    `,
    onMount: (close, modalEl) => {
      modalEl.querySelector('#obBackBtn').addEventListener('click', close);

      const renderBuilder = () => {
        chosen = sortTopToBottom(chosen);
        const harm = calculateColorHarmony(chosen, lang);
        modalEl.querySelector('#obHeaderBadge').innerHTML = harmonyBadgeHtml(harm.score, lang);
        const colorDots = chosen.map((c) => c.colors?.[0]).filter(Boolean)
          .map((cid) => `<span class="color-dot" style="background:${sanitizeHexColor(getColorHex(cid))}; width:13px; height:13px;"></span>`).join('');
        modalEl.querySelector('#obHarmonySummary').innerHTML = `
          <div style="min-width:0; flex:1;">
            <div style="font-size:16px; font-weight:600; color:var(--ink);">${esc(harm.note)}</div>
            <div class="muted-note" style="font-size:11px; margin-top:2px;">${chosen.length} ${localizedText(lang, 'parça', 'pieces')} · ${esc(st.topToBottomHint)}</div>
          </div>
          <div style="display:flex; gap:5px; flex-shrink:0;">${colorDots}</div>
        `;

        const listEl = modalEl.querySelector('#obLayersList');
        listEl.innerHTML = chosen.map((it, idx) => {
          const u = coverUrlOf(it);
          const itemDots = (it.colors || []).slice(0, 2)
            .map((cid) => `<span class="color-dot" style="background:${sanitizeHexColor(getColorHex(cid))}; width:10px; height:10px;"></span>`).join('');
          return `
            <div class="ob-piece-card">
              <div class="ob-piece-thumb ${it.useOriginalPhotos ? 'original-bg' : ''}" data-open="${esc(it.id)}">
                ${u ? `<img src="${u}" alt="${esc(it.name)}" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 28)}</span>`}
              </div>
              <div style="flex:1; min-width:0; display:flex; flex-direction:column; gap:6px;">
                <div><span class="cat-badge">${esc(displayCategory(it.category, lang))}</span></div>
                <strong data-open="${esc(it.id)}" style="font-size:16px; cursor:pointer; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(it.name || displaySubCategory(it.subCategory, lang))}</strong>
                <div style="display:flex; align-items:center; gap:6px;">
                  ${itemDots}
                  <span class="muted-note">${esc(displaySubCategory(it.subCategory, lang))}</span>
                </div>
                <div style="display:flex; gap:8px; margin-top:2px;">
                  <button type="button" class="btn-secondary btn-sm" data-sw="${idx}">${svgIcon('swapHoriz', 15)} ${esc(st.swapPiece)}</button>
                  ${chosen.length > 1 ? `<button type="button" class="btn-danger-outline btn-sm" data-rm="${idx}">${svgIcon('deleteOutline', 15)} ${esc(st.removePiece)}</button>` : ''}
                </div>
              </div>
            </div>
          `;
        }).join('');

        listEl.querySelectorAll('[data-open]').forEach((x) => x.addEventListener('click', () => openItemDetailModal(x.dataset.open)));
        listEl.querySelectorAll('[data-sw]').forEach((b) => b.addEventListener('click', () => {
          const target = chosen[Number(b.dataset.sw)];
          if (target) openSwapPieceModal(target, renderBuilder);
        }));
        listEl.querySelectorAll('[data-rm]').forEach((b) => b.addEventListener('click', () => {
          chosen.splice(Number(b.dataset.rm), 1); renderBuilder();
        }));
      };

      modalEl.querySelector('#obAddBtn').addEventListener('click', () => openAddPieceModal(renderBuilder));
      modalEl.querySelector('#obTryBtn').addEventListener('click', () => { if (chosen.length > 0) openTryOn3DWithItems(chosen); });
      attachTapAndLongPress(
        modalEl.querySelector('#obWornBtn'),
        () => { if (chosen.length > 0) markItemsWorn(chosen.map((c) => c.id)); },
        () => { if (chosen.length > 0) openWearCountEditorModal(chosen, () => renderOutfitsTab()); }
      );
      modalEl.querySelector('#obDeleteBtn')?.addEventListener('click', () => {
        appState.outfits = (appState.outfits || []).filter((o) => o.id !== existingOutfitId);
        commitState(); close(); renderOutfitsTab();
      });
      modalEl.querySelector('#obShareBtn')?.addEventListener('click', () => {
        if (chosen.length === 0) return;
        const t = modalEl.querySelector('#obTitleInput').value.trim() || st.outfitDetailTitle;
        const harm = calculateColorHarmony(chosen, lang);
        openShareOutfit(t, chosen, harm.score, harm.note, '');
      });
      modalEl.querySelector('#obSaveBtn').addEventListener('click', () => {
        if (chosen.length === 0) return;
        const t = modalEl.querySelector('#obTitleInput').value.trim() || st.outfitDetailTitle;
        const harm = calculateColorHarmony(chosen, lang);
        const obj = {
          id: existingOutfitId || createId('outfit'),
          title: t,
          itemIds: chosen.map((c) => c.id),
          season: 'ALL',
          harmonyScore: harm.score,
          harmonyNote: harm.note,
          weatherTag: '',
          notes: '',
          createdAt: Date.now()
        };
        if (existingOutfitId) {
          appState.outfits = appState.outfits.map((o) => (o.id === existingOutfitId ? obj : o));
        } else {
          appState.outfits = [obj, ...(appState.outfits || [])];
        }
        commitState(); close(); renderOutfitsTab();
        showToast(localizedText(lang, 'Kombin kaydedildi', 'Outfit saved'));
      });
      renderBuilder();
    }
  });
}

function openSmartSuitcaseModal() {
  const lang = getLang();
  const st = S();
  const closet = (appState.items || []).filter((i) => !i.isWishlist);
  const activeCity = TURKISH_CITIES.find((c) => c.id === appState.selectedCityId) || TURKISH_CITIES[0];
  let selectedDestLabel = appState.customLocationLabel || activeCity.label;
  let destForecast = forecastDays && forecastDays.length > 0 ? [...forecastDays] : generateFallbackForecast(lang);
  let searchResults = [];
  let searchNoMatch = false;
  let isLoadingDest = false;
  let tripDays = 4;
  let selectedTab = 0; // 0 = Günlük Kombinler, 1 = Bavul Listesi
  const packedIds = new Set();

  const quickDestinations = [
    { name: 'İstanbul', country: 'Türkiye', lat: 41.0082, lon: 28.9784 },
    { name: 'Antalya', country: 'Türkiye', lat: 36.8969, lon: 30.7133 },
    { name: 'İzmir', country: 'Türkiye', lat: 38.4192, lon: 27.1287 },
    { name: 'Kapadokya', country: 'Türkiye', lat: 38.6244, lon: 34.7239 },
    { name: 'Ankara', country: 'Türkiye', lat: 39.9334, lon: 32.8597 },
    { name: 'Trabzon', country: 'Türkiye', lat: 41.0027, lon: 39.7168 },
    { name: 'Paris', country: 'France', lat: 48.8566, lon: 2.3522 },
    { name: 'London', country: 'UK', lat: 51.5072, lon: -0.1276 },
    { name: 'Berlin', country: 'Germany', lat: 52.5200, lon: 13.4050 },
    { name: 'Rome', country: 'Italy', lat: 41.9028, lon: 12.4964 },
    { name: 'Barcelona', country: 'Spain', lat: 41.3874, lon: 2.1686 }
  ];

  openModal({
    title: localizedText(lang, 'Akıllı Bavul Hazırlayıcı', 'Smart Suitcase Planner'),
    subtitle: '...',
    fullscreen: true,
    bodyHtml: `
      <div style="display:flex; gap:8px; align-items:center;">
        <input type="text" class="input-field" id="scCityInput" placeholder="${localizedText(lang, 'Nereye gidiyorsun? (ör. Antalya, Paris)', 'Where are you going? (e.g. Paris, Antalya)')}" style="flex:1;" />
        <button type="button" class="sc-search-btn" id="scSearchBtn" aria-label="${localizedText(lang, 'Ara', 'Search')}">${svgIcon('search', 18)}</button>
      </div>
      <div id="scLoading" class="progress-track hidden"><div class="progress-fill" style="width:60%;"></div></div>
      <div id="scNoMatch" class="muted-note hidden" style="color:var(--danger);">${localizedText(lang, 'Şehir bulunamadı. Yakın bir il veya popüler şehir yazabilirsin.', 'City not found. Try a larger city nearby.')}</div>
      <div class="chip-row flush" id="scDestRow"></div>
      <div class="chip-row flush" id="scDaysRow"></div>
      <div class="chip-row flush" id="scForecastRow"></div>
      <div class="btn-row" id="scTabsRow"></div>
      <div id="scOutput" style="display:flex; flex-direction:column; gap:10px;"></div>
    `,
    onMount: (close, modalEl) => {
      const renderAll = () => {
        const plan = generateTravelPlan({
          items: closet,
          destinationLabel: selectedDestLabel,
          forecasts: destForecast,
          tripDays,
          lang,
          genderPref: activeGenderPref(),
          flatteringColors: new Set(personalColorProfile?.flatteringColors || [])
        });

        const subEl = modalEl.querySelector('.modal-subtitle');
        if (subEl) {
          subEl.textContent = localizedText(
            lang,
            `${plan.destinationLabel} · ${plan.packingChecklist.length} otomatik parça · ${plan.dayPlans.length} günlük kombin`,
            `${plan.destinationLabel} · ${plan.packingChecklist.length} pieces · ${plan.dayPlans.length} day looks`
          );
        }

        modalEl.querySelector('#scLoading').classList.toggle('hidden', !isLoadingDest);
        modalEl.querySelector('#scNoMatch').classList.toggle('hidden', !searchNoMatch);

        const destRow = modalEl.querySelector('#scDestRow');
        if (searchResults.length > 0) {
          destRow.innerHTML = searchResults.map((p, i) => `
            <button type="button" class="chip-pill compact ${selectedDestLabel === p.displayLabel ? 'selected' : ''}" data-sr="${i}">📍 ${esc(p.displayLabel)}</button>
          `).join('');
          destRow.querySelectorAll('[data-sr]').forEach((b) => b.addEventListener('click', () => selectPlace(searchResults[Number(b.dataset.sr)])));
        } else {
          destRow.innerHTML = quickDestinations.map((p, i) => `
            <button type="button" class="chip-pill compact ${selectedDestLabel.toLowerCase().startsWith(p.name.toLowerCase()) ? 'selected' : ''}" data-qd="${i}">${esc(p.name)}</button>
          `).join('');
          destRow.querySelectorAll('[data-qd]').forEach((b) => b.addEventListener('click', () => {
            const p = quickDestinations[Number(b.dataset.qd)];
            selectPlace({ ...p, displayLabel: p.name });
          }));
        }

        modalEl.querySelector('#scDaysRow').innerHTML = [2, 3, 4, 5, 7].map((d) => `
          <button type="button" class="chip-pill compact ${tripDays === d ? 'selected' : ''}" data-dy="${d}">${d} ${localizedText(lang, 'Gün', 'Days')}</button>
        `).join('');
        modalEl.querySelectorAll('#scDaysRow [data-dy]').forEach((b) => b.addEventListener('click', () => {
          tripDays = Number(b.dataset.dy);
          renderAll();
        }));

        const slice = Array.from({ length: tripDays }, (_, idx) => destForecast[idx] || destForecast[destForecast.length - 1]);
        modalEl.querySelector('#scForecastRow').innerHTML = slice.map((d, idx) => {
          const w = WEATHER_CONDITIONS[d.condition] || WEATHER_CONDITIONS.COOL;
          return `<span class="sc-forecast-chip">${localizedText(lang, 'G', 'D')}${idx + 1} ${w.emoji} ${d.maxTempC}°</span>`;
        }).join('');

        modalEl.querySelector('#scTabsRow').innerHTML = `
          <button type="button" class="chip-pill ${selectedTab === 0 ? 'selected' : ''}" id="scTabLooks" style="justify-content:center;">🗓️ ${localizedText(lang, `Günlük Kombinler (${plan.dayPlans.length})`, `Daily Looks (${plan.dayPlans.length})`)}</button>
          <button type="button" class="chip-pill ${selectedTab === 1 ? 'selected' : ''}" id="scTabPack" style="justify-content:center;">🧳 ${localizedText(lang, `Bavul Listesi (${plan.packingChecklist.length})`, `Suitcase (${plan.packingChecklist.length})`)}</button>
        `;
        modalEl.querySelector('#scTabLooks').addEventListener('click', () => { selectedTab = 0; renderAll(); });
        modalEl.querySelector('#scTabPack').addEventListener('click', () => { selectedTab = 1; renderAll(); });

        const out = modalEl.querySelector('#scOutput');
        if (closet.length < 2 || plan.packingChecklist.length === 0) {
          out.innerHTML = `<p class="muted-note" style="padding:12px 0;">${esc(st.needTwoItemsBody)}</p>`;
          return;
        }

        if (selectedTab === 0) {
          out.innerHTML = plan.dayPlans.map((dp, dpIdx) => {
            const w = WEATHER_CONDITIONS[dp.forecast.condition] || WEATHER_CONDITIONS.COOL;
            return `
              <div class="card" data-sc-day="${dpIdx}" style="background:var(--secondary); cursor:pointer;">
                <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                  <div style="min-width:0; flex:1;">
                    <strong style="font-size:14px;">${localizedText(lang, `${dp.dayNumber}. Gün`, `Day ${dp.dayNumber}`)} · ${w.emoji} ${dp.forecast.maxTempC}°C / ${dp.forecast.minTempC}°C</strong>
                    <div class="muted-note" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(dp.outfit.harmonyNote)}</div>
                  </div>
                  ${harmonyBadgeHtml(dp.outfit.harmonyScore, lang)}
                </div>
                <div class="piece-thumb-row">
                  ${dp.outfit.items.map((pi) => {
                    const u = coverUrlOf(pi);
                    return `
                      <div class="piece-thumb" data-sc-item="${esc(pi.id)}">
                        <div class="piece-thumb-box ${pi.useOriginalPhotos ? 'original-bg' : ''}">
                          ${u ? `<img src="${u}" alt="${esc(pi.name)}" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 24)}</span>`}
                        </div>
                        <span>${esc(pi.name || displaySubCategory(pi.subCategory, lang))}</span>
                      </div>
                    `;
                  }).join('')}
                </div>
                <div style="display:flex; gap:8px;">
                  <button type="button" class="btn-secondary sc-day-pill-btn" data-sc-3d="${dpIdx}">${esc(st.tryOn3D)}</button>
                  <button type="button" class="btn-secondary sc-day-pill-btn accent" data-sc-save="${dpIdx}">${esc(st.saveOutfit)}</button>
                </div>
              </div>
            `;
          }).join('');

          out.querySelectorAll('[data-sc-day]').forEach((card) => card.addEventListener('click', () => {
            const dp = plan.dayPlans[Number(card.dataset.scDay)];
            if (dp) openOutfitBuilderModal({ title: dp.outfit.title, initialItems: dp.outfit.items, existingOutfitId: null });
          }));
          out.querySelectorAll('[data-sc-item]').forEach((el) => el.addEventListener('click', (e) => {
            e.stopPropagation();
            openItemDetailModal(el.dataset.scItem);
          }));
          out.querySelectorAll('[data-sc-3d]').forEach((b) => b.addEventListener('click', (e) => {
            e.stopPropagation();
            const dp = plan.dayPlans[Number(b.dataset.sc3d)];
            if (dp) openTryOn3DWithItems(dp.outfit.items);
          }));
          out.querySelectorAll('[data-sc-save]').forEach((b) => b.addEventListener('click', (e) => {
            e.stopPropagation();
            const dp = plan.dayPlans[Number(b.dataset.scSave)];
            if (!dp) return;
            appState.outfits = [{
              id: createId('outfit'),
              title: dp.outfit.title,
              itemIds: dp.outfit.items.map((x) => x.id),
              season: 'ALL',
              harmonyScore: dp.outfit.harmonyScore,
              harmonyNote: dp.outfit.harmonyNote,
              weatherTag: selectedDestLabel,
              notes: '',
              createdAt: Date.now()
            }, ...(appState.outfits || [])];
            commitState();
            showToast(localizedText(lang, 'Kombin kaydedildi', 'Outfit saved'));
            renderOutfitsTab();
          }));
        } else {
          const itemDays = new Map();
          plan.dayPlans.forEach((dp) => dp.outfit.items.forEach((p) => {
            const arr = itemDays.get(p.id) || [];
            arr.push(dp.dayNumber);
            itemDays.set(p.id, arr);
          }));
          const checkedCount = plan.packingChecklist.filter((p) => packedIds.has(p.id)).length;
          const totalCount = plan.packingChecklist.length;

          out.innerHTML = `
            <div style="display:flex; flex-direction:column; gap:8px;">
              ${plan.packingChecklist.map((piece) => {
                const isChecked = packedIds.has(piece.id);
                const used = itemDays.get(piece.id) || [];
                const u = coverUrlOf(piece);
                const sub = used.length > 0
                  ? `${displayCategory(piece.category, lang)} · ${localizedText(lang, 'Günler', 'Days')}: ${used.join(',')}`
                  : displayCategory(piece.category, lang);
                return `
                  <label class="sc-checklist-row">
                    <div class="co-item-thumb ${piece.useOriginalPhotos ? 'original-bg' : ''}" style="width:44px; height:44px;">
                      ${u ? `<img src="${u}" alt="${esc(piece.name)}" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 18)}</span>`}
                    </div>
                    <div style="flex:1; min-width:0;">
                      <strong style="font-size:13px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(piece.name || displaySubCategory(piece.subCategory, lang))}</strong>
                      <span class="muted-note">${esc(sub)}</span>
                    </div>
                    <input type="checkbox" data-pk="${esc(piece.id)}" ${isChecked ? 'checked' : ''} />
                  </label>
                `;
              }).join('')}
            </div>
            <div style="display:flex; justify-content:space-between; align-items:center; padding-top:4px;">
              <span class="muted-note">${checkedCount} / ${totalCount} ${localizedText(lang, 'bavula konuldu', 'packed')}</span>
              <button type="button" class="chip-pill compact accent" id="scToggleAllPack">${checkedCount === totalCount ? localizedText(lang, 'Temizle', 'Clear All') : localizedText(lang, 'Tümünü İşaretle', 'Check All')}</button>
            </div>
          `;
          out.querySelectorAll('[data-pk]').forEach((cb) => cb.addEventListener('change', () => {
            if (cb.checked) packedIds.add(cb.dataset.pk);
            else packedIds.delete(cb.dataset.pk);
            renderAll();
          }));
          out.querySelector('#scToggleAllPack')?.addEventListener('click', () => {
            if (checkedCount === totalCount) packedIds.clear();
            else plan.packingChecklist.forEach((p) => packedIds.add(p.id));
            renderAll();
          });
        }
      };

      const selectPlace = async (place) => {
        selectedDestLabel = place.displayLabel || place.name;
        searchResults = [];
        searchNoMatch = false;
        isLoadingDest = true;
        renderAll();
        const fetched = await fetchForecastForCoords(place.lat, place.lon, lang);
        if (fetched && fetched.length > 0) destForecast = fetched;
        isLoadingDest = false;
        renderAll();
      };

      const runSearch = async () => {
        const clean = modalEl.querySelector('#scCityInput').value.trim();
        if (clean.length < 2) return;
        isLoadingDest = true;
        renderAll();
        const found = await searchDestinations(clean, lang);
        searchResults = found.map((f) => ({ ...f, displayLabel: f.country ? `${f.name}, ${f.country}` : f.name }));
        searchNoMatch = searchResults.length === 0;
        if (searchResults.length === 1) {
          await selectPlace(searchResults[0]);
        } else {
          isLoadingDest = false;
          renderAll();
        }
      };

      modalEl.querySelector('#scSearchBtn').addEventListener('click', runSearch);
      modalEl.querySelector('#scCityInput').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') { e.preventDefault(); runSearch(); }
      });
      modalEl.querySelector('#scCityInput').addEventListener('input', (e) => {
        if (!e.target.value.trim()) {
          searchResults = [];
          searchNoMatch = false;
          renderAll();
        }
      });

      renderAll();
    }
  });
}

function openTryOn3DWithItems(itemsList) {
  const triggerBodyPick = (mode, afterSave) => {
    pickFilesFromInput(mode === 'camera' ? 'bodyCameraInput' : 'bodyGalleryInput').then(async (files) => {
      const file = files[0];
      if (!file) return;
      try {
        const url = await fileToOrientedDataUrl(file, 1440);
        await savePhotoDataUrl('user_body_photo', url);
        photoMap.set('user_body_photo', url);
        afterSave?.();
      } catch {
        const l = getLang();
        showToast(localizedText(l, 'Bu dosya görsel olarak açılamadı', 'Could not open this image'));
      }
    });
  };
  openVirtualTryOn3DModal({
    initialItems: itemsList,
    allWardrobeItems: (appState.items || []).filter((i) => !i.isWishlist),
    lang: getLang(),
    modalRoot,
    showToast,
    registerModalBack: registerModalBackHandler,
    onPickBodyGallery: (cb) => triggerBodyPick('gallery', cb),
    onPickBodyCamera: (cb) => triggerBodyPick('camera', cb),
    onOpenSettings: openSettingsModal
  });
}

// ==========================================
// TAB 2: KOMBİN İLHAMI (LOOKBOOK + FACE/SKIN + SWIPE)
// ==========================================
function renderInspirationTab() {
  const st = S();
  const lang = getLang();
  const looksPool = lookGender === 'WOMEN' ? shuffledWomenLooks : shuffledMenLooks;
  const filtered = lookCategory === 'ALL' ? looksPool : looksPool.filter((l) => l.category === lookCategory);
  const closet = (appState.items || []).filter((i) => !i.isWishlist);
  const faceUrl = safeImgSrc(photoMap.get('user_face_photo'));

  mainContent.innerHTML = `
    <div class="screen-pad">
      <div class="page-header">
        <div style="min-width:0; flex:1;">
          <h2 class="page-title">${localizedText(lang, 'Kombin İlhamı', 'Street Lookbook', 'Streetstyle-Lookbook', 'Lookbook Street', 'Lookbook Urbano', '穿搭灵感')}</h2>
          <p class="page-subtitle">${esc(st.inspirationSubtitle)} · ${filtered.length}</p>
        </div>
        <div style="display:flex; align-items:center; gap:8px; flex-shrink:0;">
          <button type="button" class="icon-btn" id="lSettingsBtn" title="${esc(st.settingsTitle)}">${svgIcon('settings', 22)}</button>
        </div>
      </div>

      <div class="seg-toggle" style="margin-bottom:10px;">
        <button type="button" class="seg-btn ${lookGender === 'MEN' ? 'active' : ''}" id="lMenBtn">${svgIcon('male', 16)} ${localizedText(lang, 'Erkek', 'Men')} (${VERIFIED_MEN_LOOKS.length})</button>
        <button type="button" class="seg-btn ${lookGender === 'WOMEN' ? 'active' : ''}" id="lWomenBtn">${svgIcon('female', 16)} ${localizedText(lang, 'Kadın', 'Women')} (${VERIFIED_WOMEN_LOOKS.length})</button>
      </div>

      <button type="button" class="discovery-bar" id="lSwipeDiscoveryBtn" style="margin-bottom:10px;">
        <span class="discovery-bar-left">${svgIcon('autoAwesome', 18)} ${localizedText(lang, 'Stil Keşfi · Kaydır & Beğen', 'Style Discovery · Swipe Looks', 'Style Discovery · Wischen', 'Découverte Style · Swiper', 'Descubre Estilo · Desliza', '风格发现 · 滑动挑选')}</span>
        <span class="discovery-bar-right">
          <span style="color:var(--ink);">❤️ ${(appState.likedLookIds || []).length}</span>
          <span style="color:var(--ink-muted);">✕ ${(appState.dislikedLookIds || []).length}</span>
        </span>
      </button>
    </div>

    <div class="chip-row" id="lCatChips">
      ${INSPIRATION_CATEGORIES.map((c) => `
        <button type="button" class="chip-pill ${lookCategory === c.id ? 'selected' : ''}" data-lc="${esc(c.id)}">${esc(localizedText(lang, c.tr, c.en, c.de, c.fr, c.es, c.zh))}</button>
      `).join('')}
    </div>

    <!-- Independent Face & Skin Tone Analysis Card + Single-Column Street Looks matching InspirationScreen.kt -->
    <div class="look-list" id="lLookGrid">
      <div class="card" style="display:flex; flex-direction:column; gap:10px;">
        <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
          <div style="display:flex; align-items:center; gap:8px; min-width:0; flex:1;">
            <span style="color:var(--accent);">${svgIcon('face', 20)}</span>
            <strong style="font-size:15px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${localizedText(lang, 'Yüz & Ten Rengi Uyum Analizi', 'Face & Skin Tone Color Match', 'Gesichts- & Hautton-Farbanalyse', 'Analyse teint & couleurs', 'Análisis de rostro y tono', '面容与肤色色彩匹配')}</strong>
          </div>
          <div style="display:flex; align-items:center; gap:6px; flex-shrink:0;">
            ${faceUrl ? `<img src="${faceUrl}" alt="Yüz" class="face-thumb" />` : ''}
            ${personalColorProfile ? `<span class="skin-swatch" style="background:${sanitizeHexColor(personalColorProfile.avgSkinHex)};"></span>` : ''}
          </div>
        </div>

        ${personalColorProfile ? `
          <div class="accent-note" style="font-size:13px;">${esc(personalColorProfile.seasonTitle)} · ${esc(personalColorProfile.undertoneLabel)}</div>
          <p class="muted-note" style="margin:0; color:var(--ink);">${esc(personalColorProfile.summaryText)}</p>
          <div style="font-size:12px; font-weight:500; color:var(--ink-muted);">${localizedText(lang, 'Sana En Çok Yakışacak Renkler (dokun → ara):', 'Most Flattering Colors (tap → search):')}</div>
          <div class="swatch-grid" id="lSkinColors">
            ${personalColorProfile.flatteringColors.map((cid) => `
              <button type="button" class="swatch-chip" data-scol="${esc(cid)}">
                <span class="color-dot" style="background:${sanitizeHexColor(getColorHex(cid))};"></span>${esc(displayColor(cid, lang))}
              </button>
            `).join('')}
          </div>
          ${closet.length > 0 ? `<div class="muted-note">${localizedText(lang, `Dolabındaki ${closet.filter((it) => it.colors?.some((c) => personalColorProfile.flatteringColors.includes(c))).length} parça ten tonunla yüksek uyumlu.`, `${closet.filter((it) => it.colors?.some((c) => personalColorProfile.flatteringColors.includes(c))).length} pieces in your closet match your skin tone.`)}</div>` : ''}
        ` : `<div class="muted-note">${localizedText(lang, 'Kendi fotoğrafını veya boy fotoğrafını yüklediğinde yüzünü ve ten alt tonunu analiz edip sana en çok yakışacak renk paletini otomatik çıkarır.', 'Upload a portrait or full-body photo to analyze your face & skin tone and discover your most flattering color palette.')}</div>`}

        <div class="btn-row">
          <button type="button" class="btn-secondary btn-sm" id="lFaceGalBtn">${svgIcon('photoLibrary', 15)} ${personalColorProfile ? localizedText(lang, 'Değiştir', 'Change', 'Ändern', 'Changer', 'Cambiar', '更换照片') : localizedText(lang, 'Galeri', 'Gallery', 'Galerie', 'Galerie', 'Galería', '从相册选')}</button>
          <button type="button" class="btn-secondary btn-sm" id="lFaceCamBtn">${svgIcon('camera', 15)} ${localizedText(lang, 'Kamera', 'Camera', 'Kamera', 'Caméra', 'Cámara', '拍照')}</button>
        </div>
      </div>

      ${filtered.map((look) => {
        const lTitle = lang === 'TR' ? look.trTitle : look.enTitle;
        const lPal = lang === 'TR' ? look.trPalette : look.enPalette;
        const pieces = lang === 'TR' ? look.trPieces : look.enPieces;
        const catObj = INSPIRATION_CATEGORIES.find((c) => c.id === look.category);
        return `
          <article class="look-card" data-look-id="${esc(look.id)}">
            <div class="look-img-box">
              <img src="${safeLookbookImgSrc(look.imageUrl)}" alt="${esc(lTitle)}" loading="lazy" />
              <span class="look-badge-tl">${esc(localizedText(lang, catObj?.tr, catObj?.en, catObj?.de, catObj?.fr, catObj?.es, catObj?.zh))}</span>
              <span class="look-badge-tr">${harmonyBadgeHtml(look.harmonyPercent, lang)}</span>
            </div>
            <div class="look-body">
              <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                <div class="garment-title" style="white-space:normal; font-size:16px; line-height:1.3; flex:1;">${esc(lTitle)}</div>
                <div class="dot-row">
                  ${look.colorDots.map((hex) => `<span class="color-dot" style="width:12px; height:12px; background:${sanitizeHexColor(hex)};"></span>`).join('')}
                </div>
              </div>
              <div class="accent-note" style="font-weight:500;">${esc(lPal)}</div>
              <div style="display:flex; flex-direction:column; gap:4px;">
                ${pieces.map((p) => `<div class="muted-note" style="display:flex; align-items:center; gap:6px;">${svgIcon('autoAwesome', 12)} <span>${esc(p)}</span></div>`).join('')}
              </div>
            </div>
          </article>
        `;
      }).join('')}
    </div>
  `;

  document.getElementById('lSettingsBtn')?.addEventListener('click', openSettingsModal);
  document.getElementById('lMenBtn')?.addEventListener('click', () => { setLookGender('MEN'); renderInspirationTab(); });
  document.getElementById('lWomenBtn')?.addEventListener('click', () => { setLookGender('WOMEN'); renderInspirationTab(); });
  document.getElementById('lSwipeDiscoveryBtn')?.addEventListener('click', openSwipeDiscoveryModal);
  document.querySelectorAll('#lCatChips [data-lc]').forEach((b) => b.addEventListener('click', () => {
    lookCategory = b.dataset.lc; renderInspirationTab();
  }));

  const pickFace = (source) => {
    pickFilesFromInput(source === 'camera' ? 'faceCameraInput' : 'faceGalleryInput').then(async (files) => {
      const f = files[0];
      if (!f) return;
      try {
        const url = await fileToOrientedDataUrl(f, 960);
        await savePhotoDataUrl('user_face_photo', url);
        photoMap.set('user_face_photo', url);
        personalColorProfile = await analyzeFaceSkinTone(url, getLang());
        renderInspirationTab();
        showToast(localizedText(getLang(), 'Yüz & ten rengi analiz edildi', 'Face & skin tone analyzed'));
      } catch {
        const l = getLang();
        showToast(localizedText(l, 'Bu dosya görsel olarak açılamadı', 'Could not open this image'));
      }
    });
  };
  document.getElementById('lFaceGalBtn')?.addEventListener('click', () => pickFace('gallery'));
  document.getElementById('lFaceCamBtn')?.addEventListener('click', () => pickFace('camera'));

  document.querySelectorAll('#lSkinColors [data-scol]').forEach((b) => b.addEventListener('click', () => {
    const cid = b.dataset.scol;
    const genderPrefix = lookGender === 'WOMEN' ? (lang === 'TR' ? 'kadın' : 'women') : (lang === 'TR' ? 'erkek' : 'men');
    const colLabel = displayColor(cid, lang);
    openStoreSearchDialog(`${colLabel} ${localizedText(lang, 'Kombin Önerileri', 'Outfit Picks')}`, genderPrefix, colLabel);
  }));

  document.querySelectorAll('#lLookGrid [data-look-id]').forEach((c) => c.addEventListener('click', () => {
    const look = looksPool.find((x) => x.id === c.dataset.lookId);
    if (look) openLookDetailModal(look);
  }));
}

function openStoreSearchDialog(title, genderPrefix, rawQuery) {
  const lang = getLang();
  const st = S();
  const links = buildStoreSearchLinks(genderPrefix, rawQuery, 'Google Alışveriş');
  openModal({
    title,
    bodyHtml: `
      <p class="muted-note" style="margin:0;">${localizedText(lang, 'Mağazalarda Benzer Ürün Ara:', 'Search in Stores:')}</p>
      <div class="wrap-row" id="ssdLinks">
        ${links.map(([lbl, u]) => `<button type="button" class="chip-pill selected" data-url="${esc(u)}">${svgIcon('search', 14)} ${esc(lbl)}</button>`).join('')}
      </div>
    `,
    footerHtml: `<button type="button" class="btn-primary" id="ssdClose">${esc(st.close)}</button>`,
    onMount: (close, modalEl) => {
      modalEl.querySelectorAll('[data-url]').forEach((b) => b.addEventListener('click', () => safeOpenExternal(b.dataset.url)));
      modalEl.querySelector('#ssdClose').addEventListener('click', close);
    }
  });
}

function openLookDetailModal(look) {
  const lang = getLang();
  const lTitle = lang === 'TR' ? look.trTitle : look.enTitle;
  const lPal = lang === 'TR' ? look.trPalette : look.enPalette;
  const pieces = lang === 'TR' ? look.trPieces : look.enPieces;
  const genderPrefix = lookGender === 'WOMEN' ? (lang === 'TR' ? 'kadın' : 'women') : (lang === 'TR' ? 'erkek' : 'men');
  let selectedPieceIdx = 0;

  openModal({
    title: lTitle,
    fullscreen: true,
    bodyHtml: `
      <div style="position:relative; border-radius:var(--card-radius); overflow:hidden; height:290px; background:var(--secondary);">
        <img src="${safeLookbookImgSrc(look.imageUrl)}" alt="${esc(lTitle)}" style="width:100%; height:100%; object-fit:cover;" />
        <span class="look-badge-tr" style="top:12px; left:12px; right:auto;">${harmonyBadgeHtml(look.harmonyPercent, lang)}</span>
      </div>
      <div class="accent-note">${esc(lPal)}</div>
      <div class="section-title" style="margin:4px 0 0;">${localizedText(lang, 'Kombin Parçaları & Benzer Ürün Ara', 'Pieces & Find Similar Products')}</div>
      <div id="ldPieces" style="display:flex; flex-direction:column; gap:8px;"></div>
      <div class="section-title" id="ldStoreHeader" style="margin:6px 0 0;"></div>
      <div class="wrap-row" id="ldStoreLinks"></div>
    `,
    footerHtml: `<button type="button" class="btn-primary" id="ldCloseBtn">${esc(S().close)}</button>`,
    onMount: (close, modalEl) => {
      const renderPieces = () => {
        const pEl = modalEl.querySelector('#ldPieces');
        pEl.innerHTML = pieces.map((piece, idx) => `
          <div class="card" data-sel="${idx}" style="padding:10px; display:flex; align-items:center; justify-content:space-between; gap:8px; cursor:pointer; background:${idx === selectedPieceIdx ? 'var(--accent-soft)' : 'var(--secondary)'}; border-color:${idx === selectedPieceIdx ? 'var(--accent)' : 'var(--border)'};">
            <span style="font-size:12px; font-weight:600; flex:1;">${esc(piece)}</span>
            <button type="button" class="chip-pill compact" data-goog="${idx}">Google</button>
            <button type="button" class="chip-pill compact accent" data-trd="${idx}">Trendyol</button>
          </div>
        `).join('');

        pEl.querySelectorAll('[data-sel]').forEach((row) => row.addEventListener('click', (e) => {
          if (e.target.dataset.goog != null || e.target.dataset.trd != null) return;
          selectedPieceIdx = Number(row.dataset.sel);
          renderPieces();
        }));
        pEl.querySelectorAll('[data-goog]').forEach((b) => b.addEventListener('click', () => {
          safeOpenExternal(googleShoppingUrl(genderPrefix, pieces[Number(b.dataset.goog)]));
        }));
        pEl.querySelectorAll('[data-trd]').forEach((b) => b.addEventListener('click', () => {
          safeOpenExternal(trendyolUrl(genderPrefix, pieces[Number(b.dataset.trd)]));
        }));

        const activePiece = pieces[selectedPieceIdx] || pieces[0] || lTitle;
        modalEl.querySelector('#ldStoreHeader').textContent = localizedText(lang, `Mağazalarda Benzer Ürün Ara (${activePiece})`, `Search Stores (${activePiece})`);
        const sEl = modalEl.querySelector('#ldStoreLinks');
        sEl.innerHTML = buildStoreSearchLinks(genderPrefix, activePiece, 'Google Alışveriş').map(([lbl, u]) => `
          <button type="button" class="chip-pill selected" data-st="${esc(u)}">${esc(lbl)}</button>
        `).join('');
        sEl.querySelectorAll('[data-st]').forEach((b) => b.addEventListener('click', () => safeOpenExternal(b.dataset.st)));
      };

      modalEl.querySelector('#ldCloseBtn').addEventListener('click', close);
      renderPieces();
    }
  });
}

function openSwipeDiscoveryModal() {
  const lang = getLang();
  let subTab = 'DECK'; // DECK | LIKED | DISLIKED
  const undoStack = [];

  openModal({
    title: '',
    hideCloseX: true,
    fullscreen: true,
    onClose: () => renderInspirationTab(),
    bodyHtml: `<div id="swModalMount" style="display:none;"></div>`,
    onMount: (close, modalEl) => {
      const panel = modalEl.querySelector('.modal-panel');
      if (panel) {
        panel.className = 'modal-panel discovery-modal-panel';
        panel.innerHTML = `
          <div class="discovery-modal-shell">
            <div class="discovery-modal-header">
              <div style="min-width:0; flex:1;">
                <h3 class="discovery-modal-title">${localizedText(lang, '✨ Stil Keşfi', '✨ Style Discovery', '✨ Style Discovery', '✨ Découverte Style', '✨ Descubre Estilo', '✨ 风格发现')}</h3>
                <div class="discovery-modal-sub">${localizedText(lang, 'Sağa kaydır beğen ❤️ · Sola kaydır geç ✕', 'Swipe right to like ❤️ · Left to pass ✕', 'Rechts wischen: Like ❤️ · Links: Nein ✕', "Droite : J'aime ❤️ · Gauche : Passer ✕", 'Derecha: Me gusta ❤️ · Izquierda: Pasar ✕', '右滑喜欢 ❤️ · 左滑跳过 ✕')}</div>
              </div>
              <button type="button" class="btn-icon-ghost" id="swClose" aria-label="Kapat">${svgIcon('close', 22)}</button>
            </div>
            <div class="chip-row flush" id="swGenderRow">
              <button type="button" class="chip-pill ${lookGender === 'MEN' ? 'selected' : ''}" data-g="MEN">${localizedText(lang, 'Erkek', 'Men')}</button>
              <button type="button" class="chip-pill ${lookGender === 'WOMEN' ? 'selected' : ''}" data-g="WOMEN">${localizedText(lang, 'Kadın', 'Women')}</button>
            </div>
            <div class="chip-row flush" id="swTabs">
              <button type="button" class="chip-pill selected" data-st="DECK" id="swDeckTabBtn">${localizedText(lang, 'Keşfet', 'Discover')} (0)</button>
              <button type="button" class="chip-pill" data-st="LIKED">❤️ ${localizedText(lang, 'Beğendiklerim', 'Liked', 'Favoriten', 'Aimés', 'Me gusta', '喜欢')} (<span id="swLikeCnt">0</span>)</button>
              <button type="button" class="chip-pill" data-st="DISLIKED">✕ ${localizedText(lang, 'Geçtiklerim', 'Passed', 'Übersprungen', 'Passés', 'Pasados', '跳过')} (<span id="swDisCnt">0</span>)</button>
            </div>
            <div class="discovery-modal-body" id="swBody"></div>
          </div>
        `;
      }

      const activePool = () => (lookGender === 'WOMEN' ? shuffledWomenLooks : shuffledMenLooks);

      const recordSwipe = (lookId, liked, pushUndo = true) => {
        const likes = new Set(appState.likedLookIds || []);
        const dislikes = new Set(appState.dislikedLookIds || []);
        if (pushUndo && liked !== null) {
          undoStack.push({ id: lookId, prevLiked: likes.has(lookId) ? true : (dislikes.has(lookId) ? false : null) });
        }
        likes.delete(lookId);
        dislikes.delete(lookId);
        if (liked === true) likes.add(lookId);
        else if (liked === false) dislikes.add(lookId);
        appState.likedLookIds = [...likes];
        appState.dislikedLookIds = [...dislikes];
        commitState();
        renderBody();
      };

      const renderBody = () => {
        const pool = activePool();
        const likes = new Set(appState.likedLookIds || []);
        const dislikes = new Set(appState.dislikedLookIds || []);
        const remaining = pool.filter((l) => !likes.has(l.id) && !dislikes.has(l.id));

        const deckBtn = modalEl.querySelector('#swDeckTabBtn');
        if (deckBtn) deckBtn.textContent = `${localizedText(lang, 'Keşfet', 'Discover', 'Entdecken', 'Découvrir', 'Explorar', '发现')} (${remaining.length})`;
        modalEl.querySelector('#swLikeCnt').textContent = String(likes.size);
        modalEl.querySelector('#swDisCnt').textContent = String(dislikes.size);
        modalEl.querySelectorAll('#swGenderRow [data-g]').forEach((b) => b.classList.toggle('selected', b.dataset.g === lookGender));
        modalEl.querySelectorAll('#swTabs [data-st]').forEach((b) => b.classList.toggle('selected', b.dataset.st === subTab));

        const body = modalEl.querySelector('#swBody');
        if (subTab === 'DECK') {
          if (remaining.length === 0) {
            body.innerHTML = `
              <div class="card" style="flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; text-align:center; padding:26px; gap:12px;">
                <span style="color:var(--accent);">${svgIcon('autoAwesome', 36)}</span>
                <strong style="font-size:17px;">${localizedText(lang, 'Bu kategorideki tüm kombinleri keşfettin!', "You've swiped through every look here!")}</strong>
                <span class="muted-note">${localizedText(lang, 'Beğendiklerini görebilir ya da listeyi sıfırlayıp yeniden keşfedebilirsin.', 'View your liked outfits or reset to discover again.')}</span>
                <div class="btn-row" style="width:auto;">
                  <button type="button" class="btn-secondary btn-sm" id="swGoLikedBtn">❤️ ${likes.size}</button>
                  <button type="button" class="btn-primary btn-sm" id="swResetDeck">${svgIcon('refresh', 15)} ${localizedText(lang, 'Yeniden Karıştır', 'Shuffle Again')}</button>
                </div>
              </div>
            `;
            body.querySelector('#swGoLikedBtn')?.addEventListener('click', () => { subTab = 'LIKED'; renderBody(); });
            body.querySelector('#swResetDeck')?.addEventListener('click', () => {
              const ids = new Set(pool.map((x) => x.id));
              appState.likedLookIds = (appState.likedLookIds || []).filter((id) => !ids.has(id));
              appState.dislikedLookIds = (appState.dislikedLookIds || []).filter((id) => !ids.has(id));
              if (lookGender === 'WOMEN') shuffledWomenLooks = shuffleArray(VERIFIED_WOMEN_LOOKS);
              else shuffledMenLooks = shuffleArray(VERIFIED_MEN_LOOKS);
              undoStack.length = 0;
              commitState();
              renderBody();
            });
            return;
          }

          const topLook = remaining[0];
          const lTitle = lang === 'TR' ? topLook.trTitle : topLook.enTitle;
          const lPal = lang === 'TR' ? topLook.trPalette : topLook.enPalette;
          const catObj = INSPIRATION_CATEGORIES.find((c) => c.id === topLook.category);
          const catLabel = catObj ? localizedText(lang, catObj.tr, catObj.en, catObj.de, catObj.fr, catObj.es, catObj.zh) : topLook.category;

          body.innerHTML = `
            <div class="swipe-stage">
              <div class="swipe-card" id="swCard">
                <img src="${safeLookbookImgSrc(topLook.imageUrl)}" alt="${esc(lTitle)}" />
                <span class="swipe-stamp like" id="swStampLike">${localizedText(lang, 'BEĞENDİM ❤️', 'LIKED ❤️')}</span>
                <span class="swipe-stamp nope" id="swStampNope">${localizedText(lang, 'GEÇ ✕', 'PASS ✕')}</span>
                <div class="swipe-overlay-card">
                  <div class="swipe-overlay-top">
                    <span class="swipe-overlay-cat">${esc(catLabel)}</span>
                    ${harmonyBadgeHtml(topLook.harmonyPercent, lang)}
                  </div>
                  <h4 class="swipe-overlay-title">${esc(lTitle)}</h4>
                  <span class="swipe-overlay-pal">${esc(lPal)}</span>
                </div>
              </div>
            </div>
            <div class="discovery-actions-bar">
              <button type="button" class="btn-swipe-pass" id="swDislikeBtn">✕ ${localizedText(lang, 'Geç', 'Pass', 'Nein', 'Passer', 'Pasar', '跳过')}</button>
              <button type="button" class="btn-swipe-undo" id="swUndoBtn" ${undoStack.length === 0 ? 'disabled' : ''} aria-label="${localizedText(lang, 'Geri Al', 'Undo')}">${svgIcon('refresh', 20)}</button>
              <button type="button" class="btn-swipe-like" id="swLikeBtn">❤️ ${localizedText(lang, 'Beğen', 'Like', 'Like', "J'aime", 'Gustar', '喜欢')}</button>
            </div>
          `;

          const cardEl = body.querySelector('#swCard');
          let startX = 0, startY = 0, curDx = 0, movedPx = 0, dragging = false;
          cardEl.addEventListener('pointerdown', (e) => {
            cardEl.setPointerCapture(e.pointerId);
            startX = e.clientX;
            startY = e.clientY;
            curDx = 0;
            movedPx = 0;
            dragging = true;
            cardEl.style.transition = 'none';
          });
          cardEl.addEventListener('pointermove', (e) => {
            if (!dragging) return;
            curDx = e.clientX - startX;
            const curDy = e.clientY - startY;
            movedPx = Math.max(movedPx, Math.hypot(curDx, curDy));
            cardEl.style.transform = `translateX(${curDx}px) rotate(${Math.max(-12, Math.min(12, curDx * 0.04))}deg)`;
            body.querySelector('#swStampLike').style.opacity = String(Math.min(1, Math.max(0, curDx / 90)));
            body.querySelector('#swStampNope').style.opacity = String(Math.min(1, Math.max(0, -curDx / 90)));
          });
          const endSwipe = (e) => {
            if (!dragging) return;
            dragging = false;
            cardEl.style.transition = 'transform 0.16s ease';
            if (curDx > 95) {
              recordSwipe(topLook.id, true);
            } else if (curDx < -95) {
              recordSwipe(topLook.id, false);
            } else {
              cardEl.style.transform = '';
              body.querySelector('#swStampLike').style.opacity = '0';
              body.querySelector('#swStampNope').style.opacity = '0';
              if (e?.type === 'pointerup' && movedPx < 8) {
                openLookDetailModal(topLook);
              }
            }
            curDx = 0;
          };
          cardEl.addEventListener('pointerup', endSwipe);
          cardEl.addEventListener('pointercancel', endSwipe);

          body.querySelector('#swDislikeBtn').addEventListener('click', () => recordSwipe(topLook.id, false));
          body.querySelector('#swLikeBtn').addEventListener('click', () => recordSwipe(topLook.id, true));
          body.querySelector('#swUndoBtn').addEventListener('click', () => {
            const last = undoStack.pop();
            if (last) recordSwipe(last.id, last.prevLiked, false);
          });
        } else {
          const allLooks = [...VERIFIED_MEN_LOOKS, ...VERIFIED_WOMEN_LOOKS];
          const targetIds = subTab === 'LIKED' ? likes : dislikes;
          const list = allLooks.filter((l) => targetIds.has(l.id));
          if (list.length === 0) {
            body.innerHTML = `<div class="card"><p class="muted-note" style="margin:0; text-align:center;">${localizedText(lang, 'Henüz kombin yok.', 'No items here yet.')}</p></div>`;
            return;
          }
          body.innerHTML = `
            <div class="discovery-scroll-list">
              ${list.map((l) => `
                <div class="card" style="padding:10px; display:flex; align-items:center; gap:10px;">
                  <img src="${safeLookbookImgSrc(l.imageUrl)}" data-ins="${esc(l.id)}" style="width:60px; height:76px; border-radius:10px; object-fit:cover; cursor:pointer;" />
                  <div style="flex:1; min-width:0; cursor:pointer;" data-ins="${esc(l.id)}">
                    <strong style="font-size:13px; display:block;">${esc(lang === 'TR' ? l.trTitle : l.enTitle)}</strong>
                    <span class="muted-note">${esc(lang === 'TR' ? l.trPalette : l.enPalette)}</span>
                  </div>
                  <button type="button" class="chip-pill compact accent" data-mv="${esc(l.id)}">${subTab === 'LIKED' ? '✕' : '❤️'}</button>
                </div>
              `).join('')}
            </div>
          `;
          body.querySelectorAll('[data-ins]').forEach((el) => el.addEventListener('click', () => {
            const found = allLooks.find((x) => x.id === el.dataset.ins);
            if (found) openLookDetailModal(found);
          }));
          body.querySelectorAll('[data-mv]').forEach((b) => b.addEventListener('click', () => {
            recordSwipe(b.dataset.mv, subTab !== 'LIKED', false);
          }));
        }
      };

      modalEl.querySelectorAll('#swGenderRow [data-g]').forEach((b) => b.addEventListener('click', () => {
        setLookGender(b.dataset.g);
        renderBody();
      }));
      modalEl.querySelectorAll('#swTabs [data-st]').forEach((b) => b.addEventListener('click', () => {
        subTab = b.dataset.st;
        renderBody();
      }));
      modalEl.querySelector('#swClose').addEventListener('click', () => close());
      renderBody();
    }
  });
}

// ==========================================
// TAB 3: DOLAP ÖZETİ (SUMMARY & GAP ANALYSIS)
// ==========================================
function renderSummaryTab() {
  const st = S();
  const lang = getLang();
  const isEn = lang !== 'TR';
  const all = appState.items || [];
  const closet = all.filter((i) => !i.isWishlist);
  const wishlistItems = all.filter((i) => i.isWishlist);
  const totalPhotos = closet.reduce((s, it) => s + (it.photoFileNames?.length || 0), 0);
  const favCount = all.filter((i) => i.isFavorite).length;

  const colorCounts = new Map();
  closet.forEach((it) => (it.colors || []).forEach((c) => colorCounts.set(c, (colorCounts.get(c) || 0) + 1)));
  const sortedColors = [...colorCounts.entries()].sort((a, b) => b[1] - a[1]);

  const catCounts = new Map();
  closet.forEach((it) => { if (it.category) catCounts.set(it.category, (catCounts.get(it.category) || 0) + 1); });
  const sortedCats = [...catCounts.entries()].sort((a, b) => b[1] - a[1]);

  const subCounts = new Map();
  closet.forEach((it) => { if (it.subCategory) subCounts.set(it.subCategory, (subCounts.get(it.subCategory) || 0) + 1); });
  const sortedSubs = [...subCounts.entries()].sort((a, b) => b[1] - a[1]);

  const mostWorn = closet.filter((i) => i.wearCount > 0).sort((a, b) => b.wearCount - a.wearCount).slice(0, 5);
  const unworn = closet.filter((i) => !i.wearCount);
  const missingPieces = analyzeMissingCapsulePieces(closet, lang, activeGenderPref(), personalColorProfile);
  const missingColors = suggestMissingHarmoniousColors(closet, lang);

  const visiblePieces = expandMissingPieces ? missingPieces : missingPieces.slice(0, 3);
  const visibleColors = expandMissingColors ? missingColors : missingColors.slice(0, 3);
  const visibleUnworn = expandUnworn ? unworn : unworn.slice(0, 3);

  mainContent.innerHTML = `
    <div class="screen-pad">
      <div class="page-header">
        <div style="min-width:0; flex:1;">
          <h2 class="page-title">${esc(st.tabSummary)}</h2>
          <p class="page-subtitle">${esc(st.summarySubtitle)}</p>
        </div>
        <div style="display:flex; align-items:center; gap:8px; flex-shrink:0;">
          <button type="button" class="icon-btn" id="sSettingsBtn" title="${esc(st.settingsTitle)}">${svgIcon('settings', 22)}</button>
        </div>
      </div>
    </div>

    <div class="stack">
      <div class="stat-grid">
        <div class="stat-tile"><strong>${closet.length}</strong><span>${isEn ? 'Clothes' : 'Kıyafet'}</span></div>
        <div class="stat-tile"><strong>${totalPhotos}</strong><span>${isEn ? 'Photos' : 'Fotoğraf'}</span></div>
        <div class="stat-tile"><strong>${(appState.outfits || []).length}</strong><span>${isEn ? 'Outfits' : 'Kombin'}</span></div>
        <div class="stat-tile"><strong>${favCount}</strong><span>${isEn ? 'Favorites' : 'Favori'}</span></div>
      </div>

      ${wishlistItems.length > 0 ? (() => {
        const trackedCount = wishlistItems.filter((it) => isSafePublicHttpsUrl(it.productUrl)).length;
        const droppedCount = wishlistItems.filter((it) => it.priceDropped).length;
        return `
          <div class="card">
            <div style="display:flex; align-items:center; gap:8px; margin-bottom:6px;">
              <span style="color:var(--accent);">${svgIcon('bookmarkBorder', 18)}</span>
              <h3 class="section-title" style="margin:0;">${isEn ? 'Wishlist & Price Tracker' : 'İstek Listesi & Fiyat Takibi'}</h3>
            </div>
            <div class="muted-note">${isEn ? `${wishlistItems.length} items · ${trackedCount} links · ${droppedCount} price drops` : `${wishlistItems.length} parça · ${trackedCount} takipli link · ${droppedCount} indirim`}</div>
          </div>
        `;
      })() : ''}

      <div class="card" style="display:flex; flex-direction:column; gap:12px;">
        <h3 class="section-title" style="margin:0;">${isEn ? 'Wardrobe Color Palette' : 'Gardırop Renk Paleti'}</h3>
        ${sortedColors.length === 0 ? `
          <div class="muted-note">${isEn ? 'Add clothes to visualize your wardrobe\'s color spectrum.' : 'Kıyafet ekledikçe dolabının baskın renk tonları burada görünecek.'}</div>
        ` : `
          <div class="spectrum-bar">
            ${sortedColors.map(([cid, cnt]) => `<div style="flex:${cnt}; background:${sanitizeHexColor(getColorHex(cid))};" title="${esc(displayColor(cid, lang))} (${cnt})"></div>`).join('')}
          </div>
          <div style="display:flex; flex-direction:column; gap:8px;">
            ${sortedColors.slice(0, 8).map(([cid, cnt]) => `
              <div style="display:flex; justify-content:space-between; align-items:center;">
                <span style="display:inline-flex; align-items:center; gap:10px; font-size:14px;">
                  <span class="color-dot" style="width:16px; height:16px; background:${sanitizeHexColor(getColorHex(cid))};"></span>
                  ${esc(displayColor(cid, lang))}
                </span>
                <span class="muted-note">${isEn ? `${cnt} pcs` : `${cnt} parça`}</span>
              </div>
            `).join('')}
          </div>
        `}
      </div>

      ${sortedSubs.length > 0 ? `
        <div class="card">
          <h3 class="section-title">${isEn ? 'Clothing Types' : 'Kıyafet Türleri'}</h3>
          <div class="wrap-row">
            ${sortedSubs.map(([sid, cnt]) => `<span class="chip-pill">${cnt} ${esc(displaySubCategory(sid, lang))}</span>`).join('')}
          </div>
        </div>
      ` : ''}

      <div class="card" style="display:flex; flex-direction:column; gap:10px;">
        <h3 class="section-title" style="margin:0;">${isEn ? 'General Category Breakdown' : 'Genel Kategori Dağılımı'}</h3>
        ${sortedCats.length === 0 ? `
          <div class="muted-note">${isEn ? 'No category data yet.' : 'Henüz kategori verisi yok.'}</div>
        ` : sortedCats.map(([cid, cnt]) => {
          const pct = Math.round((cnt * 100) / Math.max(1, closet.length));
          return `
            <div style="display:flex; flex-direction:column; gap:4px;">
              <div style="display:flex; justify-content:space-between; font-size:13px;">
                <span>${esc(displayCategory(cid, lang))}</span>
                <span class="muted-note">${cnt}</span>
              </div>
              <div class="progress-track"><div class="progress-fill" style="width:${pct}%;"></div></div>
            </div>
          `;
        }).join('')}
      </div>

      ${mostWorn.length > 0 ? `
        <div class="card">
          <h3 class="section-title">${isEn ? 'Most Worn Pieces' : 'En Çok Giyilen Parçalar'}</h3>
          <div style="display:flex; flex-direction:column; gap:8px;">
            ${mostWorn.map((it) => `
              <div data-sum-item="${esc(it.id)}" style="display:flex; justify-content:space-between; cursor:pointer; font-size:14px;">
                <span>${esc(it.name || displaySubCategory(it.subCategory, lang))}</span>
                <span style="color:var(--olive); font-weight:600;">${isEn ? `${it.wearCount}x` : `${it.wearCount} kez`}</span>
              </div>
            `).join('')}
          </div>
        </div>
      ` : ''}

      ${unworn.length > 0 && closet.length >= 3 ? `
        <div class="card">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <h3 class="section-title" style="margin:0;">${localizedText(lang, `Gardırop Detoksu (${unworn.length} Hiç Giyilmedi)`, `Closet Detox (${unworn.length} Unworn)`, `Schrank-Detox (${unworn.length} ungetragen)`, `Dressing Détox (${unworn.length} non portés)`, `Detox de Armario (${unworn.length} sin usar)`, `衣橱断舍离（${unworn.length} 件未穿过）`)}</h3>
            ${unworn.length > 3 ? `<button type="button" class="btn-icon-ghost" id="sToggleUnworn" style="width:auto; padding:0 4px; color:var(--accent); font-size:12px; font-weight:600;">${expandUnworn ? esc(st.collapse) : `+${unworn.length - 3}`}</button>` : ''}
          </div>
          <p class="muted-note" style="margin:6px 0 8px;">${localizedText(lang, 'Dolabında bekleyen parçalara şans ver (dokun → detayı aç):', 'Give unworn pieces a spin (tap to inspect):')}</p>
          <div class="wrap-row">
            ${visibleUnworn.map((it) => `<button type="button" class="chip-pill compact" data-sum-item="${esc(it.id)}">${esc(it.name || displaySubCategory(it.subCategory, lang))}</button>`).join('')}
          </div>
        </div>
      ` : ''}

      <div class="card">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
          <h3 class="section-title" style="margin:0;">${esc(st.missingPiecesTitle)}</h3>
          ${missingPieces.length > 3 ? `<button type="button" class="btn-icon-ghost" id="sTogglePieces" style="width:auto; gap:2px; padding:0 4px; color:var(--accent); font-size:12px; font-weight:600;">${expandMissingPieces ? esc(st.collapse) : `+${missingPieces.length - 3}`} ${svgIcon(expandMissingPieces ? 'chevronUp' : 'chevronDown', 16)}</button>` : ''}
        </div>
        ${missingPieces.length === 0 ? `
          <div style="display:flex; align-items:center; gap:8px; color:var(--olive); font-size:13px; font-weight:600;">
            ${svgIcon('checkCircle', 16)}
            <span>${isEn ? 'All core capsule pieces are in your wardrobe.' : 'Temel parçalarının tümü dolabında mevcut.'}</span>
          </div>
        ` : `
          <div style="display:flex; flex-direction:column; gap:8px;">
            ${visiblePieces.map((gap, idx) => `
              <div data-gap-idx="${idx}" style="padding:10px 12px; border-radius:12px; background:var(--secondary); cursor:pointer;">
                <strong style="font-size:13px; display:block;">${esc(gap.title)}</strong>
                <span class="accent-note" style="font-size:11px;">${esc(gap.unlocked)}</span>
              </div>
            `).join('')}
          </div>
        `}
      </div>

      <div class="card">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
          <h3 class="section-title" style="margin:0;">${esc(st.missingColorsTitle)}</h3>
          ${missingColors.length > 3 ? `<button type="button" class="btn-icon-ghost" id="sToggleColors" style="width:auto; gap:2px; padding:0 4px; color:var(--accent); font-size:12px; font-weight:600;">${expandMissingColors ? esc(st.collapse) : `+${missingColors.length - 3}`} ${svgIcon(expandMissingColors ? 'chevronUp' : 'chevronDown', 16)}</button>` : ''}
        </div>
        ${visibleColors.length === 0 ? `<p class="accent-note" style="margin:0;">${localizedText(lang, 'Dolabındaki renk çeşitliliği çok dengeli.', 'Great wardrobe color balance.')}</p>` : `
          <div style="display:flex; flex-direction:column; gap:8px;">
            ${visibleColors.map((mc) => `
              <div data-miss-col="${esc(mc.color)}" style="display:flex; align-items:center; gap:10px; padding:8px 10px; border-radius:12px; background:var(--secondary); cursor:pointer;">
                <span class="skin-swatch" style="background:${sanitizeHexColor(getColorHex(mc.color))};"></span>
                <div style="flex:1; min-width:0;">
                  <strong style="font-size:13px; display:block;">${esc(displayColor(mc.color, lang))}</strong>
                  <span class="muted-note" style="font-size:11px;">${esc(mc.reason)}</span>
                </div>
                <span style="color:var(--ink-muted);">${svgIcon('search', 16)}</span>
              </div>
            `).join('')}
          </div>
        `}
      </div>
    </div>
  `;

  document.getElementById('sSettingsBtn')?.addEventListener('click', openSettingsModal);
  document.getElementById('sToggleUnworn')?.addEventListener('click', () => { expandUnworn = !expandUnworn; renderSummaryTab(); });
  document.getElementById('sTogglePieces')?.addEventListener('click', () => { expandMissingPieces = !expandMissingPieces; renderSummaryTab(); });
  document.getElementById('sToggleColors')?.addEventListener('click', () => { expandMissingColors = !expandMissingColors; renderSummaryTab(); });

  document.querySelectorAll('[data-sum-item]').forEach((el) => el.addEventListener('click', () => openItemDetailModal(el.dataset.sumItem)));

  const genderPrefix = activeGenderPref() === 'FEMALE' ? (lang === 'TR' ? 'kadın' : 'women') : (activeGenderPref() === 'MALE' ? (lang === 'TR' ? 'erkek' : 'men') : '');
  document.querySelectorAll('[data-gap-idx]').forEach((el) => el.addEventListener('click', () => {
    const gap = visiblePieces[Number(el.dataset.gapIdx)];
    if (!gap) return;
    openStoreSearchDialog(gap.title, genderPrefix, gap.searchQuery);
  }));
  document.querySelectorAll('[data-miss-col]').forEach((el) => el.addEventListener('click', () => {
    const cid = el.dataset.missCol;
    openStoreSearchDialog(`${displayColor(cid, lang)} ${localizedText(lang, 'Parçalar', 'Pieces')}`, genderPrefix, `${displayColor(cid, lang)} basic üst giyim`);
  }));
}

// ==========================================
// SETTINGS, PROMPT & RELEASE NOTES
// ==========================================
async function openSettingsModal(opts) {
  const replaceHistory = opts?.replaceHistory === true;
  if (!replaceHistory) trackFeature('settings_open');
  const lang = getLang();
  const st = S();
  const closet = (appState.items || []).filter((i) => !i.isWishlist);
  const inferred = inferWardrobeGender(closet);
  const curKey = await getGeminiApiKey();
  const curHf = await getHfToken();
  const keyEncrypted = !!window.crypto?.subtle;

  openModal({
    title: st.settingsTitle,
    replaceHistory,
    bodyHtml: `
      ${!isStandalonePwa() ? `
        <div class="card" style="background:var(--accent-soft); border-color:var(--accent);">
          <strong style="font-size:13px; display:flex; align-items:center; gap:6px;">${svgIcon('download', 16)} ${esc(st.installTitle)}</strong>
          <p class="muted-note" style="margin:4px 0 8px;">${esc(st.installSub)}</p>
          <button type="button" class="btn-primary btn-sm" id="setInstallBtn" style="width:100%;">${svgIcon('add', 15)} ${esc(st.installTitle)}</button>
        </div>
      ` : ''}

      <div>
        <div class="section-title">${esc(st.themeLabel)}</div>
        <div id="setThemes" style="display:flex; flex-direction:column; gap:8px;">
          ${THEME_MODES.map((m) => `
            <div class="card" data-th="${esc(m.id)}" style="padding:13px; cursor:pointer; display:flex; justify-content:space-between; align-items:center; gap:8px; background:${appState.themeMode === m.id ? 'var(--accent-soft)' : 'var(--secondary)'}; border-color:${appState.themeMode === m.id ? 'var(--accent)' : 'var(--border)'};">
              <div style="flex:1; min-width:0;">
                <strong style="font-size:13px; display:block;">${esc(localizedText(lang, m.tr, m.en, m.de, m.fr, m.es, m.zh))}</strong>
                <span class="muted-note" style="font-size:11px;">${esc(localizedText(lang, m.subTr, m.subEn, m.subDe, m.subFr, m.subEs, m.subZh))}</span>
              </div>
              ${appState.themeMode === m.id ? `<span style="color:var(--accent);">${svgIcon('check', 18)}</span>` : ''}
            </div>
          `).join('')}
        </div>
      </div>

      <div>
        <div class="section-title">${esc(st.languageLabel)}</div>
        <div class="wrap-row" id="setLangs">
          ${APP_LANGUAGES.map((l) => `<button type="button" class="chip-pill ${lang === l.code ? 'selected' : ''}" data-lg="${esc(l.code)}">${esc(l.label)}</button>`).join('')}
        </div>
      </div>

      <div>
        <div class="section-title">${localizedText(lang, 'Tercih Edilen Cinsiyet', 'Preferred Gender')}</div>
        <div class="wrap-row" id="setGenders">
          ${GENDER_PREFS.map((g) => `<button type="button" class="chip-pill ${appState.genderUserSet && appState.genderPref === g.id ? 'selected' : ''}" data-gp="${esc(g.id)}">${esc(localizedText(lang, g.tr, g.en, g.de, g.fr, g.es, g.zh))}</button>`).join('')}
          <button type="button" class="chip-pill accent ${!appState.genderUserSet ? 'selected' : ''}" id="setGenderAuto">${localizedText(lang, 'Otomatik', 'Auto')} (${esc(localizedText(lang, GENDER_PREFS.find((x) => x.id === inferred)?.tr, GENDER_PREFS.find((x) => x.id === inferred)?.en))})</button>
        </div>
      </div>

      <div class="card" style="background:var(--secondary); display:flex; flex-direction:column; gap:8px;">
        <strong style="font-size:13px; display:flex; align-items:center; gap:6px;"><span style="color:var(--accent);">${svgIcon('autoAwesome', 16)}</span> ${localizedText(lang, 'AI Stüdyo & 3D Kabin', 'AI Studio & 3D Try-On', 'KI-Studio & 3D-Anprobe', 'Studio IA & Cabine 3D', 'Estudio IA y 3D', 'AI 影棚与 3D 试衣间')}</strong>
        <p class="muted-note" style="margin:0;">${localizedText(lang, 'Ücretsiz bulut katalog fotoğrafı motoru anahtarsız çalışır. Günlük ücretsiz limiti artırmak için isteğe bağlı ücretsiz Hugging Face tokenı (hf_...) veya Gemini anahtarı ekleyebilirsin.', 'Free cloud catalog studio works without any key. For higher daily limits, optionally add a free Hugging Face token (hf_...) or Gemini key.')}</p>
        ${(curHf || curKey) ? `
          <div style="display:flex; flex-wrap:wrap; gap:6px;">
            ${curHf ? `<span class="chip-pill compact selected">${svgIcon('check', 12)} HF · ${keyEncrypted ? 'AES-256' : 'Session'}: ${esc(maskSecret(curHf))}</span>` : ''}
            ${curKey ? `<span class="chip-pill compact selected">${svgIcon('check', 12)} Gemini · ${keyEncrypted ? 'AES-256' : 'Session'}: ${esc(maskSecret(curKey))}</span>` : ''}
          </div>
        ` : ''}
        <input type="password" class="input-field" id="setHfInput" value="" autocomplete="new-password" spellcheck="false" autocapitalize="off" placeholder="${curHf ? localizedText(lang, 'Yeni Hugging Face Token (değiştirmek için)', 'New Hugging Face Token (to replace)') : 'Hugging Face Token (hf_...)'}" />
        <input type="password" class="input-field" id="setGeminiInput" value="" autocomplete="new-password" spellcheck="false" autocapitalize="off" placeholder="${curKey ? localizedText(lang, 'Yeni Gemini API Key (değiştirmek için)', 'New Gemini API Key (to replace)') : 'Gemini API Key (AIza... / AQ....)'}" />
        <a href="https://huggingface.co/settings/tokens" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer" style="display:inline-flex; align-items:center; gap:5px; font-size:12px; font-weight:600; color:var(--accent); text-decoration:none;">${svgIcon('openInNew', 14)} ${localizedText(lang, 'Ücretsiz HF Token Al', 'Get Free HF Token', 'Kostenlosen HF-Token holen', 'Obtenir un token HF gratuit', 'Obtener token HF gratis', '免费获取 HF 令牌')}</a>
        <div class="btn-row">
          <button type="button" class="btn-secondary btn-sm" id="setGeminiSave">${esc(st.save)}</button>
          ${(curKey || curHf) ? `<button type="button" class="btn-danger-outline btn-sm" id="setGeminiClear">${localizedText(lang, 'Anahtarları Sil', 'Clear Keys')}</button>` : ''}
        </div>
      </div>

      <button type="button" class="btn-accent-outline" id="setPromptBtn">${svgIcon('autoAwesome', 15)} ${esc(st.studioPromptBtn)}</button>
      <button type="button" class="btn-secondary" id="setNotesBtn">${svgIcon('newReleases', 15)} ${esc(st.releaseNotesBtn)}</button>
      <button type="button" class="btn-secondary" id="setGithubBtn">${svgIcon('openInNew', 15)} GitHub · Kyhlaer</button>

      <div class="card" style="background:var(--secondary); display:flex; flex-direction:column; gap:8px;">
        <strong style="font-size:13px;">${localizedText(lang, 'Gizlilik & Güvenlik', 'Privacy & Security')}</strong>
        <p class="muted-note" style="margin:0; font-size:11px; line-height:1.45;">
          ${localizedText(lang,
            'Tüm dolap verilerin yalnızca kendi cihazında saklanır. Yüklenen fotoğraflardaki EXIF/GPS konum verileri otomatik silinir, API anahtarları tarayıcıda AES-256-GCM ile şifrelenir.',
            'All wardrobe data stays exclusively on your device. Photo EXIF/GPS metadata is automatically stripped, and API keys are encrypted locally using AES-256-GCM.')}
        </p>
        <button type="button" class="btn-danger-outline btn-sm" id="setWipeAllBtn">${svgIcon('deleteOutline', 15)} ${localizedText(lang, 'Tüm Yerel Verileri Kalıcı Sil', 'Erase All Local Data')}</button>
      </div>

      <label style="display:flex; align-items:center; justify-content:space-between; gap:10px; padding:4px 2px; cursor:pointer;">
        <span class="muted-note" style="font-size:11px;">${localizedText(lang, 'Anonim kullanım istatistikleri', 'Anonymous usage analytics', 'Anonyme Nutzungsstatistik', 'Statistiques anonymes', 'Estadísticas anónimas', '匿名使用统计')}</span>
        <input type="checkbox" id="setAnalyticsToggle" ${isAnalyticsEnabled() ? 'checked' : ''} style="width:16px; height:16px; accent-color:var(--accent); cursor:pointer;" />
      </label>

      <div class="muted-note" style="text-align:center;">${esc(APP_VERSION)} · Web & iOS PWA · by Kyhlaer</div>
    `,
    footerHtml: `<button type="button" class="btn-primary" id="setOkBtn" style="width:100%;">${esc(st.ok)}</button>`,
    onMount: (close, modalEl) => {
      modalEl.querySelector('#setAnalyticsToggle')?.addEventListener('change', (e) => {
        setAnalyticsEnabled(Boolean(e.target.checked));
      });
      modalEl.querySelector('#setInstallBtn')?.addEventListener('click', () => {
        close();
        triggerInstallFlow({ getLang, openModal, showToast, onUpdateUi: () => syncInstallBannerCopy(getLang(), S()) });
      });
      modalEl.querySelectorAll('#setThemes [data-th]').forEach((b) => b.addEventListener('click', () => {
        trackFeature('theme_change', b.dataset.th);
        appState.themeMode = b.dataset.th; commitState(); applyThemeAndLanguage(); close({ skipHistoryBack: true }); openSettingsModal({ replaceHistory: true }); renderCurrentTab();
      }));
      modalEl.querySelectorAll('#setLangs [data-lg]').forEach((b) => b.addEventListener('click', () => {
        trackFeature('language_change', b.dataset.lg);
        appState.language = b.dataset.lg; commitState(); applyThemeAndLanguage(); close({ skipHistoryBack: true }); openSettingsModal({ replaceHistory: true }); renderCurrentTab();
      }));
      modalEl.querySelectorAll('#setGenders [data-gp]').forEach((b) => b.addEventListener('click', () => {
        trackFeature('gender_change', b.dataset.gp);
        appState.genderPref = b.dataset.gp;
        appState.genderUserSet = true;
        if (b.dataset.gp === 'MALE') {
          appState.lookGender = 'MEN';
          lookGender = 'MEN';
        } else if (b.dataset.gp === 'FEMALE') {
          appState.lookGender = 'WOMEN';
          lookGender = 'WOMEN';
        }
        commitState(); close({ skipHistoryBack: true }); openSettingsModal({ replaceHistory: true }); renderCurrentTab();
      }));
      modalEl.querySelector('#setGenderAuto').addEventListener('click', () => {
        trackFeature('gender_change', 'AUTO');
        appState.genderUserSet = false; appState.genderPref = inferred; commitState(); close({ skipHistoryBack: true }); openSettingsModal({ replaceHistory: true }); renderCurrentTab();
      });
      modalEl.querySelector('#setGeminiSave').addEventListener('click', async () => {
        const val = modalEl.querySelector('#setGeminiInput').value.trim();
        const hfVal = modalEl.querySelector('#setHfInput').value.trim();
        if (!val && !hfVal) {
          showToast(localizedText(lang, 'Kaydetmek için yeni bir anahtar girin', 'Enter a key or token to save'));
          return;
        }
        if (hfVal && !isValidHfToken(hfVal)) {
          showToast(localizedText(lang, 'Geçersiz Hugging Face token biçimi (hf_...)', 'Invalid Hugging Face token format (hf_...)'));
          return;
        }
        if (val && !isValidGeminiApiKey(val)) {
          showToast(localizedText(lang, 'Geçersiz Gemini API anahtarı biçimi (AIza... veya AQ....)', 'Invalid Gemini API key format (AIza... or AQ....)'));
          return;
        }
        try {
          if (val) await setGeminiApiKey(val);
          if (hfVal) await setHfToken(hfVal);
          showToast(localizedText(lang, 'AI anahtarları şifreli olarak kaydedildi', 'AI keys encrypted & saved'));
          close({ skipHistoryBack: true });
          openSettingsModal({ replaceHistory: true });
        } catch (err) {
          showToast(sanitizeErrorMessage(err, localizedText(lang, 'Anahtar kaydedilemedi', 'Could not save key')));
        }
      });
      modalEl.querySelector('#setGeminiClear')?.addEventListener('click', async () => {
        await setGeminiApiKey('');
        await setHfToken('');
        showToast(localizedText(lang, 'AI anahtarları kalıcı silindi', 'AI keys permanently deleted'));
        close({ skipHistoryBack: true });
        openSettingsModal({ replaceHistory: true });
      });
      modalEl.querySelector('#setWipeAllBtn')?.addEventListener('click', async () => {
        const msg1 = localizedText(
          lang,
          'Tüm kıyafetlerin, kombinlerin, fotoğrafların ve API anahtarların bu tarayıcıdan kalıcı olarak silinecek. Devam edilsin mi?',
          'All garments, outfits, photos, and API keys will be permanently erased from this browser. Continue?'
        );
        const msg2 = localizedText(
          lang,
          'Bu işlem geri alınamaz! Tüm yerel veriler sıfırlansın mı?',
          'This cannot be undone! Confirm complete data wipe?'
        );
        if (!confirm(msg1) || !confirm(msg2)) return;
        await clearAllLocalData();
        window.location.reload();
      });
      modalEl.querySelector('#setPromptBtn').addEventListener('click', () => {
        close({ skipHistoryBack: true });
        openModal({
          title: st.studioPromptBtn,
          replaceHistory: true,
          bodyHtml: `<div class="prompt-box">${esc(STUDIO_ECOMMERCE_PROMPT)}</div>`,
          footerHtml: `<button type="button" class="btn-primary" id="prCopyBtn">${svgIcon('contentCopy', 15)} ${esc(st.copyPrompt)}</button>`,
          onMount: (c, m) => m.querySelector('#prCopyBtn').addEventListener('click', () => {
            navigator.clipboard?.writeText(STUDIO_ECOMMERCE_PROMPT);
            showToast(st.copiedMsg);
            c();
          })
        });
      });
      modalEl.querySelector('#setGithubBtn')?.addEventListener('click', () => {
        trackFeature('open_github');
        safeOpenExternal('https://github.com/kyhlaer?tab=repositories');
      });
      modalEl.querySelector('#setNotesBtn').addEventListener('click', () => {
        close({ skipHistoryBack: true });
        openModal({
          title: st.releaseNotesTitle,
          replaceHistory: true,
          bodyHtml: RELEASE_NOTES_HISTORY.map((rn) => `
            <div class="card">
              <div style="display:flex; justify-content:space-between; align-items:center;">
                <strong>${esc(rn.version)}</strong>
                ${(lang === 'TR' ? rn.badgeTr : rn.badgeEn) ? `<span class="chip-pill compact accent">${esc(lang === 'TR' ? rn.badgeTr : rn.badgeEn)}</span>` : ''}
              </div>
              <ul style="margin:8px 0 0; padding-left:18px; display:flex; flex-direction:column; gap:6px; font-size:12px; color:var(--ink-muted);">
                ${(lang === 'TR' ? rn.bulletsTr : rn.bulletsEn).map((b) => `<li>${esc(b)}</li>`).join('')}
              </ul>
            </div>
          `).join(''),
          footerHtml: `<button type="button" class="btn-primary" id="rnClose">${esc(st.close)}</button>`,
          onMount: (c, m) => m.querySelector('#rnClose').addEventListener('click', c)
        });
      });
      modalEl.querySelector('#setOkBtn').addEventListener('click', close);
    }
  });
}

function updateActivityBadgeDom() {
  const cnt = Number(unreadActivityCount) || 0;
  ['oActivityBtn', 'fActivityBtn', 'lActivityBtn', 'sActivityBtn'].forEach((id) => {
    const btn = document.getElementById(id);
    if (!btn) return;
    btn.classList.toggle('has-unread', cnt > 0);
    btn.innerHTML = `
      ${svgIcon(cnt > 0 ? 'favoriteFilled' : 'favoriteBorder', 20)}
      ${cnt > 0 ? `<span class="activity-badge">${cnt > 9 ? '9+' : cnt}</span>` : ''}
    `;
  });
}

function renderSocialFeed() {
  renderSocialFeedTab({
    mainContent,
    appState,
    unreadActivityCount,
    lang: getLang(),
    S,
    openModal,
    showToast,
    onOpenActivity: openActivityModal,
    onOpenProfile: (sub) => openProfileHub(sub || 'preview'),
    onOpenPublicProfile: (target, isUsername) => openPublicProfile(target, isUsername),
    onOpenSettings: openSettingsModal,
    pickFilesFromInput,
    savePhotoDataUrl,
    createId,
    commitState,
    syncPhotosAndPersonalProfile
  });
}

function openSinglePostModalWrapper(postId, ownerUid = '') {
  openSinglePostModal({
    postId,
    ownerUid,
    openModal,
    showToast,
    lang: getLang(),
    S,
    pickFilesFromInput,
    onOpenPublicProfile: (target, isUsername) => openPublicProfile(target, isUsername),
    appState,
    savePhotoDataUrl,
    createId,
    commitState,
    syncPhotosAndPersonalProfile
  });
}

function openActivityModal() {
  openActivityHeartModal({
    openModal,
    showToast,
    lang: getLang(),
    S,
    onOpenPublicProfile: (target, isUsername) => openPublicProfile(target, isUsername),
    onOpenPost: (postId, ownerUid) => openSinglePostModalWrapper(postId, ownerUid),
    onUnreadUpdated: (total) => {
      unreadActivityCount = Math.max(0, Number(total) || 0);
      updateActivityBadgeDom();
    }
  });
}

async function pollActivityNotificationsOnce() {
  try {
    const [reqs, notifs] = await Promise.all([
      fetchPendingFollowRequests().catch(() => []),
      fetchMyNotifications().catch(() => [])
    ]);
    const unreadNotifs = notifs.filter((n) => !n.read);
    const total = reqs.length + unreadNotifs.length;
    if (total !== unreadActivityCount) {
      unreadActivityCount = total;
      updateActivityBadgeDom();
    }

    const freshNotifs = unreadNotifs.filter((n) => n.notifId && !seenNotifIds.has(n.notifId));
    notifs.forEach((n) => {
      if (n.notifId) seenNotifIds.add(n.notifId);
    });
    if (firstActivityPollDone && freshNotifs.length > 0) {
      const top = freshNotifs[0];
      const actor = top.fromUsername ? `@${top.fromUsername}` : (top.fromDisplayName || 'Kullanıcı');
      const body = top.type === 'follow_request'
        ? `${actor} seni takip etmek istedi`
        : top.type === 'post_comment'
          ? `${actor} gönderine yorum yaptı`
          : top.type === 'post_like'
            ? `${actor} gönderini beğendi`
            : `${actor} ile yeni etkileşim`;
      showToast(body);
      if (typeof window.Notification === 'function' && window.Notification.permission === 'granted') {
        try {
          new window.Notification('Dolabım', { body, icon: './icons/icon-192.png' });
        } catch {
          // ignore
        }
      }
    }
    firstActivityPollDone = true;
  } catch {
    // ignore transient poll failures
  }
}

function startActivityPolling() {
  if (activityPollTimer) return;
  pollActivityNotificationsOnce();
  activityPollTimer = setInterval(pollActivityNotificationsOnce, 20000);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) pollActivityNotificationsOnce();
  });
}

function openProfileHub(initialTab = 'preview') {
  openProfileHubModal({
    initialTab,
    openModal,
    showToast,
    lang: getLang(),
    S,
    appState,
    photoMap,
    pickFilesFromInput,
    fileToOrientedDataUrl,
    savePhotoDataUrl,
    createId,
    commitState,
    syncPhotosAndPersonalProfile,
    renderCurrentTab,
    unreadActivityCount,
    onOpenActivity: openActivityModal,
    onProfileUpdated: () => renderCurrentTab()
  });
}

function openPublicProfile(targetUidOrUsername, isUsername = false) {
  openPublicProfileModal({
    targetUidOrUsername,
    isUsername,
    openModal,
    showToast,
    lang: getLang(),
    S,
    appState,
    savePhotoDataUrl,
    createId,
    commitState,
    syncPhotosAndPersonalProfile,
    renderCurrentTab
  });
}

function openSharedItem(shareId) {
  openSharedItemViewerModal({
    shareId,
    openModal,
    showToast,
    lang: getLang(),
    S,
    appState,
    savePhotoDataUrl,
    createId,
    commitState,
    syncPhotosAndPersonalProfile,
    renderCurrentTab,
    onOpenPublicProfile: (target, isUid) => openPublicProfile(target, !isUid)
  });
}

function openShareItem(item) {
  openShareGarmentModal({
    item,
    photoMap,
    openModal,
    showToast,
    lang: getLang(),
    S,
    items: appState.items || [],
    outfits: appState.outfits || [],
    onProfileUpdated: () => renderCurrentTab()
  });
}

function openSharedOutfit(shareId) {
  openSharedOutfitViewerModal({
    shareId,
    openModal,
    showToast,
    lang: getLang(),
    S,
    appState,
    savePhotoDataUrl,
    createId,
    commitState,
    syncPhotosAndPersonalProfile,
    renderCurrentTab,
    onOpenPublicProfile: (target, isUid) => openPublicProfile(target, !isUid)
  });
}

function openShareOutfit(title, pieces, harmonyScore, harmonyNote, weatherLabel) {
  openShareOutfitModal({
    title,
    pieces,
    harmonyScore,
    harmonyNote,
    weatherLabel,
    photoMap,
    openModal,
    showToast,
    lang: getLang(),
    S,
    items: appState.items || [],
    outfits: appState.outfits || [],
    onProfileUpdated: () => renderCurrentTab()
  });
}

// ==========================================
// BOOTSTRAP
// ==========================================
async function bootstrap() {
  installClickjackingGuard();
  try {
    startWebTelemetry({
      theme: appState.themeMode || 'MODERN',
      language: getLang(),
      wardrobeCounts: {
        items: (appState.items || []).filter((i) => !i.isWishlist).length,
        wishlist: (appState.items || []).filter((i) => i.isWishlist).length,
        outfits: (appState.outfits || []).length
      }
    });
  } catch {
    // ignore telemetry boot errors
  }
  await migrateLegacySecrets();
  applyThemeAndLanguage();
  await syncPhotosAndPersonalProfile();
  initPwaInstall({
    onUpdateUi: () => syncInstallBannerCopy(getLang(), S()),
    getLang,
    openModal,
    showToast
  });

  document.querySelectorAll('.dock-btn').forEach((btn) => {
    btn.addEventListener('click', () => switchTab(Number(btn.dataset.tab), { fromDock: true }));
  });
  document.getElementById('wardrobeFab')?.addEventListener('click', () => triggerNewGarmentPick('gallery'));

  installBackNavigationHandler();
  installTabSwipeGestures();
  installKeyboardViewportAvoidance();
  syncInstallBannerCopy(getLang(), S());
  syncTelemetryIdentityFromLocalProfile();
  renderCurrentTab();
  updateTelemetryContext({
    theme: appState.themeMode || 'MODERN',
    language: getLang(),
    itemsCount: (appState.items || []).filter((i) => !i.isWishlist).length,
    wishlistCount: (appState.items || []).filter((i) => i.isWishlist).length,
    outfitsCount: (appState.outfits || []).length
  });
  window.__DOLABIM_BOOTED__ = true;
  loadForecastAsync();
  startActivityPolling();

  const deepLink = parseDeepLink(window.location.href);
  if (deepLink) {
    setTimeout(() => {
      if (deepLink.kind === 'item') openSharedItem(deepLink.shareId);
      else if (deepLink.kind === 'outfit') openSharedOutfit(deepLink.shareId);
      else if (deepLink.kind === 'profile') openPublicProfile(deepLink.target, !deepLink.isUid);
      else if (deepLink.kind === 'post') openSinglePostModalWrapper(deepLink.postId, deepLink.ownerUid || '');
    }, 220);
  }

  setTimeout(() => {
    document.getElementById('splashScreen')?.classList.add('fade-out');
  }, 550);
}

function installKeyboardViewportAvoidance() {
  const rootStyle = document.documentElement.style;
  const updateKbInset = () => {
    const vv = window.visualViewport;
    if (!vv) {
      rootStyle.setProperty('--kb-inset', '0px');
      return;
    }
    const inset = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
    rootStyle.setProperty('--kb-inset', inset > 80 ? `${Math.round(inset)}px` : '0px');
  };
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', updateKbInset, { passive: true });
    window.visualViewport.addEventListener('scroll', updateKbInset, { passive: true });
  }
  window.addEventListener('resize', updateKbInset, { passive: true });
  document.addEventListener('focusin', (e) => {
    const el = e.target;
    if (!(el instanceof HTMLElement)) return;
    if (!el.matches('input, textarea, select')) return;
    if (!el.closest('.modal-panel')) return;
    const ensureVisible = () => {
      updateKbInset();
      try {
        el.scrollIntoView({ block: 'center', behavior: 'smooth' });
      } catch {
        el.scrollIntoView();
      }
    };
    setTimeout(ensureVisible, 180);
    setTimeout(ensureVisible, 380);
  });
  document.addEventListener('focusout', () => {
    setTimeout(updateKbInset, 160);
  });
}

bootstrap();
