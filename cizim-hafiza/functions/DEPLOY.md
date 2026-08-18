# Arkadaş Daveti Push Bildirimi — Cloud Function Deploy Rehberi

Bu klasördeki Cloud Function, bir arkadaşın seni bir maça davet ettiğinde
telefonuna push bildirimi göndermeyi sağlıyor. Bu, `RELEASE_SIGNING.md` ve
`firestore.rules`'daki gibi **kendi Firebase hesabından elle yapman gereken**
bir adım — buradan (Claude Code'un çalıştığı bulut ortamından) deploy
edilemiyor, çünkü senin Firebase CLI kimlik doğrulamana ihtiyaç var.

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
deploy etmek istersen (bu turda eklenen `blockedUsers`/`inviteCooldowns`
kurallarını içeriyor):

```
firebase deploy --only functions,firestore:rules
```

(Daha önce olduğu gibi Firebase Console → Firestore → Rules'a elle
yapıştırıp yayınlamak da aynı işi görür — hangisi sana kolay geliyorsa.)

## 5. Test et

Deploy bittikten sonra:
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
hazırlandı (aynı fonksiyonun düz JavaScript, derleme adımı gerektirmeyen
kopyası).

1. Telefon/bilgisayar tarayıcısında https://console.cloud.google.com adresine
   git, Firebase hesabınla giriş yap, üstteki proje seçiciden `karalak-b6e11`
   projesini seç (Blaze'e zaten geçtiysen bu adım gerekmiyor demektir).
2. Üstteki arama kutusuna **"Cloud Functions"** yaz, aç.
3. **"Fonksiyon Yaz" / "Write a function" / "Create Function"** düğmesine bas.
4. Ortam (Environment): **2nd gen**.
5. Fonksiyon adı: `onInviteCreated` (istediğin bir isim de olur, önemli değil).
6. Bölge (Region): Firestore veritabanının bulunduğu bölgeyle aynısını seç
   (Firestore Console'da görebilirsin) — emin değilsen `europe-west1` seçilebilir.
7. **Tetikleyici (Trigger)** bölümünde: Event provider → **Cloud Firestore**,
   Event type → **"Document created"**, Database → `(default)`,
   Document path → `users/{uid}/invites/{inviteId}` (bu alanı birebir böyle yaz).
8. Çalışma zamanı (Runtime): **Node.js 20**.
9. Kaynak kodu (Source): **Inline editor** seçeneğini işaretle (ZIP yükleme
   ya da Cloud Source Repo değil).
10. Giriş noktası (Entry point): `onInviteCreated` (5. adımdaki fonksiyon
    adıyla karışmasın — bu, kodun içindeki `exports.onInviteCreated`'a karşılık geliyor).
11. Açılan düzenleyicide `index.js` dosyasının içeriğini bu repodaki
    `functions/console-inline/index.js` ile, `package.json`'ı da
    `functions/console-inline/package.json` ile **birebir değiştir**
    (kopyala-yapıştır).
12. **Deploy** düğmesine bas, birkaç dakika bekle.

Bittikten sonra "5. Test et" bölümündeki adımlarla dene.
