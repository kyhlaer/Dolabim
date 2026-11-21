# 🧥 Dolabım — *by Kyhlaer*

> Gardırobunu dijitalleştir, cihaz içi yapay zeka ile stüdyo kesimleri oluştur ve ten renginle hava durumuna uygun kombinler keşfet. ✨

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Version](https://img.shields.io/badge/S%C3%BCr%C3%BCm-v1.43.1-F59E0B?style=flat-square)
![Language](https://img.shields.io/badge/Dil-TR%20%7C%20EN%20%7C%20DE%20%7C%20FR%20%7C%20ES%20%7C%20ZH-1F1F1D?style=flat-square)

---

## ✨ Neler Sunuyor?

- 📸 **Cihaz İçi AI Stüdyo Kesim (`u2netp.onnx`)** — Kıyafet fotoğraflarının arka planını internete göndermeden telefon üzerinde temizler; tek dokunuşla orijinal fotoğrafa geçebilir, `1:1` / `3:4` kırpıp `90°` döndürebilirsin.
- 🎨 **Otomatik Renk & Tür Tahmini + Çoklu Yükleme** — Galeriden seçtiğin birden fazla fotoğrafı sırayla (`1/N`) dolabına ekler; baskın rengi ve parça türünü otomatik tahmin eder.
- 🧑 **Bağımsız Yüz & Ten Rengi Uyum Analizi** — Kombin İlhamı sekmesinden kendi yüz fotoğrafını yüklediğinde (`user_face_photo.jpg`) yüzünü ve ten alt tonunu analiz edip 12–13 çeşit renk önerisi çıkarır *(2D Sanal Kabin boy fotoğrafını değiştirmez)*.
- 🌤️ **Cinsiyet & Hava Durumuna Göre Kombin + `🎲 Sürpriz`** — Yüklenen kıyafetlerden veya Ayarlar seçiminden (*Erkek / Kadın / Unisex*) cinsiyet tarzını algılar; 7 günlük tahmin, `🎲 Sürpriz` hızlı öneri ve parça detayına tek dokunuşla geçiş sunar.
- 🧳 **Gerçek Şehir & Hava Durumlu Akıllı Bavul Hazırlayıcı** — Gideceğin şehri yazıp seçtiğinde Open-Meteo üzerinden o şehrin 7 günlük hava durumunu çeker, 2–7 gün için **gün gün renk uyumlu kombinler** ve **otomatik kapsül bavul listesi** oluşturur.
- 🏷️ **İstek Listesi & Fiyat Düşüşü Bildirimi** — Almayı planladığın parçalara `https://` ürün linki ekleyip fiyatı çekebilir, fiyat düştüğünde bildirim alabilir (`🔥 İndirim`) ve satın alınca tek dokunuşla dolabına taşıyabilirsin.
- 🪞 **2D Sanal Kabin** — Boy fotoğrafının (`user_body_photo.jpg`) üzerine parçaları yerleştirip ekranın her yerinden iki parmakla yakınlaştırarak deneyebilirsin *(AI Destekli 3D Sanal Kabin Çok Yakında)*.
- ✨ **Kombin İlhamı (Erkek & Kadın · 82 Kombin) + Stil Keşfi** — Sağa/sola kaydırmalı Tinder tarzı **Stil Keşfi** (`Beğendiklerim ❤️` / `Geçtiklerim ✕`), **Google Alışveriş, Trendyol, Zara, Pull&Bear, Bershka, H&M, Koton, DeFacto ve LCW** arama bağlantılarıyla benzer ürünleri tek tıkla bulabilirsin.
- 📊 **Dolap Özeti, Hiç Bitmeyen Eksik Parça & Gardırop Detoksu** — Renk spektrum çubuğu, 24+ uyumlu renk önerisi, 3 kademeli hiç bitmeyen akıllı eksik temel parça önericisi ve **Gardırop Detoksu** (`0 kez giyilenler`).
- 🔄 **Uygulama İçi Güncelleme & Hatırlatıcı** — Yeni sürüm çıktığında doğrudan uygulama içinden APK'yı indirir, imza sertifikasını & bütünlüğünü doğrulayıp kurar; **"Sonra"** veya özel süreli **"Daha Sonra Hatırlat"** (saat/gün girerek) seçeneği sunar.
- 🌍 **3 Tema, 6 Dil & Uzaktan Yapılandırma** — *Modern (Açık)*, *Modern (Koyu)*, *Sokak Tarzı* temaları; **Türkçe, English, Deutsch, Français, Español, 中文 (Çince)** dil seçenekleri ve gelecek için hazır sessiz `remote_config.json` altyapısı.

---

## 🔒 Gizlilik & Güvenlik

- 🔐 **Tamamen Cihaz İçi:** Gardırop fotoğrafların, yüz/boy fotoğrafların ve verilerin `0600` (yalnızca uygulama sahibi erişimli) atomik dosya yazımıyla cihazda saklanır; bulut ve cihaz-transfer yedeklemelerine dahil edilmez (`allowBackup="false"`, `backup_rules.xml`, `data_extraction_rules.xml`).
- 🌐 **Katı HTTPS & SSRF Koruması (`SecurityGuards`):** `cleartextTrafficPermitted="false"` zorunluluğu ve ek olarak her ağ/yönlendirme adımında özel IP (`127.0.0.1`, `10.x`, `172.16-31.x`, `192.168.x`, `169.254.x`, `::1`), `localhost`, kimlik bilgisi sızdıran URI'lar ve güvensiz URL şemaları reddedilir.
- ✍️ **Doğrulanmış Uygulama İçi Güncelleme:** İndirilen güncelleme APK'ları yalnızca resmi GitHub Releases HTTPS alanlarından çekilir, boyut sınırından geçer, kurulum öncesi **imza sertifikası (SHA-256)** + paket kimliği + sürüm kodu doğrulanır.
- 🛡️ **Uygulama & Kod Koruması:** `ImageStorage` kanonik yol doğrulaması (*path traversal* koruması), sınırlı `FileProvider` kapsamı, Pro kilidi için tuzlu SHA-256 + sabit zamanlı karşılaştırma + kaba kuvvet kilidi ve ekran bindirme koruması (`filterTouchesWhenObscured = true`).

---

## 📲 Kurulum & Derleme

### 📦 Hazır APK ile Kurulum
1. Sağdaki [**Releases**](https://github.com/kyhlaer/Dolabim/releases) bölümünden güncel `Dolabim-v1.43.1.apk` dosyasını indir *(ilk kurulumdan sonraki sürümleri uygulamanın içindeki otomatik güncelleyiciyle indirebilirsin)*.
2. Android 8.0+ cihazında kurulumu onayla *(aynı anahtarla imzalandığı için mevcut dolabın silinmeden güncellenir)*.

### 💻 Kaynak Koddan Derleme
*Gereksinimler: JDK 17 · Android SDK (`compileSdk = 35`, `minSdk = 26`)*
```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```
> Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📝 Sürüm Notları

- **🚀 v1.43.1 *(Güncel)***
  - 🪶 **Yeni Minimalist Askı Logosu**: Beyaz arka plan üzerinde sade siyah askı ikonu ve açılış logosu
  - 🏷️ **İstek Listesi Link & Mağaza Arama Düzeltmesi**: Gzip/yönlendirme, JSON-LD & mağaza fiyat okuma motoru yenilendi; **Zara, Bershka, Pull&Bear, H&M, Koton, DeFacto, LCW, Trendyol ve Google Alışveriş** benzer ürün aramaları doğrudan Türkçe mağaza sayfalarına bağlandı
  - 🔄 **Uygulama İçi Otomatik Güncelleyici**: Yeni sürüm geldiğinde ekranda sorar, uygulama içinden indirir & imza doğrulamasıyla kurar; **"Sonra"** veya özel saat/gün süreli **"Daha Sonra Hatırlat"** desteği içerir
  - 🇨🇳 **Çince (`中文`) Dil Desteği** eklendi (6 dil: TR, EN, DE, FR, ES, ZH)
  - 🔒 **Tam Güvenlik Denetimi & Sıkılaştırma**: SSRF & açık yönlendirme korumalı `SecurityGuards`, güvenli harici link açıcı, atomik `0600` fotoğraf/veri kaydı, `user_face_photo.jpg` yedekleme hariç tutması, tuzlu SHA-256 + deneme kilidi ve güvenlik birim testleri
  - 🧑 Yüz & Ten Rengi fotoğrafı (`user_face_photo.jpg`) **2D Sanal Kabin boy fotoğrafından ayrıldı** + dar ekran buton taşması düzeltildi (`Galeri / Değiştir`)
  - ✨ **82 Kombin İlhamı** (Erkek 41 + Kadın 41) & Tinder tarzı sağ/sol kaydırmalı **Stil Keşfi** (`Beğendiklerim ❤️` / `Geçtiklerim ✕`)
  - 🧳 **Yeni Akıllı Bavul Hazırlayıcı**: Hedef şehir arama + anlık hava durumu + gün gün renk uyumlu kombinler + otomatik bavul listesi
  - 🎨 +16 yeni renk tonu (44 renk), genişletilmiş ten/palet renk önerileri, 3 kademeli hiç bitmeyen **Eksik Temel Parçalar**, **Gardırop Detoksu** ve `🎲 Sürpriz`
  - ⚙️ Gelecekte APK güncellemesi gerektirmeden özellik/reklam kontrolü için sıfır-görünümlü `remote_config.json` altyapısı
- **🚀 v1.43** — Yüz & Ten Rengi Uyum Analizi, Cinsiyet tercihi/tahmini, Kombin→Parça geçişi, 9 mağaza araması, DE/FR/ES dil desteği, 3D Sanal Kabin duyurusu
- **🏷️ v1.42** — Yeni logo, *Modern (Koyu)* tema, istek listesi fiyat takibi & bildirimi, **Erkek (28) / Kadın (28)** lookbook, Akıllı Bavul, Sürüm Notları
- **⚡ v1.41** — `Plus Jakarta Sans` tipografi, klasik renk spektrum çubuğu, çıkış onayı ve güvenlik sertleştirmeleri
- **✨ v1.4** — *Sokak Tarzı* teması, çoklu galeri yükleme, canlı kombin düzenleyici, 2D Kabin ve İstek Listesi
