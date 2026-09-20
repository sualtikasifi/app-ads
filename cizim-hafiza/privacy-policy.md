# Gizlilik Politikası — Karalak

**Uygulama:** Karalak — Çiz, Hatırla, Yarış
**Paket adı:** com.sualtikasifi.cizimhafiza
**Geliştirici:** AVC Software
**İletişim:** sualtikasifi@gmail.com

**Son güncelleme:** 18 Eylül 2026

> Yayınlanan sürüm: https://sualtikasifi.github.io/app-ads/
> Play Console'a girilecek URL budur. Bu dosya ile `docs/index.html`
> aynı metni taşımalıdır — Play, politikanın uygulama adını VE geliştirici
> adını açıkça içermesini şart koşuyor ve ikisi eksik olduğu için sürüm bir
> kez reddedildi.

## Tek kişilik oyun modu

Oyun geçmişin (skorlar, çizim verilerin, tahminlerin, kıdem/puan durumun)
yalnızca **cihazında yerel olarak** saklanır ve hiçbir sunucuya gönderilmez.

## Arkadaşınla çevrimiçi oynama modu

Bu isteğe bağlı modu kullanmayı tercih edersen, oyunun çalışabilmesi için
aşağıdaki bilgiler Google'ın Firebase altyapısına (Firestore veritabanı)
gönderilir:

- **Cihazına özel, anonim bir kimlik** (Firebase Anonymous Authentication) —
  isim, e-posta veya şifre istenmez, hesap oluşturulmaz.
- **Kendi seçtiğin takma ad** — sadece o anki oyun odasındaki rakibine
  gösterilir.
- **Oda kodu, o oyundaki kelime listesi, skorların ve çizimlerin** — sadece
  o oyun odasındaki iki oyuncu tarafından görülebilir; oyun bittikten sonra
  bu veriler sunucuda kalmaya devam eder (odalar otomatik silinmez) ancak
  başka bir kullanıcı tarafından erişilemez.
- **Gönderdiğin emoji tepkileri.**

Bu veriler üçüncü taraflarla paylaşılmaz, reklam amacıyla kullanılmaz ve
kimlik bilgisiyle eşleştirilmez. Google'ın Firebase altyapısı için genel
gizlilik uygulamaları geçerlidir: https://firebase.google.com/support/privacy

## Hızlı Eşleş modu

Hızlı Eşleş modunda oynadığın turlar (kelimeler, skorlar ve çizimler) bir
havuza kaydedilir ve ileride başka oyunculara rakip turu olarak sunulabilir.
Bir tur havuza **ancak bir insan tarafından incelenip onaylandıktan sonra**
girer. Uygunsuz bulduğun bir çizimi uygulama içinden bildirebilirsin; iki
farklı oyuncunun bildirdiği tur otomatik olarak havuzdan çıkar.

Havuzda oyuncu başına en fazla on tur tutulur; daha eskiler silinir.

## Çizimlerin tanıtımda kullanılabilir

İncelemeye gelen çizimlerden bazıları, Karalak'ın **sosyal medya
hesaplarında tanıtım amacıyla** görsel veya kısa video olarak yayınlanabilir.

Yayınlanan içerikte **yalnızca çizimin kendisi ve çizilen kelime** yer alır.
Takma adın, e-posta adresin, hesap kimliğin veya seni tanımlayabilecek başka
hiçbir bilgi bu içeriğe eklenmez — çizim, kimin çizdiği belli olmadan
paylaşılır.

Çiziminin bu şekilde kullanılmasını istemiyorsan sualtikasifi@gmail.com
adresine yazman yeterlidir; ilgili içerik yayından kaldırılır.

## Çökme raporları ve kullanım istatistikleri

Uygulama Firebase Crashlytics ve Firebase Analytics kullanır: çökme dökümü,
cihaz modeli, Android ve uygulama sürümü, toplu kullanım istatistikleri ve
cihaza özel bir kurulum kimliği. IP adresinden ülke/şehir düzeyinde yaklaşık
bir konum türetilebilir. Bu veriler ad, e-posta veya çizimlerle
eşleştirilmez, reklam amacıyla kullanılmaz ve satılmaz.

## Reklamlar

Uygulama, **Google AdMob** aracılığıyla reklam gösterir:

- Sonuç ekranından sonra, en fazla üç maçta bir gösterilen bir **geçiş
  reklamı**.
- Senin kendi isteğinle açtığın durumlarda (ek ipucu, XP katlayıcı, seri
  kurtarma) gösterilen **ödüllü reklamlar**. Ödüllü reklamlarda ödül
  yalnızca reklamı sonuna kadar izlersen verilir.

Reklam hiçbir zaman kendiliğinden, sen bir işlem başlatmadan gösterilmez.

AdMob, reklamları göstermek ve kişiselleştirmek için **reklam kimliği
(advertising ID)** gibi cihaz tanımlayıcılarını işleyebilir. AB/EEA ve
Birleşik Krallık'taki kullanıcılara Google'ın Kullanıcı Mesaj Platformu
(UMP) üzerinden reklam kişiselleştirme rızası sorulur; bu rızanı istediğin
an geri alabilirsin. Bu durumda Google'ın kendi gizlilik politikası
geçerlidir: https://policies.google.com/privacy

## İzinler

Uygulama yalnızca **titreşim (VIBRATE)** izni ister; bu izin, çizim
süresinin son saniyelerinde haptik uyarı vermek için kullanılır ve
başka hiçbir amaçla kullanılmaz.

## Verilerin silinmesi

Karalak uygulamasını cihazından kaldırdığında (sil/uninstall), cihazında
yerel olarak tutulan tüm oyun geçmişi, kıdem/puan durumu ve ayarlar
otomatik olarak silinir — ayrıca bir işlem yapmana gerek yoktur.

Çevrimiçi (arkadaşınla oynama) modunda Firebase'e gönderilmiş veriler için
silme talebinde bulunmak istersen:

1. sualtikasifi@gmail.com adresine, kullandığın takma adı ve (varsa) oda
   kodunu belirterek bir e-posta gönder.
2. Talebin en geç 30 gün içinde işleme alınır.

Bu talep üzerine silinen veriler: anonim kullanıcı kimliğin, takma adın,
oda/oyun kayıtların (kelime listesi, skorlar, çizimler) ve gönderdiğin
emoji tepkileri. Bu veriler talep edilmediği sürece süresiz olarak
Firebase'de saklanabilir (oyun odaları otomatik silinmez).

## Üçüncü taraflarla paylaşım

Uygulama, yukarıda açıklanan Firebase (Google) altyapısı dışında hiçbir
veriyi üçüncü taraflarla paylaşmaz.

## İletişim

Sorularınız için: sualtikasifi@gmail.com
