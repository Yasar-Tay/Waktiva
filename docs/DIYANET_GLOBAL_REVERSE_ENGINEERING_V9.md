# Diyanet Yuksek Enlem Tersine Muhendislik Raporu - Waktiva v9

Tarih: 13 Temmuz 2026

## 1. Amac ve sonuc

Bu calismanin amaci Diyanet'in namaz vakti tablosunu sehir bazli sabitlerle
kopyalamak degil, tablolarda gorulen yuksek-enlem davranisini genellenebilir
bir hesaplama motoruna donusturmektir. v9 revizyonu ozellikle yaz sonundaki
yatsi gecis egrisini ve kisa imsak olay-yoklugu donemlerini duzeltir.

Nihai motor su sonuclari verdi:

| Olcum (kesin eslesen 2.702 sehir) | v8 | v9 |
|---|---:|---:|
| Merkezlenmis yatsi maksimumu 8 dakikadan buyuk | 213 | 84 |
| Mayis-Eylul 14 gunluk yatsi suruklenmesi 5 dakikadan buyuk | 975 | 520 |
| Merkezlenmis yatsi maksimumu iyilesen sehir | - | 751 |
| Merkezlenmis yatsi maksimumu kotulesen sehir | - | 89 |
| Yeni 8 dakika ustu sehir | - | 5 |
| Degisen bes-saat sinirli sehir | - | 0 |

Bu rakamlar Diyanet'in tam 2026 tablolarindan, ayni sehir kimlikleriyle
eslestirilmis v8 ve v9 ciktilarinin karsilastirilmasindan elde edildi.

## 2. Veri kapsami

- Diyanet katalogundan mutlak enlemi en az 45 derece olan 3.047 sehir GeoNames
  koordinatlari ve saat dilimleriyle eslestirildi.
- 3.040 sehir icin 1 Ocak-31 Aralik 2026 arasindaki 365 gunluk tablolar alindi.
- Toplam 1.109.600 resmi gunluk satir kullanildi; kis gunleri de veri setindedir.
- 2.702 sehir adi ulke icinde tek ve kesin bir GeoNames eslesmesine sahiptir.
- Tum enlemlerde 703 katalog kaydi otomatik ad eslestirmesinde eslesmedi.
- Yedi resmi sayfa tam yil tablosu vermedi: St. Polten, Punta Arenas,
  Beaurepaire (RA), Gerardmer, Bad Segeberg, Biberach ve Hofgeismar.

Mayis-Eylul kesiti yalnizca yaz gecis egrisinin suruklenmesini olcmek ve fit
etmek icin kullanildi. Rejim secimi ve yil geneli hata raporu kis dahil tam yil
verisiyle hesaplandi.

## 3. Olcum yontemi

Diyanet'in yayinlanmamis sabit sehir koordinati ile GeoNames/Waktiva
koordinati ayni olmayabilir. Saat dilimi veya kaynak koordinat farki da tum
yili benzer miktarda kaydirabilir. Bu nedenle iki ayri olcum kullanildi:

1. Ham hata, kullaniciya gorunen saat farkini gosterir.
2. Sehir medyan ofseti cikarilmis hata, sabit koordinat/ofset etkisinden sonra
   mevsimsel egrinin bicim hatasini gosterir.
3. Mayis-Eylul icindeki en kotu 14 gunluk hata degisimi, Berlin'de gorulen
   gun gun buyuyen veya ters yone giden gecis anomalilerini yakalar.

Gece yarisi gecislerinde farklar dairesel saat farki olarak normalize edilir;
ornegin 23:59 ile 00:01 arasindaki fark 1.438 degil 2 dakikadir.

## 4. Kesfedilen rejimler ve v9 kurallari

Kriter profili ile yuksek-enlem rejimi ayridir. Profil imsak/yatsi acilarini
tanimlar; rejim ise bu astronomik olaylarin yil icinde bulunup bulunmamasina
gore secilir.

### 4.1 Kisa imsak olay-yoklugu

v8, `ROBUST_MISSING_FAJR_FULL_YEAR` rejimini yalnizca olay-yoklugu en az 10 gun
surdugunde seciyordu. Resmi veri, Passau gibi 10 gunden kisa araliklarda da
ayni adaptif ailenin gerekli oldugunu gosterdi. v9'da herhangi bir bos olmayan
imsak olay-yoklugu araligi bu rejimi secer.

### 4.2 Standart yatsi sonbahar omzu

Gecikmeli olmayan ve bes-saat siniri kullanmayan sehirlerde yatsi gecisi,
cozulmus yatsi olay-yoklugu gun sayisina gore olceklenir:

| Yatsi olay-yoklugu | Artik genlik | Us |
|---|---:|---:|
| 0-29 gun | 12 dk | 4.0 |
| 30-59 gun | 12 dk | 1.5 |
| 60-89 gun | 14 dk | 1.25 |
| 90+ gun | 16 dk | 1.0 |

