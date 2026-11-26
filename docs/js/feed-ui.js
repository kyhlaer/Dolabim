// UI Rendering & Modals for Social Feed, Rich Media Composer/Editor, Comments+GIFs & Activity Heart (v1.43.278)

import { localizedText } from './constants.js';
import { svgIcon } from './icons.js';
import { ensureFirebaseAuth, getStoredUid, trackFeature } from './analytics.js';
import { getGifPresets, isSafeExternalGifUrl, isSafeGifDataUrl } from './gif-library.js';
import {
  escHtml,
  safeImgSrc,
  sanitizeErrorMessage
} from './security.js';
import {
  acceptFollowRequest,
  fetchPendingFollowRequests,
  fetchProfileBundle,
  followUser,
  importRemoteGarmentLocally,
  loadLocalProfile,
  unfollowOrCancel
} from './social.js';
import {
  addPostComment,
  buildSharePostUrl,
  canEditPostCaption,
  compressDataUrlForCloud,
  createSocialPost,
  deletePostComment,
  deleteSocialPost,
  editPostCaption,
  editRemainingMinutes,
  fetchFeedPosts,
  fetchMyNotifications,
  fetchPostComments,
  fetchPostLikeState,
  fetchPostLikers,
  fetchSinglePost,
  loadPostVideoBlobUrl,
  markAllNotificationsRead,
  markNotificationRead,
  SOCIAL_POST_KINDS,
  togglePostLike
} from './social-feed.js';
import {
  avatarCircleHtml,
  bindLongPressElement,
  openAvatarZoomModal,
  profileIconButtonHtml,
  requireUsernameOrPrompt
} from './profile-ui.js';

const FEED_FILTERS = [
  { id: 'ALL', tr: 'Tümü', en: 'All' },
  { id: 'FOLLOWING', tr: 'Takip', en: 'Following' },
  { id: 'outfit', tr: 'Kombin', en: 'Outfits' },
  { id: 'new_piece', tr: 'Yeni Parça', en: 'New Pieces' },
  { id: 'look', tr: 'Look', en: 'Looks' },
  { id: 'video', tr: 'Video', en: 'Videos' }
];

const PHOTO_EDITOR_FILTERS = [
  { id: 'original', tr: 'Orijinal', en: 'Original', css: 'none' },
  { id: 'warm', tr: 'Sıcak', en: 'Warm', css: 'sepia(0.24) saturate(1.18) brightness(1.03)' },
  { id: 'cool', tr: 'Soğuk', en: 'Cool', css: 'hue-rotate(12deg) saturate(0.92) brightness(1.02)' },
  { id: 'bw', tr: 'Siyah-Beyaz', en: 'B&W', css: 'grayscale(1) contrast(1.12)' },
  { id: 'vivid', tr: 'Canlı', en: 'Vivid', css: 'saturate(1.42) contrast(1.08)' },
  { id: 'editorial', tr: 'Editorial', en: 'Editorial', css: 'saturate(0.76) contrast(1.16)' }
];

let feedPostsCache = [];
let feedLoadError = '';
let feedRequest = 0;
let feedLoadedOnce = false;
let feedActiveFilter = 'ALL';
const feedLikesCache = new Map();

