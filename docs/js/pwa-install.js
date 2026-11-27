import { APP_VERSION, localizedText } from './constants.js';
import { svgIcon } from './icons.js';

let deferredPrompt = null;
let onPromptReadyCallback = null;

export function isIosDevice() {
  const ua = navigator.userAgent || '';
  return /iPhone|iPad|iPod/i.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

export function isStandalonePwa() {
  return window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
}

export function initPwaInstall({ onUpdateUi, getLang, openModal, showToast }) {
  onPromptReadyCallback = onUpdateUi;

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js', { updateViaCache: 'none' }).then((reg) => {
      reg.update().catch(() => {});
    }).catch(() => {
      // Ignore if file:// or restricted origin
    });
  }

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    onPromptReadyCallback?.();
  });

  window.addEventListener('appinstalled', () => {
    deferredPrompt = null;
    sessionStorage.setItem('dolabim_install_dismissed', '1');
    onPromptReadyCallback?.();
    const lang = getLang();
    showToast(localizedText(lang, 'Dolabım ana ekrana eklendi', 'Added to Home Screen'));
  });

  const banner = document.getElementById('installBanner');
  const dismissBtn = document.getElementById('installBannerDismiss');
  const installBtn = document.getElementById('installBannerBtn');

  dismissBtn?.addEventListener('click', () => {
    sessionStorage.setItem('dolabim_install_dismissed', '1');
    banner?.classList.add('hidden');
  });

  installBtn?.addEventListener('click', () => triggerInstallFlow({ getLang, openModal, showToast, onUpdateUi }));
}

export function syncInstallBannerCopy(lang, strings) {
  const banner = document.getElementById('installBanner');
  if (!banner) return;
  const dismissed = sessionStorage.getItem('dolabim_install_dismissed') === '1';
  if (isStandalonePwa() || dismissed) {
    banner.classList.add('hidden');
    return;
  }
  banner.classList.remove('hidden');
  const t = document.getElementById('installBannerTitle');
  const s = document.getElementById('installBannerSub');
  const b = document.getElementById('installBannerBtn');
  if (t) t.textContent = strings.installTitle;
  if (s) s.textContent = strings.installSub;
  if (b) b.textContent = strings.installTitle;
}

export async function triggerInstallFlow({ getLang, openModal, showToast, onUpdateUi }) {
  const lang = getLang();
  if (isStandalonePwa()) {
    showToast(localizedText(lang, 'Uygulama zaten ana ekranında kurulu', 'Already installed on Home Screen'));
    return;
  }

  if (deferredPrompt) {
    try {
      await deferredPrompt.prompt();
      const choice = await deferredPrompt.userChoice;
      if (choice?.outcome === 'accepted') {
        deferredPrompt = null;
        sessionStorage.setItem('dolabim_install_dismissed', '1');
        onUpdateUi?.();
      }
      return;
    } catch {
      // Fallback to visual guide below
    }
  }

  const ios = isIosDevice();
  const title = localizedText(
    lang,
    ios ? "iPhone / iPad Ana Ekrana Ekle" : "Tarayıcıdan Ana Ekrana Ekle",
    ios ? "Add Dolabım to iPhone Home Screen" : "Add Dolabım to Home Screen"
  );

  const stepsHtml = ios
    ? `
      <div class="card" style="background: var(--secondary);">
        <p style="margin:0 0 8px; font-weight:600; font-size:14px; display:flex; align-items:center; gap:6px;">${svgIcon('shareIos', 17)} ${localizedText(lang, "iPhone / iPad Safari'de 3 Adım:", '3 Quick Steps in Safari:')}</p>
        <ol style="margin:0; padding-left:18px; display:flex; flex-direction:column; gap:8px; font-size:13px; line-height:1.5;">
          <li>${localizedText(lang, "Safari ekranının altındaki (veya sağ üstteki) <strong>Paylaş</strong> simgesine dokun.", 'Tap the <strong>Share</strong> icon in Safari.')}</li>
          <li>${localizedText(lang, 'Açılan menüyü hafifçe yukarı kaydırıp <strong>"Ana Ekrana Ekle"</strong> seçeneğine dokun.', 'Scroll down and tap <strong>"Add to Home Screen"</strong>.')}</li>
          <li>${localizedText(lang, 'Sağ üstten <strong>"Ekle"</strong> de — Dolabım tam ekran uygulama gibi açılacak ve verilerin iPhone’unda saklanacak.', 'Tap <strong>"Add"</strong> — Dolabım launches full-screen like a native app.')}</li>
        </ol>
      </div>
    `
    : `
      <div class="card" style="background: var(--secondary);">
        <p style="margin:0 0 8px; font-weight:600; font-size:14px; display:flex; align-items:center; gap:6px;">${svgIcon('download', 17)} ${localizedText(lang, 'Tarayıcı Menüsünden Kurulum:', 'Install from Browser Menu:')}</p>
        <ol style="margin:0; padding-left:18px; display:flex; flex-direction:column; gap:8px; font-size:13px; line-height:1.5;">
          <li>${localizedText(lang, 'Tarayıcının sağ üstündeki <strong>Menü (⋮)</strong> veya adres çubuğundaki <strong>Yükle</strong> simgesine dokun.', 'Tap the browser <strong>Menu (⋮)</strong> or address-bar <strong>Install</strong> button.')}</li>
          <li>${localizedText(lang, '<strong>"Ana Ekrana Ekle"</strong> veya <strong>"Uygulamayı Yükle"</strong> seçeneğini onayla.', 'Choose <strong>"Add to Home screen"</strong> or <strong>"Install App"</strong>.')}</li>
          <li>${localizedText(lang, 'Uygulama simgesi telefonunun veya bilgisayarının ana ekranına eklenecek.', 'The Dolabım icon will appear right on your home screen.')}</li>
        </ol>
      </div>
    `;

  openModal({
    title,
    bodyHtml: `
      <div style="display:flex; align-items:center; gap:12px;">
        <img src="./icons/icon-192.png" alt="Dolabım" style="width:54px; height:54px; border-radius:14px; border:1px solid var(--border); background:#fff;" />
        <div>
          <strong style="font-size:15px;">Dolabım ${APP_VERSION}</strong>
          <div class="muted-note">${localizedText(lang, 'Ücretsiz · App Store ücreti olmadan tüm cihazlarda çalışır.', 'Works free across iPhone, Android & Desktop without App Store.')}</div>
        </div>
      </div>
      ${stepsHtml}
    `,
    footerHtml: `<button type="button" class="btn-primary" id="installGuideOkBtn">${localizedText(lang, 'Anladım', 'Got It')}</button>`,
    onMount: (close) => {
      document.getElementById('installGuideOkBtn')?.addEventListener('click', close);
    }
  });
}
