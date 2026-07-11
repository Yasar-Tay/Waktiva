# Waktiva Diyanet Uyumlu Uyarlamalı Tam-Yıl Aday Algoritması
## Android/Kotlin Uygulama Şartnamesi

> **Aday sürüm:** `adaptive_full_year_waktiva_v1`  
> **Algoritma kökeni:** `adaptive_full_year_v1` araştırma adayı  
> **Hedef uygulama:** Waktiva Android uygulaması  
> **Kapsam:** `methodId == 13` için çevrimdışı ve ağ sonrası imsak/yatsı düzeltmesi  
> **Hukuki/bilimsel not:** Bu algoritma Diyanet'in yayımlanmamış resmî kaynak kodu değildir. Diyanet/EzanVakti tabloları, yayımlanan kriterler ve astronomik süreklilik koşullarından tersine mühendislikle çıkarılmış bir adaydır.

---

# 1. Amaç

Bu belge, genel amaçlı Python araştırma şartnamesini Waktiva'nın mevcut Android/Kotlin mimarisine uyarlayan bağlayıcı uygulama sözleşmesidir.

Hedefler:

1. Mevcut `LocalPrayerCalculator.calculateMonthlyPrayerTimes(...)` akışını bozmamak.
2. `methodId != 13` davranışını değiştirmemek.
3. `methodId == 13` için şehir adına veya şehir tablosuna bağlı oranları kaldırmak.
4. İmsak ve yatsıyı yıllık astronomiden çıkarılan uyarlamalı modelle hesaplamak.
5. Ağ yanıtı mevcutken yalnız imsak/yatsıyı yerel adayla değiştirmek.
6. Ağ yokken mevcut aylık yerel üretimi sürdürmek.
7. Saat dilimi ve DST hesabını açık, test edilebilir ve geriye uyumlu yapmak.
8. Room şemasını bu sürümde değiştirmemek.

Bu belge, eski şartnamedeki Python dosya haritası, `/mnt/data` yolları, şehir registry'si ve çalıştırma komutlarının yerine geçer. Bilimsel formüller ile doğrulanmış eşikler korunur.

---

# 2. Mevcut Waktiva Yapısı

İlgili üretim bileşenleri:

```text
app/src/main/java/com/ybugmobile/waktiva/data/local/LocalPrayerCalculator.kt
    Aylık çevrimdışı vakit üretir.
    Adhan Java kullanır.
    Şu anda cihaz saat dilimini örtük olarak kullanır.
    methodId=13 için şehir tablosundan en yakın oranı seçer.

app/src/main/java/com/ybugmobile/waktiva/data/repository/PrayerRepositoryImpl.kt
    AlAdhan takvimini indirir.
    methodId=13 için yerel hesaplayıcıdan gelen imsak/yatsıyı ağ sonucuna uygular.
    Ağ hatasında aylık yerel hesaplamaya düşer.

app/src/main/java/com/ybugmobile/waktiva/data/remote/dto/AladhanResponseDto.kt
    PrayerDayDto.meta.timezone alanında IANA saat dilimi taşır.

app/src/main/java/com/ybugmobile/waktiva/data/local/entity/PrayerDayEntity.kt
    Nihai saatleri HH:mm metni olarak saklar.
    Diagnostics alanı içermez.

app/src/test/java/com/ybugmobile/waktiva/LocalPrayerCalculatorTest.kt
    Mevcut aylık hesap ve eski şehir-oranı testlerini içerir.
```

Mevcut bağımlılıklar hedef algoritma için yeterlidir:

```text
com.github.batoulapps:adhan-java
org.shredzone.commons:commons-suncalc
java.time (core library desugaring etkin)
```

Yeni bir çevrimiçi saat dilimi veya astronomi servisi eklenmemelidir.

---

# 3. Bağlayıcı Mimari Kararlar

## 3.1. Koordinat ve saat dilimi birlikte kullanılır

Enlem-boylam astronomik olayları belirler. Yerel saat, UTC ofseti ve DST kuralları için tek başına yeterli değildir.

