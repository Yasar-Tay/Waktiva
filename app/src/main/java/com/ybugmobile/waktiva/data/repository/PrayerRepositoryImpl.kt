package com.ybugmobile.waktiva.data.repository

import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.dao.PrayerDao
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.data.local.dao.PrayerStatusDao
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.waktiva.data.mapper.toDomain
import com.ybugmobile.waktiva.data.remote.AladhanApiService
import com.ybugmobile.waktiva.data.remote.WeatherApiService
import com.ybugmobile.waktiva.data.remote.dto.PrayerDayDto
import com.ybugmobile.waktiva.domain.model.MoonPhase
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.model.WeatherInfo
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.MoonTimes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.temporal.ChronoField
import java.util.Collections
import javax.inject.Inject

class PrayerRepositoryImpl @Inject constructor(
    private val aladhanApi: AladhanApiService,
    private val weatherApi: WeatherApiService,
    private val localCalculator: LocalPrayerCalculator,
    private val dao: PrayerDao,
    private val statusDao: PrayerStatusDao,
    private val settingsManager: SettingsManager
) : PrayerRepository {

    // Tracks in-flight fetch keys ("year/month") to prevent duplicate concurrent requests
    // from HomeViewModel, PrayerUpdateWorker, and LocationUpdateWorker all hitting the
    // same endpoint simultaneously.
    private val inFlightRequests: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override fun getPrayerDays(): Flow<List<PrayerDay>> {
        return dao.getAllPrayerDays().map { entities -> 
            entities.map { it.toDomain() } 
        }
    }

    override suspend fun getMoonPhase(dateTime: LocalDateTime): MoonPhase {
        val lat = 47.491143 // Default or from settings
        val lng = 7.5833342
        val zone = ZoneId.systemDefault()
        
        // Use SunCalc for high-precision local calculation
        val moonIllumination = MoonIllumination.compute()
            .on(dateTime)
            .timezone(zone)
            .execute()
            
        val moonTimes = MoonTimes.compute()
            .on(dateTime)
            .at(lat, lng)
            .timezone(zone)
            .execute()
            
        val moonPosition = MoonPosition.compute()
            .on(dateTime)
            .at(lat, lng)
            .timezone(zone)
            .execute()

        val phaseProgress = (moonIllumination.phase + 180.0) / 360.0 // Normalize to 0.0 - 1.0
        
        return MoonPhase(
            illumination = moonIllumination.fraction,
            phaseProgress = phaseProgress,
            phaseName = getPhaseName(phaseProgress),
            hijriDate = getLocalHijriDate(dateTime.toLocalDate()),
            moonrise = moonTimes.rise?.toLocalTime()?.toString(),
            moonset = moonTimes.set?.toLocalTime()?.toString(),
            date = dateTime.toLocalDate(),
            parallacticAngle = moonPosition.parallacticAngle
        )
    }

    override suspend fun getWeatherData(latitude: Double, longitude: Double): Result<WeatherInfo> {
        return try {
            val response = weatherApi.getCurrentWeather(latitude, longitude)
            val info = WeatherInfo(
                temperature = response.current.temperature,
                condition = WeatherCondition.fromWmoCode(response.current.weatherCode),
                isDay = response.current.isDay == 1
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getPhaseName(phaseProgress: Double): String {
        return when {
            phaseProgress < 0.03 -> "New Moon"
            phaseProgress < 0.22 -> "Waxing Crescent"
            phaseProgress < 0.28 -> "First Quarter"
            phaseProgress < 0.47 -> "Waxing Gibbous"
            phaseProgress < 0.53 -> "Full Moon"
            phaseProgress < 0.72 -> "Waning Gibbous"
            phaseProgress < 0.78 -> "Last Quarter"
            else -> "Waning Crescent"
        }
    }

    private fun getLocalHijriDate(date: LocalDate): com.ybugmobile.waktiva.domain.model.HijriData? {
        return try {
            val hDate = HijrahChronology.INSTANCE.date(date)
            com.ybugmobile.waktiva.domain.model.HijriData(
                day = hDate.get(ChronoField.DAY_OF_MONTH),
                monthNumber = hDate.get(ChronoField.MONTH_OF_YEAR),
                monthEn = "",
                year = hDate.get(ChronoField.YEAR)
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun refreshPrayerTimes(
        year: Int,
        month: Int,
        latitude: Double?,
        longitude: Double?,
        method: Int
    ): Result<Unit> {
        if (latitude == null || longitude == null) {
            return Result.failure(Exception("Location is required for fetching prayer times"))
        }

        val yearMonth = "$year-${month.toString().padStart(2, '0')}"

        // Smart cache: skip if we already fetched data for this exact
        // location (±0.1°, ~10 km) and method. This correctly handles:
        //   • Same session re-entry (app re-open)     → cache hit, skip
        //   • Method change before location change    → params differ, re-fetch
        //   • Location change (>50 km threshold)      → params differ, re-fetch
        val currentParams = buildFetchParams(latitude, longitude, method)
        if (settingsManager.getFetchParams(yearMonth) == currentParams) {
            return Result.success(Unit)
        }

        // Params changed — delete any stale data for this month so the
        // new fetch starts with a clean slate (no leftover rows from the
        // old location/method).
        dao.deletePrayerDaysForYearMonth(yearMonth)

        // Deduplicate: if another coroutine is already fetching this month, skip.
        // The in-flight coroutine will write to the DB; the caller's data will be
        // up-to-date once it reads from the Room Flow.
        val key = "$year/$month"
        if (!inFlightRequests.add(key)) return Result.success(Unit)

        return try {
            val response = aladhanApi.getPrayerTimesCalendar(year, month, latitude, longitude, method)
            if (response.code == 200) {
                var entities = response.data.map { it.toEntity() }
                // For Diyanet (method 13) above 43°N the Al-Adhan API returns standard
                // MWL angle times which diverge significantly from Diyanet's published
                // times in summer. Replace Fajr and Isha with the locally-computed
                // fraction-based values that match Diyanet's algorithm exactly.
                if (method == 13) {
                    entities = applyDiyanetFractionCorrection(entities, year, month, latitude, longitude)
                }
                dao.insertPrayerDays(entities)
                settingsManager.saveFetchParams(yearMonth, currentParams)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Aladhan API Error"))
            }
        } catch (e: Exception) {
            try {
                val localEntities = localCalculator.calculateMonthlyPrayerTimes(year, month, latitude, longitude, method)
                dao.insertPrayerDays(localEntities)
                settingsManager.saveFetchParams(yearMonth, currentParams)
                Result.success(Unit)
            } catch (localEx: Exception) {
                Result.failure(localEx)
            }
        } finally {
            inFlightRequests.remove(key)
        }
    }

    override suspend fun getRemainingDaysCount(currentDate: String): Int {
        return dao.getFutureDaysCount(currentDate)
    }

    override suspend fun deletePastData(currentDate: String) {
        dao.deletePastDays(currentDate)
        statusDao.deletePastStatuses(currentDate)
    }

    override suspend fun recalculatePrayerTimesLocally(
        method: Int,
        madhab: Int,
        latitude: Double,
        longitude: Double
    ): Result<Unit> {
        return try {
            val existing = dao.getAllPrayerDaysOnce()
            if (existing.isEmpty()) return Result.success(Unit)

            // Group stored days by year+month so we call the calculator once per month.
            val byYearMonth = existing.groupBy { it.date.substring(0, 7) } // "YYYY-MM"

            for ((yearMonth, days) in byYearMonth) {
                val (year, month) = yearMonth.split("-").map { it.toInt() }
                val recalculated = localCalculator
                    .calculateMonthlyPrayerTimes(year, month, latitude, longitude, method, madhab)
                    .associateBy { it.date }

                for (day in days) {
                    val updated = recalculated[day.date] ?: continue
                    dao.updateTimings(
                        date     = day.date,
                        fajr     = updated.fajr,
                        sunrise  = updated.sunrise,
                        dhuhr    = updated.dhuhr,
                        asr      = updated.asr,
                        maghrib  = updated.maghrib,
                        isha     = updated.isha
                    )
                }

                // Mark this month as up-to-date for the current location+method
                // so refreshPrayerTimes doesn't unnecessarily re-fetch from the network.
                settingsManager.saveFetchParams(yearMonth, buildFetchParams(latitude, longitude, method))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Replaces Al-Adhan's Fajr and Isha with locally-computed Diyanet-accurate times.
     *
     * Diyanet's high-latitude algorithm (reverse-engineered from published data):
     *   Fajr = max(standard_angle_fajr,  sunrise − night × k_fajr)
     *   Isha = min(standard_angle_isha,  maghrib + night × k_isha)
     *
     * The max/min rule produces a seamless seasonal transition — standard MWL angles
     * win in winter (when they work correctly), the fraction wins in summer (when the
     * angle calculation breaks down at high latitudes). All other columns (Hijri date,
     * Dhuhr, Asr, Maghrib, Sunrise) are kept from the more accurate Al-Adhan response.
     *
     * See: diyanet_analysis_report.md — Findings 10–17
     */
    private fun applyDiyanetFractionCorrection(
        entities: List<com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity>,
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double
    ): List<com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity> {
        return try {
            val corrected = localCalculator.calculateMonthlyPrayerTimes(
                year, month, latitude, longitude, methodId = 13
            )
            val correctedByDate = corrected.associateBy { it.date }
            entities.map { entity ->
                val fix = correctedByDate[entity.date]
                if (fix != null) entity.copy(fajr = fix.fajr, isha = fix.isha) else entity
            }
        } catch (e: Exception) {
            entities // fall back to raw API values on any error
        }
    }

    private fun PrayerDayDto.toEntity(): PrayerDayEntity {
        val parts = date.gregorian.date.split("-")
        val formattedDate = "${parts[2]}-${parts[1]}-${parts[0]}"
        return PrayerDayEntity(
            date = formattedDate,
            hijriDate = "${date.hijri.day} ${date.hijri.month.number} ${date.hijri.month.en} ${date.hijri.year}",
            fajr = timings.fajr.cleanTime(),
            sunrise = timings.sunrise.cleanTime(),
            dhuhr = timings.dhuhr.cleanTime(),
            asr = timings.asr.cleanTime(),
            maghrib = timings.maghrib.cleanTime(),
            isha = timings.isha.cleanTime()
        )
    }

    private fun String.cleanTime(): String {
        return this.split(" ")[0]
    }

    /**
     * Builds the fetch-params cache key for a given location and method.
     *
     * Latitude and longitude are rounded to one decimal place (~10 km granularity)
     * so that minor GPS drift (a few hundred metres) does not invalidate the cache,
     * while a true location change of 50+ km (which LocationUpdateWorker detects)
     * always produces a different key and triggers a fresh fetch.
     *
     * Format: "$latRounded|$lngRounded|$method"
     */
    private fun buildFetchParams(lat: Double, lng: Double, method: Int): String {
        val latR = Math.round(lat * 10) / 10.0
        val lngR = Math.round(lng * 10) / 10.0
        return "$latR|$lngR|$method"
    }
}
