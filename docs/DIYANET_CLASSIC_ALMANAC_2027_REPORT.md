# Diyanet klasik almanak ve 2027 doğrulaması

Tarih: 2026-10-03

## Sonuç

Diyanet'in yayımladığı tablolar, klasik saat açısı formülüyle hesaplanıyor. Güneşin
deklinasyonu ve zaman denklemi düşük hassasiyetli almanaktan alınıyor, tarihin 0h UT anında bir
kez hesaplanıyor ve her vakit için yeniden hesaplanmıyor. Üstüne Diyanet'in temkinleri ekleniyor:
İmsak 0, Güneş −7, Öğle +5, İkindi +4, Akşam +7, Yatsı 0 dakika. Bu formülde ayarlanmış bir
katsayı yok. 2026 verisinde bulundu, 2027'de aynen tuttu.

`DiyanetClassicAlmanac` bu formülü uyguluyor. Method 13 artık şu kaynakları kullanıyor:

- **Öğle ve İkindi:** Her gün klasik formülden.
- **İkindi'nin özel durumları:** Güneş öğlede ufkun altındaysa İkindi = Öğle. Güneş ufkun çok az
  üstündeyse formül İkindi'yi Öğle'den önceye düşürür; o zaman da Öğle'nin vakti verilir.
- **Güneş ve Akşam:** Doğuşu ve batışı olan normal günlerde klasik formülden. V14'ün sentetik
  ekseni, yani 19 saatlik uzun gün ve 5 saatlik kısa gün, olduğu gibi kalıyor.
- **İmsak ve Yatsı:** 45°K'nin altında her gün klasik formülden. 45°K'den güneşin her gün doğup
  battığı yere kadar (~65,7°K) Diyanet'in yaz kuralından (`DiyanetHighLatitudeTwilight`, aşağıda).
  Kutup bölgelerinde V14 kalıyor.
- **Güney (≤45°G):** Düz 18°/17° açıları kullanılıyor. Açı oluşmayan gecelerde Diyanet'in
  yazdığı gibi 00:00 veriliyor. Diyanet güneyde kuzeyin yüksek enlem kurallarını kullanmıyor.

## V14 düzeltmesi

64°K'nin üstünde yaz ortasında güneş gece yarısından sonra batıyor. V14 bu günlerde önceki akşamın
batışını alıyordu. Gün uzunluğu negatif ya da birkaç dakika çıkıyor, 22 saatlik yaz günü 5 saatlik
kış ekseninde hesaplanıyordu. Örneğin Oulu, 10 Haziran 2026: İmsak 07:05, Diyanet'te 02:53.

Artık doğuş ve batışın sırası güneşin kendi olaylarından okunuyor. Batış doğuştan önceyse günün
batışı ertesi günün penceresindeki ilk batış olarak alınıyor. Namaz kaydırmaları, doğuştan sadece
birkaç dakika önceki bir batışı doğuştan sonraya taşıyabiliyordu; bu yüzden sıralama kaydırmasız
olaylara bakıyor.

## Veri

- **2027 resmî seti:** 2026 setindeki 3.040 şehrin tamamı, 1.109.600 satır.
  `Diyanet Algorithm/diyanet-global-audit/official_2027.csv`; ayrıntılar aynı klasördeki
  `README_2027.md`'de.
- **Kaynak kontrolü:** Sayfaların Ekim 2026 tablosu `official_2026.csv` ile 94.098/94.240
  şehir-günde birebir aynı. Farklı 142 satır, Diyanet'in sonradan yaptığı değişiklikler:
  Ukrayna'nın üç şehri, Ukhta ve 14 Kanada şehri.
- **Panel:** `app/src/test/resources/diyanet/official_panel_2027/`, 14 şehir.

## Sonuçlar

Güvenilir segment, `v14_kotlin_city_quality.csv`'deki 2026'da sabitlenmiş sınıflandırmaya göre.
Saat dilimi politikası farklı olan şehirler bu tabloya dahil değil (aşağıda). Kapsam 2.896 şehir.
Değerler ortalama mutlak hata (dakika) ve 10 dakikayı aşan satır sayısı.

| Vakit | 2026 önce | 2026 sonra | 2027 önce | 2027 sonra |
|---|---|---|---|---|
| İmsak | 0,848 / 487 | 0,767 / 144 | 0,858 / 499 | 0,777 / 154 |
| Güneş | 0,377 / 5 | 0,095 / 0 | 0,376 / 4 | 0,095 / 0 |
| Öğle | 0,263 / 0 | 0,064 / 0 | 0,263 / 0 | 0,064 / 0 |
| İkindi | 1,318 / 1.149 | 0,072 / 0 | 1,320 / 1.145 | 0,071 / 0 |
| Akşam | 1,125 / 5 | 0,091 / 0 | 1,123 / 4 | 0,091 / 0 |
| Yatsı | 1,450 / 435 | 0,672 / 6 | 1,460 / 451 | 0,683 / 30 |

"Önce" sütunu `e4c3166` (V14.2), "sonra" sütunu bu değişiklik. 2027 hiçbir ayar için
kullanılmadı.

Güney yüksek enlemleri (2027, 6 şehir): İmsak 28,3 → 10,7, Yatsı 20,1 → 10,4. Ushuaia, Río Gallegos,
Dunedin ve Queenstown dakikası dakikasına tutuyor. Kalan hata Las Heras ve Coihaique'den geliyor;
bunlar yer ve saat dilimi eşleşmesi sorunu.

Panel testleri (`DiyanetProductionPanelTest`): İstanbul, Toronto ve Sidney'de iki yılın her
günü, altı vaktin hepsi 1 dakika içinde. 14 şehrin hepsinde altı vakit 15 dakika içinde.

