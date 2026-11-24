// UI Modals for 24h Hourly Forecast, Garment Link Sharing, Profile Hub, Search & Follows

import {
  CATEGORIES,
  WEATHER_CONDITIONS,
  localizedText
} from './constants.js';
import {
  displayCategory,
  displayColor,
  displaySubCategory,
  getColorHex
} from './recommendation-engine.js';
import {
  escHtml as esc,
  isSafePublicHttpsUrl,
  safeImgSrc,
  sanitizeHexColor
} from './security.js';
import { svgIcon } from './icons.js';
import { ensureFirebaseAuth, getStoredUid, trackFeature } from './analytics.js';
import {
  DEFAULT_STATS_VISIBILITY,
  STYLE_VIBE_PRESETS,
  acceptFollowRequest,
  blockUser,
  buildShareItemUrl,
  buildShareOutfitUrl,
  buildShareProfileUrl,
  checkUsernameAvailable,
  compressAvatarPair,
  compressGarmentPhotoForCloud,
  computeFilteredWardrobeSummary,
  fetchBlockedAccounts,
  fetchFollowList,
  fetchLikeInfo,
  fetchPendingFollowRequests,
  fetchProfileBundle,
  fetchSharedItem,
  fetchSharedOutfit,
  followUser,
  importRemoteGarmentLocally,
  isValidUsername,
  loadLocalProfile,
  normalizeUsername,
  saveAndSyncProfile,
  searchProfiles,
  shareGarment,
  shareOutfit,
  toggleLike,
  unblockUser,
  unfollowOrCancel,
  updateSharedItemOwner,
  updateSharedOutfitOwner
} from './social.js';

export function avatarCircleHtml(avatarSrc, label = 'D', size = 36) {
  const safe = safeImgSrc(avatarSrc || '');
  const initial = esc((String(label || 'D').replace(/^@/, '').trim()[0] || 'D').toUpperCase());
  if (safe) {
    return `<span class="user-avatar-circle" style="width:${size}px; height:${size}px;"><img src="${safe}" alt="${esc(label)}" /></span>`;
  }
  return `<span class="user-avatar-circle user-avatar-fallback" style="width:${size}px; height:${size}px; font-size:${Math.max(12, Math.round(size * 0.42))}px;">${initial}</span>`;
}

export function profileIconButtonHtml(id, profile) {
  const safe = safeImgSrc(profile?.avatarThumbBase64 || profile?.avatarBase64 || '');
  const hasUser = Boolean(profile?.username);
  return `
    <button type="button" class="profile-icon-btn" id="${esc(id)}" title="Profil & Sosyal" aria-label="Profil">
      ${safe
        ? `<img src="${safe}" alt="Profil" />`
        : `<span class="profile-icon-inner">${svgIcon('person', 20)}</span>`}
      ${hasUser ? '' : '<span class="profile-dot-badge" aria-hidden="true"></span>'}
    </button>
  `;
}

// ==========================================
// 1) 24-HOUR HOURLY FORECAST MODAL
// ==========================================

export function openHourlyWeatherModal({ openModal, day, locationLabel, lang, S }) {
  if (!day) return;
  const st = S();
  const hourly = Array.isArray(day.hourly) ? day.hourly : [];
  const curHour = new Date().getHours();
  const condMeta = WEATHER_CONDITIONS[day.condition] || WEATHER_CONDITIONS.COOL;
  const wLbl = lang === 'TR' ? condMeta.tr : (lang === 'ZH' ? (condMeta.zh || condMeta.en) : condMeta.en);
  trackFeature('hourly_weather', day.condition || 'COOL');

  openModal({
    title: localizedText(lang, `${day.label} · 24 Saatlik Tahmin`, `${day.label} · 24h Forecast`),
    subtitle: `${locationLabel || 'İstanbul'} · ${day.minTempC}° / ${day.maxTempC}°C`,
    bodyHtml: `
      <div class="card" style="background:var(--accent-soft); border-color:var(--accent); display:flex; align-items:center; justify-content:space-between; gap:10px;">
        <div style="display:flex; align-items:center; gap:10px; min-width:0;">
          <span style="font-size:28px; line-height:1;">${condMeta.emoji}</span>
          <div style="min-width:0;">
            <strong style="font-size:15px; display:block;">${esc(wLbl)} (${day.maxTempC}°C)</strong>
            <span class="muted-note" style="font-size:12px;">${esc(lang === 'TR' ? condMeta.trHint : condMeta.enHint)}</span>
          </div>
        </div>
        <span class="chip-pill compact selected">${esc(day.dateText || '')}</span>
      </div>

      <div class="hourly-list">
        ${hourly.map((h) => {
          const isNow = Boolean(h.isCurrentHour) || (day.dayIndex === 0 && h.hour === curHour && !h.isNextDay);
          const hm = WEATHER_CONDITIONS[h.condition] || WEATHER_CONDITIONS.COOL;
          return `
            <div class="hourly-row ${isNow ? 'hourly-now' : ''}">
              <div class="hourly-time">
                <strong>${esc(h.timeLabel)}</strong>
                ${isNow ? `<span class="hourly-now-pill">${localizedText(lang, 'Şu An', 'Now')}</span>` : (h.isNextDay ? `<span class="muted-note" style="font-size:10px; font-weight:600;">${localizedText(lang, 'Yarın', 'Tmrw')}</span>` : '')}
              </div>
              <span class="hourly-emoji">${hm.emoji}</span>
              <div class="hourly-temps">
                <strong>${h.tempC}°C</strong>
                <span class="muted-note">${localizedText(lang, 'Hiss.', 'Feels')} ${h.feelsLikeC}°</span>
              </div>
              <div class="hourly-metrics">
                <span class="hourly-metric-chip">${svgIcon('waterDrop', 12)} ${h.rainProb}%</span>
                <span class="hourly-metric-chip">${svgIcon('air', 12)} ${h.windKmh} km/h</span>
              </div>
            </div>
          `;
        }).join('')}
      </div>
    `,
    footerHtml: `<button type="button" class="btn-primary" id="hwCloseBtn" style="width:100%;">${esc(st.close)}</button>`,
    onMount: (close, modalEl) => {
      modalEl.querySelector('#hwCloseBtn')?.addEventListener('click', close);
      setTimeout(() => {
        modalEl.querySelector('.hourly-now')?.scrollIntoView({ block: 'center', behavior: 'smooth' });
      }, 120);
    }
  });
}

// ==========================================
// 2) GARMENT SHARE LINK + INLINE USERNAME CALLOUT
// ==========================================

export async function openShareGarmentModal({
  item,
  photoMap,
  openModal,
  showToast,
  lang,
  S,
  items,
  outfits,
  onProfileUpdated
}) {
  if (!item) return;
  let profile = loadLocalProfile();
  let shared = null;
  const st = S();

  openModal({
    title: localizedText(lang, 'Kıyafeti Paylaş', 'Share Garment'),
    subtitle: item.name || displaySubCategory(item.subCategory, lang),
    bodyHtml: `
      <div id="sgLoading" class="card" style="text-align:center; padding:26px 14px;">
        <div class="muted-note">${localizedText(lang, 'Paylaşım linki hazırlanıyor...', 'Creating shareable link...')}</div>
      </div>
      <div id="sgContent" style="display:none; flex-direction:column; gap:12px;"></div>
    `,
    footerHtml: `<button type="button" class="btn-secondary" id="sgCloseBtn" style="width:100%;">${esc(st.close)}</button>`,
    onMount: async (close, modalEl) => {
      modalEl.querySelector('#sgCloseBtn')?.addEventListener('click', close);
      const loadingEl = modalEl.querySelector('#sgLoading');
      const contentEl = modalEl.querySelector('#sgContent');

      const renderSharedBody = () => {
        if (!shared) return;
        const link = buildShareItemUrl(shared.shareId);
        const hasUsername = Boolean(normalizeUsername(profile.username));
        const photoUrl = safeImgSrc(shared.photos?.[0] || '');
        const ownerTag = hasUsername ? `@${shared.ownerUsername}` : (shared.ownerDisplayName || localizedText(lang, 'İsimsiz Kullanıcı', 'Anonymous'));

        contentEl.innerHTML = `
          <div class="card" style="display:flex; gap:12px; align-items:center;">
            ${photoUrl ? `<img src="${photoUrl}" alt="${esc(shared.name)}" style="width:72px; height:72px; border-radius:12px; object-fit:contain; background:#fff; border:1px solid var(--border); flex-shrink:0;" />` : ''}
            <div style="min-width:0; flex:1;">
              <strong style="font-size:15px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(shared.name)}</strong>
              <div style="display:flex; align-items:center; gap:6px; margin-top:6px;">
                ${avatarCircleHtml(profile.avatarThumbBase64 || profile.avatarBase64, ownerTag, 24)}
                <span style="font-size:12px; font-weight:600; color:var(--accent);">${esc(ownerTag)}</span>
              </div>
              <div class="muted-note" style="font-size:11px; margin-top:4px;">${esc(displayCategory(shared.category, lang))} · ${esc(displaySubCategory(shared.subCategory, lang))}</div>
            </div>
          </div>

          <div class="card" style="background:var(--secondary); display:flex; flex-direction:column; gap:8px;">
            <span class="muted-note" style="font-size:11px; font-weight:600;">${localizedText(lang, 'Paylaşım Bağlantısı (Web & Mobil Uygulamada Açılır)', 'Share Link (Opens in Web & Mobile App)')}</span>
            <input type="text" class="input-field" id="sgLinkInput" readonly value="${esc(link)}" style="font-size:12px;" />
            <div class="btn-row">
              <button type="button" class="btn-secondary btn-sm" id="sgCopyBtn">${svgIcon('contentCopy', 15)} ${localizedText(lang, 'Kopyala', 'Copy')}</button>
              <button type="button" class="btn-primary btn-sm" id="sgNativeShareBtn">${svgIcon('share', 15)} ${localizedText(lang, 'Paylaş', 'Share')}</button>
            </div>
          </div>

          ${!hasUsername ? `
            <div class="share-username-callout" id="sgQuickUserBox">
              <div style="display:flex; align-items:center; gap:8px;">
                <span style="color:var(--accent);">${svgIcon('personAdd', 18)}</span>
                <strong style="font-size:13px;">${localizedText(lang, 'Henüz bir kullanıcı adın yok', 'No username yet')}</strong>
              </div>
              <p class="muted-note" style="margin:4px 0 8px; font-size:12px;">
                ${localizedText(lang, 'Arkadaşlarının bu parçayı senden geldiğini görebilmesi için hemen kullanıcı adı oluşturabilirsin.', 'Create a username right now so friends see this garment comes from you.')}
              </p>
              <button type="button" class="btn-accent-outline btn-sm" id="sgRevealQuickUserBtn" style="width:100%;">${svgIcon('personAdd', 15)} ${localizedText(lang, 'Hemen Kullanıcı Adı Oluştur', 'Create Username Now')}</button>
              <div id="sgQuickUserForm" style="display:none; margin-top:10px; flex-direction:column; gap:8px;">
                <input type="text" class="input-field" id="sgQuickUserInput" placeholder="${localizedText(lang, 'örn: kerem_style', 'e.g. alex_style')}" maxlength="24" autocapitalize="off" spellcheck="false" />
                <div class="muted-note" id="sgQuickUserHint" style="font-size:11px;">${localizedText(lang, '3-24 karakter: küçük harf, rakam, _ veya .', '3-24 chars: lowercase letters, numbers, _ or .')}</div>
                <button type="button" class="btn-primary btn-sm" id="sgQuickUserSaveBtn">${svgIcon('check', 15)} ${localizedText(lang, 'Oluştur ve Linke Ekle', 'Create & Attach to Link')}</button>
              </div>
            </div>
          ` : ''}
        `;

        contentEl.querySelector('#sgCopyBtn')?.addEventListener('click', async () => {
          try {
            await navigator.clipboard.writeText(link);
            showToast(localizedText(lang, 'Link panoya kopyalandı', 'Link copied to clipboard'));
          } catch {
            contentEl.querySelector('#sgLinkInput')?.select();
            showToast(link);
          }
        });

        contentEl.querySelector('#sgNativeShareBtn')?.addEventListener('click', async () => {
          if (navigator.share) {
            try {
              await navigator.share({ title: shared.name, text: `${shared.name} - Dolabım`, url: link });
              return;
            } catch {
              // user cancelled or unsupported -> fallback copy
            }
          }
          try {
            await navigator.clipboard.writeText(link);
            showToast(localizedText(lang, 'Link panoya kopyalandı', 'Link copied to clipboard'));
          } catch {
            showToast(link);
          }
        });

        contentEl.querySelector('#sgRevealQuickUserBtn')?.addEventListener('click', () => {
          const btn = contentEl.querySelector('#sgRevealQuickUserBtn');
          const form = contentEl.querySelector('#sgQuickUserForm');
          if (btn) btn.style.display = 'none';
          if (form) form.style.display = 'flex';
          contentEl.querySelector('#sgQuickUserInput')?.focus();
        });

        contentEl.querySelector('#sgQuickUserSaveBtn')?.addEventListener('click', async () => {
          const raw = contentEl.querySelector('#sgQuickUserInput')?.value || '';
          const norm = normalizeUsername(raw);
          const hint = contentEl.querySelector('#sgQuickUserHint');
          if (!isValidUsername(norm)) {
            if (hint) { hint.textContent = localizedText(lang, 'Geçersiz kullanıcı adı (en az 3 karakter: a-z, 0-9, _, .)', 'Invalid username (3-24 chars: a-z, 0-9, _, .)'); hint.style.color = 'var(--danger)'; }
            return;
          }
          const saveBtn = contentEl.querySelector('#sgQuickUserSaveBtn');
          if (saveBtn) saveBtn.disabled = true;
          try {
            const avail = await checkUsernameAvailable(norm);
            if (!avail) {
              if (hint) { hint.textContent = localizedText(lang, `@${norm} başka biri tarafından alınmış`, `@${norm} is already taken`); hint.style.color = 'var(--danger)'; }
              if (saveBtn) saveBtn.disabled = false;
              return;
            }
            profile = await saveAndSyncProfile(
              { ...profile, username: norm, displayName: profile.displayName || norm },
              items || [],
              outfits || [],
              photoMap,
              lang
            );
            shared = await updateSharedItemOwner(shared, profile);
            onProfileUpdated?.();
            renderSharedBody();
            showToast(localizedText(lang, `@${norm} kullanıcı adı linke eklendi`, `@${norm} attached to link`));
          } catch (err) {
            if (hint) { hint.textContent = err?.message || 'Hata'; hint.style.color = 'var(--danger)'; }
            if (saveBtn) saveBtn.disabled = false;
          }
        });
      };

      const attemptShare = async () => {
        loadingEl.style.display = '';
        loadingEl.innerHTML = `<div class="muted-note">${localizedText(lang, 'Paylaşım linki hazırlanıyor...', 'Creating shareable link...')}</div>`;
        contentEl.style.display = 'none';
        try {
          shared = await shareGarment(item, profile, photoMap, lang);
          loadingEl.style.display = 'none';
          contentEl.style.display = 'flex';
          renderSharedBody();
        } catch (err) {
          loadingEl.innerHTML = `
            <div style="color:var(--danger); font-size:13px; margin-bottom:10px;">${esc(err?.message || localizedText(lang, 'Paylaşım oluşturulamadı', 'Could not create share link'))}</div>
            <button type="button" class="btn-secondary btn-sm" id="sgRetryBtn">${localizedText(lang, 'Tekrar Dene', 'Retry')}</button>
          `;
          loadingEl.querySelector('#sgRetryBtn')?.addEventListener('click', attemptShare);
        }
      };
      await attemptShare();
    }
  });
}

