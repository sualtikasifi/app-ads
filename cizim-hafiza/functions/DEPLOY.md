# Cloud Functions Deploy Rehberi

Bu klasörde iki ayrı özelliğin Cloud Function'ları var:
1. **Arkadaş daveti push bildirimi** (`onInviteCreated`) — bir arkadaşın seni
   bir maça davet ettiğinde telefonuna push bildirimi göndermeyi sağlıyor.
2. **Bot rakip** (`onBotRoomWrite` + `maintainBotRoom`) — 130246 numaralı
   odadaki bot hesabının tüm hamlelerini (katılma sonrası maçı otomatik
   başlatma, "Bot Eğitim" ekranında kaydedilen çizimlerle sonuç gönderme,
   rövanş oyu + emoji tepkileri, oda temizliği/geri dönüşümü) simüle ediyor
   — bkz. `../src/index.ts`'teki yorum blokları.

Her ikisi de `RELEASE_SIGNING.md` ve `firestore.rules`'daki gibi **kendi
Firebase hesabından elle yapman gereken** bir adım — buradan (Claude Code'un
çalıştığı bulut ortamından) deploy edilemiyor, çünkü senin Firebase CLI
kimlik doğrulamana ihtiyaç var.

## 1. Ön koşul: Blaze plana geçiş

Cloud Functions, Firebase'in ücretsiz Spark planında **çalışmıyor** —
Blaze (kullandıkça öde) planına geçmen gerekiyor:

Firebase Console → proje seç → sol alttaki "Spark Plan" yazısına tıkla →
"Upgrade" → Blaze'i seç → bir ödeme yöntemi bağla.

Merak etme: Blaze'in kendi ücretsiz kotası var (ayda 2 milyon çağrıya
kadar) — bir kaç kişilik arkadaş grubu için bu fonksiyon büyük ihtimalle
**hiç ücret çıkarmaz**, sadece kredi kartı bağlamanı istiyor.

## 2. Firebase CLI kurulumu (bir kere)

```
npm install -g firebase-tools
firebase login
```

Tarayıcıda Firebase hesabınla giriş yap.

## 3. Bağımlılıkları kur

```
cd functions
npm install
```

## 4. Deploy et

Proje kökünden (`functions/` klasörünün bir üstünden):

```
firebase deploy --only functions
```

Bu aynı zamanda `firebase.json`'da tanımlı `firestore.rules`'u da
deploy etmek istersen (bu turda eklenen `botTrainedWords` kuralını içeriyor):

```
firebase deploy --only functions,firestore:rules
```

(Daha önce olduğu gibi Firebase Console → Firestore → Rules'a elle
yapıştırıp yayınlamak da aynı işi görür — hangisi sana kolay geliyorsa.)

`maintainBotRoom` bir **zamanlanmış (scheduled)** fonksiyon — ilk deploy'da
Firebase CLI, Cloud Scheduler API'yi senin için otomatik etkinleştirmeye
çalışır (Blaze planında ekstra bir adım/maliyet gerekmiyor, sadece ilk
deploy'da bir Google Cloud izin isteği/onayı çıkabilir, "Allow"/"İzin ver"
de yeterli).

## 5. Test et

**Arkadaş daveti push bildirimi:**
1. İki farklı hesapla (iki telefon ya da bir telefon + emulator) uygulamayı
   aç, birbirinizi arkadaş ekleyin.
2. Davet edilecek telefonda uygulamayı **tamamen kapat** (arka plandan da
   kaldır).
3. Diğer telefondan davet gönder.
4. Birkaç saniye içinde kapalı telefona bildirim gelmeli — dokununca
   uygulama açılır ve davet banner'ı görünür.

Bildirim gelmiyorsa:
- Firebase Console → Functions → `onInviteCreated`'ın loglarını kontrol et
  (hata mesajı orada görünür — ör. "recipient token yok" gibi).
- Bildirim izninin (POST_NOTIFICATIONS) telefonda verildiğinden emin ol.
- `firebase deploy` çıktısında hata olup olmadığını kontrol et.

