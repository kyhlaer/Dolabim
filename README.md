# 🧥 Dolabım — *by Kyhlaer*

> Gardırobunu dijitalleştir, cihaz içi yapay zeka ile stüdyo kesimleri oluştur ve ten renginle hava durumuna uygun kombinler keşfet. ✨

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Version](https://img.shields.io/badge/S%C3%BCr%C3%BCm-v1.43-F59E0B?style=flat-square)
![Language](https://img.shields.io/badge/Dil-TR%20%7C%20EN%20%7C%20DE%20%7C%20FR%20%7C%20ES-1F1F1D?style=flat-square)

---

## ✨ Neler Sunuyor?

- 📸 **Cihaz İçi AI Stüdyo Kesim (`u2netp.onnx`)** — Kıyafet fotoğraflarının arka planını internete göndermeden telefon üzerinde temizler; tek dokunuşla orijinal fotoğrafa geçebilir, `1:1` / `3:4` kırpıp `90°` döndürebilirsin.
- 🎨 **Otomatik Renk & Tür Tahmini + Çoklu Yükleme** — Galeriden seçtiğin birden fazla fotoğrafı sırayla (`1/N`) dolabına ekler; baskın rengi ve parça türünü otomatik tahmin eder.
- 🧑 **Yüz & Ten Rengi Uyum Analizi** — Kendi fotoğrafını yüklediğinde yüzünü ve ten alt tonunu analiz edip sana en çok yakışacak renk paletini çıkarır.
- 🌤️ **Cinsiyet & Hava Durumuna Göre Kombin + Parça Geçişi** — Yüklenen kıyafetlerden veya Ayarlar seçiminden (*Erkek / Kadın / Unisex*) cinsiyet tarzını algılar; 7 günlük tahmin ve ten uyumuna göre kombinler önerir. Kombin içindeki herhangi bir kıyafete tıklayıp detayına gidebilir, geri geldiğinde aynı ekrana dönebilirsin.
- 🧳 **Akıllı Bavul Hazırlayıcı** — Seyahat gün sayısı (2–7 gün) ve gidilecek hava durumuna göre kapsül bavul listesi çıkarır.
- 🏷️ **İstek Listesi & Fiyat Düşüşü Bildirimi** — Almayı planladığın parçalara `https://` ürün linki ekleyip fiyatı çekebilir, fiyat düştüğünde bildirim alabilir (`🔥 İndirim`) ve satın alınca tek dokunuşla dolabına taşıyabilirsin.
- 🪞 **2D Sanal Kabin** — Boy fotoğrafının üzerine parçaları yerleştirip ekranın her yerinden iki parmakla yakınlaştırarak deneyebilirsin *(AI Destekli 3D Sanal Kabin Çok Yakında)*.
- 👟 **Kombin İlhamı (Erkek & Kadın · 56 Kombin)** — Her kombindeki parçalarda ve tam kombinde **Google Alışveriş, Trendyol, Zara, Pull&Bear, Bershka, H&M, Koton, DeFacto ve LCW** arama bağlantılarıyla benzer ürünleri tek tıkla bulabilirsin.
- 📊 **Dolap Özeti & Genişletilmiş Eksik Parçalar** — Renk spektrum çubuğu, istek listesi bütçe özeti ve tıklayınca mağaza aramasına/önerilere yönlendiren **12+** temel parça kontrolü.
- 🌍 **3 Tema & 5 Dil Desteği** — *Modern (Açık)*, *Modern (Koyu)*, *Sokak Tarzı* temaları; **Türkçe, English, Deutsch, Français, Español** dil seçenekleri ve uygulama içi **Sürüm Notları**.

---

## 🔒 Gizlilik & Güvenlik

- 🔐 **Tamamen Cihaz İçi:** Gardırop fotoğrafların, boy fotoğrafın ve verilerin cihazda `0600` izinli yerel depoda saklanır; bulut/ADB yedeklemelerine dahil edilmez (`allowBackup="false"`).
- 🌐 **Yalnızca HTTPS:** Tüm ağ istekleri (hava durumu ve fiyat kontrolü) `cleartextTrafficPermitted="false"` ile yalnızca HTTPS üzerinden çalışır.
- 🛡️ **Uygulama Koruması:** `ImageStorage` kanonik yol doğrulaması (*path traversal* koruması), sınırlı `FileProvider` kapsamı ve ekran bindirme koruması (`filterTouchesWhenObscured = true`).

---

## 📲 Kurulum & Derleme

### 📦 Hazır APK ile Kurulum
1. Sağdaki [**Releases**](https://github.com/kyhlaer/Dolabim/releases) bölümünden güncel `Dolabim-v1.43.apk` dosyasını indir.
2. Android 8.0+ cihazında kurulumu onayla *(aynı anahtarla imzalandığı için mevcut dolabın silinmeden güncellenir)*.

### 💻 Kaynak Koddan Derleme
*Gereksinimler: JDK 17 · Android SDK (`compileSdk = 35`, `minSdk = 26`)*
```powershell
.\gradlew.bat assembleDebug
```
> Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📝 Sürüm Notları

- **🚀 v1.43 *(Güncel)***
  - 🧑 Boy/selfie fotoğrafından **Yüz & Ten Rengi Uyum Analizi**
  - 👤 Otomatik cinsiyet tahmini + Ayarlar'da **Erkek / Kadın / Unisex** seçimi
  - 🔗 Kombinlerden kıyafet detayına gitme & geri dönünce kaldığın yere dönüş
  - 🛍️ Kombin İlhamı ve **12+ Eksik Temel Parça** için doğrudan **Google, Trendyol, Zara, Pull&Bear, Bershka, H&M, Koton, DeFacto, LCW** arama bağlantıları + Kadın ilhamı yeni sıralaması (`4→1`, `5→2`)
  - 🌍 **Almanca (DE), Fransızca (FR), İspanyolca (ES)** dil desteği
  - 🪞 2D Sanal Kabin'e *AI Destekli 3D Sanal Kabin Çok Yakında* duyurusu & *Giydim* buton hizalama düzeltmesi
- **🏷️ v1.42** — Yeni logo, *Modern (Koyu)* tema, istek listesi fiyat takibi & bildirimi, **Erkek (28) / Kadın (28)** lookbook, Akıllı Bavul, Sürüm Notları
- **⚡ v1.41** — `Plus Jakarta Sans` tipografi, klasik renk spektrum çubuğu, çıkış onayı ve güvenlik sertleştirmeleri
- **✨ v1.4** — *Sokak Tarzı* teması, çoklu galeri yükleme, canlı kombin düzenleyici, 2D Kabin ve İstek Listesi
