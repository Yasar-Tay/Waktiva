package com.ybugmobile.waktiva

import com.ybugmobile.waktiva.data.local.DiyanetAsrSource
import com.ybugmobile.waktiva.data.local.DiyanetCalculationTrace
import com.ybugmobile.waktiva.data.local.DiyanetEngineRouting
import com.ybugmobile.waktiva.data.local.LocalPrayerCalculator
import com.ybugmobile.waktiva.data.local.entity.PrayerDayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil

/** Production-output-only segmentation and diagnostics export for Diyanet method 13. */
class DiyanetV14SegmentAnalysisTest {

    @Test
    fun `export production Kotlin V14 segment analysis`() {
        val auditDirValue = System.getenv(AUDIT_DIR_ENV)
        assumeTrue("Set $AUDIT_DIR_ENV to run segment analysis", !auditDirValue.isNullOrBlank())
        val startedNanos = System.nanoTime()
        val auditDir = Path.of(auditDirValue!!)
        val cities = loadCities(auditDir.resolve("cities.csv"))
        val officialPath = auditDir.resolve("official_2026.csv")
        assertTrue("Missing official fixture: $officialPath", Files.isRegularFile(officialPath))

        val rowPath = auditDir.resolve("v14_kotlin_golden_rows.csv")
        val cityQualityPath = auditDir.resolve("v14_kotlin_city_quality.csv")
        val tailPath = auditDir.resolve("v14_kotlin_model_tail_gt10_rows.csv")
        val clusterPath = auditDir.resolve("v14_kotlin_model_tail_gt10_clusters.csv")
        val segmentAccumulators = Segment.entries.associateWith { SegmentAccumulator(it) }
        val asrAccumulators = DiyanetAsrSource.entries.associateWith { SourceAccumulator() }
        val topErrors = PriorityQueue<AnalysisRow>(compareBy(AnalysisRow::absoluteErrorMinutes))
        val qualities = mutableListOf<CityQuality>()
        val evaluatedCityIds = mutableSetOf<Int>()
        val finalizedCityIds = mutableSetOf<Int>()
        val calculator = LocalPrayerCalculator()

        largeWriter(rowPath).use { rowWriter ->
            largeWriter(cityQualityPath).use { cityWriter ->
                largeWriter(tailPath).use { tailWriter ->
                    largeWriter(clusterPath).use { clusterWriter ->
                        writeGoldenHeader(rowWriter)
                        writeCityQualityHeader(cityWriter)
                        writeGoldenHeader(tailWriter)
                        writeClusterHeader(clusterWriter)

                        var current: CityRows? = null
                        var currentMonth: MonthKey? = null
                        var predictions: Map<String, PrayerDayEntity> = emptyMap()
                        var traces: Map<LocalDate, DiyanetCalculationTrace> = emptyMap()

                        fun finalizeCurrent() {
                            val completed = current ?: return
                            require(finalizedCityIds.add(completed.city.cityId)) {
                                "official_2026.csv is not grouped by city: ${completed.city.cityId}"
                            }
                            val quality = CityQuality.from(completed)
                            qualities += quality
                            writeCityQuality(cityWriter, quality)

                            fun add(segment: Segment, rows: List<AnalysisRow> = completed.rows) {
                                segmentAccumulators.getValue(segment).addCity(rows)
                            }
                            add(Segment.A_ALL)
                            if (quality.northV14) add(Segment.B_NORTH_V14)
                            if (quality.trustedLongitudeTimeAxis) add(Segment.C_TRUSTED_LONGITUDE_TIME_AXIS)
                            if (quality.northV14 && quality.trustedFullCoordinateAxis) {
                                add(Segment.D_TRUSTED_FULL_COORDINATE_AXIS)
                                add(
                                    Segment.D_NORMAL_REGIME,
                                    completed.rows.filter { row ->
                                        row.trace.axisMode == "raw" &&
                                            !row.trace.polarNight && !row.trace.polarDay
                                    }
                                )
                                writeModelTail(completed, tailWriter, clusterWriter)
                            }
                            if (quality.suspiciousMapping) add(Segment.E_SUSPECT_MAPPING)
                            if (quality.southernFallback) add(Segment.F_SOUTH_V9_FALLBACK)
                            add(
                                Segment.G_SYNTHETIC_AXIS_DIAGNOSTIC,
                                completed.rows.filter { row -> row.trace.axisMode != null && row.trace.axisMode != "raw" }
                            )

                            completed.rows.asSequence()
                                .filter { it.event == PrayerEvent.ASR }
                                .groupBy { it.trace.asrSource }
                                .forEach { (source, rows) -> asrAccumulators.getValue(source).addCity(rows) }

                            if (qualities.size % 100 == 0) {
                                println("DIYANET_V14_SEGMENT_PROGRESS|cities=${qualities.size}")
                            }
                        }

                        Files.newBufferedReader(officialPath).use { reader ->
                            val iterator = reader.lineSequence().iterator()
                            require(iterator.hasNext()) { "Empty official fixture: $officialPath" }
                            val headers = headerIndex(parseCsvLine(iterator.next()))

                            while (iterator.hasNext()) {
                                val line = iterator.next()
                                if (line.isBlank()) continue
                                val fields = parseCsvLine(line)
                                val cityId = fields[headers.getValue("city_id")].toInt()
                                val city = requireNotNull(cities[cityId]) { "Missing city metadata: $cityId" }
                                val dateText = fields[headers.getValue("date")]
                                val date = LocalDate.parse(dateText)

                                if (current?.city?.cityId != cityId) {
                                    finalizeCurrent()
                                    current = CityRows(city)
                                    currentMonth = null
                                }
                                evaluatedCityIds += cityId

                                val monthKey = MonthKey(cityId, date.year, date.monthValue)
                                if (monthKey != currentMonth) {
                                    val monthTraces = mutableListOf<DiyanetCalculationTrace>()
                                    predictions = calculator.calculateMonthlyPrayerTimes(
                                        year = date.year,
                                        month = date.monthValue,
                                        latitude = city.latitude,
                                        longitude = city.longitude,
                                        methodId = LocalPrayerCalculator.DIYANET_METHOD_ID,
                                        zoneId = city.zoneId,
                                        calculationTraceSink = monthTraces::add
                                    ).associateBy(PrayerDayEntity::date)
                                    traces = monthTraces.associateBy(DiyanetCalculationTrace::date)
                                    currentMonth = monthKey
                                }

                                val prediction = requireNotNull(predictions[dateText]) { "No prediction for $cityId $date" }
                                val trace = requireNotNull(traces[date]) { "No trace for $cityId $date" }
                                PrayerEvent.entries.forEach { event ->
                                    val official = fields[headers.getValue(event.csvName)]
                                    val predicted = event.read(prediction)
                                    val signedError = circularClockDelta(predicted, official)
                                    val row = AnalysisRow(
                                        city = city,
                                        date = date,
                                        event = event,
                                        officialTime = official,
                                        predictedTime = predicted,
                                        signedErrorMinutes = signedError,
                                        trace = trace
                                    )
                                    current!!.rows += row
                                    writeGoldenRow(rowWriter, row)
                                    if (topErrors.size < TOP_ERROR_COUNT) {
                                        topErrors += row
                                    } else if (row.absoluteErrorMinutes > topErrors.peek()!!.absoluteErrorMinutes) {
                                        topErrors.poll()
                                        topErrors += row
                                    }
                                }
                            }
                        }
                        finalizeCurrent()
                    }
                }
            }
        }

        assertEquals(EXPECTED_CITY_COUNT, evaluatedCityIds.size)
        assertEquals(evaluatedCityIds, finalizedCityIds)

        writeSegmentMetrics(auditDir.resolve("v14_kotlin_segment_metrics.csv"), segmentAccumulators)
        writeAsrMetrics(auditDir.resolve("v14_kotlin_asr_source_metrics.csv"), asrAccumulators)
        writeTopErrors(auditDir.resolve("v14_kotlin_top_100_errors.csv"), topErrors)

        val elapsedSeconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0
        writeAnalysisReport(
            path = auditDir.resolve("v14_kotlin_segment_analysis.md"),
            elapsedSeconds = elapsedSeconds,
            fixtureCityCount = cities.size,
            qualities = qualities,
            segments = segmentAccumulators,
            asrSources = asrAccumulators
        )
        writeProductionReadiness(
            path = auditDir.resolve("v14_kotlin_production_readiness.md"),
            qualities = qualities,
            segments = segmentAccumulators
        )

        val all = segmentAccumulators.getValue(Segment.A_ALL).all.snapshot()
        assertEquals(EXPECTED_COMPARISON_COUNT, all.count)
        assertEquals(2.11994, all.mae, 0.00001)
        assertEquals(46, all.p99)
        assertEquals(158_083L, all.greaterThanTen)
        println(
            "DIYANET_V14_SEGMENT_DONE|cities=${qualities.size}|comparisons=${all.count}|" +
                "elapsed_seconds=${decimal(elapsedSeconds)}|output=$auditDir"
        )
    }

