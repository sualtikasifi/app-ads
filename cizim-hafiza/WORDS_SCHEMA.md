# words.json şeması

`app/src/main/assets/words.json` — bir JSON array, her eleman bir kelime:

```json
{ "id": 51, "text": "balina", "category": "Hayvanlar", "difficulty": "MEDIUM" }
```

| Alan         | Tip    | Kural                                                             |
|--------------|--------|--------------------------------------------------------------------|
| `id`         | int    | **Tüm dosyada unique** olmalı. Yeni eklerken en yüksek id'den devam et (şu an 1–275 kullanılı). |
| `text`       | string | Küçük harf, tek kelime tercih edilir (Türkçe karakterler serbest). |
| `category`   | string | Şu an kullanılan 8 kategoriden biri (aşağıda) — yeni kategori eklemek istersen `WordCountScreen` otomatik gösterir, ekstra kod değişikliği gerekmez. |
| `difficulty` | string | `EASY`, `MEDIUM` veya `HARD` (büyük harf, `domain.model.Difficulty` enum'una birebir eşleşir). Süre: Kolay 5sn, Orta 7sn, Zor 10sn. |

Mevcut kategoriler: `Hayvanlar`, `Eşyalar`, `Meslekler`, `Spor`, `Doğa`,
`Yiyecekler`, `Taşıtlar`, `Duygular`.

## 1000 kelimeye çıkarma önerisi

- Kategori başına ~125 kelime hedefle (8 kategori × 125 ≈ 1000).
- Zorluk dağılımı: kategori başına kabaca %40 EASY / %40 MEDIUM / %20 HARD —
  çizilmesi kolay/somut kelimeler EASY, soyut veya çok-parçalı kelimeler HARD.
- Aynı kelimenin farklı id'lerle iki kez geçmemesine dikkat et (tekrar
  kelime aynı oturumda zaten `WordDao.getRandomWords` ile tekilleştiriliyor,
  ama havuzda mükerrer `text` olması rastgelelik dağılımını bozar).
- Toplu üretim için bu dosyayı bir script/LLM promptuna referans ver — id
  aralığı ve şema burada sabit, sadece içerik üretmen yeterli.