// ==========================================
// 3) SHARED GARMENT VIEWER MODAL (DEEP LINK)
// ==========================================

export function openSharedItemViewerModal({
  shareId,
  openModal,
  showToast,
  lang,
  S,
  appState,
  savePhotoDataUrl,
  createId,
  commitState,
  syncPhotosAndPersonalProfile,
  renderCurrentTab,
  onOpenPublicProfile
}) {
  const st = S();
  openModal({
    title: localizedText(lang, 'Paylaşılan Kıyafet', 'Shared Garment'),
    fullscreen: true,
    bodyHtml: `<div id="sivBody" style="display:flex; flex-direction:column; gap:12px;"><div class="card muted-note" style="text-align:center; padding:28px;">${localizedText(lang, 'Kıyafet yükleniyor...', 'Loading item...')}</div></div>`,
    footerHtml: `<button type="button" class="btn-secondary" id="sivCloseBtn" style="width:100%;">${esc(st.close)}</button>`,
    onMount: async (close, modalEl) => {
      modalEl.querySelector('#sivCloseBtn')?.addEventListener('click', close);
      const bodyEl = modalEl.querySelector('#sivBody');
      try {
        const [item, likeInfoInit] = await Promise.all([
          fetchSharedItem(shareId),
          fetchLikeInfo(`item_${shareId}`)
        ]);
        if (!item) {
          bodyEl.innerHTML = `<div class="card muted-note" style="text-align:center; padding:28px;">${localizedText(lang, 'Paylaşılan kıyafet bulunamadı veya silinmiş.', 'Shared item not found.')}</div>`;
          return;
        }
        let photoIdx = 0;
        let likeState = likeInfoInit;
        const ownerName = item.ownerUsername ? `@${item.ownerUsername}` : (item.ownerDisplayName || localizedText(lang, 'Dolabım Kullanıcısı', 'Dolabım User'));

        const renderViewer = () => {
          const curPhoto = safeImgSrc(item.photos?.[photoIdx] || '');
          bodyEl.innerHTML = `
            <div class="card" id="sivOwnerCard" style="display:flex; align-items:center; justify-content:space-between; gap:10px; cursor:pointer;">
              <div style="display:flex; align-items:center; gap:10px; min-width:0;">
                ${avatarCircleHtml(item.ownerAvatar, ownerName, 40)}
                <div style="min-width:0;">
                  <strong style="font-size:14px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(item.ownerDisplayName || ownerName)}</strong>
                  <span class="muted-note" style="font-size:12px;">${item.ownerUsername ? `@${esc(item.ownerUsername)}` : localizedText(lang, 'Profili Görüntüle', 'View Profile')}</span>
                </div>
              </div>
              <span class="chip-pill compact accent">${localizedText(lang, 'Profil', 'Profile')}</span>
            </div>

            <div style="position:relative; width:100%; aspect-ratio:1/1; background:#fff; border:1px solid var(--border); border-radius:var(--card-radius); overflow:hidden; display:flex; align-items:center; justify-content:center;">
              ${curPhoto ? `<img src="${curPhoto}" alt="${esc(item.name)}" style="width:100%; height:100%; object-fit:contain; padding:12px;" />` : ''}
              ${item.photos.length > 1 ? `
                <button type="button" class="btn-icon-ghost" id="sivPrevPhoto" style="position:absolute; left:8px; background:rgba(0,0,0,0.55); color:#fff; border-radius:999px;">${svgIcon('chevronLeft', 20)}</button>
                <button type="button" class="btn-icon-ghost" id="sivNextPhoto" style="position:absolute; right:8px; background:rgba(0,0,0,0.55); color:#fff; border-radius:999px;">${svgIcon('chevronRight', 20)}</button>
                <span style="position:absolute; bottom:10px; right:10px; background:rgba(0,0,0,0.65); color:#fff; padding:4px 10px; border-radius:999px; font-size:11px;">${photoIdx + 1} / ${item.photos.length}</span>
              ` : ''}
            </div>

            <div class="card" style="display:flex; flex-direction:column; gap:10px;">
              <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                <h3 style="margin:0; font-size:19px; flex:1; min-width:0;">${esc(item.name)}</h3>
                <button type="button" class="chip-pill ${likeState.liked ? 'selected' : ''}" id="sivLikeBtn">
                  ${svgIcon(likeState.liked ? 'favoriteFilled' : 'favoriteBorder', 15)} ${likeState.count}
                </button>
              </div>
              <div class="wrap-row">
                <span class="detail-tag-chip">${esc(displayCategory(item.category, lang))}</span>
                ${item.subCategory ? `<span class="detail-tag-chip">${esc(displaySubCategory(item.subCategory, lang))}</span>` : ''}
                ${item.priceText ? `<span class="chip-pill compact accent">${esc(item.priceText)}</span>` : ''}
              </div>
              ${item.colors?.length ? `
                <div class="wrap-row">
                  ${item.colors.map((c) => `<span style="display:inline-flex; align-items:center; gap:5px; font-size:12px;"><span class="color-dot" style="background:${sanitizeHexColor(getColorHex(c))};"></span>${esc(displayColor(c, lang))}</span>`).join('')}
                </div>
              ` : ''}
              ${item.notes ? `<p class="muted-note" style="margin:0; font-size:13px; color:var(--ink);">${esc(item.notes)}</p>` : ''}
              ${isSafePublicHttpsUrl(item.productUrl) ? `
                <a href="${esc(item.productUrl)}" target="_blank" rel="noopener noreferrer" class="btn-secondary btn-sm" style="text-decoration:none;">${svgIcon('openInNew', 15)} ${localizedText(lang, 'Ürün Linkini Aç', 'Open Product Link')}</a>
              ` : ''}
            </div>

            <div class="btn-row">
              <button type="button" class="btn-secondary" id="sivAddWishBtn">${svgIcon('bookmarkBorder', 16)} ${localizedText(lang, 'İstek Listeme Ekle', 'Add to Wishlist')}</button>
              <button type="button" class="btn-primary" id="sivAddClosetBtn">${svgIcon('checkroom', 16)} ${localizedText(lang, 'Dolabıma Ekle', 'Add to Closet')}</button>
            </div>
          `;

          bodyEl.querySelector('#sivOwnerCard')?.addEventListener('click', () => {
            if (item.ownerUsername) onOpenPublicProfile(item.ownerUsername, false);
            else if (item.ownerUid) onOpenPublicProfile(item.ownerUid, true);
          });
          bodyEl.querySelector('#sivPrevPhoto')?.addEventListener('click', () => {
            photoIdx = (photoIdx - 1 + item.photos.length) % item.photos.length;
            renderViewer();
          });
          bodyEl.querySelector('#sivNextPhoto')?.addEventListener('click', () => {
            photoIdx = (photoIdx + 1) % item.photos.length;
            renderViewer();
          });
          bodyEl.querySelector('#sivLikeBtn')?.addEventListener('click', async () => {
            try {
              likeState = await toggleLike(`item_${shareId}`);
              renderViewer();
            } catch (e) {
              showToast(e?.message || 'Hata');
            }
          });
          const handleImport = async (asWishlist) => {
            await importRemoteGarmentLocally({
              itemData: item,
              asWishlist,
              appState,
              savePhotoDataUrl,
              createId,
              commitState,
              syncPhotosAndPersonalProfile,
              lang
            });
            renderCurrentTab();
            showToast(asWishlist
              ? localizedText(lang, 'İstek listene eklendi', 'Added to your wishlist')
              : localizedText(lang, 'Dolabına eklendi', 'Added to your closet'));
          };
          bodyEl.querySelector('#sivAddWishBtn')?.addEventListener('click', () => handleImport(true));
          bodyEl.querySelector('#sivAddClosetBtn')?.addEventListener('click', () => handleImport(false));
        };
        renderViewer();
      } catch (err) {
        bodyEl.innerHTML = `<div class="card muted-note" style="color:var(--danger);">${esc(err?.message || 'Hata')}</div>`;
      }
    }
  });
}

