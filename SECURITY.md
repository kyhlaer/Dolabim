# Güvenlik Politikası / Security Policy

## Desteklenen Sürümler / Supported Versions

| Sürüm / Version | Güvenlik Desteği / Security Support |
| --- | --- |
| `1.43.x` | Aktif / Supported |
| `< 1.43.0` | Desteklenmiyor / Unsupported |

## Mimari Güvenlik İlkeleri / Security Controls

- **Yerel Gizlilik & İstemci Taraflı Şifreleme / Local-First Encryption:**
  - Android sürümünde kullanıcıya ait Gemini API anahtarı ve Hugging Face token'ları `AndroidKeyStore` üzerinden oluşturulan donanım/keystore destekli `AES-256-GCM` (`AES/GCM/NoPadding`) anahtarıyla şifreli olarak saklanır. Yedekleme (`android:allowBackup="false"`), bulut çıkarımı ve cleartext HTTP (`android:usesCleartextTraffic="false"`) devre dışıdır.
  - Web sürümünde kullanıcı anahtarları çıkarılamaz (`extractable: false`) WebCrypto `AES-256-GCM` anahtarıyla `IndexedDB` içinde tutulur ve yalnızca resmi API uç noktalarına güvenli `fetch` üzerinden gönderilir.
- **SSRF & Dış Bağlantı Koruması / SSRF & URL Hardening:** Tüm dış istekler ve ürün/mağaza bağlantıları yalnızca standart port (`443`) `https://` genel alan adlarına izin verir; loopback, link-local, CGNAT, ULA, IPv4-mapped/NAT64/Teredo/6to4 IPv6 ve octal/hex IP atlatmaları reddedilir.
- **Güncelleme Bütünlüğü / Update Integrity:** Uygulama içi APK ve delta paket güncellemeleri (`ApkDeltaPatcher` / `AppUpdateManager`) zorunlu SHA-256 doğrulamasından ve Zip-Slip / boyut sınırı kontrollerinden geçer.

## Açık Bildirimi / Reporting a Vulnerability

Bir güvenlik açığı tespit ederseniz lütfen herkese açık Issue açmak yerine GitHub **Private Vulnerability Reporting** (Security Advisories) üzerinden veya depo sahibi `@kyhlaer` ile doğrudan iletişime geçerek sorumlu bildirimde bulunun. İncelenen raporlara en kısa sürede yanıt verilir.