Bu nedenle hesap girdisi mantıksal olarak şudur:

```kotlin
data class PrayerLocation(
    val latitude: Double,
    val longitude: Double,
    val zoneId: ZoneId,
    val calculationElevationMeters: Double = 0.0
)
```

Koordinattan saat dilimi çıkarmak `LocalPrayerCalculator` sorumluluğu değildir. Sınır bölgeleri, denizler ve siyasi saat dilimi kuralları nedeniyle bunun için ayrı bir coğrafi timezone veri tabanı gerekir.

## 3.2. ZoneId öncelik sırası

Waktiva'nın mevcut veri akışında saat dilimi şu sırayla belirlenir:

```text
1. Başarılı AlAdhan yanıtındaki ilk geçerli meta.timezone
2. Çağıranın açıkça verdiği ZoneId
3. ZoneId.systemDefault()
```

Kurallar:

- Ağ yanıtındaki değer `ZoneId.of(meta.timezone)` ile doğrulanır.
- Geçersiz veya boş değer uygulamayı çökertmez; cihaz saat dilimine düşülür.
- Yerel fallback ve ayarlar ekranından yeniden hesaplama, mevcut uygulama yalnız cihazın konumunu kullandığı için varsayılan olarak `ZoneId.systemDefault()` kullanır.
- Uzak şehir hesaplama özelliği eklenirse o özellik doğru `ZoneId` değerini çağrıya vermek zorundadır.

## 3.3. Geriye uyumlu aylık API

Mevcut çağrıları bozmamak için `zoneId` sona ve varsayılan değerle eklenir:

```kotlin
fun calculateMonthlyPrayerTimes(
    year: Int,
    month: Int,
    latitude: Double,
    longitude: Double,
    methodId: Int,
    madhabId: Int = 0,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<PrayerDayEntity>
```

Testler JVM'in global saat dilimini değiştirmemeli; hedef şehrin `ZoneId` değerini doğrudan vermelidir.

## 3.4. Room şeması değişmez

Bu sürümde `PrayerDayEntity` alanları değişmez. Aday motorun zengin sonucu önce dahili modelde tutulur, `PrayerDayEntity` oluşturulurken yalnız yuvarlanmış `fajr` ve `isha` alanları aktarılır.

Diagnostics:

- debug log veya test erişimli dahili sonuçta korunur,
- kalıcı Room migration gerektirmez,
- kullanıcı arayüzüne eklenmez,
- hassas konum tam koordinatları üretim loguna yazılmaz.

---

# 4. Waktiva Profil Politikası

Eski araştırma şartnamesindeki şehir/ülke profil registry'si Waktiva girdilerinde mevcut değildir. Bu sürümde profil çözümü, mevcut davranışla uyumlu ve açıkça sürümlenmiş bir uygulama politikasıdır.

```text
methodId != 13
    Mevcut Adhan CalculationMethod davranışı değişmez.

methodId == 13 and abs(latitude) <= 43.0
    profile_id = waktiva_diyanet_direct_18_17_v1
    fajr_angle = 18°
    isha_angle = 17°
    high_latitude = false
    regime = DIRECT_ANGLES

methodId == 13 and abs(latitude) > 43.0
    profile_id = waktiva_diyanet_adaptive_18_16_v1
    fajr_angle = 18°
    isha_angle = 16°
    high_latitude = true
    yıllık rejim sınıflandırması çalışır
```

`43.0°` sınırı Diyanet'in kanıtlanmış evrensel kriteri olarak sunulamaz. Waktiva'nın mevcut yüksek-enlem dalını koruyan uyumluluk politikasıdır. Gelecekte ülke/bölge verisi veya kullanıcı profil seçimi eklenirse resolver ayrı bir arayüz üzerinden değiştirilebilir; astronomi motoruna şehir adı eklenmemelidir.

Sabit Diyanet dakika ayarları:

```text
sunrise = -7 dakika
dhuhr   = +5 dakika
asr     = +4 dakika
maghrib = +7 dakika
```