// ==========================================
// 4) WARDROBE SUMMARY CARD & SHOWCASE HELPERS
// ==========================================

function renderWardrobeSummaryPublicCard(summary, lang) {
  if (!summary || Object.keys(summary).length === 0) return '';
  const chips = [];
  if (summary.totalItems != null) chips.push(`<strong>${summary.totalItems}</strong><span>${localizedText(lang, 'Kıyafet', 'Items')}</span>`);
  if (summary.totalPhotos != null) chips.push(`<strong>${summary.totalPhotos}</strong><span>${localizedText(lang, 'Fotoğraf', 'Photos')}</span>`);
  if (summary.outfitCount != null) chips.push(`<strong>${summary.outfitCount}</strong><span>${localizedText(lang, 'Kombin', 'Outfits')}</span>`);
  if (summary.favoriteCount != null) chips.push(`<strong>${summary.favoriteCount}</strong><span>${localizedText(lang, 'Favori', 'Favorites')}</span>`);
  if (summary.wishlistCount != null) chips.push(`<strong>${summary.wishlistCount}</strong><span>${localizedText(lang, 'İstek', 'Wishlist')}</span>`);

  const cats = String(summary.categoryCounts || '').split(',').filter(Boolean).map((p) => {
    const [k, v] = p.split(':');
    return `${displayCategory(k, lang)} (${v})`;
  });
  const cols = String(summary.topColors || '').split(',').filter(Boolean).map((p) => {
    const [k, v] = p.split(':');
    return { id: k, count: v };
  });
  const subs = String(summary.topSubCategories || '').split(',').filter(Boolean).map((p) => {
    const [k, v] = p.split(':');
    return `${v} ${displaySubCategory(k, lang)}`;
  });

  return `
    <div class="card" style="display:flex; flex-direction:column; gap:10px;">
      <strong style="font-size:14px;">${localizedText(lang, 'Dolap Özeti', 'Wardrobe Summary')}</strong>
      ${chips.length ? `<div class="stat-grid">${chips.map((c) => `<div class="stat-tile">${c}</div>`).join('')}</div>` : ''}
      ${cols.length ? `
        <div class="wrap-row">
          ${cols.map((c) => `<span class="chip-pill compact"><span class="color-dot" style="background:${sanitizeHexColor(getColorHex(c.id))};"></span> ${esc(displayColor(c.id, lang))} (${esc(c.count)})</span>`).join('')}
        </div>
      ` : ''}
      ${cats.length ? `<div class="muted-note" style="font-size:12px;">${esc(cats.join(' · '))}</div>` : ''}
      ${subs.length ? `<div class="wrap-row">${subs.map((s) => `<span class="detail-tag-chip">${esc(s)}</span>`).join('')}</div>` : ''}
      ${(summary.estimatedWardrobeValue != null || summary.estimatedWishlistValue != null) ? `
        <div class="accent-note" style="font-size:12px;">
          ${localizedText(lang, 'Tahmini Değer:', 'Estimated Value:')}
          ${summary.estimatedWardrobeValue || 0} ₺ (${localizedText(lang, 'Dolap', 'Closet')}) ·
          ${summary.estimatedWishlistValue || 0} ₺ (${localizedText(lang, 'İstek', 'Wishlist')})
        </div>
      ` : ''}
    </div>
  `;
}

function openShowcaseInspectModal({
  sc,
  openModal,
  showToast,
  lang,
  S,
  appState,
  savePhotoDataUrl,
  createId,
  commitState,
  syncPhotosAndPersonalProfile,
  renderCurrentTab
}) {
  const likeKey = `sc_${String(sc.ownerUid || '').slice(0, 18)}_${String(sc.itemId || '').slice(0, 40)}`;
  openModal({
    title: sc.name,
    subtitle: sc.priceText || displayCategory(sc.category, lang),
    bodyHtml: `
      ${safeImgSrc(sc.photoBase64) ? `
        <div style="width:100%; aspect-ratio:1/1; background:#fff; border:1px solid var(--border); border-radius:16px; overflow:hidden; display:flex; align-items:center; justify-content:center;">
          <img src="${safeImgSrc(sc.photoBase64)}" alt="${esc(sc.name)}" style="width:100%; height:100%; object-fit:contain; padding:10px;" />
        </div>
      ` : ''}
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <div class="wrap-row">
          ${sc.category ? `<span class="detail-tag-chip">${esc(displayCategory(sc.category, lang))}</span>` : ''}
          ${sc.subCategory ? `<span class="detail-tag-chip">${esc(displaySubCategory(sc.subCategory, lang))}</span>` : ''}
        </div>
        <button type="button" class="chip-pill" id="sciLikeBtn">${svgIcon('favoriteBorder', 15)} <span id="sciLikeCnt">0</span></button>
      </div>
      ${sc.notes ? `<p class="muted-note" style="margin:0; color:var(--ink);">${esc(sc.notes)}</p>` : ''}
      ${isSafePublicHttpsUrl(sc.productUrl) ? `<a href="${esc(sc.productUrl)}" target="_blank" rel="noopener noreferrer" class="btn-secondary btn-sm" style="text-decoration:none;">${svgIcon('openInNew', 15)} ${localizedText(lang, 'Ürüne Git', 'Open Link')}</a>` : ''}
    `,
    footerHtml: `
      <div class="btn-row" style="width:100%;">
        <button type="button" class="btn-secondary btn-sm" id="sciWishBtn">${localizedText(lang, 'İstek Listeme', 'Wishlist')}</button>
        <button type="button" class="btn-primary btn-sm" id="sciClosetBtn">${localizedText(lang, 'Dolabıma Ekle', 'Add Closet')}</button>
      </div>
    `,
    onMount: async (close, modalEl) => {
      let likeState = await fetchLikeInfo(likeKey).catch(() => ({ liked: false, count: 0 }));
      const syncLikeUi = () => {
        const btn = modalEl.querySelector('#sciLikeBtn');
        if (!btn) return;
        btn.classList.toggle('selected', likeState.liked);
        btn.innerHTML = `${svgIcon(likeState.liked ? 'favoriteFilled' : 'favoriteBorder', 15)} <span>${likeState.count}</span>`;
      };
      syncLikeUi();
      modalEl.querySelector('#sciLikeBtn')?.addEventListener('click', async () => {
        likeState = await toggleLike(likeKey).catch(() => likeState);
        syncLikeUi();
      });
      const imp = async (asWish) => {
        await importRemoteGarmentLocally({
          itemData: sc,
          asWishlist: asWish,
          appState,
          savePhotoDataUrl,
          createId,
          commitState,
          syncPhotosAndPersonalProfile,
          lang
        });
        renderCurrentTab();
        showToast(asWish ? localizedText(lang, 'İstek listene eklendi', 'Added to wishlist') : localizedText(lang, 'Dolabına eklendi', 'Added to closet'));
        close();
      };
      modalEl.querySelector('#sciWishBtn')?.addEventListener('click', () => imp(true));
      modalEl.querySelector('#sciClosetBtn')?.addEventListener('click', () => imp(false));
    }
  });
}

function openFollowListModal({ targetUid, followers, openModal, onSelectUser, lang, S }) {
  openModal({
    title: followers ? localizedText(lang, 'Takipçiler', 'Followers') : localizedText(lang, 'Takip Edilenler', 'Following'),
    bodyHtml: `<div id="flListBody" class="muted-note" style="text-align:center; padding:20px;">${localizedText(lang, 'Yükleniyor...', 'Loading...')}</div>`,
    footerHtml: `<button type="button" class="btn-secondary" id="flCloseBtn" style="width:100%;">${esc(S().close)}</button>`,
    onMount: async (close, modalEl) => {
      modalEl.querySelector('#flCloseBtn')?.addEventListener('click', close);
      const box = modalEl.querySelector('#flListBody');
      try {
        const list = await fetchFollowList(targetUid, followers);
        if (!list.length) {
          box.textContent = localizedText(lang, 'Henüz kimse yok.', 'No users yet.');
          return;
        }
        box.style.textAlign = 'left';
        box.innerHTML = `<div style="display:flex; flex-direction:column; gap:8px;">
          ${list.map((u) => `
            <div class="card" data-fl-uid="${esc(u.uid)}" style="padding:10px; display:flex; align-items:center; gap:10px; cursor:pointer;">
              ${avatarCircleHtml(u.avatarBase64, u.username || u.displayName, 36)}
              <div style="min-width:0; flex:1;">
                <strong style="font-size:13px; display:block;">${esc(u.displayName || u.username || 'Kullanıcı')}</strong>
                <span class="muted-note" style="font-size:11px;">${u.username ? `@${esc(u.username)}` : ''}</span>
              </div>
              ${u.isPrivate ? svgIcon('lock', 15) : ''}
            </div>
          `).join('')}
        </div>`;
        box.querySelectorAll('[data-fl-uid]').forEach((row) => {
          row.addEventListener('click', () => {
            close();
            onSelectUser(row.dataset.flUid, true);
          });
        });
      } catch (e) {
        box.textContent = e?.message || 'Hata';
      }
    }
  });
}

// ==========================================
// 5) PUBLIC / OTHER USER PROFILE VIEWER
// ==========================================