function fmtSocialRel(ms) {
  if (!ms) return '';
  const diff = Math.max(0, Math.floor((Date.now() - ms) / 1000));
  if (diff < 60) return 'az önce';
  if (diff < 3600) return `${Math.floor(diff / 60)} dk`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} sa`;
  return new Date(ms).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short' });
}

function fileToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(new Error('Dosya okunamadı'));
    reader.readAsDataURL(file);
  });
}

function safeCommentMediaSrc(c) {
  if (c?.mediaUrl && isSafeExternalGifUrl(c.mediaUrl)) return c.mediaUrl;
  if (c?.mediaBase64 && (isSafeGifDataUrl(c.mediaBase64) || safeImgSrc(c.mediaBase64))) return c.mediaBase64;
  return '';
}

export function activityHeartButtonHtml(id, unreadCount = 0) {
  const cnt = Number(unreadCount) || 0;
  return `
    <button type="button" class="activity-heart-btn ${cnt > 0 ? 'has-unread' : ''}" id="${escHtml(id)}" title="Hareketler & Takip İstekleri" aria-label="Hareketler">
      ${svgIcon(cnt > 0 ? 'favoriteFilled' : 'favoriteBorder', 20)}
      ${cnt > 0 ? `<span class="activity-badge">${cnt > 9 ? '9+' : cnt}</span>` : ''}
    </button>
  `;
}

export function chatInboxButtonHtml(id, unreadCount = 0) {
  const cnt = Number(unreadCount) || 0;
  return `
    <button type="button" class="social-chat-btn ${cnt > 0 ? 'has-unread' : ''}" id="${escHtml(id)}" title="Sohbet" aria-label="Sohbet">
      ${svgIcon('chatForum', 19)}
      ${cnt > 0 ? `<span class="activity-badge">${cnt > 9 ? '9+' : cnt}</span>` : ''}
    </button>
  `;
}

// ==========================================
// 1) ACTIVITY HEART & FOLLOW REQUESTS MODAL
// ==========================================
export function openActivityHeartModal({
  openModal,
  showToast,
  lang = 'TR',
  S,
  appState = {},
  photoMap = new Map(),
  onProfileUpdated,
  onOpenPublicProfile,
  onOpenPost,
  onOpenDmUid,
  onUnreadUpdated
}) {
  trackFeature('notif_open');
  const st = S();
  openModal({
    title: localizedText(lang, 'Hareketler & Takip İstekleri', 'Activity & Follow Requests'),
    fullscreen: true,
    bodyHtml: `<div id="actMount" style="display:flex; flex-direction:column; gap:12px;"><div class="card muted-note" style="text-align:center; padding:26px;">${localizedText(lang, 'Yükleniyor...', 'Loading...')}</div></div>`,
    footerHtml: `<button type="button" class="btn-secondary" id="actCloseBtn" style="width:100%;">${escHtml(st.close)}</button>`,
    onMount: async (close, modalEl) => {
      modalEl.querySelector('#actCloseBtn')?.addEventListener('click', close);
      const mount = modalEl.querySelector('#actMount');
      const acceptedState = new Map();

      const loadAndRender = async () => {
        try {
          const [reqs, rawNotifs] = await Promise.all([
            fetchPendingFollowRequests().catch(() => []),
            fetchMyNotifications().catch(() => [])
          ]);
          const notifs = rawNotifs.filter((n) => n.type !== 'dm_message' && n.type !== 'dm_request' && n.type !== 'dm_post_reply');
          const unread = notifs.filter((n) => !n.read).length;
          onUnreadUpdated?.(reqs.length + unread, reqs.length, unread);

          mount.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <strong style="font-size:14px;">${localizedText(lang, `Takip İstekleri (${reqs.length})`, `Follow Requests (${reqs.length})`)}</strong>
              ${unread > 0 ? `<button type="button" class="btn-secondary btn-sm" id="actMarkReadBtn">${localizedText(lang, 'Tümünü Okundu Yap', 'Mark all read')}</button>` : ''}
            </div>

            ${reqs.length === 0 && acceptedState.size === 0 ? `
              <div class="card muted-note" style="font-size:12px;">${localizedText(lang, 'Bekleyen takip isteğin yok.', 'No pending follow requests.')}</div>
            ` : `
              <div style="display:flex; flex-direction:column; gap:8px;">
                ${[
                  ...reqs,
                  ...Array.from(acceptedState.values())
                    .map((v) => v?.user)
                    .filter((u) => u && !reqs.some((r) => r.uid === u.uid))
                ].map((u) => {
                  const acceptedInfo = acceptedState.get(u.uid);
                  return `
                    <div class="card" style="padding:10px; display:flex; align-items:center; gap:10px;">
                      <div data-act-user="${escHtml(u.uid)}" style="display:flex; align-items:center; gap:10px; flex:1; min-width:0; cursor:pointer;">
                        ${avatarCircleHtml(u.avatarBase64, u.username || u.displayName, 40)}
                        <div style="min-width:0;">
                          <strong style="font-size:13px; display:block;">${escHtml(u.displayName || u.username || 'Kullanıcı')}</strong>
                          <span class="muted-note" style="font-size:11px;">${u.username ? `@${escHtml(u.username)}` : ''}</span>
                        </div>
                      </div>
                      ${!acceptedInfo ? `
                        <button type="button" class="btn-secondary btn-sm" data-req-decline="${escHtml(u.uid)}">${localizedText(lang, 'Reddet', 'Decline')}</button>
                        <button type="button" class="btn-primary btn-sm" data-req-accept="${escHtml(u.uid)}">${localizedText(lang, 'Onayla', 'Accept')}</button>
                      ` : `
                        <button type="button" class="btn-secondary btn-sm" data-req-followback="${escHtml(u.uid)}" ${acceptedInfo.followedBack ? 'disabled' : ''}>
                          ${acceptedInfo.followedBack ? localizedText(lang, 'Takip Edildi', 'Following') : localizedText(lang, 'Geri Takip Et', 'Follow Back')}
                        </button>
                      `}
                    </div>
                  `;
                }).join('')}
              </div>
            `}

            <strong style="font-size:14px; margin-top:4px;">${localizedText(lang, `Bildirimler (${notifs.length})`, `Notifications (${notifs.length})`)}</strong>
            ${notifs.length === 0 ? `
              <div class="card muted-note" style="font-size:12px;">${localizedText(lang, 'Henüz yeni hareket yok.', 'No recent activity yet.')}</div>
            ` : `
              <div style="display:flex; flex-direction:column; gap:8px;">
                ${notifs.map((n) => {
                  const actor = n.fromUsername ? `@${n.fromUsername}` : (n.fromDisplayName || 'Kullanıcı');
                  let msg = `${actor} ${n.previewText || ''}`;
                  if (n.type === 'follow_request') msg = `${actor} seni takip etmek istedi`;
                  else if (n.type === 'follow_accepted') msg = `${actor} takip isteğini kabul etti`;
                  else if (n.type === 'new_follower') msg = `${actor} seni takip etmeye başladı`;
                  else if (n.type === 'post_like') msg = `${actor} gönderini beğendi`;
                  else if (n.type === 'post_comment') msg = `${actor} yorum yaptı: ${n.previewText || ''}`;
                  else if (n.type === 'dm_request') msg = `${actor} sana mesaj isteği gönderdi`;
                  else if (n.type === 'dm_post_reply') msg = `${actor} gönderine özel yanıt verdi: ${n.previewText || ''}`;
                  else if (n.type === 'dm_message') msg = `${actor} mesaj gönderdi: ${n.previewText || ''}`;
                  return `
                    <div class="card" data-notif-idx="${escHtml(n.notifId)}" style="padding:10px; display:flex; align-items:center; gap:10px; cursor:pointer; ${!n.read ? 'border-color:var(--accent); background:var(--secondary);' : ''}">
                      ${avatarCircleHtml(n.fromAvatar, actor, 36)}
                      <div style="flex:1; min-width:0;">
                        <div style="font-size:12.5px; line-height:1.4;">${escHtml(msg)}</div>
                        <div class="muted-note" style="font-size:11px;">${fmtSocialRel(n.createdAtMs)}</div>
                      </div>
                      ${!n.read ? '<span style="width:8px; height:8px; border-radius:50%; background:var(--accent); flex-shrink:0;"></span>' : ''}
                    </div>
                  `;
                }).join('')}
              </div>
            `}
          `;

          mount.querySelector('#actMarkReadBtn')?.addEventListener('click', async () => {
            await markAllNotificationsRead(notifs);
            await loadAndRender();
          });
          mount.querySelectorAll('[data-act-user]').forEach((el) => el.addEventListener('click', () => {
            close();
            onOpenPublicProfile?.(el.dataset.actUser, false);
          }));
          mount.querySelectorAll('[data-req-decline]').forEach((btn) => btn.addEventListener('click', async () => {
            btn.disabled = true;
            const myUid = getStoredUid();
            await unfollowOrCancel(btn.dataset.reqDecline, myUid).catch(() => {});
            showToast(localizedText(lang, 'İstek reddedildi', 'Declined'));
            await loadAndRender();
          }));
          mount.querySelectorAll('[data-req-accept]').forEach((btn) => btn.addEventListener('click', async () => {
            const ok = await requireUsernameOrPrompt(
              { openModal, showToast, lang, appState, photoMap, onProfileUpdated },
              localizedText(lang, 'Takip isteğini onaylamak için önce kullanıcı adı oluşturmalısın.', 'Create a username before accepting follow requests.')
            );
            if (!ok) return;
            btn.disabled = true;
            const uid = btn.dataset.reqAccept;
            try {
              await acceptFollowRequest(uid);
              const bundle = await fetchProfileBundle(uid, false).catch(() => null);
              acceptedState.set(uid, { user: reqs.find((x) => x.uid === uid), followedBack: Boolean(bundle?.followStatus) });
              showToast(localizedText(lang, 'Takip isteği kabul edildi', 'Follow request accepted'));
              await loadAndRender();
            } catch (err) {
              showToast(sanitizeErrorMessage(err));
            }
          }));
          mount.querySelectorAll('[data-req-followback]').forEach((btn) => btn.addEventListener('click', async () => {
            const uid = btn.dataset.reqFollowback;
            const info = acceptedState.get(uid);
            if (!info?.user) return;
            const ok = await requireUsernameOrPrompt(
              { openModal, showToast, lang, appState, photoMap, onProfileUpdated },
              localizedText(lang, 'Geri takip etmek için önce kullanıcı adı oluşturmalısın.', 'Create a username before following back.')
            );
            if (!ok) return;
            btn.disabled = true;
            try {
              await followUser(info.user);
              acceptedState.set(uid, { ...info, followedBack: true });
              showToast(localizedText(lang, 'Geri takip edildi!', 'Followed back!'));
              await loadAndRender();
            } catch (err) {
              showToast(sanitizeErrorMessage(err));
            }
          }));
          mount.querySelectorAll('[data-notif-idx]').forEach((card) => card.addEventListener('click', () => {
            const notif = notifs.find((x) => x.notifId === card.dataset.notifIdx);
            if (!notif) return;
            markNotificationRead(notif).catch(() => {});
            close();
            if ((notif.type === 'dm_message' || notif.type === 'dm_request' || notif.type === 'dm_post_reply') && onOpenDmUid) {
              onOpenDmUid(notif.fromUid);
            } else if (notif.postId && onOpenPost) {
              onOpenPost(notif.postId, notif.postOwnerUid || '');
            } else {
              onOpenPublicProfile?.(notif.fromUid, false);
            }
          }));
        } catch (err) {
          mount.innerHTML = `<div class="card muted-note">${escHtml(sanitizeErrorMessage(err))}</div>`;
        }
      };

      await loadAndRender();
    }
  });
}

