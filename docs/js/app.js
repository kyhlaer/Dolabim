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
  createId,
  deletePhotoDataUrl,
  exportFullBackupJson,
  getGeminiApiKey,
  getPhotoDataUrl,
  importFullBackupJson,
  loadAllPhotoMap,
  loadAppState,
  saveAppState,
  savePhotoDataUrl,
  setGeminiApiKey
} from './storage.js';
import {
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
import { openCropperQueueModal } from './cropper-modal.js';
import { openVirtualTryOn3DModal } from './tryon3d-modal.js';
import { svgIcon } from './icons.js';

let appState = loadAppState();
let photoMap = new Map();
let personalColorProfile = null;
let currentTab = 0;

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

let lookGender = 'MEN';
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

function esc(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function showToast(msg) {
  const el = document.createElement('div');
  el.className = 'toast-item';
  el.textContent = msg;
  toastRoot.appendChild(el);
  setTimeout(() => el.remove(), 2600);
}

function safeOpenExternal(url) {
  if (!isSafePublicHttpsUrl(url)) {
    showToast(localizedText(getLang(), 'Güvenli https:// linki gerekli', 'Requires safe https:// link'));
    return;
  }
  window.open(url, '_blank', 'noopener,noreferrer');
}

function openModal({ title, bodyHtml, footerHtml = '', fullscreen = false, onMount }) {
  const backdrop = document.createElement('div');
  backdrop.className = 'modal-backdrop';
  backdrop.innerHTML = `
    <div class="modal-panel ${fullscreen ? 'fullscreen' : ''}">
      <div class="modal-header">
        <h3 class="modal-title">${esc(title)}</h3>
        <button type="button" class="btn-icon-ghost modal-close-x" aria-label="Kapat">${svgIcon('close', 20)}</button>
      </div>
      <div class="modal-body">${bodyHtml}</div>
      ${footerHtml ? `<div class="modal-footer">${footerHtml}</div>` : ''}
    </div>
  `;
  const close = () => backdrop.remove();
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) close(); });
  backdrop.querySelector('.modal-close-x').addEventListener('click', close);
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
  return item.photoFileNames || [];
}

function coverUrlOf(item) {
  const pid = activePhotoIds(item)[0];
  return pid ? photoMap.get(pid) : null;
}

function activeGenderPref() {
  if (appState.genderUserSet) return appState.genderPref || 'UNISEX';
  return inferWardrobeGender((appState.items || []).filter((i) => !i.isWishlist));
}

function commitState() {
  saveAppState(appState);
}