    private fun writeModelTail(cityRows: CityRows, tailWriter: BufferedWriter, clusterWriter: BufferedWriter) {
        listOf(PrayerEvent.FAJR, PrayerEvent.ISHA).forEach { event ->
            val eventRows = cityRows.rows.filter { it.event == event }.sortedBy(AnalysisRow::date)
            eventRows.filter { it.absoluteErrorMinutes > 10 }.forEach { writeGoldenRow(tailWriter, it) }

            var cluster = mutableListOf<AnalysisRow>()
            fun closeCluster() {
                if (cluster.isEmpty()) return
                writeCluster(clusterWriter, cluster)
                cluster = mutableListOf()
            }
            eventRows.forEach { row ->
                if (row.absoluteErrorMinutes <= 10) {
                    closeCluster()
                } else if (cluster.isNotEmpty() && cluster.last().date.plusDays(1) != row.date) {
                    closeCluster()
                    cluster += row
                } else {
                    cluster += row
                }
            }
            closeCluster()
        }
    }

    private fun writeCluster(writer: BufferedWriter, rows: List<AnalysisRow>) {
        val first = rows.first()
        val meanSigned = rows.map(AnalysisRow::signedErrorMinutes).average()
        val direction = when {
            rows.all { it.signedErrorMinutes > 0 } -> "LATE"
            rows.all { it.signedErrorMinutes < 0 } -> "EARLY"
            else -> "MIXED"
        }
        writeCsvRow(
            writer,
            first.city.cityId,
            first.city.name,
            first.city.countryName,
            first.event.csvName,
            first.date,
            rows.last().date,
            rows.size,
            rows.maxOf(AnalysisRow::absoluteErrorMinutes),
            decimal(meanSigned),
            decimal(rows.map(AnalysisRow::absoluteErrorMinutes).average()),
            direction,
            rows.map { it.trace.regime }.distinct().joinToString("|"),
            rows.mapNotNull { diagnosticFor(it) }.distinct().joinToString("|").ifBlank { "NONE" },
            availabilitySummary(rows, first.event),
            polarSummary(rows)
        )
    }

    private fun availabilitySummary(rows: List<AnalysisRow>, event: PrayerEvent): String {
        val values = rows.mapNotNull {
            if (event == PrayerEvent.FAJR) it.trace.directFajrAvailable else it.trace.directIshaAvailable
        }
        return when {
            values.isEmpty() -> "UNKNOWN"
            values.all { it } -> "ALL"
            values.none { it } -> "NONE"
            else -> "SOME"
        }
    }

    private fun polarSummary(rows: List<AnalysisRow>): String = when {
        rows.any { it.trace.polarNight } && rows.any { it.trace.polarDay } -> "POLAR_DAY|POLAR_NIGHT"
        rows.any { it.trace.polarNight } -> "POLAR_NIGHT"
        rows.any { it.trace.polarDay } -> "POLAR_DAY"
        else -> "NORMAL"
    }

    private fun writeSegmentMetrics(path: Path, segments: Map<Segment, SegmentAccumulator>) {
        largeWriter(path).use { writer ->
            writer.appendLine(
                "segment,event,segment_city_count,n,mae,signed_mean_error,median_absolute_error," +
                    "p90,p95,p99,p999,within1,within2,within5,within10,gt10,gt10_rate,gt30,gt60," +
                    "max,affected_city_count,affected_city_day_count,longest_consecutive_gt10_cluster"
            )
            segments.values.forEach { segment ->
                PrayerEvent.entries.forEach { event ->
                    writeMetricRow(writer, segment, event.csvName, segment.byEvent.getValue(event).snapshot())
                }
                writeMetricRow(writer, segment, "all", segment.all.snapshot())
            }
        }
    }

