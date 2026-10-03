# Diyanet namaz vakti algoritması: başvuru belgesi

Son güncelleme: 2026-10-03. Bu belge, Diyanet'in yayımladığı tabloların (namazvakitleri.diyanet.gov.tr)
nasıl hesaplandığına dair bugüne kadar bulunan her şeyi tek yerde toplar. Amaç, bir dahaki çalışmada
keşfi baştan yapmamak. Kurallar 2026 resmî tablolarından türetildi ve hiç ayar için kullanılmayan 2027
tablolarında doğrulandı.

Uygulamadaki karşılığı: method 13 (`LocalPrayerCalculator.DIYANET_METHOD_ID`).

- `data/local/diyanet/DiyanetClassicAlmanac.kt`: almanak, vakit formülleri, namaz günü sınırları.
- `data/local/diyanet/DiyanetHighLatitudeTwilight.kt`: 45°K–72°K İmsak/Yatsı yaz kuralı.
- `data/local/LocalPrayerCalculator.kt`: kaynak seçimi.
- V14 (`DiyanetReconstructionV14`) ve V9 (`AdaptiveDiyanetCalculator`) eski, ayarlanmış motorlar.
  V14 (45°K ve kuzeyi) artık yalnızca 72°K'nin kuzeyinde İmsak/Yatsı ve bazı İkindi yedekleri için
  kullanılıyor. V9 45°K'nin altında hâlâ çalışıyor ama vakitleri klasik almanak veriyor; V9 yalnızca
  klasik bir değer vermezse devreye giren yedek.

İlgili commit'ler: `e29f4ef` (2027 paneli), `e4c3166` (V14 gece yarısı batışı), `fb8f48e` (klasik
almanak + yüksek enlem kuralı), `1da9d09` (namaz günü sınırları, kutup bölgesi).

---

## 1. Temel: klasik almanak

Diyanet, saat açısı formülünü düşük hassasiyetli almanakla kullanıyor. **Güneş her tarih için bir
kez, o tarihin 0h UT anında alınıyor**; olay anına göre yinelenmiyor. Hassas efemeris
kullanılmıyor. Bu bulgu tek başına Güneş/Öğle/İkindi/Akşam'ı her gün dakikası dakikasına veriyor.

Julian gün `jd = epochDay + 2440587.5`, `d = jd − 2451545`:

```
g   = 357.529 + 0.98560028·d          (ortalama anomali)
q   = 280.459 + 0.98564736·d          (ortalama boylam)
L   = q + 1.915·sin g + 0.020·sin 2g  (ekliptik boylam)
e   = 23.439 − 0.00000036·d           (eğiklik)
RA  = atan2(cos e·sin L, cos L) / 15  (saat)
δ   = asin(sin e·sin L)               (deklinasyon)
EqT = wrap(q/15 − wrap24(RA))         (saat, −12..12)
öğle (UT saat) = 12 − EqT − boylam/15
saat açısı(h)  = acos((−sin h − sin φ·sin δ) / (cos φ·cos δ)) / 15   (|x|>1 ise yok)
```

Bütün süreler, o tarihin 0h UT'sinden itibaren dakika olarak tutulur (İmsak negatif olabilir).
Gösterim için **en yakın dakikaya yuvarlama**: `+30 sn`, saniyeler atılır (`roundForDisplay`).

### Açılar ve temkinler

| Vakit | Açı / formül | Temkin |
|---|---|---|
| İmsak | 18° | 0 |
| Güneş | 0.833° | −7 dk |
| Öğle | öğle | +5 dk |
| İkindi | Şafii: gölge = öğle gölgesi + boy | +4 dk |
| Akşam | 0.833° | +7 dk |
| Yatsı | 17° (enlem ≤ 43°K ve tüm güney), 16° (43°K kuzeyi) | 0 |

Toronto (43.65°K) 16° kullanıyor; İstanbul ve Sidney 17°. Bu açılar ve temkinler ayarlanmış değil,
Diyanet'in kendi değerleri.

### İkindi'nin özel durumları

- Öğlede güneş ufkun altındaysa (öğle yüksekliği 90 − |φ − δ| ≤ 0): **İkindi = Öğle** (Tromsø
  25 Kasım – 18 Ocak).
