package com.ybugmobile.waktiva.data.backup

import com.ybugmobile.waktiva.domain.model.PrayerLogBackup
import com.ybugmobile.waktiva.domain.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrayerLogBackupCodecTest {

    private val backup = PrayerLogBackup(
        startDate = LocalDate.of(2026, 9, 29),
        prayed = mapOf(
            LocalDate.of(2026, 9, 29) to setOf(PrayerType.ISHA, PrayerType.FAJR),
            LocalDate.of(2026, 9, 30) to setOf(PrayerType.DHUHR)
        )
    )

    @Test
    fun aLogComesBackAsItWent() {
        assertEquals(backup, PrayerLogBackupCodec.decode(PrayerLogBackupCodec.encode(backup)))
    }

    @Test
    fun theFileIsReadableAndInOrder() {
        val json = PrayerLogBackupCodec.encode(backup)
        assertTrue(json.contains("\"app\": \"Waktiva\""))
        assertTrue(json.contains("\"startDate\": \"2026-09-29\""))
        // Days in date order, prayers in the order of the day.
        assertTrue(json.indexOf("2026-09-29\",") < json.indexOf("2026-09-30"))
        assertTrue(json.indexOf("\"FAJR\"") < json.indexOf("\"ISHA\""))
    }

    @Test
    fun anEmptyLogStillMakesAFile() {
        val empty = PrayerLogBackup(null, emptyMap())
        assertEquals(empty, PrayerLogBackupCodec.decode(PrayerLogBackupCodec.encode(empty)))
    }

    @Test
    fun otherFilesAreTurnedAway() {
        listOf(
            "",
            "not json",
            "[1, 2, 3]",
            """{"app": "Other", "type": "prayer-log", "version": 1}""",
            """{"app": "Waktiva", "type": "settings", "version": 1}"""
        ).forEach { json ->
            assertThrows(json, InvalidPrayerLogBackupException::class.java) { PrayerLogBackupCodec.decode(json) }
        }
    }

    @Test
    fun aNewerVersionIsTurnedAway() {
        val json = """{"app": "Waktiva", "type": "prayer-log", "version": 2, "days": []}"""
        assertThrows(InvalidPrayerLogBackupException::class.java) { PrayerLogBackupCodec.decode(json) }
    }

    @Test
    fun unknownPrayersAndBadDaysAreLeftOut() {
        val json = """
            {"app": "Waktiva", "type": "prayer-log", "version": 1, "startDate": "someday",
             "days": [
               {"date": "2026-09-30", "prayed": ["ASR", "SUNRISE", "WITR", "asr"]},
               {"date": "yesterday", "prayed": ["FAJR"]},
               {"date": "2026-10-01", "prayed": ["SUNRISE"]}
             ]}
        """.trimIndent()
        val read = PrayerLogBackupCodec.decode(json)
        assertNull(read.startDate)
        assertEquals(mapOf(LocalDate.of(2026, 9, 30) to setOf(PrayerType.ASR)), read.prayed)
        assertEquals(1, read.size)
    }
}
