# Play Store Mağaza Metni ve Yayın Notları

## Uygulama başlığı
Karalak — Çiz, Tahmin Et!

## Kısa açıklama (80 karakter)
Kelimeyi çiz, hafızanı test et, arkadaşınla online yarış!

## Uzun açıklama

Karalak, klasik "çiz ve tahmin et" oyununu hafıza dokunuşuyla
birleştiren eğlenceli bir kelime oyunudur!

**Nasıl oynanır?**
1. Kaç kelimeyle oynamak istediğini seç (10, 20, 30, 40 veya 50).
2. Ekrana gelen her kelimeyi süresi dolmadan çiz — kolay kelimelerde 5,
   orta zorlukta 7, zor kelimelerde 10 saniyen var.
3. Tüm kelimeleri çizdikten sonra kısa bir mola ver.
4. Sırada hafızan var: az önce çizdiğin resimleri hatırlayıp ne
   olduklarını yazarak tahmin et!
5. Puanını topla, en hızlı doğru cevabını gör, çizimlerinin galerisini incele.

**Özellikler**
- 8 farklı kategoride yüzlerce kelime: hayvanlar, eşyalar, meslekler, spor,
  doğa, yiyecekler, taşıtlar, duygular
- Zorluk seviyesine göre değişen süre ve görsel/titreşimli uyarılar
- Hızlı doğru cevaplara bonus puan
- Türkçe karakter ve yazım toleranslı tahmin kontrolü
- **Arkadaşınla online yarış**: aynı anda başlayın, aynı kelimeleri çizin,
  skorlarınızı karşılaştırın, emojilerle tepki verin, tekrar oynayın
- **Kıdem sistemi**: topladığın puan arttıkça Karalamacı'dan Büyük Usta'ya
  kadar yükselen sanatçı kıdemleri kazan
- Detaylı istatistikler: geçmiş oyunların, en yüksek skorun, toplam
  oynadığın kelime sayısı

Hem tek başına pratik yapmak hem de arkadaşınla online yarışmak için ideal!

## Kategori
Oyun / Kelime Oyunu

## Grafik varlıkları
- Uygulama simgesi: hazır (`app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`
  + `launcher_background` rengi) — Play Console için 512×512 PNG'ye dışa aktarılmalı.
- Öne çıkan grafik (1024×500) — ayrıca hazırlanmalı.
- En az 2 telefon ekran görüntüsü — ayrıca hazırlanmalı.

## Gizlilik politikası
Bkz. `privacy-policy.md` — Play Console'da "App content" bölümüne bu
dosyanın barındırıldığı bir URL girilmelidir (ör. bu repo üzerinden
GitHub Pages ile yayınlanabilir).

## "Veri Güvenliği" (Data Safety) formu — Play Console'da doldurulacak

Play Console → App content → Data safety bölümünde sorulan sorulara
karşılık gelen cevaplar:

| Veri türü | Toplanıyor mu? | Nasıl kullanılıyor | Paylaşılıyor mu? |
|---|---|---|---|
| Kullanıcı kimliği (anonim cihaz ID — Firebase Anonymous Auth) | Evet | Uygulama işlevselliği (çevrimiçi oda eşleştirme) | Hayır |
| Kullanıcı tarafından girilen takma ad | Evet | Uygulama işlevselliği (rakibe gösterim) | Hayır |
| Uygulama içi etkinlik (oyun skorları, çizimler) | Evet (yalnızca çevrimiçi modda) | Uygulama işlevselliği | Hayır |

Ek notlar:
- Veriler şifreli olarak iletilir (Firestore, HTTPS/TLS).
- Kullanıcı, verisinin silinmesini talep edebilir (gizlilik politikasındaki
  iletişim adresi üzerinden).
- Reklam/analitik SDK'sı şu an **aktif değil** (AdMob altyapısı hazır ama
  `GameConstants.ADMOB_ENABLED = false`), bu yüzden "reklam amaçlı veri
  toplama" sorularına "Hayır" cevabı verilmeli — AdMob etkinleştirildiğinde
  bu form güncellenmelidir.

## Yayın öncesi teknik kontrol listesi

- [x] `compileSdk`/`targetSdk` = 36 (31 Ağustos 2026'dan itibaren Play
  Store'un zorunlu kıldığı minimum). Bunun için AGP 8.5.2 → 9.0.1,
  Gradle 8.9 → 9.1.0, Kotlin 2.0.21 → 2.2.10, KSP, Room 2.6.1 → 2.8.4,
  Hilt 2.51.1 → 2.60.1 ve Compose BOM güncellendi; AGP 9'un zorunlu kıldığı
  "yerleşik Kotlin" mimari geçişi `gradle.properties`'teki
  `android.builtInKotlin=false` / `android.newDsl=false` bayraklarıyla
  şimdilik erteledi (AGP 10.0'a kadar geçerli, o zaman tekrar ele alınmalı).
- [ ] Yayın imzalama anahtarı (keystore) oluşturuldu ve `local.properties`'e
  eklendi — bkz. `RELEASE_SIGNING.md`.
- [ ] `./gradlew :app:bundleRelease` ile imzalı `.aab` üretildi ve gerçek
  cihazda test edildi.
- [ ] Gizlilik politikası bir web adresinde yayınlandı (GitHub Pages vb.)
  ve URL Play Console'a girildi.
- [ ] İçerik derecelendirme anketi dolduruldu.
- [ ] En az 12 test kullanıcısıyla 14 gün kesintisiz kapalı test tamamlandı
  (Kasım 2023 sonrası açılan yeni Play Console hesapları için zorunlu).
- [ ] Ekran görüntüleri (en az 2) ve öne çıkan grafik (1024×500) hazırlandı.
