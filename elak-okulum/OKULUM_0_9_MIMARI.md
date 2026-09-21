# ELAK Okulum 0.9 — Birleşik Okul Uygulaması Mimarisi

## Amaç

ELAK Okulum'u tek bir WebView portalı olmaktan çıkarıp veli, öğretmen, idareci ve güvenlik rollerine göre değişen gerçek bir mobil okul uygulamasına dönüştürmek.

Temel ilke: Kullanıcı bir kez giriş yapar; rolü, ilişkili öğrencileri, sınıfları ve yetkileri sunucudan gelir. Ana ekran, modüller ve bildirimler bu bağlama göre otomatik şekillenir.

## Ana navigasyon

Alt menü: Ana Sayfa · Bildirimler · Mesajlar · Takvim · Profil

Ana sayfa üst bölümü:
- Okul logosu ve okul adı
- Kullanıcı adı + rol
- Veli için aktif öğrenci seçici
- Okunmamış bildirim sayacı
- Günün hızlı özeti

Ana sayfa kartları rol bazlı ve sunucu kontrollü olacaktır.

### Veli
- Bildirimler / devamsızlık
- Ders programı
- Sınav takvimi ve sonuçları
- Öğretmen randevusu
- Duyuru / haber
- Dokümanlar
- Anketler
- Gezi / etkinlik onayı
- Öğretmen yorumları
- Mesajlar
- Öğrenci izin durumu

### Öğretmen
- Bugünkü derslerim
- e-Yoklama
- Ders programım
- Nöbet programım
- Akıllı tahta QR
- Öğrenci rehberi
- Veli randevularım
- Öğretmen yorumu
- Duyuru / doküman
- Anket / gezi
- Mesajlar
- DYK / sosyal etkinlik yoklama

### İdareci
- Günlük okul özeti
- Devamsızlık merkezi
- Duyuru / push bildirimi
- Öğrenci izin takip
- Akıllı rehber
- Akıllı tahta yönetimi
- Randevu yönetimi
- Sınav / kelebek
- Deneme sonuçları
- DYK / sosyal etkinlik
- Anket / gezi
- Dokümanlar
- Bildirim raporları
- Kullanıcı / rol / yetki yönetimi

### Güvenlik
- Çıkış bekleyen öğrenciler
- Dışarıdaki öğrenciler
- Dönüş bekleyenler
- Randevulu ziyaretçiler
- Anlık yenileme / push

## Ortak veri modeli

### users
id, phone, username, password_hash, full_name, status, last_login_at

### user_roles
user_id, role (admin|teacher|guardian|security|staff)

### students
id, school_no, full_name, class_id, school_level, status, photo_url

### guardians
id, user_id, full_name, phone

### student_guardians
student_id, guardian_id, relation_type, is_primary, notification_enabled

### teachers
id, user_id, branch, staff_code

### teacher_classes
teacher_id, class_id, course_id, academic_year

### devices
id, user_id, platform, push_token, app_version, last_seen_at

### notifications
id, type, title, body, source_module, source_id, created_by, created_at, priority

### notification_targets
notification_id, user_id, delivered_at, read_at

## API yaklaşımı

Yeni mobil omurga PHP 8.3 üzerinde `/api/v1/` altında çalışmalıdır. Mevcut PHP 5.6 modülleri korunur ve adapter endpoint'lerle yeni API'ye bağlanır.

Önerilen uçlar:
- POST /api/v1/auth/login
- POST /api/v1/auth/refresh
- POST /api/v1/auth/logout
- GET /api/v1/me
- GET /api/v1/home
- GET /api/v1/modules
- GET /api/v1/notifications
- POST /api/v1/notifications/{id}/read
- GET /api/v1/students/{id}/attendance
- GET /api/v1/students/{id}/schedule
- GET /api/v1/students/{id}/exams
- GET /api/v1/teacher/today
- GET /api/v1/teacher/schedule
- POST /api/v1/attendance
- GET /api/v1/appointments
- GET/POST /api/v1/messages
- GET /api/v1/documents
- GET/POST /api/v1/forms
- GET /api/v1/app/version

## Kimlik doğrulama

Mobil uygulama PHP session cookie yerine kısa ömürlü access token + refresh token kullanmalıdır. Refresh token cihazda Android Keystore ile şifrelenmiş tutulur. Kullanıcının rol ve yetkileri `/me` yanıtından gelir.

İlk aşamada mevcut kullanıcı adı/şifre yapısı korunabilir. İkinci aşamada veli girişinde telefon numarası + OTP/parola eklenir.

## Bildirim mimarisi

Firebase Cloud Messaging (FCM) kullanılacaktır.

Akış:
1. Uygulama cihaz tokenını sunucuya kaydeder.
2. Modül bir olay üretir: devamsızlık, izin, randevu, duyuru, sınav vb.
3. Notification service hedef kullanıcıları belirler.
4. Push gönderilir ve notification_targets kaydı açılır.
5. Uygulama bildirimi açınca read_at yazılır.
6. İdareci paneli teslim/okunma oranını görür.