// ==========================================
// 2) PER-PHOTO EDITOR (ROTATE + ASPECT CROP + 6 FILTERS)
// ==========================================
export function openPostPhotoEditorModal({ sourceDataUrl, openModal, lang = 'TR', S, onSave }) {
  let rotDeg = 0;
  let aspectMode = 'orig';
  let filterId = 'original';

  openModal({
    title: localizedText(lang, 'Fotoğraf Düzenleyici', 'Photo Editor'),
    fullscreen: true,
    bodyHtml: `
      <div style="display:flex; flex-direction:column; gap:12px;">
        <canvas id="peCanvas" class="photo-editor-canvas" width="600" height="600"></canvas>
        <div class="chip-row flush">
          <button type="button" class="chip-pill" id="peRotateBtn">${svgIcon('rotateRight', 15)} 90°</button>
          <button type="button" class="chip-pill selected" data-pe-aspect="orig">${localizedText(lang, 'Orijinal', 'Original')}</button>
          <button type="button" class="chip-pill" data-pe-aspect="1:1">1:1</button>
          <button type="button" class="chip-pill" data-pe-aspect="4:5">4:5</button>
          <button type="button" class="chip-pill" data-pe-aspect="16:9">16:9</button>
        </div>
        <div class="chip-row flush">
          ${PHOTO_EDITOR_FILTERS.map((f) => `
            <button type="button" class="chip-pill ${f.id === 'original' ? 'selected' : ''}" data-pe-filter="${escHtml(f.id)}">${escHtml(localizedText(lang, f.tr, f.en))}</button>
          `).join('')}
        </div>
      </div>
    `,
    footerHtml: `
      <div class="btn-row" style="width:100%;">
        <button type="button" class="btn-secondary" id="peCancelBtn" style="flex:1;">${escHtml(S().close)}</button>
        <button type="button" class="btn-primary" id="peApplyBtn" style="flex:1;">${localizedText(lang, 'Uygula', 'Apply')}</button>
      </div>
    `,
    onMount: (close, modalEl) => {
      const canvas = modalEl.querySelector('#peCanvas');
      const ctx2d = canvas.getContext('2d');
      const srcImg = new Image();

      const drawPreview = () => {
        if (!srcImg.complete || !srcImg.naturalWidth) return;
        const iw = srcImg.naturalWidth;
        const ih = srcImg.naturalHeight;
        const swap = rotDeg % 180 !== 0;
        const rw = swap ? ih : iw;
        const rh = swap ? iw : ih;

        let cropW = rw;
        let cropH = rh;
        const targetRatio = aspectMode === '1:1' ? 1 : aspectMode === '4:5' ? 0.8 : aspectMode === '16:9' ? 16 / 9 : 0;
        if (targetRatio > 0) {
          if (rw / rh > targetRatio) {
            cropW = Math.max(1, Math.round(rh * targetRatio));
          } else {
            cropH = Math.max(1, Math.round(rw / targetRatio));
          }
        }
        const maxSide = 960;
        const scale = Math.min(1, maxSide / Math.max(cropW, cropH));
        canvas.width = Math.max(64, Math.round(cropW * scale));
        canvas.height = Math.max(64, Math.round(cropH * scale));

        const filterMeta = PHOTO_EDITOR_FILTERS.find((f) => f.id === filterId) || PHOTO_EDITOR_FILTERS[0];
        ctx2d.save();
        ctx2d.clearRect(0, 0, canvas.width, canvas.height);
        ctx2d.filter = filterMeta.css;
        ctx2d.translate(canvas.width / 2, canvas.height / 2);
        ctx2d.rotate((rotDeg * Math.PI) / 180);
        const drawW = iw * scale;
        const drawH = ih * scale;
        ctx2d.drawImage(srcImg, -drawW / 2, -drawH / 2, drawW, drawH);
        ctx2d.restore();
      };

      srcImg.onload = drawPreview;
      srcImg.src = sourceDataUrl;

      modalEl.querySelector('#peRotateBtn')?.addEventListener('click', () => {
        rotDeg = (rotDeg + 90) % 360;
        drawPreview();
      });
      modalEl.querySelectorAll('[data-pe-aspect]').forEach((b) => b.addEventListener('click', () => {
        aspectMode = b.dataset.peAspect;
        modalEl.querySelectorAll('[data-pe-aspect]').forEach((x) => x.classList.toggle('selected', x === b));
        drawPreview();
      }));
      modalEl.querySelectorAll('[data-pe-filter]').forEach((b) => b.addEventListener('click', () => {
        filterId = b.dataset.peFilter;
        modalEl.querySelectorAll('[data-pe-filter]').forEach((x) => x.classList.toggle('selected', x === b));
        drawPreview();
      }));
      modalEl.querySelector('#peCancelBtn')?.addEventListener('click', close);
      modalEl.querySelector('#peApplyBtn')?.addEventListener('click', () => {
        const edited = canvas.toDataURL('image/jpeg', 0.86);
        close();
        onSave(edited);
      });
    }
  });
}

// ==========================================
// 3) CREATE POST MODAL (CAMERA / GALLERY / VIDEO / EDITOR)
// ==========================================
export function openCreatePostModal({
  items,
  openModal,
  showToast,
  lang = 'TR',
  S,
  pickFilesFromInput,
  onCreated
}) {
  let postKind = 'outfit';
  let caption = '';
  let photoDataUrls = [];
  let selectedVideoFile = null;
  let attachedItem = null;
  let busy = false;
  const profile = loadLocalProfile();

  openModal({
    title: localizedText(lang, 'Yeni Gönderi Oluştur', 'Create Post'),
    fullscreen: true,
    bodyHtml: `
      <div id="cpBody" style="display:flex; flex-direction:column; gap:12px;">
        <div class="chip-row flush" id="cpKindRow">
          ${SOCIAL_POST_KINDS.map((k) => `
            <button type="button" class="chip-pill ${k.key === postKind ? 'selected' : ''}" data-cp-kind="${escHtml(k.key)}">${escHtml(localizedText(lang, k.tr, k.en))}</button>
          `).join('')}
        </div>
        <textarea id="cpCaption" class="input-field" rows="3" maxlength="800" placeholder="${localizedText(lang, 'Kombinin veya parçan hakkında yaz...', 'Write about your outfit or piece...')}"></textarea>
        <div class="btn-row">
          <button type="button" class="btn-secondary btn-sm" id="cpCamPhotoBtn" style="flex:1;">${svgIcon('camera', 16)} ${localizedText(lang, 'Foto Çek', 'Camera')}</button>
          <button type="button" class="btn-secondary btn-sm" id="cpGalPhotoBtn" style="flex:1;">${svgIcon('photoLibrary', 16)} ${localizedText(lang, 'Galeri', 'Gallery')}</button>
        </div>
        <div class="btn-row">
          <button type="button" class="btn-secondary btn-sm" id="cpRecVidBtn" style="flex:1;">${svgIcon('videocam', 16)} ${localizedText(lang, 'Video Çek', 'Record Video')}</button>
          <button type="button" class="btn-secondary btn-sm" id="cpPickVidBtn" style="flex:1;">${svgIcon('videoLibrary', 16)} ${localizedText(lang, 'Video Yükle', 'Upload Video')}</button>
        </div>
        <div id="cpMediaPreview" style="display:flex; gap:10px; overflow-x:auto;"></div>
        <div style="display:flex; align-items:center; gap:8px;">
          <select id="cpItemSelect" class="input-field" style="flex:1;">
            <option value="">${localizedText(lang, 'Dolabımdan kıyafet ekle (opsiyonel)...', 'Attach wardrobe piece (optional)...')}</option>
            ${(items || []).slice(0, 60).map((it) => `<option value="${escHtml(it.id)}">${escHtml(it.name || it.subCategory)}</option>`).join('')}
          </select>
        </div>
        <div class="card muted-note" style="font-size:11.5px; padding:10px;">
          ${profile.isPrivate
            ? localizedText(lang, 'Hesabın gizli: Bu gönderi yalnızca onaylı takipçilerine görünür.', 'Private account: Visible to accepted followers only.')
            : localizedText(lang, 'Hesabın herkese açık: Tüm kullanıcılar gönderini görebilir.', 'Public account: Visible to everyone.')}
        </div>
      </div>
    `,
    footerHtml: `
      <div class="btn-row" style="width:100%;">
        <button type="button" class="btn-secondary" id="cpCancelBtn" style="flex:1;">${escHtml(S().close)}</button>
        <button type="button" class="btn-primary" id="cpPublishBtn" style="flex:1.4;">${localizedText(lang, 'Paylaş', 'Publish')}</button>
      </div>
    `,
    onMount: (close, modalEl) => {
      const renderPreviews = () => {
        const box = modalEl.querySelector('#cpMediaPreview');
        if (selectedVideoFile) {
          box.innerHTML = `
            <div class="card" style="width:100%; padding:10px; display:flex; align-items:center; justify-content:space-between;">
              <strong style="font-size:12px;">Video hazır (${Math.round(selectedVideoFile.size / 1024)} KB)</strong>
              <button type="button" class="btn-secondary btn-sm" id="cpClearVidBtn">${svgIcon('close', 14)}</button>
            </div>
          `;
          box.querySelector('#cpClearVidBtn')?.addEventListener('click', () => {
            selectedVideoFile = null;
            renderPreviews();
          });
          return;
        }
        box.innerHTML = photoDataUrls.map((url, idx) => `
          <div class="card" style="min-width:128px; width:128px; padding:6px; display:flex; flex-direction:column; gap:6px;">
            <img src="${escHtml(safeImgSrc(url))}" style="width:100%; height:112px; object-fit:cover; border-radius:8px;" />
            <div style="display:flex; justify-content:space-between; font-size:11px;">
              <button type="button" class="link-btn" data-cp-edit="${idx}" style="color:var(--accent); font-weight:700;">${localizedText(lang, 'Düzenle', 'Edit')}</button>
              <button type="button" class="link-btn" data-cp-del="${idx}" style="color:var(--danger);">${localizedText(lang, 'Kaldır', 'Remove')}</button>
            </div>
          </div>
        `).join('');
        box.querySelectorAll('[data-cp-edit]').forEach((b) => b.addEventListener('click', () => {
          const idx = Number(b.dataset.cpEdit);
          openPostPhotoEditorModal({
            sourceDataUrl: photoDataUrls[idx],
            openModal,
            lang,
            S,
            onSave: (edited) => {
              photoDataUrls[idx] = edited;
              renderPreviews();
            }
          });
        }));
        box.querySelectorAll('[data-cp-del]').forEach((b) => b.addEventListener('click', () => {
          const idx = Number(b.dataset.cpDel);
          photoDataUrls = photoDataUrls.filter((_, i) => i !== idx);
          renderPreviews();
        }));
      };

      modalEl.querySelectorAll('[data-cp-kind]').forEach((b) => b.addEventListener('click', () => {
        postKind = b.dataset.cpKind;
        modalEl.querySelectorAll('[data-cp-kind]').forEach((x) => x.classList.toggle('selected', x === b));
      }));
      modalEl.querySelector('#cpItemSelect')?.addEventListener('change', (e) => {
        attachedItem = (items || []).find((it) => it.id === e.target.value) || null;
      });
      modalEl.querySelector('#cpCamPhotoBtn')?.addEventListener('click', async () => {
        const files = await pickFilesFromInput('socialPostCamInput');
        if (!files[0]) return;
        const dataUrl = await compressDataUrlForCloud(await fileToDataUrl(files[0]), 210000, 960);
        if (dataUrl) { selectedVideoFile = null; photoDataUrls = [...photoDataUrls, dataUrl].slice(0, 4); renderPreviews(); }
      });
      modalEl.querySelector('#cpGalPhotoBtn')?.addEventListener('click', async () => {
        const files = await pickFilesFromInput('socialPostGalInput');
        for (const f of files.slice(0, 4)) {
          const dataUrl = await compressDataUrlForCloud(await fileToDataUrl(f), 210000, 960);
          if (dataUrl) photoDataUrls.push(dataUrl);
        }
        photoDataUrls = photoDataUrls.slice(0, 4);
        selectedVideoFile = null;
        renderPreviews();
      });
      const handleVidPick = async (inputId) => {
        const files = await pickFilesFromInput(inputId);
        const vf = files[0];
        if (!vf) return;
        if (vf.size > 7500000) {
          showToast(localizedText(lang, 'Video boyutu en fazla 7.5 MB olabilir', 'Video max size is 7.5 MB'));
          return;
        }
        photoDataUrls = [];
        selectedVideoFile = vf;
        postKind = 'video';
        modalEl.querySelectorAll('[data-cp-kind]').forEach((x) => x.classList.toggle('selected', x.dataset.cpKind === 'video'));
        renderPreviews();
      };
      modalEl.querySelector('#cpRecVidBtn')?.addEventListener('click', () => handleVidPick('socialPostVidCamInput'));
      modalEl.querySelector('#cpPickVidBtn')?.addEventListener('click', () => handleVidPick('socialPostVidGalInput'));
      modalEl.querySelector('#cpCancelBtn')?.addEventListener('click', close);
      modalEl.querySelector('#cpPublishBtn')?.addEventListener('click', async () => {
        if (busy) return;
        caption = modalEl.querySelector('#cpCaption')?.value || '';
        const mediaType = selectedVideoFile ? 'video' : (photoDataUrls.length ? 'photo' : 'text');
        if (mediaType === 'text' && !caption.trim()) {
          showToast(localizedText(lang, 'Yazı, fotoğraf veya video eklemelisin', 'Add text, photo or video'));
          return;
        }
        busy = true;
        const pubBtn = modalEl.querySelector('#cpPublishBtn');
        if (pubBtn) { pubBtn.disabled = true; pubBtn.textContent = localizedText(lang, 'Paylaşılıyor...', 'Publishing...'); }
        try {
          const created = await createSocialPost({
            profile: loadLocalProfile(),
            postKind,
            caption,
            mediaType,
            photoDataUrls,
            videoFile: selectedVideoFile,
            attachedItem,
            lang
          });
          feedPostsCache = [created, ...feedPostsCache];
          close();
          showToast(localizedText(lang, 'Gönderi paylaşıldı!', 'Post published!'));
          onCreated?.(created);
        } catch (err) {
          showToast(sanitizeErrorMessage(err));
          busy = false;
          if (pubBtn) { pubBtn.disabled = false; pubBtn.textContent = localizedText(lang, 'Paylaş', 'Publish'); }
        }
      });
    }
  });
}