export function openPublicProfileModal(ctx) {
  const {
    targetUidOrUsername,
    isUsername = false,
    openModal,
    showToast,
    lang,
    S
  } = ctx;
  const st = S();
  openModal({
    title: localizedText(lang, 'Kullanıcı Profili', 'User Profile'),
    fullscreen: true,
    bodyHtml: `<div id="ppMount" style="display:flex; flex-direction:column; gap:12px;"><div class="card muted-note" style="text-align:center; padding:28px;">${localizedText(lang, 'Profil yükleniyor...', 'Loading profile...')}</div></div>`,
    footerHtml: `<button type="button" class="btn-secondary" id="ppCloseBtn" style="width:100%;">${esc(st.close)}</button>`,
    onMount: async (close, modalEl) => {
      modalEl.querySelector('#ppCloseBtn')?.addEventListener('click', close);
      const mountEl = modalEl.querySelector('#ppMount');

      const loadAndRender = async () => {
        try {
          const bundle = await fetchProfileBundle(targetUidOrUsername, isUsername);
          if (!bundle) {
            mountEl.innerHTML = `<div class="card muted-note" style="text-align:center; padding:28px;">${localizedText(lang, 'Bu kullanıcı profili henüz oluşturulmamış veya bulunamadı.', 'User profile not found.')}</div>`;
            return;
          }
          const { header, canViewContent, followStatus, followersCount, followingCount, content, showcaseItems, myUid, isBlockedByMe, hasBlockedMe } = bundle;
          const isMe = myUid && header.uid === myUid;
          const shareUrl = buildShareProfileUrl(header.username, header.uid);

          const followBtnLabel = followStatus === 'accepted'
            ? localizedText(lang, 'Takiptesin (Bırak)', 'Following (Unfollow)')
            : (followStatus === 'pending'
              ? localizedText(lang, 'İstek Gönderildi (İptal)', 'Requested (Cancel)')
              : (header.isPrivate ? localizedText(lang, 'Takip İsteği Gönder', 'Request Follow') : localizedText(lang, 'Takip Et', 'Follow')));

          mountEl.innerHTML = `
            <div class="card" style="display:flex; flex-direction:column; gap:12px;">
              <div style="display:flex; align-items:center; gap:12px;">
                ${avatarCircleHtml(header.avatarBase64, header.username || header.displayName, 64)}
                <div style="min-width:0; flex:1;">
                  <div style="display:flex; align-items:center; gap:6px;">
                    <strong style="font-size:17px;">${esc(header.displayName || header.username || 'Kullanıcı')}</strong>
                    ${header.isPrivate ? `<span title="Gizli">${svgIcon('lock', 15)}</span>` : `<span style="color:var(--accent);" title="Herkese Açık">${svgIcon('publicGlobe', 15)}</span>`}
                  </div>
                  ${header.username ? `<div class="accent-note" style="font-size:13px;">@${esc(header.username)}</div>` : ''}
                  ${header.bio ? `<p class="muted-note" style="margin:6px 0 0; font-size:12px; color:var(--ink);">${esc(header.bio)}</p>` : ''}
                </div>
              </div>
              ${header.styleTags?.length ? `<div class="wrap-row">${header.styleTags.map((t) => `<span class="chip-pill compact selected">${esc(t)}</span>`).join('')}</div>` : ''}

              <div class="profile-follow-counts">
                <button type="button" class="profile-count-btn" id="ppFollowersBtn"><strong>${followersCount}</strong><span>${localizedText(lang, 'Takipçi', 'Followers')}</span></button>
                <button type="button" class="profile-count-btn" id="ppFollowingBtn"><strong>${followingCount}</strong><span>${localizedText(lang, 'Takip', 'Following')}</span></button>
              </div>

              <div class="btn-row" style="flex-wrap:wrap;">
                ${!isMe && !isBlockedByMe && !hasBlockedMe ? `<button type="button" class="${followStatus ? 'btn-secondary' : 'btn-primary'} btn-sm" id="ppFollowActionBtn">${svgIcon('personAdd', 15)} ${esc(followBtnLabel)}</button>` : ''}
                ${!isMe && isBlockedByMe ? `<button type="button" class="btn-secondary btn-sm" id="ppUnblockBtn">${svgIcon('block', 15)} ${localizedText(lang, 'Engeli Kaldır', 'Unblock')}</button>` : ''}
                ${!isMe && !isBlockedByMe && !hasBlockedMe ? `<button type="button" class="btn-danger-outline btn-sm" id="ppBlockBtn">${svgIcon('block', 15)} ${localizedText(lang, 'Engelle', 'Block')}</button>` : ''}
                <button type="button" class="btn-secondary btn-sm" id="ppShareProfileBtn">${svgIcon('share', 15)} ${localizedText(lang, 'Profili Paylaş', 'Share Profile')}</button>
              </div>
            </div>

            ${!canViewContent ? `
              <div class="card" style="text-align:center; padding:28px 16px; display:flex; flex-direction:column; align-items:center; gap:8px;">
                <span style="color:${(isBlockedByMe || hasBlockedMe) ? 'var(--danger)' : 'var(--accent)'};">${svgIcon((isBlockedByMe || hasBlockedMe) ? 'block' : 'lock', 32)}</span>
                <strong>${isBlockedByMe
                  ? localizedText(lang, 'Bu Kullanıcıyı Engelledin', 'You Blocked This User')
                  : (hasBlockedMe
                    ? localizedText(lang, 'Bu Profile Erişilemiyor', 'Profile Unavailable')
                    : localizedText(lang, 'Bu Profil Gizli', 'This Account is Private'))}</strong>
                <p class="muted-note" style="margin:0; font-size:12px;">${isBlockedByMe
                  ? localizedText(lang, 'Engeli kaldırdığında profilini ve paylaşımlarını yeniden görebilirsin.', 'Unblock this user to view their profile and posts again.')
                  : (hasBlockedMe
                    ? localizedText(lang, 'Kullanıcı içeriği şu anda görüntülenemiyor.', 'This content is currently unavailable.')
                    : localizedText(lang, 'Dolap özetini ve sergilediği parçaları görmek için takip isteği gönder.', 'Send a follow request to view their wardrobe summary and showcase.'))}</p>
              </div>
            ` : `
              ${renderWardrobeSummaryPublicCard(content?.wardrobeSummary, lang)}
              ${renderShowcaseSectionsHtml(showcaseItems, lang)}
            `}
          `;

          mountEl.querySelector('#ppShareProfileBtn')?.addEventListener('click', async () => {
            try {
              await navigator.clipboard.writeText(shareUrl);
              showToast(localizedText(lang, 'Profil linki kopyalandı', 'Profile link copied'));
            } catch {
              showToast(shareUrl);
            }
          });
          mountEl.querySelector('#ppFollowersBtn')?.addEventListener('click', () => {
            if (!canViewContent || isBlockedByMe || hasBlockedMe) { showToast(localizedText(lang, 'Takipçi listesi şu anda kapalı', 'Followers unavailable')); return; }
            openFollowListModal({ targetUid: header.uid, followers: true, openModal, onSelectUser: (t, isU) => openPublicProfileModal({ ...ctx, targetUidOrUsername: t, isUsername: Boolean(isU) }), lang, S });
          });
          mountEl.querySelector('#ppFollowingBtn')?.addEventListener('click', () => {
            if (!canViewContent || isBlockedByMe || hasBlockedMe) { showToast(localizedText(lang, 'Takip listesi şu anda kapalı', 'Following unavailable')); return; }
            openFollowListModal({ targetUid: header.uid, followers: false, openModal, onSelectUser: (t) => openPublicProfileModal({ ...ctx, targetUidOrUsername: t, isUsername: false }), lang, S });
          });
          mountEl.querySelector('#ppFollowActionBtn')?.addEventListener('click', async () => {
            try {
              if (followStatus) {
                await unfollowOrCancel(myUid, header.uid);
              } else {
                await followUser(header);
              }
              await loadAndRender();
            } catch (e) {
              showToast(e?.message || 'Hata');
            }
          });
          mountEl.querySelector('#ppBlockBtn')?.addEventListener('click', async () => {
            const label = header.displayName || (header.username ? `@${header.username}` : 'Kullanıcı');
            if (!window.confirm(localizedText(lang, `${label} engellensin mi? Takipler kaldırılır ve birbirinizin içeriklerini göremezsiniz.`, `Block ${label}?`))) return;
            try {
              await blockUser(header.uid);
              showToast(localizedText(lang, 'Kullanıcı engellendi', 'User blocked'));
              await loadAndRender();
            } catch (e) {
              showToast(e?.message || 'Hata');
            }
          });
          mountEl.querySelector('#ppUnblockBtn')?.addEventListener('click', async () => {
            try {
              await unblockUser(header.uid);
              showToast(localizedText(lang, 'Engel kaldırıldı', 'User unblocked'));
              await loadAndRender();
            } catch (e) {
              showToast(e?.message || 'Hata');
            }
          });
          bindShowcaseItemClicks(mountEl, showcaseItems, ctx);
        } catch (err) {
          mountEl.innerHTML = `<div class="card muted-note" style="color:var(--danger);">${esc(err?.message || 'Hata')}</div>`;
        }
      };
      loadAndRender();
    }
  });
}

function renderShowcaseSectionsHtml(showcaseItems, lang) {
  if (!showcaseItems || showcaseItems.length === 0) {
    return `<div class="card muted-note" style="text-align:center; padding:20px;">${localizedText(lang, 'Henüz vitrinde parça sergilenmiyor.', 'No showcased items yet.')}</div>`;
  }
  const sections = [
    ['selected', localizedText(lang, 'Seçilen Parçalar', 'Featured Pieces')],
    ['favorites', localizedText(lang, 'Favoriler', 'Favorites')],
    ['wishlist', localizedText(lang, 'İstek Listesi', 'Wishlist')],
    ['custom', localizedText(lang, 'Özel Yüklenenler', 'Custom Pieces')]
  ];
  return sections.map(([secKey, label]) => {
    const group = showcaseItems.filter((s) => s.section === secKey);
    if (!group.length) return '';
    return `
      <div class="card" style="display:flex; flex-direction:column; gap:10px;">
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <strong style="font-size:14px;">${esc(label)}</strong>
          <span class="chip-pill compact">${group.length}</span>
        </div>
        <div class="showcase-grid">
          ${group.map((sc) => `
            <div class="showcase-tile" data-sc-id="${esc(sc.itemId)}">
              <div class="showcase-thumb">
                ${safeImgSrc(sc.photoBase64) ? `<img src="${safeImgSrc(sc.photoBase64)}" alt="${esc(sc.name)}" />` : svgIcon('checkroom', 24)}
              </div>
              <span class="showcase-name">${esc(sc.name)}</span>
              ${sc.priceText ? `<span class="accent-note" style="font-size:10px;">${esc(sc.priceText)}</span>` : ''}
            </div>
          `).join('')}
        </div>
      </div>
    `;
  }).join('');
}

function bindShowcaseItemClicks(container, showcaseItems, ctx) {
  container.querySelectorAll('[data-sc-id]').forEach((tile) => {
    tile.addEventListener('click', () => {
      const sc = showcaseItems.find((x) => x.itemId === tile.dataset.scId);
      if (sc) openShowcaseInspectModal({ ...ctx, sc });
    });
  });
}

// ==========================================
// 6) PROFILE HUB MODAL (MY PROFILE + EDIT + DISCOVER)
// ==========================================

