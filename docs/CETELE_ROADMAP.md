# Çetele (Prayer Log) — Roadmap

Kullanıcının kıldığı ve kaçırdığı namazları takip ettiği Çetele özelliğinin yol haritası.
Faz 1 bu çalışmada tamamlandı; sonraki fazlar öncelik sırasına göredir.

---

## Faz 1 — Temel Çetele ✅ (tamamlandı)

**Veri katmanı**
- [x] `prayer_status` tablosu `prayer_days`'e bağlı `CASCADE` FK'dan kurtarıldı (ay yenilenince / geçmiş silinince kayıtlar gidiyordu). DB v5 → v6 migration, mevcut satırlar korunarak.
- [x] `PrayerLogRepository` (domain) + `PrayerLogRepositoryImpl` (Room), Hilt bağlantısı.
- [x] Çetele başlangıç tarihi DataStore'da (`prayer_log_start`): başlangıçtan önceki günler "kaçırıldı" sayılmaz, "kayıt yok" olur. Eski bir günü geriye dönük işaretlemek başlangıcı kaydırmaz.
- [x] "Namaz geçmişini sil" artık Çeteleye dokunmuyor (sadece önbellekteki vakitleri siler).
- [x] Saf durum mantığı `PrayerLog`: Kılındı / Kaçırıldı / Vakitte / Bekliyor / Kayıt yok; Yatsı ertesi imsaka kadar açık; tam gün serisi.

**Ana ekran**
- [x] Vakti girmiş/geçmiş bir namaz rozetine dokununca "Kıldım" mini ekranı (sheet); kılınmışsa "İşareti kaldır". Güneş ve henüz girmemiş vakitler eskisi gibi vakit kartını gösterir.
- [x] Kılınan rozet: altın parlama ve çift halka (onay mührü yok). İşaretlenince dalga + yaylı büyüme animasyonu; klasik, pirinç ve çelik halkaların hepsinde.

**Çetele ekranı**
- [x] Navbar'da Bağış butonunun yerine Çetele; ikon özel çizim çetele çentikleri (4 çizgi + çapraz 5.).
- [x] İki kartlık sade ekran: **Bugün** (5 dilimli halka, akıllı durum satırı, vakti giren namaz nabız gibi atar, seri + son 7/30 gün oranları kartın altında) ve **Geçmiş** (her günü minik 5 dilimli halka olan ay takvimi; kaydırarak/oklarla 12 ay geriye; seçili günün namazları takvimin altında düzenlenir, varsayılan dün; toplam kaçırılan başlıkta).
- [x] Ayarlar'ın en üstünde "Waktiva'yı Destekle" kartı → Bağış ekranı (Çetele açık da kapalı da olsa orada).
- [x] Çetele isteğe bağlı: Ayarlar'da ve karşılama ekranında "Çeteleyi kullan" anahtarı (varsayılan açık). Kapalıyken alt menüde Çetele yerine Bağış sekmesi durur, rozetler yalnızca vakit kartını açar ve parlama gösterilmez; kayıtlar silinmez.
- [x] 20 dilde metinler; `PrayerLog` ve durum üretimi için birim testler.

---

## Faz 2 — Hatırlatma ve hızlı işaretleme

- [x] **Yatsı sonrası hatırlatma:** Yatsıdan 30/60/90/120 dk sonra (varsayılan 60), günün işaretlenmemiş namazı kaldıysa bildirim; dokununca Çetele açılır, "Hepsini kıldım" aksiyonu hepsini işaretler. Ayarlar'da açılıp kapatılabilir, Çetele kapalıyken hiç kurulmaz.
- [ ] **Bildirimden "Kıldım":** Ezan/sessiz vakit bildirimine "Kıldım" aksiyonu (`BroadcastReceiver` → repository). Uygulamayı açmadan işaretleme.
- [ ] **Vakit çıkmadan hatırlatma:** İşaretlenmemiş namaz için vakit bitimine X dk kala nazik hatırlatma (ayarlanabilir, varsayılan kapalı).
- [ ] **Gece yarısı sonrası Yatsı:** 00:00–İmsak arasında ana ekran yeni güne geçtiği için dünün Yatsı'sı halkadan işaretlenemiyor; o aralıkta dünün Yatsı rozetini göster veya sheet'te "Dünkü Yatsı" seçeneği sun.
- [ ] **Widget entegrasyonu:** Widget'ta günün 5 vakti için kılındı işaretleri; büyük widget'ta dokunarak işaretleme.

