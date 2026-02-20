package com.ybugmobile.vaktiva.data.repository

import android.util.Log
import com.ybugmobile.vaktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.vaktiva.data.local.dao.PrayerDao
import com.ybugmobile.vaktiva.data.local.dao.PrayerStatusDao
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.data.mapper.toDomain
import com.ybugmobile.vaktiva.data.remote.AladhanApiService
import com.ybugmobile.vaktiva.data.remote.dto.PrayerDayDto
import com.ybugmobile.vaktiva.domain.model.MoonPhase
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
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
import java.util.Date
import javax.inject.Inject

class PrayerRepositoryImpl @Inject constructor(
    private val aladhanApi: AladhanApiService,
    private val localCalculator: LocalPrayerCalculator,
    private val dao: PrayerDao,
    private val statusDao: PrayerStatusDao
) : PrayerRepository {

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

    private fun getLocalHijriDate(date: LocalDate): com.ybugmobile.vaktiva.domain.model.HijriData? {
        return try {
            val hDate = HijrahChronology.INSTANCE.date(date)
            com.ybugmobile.vaktiva.domain.model.HijriData(
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

        return try {
            val response = aladhanApi.getPrayerTimesCalendar(year, month, latitude, longitude, method)
            if (response.code == 200) {
                val entities = response.data.map { it.toEntity() }
                dao.insertPrayerDays(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Aladhan API Error"))
            }
        } catch (e: Exception) {
            try {
                val localEntities = localCalculator.calculateMonthlyPrayerTimes(year, month, latitude, longitude, method)
                dao.insertPrayerDays(localEntities)
                Result.success(Unit)
            } catch (localEx: Exception) {
                Result.failure(localEx)
            }
        }
    }

    override suspend fun getRemainingDaysCount(currentDate: String): Int {
        return dao.getFutureDaysCount(currentDate)
    }

    override suspend fun deletePastData(currentDate: String) {
        dao.deletePastDays(currentDate)
        statusDao.deletePastStatuses(currentDate)
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
