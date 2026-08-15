# Çizim & Hafıza

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

`app/src/main/assets/words.json` — ilk açılışta Room'a otomatik seed edilir
(`WordSeeder` + `DatabaseModule`'daki `RoomDatabase.Callback`). Şu an 50
örnek kelime var (8 kategori × ~6). 1000 kelimeye çıkarmak için:

1. `WORDS_SCHEMA.md` dosyasındaki şemayı ve id/kategori kurallarını oku.
2. Aynı formatta yeni kayıtları `words.json`'a ekle (id'ler unique olmalı).
3. Uygulamayı temiz kurulumla (verileri temizleyip) çalıştır ki yeni seed
   uygulansın — Room `onCreate` callback'i sadece veritabanı ilk oluşurken
   çalışır.

## AdMob

`GameConstants.ADMOB_ENABLED = false` — SDK bağımlılığı, `BuildConfig` alanları
ve `AdManager` sınıfı hazır ama gerçek `MobileAds.initialize()` / reklam
gösterim çağrıları henüz eklenmedi (bkz. `ads/AdManager.kt` içindeki TODO'lar).
Hazır olduğunda: `local.properties`'e gerçek ID'leri koy, flag'i `true` yap,
TODO'ları doldur.

## Hız bonusu

`GameConstants.SPEED_BONUS_ENABLED = true` — 3 saniye altı doğru cevaba +2
puan. Tek satırdan kapatılabilir.

## Play Store

`PLAY_STORE.md` dosyasında Türkçe mağaza metni taslağı, `privacy-policy.md`
dosyasında gizlilik politikası taslağı var.