    private fun writeMetricRow(
        writer: BufferedWriter,
        segment: SegmentAccumulator,
        event: String,
        metric: MetricSnapshot
    ) = writeCsvRow(
        writer,
        segment.segment.name,
        event,
        segment.cityCount,
        metric.count,
        decimal(metric.mae),
        decimal(metric.signedMean),
        metric.medianAbsolute,
        metric.p90,
        metric.p95,
        metric.p99,
        metric.p999,
        metric.within1,
        metric.within2,
        metric.within5,
        metric.within10,
        metric.greaterThanTen,
        decimal(metric.greaterThanTenRate),
        metric.greaterThanThirty,
        metric.greaterThanSixty,
        metric.maximum,
        metric.affectedCityCount,
        metric.affectedCityDayCount,
        metric.longestConsecutiveGt10
    )

    private fun writeAsrMetrics(path: Path, sources: Map<DiyanetAsrSource, SourceAccumulator>) {
        largeWriter(path).use { writer ->
            writer.appendLine("asr_source,n,city_count,mae,p95,p99,gt10,gt10_rate,max")
            sources.forEach { (source, accumulator) ->
                val metric = accumulator.metric.snapshot()
                writeCsvRow(
                    writer, source.name, metric.count, accumulator.cityIds.size, decimal(metric.mae),
                    metric.p95, metric.p99, metric.greaterThanTen,
                    decimal(metric.greaterThanTenRate), metric.maximum
                )
            }
        }
    }

    private fun writeTopErrors(path: Path, queue: PriorityQueue<AnalysisRow>) {
        largeWriter(path).use { writer ->
            writeGoldenHeader(writer)
            queue.toList()
                .sortedWith(compareByDescending<AnalysisRow> { it.absoluteErrorMinutes }
                    .thenBy { it.city.cityId }.thenBy(AnalysisRow::date).thenBy { it.event.ordinal })
                .forEach { writeGoldenRow(writer, it) }
        }
    }

