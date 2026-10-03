package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.DiyanetCalculationTrace
import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId

/**
 * Production method 13 against one year of the global official set, row by row, for comparing
 * years and engine changes.
 *
 * Set DIYANET_GLOBAL_AUDIT_DIR to the directory holding cities.csv and official_<year>.csv,
 * DIYANET_OFFICIAL_YEAR to the year (default 2026) and optionally DIYANET_EVAL_LABEL. Writes
 * eval_<year>_<label>.csv: per city-day the signed error of each prayer in minutes (app minus
 * official, on the clock circle) and the engine's trace for the day.
 */
class DiyanetYearEvaluationTest {

    @Test
    fun `write production errors for one official year`() {
        val auditDirValue = System.getenv("DIYANET_GLOBAL_AUDIT_DIR")
        assumeTrue("Set DIYANET_GLOBAL_AUDIT_DIR to run the year evaluation", !auditDirValue.isNullOrBlank())
        val auditDir = Path.of(auditDirValue!!)
        val year = System.getenv("DIYANET_OFFICIAL_YEAR")?.takeIf { it.isNotBlank() }?.toInt() ?: 2026
        val label = System.getenv("DIYANET_EVAL_LABEL")?.takeIf { it.isNotBlank() } ?: "current"

        val cities = loadCities(auditDir.resolve("cities.csv"))
        val calculator = LocalPrayerCalculator()
        val output = auditDir.resolve("eval_${year}_$label.csv")
        var currentMonth: Triple<Int, Int, Int>? = null
        var predictions: Map<String, PrayerDayEntity> = emptyMap()
        var traces: Map<String, DiyanetCalculationTrace> = emptyMap()

        Files.newBufferedWriter(output).use { writer ->
            writer.appendLine(
                "city_id,date,fajr,sunrise,dhuhr,asr,maghrib,isha,routing,axis_mode,fajr_state,isha_state," +
                    "fajr_shoulder,polar_day,polar_night,asr_source"
            )
            Files.newBufferedReader(auditDir.resolve("official_$year.csv")).useLines { lines ->
                lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
                    val fields = line.split(',')
                    val cityId = fields[0].toInt()
                    val city = requireNotNull(cities[cityId]) { "Missing metadata for city $cityId" }
                    val date = LocalDate.parse(fields[1])
                    val month = Triple(cityId, date.year, date.monthValue)
                    if (month != currentMonth) {
                        val dayTraces = mutableMapOf<String, DiyanetCalculationTrace>()
                        predictions = calculator.calculateMonthlyPrayerTimes(
                            year = date.year,
                            month = date.monthValue,
                            latitude = city.latitude,
                            longitude = city.longitude,
                            methodId = LocalPrayerCalculator.DIYANET_METHOD_ID,
                            zoneId = city.zoneId,
                            calculationTraceSink = { dayTraces[it.date.toString()] = it }
                        ).associateBy(PrayerDayEntity::date)
                        traces = dayTraces
                        currentMonth = month
                    }
                    val predicted = requireNotNull(predictions[fields[1]]) { "No prediction for $cityId ${fields[1]}" }
                    val trace = traces[fields[1]]
                    val actual = listOf(predicted.fajr, predicted.sunrise, predicted.dhuhr, predicted.asr, predicted.maghrib, predicted.isha)
                    val errors = actual.mapIndexed { i, value -> clockDelta(value, fields[2 + i]) }
                    writer.append("$cityId,${fields[1]},${errors.joinToString(",")},")
                    writer.appendLine(
                        listOf(
                            trace?.routing, trace?.axisMode, trace?.fajrState, trace?.ishaState,
                            trace?.fajrShoulderMode, trace?.polarDay, trace?.polarNight, trace?.asrSource
                        ).joinToString(",") { it?.toString().orEmpty() }
                    )
                }
            }
        }
        println("DIYANET_YEAR_EVALUATION|year=$year|label=$label|output=$output")
    }

    private fun loadCities(path: Path): Map<Int, City> = Files.newBufferedReader(path).useLines { lines ->
        val iterator = lines.iterator()
        val headers = iterator.next().split(',').withIndex().associate { it.value to it.index }
        val cities = mutableMapOf<Int, City>()
        iterator.forEach { line ->
            if (line.isBlank()) return@forEach
            // Country and city names never hold a comma in cities.csv; the fields used here are numeric or zone ids.
            val fields = line.split(',')
            val id = fields[headers.getValue("city_id")].toInt()
            cities[id] = City(
                latitude = fields[headers.getValue("latitude")].toDouble(),
                longitude = fields[headers.getValue("longitude")].toDouble(),
                zoneId = ZoneId.of(fields[headers.getValue("timezone")])
            )
        }
        cities
    }

    private fun clockDelta(actual: String, official: String): Int {
        fun minutes(value: String) = value.substring(0, 2).toInt() * 60 + value.substring(3, 5).toInt()
        var delta = minutes(actual) - minutes(official)
        if (delta > 720) delta -= 1440
        if (delta < -720) delta += 1440
        return delta
    }

    private data class City(val latitude: Double, val longitude: Double, val zoneId: ZoneId)
}