Uyarlamalı motor yalnız imsak ve yatsıyı sahiplenir. Öğle, ikindi, güneş ve akşam mevcut Adhan/API akışından gelmeye devam eder.

---

# 5. Hedef Kod Yapısı

Önerilen sorumluluk ayrımı:

```text
data/local/diyanet/AdaptiveDiyanetCalculator.kt
    Günlük imsak/yatsı ve diagnostics üretir.

data/local/diyanet/DiyanetAstronomyKernel.kt
    Sunrise, sunset, true solar elevation ve robust twilight root üretir.

data/local/diyanet/DiyanetModels.kt
    Profile, RawEvents, AnnualSeason, Result ve Diagnostics modellerini içerir.

data/local/LocalPrayerCalculator.kt
    Mevcut aylık API'yi korur.
    methodId=13 için AdaptiveDiyanetCalculator sonucunu entegre eder.

data/repository/PrayerRepositoryImpl.kt
    Ağ timezone değerini çözer.
    Ağ sonucunda yalnız fajr/isha override eder.
```

Dosyalar birleştirilebilir; ancak astronomi, yıllık profil ve entegrasyon sorumlulukları sınıf sınırlarında görünür kalmalıdır.

Temel arayüz:

```kotlin
internal interface DiyanetCandidateCalculator {
    fun calculate(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetCalculationResult
}
```

Sonuç modeli:

```kotlin
internal data class DiyanetCalculationResult(
    val fajr: ZonedDateTime?,
    val isha: ZonedDateTime?,
    val regime: DiyanetRegime,
    val confidence: DiyanetConfidence,
    val diagnostics: DiyanetDiagnostics
)
```

---

# 6. Zaman ve DST Kuralları

1. Takvim günü `LocalDate`, bölge `ZoneId`, mutlak zaman `Instant` ile temsil edilir.
2. Süre çıkarma ve karşılaştırma `Instant` üzerinde yapılır.
3. Yerel tarihe sabit `24 saat` eklenmez; `date.plusDays(1).atStartOfDay(zoneId)` kullanılır.
4. Sabah tarama sınırları ayrı oluşturulur:
   ```text
   date 00:00 local -> date 12:00 local
   ```
5. Akşam tarama sınırları ayrı oluşturulur:
   ```text
   date 12:00 local -> nextDate 00:00 local
   ```
6. Sınırlar `Instant` değerlerine çevrildikten sonra taranır.
7. Akşamdan sonraki sabahı açmak için takvim günü mantığı kullanılır; yerel saate `+24h` uygulanmaz.
8. Nihai çıktı hedef takvim gününün `ZoneId` içindeki yerel saatidir.
9. `SimpleDateFormat` veya `Calendar` iç hesap için kullanılmamalıdır. Sadece geriye dönük formatlama gerekiyorsa açık `TimeZone` ile kullanılabilir; tercih `DateTimeFormatter` olmalıdır.

---

# 7. Astronomi Çekirdeği

## 7.1. Gerekli olaylar

Her gün için:

```text
astronomik sunrise
astronomik sunset
sabah -fajrAngle yükselen kesişimi
akşam -ishaAngle alçalan kesişimi
```

hesaplanır.

`calculationElevationMeters = 0.0` sabiti korunur. Fiziksel rakım varsa metadata olabilir, astronomi kernel'ine bu aday sürümde verilmez.

Sunrise/sunset commons-suncalc standart güneş olayından alınır. Twilight kökü `SunPosition.getTrueAltitude()` veya aynı anlama gelen refraksiyonsuz gerçek güneş yüksekliğiyle çözülür.

## 7.2. Robust twilight root

Kök fonksiyonu:

```text
f(t) = trueSolarElevation(t) + angle
```

Sabah işaret geçişi:

```text
previous <= 0 and current > 0
```

Akşam işaret geçişi:

```text
previous > 0 and current <= 0
```

Tarama adımı:

```text
5 dakika
```

Kesişim bulunduğunda:

```text
28 binary-search iterasyonu
```

Kesişim yoksa `null` döner. Rastgele sabit saat üretilmez.

## 7.3. Ham olay cache anahtarı