    private fun writeAnalysisReport(
        path: Path,
        elapsedSeconds: Double,
        fixtureCityCount: Int,
        qualities: List<CityQuality>,
        segments: Map<Segment, SegmentAccumulator>,
        asrSources: Map<DiyanetAsrSource, SourceAccumulator>
    ) {
        val all = segments.getValue(Segment.A_ALL).all.snapshot()
        val trusted = segments.getValue(Segment.D_TRUSTED_FULL_COORDINATE_AXIS).all.snapshot()
        val normal = segments.getValue(Segment.D_NORMAL_REGIME).all.snapshot()
        val suspect = segments.getValue(Segment.E_SUSPECT_MAPPING).all.snapshot()
        val south = segments.getValue(Segment.F_SOUTH_V9_FALLBACK).all.snapshot()
        val synthetic = segments.getValue(Segment.G_SYNTHETIC_AXIS_DIAGNOSTIC).all.snapshot()
        val trustedFajr = segments.getValue(Segment.D_TRUSTED_FULL_COORDINATE_AXIS)
            .byEvent.getValue(PrayerEvent.FAJR).snapshot()
        val trustedIsha = segments.getValue(Segment.D_TRUSTED_FULL_COORDINATE_AXIS)
            .byEvent.getValue(PrayerEvent.ISHA).snapshot()

        largeWriter(path).use { writer ->
            writer.appendLine("# Diyanet V14 Production Kotlin Segment Analysis")
            writer.appendLine()
            writer.appendLine("Generated: 2026-07-14")
            writer.appendLine()
            writer.appendLine("## Scope and environment")
            writer.appendLine()
            writer.appendLine("- Production path: `LocalPrayerCalculator`, method 13; no coefficient, formula or routing changes.")
            writer.appendLine("- Input: 3,040 evaluated cities from `official_2026.csv`; metadata file contains $fixtureCityCount cities.")
            writer.appendLine("- Dependencies: `adhan-java:1.1.0`, `commons-suncalc:3.11` (no stubs).")
            writer.appendLine("- JVM: `${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}`.")
            writer.appendLine("- Gradle wrapper: `9.4.1`; elapsed analysis time: `${duration(elapsedSeconds)}`.")
            writer.appendLine("- Command: `./gradlew :app:testDebugUnitTest --tests \"com.ybugmobile.waktiva.DiyanetV14SegmentAnalysisTest\"` with `$AUDIT_DIR_ENV` set.")
            writer.appendLine()
            writer.appendLine("Circular clock errors are normalized to [-720, 720], so 23:55 and 00:05 differ by 10 minutes.")
            writer.appendLine()
            writer.appendLine("## Segment definitions")
            writer.appendLine()
            writer.appendLine("- A: all production rows; no exclusion.")
            writer.appendLine("- B: `latitude >= 45` and production routing `V14`.")
            writer.appendLine("- C: annual Dhuhr absolute median error <= 5 minutes.")
            writer.appendLine("- D: northern V14 scope plus C and annual Sunrise/Maghrib absolute medians <= 3 minutes each.")
            writer.appendLine("- E: Dhuhr absolute median >10, four-event same-direction >10 shift, ±60/±120 cluster, or aligned core-axis >10 shift.")
            writer.appendLine("- F: `latitude <= -45`, V9 routing, southern-disabled diagnostic.")
            writer.appendLine("- D_NORMAL and G_SYNTHETIC are diagnostic subsegments, not filtered replacements for A-F.")
            writer.appendLine()
            writer.appendLine("## Segment results (all six prayers)")
            writer.appendLine()
            writer.appendLine("| Segment | Cities | n | MAE | P99 | >10 | >10 rate | Max | Longest cluster |")
            writer.appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
            segments.values.forEach { segment ->
                val metric = segment.all.snapshot()
                writer.appendLine(
                    "| ${segment.segment.name} | ${segment.cityCount} | ${metric.count} | ${decimal(metric.mae)} | " +
                        "${metric.p99} | ${metric.greaterThanTen} | ${percent(metric.greaterThanTenRate)} | " +
                        "${metric.maximum} | ${metric.longestConsecutiveGt10} |"
                )
            }
            writer.appendLine()
            writer.appendLine("Segment D independently contains ${segments.getValue(Segment.D_TRUSTED_FULL_COORDINATE_AXIS).cityCount} cities; the criteria were fixed before observing its V14 tail and were not optimized to improve Fajr/Isha.")
            writer.appendLine("The earlier 2,892-city reference cannot be exactly reconstructed from production rows alone: its script additionally required a precomputed `good_city` flag and independent `axis_day_mae_geo <= 5`. Those fields are absent from `cities.csv`. Importing that previous classification would no longer be an independent production-output segmentation. The 21-city difference is therefore retained and explained rather than tuned away.")
            writer.appendLine()
            writer.appendLine("## Model tail in trusted full-coordinate segment")
            writer.appendLine()
            writer.appendLine("| Event | MAE | P99 | >10 | Max | Longest cluster |")
            writer.appendLine("|---|---:|---:|---:|---:|---:|")
            writer.appendLine("| Fajr | ${decimal(trustedFajr.mae)} | ${trustedFajr.p99} | ${trustedFajr.greaterThanTen} | ${trustedFajr.maximum} | ${trustedFajr.longestConsecutiveGt10} |")
            writer.appendLine("| Isha | ${decimal(trustedIsha.mae)} | ${trustedIsha.p99} | ${trustedIsha.greaterThanTen} | ${trustedIsha.maximum} | ${trustedIsha.longestConsecutiveGt10} |")
            writer.appendLine()
            writer.appendLine("Requested target check: Fajr tail is ${target(trustedFajr.greaterThanTen <= 15 && trustedFajr.maximum <= 15)}; Isha tail is ${target(trustedIsha.greaterThanTen <= 10 && trustedIsha.maximum <= 12)}; combined trusted MAE/P99 is ${target(trusted.mae <= 0.85 && trusted.p99 <= 5)}.")
            writer.appendLine()
            writer.appendLine("## Asr source isolation")
            writer.appendLine()
            writer.appendLine("| Source | Rows | Cities | MAE | P95 | P99 | >10 | Max |")
            writer.appendLine("|---|---:|---:|---:|---:|---:|---:|---:|")
            asrSources.forEach { (source, accumulator) ->
                val metric = accumulator.metric.snapshot()
                writer.appendLine("| ${source.name} | ${metric.count} | ${accumulator.cityIds.size} | ${decimal(metric.mae)} | ${metric.p95} | ${metric.p99} | ${metric.greaterThanTen} | ${metric.maximum} |")
            }
            writer.appendLine()
            writer.appendLine("## Reference-versus-production difference")
            writer.appendLine()
            writer.appendLine("The independent reference was approximately MAE 1.94, P99 43-44 and 156,150 >10 rows. Production Kotlin is MAE ${decimal(all.mae)}, P99 ${all.p99}, and ${all.greaterThanTen} >10 rows: about +${decimal(all.mae - 1.94)} MAE and +${all.greaterThanTen - 156_150} tail rows.")
            writer.appendLine()
            writer.appendLine("| Event | Reference MAE | Kotlin MAE | MAE delta | Approx. absolute-minute delta | >10 delta |")
            writer.appendLine("|---|---:|---:|---:|---:|---:|")
            val referenceByEvent = mapOf(
                PrayerEvent.FAJR to ReferenceMetric(2.0560607426, 25_768),
                PrayerEvent.SUNRISE to ReferenceMetric(1.5044673756, 26_750),
                PrayerEvent.DHUHR to ReferenceMetric(1.2814653929, 24_520),
                PrayerEvent.ASR to ReferenceMetric(1.8721818673, 25_553),
                PrayerEvent.MAGHRIB to ReferenceMetric(2.3045890411, 26_768),
                PrayerEvent.ISHA to ReferenceMetric(2.5991222062, 26_314)
            )
            val absoluteMinuteDelta = referenceByEvent.mapValues { (event, reference) ->
                val actual = segments.getValue(Segment.A_ALL).byEvent.getValue(event).snapshot()
                (actual.mae - reference.mae) * actual.count
            }
            referenceByEvent.forEach { (event, reference) ->
                val actual = segments.getValue(Segment.A_ALL).byEvent.getValue(event).snapshot()
                writer.appendLine(
                    "| ${event.csvName} | ${decimal(reference.mae)} | ${decimal(actual.mae)} | " +
                        "${decimal(actual.mae - reference.mae)} | ${absoluteMinuteDelta.getValue(event).toLong()} | " +
                        "${actual.greaterThanTen - reference.gt10} |"
                )
            }
            val totalAbsoluteMinuteDelta = absoluteMinuteDelta.values.sum()
            val asrDeltaShare = absoluteMinuteDelta.getValue(PrayerEvent.ASR) / totalAbsoluteMinuteDelta
            writer.appendLine()
            writer.appendLine("Asr alone contributes about ${percent(asrDeltaShare)} of the aggregate absolute-minute increase, consistent with the independent evaluator using a Shafi-equivalent solar calculation while production uses real Adhan plus fallbacks.")
            writer.appendLine()
            writer.appendLine("Quantifiable overlapping contributors (not additive causal ablations):")
            writer.appendLine()
            writer.appendLine("- Suspect mapping segment E: ${segments.getValue(Segment.E_SUSPECT_MAPPING).cityCount} cities, ${suspect.greaterThanTen} >10 rows (${percent(suspect.greaterThanTen.toDouble() / all.greaterThanTen)} of the raw tail), MAE ${decimal(suspect.mae)}.")
            writer.appendLine("- Southern V9 fallback: ${segments.getValue(Segment.F_SOUTH_V9_FALLBACK).cityCount} cities, ${south.greaterThanTen} >10 rows, MAE ${decimal(south.mae)}.")
            writer.appendLine("- Synthetic/polar axis rows: ${synthetic.count} comparisons, ${synthetic.greaterThanTen} >10 rows, MAE ${decimal(synthetic.mae)}.")
            writer.appendLine("- Real Adhan Asr contribution: ${asrSources.values.sumOf { it.metric.snapshot().greaterThanTen }} >10 Asr rows; source-level figures are above.")
            writer.appendLine("- Reliable full-coordinate segment D: MAE ${decimal(trusted.mae)}, P99 ${trusted.p99}, ${trusted.greaterThanTen} >10 rows. Normal-axis subset: MAE ${decimal(normal.mae)}, P99 ${normal.p99}, max ${normal.maximum}.")
            writer.appendLine()
            writer.appendLine("The southern reference used experimental V14 (MAE 13.22702, 3,261 >10 rows); production V9 fallback is MAE ${decimal(south.mae)}, ${south.greaterThanTen} >10 rows. Its net delta is only about ${((south.mae - 13.2270167428) * south.count).toLong()} absolute minutes and ${south.greaterThanTen - 3_261} tail rows.")
            writer.appendLine()
            writer.appendLine("SunCalc, rounding and time-zone database effects cannot be uniquely separated from aggregate reference metrics without the independent evaluator's row-level predictions. The non-Asr core-event deltas above bound their combined impact; this run records UTC offsets, axis modes and exact production rounding on every row. Coverage is the same 3,040 official cities; the seven extra metadata cities have no official rows and do not enter any metric.")
            writer.appendLine()
            writer.appendLine("The previous reference's 128 `good_city=false` matches were established upstream. The production-only global rules flag 69 strongly systematic cities and explain ${percent(suspect.greaterThanTen.toDouble() / all.greaterThanTen)} of the raw tail. The remaining difference includes seasonal-only mapping anomalies, synthetic polar axes and genuine Fajr/Isha transition differences; it is not hidden by importing the old city list.")
            writer.appendLine()
            writer.appendLine("## Quality flags")
            writer.appendLine()
            writer.appendLine("- Trusted longitude/time axis: ${qualities.count { it.trustedLongitudeTimeAxis }} cities.")
            writer.appendLine("- Trusted full coordinate axis: ${qualities.count { it.trustedFullCoordinateAxis }} cities.")
            writer.appendLine("- Suspect mapping: ${qualities.count { it.suspiciousMapping }} cities.")
            writer.appendLine("- Southern fallback: ${qualities.count { it.southernFallback }} cities.")
        }
    }

