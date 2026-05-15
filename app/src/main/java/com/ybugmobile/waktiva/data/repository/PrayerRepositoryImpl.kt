package com.ybugmobile.waktiva.data.repository

import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.dao.PrayerDao
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
import kotlin.math.abs

class PrayerRepositoryImpl @Inject constructor(
    private val aladhanApi: AladhanApiService,
    private val weatherApi: WeatherApiService,
    private val localCalculator: LocalPrayerCalculator,
    private val dao: PrayerDao,
    private val statusDao: PrayerStatusDao
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

        // Skip entirely if we already have data for this month in the DB.
        val yearMonth = "$year-${month.toString().padStart(2, '0')}"
        if (dao.getCountForYearMonth(yearMonth) > 0) return Result.success(Unit)

        // Deduplicate: if another coroutine is already fetching this month, skip.
        // The in-flight coroutine will write to the DB; the caller's data will be
        // up-to-date once it reads from the Room Flow.
        val key = "$year/$month"
        if (!inFlightRequests.add(key)) return Result.success(Unit)

        return try {
            // For Diyanet (method 13) above 55°N, ask Aladhan to use "1/7 of night"
            // latitude adjustment which better approximates Diyanet's published times
            // than the standard angle-based calculation.
            val latAdjustment = if (method == 13 && abs(latitude) > 55.0) 2 else null
            val response = aladhanApi.getPrayerTimesCalendar(year, month, latitude, longitude, method,
                latitudeAdjustmentMethod = latAdjustment)
            if (response.code == 200) {
                val entities = response.data.map { it.toEntity() }
                val finalEntities = applyHighLatIshaCorrection(entities, year, month, latitude, longitude, method)
                dao.insertPrayerDays(finalEntities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Aladhan API Error"))
            }
        } catch (e: Exception) {
            try {
                // Local fallback already applies the effective angle internally.
                val localEntities = localCalculator.calculateMonthlyPrayerTimes(year, month, latitude, longitude, method)
                dao.insertPrayerDays(localEntities)
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
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * For Diyanet (method 13) at latitudes above 45°N/S, the Aladhan API's Fajr and Isha
     * times drift significantly from Diyanet's published times because Diyanet silently
     * reduces both angles as latitude increases (formulas reverse-engineered from
     * Diyanet's published times for Basel, Zurich, London, Berlin):
     *
     *   Fajr : max(11°, 18° − 0.67 × (|lat| − 45°))
     *   Isha : max(11°, 17° − 0.80 × (|lat| − 45°))
     *
     * Replaces only Fajr and Isha; all other times (including Hijri date) are kept
     * from the more accurate Aladhan API response.
     */
    private fun applyHighLatIshaCorrection(
        entities: List<com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity>,
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
        method: Int
    ): List<com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity> {
        // Only apply angle-based correction for the 45–55°N band.
        // Below 45°: standard angles are accurate. Above 55°: Aladhan's
        // latitudeAdjustmentMethod=2 is passed directly to the API instead.
        if (method != 13 || abs(latitude) <= 45.0 || abs(latitude) > 55.0) return entities

        return try {
            val corrected = localCalculator.calculateMonthlyPrayerTimes(year, month, latitude, longitude, method)
            val correctedByDate = corrected.associateBy { it.date }
            entities.map { entity ->
                val fix = correctedByDate[entity.date]
                if (fix != null) entity.copy(fajr = fix.fajr, isha = fix.isha) else entity
            }
        } catch (e: Exception) {
            // If local recalculation fails for any reason, fall back to raw API values.
            entities
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
}