En az:

```text
candidate_version
latitude (normalize edilmiş kesinlik)
longitude (normalize edilmiş kesinlik)
zone_id
date
calculation_elevation
fajr_angle
isha_angle
root_step
root_iterations
astronomy_kernel_version
```

bulunmalıdır.

---

# 8. Diyanet Namaz Ekseni

Astronomik sunrise/sunset doğrudan namaz ekseni olarak kullanılmaz:

```text
prayerSunrise = astronomicalSunrise - 7 dakika
prayerMaghrib = astronomicalSunset + 7 dakika
```

Bu eksen şunların tamamında kullanılır:

- şer'î gece,
- örfî gece,
- gündönümü üçte-bir hesabı,
- missing-fajr summer ratio,
- günlük tahminî imsak/yatsı,
- minimum gece kontrolü.

Mevcut koddaki gibi HH:mm metninden ayarları geri çevirip astronomik saat üretme yöntemi kaldırılmalıdır. Aday motor ham astronomik olayları doğrudan kullanır.

---

# 9. Nihai Yuvarlama

Ara hesaplarda yuvarlama yapılmaz. Yalnız nihai `fajr` ve `isha`:

```text
value + 30 saniye
second = 0
nano = 0
```

ile en yakın dakikaya yuvarlanır. Sonra `HH:mm` formatlanır.

---

# 10. Yıllık Rejim Seçimi

Yaz ankrajı:

```text
latitude >= 0 -> ilgili 21 Haziran
latitude < 0 and target.month <= 6 -> önceki 21 Aralık
latitude < 0 and target.month > 6 -> ilgili 21 Aralık
```

Doğrudan profil için rejim her zaman:

```text
DIRECT_ANGLES
```

Uyarlamalı profil için ankraj çevresinde:

```text
anchor - 190 gün ... anchor + 190 gün
```

gerçek `-18°` sabah kökü taranır.

```text
dominantMissingRun = en uzun kesintisiz fajr == null serisi
```

Karar:

```text
run length >= 10 gün
    ROBUST_MISSING_FAJR_FULL_YEAR

aksi halde
    SOLSTICE_ONE_THIRD_GRADUAL
```

Dağınık veya tek günlük null olaylar missing-fajr rejimini başlatmaz.

---

# 11. Rejim 1: Direct Angles

```text
fajr = sabah -fajrAngle yükselen kökü
isha = akşam -ishaAngle alçalan kökü
```

Uyarlamalı profilin doğrudan kısımlarında da aynı robust solver kullanılır.

Direct olay yoksa sonuç uydurulmaz. Yüksek-enlem modelinde estimate mevcutsa ilgili geçiş/estimate kullanılabilir; düşük-enlem doğrudan profilde `null + LOW/UNSUPPORTED` üretilir ve entegrasyon katmanı kontrollü fallback uygular.

---

# 12. Rejim 2: Solstice One-Third Gradual

Bu rejim yüksek-enlem profili etkin, fakat en az 10 günlük dominant missing-fajr serisi yoksa kullanılır.

Ankrajda:

```text
P_sunrise = anchor sunrise - 7 dakika
P_maghrib = anchor sunset + 7 dakika
next_true_fajr = next day real -18° fajr
```

`next_true_fajr`, `P_maghrib` sonrasına timeline üzerinde açılır.

```text
shariNight = next_true_fajr - P_maghrib
oneThird = shariNight / 3
estimatedIsha = P_maghrib + oneThird
estimatedFajr = P_sunrise - oneThird * fajrAngle / ishaAngle
```

Geçiş kenarı:

```text
transition_margin = 20 dakika
fajrEdge = estimatedFajr + 20 dakika
ishaEdge = estimatedIsha - 20 dakika
```

İlkbahar kenarı ile ankraj ve ankraj ile sonbahar kenarı arasında takvim günü bazında doğrusal interpolasyon uygulanır. Direct sınırlar korunur.

---

# 13. Rejim 3: Missing-Fajr Full-Year

## 13.1. Sezon ve summer ratio