    private fun writeProductionReadiness(
        path: Path,
        qualities: List<CityQuality>,
        segments: Map<Segment, SegmentAccumulator>
    ) {
        val trusted = segments.getValue(Segment.D_TRUSTED_FULL_COORDINATE_AXIS).all.snapshot()
        val normal = segments.getValue(Segment.D_NORMAL_REGIME).all.snapshot()
        val acceptance = listOf(
            "MAE <= 1" to (trusted.mae <= 1.0),
            "P99 <= 5" to (trusted.p99 <= 5),
            ">10 rate <= 0.01%" to (trusted.greaterThanTenRate <= 0.0001),
            "normal-regime max <= 15" to (normal.maximum <= 15),
            "longest >10 cluster <= 3 days" to (trusted.longestConsecutiveGt10 <= 3)
        )
        val ready = acceptance.all { it.second }
        largeWriter(path).use { writer ->
            writer.appendLine("# Diyanet V14 Production Readiness")
            writer.appendLine()
            writer.appendLine("## Decision")
            writer.appendLine()
            writer.appendLine(
                if (ready) {
                    "V14 meets the requested production thresholds for trusted northern use."
                } else {
                    "V14 does **not** meet all requested production thresholds for an unrestricted trusted-north rollout. Keep it in a controlled feature-flagged pilot with API fallback and telemetry; do not tune coefficients on this acceptance set."
                }
            )
            writer.appendLine()
            writer.appendLine("## Answers")
            writer.appendLine()
            writer.appendLine("1. **Trusted northern coordinates:** not yet an unconditional rollout under the proposed thresholds. Segment D has ${segments.getValue(Segment.D_TRUSTED_FULL_COORDINATE_AXIS).cityCount} cities, MAE ${decimal(trusted.mae)} and P99 ${trusted.p99}, but its tail rate/max/cluster thresholds fail.")
            writer.appendLine("2. **Geographic scope:** pilot only for method 13, northern latitude >=45 degrees, valid IANA zone, raw/non-polar axis and non-suspect location mapping. Keep a remote kill switch.")
            writer.appendLine("3. **Fallback:** keep V9 below the calibrated northern scope and in southern high latitudes. Prefer official API data for suspect mappings, synthetic polar axes, or non-direct Asr sources when network data is available.")
            writer.appendLine("4. **Southern hemisphere:** mark `SOUTHERN_HEMISPHERE_V14_DISABLED`; keep V9 and treat the six-city 2026 sample as diagnostic, not calibration evidence.")
            writer.appendLine("5. **Suspect mappings:** do not hide the city. Ask the user to verify city/location/time zone and label locally calculated times as unverified; prefer official API output when available.")
            writer.appendLine("6. **Telemetry:** engine version, routing, rounded coordinates, zone ID/offset, axis mode, Fajr/Isha states, direct-root availability, polar state, Asr source, fallback diagnostic and invariant violations. Do not collect exact coordinates unless consent and privacy policy permit it.")
            writer.appendLine("7. **Second official year:** rerun the unchanged row-level runner and require the thresholds below both globally in trusted north and by held-out country/latitude band.")
            writer.appendLine()
            writer.appendLine("## Acceptance threshold status on 2026 trusted segment")
            writer.appendLine()
            acceptance.forEach { (criterion, passed) -> writer.appendLine("- ${target(passed)}: $criterion") }
            writer.appendLine()
            writer.appendLine("A failed threshold is an investigation trigger, not permission to tune coefficients on the acceptance set. Require no new normal-regime cluster, validate DST/year-boundary fixtures, and compare city-match quality before any model change.")
            writer.appendLine()
            writer.appendLine("Current quality counts: ${qualities.count { it.northV14 }} northern V14 cities, ${qualities.count { it.southernFallback }} southern fallback cities, ${qualities.count { it.suspiciousMapping }} suspect mappings.")
            writer.appendLine()
            writer.appendLine("Asr is the clearest operational risk: reference-latitude rows have very high >10 frequency and midpoint fallback has triple-digit MAE. These sources must be telemetered and should trigger API preference rather than being treated as ordinary direct-Adhan results.")
        }
    }

