// Instagram-Style Direct Messaging Modals & Live Chat UI (v1.43.275)

import { localizedText } from './constants.js';
import { svgIcon } from './icons.js';
import { getStoredUid, trackFeature } from './analytics.js';
import { getGifPresets, isSafeExternalGifUrl, isSafeGifDataUrl } from './gif-library.js';
import {
  escHtml,
  safeImgSrc,
  sanitizeErrorMessage
} from './security.js';
import {
  blockUser,
  fetchFollowList,
  isValidUsername,
  loadLocalProfile,
  searchProfiles,
  unblockUser
} from './social.js';
import {
  avatarCircleHtml,
  bindLongPressElement,
  openAvatarZoomModal,
  requireUsernameOrPrompt
} from './profile-ui.js';
import {
  canEditDmMessage,
  convPartnerLastRead,
  dmEditRemainingMinutes,
  editDmMessage,
  fetchMessages,
  fetchMyConversations,
  fetchPartnerActivityState,
  fetchUserPresence,
  getOrCreateConversation,
  isConversationMuted,
  isConvRequestLocked,
  loadDmVideoBlobUrl,
  markConversationRead,
  preparePhotoDataUrlForDm,
  refreshConversation,
  sendMessage,
  setChatActivityState,
  setConversationMuted,
  softDeleteDmMessage,
  updateMessageRequestStatus
} from './dm-service.js';