## Faz 3 — Kaza takibi

- [ ] **Kaza borcu sayacı:** Kaçırılan namazlar kaza borcu olarak birikir; "Kaza kıldım" ile borçtan düşülür (ayrı tablo: `qaza_log`, vakit bazlı sayaç).
- [ ] **Geçmiş borç girişi:** Kullanıcı Çetele öncesinden kalan kaza borcunu vakit vakit elle girebilsin.
- [ ] **Vitir (vacip) takibi** isteğe bağlı olarak (Hanefi mezhebi seçiliyse varsayılan açık).
- [ ] Kaçırılan ile "kaza edilen"i zaman çizelgesinde ayrı işaretle göster (ör. içi yarım dolu rozet).

## Faz 4 — İstatistik ve motivasyon

- [ ] **Vakit bazlı analiz:** En çok kaçırılan vakit, haftanın günlerine göre dağılım.
- [ ] **Seri kilometre taşları:** 7 / 30 / 100 günlük tam seri kutlaması (abartısız, tek seferlik animasyon).
- [ ] **Cemaatle / vaktinde kılındı** ayrımı (opsiyonel ikinci işaret) ve "ilk vaktinde" oranı.
- [ ] Ramazan'a özel: teravih ve oruç çetelesi (ReligiousDaysProvider ile Ramazan günlerinde otomatik açılır).

## Faz 5 — Veri güvenliği ve taşınabilirlik

- [ ] **Yedekleme:** Çeteleyi JSON olarak dışa/içe aktarma (SAF ile dosya seçimi). Room tablosu Android Auto Backup kurallarına dahil mi, `backup_rules.xml` / `data_extraction_rules.xml` kontrolü.
- [ ] **Çeteleyi sıfırla** ayarı (onaylı) — şu an yalnızca "Namaz geçmişini sil" var ve o Çeteleye dokunmuyor.
- [ ] Room şemasını dışa aktarmayı aç (`exportSchema = true`) ve `MigrationTestHelper` ile 5→6 (ve sonrası) için enstrümantasyon testi yaz.

## Faz 6 — Cila ve erişilebilirlik

- [ ] TalkBack: gün kartlarına "3/5 kılındı" özet açıklaması; rozetlere "iki kez dokun: kıldım olarak işaretle" eylem etiketi.
- [ ] Büyük yazı tipinde zaman çizelgesi başlık hizası (namaz adları taşabiliyor) — telefon üzerinde kontrol.
- [ ] Yatay (landscape) düzende Bugün kartı + çizelgeyi iki sütuna böl.
- [ ] Parlama renginin hava durumu tonlamasıyla uyumu (bulutlu/yağmurlu gökyüzünde altın tonu biraz kıs).

---

### Teknik notlar
- Tablo: `prayer_status(date TEXT, prayerType TEXT, isDone INTEGER, updatedAt INTEGER, PK(date, prayerType))`, FK yok.
- Durum hesabı saf fonksiyonlarda (`domain/model/PrayerLog.kt`, `ui/prayerlog/PrayerLogViewModel.kt#buildPrayerLogState`); UI yalnızca sonucu çizer.
- Geçmiş günlerin vakitleri önbellekten silindiği için geçmiş günlerde "vakit çıktı" varsayılır; bugünün ve dünün Yatsı'sı gerçek vakitlerle hesaplanır.