    private data class CityQuality(
        val city: City,
        val eventSignedMedians: Map<PrayerEvent, Int>,
        val eventAbsoluteMedians: Map<PrayerEvent, Int>,
        val systematicSameDirection: Boolean,
        val systematicDirection: String,
        val hourClustered: Boolean,
        val hourClusterCenter: Int?,
        val suspiciousMapping: Boolean,
        val suspiciousReasons: String,
        val southernFallback: Boolean,
        val northV14: Boolean,
        val anyGt10: Boolean
    ) {
        val trustedLongitudeTimeAxis: Boolean
            get() = eventAbsoluteMedians.getValue(PrayerEvent.DHUHR) <= 5
        val trustedFullCoordinateAxis: Boolean
            get() = trustedLongitudeTimeAxis &&
                eventAbsoluteMedians.getValue(PrayerEvent.SUNRISE) <= 3 &&
                eventAbsoluteMedians.getValue(PrayerEvent.MAGHRIB) <= 3

        companion object {
            fun from(cityRows: CityRows): CityQuality {
                val byEvent = cityRows.rows.groupBy(AnalysisRow::event)
                val signed = PrayerEvent.entries.associateWith { event ->
                    median(byEvent.getValue(event).map(AnalysisRow::signedErrorMinutes))
                }
                val absolute = PrayerEvent.entries.associateWith { event ->
                    median(byEvent.getValue(event).map(AnalysisRow::absoluteErrorMinutes))
                }
                val positive = signed.values.count { it > 10 }
                val negative = signed.values.count { it < -10 }
                val systematic = positive >= 4 || negative >= 4
                val direction = when {
                    positive >= 4 -> "LATE"
                    negative >= 4 -> "EARLY"
                    else -> "NONE"
                }
                val clusterCenter = listOf(-120, -60, 60, 120).firstOrNull { center ->
                    signed.values.count { abs(it - center) <= 10 } >= 4
                }
                val dhuhr = signed.getValue(PrayerEvent.DHUHR)
                val alignedCoreShift = abs(dhuhr) > 10 &&
                    abs(signed.getValue(PrayerEvent.SUNRISE) - dhuhr) <= 3 &&
                    abs(signed.getValue(PrayerEvent.MAGHRIB) - dhuhr) <= 3
                val reasons = buildList {
                    if (absolute.getValue(PrayerEvent.DHUHR) > 10) add("DHUHR_ABS_MEDIAN_GT10")
                    if (systematic) add("FOUR_EVENT_SAME_DIRECTION_GT10")
                    if (clusterCenter != null) add("HOUR_CLUSTER_${clusterCenter}")
                    if (alignedCoreShift) add("ALIGNED_CORE_AXIS_GT10")
                }
                val firstTrace = cityRows.rows.first().trace
                val southern = cityRows.city.latitude <= -45.0 &&
                    firstTrace.routing == DiyanetEngineRouting.V9 &&
                    cityRows.rows.any {
                        it.trace.diagnostic?.contains("SOUTHERN_HEMISPHERE_V14_DISABLED") == true
                    }
                return CityQuality(
                    city = cityRows.city,
                    eventSignedMedians = signed,
                    eventAbsoluteMedians = absolute,
                    systematicSameDirection = systematic,
                    systematicDirection = direction,
                    hourClustered = clusterCenter != null,
                    hourClusterCenter = clusterCenter,
                    suspiciousMapping = reasons.isNotEmpty(),
                    suspiciousReasons = reasons.joinToString("|").ifBlank { "NONE" },
                    southernFallback = southern,
                    northV14 = cityRows.city.latitude >= 45.0 && firstTrace.routing == DiyanetEngineRouting.V14,
                    anyGt10 = cityRows.rows.any { it.absoluteErrorMinutes > 10 }
                )
            }
        }
    }

    private class SegmentAccumulator(val segment: Segment) {
        val byEvent = PrayerEvent.entries.associateWith { MetricAccumulator() }
        val all = MetricAccumulator()
        var cityCount: Int = 0
            private set

        fun addCity(rows: List<AnalysisRow>) {
            if (rows.isEmpty()) return
            cityCount++
            PrayerEvent.entries.forEach { event ->
                byEvent.getValue(event).addCity(rows.filter { it.event == event })
            }
            all.addCity(rows)
        }
    }

    private class SourceAccumulator {
        val metric = MetricAccumulator()
        val cityIds = mutableSetOf<Int>()
        fun addCity(rows: List<AnalysisRow>) {
            if (rows.isEmpty()) return
            cityIds += rows.first().city.cityId
            metric.addCity(rows)
        }
    }

    private class MetricAccumulator {
        private val absoluteHistogram = LongArray(HALF_DAY_MINUTES + 1)
        private var absoluteTotal = 0L
        private var signedTotal = 0L
        private var count = 0L
        private var gt10 = 0L
        private var gt30 = 0L
        private var gt60 = 0L
        private var maximum = 0
        private var affectedCities = 0
        private var affectedCityDays = 0L
        private var longestRun = 0

        fun addCity(rows: List<AnalysisRow>) {
            if (rows.isEmpty()) return
            rows.forEach { row ->
                val absolute = row.absoluteErrorMinutes
                count++
                absoluteTotal += absolute
                signedTotal += row.signedErrorMinutes
                absoluteHistogram[absolute]++
                if (absolute > 10) gt10++
                if (absolute > 30) gt30++
                if (absolute > 60) gt60++
                if (absolute > maximum) maximum = absolute
            }
            val byDate = rows.groupBy(AnalysisRow::date).toSortedMap()
            var cityAffected = false
            var previousDate: LocalDate? = null
            var run = 0
            byDate.forEach { (date, dayRows) ->
                val affected = dayRows.any { it.absoluteErrorMinutes > 10 }
                if (affected) {
                    cityAffected = true
                    affectedCityDays++
                    run = if (previousDate?.plusDays(1) == date) run + 1 else 1
                    if (run > longestRun) longestRun = run
                } else {
                    run = 0
                }
                previousDate = date
            }
            if (cityAffected) affectedCities++
        }