```text
firstMissing = dominant run başlangıcı
lastMissing = dominant run sonu
springReferenceDay = firstMissing - 1 gün
```

Referans sabahı:

```text
previousPrayerMaghrib = sunset(referenceDay - 1) + 7 dakika
currentPrayerSunrise = sunrise(referenceDay) - 7 dakika
trueFajr = referenceDay gerçek -18° fajr
```

Sabah olayları önceki akşamdan sonraya timeline üzerinde açılır.

```text
shari = trueFajr - previousPrayerMaghrib
urfi = currentPrayerSunrise - previousPrayerMaghrib
summerRatio = (shari / 3) / urfi
```

Kabul:

```text
0 < summerRatio < 1
ratio_source = last_true_fajr_before_dominant_missing_run
```

Şehir oranı, en yakın şehir, varsayılan oran veya eski `DIYANET_CITY_FRACTIONS` tablosu kullanılmaz.

## 13.2. Otomatik minimum gece

Ankrajın `-10 ... +10` günleri için ham namaz geceleri hesaplanır.

```text
minimum raw night < 295 dakika -> minimumNightMinutes = 300
aksi halde -> minimumNightMinutes = 0
```

Politika etiketi:

```text
auto_295
```

Seçim şehir adına göre yapılmaz.

## 13.3. Günlük estimate

Hedef gün `D` için:

```text
previousMaghrib = sunset(D-1) + 7 dakika
currentSunrise = sunrise(D) - 7 dakika
currentMaghrib = sunset(D) + 7 dakika
nextSunrise = sunrise(D+1) - 7 dakika
```

Sabahlar ilgili akşamlardan sonraya timeline üzerinde açılır.

```text
previousUrfiRaw = currentSunrise - previousMaghrib
currentUrfiRaw = nextSunrise - currentMaghrib

previousUrfiEffective = max(previousUrfiRaw, minimumNightMinutes)
currentUrfiEffective = max(currentUrfiRaw, minimumNightMinutes)

ishaDuration = currentUrfiEffective * summerRatio
fajrDuration = previousUrfiEffective * summerRatio * fajrAngle / ishaAngle

estimatedFajr = currentSunrise - fajrDuration
estimatedIsha = currentMaghrib + ishaDuration
```

## 13.4. Dört geçiş omzu

Standart kısa-gece ailesi:

```text
spring fajr = 16
spring isha = 10
autumn fajr = 12
autumn isha = 18
```

Beş saat ailesi:

```text
spring fajr = 20
spring isha = 14
autumn fajr = 14
autumn isha = 20
```

```text
minimumNightMinutes >= 300 -> five-hour family
aksi halde -> standard family
```

Omuzlar tek bir simetrik sabitte birleştirilmez.

## 13.5. Geçiş sınırları

```text
fajrDiff = estimatedFajr - directFajr
ishaDiff = directIsha - estimatedIsha
```

İlkbaharda `firstMissing` öncesindeki en çok 90 gün kronolojik taranır. İlgili farkın ilk kez ilgili spring margin değerine ulaştığı gün başlangıçtır.

Sonbaharda `lastMissing + 1` sonrasındaki en çok 90 gün taranır. Farkın ilgili autumn margin değerine veya altına düştüğü gün bitiştir. İlk sağlam gün zaten sınır altındaysa geçiş hemen bitebilir.

İmsak ve yatsı sınırları ayrı bulunur.

## 13.6. Geçiş formülü

Missing döneminde:

```text
firstMissing <= target <= lastMissing -> estimated
```

İlkbaharda:

```text
t = (target - transitionStart) / (firstMissing - transitionStart)
residual = springMargin * (1 - t)
fajrCandidate = estimatedFajr - residual
ishaCandidate = estimatedIsha + residual
finalFajr = max(directFajr, fajrCandidate)
finalIsha = min(directIsha, ishaCandidate)
```

Sonbaharda:

```text
firstDirect = lastMissing + 1 gün
t = (target - firstDirect) / (transitionEnd - firstDirect)
residual = autumnMargin * t
fajrCandidate = estimatedFajr - residual
ishaCandidate = estimatedIsha + residual
finalFajr = max(directFajr, fajrCandidate)
finalIsha = min(directIsha, ishaCandidate)
```

Geçiş dışında direct kullanılır. Eğri `linear` kalır.

---

# 14. Repository Entegrasyonu

## 14.1. Başarılı ağ yanıtı

`PrayerRepositoryImpl.refreshPrayerTimes(...)`:

1. AlAdhan yanıtını mevcut şekilde `PrayerDayEntity` listesine çevirir.
2. `method != 13` ise listeyi değiştirmez.
3. `method == 13` ise ilk geçerli `response.data[*].meta.timezone` değerini çözer.
4. Aynı ayı `LocalPrayerCalculator` ile bu `ZoneId` üzerinden hesaplar.
5. Tarihe göre eşleyip yalnız `fajr` ve `isha` alanlarını değiştirir.
6. Yerel aday hata verirse ham API değerlerini korur ve hata diagnostics/log üretir.

Fonksiyon adı eski algoritmayı taşımamalıdır:

```text
applyDiyanetFractionCorrection -> applyAdaptiveDiyanetCorrection
```

## 14.2. Ağ hatası

Mevcut fallback korunur:

```kotlin
localCalculator.calculateMonthlyPrayerTimes(
    year = year,
    month = month,
    latitude = latitude,
    longitude = longitude,
    methodId = method,
    zoneId = ZoneId.systemDefault()
)
```

## 14.3. Yerel yeniden hesaplama

`recalculatePrayerTimesLocally(...)` mevcut imzasını korur ve cihazın güncel `ZoneId.systemDefault()` değerini aylık çağrıya açıkça geçirir.

## 14.4. Fetch cache anahtarı

Mevcut anahtar:

```text
roundedLatitude|roundedLongitude|method
```

`methodId == 13` için şu bilgiler de eklenmelidir:

```text
zoneId.id
candidate_version
```

Örnek:

```text
52.5|13.4|13|Europe/Berlin|adaptive_full_year_waktiva_v1
```

Böylece cihaz saat dilimi veya algoritma sürümü değiştiğinde eski ay yanlışlıkla güncel kabul edilmez.

`inFlightRequests` anahtarı da en az yıl/ay ve fetch parametrelerini ayırt etmelidir; aynı ay için farklı konum çağrılarının birbirini yanlışlıkla bastırmasına izin verilmemelidir.

---

# 15. Hata ve Fallback Politikası

```text
Geçersiz ZoneId
    systemDefault kullan, diagnostics işaretle.

Twilight root yok, fakat doğrulanmış yüksek-enlem estimate var
    estimate/geçiş sonucunu kullan.

Direct profilde gerekli root yok
    aday sonucu null/unsupported üret.

Sunrise veya sunset yok
    polar_unsupported üret; sabit saat uydurma.

Geçersiz summer ratio
    aday hesabı başarısız; ağ akışında API değerini koru.

Yerel tam fallback sırasında aday Fajr/Isha üretilemiyor
    mevcut Adhan sonucu LOW confidence fallback olarak korunabilir,
    ancak bu durum Diyanet-adaptive sonucu gibi etiketlenemez.
```

Tam polar destek bu sürümün kapsamı dışındadır.

---

# 16. Diagnostics

Yüksek-enlem sonuçta en az:

```text
candidate_version
profile_id
zone_id
zone_source
calculation_elevation_m
anchor
regime
adaptive_shoulder_regime
first_missing
last_missing
spring_reference_day
summer_ratio
ratio_source
minimum_night_minutes
previous_urfi_raw_minutes
current_urfi_raw_minutes
previous_urfi_effective_minutes
current_urfi_effective_minutes
spring_fajr_margin_minutes
spring_isha_margin_minutes
autumn_fajr_margin_minutes
autumn_isha_margin_minutes
fajr_transition_start
fajr_transition_end
isha_transition_start
isha_transition_end
phase
transition_curve
direct_fajr
direct_isha
estimated_fajr
estimated_isha
fallback_reason
```