// ==========================================
// 4) COMMENTS + CAMERA/GALLERY + ANIMATED GIF MODAL
// ==========================================
export function openPostCommentsModal({
  post,
  openModal,
  showToast,
  lang = 'TR',
  S,
  appState = {},
  photoMap = new Map(),
  onProfileUpdated,
  pickFilesFromInput,
  onOpenPublicProfile
}) {
  let mediaType = 'none';
  let mediaBase64 = '';
  let mediaUrl = '';
  let showGifs = false;
  let sending = false;
  const presets = getGifPresets(lang);

  openModal({
    title: localizedText(lang, 'Yorumlar', 'Comments'),
    fullscreen: true,
    bodyHtml: `
      <div style="display:flex; flex-direction:column; gap:10px;">
        <div id="cmList" style="display:flex; flex-direction:column; gap:8px;">
          <div class="card muted-note" style="text-align:center; padding:20px;">${localizedText(lang, 'Yorumlar yükleniyor...', 'Loading comments...')}</div>
        </div>
        <div id="cmGifDrawer" style="display:none; flex-direction:column; gap:8px;" class="card">
          <div style="display:flex; gap:6px;">
            <input id="cmExtGifUrl" class="input-field" placeholder="Tenor / Giphy HTTPS GIF URL..." style="flex:1; padding:8px 10px; font-size:12px;" />
            <button type="button" class="btn-secondary btn-sm" id="cmAddExtGifBtn">${localizedText(lang, 'Ekle', 'Add')}</button>
          </div>
          <div class="gif-grid">
            ${presets.map((g) => `
              <button type="button" class="gif-tile" data-gif-id="${escHtml(g.id)}">
                <img src="${escHtml(g.dataUrl)}" alt="${escHtml(g.label)}" />
                <span style="font-size:10px; color:var(--ink-muted);">${escHtml(g.label)}</span>
              </button>
            `).join('')}
          </div>
        </div>
        <div id="cmDraftMedia" style="display:none; align-items:center; gap:8px;" class="card"></div>
      </div>
    `,
    footerHtml: `
      <div style="display:flex; width:100%; align-items:center; gap:6px;">
        <button type="button" class="btn-icon-ghost" id="cmCamBtn" title="${localizedText(lang, 'Kamera', 'Camera')}">${svgIcon('camera', 19)}</button>
        <button type="button" class="btn-icon-ghost" id="cmGalBtn" title="${localizedText(lang, 'Galeri / GIF', 'Gallery / GIF')}">${svgIcon('photoLibrary', 19)}</button>
        <button type="button" class="btn-icon-ghost" id="cmGifBtn" title="GIF" style="color:var(--accent);">${svgIcon('gif', 22)}</button>
        <input id="cmTextInput" class="input-field" maxlength="500" placeholder="${localizedText(lang, 'Yorum yaz...', 'Add a comment...')}" style="flex:1; padding:9px 10px;" />
        <button type="button" class="btn-primary btn-sm" id="cmSendBtn">${svgIcon('send', 16)}</button>
      </div>
    `,
    onMount: async (close, modalEl) => {
      const myUid = getStoredUid();
      const listEl = modalEl.querySelector('#cmList');
      const gifDrawer = modalEl.querySelector('#cmGifDrawer');
      const draftBox = modalEl.querySelector('#cmDraftMedia');

      const updateDraftPreview = () => {
        const src = mediaType === 'gif' && mediaUrl ? mediaUrl : mediaBase64;
        if (!src) {
          draftBox.style.display = 'none';
          draftBox.innerHTML = '';
          return;
        }
        draftBox.style.display = 'flex';
        draftBox.innerHTML = `
          <img src="${escHtml(src)}" style="width:54px; height:54px; border-radius:8px; object-fit:cover;" />
          <span class="accent-note" style="flex:1; font-size:12px;">${mediaType === 'gif' ? 'GIF seçildi' : 'Fotoğraf eklendi'}</span>
          <button type="button" class="btn-secondary btn-sm" id="cmClearMediaBtn">${svgIcon('close', 14)}</button>
        `;
        draftBox.querySelector('#cmClearMediaBtn')?.addEventListener('click', () => {
          mediaType = 'none'; mediaBase64 = ''; mediaUrl = '';
          updateDraftPreview();
        });
      };

      const renderComments = (comments) => {
        if (!comments.length) {
          listEl.innerHTML = `<div class="card muted-note" style="text-align:center; padding:20px;">${localizedText(lang, 'İlk yorumu sen yap, fotoğraf veya GIF gönder!', 'Be the first to comment or send a GIF!')}</div>`;
          return;
        }
        listEl.innerHTML = comments.map((c) => {
          const cAuthor = c.authorUsername ? `@${c.authorUsername}` : (c.authorDisplayName || 'Kullanıcı');
          const mSrc = safeCommentMediaSrc(c);
          const canDel = c.authorUid === myUid || post.ownerUid === myUid;
          return `
            <div class="comment-item">
              <div data-cm-user="${escHtml(c.authorUid)}" style="cursor:pointer;">${avatarCircleHtml(c.authorAvatar, cAuthor, 34)}</div>
              <div style="flex:1; min-width:0;">
                <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                  <strong style="font-size:12px; cursor:pointer;" data-cm-user="${escHtml(c.authorUid)}">${escHtml(cAuthor)}</strong>
                  <span class="muted-note" style="font-size:10.5px;">${fmtSocialRel(c.createdAtMs)} ${canDel ? `<button type="button" class="link-btn" data-cm-del="${escHtml(c.commentId)}" style="color:var(--danger); margin-left:4px;">✕</button>` : ''}</span>
                </div>
                ${c.text ? `<div style="font-size:13px; margin-top:3px; line-height:1.4;">${escHtml(c.text)}</div>` : ''}
                ${mSrc ? `<img class="comment-media-img" src="${escHtml(mSrc)}" alt="" />` : ''}
              </div>
            </div>
          `;
        }).join('');
        listEl.querySelectorAll('[data-cm-user]').forEach((el) => {
          const uid = el.dataset.cmUser;
          const commentObj = comments.find((c) => c.authorUid === uid);
          if (el.querySelector('.user-avatar-circle')) {
            bindLongPressElement(
              el,
              () => openAvatarZoomModal({
                avatarSrc: commentObj?.authorAvatar || '',
                displayName: commentObj?.authorDisplayName || '',
                username: commentObj?.authorUsername || '',
                lang
              }),
              () => { close(); onOpenPublicProfile?.(uid, false); }
            );
          } else {
            el.addEventListener('click', () => {
              close();
              onOpenPublicProfile?.(uid, false);
            });
          }
        });
        listEl.querySelectorAll('[data-cm-del]').forEach((btn) => btn.addEventListener('click', async () => {
          try {
            await deletePostComment(post, btn.dataset.cmDel);
            comments = comments.filter((x) => x.commentId !== btn.dataset.cmDel);
            renderComments(comments);
          } catch (err) {
            showToast(sanitizeErrorMessage(err));
          }
        }));
      };

      let comments = [];
      try {
        comments = await fetchPostComments(post);
      } catch {
        comments = [];
      }
      renderComments(comments);

      modalEl.querySelector('#cmGifBtn')?.addEventListener('click', () => {
        showGifs = !showGifs;
        gifDrawer.style.display = showGifs ? 'flex' : 'none';
      });
      modalEl.querySelectorAll('[data-gif-id]').forEach((btn) => btn.addEventListener('click', () => {
        const g = presets.find((p) => p.id === btn.dataset.gifId);
        if (!g) return;
        mediaType = 'gif'; mediaBase64 = g.dataUrl; mediaUrl = '';
        showGifs = false; gifDrawer.style.display = 'none';
        updateDraftPreview();
      }));
      modalEl.querySelector('#cmAddExtGifBtn')?.addEventListener('click', () => {
        const raw = modalEl.querySelector('#cmExtGifUrl')?.value || '';
        if (!isSafeExternalGifUrl(raw)) {
          showToast(localizedText(lang, 'Sadece Tenor / Giphy HTTPS linki desteklenir', 'Only Tenor / Giphy HTTPS URLs'));
          return;
        }
        mediaType = 'gif'; mediaBase64 = ''; mediaUrl = raw.trim();
        showGifs = false; gifDrawer.style.display = 'none';
        updateDraftPreview();
      });
      modalEl.querySelector('#cmCamBtn')?.addEventListener('click', async () => {
        const files = await pickFilesFromInput('socialCommentCamInput');
        if (!files[0]) return;
        const url = await compressDataUrlForCloud(await fileToDataUrl(files[0]), 235000, 720);
        if (url) { mediaType = 'image'; mediaBase64 = url; mediaUrl = ''; updateDraftPreview(); }
      });
      modalEl.querySelector('#cmGalBtn')?.addEventListener('click', async () => {
        const files = await pickFilesFromInput('socialCommentGalInput');
        const f = files[0];
        if (!f) return;
        const raw = await fileToDataUrl(f);
        if (f.type === 'image/gif' || raw.startsWith('data:image/gif;base64,')) {
          if (raw.length <= 255000) {
            mediaType = 'gif'; mediaBase64 = raw; mediaUrl = ''; updateDraftPreview();
          } else {
            showToast(localizedText(lang, 'GIF en fazla ~250 KB olabilir', 'GIF max size ~250 KB'));
          }
          return;
        }
        const url = await compressDataUrlForCloud(raw, 235000, 720);
        if (url) { mediaType = 'image'; mediaBase64 = url; mediaUrl = ''; updateDraftPreview(); }
      });
      modalEl.querySelector('#cmSendBtn')?.addEventListener('click', async () => {
        if (sending) return;
        const ok = await requireUsernameOrPrompt(
          { openModal, showToast, lang, appState, photoMap, onProfileUpdated },
          localizedText(lang, 'Yorum yapmak için önce kullanıcı adı oluşturmalısın.', 'Create a username before commenting.')
        );
        if (!ok) return;
        const text = modalEl.querySelector('#cmTextInput')?.value || '';
        sending = true;
        try {
          const added = await addPostComment({
            post,
            myProfile: loadLocalProfile(),
            text,
            mediaType,
            mediaBase64,
            mediaUrl
          });
          comments = [...comments, added];
          renderComments(comments);
          modalEl.querySelector('#cmTextInput').value = '';
          mediaType = 'none'; mediaBase64 = ''; mediaUrl = '';
          updateDraftPreview();
        } catch (err) {
          if (err?.code === 'USERNAME_REQUIRED' || String(err?.message || '').startsWith('USERNAME_REQUIRED:')) {
            await requireUsernameOrPrompt({ openModal, showToast, lang, appState, photoMap, onProfileUpdated });
          } else {
            showToast(sanitizeErrorMessage(err));
          }
        } finally {
          sending = false;
        }
      });
    }
  });
}