**Bot rakip (oda 130246):**
1. Önce anasayfadaki **"Bot Eğitim"** ekranından en az 3 kelime çiz ve
   kaydet — bot, henüz eğitilmiş kelimesi yoksa maçı hiç başlatmaz (bkz.
   `src/index.ts`'teki `BOT_WORD_COUNT_MIN`).
2. `maintainBotRoom` her 2 dakikada bir çalıştığı için, deploy'dan sonra
   oda 130246 ilk kez otomatik oluşturulana kadar birkaç dakika beklemen
   gerekebilir (Firebase Console → Firestore → `rooms/130246` dokümanının
   belirip belirmediğini kontrol edebilirsin).
3. "Arkadaşınla Yarış" → "Koda Katıl" → **130246** yaz, katıl.
4. Birkaç saniye içinde ("host hazırlanıyor" gecikmesi) maç otomatik
   başlamalı — host'un "Başlat" düğmesine basmasını beklemene gerek yok.
5. Kendi turunu bitirdiğinde, bot da (kelime sayısına göre birkaç
   on saniye içinde) kendi sonucunu gönderir; Sonuç ekranında botun
   skorunu ve (Bot Eğitim'de kaydettiğin) çizimini görmelisin.
6. Rövanş oyu verirsen, bot da kısa bir gecikmeyle oy verip birkaç emoji
   tepkisi gönderir.

Bir şey çalışmıyorsa Firebase Console → Functions → `onBotRoomWrite` /
`maintainBotRoom` loglarına bak — her iki fonksiyon da neden bir şey
yapmadığını (ör. "only N trained words available") loglar.

## Yerel geliştirme (opsiyonel)

`npm run build` derler, hataları TypeScript derleme zamanında yakalar —
gerçek bir push göndermeden önce en azından bunu çalıştırmak iyi bir fikir:

```
cd functions
npm run build
```

## Bilgisayarsız / tarayıcıdan deploy (Firebase CLI'a hiç ihtiyaç yok)

Bilgisayara/terminale erişimin yoksa (ör. sadece telefondan yönetiyorsan),
tamamen **Google Cloud Console'un web arayüzünden** deploy edebilirsin —
`console-inline/` klasöründeki `index.js` + `package.json` tam bunun için
hazırlandı (aynı fonksiyonların düz JavaScript, derleme adımı gerektirmeyen
kopyası).

Bu yöntemde Cloud Console her seferinde **tek bir fonksiyon** deploy eder
(seçtiğin "Entry point"e karşılık gelen tek `exports.*`), kaynak dosyasında
başka fonksiyonlar olması sorun değil — o yüzden üç fonksiyonu (
`onInviteCreated`, `onBotRoomWrite`, `maintainBotRoom`) deploy etmek
istiyorsan aşağıdaki adımları **üç kez**, her seferinde farklı bir "Fonksiyon
adı" + "Giriş noktası" + tetikleyici ile tekrarlaman gerekiyor. Bot rakip
özelliğini istemiyorsan sadece `onInviteCreated` için bir kez yapman yeterli.

1. Telefon/bilgisayar tarayıcısında https://console.cloud.google.com adresine
   git, Firebase hesabınla giriş yap, üstteki proje seçiciden `karalak-b6e11`
   projesini seç (Blaze'e zaten geçtiysen bu adım gerekmiyor demektir).
2. Üstteki arama kutusuna **"Cloud Functions"** yaz, aç.
3. **"Fonksiyon Yaz" / "Write a function" / "Create Function"** düğmesine bas.
4. Ortam (Environment): **2nd gen**.
5. Fonksiyon adı: deploy ettiğin fonksiyonla aynı isim kullanmak en az
   kafa karıştırıcısı olur (`oninvitecreated`, `onbotroomwrite`,
   `maintainbotroom` gibi — büyük/küçük harf önemli değil).
6. Bölge (Region): Firestore veritabanının bulunduğu bölgeyle aynısını seç
   (Firestore Console'da görebilirsin) — emin değilsen `europe-west1` seçilebilir.
7. **Tetikleyici (Trigger)** bölümü fonksiyona göre değişir:
   - `onInviteCreated`: Event provider → **Cloud Firestore**, Event type →
     **"Document created"**, Database → `(default)`, Document path →
     `users/{uid}/invites/{inviteId}`.
   - `onBotRoomWrite`: Event provider → **Cloud Firestore**, Event type →
     **"Document written"**, Database → `(default)`, Document path →
     `rooms/130246` (dinamik `{roomCode}` DEĞİL — birebir bu sabit kod).
   - `maintainBotRoom`: Event provider → **Cloud Scheduler**, sıklık için
     "every 2 minutes" (ya da cron `*/2 * * * *`) — Console ilk seferde
     Cloud Scheduler API'yi etkinleştirmeni isteyebilir, onayla.
8. Çalışma zamanı (Runtime): **Node.js 20**.
9. Kaynak kodu (Source): **Inline editor** seçeneğini işaretle (ZIP yükleme
   ya da Cloud Source Repo değil).
10. Giriş noktası (Entry point): deploy ettiğin fonksiyonun adı — birebir
    `onInviteCreated`, `onBotRoomWrite` ya da `maintainBotRoom` (bunlar bu
    repodaki `exports.*` adlarıyla eşleşmeli, 5. adımdaki "Fonksiyon adı"yla
    karıştırma).
11. Açılan düzenleyicide `index.js` dosyasının içeriğini bu repodaki
    `functions/console-inline/index.js` ile, `package.json`'ı da
    `functions/console-inline/package.json` ile **birebir değiştir**
    (kopyala-yapıştır) — üç fonksiyon için de aynı iki dosya, sadece Entry
    point farklı.
12. **Deploy** düğmesine bas, birkaç dakika bekle.
13. Bot rakip için 7-12 arası adımları `onBotRoomWrite` ve `maintainBotRoom`
    için tekrarla.

Bittikten sonra "5. Test et" bölümündeki adımlarla dene.
