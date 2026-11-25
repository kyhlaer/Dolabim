# 🧥 Dolabım — *by Kyhlaer*

> Gardırobunu dijitalleştir, cihaz içi yapay zeka ile stüdyo kesimleri oluştur ve ten renginle hava durumuna uygun kombinler keşfet. ✨

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%7C%20Web%20PWA%20%2F%20iOS-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Version](https://img.shields.io/badge/S%C3%BCr%C3%BCm-v1.43.275-F59E0B?style=flat-square)
![Language](https://img.shields.io/badge/Dil-TR%20%7C%20EN%20%7C%20DE%20%7C%20FR%20%7C%20ES%20%7C%20ZH-1F1F1D?style=flat-square)

---

## ✨ Neler Sunuyor?

- 📸 **Cihaz İçi & AI E-Ticaret Stüdyo Kesim (`u2netp.onnx` + Opsiyonel Gemini)** — Kıyafet fotoğraflarının arka planını, askı kancalarını ve masa kalıntılarını temizler; kırışıklık gölgelerini yumuşatıp `#FFFFFF` zeminli `1:1` profesyonel e-ticaret stüdyo fotoğrafına dönüştürür *(Ayarlar'da opsiyonel kendi Gemini `gemini-nano-banana-2.1` API anahtarı desteğiyle)*.
- 🎨 **Otomatik Renk & Tür Tahmini + Çoklu Yükleme** — Galeriden seçtiğin birden fazla fotoğrafı sırayla (`1/N`) dolabına ekler; baskın rengi ve parça türünü otomatik tahmin eder.
- 🧑 **Bağımsız Yüz & Ten Rengi Uyum Analizi** — Kombin İlhamı sekmesinden kendi yüz fotoğrafını yüklediğinde (`user_face_photo.jpg`) yüzünü ve ten alt tonunu analiz edip 12–13 çeşit renk önerisi çıkarır *(3D Sanal Kabin boy fotoğrafını değiştirmez)*.
- 🌤️ **Cinsiyet & Hava Durumuna Göre Kombin + `🎲 Sürpriz`** — Yüklenen kıyafetlerden veya Ayarlar seçiminden (*Erkek / Kadın / Unisex*) cinsiyet tarzını algılar; 7 günlük tahmin, `🎲 Sürpriz` hızlı öneri ve parça detayına tek dokunuşla geçiş sunar.
- 🧳 **Gerçek Şehir & Hava Durumlu Akıllı Bavul Hazırlayıcı** — Gideceğin şehri yazıp seçtiğinde Open-Meteo üzerinden o şehrin 7 günlük hava durumunu çeker, 2–7 gün için **gün gün renk uyumlu kombinler** ve **otomatik kapsül bavul listesi** oluşturur.
- 🏷️ **İstek Listesi & Fiyat Düşüşü Bildirimi** — Almayı planladığın parçalara `https://` ürün linki ekleyip fiyatı çekebilir, fiyat düştüğünde bildirim alabilir (`🔥 İndirim`) ve satın alınca tek dokunuşla dolabına taşıyabilirsin.
- 🪞 **3D Sanal Kabin (Vücut Entegrasyonlu)** — Boy fotoğrafındaki (`user_body_photo.jpg`) kişi silüetini ve yüz/omuz/bel/ayak noktalarını çıkararak kıyafetleri üzerine oturtur; silindirik **3D Kıvrım**, **Işık Uyumu**, iki parmakla her yerden yakınlaştırma ve opsiyonel **✨ AI Gerçekçi Giydirme** sunar.
- ✨ **Kombin İlhamı (Erkek & Kadın · 82 Kombin) + Stil Keşfi** — Sağa/sola kaydırmalı Tinder tarzı **Stil Keşfi** (`Beğendiklerim ❤️` / `Geçtiklerim ✕`), **Google Alışveriş, Trendyol, Hepsiburada, Zara, Bershka, H&M, Boyner, Koton, DeFacto ve LCW** arama bağlantılarıyla benzer ürünleri tek tıkla bulabilirsin.
- 📊 **Dolap Özeti, Hiç Bitmeyen Eksik Parça & Gardırop Detoksu** — Renk spektrum çubuğu, 24+ uyumlu renk önerisi, 3 kademeli hiç bitmeyen akıllı eksik temel parça önericisi ve **Gardırop Detoksu** (`0 kez giyilenler`).
- 🔄 **Uygulama İçi Akıllı Güncelleme & Hatırlatıcı** — Yeni sürüm çıktığında uyumlu ikili delta yama (`.dpatch`) varsa yalnızca değişen kısımları indirip mevcut kurulu APK ile güvenli birleştirir (yoksa sıkıştırılmış APK indirir); SHA-256 & imza sertifikasını doğrulayıp kurar.
- 🌍 **3 Tema & 6 Dil** — *Modern (Açık)*, *Modern (Koyu)*, *Sokak Tarzı* temaları ve **Türkçe, English, Deutsch, Français, Español, 中文 (Çince)** dil seçenekleri.

---

## 🔒 Gizlilik & Güvenlik

- 🔐 **Tamamen Cihaz İçi:** Gardırop fotoğrafların, yüz/boy fotoğrafların ve verilerin `0600` (yalnızca uygulama sahibi erişimli) atomik dosya yazımıyla cihazda saklanır; bulut ve cihaz-transfer yedeklemelerine dahil edilmez (`allowBackup="false"`, `backup_rules.xml`, `data_extraction_rules.xml`).
- 🌐 **Katı HTTPS & SSRF Koruması (`SecurityGuards`):** `cleartextTrafficPermitted="false"` zorunluluğu ve ek olarak her ağ/yönlendirme adımında özel IP (`127.0.0.1`, `10.x`, `172.16-31.x`, `192.168.x`, `169.254.x`, `::1`), `localhost`, kimlik bilgisi sızdıran URI'lar ve güvensiz URL şemaları reddedilir.
- ✍️ **Doğrulanmış Uygulama İçi Güncelleme:** İndirilen güncelleme APK'ları yalnızca resmi GitHub Releases HTTPS alanlarından çekilir, boyut sınırından geçer, kurulum öncesi **imza sertifikası (SHA-256)** + paket kimliği + sürüm kodu doğrulanır.
- 🛡️ **Uygulama & Kod Koruması:** `ImageStorage` kanonik yol doğrulaması (*path traversal* koruması), sınırlı `FileProvider` kapsamı ve ekran bindirme koruması (`filterTouchesWhenObscured = true`).

---

## 📲 Kurulum & Derleme

### 🌐 Web & iOS PWA (Kurulumsuz Tarayıcı Sürümü)
- Tarayıcıdan doğrudan aç: **[kyhlaer.github.io/Dolabim](https://kyhlaer.github.io/Dolabim/)**
- iPhone / iPad Safari ve Chrome üzerinden **"Ana Ekrana Ekle"** ile tam ekran uygulama gibi çalışır.

### 📦 Hazır APK ile Kurulum
1. Sağdaki [**Releases**](https://github.com/kyhlaer/Dolabim/releases) bölümünden güncel `Dolabim-v1.43.275.apk` dosyasını indir *(ilk kurulumdan sonraki sürümleri uygulamanın içindeki akıllı delta güncelleyiciyle otomatik indirebilirsin)*.
2. Android 8.0+ cihazında kurulumu onayla *(aynı anahtarla imzalandığı için mevcut dolabın silinmeden güncellenir)*.

### 💻 Kaynak Koddan Derleme
*Gereksinimler: JDK 17 · Android SDK (`compileSdk = 35`, `minSdk = 26`)*
```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```
> Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📝 Sürüm Notları

- **🚀 v1.43.275 *(Güncel)***
  - 💬 **Instagram Tarzı Direkt Mesajlaşma (`Sohbet`)**: Sosyal başlıkta Ayarlar kaldırılıp Profil sağa kaydırıldı, eski profil yerine canlı okunmamış rozetli **Sohbet** eklendi; takip ettiklerinle sohbet veya takip etmediklerin için onaylanana kadar **tek mesajlık Mesaj İstekleri** ayrıldı. **1x / 1,5x / 2x** hızda sesli mesaj, fotoğraf/video çekme & yükleme, GIF gönderme, balona dokununca `HH:mm` saat gösterimi, **15 dk** düzenleme (`değiştirildi`), yumuşak silme (`Bu mesaj silindi`), sohbeti sessize alma ve canlı *çevrimiçi / yazıyor / fotoğraf çekiyor / fotoğraf yüklüyor / ses kaydediyor / Görüldü / İletildi* göstergeleri eklendi *(v1.43.274 ve v1.43.273 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
  - 📌 **Gönderilerden Doğrudan Özel Yanıt & İletme (`⋮`)**: Her gönderinin sağ üstündeki `⋮` menüsünden **Özel Sohbetten Yanıtla** (metin + kamera/galeri fotoğrafı) ve **Sohbette Gönder** doğrudan çalışır
  - 👤 **Kullanıcı Adı Kapısı, Profil Fotoğrafı Büyütme & Web 1:1 Profil Arayüzü**: Takip, beğeni, yorum, gönderi ve sohbet için kullanıcı adı oluşturma zorunluluğu eklendi; akış ve yorumlardaki profil resimlerine basılı tutunca, kullanıcı profilindeki fotoğrafa tek dokunuşta tam ekran yakınlaştırma açılır; Web profil arayüzü üst başlık çizgisi, aralık ve `42px` buton boyutlarıyla Android ile 1:1 eşlendi
  - 🎨 **Bilimsel 12 Sezon CIE L*a*b* Ten & Kontrast Analizi**: Yanak/alın cilt piksellerinde gri-dünya beyaz dengesi ve yüz kontrastı kullanılarak soğuk/nötr/zeytin/sıcak alt tonlar 12 mevsime göre ayrıştırılır; **Kaçınılacak Tonlar** ve **Tüm 12 Sezon** açılır seçicisiyle manuel keşif imkânı eklendi
- **🚀 v1.43.274**
  - 🖼️ **Dolap Özeti 1:1 Birebir Görünüm**: Paylaşılan profillerdeki **Dolap Özeti** istatistik kutucukları, renk spektrum çubuğu kenarlığı, renk kapsülleri ve alt kategori etiketleri Android ile Web'de birebir eşlendi *(v1.43.273 ve v1.43.272 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
  - 🧭 **Profil Başlıkları & Üst Navigasyon Eşitliği**: İncelenen profillerde başlık her iki platformda **Kullanıcı Profili** olarak sabitlendi; Web profil ve keşfet pencerelerine sol üst geri / sağ üst paylaş butonları eklenip alttaki kapat çubukları kaldırıldı
  - 🔴 **Canlı Kırmızı Engelle & Net Biyografi**: Takip/Paylaş/Engelle tuşları hizalandı, **Engelle** tuşu canlı kırmızıya güncellendi, biyografi yazıları keskinleştirildi ve Web tarz etiketleri Android kapsülleriyle eşlendi
- **🚀 v1.43.273**
  - ✨ **Mobil & Web Birebir Tasarım Eşitlemesi**: Sosyal akıştaki gönderi butonları şeffaf ve okunaklı hale getirildi; `+ Paylaş` tuşu sağ alt köşeye sabitlenip **Keşfet** üst başlığa taşındı
  - 👤 **2 Sekmeli Profil & Vitrinde Tam Hizalama**: Profil penceresi **Profilim · Düzenle** olarak sadeleşti; takip/engelle/paylaş butonları ve 2 sütunlu vitrin kartları Android ile Web'de birebir eşlendi
  - 🎨 **Gardırop Renk Paleti & 20 Tarz Etiketi**: Paylaşılan profillere **Gardırop Renk Paleti** çubuğu eklendi, tarz etiketleri 20'ye çıkarıldı ve aç/kapa anahtarları yenilendi *(v1.43.272 ve v1.43.271 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
  - ⌨️ **Akıllı Klavye Odak Düzeni**: Hem mobil uygulamada hem Web/PWA'da metin kutularına yazarken ekranın yukarı kaçması düzeltildi
- **🚀 v1.43.272**
  - 👤 **Kullanıcı Adı & Türkçe Harf Desteği**: `@kyhlaer` ve Türkçe harfler içeren kullanıcı adları otomatik dönüştürülerek profil ve paylaşım bağlantılarında sorunsuz kullanılır
  - 🔗 **Onarılan Kombin Paylaşımı & Tema Uyumlu Paylaş Butonları**: Önerilen ve kayıtlı kombin paylaşım linki oluşturma hatası giderildi; kıyafet ve kombin kartlarındaki paylaşım simgeleri koyu ve açık temalara uyumlu sade dairesel tasarımla yenilendi
  - ✨ **Dengeli Sosyal Başlık & Tam Ekran Profil Düzenleme**: Sosyal sekmesi üst aksiyon butonları eşit aralıklarla hizalandı; Profil penceresi tam ekran yapılıp 3 sekme (**Profilim · Düzenle · Keşfet & Ara**) tek sıraya alındı ve **Kaydet & Yayınla** alt çubuğa sabitlendi
  - 🧭 **Kalıcı Kombin İlhamı Cinsiyet Seçimi**: Kombin İlhamı ve Stil Keşfi'nde en son tıklanan **Erkek / Kadın** tercihi artık hatırlanır *(v1.43.271 ve v1.43.270 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
- **🚀 v1.43.271**
  - ✨ **Ortadaki Sosyal Akış Sekmesi & Birleşik Simge**: Alt gezinme çubuğunun tam ortasına eklenen yenilenmiş **Sosyal** sekmesinden kombinlerini, look'larını, yeni parçalarını, fotoğraf ve kısa videolarını paylaşabilir; fotoğraf düzenleyiciyle kırpıp filtreleyebilir, ilk **15 dakika** içinde açıklamasını güncelleyebilir ve fotoğraf/GIF yorumları yazabilirsin
  - 🔗 **Kombin Linki Paylaşımı & Tek Tıkla Kaydetme**: Önerilen, kayıtlı ve detay kombinlerini `@kullanıcıadın` ile Web ve Android'de açılan link olarak paylaşabilir, beğenebilir veya tüm parçalarıyla dolabına aktarabilirsin
  - 🛡️ **Kullanıcı Engelleme, Sade Üst Bar & Kalp Bildirimleri**: Kullanıcı profillerinden doğrudan engelleyebilir ve **Sosyal -> Profil -> Düzenle** bölümünden engelleri yönetebilirsin; gizli hesap takip istekleri cihaza bildirim düşürür ve Ayarlar altına **GitHub** bağlantısı eklendi
  - ⏰ **Bulunduğun Saatten Başlayan 24 Saatlik Hava Tahmini**: Bugünkü saatlik tahmin artık günün başından değil doğrudan bulunduğun saatten başlayarak gece yarısından yarına uzanan 24 saati gösterir *(v1.43.270 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
- **🚀 v1.43.270**
  - ⏰ **24 Saatlik Saatlik Hava Durumu**: Kombinler ekranında hava durumu kartına dokunarak o günün saat saat sıcaklık, hissedilen, yağış % ve rüzgar bilgilerini görebilirsin
  - 🔗 **Kıyafet & Profil Paylaşımı + Tek Tıkla Kaydetme**: Dolabından sevdiğin parçaları `@kullanıcıadın` ve profil fotoğrafınla hem Web hem Android'de açılan link olarak paylaşabilir, beğenebilir veya dolabına/istek listene aktarabilirsin
  - 👤 **Herkese Açık / Gizli Profil, Vitrin & Takip Sistemi**: Kombinler, Kombin İlhamı ve Dolap Özeti sekmelerindeki Profil butonundan kullanıcı adı/fotoğraf seçebilir, sergileyeceğin parçalar ve gizleyeceğin dolap istatistiklerini belirleyip arkadaşlarını arayarak takip edebilirsin *(v1.43.263 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
- **🚀 v1.43.263**
  - 🧲 **Basılı Tutup Taşırken Otomatik Ekran Kaydırma**: Dolabım ızgarasında bir kıyafeti basılı tutup ekranın üst veya alt köşesine sürüklediğinde ekran artık yavaş ve akıcı şekilde yukarı/aşağı kayar; görünmeyen sıralara da rahatça taşınabilir *(v1.43.262 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
- **🚀 v1.43.262**
  - 🤖 **Doğru Katalog AI Hata Mesajları**: Hugging Face ZeroGPU sunucu yoğunluğu veya günlük limit hatalarında yanlış *"İnternet bağlantını kontrol et"* uyarısı düzeltildi
  - ✂️ **Açı Ekleme & Tek Fotoğraf Kırpma**: *"Kıyafeti Düzenle -> Açı Ekle"* akışında yeni ürün oluşturma hatası giderildi; ekleme ve detay ekranlarında tek tek fotoğraf kırpma (**"Fotoğrafı Düzenle"**) eklendi
  - 🧲 **Basılı Tutup Sıralama, Kalıcı Konum, Klavye & Sekme Geçişi**: Dolabım ızgarasında basılı tutup sürükle-bırak sıralama, seçilen hava durumu konumunu hatırlama, **Not** alanında klavye açılınca ekranın yukarı kayması, kayar alt menü hapı + yumuşak yatay sekme geçişi ve her açılışta yenilenen rastgele **Kombin İlhamı** sırası eklendi *(v1.43.261 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
- **🚀 v1.43.261**
  - ✂️ **Arka Planı Kaldır vs Katalog Ayrımı + "Katalog Fotoğrafı Kullan"**: Daha önce katalog fotoğrafına dönüştürülmüş parçalarda **"Arka Planı Kaldır"** seçildiğinde katalog hali yerine saf arka planı kaldırılmış kesime dönülür; katalog görseli ayrı yuvada saklanıp buton **"Katalog Fotoğrafı Kullan"** durumuna geçer
  - 📐 **Eşit Boyutlu Fotoğraf / Açı Ekle Tuşları**: Hem Android hem Web sürümünde *"Fotoğraf Ekle"* ve *"Açı Ekle"* (Kamera / Galeri) butonları birebir aynı yükseklik ve genişliğe hizalandı *(v1.43.26 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
- **🚀 v1.43.26**
  - 🖼️ **Evrensel Görsel Format & Boyut Desteği**: Hem Android'de (`ImageDecoder` API 28+ + tek geçişli akıllı önbellek/OOM ölçekleme) hem Web'de 4 aşamalı görsel çözücüyle **HEIC, HEIF, AVIF, WebP, GIF, BMP, TIFF, SVG** dahil tüm formatlar ve sınırsız dosya boyutu/çözünürlüğü desteklenir
  - 🧭 **Web Akıllı Alt Sekme & 4 Yönlü Yaylı Esneme (`Bouncy Overscroll`)**: Alt sekme ikonuna tıklayınca en üste yumuşak çıkış/tekrar basınca önceki konuma dönüş ve yukarı-aşağı-sağa-sola sınır esnemesi eklendi
  - 🎨 **1:1 Mobil Ana Ekran Logosu**: Web PWA ikonları (`any` + `maskable`) Android `ic_launcher` vektörüyle birebir aynı çizgi kalınlığına getirildi *(v1.43.25 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
- **🚀 v1.43.25**
  - 📱 **Web Android Dikey Kaydırma & Filtre UX Düzeltmesi**: Android telefonlarda dikey kaydırmayı kilitleyen CSS viewport kuralı çözüldü; hem Web hem Android'de **"Diğer Renkler (+N)"** filtresi ve kesilmeyen **"Filtreleri Temizle"** butonu eşitlendi
  - 🛍️ **Doğru Mağaza Arama Linkleri**: Bershka (`?gender=man/woman`), Zara (`&section=MAN/WOMAN`), bileşik parça sorgu sadeleştirmesi ve **Hepsiburada / Boyner** doğrudan ürün araması eklendi
  - 🔐 **Tam Kod & GitHub Güvenlik Kalkanı**: Android Keystore (`AES-256-GCM`) şifreli anahtar saklama, IPv6 NAT64/Teredo/6to4/obfuscated IPv4 SSRF koruması, `usesCleartextTraffic="false"`, Dependabot, Secret Scanning, `SECURITY.md` ve `main` dal koruması etkinleştirildi *(v1.43.24 kullanıcıları hafif `DPATCH1` delta yamasıyla güncellenir)*
- **🚀 v1.43.24**
  - 🎯 **Resmi Katalog AI Model Zinciri (`FLUX.2 / Qwen-2511 / FireRed-1.1`)**: 4 adımlı aşırı damıtılmış eski alanlar kaldırılıp tam kaliteli resmi modeller, aynı açı/arka-ön kilidi, doğal oranlı `1024px` girdi ve sıkı `%87` çerçeve temizliği eklendi *(ilk canlı `DPATCH1` delta yamasıyla güncellenir)*
- **🚀 v1.43.23**
  - ✂️ **Anahtarsız Arka Plan Kaldırma**: Hem mobilde (`u2netp.onnx`) hem Web'de (çoklu-küme CIE Lab GrabCut kesimi) API gerektirmeyen **"Arka Planı Kaldır"** geçişi ve güncellenen **Katalog Fotoğrafı Oluştur** promptu
  - 📦 **Küçük APK & Play Store Tarzı Akıllı Yama (`DPATCH1`)**: Native kütüphane sıkıştırmasıyla çok daha küçük kurulum paketi ve sonraki sürümlerde yalnızca değişen ikili farkı indiren kriptografik delta güncelleme sistemi
  - 📱 **Web Dokunmatik Gezinme, Geri Tuşu & 1:1 Stil Keşfi**: Web'de pürüzsüz tek parmak dikey kaydırma, sağa/sola sekme kaydırma, önceki modal/sekmeye dönen Geri tuşu ve mobil ile birebir tam ekran **Stil Keşfi**
- **🚀 v1.43.22**
  - 🤖 **AI Katalog & Vertex Express Desteği**: Hem `AIza...` hem de `AQ....` (Google Cloud / Vertex AI Express) Gemini API anahtarlarıyla uyumlu katalog fotoğrafı ve 3D Sanal Kabin desteği; anahtarsız ücretsiz bulut stüdyo ve opsiyonel Hugging Face tokenı korunur
  - 🎨 **Mobil & Web 1:1 UI Uyumu**: Web PWA ve Android sürümünde aynı akıllı bavul, kombin düzenleyici, sahip olunan renkleri öne çıkaran genişletilebilir `Detaylı Filtrele` modalı ve `Stil` etiketleri
  - 🛡️ **Tam Web & PWA Siber Güvenlik Altyapısı**: Katı CSP, clickjacking koruması, non-extractable AES-256-GCM şifreli anahtar deposu, SSRF ağ sınırı ve sihirli bayt dosya yükleme doğrulaması
- **🚀 v1.43.2**
  - 🛍️ **E-Ticaret Stüdyo Flat-Lay Dönüştürücü**: Kıyafet ekleme ve detay ekranında tek dokunuşla askıları, elleri ve arka planı temizler; kırışıklık gölgelerini yumuşatıp ticari stüdyo aydınlatmasıyla `#FFFFFF` `1:1` kare e-ticaret fotoğrafına çevirir *(Ayarlar'da opsiyonel kendi Gemini `gemini-nano-banana-2.1` anahtarı desteği)*
  - 🪞 **3D Sanal Kabin (Vücut Entegrasyonlu)**: Kişi silüeti + yüz/omuz/bel/ayak noktalarını çıkararak kıyafetleri üzerine oturtur; `drawBitmapMesh` silindirik **3D Kıvrım**, **Işık Uyumu**, yüz koruma maskesi ve opsiyonel **✨ AI Gerçekçi Giydirme** (`Önce / Sonra` & galeriye kaydetme) sunar
- **🚀 v1.43.1** — Minimalist askı logosu, istek listesi JSON-LD/Inditex fiyat çekme ve doğrudan 9 Türk mağaza arama düzeltmesi, uygulama içi otomatik güncelleyici, Çince (`中文`), 82 kombin & Stil Keşfi, canlı şehir hava durumlu Akıllı Bavul ve güvenlik sıkılaştırması
- **🚀 v1.43** — Yüz & Ten Rengi Uyum Analizi, Cinsiyet tercihi/tahmini, Kombin→Parça geçişi, 9 mağaza araması, DE/FR/ES dil desteği, 3D Sanal Kabin duyurusu
- **🏷️ v1.42** — Yeni logo, *Modern (Koyu)* tema, istek listesi fiyat takibi & bildirimi, **Erkek (28) / Kadın (28)** lookbook, Akıllı Bavul, Sürüm Notları
- **⚡ v1.41** — `Plus Jakarta Sans` tipografi, klasik renk spektrum çubuğu, çıkış onayı ve güvenlik sertleştirmeleri
- **✨ v1.4** — *Sokak Tarzı* teması, çoklu galeri yükleme, canlı kombin düzenleyici, 2D Kabin ve İstek Listesi
