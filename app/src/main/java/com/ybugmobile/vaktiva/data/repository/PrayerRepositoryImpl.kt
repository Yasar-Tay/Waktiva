package com.ybugmobile.vaktiva.data.repository

import com.ybugmobile.vaktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.vaktiva.data.local.dao.PrayerDao
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.data.mapper.toDomain
import com.ybugmobile.vaktiva.data.remote.AladhanApiService
import com.ybugmobile.vaktiva.data.remote.UmmahApiService
import com.ybugmobile.vaktiva.data.remote.dto.PrayerDayDto
import com.ybugmobile.vaktiva.data.remote.dto.UmmahPrayerDayDto
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PrayerRepositoryImpl @Inject constructor(
    private val aladhanApi: AladhanApiService,
    private val ummahApi: UmmahApiService,
    private val localCalculator: LocalPrayerCalculator,
    private val dao: PrayerDao
) : PrayerRepository {

    override fun getPrayerDays(): Flow<List<PrayerDay>> {
        return dao.getAllPrayerDays().map { entities -> 
            entities.map { it.toDomain() } 
        }
    }

    override suspend fun refreshPrayerTimes(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
        method: Int
    ): Result<Unit> {
        // 1. Try Primary Source (Aladhan)
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

        // 2. Try Secondary Source (UmmahAPI) fallback
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

        // 3. Final Fallback: Local Calculation
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