bulunmalıdır.

Confidence önerisi:

```text
HIGH
    Direct olaylar veya doğrulanmış annual model eksiksiz.

MEDIUM
    Waktiva koordinat tabanlı profil politikası kullanıldı.

LOW
    ZoneId fallback, olay fallback veya doğrulanmamış güney yüksek-enlem durumu.

UNSUPPORTED
    Sunrise/sunset yok veya polar durum.
```

---

# 17. Cache Stratejisi

Önerilen katmanlar:

```text
RawEventCache
    location + zone + profile + date + kernel parameters

AnnualProfileCache
    location + zone + profile + anchor year + all candidate parameters

DailyResultCache (opsiyonel)
    annual profile key + date + candidate version
```

Yıllık profil aynı ay içindeki her gün için yeniden çıkarılmamalıdır.

Cache thread-safe olmalı, sınırsız büyümemeli ve bütün algoritma sabitleriyle sürümlenmelidir. En az şu değişiklikler invalidation üretir:

```text
angles
ZoneId
kernel version
sunrise/maghrib adjustments
minimum-night policy
shoulder margins
transition curve
root scan step/iterations
candidate version
```

---

# 18. Test Gereksinimleri

## 18.1. Mevcut davranış

- Artık yıl ayı doğru gün sayısı üretir.
- Tüm saat alanları `HH:mm` biçimindedir.
- `methodId != 13` sonuçları bu değişiklikten etkilenmez.
- Hanafi seçimi ikindi sonucunu değiştirmeye devam eder.

## 18.2. ZoneId

- Testler `TimeZone.setDefault(...)` kullanmaz.
- Toronto `America/Toronto`, Helsinki `Europe/Helsinki` ile açıkça hesaplanır.
- Aynı koordinat farklı `ZoneId` ile çağrıldığında çıktı ofset farkını doğru yansıtır.
- 29 Mart 2026 Stockholm DST günü kökleri doğru ofsettedir.
- Ağ `meta.timezone` değeri yerel override çağrısına aktarılır.
- Geçersiz API timezone cihaz bölgesine güvenli düşer.

## 18.3. Astronomi ve yıllık model

- Sabah kökü öğleden önce, akşam kökü öğleden sonra bulunur.
- Tek günlük null dominant seri seçmez.
- En uzun kesintisiz seri doğru seçilir.
- 10 günden kısa seri missing-fajr rejimini başlatmaz.
- `0 < summerRatio < 1`.
- Referans gün `firstMissing - 1` olur.
- Şehir oranı kullanılmaz.
- 2026 çıkarılan oranlar yaklaşık:
  ```text
  Stockholm 0.184684
  Oslo      0.187689
  Helsinki  0.176248
  ```
- Tolerans:
  ```text
  abs(actual - expected) < 0.002
  ```
- Stockholm ve Oslo floor `0`, Helsinki floor `300` üretir.
- Tam missing dönemde estimate kullanılır.
- Geçiş dışında direct kullanılır.
- `finalFajr >= directFajr` ve `finalIsha <= directIsha` clamp'leri korunur.

## 18.4. Yapısal tam-yıl testleri

Seçili her şehir için 365/366 gün:

```text
eksik sonuç = 0 (polar kapsam dışı)
fajr < prayerSunrise
isha > prayerMaghrib
gün sırası ve tarih eşleşmesi doğru
DST normalize edildikten sonra yapay büyük sıçrama yok
yalnız nihai dakikada yuvarlama
```

En az İstanbul, Basel, Stockholm, Oslo, Helsinki, Toronto ve Sydney kapsanmalıdır.

## 18.5. Eski testlerin dönüşümü

Aşağıdaki test yaklaşımı kaldırılır:

```text
şehir katsayısı kalibrasyonu
en yakın şehir oranı beklentisi
global JVM timezone değiştirme
HH:mm üzerinden astronomik gece türetme
```

Referans Diyanet saat testleri korunabilir; ancak aday algoritmanın beklenen rejimi ve açık `ZoneId` ile çalışmalıdır.

---