SMS/WhatsApp yalnız push alamayan veya özel olarak seçilen kullanıcılar için yedek kanal olur.

## Veli çoklu öğrenci desteği

Bir veli aynı hesapta birden fazla öğrenciye bağlı olabilir. Ana ekranda öğrenci seçici bulunur. Bildirimlerde öğrenci adı/şubesi açıkça görünür. Okul geneli duyurular tekrar edilmez; öğrenciye özel kayıtlar ilgili öğrenci altında gösterilir.

## Kontrollü mesajlaşma

- Veli ↔ öğretmen birebir konuşma
- Öğretmen telefon numarası görünmez
- Okul sohbetin hangi saatlerde başlatılabileceğini belirler
- Dosya/görsel paylaşımı yetkiyle açılır
- Okundu bilgisi tutulur
- Gerekirse idare tarafından konuşma başlatma kapatılabilir

## e-Yoklama

Durumlar: Var · Yok · Geç · İzinli · Nöbetçi

- Öğretmene sadece o saat ilişkili sınıf/şube gösterilir
- İnternet yoksa cihazda taslak tutulur ve bağlantı gelince senkronize edilir
- İdare panelinde günün tüm yoklamaları tek listede görünür
- Yanlış kayıt resmi aktarım öncesi düzeltilebilir
- Veli bildirimi doğrudan veya idare onayı sonrası gönderilebilir

## Randevu

- Veliye yalnız öğrencinin dersine giren öğretmenler gösterilir
- Öğretmen müsaitlik slotlarını yönetir veya idare tanımlar
- Dolu slot tekrar seçilemez
- Ders/nöbet saatleri otomatik kapatılır
- Onay, iptal ve hatırlatma push ile gider
- Güvenlik ekranında o gün gelecek veliler ayrıca görünür

## Akıllı tahta

Mevcut ELAK Akıllı Tahta sistemi yeni omurgaya bağlanır.

- Dinamik / tek kullanımlık QR
- Öğretmen yetkisi ve ders saati kontrolü
- Cihaz çevrimiçi/çevrimdışı durumu
- Uzaktan kilitle/aç/kapat
- Akıllı tahtadan e-Yoklama
- USB kurtarma anahtarı

## Sınav / kelebek

Mevcut sınav planlayıcı mobilde yönetim modülü olarak görünür. Ağır dağıtım işlemi web panelinde kalabilir; mobilde sınav oturumları, salonlar, gözetmenler, öğrenci salon/sıra bilgisi ve sınav yoklaması gösterilir.

## Mobil arayüz standardı

- Native Android ana kabuk
- Büyük okul başlığı yerine kompakt marka alanı
- 2 sütunlu renkli modül kartları
- Kartlarda Material ikon + kısa isim + gerekirse sayaç rozeti
- "Bugün" alanında ders, nöbet, randevu ve kritik bildirimler
- Alt menü sabit ve sistem navigation bar üstünde
- Android edge-to-edge insets zorunlu
- Dark mode daha sonraki aşamada
- Minimum dokunma alanı 48dp
- Uzun listelerde RecyclerView / pagination
- Yükleme, boş durum ve hata ekranları her modülde standart

## Geçiş planı

### 0.9.0 — Native ana kabuk
Native ana sayfa, alt menü, okul kimliği, kullanıcı kartı, mevcut Akıllı Rehber ve İzin Takip native yönlendirmeleri, diğer mevcut modüllere kontrollü web geçişi.

### 0.9.1 — Tek kimlik ve rol
/api/v1 auth, /me, rol bazlı menü, öğretmen/idareci ayrımı.

### 0.9.2 — Bildirim merkezi
FCM, duyurular, okundu/teslim raporu, uygulama içi gelen kutusu.

### 0.9.3 — Veli hesabı
Telefon/OTP, öğrenci-veli ilişkisi, çoklu çocuk, devamsızlık/sınav/program.

### 0.9.4 — Öğretmen günlük ekranı
Bugünkü dersler, program, nöbet, hızlı e-Yoklama, QR tahta.

### 0.9.5 — Randevu + mesajlaşma
Veli randevusu, öğretmen takvimi, kontrollü sohbet.

### 0.9.6 — Anket/gezi/doküman
Form motoru, onay süreçleri, dosya merkezi.

### 1.0.0 — Birleşik ELAK Okulum
Veli + öğretmen + idare + güvenlik rollerinin tek uygulamada kararlı sürümü.

## Mevcut Android durumundan geçiş

Şu an MainActivity ağırlıklı olarak WebView kabuğudur. `/rehber` ve `/izin` rotaları native activity'lere yönlendirilmektedir. 0.9.0'da bu model tersine çevrilecek: ana kabuk native olacak, yalnız henüz native'e taşınmamış modüller geçici olarak WebView'da açılacaktır.

Bu yaklaşım mevcut çalışan modülleri bozmadan kademeli ve test edilebilir dönüşüm sağlar.