## Algoritma dışında kalanlar

- **Kanada (British Columbia, Alberta; 9059–9073):** Diyanet 2027 tablolarında kışın da yaz
  saatini kullanıyor. Kasım 2026'dan itibaren tablolar 1 saat ileri. Uygulama cihazın saat
  dilimi verisini izliyor.
- **Coihaique (16205):** Şili'nin `America/Coyhaique` dilimiyle Diyanet'in tabloları arasında
  ±60 dakika fark var.
- **Şüpheli eşleşmeler (69 şehir):** Yer ya da saat dilimi uyuşmazlığı. Değişmedi.
- **Yıla ve şehre özgü İmsak sapmaları:** 2026'da Semey, Kazan ve Arsk'ta, 2027'de Laishevo ve
  Naberezhnye Chelny'de yaz İmsak'ı ~130 dakika farklı. Sistematik bir kural değil.

## Yüksek enlem kuralı

Kalan İmsak ve Yatsı hatası yüksek enlemin yaz aylarındaydı. Diyanet'in kuralı ham veriden,
klasik almanak üzerinde yeniden türetildi: 2026'da kuruldu, 2027'de doğrulandı. V14'ün
katsayıları kullanılmadı. Kural tamamen enlem, boylam ve yıldan hesaplanıyor.

1. **Referans gece:** Güneşin hâlâ 18° alçaldığı en kısa gece. 18°'nin kaybolduğu yerlerde
   (~48,56°K'nin kuzeyi) bu, 18°'ye ulaşılmayan ilk gecenin bir önceki gecesi. Güneyde 21 Haziran.
2. **Yaz payı:** İmsak, güneş doğuşundan (−7) gecenin sabit bir payı kadar önce; Yatsı akşamdan
   (+7) sabit bir pay kadar sonra. Pay, referans gecedeki 18° İmsak'ın gece yarısından uzaklığı Δ
   ve o gecenin uzunluğu N ile belirleniyor:
   - İmsak: 0,1866 + 0,379·Δ/N.
   - Yatsı: (1 + 2Δ/N)/6.
   Yaz boyunca, 18°'nin olmadığı ilk geceden son geceye kadar sabit. V14'ün açıklayamadığı
   şehirler arası ±7 dakikalık "testere dişi" buradan geliyor: referans gecenin gün içindeki
   konumu.
3. **Kısa geceler:** Gece ~5,5 saatin altına inince paylar, Öğle + 12 saate (Öğle temkini dahil)
   ortalanmış 5 saatlik bir gecenin payı olarak alınıyor. ~59,5°K'nin kuzeyinde yaz ortasında
   İmsak ve Yatsı bu yüzden neredeyse sabit kalıyor.
4. **Geçişler:** İmsak, 18° saatinin yaz hedefinden en az 20,5 dakika geç kaldığı son günden
   hedefe saat cinsinden düz bir çizgiyle iniyor. Ölçülen düşüş 20,50 ± 0,42 dakika. Yatsı 16°
   ile simetrik. Sonbaharda aynı yoldan dönülüyor; Diyanet burada saatleri gün içi saat olarak
   karşılaştırıyor. Güneşin gece yarısı yerel 00:00'dan önce olan şehirlerde (Kazan, Oskemen,
   Aihui, Semey) 18° İmsak 23:xx'e düşünce hemen doğrudan açıya dönülüyor.
5. **Kayıp sınırı:** 18°'ye son gece çok az ulaşılıyorsa Diyanet çoğu zaman bir önceki geceyi
   referans alıyor. Bunun nedeni muhtemelen koordinat farkı; bu yüzden iki aday pay, olasılıklarına
   göre harmanlanıyor.

Sonuçlar (güvenilir segment, saat dilimi politikası farklı şehirler hariç, 2.896 şehir; değerler
ortalama hata, dakika / 5 dakikayı aşan satır):

| | 2026 önce | 2026 sonra | 2027 önce | 2027 sonra |
|---|---|---|---|---|
| İmsak | 0,767 / 7.174 | 0,243 / 3.814 | 0,777 / 8.141 | 0,257 / 4.465 |
| Yatsı | 0,672 / 9.425 | 0,210 / 2.579 | 0,683 / 10.331 | 0,218 / 3.255 |

10 dakikayı aşan İmsak satırı 2026'da aynı (144 → 142), 2027'de 154 → 326. Artışın kaynakları:
- **Diyanet'teki konumu farklı şehirler:** Arnstorf ve Hovd. Güneş doğuşu ve akşamları da
  kayık.
- **Kaynak verisi hatası:** Syktyvkar'da 27 Ağustos 2027'den itibaren iki ay boyunca İmsak
  "23:59" yayımlanmış.
- **Sınır harmanının yanlış tarafına düşen iki şehir:** Vantaa ve Lillestrøm.

Panel şehirlerinde kutup dışında kalan 9 şehrin hepsinde İmsak ve Yatsı her gün 8 dakika içinde.
Ortalama hata 1,5 dakikanın altında; V14'te aynı şehirlerde 1–2,4.

Türetmede kullanılan Python prototipi ve ayrıntılar `Diyanet Algorithm/diyanet-global-audit/
high_latitude/` klasöründe.

## Araçlar

- `DiyanetYearEvaluationTest`: Üretimi bir resmî yıla karşı satır satır karşılaştırır. Ortam
  değişkenleri `DIYANET_GLOBAL_AUDIT_DIR`, `DIYANET_OFFICIAL_YEAR`, `DIYANET_EVAL_LABEL`.
- `fetch_2027.sh`, `parse_2027.sh`, `analyze_eval.sh`: Denetim klasöründe.