function fmtDmRel(ms) {
  if (!ms) return '';
  const diff = Math.max(0, Math.floor((Date.now() - Number(ms)) / 1000));
  if (diff < 60) return 'az önce';
  if (diff < 3600) return `${Math.floor(diff / 60)} dk`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} sa`;
  return new Date(Number(ms)).toLocaleDateString('tr-TR', { day: 'numeric', month: 'short' });
}

function fmtClockHHMM(ms) {
  if (!ms) return '';
  const d = new Date(Number(ms));
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

export function openDmInboxModal(ctx) {
  const {
    openModal,
    showToast,
    lang = 'TR',
    S,
    appState,
    photoMap,
    pickFilesFromInput,
    onOpenPublicProfile,
    onOpenSinglePost,
    onProfileUpdated,
    onUnreadChanged
  } = ctx;
  trackFeature('dm_inbox_open');
  const st = S();
  const myUid = getStoredUid();
  let activeTab = 'messages';
  let threads = [];
  let followingList = [];
  let searchQ = '';
  let searchResults = [];
  let searchTimer = null;

  openModal({
    title: localizedText(lang, 'Sohbet', 'Messages'),
    headerLeftHtml: `<button type="button" class="btn-icon-ghost" id="dmInboxBackBtn" aria-label="${escHtml(st.close)}">${svgIcon('arrowBack', 20)}</button>`,
    centerTitle: true,
    hideCloseX: true,
    fullscreen: true,
    bodyHtml: `
      <div class="seg-toggle" id="dmInboxTabs">
        <button type="button" class="seg-btn active" data-dmtab="messages">${localizedText(lang, 'Mesajlar (0)', 'Messages (0)')}</button>
        <button type="button" class="seg-btn" data-dmtab="requests">${localizedText(lang, 'İstekler', 'Requests')}</button>
      </div>
      <div style="position:relative;">
        <input type="text" class="input-field" id="dmInboxSearchInput" placeholder="${localizedText(lang, 'Yeni sohbet için @kullanıcı ara...', 'Search @username to start chat...')}" />
      </div>
      <div id="dmInboxMount" style="display:flex; flex-direction:column; gap:8px;">
        <div class="card muted-note" style="text-align:center; padding:26px;">${localizedText(lang, 'Sohbetler yükleniyor...', 'Loading chats...')}</div>
      </div>
    `,
    footerHtml: '',
    onMount: (close, modalEl) => {
      modalEl.querySelector('#dmInboxBackBtn')?.addEventListener('click', close);
      const mount = modalEl.querySelector('#dmInboxMount');
      const searchInput = modalEl.querySelector('#dmInboxSearchInput');
      const msgTabBtn = modalEl.querySelector('[data-dmtab="messages"]');
      const reqTabBtn = modalEl.querySelector('[data-dmtab="requests"]');

      const launchChat = async (partnerUid) => {
        const ok = await requireUsernameOrPrompt(
          { openModal, showToast, lang, appState, photoMap, onProfileUpdated },
          localizedText(lang, 'Sohbet başlatmak için önce kullanıcı adı oluşturmalısın.', 'Create a username to start chatting.')
        );
        if (!ok) return;
        close();
        openDmChatModal({ ...ctx, partnerUid });
      };

      const renderList = () => {
        const msgThreads = threads.filter((t) => t.conversation.status === 'accepted');
        const reqThreads = threads.filter((t) => t.conversation.status === 'request');
        const inReqCnt = reqThreads.filter((t) => t.conversation.recipientUid === myUid).length;
        if (msgTabBtn) msgTabBtn.textContent = localizedText(lang, `Mesajlar (${msgThreads.length})`, `Messages (${msgThreads.length})`);
        if (reqTabBtn) reqTabBtn.textContent = inReqCnt > 0 ? localizedText(lang, `İstekler (${inReqCnt})`, `Requests (${inReqCnt})`) : localizedText(lang, 'İstekler', 'Requests');

        if (searchQ.trim().length >= 2) {
          mount.innerHTML = searchResults.length === 0
            ? `<div class="card muted-note" style="text-align:center; padding:20px;">${localizedText(lang, 'Eşleşen kullanıcı bulunamadı.', 'No matching users.')}</div>`
            : searchResults.map((u) => `
              <div class="card dm-thread-row" data-dm-start="${escHtml(u.uid)}">
                ${avatarCircleHtml(u.avatarBase64, u.username || u.displayName, 42)}
                <div style="flex:1; min-width:0;">
                  <strong style="font-size:14px; display:block;">${escHtml(u.displayName || `@${u.username}`)}</strong>
                  ${u.username ? `<span class="accent-note" style="font-size:12px;">@${escHtml(u.username)}</span>` : ''}
                </div>
                <span class="accent-note" style="font-size:12px; font-weight:700;">${localizedText(lang, 'Mesaj', 'Chat')}</span>
              </div>
            `).join('');
        } else {
          const shown = activeTab === 'messages' ? msgThreads : reqThreads;
          const followingStripHtml = (activeTab === 'messages' && followingList.length)
            ? `
              <div style="display:flex; flex-direction:column; gap:6px; margin-bottom:4px;">
                <span class="muted-note" style="font-size:11.5px; font-weight:700;">${localizedText(lang, 'Takip Ettiklerin', 'Following')}</span>
                <div style="display:flex; gap:12px; overflow-x:auto; padding-bottom:4px;">
                  ${followingList.map((fu) => `
                    <button type="button" data-dm-start="${escHtml(fu.uid)}" style="background:none; border:none; padding:0; cursor:pointer; width:60px; display:flex; flex-direction:column; align-items:center; gap:4px; color:var(--ink); flex-shrink:0;">
                      ${avatarCircleHtml(fu.avatarBase64, fu.username || fu.displayName, 46)}
                      <span style="font-size:11px; max-width:58px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${escHtml(fu.displayName || fu.username || 'Üye')}</span>
                    </button>
                  `).join('')}
                </div>
              </div>
            ` : '';

          const rowsHtml = shown.length === 0
            ? `<div class="card" style="text-align:center; padding:24px; display:flex; flex-direction:column; gap:6px;">
                <strong>${activeTab === 'messages' ? localizedText(lang, 'Henüz mesajın yok', 'No messages yet') : localizedText(lang, 'Bekleyen mesaj isteği yok', 'No message requests')}</strong>
                <span class="muted-note" style="font-size:12px;">${activeTab === 'messages'
                  ? localizedText(lang, 'Takip ettiklerinle sohbet başlatabilir veya yukarıdan @kullanıcı arayabilirsin.', 'Tap someone you follow or search @username above.')
                  : localizedText(lang, 'Takip etmediğin kullanıcıların mesaj istekleri burada görünür.', 'Message requests from non-followers appear here.')}</span>
              </div>`
            : shown.map((t) => {
                const c = t.conversation;
                const p = t.partner;
                const name = p.displayName || (p.username ? `@${p.username}` : 'Kullanıcı');
                const preview = c.lastMessageType === 'deleted'
                  ? localizedText(lang, 'Bu mesaj silindi', 'This message was deleted')
                  : (c.lastMessageText || (c.status === 'request' ? localizedText(lang, 'Mesaj isteği', 'Message request') : localizedText(lang, 'Sohbeti aç', 'Open chat')));
                return `
                  <div class="card dm-thread-row" data-dm-start="${escHtml(p.uid)}" style="${t.isUnread ? 'border-color:var(--accent); background:var(--secondary);' : ''}">
                    <div class="dm-avatar-wrap">
                      ${avatarCircleHtml(p.avatarBase64, name, 46)}
                      ${t.partnerPresence?.effectiveOnline && !t.isBlockedByMe && !t.hasBlockedMe ? '<span class="dm-online-dot"></span>' : ''}
                    </div>
                    <div style="flex:1; min-width:0;">
                      <div style="display:flex; align-items:center; gap:5px;">
                        <strong style="font-size:14px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escHtml(name)}</strong>
                        ${t.isMuted ? `<span style="color:var(--ink-muted); display:inline-flex;">${svgIcon('volumeOff', 14)}</span>` : ''}
                      </div>
                      <div class="muted-note" style="font-size:12px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; ${c.lastMessageType === 'deleted' ? 'font-style:italic;' : ''}">${escHtml(preview)}</div>
                    </div>
                    <div style="display:flex; flex-direction:column; align-items:flex-end; gap:4px;">
                      ${c.lastMessageAtMs ? `<span class="muted-note" style="font-size:10.5px;">${escHtml(fmtDmRel(c.lastMessageAtMs))}</span>` : ''}
                      ${t.isUnread ? '<span class="dm-unread-pill" style="min-width:10px; width:10px; height:10px; padding:0;"></span>' : ''}
                    </div>
                  </div>
                `;
              }).join('');
          mount.innerHTML = `${followingStripHtml}${rowsHtml}`;
        }

        mount.querySelectorAll('[data-dm-start]').forEach((el) => {
          el.addEventListener('click', () => launchChat(el.dataset.dmStart));
        });
      };

      modalEl.querySelectorAll('[data-dmtab]').forEach((b) => b.addEventListener('click', () => {
        activeTab = b.dataset.dmtab;
        modalEl.querySelectorAll('[data-dmtab]').forEach((x) => x.classList.toggle('active', x.dataset.dmtab === activeTab));
        renderList();
      }));

      searchInput?.addEventListener('input', () => {
        searchQ = searchInput.value || '';
        clearTimeout(searchTimer);
        if (searchQ.trim().length < 2) {
          searchResults = [];
          renderList();
          return;
        }
        searchTimer = setTimeout(async () => {
          searchResults = (await searchProfiles(searchQ.trim()).catch(() => [])).filter((u) => u.uid !== myUid);
          renderList();
        }, 250);
      });

      Promise.all([
        fetchMyConversations().catch(() => []),
        myUid ? fetchFollowList(myUid, false).catch(() => []) : Promise.resolve([])
      ]).then(([list, fol]) => {
        threads = list;
        followingList = fol;
        renderList();
        onUnreadChanged?.();
      });
    }
  });
}

export function openDmChatModal(ctx) {
  const {
    partnerUid,
    openModal,
    showToast,
    lang = 'TR',
    S,
    appState,
    photoMap,
    pickFilesFromInput,
    onOpenPublicProfile,
    onOpenSinglePost,
    onProfileUpdated,
    onUnreadChanged
  } = ctx;
  const st = S();
  const myUid = getStoredUid();
  let conv = null;
  let partner = null;
  let presence = { effectiveOnline: false, lastOnlineAtMs: 0 };
  let partnerActivity = 'idle';
  let isBlockedByMe = false;
  let hasBlockedMe = false;
  let isMuted = isConversationMuted(partnerUid);
  let messages = [];
  let sending = false;
  let showGifs = false;
  let expandedTimeId = null;
  let pollTimer = null;
  let typingTimer = null;
  let mediaRec = null;
  let recChunks = [];
  let recTimer = null;
  let recSeconds = 0;
  let recStream = null;
  let isRecording = false;
  const speedMap = new Map();

  openModal({
    title: '',
    hideCloseX: true,
    fullscreen: true,
    headerLeftHtml: `
      <div style="display:flex; align-items:center; gap:8px; min-width:0; flex:1;">
        <button type="button" class="btn-icon-ghost" id="dmChatBackBtn" aria-label="${escHtml(st.close)}">${svgIcon('arrowBack', 20)}</button>
        <div id="dmHeaderPartnerWrap" style="display:flex; align-items:center; gap:8px; min-width:0; flex:1; cursor:pointer;"></div>
      </div>
    `,
    headerRightHtml: `
      <div style="display:flex; align-items:center; gap:2px; position:relative;">
        <button type="button" class="btn-icon-ghost" id="dmMuteBtn" title="Bildirimler">${svgIcon(isMuted ? 'volumeOff' : 'volumeUp', 20)}</button>
        <button type="button" class="btn-icon-ghost" id="dmChatMoreBtn" title="Menü">${svgIcon('moreVert', 20)}</button>
        <div class="post-dropdown-menu hidden" id="dmChatDropdown">
          <button type="button" class="post-dropdown-item" id="dmActViewProfile">${svgIcon('person', 15)} ${localizedText(lang, 'Profili Gör', 'View Profile')}</button>
          <button type="button" class="post-dropdown-item" id="dmActToggleMute">${svgIcon(isMuted ? 'volumeUp' : 'volumeOff', 15)} ${isMuted ? localizedText(lang, 'Sesi Aç', 'Unmute') : localizedText(lang, 'Sessize Al', 'Mute')}</button>
          <button type="button" class="post-dropdown-item danger" id="dmActToggleBlock">${svgIcon('block', 15)} <span id="dmActBlockTxt">${localizedText(lang, 'Engelle', 'Block')}</span></button>
        </div>
      </div>
    `,
    bodyHtml: `
      <div class="dm-chat-shell" id="dmChatShell">
        <div id="dmBannerWrap"></div>
        <div class="dm-messages-scroll" id="dmMessagesScroll">
          <div class="card muted-note" style="text-align:center; padding:24px;">${localizedText(lang, 'Mesajlar yükleniyor...', 'Loading chat...')}</div>
        </div>
        <div id="dmGifDrawerMount"></div>
        <div id="dmComposerMount"></div>
      </div>
    `,
    footerHtml: '',
    onMount: (close, modalEl) => {
      const cleanup = () => {
        if (pollTimer) clearInterval(pollTimer);
        if (typingTimer) clearTimeout(typingTimer);
        if (recTimer) clearInterval(recTimer);
        try { if (mediaRec && mediaRec.state !== 'inactive') mediaRec.stop(); } catch {}
        recStream?.getTracks()?.forEach((t) => t.stop());
        if (conv?.convId) setChatActivityState(conv.convId, 'idle');
      };
      const origClose = () => { cleanup(); close(); };
      modalEl.querySelector('#dmChatBackBtn')?.addEventListener('click', origClose);
      modalEl.querySelector('.modal-backdrop')?.addEventListener('click', (e) => {
        if (e.target.classList.contains('modal-backdrop')) cleanup();
      });

      const partnerWrap = modalEl.querySelector('#dmHeaderPartnerWrap');
      const muteBtn = modalEl.querySelector('#dmMuteBtn');
      const dropdown = modalEl.querySelector('#dmChatDropdown');
      const bannerWrap = modalEl.querySelector('#dmBannerWrap');
      const scrollEl = modalEl.querySelector('#dmMessagesScroll');
      const gifMount = modalEl.querySelector('#dmGifDrawerMount');
      const compMount = modalEl.querySelector('#dmComposerMount');

      const renderHeader = () => {
        if (muteBtn) {
          muteBtn.innerHTML = svgIcon(isMuted ? 'volumeOff' : 'volumeUp', 20);
          muteBtn.style.color = isMuted ? 'var(--accent)' : '';
        }
        const blkSpan = modalEl.querySelector('#dmActBlockTxt');
        if (blkSpan) blkSpan.textContent = isBlockedByMe ? localizedText(lang, 'Engeli Kaldır', 'Unblock') : localizedText(lang, 'Engelle', 'Block');
        const actMuteBtn = modalEl.querySelector('#dmActToggleMute');
        if (actMuteBtn) actMuteBtn.innerHTML = `${svgIcon(isMuted ? 'volumeUp' : 'volumeOff', 15)} ${isMuted ? localizedText(lang, 'Sesi Aç', 'Unmute') : localizedText(lang, 'Sessize Al', 'Mute')}`;

        if (!partner || !partnerWrap) return;
        const pTitle = partner.displayName || (partner.username ? `@${partner.username}` : 'Kullanıcı');
        const subLabel = (() => {
          if (isBlockedByMe || hasBlockedMe) return '';
          if (partnerActivity === 'typing') return localizedText(lang, 'yazıyor...', 'typing...');
          if (partnerActivity === 'capturing_photo') return localizedText(lang, 'fotoğraf çekiyor...', 'taking a photo...');
          if (partnerActivity === 'uploading_photo') return localizedText(lang, 'fotoğraf yüklüyor...', 'uploading a photo...');
          if (partnerActivity === 'recording_audio') return localizedText(lang, 'ses kaydediyor...', 'recording audio...');
          if (presence?.effectiveOnline) return localizedText(lang, 'Çevrimiçi', 'Online');
          if (presence?.lastOnlineAtMs) return localizedText(lang, `Son görülme: ${fmtDmRel(presence.lastOnlineAtMs)}`, `Last seen: ${fmtDmRel(presence.lastOnlineAtMs)}`);
          return partner.username ? `@${partner.username}` : '';
        })();
        const liveHighlight = partnerActivity !== 'idle' || presence?.effectiveOnline;
        partnerWrap.innerHTML = `
          <div class="dm-avatar-wrap avatar-clickable-zoom" id="dmPartnerAvBtn">
            ${avatarCircleHtml(partner.avatarBase64, pTitle, 36)}
            ${presence?.effectiveOnline && !isBlockedByMe && !hasBlockedMe ? '<span class="dm-online-dot"></span>' : ''}
          </div>
          <div style="min-width:0;">
            <strong style="font-size:14.5px; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escHtml(pTitle)}</strong>
            ${subLabel ? `<span style="font-size:11.5px; color:${liveHighlight ? 'var(--accent)' : 'var(--ink-muted)'}; display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escHtml(subLabel)}</span>` : ''}
          </div>
        `;
        bindLongPressElement(
          partnerWrap.querySelector('#dmPartnerAvBtn'),
          () => openAvatarZoomModal({ avatarSrc: partner.avatarBase64, displayName: partner.displayName, username: partner.username, lang }),
          () => { origClose(); onOpenPublicProfile?.(partner.uid, false); }
        );
      };

      const renderMessages = (scrollBottom = false) => {
        if (!scrollEl || !conv) return;
        if (bannerWrap) {
          if (conv.status === 'request' && conv.recipientUid === myUid && !isBlockedByMe && !hasBlockedMe) {
            const pTitle = partner?.displayName || (partner?.username ? `@${partner.username}` : 'Kullanıcı');
            bannerWrap.innerHTML = `
              <div class="card" style="background:var(--accent-soft); border-color:var(--accent); display:flex; flex-direction:column; gap:8px; margin-bottom:8px;">
                <span style="font-size:12.5px; font-weight:600;">${escHtml(pTitle)} sana mesaj isteği gönderdi. Yanıtlamak için kabul et.</span>
                <div class="btn-row">
                  <button type="button" class="btn-primary btn-sm" id="dmReqAcceptBtn">${localizedText(lang, 'Kabul Et', 'Accept')}</button>
                  <button type="button" class="btn-secondary btn-sm" id="dmReqDeclineBtn">${localizedText(lang, 'Reddet', 'Decline')}</button>
                  <button type="button" class="btn-danger-outline btn-sm" id="dmReqBlockBtn">${localizedText(lang, 'Engelle', 'Block')}</button>
                </div>
              </div>
            `;
            bannerWrap.querySelector('#dmReqAcceptBtn')?.addEventListener('click', async () => {
              conv = await updateMessageRequestStatus(conv, 'accepted');
              renderMessages(false);
              renderComposer();
            });
            bannerWrap.querySelector('#dmReqDeclineBtn')?.addEventListener('click', async () => {
              await updateMessageRequestStatus(conv, 'declined');
              origClose();
            });
            bannerWrap.querySelector('#dmReqBlockBtn')?.addEventListener('click', async () => {
              await blockUser(partnerUid);
              isBlockedByMe = true;
              renderMessages(false);
              renderComposer();
            });
          } else {
            bannerWrap.innerHTML = '';
          }
        }

        const lastOwn = [...messages].reverse().find((x) => x.senderUid === myUid);
        const partnerRead = convPartnerLastRead(conv, myUid);

        scrollEl.innerHTML = messages.length === 0
          ? `<div class="card muted-note" style="text-align:center; padding:22px;">${localizedText(lang, 'Sohbeti başlatmak için bir mesaj gönder.', 'Send a message to start chatting.')}</div>`
          : messages.map((m) => {
              const isMine = m.senderUid === myUid;
              const showClock = expandedTimeId === m.msgId;
              const clockHtml = showClock ? `<span class="dm-msg-time-side">${escHtml(fmtClockHHMM(m.createdAtMs))}</span>` : '';
              const seen = isMine && lastOwn?.msgId === m.msgId ? (partnerRead >= m.createdAtMs && m.createdAtMs > 0) : false;

              let inner = '';
              if (m.isDeleted) {
                inner = `<span>${localizedText(lang, 'Bu mesaj silindi', 'This message was deleted')}</span>`;
              } else {
                const postCard = (m.msgType === 'shared_post' || m.msgType === 'post_reply') && m.sharedPostId
                  ? `
                    <div class="dm-shared-post-card" data-dm-open-post="${escHtml(m.sharedPostId)}" data-dm-open-owner="${escHtml(m.sharedPostOwnerUid)}">
                      <span class="accent-note" style="font-size:11px; font-weight:700;">${m.msgType === 'post_reply' ? 'Gönderine yanıt' : 'Paylaşılan Gönderi'} · @${escHtml(m.sharedPostOwnerUsername || '')}</span>
                      ${safeImgSrc(m.sharedPostThumb) ? `<img src="${escHtml(safeImgSrc(m.sharedPostThumb))}" alt="" />` : ''}
                      ${m.sharedPostCaption ? `<span style="font-size:12px; color:var(--ink);">${escHtml(m.sharedPostCaption)}</span>` : ''}
                    </div>
                  ` : '';

                const photoHtml = (m.msgType === 'photo' || m.msgType === 'post_reply') && safeImgSrc(m.mediaBase64)
                  ? `<img src="${escHtml(safeImgSrc(m.mediaBase64))}" data-dm-zoom-img="${escHtml(safeImgSrc(m.mediaBase64))}" style="width:100%; max-height:220px; border-radius:10px; object-fit:cover;" alt="" />`
                  : '';

                const gifSrc = m.msgType === 'gif'
                  ? (isSafeExternalGifUrl(m.mediaUrl) ? m.mediaUrl : (isSafeGifDataUrl(m.mediaBase64) ? m.mediaBase64 : safeImgSrc(m.mediaBase64)))
                  : '';
                const gifHtml = gifSrc ? `<img src="${escHtml(gifSrc)}" style="width:100%; max-height:180px; border-radius:10px; object-fit:contain;" alt="GIF" />` : '';

                const vidHtml = m.msgType === 'video'
                  ? `<button type="button" class="btn-secondary btn-sm" data-dm-play-vid="${escHtml(m.msgId)}" data-dm-chunks="${m.chunkCount}" data-dm-mime="${escHtml(m.mediaMime || 'video/mp4')}">${svgIcon('playArrow', 16)} Video (${m.mediaDurationSec || 0} sn)</button><div data-dm-vid-mount="${escHtml(m.msgId)}"></div>`
                  : '';

                const curSpd = speedMap.get(m.msgId) || 1;
                const audioHtml = m.msgType === 'audio' && m.mediaBase64
                  ? `
                    <div class="dm-voice-player">
                      <button type="button" class="btn-primary btn-sm" data-dm-audio-play="${escHtml(m.msgId)}" style="width:32px; height:32px; padding:0; border-radius:50%;">${svgIcon('playArrow', 16)}</button>
                      <div style="flex:1; min-width:0;">
                        <strong style="font-size:12px; display:block;">🎤 0:${String(Math.max(1, m.mediaDurationSec || 1)).padStart(2, '0')}</strong>
                        <span class="muted-note" style="font-size:10px;">Sesli mesaj</span>
                        <audio class="hidden" data-dm-audio-el="${escHtml(m.msgId)}" src="${escHtml(m.mediaBase64)}"></audio>
                      </div>
                      <button type="button" class="dm-speed-pill" data-dm-audio-speed="${escHtml(m.msgId)}">${curSpd === 1.5 ? '1.5x' : curSpd >= 2 ? '2x' : '1x'}</button>
                    </div>
                  ` : '';

                const txtHtml = m.text ? `<div style="white-space:pre-line;">${escHtml(m.text)}</div>` : '';
                const editTag = m.editedAtMs > 0 ? `<span class="dm-edited-tag">${localizedText(lang, 'değiştirildi', 'edited')}</span>` : '';
                inner = `${postCard}${photoHtml}${gifHtml}${vidHtml}${audioHtml}${txtHtml}${editTag}`;
              }

              return `
                <div class="dm-msg-row ${isMine ? 'mine' : 'theirs'}">
                  ${isMine ? clockHtml : ''}
                  <div class="dm-bubble ${m.isDeleted ? 'deleted' : ''}" data-dm-bubble="${escHtml(m.msgId)}" data-dm-mine="${isMine && !m.isDeleted ? '1' : '0'}">${inner}</div>
                  ${!isMine ? clockHtml : ''}
                </div>
                ${isMine && lastOwn?.msgId === m.msgId ? `
                  <div class="dm-receipt-row" style="color:${seen ? 'var(--accent)' : 'var(--ink-muted)'};">
                    ${svgIcon(seen ? 'doneAll' : 'check', 13)}<span>${seen ? localizedText(lang, 'Görüldü', 'Seen') : localizedText(lang, 'İletildi', 'Delivered')}</span>
                  </div>
                ` : ''}
              `;
            }).join('');

        const openDmMessageActionsModal = (msg) => {
          if (!msg || msg.isDeleted) return;
          const editable = canEditDmMessage(msg);
          openModal({
            title: localizedText(lang, 'Mesaj İşlemleri', 'Message Actions'),
            bodyHtml: `
              <div style="display:flex; flex-direction:column; gap:10px;">
                ${editable ? `
                  <button type="button" class="btn-secondary" id="dmActEditBtn" style="width:100%; justify-content:center;">
                    ${svgIcon('edit', 16)} ${localizedText(lang, `Düzenle (${dmEditRemainingMinutes(msg)} dk)`, `Edit (${dmEditRemainingMinutes(msg)}m)`)}
                  </button>
                ` : ''}
                <button type="button" class="btn-danger-outline" id="dmActDeleteBtn" style="width:100%; justify-content:center;">
                  ${svgIcon('deleteOutline', 16)} ${localizedText(lang, 'Mesajı Sil', 'Delete Message')}
                </button>
              </div>
            `,
            footerHtml: `<button type="button" class="btn-secondary" id="dmActCloseBtn" style="width:100%;">${escHtml(st.close)}</button>`,
            onMount: (closeAct, actEl) => {
              actEl.querySelector('#dmActCloseBtn')?.addEventListener('click', closeAct);
              actEl.querySelector('#dmActEditBtn')?.addEventListener('click', () => {
                closeAct();
                openModal({
                  title: localizedText(lang, 'Mesajı Düzenle', 'Edit Message'),
                  bodyHtml: `<textarea class="input-field" id="dmEditMsgTextarea" rows="4" maxlength="1000" style="width:100%; resize:vertical;">${escHtml(msg.text || '')}</textarea>`,
                  footerHtml: `
                    <button type="button" class="btn-secondary" id="dmEditCancelBtn" style="flex:1;">${localizedText(lang, 'Vazgeç', 'Cancel')}</button>
                    <button type="button" class="btn-primary" id="dmEditSaveBtn" style="flex:1;">${escHtml(st.save)}</button>
                  `,
                  onMount: (closeEd, edEl) => {
                    edEl.querySelector('#dmEditCancelBtn')?.addEventListener('click', closeEd);
                    edEl.querySelector('#dmEditSaveBtn')?.addEventListener('click', async () => {
                      const nextTxt = (edEl.querySelector('#dmEditMsgTextarea')?.value || '').trim();
                      if (!nextTxt) {
                        showToast(localizedText(lang, 'Mesaj boş olamaz', 'Message cannot be empty'));
                        return;
                      }
                      closeEd();
                      try {
                        const upd = await editDmMessage(conv, msg, nextTxt);
                        messages = messages.map((x) => (x.msgId === upd.msgId ? upd : x));
                        renderMessages(false);
                      } catch (err) {
                        showToast(sanitizeErrorMessage(err));
                      }
                    });
                  }
                });
              });
              actEl.querySelector('#dmActDeleteBtn')?.addEventListener('click', async () => {
                closeAct();
                try {
                  const tomb = await softDeleteDmMessage(conv, msg);
                  messages = messages.map((x) => (x.msgId === tomb.msgId ? tomb : x));
                  renderMessages(false);
                } catch (err) {
                  showToast(sanitizeErrorMessage(err));
                }
              });
            }
          });
        };

        // Short tap toggles clock side timestamp; long press / right-click on own non-deleted bubble opens Message Actions modal (1:1 Android)
        scrollEl.querySelectorAll('[data-dm-bubble]').forEach((b) => {
          const msgId = b.dataset.dmBubble;
          const isOwnActive = b.dataset.dmMine === '1';
          const toggleClock = (e) => {
            if (e?.target?.closest?.('button, a, audio, video, [data-dm-open-post], [data-dm-zoom-img]')) return;
            expandedTimeId = expandedTimeId === msgId ? null : msgId;
            renderMessages(false);
          };
          if (isOwnActive) {
            bindLongPressElement(
              b,
              () => {
                const target = messages.find((x) => x.msgId === msgId);
                if (target) openDmMessageActionsModal(target);
              },
              toggleClock
            );
            b.addEventListener('contextmenu', (e) => {
              e.preventDefault();
              const target = messages.find((x) => x.msgId === msgId);
              if (target) openDmMessageActionsModal(target);
            });
          } else {
            b.addEventListener('click', toggleClock);
          }
        });
        scrollEl.querySelectorAll('[data-dm-open-post]').forEach((el) => el.addEventListener('click', (e) => {
          e.stopPropagation();
          origClose();
          onOpenSinglePost?.(el.dataset.dmOpenPost, el.dataset.dmOpenOwner || '');
        }));
        scrollEl.querySelectorAll('[data-dm-zoom-img]').forEach((img) => img.addEventListener('click', (e) => {
          e.stopPropagation();
          openModal({
            title: '',
            fullscreen: true,
            bodyHtml: `<div style="display:flex; align-items:center; justify-content:center; min-height:70vh;"><img src="${escHtml(img.dataset.dmZoomImg)}" style="max-width:100%; max-height:80vh; border-radius:12px;" /></div>`,
            footerHtml: `<button type="button" class="btn-secondary" id="dmImgZClose" style="width:100%;">${escHtml(st.close)}</button>`,
            onMount: (c2, m2) => m2.querySelector('#dmImgZClose')?.addEventListener('click', c2)
          });
        }));
        scrollEl.querySelectorAll('[data-dm-play-vid]').forEach((btn) => btn.addEventListener('click', async (e) => {
          e.stopPropagation();
          btn.disabled = true;
          const mid = btn.dataset.dmPlayVid;
          const box = scrollEl.querySelector(`[data-dm-vid-mount="${CSS.escape(mid)}"]`);
          try {
            const url = await loadDmVideoBlobUrl(conv.convId, mid, Number(btn.dataset.dmChunks || 0), btn.dataset.dmMime);
            if (box) box.innerHTML = `<video src="${escHtml(url)}" controls playsinline autoplay style="width:100%; max-height:200px; border-radius:8px; background:#000;"></video>`;
            btn.classList.add('hidden');
          } catch (err) {
            btn.disabled = false;
            showToast(sanitizeErrorMessage(err));
          }
        }));
        scrollEl.querySelectorAll('[data-dm-audio-play]').forEach((btn) => btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const mid = btn.dataset.dmAudioPlay;
          const audio = scrollEl.querySelector(`[data-dm-audio-el="${CSS.escape(mid)}"]`);
          if (!audio) return;
          audio.playbackRate = speedMap.get(mid) || 1;
          if (audio.paused) {
            audio.play().catch(() => {});
            btn.innerHTML = svgIcon('pause', 16);
          } else {
            audio.pause();
            btn.innerHTML = svgIcon('playArrow', 16);
          }
          audio.onended = () => { btn.innerHTML = svgIcon('playArrow', 16); };
        }));
        scrollEl.querySelectorAll('[data-dm-audio-speed]').forEach((btn) => btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const mid = btn.dataset.dmAudioSpeed;
          const prev = speedMap.get(mid) || 1;
          const next = prev < 1.25 ? 1.5 : (prev < 1.75 ? 2 : 1);
          speedMap.set(mid, next);
          btn.textContent = next === 1.5 ? '1.5x' : (next >= 2 ? '2x' : '1x');
          const audio = scrollEl.querySelector(`[data-dm-audio-el="${CSS.escape(mid)}"]`);
          if (audio) audio.playbackRate = next;
        }));
        if (scrollBottom) scrollEl.scrollTop = scrollEl.scrollHeight;
      };

      const renderGifDrawer = () => {
        if (!gifMount) return;
        if (!showGifs || !conv) { gifMount.innerHTML = ''; return; }
        const presets = getGifPresets();
        gifMount.innerHTML = `
          <div class="card" style="padding:8px; display:flex; flex-direction:column; gap:8px;">
            <div class="gif-drawer">
              ${presets.map((g) => `<button type="button" class="gif-preset-btn" data-dmgif-id="${escHtml(g.id)}"><img src="${escHtml(g.dataUrl)}" alt="${escHtml(g.label)}" /></button>`).join('')}
            </div>
            <div style="display:flex; gap:6px;">
              <input type="url" class="input-field" id="dmExtGifInput" placeholder="Tenor / Giphy HTTPS linki" style="flex:1;" />
              <button type="button" class="btn-primary btn-sm" id="dmExtGifSendBtn">GIF</button>
            </div>
          </div>
        `;
        gifMount.querySelectorAll('[data-dmgif-id]').forEach((b) => b.addEventListener('click', async () => {
          const g = presets.find((p) => p.id === b.dataset.dmgifId);
          if (!g || sending) return;
          showGifs = false; renderGifDrawer(); sending = true;
          try {
            const res = await sendMessage({ conv, msgType: 'gif', mediaBase64: g.dataUrl });
            conv = res.conversation; messages = [...messages.filter((x) => x.msgId !== res.message.msgId), res.message];
            renderMessages(true); renderComposer();
          } catch (err) { showToast(sanitizeErrorMessage(err)); }
          sending = false;
        }));
        gifMount.querySelector('#dmExtGifSendBtn')?.addEventListener('click', async () => {
          const raw = gifMount.querySelector('#dmExtGifInput')?.value || '';
          if (!isSafeExternalGifUrl(raw)) { showToast(localizedText(lang, 'Sadece Tenor/Giphy HTTPS linki', 'Tenor/Giphy HTTPS only')); return; }
          showGifs = false; renderGifDrawer(); sending = true;
          try {
            const res = await sendMessage({ conv, msgType: 'gif', mediaUrl: raw.trim() });
            conv = res.conversation; messages = [...messages.filter((x) => x.msgId !== res.message.msgId), res.message];
            renderMessages(true); renderComposer();
          } catch (err) { showToast(sanitizeErrorMessage(err)); }
          sending = false;
        });
      };

      const renderComposer = () => {
        if (!compMount || !conv) return;
        if (isBlockedByMe || hasBlockedMe) {
          compMount.innerHTML = `<div class="dm-composer-bar muted-note" style="text-align:center;">${isBlockedByMe ? localizedText(lang, 'Bu kullanıcıyı engelledin.', 'User blocked.') : localizedText(lang, 'Bu kullanıcıya mesaj gönderilemez.', 'Messaging unavailable.')}</div>`;
          return;
        }
        if (conv.status === 'request' && conv.recipientUid === myUid) {
          compMount.innerHTML = `<div class="dm-composer-bar muted-note" style="text-align:center;">${localizedText(lang, 'Yanıtlamak için yukarıdan isteği kabul et.', 'Accept the request above to reply.')}</div>`;
          return;
        }
        if (isConvRequestLocked(conv, myUid) || conv.status === 'declined') {
          compMount.innerHTML = `<div class="dm-composer-bar muted-note" style="text-align:center;">${conv.status === 'declined' ? localizedText(lang, 'İstek reddedildi.', 'Request declined.') : localizedText(lang, 'Mesaj isteğin gönderildi. Onaylanana kadar başka mesaj atamazsın.', 'Request sent. Wait for acceptance.')}</div>`;
          return;
        }

        if (isRecording) {
          compMount.innerHTML = `
            <div class="dm-composer-bar">
              <div class="dm-rec-pill">
                <span id="dmRecLabel">Ses kaydediliyor... 0:${String(recSeconds).padStart(2, '0')} / 0:35</span>
                <div style="display:flex; gap:6px;">
                  <button type="button" class="btn-icon-ghost" id="dmRecCancelBtn" style="color:var(--danger-vivid, #DC2626);">${svgIcon('close', 18)}</button>
                  <button type="button" class="btn-icon-ghost" id="dmRecSendBtn" style="color:var(--accent);">${svgIcon('send', 18)}</button>
                </div>
              </div>
            </div>
          `;
          compMount.querySelector('#dmRecCancelBtn')?.addEventListener('click', () => stopRec(false));
          compMount.querySelector('#dmRecSendBtn')?.addEventListener('click', () => stopRec(true));
          return;
        }

        compMount.innerHTML = `
          <div class="dm-composer-bar">
            ${conv.status === 'request' && conv.requesterUid === myUid ? `<div class="accent-note" style="font-size:11px;">${localizedText(lang, 'Takip etmediğin kişiye onaylanana kadar en fazla 1 mesaj atabilirsin.', 'Max 1 request message until accepted.')}</div>` : ''}
            <div class="dm-composer-row">
              <button type="button" class="btn-icon-ghost" id="dmCamPhotoBtn" title="Foto Çek">${svgIcon('camera', 19)}</button>
              <button type="button" class="btn-icon-ghost" id="dmGalPhotoBtn" title="Foto Seç">${svgIcon('photoLibrary', 19)}</button>
              <button type="button" class="btn-icon-ghost" id="dmCamVidBtn" title="Video Çek">${svgIcon('videocam', 19)}</button>
              <button type="button" class="btn-icon-ghost" id="dmGalVidBtn" title="Video Seç">${svgIcon('videoLibrary', 19)}</button>
              <button type="button" class="btn-icon-ghost" id="dmGifToggleBtn" title="GIF" style="color:var(--accent);">${svgIcon('gif', 22)}</button>
            </div>
            <div class="dm-composer-row">
              <button type="button" class="btn-icon-ghost" id="dmMicBtn" title="Sesli Mesaj" style="color:var(--accent);">${svgIcon('mic', 20)}</button>
              <input type="text" class="input-field" id="dmTextInput" placeholder="${localizedText(lang, 'Mesaj yaz...', 'Message...')}" maxlength="1000" style="flex:1;" />
              <button type="button" class="btn-primary btn-sm" id="dmSendBtn">${svgIcon('send', 16)}</button>
            </div>
          </div>
        `;

        const txtEl = compMount.querySelector('#dmTextInput');
        txtEl?.addEventListener('input', () => {
          if (!conv?.convId) return;
          clearTimeout(typingTimer);
          if (txtEl.value.trim()) {
            setChatActivityState(conv.convId, 'typing');
            typingTimer = setTimeout(() => setChatActivityState(conv.convId, 'idle'), 3500);
          } else {
            setChatActivityState(conv.convId, 'idle');
          }
        });

        const sendTextMsg = async () => {
          const val = (txtEl?.value || '').trim();
          if (!val || sending) return;
          txtEl.value = '';
          sending = true;
          try {
            const res = await sendMessage({ conv, msgType: 'text', text: val });
            conv = res.conversation;
            messages = [...messages.filter((x) => x.msgId !== res.message.msgId), res.message];
            setChatActivityState(conv.convId, 'idle');
            renderMessages(true);
            renderComposer();
          } catch (err) {
            txtEl.value = val;
            showToast(sanitizeErrorMessage(err));
          } finally {
            sending = false;
          }
        };
        compMount.querySelector('#dmSendBtn')?.addEventListener('click', sendTextMsg);
        txtEl?.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendTextMsg(); } });
        compMount.querySelector('#dmGifToggleBtn')?.addEventListener('click', () => { showGifs = !showGifs; renderGifDrawer(); });

        const handlePhotoPick = async (inputId, activityBefore) => {
          setChatActivityState(conv.convId, activityBefore);
          const files = await pickFilesFromInput(inputId);
          const f = files[0];
          if (!f) { setChatActivityState(conv.convId, 'idle'); return; }
          sending = true;
          setChatActivityState(conv.convId, 'uploading_photo');
          try {
            const dataUrl = await preparePhotoDataUrlForDm(f);
            const res = await sendMessage({ conv, msgType: 'photo', mediaBase64: dataUrl, mediaMime: 'image/jpeg' });
            conv = res.conversation;
            messages = [...messages.filter((x) => x.msgId !== res.message.msgId), res.message];
            renderMessages(true);
            renderComposer();
          } catch (err) {
            showToast(sanitizeErrorMessage(err));
          } finally {
            setChatActivityState(conv.convId, 'idle');
            sending = false;
          }
        };
        compMount.querySelector('#dmCamPhotoBtn')?.addEventListener('click', () => handlePhotoPick('dmPhotoCamInput', 'capturing_photo'));
        compMount.querySelector('#dmGalPhotoBtn')?.addEventListener('click', () => handlePhotoPick('dmPhotoGalInput', 'uploading_photo'));

        const handleVideoPick = async (inputId) => {
          setChatActivityState(conv.convId, 'capturing_photo');
          const files = await pickFilesFromInput(inputId);
          const vf = files[0];
          if (!vf) { setChatActivityState(conv.convId, 'idle'); return; }
          if (vf.size > 7500000) { setChatActivityState(conv.convId, 'idle'); showToast(localizedText(lang, 'Video boyutu max 7.5 MB olabilir', 'Video max size 7.5 MB')); return; }
          sending = true;
          setChatActivityState(conv.convId, 'uploading_photo');
          try {
            const res = await sendMessage({ conv, msgType: 'video', videoFile: vf });
            conv = res.conversation;
            messages = [...messages.filter((x) => x.msgId !== res.message.msgId), res.message];
            renderMessages(true);
            renderComposer();
          } catch (err) {
            showToast(sanitizeErrorMessage(err));
          } finally {
            setChatActivityState(conv.convId, 'idle');
            sending = false;
          }
        };
        compMount.querySelector('#dmCamVidBtn')?.addEventListener('click', () => handleVideoPick('dmVideoCamInput'));
        compMount.querySelector('#dmGalVidBtn')?.addEventListener('click', () => handleVideoPick('dmVideoGalInput'));
        compMount.querySelector('#dmMicBtn')?.addEventListener('click', startRec);
      };

      const startRec = async () => {
        if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
          showToast(localizedText(lang, 'Tarayıcın ses kaydını desteklemiyor', 'Voice recording unsupported in this browser'));
          return;
        }
        try {
          recStream = await navigator.mediaDevices.getUserMedia({ audio: true });
          const mime = ['audio/mp4', 'audio/webm;codecs=opus', 'audio/webm'].find((m) => MediaRecorder.isTypeSupported?.(m)) || '';
          mediaRec = mime ? new MediaRecorder(recStream, { mimeType: mime, audioBitsPerSecond: 24000 }) : new MediaRecorder(recStream);
          recChunks = [];
          recSeconds = 0;
          isRecording = true;
          setChatActivityState(conv.convId, 'recording_audio');
          mediaRec.ondataavailable = (ev) => { if (ev.data?.size > 0) recChunks.push(ev.data); };
          mediaRec.start();
          renderComposer();
          recTimer = setInterval(() => {
            recSeconds += 1;
            const lbl = modalEl.querySelector('#dmRecLabel');
            if (lbl) lbl.textContent = `Ses kaydediliyor... 0:${String(recSeconds).padStart(2, '0')} / 0:35`;
            if (conv?.convId && recSeconds % 3 === 0) setChatActivityState(conv.convId, 'recording_audio');
            if (recSeconds >= 35) stopRec(true);
          }, 1000);
        } catch {
          showToast(localizedText(lang, 'Mikrofon izni verilmedi', 'Microphone permission denied'));
        }
      };

      const stopRec = (sendIt) => {
        clearInterval(recTimer);
        recTimer = null;
        isRecording = false;
        const dur = Math.max(1, recSeconds);
        const rec = mediaRec;
        const stream = recStream;
        mediaRec = null;
        recStream = null;
        if (!rec) {
          stream?.getTracks()?.forEach((t) => t.stop());
          renderComposer();
          return;
        }
        rec.onstop = async () => {
          stream?.getTracks()?.forEach((t) => t.stop());
          if (!sendIt || !recChunks.length) {
            setChatActivityState(conv?.convId, 'idle');
            renderComposer();
            return;
          }
          const mime = rec.mimeType || 'audio/webm';
          const blob = new Blob(recChunks, { type: mime });
          const dataUrl = await new Promise((resolve) => {
            const fr = new FileReader();
            fr.onload = () => resolve(String(fr.result || ''));
            fr.readAsDataURL(blob);
          });
          sending = true;
          try {
            const res = await sendMessage({ conv, msgType: 'audio', mediaBase64: dataUrl, mediaMime: mime, mediaDurationSec: dur });
            conv = res.conversation;
            messages = [...messages.filter((x) => x.msgId !== res.message.msgId), res.message];
            renderMessages(true);
          } catch (err) {
            showToast(sanitizeErrorMessage(err));
          } finally {
            setChatActivityState(conv?.convId, 'idle');
            sending = false;
            renderComposer();
          }
        };
        try { rec.stop(); } catch { stream?.getTracks()?.forEach((t) => t.stop()); renderComposer(); }
      };

      const pollOnce = async (scrollBottom = false) => {
        if (!conv) return;
        const [latestConv, msgs, pPres, pAct] = await Promise.all([
          refreshConversation(conv.convId),
          fetchMessages(conv.convId).catch(() => messages),
          fetchUserPresence(partnerUid),
          fetchPartnerActivityState(conv.convId, partnerUid)
        ]);
        if (latestConv) conv = latestConv;
        presence = pPres;
        partnerActivity = pAct;
        const grew = msgs.length > messages.length;
        messages = msgs;
        conv = await markConversationRead(conv);
        renderHeader();
        renderMessages(scrollBottom || grew);
        onUnreadChanged?.();
      };

      const toggleMuteAction = () => {
        isMuted = !isMuted;
        setConversationMuted(partnerUid, isMuted);
        renderHeader();
        onUnreadChanged?.();
        showToast(isMuted ? localizedText(lang, 'Sohbet sessize alındı', 'Muted') : localizedText(lang, 'Sohbet bildirimleri açıldı', 'Unmuted'));
      };
      muteBtn?.addEventListener('click', toggleMuteAction);
      modalEl.querySelector('#dmChatMoreBtn')?.addEventListener('click', (e) => {
        e.stopPropagation();
        dropdown?.classList.toggle('hidden');
      });
      modalEl.addEventListener('click', () => dropdown?.classList.add('hidden'));
      modalEl.querySelector('#dmActViewProfile')?.addEventListener('click', () => { dropdown?.classList.add('hidden'); origClose(); onOpenPublicProfile?.(partnerUid, false); });
      modalEl.querySelector('#dmActToggleMute')?.addEventListener('click', () => { dropdown?.classList.add('hidden'); toggleMuteAction(); });
      modalEl.querySelector('#dmActToggleBlock')?.addEventListener('click', async () => {
        dropdown?.classList.add('hidden');
        try {
          if (isBlockedByMe) { await unblockUser(partnerUid); isBlockedByMe = false; }
          else { await blockUser(partnerUid); isBlockedByMe = true; }
          renderHeader(); renderMessages(false); renderComposer();
        } catch (err) { showToast(sanitizeErrorMessage(err)); }
      });

      getOrCreateConversation(partnerUid).then(async (summary) => {
        conv = summary.conversation;
        partner = summary.partner;
        presence = summary.partnerPresence;
        isBlockedByMe = summary.isBlockedByMe;
        hasBlockedMe = summary.hasBlockedMe;
        isMuted = summary.isMuted;
        renderHeader();
        await pollOnce(true);
        renderComposer();
        pollTimer = setInterval(() => pollOnce(false), 2500);
      }).catch((err) => {
        if (err?.code === 'USERNAME_REQUIRED' || String(err?.message || '').startsWith('USERNAME_REQUIRED:')) {
          origClose();
          requireUsernameOrPrompt({ openModal, showToast, lang, appState, photoMap, onProfileUpdated });
        } else if (scrollEl) {
          scrollEl.innerHTML = `<div class="card muted-note" style="color:var(--danger-vivid, #DC2626);">${escHtml(sanitizeErrorMessage(err))}</div>`;
        }
      });
    }
  });
}

export async function openReplyPostInDmModal(ctx) {
  const { post, openModal, showToast, lang = 'TR', S, appState, photoMap, pickFilesFromInput, onProfileUpdated, onSentOpenChat } = ctx;
  const ok = await requireUsernameOrPrompt(
    { openModal, showToast, lang, appState, photoMap, onProfileUpdated },
    localizedText(lang, 'Özel mesajla yanıtlamak için önce kullanıcı adı oluşturmalısın.', 'Create a username before sending private replies.')
  );
  if (!ok) return;
  let photoBase64 = '';
  let busy = false;
  const st = S();

  openModal({
    title: localizedText(lang, 'Özel Sohbetten Yanıtla', 'Reply in Private Chat'),
    bodyHtml: `
      <div class="card" style="background:var(--secondary); display:flex; flex-direction:column; gap:4px; margin-bottom:10px;">
        <strong style="font-size:12px; color:var(--accent);">@${escHtml(post.ownerUsername || post.ownerDisplayName || 'kullanici')}</strong>
        ${post.caption ? `<span style="font-size:12.5px;">${escHtml(post.caption.slice(0, 140))}</span>` : ''}
      </div>
      <textarea id="prText" class="input-field" rows="3" maxlength="1000" placeholder="${localizedText(lang, 'Yanıtını yaz (isteğe bağlı)...', 'Write your reply...')}"></textarea>
      <div class="btn-row" style="margin-top:8px;">
        <button type="button" class="btn-secondary btn-sm" id="prCamBtn">${svgIcon('photoCamera', 15)} ${localizedText(lang, 'Kamera', 'Camera')}</button>
        <button type="button" class="btn-secondary btn-sm" id="prGalBtn">${svgIcon('photoLibrary', 15)} ${localizedText(lang, 'Galeri', 'Gallery')}</button>
      </div>
      <div id="prPhotoBadge" class="accent-note" style="font-size:12px; margin-top:6px;"></div>
    `,
    footerHtml: `
      <div class="btn-row" style="width:100%;">
        <button type="button" class="btn-secondary btn-sm" id="prCancel">${escHtml(st.close)}</button>
        <button type="button" class="btn-primary btn-sm" id="prSend">${localizedText(lang, 'Gönder', 'Send')}</button>
      </div>
    `,
    onMount: (close, modalEl) => {
      const badge = modalEl.querySelector('#prPhotoBadge');
      modalEl.querySelector('#prCancel')?.addEventListener('click', close);
      const pick = async (id) => {
        const files = await pickFilesFromInput(id);
        if (!files[0]) return;
        photoBase64 = await preparePhotoDataUrlForDm(files[0]);
        if (badge) badge.textContent = localizedText(lang, 'Fotoğraf eklendi', 'Photo attached');
      };
      modalEl.querySelector('#prCamBtn')?.addEventListener('click', () => pick('dmPostReplyCamInput'));
      modalEl.querySelector('#prGalBtn')?.addEventListener('click', () => pick('dmPostReplyGalInput'));
      modalEl.querySelector('#prSend')?.addEventListener('click', async () => {
        if (busy) return;
        const txt = (modalEl.querySelector('#prText')?.value || '').trim();
        if (!txt && !photoBase64) { showToast(localizedText(lang, 'Metin veya fotoğraf ekle', 'Add text or photo')); return; }
        busy = true;
        try {
          const sum = await getOrCreateConversation(post.ownerUid);
          await sendMessage({
            conv: sum.conversation,
            msgType: 'post_reply',
            text: txt,
            mediaBase64: photoBase64,
            mediaMime: photoBase64 ? 'image/jpeg' : '',
            sharedPost: post
          });
          showToast(localizedText(lang, 'Özel sohbete gönderildi!', 'Sent in private chat!'));
          close();
          onSentOpenChat?.(post.ownerUid);
        } catch (err) {
          showToast(sanitizeErrorMessage(err));
          busy = false;
        }
      });
    }
  });
}

export async function openForwardPostToDmModal(ctx) {
  const { post, openModal, showToast, lang = 'TR', S, appState, photoMap, onProfileUpdated } = ctx;
  const ok = await requireUsernameOrPrompt(
    { openModal, showToast, lang, appState, photoMap, onProfileUpdated },
    localizedText(lang, 'Gönderiyi sohbette iletmek için önce kullanıcı adı oluşturmalısın.', 'Create a username before forwarding posts.')
  );
  if (!ok) return;
  const myUid = getStoredUid();
  let targets = [];
  let timer = null;

  openModal({
    title: localizedText(lang, 'Sohbette Gönder', 'Send in Chat'),
    bodyHtml: `
      <input type="text" class="input-field" id="fwNoteInput" placeholder="${localizedText(lang, 'Not ekle (isteğe bağlı)...', 'Add note...')}" maxlength="400" />
      <input type="text" class="input-field" id="fwSearchInput" placeholder="${localizedText(lang, '@kullanıcı ara...', 'Search @username...')}" style="margin-top:8px;" />
      <div id="fwTargetsMount" style="display:flex; flex-direction:column; gap:6px; margin-top:10px; max-height:280px; overflow-y:auto;">
        <div class="muted-note" style="text-align:center; padding:16px;">${localizedText(lang, 'Yükleniyor...', 'Loading...')}</div>
      </div>
    `,
    footerHtml: `<button type="button" class="btn-secondary" id="fwCloseBtn" style="width:100%;">${escHtml(S().close)}</button>`,
    onMount: async (close, modalEl) => {
      modalEl.querySelector('#fwCloseBtn')?.addEventListener('click', close);
      const box = modalEl.querySelector('#fwTargetsMount');
      const noteEl = modalEl.querySelector('#fwNoteInput');
      const searchEl = modalEl.querySelector('#fwSearchInput');

      const drawTargets = () => {
        if (!box) return;
        box.innerHTML = targets.length === 0
          ? `<div class="muted-note" style="text-align:center; padding:14px;">${localizedText(lang, 'Kullanıcı bulunamadı.', 'No users found.')}</div>`
          : targets.map((u) => `
            <div class="card" data-fw-uid="${escHtml(u.uid)}" style="padding:8px 10px; display:flex; align-items:center; justify-content:space-between; gap:8px; cursor:pointer;">
              <div style="display:flex; align-items:center; gap:8px; min-width:0;">
                ${avatarCircleHtml(u.avatarBase64, u.username || u.displayName, 34)}
                <div style="min-width:0;">
                  <strong style="font-size:13px; display:block;">${escHtml(u.displayName || `@${u.username}`)}</strong>
                  ${u.username ? `<span class="muted-note" style="font-size:11px;">@${escHtml(u.username)}</span>` : ''}
                </div>
              </div>
              <span class="accent-note" style="font-size:12px; font-weight:700;">${localizedText(lang, 'Gönder', 'Send')}</span>
            </div>
          `).join('');
        box.querySelectorAll('[data-fw-uid]').forEach((row) => row.addEventListener('click', async () => {
          const uid = row.dataset.fwUid;
          row.style.pointerEvents = 'none';
          try {
            const sum = await getOrCreateConversation(uid);
            await sendMessage({ conv: sum.conversation, msgType: 'shared_post', text: noteEl?.value || '', sharedPost: post });
            showToast(localizedText(lang, 'Gönderi iletildi!', 'Post sent!'));
            close();
          } catch (err) {
            row.style.pointerEvents = '';
            showToast(sanitizeErrorMessage(err));
          }
        }));
      };

      const [convs, fol] = await Promise.all([
        fetchMyConversations().catch(() => []),
        myUid ? fetchFollowList(myUid, false).catch(() => []) : Promise.resolve([])
      ]);
      const uniq = new Map();
      convs.filter((c) => !c.isBlockedByMe && !c.hasBlockedMe).forEach((c) => uniq.set(c.partner.uid, c.partner));
      fol.forEach((u) => { if (!uniq.has(u.uid)) uniq.set(u.uid, u); });
      targets = [...uniq.values()];
      drawTargets();

      searchEl?.addEventListener('input', () => {
        const q = (searchEl.value || '').trim();
        clearTimeout(timer);
        if (q.length < 2) { targets = [...uniq.values()]; drawTargets(); return; }
        timer = setTimeout(async () => {
          targets = (await searchProfiles(q).catch(() => [])).filter((u) => u.uid !== myUid);
          drawTargets();
        }, 250);
      });
    }
  });
}
