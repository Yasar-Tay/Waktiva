package com.ybugmobile.waktiva.data.repository

import android.util.Log
import com.ybugmobile.waktiva.BuildConfig
import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.dao.PrayerDao
import com.ybugmobile.waktiva.data.local.dao.PrayerStatusDao
import com.ybugmobile.waktiva.data.local.diyanet.ADAPTIVE_DIYANET_CANDIDATE_VERSION
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.data.remote.AladhanApiService
import com.ybugmobile.waktiva.data.remote.WeatherApiService
import com.ybugmobile.waktiva.data.remote.dto.PrayerDayDto
import com.ybugmobile.waktiva.domain.model.HijriData
import com.ybugmobile.waktiva.domain.model.MoonPhase
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.domain.model.WeatherInfo
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.MoonTimes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.temporal.ChronoField
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PrayerRepositoryImpl @Inject constructor(
    private val aladhanApi: AladhanApiService,
    private val weatherApi: WeatherApiService,
    private val localCalculator: LocalPrayerCalculator,
    private val dao: PrayerDao,
    private val statusDao: PrayerStatusDao,
    private val settingsManager: SettingsManager
) : PrayerRepository {

    private val inFlightRequests: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override fun getPrayerDays(): Flow<List<PrayerDay>> {
        return dao.getAllPrayerDays().map { entities ->
            entities.map { entity -> entity.toPrayerDay() }
        }
    }

    override suspend fun getMoonPhase(dateTime: LocalDateTime): MoonPhase {
        val lat = 47.491143
        val lng = 7.5833342
        val zone = ZoneId.systemDefault()

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

        val phaseProgress = (moonIllumination.phase + 180.0) / 360.0

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

    override suspend fun getWeatherData(
        latitude: Double,
        longitude: Double,
        locationName: String?
    ): Result<WeatherInfo> {
        return try {
            if (BuildConfig.WEATHER_API_KEY.isBlank()) {
                return Result.failure(IllegalStateException("Missing WEATHER_API_KEY"))
            }

            val coordinateQuery = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
            val namedResponse = locationName
                ?.takeIf { it.isNotBlank() }
                ?.let { weatherApi.getCurrentWeather(it) }

            val response = if (
                namedResponse != null && locationsAreNear(
                    latitude,
                    longitude,
                    namedResponse.location.latitude,
                    namedResponse.location.longitude
                )
            ) {
                namedResponse
            } else {
                weatherApi.getCurrentWeather(coordinateQuery)
            }
            val reportedCondition = WeatherCondition.fromWeatherApiCode(
                response.current.condition.code
            )
            val info = WeatherInfo(
                temperature = response.current.temperatureCelsius,
                condition = reportedCondition,
                isDay = response.current.isDay == 1,
                precipitationMillimeters = response.current.precipitationMillimeters,
                cloudCoverPercent = response.current.cloudCoverPercent,
                effectCondition = WeatherCondition.forVisualEffects(
                    reportedCondition = reportedCondition,
                    precipitationMillimeters = response.current.precipitationMillimeters,
                    cloudCoverPercent = response.current.cloudCoverPercent
                )
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun locationsAreNear(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double
    ): Boolean {
        val earthRadiusKm = 6_371.0
        val latitudeDelta = Math.toRadians(secondLatitude - firstLatitude)
        val longitudeDelta = Math.toRadians(secondLongitude - firstLongitude)
        val firstLatitudeRadians = Math.toRadians(firstLatitude)
        val secondLatitudeRadians = Math.toRadians(secondLatitude)
        val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitudeRadians) * cos(secondLatitudeRadians) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        val distanceKm = 2 * earthRadiusKm * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return distanceKm <= 30.0
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
        val cachedParams = settingsManager.getFetchParams(yearMonth)
        val currentParams = if (method == 13) {
            compatibleMethod13FetchParams(cachedParams, latitude, longitude)
                ?: buildFetchParams(latitude, longitude, method, ZoneId.systemDefault())
        } else {
            buildFetchParams(latitude, longitude, method)
        }
        val inFlightKey = "$year/$month/$currentParams"

        if (!inFlightRequests.add(inFlightKey)) return Result.success(Unit)

        return try {
            val expectedDayCount = YearMonth.of(year, month).lengthOfMonth()
            val cachedDayCount = dao.getCountForYearMonth(yearMonth)
            val hasCompleteMonthCache = cachedDayCount >= expectedDayCount

            if (cachedParams == currentParams && hasCompleteMonthCache) {
                return Result.success(Unit)
            }

            dao.deletePrayerDaysForYearMonth(yearMonth)

            val response = aladhanApi.getPrayerTimesCalendar(year, month, latitude, longitude, method)
            if (response.code == 200) {
                val resolvedZoneId = if (method == 13) {
                    resolveApiZoneId(response.data)
                } else {
                    ZoneId.systemDefault()
                }
                var entities = response.data.map { it.toEntity() }
                if (method == 13) {
                    entities = applyAdaptiveDiyanetCorrection(
                        entities = entities,
                        year = year,
                        month = month,
                        latitude = latitude,
                        longitude = longitude,
                        zoneId = resolvedZoneId
                    )
                }
                dao.insertPrayerDays(entities)
                settingsManager.saveFetchParams(
                    yearMonth,
                    buildFetchParams(latitude, longitude, method, resolvedZoneId)
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception("Aladhan API Error"))
            }
        } catch (e: Exception) {
            try {
                val fallbackZoneId = ZoneId.systemDefault()
                val localEntities = localCalculator.calculateMonthlyPrayerTimes(
                    year = year,
                    month = month,
                    latitude = latitude,
                    longitude = longitude,
                    methodId = method,
                    zoneId = fallbackZoneId
                )
                dao.insertPrayerDays(localEntities)
                settingsManager.saveFetchParams(
                    yearMonth,
                    buildFetchParams(latitude, longitude, method, fallbackZoneId)
                )
                Result.success(Unit)
            } catch (localEx: Exception) {
                Result.failure(localEx)
            }
        } finally {
            inFlightRequests.remove(inFlightKey)
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

            val byYearMonth = existing.groupBy { it.date.substring(0, 7) }

            for ((yearMonth, days) in byYearMonth) {
                val (year, month) = yearMonth.split("-").map { it.toInt() }
                val recalculated = localCalculator
                    .calculateMonthlyPrayerTimes(
                        year = year,
                        month = month,
                        latitude = latitude,
                        longitude = longitude,
                        methodId = method,
                        madhabId = madhab,
                        zoneId = ZoneId.systemDefault()
                    )
                    .associateBy { it.date }

                for (day in days) {
                    val updated = recalculated[day.date] ?: continue
                    dao.updateTimings(
                        date = day.date,
                        fajr = updated.fajr,
                        sunrise = updated.sunrise,
                        dhuhr = updated.dhuhr,
                        asr = updated.asr,
                        maghrib = updated.maghrib,
                        isha = updated.isha
                    )
                }

                settingsManager.saveFetchParams(
                    yearMonth,
                    buildFetchParams(latitude, longitude, method, ZoneId.systemDefault())
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun applyAdaptiveDiyanetCorrection(
        entities: List<PrayerDayEntity>,
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId
    ): List<PrayerDayEntity> {
        return try {
            val corrected = localCalculator.calculateMonthlyPrayerTimes(
                year = year,
                month = month,
                latitude = latitude,
                longitude = longitude,
                methodId = 13,
                zoneId = zoneId
            )
            val correctedByDate = corrected.associateBy { it.date }
            entities.map { entity ->
                val fix = correctedByDate[entity.date]
                if (fix != null) entity.copy(fajr = fix.fajr, isha = fix.isha) else entity
            }
        } catch (e: Exception) {
            Log.w("PrayerRepository", "Adaptive Diyanet correction failed", e)
            entities
        }
    }

    private fun resolveApiZoneId(days: List<PrayerDayDto>): ZoneId {
        val fallback = ZoneId.systemDefault()
        days.asSequence()
            .map { it.meta.timezone.trim() }
            .filter { it.isNotEmpty() }
            .forEach { timezoneId ->
                runCatching { ZoneId.of(timezoneId) }
                    .onSuccess { return it }
                    .onFailure {
                        Log.w("PrayerRepository", "Ignoring invalid API timezone: $timezoneId")
                    }
            }
        return fallback
    }

    private fun compatibleMethod13FetchParams(
        cachedParams: String?,
        lat: Double,
        lng: Double
    ): String? {
        val latR = Math.round(lat * 10) / 10.0
        val lngR = Math.round(lng * 10) / 10.0
        val prefix = "$latR|$lngR|13|"
        val suffix = "|$ADAPTIVE_DIYANET_CANDIDATE_VERSION"
        return cachedParams?.takeIf { it.startsWith(prefix) && it.endsWith(suffix) }
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
        return split(" ")[0]
    }

    private fun PrayerDayEntity.toPrayerDay(): PrayerDay {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")

        fun parseTime(timeStr: String): LocalTime {
            return LocalTime.parse(timeStr.split(" ")[0], formatter)
        }

        val hijri = try {
            val parts = hijriDate.split(" ")
            when {
                parts.size >= 4 -> HijriData(
                    day = parts[0].toInt(),
                    monthNumber = parts[1].toInt(),
                    monthEn = parts[2],
                    year = parts[3].toInt()
                )

                parts.size == 3 -> HijriData(
                    day = parts[0].toInt(),
                    monthNumber = 1,
                    monthEn = parts[1],
                    year = parts[2].toInt()
                )

                else -> null
            }
        } catch (_: Exception) {
            null
        }

        return PrayerDay(
            date = LocalDate.parse(date),
            hijriDate = hijri,
            timings = mapOf(
                PrayerType.FAJR to parseTime(fajr),
                PrayerType.SUNRISE to parseTime(sunrise),
                PrayerType.DHUHR to parseTime(dhuhr),
                PrayerType.ASR to parseTime(asr),
                PrayerType.MAGHRIB to parseTime(maghrib),
                PrayerType.ISHA to parseTime(isha)
            ),
            moonPhase = moonPhase,
            moonIllumination = moonIllumination,
            moonrise = moonrise,
            moonset = moonset
        )
    }

    private fun buildFetchParams(
        lat: Double,
        lng: Double,
        method: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val latR = Math.round(lat * 10) / 10.0
        val lngR = Math.round(lng * 10) / 10.0
        return if (method == 13) {
            "$latR|$lngR|$method|${zoneId.id}|$ADAPTIVE_DIYANET_CANDIDATE_VERSION"
        } else {
            "$latR|$lngR|$method"
        }
    }
}
