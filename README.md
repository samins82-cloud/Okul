# ELAK İzin Takip Android

Android WebView uygulaması. Okulun mevcut izin takip sistemini Android uygulaması içinde açar.

- Uygulama: **ELAK İzin Takip**
- Paket: `com.elak.izintakip`
- Web adresi: `https://mcoaihl.com/izin/`
- Minimum Android: 7.0 (API 24)
- Hedef Android SDK: 35

## GitHub Actions ile APK

`.github/workflows/build-apk.yml` dosyası her `main` push'unda debug APK üretir ve `ELAK-Izin-Takip-debug` artifact'ı olarak yükler.

## Desteklenenler

- Oturum çerezlerinin korunması
- PDF / Excel / fotoğraf dosya seçimi
- Kamera ile fotoğraf yükleme
- Dosya indirme
- WhatsApp / telefon / SMS ve harici bağlantıları ilgili uygulamada açma
- Android geri tuşunda WebView geçmişinde geri dönme
- SSL hatasında bağlantıyı durdurma
