# 🧥 Dolabım — *by Kyhlaer*

> Gardırobunu dijitalleştir, cihaz içi yapay zeka ile stüdyo kesimleri oluştur ve hava durumuna göre renk uyumlu kombinler keşfet. ✨

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Version](https://img.shields.io/badge/S%C3%BCr%C3%BCm-v1.42-F59E0B?style=flat-square)
![Language](https://img.shields.io/badge/Dil-TR%20%7C%20EN-1F1F1D?style=flat-square)

---

## ✨ Neler Sunuyor?

- 📸 **Cihaz İçi AI Stüdyo Kesim (`u2netp.onnx`)** — Kıyafet fotoğraflarının arka planını internete göndermeden telefon üzerinde temizler; istersen tek dokunuşla orijinal fotoğrafa geçebilir, `1:1` / `3:4` kırpıp `90°` döndürebilirsin.
- 🎨 **Otomatik Renk & Tür Tahmini + Çoklu Yükleme** — Galeriden seçtiğin birden fazla fotoğrafı sırayla (`1/N`) dolabına ekler; baskın rengi ve parça türünü otomatik tahmin eder.
- 🌤️ **Hava Durumuna Göre Kombin & Canlı Düzenleyici** — Open-Meteo üzerinden 7 günlük tahmine ve GPS/IP konumuna göre renk teorisi uyumlu (`%`) kombinler önerir. Kombin detayında parçaları üstten alta **Değiştir / Çıkar / Ekle** ile anlık düzenleyebilirsin.
- 🧳 **Akıllı Bavul Hazırlayıcı** — Seyahat gün sayısı (2–7 gün) ve gidilecek hava durumuna göre dolabından kapsül bavul kontrol listesi çıkarır.
- 🏷️ **İstek Listesi & Fiyat Düşüşü Bildirimi** — Almayı planladığın parçalara `https://` ürün linki ekleyip fiyatı çekebilir, arka plan `JobScheduler` kontrolüyle fiyat düştüğünde telefona bildirim alabilir (`🔥 İndirim`) ve satın alınca tek dokunuşla dolabına taşıyabilirsin.
- 🪞 **2D Sanal Kabin** — Tam boy fotoğrafının üzerine dolabındaki parçaları yerleştirip iki parmakla yakınlaştırma, döndürme ve sürükleme ile önizleme yapabilirsin.
- 👟 **Kombin İlhamı (Erkek & Kadın · 56 Kombin)** — **Erkek (28)** ve **Kadın (28)** cinsiyet seçimiyle kategori bazlı sokak stili kombinleri ve tam ekran önizleme.
- 📊 **Dolap Özeti** — Renk spektrum çubuğu, parça türleri dağılımı, istek listesi bütçe özeti ve açılır-kapanır eksik parça/renk önerileri.
- 🌗 **3 Tema, Çift Dil & Ayarlar** — *Modern (Açık)*, *Modern (Koyu)* ve *Sokak Tarzı* temaları, `TR / EN` dil desteği, **Stüdyo Fotoğraf Promptu** ve uygulama içi **Sürüm Notları**.

---

## 🔒 Gizlilik & Güvenlik

- 🔐 **Tamamen Cihaz İçi:** Gardırop fotoğrafların, boy fotoğrafın ve verilerin cihazda `0600` izinli yerel depoda saklanır; bulut/ADB yedeklemelerine dahil edilmez (`allowBackup="false"`).
- 🌐 **Yalnızca HTTPS:** Tüm ağ istekleri (hava durumu ve fiyat kontrolü) `cleartextTrafficPermitted="false"` ile yalnızca HTTPS üzerinden çalışır.
- 🛡️ **Uygulama Koruması:** `ImageStorage` kanonik yol doğrulaması (*path traversal* koruması), sınırlı `FileProvider` kapsamı ve ekran bindirme koruması (`filterTouchesWhenObscured = true`).

---

## 🛠️ Teknoloji Özeti

| Alan | Altyapı |
| :--- | :--- |
| 🎯 **Arayüz** | Kotlin · Jetpack Compose · Material 3 (`Plus Jakarta Sans`) |
| 🤖 **Yapay Zeka** | ONNX Runtime Mobile (`u2netp.onnx`) |
| 🖼️ **Görsel & Önbellek** | Coil 2.7 (`512px` örnekleme, `%25` RAM + `80 MB` disk) |
| 🌦️ **Hava & Fiyat Takip** | Open-Meteo HTTPS API · Android `JobScheduler` |

---

## 📲 Kurulum & Derleme

### 📦 Hazır APK ile Kurulum (Önerilen)
1. Sağdaki [**Releases**](https://github.com/kyhlaer/Dolabim/releases) bölümünden güncel `Dolabim-v1.42.apk` dosyasını indir.
2. Android 8.0+ cihazında kurulumu onayla *(aynı anahtarla imzalandığı için mevcut dolabın silinmeden güncellenir)*.

### 💻 Kaynak Koddan Derleme
*Gereksinimler: JDK 17 · Android SDK (`compileSdk = 35`, `minSdk = 26`)*
```powershell
.\gradlew.bat assembleDebug
```
> Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📝 Sürüm Notları

- **🚀 v1.42 *(Güncel)***
  - 🎨 Yeni askı + turuncu-kırmızı noktalı uygulama logosu & açılış ekranı
  - 🌙 3. tema **Modern (Koyu)** eklendi
  - 🏷️ İstek listesine `https://` ürün linki, anlık fiyat çekme & fiyat düşüşü bildirimi eklendi
  - 👟 Kombin İlhamı'na **Erkek (28) / Kadın (28)** cinsiyet seçimi eklendi *(toplam 56 kombin)*
  - 🧳 **Akıllı Bavul Hazırlayıcı**, **İstek Listesi Bütçe Özeti** ve Ayarlar içi **Sürüm Notları** eklendi
- **⚡ v1.41** — `Plus Jakarta Sans` tipografi, klasik renk spektrum çubuğu, çıkış onayı ve güvenlik sertleştirmeleri
- **✨ v1.4** — *Sokak Tarzı* teması, `TR / EN` dil, çoklu galeri yükleme, canlı kombin düzenleyici, 2D Kabin ve İstek Listesi
