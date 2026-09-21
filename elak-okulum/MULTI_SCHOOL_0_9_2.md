# ELAK Okulum 0.9.2 — Çok Okullu Mimari

## Temel ilke

Tek Android APK ve tek merkezi API birden fazla okul tarafından kullanılabilir. Her okul `schools.school_code` ile ayrılır. Kullanıcı adı benzersizliği okul içindedir (`school_id + username`), bu nedenle aynı kullanıcı adı farklı okullarda bulunabilir.

## Android giriş

Giriş alanları:
- Okul Kodu
- Kullanıcı Adı
- Şifre

Android merkezi giriş isteğinde `school_code` gönderir. Sunucu kullanıcıyı yalnız o okulun `school_id` değeri içinde arar. Cihaz son kullanılan okul kodunu saklar.

MCOAIHL eski uyumluluk kuralı: Akıllı Rehber ve İzin Takip eski servislerine yalnız `MCOAIHL` okul kodunda geri dönüş yapılır. Başka okul MCOAIHL servislerine yönlendirilmez.

## Okul verisi ayrımı

Merkezi tablolar okul ilişkisini `school_id` üzerinden taşır. Okul kullanıcıları, modülleri ve modül ayarları birbirinden ayrıdır. Yetkiler kullanıcı bazında uygulanır.

## Merkezi yönetim

`server/admin/index.php`
- Merkezi yönetici girişi
- Yeni okul oluşturma
- Okul kodu, ad, kısa ad, logo ve renkler
- İlk `school_admin` hesabını oluşturma

`server/admin/modules.php`
- Okula göre modül aç/kapat
- Okula özel görünen modül adı
- Okula özel HTTPS modül adresi
- Sıralama

## Android modül yönlendirme

Merkezi API profil yanıtı okul için etkin ve kullanıcının yetkili olduğu `modules` listesini döndürür. Android ana ekranı merkezi oturumda bu listeyi kullanır.

- MCOAIHL `izin_takip` -> native İzin Takip
- MCOAIHL `akilli_rehber` -> native Akıllı Rehber
- Okula özel `url` tanımlı diğer modüller -> uygulama içi güvenli web modülü
- Başka okulda URL tanımlanmamış bir modül MCOAIHL adresine düşmez; kullanıcıya yapılandırma uyarısı gösterilir.

## Sunucu dağıtımı

`.github/workflows/deploy-elak-central-server.yml` yalnız elle çalıştırılır. Gerekli GitHub Actions secrets:
- `ELAK_FTP_SERVER`
- `ELAK_FTP_USERNAME`
- `ELAK_FTP_PASSWORD`
- `ELAK_FTP_PROJECT_ROOT`

Workflow `elak-okulum/server` ve `elak-okulum/database` klasörlerini proje köküne yükler. Canlı `.env` ve `storage` dosyalarını silmez. Web document root proje içindeki `server` klasörünü göstermelidir.

## Sonraki geliştirmeler

- Okul kodunu QR / davet bağlantısıyla otomatik seçme
- Okul logosunu ve renklerini Android temaya dinamik uygulama
- Okul yöneticisinin kendi kullanıcı/rol/yetkilerini merkezi panelden yönetmesi
- Merkezi bildirim servisi ve FCM cihaz kaydı
