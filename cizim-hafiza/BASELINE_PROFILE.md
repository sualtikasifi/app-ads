# Baseline Profile — Kurulum ve Üretim Rehberi

Bir "baseline profile", uygulamanın açılışta hangi sınıf/metotları
kullandığını önceden kaydedip APK'ya gömen bir dosyadır (`baseline-prof.txt`).
Bu sayede Android, o kodu her kullanıcının cihazında ilk açılışta yorumlamak/
JIT ile ısıtmak yerine, kurulum sırasında derler — Compose-ağırlıklı bu
uygulamada ilk açılış süresini gözle görülür şekilde kısaltır.

Altyapı bu projede zaten kurulu (`:baselineprofile` modülü +
`androidx.baselineprofile` Gradle eklentisi + `:app`'teki
`androidx-profileinstaller` bağımlılığı). **Eksik olan tek şey, gerçek
profili bir cihaz/emülatörde üretip commit'lemek** — bu adım bir Android SDK
emülatör/cihazı gerektirdiği için bu depoyu üzerinde geliştirdiğimiz
sanal ortamda çalıştırılamadı (adb/emulator hiç kurulu değil).

## Neden hazır bir `baseline-prof.txt` commit'lenmedi

Baseline profile, uygulamayı **gerçekten çalıştırıp** hangi kodun
kullanıldığını ölçerek üretilir — elle yazılmış ya da tahmin edilmiş bir
`baseline-prof.txt`, format olarak geçse bile yanlış (veya boş) bir profil
olur ve hiçbir hız kazancı sağlamaz, üstelik "ölçüldü" yanılgısı yaratır.
Bu yüzden burada gerçek bir profil uydurmak yerine, sen (ya da CI) bir kere
çalıştırdığında doğru sonucu üretecek altyapıyı bırakıyoruz.

## Bir kere, kendi bilgisayarında üretmek için

Android Studio kurulu, gerçek bir cihaz veya çalışan bir emülatörle:

```
./gradlew :app:generateBaselineProfile
```

Bu komut:
1. `:baselineprofile` modülündeki `BaselineProfileGenerator.kt`'yi
   (`app`'in "nonMinifiedRelease" varyantına karşı) cihazda/emülatörde
   çalıştırır — uygulamayı soğuk açar, ana ekran çizilene kadar bekler.
2. Elde ettiği profili otomatik olarak
   `app/src/main/baselineProfiles/baseline-prof.txt` dosyasına yazar.

O dosyayı normal bir kod değişikliği gibi commit'le — `libs.androidx.profileinstaller`
zaten `:app`'e ekli olduğu için, bir sonraki `bundleRelease`/`assembleRelease`
bu profili otomatik APK/AAB'ye gömer, başka hiçbir şey yapman gerekmez.

## Cihaza/emülatöre dokunmadan (Gradle Managed Device) üretmek için

`:baselineprofile/build.gradle.kts` içinde tanımlı `pixel6Api34` adlı bir
[Gradle Managed Device](https://developer.android.com/studio/test/gradle-managed-devices)
var — donanım hızlandırmalı (KVM) bir makinede aşağıdaki komut, Android
Studio ya da elle bağlanmış bir cihaz olmadan otomatik bir emülatör indirip
profili üretir:

```
./gradlew :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

CI'da otomatikleştirmek istersen, bu komutu donanım hızlandırmalı (KVM
etkin) bir runner'da bir GitHub Actions adımı olarak çalıştırıp çıkan
`app/src/main/baselineProfiles/baseline-prof.txt` dosyasını bir pull
request olarak açman yeterli — profil, uygulamanın kritik akışları
değiştikçe (yeni ekranlar, büyük refactor'lar) periyodik olarak yeniden
üretilmesi gereken bir dosyadır, bir kere yazılıp unutulan bir dosya değil.

## Doğrulama

Profil üretildikten sonra, gerçekten paketlendiğini görmek için:

```
./gradlew :app:bundleRelease
unzip -p app/build/outputs/bundle/release/app-release.aab base/assets/dexopt/baseline.prof | wc -c
```

Çıktı 0'dan büyükse profil AAB'ye gömülmüş demektir.
