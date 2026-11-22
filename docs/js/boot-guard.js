(function () {
  function showBootFallback(titleText, bodyText, hintText, showReset) {
    var splash = document.getElementById('splashScreen');
    if (splash) splash.classList.add('fade-out');

    var main = document.getElementById('mainContent');
    if (!main) return;
    main.textContent = '';

    var wrap = document.createElement('div');
    wrap.className = 'screen-pad';

    var card = document.createElement('div');
    card.className = 'card';
    card.style.marginTop = '32px';

    var h2 = document.createElement('h2');
    h2.className = 'page-title';
    h2.style.fontSize = '20px';
    h2.textContent = titleText;

    var p1 = document.createElement('p');
    p1.className = 'muted-note';
    p1.style.marginTop = '10px';
    p1.style.lineHeight = '1.5';
    p1.textContent = bodyText;

    var p2 = document.createElement('p');
    p2.className = 'accent-note';
    p2.style.marginTop = '10px';
    p2.style.lineHeight = '1.5';
    p2.textContent = hintText;

    card.appendChild(h2);
    card.appendChild(p1);
    card.appendChild(p2);

    if (showReset) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn-primary';
      btn.style.marginTop = '14px';
      btn.textContent = 'Yenile ve Önbelleği Temizle';
      btn.addEventListener('click', function () {
        var clearP = Promise.resolve();
        if ('caches' in window) {
          clearP = caches.keys().then(function (keys) {
            return Promise.all(keys.map(function (k) { return caches.delete(k); }));
          });
        }
        clearP.finally(function () { window.location.reload(); });
      });
      card.appendChild(btn);
    }

    wrap.appendChild(card);
    main.appendChild(wrap);
  }

  if (window.location.protocol === 'file:') {
    document.addEventListener('DOMContentLoaded', function () {
      showBootFallback(
        'Dolabım Yerel Sunucu Gerekli',
        'Modern tarayıcılar güvenlik (CORS) nedeniyle file:// üzerinden JavaScript modüllerini çalıştırmaz.',
        'Klasördeki Dolabim_Web_Ac.bat dosyasına çift tıklayın ve tarayıcıda http://127.0.0.1:8765/ adresini açın.',
        false
      );
    });
    return;
  }

  window.setTimeout(function () {
    if (!window.__DOLABIM_BOOTED__) {
      showBootFallback(
        'Dolabım Yüklenemedi',
        'Uygulama modülleri başlatılırken bir bağlantı veya önbellek sorunu oluştu.',
        'Dolabim_Web_Ac.bat üzerinden http://127.0.0.1:8765/ adresinin açık olduğundan emin olun.',
        true
      );
    }
  }, 5000);
})();
