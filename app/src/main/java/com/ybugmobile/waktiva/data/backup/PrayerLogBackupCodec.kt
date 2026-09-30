package com.ybugmobile.waktiva.data.backup

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.ybugmobile.waktiva.domain.model.PrayerLogBackup
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.isLogged
import java.time.LocalDate

/** The file isn't a Waktiva prayer log, or is one from a newer version than this app reads. */
class InvalidPrayerLogBackupException(message: String) : Exception(message)

/**
 * The prayer log's file format: plain JSON a person can read, marked as Waktiva's so another
 * file is turned away, with a version for later changes.
 *
 * ```
 * { "app": "Waktiva", "type": "prayer-log", "version": 1, "startDate": "2026-09-29",
 *   "days": [ { "date": "2026-09-29", "prayed": ["FAJR", "DHUHR"] } ] }
 * ```
 */
object PrayerLogBackupCodec {
    private const val APP = "Waktiva"
    private const val TYPE = "prayer-log"
    const val VERSION = 1

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(backup: PrayerLogBackup): String = gson.toJson(
        FileDto(
            app = APP,
            type = TYPE,
            version = VERSION,
            startDate = backup.startDate?.toString(),
            days = backup.prayed.entries
                .filter { it.value.isNotEmpty() }
                .sortedBy { it.key }
                .map { (date, prayers) -> DayDto(date.toString(), prayers.sortedBy { it.ordinal }.map { it.name }) }
        )
    )

    /**
     * Reads a prayer log file. Prayers it doesn't know, and days it can't read, are left out;
     * anything that isn't a Waktiva prayer log at all is refused.
     */
    fun decode(json: String): PrayerLogBackup {
        val file = try {
            gson.fromJson(json, FileDto::class.java)
        } catch (e: JsonParseException) {
            throw InvalidPrayerLogBackupException("Not JSON: ${e.message}")
        } ?: throw InvalidPrayerLogBackupException("Empty file")
        if (file.app != APP || file.type != TYPE) throw InvalidPrayerLogBackupException("Not a Waktiva prayer log")
        if ((file.version ?: 0) !in 1..VERSION) throw InvalidPrayerLogBackupException("Unsupported version ${file.version}")

        val prayed = file.days.orEmpty().mapNotNull { day ->
            val date = day.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val prayers = day.prayed.orEmpty().mapNotNull { PrayerType.fromString(it) }.filter { it.isLogged }.toSet()
            if (prayers.isEmpty()) null else date to prayers
        }.groupBy({ it.first }, { it.second }).mapValues { (_, sets) -> sets.flatten().toSet() }

        return PrayerLogBackup(
            startDate = file.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            prayed = prayed
        )
    }

    // Nullable throughout: Gson fills in whatever the file has, and decode checks it.
    private class FileDto(
        @SerializedName("app") val app: String?,
        @SerializedName("type") val type: String?,
        @SerializedName("version") val version: Int?,
        @SerializedName("startDate") val startDate: String?,
        @SerializedName("days") val days: List<DayDto>?
    )

    private class DayDto(
        @SerializedName("date") val date: String?,
        @SerializedName("prayed") val prayed: List<String>?
    )
}