# 19. Kabul Kriterleri

Kod tamamlanmış sayılmadan önce:

```text
[ ] methodId != 13 regresyonu yok
[ ] Aylık API geriye uyumlu
[ ] ZoneId açıkça taşınıyor
[ ] API meta.timezone kullanılıyor
[ ] Koordinattan timezone türetilmiyor
[ ] calculation elevation = 0
[ ] Sunrise -7 / Maghrib +7 namaz ekseni uygulanıyor
[ ] Robust twilight root uygulanıyor
[ ] Dominant missing run >= 10 kuralı uygulanıyor
[ ] Summer ratio yıllık astronomiden çıkarılıyor
[ ] Şehir oranı tablosu kaldırılmış
[ ] Auto-295 / floor-300 uygulanıyor
[ ] Dört omuz ayrı korunuyor
[ ] Direct clamp uygulanıyor
[ ] Tek final yuvarlama var
[ ] Polar durumda saat uydurulmuyor
[ ] Cache ZoneId ve candidate version içeriyor
[ ] Unit testler geçiyor
[ ] Tam-yıl invariant testleri geçiyor
```

Upstream araştırma benchmark'ları erişilebiliyorsa ek regresyon hedefleri:

```text
mevsim örneklemi MAE <= 1 dakika
kör tarih MAE <= 1 dakika
maksimum gözlenen hata <= 4 dakika
```

Bu veri setleri Waktiva repository'sinde yoksa build'in zorunlu parçası gibi gösterilmemeli; ayrı doğrulama girdisi olarak belgelenmelidir.

---

# 20. Uygulama Sırası

1. `PrayerLocation`, profil, sonuç ve diagnostics modellerini ekle.
2. SunCalc tabanlı ham olay ve robust twilight kernel'ini ekle.
3. Yıllık rejim, ratio, floor ve geçiş hesaplarını ekle.
4. Eski `DIYANET_CITY_FRACTIONS` ve `nearestDiyanetFractions` akışını kaldır.
5. `LocalPrayerCalculator` imzasına sona varsayılan `zoneId` ekle.
6. `methodId=13` imsak/yatsı sonucunu yeni motordan al.
7. Repository ağ yolunda `meta.timezone` değerini geçir.
8. Fetch cache anahtarını ZoneId ve candidate version ile sürümle.
9. Eski şehir-oranı testlerini şartname testlerine dönüştür.
10. Unit testleri ve tam-yıl invariant testlerini çalıştır.
11. `DIYANET_IMSAK_YATSI_CALCULATION.md` belgesini yeni üretim davranışıyla güncelle.

Her adımda uygulama derlenebilir kalmalıdır. Room migration bu işin parçası değildir.

---

# 21. Bilinen Sınırlar

- Profil seçiminin `43°` uyumluluk sınırı Diyanet'in resmî evrensel kuralı değildir.
- Stockholm sonbahar yatsı geçişinde upstream aday bazı günlerde yaklaşık 4 dakika erken kalabilir.
- Güney yüksek enlemleri yeterince dış doğrulanmamıştır.
- Polar sunrise/sunset yokluğu desteklenmez.
- İkinci bağımsız tam yıl ve yeni şehir holdout doğrulaması gereklidir.
- Düşük benchmark hatası, Diyanet'in kesin iç algoritmasının bulunduğu anlamına gelmez.

---

# 22. Sonuç

Waktiva için doğru veri sözleşmesi yalnız enlem-boylam değildir:

```text
astronomi girdisi = enlem + boylam
yerel saat girdisi = ZoneId
```

Mevcut uygulama akışında `ZoneId.systemDefault()` geçerli ve geriye uyumlu fallback'tir; başarılı ağ yanıtında `meta.timezone` daha doğru kaynaktır. Hesaplayıcı koordinattan saat dilimi tahmin etmez.

`adaptive_full_year_waktiva_v1`, şehir özel oranları kaldırır; oranı yıllık astronomiden çıkarır; mevcut aylık hesaplayıcı, repository ve Room modeline kontrollü biçimde entegre olur.
