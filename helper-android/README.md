# ELAK Arayan Tanıma 1.0.0

Bu APK, ELAK Okulum'un yerine geçmez. Ana uygulama PWA olarak kalır.

Görevi:
- Android'in gelen çağrı numarasını CallScreeningService ile almak
- Numarayı ELAK Akıllı Rehber sunucusuna güvenli cihaz token'ı ile sormak
- Öğrenci/veli/personel eşleşmesini yüksek öncelikli bildirim olarak göstermek

Kurulum:
1. GitHub Actions ile APK üretin.
2. `ELAK-Arayan-Tanima-v1.0.0` artifact paketini indirin.
3. İçindeki `ELAK-Arayan-Tanima.apk` dosyasını `https://elak.mcoaihl.com/rehber/helper-admin.php` üzerinden yayınlayın.
4. PWA > Akıllı Rehber > Ayar > Yardımcı APK'yı Kur.
5. PWA'da Bu Telefonu Bağla düğmesine basın.
6. Yardımcı uygulamada Arayan Kimliği Yetkisini açın.

Paket: `tr.mcoaihl.elakcaller`
Minimum Android: 10 (API 29)