- Güneş öğlede ufkun çok az üstündeyse formül İkindi'yi Öğle'den önceye düşürür (İkindi temkini
  küçük): **max(İkindi, Öğle)** (Rovaniemi, kış solstisi civarı).
- Kutup yazında da formül kullanılıyor.

### Güney (≤ 45°G)

Düz 18° / 17° açılar. Açının oluşmadığı gecelerde Diyanet **00:00** yazıyor (İmsak için o günün
00:00'ı, Yatsı için ertesi günün 00:00'ı). Kuzeyin yüksek enlem kuralları güneyde yok. Ushuaia,
Río Gallegos, Dunedin, Queenstown dakikası dakikasına.

---

## 2. Namaz günü sınırları (Güneş ve Akşam)

Yayımlanan Güneş ve Akşam, temkinli doğuş ve batış; ama namaz günü (Güneş→Akşam) **en fazla 19,
en az 5 saat**, Öğle'ye (temkinli, öğle+5) göre:

```
Öğle_t   = öğle(t) + 5
Öğle_sol = öğle(21 Haziran, aynı yıl) + 5      (güneyde en yakın 21 Aralık; orada hiç bağlamıyor)
Güneş(t) = min( max( doğuş(t) − 7, Öğle_t − 570, Öğle_sol − 570 ), Öğle_t − 150 )
Akşam(t) = max( min( batış(t) + 7, Öğle_t + 570, Öğle_sol + 570 ), Öğle_t + 150 )
```

- Güneş hiç doğmuyorsa ya da batmıyorsa: güneş öğlede ufkun üstündeyse (kutup günü) doğuş = −∞,
  batış = +∞; altındaysa (kutup gecesi) tersi. Sınırlar günü verir.
- 21 Haziran terimi bir "tutma" yaratıyor. ~59°K'nin kuzeyinde yaz ortasında Güneş solstise kadar
  sabit saatte kalıyor, sonra Öğle ile kayıyor; Akşam ise solstise kadar Öğle ile kayıyor, sonra
  sabit kalıyor. Umeå 2026: Güneş 18 Mayıs – 24 Haziran 01:16 UT.
- Kısa gün tarafında kış solstisi terimi yok; düz Öğle ± 150 en iyi.
- Ölçüm: 58°K'nin kuzeyinde uzun ve kısa günlerin Güneş/Akşam'ının %98'i 1 dakika içinde.

Kod: `DiyanetClassicAlmanac.prayerAxis`.

---

## 3. Yüksek enlem İmsak/Yatsı kuralı (45°K – 72°K)

`DiyanetHighLatitudeTwilight`. Her yıl ve konum için bir kez, bütün yıl hesaplanır. Notasyon: gün
indeksi `i` (yılın günü − 1). Bütün zamanlar 0h UT'den dakika.

### 3.1 Tanımlar

```
Güneş(i), Akşam(i)       : Bölüm 2'deki sınırlı (yayımlanan) değerler
gece_önce(i) = Güneş(i) − (Akşam(i−1) − 1440)    (i gününün İmsak'ının gecesi)
gece_sonra(i) = Güneş(i+1) + 1440 − Akşam(i)     (i gününün Yatsı'sının gecesi)
İmsak18(i)   = öğle(i) − saatAçısı(18°)          (yoksa null)
Yatsı16(i)   = öğle(i) + saatAçısı(16°)
minYük(i)    = φ + δ(i) − 90                     (gece yarısı güneş yüksekliği)
```

### 3.2 Yaz dönemi ve referans gece

