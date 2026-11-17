# 🧥 Dolabım — by Kyhlaer

**Dolabım**, gardırobundaki kıyafetleri dijitalleştirmeni, arka planlarını cihaz içi yapay zeka ile temizlemeni ve bulunduğun şehrin 7 günlük hava durumu tahminine göre renk uyumlu kombinler oluşturmanı sağlayan modern bir **Android** gardırop ve stil asistanıdır.

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Version](https://img.shields.io/badge/S%C3%BCr%C3%BCm-v1.41-F59E0B?style=flat-square)
![Languages](https://img.shields.io/badge/Dil-TR%20%7C%20EN-1F1F1D?style=flat-square)

---

## ✨ Öne Çıkan Özellikler

### 1. Akıllı Gardırop & Stüdyo Kesim (`u2netp.onnx`)
- **Cihaz İçi Arka Plan Kaldırma:** Yüklenen veya kameradan çekilen kıyafet fotoğraflarını ONNX Runtime (`u2netp.onnx`) kullanarak doğrudan telefon üzerinde temiz beyaz stüdyo arka planına yerleştirir. İstersen tek dokunuşla orijinal fotoğraf görünümüne de geçebilirsin.
- **Kırpma & Döndürme:** Her fotoğraf için serbest, `1:1` veya `3:4` kırpma ve `90°` döndürme desteği.
- **Otomatik Renk & Tür Tahmini:** Eklenen kıyafetin baskın renklerini, alt kategorisini (Tişört, Hoodie, Gömlek, Jean, Kargo, Ceket, Sneaker vb.) ve adını otomatik tahmin eder; istediğin gibi düzenleyebilirsin.
- **Çoklu Galeri İçe Aktarma:** Galeriden tek seferde birden fazla fotoğraf seçildiğinde her fotoğrafı ayrı bir parça (`1/N`) olarak arka arkaya eklemeni sağlar.
- **Fiyatlı İstek Listesi (Wishlist):** Almayı planladığın parçaları fiyat etiketiyle ayrı sekmede takip edebilir, satın aldığında tek dokunuşla ana dolabına taşıyabilirsin.

### 2. Hava Durumuna Göre Kombin Önerileri & Canlı Düzenleyici
- **7 Günlük Canlı Hava Tahmini:** Open-Meteo üzerinden şehir seçimiyle veya GPS/IP tabanlı **Konumumu Bul** özelliğiyle bugünün ve önümüzdeki günlerin hava koşullarına uygun kombinler önerir.
- **Gerçekçi Renk Uyumu Puanı:** Nötrler, toprak tonları, kontrast ve çarpışan renk kurallarına göre gerçekçi `%` uyum puanı hesaplar.
- **Kombin Detayı (`OutfitDetailScreen`):** Herhangi bir önerilen veya kayıtlı kombine tıkladığında parçaları üstten alta (**Aksesuar → Dış Giyim → Üst → Takım → Alt → Ayakkabı**) listeler; parça **Değiştir**, **Çıkar**, **Parça Ekle** ve anlık uyum skoru önizlemesi sunar.

### 3. 2D Sanal Kabin
- Kendi tam boy fotoğrafını yükleyip üzerine dolabındaki parçaları yerleştirebilir; **iki parmakla yakınlaştırma (`pinch-to-zoom`)**, döndürme ve sürükleme hareketleriyle önizleme yapabilirsin.

### 4. Sokak Stili İlhamı & Dolap Özeti
- **Kombin İlhamı:** 20–25 yaş genç erkek sokak stili referans kombinleri ve tam ekran önizleme.
- **Dolap Özeti:** Renk spektrum dağılım çubuğu, kıyafet türleri rozetleri, genel kategori dağılımı ve alt kısımda açılıp kapanabilen **Eksik Temel Parçalar / Eksik Uyumlu Renkler** önerileri.

### 5. Çift Tema, Çoklu Dil & Stüdyo Prompt Aracı
- **Modern** (ferah krem/beyaz katalog) ve **Sokak Tarzı** (koyu arduvaz, grafit kartlar & sıcak kehribar vurgular) temaları.
- **Türkçe (`TR`) / English (`EN`)** tam arayüz desteği.
- Ayarlar içindeki **Stüdyo Fotoğraf Promptu** aracı ile e-ticaret stüdyo flat-lay promptunu tek tıkla kopyalayabilme.

---

## 🛠️ Kullanılan Teknolojiler & Mimari

| Alan | Teknoloji |
| :--- | :--- |
| **Dil & Arayüz** | Kotlin, Jetpack Compose, Material 3 (`Plus Jakarta Sans`) |
| **Görsel İşleme & AI** | ONNX Runtime Mobile (`onnxruntime-android` + `u2netp.onnx`), Android `ExifInterface` |
| **Görsel Önbellekleme** | Coil 2.7 (`512px` ızgara örnekleme, `%25` RAM + `80 MB` disk önbelleği) |
| **Veri & Kalıcılık** | Cihaz içi JSON deposu (`WardrobeRepository`) ve sahip-özel (`0600`) yerel dosya depolama |
| **Hava Durumu** | Open-Meteo HTTPS API (20 dk bellek içi önbellekli) |

### Güvenlik Önlemleri
- Tüm ağ trafiğinde yalnızca **HTTPS** zorunluluğu (`cleartextTrafficPermitted="false"`).
- Bulut/ADB yedeklemelerinden özel gardırop ve boy fotoğraflarının hariç tutulması (`allowBackup="false"`, `backup_rules.xml`, `data_extraction_rules.xml`).
- `ImageStorage` içinde kanonik dosya yolu (`canonicalPath`) doğrulamasıyla *path traversal* koruması ve eski geçici kamera dosyalarının otomatik temizliği.
- `FileProvider` kapsamının yalnızca geçici kamera önbelleğiyle sınırlandırılması (`file_paths.xml`).
- Ekran bindirme saldırılarına (*tapjacking*) karşı `filterTouchesWhenObscured = true`.

---

## 📂 Proje Yapısı

```text
app/src/main/
├── assets/
│   └── u2netp.onnx                 # Cihaz içi arka plan temizleme modeli
├── java/com/dresskit/wardrobe/
│   ├── MainActivity.kt             # Splash, tema/dil kalıcılığı, çoklu ürün akışı ve navigasyon
│   ├── model/WardrobeModels.kt     # Parça, kategori, renk, istek listesi ve kombin modelleri
│   ├── data/
│   │   ├── ImageStorage.kt         # Güvenli fotoğraf kaydı, renk & kategori tahmini
│   │   ├── StudioCutoutProcessor.kt# ONNX ile stüdyo kesimi üretimi
│   │   ├── OutfitRecommendationEngine.kt # 7 günlük hava tahmini & renk teorisi motoru
│   │   └── WardrobeRepository.kt   # JSON veri kalıcılığı ve doğrulama
│   └── ui/
│       ├── theme/                  # Modern & Sokak Tarzı temaları, tipografi, TR/EN metinler
│       ├── components/             # GarmentCard, ImageCropperDialog, VirtualTryOn2DDialog, ScrollEffects
│       └── screens/                # Wardrobe, Outfits, OutfitDetail, Inspiration, PaletteStats, AddEdit, ItemDetail
└── res/xml/                        # network_security_config, backup_rules, data_extraction_rules, file_paths
```

---

## 🚀 Kurulum ve Derleme

### Hazır APK Kurulumu
Hazır `.apk` kurulum dosyası boyut sınırı sebebiyle doğrudan **Releases** bölümünde yayınlanır:
1. Sağdaki **Releases** sayfasından en güncel `.apk` dosyasını (`Dolabim-v1.41.apk`) indir.
2. Android cihazında kurulumu onayla (aynı imza anahtarıyla imzalanan yeni sürümler mevcut gardırop verilerini ve fotoğraflarını koruyarak güncellenir).

### Kaynak Koddan Derleme
- **Gereksinimler:** JDK 17, Android SDK (`compileSdk = 35`, `minSdk = 26`).
```powershell
.\gradlew.bat assembleDebug
```
Çıktı APK dosyası `app/build/outputs/apk/debug/app-debug.apk` konumunda oluşturulur.

---

<p align="center">
  <b>Dolabım · v1.41</b> — by Kyhlaer
</p>
