# Cloud Functions Deploy Rehberi

Bu proje **Blaze (kullandıkça öde) planına geçmiyor** — bu kararlı bir
tercih. Cloud Functions'ın kendisi (Google'ın kuralı, deploy yönteminden
bağımsız) Spark planında çalışmıyor, bu yüzden bu klasördeki fonksiyonlar
ikiye ayrılıyor:

| Fonksiyon | Tetikleyici | Blaze'siz çalışır mı? |
|---|---|---|
| `buildGlobalLeaderboard` | zamanlanmış (6 saatte bir) | ✅ — GitHub Actions cron |
| `finalizeLeaguePeriod` | zamanlanmış (günlük) | ✅ — GitHub Actions cron |
| `cleanupAbandonedRooms` | zamanlanmış (günlük) | ✅ — GitHub Actions cron |
| `onInviteCreated` | Firestore'a canlı yazma (arkadaş daveti) | ❌ — imkansız |
| `clampImpossibleScores` | Firestore'a canlı yazma (skor hile önleme) | ❌ — imkansız |

Zamanlanmış üç fonksiyon canlı bir Firestore yazmasına tepki vermiyor,
sadece belirli aralıklarla çalışıyor — bu yüzden Cloud Functions olmak
zorunda değiller, aynı mantığı düz bir Node scripti olarak GitHub Actions'ın
kendi zamanlayıcısından (cron) çalıştırabiliyoruz. Son iki fonksiyon ise
gerçekten "biri şu dokümanı yazdığında hemen tepki ver" tetikleyicisi
kullanıyor — bunun bir GitHub Actions eşdeğeri yok, Cloud Functions
çalışma zamanı dışında imkansız. **Bu iki fonksiyon Blaze'e geçilmediği
sürece deploy edilmeyecek ve çalışmayacak** — yani arkadaş daveti push
bildirimi ve otomatik skor hile kırpma şu an devre dışı.

## Otomatik çalışan kısım (elle bir şey yapman gerekmiyor)

### Kurallar ve indeksler — `firebase-deploy.yml`

`firestore.rules`, `firestore.indexes.json` veya `functions/` klasöründe bir
değişiklik `main`'e push'landığında GitHub Actions otomatik olarak
`firebase deploy --only firestore:rules,firestore:indexes` çalıştırır.
Blaze gerektirmez — sadece "Firebase Rules Admin" rolüne sahip bir servis
hesabı (`FIREBASE_SERVICE_ACCOUNT` secret'ı) yeterli.

### Lig'in üç zamanlanmış görevi — `league-scheduler.yml`

`functions/src/cli.ts`, `functions/src/index.ts`'teki
`runBuildGlobalLeaderboard`/`runFinalizeLeaguePeriod`/
`runCleanupAbandonedRooms` fonksiyonlarını (asıl Cloud Function
tanımlarının ayrıştırıldığı düz `async function`'lar) çağıran küçük bir
komut satırı programı. `.github/workflows/league-scheduler.yml` bunu üç
ayrı cron zamanlamasıyla çalıştırıyor, aynı `FIREBASE_SERVICE_ACCOUNT`
secret'ıyla kimlik doğruluyor (rules/indexes deploy'unun kullandığı servis
hesabıyla aynısı — `firebase-admin` SDK'sı `GOOGLE_APPLICATION_CREDENTIALS`
ortam değişkenini okuyor, Cloud Functions çalışma zamanına ihtiyaç yok):

- `build-global-leaderboard` — 6 saatte bir, global tabloyu **tek bir
  doküman** olarak `leaderboards/global`'a yazar.
- `cleanup-abandoned-rooms` — günlük, terk edilmiş odaları temizler.
- `finalize-league-period` — günlük çalışır ama ayın başında değilse
  hiçbir şey yapmaz (idempotency guard `leaderboards/global.lastPeriod.periodId`'i
  kontrol eder) — GitHub Actions cron'un saat dilimi desteklememesinden
  (her şey UTC) dolayı "günde bir kere ayın 1'ine denk gelirse çalış"
  yerine "her gün kontrol et, ay değiştiyse bir kere işle" mantığı
  kullanılıyor.

Elle tetiklemek istersen: GitHub → Actions → "League scheduled tasks" →
"Run workflow" → hangi görevi çalıştırmak istediğini seç.

Doğrulama: Firebase Console → Firestore → `leaderboards` koleksiyonunda
`global` dokümanı görünmeli. `.github/workflows/league-scheduler.yml`'in
son çalışmasının loglarına GitHub → Actions'tan bakabilirsin.

Ayın ödülü uygulama içinden ayarlanır: Geliştirici Paneli → **Lig**
sekmesi. Seçim `leaderboards/config`'e yazılır ve oyunculara bir sonraki
tablo yenilenmesinde (en geç 6 saat) ulaşır. Seçim yapılmazsa ödül o ayın
adını taşıyan çerçeveden türetiliyor (`FRAME:LEAGUE_CHAMPION_2026_09`
gibi) — bkz. `LeagueReward.forPeriod`.

## Blaze'siz çalışmayan kısım — `onInviteCreated`, `clampImpossibleScores`

Bu ikisi Firestore'a **canlı bir yazma** olduğunda (bir davet dokümanı
oluşturulduğunda, bir skor yazıldığında) anında tepki vermesi gereken
gerçek event-triggered fonksiyonlar. Zamanlanmış görevlerin aksine "arada
bir kontrol et" diye bir alternatifleri yok — ya Cloud Functions çalışma
zamanında canlı dinler ya da hiç çalışmaz. Kod `functions/src/index.ts`'te
duruyor (derleniyor, test edilebiliyor) ama **deploy edilmiyor**:
`firebase-deploy.yml` artık `functions` hedefini dahil etmiyor.

Sonuç: arkadaş daveti gönderildiğinde davet edilen kişiye push bildirimi
gitmiyor (davet yine de uygulama içinde görünür, sadece anlık bildirim
yok), ve imkansız yüksek skorlar otomatik kırpılmıyor (var olan istemci
taraflı `WrittenWordDetector` + onay kuyruğu koruması bundan etkilenmiyor,
sadece bu ek sunucu tarafı güvenlik ağı yok). Bu proje Blaze'e geçmeyi
tercih etmediği sürece bu iki özellik bu şekilde kalacak — başka bir
workaround yok.

Blaze'e geçmeye karar verilirse (kredi kartı bağlamak dışında Spark'tan
farkı yok, ayda 2 milyon çağrıya kadar ücretsiz kota var): Firebase
Console → proje seç → "Spark Plan" → "Upgrade" → Blaze, sonra
`firebase-deploy.yml`'deki deploy komutuna `,functions`'ı geri ekle.

## Yerel geliştirme

```
cd functions
npm install
npm run build   # tsc — hataları derleme zamanında yakalar
```

CLI'ı yerelde denemek için (gerçek bir servis hesabı JSON'una ihtiyaç var,
`GOOGLE_APPLICATION_CREDENTIALS` ile göster):

```
node lib/cli.js build-global-leaderboard
```