- **Kuzey rejimi (18° kayboluyor, ~48.56°K'nin kuzeyi):** B = 21 Haziran'a kadar İmsak18'in
  olmadığı ilk gün; B2 = 21 Haziran'dan sonra İmsak18'in yeniden olduğu ilk gün.
  Yaz = [E, E2] = [B, B2 − 1]. Referans gece R = B − 1 (18°'ye hâlâ ulaşılan en kısa gece).
- **Tepe rejimi (18° hiç kaybolmuyor, 45°K – ~48.56°K):** E = E2 = R = 21 Haziran.

### 3.3 Yaz payı

```
Δ = İmsak18(R) − (öğle(R) − 720)      (R'deki 18° İmsak'ın gece yarısından sonraki dakikası; öğle temkinsiz)
N = gece_önce(R)
s_İmsak = 0.18661 + 0.37909·Δ/N       (2026'dan uydurma)
s_Yatsı = (1 + 2Δ/N) / 6              (temiz formül; 2027'de sapmasız)
Yaz İmsak(i) = Güneş(i) − s_İmsak·gece_önce(i)
Yaz Yatsı(i) = Akşam(i) + s_Yatsı·gece_sonra(i)
```

- Pay bütün yaz sabit (erken–geç yaz farkı ~0.001).
- Şehirler arası ±7 dakikalık "testere dişi" buradan gelir: R tam gün, Δ ise ~√derinlik ile değişir.
  Berlin ile Nauen (0.08° fark) farklı R'ye düşüyor.
- (3/16)(1 + 2Δ/N) İmsak için ~0.3 dk sapmalı. Kesin İmsak formülü bulunamadı.
- Gece 5 saate inince (Bölüm 2'deki 19 saatlik sınır) İmsak ve Yatsı neredeyse sabitleniyor. Ayrı
  bir kural değil; ölçülen "sanal gece" 316.7 (İmsak) ve 286 (Yatsı) temkinli dakika, bu formülün
  sonucu.

### 3.4 Sınır harmanı (18° kaybının ±1 günü)

R gecesi 18°'ye çok az ulaşıyorsa Diyanet sık sık R − 1'i referans alıyor. Olasılık derinliğe
bağlı: derinlik ≈ 0'da ~%70, gün kesri 0.1'de ~%10. Üst kenarda (kesir → 1) bazen R = B, ~%13.
Muhtemel neden: Diyanet'in şehir koordinatları geonames'ten birkaç km farklı. Boylam ve saat
dilimiyle ilişkisi yok. Uygulanan harman:

```
derinlik = −18 − minYük(R)
kesir    = derinlik / (minYük(B) − minYük(R))
w        = 1 / (1 + exp(−(0.035 − kesir)/0.02))
s        = (1 − w)·s(R) + w·s(R − 1)        (İmsak ve Yatsı aynı ağırlıkla)
```

Yalnız kuzey rejiminde. Tepe rejiminde 48.56°K sınırına çok yakın şehirlerde benzer belirsizlik var
ama harmanlanmıyor.

### 3.5 Geçişler (20.5 dakika)

İmsak, 18° saatinden yaz hedefine **saat cinsinden düz bir çizgiyle** iniyor; toplam düşüş
**20.5 dakika**. Bu her enlemde ölçüldü: çizgi uydurmasının düşüşü 20.50 ± 0.42 dk.

```
F_E  = Yaz İmsak(E)
A    = E'den önceki son gün, İmsak18(A) ≥ F_E + 20.5     (sürekli UT dakikasıyla)
İmsak(i) = F_E + 20.5·(E − i)/(E − A)    , A < i < E      (A ve öncesi 18°)

F_E2 = Yaz İmsak(E2)
A2   = E2'den sonraki ilk gün, saat(İmsak18(A2)) ≥ saat(F_E2) + 20.5
İmsak(i) = F_E2 + 20.5·(i − E2)/(A2 − E2), E2 < i < A2
```

- **Sonbaharda yerel saat karşılaştırması:** `saat()` yerel saatin gün içi dakikası (0–1440).
  Güneşin gece yarısı yerel 00:00'dan önce olan şehirlerde (Kazan, Oskemen, Aihui, Semey,
  Naberezhnye Chelny, Baltasi, Arsk) yaz sonrası ilk 18° İmsak önceki tarihin 23:xx'ine düşer ve
  "daha geç" okunur; geçiş olmadan hemen 18°'ye dönülür.
- Baharda bu sorun yok (ileri aramayla A, sarma olmadan bulunuyor).
- **Yatsı:** 16° ile simetrik. AI = E'den önceki son gün, Yatsı16 ≤ I_E − 20.5; sonbaharda AI2 =
  E2'den sonraki ilk gün, Yatsı16 ≤ I_E2 − 20.5 (UT ile). Yatsı yazı da İmsak'ın E/E2'sine bağlı.
  16° yaz boyunca kaybolmasa bile (48.5–50.5°K) Yatsı E'de yaz payına geçiyor.
- 20.5'in kökeni bilinmiyor.
- Geçiş başlangıcı A tek bir eşikle tanımlanamıyor (boşluk, pay, minYük denendi). Doğru okuma:
  hedef birincil, A çizginin 18° eğrisini kestiği gün.

---

## 4. Uygulamadaki kaynak seçimi (method 13)

| Vakit | Kaynak |
|---|---|
| Güneş, Akşam | Her yerde `DiyanetClassicAlmanac` (sınırlı eksen) |
| Öğle | Klasik |
| İkindi | Klasik (Öğle ile Akşam arasında değilse V14/adhan yedekleri) |
| İmsak, Yatsı | 45°K–72°K: `DiyanetHighLatitudeTwilight`; < 45°K ve güney: klasik (güney ≤45°G'de 00:00 kuralı); > 72°K: V14 |

Önbellek anahtarı `method13EngineVersion`: `V14 sürümü + almanak sürümü + yüksek enlem sürümü`.
Kurallar değişince sürümleri değiştir.

---

## 5. Sonuçlar (güvenilir segment, 2027, hiç ayarlanmadı)

Ortalama mutlak hata (dakika). "V14.2" = `e4c3166`, "şimdi" = `1da9d09`.

| Vakit | V14.2 | şimdi |
|---|---|---|
| İmsak | 0.777 | 0.243 |
| Güneş | 0.095 | 0.083 |
| Öğle | 0.064 | 0.064 |
| İkindi | 0.071 | 0.071 |
| Akşam | 0.091 | 0.080 |
| Yatsı | 0.683 | 0.206 |
| İmsak ≥65.5°K (16 şehir) | 2.00 | 0.43 |

İstanbul, Toronto, Sidney: iki yıl, altı vakit, her gün ≤ 1 dk. Panel (14 şehir): her vakit ≤ 8 dk.
Avrupa örneği (19 şehir × 16 tarih × 6 vakit): 1.644/1.824 tam, 1.798 ≤ 1 dk.

---

## 6. Diyanet'in kendi veri hataları ve uymayan şehirler

Bunlar algoritma değil; çözmeye çalışma.

- **Kanada BC/Alberta (9059–9073):** 2027'de kalıcı yaz saati (kışın 1 saat ileri). 2026'da 1–2 Kasım
  da yaz saatinde.
- **Coihaique (16205):** `America/Coyhaique` ile ±60 dk.
- **Ukhta (16111):** Ekim 2026'da İmsak haftalarca "23:44" yayımlanmış, sonra düzeltilmiş.
- **Syktyvkar (16163):** 27 Ağustos 2027'den itibaren iki ay İmsak "23:59".
- **Moskova (16126) 2027-08-07:** İmsak "1.00:" (`official_2027_anomalies.csv`'ye ayrıldı).
- **Alchevsk, Horlivka, Donetsk:** 2026 sonunda Kyiv'den Moskova saatine geçirilmiş.
- **Yıla özgü ~130 dk yaz İmsak sapmaları:** 2026'da Semey, Kazan, Arsk; 2027'de Laishevo,
  Naberezhnye Chelny.
- **Konumu farklı şehirler:** Güneş ve Akşam da kayık. Arnstorf (10048), Hovd (15486, 15517),
  Reinach, Hamm, Lohne, Dieuze, Kuressaare (12787), Kostomuksha (16048), "şüpheli eşleşme"
  segmentindeki ~69 şehir.

---

## 7. Açık sorular

1. İmsak payının kesin formülü (Yatsı'nınki (1 + 2Δ/N)/6).
2. 20.5 dakikalık geçişin kaynağı.
3. 18° kaybındaki ±1 gün; tahmin: Diyanet koordinatları. Diyanet'in kendi şehir koordinatları
   bulunursa çözülür.
4. Kural gerçekten 45°K'de mi başlıyor? 43.65°K ile 45°K arasında veri yok (45.01°K'de geçiş
   zaten ~22 dk).
5. 72°K kuzeyi (resmî veri yok; V14).
6. Paris 2026 yazı: İmsak +2–3, Yatsı −2; açıklanmadı.

---

## 8. Veri ve araçlar

Repo dışındaki denetim klasörü: `C:\Users\yBug\Desktop\Waktiva\Diyanet Algorithm\diyanet-global-audit\`

- `cities.csv`: 3.047 şehir (Diyanet id, geonames koordinatları, saat dilimi).
- `official_2026.csv`: 3.040 şehir × 365 (CRLF!).
- `official_2027.csv`: aynı şehirler, 1.109.600 satır (LF).
- `README_2027.md`: 2027 verisinin çekiliş ve kontrol notları.
- `fetch_2027.sh`, `parse_2027.sh`: sayfa indirme ve ayrıştırma.
  - Site her şehir sayfasında yalnızca **o ayın** tablosunu ve **bir yıllık** tabloyu (Ekim
    2026'dan itibaren 2027) gösteriyor. Geçmiş aylar sitede yok.
  - Sayfa adresi `https://namazvakitleri.diyanet.gov.tr/tr-TR/<city_id>`; ay adlarında HTML
    entity'leri var.
- `cache/pages/live_2026-10-03/`: 19 Avrupa şehrinin canlı sayfaları; kayıtla birebir aynı.
- `v14_kotlin_city_quality.csv`: 2026'da sabitlenen segmentler. Sütun 27 =
  `trusted_full_coordinate_axis`, 24 = `southern_fallback`, 22 = `suspicious_mapping`.
  Değerlendirmede ayrıca 9059–9073 ve 16205'i çıkar.
- `eval_<yıl>_<etiket>.csv`: üretim hataları; `final` = V14.2, `hl`, `axis` = son sürüm.
- `analyze_eval.sh eval_x.csv ...`: segment/ay/rejim özeti.
- `europe_sample_comparison.csv`: Avrupa örneği satır satır.
- `high_latitude/`: Python prototipi.
  - `classic.py`, `feat.py`, `model.py`, `model2.py` (= üretim), `evaluate.py`, `README.md`.
  - Kullanım: `python evaluate.py <yıl> '<json parametre>' [örnek]`.

Repo içi:

- `app/src/test/resources/diyanet/official_panel_2026|2027/`: 14 şehirlik panel.
- `DiyanetProductionPanelTest`: panel testleri (≤ 8 dk, klasik şehirler ≤ 1 dk, yaz kuralı).
- `DiyanetYearEvaluationTest`: ortam değişkenli; tüm resmî yılı üretim koduyla karşılaştırır.

```bash
DIYANET_GLOBAL_AUDIT_DIR="C:/Users/yBug/Desktop/Waktiva/Diyanet Algorithm/diyanet-global-audit" DIYANET_OFFICIAL_YEAR=2027 DIYANET_EVAL_LABEL=deneme ./gradlew.bat :app:testDebugUnitTest --tests "com.ybugmobile.waktiva.DiyanetYearEvaluationTest" --rerun-tasks
```

Bir yıl ~10 dakika sürüyor. İki değerlendirmeyi aynı anda çalıştırma ve değerlendirme
çalışırken derleme yapma.

---

## 9. Yöntem notları

- **Ayar ve doğrulama:** 2026'da kur, 2027'de doğrula; 2027'yi asla ayar için kullanma.
- **Hızlı deneme:** Python prototipi tüm set için ~2 dakika; Kotlin değerlendirmesi ~10 dakika.
  Önce prototipte dene.
- **UTC farkı:** Resmî satırın UTC farkı Öğle'den çıkarılabiliyor:
  `round((Öğle_yerel − (öğle_UT + 5)) / 30)·30`. tzdata gerekmiyor.
- **CRLF:** `official_2026.csv` CRLF; karşılaştırmadan önce `\r` temizle.
- **İzlerin okunması:**
  - **Zamanda doğrusal mı:** Geçişler saat cinsinden mi doğrusal, açı cinsinden mi? Saat cinsinden
    doğrusal olanlar sabit düşüşlü çizgi.
  - **Gece payı:** Yaz boyunca payın sabit kalması gece payı kuralını gösterir.
  - **Testere dişi:** Yıldan yıla sıçrayan şehre özgü kayma, tam güne bağlı bir referans günün
    işaretidir.