        fun snapshot(): MetricSnapshot = MetricSnapshot(
            count = count,
            absoluteTotal = absoluteTotal,
            mae = if (count == 0L) 0.0 else absoluteTotal.toDouble() / count,
            signedMean = if (count == 0L) 0.0 else signedTotal.toDouble() / count,
            medianAbsolute = percentile(0.5),
            p90 = percentile(0.90),
            p95 = percentile(0.95),
            p99 = percentile(0.99),
            p999 = percentile(0.999),
            within1 = cumulative(1),
            within2 = cumulative(2),
            within5 = cumulative(5),
            within10 = cumulative(10),
            greaterThanTen = gt10,
            greaterThanThirty = gt30,
            greaterThanSixty = gt60,
            maximum = maximum,
            affectedCityCount = affectedCities,
            affectedCityDayCount = affectedCityDays,
            longestConsecutiveGt10 = longestRun
        )

        private fun cumulative(limit: Int): Long = (0..limit).sumOf { absoluteHistogram[it] }
        private fun percentile(fraction: Double): Int {
            if (count == 0L) return 0
            val target = ceil(count * fraction).toLong().coerceAtLeast(1)
            var cumulative = 0L
            absoluteHistogram.forEachIndexed { value, frequency ->
                cumulative += frequency
                if (cumulative >= target) return value
            }
            return maximum
        }
    }

    private data class MetricSnapshot(
        val count: Long,
        val absoluteTotal: Long,
        val mae: Double,
        val signedMean: Double,
        val medianAbsolute: Int,
        val p90: Int,
        val p95: Int,
        val p99: Int,
        val p999: Int,
        val within1: Long,
        val within2: Long,
        val within5: Long,
        val within10: Long,
        val greaterThanTen: Long,
        val greaterThanThirty: Long,
        val greaterThanSixty: Long,
        val maximum: Int,
        val affectedCityCount: Int,
        val affectedCityDayCount: Long,
        val longestConsecutiveGt10: Int
    ) {
        val greaterThanTenRate: Double
            get() = if (count == 0L) 0.0 else greaterThanTen.toDouble() / count
    }

    private data class ReferenceMetric(val mae: Double, val gt10: Long)

    private data class AnalysisRow(
        val city: City,
        val date: LocalDate,
        val event: PrayerEvent,
        val officialTime: String,
        val predictedTime: String,
        val signedErrorMinutes: Int,
        val trace: DiyanetCalculationTrace
    ) {
        val absoluteErrorMinutes: Int = abs(signedErrorMinutes)
    }

    private data class CityRows(val city: City, val rows: MutableList<AnalysisRow> = ArrayList(2_190))
    private data class MonthKey(val cityId: Int, val year: Int, val month: Int)

    private data class City(
        val cityId: Int,
        val countryCode: String,
        val countryName: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val zoneId: ZoneId,
        val matchQuality: String
    )

    private enum class PrayerEvent(val csvName: String, val read: (PrayerDayEntity) -> String) {
        FAJR("fajr", PrayerDayEntity::fajr),
        SUNRISE("sunrise", PrayerDayEntity::sunrise),
        DHUHR("dhuhr", PrayerDayEntity::dhuhr),
        ASR("asr", PrayerDayEntity::asr),
        MAGHRIB("maghrib", PrayerDayEntity::maghrib),
        ISHA("isha", PrayerDayEntity::isha)
    }

    private enum class Segment {
        A_ALL,
        B_NORTH_V14,
        C_TRUSTED_LONGITUDE_TIME_AXIS,
        D_TRUSTED_FULL_COORDINATE_AXIS,
        D_NORMAL_REGIME,
        E_SUSPECT_MAPPING,
        F_SOUTH_V9_FALLBACK,
        G_SYNTHETIC_AXIS_DIAGNOSTIC
    }

    private fun loadCities(path: Path): Map<Int, City> = Files.newBufferedReader(path).use { reader ->
        val iterator = reader.lineSequence().iterator()
        require(iterator.hasNext()) { "Empty city fixture: $path" }
        val headers = headerIndex(parseCsvLine(iterator.next()))
        buildMap {
            while (iterator.hasNext()) {
                val line = iterator.next()
                if (line.isBlank()) continue
                val fields = parseCsvLine(line)
                val countryName = fields[headers.getValue("country")]
                val city = City(
                    cityId = fields[headers.getValue("city_id")].toInt(),
                    countryCode = countryCode(countryName),
                    countryName = countryName,
                    name = fields[headers.getValue("city")],
                    latitude = fields[headers.getValue("latitude")].toDouble(),
                    longitude = fields[headers.getValue("longitude")].toDouble(),
                    zoneId = ZoneId.of(fields[headers.getValue("timezone")]),
                    matchQuality = fields[headers.getValue("match_quality")]
                )
                put(city.cityId, city)
            }
        }
    }

    private fun writeGoldenHeader(writer: BufferedWriter) = writer.appendLine(
        "city_id,country_code,country_name,city_name,date,latitude,longitude,zone_id,event," +
            "official_time,predicted_time,signed_error_minutes,absolute_error_minutes,engine_version," +
            "routing,regime,diagnostic,asr_source,direct_fajr_available,direct_isha_available," +
            "fajr_state,isha_state,axis_mode,polar_night,polar_day,utc_offset_seconds"
    )

    private fun writeGoldenRow(writer: BufferedWriter, row: AnalysisRow) = writeCsvRow(
        writer,
        row.city.cityId,
        row.city.countryCode,
        row.city.countryName,
        row.city.name,
        row.date,
        row.city.latitude,
        row.city.longitude,
        row.city.zoneId.id,
        row.event.csvName,
        row.officialTime,
        row.predictedTime,
        row.signedErrorMinutes,
        row.absoluteErrorMinutes,
        row.trace.engineVersion,
        row.trace.routing.name,
        row.trace.regime,
        diagnosticFor(row).orEmpty(),
        if (row.event == PrayerEvent.ASR) row.trace.asrSource.name else "",
        row.trace.directFajrAvailable,
        row.trace.directIshaAvailable,
        row.trace.fajrState,
        row.trace.ishaState,
        row.trace.axisMode,
        row.trace.polarNight,
        row.trace.polarDay,
        row.trace.utcOffsetSeconds
    )

    private fun diagnosticFor(row: AnalysisRow): String? {
        val values = row.trace.diagnostic?.split('|').orEmpty().filter { diagnostic ->
            row.event == PrayerEvent.ASR || diagnostic != "NORMAL_DAY_ASR_FALLBACK"
        }
        return values.joinToString("|").ifBlank { null }
    }

