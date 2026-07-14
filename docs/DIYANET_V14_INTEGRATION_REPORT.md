# Diyanet V14 Waktiva Entegrasyon Raporu

Tarih: 2026-07-14

## Sonuç

V14 paketi Waktiva'nın mevcut `LocalPrayerCalculator` ve repository akışına merge edildi. Ayrı bir `LocalPrayerCalculator_v14` sınıfı oluşturulmadı. Method 13 yalnız kuzey yarımkürede `latitude >= 45.0` olduğunda V14'e yönlenir; diğer konumlar mevcut Adaptive Diyanet V9 yolunu korur. V14 katsayıları paket kaynaklarındaki haliyle bırakıldı.

## Değiştirilen dosyalar

- `app/src/main/java/com/ybugmobile/waktiva/data/local/LocalPrayerCalculator.kt`
  - Method 13 V14/V9 yönlendirmesi, güney diagnostic'i ve İkindi güvenlik kuralları eklendi.
- `app/src/main/java/com/ybugmobile/waktiva/data/local/diyanet/DiyanetNightAstronomyKernel.kt`
  - Fiziksel namaz gecesi üzerinde twilight kök sahipliği eklendi.
- `app/src/main/java/com/ybugmobile/waktiva/data/local/diyanet/DiyanetReconstructionV14.kt`
  - Paket V14 motoru ve konum/yıl yıllık profil cache'i eklendi.
- `app/src/main/java/com/ybugmobile/waktiva/data/local/diyanet/DiyanetModels.kt`
  - V9/V14 cache sürümü seçiminde kullanılan motor sürüm erişimi eklendi.
- `app/src/main/java/com/ybugmobile/waktiva/data/repository/PrayerRepositoryImpl.kt`
  - Yerel method 13 hesapları `Dispatchers.Default` üzerine taşındı; cache anahtarına seçilen motor sürümü eklendi; routing diagnostic'leri loglanıyor.
- `app/src/test/java/com/ybugmobile/waktiva/DiyanetReconstructionV14Test.kt`
  - V14 örnekleri, kuzey/güney routing, tam-yıl invariant, DST, 23:xx/00:xx kökü ve yıl sınırı testleri eklendi.
- `app/src/test/java/com/ybugmobile/waktiva/DiyanetV14GoldenRegressionTest.kt`
  - `cities.csv` ve `official_2026.csv` kullanan üretim Kotlin motoru golden runner'ı eklendi.
- `app/src/test/java/com/ybugmobile/waktiva/LocalPrayerCalculatorTest.kt`
  - Doğrulanmış polar gecedeki `asr = dhuhr` istisnası açıkça test edildi.
- `app/src/test/java/com/ybugmobile/waktiva/AdaptiveDiyanetCalculatorTest.kt`
  - Method 13'ün artık V14'e ait olan eski V9 entegrasyon beklentileri ayrıştırıldı.
- `app/src/test/java/com/ybugmobile/waktiva/DiyanetOfficialPanelBenchmarkTest.kt`
  - Fiziksel doğuş/batış olmayan polar tarihler için eski V9 panel guardrail'i açık ve sınırlı bir istisnaya dönüştürüldü.

## Tasarım kararları

- V14 kapsamı `methodId == 13 && latitude >= 45.0` olarak sınırlandı. Güneyde `latitude <= -45.0` V9'a döner ve `SOUTHERN_HEMISPHERE_V14_DISABLED` diagnostic'i üretir.
- Normal günde geçersiz Adhan İkindi değeri Öğle'ye eşitlenmez. Önce 62 derece referans-enlem hesabı, ardından Öğle-Akşam orta noktası denenir; orta nokta kullanılırsa diagnostic üretilir. Yalnız V14'ün doğruladığı polar gecede `asr = dhuhr` kullanılır.
- Yıllık profil cache anahtarı yıl, enlem, boylam, saat dilimi, yükseklik, profil ve V14 sürümünü içerir; cache 64 girişle sınırlıdır.
- Repository'nin tüm yerel aylık hesap çağrıları `Dispatchers.Default` üzerinde çalışır. Böylece ilk yıllık profil üretimi UI thread'ini bloke etmez.
- Diyanet cache anahtarında kuzey V14 ve diğer konumlardaki V9 sürümleri ayrılır; eski V9 verisi V14 kapsamındaki konumlarda yeniden kullanılmaz.
- Invariant testinde normal günler için `fajr < sunrise < dhuhr < asr < maghrib < isha` zorunludur. İstenen polar-gece kuralı nedeniyle tek açık istisna `dhuhr == asr` değeridir.