async function syncPhotosAndPersonalProfile() {
  const allIds = ['user_face_photo', 'user_body_photo'];
  (appState.items || []).forEach((it) => {
    (it.photoFileNames || []).forEach((id) => allIds.push(id));
    (it.originalPhotoFileNames || []).forEach((id) => allIds.push(id));
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
  const labels = [st.appTitle, st.tabOutfits, st.tabInspiration, st.tabSummary];
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

function switchTab(tabIdx) {
  currentTab = tabIdx;
  document.querySelectorAll('.dock-btn').forEach((btn) => {
    btn.classList.toggle('active', Number(btn.dataset.tab) === currentTab);
  });
  renderCurrentTab();
}

function renderCurrentTab() {
  document.getElementById('wardrobeFab')?.classList.toggle('hidden', currentTab !== 0);
  if (currentTab === 0) renderWardrobeTab();
  else if (currentTab === 1) renderOutfitsTab();
  else if (currentTab === 2) renderInspirationTab();
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
      ${CATEGORIES.map((c) => `<button type="button" class="chip-pill ${wFilterCat === c.id ? 'selected' : ''}" data-cat="${c.id}">${esc(displayCategory(c.id, lang))}</button>`).join('')}
    </div>

    <div class="chip-row sub-row" id="wSubFilters">
      ${subPool.map((sid) => `<button type="button" class="chip-pill ${wFilterSub === sid ? 'selected' : ''}" data-sub="${sid}">${esc(displaySubCategory(sid, lang))}</button>`).join('')}
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
          const dots = (it.colors || []).slice(0, 3).map((cid) => `<span class="color-dot" style="background:${getColorHex(cid)};"></span>`).join('');
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

  document.querySelectorAll('[data-fav-id]').forEach((b) => {
    b.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleFavoriteItem(b.dataset.favId);
    });
  });
  document.querySelectorAll('[data-item-id]').forEach((card) => {
    card.addEventListener('click', () => openItemDetailModal(card.dataset.itemId));
  });
}

function openWardrobeFilterModal() {
  const lang = getLang();
  const st = S();
  openModal({
    title: localizedText(lang, 'Detaylı Filtrele', 'Filter Wardrobe'),
    bodyHtml: `
      <div>
        <div class="section-title">${localizedText(lang, 'Renkler', 'Colors')}</div>
        <div class="swatch-grid" id="wfColors">
          ${GARMENT_COLORS.map((c) => `
            <button type="button" class="swatch-chip ${wFilterColor === c.id ? 'selected' : ''}" data-c="${c.id}">
              <span class="color-dot" style="background:${c.hex};"></span>${esc(displayColor(c.id, lang))}
            </button>
          `).join('')}
        </div>
      </div>
      <div>
        <div class="section-title">${localizedText(lang, 'Tarz', 'Style')}</div>
        <div class="wrap-row" id="wfStyles">
          ${STYLES.map((s) => `<button type="button" class="chip-pill compact ${wFilterStyle === s.id ? 'selected' : ''}" data-s="${s.id}">${esc(displayStyle(s.id, lang))}</button>`).join('')}
        </div>
      </div>
      <div>
        <div class="section-title">${localizedText(lang, 'Mevsim', 'Season')}</div>
        <div class="wrap-row" id="wfSeasons">
          ${SEASONS.map((s) => `<button type="button" class="chip-pill compact ${wFilterSeason === s.id ? 'selected' : ''}" data-se="${s.id}">${esc(displaySeason(s.id, lang))}</button>`).join('')}
        </div>
      </div>
    `,
    footerHtml: `
      <button type="button" class="btn-secondary" id="wfClear">${esc(st.clearFilters)}</button>
      <button type="button" class="btn-primary" id="wfDone">${esc(st.ok)}</button>
    `,
    onMount: (close, modalEl) => {
      modalEl.querySelectorAll('#wfColors [data-c]').forEach((b) => b.addEventListener('click', () => {
        wFilterColor = wFilterColor === b.dataset.c ? null : b.dataset.c;
        modalEl.querySelectorAll('#wfColors [data-c]').forEach((x) => x.classList.toggle('selected', x.dataset.c === wFilterColor));
      }));
      modalEl.querySelectorAll('#wfStyles [data-s]').forEach((b) => b.addEventListener('click', () => {
        wFilterStyle = wFilterStyle === b.dataset.s ? null : b.dataset.s;
        modalEl.querySelectorAll('#wfStyles [data-s]').forEach((x) => x.classList.toggle('selected', x.dataset.s === wFilterStyle));
      }));
      modalEl.querySelectorAll('#wfSeasons [data-se]').forEach((b) => b.addEventListener('click', () => {
        wFilterSeason = wFilterSeason === b.dataset.se ? null : b.dataset.se;
        modalEl.querySelectorAll('#wfSeasons [data-se]').forEach((x) => x.classList.toggle('selected', x.dataset.se === wFilterSeason));
      }));
      modalEl.querySelector('#wfClear').addEventListener('click', () => {
        wFilterColor = null; wFilterStyle = null; wFilterSeason = null;
        close(); renderWardrobeTab();
      });
      modalEl.querySelector('#wfDone').addEventListener('click', () => { close(); renderWardrobeTab(); });
    }
  });
}

// ==========================================
// PHOTO PICKING + ADD / EDIT GARMENT
// ==========================================
function pickFilesFromInput(inputId) {
  return new Promise((resolve) => {
    const inp = document.getElementById(inputId);
    inp.value = '';
    const onChange = () => {
      inp.removeEventListener('change', onChange);
      resolve(Array.from(inp.files || []));
    };
    inp.addEventListener('change', onChange);
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
    onCompleteAll: async (croppedDataUrls) => {
      showToast(localizedText(getLang(), 'E-Ticaret stüdyo kesimi hazırlanıyor...', 'Preparing studio cutout...'));
      const pairs = [];
      for (const origUrl of croppedDataUrls) {
        const uid = createId('ph');
        const origId = `orig_${uid}`;
        const studioId = `studio_${uid}`;
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
  const openStep = () => {
    const first = pairs[step];
    openAddEditItemModal({
      existingItem: null,
      initialPairs: first ? [first] : [],
      batchLabel: total > 1 ? `${step + 1}/${total}` : null,
      hasMoreInBatch: step + 1 < total,
      onSavedContinue: (addAnother) => {
        step++;
        if (step < total) openStep();
        else if (addAnother) openAddEditItemModal({ existingItem: null, initialPairs: [], onSavedContinue: () => {} });
      }
    });
  };
  openStep();
}

async function openAddEditItemModal({
  existingItem = null,
  initialPairs = [],
  batchLabel = null,
  hasMoreInBatch = false,
  onSavedContinue = () => {}
}) {
  const lang = getLang();
  const st = S();
  const isEditing = !!existingItem;

  const studioPhotos = existingItem ? [...(existingItem.photoFileNames || [])] : initialPairs.map((p) => p.studioId);
  const originalPhotos = existingItem ? [...(existingItem.originalPhotoFileNames || [])] : initialPairs.map((p) => p.origId);
  let useOrig = existingItem ? !!existingItem.useOriginalPhotos : false;
  let isWish = existingItem ? !!existingItem.isWishlist : false;
  let priceText = existingItem?.priceText || '';
  let productUrl = existingItem?.productUrl || '';
  let nameVal = existingItem?.name || '';
  let userEditedName = isEditing;
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
      if (!userEditedName) nameVal = pred.suggestedName;
    }
  }

  const title = isEditing
    ? localizedText(lang, 'Kıyafeti Düzenle', 'Edit Piece')
    : (batchLabel ? localizedText(lang, `Yeni Kıyafet (${batchLabel})`, `Add Piece (${batchLabel})`) : localizedText(lang, 'Yeni Kıyafet Ekle', 'Add New Piece'));

  const modalClose = openModal({
    title,
    fullscreen: true,
    bodyHtml: `
      <div class="seg-toggle" id="aeDestToggle">
        <button type="button" class="seg-btn ${!isWish ? 'active' : ''}" data-w="0">${svgIcon('checkroom', 16)} ${esc(st.addToWardrobeLabel)}</button>
        <button type="button" class="seg-btn ${isWish ? 'active' : ''}" data-w="1">${svgIcon('bookmarkBorder', 16)} ${esc(st.addToWishlistLabel)}</button>
      </div>
      <div id="aeWishFields" class="${isWish ? '' : 'hidden'}" style="display:flex; flex-direction:column; gap:8px;">
        <input type="text" class="input-field" id="aePriceInput" placeholder="${esc(st.pricePlaceholder)}" value="${esc(priceText)}" />
        <input type="url" class="input-field" id="aeUrlInput" placeholder="https://... (${localizedText(lang, 'Mağaza ürün linki', 'Store product link')})" value="${esc(productUrl)}" />
      </div>

      <div>
        <div class="section-title">${localizedText(lang, 'Fotoğraflar', 'Photos')} (<span id="aePhotoCount">${studioPhotos.length}</span>)</div>
        <div class="chip-row flush" id="aePhotoStrip"></div>
        <div class="btn-row" style="margin-top:8px;">
          <button type="button" class="chip-pill ${!useOrig ? 'selected' : ''}" id="aeModeStudio" style="justify-content:center;">${svgIcon('autoAwesome', 14)} ${localizedText(lang, 'E-Ticaret Stüdyo', 'E-Commerce Studio')}</button>
          <button type="button" class="chip-pill ${useOrig ? 'selected' : ''}" id="aeModeOrig" style="justify-content:center;">${svgIcon('photoLibrary', 14)} ${localizedText(lang, 'Olduğu Gibi', 'Original Photo')}</button>
        </div>
        <button type="button" class="btn-accent-outline" id="aeFlatLayBtn" style="width:100%; margin-top:8px;">${svgIcon('autoFix', 15)} ${localizedText(lang, 'E-Ticaret Stüdyo Fotoğrafı Yap', 'Transform to E-Commerce Flat-Lay')}</button>
        <div class="btn-row" style="margin-top:8px;">
          <button type="button" class="btn-secondary btn-sm" id="aeAddCam">${svgIcon('camera', 15)} ${localizedText(lang, 'Kamera', 'Camera')}</button>
          <button type="button" class="btn-secondary btn-sm" id="aeAddGal">${svgIcon('photoLibrary', 15)} ${localizedText(lang, 'Galeri', 'Gallery')}</button>
        </div>
      </div>

      <div>
        <div class="section-title">${localizedText(lang, 'Parça Adı', 'Name')}</div>
        <input type="text" class="input-field" id="aeNameInput" value="${esc(nameVal)}" placeholder="${localizedText(lang, 'Örn: Siyah Boxy Hoodie', 'e.g. Black Boxy Hoodie')}" />
      </div>

      <div>
        <div class="section-title">${localizedText(lang, 'Kategori & Tür', 'Category & Type')}</div>
        <div class="chip-row flush" id="aeCatChips"></div>
        <div class="chip-row flush" id="aeSubChips"></div>
      </div>

      <div>
        <div class="section-title">${localizedText(lang, 'Renkler', 'Colors')}</div>
        <div class="swatch-grid" id="aeColorChips"></div>
      </div>

      <div>
        <div class="section-title">${localizedText(lang, 'Tarz & Mevsim', 'Style & Season')}</div>
        <div class="wrap-row" id="aeStyleChips"></div>
        <div class="wrap-row" id="aeSeasonChips" style="margin-top:8px;"></div>
      </div>

      <div>
        <div class="section-title">${localizedText(lang, 'Notlar', 'Notes')}</div>
        <textarea class="input-field" id="aeNotesInput" placeholder="${localizedText(lang, 'Kumaş, kalıp, marka notu...', 'Fabric, fit or brand notes...')}">${esc(notes)}</textarea>
      </div>
    `,
    footerHtml: `
      ${!isEditing && !hasMoreInBatch ? `<button type="button" class="btn-secondary" id="aeSaveNewBtn">${localizedText(lang, 'Kaydet & Yeni', 'Save & New')}</button>` : ''}
      <button type="button" class="btn-primary" id="aeSaveBtn">${isEditing ? localizedText(lang, 'Güncelle', 'Update') : (hasMoreInBatch ? localizedText(lang, 'Kaydet & Sıradaki', 'Save & Next') : esc(st.save))}</button>
    `,
    onMount: (close, modalEl) => {
      const renderPhotoStrip = () => {
        const list = useOrig && originalPhotos.length === studioPhotos.length ? originalPhotos : studioPhotos;
        modalEl.querySelector('#aePhotoCount').textContent = String(list.length);
        const strip = modalEl.querySelector('#aePhotoStrip');
        strip.innerHTML = list.map((pid, idx) => `
          <div style="position:relative; width:112px; height:112px; border-radius:14px; background:${useOrig ? 'var(--photo-surface)' : '#fff'}; border:${idx === 0 ? '2px solid var(--accent)' : '1px solid var(--border)'}; flex-shrink:0; overflow:hidden;">
            <img src="${photoMap.get(pid) || ''}" style="width:100%; height:100%; object-fit:${useOrig ? 'cover' : 'contain'}; padding:${useOrig ? '0' : '6px'};" />
            <button type="button" data-cover="${idx}" style="position:absolute; bottom:4px; left:4px; border:none; border-radius:999px; font-size:10px; font-weight:700; padding:3px 7px; display:inline-flex; align-items:center; gap:3px; background:${idx === 0 ? 'var(--btn-bg)' : 'rgba(255,255,255,0.9)'}; color:${idx === 0 ? 'var(--btn-fg)' : '#1F1F1D'}; cursor:pointer;">${idx === 0 ? `${svgIcon('starFilled', 10)} Kapak` : 'Kapak Yap'}</button>
            ${list.length > 1 ? `<button type="button" data-del="${idx}" style="position:absolute; top:4px; right:4px; border:none; border-radius:50%; width:22px; height:22px; background:rgba(0,0,0,0.72); color:#fff; display:inline-flex; align-items:center; justify-content:center; padding:0; cursor:pointer;">${svgIcon('close', 12)}</button>` : ''}
          </div>
        `).join('');
        strip.querySelectorAll('[data-cover]').forEach((b) => b.addEventListener('click', () => {
          const i = Number(b.dataset.cover);
          if (i > 0) {
            studioPhotos.unshift(...studioPhotos.splice(i, 1));
            if (i < originalPhotos.length) originalPhotos.unshift(...originalPhotos.splice(i, 1));
            renderPhotoStrip();
          }
        }));
        strip.querySelectorAll('[data-del]').forEach((b) => b.addEventListener('click', () => {
          const i = Number(b.dataset.del);
          studioPhotos.splice(i, 1);
          if (i < originalPhotos.length) originalPhotos.splice(i, 1);
          renderPhotoStrip();
        }));
      };

      const renderTaxonomies = () => {
        modalEl.querySelector('#aeCatChips').innerHTML = CATEGORIES.map((c) => `
          <button type="button" class="chip-pill compact ${cat === c.id ? 'selected' : ''}" data-c="${c.id}">${esc(displayCategory(c.id, lang))}</button>
        `).join('');
        modalEl.querySelectorAll('#aeCatChips [data-c]').forEach((b) => b.addEventListener('click', () => {
          cat = b.dataset.c;
          const firstSub = SUBCATEGORIES.find((s) => s.cat === cat)?.id;
          if (firstSub && SUBCATEGORIES.find((s) => s.id === subCat)?.cat !== cat) subCat = firstSub;
          if (!userEditedName) modalEl.querySelector('#aeNameInput').value = cleanGarmentTitleForName(colors[0], subCat, lang);
          renderTaxonomies();
        }));

        modalEl.querySelector('#aeSubChips').innerHTML = SUBCATEGORIES.filter((s) => s.cat === cat).map((s) => `
          <button type="button" class="chip-pill compact ${subCat === s.id ? 'selected' : ''}" data-sub="${s.id}">${esc(displaySubCategory(s.id, lang))}</button>
        `).join('');
        modalEl.querySelectorAll('#aeSubChips [data-sub]').forEach((b) => b.addEventListener('click', () => {
          subCat = b.dataset.sub;
          if (!userEditedName) modalEl.querySelector('#aeNameInput').value = cleanGarmentTitleForName(colors[0], subCat, lang);
          renderTaxonomies();
        }));

        modalEl.querySelector('#aeColorChips').innerHTML = GARMENT_COLORS.map((c) => `
          <button type="button" class="swatch-chip ${colors.includes(c.id) ? 'selected' : ''}" data-col="${c.id}">
            <span class="color-dot" style="background:${c.hex};"></span>${esc(displayColor(c.id, lang))}
          </button>
        `).join('');
        modalEl.querySelectorAll('#aeColorChips [data-col]').forEach((b) => b.addEventListener('click', () => {
          const id = b.dataset.col;
          const ix = colors.indexOf(id);
          if (ix >= 0) colors.splice(ix, 1);
          else colors.unshift(id);
          if (!userEditedName) modalEl.querySelector('#aeNameInput').value = cleanGarmentTitleForName(colors[0], subCat, lang);
          renderTaxonomies();
        }));

        modalEl.querySelector('#aeStyleChips').innerHTML = STYLES.map((s) => `
          <button type="button" class="chip-pill compact ${styleTag === s.id ? 'selected' : ''}" data-st="${s.id}">${esc(displayStyle(s.id, lang))}</button>
        `).join('');
        modalEl.querySelectorAll('#aeStyleChips [data-st]').forEach((b) => b.addEventListener('click', () => {
          styleTag = b.dataset.st; renderTaxonomies();
        }));

        modalEl.querySelector('#aeSeasonChips').innerHTML = SEASONS.map((s) => `
          <button type="button" class="chip-pill compact ${seasons.has(s.id) ? 'selected' : ''}" data-se="${s.id}">${esc(displaySeason(s.id, lang))}</button>
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

      modalEl.querySelectorAll('#aeDestToggle .seg-btn').forEach((b) => b.addEventListener('click', () => {
        isWish = b.dataset.w === '1';
        modalEl.querySelectorAll('#aeDestToggle .seg-btn').forEach((x) => x.classList.toggle('active', (x.dataset.w === '1') === isWish));
        modalEl.querySelector('#aeWishFields').classList.toggle('hidden', !isWish);
      }));

      modalEl.querySelector('#aeNameInput').addEventListener('input', (e) => {
        nameVal = e.target.value;
        userEditedName = true;
      });
      modalEl.querySelector('#aeUrlInput').addEventListener('change', (e) => {
        e.target.value = normalizeProductUrl(e.target.value);
      });
      modalEl.querySelector('#aeModeStudio').addEventListener('click', () => {
        useOrig = false;
        modalEl.querySelector('#aeModeStudio').classList.add('selected');
        modalEl.querySelector('#aeModeOrig').classList.remove('selected');
        renderPhotoStrip();
      });
      modalEl.querySelector('#aeModeOrig').addEventListener('click', () => {
        useOrig = true;
        modalEl.querySelector('#aeModeOrig').classList.add('selected');
        modalEl.querySelector('#aeModeStudio').classList.remove('selected');
        renderPhotoStrip();
      });

      modalEl.querySelector('#aeFlatLayBtn').addEventListener('click', async () => {
        const srcId = originalPhotos[0] || studioPhotos[0];
        const srcUrl = srcId ? photoMap.get(srcId) : null;
        if (!srcUrl) return;
        const btn = modalEl.querySelector('#aeFlatLayBtn');
        btn.disabled = true;
        btn.innerHTML = `<span class="spinner"></span> ${localizedText(lang, 'E-Ticaret Stüdyo Hazırlanıyor...', 'Generating Studio Shot...')}`;
        try {
          const key = getGeminiApiKey();
          let outUrl;
          if (key) {
            try { outUrl = await generateAiEcommerceFlatLay(srcUrl, key); }
            catch { outUrl = await toEcommerceFlatLayDataUrl(srcUrl); }
          } else {
            outUrl = await toEcommerceFlatLayDataUrl(srcUrl);
          }
          const newStudioId = `studio_${createId('ph')}`;
          await savePhotoDataUrl(newStudioId, outUrl);
          photoMap.set(newStudioId, outUrl);
          studioPhotos[0] = newStudioId;
          useOrig = false;
          modalEl.querySelector('#aeModeStudio').classList.add('selected');
          modalEl.querySelector('#aeModeOrig').classList.remove('selected');
          renderPhotoStrip();
          showToast(localizedText(lang, 'E-Ticaret Stüdyo (#FFFFFF) hazır', 'E-Commerce Studio (#FFFFFF) ready'));
        } finally {
          btn.disabled = false;
          btn.innerHTML = `${svgIcon('autoFix', 15)} ${localizedText(lang, 'E-Ticaret Stüdyo Fotoğrafı Yap', 'Transform to E-Commerce Flat-Lay')}`;
        }
      });

      const addMorePhotos = async (mode) => {
        const files = await pickFilesFromInput(mode === 'camera' ? 'cameraInput' : 'galleryInput');
        if (!files.length) return;
        openCropperQueueModal({
          files, lang, modalRoot, showToast,
          onCompleteAll: async (urls) => {
            for (const oUrl of urls) {
              const uid = createId('ph');
              const sId = `studio_${uid}`, oId = `orig_${uid}`;
              const sUrl = await toEcommerceFlatLayDataUrl(oUrl);
              await savePhotoDataUrl(oId, oUrl);
              await savePhotoDataUrl(sId, sUrl);
              photoMap.set(oId, oUrl); photoMap.set(sId, sUrl);
              studioPhotos.push(sId); originalPhotos.push(oId);
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
        } else {
          appState.items = [obj, ...(appState.items || [])];
        }
        commitState();
        close();
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

  openModal({
    title: item.name || displaySubCategory(item.subCategory, lang),
    fullscreen: true,
    bodyHtml: `
      <div id="idStage" style="position:relative; width:100%; aspect-ratio:1/1; background:${item.useOriginalPhotos ? 'var(--photo-surface)' : '#fff'}; border:1px solid var(--border); border-radius:var(--card-radius); overflow:hidden; display:flex; align-items:center; justify-content:center; cursor:zoom-in;">
        <img id="idMainImg" src="${coverUrlOf(item) || ''}" style="width:100%; height:100%; object-fit:${item.useOriginalPhotos ? 'cover' : 'contain'}; padding:${item.useOriginalPhotos ? '0' : '12px'};" />
        <span id="idCounter" style="position:absolute; bottom:10px; right:10px; background:rgba(0,0,0,0.78); color:#fff; padding:4px 10px; border-radius:999px; font-size:11px; font-weight:700;">1 / ${Math.max(1, activePhotoIds(item).length)}</span>
      </div>
      <div class="btn-row" id="idModeRow">
        <button type="button" class="chip-pill ${!item.useOriginalPhotos ? 'selected' : ''}" id="idModeStudio" style="justify-content:center;">${svgIcon('autoAwesome', 14)} ${localizedText(lang, 'E-Ticaret Stüdyo', 'E-Commerce Studio')}</button>
        <button type="button" class="chip-pill ${item.useOriginalPhotos ? 'selected' : ''}" id="idModeOrig" style="justify-content:center;">${svgIcon('photoLibrary', 14)} ${localizedText(lang, 'Orijinal Fotoğraf', 'Original Photo')}</button>
      </div>
      <button type="button" class="btn-accent-outline" id="idFlatLayBtn">${svgIcon('autoFix', 15)} ${localizedText(lang, 'E-Ticaret Stüdyo Fotoğrafına Dönüştür', 'Transform to E-Commerce Studio Shot')}</button>
      <div class="chip-row flush" id="idThumbs"></div>

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

      <div class="card">
        <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
          <h3 style="margin:0; font-size:18px;">${esc(item.name || displaySubCategory(item.subCategory, lang))}</h3>
          ${!item.isWishlist ? `<button type="button" class="chip-pill accent" id="idWearBtn" title="Basılı tut: miktarı düzenle">${svgIcon('checkCircle', 14)} ${esc(st.wornBtn)}${item.wearCount > 0 ? ` (${item.wearCount})` : ''}</button>` : ''}
        </div>
        <div class="wrap-row" style="margin-top:10px;">
          <span class="chip-pill compact">${esc(displayCategory(item.category, lang))}</span>
          <span class="chip-pill compact">${esc(displaySubCategory(item.subCategory, lang))}</span>
          <span class="chip-pill compact">${esc(displayStyle(item.styleTag, lang))}</span>
          ${(item.seasons || []).map((se) => `<span class="chip-pill compact">${esc(displaySeason(se, lang))}</span>`).join('')}
        </div>
        ${(item.colors || []).length > 0 ? `
          <div class="wrap-row" style="margin-top:10px;">
            ${item.colors.map((c) => `<span class="swatch-chip"><span class="color-dot" style="background:${getColorHex(c)};"></span>${esc(displayColor(c, lang))}</span>`).join('')}
          </div>
        ` : ''}
        ${item.notes ? `<p class="muted-note" style="margin:10px 0 0;">${esc(item.notes)}</p>` : ''}
      </div>
    `,
    footerHtml: `
      <button type="button" class="btn-secondary btn-sm" id="idFavBtn" aria-label="Favori">${svgIcon(item.isFavorite ? 'favoriteFilled' : 'favoriteBorder', 18)}</button>
      <button type="button" class="btn-secondary" id="idEditBtn">${svgIcon('edit', 16)} ${localizedText(lang, 'Düzenle', 'Edit')}</button>
      <button type="button" class="btn-danger-outline" id="idDeleteBtn" aria-label="Sil">${svgIcon('deleteOutline', 18)}</button>
    `,
    onMount: (close, modalEl) => {
      const getLatest = () => (appState.items || []).find((x) => x.id === itemId);
      const refresh = () => {
        const cur = getLatest();
        if (!cur) { close(); return; }
        const photos = activePhotoIds(cur);
        pageIdx = Math.min(pageIdx, Math.max(0, photos.length - 1));
        const imgEl = modalEl.querySelector('#idMainImg');
        imgEl.src = photoMap.get(photos[pageIdx]) || '';
        imgEl.style.objectFit = cur.useOriginalPhotos ? 'cover' : 'contain';
        imgEl.style.padding = cur.useOriginalPhotos ? '0' : '12px';
        modalEl.querySelector('#idStage').style.background = cur.useOriginalPhotos ? 'var(--photo-surface)' : '#fff';
        modalEl.querySelector('#idCounter').textContent = `${pageIdx + 1} / ${Math.max(1, photos.length)}`;
        modalEl.querySelector('#idModeStudio').classList.toggle('selected', !cur.useOriginalPhotos);
        modalEl.querySelector('#idModeOrig').classList.toggle('selected', cur.useOriginalPhotos);
        const wBtn = modalEl.querySelector('#idWearBtn');
        if (wBtn) wBtn.innerHTML = `${svgIcon('checkCircle', 14)} ${esc(st.wornBtn)}${cur.wearCount > 0 ? ` (${cur.wearCount})` : ''}`;

        const thumbs = modalEl.querySelector('#idThumbs');
        if (photos.length > 1) {
          thumbs.innerHTML = photos.map((pid, idx) => `
            <button type="button" data-p="${idx}" style="width:60px; height:60px; border-radius:10px; border:${idx === pageIdx ? '2px solid var(--accent)' : '1px solid var(--border)'}; background:#fff; padding:3px; flex-shrink:0;">
              <img src="${photoMap.get(pid) || ''}" style="width:100%; height:100%; object-fit:contain;" />
            </button>
          `).join('');
          thumbs.querySelectorAll('[data-p]').forEach((b) => b.addEventListener('click', () => {
            pageIdx = Number(b.dataset.p);
            refresh();
          }));
        } else thumbs.innerHTML = '';
      };

      modalEl.querySelector('#idStage').addEventListener('click', () => {
        const cur = getLatest();
        const url = cur ? photoMap.get(activePhotoIds(cur)[pageIdx]) : null;
        if (!url) return;
        openModal({
          title: cur.name,
          fullscreen: true,
          bodyHtml: `<div style="display:flex; align-items:center; justify-content:center; height:100%;"><img src="${url}" style="max-width:100%; max-height:100%; object-fit:contain;" /></div>`,
          footerHtml: `<button type="button" class="btn-primary" id="fsCloseBtn">${esc(st.close)}</button>`,
          onMount: (c, m) => m.querySelector('#fsCloseBtn').addEventListener('click', c)
        });
      });

      modalEl.querySelector('#idModeStudio').addEventListener('click', () => {
        appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, useOriginalPhotos: false } : x));
        commitState(); refresh(); renderCurrentTab();
      });
      modalEl.querySelector('#idModeOrig').addEventListener('click', () => {
        appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, useOriginalPhotos: true } : x));
        commitState(); refresh(); renderCurrentTab();
      });

      modalEl.querySelector('#idFlatLayBtn').addEventListener('click', async () => {
        const cur = getLatest();
        if (!cur) return;
        const srcId = cur.originalPhotoFileNames?.[pageIdx] || cur.photoFileNames?.[pageIdx];
        const srcUrl = srcId ? photoMap.get(srcId) : null;
        if (!srcUrl) return;
        const btn = modalEl.querySelector('#idFlatLayBtn');
        btn.disabled = true;
        btn.innerHTML = `<span class="spinner"></span> ${localizedText(lang, 'E-Ticaret Stüdyo Hazırlanıyor...', 'Generating...')}`;
        try {
          const key = getGeminiApiKey();
          let outUrl;
          if (key) {
            try { outUrl = await generateAiEcommerceFlatLay(srcUrl, key); }
            catch { outUrl = await toEcommerceFlatLayDataUrl(srcUrl); }
          } else {
            outUrl = await toEcommerceFlatLayDataUrl(srcUrl);
          }
          const newId = `studio_${createId('ph')}`;
          await savePhotoDataUrl(newId, outUrl);
          photoMap.set(newId, outUrl);
          const nextStudio = [...(cur.photoFileNames || [])];
          nextStudio[pageIdx] = newId;
          appState.items = appState.items.map((x) => (x.id === itemId ? { ...x, photoFileNames: nextStudio, useOriginalPhotos: false } : x));
          commitState(); refresh(); renderCurrentTab();
          showToast(localizedText(lang, 'E-Ticaret Stüdyo (#FFFFFF) güncellendi', 'E-Commerce Studio updated'));
        } finally {
          btn.disabled = false;
          btn.innerHTML = `${svgIcon('autoFix', 15)} ${localizedText(lang, 'E-Ticaret Stüdyo Fotoğrafına Dönüştür', 'Transform to E-Commerce Studio Shot')}`;
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
      modalEl.querySelector('#idFavBtn').addEventListener('click', () => {
        toggleFavoriteItem(itemId);
        modalEl.querySelector('#idFavBtn').innerHTML = svgIcon(getLatest()?.isFavorite ? 'favoriteFilled' : 'favoriteBorder', 18);
      });
      modalEl.querySelector('#idEditBtn').addEventListener('click', () => {
        close();
        openAddEditItemModal({ existingItem: getLatest() });
      });
      modalEl.querySelector('#idDeleteBtn').addEventListener('click', () => {
        if (!confirm(localizedText(lang, 'Bu kıyafet silinsin mi?', 'Delete this piece?'))) return;
        const target = getLatest();
        appState.items = appState.items.filter((x) => x.id !== itemId);
        appState.outfits = (appState.outfits || []).map((o) => ({ ...o, itemIds: o.itemIds.filter((i) => i !== itemId) })).filter((o) => o.itemIds.length > 0);
        commitState();
        (target?.photoFileNames || []).forEach(deletePhotoDataUrl);
        (target?.originalPhotoFileNames || []).forEach(deletePhotoDataUrl);
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
        const inp = modalEl.querySelector(`[data-num="${id}"]`);
        const next = Math.max(0, (parseInt(inp.value, 10) || 0) - 1);
        inp.value = String(next); counts.set(id, next);
      }));
      modalEl.querySelectorAll('[data-inc]').forEach((b) => b.addEventListener('click', () => {
        const id = b.dataset.inc;
        const inp = modalEl.querySelector(`[data-num="${id}"]`);
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
  const city = TURKISH_CITIES.find((c) => c.id === appState.selectedCityId) || TURKISH_CITIES[0];
  const live = await fetchForecastForCoords(city.lat, city.lon, getLang());
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
        <button type="button" class="icon-btn" id="oSettingsBtn" title="${esc(st.settingsTitle)}">${svgIcon('settings', 22)}</button>
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
          <div style="display:flex; align-items:center; gap:8px; min-width:0; flex:1;">
            <span style="font-size:24px; line-height:1;">${condMeta.emoji}</span>
            <div style="min-width:0;">
              <div style="font-size:15px; font-weight:600; color:var(--ink);">${esc(weatherLabel(condMeta))} (${day.maxTempC}°C)</div>
              <div class="muted-note" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(weatherHint(condMeta))}</div>
            </div>
          </div>
          <label class="city-select-pill">
            <span style="color:var(--accent);">${svgIcon('locationOn', 14)}</span>
            <select id="oCitySelect" aria-label="Şehir">
              ${TURKISH_CITIES.map((c) => `<option value="${c.id}" ${c.id === activeCity.id ? 'selected' : ''}>${esc(c.label)}</option>`).join('')}
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
        <div class="card">
          <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
            <div style="min-width:0; flex:1;">
              <strong style="font-size:15px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(sug.title)}</strong>
              <div class="muted-note" style="margin-top:2px;">${esc(sug.harmonyNote)}</div>
            </div>
            ${harmonyBadgeHtml(sug.harmonyScore, lang)}
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
          const ofItems = of.itemIds.map((id) => (appState.items || []).find((x) => x.id === id)).filter(Boolean);
          return `
            <div class="card" data-open-outfit="${esc(of.id)}" style="cursor:pointer;">
              <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                <strong style="font-size:15px; min-width:0; flex:1; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(of.title)}</strong>
                ${harmonyBadgeHtml(of.harmonyScore || 90, lang)}
              </div>
              <div class="piece-thumb-row">
                ${ofItems.slice(0, 4).map((pi) => {
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
            </div>
          `;
        }).join('')}
      ` : ''}
    </div>
  `;

  document.getElementById('oSettingsBtn')?.addEventListener('click', openSettingsModal);
  document.getElementById('o3DBtn')?.addEventListener('click', () => openTryOn3DWithItems([]));
  document.getElementById('oSuitcaseBtn')?.addEventListener('click', openSmartSuitcaseModal);
  document.getElementById('oSurpriseBtn')?.addEventListener('click', () => {
    if (closet.length < 2) { showToast(st.needTwoItemsTitle); return; }
    const pick = suggestOutfits({ items: closet, weather: activeCond, targetDayLabel: localizedText(lang, 'Sürpriz', 'Surprise'), count: 1, lang, genderPref: activeGenderPref(), flatteringColors: flatSet })[0];
    if (pick) openOutfitBuilderModal({ title: pick.title, initialItems: pick.items, existingOutfitId: null });
  });
  document.getElementById('oCreateBtn')?.addEventListener('click', () => {
    if (closet.length === 0) { showToast(st.needTwoItemsTitle); return; }
    openOutfitBuilderModal({ title: localizedText(lang, 'Yeni Kombin', 'New Outfit'), initialItems: closet.slice(0, 2), existingOutfitId: null });
  });
  document.getElementById('oCitySelect')?.addEventListener('change', (e) => {
    appState.selectedCityId = e.target.value;
    commitState();
    loadForecastAsync();
  });
  document.getElementById('oGpsBtn')?.addEventListener('click', async () => {
    showToast(localizedText(lang, 'Konum hava durumu alınıyor...', 'Fetching location weather...'));
    const res = await detectBrowserLocationAndForecast(lang);
    if (res) {
      forecastDays = res.days;
      selectedDayIdx = 0;
      manualWeatherOverride = null;
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

  document.querySelectorAll('[data-try]').forEach((b) => b.addEventListener('click', () => {
    const sug = currentOutfitSuggestions[Number(b.dataset.try)];
    if (sug) openTryOn3DWithItems(sug.items);
  }));
  document.querySelectorAll('[data-worn]').forEach((b) => {
    const sug = currentOutfitSuggestions[Number(b.dataset.worn)];
    if (!sug) return;
    attachTapAndLongPress(
      b,
      () => markItemsWorn(sug.items.map((x) => x.id)),
      () => openWearCountEditorModal(sug.items, () => renderOutfitsTab())
    );
  });
  document.querySelectorAll('[data-save-sug]').forEach((b) => b.addEventListener('click', () => {
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
    openOutfitBuilderModal({ title: of.title, initialItems: ofItems, existingOutfitId: of.id });
  }));
}

function openOutfitBuilderModal({ title, initialItems, existingOutfitId }) {
  const lang = getLang();
  const st = S();
  const closet = (appState.items || []).filter((i) => !i.isWishlist);
  let chosen = [...initialItems];
  let outfitTitle = title;

  openModal({
    title: st.outfitDetailTitle,
    fullscreen: true,
    bodyHtml: `
      <input type="text" class="input-field" id="obTitleInput" value="${esc(outfitTitle)}" />
      <div id="obHarmonyRow" style="display:flex; justify-content:space-between; align-items:center;"></div>
      <p class="muted-note" style="margin:0;">${esc(st.topToBottomHint)}</p>
      <div id="obLayersList" style="display:flex; flex-direction:column; gap:8px;"></div>
      <div id="obAddRow" class="chip-row flush"></div>
    `,
    footerHtml: `
      ${existingOutfitId ? `<button type="button" class="btn-danger-outline" id="obDeleteBtn" aria-label="Sil">${svgIcon('deleteOutline', 18)}</button>` : ''}
      <button type="button" class="btn-secondary" id="obTryBtn">${esc(st.try3DShort)}</button>
      <button type="button" class="btn-primary" id="obSaveBtn">${esc(st.saveOutfit)}</button>
    `,
    onMount: (close, modalEl) => {
      const renderBuilder = () => {
        chosen = sortTopToBottom(chosen);
        const harm = calculateColorHarmony(chosen, lang);
        modalEl.querySelector('#obHarmonyRow').innerHTML = `
          ${harmonyBadgeHtml(harm.score, lang)}
          <span class="muted-note">${esc(harm.note)}</span>
        `;
        const listEl = modalEl.querySelector('#obLayersList');
        listEl.innerHTML = chosen.map((it, idx) => `
          <div class="card" style="padding:10px; display:flex; align-items:center; gap:10px;">
            <img src="${coverUrlOf(it) || ''}" data-open="${esc(it.id)}" style="width:54px; height:54px; object-fit:contain; background:#fff; border:1px solid var(--border); border-radius:10px; cursor:pointer;" />
            <div style="flex:1; min-width:0; cursor:pointer;" data-open="${esc(it.id)}">
              <strong style="font-size:13px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(it.name || displaySubCategory(it.subCategory, lang))}</strong>
              <span class="muted-note">${esc(displayCategory(it.category, lang))} · ${esc(displaySubCategory(it.subCategory, lang))}</span>
            </div>
            <button type="button" class="btn-secondary btn-sm" data-rm="${idx}" aria-label="Çıkar">${svgIcon('close', 14)}</button>
          </div>
        `).join('');
        listEl.querySelectorAll('[data-open]').forEach((x) => x.addEventListener('click', () => openItemDetailModal(x.dataset.open)));
        listEl.querySelectorAll('[data-rm]').forEach((b) => b.addEventListener('click', () => {
          chosen.splice(Number(b.dataset.rm), 1); renderBuilder();
        }));

        const chosenSet = new Set(chosen.map((c) => c.id));
        const available = closet.filter((i) => !chosenSet.has(i.id));
        const addEl = modalEl.querySelector('#obAddRow');
        addEl.innerHTML = available.map((it) => `
          <button type="button" class="chip-pill compact" data-add="${esc(it.id)}">+ ${esc(it.name || displaySubCategory(it.subCategory, lang))}</button>
        `).join('');
        addEl.querySelectorAll('[data-add]').forEach((b) => b.addEventListener('click', () => {
          const found = closet.find((x) => x.id === b.dataset.add);
          if (found) { chosen.push(found); renderBuilder(); }
        }));
      };

      modalEl.querySelector('#obTryBtn').addEventListener('click', () => openTryOn3DWithItems(chosen));
      modalEl.querySelector('#obDeleteBtn')?.addEventListener('click', () => {
        appState.outfits = (appState.outfits || []).filter((o) => o.id !== existingOutfitId);
        commitState(); close(); renderOutfitsTab();
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
  let tripDays = 4;
  let searchQ = '';
  let destResults = [];
  let selectedDest = { name: 'İstanbul', country: 'Türkiye', lat: 41.0082, lon: 28.9784 };
  let plan = null;

  openModal({
    title: localizedText(lang, 'Akıllı Bavul Hazırlayıcı', 'Smart Suitcase Planner'),
    fullscreen: true,
    bodyHtml: `
      <div style="display:flex; gap:8px;">
        <input type="text" class="input-field" id="scCityInput" placeholder="${localizedText(lang, 'Nereye gidiyorsun? (Örn: Antalya, Paris...)', 'Where are you going?')}" style="flex:1;" />
        <button type="button" class="btn-primary btn-sm" id="scSearchBtn">${localizedText(lang, 'Ara', 'Find')}</button>
      </div>
      <div id="scDestResults" class="wrap-row"></div>
      <div style="display:flex; gap:8px; align-items:center;">
        <span class="muted-note">${localizedText(lang, 'Süre:', 'Days:')}</span>
        <button type="button" class="chip-pill compact" data-days="2">2–3 ${localizedText(lang, 'Gün', 'Days')}</button>
        <button type="button" class="chip-pill compact selected" data-days="4">4–5 ${localizedText(lang, 'Gün', 'Days')}</button>
        <button type="button" class="chip-pill compact" data-days="7">7 ${localizedText(lang, 'Gün', 'Days')}</button>
      </div>
      <button type="button" class="btn-primary" id="scGenerateBtn">${svgIcon('luggage', 16)} ${localizedText(lang, 'Hava Durumunu Çek & Bavulu Hazırla', 'Fetch Weather & Build Packing Plan')}</button>
      <div id="scOutput" style="display:flex; flex-direction:column; gap:10px;"></div>
    `,
    footerHtml: `<button type="button" class="btn-primary" id="scCloseBtn">${esc(st.close)}</button>`,
    onMount: (close, modalEl) => {
      modalEl.querySelector('#scCloseBtn').addEventListener('click', close);

      const renderDests = () => {
        const el = modalEl.querySelector('#scDestResults');
        el.innerHTML = destResults.map((d, idx) => `
          <button type="button" class="chip-pill compact ${selectedDest?.name === d.name ? 'selected' : ''}" data-di="${idx}">${svgIcon('locationOn', 13)} ${esc(d.name)}${d.country ? `, ${esc(d.country)}` : ''}</button>
        `).join('');
        el.querySelectorAll('[data-di]').forEach((b) => b.addEventListener('click', () => {
          selectedDest = destResults[Number(b.dataset.di)];
          renderDests();
        }));
      };

      modalEl.querySelector('#scSearchBtn').addEventListener('click', async () => {
        searchQ = modalEl.querySelector('#scCityInput').value.trim();
        if (searchQ.length < 2) return;
        destResults = await searchDestinations(searchQ, lang);
        if (destResults[0]) selectedDest = destResults[0];
        renderDests();
      });

      modalEl.querySelectorAll('[data-days]').forEach((b) => b.addEventListener('click', () => {
        tripDays = Number(b.dataset.days);
        modalEl.querySelectorAll('[data-days]').forEach((x) => x.classList.toggle('selected', Number(x.dataset.days) === tripDays));
      }));

      modalEl.querySelector('#scGenerateBtn').addEventListener('click', async () => {
        if (closet.length < 2) { showToast(st.needTwoItemsTitle); return; }
        const btn = modalEl.querySelector('#scGenerateBtn');
        btn.disabled = true;
        btn.innerHTML = `<span class="spinner"></span>`;
        const live = (await fetchForecastForCoords(selectedDest.lat, selectedDest.lon, lang)) || generateFallbackForecast(lang);
        plan = generateTravelPlan({
          items: closet,
          destinationLabel: `${selectedDest.name}${selectedDest.country ? `, ${selectedDest.country}` : ''}`,
          forecasts: live,
          tripDays,
          lang,
          genderPref: activeGenderPref(),
          flatteringColors: new Set(personalColorProfile?.flatteringColors || [])
        });
        btn.disabled = false;
        btn.innerHTML = `${svgIcon('luggage', 16)} ${localizedText(lang, 'Hava Durumunu Çek & Bavulu Hazırla', 'Fetch Weather & Build Packing Plan')}`;

        const out = modalEl.querySelector('#scOutput');
        out.innerHTML = `
          <div class="card" style="background:var(--accent-soft); border-color:var(--accent);">
            <strong>${esc(plan.destinationLabel)} · ${plan.packingChecklist.length} ${localizedText(lang, 'Parça Bavul Listesi', 'Packing Items')}</strong>
            <div class="wrap-row" style="margin-top:8px;">
              ${plan.packingChecklist.map((it) => `<span class="chip-pill compact">${svgIcon('check', 12)} ${esc(it.name || displaySubCategory(it.subCategory, lang))}</span>`).join('')}
            </div>
          </div>
          ${plan.dayPlans.map((dp) => {
            const w = WEATHER_CONDITIONS[dp.forecast.condition] || WEATHER_CONDITIONS.COOL;
            return `
              <div class="card">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                  <strong>${w.emoji} ${localizedText(lang, `${dp.dayNumber}. Gün`, `Day ${dp.dayNumber}`)} · ${dp.forecast.maxTempC}°C / ${dp.forecast.minTempC}°C</strong>
                  ${harmonyBadgeHtml(dp.outfit.harmonyScore, lang)}
                </div>
                <div class="piece-thumb-row">
                  ${dp.outfit.items.map((pi) => `
                    <div class="piece-thumb">
                      <div class="piece-thumb-box ${pi.useOriginalPhotos ? 'original-bg' : ''}"><img src="${coverUrlOf(pi) || ''}" /></div>
                      <span>${esc(pi.name || displaySubCategory(pi.subCategory, lang))}</span>
                    </div>
                  `).join('')}
                </div>
              </div>
            `;
          }).join('')}
        `;
      });
    }
  });
}

function openTryOn3DWithItems(itemsList) {
  const triggerBodyPick = (mode, afterSave) => {
    pickFilesFromInput(mode === 'camera' ? 'bodyCameraInput' : 'bodyGalleryInput').then(async (files) => {
      const file = files[0];
      if (!file) return;
      const url = await fileToOrientedDataUrl(file, 1440);
      await savePhotoDataUrl('user_body_photo', url);
      photoMap.set('user_body_photo', url);
      afterSave?.();
    });
  };
  openVirtualTryOn3DModal({
    initialItems: itemsList,
    allWardrobeItems: (appState.items || []).filter((i) => !i.isWishlist),
    lang: getLang(),
    modalRoot,
    showToast,
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
  const looksPool = lookGender === 'WOMEN' ? VERIFIED_WOMEN_LOOKS : VERIFIED_MEN_LOOKS;
  const filtered = lookCategory === 'ALL' ? looksPool : looksPool.filter((l) => l.category === lookCategory);
  const closet = (appState.items || []).filter((i) => !i.isWishlist);
  const faceUrl = photoMap.get('user_face_photo');

  mainContent.innerHTML = `
    <div class="screen-pad">
      <div class="page-header">
        <div style="min-width:0; flex:1;">
          <h2 class="page-title">${localizedText(lang, 'Kombin İlhamı', 'Street Lookbook', 'Streetstyle-Lookbook', 'Lookbook Street', 'Lookbook Urbano', '穿搭灵感')}</h2>
          <p class="page-subtitle">${esc(st.inspirationSubtitle)} · ${filtered.length}</p>
        </div>
        <button type="button" class="icon-btn" id="lSettingsBtn" title="${esc(st.settingsTitle)}">${svgIcon('settings', 22)}</button>
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
        <button type="button" class="chip-pill ${lookCategory === c.id ? 'selected' : ''}" data-lc="${c.id}">${esc(localizedText(lang, c.tr, c.en, c.de, c.fr, c.es, c.zh))}</button>
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
            ${personalColorProfile ? `<span class="skin-swatch" style="background:${personalColorProfile.avgSkinHex};"></span>` : ''}
          </div>
        </div>

        ${personalColorProfile ? `
          <div class="accent-note" style="font-size:13px;">${esc(personalColorProfile.seasonTitle)} · ${esc(personalColorProfile.undertoneLabel)}</div>
          <p class="muted-note" style="margin:0; color:var(--ink);">${esc(personalColorProfile.summaryText)}</p>
          <div style="font-size:12px; font-weight:500; color:var(--ink-muted);">${localizedText(lang, 'Sana En Çok Yakışacak Renkler (dokun → ara):', 'Most Flattering Colors (tap → search):')}</div>
          <div class="swatch-grid" id="lSkinColors">
            ${personalColorProfile.flatteringColors.map((cid) => `
              <button type="button" class="swatch-chip" data-scol="${cid}">
                <span class="color-dot" style="background:${getColorHex(cid)};"></span>${esc(displayColor(cid, lang))}
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
              <img src="${look.imageUrl}" alt="${esc(lTitle)}" loading="lazy" />
              <span class="look-badge-tl">${esc(localizedText(lang, catObj?.tr, catObj?.en, catObj?.de, catObj?.fr, catObj?.es, catObj?.zh))}</span>
              <span class="look-badge-tr">${harmonyBadgeHtml(look.harmonyPercent, lang)}</span>
            </div>
            <div class="look-body">
              <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                <div class="garment-title" style="white-space:normal; font-size:16px; line-height:1.3; flex:1;">${esc(lTitle)}</div>
                <div class="dot-row">
                  ${look.colorDots.map((hex) => `<span class="color-dot" style="width:12px; height:12px; background:${hex};"></span>`).join('')}
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
  document.getElementById('lMenBtn')?.addEventListener('click', () => { lookGender = 'MEN'; renderInspirationTab(); });
  document.getElementById('lWomenBtn')?.addEventListener('click', () => { lookGender = 'WOMEN'; renderInspirationTab(); });
  document.getElementById('lSwipeDiscoveryBtn')?.addEventListener('click', openSwipeDiscoveryModal);
  document.querySelectorAll('#lCatChips [data-lc]').forEach((b) => b.addEventListener('click', () => {
    lookCategory = b.dataset.lc; renderInspirationTab();
  }));

  const pickFace = (source) => {
    pickFilesFromInput(source === 'camera' ? 'faceCameraInput' : 'faceGalleryInput').then(async (files) => {
      const f = files[0];
      if (!f) return;
      const url = await fileToOrientedDataUrl(f, 960);
      await savePhotoDataUrl('user_face_photo', url);
      photoMap.set('user_face_photo', url);
      personalColorProfile = await analyzeFaceSkinTone(url, getLang());
      renderInspirationTab();
      showToast(localizedText(getLang(), 'Yüz & ten rengi analiz edildi', 'Face & skin tone analyzed'));
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
        <img src="${look.imageUrl}" alt="${esc(lTitle)}" style="width:100%; height:100%; object-fit:cover;" />
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
  const pool = lookGender === 'WOMEN' ? VERIFIED_WOMEN_LOOKS : VERIFIED_MEN_LOOKS;
  let subTab = 'DECK'; // DECK | LIKED | DISLIKED

  openModal({
    title: localizedText(lang, 'Stil Keşfi', 'Style Discovery'),
    fullscreen: true,
    bodyHtml: `
      <div class="wrap-row" id="swTabs">
        <button type="button" class="chip-pill selected" data-st="DECK">${localizedText(lang, 'Keşfet', 'Discover')}</button>
        <button type="button" class="chip-pill" data-st="LIKED">❤️ ${localizedText(lang, 'Beğendiklerim', 'Liked')} (<span id="swLikeCnt">0</span>)</button>
        <button type="button" class="chip-pill" data-st="DISLIKED">✕ ${localizedText(lang, 'Geçtiklerim', 'Passed')} (<span id="swDisCnt">0</span>)</button>
      </div>
      <div id="swBody"></div>
    `,
    footerHtml: `<button type="button" class="btn-primary" id="swClose">${esc(S().close)}</button>`,
    onMount: (close, modalEl) => {
      const recordSwipe = (lookId, liked) => {
        const likes = new Set(appState.likedLookIds || []);
        const dislikes = new Set(appState.dislikedLookIds || []);
        likes.delete(lookId); dislikes.delete(lookId);
        if (liked === true) likes.add(lookId);
        else if (liked === false) dislikes.add(lookId);
        appState.likedLookIds = [...likes];
        appState.dislikedLookIds = [...dislikes];
        commitState();
        renderBody();
      };

      const renderBody = () => {
        const likes = new Set(appState.likedLookIds || []);
        const dislikes = new Set(appState.dislikedLookIds || []);
        modalEl.querySelector('#swLikeCnt').textContent = String(likes.size);
        modalEl.querySelector('#swDisCnt').textContent = String(dislikes.size);
        modalEl.querySelectorAll('#swTabs [data-st]').forEach((b) => b.classList.toggle('selected', b.dataset.st === subTab));

        const body = modalEl.querySelector('#swBody');
        if (subTab === 'DECK') {
          const remaining = pool.filter((l) => !likes.has(l.id) && !dislikes.has(l.id));
          if (remaining.length === 0) {
            body.innerHTML = `
              <div class="card" style="text-align:center; padding:26px;">
                <p style="margin:0 0 12px; font-weight:700;">${localizedText(lang, 'Tüm kombinleri gezdin!', 'You explored every look!')}</p>
                <button type="button" class="btn-primary" id="swResetDeck">${svgIcon('refresh', 15)} ${localizedText(lang, 'Listeyi Sıfırla', 'Reset Deck')}</button>
              </div>
            `;
            body.querySelector('#swResetDeck').addEventListener('click', () => {
              const ids = new Set(pool.map((x) => x.id));
              appState.likedLookIds = (appState.likedLookIds || []).filter((id) => !ids.has(id));
              appState.dislikedLookIds = (appState.dislikedLookIds || []).filter((id) => !ids.has(id));
              commitState(); renderBody();
            });
            return;
          }
          const topLook = remaining[0];
          const lTitle = lang === 'TR' ? topLook.trTitle : topLook.enTitle;
          const lPal = lang === 'TR' ? topLook.trPalette : topLook.enPalette;
          body.innerHTML = `
            <div class="swipe-stage">
              <div class="swipe-card" id="swCard">
                <img src="${topLook.imageUrl}" alt="${esc(lTitle)}" />
                <span class="swipe-stamp like" id="swStampLike">BEĞENDİM ❤️</span>
                <span class="swipe-stamp nope" id="swStampNope">GEÇ ✕</span>
                <div class="swipe-overlay-info">
                  <strong style="font-size:16px; display:block;">${esc(lTitle)}</strong>
                  <span style="font-size:12px; opacity:0.86;">${esc(lPal)}</span>
                </div>
              </div>
            </div>
            <div class="btn-row" style="margin-top:12px;">
              <button type="button" class="btn-danger-outline" id="swDislikeBtn">${svgIcon('close', 16)} ${localizedText(lang, 'Beğenmedim', 'Pass')}</button>
              <button type="button" class="btn-secondary" id="swInspectBtn">${svgIcon('search', 16)} ${localizedText(lang, 'İncele', 'Inspect')}</button>
              <button type="button" class="btn-primary" id="swLikeBtn">${svgIcon('favoriteFilled', 16)} ${localizedText(lang, 'Beğendim', 'Like')}</button>
            </div>
          `;

          const cardEl = body.querySelector('#swCard');
          let startX = 0, curDx = 0, dragging = false;
          cardEl.addEventListener('pointerdown', (e) => {
            cardEl.setPointerCapture(e.pointerId);
            startX = e.clientX; dragging = true; cardEl.style.transition = 'none';
          });
          cardEl.addEventListener('pointermove', (e) => {
            if (!dragging) return;
            curDx = e.clientX - startX;
            cardEl.style.transform = `translateX(${curDx}px) rotate(${curDx * 0.04}deg)`;
            body.querySelector('#swStampLike').style.opacity = String(Math.min(1, Math.max(0, curDx / 90)));
            body.querySelector('#swStampNope').style.opacity = String(Math.min(1, Math.max(0, -curDx / 90)));
          });
          const endSwipe = () => {
            if (!dragging) return;
            dragging = false; cardEl.style.transition = 'transform 0.16s ease';
            if (curDx > 95) recordSwipe(topLook.id, true);
            else if (curDx < -95) recordSwipe(topLook.id, false);
            else {
              cardEl.style.transform = '';
              body.querySelector('#swStampLike').style.opacity = '0';
              body.querySelector('#swStampNope').style.opacity = '0';
            }
            curDx = 0;
          };
          cardEl.addEventListener('pointerup', endSwipe);
          cardEl.addEventListener('pointercancel', endSwipe);

          body.querySelector('#swDislikeBtn').addEventListener('click', () => recordSwipe(topLook.id, false));
          body.querySelector('#swLikeBtn').addEventListener('click', () => recordSwipe(topLook.id, true));
          body.querySelector('#swInspectBtn').addEventListener('click', () => openLookDetailModal(topLook));
        } else {
          const allLooks = [...VERIFIED_MEN_LOOKS, ...VERIFIED_WOMEN_LOOKS];
          const targetIds = subTab === 'LIKED' ? likes : dislikes;
          const list = allLooks.filter((l) => targetIds.has(l.id));
          if (list.length === 0) {
            body.innerHTML = `<div class="card"><p class="muted-note" style="margin:0; text-align:center;">${localizedText(lang, 'Henüz kombin yok.', 'No items here yet.')}</p></div>`;
            return;
          }
          body.innerHTML = list.map((l) => `
            <div class="card" style="padding:10px; display:flex; align-items:center; gap:10px;">
              <img src="${l.imageUrl}" data-ins="${esc(l.id)}" style="width:60px; height:76px; border-radius:10px; object-fit:cover; cursor:pointer;" />
              <div style="flex:1; min-width:0; cursor:pointer;" data-ins="${esc(l.id)}">
                <strong style="font-size:13px; display:block;">${esc(lang === 'TR' ? l.trTitle : l.enTitle)}</strong>
                <span class="muted-note">${esc(lang === 'TR' ? l.trPalette : l.enPalette)}</span>
              </div>
              <button type="button" class="chip-pill compact accent" data-mv="${esc(l.id)}">${subTab === 'LIKED' ? '✕' : '❤️'}</button>
            </div>
          `).join('');
          body.querySelectorAll('[data-ins]').forEach((el) => el.addEventListener('click', () => {
            const found = allLooks.find((x) => x.id === el.dataset.ins);
            if (found) openLookDetailModal(found);
          }));
          body.querySelectorAll('[data-mv]').forEach((b) => b.addEventListener('click', () => {
            recordSwipe(b.dataset.mv, subTab !== 'LIKED');
          }));
        }
      };

      modalEl.querySelectorAll('#swTabs [data-st]').forEach((b) => b.addEventListener('click', () => {
        subTab = b.dataset.st; renderBody();
      }));
      modalEl.querySelector('#swClose').addEventListener('click', () => { close(); renderInspirationTab(); });
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
        <button type="button" class="icon-btn" id="sSettingsBtn" title="${esc(st.settingsTitle)}">${svgIcon('settings', 22)}</button>
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
            ${sortedColors.map(([cid, cnt]) => `<div style="flex:${cnt}; background:${getColorHex(cid)};" title="${esc(displayColor(cid, lang))} (${cnt})"></div>`).join('')}
          </div>
          <div style="display:flex; flex-direction:column; gap:8px;">
            ${sortedColors.slice(0, 8).map(([cid, cnt]) => `
              <div style="display:flex; justify-content:space-between; align-items:center;">
                <span style="display:inline-flex; align-items:center; gap:10px; font-size:14px;">
                  <span class="color-dot" style="width:16px; height:16px; background:${getColorHex(cid)};"></span>
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
              <div data-miss-col="${mc.color}" style="display:flex; align-items:center; gap:10px; padding:8px 10px; border-radius:12px; background:var(--secondary); cursor:pointer;">
                <span class="skin-swatch" style="background:${getColorHex(mc.color)};"></span>
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
// SETTINGS, PROMPT, BACKUP & RELEASE NOTES
// ==========================================
function openSettingsModal() {
  const lang = getLang();
  const st = S();
  const closet = (appState.items || []).filter((i) => !i.isWishlist);
  const inferred = inferWardrobeGender(closet);
  const curKey = getGeminiApiKey();

  openModal({
    title: st.settingsTitle,
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
            <div class="card" data-th="${m.id}" style="padding:13px; cursor:pointer; display:flex; justify-content:space-between; align-items:center; gap:8px; background:${appState.themeMode === m.id ? 'var(--accent-soft)' : 'var(--secondary)'}; border-color:${appState.themeMode === m.id ? 'var(--accent)' : 'var(--border)'};">
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
          ${APP_LANGUAGES.map((l) => `<button type="button" class="chip-pill ${lang === l.code ? 'selected' : ''}" data-lg="${l.code}">${esc(l.label)}</button>`).join('')}
        </div>
      </div>

      <div>
        <div class="section-title">${localizedText(lang, 'Tercih Edilen Cinsiyet', 'Preferred Gender')}</div>
        <div class="wrap-row" id="setGenders">
          ${GENDER_PREFS.map((g) => `<button type="button" class="chip-pill ${appState.genderUserSet && appState.genderPref === g.id ? 'selected' : ''}" data-gp="${g.id}">${esc(localizedText(lang, g.tr, g.en, g.de, g.fr, g.es, g.zh))}</button>`).join('')}
          <button type="button" class="chip-pill accent ${!appState.genderUserSet ? 'selected' : ''}" id="setGenderAuto">${localizedText(lang, 'Otomatik', 'Auto')} (${esc(localizedText(lang, GENDER_PREFS.find((x) => x.id === inferred)?.tr, GENDER_PREFS.find((x) => x.id === inferred)?.en))})</button>
        </div>
      </div>

      <div class="card" style="background:var(--secondary);">
        <strong style="font-size:13px; display:flex; align-items:center; gap:6px;"><span style="color:var(--accent);">${svgIcon('autoAwesome', 16)}</span> ${localizedText(lang, 'AI Stüdyo & 3D Kabin (Opsiyonel Gemini Key)', 'AI Studio & 3D Try-On (Optional Gemini Key)')}</strong>
        <p class="muted-note" style="margin:4px 0 8px;">${localizedText(lang, 'Cihaz içi E-Ticaret Stüdyo ve 3D Kabin çevrimdışı çalışır. Bulut Gemini AI için isteğe bağlı kendi anahtarını ekleyebilirsin.', 'On-device Studio & 3D Fit work offline. Add an optional Gemini key for cloud generative AI.')}</p>
        <input type="password" class="input-field" id="setGeminiInput" value="${esc(curKey)}" placeholder="AIza..." />
        <div class="btn-row" style="margin-top:8px;">
          <button type="button" class="btn-secondary btn-sm" id="setGeminiSave">${esc(st.save)}</button>
          ${curKey ? `<button type="button" class="btn-danger-outline btn-sm" id="setGeminiClear">${localizedText(lang, 'Temizle', 'Clear')}</button>` : ''}
        </div>
      </div>

      <div class="btn-row">
        <button type="button" class="btn-secondary btn-sm" id="setExportBtn">${svgIcon('download', 15)} ${localizedText(lang, 'Yedeği İndir', 'Export Backup')}</button>
        <button type="button" class="btn-secondary btn-sm" id="setImportBtn">${svgIcon('upload', 15)} ${localizedText(lang, 'Yedek Yükle', 'Import Backup')}</button>
      </div>

      <button type="button" class="btn-accent-outline" id="setPromptBtn">${svgIcon('autoAwesome', 15)} ${esc(st.studioPromptBtn)}</button>
      <button type="button" class="btn-secondary" id="setNotesBtn">${svgIcon('newReleases', 15)} ${esc(st.releaseNotesBtn)}</button>
      <div class="muted-note" style="text-align:center;">${APP_VERSION} · Web & iOS PWA · by Kyhlaer</div>
    `,
    footerHtml: `<button type="button" class="btn-primary" id="setOkBtn">${esc(st.ok)}</button>`,
    onMount: (close, modalEl) => {
      modalEl.querySelector('#setInstallBtn')?.addEventListener('click', () => {
        close();
        triggerInstallFlow({ getLang, openModal, showToast, onUpdateUi: () => syncInstallBannerCopy(getLang(), S()) });
      });
      modalEl.querySelectorAll('#setThemes [data-th]').forEach((b) => b.addEventListener('click', () => {
        appState.themeMode = b.dataset.th; commitState(); applyThemeAndLanguage(); close(); openSettingsModal(); renderCurrentTab();
      }));
      modalEl.querySelectorAll('#setLangs [data-lg]').forEach((b) => b.addEventListener('click', () => {
        appState.language = b.dataset.lg; commitState(); applyThemeAndLanguage(); close(); openSettingsModal(); renderCurrentTab();
      }));
      modalEl.querySelectorAll('#setGenders [data-gp]').forEach((b) => b.addEventListener('click', () => {
        appState.genderPref = b.dataset.gp; appState.genderUserSet = true; commitState(); close(); openSettingsModal(); renderCurrentTab();
      }));
      modalEl.querySelector('#setGenderAuto').addEventListener('click', () => {
        appState.genderUserSet = false; appState.genderPref = inferred; commitState(); close(); openSettingsModal(); renderCurrentTab();
      });
      modalEl.querySelector('#setGeminiSave').addEventListener('click', () => {
        const val = modalEl.querySelector('#setGeminiInput').value.trim();
        setGeminiApiKey(val);
        showToast(val ? localizedText(lang, 'AI anahtarı kaydedildi', 'AI key saved') : localizedText(lang, 'Anahtar temizlendi', 'Key cleared'));
      });
      modalEl.querySelector('#setGeminiClear')?.addEventListener('click', () => {
        setGeminiApiKey(''); modalEl.querySelector('#setGeminiInput').value = '';
        showToast(localizedText(lang, 'Anahtar temizlendi', 'Key cleared'));
      });
      modalEl.querySelector('#setExportBtn').addEventListener('click', async () => {
        const json = await exportFullBackupJson(appState);
        const blob = new Blob([json], { type: 'application/json' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `Dolabim_Yedek_${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(a.href), 2000);
        showToast(localizedText(lang, 'Yedek dosyası indirildi', 'Backup downloaded'));
      });
      modalEl.querySelector('#setImportBtn').addEventListener('click', () => {
        const inp = document.getElementById('backupImportInput');
        inp.value = '';
        inp.onchange = async () => {
          const f = inp.files?.[0];
          if (!f) return;
          try {
            appState = await importFullBackupJson(await f.text());
            await syncPhotosAndPersonalProfile();
            applyThemeAndLanguage(); close(); renderCurrentTab();
            showToast(localizedText(getLang(), 'Yedek başarıyla yüklendi', 'Backup restored'));
          } catch {
            showToast(localizedText(getLang(), 'Geçersiz yedek dosyası', 'Invalid backup file'));
          }
        };
        inp.click();
      });
      modalEl.querySelector('#setPromptBtn').addEventListener('click', () => {
        close();
        openModal({
          title: st.studioPromptBtn,
          bodyHtml: `<div class="prompt-box">${esc(STUDIO_ECOMMERCE_PROMPT)}</div>`,
          footerHtml: `<button type="button" class="btn-primary" id="prCopyBtn">${svgIcon('contentCopy', 15)} ${esc(st.copyPrompt)}</button>`,
          onMount: (c, m) => m.querySelector('#prCopyBtn').addEventListener('click', () => {
            navigator.clipboard?.writeText(STUDIO_ECOMMERCE_PROMPT);
            showToast(st.copiedMsg);
            c();
          })
        });
      });
      modalEl.querySelector('#setNotesBtn').addEventListener('click', () => {
        close();
        openModal({
          title: st.releaseNotesTitle,
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

// ==========================================
// BOOTSTRAP
// ==========================================
async function bootstrap() {
  applyThemeAndLanguage();
  await syncPhotosAndPersonalProfile();
  initPwaInstall({
    onUpdateUi: () => syncInstallBannerCopy(getLang(), S()),
    getLang,
    openModal,
    showToast
  });

  document.querySelectorAll('.dock-btn').forEach((btn) => {
    btn.addEventListener('click', () => switchTab(Number(btn.dataset.tab)));
  });
  document.getElementById('wardrobeFab')?.addEventListener('click', () => triggerNewGarmentPick('gallery'));

  syncInstallBannerCopy(getLang(), S());
  renderCurrentTab();
  loadForecastAsync();

  setTimeout(() => {
    document.getElementById('splashScreen')?.classList.add('fade-out');
  }, 550);
}

bootstrap();
