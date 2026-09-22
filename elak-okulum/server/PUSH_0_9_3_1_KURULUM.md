# ELAK Okulum 0.9.3.1 — Anlık Push Kurulumu

Bu katman mevcut ELAK CORE kullanıcı/veritabanı yapısını değiştirmez. `mobile-push.php`, `api.php` ile aynı `ELAKCORESESSID` oturumunu ve aynı `db.php/config.php` yapılandırmasını kullanır.

## 1. Sunucu dosyası

`mobile-push.php` dosyasını `elak.mcoaihl.com` kök dizinine, mevcut `api.php` ve `db.php` ile aynı klasöre yükleyin.

İlk çağrıda aşağıdaki tablolar `CREATE TABLE IF NOT EXISTS` ile otomatik oluşturulur:

- `elak_mobile_devices`
- `elak_mobile_notifications`
- `elak_mobile_receipts`
- `elak_mobile_push_queue`
- `elak_mobile_user_classes`

Tablo öneki mevcut `config.php` içindeki `table_prefix` değerinden alınır.

## 2. Firebase servis hesabı

Firebase Console / Google Cloud üzerinden Firebase Cloud Messaging HTTP v1 yetkili bir servis hesabı JSON anahtarı oluşturun.

**JSON dosyasını `public_html` içine koymayın.** `private` isimli bir alt klasör de güvenli web dışı alan sayılmaz; örneğin `public_html/private/anahtar.json` kullanılmamalıdır.

Önerilen yapı:

```text
HOSTING-HESAP-KOKU/
├── private/
│   └── elak-okulum-firebase-adminsdk.json
└── public_html/
    ├── index.php
    ├── api.php
    ├── db.php
    └── mobile-push.php
```

Yeni push paketi servis hesabı dosyasının `public_html` veya aktif `DOCUMENT_ROOT` altında olduğunu tespit ederse güvenlik amacıyla anahtarı **kullanmayı reddeder**.

Mevcut `config.local.php` dosyanıza yalnız dosya yolunu ve worker anahtarını ekleyin:

```php
'firebase_service_account_file' => '/SUNUCUDA/WEB-DISI/private/elak-okulum-firebase-adminsdk.json',
'push_worker_key' => 'UZUN-RASGELE-GIZLI-ANAHTAR',
```

Servis hesabı özel anahtarını GitHub'a commit etmeyin ve APK içine koymayın.

## 3. Android Firebase istemci değerleri

Android Firebase istemci yapılandırması `com.elak.okulum` paket adıyla yapılır. İstemci değerleri APK içinde Firebase bağlantısı için kullanılır; servis hesabının özel anahtarı Android uygulamasına konmaz.

Firebase istemci ayarı bulunmazsa APK 15 dakikalık WorkManager senkronizasyonuna geri düşer. İstemci ayarı bulunduğunda FCM token otomatik alınır ve `mobile-push.php?action=device_register` ile ELAK CORE oturumuna bağlanır.

## 4. Worker / cron

Sunucu CLI cron destekliyorsa önerilen komut:

```bash
php /TAM/YOL/mobile-push.php worker
```

CLI yoksa HTTPS cron çağrısı kullanılabilir. `X-ELAK-PUSH-KEY` başlığı `push_worker_key` ile aynı olmalıdır.

Worker FCM HTTP v1 ile bekleyen gönderimleri paralel işler. FCM tarafından kabul edilmesi `sent`, cihaz uygulamasının bildirimi alması `delivered`, kullanıcının bildirimi açması `read` olarak tutulur.

## 5. Hedefleme

`notification_send` hedef türleri:

- `school` — okulun tüm kayıtlı cihazları
- `role` — örn. `teacher`, `parent`, `security`
- `user` — ELAK CORE `users.id`
- `class` — `elak_mobile_user_classes` tablosundaki `class_key`

Sınıf eşleşmeleri `class_members_save` işlemi ile sunucu tarafında yönetilir; istemcinin kendi kendine sınıf seçmesine güvenilmez.

## 6. Teslim / okundu raporu

`notification_report`, her mobil bildirim için:

- hedef cihaz sayısı
- FCM tarafından kabul edilen sayısı
- cihaza ulaştığı doğrulanan sayısı
- kullanıcı tarafından açılan/okunan sayısı

döndürür.

Bu ayrım özellikle önemlidir: FCM HTTP 200 yanıtı doğrudan 'kullanıcı gördü' anlamına gelmez. ELAK teslim ve okundu durumlarını Android istemcisinin geri bildirimi ile ayrı ayrı kaydeder.