// ==========================================
// 5) POST CARD HTML & HANDLERS + SINGLE POST DEEP LINK
// ==========================================
function openPostLikersModal(openModal, postId, lang, trigger) {
  const title = localizedText(lang, 'Beğenenler', 'Likes');
  const closeLabel = localizedText(lang, 'Kapat', 'Close');
  openModal({
    title,
    hideCloseX: true,
    panelClass: 'post-likers-modal',
    bodyHtml: `<div class="post-likers-list" data-post-likers-list>
      <div class="post-likers-loading" role="status" aria-label="${localizedText(lang, 'Yükleniyor', 'Loading')}"><span class="dm-voice-spinner"></span></div>
    </div>`,
    footerHtml: `<button type="button" class="post-likers-close" data-post-likers-close>${closeLabel}</button>`,
    onClose: () => { if (trigger.isConnected) trigger.focus(); },
    onMount: async (close, modal) => {
      const panel = modal.querySelector('.post-likers-modal');
      panel.setAttribute('role', 'dialog');
      panel.setAttribute('aria-modal', 'true');
      panel.setAttribute('aria-label', title);
      const button = modal.querySelector('[data-post-likers-close]');
      button.addEventListener('click', close);
      button.focus();
      panel.addEventListener('keydown', (event) => { if (event.key === 'Escape') close(); });
      const list = modal.querySelector('[data-post-likers-list]');
      try {
        const people = await fetchPostLikers(postId);
        if (!modal.isConnected) return;
        if (!people.length) {
          list.textContent = localizedText(lang, 'Henüz beğeni yok.', 'No likes yet.');
          return;
        }
        list.innerHTML = people.map((person) => {
          const name = person.displayName || person.username || localizedText(lang, 'Kullanıcı', 'User');
          return `<div class="post-liker-row">
            ${avatarCircleHtml(person.avatarBase64, name, 36)}
            <div class="post-liker-name">
              <strong>${escHtml(name)}</strong>
              ${person.username ? `<span>@${escHtml(person.username)}</span>` : ''}
            </div>
          </div>`;
        }).join('');
      } catch {
        if (modal.isConnected) list.textContent = localizedText(lang, 'Beğenenler yüklenemedi.', 'Could not load likes.');
      }
    }
  });
}