## Bağımlılıklar

Derleme stub olmadan repository'deki gerçek bağımlılıklarla yapıldı:

- `com.github.batoulapps:adhan-java:1.1.0`
- `org.shredzone.commons:commons-suncalc:3.11`

## Test sonuçları

- `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin --rerun-tasks`: başarılı.
- V14 + `LocalPrayerCalculator` hedefli testleri: 13 test, 13 başarılı.
- `:app:testDebugUnitTest`: 46 test, 0 hata, 3 harici-veri testi ortam değişkeni olmadığı için atlandı.
- Tam Kotlin golden koşumu:
  - 3.040 resmi şehir, 1.109.600 gün, 6.657.600 vakit.
  - 72 güney-yüksek-enlem aylık V9 fallback diagnostic'i.
  - Fajr: MAE `2.12759`, P99 `47`, `>10` sayısı `26.110`, maksimum `634`.
  - Isha: MAE `2.65982`, P99 `44`, `>10` sayısı `26.593`, maksimum `608`.
  - Altı vakit: MAE `2.11994`, P99 `46`, `>10` sayısı `158.083`, maksimum `634`.
  - Çıktı: `v14_kotlin_golden_summary.csv`.

Golden runner şu şekilde çalıştırılır:

```powershell
$env:DIYANET_GLOBAL_AUDIT_DIR='C:\path\to\diyanet-global-audit'
.\gradlew.bat :app:testDebugUnitTest --tests "com.ybugmobile.waktiva.DiyanetV14GoldenRegressionTest"
```

## Kalan riskler

- V14 katsayıları kuzey yarımküre 2026 verisiyle kalibre edilmiştir. Yıl sınırı davranışı unit test ile doğrulansa da ikinci bir resmi yıl golden veri seti henüz yoktur.
- Kaynak raporda belirtilen şüpheli şehir/koordinat/saat-dilimi eşleşmeleri ham global maksimumları büyütmektedir; runner bu kayıtları bilerek dışlamaz.
- Fiziksel güneş doğuşu veya batışı olmayan bazı polar tarihlerde sentetik çekirdek-eksenin resmi saate farkı yüksek olabilir. Sıralama invariant'ları korunur; doğruluk riski legacy panel ve global golden guardrail'leriyle görünür tutulur.
- `LocalPrayerCalculator` senkron bir alt seviye API'dir. Mevcut uygulama çağrılarının tamamı repository üzerinden `Dispatchers.Default` kullanır; gelecekte doğrudan eklenecek çağrıların da UI thread dışında tutulması gerekir.

## Satır bazlı segment analizi

Production motoruna çıktı seçimini etkilemeyen bir trace callback'i ve
`DiyanetV14SegmentAnalysisTest` eklendi. 3.040 şehirlik son koşum 10 dakika 18
saniyede tamamlandı ve ham golden sonucu değiştirmedi.

- Production-only güvenilir kuzey segmenti: 2.913 şehir, MAE `0.95224`, P99 `4`.
- Bu segmentin Fajr kuyruğu: 415 adet `>10`, maksimum `340`.
- Isha kuyruğu: 276 adet `>10`, maksimum `349`.
- Normal-eksen alt kümesi: MAE `0.88801`, P99 `4`, fakat maksimum `141` ve en uzun küme 33 gün.
- 69 kuvvetli şüpheli eşleşme ham `>10` kuyruğunun `%85,44`ünü açıklıyor.
- Bağımsız referansa göre toplam mutlak-dakika artışının yaklaşık `%57,93`ü gerçek Adhan İkindi yolundan geliyor.
- 62° referans İkindi: 710 satır, MAE `18.12958`, 659 adet `>10`.
- Öğle–Akşam orta nokta İkindi: 449 satır, MAE `142.80624`, 430 adet `>10`.

Önceki 2.892 şehirlik referans, `cities.csv` içinde bulunmayan ön-hesaplanmış
`good_city` ve bağımsız `axis_day_mae_geo <= 5` alanlarını kullanıyordu. Bu liste
production sonuçlarını iyi göstermek için içe aktarılmadı; production-only global
kriterle bulunan 21 şehirlik fark raporda açıkça korunuyor.

Önerilen acceptance eşiklerinden MAE ve P99 geçiyor; kuyruk oranı, normal-rejim
maksimumu ve ardışık küme sınırı geçmiyor. Bu nedenle sonuç sınırsız rollout değil,
API fallback ve telemetry içeren kontrollü feature-flag pilotunu destekliyor.