Bu ayrim Berlin ve Passau'daki yaz sonu yon/sekil hatasini sehir adi kontrolu
eklemeden duzeltir.

### 4.3 Gecikmeli yatsi dali

Imsak olayinin donusunden sonra yatsi olayinin daha gec dondugu normal gece
eksenli sehirlerde ayri fit kullanilir:

- Artik genlik: 13 dakika
- Dogrudan olaya yakinlasma esigi: 10 dakika
- Gecis usu: 1.5

Parametreler 54.172 yaz-sonbahar orneginde tarandi. Onceki `5/18/2` modeli
MAE 2,357 ve p95 4,292 dakika verirken secilen `10/13/1,5` modeli MAE 2,271 ve
p95 4,206 dakika verdi. Paris, Amsterdam ve Bruksel sonradan kontrol edilen
holdout/denetim sehirleridir; fit tek bir sehre gore yapilmadi.

### 4.4 Bes-saat sinirli kutup ailesi

Bu revizyonda Tromso ve diger bes-saat sinirli kutup ailesi yeniden fit
edilmedi. Genel yapiyi bozmamak icin v8 davranisi korundu:

- Standart dal: 20 dakika ve dogrusal gecis
- Gecikmeli dal: 20 dakika genlik, 5 dakika yakinlasma esigi, us 2

Nihai karsilastirmada bu ailede merkezlenmis maksimumu degisen sehir sayisi
sifirdir.

## 5. Sehir sonuclari

| Sehir | v8 merkezlenmis max | v9 merkezlenmis max | v8 14 gun suruklenme | v9 14 gun suruklenme |
|---|---:|---:|---:|---:|
| Berlin | 8 | 4 | 11 | 6 |
| Passau | 11 | 5 | 14 | 5 |
| Paris | 4 | 4 | 3 | 3 |

Berlin icin 24 Temmuz-11 Agustos arasindaki her resmi gun test fiksturune
eklendi. Secili Berlin serisinde yatsi MAE 0,85, maksimum hata 2 dakikadir.
Amsterdam ve Bruksel gecikmeli dal holdout sonuclari sirasiyla MAE 1,18/max 4
ve MAE 1,09/max 3 dakikadir.

## 6. Bilinen sinirlar ve acik riskler

Bu calisma Diyanet'in ic kaynak kodunun birebir kopyasi oldugunu iddia etmez.
Diyanet'in kullandigi sabit sehir koordinatlari, yukseklik modeli, yuvarlama
siralari ve istisna tablolari yayinlanmis degildir.

v9 ile daha once 8 dakika veya altinda olup 8 dakikayi asan bes sehir vardir:

| Sehir | Ulke | v8 | v9 |
|---|---|---:|---:|
| Tambov | Rusya | 8 | 10 |
| Maidstone | Birlesik Krallik | 7 | 9 |
| Penza | Rusya | 7 | 9 |
| Ipswich | Birlesik Krallik | 8 | 9 |
| Lubbenau | Almanya | 8 | 9 |

Bu sapmalar gizlenmedi ve global testte yeni agir sapma sayisi en fazla bes
olarak korumaya alindi. Imsak tarafinda halen genis bir tersine muhendislik
borcu vardir: tum 3.040 raporda merkezlenmis maksimum 8 veya 14 gunluk
suruklenme 5 dakika esigini asan 1.048 sehir bulunur. Tromso/kutup fit'i ve
imsak modelinin ayri global revizyonu sonraki asamadir.

## 7. Tekrarlanabilirlik

Veri toplama ve denetim dosyalari `build/diyanet-global-audit` altinda uretilir
ve Git'e eklenmez. Ana komutlar:

```powershell
python scripts/diyanet_global_audit.py
.\scripts\download_diyanet_pages.ps1
python scripts/diyanet_global_audit.py --fetch-tables --cached-only
$env:DIYANET_GLOBAL_AUDIT_DIR=(Resolve-Path 'build\diyanet-global-audit').Path
.\gradlew.bat :app:testDebugUnitTest --tests "com.ybugmobile.waktiva.DiyanetGlobalAuditTest"
python scripts/fit_diyanet_isha_transition.py
```

Normal unit test kosusunda global test veri ortam degiskeni yoksa atlanir. Tam
yerel panel, Berlin gunluk serisi ve hesaplayici testleri her kosuda calisir.

## 8. Ilgili dosyalar

- Motor: `app/src/main/java/com/ybugmobile/waktiva/data/local/diyanet/AdaptiveDiyanetCalculator.kt`
- Global denetim: `app/src/test/java/com/ybugmobile/waktiva/DiyanetGlobalAuditTest.kt`
- Veri toplayici: `scripts/diyanet_global_audit.py`
- Sayfa indirici: `scripts/download_diyanet_pages.ps1`
- Parametre tarayici: `scripts/fit_diyanet_isha_transition.py`
- Gecikmeli yatsi fiksturu: `app/src/test/resources/diyanet/delayed_isha_holdout_2026.csv`
