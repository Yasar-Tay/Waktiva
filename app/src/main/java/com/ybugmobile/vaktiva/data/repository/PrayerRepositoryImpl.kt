package com.ybugmobile.vaktiva.data.repository

import com.ybugmobile.vaktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.vaktiva.data.local.dao.PrayerDao
import com.ybugmobile.vaktiva.data.local.dao.PrayerStatusDao
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.data.mapper.toDomain
import com.ybugmobile.vaktiva.data.remote.AladhanApiService
import com.ybugmobile.vaktiva.data.remote.UmmahApiService
import com.ybugmobile.vaktiva.data.remote.dto.PrayerDayDto
import com.ybugmobile.vaktiva.data.remote.dto.UmmahPrayerDayDto
import com.ybugmobile.vaktiva.domain.model.MoonPhase
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.temporal.ChronoField
import java.util.Date
import javax.inject.Inject

class PrayerRepositoryImpl @Inject constructor(
    private val aladhanApi: AladhanApiService,
    private val ummahApi: UmmahApiService,
    private val localCalculator: LocalPrayerCalculator,
    private val dao: PrayerDao,
    private val statusDao: PrayerStatusDao
) : PrayerRepository {

    override fun getPrayerDays(): Flow<List<PrayerDay>> {
        return dao.getAllPrayerDays().map { entities -> 
            entities.map { it.toDomain() } 
        }
    }

    override suspend fun getMoonPhase(date: LocalDate): MoonPhase {
        val dateInMs = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()).time
        val lunarMonth = 2551442877L // 29.53059 days in ms
        val newMoonReference = 947163600000L // New Moon on Jan 6, 2000
        
        val diff = dateInMs - newMoonReference
        val phaseProgress = (diff % lunarMonth).toDouble() / lunarMonth
        
        // Illumination approximation: 0.0 (New Moon) -> 1.0 (Full Moon) -> 0.0 (New Moon)
        val illumination = if (phaseProgress < 0.5) {
            phaseProgress * 2
        } else {
            (1.0 - phaseProgress) * 2
        }

        val phaseName = when {
            phaseProgress < 0.03 -> "New Moon"
            phaseProgress < 0.22 -> "Waxing Crescent"
            phaseProgress < 0.28 -> "First Quarter"
            phaseProgress < 0.47 -> "Waxing Gibbous"
            phaseProgress < 0.53 -> "Full Moon"
            phaseProgress < 0.72 -> "Waning Gibbous"
            phaseProgress < 0.78 -> "Last Quarter"
            else -> "Waning Crescent"
        }

        // Calculate Hijri Date locally
        val hijriDateStr = try {
            val hDate = HijrahChronology.INSTANCE.date(date)
            "${hDate.get(ChronoField.DAY_OF_MONTH)} ${hDate.get(ChronoField.MONTH_OF_YEAR)} ${hDate.get(ChronoField.YEAR)}"
        } catch (e: Exception) {
            ""
        }

        return MoonPhase(
            illumination = illumination,
            phaseProgress = phaseProgress,
            phaseName = phaseName,
            hijriDate = hijriDateStr,
            moonrise = null,
            moonset = null,
            date = date
        )
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

        val aladhanResult = try {
            val response = aladhanApi.getPrayerTimesCalendar(year, month, latitude, longitude, method)
            if (response.code == 200) {
                val entities = response.data.map { it.toEntity() }
                dao.insertPrayerDays(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Aladhan API Error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

        if (aladhanResult.isSuccess) return aladhanResult

        val ummahResult = try {
            val response = ummahApi.getPrayerTimesCalendar(latitude, longitude, year, month, method)
            if (response.data != null) {
                val entities = response.data.map { it.toEntity() }
                dao.insertPrayerDays(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("UmmahAPI Error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

        if (ummahResult.isSuccess) return ummahResult

        return try {
            val localEntities = localCalculator.calculateMonthlyPrayerTimes(year, month, latitude, longitude, method)
            dao.insertPrayerDays(localEntities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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

    private fun UmmahPrayerDayDto.toEntity(): PrayerDayEntity {
        return PrayerDayEntity(
            date = date,
            hijriDate = "",
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
