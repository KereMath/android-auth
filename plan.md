# Android OAuth ve TOTP Doğrulama Uygulaması - Uygulama Planı (Backend'li)

Bu belge, `keremrgur@gmail.com` adresinin sahipliğini doğrulayan, **Docker üzerinde çalışan bir Backend ve Veritabanı** kullanan Android uygulama planını içerir.

## 1. Mimari Yaklaşım: Sunucu Tabanlı (Server-Side)
Güvenlik ve verilerin kalıcılığı için Auth ve TOTP işlemleri sunucu tarafında yönetilecektir.

*   **Backend:** Python (FastAPI) - Hafif ve hızlı native async desteği.
*   **Veritabanı:** PostgreSQL - Kullanıcı kayıtları ve TOTP secret'ları için.
*   **Konteynerizasyon:** Docker Compose - Tek komutla ayağa kaldırmak için.

## 2. Port Yapılandırması
Kullanıcının isteği üzerine 11k bloğu kullanılacaktır:
*   **Backend API:** `11000` (Host) -> `8000` (Container)
*   **PostgreSQL:** `11001` (Host) -> `5432` (Container)

## 3. Veritabanı Şeması (Tablo: `users`)
*   `access_token` (String, PK veya Unique Key) - *Basitlik için veya email PK olabilir*
*   `email` (String) - Doğrulanmış e-posta adresi.
*   `totp_secret` (String) - Google Authenticator için üretilen base32 secret.
*   `is_verified` (Boolean) - TOTP kurulumu tamamlandı mı?

## 4. Uygulama Akışı (Flow)

### Adım 1: Kimlik Doğrulama (Google Sign-In)
1.  **Android:** "Google ile Giriş Yap" butonuna basılır.
2.  **Android:** Google'dan `id_token` alınır.
3.  **Android:** `id_token` Backend'e (`POST /auth/google`) gönderilir.
4.  **Backend:**
    *   Google sunucularından token'ı doğrular.
    *   E-posta `keremrgur@gmail.com` mu kontrol eder.
    *   DB'de kayıt yoksa oluşturur, varsa günceller.
    *   Android'e `success` ve kullanıcının durumunu döner.

### Adım 2: TOTP Kurulumu (Setup)
1.  **Android:** Eğer kullanıcı yeni ise "Kuruluma Başla" der.
2.  **Request:** Backend'e (`POST /totp/generate`) istek atar.
3.  **Backend:**
    *   Rastgele bir `Secret` üretir (pyotp kütüphanesi ile).
    *   DB'ye bu e-posta için `totp_secret` olarak kaydeder.
    *   Android'e `secret` ve `otpauth_url` döner.
4.  **Android:** Kullanıcıya "Google Authenticator'a Ekle" butonu gösterir. Linke basınca Authenticator açılır.

### Adım 3: TOTP Doğrulama (Verify)
1.  **Android:** Kullanıcı Authenticator'daki 6 haneli kodu girer.
2.  **Request:** Backend'e (`POST /totp/verify`) `code` gönderilir.
3.  **Backend:**
    *   DB'den kullanıcının `secret`'ını çeker.
    *   Sunucu saati ile kodu doğrular (`pyotp.verify(code)`).
    *   Doğruysa `is_verified = True` yapar ve `200 OK` döner.
4.  **Android:** "Başarıyla Doğrulandınız!" ekranı gösterilir.

## 5. Teknik Gereksinimler & Komutlar

### Gerekli Dosyalar
*   `docker-compose.yml`: Postgres ve API servisi tanımı.
*   `backend/main.py`: FastAPI uygulama kodu.
*   `backend/requirements.txt`: `fastapi`, `uvicorn`, `psycopg2`, `pyotp`, `google-auth`.

### Docker Komutu
```bash
cd backend_setup
docker-compose up -d --build
```

### Android Build Komutu
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd /c/Users/user/Desktop/android auth
./gradlew.bat assembleDebug --no-daemon
```