function buildPostCardHtml(post, myUid, likeState, lang) {
  const kindMeta = SOCIAL_POST_KINDS.find((k) => k.key === post.postKind) || SOCIAL_POST_KINDS[0];
  const authorLabel = post.ownerDisplayName || post.ownerUsername || 'Dolabım Kullanıcısı';
  const isMine = myUid && post.ownerUid === myUid;
  const canEdit = isMine && canEditPostCaption(post);

  return `
    <article class="card social-post-card" data-post-card="${escHtml(post.postId)}">
      <div style="display:flex; align-items:center; justify-content:space-between; gap:8px;">
        <div style="display:flex; align-items:center; gap:10px; flex:1; min-width:0;">
          <span class="avatar-clickable-zoom" data-post-avatar-zoom="${escHtml(post.postId)}" data-post-author="${escHtml(post.ownerUid)}" style="display:inline-flex;">${avatarCircleHtml(post.ownerAvatar, authorLabel, 38)}</span>
          <div style="min-width:0; flex:1; cursor:pointer;" data-post-author="${escHtml(post.ownerUid)}">
            <div style="display:flex; align-items:center; gap:4px;">
              <strong style="font-size:13.5px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escHtml(authorLabel)}</strong>
              ${post.isPrivatePost ? svgIcon('lock', 13) : ''}
            </div>
            <div class="muted-note" style="font-size:11px;">
              ${post.ownerUsername ? `@${escHtml(post.ownerUsername)} · ` : ''}${fmtSocialRel(post.createdAtMs)}${post.editedAtMs > 0 ? ' · düzenlendi' : ''}
            </div>
          </div>
        </div>
        <div style="display:flex; align-items:center; gap:4px;">
          <span class="chip-pill compact">${escHtml(localizedText(lang, kindMeta.tr, kindMeta.en))}</span>
          <div class="post-menu-wrap">
            <button type="button" class="btn-icon-ghost" data-post-menu="${escHtml(post.postId)}" aria-label="${localizedText(lang, 'İşlemler', 'Actions')}">${svgIcon('moreVert', 18)}</button>
            <div class="post-dropdown-menu hidden" data-post-dropdown="${escHtml(post.postId)}">
              ${!isMine ? `<button type="button" class="post-dropdown-item" data-post-dm-reply="${escHtml(post.postId)}">${svgIcon('reply', 15)} ${localizedText(lang, 'Özel Sohbetten Yanıtla', 'Reply in Chat')}</button>` : ''}
              <button type="button" class="post-dropdown-item" data-post-dm-forward="${escHtml(post.postId)}">${svgIcon('send', 15)} ${localizedText(lang, 'Sohbette Gönder', 'Send in Chat')}</button>
              ${isMine ? `
                <button type="button" class="post-dropdown-item" data-post-edit="${escHtml(post.postId)}" ${canEdit ? '' : 'disabled'}>${svgIcon('edit', 15)} ${canEdit ? localizedText(lang, `Yazıyı Düzenle (${editRemainingMinutes(post)} dk)`, 'Edit Caption') : localizedText(lang, 'Düzenleme süresi doldu (15 dk)', 'Edit expired (15m)')}</button>
                <button type="button" class="post-dropdown-item danger" data-post-del="${escHtml(post.postId)}">${svgIcon('deleteOutline', 15)} ${localizedText(lang, 'Gönderiyi Sil', 'Delete Post')}</button>
              ` : ''}
            </div>
          </div>
        </div>
      </div>

      ${post.mediaType === 'photo' && post.photos?.length ? `
        <div class="social-media-stage">
          <div class="social-carousel" data-carousel="${escHtml(post.postId)}">
            ${post.photos.map((url) => `<div class="social-carousel-slide"><img src="${escHtml(safeImgSrc(url))}" data-post-zoom="${escHtml(safeImgSrc(url))}" alt="" /></div>`).join('')}
          </div>
          ${post.photos.length > 1 ? `<span class="social-carousel-counter" data-carousel-cnt="${escHtml(post.postId)}">1/${post.photos.length}</span>` : ''}
        </div>
      ` : post.mediaType === 'video' ? `
        <div class="social-media-stage social-video-poster" data-video-box="${escHtml(post.postId)}">
          ${post.posterBase64 ? `<img src="${escHtml(safeImgSrc(post.posterBase64))}" alt="Video" />` : ''}
          <button type="button" class="social-play-btn" data-post-play="${escHtml(post.postId)}">${svgIcon('playArrow', 30)}</button>
          ${Number(post.videoDurationSec) > 0 ? `<span class="social-video-dur">${Number(post.videoDurationSec)} sn</span>` : ''}
        </div>
      ` : ''}

      ${post.caption ? `<div style="font-size:14px; line-height:1.45; white-space:pre-line;">${escHtml(post.caption)}</div>` : ''}

      ${post.attachedItemName ? `
        <div class="card" style="padding:9px 10px; background:var(--secondary); display:flex; align-items:center; justify-content:space-between; gap:8px;">
          <div style="display:flex; align-items:center; gap:8px; min-width:0; flex:1;">
            <span style="color:var(--accent); display:inline-flex; flex-shrink:0;">${svgIcon('checkroom', 18)}</span>
            <div style="min-width:0;">
              <strong style="font-size:12px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escHtml(post.attachedItemName)}</strong>
              ${post.attachedPriceText ? `<span class="muted-note" style="font-size:11px;">${escHtml(post.attachedPriceText)}</span>` : ''}
            </div>
          </div>
          <button type="button" class="btn-secondary btn-sm" data-post-import="${escHtml(post.postId)}">${svgIcon('bookmarkAdd', 15)} ${localizedText(lang, 'İstek', 'Save')}</button>
        </div>
      ` : ''}

      <div class="social-actions-row">
        <div style="display:flex; align-items:center; gap:14px;">
          <button type="button" class="social-action-btn ${likeState.liked ? 'liked' : ''}" data-post-like="${escHtml(post.postId)}" aria-pressed="${Boolean(likeState.liked)}" aria-label="${localizedText(lang, 'Beğen', 'Like')}, ${Number(likeState.count) || 0}">
            ${svgIcon(likeState.liked ? 'favoriteFilled' : 'favoriteBorder', 20)} <span>${likeState.count || 0}</span>
          </button>
          <button type="button" class="social-action-btn" data-post-comments="${escHtml(post.postId)}">
            ${svgIcon('chatBubble', 19)} <span>${localizedText(lang, 'Yorum', 'Comment')}</span>
          </button>
        </div>
        <button type="button" class="btn-icon-ghost" data-post-share="${escHtml(post.postId)}" title="${localizedText(lang, 'Paylaş', 'Share')}">${svgIcon('share', 18)}</button>
      </div>
    </article>
  `;
}

