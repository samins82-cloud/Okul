# ELAK Okulum 0.9.2 – Merkezi Kimlik Kurulumu

## Amaç
0.9.2 ile Android uygulaması tek ELAK kimliği, çoklu rol, okul kapsamı, modül yetkileri ve kullanıcı–öğrenci/sınıf bağlantılarını destekler.

## Sunucu hedefi
Android istemcisi şu adresi kullanır:

`https://elak.mcoaihl.com/api/v1/index.php?r=...`

Repo içindeki `elak-okulum/server/api/v1/` klasörü sunucuda `/api/v1/` altına yayınlanmalıdır.

## Mevcut kurulum yükseltmesi
1. Veritabanının yedeğini alın.
2. `elak-okulum/database/migration_0_9_2.sql` dosyasını `elak_okulum` veritabanına **bir kez** uygulayın.
3. `elak-okulum/server/api/v1/index.php` ve `.htaccess` dosyasını web sunucusundaki `/api/v1/` klasörüne yükleyin.
4. API klasörünün üst uygulama dizinindeki `.env` dosyasının DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS bilgilerini okuyabildiğini doğrulayın. Sunucudaki dizin yapısı farklıysa `app_root()` yolu uyarlanmalıdır.
5. `GET /api/v1/health` veya `GET /api/v1/index.php?r=health` yanıtında `version: 0.9.2` görülmelidir.

## Merkezi kullanıcı modeli
- `users`: temel okul kullanıcısı ve okul kapsamı
- `user_roles`: bir kullanıcıya birden fazla rol
- `user_permissions`: kullanıcı bazlı ek izin/engeller
- `user_module_permissions`: modül görüntüleme/yönetme yetkisi
- `user_links`: veli–öğrenci, öğretmen–sınıf vb. bağlantılar
- `auth_sessions`: kısa ömürlü access token + uzun ömürlü refresh token

Roller: `school_admin`, `manager`, `teacher`, `security`, `dormitory`, `guardian`, `student`, `staff`.

Okul kapsamı: `middle`, `high`, `both`.

## Geçiş güvenliği
Merkezi API henüz kurulmamışsa Android 0.9.2 mevcut Akıllı Rehber ve İzin Takip hesaplarını doğrulamaya devam eder. Bu durumda Profil ekranında `Eski sistem uyum modu` görünür. Merkezi API başarılı olduğunda Profil ekranında `Merkezi Oturum: Aktif` görünür ve modül kartları sunucudan gelen yetkilere göre filtrelenir.

## Sürüm
Android: `0.9.2` / versionCode `35`.
