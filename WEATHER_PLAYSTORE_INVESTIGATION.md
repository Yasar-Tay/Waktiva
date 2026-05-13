# Weather / Play Store Investigation Notes

## Amaç

Bu doküman, `Waktiva` uygulamasında yaşanan `WeatherSection` / Play Store davranış farkı ve sonrasında ortaya çıkan landscape rotasyon kaynaklı weather timeout probleminin nasıl incelendiğini ve nasıl çözüldüğünü özetler.

Tarih bağlamı:

- İlk ana inceleme: Play Store kurulumunda `WeatherSection` görünmeme problemi
- Sonraki inceleme: landscape geçişinde çift weather isteği ve timeout hataları

## Başlangıçtaki Ana Problem

Kullanıcı gözlemi:

- Uygulama Android Studio üzerinden yüklendiğinde `WeatherSection` görünüyordu.
- Google Play üzerinden yüklenen sürümde `WeatherSection` görünmüyordu.
- Weather’a bağlı bazı görsel davranışlar da etkileniyor gibi görünüyordu.

İlk hipotezler:

- Release / Play Store sürümünde `ProGuard` veya `R8` weather modelini bozuyor olabilir.
- Weather API çağrısı Play sürümünde başarısız oluyor olabilir.
- UI tarafında release’e özgü koşullu render problemi olabilir.

## İncelenen Dosyalar

İnceleme boyunca özellikle şu dosyalar üzerinden gidildi:

- [HomeViewModel.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/HomeViewModel.kt)
- [HomeScreen.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/HomeScreen.kt)
- [HomePortraitContent.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/composables/HomePortraitContent.kt)
- [HomeLandscapeContent.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/composables/HomeLandscapeContent.kt)
- [WeatherSection.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/composables/WeatherSection.kt)
- [PrayerRepositoryImpl.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/data/repository/PrayerRepositoryImpl.kt)
- [WeatherApiService.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/data/remote/WeatherApiService.kt)
- [PermissionUtils.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/utils/PermissionUtils.kt)
- [proguard-rules.pro](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/proguard-rules.pro)
- [build.gradle.kts](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/build.gradle.kts)

## Nasıl Teşhis Ettik

### 1. Release / ProGuard Şüphesi Kontrol Edildi

İlk adımda release yapılandırması incelendi.

Bakılan noktalar:

- `minifyEnabled`
- `shrinkResources`
- Gson / DTO keep kuralları
- `SerializedName` anotasyonları

Sonuç:

- Release yapılandırmasında weather JSON parse’ını doğrudan bozacak net bir eksik görülmedi.
- Yani sorun büyük olasılıkla yalnızca `R8/ProGuard` kaynaklı değildi.

### 2. Gerçek Play Sürümünden Log Toplandı

Kullanıcı, Play / Internal App Sharing sürümünden log sağladı.

Bu loglarda görülenler:

- `Aladhan` istekleri başarılı
- `Open-Meteo` isteği başarılı
- `temperature_2m`, `weather_code`, `is_day` değerleri dönüyor

Örnek gözlem:

- `PrayerRepository getWeatherData success ... temp=15.1 code=3 condition=OVERCAST`
- `HomeViewModel refreshWeather success ...`

Bu çok önemliydi, çünkü:

- Weather verisi aslında geliyor
- Repository bunu parse ediyor
- ViewModel state’e yazıyor

Yani sorun “API başarısız” değildi.

### 3. Diagnostic Log Eklendi

Sorunun veri katmanında mı yoksa UI katmanında mı olduğunu ayırmak için geçici loglar eklendi:

- `PrayerRepositoryImpl.getWeatherData()` içine success / failure logları
- `HomeViewModel.refreshWeather()` içine success / failure logları
- `WeatherSection` render edildiğinde görünmesi için compose logu

Amaç:

- Veri geliyor mu?
- State güncelleniyor mu?
- UI gerçekten compose ediliyor mu?

### 4. Asıl Kırılma Noktası Bulundu

Log sonucu:

- `PrayerRepository` success logu var
- `HomeViewModel` success logu var
- Ama `WeatherSection` render logu yok

Bu şu anlama geldi:

- Weather verisi geliyor
- State güncelleniyor
- Fakat `WeatherSection` hiç compose edilmiyor

## Ana Kök Neden

Hem portrait hem landscape içerikte `WeatherSection` sadece şu mantıkla gösteriliyordu:

- `state.isNetworkAvailable == true` ise göster

Problem:

- `state.isNetworkAvailable`, bazı release / Play senaryolarında yanlış negatif kalabiliyordu
- Buna rağmen gerçek weather verisi çoktan gelmiş olabiliyordu
- Sonuçta UI, elinde veri olduğu halde bile `WeatherSection`’ı göstermiyordu

Başka bir deyişle:

- “Network bayrağı” UI görünürlüğü için tek kriter yapılmıştı
- Ama bu bayrak, gerçek veri varlığından daha güvenilir değildi

## Uygulanan Çözüm

`WeatherSection` görünürlüğü veri tabanlı hale getirildi.

Değişiklik:

- [HomePortraitContent.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/composables/HomePortraitContent.kt)
- [HomeLandscapeContent.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/composables/HomeLandscapeContent.kt)

Yeni yaklaşım:

- Eğer `temperature != null` ise göster
- veya `weatherCondition != WeatherCondition.UNKNOWN` ise göster
- veya `state.isNetworkAvailable == true` ise yine göster

Pratikte kullanılan mantık:

- `hasWeatherData || state.isNetworkAvailable`

Böylece:

- `isNetworkAvailable` yanlış negatif bile olsa
- weather verisi gerçekten state’te varsa
- `WeatherSection` ekranda görünmeye devam ediyor

## Doğrulama

### Manuel APK Testi

Kullanıcı manuel `APK` build alıp telefona kurdu.

Sonuç:

- `WeatherSection` görünüyordu

### Internal App Sharing ile AAB Testi

Kullanıcı `AAB` dosyasını `Internal app sharing` ile yükleyip Play üzerinden kurdu.

Sonuç:

- `WeatherSection` görünüyordu
- Weather refresh logları başarılıydı

Bu sonuç çok önemliydi, çünkü:

- Sorun artık yalnızca lokal APK’de değil
- Play’in dağıttığı AAB akışında da çözülmüş oldu

## İkinci Problem: Landscape Rotasyonunda Timeout

İlk problem çözüldükten sonra kullanıcı yeni bir log paylaştı:

- Telefon yatay konuma alınca peş peşe iki `Open-Meteo` çağrısı başlıyor
- Sonrasında `SocketTimeoutException`
- `PrayerRepository` ve `HomeViewModel` hata logları görülüyor

Kritik log paterni:

- Aynı saniyede iki farklı thread’den iki `Open-Meteo` GET isteği çıkıyor

Bu, tekil bir network sorunu değil; çoğaltılmış tetikleme işaretiydi.

## İkinci Problemin Teşhisi

Bu aşamada `refreshWeather()` çağrısının nerelerden tetiklendiği tarandı.

Bulunan ana tetikleyiciler:

- `init { refreshWeather() }`
- `onResume(...) { refreshWeather() }`
- `refresh() { ... refreshWeather() }`
- saat başı polling

Ayrıca [HomeScreen.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/HomeScreen.kt) içinde lifecycle observer şu şekildeydi:

- `ON_START` gelince `viewModel.onResume(...)`
- `ON_RESUME` gelince `viewModel.onResume(...)`

Bu nedenle orientation change sırasında:

- Activity lifecycle yeniden ilerlerken
- aynı mantık hem `ON_START` hem `ON_RESUME` üzerinden çalışabiliyordu
- bu da `refreshWeather()`’ın kısa aralıkla iki kez tetiklenmesine yol açıyordu

Ek problem:

- `refreshWeather()` içinde “zaten çalışan bir request var mı?” koruması yoktu

Sonuç:

- Aynı anda birden fazla weather isteği başladı
- rotasyon sırasında UI / lifecycle churn ile birleşince bazıları timeout oldu

## İkinci Çözüm

İki seviyede düzeltme yapıldı.

### 1. Lifecycle Observer Sadeleştirildi

Dosya:

- [HomeScreen.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/HomeScreen.kt)

Eski davranış:

- `ON_START` ve `ON_RESUME` ikisinde de `viewModel.onResume(...)`

Yeni davranış:

- Sadece `ON_RESUME` üzerinde `viewModel.onResume(...)`

Böylece orientation / lifecycle geçişlerinde gereksiz çift tetikleme azaltıldı.

### 2. Eşzamanlı Weather Refresh Koruması Eklendi

Dosya:

- [HomeViewModel.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/HomeViewModel.kt)

Yapılan değişiklik:

- `weatherRefreshJob: Job?` alanı eklendi
- `refreshWeather()` başında:
  - eğer aktif bir weather request varsa yeni request başlatılmıyor
  - log ile “skip” ediliyor

Bu koruma sayesinde:

- Aynı anda paralel weather request çıkmıyor
- rotasyon sırasında bir request çalışıyorsa diğeri engelleniyor

## Sonuçlar

### Problem 1 Sonucu

Play / AAB sürümünde görünmeyen `WeatherSection` problemi çözüldü.

Sebep:

- UI görünürlüğü yalnızca `isNetworkAvailable` bayrağına bağlanmıştı

Çözüm:

- Gerçek weather verisi varsa section’ı göster

### Problem 2 Sonucu

Landscape rotasyonda peş peşe oluşan weather request ve timeout sorunu kontrol altına alındı.

Sebep:

- `ON_START` + `ON_RESUME` çift tetikleme
- ve in-flight request guard eksikliği

Çözüm:

- yalnızca `ON_RESUME` tetikleme
- eşzamanlı weather request engeli

## Teknik Olarak Öğrendiklerimiz

Bu inceleme birkaç önemli ders verdi:

1. UI görünürlüğünü yalnızca “genel sağlık” bayraklarına bağlamak risklidir.
2. Gerçek veri varsa UI çoğu zaman veri temelli karar vermelidir.
3. Orientation / lifecycle geçişlerinde `ON_START` ve `ON_RESUME` birlikte kullanılırsa çift refresh oluşabilir.
4. Network tabanlı `refresh` fonksiyonlarında in-flight request guard eklemek güvenlidir.
5. Play / AAB farkı şüphesinde `Internal app sharing` çok etkili bir doğrulama aracıdır.

## Bu Süreçte Yapılan Başlıca Kod Değişiklikleri

- `WeatherSection` görünürlük koşulu veri odaklı hale getirildi
- `HomeScreen` lifecycle observer sadeleştirildi
- `HomeViewModel.refreshWeather()` içine in-flight job koruması eklendi
- Geçici teşhis logları eklendi

İlgili dosyalar:

- [HomePortraitContent.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/composables/HomePortraitContent.kt)
- [HomeLandscapeContent.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/composables/HomeLandscapeContent.kt)
- [HomeScreen.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/HomeScreen.kt)
- [HomeViewModel.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/HomeViewModel.kt)
- [PrayerRepositoryImpl.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/data/repository/PrayerRepositoryImpl.kt)
- [WeatherSection.kt](/C:/Users/yusBug/AndroidStudioProjects/Waktiva/app/src/main/java/com/ybugmobile/waktiva/ui/home/composables/WeatherSection.kt)

## Sonraki Temizlik Önerileri

İnceleme tamamlandıktan sonra şu adımlar uygulanabilir:

1. Teşhis için eklenen geçici `Log.d` / `Log.e` satırlarını sadeleştirmek
2. `PermissionUtils.isNetworkAvailable()` implementasyonunu VPN / farklı transport senaryoları için ayrıca güçlendirmek
3. İstenirse weather refresh için debounce veya daha açık bir refresh policy eklemek

## Kısa Özet

Ana problem, weather verisinin gelmemesi değil; UI’nin weather verisi olduğu halde bunu göstermemesiydi.

Sonraki problem ise landscape geçişinde aynı weather isteğinin birden fazla kez tetiklenmesiydi.

Her iki sorun da:

- log odaklı teşhis,
- lifecycle davranışının izlenmesi,
- ve UI görünürlük mantığının veri merkezli hale getirilmesi

ile çözüldü.