function wirePostCardListeners(container, posts, ctx) {
  const {
    openModal,
    showToast,
    lang,
    S,
    photoMap,
    pickFilesFromInput,
    onOpenPublicProfile,
    onReplyPostInDm,
    onForwardPostInDm,
    onProfileUpdated,
    onRefreshPosts,
    appState,
    savePhotoDataUrl,
    createId,
    commitState,
    syncPhotosAndPersonalProfile
  } = ctx;

  container.querySelectorAll('.social-carousel[data-carousel]').forEach((track) => {
    const postId = track.dataset.carousel;
    const badge = container.querySelector(`.social-carousel-counter[data-carousel-cnt="${CSS.escape(postId)}"]`);
    if (!badge) return;
    const total = track.children.length;
    track.addEventListener('scroll', () => {
      const w = track.clientWidth || 1;
      const idx = Math.max(0, Math.min(total - 1, Math.round(track.scrollLeft / w)));
      badge.textContent = `${idx + 1}/${total}`;
    }, { passive: true });
  });
  container.querySelectorAll('[data-post-avatar-zoom]').forEach((el) => {
    const post = posts.find((p) => p.postId === el.dataset.postAvatarZoom);
    if (!post) return;
    bindLongPressElement(
      el,
      () => openAvatarZoomModal({
        avatarSrc: post.ownerAvatar || '',
        displayName: post.ownerDisplayName || '',
        username: post.ownerUsername || '',
        lang
      }),
      () => onOpenPublicProfile?.(post.ownerUid, false)
    );
  });
  container.querySelectorAll('[data-post-author]:not([data-post-avatar-zoom])').forEach((el) => el.addEventListener('click', () => {
    onOpenPublicProfile?.(el.dataset.postAuthor, false);
  }));
  const closeAllDropdowns = () => {
    container.querySelectorAll('[data-post-dropdown]').forEach((m) => m.classList.add('hidden'));
  };
  container.querySelectorAll('[data-post-menu]').forEach((btn) => btn.addEventListener('click', (e) => {
    e.stopPropagation();
    const target = container.querySelector(`[data-post-dropdown="${CSS.escape(btn.dataset.postMenu)}"]`);
    const willOpen = target?.classList.contains('hidden');
    closeAllDropdowns();
    if (willOpen) target?.classList.remove('hidden');
  }));
  container.addEventListener('click', closeAllDropdowns);
  container.querySelectorAll('[data-post-dm-reply]').forEach((btn) => btn.addEventListener('click', (e) => {
    e.stopPropagation();
    closeAllDropdowns();
    const post = posts.find((p) => p.postId === btn.dataset.postDmReply);
    if (post) onReplyPostInDm?.(post);
  }));
  container.querySelectorAll('[data-post-dm-forward]').forEach((btn) => btn.addEventListener('click', (e) => {
    e.stopPropagation();
    closeAllDropdowns();
    const post = posts.find((p) => p.postId === btn.dataset.postDmForward);
    if (post) onForwardPostInDm?.(post);
  }));
  container.querySelectorAll('[data-post-zoom]').forEach((img) => img.addEventListener('click', () => {
    openModal({
      title: '',
      fullscreen: true,
      bodyHtml: `<div style="display:flex; align-items:center; justify-content:center; min-height:70vh;"><img src="${escHtml(img.dataset.postZoom)}" style="max-width:100%; max-height:80vh; border-radius:12px;" /></div>`,
      footerHtml: `<button type="button" class="btn-secondary" id="pzClose" style="width:100%;">${escHtml(S().close)}</button>`,
      onMount: (close, m) => m.querySelector('#pzClose')?.addEventListener('click', close)
    });
  }));
  container.querySelectorAll('[data-post-play]').forEach((btn) => btn.addEventListener('click', async () => {
    const post = posts.find((p) => p.postId === btn.dataset.postPlay);
    if (!post) return;
    btn.disabled = true;
    const box = container.querySelector(`[data-video-box="${CSS.escape(post.postId)}"]`);
    try {
      const blobUrl = await loadPostVideoBlobUrl(post);
      if (box) {
        box.innerHTML = `<video src="${escHtml(blobUrl)}" controls playsinline autoplay loop style="width:100%; height:100%; object-fit:contain; background:#000;"></video>`;
      }
    } catch (err) {
      btn.disabled = false;
      showToast(sanitizeErrorMessage(err));
    }
  }));
  container.querySelectorAll('[data-post-like]').forEach((btn) => {
    const post = posts.find((p) => p.postId === btn.dataset.postLike);
    if (!post) return;
    const showLikers = () => openPostLikersModal(openModal, post.postId, lang, btn);
    bindLongPressElement(btn, showLikers);
    btn.addEventListener('keydown', (event) => {
      if (event.key !== 'ContextMenu' && !(event.shiftKey && event.key === 'F10')) return;
      event.preventDefault();
      if (!event.repeat) showLikers();
    });
    let toggling = false;
    btn.addEventListener('click', async (event) => {
      if (event.defaultPrevented || toggling) return;
      toggling = true;
      try {
        const ok = await requireUsernameOrPrompt(
          { openModal, showToast, lang, appState, photoMap, onProfileUpdated },
          localizedText(lang, 'Gönderi beğenmek için önce kullanıcı adı oluşturmalısın.', 'Create a username before liking posts.')
        );
        if (!ok) return;
        const likedNow = await togglePostLike(post, loadLocalProfile());
        const prev = feedLikesCache.get(post.postId) || { count: 0, liked: false };
        feedLikesCache.set(post.postId, {
          liked: likedNow,
          count: Math.max(0, prev.count + (likedNow === prev.liked ? 0 : likedNow ? 1 : -1))
        });
        onRefreshPosts?.();
      } catch (err) {
        if (err?.code === 'USERNAME_REQUIRED' || String(err?.message || '').startsWith('USERNAME_REQUIRED:')) {
          await requireUsernameOrPrompt({ openModal, showToast, lang, appState, photoMap, onProfileUpdated });
        } else {
          showToast(sanitizeErrorMessage(err));
        }
      } finally {
        toggling = false;
      }
    });
  });
  container.querySelectorAll('[data-post-comments]').forEach((btn) => btn.addEventListener('click', () => {
    const post = posts.find((p) => p.postId === btn.dataset.postComments);
    if (!post) return;
    openPostCommentsModal({ post, openModal, showToast, lang, S, appState, photoMap, onProfileUpdated, pickFilesFromInput, onOpenPublicProfile });
  }));
  container.querySelectorAll('[data-post-share]').forEach((btn) => btn.addEventListener('click', async () => {
    const post = posts.find((p) => p.postId === btn.dataset.postShare);
    if (!post) return;
    const url = buildSharePostUrl(post);
    if (navigator.share) await navigator.share({ title: 'Dolabım', url }).catch(() => {});
    else await navigator.clipboard?.writeText(url).catch(() => {});
    showToast(localizedText(lang, 'Gönderi linki kopyalandı', 'Post link copied'));
  }));
  container.querySelectorAll('[data-post-edit]').forEach((btn) => btn.addEventListener('click', () => {
    closeAllDropdowns();
    const post = posts.find((p) => p.postId === btn.dataset.postEdit);
    if (!post) return;
    openModal({
      title: localizedText(lang, `Yazıyı Düzenle (${editRemainingMinutes(post)} dk kaldı)`, 'Edit Caption'),
      bodyHtml: `<textarea id="epCapInput" class="input-field" rows="4" maxlength="800">${escHtml(post.caption || '')}</textarea>`,
      footerHtml: `<div class="btn-row" style="width:100%;"><button type="button" class="btn-secondary" id="epCancel" style="flex:1;">${escHtml(S().close)}</button><button type="button" class="btn-primary" id="epSave" style="flex:1;">${escHtml(S().save)}</button></div>`,
      onMount: (close, m) => {
        m.querySelector('#epCancel')?.addEventListener('click', close);
        m.querySelector('#epSave')?.addEventListener('click', async () => {
          try {
            const updated = await editPostCaption(post, m.querySelector('#epCapInput').value);
            feedPostsCache = feedPostsCache.map((x) => (x.postId === updated.postId ? updated : x));
            close();
            showToast(localizedText(lang, 'Yazı güncellendi', 'Caption updated'));
            onRefreshPosts?.();
          } catch (err) {
            showToast(sanitizeErrorMessage(err));
          }
        });
      }
    });
  }));
  container.querySelectorAll('[data-post-del]').forEach((btn) => btn.addEventListener('click', async () => {
    closeAllDropdowns();
    const post = posts.find((p) => p.postId === btn.dataset.postDel);
    if (!post) return;
    if (!window.confirm(localizedText(lang, 'Bu gönderiyi silmek istiyor musun?', 'Delete this post?'))) return;
    try {
      await deleteSocialPost(post);
      feedPostsCache = feedPostsCache.filter((x) => x.postId !== post.postId);
      showToast(localizedText(lang, 'Gönderi silindi', 'Post deleted'));
      onRefreshPosts?.();
    } catch (err) {
      showToast(sanitizeErrorMessage(err));
    }
  }));
  container.querySelectorAll('[data-post-import]').forEach((btn) => btn.addEventListener('click', async () => {
    const post = posts.find((p) => p.postId === btn.dataset.postImport);
    if (!post) return;
    await importRemoteGarmentLocally({
      itemData: {
        name: post.attachedItemName,
        category: post.attachedCategory || 'TOP',
        priceText: post.attachedPriceText,
        productUrl: post.attachedProductUrl,
        photos: post.photos?.length ? [post.photos[0]] : (post.posterBase64 ? [post.posterBase64] : [])
      },
      asWishlist: true,
      appState,
      savePhotoDataUrl,
      createId,
      commitState,
      syncPhotosAndPersonalProfile,
      lang
    });
    showToast(localizedText(lang, 'İstek listene eklendi!', 'Added to wishlist!'));
  }));
}

