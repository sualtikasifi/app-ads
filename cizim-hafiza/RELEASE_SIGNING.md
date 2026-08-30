# Yayın İmzalama (Release Signing) — Kurulum Rehberi

Play Store'a yüklenecek her Android uygulamasının bir "imza anahtarı"
(keystore) ile imzalanması gerekir. Bu anahtar, uygulamanın gerçekten senin
tarafından yayınlandığını kanıtlar — **kaybedersen o uygulamayı bir daha asla
güncelleyemezsin**, bu yüzden bu adımları dikkatle takip et ve dosyayı/şifreleri
güvenli bir yerde (parola yöneticisi, bulut yedek) sakla.

## 1. Keystore oluştur

Bilgisayarında (Android Studio kurulu değilse JDK kurulu olması yeterli)
bir terminal aç ve şunu çalıştır:

```
keytool -genkeypair -v -keystore karalak-release.jks -alias karalak -keyalg RSA -keysize 2048 -validity 10000
```

Sırayla soracağı bilgiler:
- **Keystore şifresi** — en az 6 karakter, unutma/bir yere kaydet.
- **Ad, kurum, şehir, ülke kodu (TR) vb.** — bunlar kullanıcıya hiç
  gösterilmez, doğru/gerçekçi olmak zorunda değil.
- **Anahtar şifresi** — Enter'a basarsan keystore şifresiyle aynı olur
  (önerilir, tek şifre yönetmek daha kolay).

Bu komut, çalıştırdığın klasörde `karalak-release.jks` adında bir dosya
oluşturur. **Bu dosyayı proje klasörünün (git deposunun) dışında bir yerde
tut** — `.gitignore` zaten `*.jks` dosyalarını hariç tutuyor ama en güvenlisi
hiç repo klasörüne koymamak.

Android Studio kullanıyorsan, terminal yerine **Build → Generate Signed App
Bundle / APK → Create new...** menüsünden de aynı işlemi grafik arayüzle
yapabilirsin — sonuç aynıdır.

## 2. Bilgileri projeye bildir

Proje kökündeki `local.properties` dosyasını aç (yoksa oluştur — bu dosya
zaten `.gitignore`'da, asla GitHub'a gitmez) ve şu satırları ekle:

```
RELEASE_KEYSTORE_PATH=/tam/yol/karalak-release.jks
RELEASE_KEYSTORE_PASSWORD=<keystore şifren>
RELEASE_KEY_ALIAS=karalak
RELEASE_KEY_PASSWORD=<anahtar şifren>
```

`RELEASE_KEYSTORE_PATH` için dosyanın **tam (absolute) yolunu** kullan.
Bu dört satır dolduğu an, `./gradlew :app:bundleRelease` (Play Store'a
yüklenecek `.aab` dosyasını üretir) otomatik olarak bu anahtarla imzalı bir
çıktı üretir — build dosyalarında başka bir şey değiştirmene gerek yok.

## 3. Yedekle

- Keystore dosyasını (`karalak-release.jks`) **ve** yukarıdaki 4 satırı
  (özellikle şifreleri) en az bir yere daha yedekle (Google Drive, parola
  yöneticisi vb.). Bu bilgisayar bozulursa ve elinde yedek yoksa, uygulamayı
  Play Store'da bir daha güncelleyemezsin — yeni bir uygulama olarak baştan
  yayınlamak zorunda kalırsın.

## 4. Play Store'a yükleme paketini üret

```
./gradlew :app:bundleRelease
```

Çıktı: `app/build/outputs/bundle/release/app-release.aab` — Play Console'a
yüklenecek dosya budur (APK değil, `.aab`).