    private fun writeCityQualityHeader(writer: BufferedWriter) = writer.appendLine(
        "city_id,country_code,country_name,city_name,latitude,longitude,zone_id,match_quality," +
            "dhuhr_signed_median,dhuhr_absolute_median,sunrise_signed_median,sunrise_absolute_median," +
            "maghrib_signed_median,maghrib_absolute_median,fajr_signed_median,isha_signed_median," +
            "asr_signed_median,systematic_same_direction,systematic_direction,hour_clustered," +
            "hour_cluster_center_minutes,suspicious_mapping,suspicious_reasons,southern_fallback," +
            "north_v14,trusted_longitude_time_axis,trusted_full_coordinate_axis,any_gt10"
    )

    private fun writeCityQuality(writer: BufferedWriter, quality: CityQuality) = writeCsvRow(
        writer,
        quality.city.cityId,
        quality.city.countryCode,
        quality.city.countryName,
        quality.city.name,
        quality.city.latitude,
        quality.city.longitude,
        quality.city.zoneId.id,
        quality.city.matchQuality,
        quality.eventSignedMedians.getValue(PrayerEvent.DHUHR),
        quality.eventAbsoluteMedians.getValue(PrayerEvent.DHUHR),
        quality.eventSignedMedians.getValue(PrayerEvent.SUNRISE),
        quality.eventAbsoluteMedians.getValue(PrayerEvent.SUNRISE),
        quality.eventSignedMedians.getValue(PrayerEvent.MAGHRIB),
        quality.eventAbsoluteMedians.getValue(PrayerEvent.MAGHRIB),
        quality.eventSignedMedians.getValue(PrayerEvent.FAJR),
        quality.eventSignedMedians.getValue(PrayerEvent.ISHA),
        quality.eventSignedMedians.getValue(PrayerEvent.ASR),
        quality.systematicSameDirection,
        quality.systematicDirection,
        quality.hourClustered,
        quality.hourClusterCenter,
        quality.suspiciousMapping,
        quality.suspiciousReasons,
        quality.southernFallback,
        quality.northV14,
        quality.trustedLongitudeTimeAxis,
        quality.trustedFullCoordinateAxis,
        quality.anyGt10
    )

    private fun writeClusterHeader(writer: BufferedWriter) = writer.appendLine(
        "city_id,city_name,country_name,event,start_date,end_date,days,max_absolute_error," +
            "mean_signed_error,mean_absolute_error,error_direction,regime,diagnostic," +
            "direct_root_availability,polar_status"
    )

    private fun writeCsvRow(writer: BufferedWriter, vararg values: Any?) {
        values.forEachIndexed { index, value ->
            if (index > 0) writer.append(',')
            val text = value?.toString().orEmpty()
            if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                writer.append('"').append(text.replace("\"", "\"\"")).append('"')
            } else {
                writer.append(text)
            }
        }
        writer.newLine()
    }

    private fun largeWriter(path: Path): BufferedWriter = BufferedWriter(
        OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8),
        1 shl 20
    )

    private fun headerIndex(headers: List<String>): Map<String, Int> =
        headers.withIndex().associate { (index, header) -> header.trim() to index }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when {
                line[index] == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                line[index] == '"' -> quoted = !quoted
                line[index] == ',' && !quoted -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(line[index])
            }
            index++
        }
        fields += current.toString()
        return fields
    }

    private fun circularClockDelta(actual: String, official: String): Int {
        var delta = clockMinutes(actual) - clockMinutes(official)
        while (delta > HALF_DAY_MINUTES) delta -= DAY_MINUTES
        while (delta < -HALF_DAY_MINUTES) delta += DAY_MINUTES
        return delta
    }

    private fun clockMinutes(value: String): Int {
        val separator = value.indexOf(':')
        require(separator > 0) { "Invalid clock: $value" }
        return value.substring(0, separator).toInt() * 60 +
            value.substring(separator + 1, separator + 3).toInt()
    }

    private fun countryCode(countryName: String): String {
        val normalized = countryName.trim().uppercase(Locale.ROOT)
        COUNTRY_CODE_ALIASES[normalized]?.let { return it }
        return ISO_COUNTRY_CODES[normalized].orEmpty()
    }

    private fun decimal(value: Double): String = "%.5f".format(Locale.ROOT, value)
    private fun percent(value: Double): String = "%.5f%%".format(Locale.ROOT, value * 100.0)
    private fun duration(seconds: Double): String = "%02d:%02d:%02d".format(
        Locale.ROOT,
        (seconds / 3600).toInt(),
        ((seconds % 3600) / 60).toInt(),
        (seconds % 60).toInt()
    )
    private fun target(passed: Boolean): String = if (passed) "PASS" else "FAIL"

    private companion object {
        const val AUDIT_DIR_ENV = "DIYANET_GLOBAL_AUDIT_DIR"
        const val EXPECTED_CITY_COUNT = 3_040
        const val EXPECTED_COMPARISON_COUNT = 6_657_600L
        const val TOP_ERROR_COUNT = 100
        const val DAY_MINUTES = 24 * 60
        const val HALF_DAY_MINUTES = DAY_MINUTES / 2

        fun median(values: List<Int>): Int {
            require(values.isNotEmpty())
            val sorted = values.sorted()
            return sorted[sorted.size / 2]
        }

        val ISO_COUNTRY_CODES: Map<String, String> = Locale.getISOCountries().associateBy(
            keySelector = { code -> Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH).uppercase(Locale.ROOT) },
            valueTransform = { it }
        )
        val COUNTRY_CODE_ALIASES = mapOf(
            "USA" to "US",
            "UNITED STATES" to "US",
            "RUSSIA" to "RU",
            "SOUTH KOREA" to "KR",
            "NORTH KOREA" to "KP",
            "CZECH REPUBLIC" to "CZ",
            "KOSOVO" to "XK",
            "BOLIVIA" to "BO",
            "VENEZUELA" to "VE",
            "IRAN" to "IR",
            "SYRIA" to "SY",
            "TANZANIA" to "TZ",
            "MOLDOVA" to "MD",
            "VIETNAM" to "VN",
            "LAOS" to "LA",
            "BRUNEI" to "BN",
            "BOSNIA-HERZEGOVINA" to "BA",
            "ESTONYA" to "EE",
            "SIRBISTAN" to "RS"
        )
    }
}
