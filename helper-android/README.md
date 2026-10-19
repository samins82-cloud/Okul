# ELAK Arayan Tanıma 1.2.4

Android yardımcı uygulama; gelen GSM aramasını Android arayan kimliği rolü aracılığıyla algılar ve yalnız yetkili ELAK KVM verisiyle eşleştirir.

## v1.2.4 düzeltmeleri
- `READ_CONTACTS` izni kullanıcıya ayrı bir düğmeyle sorulur. Bu izin verilmeden Android, telefona kayıtlı kişilerden gelen çağrıları CallScreeningService'e iletmeyebilir.
- Gelen arama kartı, `CallScreeningService.onDestroy()` çağrıldığında kaldırılmaz; en fazla 30 saniye veya kullanıcı kapatana kadar görünür.
- Eski aramanın gecikmiş kaldırma görevi yeni arama kartını silemez.
- Sunucu bağlantı/okuma zaman aşımı sırasıyla 2,5 / 3,5 saniyeye çıkarıldı; telefon görüşmesinin kendisi ağ yanıtını beklemez.
- Yerel "Son Gelen Arama Tanılaması": arama algılandı mı, cihaz eşli mi, sunucu hata kodu verdi mi, KVM eşleşmesi var mı, kart gösterildi mi.
- Hata günlüğüne telefon numarası, token veya öğrenci/veli bilgisi yazılmaz.
- Derleme `versionCode=8`, `versionName=1.2.4`; mevcut CI imzalama önbelleği korunmuştur.

## Kurulum / Deneme
1. GitHub Actions `Build ELAK Caller APK` iş akışının başarılı olduğundan ve APK sertifikasının önceki kurulu sürümle aynı olduğundan emin olun.
2. Oluşan `ELAK-Arayan-Tanima.apk` ve JSON sürüm verisini sunucuda `/rehber/downloads/` dizinine **birlikte** yükleyin. GitHub derleme artefaktı tek başına canlı sunucudaki APK'yı güncellemez.
3. Android uygulamasını açın: Arayan Kimliği, Ekran Üstü, Bildirim, Kayıtlı Kişiler izinlerini etkinleştirin ve cihazı ELAK'a bağlayın.
4. Önce KVM'de yetkili, telefona kayıtlı olmayan; ardından telefona kayıtlı bir numara ile **gerçek gelen GSM araması** sınayın.
5. Sorun varsa uygulamanın "Son Gelen Arama Tanılaması" alanını kontrol edin. Kayıt hiç değişmiyorsa Android arayan kimliği rolü / üretici pil kısıtları incelenmeli.
6. Bazı üreticilerin kilit ekranı ve telefon arayüzünde overlay gösterimini kısıtlaması mümkündür; bu nedenle bildirim yedeği de korunmuştur.

## Güvenlik
Numaralar yalnız ELAK sunucusuyla HTTPS üzerinden eşleştirilir. Arama engellenmez. Kişi listesinin sunucuya aktarımı yapılmaz. APK'nın imzalama anahtarını, cihaz token'ını ve öğrenci fotoğraflarını açık depolarda tutmayın.
