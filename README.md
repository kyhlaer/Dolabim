# 🧥 Dolabım — *by Kyhlaer*

> Gardırobunu dijitalleştir, cihaz içi yapay zeka ile stüdyo kesimleri oluştur ve ten renginle hava durumuna uygun kombinler keşfet. ✨

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%7C%20Web%20PWA%20%2F%20iOS-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Version](https://img.shields.io/badge/S%C3%BCr%C3%BCm-v1.43.22-F59E0B?style=flat-square)
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
- ✨ **Kombin İlhamı (Erkek & Kadın · 82 Kombin) + Stil Keşfi** — Sağa/sola kaydırmalı Tinder tarzı **Stil Keşfi** (`Beğendiklerim ❤️` / `Geçtiklerim ✕`), **Google Alışveriş, Trendyol, Zara, Pull&Bear, Bershka, H&M, Koton, DeFacto ve LCW** arama bağlantılarıyla benzer ürünleri tek tıkla bulabilirsin.
- 📊 **Dolap Özeti, Hiç Bitmeyen Eksik Parça & Gardırop Detoksu** — Renk spektrum çubuğu, 24+ uyumlu renk önerisi, 3 kademeli hiç bitmeyen akıllı eksik temel parça önericisi ve **Gardırop Detoksu** (`0 kez giyilenler`).
- 🔄 **Uygulama İçi Güncelleme & Hatırlatıcı** — Yeni sürüm çıktığında doğrudan uygulama içinden APK'yı indirir, imza sertifikasını & bütünlüğünü doğrulayıp kurar; **"Sonra"** veya özel süreli **"Daha Sonra Hatırlat"** (saat/gün girerek) seçeneği sunar.
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
1. Sağdaki [**Releases**](https://github.com/kyhlaer/Dolabim/releases) bölümünden güncel `Dolabim-v1.43.22.apk` dosyasını indir *(ilk kurulumdan sonraki sürümleri uygulamanın içindeki otomatik güncelleyiciyle indirebilirsin)*.
2. Android 8.0+ cihazında kurulumu onayla *(aynı anahtarla imzalandığı için mevcut dolabın silinmeden güncellenir)*.

### 💻 Kaynak Koddan Derleme
*Gereksinimler: JDK 17 · Android SDK (`compileSdk = 35`, `minSdk = 26`)*
```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```
> Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📝 Sürüm Notları

- **🚀 v1.43.22 *(Güncel)***
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