export function openProfileHubModal(ctx) {
  const {
    initialTab = 'preview',
    openModal,
    showToast,
    lang,
    S,
    appState,
    photoMap,
    pickFilesFromInput,
    fileToOrientedDataUrl,
    createId,
    unreadActivityCount = 0,
    onOpenActivity = null,
    onProfileUpdated
  } = ctx;

  trackFeature('profile_open');
  let activeSubTab = initialTab; // preview | edit | discover
  let draft = loadLocalProfile();
  let myBundle = null;
  let pendingRequests = [];
  let blockedAccounts = [];
  let searchQuery = '';
  let searchResults = [];
  let saving = false;

  const st = S();
  const actCount = Number(unreadActivityCount) || 0;
  openModal({
    title: localizedText(lang, 'Profilim & Dolap Vitrini', 'My Profile & Showcase'),
    fullscreen: true,
    headerRightHtml: onOpenActivity ? `
      <button type="button" class="activity-heart-btn ${actCount > 0 ? 'has-unread' : ''}" id="phActivityBtn" title="${localizedText(lang, 'Hareketler & Takip İstekleri', 'Activity & Requests')}" aria-label="${localizedText(lang, 'Hareketler', 'Activity')}">
        ${svgIcon(actCount > 0 ? 'favoriteFilled' : 'favoriteBorder', 20)}
        ${actCount > 0 ? `<span class="activity-badge">${actCount > 9 ? '9+' : actCount}</span>` : ''}
      </button>
    ` : '',
    bodyHtml: `
      <div class="seg-toggle seg-3" id="phTabs">
        <button type="button" class="seg-btn ${activeSubTab === 'preview' ? 'active' : ''}" data-phtab="preview">${localizedText(lang, 'Profilim', 'Profile')}</button>
        <button type="button" class="seg-btn ${activeSubTab === 'edit' ? 'active' : ''}" data-phtab="edit">${localizedText(lang, 'Düzenle', 'Edit')}</button>
        <button type="button" class="seg-btn ${activeSubTab === 'discover' ? 'active' : ''}" data-phtab="discover">${localizedText(lang, 'Keşfet & Ara', 'Discover')}</button>
      </div>
      <div id="phMount" style="display:flex; flex-direction:column; gap:12px;"></div>
    `,
    footerHtml: `
      <button type="button" class="btn-secondary" id="phCloseBtn" style="flex:1;">${esc(st.close)}</button>
      <button type="button" class="btn-primary ${activeSubTab === 'edit' ? '' : 'hidden'}" id="peSaveAllBtn" style="flex:1.6;">${localizedText(lang, 'Profili Kaydet & Yayınla', 'Save & Publish Profile')}</button>
    `,
    onMount: (close, modalEl) => {
      modalEl.querySelector('#phCloseBtn')?.addEventListener('click', close);
      modalEl.querySelector('#phActivityBtn')?.addEventListener('click', () => {
        close();
        onOpenActivity?.();
      });
      const mountEl = modalEl.querySelector('#phMount');
      const footerSaveBtn = modalEl.querySelector('#peSaveAllBtn');

      const bindDraftInputs = () => {
        const u = mountEl.querySelector('#peUsername')?.value;
        const d = mountEl.querySelector('#peDisplayName')?.value;
        const b = mountEl.querySelector('#peBio')?.value;
        if (u !== undefined) draft.username = normalizeUsername(u);
        if (d !== undefined) draft.displayName = d.slice(0, 50);
        if (b !== undefined) draft.bio = b.slice(0, 220);
      };

      const syncFooterForTab = () => {
        if (!footerSaveBtn) return;
        footerSaveBtn.classList.toggle('hidden', activeSubTab !== 'edit');
        footerSaveBtn.disabled = saving;
        footerSaveBtn.textContent = saving
          ? localizedText(lang, 'Kaydediliyor...', 'Saving...')
          : localizedText(lang, 'Profili Kaydet & Yayınla', 'Save & Publish Profile');
      };

      const updateTabBtns = () => {
        modalEl.querySelectorAll('[data-phtab]').forEach((b) => {
          b.classList.toggle('active', b.dataset.phtab === activeSubTab);
        });
        syncFooterForTab();
      };

      const loadCloudState = async () => {
        try {
          const authUid = (await import('./analytics.js')).getStoredUid();
          if (authUid) {
            myBundle = await fetchProfileBundle(authUid, false).catch(() => null);
          }
          pendingRequests = await fetchPendingFollowRequests().catch(() => []);
          blockedAccounts = await fetchBlockedAccounts().catch(() => []);
          if (activeSubTab === 'discover' && searchResults.length === 0) {
            searchResults = await searchProfiles('').catch(() => []);
          }
          renderSubTab();
        } catch {
          // keep local preview usable even offline
        }
      };

      const renderPreview = () => {
        const items = appState.items || [];
        const outfits = appState.outfits || [];
        const localSum = computeFilteredWardrobeSummary(items, outfits, draft.statsVisibility || DEFAULT_STATS_VISIBILITY);
        const followersCnt = myBundle?.followersCount || 0;
        const followingCnt = myBundle?.followingCount || 0;
        const uid = myBundle?.myUid || '';

        mountEl.innerHTML = `
          <div class="card" style="display:flex; flex-direction:column; gap:12px;">
            <div style="display:flex; align-items:center; gap:12px;">
              ${avatarCircleHtml(draft.avatarBase64 || draft.avatarThumbBase64, draft.username || draft.displayName || 'D', 64)}
              <div style="min-width:0; flex:1;">
                <div style="display:flex; align-items:center; gap:6px;">
                  <strong style="font-size:17px;">${esc(draft.displayName || draft.username || localizedText(lang, 'Profilini Oluştur', 'Set Up Profile'))}</strong>
                  ${draft.isPrivate ? svgIcon('lock', 15) : `<span style="color:var(--accent);">${svgIcon('publicGlobe', 15)}</span>`}
                </div>
                ${draft.username ? `<div class="accent-note" style="font-size:13px;">@${esc(draft.username)}</div>` : `<div class="muted-note" style="font-size:12px;">${localizedText(lang, 'Henüz kullanıcı adı yok', 'No username set')}</div>`}
                ${draft.bio ? `<p class="muted-note" style="margin:6px 0 0; font-size:12px; color:var(--ink);">${esc(draft.bio)}</p>` : ''}
              </div>
            </div>
            ${draft.styleTags?.length ? `<div class="wrap-row">${draft.styleTags.map((t) => `<span class="chip-pill compact selected">${esc(t)}</span>`).join('')}</div>` : ''}

            <div class="profile-follow-counts">
              <button type="button" class="profile-count-btn" id="phFollowersBtn"><strong>${followersCnt}</strong><span>${localizedText(lang, 'Takipçi', 'Followers')}</span></button>
              <button type="button" class="profile-count-btn" id="phFollowingBtn"><strong>${followingCnt}</strong><span>${localizedText(lang, 'Takip', 'Following')}</span></button>
            </div>

            <div class="btn-row">
              <button type="button" class="btn-secondary btn-sm" id="phGoEditBtn">${svgIcon('edit', 15)} ${localizedText(lang, 'Profili Düzenle', 'Edit Profile')}</button>
              <button type="button" class="btn-primary btn-sm" id="phShareProfileBtn">${svgIcon('share', 15)} ${localizedText(lang, 'Profilimi Paylaş', 'Share Profile')}</button>
            </div>
          </div>

          ${pendingRequests.length > 0 ? `
            <div class="card" style="background:var(--accent-soft); border-color:var(--accent); display:flex; flex-direction:column; gap:8px;">
              <strong style="font-size:13px;">${localizedText(lang, `Takip İstekleri (${pendingRequests.length})`, `Follow Requests (${pendingRequests.length})`)}</strong>
              ${pendingRequests.map((rq) => `
                <div style="display:flex; align-items:center; justify-content:space-between; gap:8px;">
                  <div style="display:flex; align-items:center; gap:8px; min-width:0;">
                    ${avatarCircleHtml(rq.avatarBase64, rq.username || rq.displayName, 32)}
                    <div style="min-width:0;">
                      <strong style="font-size:13px; display:block;">${esc(rq.displayName || rq.username || 'Kullanıcı')}</strong>
                      ${rq.username ? `<span class="muted-note" style="font-size:11px;">@${esc(rq.username)}</span>` : ''}
                    </div>
                  </div>
                  <div style="display:flex; gap:4px;">
                    <button type="button" class="btn-primary btn-sm" data-acc-req="${esc(rq.uid)}">${localizedText(lang, 'Onayla', 'Accept')}</button>
                    <button type="button" class="btn-danger-outline btn-sm" data-dec-req="${esc(rq.uid)}">${localizedText(lang, 'Reddet', 'Decline')}</button>
                  </div>
                </div>
              `).join('')}
            </div>
          ` : ''}

          ${renderWardrobeSummaryPublicCard(localSum, lang)}
          ${myBundle?.showcaseItems?.length ? renderShowcaseSectionsHtml(myBundle.showcaseItems, lang) : ''}
        `;

        mountEl.querySelector('#phGoEditBtn')?.addEventListener('click', () => {
          activeSubTab = 'edit';
          updateTabBtns();
          renderSubTab();
        });
        mountEl.querySelector('#phShareProfileBtn')?.addEventListener('click', async () => {
          let resolvedUid = uid;
          if (!normalizeUsername(draft.username) && !resolvedUid) {
            const auth = await ensureFirebaseAuth().catch(() => null);
            resolvedUid = auth?.uid || getStoredUid() || '';
            if (!resolvedUid) {
              showToast(localizedText(lang, 'Paylaşmak için sunucuya bağlanılamadı veya bir kullanıcı adı belirle', 'Connect to server or set a username before sharing'));
              return;
            }
          }
          const shareUrl = buildShareProfileUrl(draft.username, resolvedUid);
          trackFeature('profile_share');
          if (navigator.share) {
            try { await navigator.share({ title: `${draft.displayName || 'Dolabım'} · Dolabım`, url: shareUrl }); return; } catch {}
          }
          try {
            await navigator.clipboard.writeText(shareUrl);
            showToast(localizedText(lang, 'Profil linki kopyalandı', 'Profile link copied'));
          } catch {
            showToast(shareUrl);
          }
        });
        mountEl.querySelector('#phFollowersBtn')?.addEventListener('click', () => {
          if (!uid) return;
          openFollowListModal({ targetUid: uid, followers: true, openModal, onSelectUser: (t) => openPublicProfileModal({ ...ctx, targetUidOrUsername: t, isUsername: false }), lang, S });
        });
        mountEl.querySelector('#phFollowingBtn')?.addEventListener('click', () => {
          if (!uid) return;
          openFollowListModal({ targetUid: uid, followers: false, openModal, onSelectUser: (t) => openPublicProfileModal({ ...ctx, targetUidOrUsername: t, isUsername: false }), lang, S });
        });
        mountEl.querySelectorAll('[data-acc-req]').forEach((b) => b.addEventListener('click', async () => {
          await acceptFollowRequest(b.dataset.accReq).catch((e) => showToast(e?.message || 'Hata'));
          await loadCloudState();
        }));
        mountEl.querySelectorAll('[data-dec-req]').forEach((b) => b.addEventListener('click', async () => {
          if (!uid) return;
          await unfollowOrCancel(b.dataset.decReq, uid).catch((e) => showToast(e?.message || 'Hata'));
          await loadCloudState();
        }));
        if (myBundle?.showcaseItems) bindShowcaseItemClicks(mountEl, myBundle.showcaseItems, ctx);
      };

      const renderEdit = () => {
        const vis = draft.statsVisibility || DEFAULT_STATS_VISIBILITY;
        const visRows = [
          ['showTotalItems', localizedText(lang, 'Toplam Parça & Fotoğraf Sayısı', 'Total Clothes & Photos')],
          ['showFavoriteCount', localizedText(lang, 'Favori Parça Sayısı', 'Favorite Count')],
          ['showWishlistCount', localizedText(lang, 'İstek Listesi Sayısı', 'Wishlist Count')],
          ['showOutfitCount', localizedText(lang, 'Kayıtlı Kombin Sayısı', 'Saved Outfits Count')],
          ['showCategoryBreakdown', localizedText(lang, 'Kategori Dağılımı', 'Category Breakdown')],
          ['showTopColors', localizedText(lang, 'Baskın Renkler', 'Top Colors')],
          ['showTopSubCategories', localizedText(lang, 'Öne Çıkan Kıyafet Türleri', 'Top Clothing Types')],
          ['showEstimatedValue', localizedText(lang, 'Tahmini Dolap & İstek Değeri (₺)', 'Estimated Wardrobe Value')]
        ];
        const secRows = [
          ['favorites', localizedText(lang, 'Favori Parçalarım', 'My Favorites')],
          ['wishlist', localizedText(lang, 'İstek Listem', 'My Wishlist')],
          ['selected', localizedText(lang, 'Dolabımdan Seçtiklerim', 'Selected Wardrobe Pieces')],
          ['custom', localizedText(lang, 'Özel Yüklediğim Parçalar', 'Custom Uploaded Pieces')]
        ];
        const allItems = appState.items || [];

        mountEl.innerHTML = `
          <div class="card" style="display:flex; flex-direction:column; gap:10px;">
            <strong style="font-size:14px;">${localizedText(lang, 'Profil Fotoğrafı & Kimlik', 'Avatar & Identity')}</strong>
            <div style="display:flex; align-items:center; gap:12px;">
              ${avatarCircleHtml(draft.avatarBase64 || draft.avatarThumbBase64, draft.username || draft.displayName || 'D', 60)}
              <div class="wrap-row" style="flex:1;">
                <button type="button" class="btn-secondary btn-sm" id="peAvGal">${svgIcon('photoLibrary', 14)} ${localizedText(lang, 'Galeri', 'Gallery')}</button>
                <button type="button" class="btn-secondary btn-sm" id="peAvCam">${svgIcon('camera', 14)} ${localizedText(lang, 'Kamera', 'Camera')}</button>
                ${(draft.avatarBase64 || draft.avatarThumbBase64) ? `<button type="button" class="btn-danger-outline btn-sm" id="peAvDel">${localizedText(lang, 'Kaldır', 'Remove')}</button>` : ''}
              </div>
            </div>
            <label class="muted-note" style="font-size:11px; font-weight:600;">${localizedText(lang, 'Kullanıcı Adı (@)', 'Username (@)')}</label>
            <div style="display:flex; gap:6px;">
              <input type="text" class="input-field" id="peUsername" value="${esc(draft.username || '')}" placeholder="orn: kerem_style" maxlength="24" autocapitalize="off" spellcheck="false" style="flex:1;" />
              <button type="button" class="btn-secondary btn-sm" id="peCheckUserBtn">${localizedText(lang, 'Kontrol', 'Check')}</button>
            </div>
            <div id="peUserStatus" class="muted-note" style="font-size:11px;">${localizedText(lang, '3-24 karakter: a-z, 0-9, _ veya .', '3-24 chars: a-z, 0-9, _ or .')}</div>

            <label class="muted-note" style="font-size:11px; font-weight:600;">${localizedText(lang, 'Görünen Ad', 'Display Name')}</label>
            <input type="text" class="input-field" id="peDisplayName" value="${esc(draft.displayName || '')}" placeholder="Ad Soyad" maxlength="50" />

            <label class="muted-note" style="font-size:11px; font-weight:600;">${localizedText(lang, 'Açıklama / Bio', 'Bio')}</label>
            <textarea class="input-field" id="peBio" rows="2" maxlength="220" placeholder="${localizedText(lang, 'Stilini veya dolap temanı anlat...', 'Describe your style...')}">${esc(draft.bio || '')}</textarea>

            <label class="muted-note" style="font-size:11px; font-weight:600;">${localizedText(lang, 'Stil Etiketleri (En fazla 3)', 'Style Tags (Max 3)')}</label>
            <div class="wrap-row" id="peStyleTags">
              ${STYLE_VIBE_PRESETS.map((tag) => `<button type="button" class="chip-pill compact ${(draft.styleTags || []).includes(tag) ? 'selected' : ''}" data-ptag="${esc(tag)}">${esc(tag)}</button>`).join('')}
            </div>
          </div>

          <div class="card" style="display:flex; flex-direction:column; gap:10px;">
            <strong style="font-size:14px;">${localizedText(lang, 'Gizlilik Ayarı', 'Account Privacy')}</strong>
            <div class="seg-toggle">
              <button type="button" class="seg-btn ${!draft.isPrivate ? 'active' : ''}" id="pePublicBtn">${svgIcon('publicGlobe', 15)} ${localizedText(lang, 'Herkese Açık', 'Public')}</button>
              <button type="button" class="seg-btn ${draft.isPrivate ? 'active' : ''}" id="pePrivateBtn">${svgIcon('lock', 15)} ${localizedText(lang, 'Gizli Profil', 'Private')}</button>
            </div>
            <p class="muted-note" style="margin:0; font-size:11px;">
              ${draft.isPrivate
                ? localizedText(lang, 'Dolap özetin ve sergi parçaların yalnızca onayladığın takipçilere görünür.', 'Only approved followers can view your summary & showcase.')
                : localizedText(lang, 'Profil linkinle herkes dolap özetini ve seçtiğin vitrini görebilir.', 'Anyone with your link or searching can view your profile.')}
            </p>
          </div>

          <div class="card" style="display:flex; flex-direction:column; gap:8px;">
            <strong style="font-size:14px;">${localizedText(lang, 'Paylaşılan Dolap Özeti İstatistikleri', 'Shared Summary Statistics Visibility')}</strong>
            <p class="muted-note" style="margin:0 0 4px; font-size:11px;">${localizedText(lang, 'Profilinde hangi istatistiklerin görünüp gizleneceğini seç:', 'Choose which stats to show or hide on your shared profile:')}</p>
            ${visRows.map(([k, lbl]) => `
              <label class="profile-toggle-row">
                <span>${esc(lbl)}</span>
                <input type="checkbox" data-vis="${esc(k)}" ${vis[k] ? 'checked' : ''} />
              </label>
            `).join('')}
          </div>

          <div class="card" style="display:flex; flex-direction:column; gap:10px;">
            <strong style="font-size:14px;">${localizedText(lang, 'Vitrinde Sergilenecek Bölümler', 'Showcase Sections')}</strong>
            ${secRows.map(([secKey, lbl]) => `
              <label class="profile-toggle-row">
                <span>${esc(lbl)}</span>
                <input type="checkbox" data-sec="${esc(secKey)}" ${(draft.showcaseSections || []).includes(secKey) ? 'checked' : ''} />
              </label>
            `).join('')}

            ${(draft.showcaseSections || []).includes('selected') ? `
              <div style="margin-top:6px;">
                <span class="muted-note" style="font-size:11px; font-weight:600; display:block; margin-bottom:6px;">${localizedText(lang, 'Dolabından Sergileyeceğin Parçaları Seç (maks 12):', 'Pick Wardrobe Pieces to Showcase (max 12):')}</span>
                <div class="wrap-row" style="max-height:150px; overflow-y:auto;">
                  ${allItems.map((it) => {
                    const sel = (draft.selectedShowcaseItemIds || []).includes(it.id);
                    return `<button type="button" class="chip-pill compact ${sel ? 'selected' : ''}" data-sel-it="${esc(it.id)}">${esc(it.name || displaySubCategory(it.subCategory, lang))}</button>`;
                  }).join('')}
                </div>
              </div>
            ` : ''}

            ${(draft.showcaseSections || []).includes('custom') ? `
              <div style="margin-top:6px; display:flex; flex-direction:column; gap:8px;">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                  <span class="muted-note" style="font-size:11px; font-weight:600;">${localizedText(lang, 'Özel Parçalar', 'Custom Pieces')} (${(draft.customShowcaseItems || []).length}/8)</span>
                  <button type="button" class="btn-secondary btn-sm" id="peAddCustomBtn">${svgIcon('add', 14)} ${localizedText(lang, 'Fotoğraf Yükle', 'Upload Piece')}</button>
                </div>
                ${(draft.customShowcaseItems || []).map((cu) => `
                  <div style="display:flex; align-items:center; justify-content:space-between; gap:8px; padding:6px 8px; border-radius:10px; background:var(--secondary);">
                    <div style="display:flex; align-items:center; gap:8px; min-width:0;">
                      ${safeImgSrc(cu.photoBase64) ? `<img src="${safeImgSrc(cu.photoBase64)}" style="width:36px; height:36px; border-radius:8px; object-fit:cover;" />` : ''}
                      <span style="font-size:12px; font-weight:600;">${esc(cu.name)}</span>
                    </div>
                    <button type="button" class="btn-icon-ghost" data-del-custom="${esc(cu.id)}" style="color:var(--danger);">${svgIcon('deleteOutline', 16)}</button>
                  </div>
                `).join('')}
              </div>
            ` : ''}
          </div>

          <div class="card" style="display:flex; flex-direction:column; gap:8px;" id="peBlockedSection">
            <div style="display:flex; align-items:center; gap:6px;">
              <span style="color:var(--danger); display:inline-flex;">${svgIcon('block', 16)}</span>
              <strong style="font-size:14px;">${localizedText(lang, `Engellenen Hesaplar (${blockedAccounts.length})`, `Blocked Accounts (${blockedAccounts.length})`)}</strong>
            </div>
            ${blockedAccounts.length === 0
              ? `<div class="muted-note" style="font-size:12px;">${localizedText(lang, 'Engellediğin kullanıcı bulunmuyor.', 'You have not blocked anyone.')}</div>`
              : blockedAccounts.map((bl) => `
                <div style="display:flex; align-items:center; justify-content:space-between; gap:8px; padding:6px 8px; border-radius:10px; background:var(--secondary);">
                  <div style="display:flex; align-items:center; gap:8px; min-width:0; flex:1;">
                    ${avatarCircleHtml(bl.avatarBase64, bl.displayName || bl.username, 30)}
                    <div style="min-width:0;">
                      <div style="font-size:12px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(bl.displayName || bl.username || 'Kullanıcı')}</div>
                      ${bl.username ? `<div class="muted-note" style="font-size:11px;">@${esc(bl.username)}</div>` : ''}
                    </div>
                  </div>
                  <button type="button" class="btn-secondary btn-sm" data-unblock-uid="${esc(bl.uid)}">${localizedText(lang, 'Engeli Kaldır', 'Unblock')}</button>
                </div>
              `).join('')}
          </div>
        `;

        const pickAv = (inputId) => {
          bindDraftInputs();
          pickFilesFromInput(inputId).then(async (files) => {
            const f = files[0];
            if (!f) return;
            try {
              const raw = await fileToOrientedDataUrl(f, 512);
              const pair = await compressAvatarPair(raw);
              draft = { ...draft, ...pair };
              renderEdit();
            } catch {
              showToast(localizedText(lang, 'Fotoğraf yüklenemedi', 'Could not load image'));
            }
          });
        };
        mountEl.querySelector('#peAvGal')?.addEventListener('click', () => pickAv('avatarGalleryInput'));
        mountEl.querySelector('#peAvCam')?.addEventListener('click', () => pickAv('avatarCameraInput'));
        mountEl.querySelector('#peAvDel')?.addEventListener('click', () => {
          bindDraftInputs();
          draft = { ...draft, avatarBase64: '', avatarThumbBase64: '' };
          renderEdit();
        });

        mountEl.querySelector('#peCheckUserBtn')?.addEventListener('click', async () => {
          bindDraftInputs();
          const stEl = mountEl.querySelector('#peUserStatus');
          const norm = normalizeUsername(draft.username);
          if (!isValidUsername(norm)) {
            if (stEl) { stEl.textContent = localizedText(lang, 'Geçersiz format (3-24 karakter: a-z, 0-9, _, .)', 'Invalid format'); stEl.style.color = 'var(--danger)'; }
            return;
          }
          try {
            const ok = await checkUsernameAvailable(norm);
            if (stEl) {
              stEl.textContent = ok ? localizedText(lang, `@${norm} uygun!`, `@${norm} is available!`) : localizedText(lang, `@${norm} başka biri tarafından alınmış`, `@${norm} is taken`);
              stEl.style.color = ok ? 'var(--olive)' : 'var(--danger)';
            }
          } catch (e) {
            if (stEl) { stEl.textContent = e?.message || 'Hata'; stEl.style.color = 'var(--danger)'; }
          }
        });

        mountEl.querySelectorAll('[data-ptag]').forEach((b) => b.addEventListener('click', () => {
          bindDraftInputs();
          const tag = b.dataset.ptag;
          const cur = [...(draft.styleTags || [])];
          if (cur.includes(tag)) draft.styleTags = cur.filter((x) => x !== tag);
          else if (cur.length < 3) draft.styleTags = [...cur, tag];
          else showToast(localizedText(lang, 'En fazla 3 stil etiketi seçebilirsin', 'Up to 3 style tags'));
          renderEdit();
        }));

        mountEl.querySelector('#pePublicBtn')?.addEventListener('click', () => { bindDraftInputs(); draft.isPrivate = false; renderEdit(); });
        mountEl.querySelector('#pePrivateBtn')?.addEventListener('click', () => { bindDraftInputs(); draft.isPrivate = true; renderEdit(); });

        mountEl.querySelectorAll('[data-vis]').forEach((cb) => cb.addEventListener('change', () => {
          draft.statsVisibility = { ...(draft.statsVisibility || DEFAULT_STATS_VISIBILITY), [cb.dataset.vis]: Boolean(cb.checked) };
        }));

        mountEl.querySelectorAll('[data-sec]').forEach((cb) => cb.addEventListener('change', () => {
          bindDraftInputs();
          const sec = cb.dataset.sec;
          const cur = new Set(draft.showcaseSections || []);
          if (cb.checked) cur.add(sec); else cur.delete(sec);
          draft.showcaseSections = [...cur];
          renderEdit();
        }));

        mountEl.querySelectorAll('[data-sel-it]').forEach((b) => b.addEventListener('click', () => {
          bindDraftInputs();
          const id = b.dataset.selIt;
          const cur = [...(draft.selectedShowcaseItemIds || [])];
          if (cur.includes(id)) draft.selectedShowcaseItemIds = cur.filter((x) => x !== id);
          else if (cur.length < 12) draft.selectedShowcaseItemIds = [...cur, id];
          renderEdit();
        }));

        mountEl.querySelector('#peAddCustomBtn')?.addEventListener('click', () => {
          bindDraftInputs();
          if ((draft.customShowcaseItems || []).length >= 8) { showToast(localizedText(lang, 'En fazla 8 özel parça eklenebilir', 'Max 8 custom items')); return; }
          pickFilesFromInput('customShowcaseInput').then(async (files) => {
            const f = files[0];
            if (!f) return;
            try {
              const raw = await fileToOrientedDataUrl(f, 800);
              const compressed = await compressGarmentPhotoForCloud(raw, 680, 210000);
              const nm = window.prompt(localizedText(lang, 'Özel parça adı:', 'Custom piece name:'), localizedText(lang, 'Özel Parçam', 'My Custom Piece'));
              if (nm === null) return;
              draft.customShowcaseItems = [
                ...(draft.customShowcaseItems || []),
                {
                  id: createId('sc'),
                  name: (nm.trim() || localizedText(lang, 'Özel Parçam', 'Custom Piece')).slice(0, 120),
                  category: CATEGORIES[0].id,
                  priceText: '',
                  productUrl: '',
                  notes: '',
                  photoBase64: compressed,
                  createdAtMs: Date.now()
                }
              ];
              renderEdit();
            } catch {
              showToast(localizedText(lang, 'Görsel eklenemedi', 'Could not add image'));
            }
          });
        });

        mountEl.querySelectorAll('[data-del-custom]').forEach((b) => b.addEventListener('click', () => {
          bindDraftInputs();
          draft.customShowcaseItems = (draft.customShowcaseItems || []).filter((x) => x.id !== b.dataset.delCustom);
          renderEdit();
        }));

        mountEl.querySelectorAll('[data-unblock-uid]').forEach((b) => b.addEventListener('click', async () => {
          const targetUid = b.dataset.unblockUid;
          b.disabled = true;
          try {
            await unblockUser(targetUid);
            blockedAccounts = blockedAccounts.filter((x) => x.uid !== targetUid);
            renderEdit();
            showToast(localizedText(lang, 'Engel kaldırıldı', 'Unblocked'));
          } catch (e) {
            showToast(e?.message || 'Hata');
            b.disabled = false;
          }
        }));
      };

      footerSaveBtn?.addEventListener('click', async () => {
        if (saving) return;
        bindDraftInputs();
        saving = true;
        syncFooterForTab();
        try {
          draft = await saveAndSyncProfile(draft, appState.items || [], appState.outfits || [], photoMap, lang);
          onProfileUpdated?.();
          activeSubTab = 'preview';
          updateTabBtns();
          renderSubTab();
          await loadCloudState();
          showToast(localizedText(lang, 'Profilin kaydedildi ve güncellendi', 'Profile saved & published'));
        } catch (e) {
          showToast(e?.message || 'Hata');
        } finally {
          saving = false;
          syncFooterForTab();
          if (activeSubTab === 'edit') renderEdit();
        }
      });

      const renderDiscover = () => {
        mountEl.innerHTML = `
          <div style="display:flex; gap:8px;">
            <input type="text" class="input-field" id="pdSearchInput" value="${esc(searchQuery)}" placeholder="${localizedText(lang, '@kullaniciadi veya isim ara...', 'Search @username or name...')}" style="flex:1;" />
            <button type="button" class="btn-primary btn-sm" id="pdSearchBtn">${svgIcon('search', 16)}</button>
          </div>
          <div id="pdResults" style="display:flex; flex-direction:column; gap:8px;">
            ${searchResults.length === 0 ? `<div class="card muted-note" style="text-align:center; padding:24px;">${localizedText(lang, 'Henüz eşleşen profil yok.', 'No matching profiles found.')}</div>` : searchResults.map((p) => `
              <div class="card" data-prof-uid="${esc(p.uid)}" style="padding:10px; display:flex; align-items:center; gap:10px; cursor:pointer;">
                ${avatarCircleHtml(p.avatarBase64, p.username || p.displayName, 44)}
                <div style="min-width:0; flex:1;">
                  <div style="display:flex; align-items:center; gap:6px;">
                    <strong style="font-size:14px;">${esc(p.displayName || p.username || 'Kullanıcı')}</strong>
                    ${p.isPrivate ? svgIcon('lock', 13) : ''}
                  </div>
                  ${p.username ? `<div class="accent-note" style="font-size:12px;">@${esc(p.username)}</div>` : ''}
                  ${p.bio ? `<div class="muted-note" style="font-size:11px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(p.bio)}</div>` : ''}
                </div>
                <span class="chip-pill compact">${localizedText(lang, 'Gör', 'View')}</span>
              </div>
            `).join('')}
          </div>
        `;

        const doSearch = async () => {
          searchQuery = mountEl.querySelector('#pdSearchInput')?.value || '';
          try {
            searchResults = await searchProfiles(searchQuery);
            renderDiscover();
          } catch (e) {
            showToast(e?.message || 'Hata');
          }
        };
        mountEl.querySelector('#pdSearchBtn')?.addEventListener('click', doSearch);
        mountEl.querySelector('#pdSearchInput')?.addEventListener('keydown', (e) => {
          if (e.key === 'Enter') doSearch();
        });
        mountEl.querySelectorAll('[data-prof-uid]').forEach((row) => {
          row.addEventListener('click', () => {
            openPublicProfileModal({ ...ctx, targetUidOrUsername: row.dataset.profUid, isUsername: false });
          });
        });
      };

      const renderSubTab = () => {
        if (activeSubTab === 'preview') renderPreview();
        else if (activeSubTab === 'edit') renderEdit();
        else renderDiscover();
      };

      modalEl.querySelectorAll('[data-phtab]').forEach((b) => {
        b.addEventListener('click', () => {
          if (activeSubTab === 'edit') bindDraftInputs();
          activeSubTab = b.dataset.phtab;
          updateTabBtns();
          if (activeSubTab === 'discover' && searchResults.length === 0) loadCloudState();
          else renderSubTab();
        });
      });

      syncFooterForTab();
      renderSubTab();
      loadCloudState();
    }
  });
}

