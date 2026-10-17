# ELAK Arayan Tanıma 1.2.0

Bu Android yardımcı uygulama, ELAK Akıllı Rehber PWA ile birlikte çalışır. Ana rehber PWA olarak kalır; APK yalnızca Android'in gelen çağrı numarasını yakalayıp ELAK sunucusuyla eşleştirmek için kullanılır.

## v1.2.0 yenilikleri

- `CallScreeningService` çağrıya ağ sorgusunu beklemeden hemen yanıt verir; telefon görüşmesi akışı gecikmez.
- Sunucu sorgusu arka planda yapılır.
- Veli/öğrenci/personel adı yanında ilgili öğrenciler, sınıf ve ilişki bilgisi gösterilir.
- İlk ilgili öğrencinin fotoğrafı güvenli cihaz token'ı ile `native_student_photo` endpoint'inden yüklenebilir.
- Ekran üstü izin yoksa yüksek öncelikli bildirim yedeği kullanılır.
- Geçici internet hatasında eşleştirme token'ı artık silinmez; yalnız 401/403 durumunda geçersiz sayılır.
- Eşleştirme URL'sindeki sunucu adresi güvenlik için `https://elak.mcoaihl.com/rehber/` ile sınırlandırılmıştır.
- Uygulama ekranında arayan kimliği, ekran üstü ve bildirim izinleri ayrı ayrı gösterilir.
- Cihaz bağlantısı uygulamadan kaldırılabilir (`native_disconnect`).
- Derleme çıktısında APK ile birlikte gerçek SHA-256, dosya boyutu ve sürüm bilgisini içeren JSON metadata üretilir.

## Kurulum

1. GitHub Actions `Build ELAK Caller APK` iş akışını çalıştırın veya `main` dalında `helper-android/**` değişikliği yapın.
2. `ELAK-Arayan-Tanima-v1.2.0` artifact paketini indirin.
3. İçindeki `ELAK-Arayan-Tanima.apk` ve `ELAK-Arayan-Tanima.json` dosyalarını Akıllı Rehber yardımcı APK yönetiminden yayınlayın.
4. PWA > Akıllı Rehber > Ayarlar > Yardımcı APK'yı Kur.
5. PWA'da **Bu Telefonu Bağla** düğmesine basın.
6. Yardımcı uygulamada **Arayan Kimliği**, **Ekranın Üzerinde Gösterme** ve Android 13+ için **Bildirim** yetkilerini açın.

Paket: `tr.mcoaihl.elakcaller`  
Minimum Android: 10 (API 29)  
Hedef Android: API 35