export function renderSocialFeedTab(ctx) {
  const {
    mainContent,
    appState,
    photoMap,
    unreadActivityCount = 0,
    unreadDmCount = 0,
    lang = 'TR',
    S,
    openModal,
    showToast,
    onOpenActivity,
    onOpenDiscover,
    onOpenDmInbox,
    onOpenProfile,
    onOpenPublicProfile,
    onProfileUpdated,
    pickFilesFromInput
  } = ctx;
  const st = S();
  const profile = loadLocalProfile();

  const drawFeed = () => {
    const myUid = getStoredUid();
    const list = feedPostsCache.filter((p) => {
      if (feedActiveFilter === 'ALL' || feedActiveFilter === 'FOLLOWING') return true;
      if (feedActiveFilter === 'video') return p.mediaType === 'video';
      return p.postKind === feedActiveFilter;
    });

    mainContent.innerHTML = `
      <div class="screen-pad">
        <div class="page-header">
          <div style="min-width:0; flex:1;">
            <h2 class="page-title">${escHtml(st.tabSocial || 'Sosyal')}</h2>
            <p class="page-subtitle">${localizedText(lang, 'Kombinler, yeni parçalar & stil akışı', 'Outfits, new pieces & style stream')}</p>
          </div>
          <div class="social-header-actions">
            <button type="button" class="social-discover-btn" id="fDiscoverBtn" title="${localizedText(lang, 'Keşfet & Ara', 'Discover & Search')}" aria-label="${localizedText(lang, 'Keşfet & Ara', 'Discover & Search')}">${svgIcon('search', 16)} <span>${localizedText(lang, 'Keşfet', 'Discover')}</span></button>
            ${activityHeartButtonHtml('fActivityBtn', unreadActivityCount)}
            ${chatInboxButtonHtml('fChatBtn', unreadDmCount)}
            ${profileIconButtonHtml('fProfileBtn', profile)}
          </div>
        </div>
      </div>

      <div class="chip-row" id="fFilterChips">
        ${FEED_FILTERS.map((f) => `
          <button type="button" class="chip-pill ${feedActiveFilter === f.id ? 'selected' : ''}" data-ff="${escHtml(f.id)}">${escHtml(localizedText(lang, f.tr, f.en))}</button>
        `).join('')}
        <button type="button" class="icon-btn" id="fRefreshBtn" title="Yenile">${svgIcon('refresh', 18)}</button>
      </div>

      <div class="stack" id="fPostsMount">
        ${feedLoadError ? `<div class="card muted-note" role="alert">${escHtml(feedLoadError)}</div>` : ''}
        ${!feedLoadedOnce ? `
          <div class="card muted-note" style="text-align:center; padding:28px;">${localizedText(lang, 'Sosyal akış yükleniyor...', 'Loading feed...')}</div>
        ` : feedLoadError && !list.length ? '' : list.length === 0 ? `
          <div class="card" style="text-align:center; padding:26px; display:flex; flex-direction:column; gap:10px; align-items:center;">
            <strong>${localizedText(lang, 'Bu akışta henüz gönderi yok', 'No posts yet')}</strong>
            <span class="muted-note">${localizedText(lang, 'İlk kombini, fotoğrafı veya videoyu sen paylaş!', 'Share the first outfit, photo or video!')}</span>
            <button type="button" class="btn-primary btn-sm" id="fEmptyCreateBtn">${localizedText(lang, 'İlk Gönderini Paylaş', 'Share First Post')}</button>
          </div>
        ` : list.map((p) => buildPostCardHtml(p, myUid, feedLikesCache.get(p.postId) || { count: 0, liked: false }, lang)).join('')}
      </div>
    `;

    document.getElementById('fDiscoverBtn')?.addEventListener('click', () => onOpenDiscover?.());
    document.getElementById('fEmptyCreateBtn')?.addEventListener('click', openCreator);
    document.getElementById('fActivityBtn')?.addEventListener('click', onOpenActivity);
    document.getElementById('fChatBtn')?.addEventListener('click', () => onOpenDmInbox?.());
    document.getElementById('fProfileBtn')?.addEventListener('click', () => onOpenProfile?.('preview'));
    document.getElementById('fRefreshBtn')?.addEventListener('click', () => reloadCloud());
    document.querySelectorAll('#fFilterChips [data-ff]').forEach((b) => b.addEventListener('click', () => {
      const prevFollowing = feedActiveFilter === 'FOLLOWING';
      feedActiveFilter = b.dataset.ff;
      if ((feedActiveFilter === 'FOLLOWING') !== prevFollowing) reloadCloud();
      else drawFeed();
    }));

    const postsMount = document.getElementById('fPostsMount');
    if (postsMount && list.length) {
      wirePostCardListeners(postsMount, list, { ...ctx, onRefreshPosts: drawFeed });
    }
  };

  const openCreator = async () => {
    const ok = await requireUsernameOrPrompt(
      { openModal, showToast, lang, appState, photoMap, onProfileUpdated },
      localizedText(lang, 'Gönderi paylaşmak için önce kullanıcı adı oluşturmalısın.', 'Create a username before posting.')
    );
    if (!ok) return;
    openCreatePostModal({
      items: appState.items || [],
      openModal,
      showToast,
      lang,
      S,
      pickFilesFromInput,
      onCreated: () => drawFeed()
    });
  };

  const reloadCloud = async () => {
    const request = ++feedRequest;
    try {
      const loaded = await fetchFeedPosts({ followingOnly: feedActiveFilter === 'FOLLOWING' });
      if (request !== feedRequest) return;
      feedLoadError = '';
      feedPostsCache = loaded;
      feedLoadedOnce = true;
      await Promise.all(loaded.slice(0, 20).map(async (p) => {
        const stLike = await fetchPostLikeState(p.postId).catch(() => ({ count: 0, liked: false }));
        feedLikesCache.set(p.postId, stLike);
      }));
    } catch (err) {
      if (request !== feedRequest) return;
      feedLoadError = sanitizeErrorMessage(err);
      feedLoadedOnce = true;
    }
    if (request === feedRequest && mainContent.querySelector('#fPostsMount')) drawFeed();
  };

  drawFeed();
  if (!feedLoadedOnce) reloadCloud();
}

export function openSinglePostModal(ctx) {
  const { postId, ownerUid = '', openModal, showToast, lang = 'TR', S, onOpenPublicProfile } = ctx;
  openModal({
    title: localizedText(lang, 'Paylaşılan Gönderi', 'Shared Post'),
    fullscreen: true,
    bodyHtml: `<div id="spMount" style="display:flex; flex-direction:column; gap:12px;"><div class="card muted-note" style="text-align:center; padding:26px;">${localizedText(lang, 'Gönderi yükleniyor...', 'Loading post...')}</div></div>`,
    footerHtml: `<button type="button" class="btn-secondary" id="spCloseBtn" style="width:100%;">${escHtml(S().close)}</button>`,
    onMount: async (close, modalEl) => {
      modalEl.querySelector('#spCloseBtn')?.addEventListener('click', close);
      const mount = modalEl.querySelector('#spMount');
      try {
        const p = await fetchSinglePost(postId, ownerUid);
        if (!p) {
          mount.innerHTML = `<div class="card muted-note" style="text-align:center; padding:26px;">${localizedText(lang, 'Bu gönderi bulunamadı veya gizli bir hesaba ait.', 'Post not found or private.')}</div>`;
          return;
        }
        const stLike = await fetchPostLikeState(p.postId).catch(() => ({ count: 0, liked: false }));
        feedLikesCache.set(p.postId, stLike);
        const renderSingle = () => {
          mount.innerHTML = buildPostCardHtml(p, getStoredUid(), feedLikesCache.get(p.postId) || stLike, lang);
          wirePostCardListeners(mount, [p], {
            ...ctx,
            onOpenPublicProfile: (u, fl) => { close(); onOpenPublicProfile?.(u, fl); },
            onRefreshPosts: renderSingle
          });
        };
        renderSingle();
      } catch (err) {
        mount.innerHTML = `<div class="card muted-note">${escHtml(sanitizeErrorMessage(err))}</div>`;
      }
    }
  });
}