// ==========================================
// 7) OUTFIT SHARE LINK & VIEWER MODALS
// ==========================================

export async function openShareOutfitModal({
  title,
  pieces,
  harmonyScore = 80,
  harmonyNote = '',
  weatherLabel = '',
  photoMap,
  openModal,
  showToast,
  lang,
  S,
  items,
  outfits,
  onProfileUpdated
}) {
  if (!pieces || !pieces.length) return;
  let profile = loadLocalProfile();
  let shared = null;
  const st = S();
  const outfitTitle = String(title || localizedText(lang, 'Kombinim', 'My Outfit'));

  openModal({
    title: localizedText(lang, 'Kombini Paylaş', 'Share Outfit'),
    subtitle: outfitTitle,
    bodyHtml: `
      <div id="soLoading" class="card" style="text-align:center; padding:26px 14px;">
        <div class="muted-note">${localizedText(lang, 'Kombin paylaşım linki hazırlanıyor...', 'Creating outfit link...')}</div>
      </div>
      <div id="soContent" style="display:none; flex-direction:column; gap:12px;"></div>
    `,
    footerHtml: `<button type="button" class="btn-secondary" id="soCloseBtn" style="width:100%;">${esc(st.close)}</button>`,
    onMount: async (close, modalEl) => {
      modalEl.querySelector('#soCloseBtn')?.addEventListener('click', close);
      const loadingEl = modalEl.querySelector('#soLoading');
      const contentEl = modalEl.querySelector('#soContent');

      const renderSharedBody = () => {
        if (!shared) return;
        const link = buildShareOutfitUrl(shared.shareId);
        const hasUsername = Boolean(normalizeUsername(profile.username));
        const ownerTag = hasUsername ? `@${shared.ownerUsername}` : (shared.ownerDisplayName || localizedText(lang, 'İsimsiz Kullanıcı', 'Anonymous'));

        contentEl.innerHTML = `
          <div class="card" style="display:flex; flex-direction:column; gap:10px;">
            <div style="display:flex; gap:12px; align-items:center;">
              ${avatarCircleHtml(profile.avatarThumbBase64 || profile.avatarBase64, ownerTag, 38)}
              <div style="min-width:0; flex:1;">
                <strong style="font-size:15px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(shared.title)}</strong>
                <div style="font-size:12px; font-weight:600; color:var(--accent); margin-top:2px;">${esc(ownerTag)}</div>
                <div class="muted-note" style="font-size:11px; margin-top:2px;">${shared.pieces.length} ${localizedText(lang, 'parça', 'pieces')} · ${shared.harmonyScore}/100</div>
              </div>
            </div>
            ${Array.isArray(shared.pieces) && shared.pieces.length > 0 ? `
              <div style="display:flex; gap:6px; overflow-x:auto; padding-bottom:2px;">
                ${shared.pieces.map((p) => {
                  const pu = safeImgSrc(p.photoBase64 || '');
                  return `
                    <div style="width:52px; height:52px; border-radius:10px; border:1px solid var(--border); background:var(--secondary); overflow:hidden; display:flex; align-items:center; justify-content:center; flex-shrink:0;" title="${esc(p.name || '')}">
                      ${pu ? `<img src="${pu}" alt="${esc(p.name || '')}" style="width:100%; height:100%; object-fit:cover;" />` : `<span style="color:var(--ink-muted);">${svgIcon('checkroom', 20)}</span>`}
                    </div>
                  `;
                }).join('')}
              </div>
            ` : ''}
          </div>

          <div class="card" style="background:var(--secondary); display:flex; flex-direction:column; gap:8px;">
            <span class="muted-note" style="font-size:11px; font-weight:600;">${localizedText(lang, 'Kombin Bağlantısı (Web & Mobil Uygulamada Açılır)', 'Outfit Link (Opens in Web & Mobile App)')}</span>
            <input type="text" class="input-field" id="soLinkInput" readonly value="${esc(link)}" style="font-size:12px;" />
            <div class="btn-row">
              <button type="button" class="btn-secondary btn-sm" id="soCopyBtn">${svgIcon('contentCopy', 15)} ${localizedText(lang, 'Kopyala', 'Copy')}</button>
              <button type="button" class="btn-primary btn-sm" id="soNativeShareBtn">${svgIcon('share', 15)} ${localizedText(lang, 'Paylaş', 'Share')}</button>
            </div>
          </div>

          ${!hasUsername ? `
            <div class="share-username-callout" id="soQuickUserBox">
              <div style="display:flex; align-items:center; gap:8px;">
                <span style="color:var(--accent);">${svgIcon('personAdd', 18)}</span>
                <strong style="font-size:13px;">${localizedText(lang, 'Henüz bir kullanıcı adın yok', 'No username yet')}</strong>
              </div>
              <p class="muted-note" style="margin:4px 0 8px; font-size:12px;">
                ${localizedText(lang, 'Arkadaşlarının bu kombini senden geldiğini görebilmesi için hemen kullanıcı adı oluşturabilirsin.', 'Create a username right now so friends see this outfit comes from you.')}
              </p>
              <button type="button" class="btn-accent-outline btn-sm" id="soRevealQuickUserBtn" style="width:100%;">${svgIcon('personAdd', 15)} ${localizedText(lang, 'Hemen Kullanıcı Adı Oluştur', 'Create Username Now')}</button>
              <div id="soQuickUserForm" style="display:none; margin-top:10px; flex-direction:column; gap:8px;">
                <input type="text" class="input-field" id="soQuickUserInput" placeholder="${localizedText(lang, 'örn: kerem_style', 'e.g. alex_style')}" maxlength="24" autocapitalize="off" spellcheck="false" />
                <div class="muted-note" id="soQuickUserHint" style="font-size:11px;">${localizedText(lang, '3-24 karakter: küçük harf, rakam, _ veya .', '3-24 chars: lowercase letters, numbers, _ or .')}</div>
                <button type="button" class="btn-primary btn-sm" id="soQuickUserSaveBtn">${svgIcon('check', 15)} ${localizedText(lang, 'Oluştur ve Linke Ekle', 'Create & Attach to Link')}</button>
              </div>
            </div>
          ` : ''}
        `;

        contentEl.querySelector('#soCopyBtn')?.addEventListener('click', async () => {
          try {
            await navigator.clipboard.writeText(link);
            showToast(localizedText(lang, 'Link panoya kopyalandı', 'Link copied to clipboard'));
          } catch {
            contentEl.querySelector('#soLinkInput')?.select();
            showToast(link);
          }
        });

        contentEl.querySelector('#soNativeShareBtn')?.addEventListener('click', async () => {
          if (navigator.share) {
            try {
              await navigator.share({ title: shared.title, text: `${shared.title} - Dolabım`, url: link });
              return;
            } catch {
              // ignore cancel
            }
          }
          try {
            await navigator.clipboard.writeText(link);
            showToast(localizedText(lang, 'Link panoya kopyalandı', 'Link copied to clipboard'));
          } catch {
            showToast(link);
          }
        });

        contentEl.querySelector('#soRevealQuickUserBtn')?.addEventListener('click', () => {
          const btn = contentEl.querySelector('#soRevealQuickUserBtn');
          const form = contentEl.querySelector('#soQuickUserForm');
          if (btn) btn.style.display = 'none';
          if (form) form.style.display = 'flex';
          contentEl.querySelector('#soQuickUserInput')?.focus();
        });

        contentEl.querySelector('#soQuickUserSaveBtn')?.addEventListener('click', async () => {
          const raw = contentEl.querySelector('#soQuickUserInput')?.value || '';
          const norm = normalizeUsername(raw);
          const hint = contentEl.querySelector('#soQuickUserHint');
          if (!isValidUsername(norm)) {
            if (hint) { hint.textContent = localizedText(lang, 'Geçersiz kullanıcı adı (3-24 karakter: a-z, 0-9, _, .)', 'Invalid username'); hint.style.color = 'var(--danger)'; }
            return;
          }
          const saveBtn = contentEl.querySelector('#soQuickUserSaveBtn');
          if (saveBtn) saveBtn.disabled = true;
          try {
            const avail = await checkUsernameAvailable(norm);
            if (!avail) {
              if (hint) { hint.textContent = localizedText(lang, `@${norm} başka biri tarafından alınmış`, `@${norm} is taken`); hint.style.color = 'var(--danger)'; }
              if (saveBtn) saveBtn.disabled = false;
              return;
            }
            profile = await saveAndSyncProfile(
              { ...profile, username: norm, displayName: profile.displayName || norm },
              items || [],
              outfits || [],
              photoMap,
              lang
            );
            shared = await updateSharedOutfitOwner(shared, profile);
            onProfileUpdated?.();
            renderSharedBody();
            showToast(localizedText(lang, `@${norm} kullanıcı adı linke eklendi`, `@${norm} attached to link`));
          } catch (err) {
            if (hint) { hint.textContent = err?.message || 'Hata'; hint.style.color = 'var(--danger)'; }
            if (saveBtn) saveBtn.disabled = false;
          }
        });
      };

      const attemptOutfitShare = async () => {
        loadingEl.style.display = '';
        loadingEl.innerHTML = `<div class="muted-note">${localizedText(lang, 'Kombin paylaşım linki hazırlanıyor...', 'Creating outfit link...')}</div>`;
        contentEl.style.display = 'none';
        try {
          shared = await shareOutfit({ title: outfitTitle, pieces, harmonyScore, harmonyNote, weatherLabel, profile, photoMap, lang });
          loadingEl.style.display = 'none';
          contentEl.style.display = 'flex';
          renderSharedBody();
        } catch (err) {
          loadingEl.innerHTML = `
            <div style="color:var(--danger); font-size:13px; margin-bottom:10px;">${esc(err?.message || localizedText(lang, 'Kombin paylaşılamadı', 'Could not create outfit link'))}</div>
            <button type="button" class="btn-secondary btn-sm" id="soRetryBtn">${localizedText(lang, 'Tekrar Dene', 'Retry')}</button>
          `;
          loadingEl.querySelector('#soRetryBtn')?.addEventListener('click', attemptOutfitShare);
        }
      };
      await attemptOutfitShare();
    }
  });
}

