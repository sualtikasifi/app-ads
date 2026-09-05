# Hesap Bağlama / Bulut Yedekleme — Kurulum Rehberi

Uygulama artık anonim Firebase oturumunu kalıcı bir Google hesabına
bağlayıp (Ayarlar → Hesap) ilerlemeyi (seviye, başarımlar, seriler,
kozmetikler) buluta yedekleyip geri yükleyebiliyor — kod tarafı tamamen
hazır. **Eksik olan tek şey, Firebase projesinde Google ile Giriş'i
etkinleştirmek** — bu, Firebase Console'dan yapılması gereken, kod
değişikliği gerektirmeyen tek adım.

## Neden şu an çalışmıyor

`google-services.json` içindeki `oauth_client` listesi şu an boş —
yani bu Firebase projesi için hiç OAuth istemci kimliği üretilmemiş.
Uygulama bunu tespit edip (`AuthRepositoryImpl.isGoogleSignInConfigured`)
"Hesap" ekranında düğmeyi göstermek yerine "şu an ayarlanmadı" mesajı
gösteriyor — kırık bir düğme yerine dürüst bir durum.

## Etkinleştirme adımları

1. [Firebase Console](https://console.firebase.google.com) → bu proje →
   **Authentication** → **Sign-in method** sekmesi.
2. **Google**'ı bul, **Etkinleştir**'e tıkla, açılan formda bir destek
   e-postası seç, **Kaydet**'e bas.
3. Bu işlem Firebase'in arka planda bir OAuth 2.0 Web İstemci Kimliği
   oluşturmasını tetikler.
4. **Project settings** (dişli ikonu) → **General** sekmesi → en alttaki
   **Your apps** → Android uygulaması → **google-services.json**'ı
   yeniden indir.
5. İndirdiğin dosyayı `cizim-hafiza/app/google-services.json` ile
   değiştir (aynı isim, üzerine yaz) ve commit'le.
6. `./gradlew :app:assembleDebug` çalıştır — artık `default_web_client_id`
   kaynağı üretilecek ve "Hesap" ekranındaki "Google ile bağla" düğmesi
   gerçek bir hesap seçici açacak.

Başka hiçbir kod değişikliği gerekmiyor — `AuthRepositoryImpl` bu
kaynağı derleme zamanında değil çalışma zamanında (isme göre) okuyor,
tam olarak bu yüzden.

## Doğrulama

Adımları tamamladıktan sonra cihazda/emülatörde:

1. Ayarlar → Hesap → "Google ile bağla" → hesap seçici açılmalı.
2. Bir hesap seçtikten sonra ekran "Hesap bağlı" durumuna geçmeli, e-posta
   adresini göstermeli.
3. "Şimdi Yedekle" → Firestore konsolunda `users/{uid}/backup/state`
   dokümanının gerçekten oluştuğunu doğrula.
4. Uygulamayı silip yeniden kur, aynı Google hesabıyla tekrar bağla,
   "Yedeği Geri Yükle" → seviye/başarım/seri verilerinin geri geldiğini
   doğrula.

## Not: hangi Google Sign-In API'si kullanılıyor

`AuthRepositoryImpl` klasik `GoogleSignInClient` API'sini kullanıyor, daha
yeni "Credential Manager" API'sini değil. İlk sürüm Credential Manager ile
yazılmıştı ama MIUI/HyperOS (Xiaomi/Redmi/POCO) cihazlarda hesap seçildikten
hemen sonra hiçbir hata göstermeden sessizce iptal oluyordu — cihaz
loglarında doğrulanan sebep `GetCredentialCancellationException: [16]
Account reauth failed`, Play Hizmetleri'nin arka plandaki hesap yenileme
adımının MIUI'de başarısız olması. Klasik API'de bu adım yok, bu yüzden
etkilenmiyor. Yukarıdaki kurulum adımları (Google sağlayıcısını etkinleştir,
SHA-1 ekle, google-services.json'ı indir) değişmedi — ikisi de aynı
`default_web_client_id` kaynağını ve aynı Firebase projesi ayarlarını
kullanıyor.
