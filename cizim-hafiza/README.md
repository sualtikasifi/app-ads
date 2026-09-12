# Karalak

Kotlin + Jetpack Compose ile geliştirilmiş çizim/hafıza oyunu. MVVM + Clean
Architecture (`data` / `domain` / `presentation`), Room, Hilt, Navigation-Compose.

## Kurulum

1. Android Studio ile `cizim-hafiza/` klasörünü aç.
2. `local.properties.example` dosyasını `local.properties` olarak kopyala,
   `sdk.dir` değerini kendi Android SDK yoluna göre düzenle.
3. Gerçek AdMob ID'lerin varsa aynı dosyaya `ADMOB_*` anahtarlarını ekle
   (boş bırakırsan Google'ın public test ID'leri kullanılır — bkz. altta).

## Mimari

```
data/          Room entity/DAO/Database, repository implementasyonu
domain/        Modeller, repository arayüzü, use case'ler
presentation/  Compose ekranları, ViewModel'ler, navigation
di/            Hilt modülleri
ads/           AdManager (altyapı hazır, canlı reklam çağrısı yok)
util/          Constants, AnswerMatcher (Levenshtein), VibratorHelper, SettingsRepository
```

## Kelime havuzu

`app/src/main/assets/words.json` — her uygulama açılışında Room ile
senkronize edilir (`WordSeeder` + `CizimHafizaApp.onCreate`): veritabanındaki
kelime sayısı dosyadakiyle eşleşmiyorsa eksikler otomatik eklenir. Şu an 1169
kelime var (8 kategori × 83–165). Daha da eklemek için:

1. `WORDS_SCHEMA.md` dosyasındaki şemayı ve id/kategori kurallarını oku.
2. Aynı formatta yeni kayıtları `words.json`'a ekle (id'ler unique olmalı,
   en yüksek id'den devam et — mevcut kayıtları değiştirme).
3. Uygulamayı yeniden derleyip kur; ilk açılışta yeni kelimeler otomatik
   eklenir, mevcut oyun geçmişi/istatistikler silinmez.

## AdMob

`GameConstants.ADMOB_ENABLED = BuildConfig.DEBUG` — reklam akışı **debug
build'lerde açık, yayın build'lerinde kapalı**. Akışın tamamı (UMP onayı,
geçiş reklamı, dört ödüllü reklam girişi) yazılmış ve debug APK'da baştan
sona denenebilir; birim kimlikleri Google'ın herkese açık TEST kimliklerine
düşer, dolayısıyla geliştirme sırasında reklamlara serbestçe tıklanabilir —
kendi *gerçek* biriminize tıklamak AdMob hesabını askıya aldıran şeydir.

Yayına açmak tek bir değişiklik: bu satır `true` olur, manifest'teki dört
`tools:node="remove"` satırı çıkar, `local.properties`'e gerçek ID'ler
girilir ve Play Console Veri Güvenliği formu + gizlilik politikası reklam
kimliğini beyan edecek şekilde güncellenir. Dördü aynı sürümde olmalı
(bkz. PLAY_STORE.md).

## Hız bonusu

`GameConstants.SPEED_BONUS_ENABLED = true` — 3 saniye altı doğru cevaba +2
puan. Tek satırdan kapatılabilir.

## Play Store

`PLAY_STORE.md` dosyasında Türkçe mağaza metni taslağı, `privacy-policy.md`
dosyasında gizlilik politikası taslağı var.