export function openSharedOutfitViewerModal({
  shareId,
  openModal,
  showToast,
  lang,
  S,
  appState,
  savePhotoDataUrl,
  createId,
  commitState,
  syncPhotosAndPersonalProfile,
  renderCurrentTab,
  onOpenPublicProfile
}) {
  const st = S();
  openModal({
    title: localizedText(lang, 'Paylaşılan Kombin', 'Shared Outfit'),
    fullscreen: true,
    bodyHtml: `<div id="sovBody" style="display:flex; flex-direction:column; gap:12px;"><div class="card muted-note" style="text-align:center; padding:28px;">${localizedText(lang, 'Kombin yükleniyor...', 'Loading outfit...')}</div></div>`,
    footerHtml: `<button type="button" class="btn-secondary" id="sovCloseBtn" style="width:100%;">${esc(st.close)}</button>`,
    onMount: async (close, modalEl) => {
      modalEl.querySelector('#sovCloseBtn')?.addEventListener('click', close);
      const bodyEl = modalEl.querySelector('#sovBody');
      try {
        const [shared, likeInit] = await Promise.all([
          fetchSharedOutfit(shareId),
          fetchLikeInfo(`outfit_${shareId}`)
        ]);
        if (!shared) {
          bodyEl.innerHTML = `<div class="card muted-note" style="text-align:center; padding:28px;">${localizedText(lang, 'Paylaşılan kombin bulunamadı veya silinmiş.', 'Shared outfit not found.')}</div>`;
          return;
        }
        let { liked, count } = likeInit;
        const ownerTag = shared.ownerUsername ? `@${shared.ownerUsername}` : (shared.ownerDisplayName || localizedText(lang, 'Dolabım Kullanıcısı', 'DressKit User'));

        const renderBody = () => {
          bodyEl.innerHTML = `
            <div class="card" id="sovOwnerRow" style="display:flex; align-items:center; justify-content:space-between; gap:10px; cursor:pointer;">
              <div style="display:flex; align-items:center; gap:10px; min-width:0;">
                ${avatarCircleHtml(shared.ownerAvatar, ownerTag, 38)}
                <div style="min-width:0;">
                  <strong style="font-size:14px; display:block;">${esc(shared.ownerDisplayName || ownerTag)}</strong>
                  ${shared.ownerUsername ? `<span class="accent-note" style="font-size:12px;">@${esc(shared.ownerUsername)}</span>` : ''}
                </div>
              </div>
              <span class="chip-pill compact">${localizedText(lang, 'Profile Git >', 'Profile >')}</span>
            </div>

            <div class="card" style="display:flex; flex-direction:column; gap:8px;">
              <div style="display:flex; align-items:center; justify-content:space-between; gap:8px;">
                <strong style="font-size:17px;">${esc(shared.title)}</strong>
                <div style="display:flex; align-items:center; gap:6px;">
                  <span class="chip-pill compact selected">${shared.harmonyScore}/100</span>
                  <button type="button" class="like-pill-btn ${liked ? 'liked' : ''}" id="sovLikeBtn">${svgIcon(liked ? 'favoriteFilled' : 'favoriteBorder', 16)} <span>${count}</span></button>
                </div>
              </div>
              ${shared.harmonyNote ? `<div class="muted-note" style="font-size:12px;">${esc(shared.harmonyNote)}</div>` : ''}
              ${shared.weatherLabel ? `<div class="accent-note" style="font-size:12px;">${localizedText(lang, 'Hava', 'Weather')}: ${esc(shared.weatherLabel)}</div>` : ''}
              <div class="muted-note" style="font-size:11px;">${shared.pieces.length} ${localizedText(lang, 'parça', 'pieces')}</div>
            </div>

            <div style="display:flex; flex-direction:column; gap:8px;">
              ${shared.pieces.map((p, idx) => {
                const ph = safeImgSrc(p.photoBase64 || '');
                return `
                  <div class="card" style="display:flex; align-items:center; gap:10px; padding:10px;">
                    <div style="width:60px; height:60px; border-radius:10px; background:#fff; border:1px solid var(--border); display:flex; align-items:center; justify-content:center; flex-shrink:0; overflow:hidden;">
                      ${ph ? `<img src="${ph}" alt="${esc(p.name)}" style="width:100%; height:100%; object-fit:contain;" />` : svgIcon('checkroom', 24)}
                    </div>
                    <div style="min-width:0; flex:1;">
                      <strong style="font-size:13px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${esc(p.name)}</strong>
                      <div class="muted-note" style="font-size:11px;">${esc(displayCategory(p.category, lang))} · ${esc(displaySubCategory(p.subCategory, lang))}</div>
                      ${p.priceText ? `<div class="accent-note" style="font-size:11px; font-weight:600;">${esc(p.priceText)}</div>` : ''}
                    </div>
                    <button type="button" class="btn-secondary btn-sm" data-sov-wish="${idx}">${localizedText(lang, 'İstek', 'Wish')}</button>
                  </div>
                `;
              }).join('')}
            </div>

            <button type="button" class="btn-primary" id="sovSaveAllBtn" style="width:100%;">${svgIcon('bookmarkAdd', 16)} ${localizedText(lang, 'Kombini Dolabıma Kaydet', 'Save Outfit to Closet')}</button>
          `;

          bodyEl.querySelector('#sovOwnerRow')?.addEventListener('click', () => {
            if (shared.ownerUid) {
              close();
              onOpenPublicProfile?.(shared.ownerUid, true);
            }
          });
          bodyEl.querySelector('#sovLikeBtn')?.addEventListener('click', async () => {
            try {
              const res = await toggleLike(`outfit_${shareId}`);
              liked = res.liked;
              count = res.count;
              renderBody();
            } catch (e) {
              showToast(e?.message || 'Hata');
            }
          });
          bodyEl.querySelectorAll('[data-sov-wish]').forEach((btn) => btn.addEventListener('click', async () => {
            const p = shared.pieces[Number(btn.dataset.sovWish)];
            if (!p) return;
            btn.disabled = true;
            await importRemoteGarmentLocally({
              itemData: { ...p, photos: p.photoBase64 ? [p.photoBase64] : [] },
              asWishlist: true,
              appState,
              savePhotoDataUrl,
              createId,
              commitState,
              syncPhotosAndPersonalProfile,
              lang
            });
            showToast(localizedText(lang, 'İstek listene eklendi', 'Added to wishlist'));
            renderCurrentTab?.();
            btn.disabled = false;
          }));
          bodyEl.querySelector('#sovSaveAllBtn')?.addEventListener('click', async () => {
            const saveBtn = bodyEl.querySelector('#sovSaveAllBtn');
            if (saveBtn) saveBtn.disabled = true;
            try {
              const importedIds = [];
              for (const p of shared.pieces) {
                const it = await importRemoteGarmentLocally({
                  itemData: { ...p, photos: p.photoBase64 ? [p.photoBase64] : [] },
                  asWishlist: false,
                  appState,
                  savePhotoDataUrl,
                  createId,
                  commitState,
                  syncPhotosAndPersonalProfile,
                  lang
                });
                if (it?.id) importedIds.push(it.id);
              }
              const newOutfit = {
                id: createId('outfit'),
                title: (shared.title || localizedText(lang, 'Paylaşılan Kombin', 'Shared Outfit')).slice(0, 120),
                itemIds: importedIds,
                season: 'ALL',
                harmonyScore: shared.harmonyScore,
                harmonyNote: shared.harmonyNote,
                weatherTag: shared.weatherLabel || '',
                notes: shared.notes || '',
                createdAt: Date.now()
              };
              appState.outfits = [newOutfit, ...(appState.outfits || [])];
              commitState();
              trackFeature('shared_outfit_import');
              showToast(localizedText(lang, 'Kombin dolabına kaydedildi', 'Outfit saved to closet'));
              renderCurrentTab?.();
              close();
            } catch (e) {
              showToast(e?.message || 'Hata');
              if (saveBtn) saveBtn.disabled = false;
            }
          });
        };

        renderBody();
      } catch (err) {
        bodyEl.innerHTML = `<div class="card muted-note" style="color:var(--danger);">${esc(err?.message || 'Hata')}</div>`;
      }
    }
  });
}
