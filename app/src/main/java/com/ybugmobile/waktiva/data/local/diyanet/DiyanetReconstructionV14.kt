package com.ybugmobile.waktiva.data.local.diyanet

import com.ybugmobile.waktiva.data.local.diyanet.minutesAsDuration
import com.ybugmobile.waktiva.data.local.diyanet.toMinutesDouble
import org.shredzone.commons.suncalc.SunPosition
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.tanh

/**
 * Diyanet high-latitude reconstruction candidate V14.1.
 *
 * V14.1 keeps V14's prayer-night event ownership and Isha model. Fajr is revised
 * with three astronomy-defined pieces: a 16-degree summer anchor blended with
 * the direct -18° root, distance-to-missing-range shoulders for long polar
 * seasons, and a one-day pre-reappearance bridge. A UTC-offset transition guard
 * preserves the V13 result when an elapsed-duration interpolation would jump
 * across a daylight-saving discontinuity. The short-missing-range summer anchor
 * is direction-aware and limits its change from the frozen V14 result.
 *
 * No city names, city IDs, or city-specific constants are used. This remains a
 * reconstruction of the 2026 published tables, not Diyanet's source code.
 */
class DiyanetReconstructionV14(
    private val solarAstronomy: DiyanetAstronomyKernel = DiyanetAstronomyKernel(),
    private val nightAstronomy: DiyanetNightAstronomyKernel = DiyanetNightAstronomyKernel()
) {

    fun calculate(date: LocalDate, location: PrayerLocation): DiyanetV14Day {
        require(abs(location.latitude) >= MIN_ABS_LATITUDE) {
            "DiyanetReconstructionV14 was calibrated only for |latitude| >= $MIN_ABS_LATITUDE degrees"
        }

        val profile = HIGH_LATITUDE_PROFILE
        val annual = annualProfile(date.year, location, profile)
        val previousAxis = dailyAxis(date.minusDays(1), location, profile)
        val currentAxis = dailyAxis(date, location, profile)
        val nextAxis = dailyAxis(date.plusDays(1), location, profile)

        val sunrise = requireNotNull(currentAxis.prayerSunrise)
        val dhuhr = requireNotNull(currentAxis.prayerNoon)
        val maghrib = requireNotNull(currentAxis.prayerMaghrib)
        val previousMaghrib = requireNotNull(previousAxis.prayerMaghrib)
        val nextSunrise = requireNotNull(nextAxis.prayerSunrise)

        val previousNight = nightAstronomy.events(
            eveningDate = date.minusDays(1),
            prayerMaghrib = previousMaghrib,
            nextPrayerSunrise = sunrise,
            location = location,
            profile = profile
        )
        val currentNight = nightAstronomy.events(
            eveningDate = date,
            prayerMaghrib = maghrib,
            nextPrayerSunrise = nextSunrise,
            location = location,
            profile = profile
        )

        val previousPrayerNight = Duration.between(previousMaghrib.toInstant(), sunrise.toInstant())
        val nextPrayerNight = Duration.between(maghrib.toInstant(), nextSunrise.toInstant())
        val coreFajrGap = scaleDuration(previousPrayerNight, FAJR_ANGLE, NIGHT_ARC_DENOMINATOR)
        val coreIshaGap = scaleDuration(nextPrayerNight, ISHA_ANGLE, NIGHT_ARC_DENOMINATOR)

        val directFajr = previousNight.selectedNextFajr
        val directIsha = currentNight.selectedIsha
        val directFajrGap = directFajr?.let { Duration.between(it.toInstant(), sunrise.toInstant()) }
            ?.takeIf { !it.isNegative && !it.isZero }
        val directIshaGap = directIsha?.let { Duration.between(maghrib.toInstant(), it.toInstant()) }
            ?.takeIf { !it.isNegative && !it.isZero }

        val dayLengthTrend = annual.dayLengthTrendMinutesPerDay[date.dayOfYear - 1]
        val fajrThresholds = thresholds(
            calibration = FAJR_CALIBRATION,
            annualDirect = annual.fajrExistsAllYear,
            summerSeverityMinutes = annual.fajrSummerSeverityMinutes,
            missingDays = annual.fajrMissingDays,
            dayLengthTrendMinutesPerDay = dayLengthTrend
        )
        val ishaThresholds = thresholds(
            calibration = ISHA_CALIBRATION,
            annualDirect = annual.ishaExistsAllYear,
            summerSeverityMinutes = annual.ishaSummerSeverityMinutes,
            missingDays = annual.ishaMissingDays,
            dayLengthTrendMinutesPerDay = dayLengthTrend
        )

        val baseFajrGap = selectGap(directFajrGap, coreFajrGap, fajrThresholds)
        val fajrSelection = selectFajrGapV14(
            date = date,
            sunrise = sunrise,
            directGap = directFajrGap,
            coreGap = coreFajrGap,
            baseGap = baseFajrGap,
            annual = annual,
            nightMinimumSolarAltitudeDegrees = previousNight.minimumSolarAltitudeDegrees,
            dayLengthTrendMinutesPerDay = dayLengthTrend
        )
        val exactFajr = exactRecoveryFajr(
            date = date,
            sunrise = sunrise,
            directFajr = directFajr,
            coreFajrGap = coreFajrGap,
            annual = annual
        )
        val fajrGap = exactFajr?.let { Duration.between(it.toInstant(), sunrise.toInstant()) }
            ?: fajrSelection.gap
        val baseIshaGap = selectGap(directIshaGap, coreIshaGap, ishaThresholds)
        val ishaSelection = selectIshaGapV14(
            date = date,
            directGap = directIshaGap,
            coreGap = coreIshaGap,
            baseGap = baseIshaGap,
            annual = annual,
            nightMinimumSolarAltitudeDegrees = currentNight.minimumSolarAltitudeDegrees,
            dayLengthTrendMinutesPerDay = dayLengthTrend
        )
        val ishaGap = ishaSelection.gap

        val fajrState = resolveFajrState(
            date = date,
            directGap = directFajrGap,
            coreGap = coreFajrGap,
            thresholds = fajrThresholds,
            annual = annual,
            exactRecoveryFajr = exactFajr
        )
        val ishaState = resolveShoulderState(
            directGap = directIshaGap,
            coreGap = coreIshaGap,
            thresholds = ishaThresholds,
            dayLengthTrendMinutesPerDay = dayLengthTrend
        )
        val polarState = polarState(date, location, currentAxis.prayerNoon)

        return DiyanetV14Day(
            date = date,
            fajr = exactFajr ?: sunrise.minus(fajrGap),
            sunrise = sunrise,
            dhuhr = dhuhr,
            maghrib = maghrib,
            isha = maghrib.plus(ishaGap),
            polarNight = polarState == PolarState.POLAR_NIGHT,
            confidence = if (location.latitude >= MIN_ABS_LATITUDE) {
                DiyanetV14Confidence.CALIBRATED_NORTH_2026
            } else {
                DiyanetV14Confidence.EXPERIMENTAL_SOUTH
            },
            diagnostics = DiyanetV14Diagnostics(
                candidateVersion = CANDIDATE_VERSION,
                astronomyKernelVersion = DiyanetNightAstronomyKernel.KERNEL_VERSION,
                profileId = profile.profileId,
                axisMode = currentAxis.phase ?: "raw",
                coreNightBasis = "prayer_maghrib_to_prayer_sunrise",
                fajrState = fajrState,
                ishaState = ishaState,
                recoveryMode = annual.fajrRecoveryMode,
                dayLengthTrendMinutesPerDay = dayLengthTrend,
                fajrExistsAllYear = annual.fajrExistsAllYear,
                ishaExistsAllYear = annual.ishaExistsAllYear,
                fajrMissingDays = annual.fajrMissingDays,
                ishaMissingDays = annual.ishaMissingDays,
                fajrMissingRange = annual.fajrMissingRange,
                fajrReappearanceDate = annual.fajrReappearanceDate,
                fajrHoldEndExclusive = annual.fajrHoldEndExclusive,
                fajrSyntheticBackdate = annual.fajrSyntheticBackdate,
                fajrRoot = directFajr,
                fajrRootCivilDate = directFajr?.toLocalDate(),
                fajrRootBelongsToPreviousCivilDate = directFajr?.toLocalDate()?.isBefore(date) == true,
                fajrRootMinutesAfterPreviousMaghrib = directFajr?.let {
                    Duration.between(previousMaghrib.toInstant(), it.toInstant()).toMinutesDouble()
                },
                fajrNightMinimumSolarAltitudeDegrees = previousNight.minimumSolarAltitudeDegrees,
                fajrDirectGapMinutes = directFajrGap?.toMinutesDouble(),
                fajrCoreGapMinutes = coreFajrGap.toMinutesDouble(),
                fajrBaseGapMinutes = baseFajrGap.toMinutesDouble(),
                fajrOutputGapMinutes = fajrGap.toMinutesDouble(),
                fajrCorrectionMinutes = fajrSelection.correctionMinutes,
                fajrLegacyV14GapMinutes = fajrSelection.legacyV14GapMinutes,
                fajrAsymmetricAdjustmentMinutes = fajrSelection.asymmetricAdjustmentMinutes,
                fajrShoulderMode = fajrSelection.mode.name,
                fajrAnnualSummerMinimumSolarAltitudeDegrees = annual.fajrSummerMinimumSolarAltitudeDegrees,
                fajrPreRecoveryBridgeDate = annual.fajrPreRecoveryBridgeDate,
                fajrPreRecoveryBridgeAngleDegrees = annual.fajrPreRecoveryBridgeAngleDegrees,
                ishaDirectGapMinutes = directIshaGap?.toMinutesDouble(),
                ishaCoreGapMinutes = coreIshaGap.toMinutesDouble(),
                ishaBaseGapMinutes = baseIshaGap.toMinutesDouble(),
                ishaOutputGapMinutes = ishaGap.toMinutesDouble(),
                ishaCorrectionMinutes = ishaSelection.correctionMinutes,
                ishaShoulderMode = ishaSelection.mode.name,
                ishaNightMinimumSolarAltitudeDegrees = currentNight.minimumSolarAltitudeDegrees,
                ishaAnnualSummerMinimumSolarAltitudeDegrees = annual.ishaSummerMinimumSolarAltitudeDegrees,
                ishaMissingRange = annual.ishaMissingRange,
                fajrDirectLockMinutes = fajrThresholds.directLockMinutes,
                fajrCoreLockMinutes = fajrThresholds.coreLockMinutes,
                ishaDirectLockMinutes = ishaThresholds.directLockMinutes,
                ishaCoreLockMinutes = ishaThresholds.coreLockMinutes,
                polarState = polarState.name
            )
        )
    }

    fun dailyAxis(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile = HIGH_LATITUDE_PROFILE
    ): DiyanetPrayerAxis {
        val raw = solarAstronomy.rawEvents(date, location, profile)
        val noon = requireNotNull(raw.prayerNoon) { "Solar noon is required for $date at $location" }
        val sunrise = raw.prayerSunrise
        val maghrib = raw.prayerMaghrib

        if (sunrise == null || maghrib == null) {
            return when (polarState(date, location, noon)) {
                PolarState.POLAR_DAY -> boundedAxis(noon, LONG_DAY_HALF_MINUTES, "polar_day_19h")
                PolarState.POLAR_NIGHT -> boundedAxis(noon, SHORT_DAY_HALF_MINUTES, "polar_night_5h")
                PolarState.NORMAL -> boundedAxis(noon, SHORT_DAY_HALF_MINUTES, "missing_event_5h")
            }
        }

        val prayerDayMinutes = Duration.between(sunrise.toInstant(), maghrib.toInstant()).toMinutesDouble()
        return when {
            prayerDayMinutes < MIN_PRAYER_DAY_MINUTES ->
                boundedAxis(noon, SHORT_DAY_HALF_MINUTES, "short_day_5h")
            prayerDayMinutes > MAX_PRAYER_DAY_MINUTES ->
                boundedAxis(noon, LONG_DAY_HALF_MINUTES, "long_day_19h")
            else -> DiyanetPrayerAxis(
                prayerSunrise = sunrise,
                prayerNoon = noon,
                prayerMaghrib = maghrib,
                phase = "raw"
            )
        }
    }

    private fun boundedAxis(
        noon: ZonedDateTime,
        halfDayMinutes: Long,
        phase: String
    ): DiyanetPrayerAxis = DiyanetPrayerAxis(
        prayerSunrise = noon.minusMinutes(halfDayMinutes),
        prayerNoon = noon,
        prayerMaghrib = noon.plusMinutes(halfDayMinutes),
        phase = phase
    )

    private fun annualProfile(
        year: Int,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): AnnualTwilightProfile {
        val key = AnnualKey(
            year = year,
            latitudeE6 = (location.latitude * 1_000_000.0).roundToLong(),
            longitudeE6 = (location.longitude * 1_000_000.0).roundToLong(),
            zoneId = location.zoneId.id,
            elevationDecimeters = (location.calculationElevationMeters * 10.0).roundToLong(),
            profileId = profile.profileId,
            candidateVersion = CANDIDATE_VERSION
        )
        return annualCache.getOrPut(key) { buildAnnualProfile(year, location, profile) }
    }

    private fun buildAnnualProfile(
        year: Int,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): AnnualTwilightProfile {
        val firstDay = LocalDate.of(year, 1, 1)
        val endExclusive = LocalDate.of(year + 1, 1, 1)
        val dates = generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { it.isBefore(endExclusive) }
            .toList()

        val axisStart = firstDay.minusDays(TREND_RADIUS_DAYS + 2L)
        val axisEndExclusive = endExclusive.plusDays(TREND_RADIUS_DAYS + 2L)
        val axes = HashMap<LocalDate, DiyanetPrayerAxis>(dates.size + 24)
        var axisDate = axisStart
        while (axisDate.isBefore(axisEndExclusive)) {
            axes[axisDate] = dailyAxis(axisDate, location, profile)
            axisDate = axisDate.plusDays(1)
        }
        fun axis(day: LocalDate): DiyanetPrayerAxis = requireNotNull(axes[day])

        val nights = HashMap<LocalDate, DiyanetNightEvents>(dates.size + 2)
        var eveningDate = firstDay.minusDays(1)
        while (eveningDate.isBefore(endExclusive)) {
            nights[eveningDate] = nightAstronomy.events(
                eveningDate = eveningDate,
                prayerMaghrib = requireNotNull(axis(eveningDate).prayerMaghrib),
                nextPrayerSunrise = requireNotNull(axis(eveningDate.plusDays(1)).prayerSunrise),
                location = location,
                profile = profile
            )
            eveningDate = eveningDate.plusDays(1)
        }
        fun previousNight(day: LocalDate): DiyanetNightEvents = requireNotNull(nights[day.minusDays(1)])
        fun currentNight(day: LocalDate): DiyanetNightEvents = requireNotNull(nights[day])

        val dayLengthTrend = DoubleArray(dates.size)
        for ((index, date) in dates.withIndex()) {
            var derivativeSum = 0.0
            for (offset in -TREND_RADIUS_DAYS..TREND_RADIUS_DAYS) {
                val center = date.plusDays(offset.toLong())
                val previousLength = prayerDayLengthMinutes(axis(center.minusDays(1)))
                val nextLength = prayerDayLengthMinutes(axis(center.plusDays(1)))
                derivativeSum += (nextLength - previousLength) / 2.0
            }
            dayLengthTrend[index] = derivativeSum / (TREND_RADIUS_DAYS * 2 + 1)
        }

        val fajrExists = BooleanArray(dates.size)
        val ishaExists = BooleanArray(dates.size)
        var fajrSummerSeverity = Double.NEGATIVE_INFINITY
        var ishaSummerSeverity = Double.NEGATIVE_INFINITY
        var fajrSummerMinimumSolarAltitude = Double.NEGATIVE_INFINITY
        var ishaSummerMinimumSolarAltitude = Double.NEGATIVE_INFINITY

        for ((index, date) in dates.withIndex()) {
            val currentAxis = axis(date)
            val previousAxis = axis(date.minusDays(1))
            val nextAxis = axis(date.plusDays(1))
            val sunrise = requireNotNull(currentAxis.prayerSunrise)
            val maghrib = requireNotNull(currentAxis.prayerMaghrib)
            val previousNightEvents = previousNight(date)
            val fajr = previousNightEvents.selectedNextFajr
            fajrSummerMinimumSolarAltitude = maxOf(
                fajrSummerMinimumSolarAltitude,
                previousNightEvents.minimumSolarAltitudeDegrees
            )
            val currentNightEvents = currentNight(date)
            val isha = currentNightEvents.selectedIsha
            ishaSummerMinimumSolarAltitude = maxOf(
                ishaSummerMinimumSolarAltitude,
                currentNightEvents.minimumSolarAltitudeDegrees
            )
            fajrExists[index] = fajr != null
            ishaExists[index] = isha != null

            val fajrCore = scaleDuration(
                Duration.between(requireNotNull(previousAxis.prayerMaghrib).toInstant(), sunrise.toInstant()),
                FAJR_ANGLE,
                NIGHT_ARC_DENOMINATOR
            )
            val ishaCore = scaleDuration(
                Duration.between(maghrib.toInstant(), requireNotNull(nextAxis.prayerSunrise).toInstant()),
                ISHA_ANGLE,
                NIGHT_ARC_DENOMINATOR
            )

            fajr?.let {
                val directGap = Duration.between(it.toInstant(), sunrise.toInstant()).toMinutesDouble()
                fajrSummerSeverity = maxOf(fajrSummerSeverity, directGap - fajrCore.toMinutesDouble())
            }
            isha?.let {
                val directGap = Duration.between(maghrib.toInstant(), it.toInstant()).toMinutesDouble()
                ishaSummerSeverity = maxOf(ishaSummerSeverity, directGap - ishaCore.toMinutesDouble())
            }
        }

        val fajrMissingRange = dominantMissingRange(dates, fajrExists)
        val ishaMissingRange = dominantMissingRange(dates, ishaExists)
        val reappearanceDate = fajrMissingRange?.end?.plusDays(1)
            ?.takeIf { it.year == year }
            ?.takeIf { candidate -> hasConsecutiveRoots(candidate, dates, fajrExists, RECOVERY_CONFIRMATION_DAYS) }

        var recoveryMode = DiyanetV14FajrRecoveryMode.NONE
        var holdClock: LocalTime? = null
        var holdEndExclusive: LocalDate? = null
        var syntheticBackdate: LocalDate? = null
        var syntheticClock: LocalTime? = null

        if (reappearanceDate != null) {
            val root = previousNight(reappearanceDate).selectedNextFajr
            val noon = axis(reappearanceDate).prayerNoon
            if (root != null && noon != null) {
                val rootBelongsToPreviousDate = root.toLocalDate().isBefore(reappearanceDate)
                val rootMinutes = civilClockMinutes(root)
                val sameDateNearMidnight =
                    root.toLocalDate() == reappearanceDate && rootMinutes <= SAME_DATE_ROOT_WINDOW_MINUTES
                val earlyPrayerNoon = civilClockMinutes(noon) < EARLY_PRAYER_NOON_CUTOFF_MINUTES
                val twilightDepth = -FAJR_ANGLE - previousNight(reappearanceDate).minimumSolarAltitudeDegrees
                val robustSameDateRoot = sameDateNearMidnight && twilightDepth >= SAME_DATE_MINIMUM_DEPTH_DEGREES

                if (earlyPrayerNoon && (rootBelongsToPreviousDate || robustSameDateRoot)) {
                    val extremeHold = rootBelongsToPreviousDate &&
                        fajrMissingRange.lengthDays > EXTREME_HOLD_MIN_MISSING_DAYS &&
                        abs(location.latitude) > EXTREME_HOLD_MIN_ABS_LATITUDE

                    recoveryMode = if (extremeHold) {
                        DiyanetV14FajrRecoveryMode.PREVIOUS_DATE_HOLD
                    } else {
                        DiyanetV14FajrRecoveryMode.DIRECT_REAPPEARANCE
                    }

                    if (extremeHold) {
                        holdClock = root.plusMinutes(REAPPEARANCE_TEMKIN_MINUTES).toLocalTime()
                        holdEndExclusive = findStableMorningRootDate(
                            start = reappearanceDate,
                            endExclusive = endExclusive,
                            dates = dates,
                            rootsExist = fajrExists,
                            rootProvider = { previousNight(it).selectedNextFajr }
                        )
                    }

                    if (robustSameDateRoot) {
                        syntheticBackdate = reappearanceDate.minusDays(1)
                        syntheticClock = root.minusMinutes(ROOT_DATE_UNCERTAINTY_MINUTES).toLocalTime()
                    }
                }
            }
        }

        val preRecoveryBridgeDate = if (recoveryMode != DiyanetV14FajrRecoveryMode.NONE) {
            (syntheticBackdate?.minusDays(1) ?: reappearanceDate?.minusDays(1))
                ?.takeIf { it.year == year }
        } else {
            null
        }
        val preRecoveryBridgeAngle = when (recoveryMode) {
            DiyanetV14FajrRecoveryMode.PREVIOUS_DATE_HOLD -> PRE_RECOVERY_EXTREME_ANGLE
            DiyanetV14FajrRecoveryMode.DIRECT_REAPPEARANCE -> PRE_RECOVERY_STANDARD_ANGLE
            DiyanetV14FajrRecoveryMode.NONE -> null
        }

        return AnnualTwilightProfile(
            fajrExistsAllYear = fajrExists.all { it },
            ishaExistsAllYear = ishaExists.all { it },
            fajrMissingDays = fajrExists.count { !it },
            ishaMissingDays = ishaExists.count { !it },
            fajrSummerSeverityMinutes = fajrSummerSeverity.takeIf { it.isFinite() } ?: 0.0,
            ishaSummerSeverityMinutes = ishaSummerSeverity.takeIf { it.isFinite() } ?: 0.0,
            fajrSummerMinimumSolarAltitudeDegrees = fajrSummerMinimumSolarAltitude
                .takeIf { it.isFinite() } ?: Double.NaN,
            ishaSummerMinimumSolarAltitudeDegrees = ishaSummerMinimumSolarAltitude
                .takeIf { it.isFinite() } ?: Double.NaN,
            dayLengthTrendMinutesPerDay = dayLengthTrend,
            fajrMissingRange = fajrMissingRange,
            ishaMissingRange = ishaMissingRange,
            fajrReappearanceDate = reappearanceDate,
            fajrRecoveryMode = recoveryMode,
            fajrHoldClock = holdClock,
            fajrHoldEndExclusive = holdEndExclusive,
            fajrSyntheticBackdate = syntheticBackdate,
            fajrSyntheticClock = syntheticClock,
            fajrPreRecoveryBridgeDate = preRecoveryBridgeDate,
            fajrPreRecoveryBridgeAngleDegrees = preRecoveryBridgeAngle
        )
    }

    private fun dominantMissingRange(
        dates: List<LocalDate>,
        exists: BooleanArray
    ): DiyanetDateRange? {
        var bestStart = -1
        var bestEnd = -1
        var runStart = -1

        for (index in exists.indices) {
            if (!exists[index] && runStart < 0) runStart = index
            val runEnds = runStart >= 0 && (exists[index] || index == exists.lastIndex)
            if (runEnds) {
                val runEnd = if (exists[index]) index - 1 else index
                if (bestStart < 0 || runEnd - runStart > bestEnd - bestStart) {
                    bestStart = runStart
                    bestEnd = runEnd
                }
                runStart = -1
            }
        }

        return if (bestStart >= 0) DiyanetDateRange(dates[bestStart], dates[bestEnd]) else null
    }

    private fun hasConsecutiveRoots(
        start: LocalDate,
        dates: List<LocalDate>,
        exists: BooleanArray,
        count: Int
    ): Boolean {
        val startIndex = dates.indexOf(start)
        if (startIndex < 0 || startIndex + count > exists.size) return false
        return (startIndex until startIndex + count).all { exists[it] }
    }

    private fun findStableMorningRootDate(
        start: LocalDate,
        endExclusive: LocalDate,
        dates: List<LocalDate>,
        rootsExist: BooleanArray,
        rootProvider: (LocalDate) -> ZonedDateTime?
    ): LocalDate? {
        var candidate = start
        while (candidate.plusDays((RECOVERY_CONFIRMATION_DAYS - 1).toLong()).isBefore(endExclusive)) {
            val stable = (0 until RECOVERY_CONFIRMATION_DAYS).all { offset ->
                val day = candidate.plusDays(offset.toLong())
                val index = dates.indexOf(day)
                val root = rootProvider(day)
                index >= 0 && rootsExist[index] && root != null &&
                    !root.toLocalTime().isBefore(STABLE_MORNING_ROOT_TIME) &&
                    root.toLocalTime().isBefore(LocalTime.NOON)
            }
            if (stable) return candidate
            candidate = candidate.plusDays(1)
        }
        return null
    }

    private fun exactRecoveryFajr(
        date: LocalDate,
        sunrise: ZonedDateTime,
        directFajr: ZonedDateTime?,
        coreFajrGap: Duration,
        annual: AnnualTwilightProfile
    ): ZonedDateTime? {
        if (
            date == annual.fajrPreRecoveryBridgeDate &&
            annual.fajrPreRecoveryBridgeAngleDegrees != null
        ) {
            val bridgeGap = scaleDuration(
                coreFajrGap,
                annual.fajrPreRecoveryBridgeAngleDegrees,
                FAJR_ANGLE
            )
            return sunrise.minus(bridgeGap)
        }

        if (date == annual.fajrSyntheticBackdate && annual.fajrSyntheticClock != null) {
            return occurrenceBefore(sunrise, annual.fajrSyntheticClock)
        }

        val reappearance = annual.fajrReappearanceDate ?: return null
        if (date.isBefore(reappearance)) return null

        return when (annual.fajrRecoveryMode) {
            DiyanetV14FajrRecoveryMode.NONE -> null
            DiyanetV14FajrRecoveryMode.DIRECT_REAPPEARANCE -> directFajr
            DiyanetV14FajrRecoveryMode.PREVIOUS_DATE_HOLD -> {
                val holdEnd = annual.fajrHoldEndExclusive
                if (holdEnd == null || date.isBefore(holdEnd)) {
                    annual.fajrHoldClock?.let { occurrenceBefore(sunrise, it) }
                } else {
                    directFajr
                }
            }
        }
    }

    private fun occurrenceBefore(reference: ZonedDateTime, localTime: LocalTime): ZonedDateTime {
        var candidate = reference.toLocalDate().atTime(localTime).atZone(reference.zone)
        while (!candidate.toInstant().isBefore(reference.toInstant())) candidate = candidate.minusDays(1)
        while (Duration.between(candidate.toInstant(), reference.toInstant()) > Duration.ofDays(1)) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    private fun resolveFajrState(
        date: LocalDate,
        directGap: Duration?,
        coreGap: Duration,
        thresholds: ShoulderThresholds,
        annual: AnnualTwilightProfile,
        exactRecoveryFajr: ZonedDateTime?
    ): DiyanetV14TwilightState {
        if (date == annual.fajrPreRecoveryBridgeDate) {
            return DiyanetV14TwilightState.PRE_REAPPEARANCE_BRIDGE
        }
        if (date == annual.fajrSyntheticBackdate) return DiyanetV14TwilightState.DATE_OWNERSHIP_GUARD
        if (exactRecoveryFajr != null) {
            return if (
                annual.fajrRecoveryMode == DiyanetV14FajrRecoveryMode.PREVIOUS_DATE_HOLD &&
                (annual.fajrHoldEndExclusive == null || date.isBefore(annual.fajrHoldEndExclusive))
            ) {
                DiyanetV14TwilightState.PREVIOUS_DATE_HOLD
            } else {
                DiyanetV14TwilightState.DIRECT
            }
        }

        val missing = annual.fajrMissingRange
        if (missing != null) {
            if (date in missing) return DiyanetV14TwilightState.ESTIMATED
            if (date.isBefore(missing.start)) return DiyanetV14TwilightState.TRANSITION_TO_ESTIMATED
            if (date.isAfter(missing.end)) return DiyanetV14TwilightState.TRANSITION_TO_DIRECT
        }
        return resolveShoulderState(directGap, coreGap, thresholds, 0.0)
    }

    private fun resolveShoulderState(
        directGap: Duration?,
        coreGap: Duration,
        thresholds: ShoulderThresholds,
        dayLengthTrendMinutesPerDay: Double
    ): DiyanetV14TwilightState {
        if (directGap == null) return DiyanetV14TwilightState.ESTIMATED
        val excess = directGap.toMinutesDouble() - coreGap.toMinutesDouble()
        if (excess <= thresholds.directLockMinutes) return DiyanetV14TwilightState.DIRECT
        if (excess >= thresholds.coreLockMinutes) return DiyanetV14TwilightState.ESTIMATED
        return if (dayLengthTrendMinutesPerDay >= 0.0) {
            DiyanetV14TwilightState.TRANSITION_TO_ESTIMATED
        } else {
            DiyanetV14TwilightState.TRANSITION_TO_DIRECT
        }
    }

    private fun prayerDayLengthMinutes(axis: DiyanetPrayerAxis): Double = Duration.between(
        requireNotNull(axis.prayerSunrise).toInstant(),
        requireNotNull(axis.prayerMaghrib).toInstant()
    ).toMinutesDouble()

    private fun thresholds(
        calibration: ShoulderCalibration,
        annualDirect: Boolean,
        summerSeverityMinutes: Double,
        missingDays: Int,
        dayLengthTrendMinutesPerDay: Double
    ): ShoulderThresholds {
        val approachingWeight = 0.5 *
            (1.0 + tanh(dayLengthTrendMinutesPerDay / calibration.seasonTrendScaleMinutesPerDay))

        val directLock: Double
        val shoulderWidth: Double
        if (annualDirect) {
            val severityDelta = summerSeverityMinutes - calibration.severityReferenceMinutes
            val approachingDirectLock = (
                calibration.approachingDirectLockReferenceMinutes -
                    calibration.directLockSeveritySlope * severityDelta
                ).coerceIn(MIN_DIRECT_LOCK_MINUTES, MAX_DIRECT_LOCK_MINUTES)
            val recedingDirectLock = (
                calibration.recedingDirectLockReferenceMinutes -
                    calibration.directLockSeveritySlope * severityDelta
                ).coerceIn(MIN_DIRECT_LOCK_MINUTES, MAX_DIRECT_LOCK_MINUTES)
            val approachingWidth = (
                calibration.approachingShoulderWidthReferenceMinutes -
                    calibration.shoulderWidthSeveritySlope * severityDelta
                ).coerceIn(MIN_SHOULDER_WIDTH_MINUTES, MAX_ANNUAL_SHOULDER_WIDTH_MINUTES)
            val recedingWidth = (
                calibration.recedingShoulderWidthReferenceMinutes -
                    calibration.shoulderWidthSeveritySlope * severityDelta
                ).coerceIn(MIN_SHOULDER_WIDTH_MINUTES, MAX_ANNUAL_SHOULDER_WIDTH_MINUTES)
            directLock = lerp(recedingDirectLock, approachingDirectLock, approachingWeight)
            shoulderWidth = lerp(recedingWidth, approachingWidth, approachingWeight)
        } else {
            directLock = (
                calibration.missingDirectLockInterceptMinutes +
                    calibration.missingDirectLockPerDayMinutes * missingDays
                ).coerceIn(MIN_MISSING_DIRECT_LOCK_MINUTES, MAX_MISSING_DIRECT_LOCK_MINUTES)
            shoulderWidth = (
                calibration.missingShoulderWidthInterceptMinutes +
                    calibration.missingShoulderWidthPerDayMinutes * missingDays
                ).coerceIn(MIN_MISSING_SHOULDER_WIDTH_MINUTES, MAX_MISSING_SHOULDER_WIDTH_MINUTES)
        }

        return ShoulderThresholds(
            directLockMinutes = directLock,
            coreLockMinutes = directLock + shoulderWidth,
            approachingSummerWeight = approachingWeight
        )
    }

    private fun selectGap(
        directGap: Duration?,
        coreGap: Duration,
        thresholds: ShoulderThresholds
    ): Duration {
        if (directGap == null) return coreGap
        val directMinutes = directGap.toMinutesDouble()
        val coreMinutes = coreGap.toMinutesDouble()
        val excess = directMinutes - coreMinutes
        val a = thresholds.directLockMinutes
        val b = thresholds.coreLockMinutes
        val outputMinutes = when {
            excess <= 0.0 -> directMinutes
            excess <= a -> directMinutes
            excess >= b -> coreMinutes
            else -> coreMinutes + a * (b - excess) / (b - a)
        }
        return minutesAsDuration(outputMinutes)
    }

    private fun selectFajrGapV14(
        date: LocalDate,
        sunrise: ZonedDateTime,
        directGap: Duration?,
        coreGap: Duration,
        baseGap: Duration,
        annual: AnnualTwilightProfile,
        nightMinimumSolarAltitudeDegrees: Double,
        dayLengthTrendMinutesPerDay: Double
    ): FajrSelection {
        if (directGap == null) {
            return FajrSelection(baseGap, 0.0, FajrShoulderMode.V13_BASELINE)
        }

        val directMinutes = directGap.toMinutesDouble()
        val coreMinutes = coreGap.toMinutesDouble()
        val baseMinutes = baseGap.toMinutesDouble()
        if (directMinutes <= coreMinutes) {
            return FajrSelection(directGap, directMinutes - baseMinutes, FajrShoulderMode.DIRECT_NOT_LATER_THAN_CORE)
        }

        val summerMinimum = annual.fajrSummerMinimumSolarAltitudeDegrees
        if (
            annual.fajrMissingDays <= SUMMER_ANCHOR_MAX_MISSING_DAYS &&
            summerMinimum in SUMMER_ANCHOR_MIN_ALTITUDE..SUMMER_ANCHOR_MAX_ALTITUDE
        ) {
            val depthFromSummer = (summerMinimum - nightMinimumSolarAltitudeDegrees)
                .coerceAtLeast(0.0)
            val activation = smoothStep01(
                1.0 - depthFromSummer / SUMMER_ANCHOR_ACTIVATION_SPAN_DEGREES
            )
            val directWeight = (
                (SUMMER_ANCHOR_ZERO_ALTITUDE - summerMinimum) /
                    SUMMER_ANCHOR_BLEND_SPAN_DEGREES
                ).coerceIn(0.0, 1.0)
            val sixteenDegreeCore = scaleDuration(coreGap, SUMMER_ANCHOR_CORE_ANGLE, FAJR_ANGLE)
                .toMinutesDouble()
            val solsticeTarget = lerp(sixteenDegreeCore, directMinutes, directWeight)
            val legacyV14Output = lerp(baseMinutes, solsticeTarget, activation)

            // The published shoulder is asymmetric. Preserve the V14 correction
            // while days lengthen, damp it as days shorten, fade the change where
            // the trend direction is ambiguous, and use annual solar depth instead
            // of city/country identifiers for the latitude-like adjustment.
            val approachingWeight = 0.5 * (
                1.0 + tanh(
                    dayLengthTrendMinutesPerDay /
                        SUMMER_ASYMMETRY_TREND_SCALE_MINUTES_PER_DAY
                )
            )
            val altitudeOffset = activation * SUMMER_ASYMMETRY_ALTITUDE_SLOPE_MINUTES_PER_DEGREE *
                (summerMinimum - SUMMER_ASYMMETRY_REFERENCE_ALTITUDE_DEGREES)
            val asymmetricTarget = baseMinutes +
                (legacyV14Output - baseMinutes) * approachingWeight +
                altitudeOffset
            val trendMagnitude = smoothStep01(
                abs(dayLengthTrendMinutesPerDay) /
                    SUMMER_ASYMMETRY_TREND_FADE_MINUTES_PER_DAY
            )
            val requestedAdjustment = (asymmetricTarget - legacyV14Output) * trendMagnitude
            val boundedAdjustment = requestedAdjustment.coerceIn(
                -SUMMER_ASYMMETRY_MAX_CHANGE_MINUTES,
                SUMMER_ASYMMETRY_MAX_CHANGE_MINUTES
            )
            val output = legacyV14Output + boundedAdjustment
            return FajrSelection(
                gap = minutesAsDuration(output),
                correctionMinutes = output - baseMinutes,
                mode = FajrShoulderMode.SIXTEEN_DEGREE_SUMMER_ANCHOR_ASYMMETRIC,
                legacyV14GapMinutes = legacyV14Output,
                asymmetricAdjustmentMinutes = boundedAdjustment
            )
        }

        val missingRange = annual.fajrMissingRange
        if (
            annual.fajrMissingDays >= LONG_MISSING_RANGE_MIN_DAYS &&
            missingRange != null &&
            date !in missingRange
        ) {
            val approaching = date.isBefore(missingRange.start)
            val distanceDays = if (approaching) {
                java.time.temporal.ChronoUnit.DAYS.between(date, missingRange.start).toDouble()
            } else {
                java.time.temporal.ChronoUnit.DAYS.between(missingRange.end, date).toDouble()
            }
            val normalizedMissing = (annual.fajrMissingDays - LONG_MISSING_REFERENCE_DAYS) /
                LONG_MISSING_NORMALIZATION_DAYS
            val parameters = if (approaching) {
                APPROACHING_FAJR_DISTANCE_SHOULDER
            } else {
                RECEDING_FAJR_DISTANCE_SHOULDER
            }
            val center = parameters.centerDays + parameters.centerMissingSlope * normalizedMissing
            val scale = kotlin.math.exp(
                parameters.logScale + parameters.logScaleMissingSlope * normalizedMissing
            )
            val directWeight = logistic((distanceDays - center) / scale)
            val output = lerp(coreMinutes, directMinutes, directWeight)
            val candidate = minutesAsDuration(output)
            val baseTime = sunrise.minus(baseGap)
            val candidateTime = sunrise.minus(candidate)
            if (baseTime.offset != candidateTime.offset) {
                return FajrSelection(baseGap, 0.0, FajrShoulderMode.UTC_OFFSET_TRANSITION_GUARD)
            }
            return FajrSelection(
                gap = candidate,
                correctionMinutes = output - baseMinutes,
                mode = if (approaching) {
                    FajrShoulderMode.APPROACHING_MISSING_RANGE_BY_DISTANCE
                } else {
                    FajrShoulderMode.RECEDING_MISSING_RANGE_BY_DISTANCE
                }
            )
        }

        return FajrSelection(baseGap, 0.0, FajrShoulderMode.V13_BASELINE)
    }

    private fun smoothStep01(value: Double): Double {
        val x = value.coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    private fun selectIshaGapV14(
        date: LocalDate,
        directGap: Duration?,
        coreGap: Duration,
        baseGap: Duration,
        annual: AnnualTwilightProfile,
        nightMinimumSolarAltitudeDegrees: Double,
        dayLengthTrendMinutesPerDay: Double
    ): IshaSelection {
        if (directGap == null) {
            return IshaSelection(
                gap = coreGap,
                correctionMinutes = coreGap.toMinutesDouble() - baseGap.toMinutesDouble(),
                mode = IshaShoulderMode.CORE_WHILE_ROOT_MISSING
            )
        }

        val directMinutes = directGap.toMinutesDouble()
        val baseMinutes = baseGap.toMinutesDouble()
        val summerMinimum = annual.ishaSummerMinimumSolarAltitudeDegrees

        if (annual.ishaExistsAllYear) {
            var outputMinutes = baseMinutes
            var totalCorrection = 0.0
            var mode = IshaShoulderMode.V12_BASELINE

            if (summerMinimum in ANNUAL_SHALLOW_18_ALTITUDE_LOW..ANNUAL_SHALLOW_18_ALTITUDE_HIGH) {
                val nightActivation = logistic(
                    (nightMinimumSolarAltitudeDegrees - ANNUAL_SUNSET_LIMIT_NIGHT_CENTER_DEGREES) /
                        ANNUAL_SUNSET_LIMIT_NIGHT_SCALE_DEGREES
                )
                val approachingWeight = 0.5 * (
                    1.0 + tanh(
                        dayLengthTrendMinutesPerDay /
                            ANNUAL_SUNSET_LIMIT_TREND_SCALE_MINUTES_PER_DAY
                    )
                )
                val correctionFraction = lerp(
                    ANNUAL_SUNSET_LIMIT_RECEDING_FRACTION,
                    ANNUAL_SUNSET_LIMIT_APPROACHING_FRACTION,
                    approachingWeight
                )
                val availableCorrection = (outputMinutes - ANNUAL_SUNSET_LIMIT_GAP_MINUTES)
                    .coerceIn(0.0, ANNUAL_SUNSET_LIMIT_MAX_SHIFT_MINUTES)
                val correction = nightActivation * correctionFraction * availableCorrection
                outputMinutes -= correction
                totalCorrection -= correction
                mode = IshaShoulderMode.ANNUAL_SHALLOW_18_SUNSET_LIMIT
            }

            if (summerMinimum in ANNUAL_NEAR_LOSS_ALTITUDE_LOW..ANNUAL_NEAR_LOSS_ALTITUDE_HIGH) {
                val twilightDepth = -ISHA_ANGLE - nightMinimumSolarAltitudeDegrees
                val rootActivation = logistic(
                    (twilightDepth - ANNUAL_NEAR_LOSS_DEPTH_CENTER_DEGREES) /
                        ANNUAL_NEAR_LOSS_DEPTH_SCALE_DEGREES
                )
                val recedingActivation = logistic(
                    (-dayLengthTrendMinutesPerDay - ANNUAL_NEAR_LOSS_TREND_CENTER) /
                        ANNUAL_NEAR_LOSS_TREND_SCALE
                )
                val desiredDirect = directMinutes + ANNUAL_NEAR_LOSS_DIRECT_OFFSET_MINUTES
                val availableCorrection = (desiredDirect - outputMinutes)
                    .coerceIn(0.0, ANNUAL_NEAR_LOSS_MAX_SHIFT_MINUTES)
                val correction = rootActivation * recedingActivation * availableCorrection
                outputMinutes += correction
                totalCorrection += correction
                mode = IshaShoulderMode.ANNUAL_NEAR_LOSS_DIRECT_RECOVERY
            }

            return IshaSelection(
                gap = minutesAsDuration(outputMinutes),
                correctionMinutes = totalCorrection,
                mode = mode
            )
        }

        val missingRange = annual.ishaMissingRange
            ?: return IshaSelection(baseGap, 0.0, IshaShoulderMode.V12_BASELINE)
        if (date in missingRange) {
            return IshaSelection(coreGap, coreGap.toMinutesDouble() - baseMinutes, IshaShoulderMode.CORE_WHILE_ROOT_MISSING)
        }

        val approachingMissing = date.isBefore(missingRange.start)
        val polarDayRegime = summerMinimum > 0.0
        val parameters = when {
            polarDayRegime && approachingMissing -> POLAR_APPROACHING_ISHA_SHOULDER
            polarDayRegime -> POLAR_RECEDING_ISHA_SHOULDER
            approachingMissing -> APPROACHING_ISHA_SHOULDER
            else -> RECEDING_ISHA_SHOULDER
        }
        val twilightDepth = -ISHA_ANGLE - nightMinimumSolarAltitudeDegrees
        val activation = logistic(
            (twilightDepth - parameters.depthCenterDegrees) /
                parameters.depthScaleDegrees
        )
        val desiredDirect = directMinutes + parameters.directOffsetMinutes
        val availableCorrection = (desiredDirect - baseMinutes)
            .coerceIn(0.0, parameters.maxLateCorrectionMinutes)
        val correction = activation * availableCorrection

        return IshaSelection(
            gap = minutesAsDuration(baseMinutes + correction),
            correctionMinutes = correction,
            mode = when {
                polarDayRegime && approachingMissing -> IshaShoulderMode.POLAR_APPROACHING_DIRECT
                polarDayRegime -> IshaShoulderMode.POLAR_RECEDING_DIRECT
                approachingMissing -> IshaShoulderMode.APPROACHING_DIRECT_ROBUSTNESS
                else -> IshaShoulderMode.RECEDING_DIRECT_ROBUSTNESS
            }
        )
    }

    private fun logistic(value: Double): Double = 1.0 / (1.0 + kotlin.math.exp(-value.coerceIn(-40.0, 40.0)))

    private fun polarState(
        date: LocalDate,
        location: PrayerLocation,
        noon: ZonedDateTime?
    ): PolarState {
        val reference = noon ?: date.atTime(12, 0).atZone(location.zoneId)
        val altitude = SunPosition.compute()
            .timezone(location.zoneId)
            .on(reference.toInstant())
            .at(location.latitude, location.longitude)
            .elevation(location.calculationElevationMeters)
            .execute()
            .trueAltitude
        val raw = solarAstronomy.rawEvents(date, location, HIGH_LATITUDE_PROFILE)
        return if (raw.astronomicalSunrise == null || raw.astronomicalSunset == null) {
            if (altitude > 0.0) PolarState.POLAR_DAY else PolarState.POLAR_NIGHT
        } else {
            PolarState.NORMAL
        }
    }

    private fun scaleDuration(duration: Duration, numerator: Double, denominator: Double): Duration {
        val nanos = duration.seconds * 1_000_000_000.0 + duration.nano
        return Duration.ofNanos((nanos * numerator / denominator).roundToLong())
    }

    private fun civilClockMinutes(value: ZonedDateTime): Double =
        value.hour * 60.0 + value.minute + value.second / 60.0 + value.nano / 60_000_000_000.0

    private fun lerp(start: Double, end: Double, weight: Double): Double =
        start + (end - start) * weight

    private data class AnnualKey(
        val year: Int,
        val latitudeE6: Long,
        val longitudeE6: Long,
        val zoneId: String,
        val elevationDecimeters: Long,
        val profileId: String,
        val candidateVersion: String
    )

    private data class AnnualTwilightProfile(
        val fajrExistsAllYear: Boolean,
        val ishaExistsAllYear: Boolean,
        val fajrMissingDays: Int,
        val ishaMissingDays: Int,
        val fajrSummerSeverityMinutes: Double,
        val ishaSummerSeverityMinutes: Double,
        val fajrSummerMinimumSolarAltitudeDegrees: Double,
        val ishaSummerMinimumSolarAltitudeDegrees: Double,
        val dayLengthTrendMinutesPerDay: DoubleArray,
        val fajrMissingRange: DiyanetDateRange?,
        val ishaMissingRange: DiyanetDateRange?,
        val fajrReappearanceDate: LocalDate?,
        val fajrRecoveryMode: DiyanetV14FajrRecoveryMode,
        val fajrHoldClock: LocalTime?,
        val fajrHoldEndExclusive: LocalDate?,
        val fajrSyntheticBackdate: LocalDate?,
        val fajrSyntheticClock: LocalTime?,
        val fajrPreRecoveryBridgeDate: LocalDate?,
        val fajrPreRecoveryBridgeAngleDegrees: Double?
    )

    private data class FajrSelection(
        val gap: Duration,
        val correctionMinutes: Double,
        val mode: FajrShoulderMode,
        val legacyV14GapMinutes: Double? = null,
        val asymmetricAdjustmentMinutes: Double = 0.0
    )

    private data class FajrDistanceShoulderParameters(
        val centerDays: Double,
        val centerMissingSlope: Double,
        val logScale: Double,
        val logScaleMissingSlope: Double
    )

    private enum class FajrShoulderMode {
        V13_BASELINE,
        DIRECT_NOT_LATER_THAN_CORE,
        SIXTEEN_DEGREE_SUMMER_ANCHOR_ASYMMETRIC,
        APPROACHING_MISSING_RANGE_BY_DISTANCE,
        RECEDING_MISSING_RANGE_BY_DISTANCE,
        UTC_OFFSET_TRANSITION_GUARD
    }

    private data class IshaSelection(
        val gap: Duration,
        val correctionMinutes: Double,
        val mode: IshaShoulderMode
    )

    private data class IshaShoulderParameters(
        val depthCenterDegrees: Double,
        val depthScaleDegrees: Double,
        val directOffsetMinutes: Double,
        val maxLateCorrectionMinutes: Double
    )

    private enum class IshaShoulderMode {
        V12_BASELINE,
        CORE_WHILE_ROOT_MISSING,
        ANNUAL_SHALLOW_18_SUNSET_LIMIT,
        ANNUAL_NEAR_LOSS_DIRECT_RECOVERY,
        APPROACHING_DIRECT_ROBUSTNESS,
        RECEDING_DIRECT_ROBUSTNESS,
        POLAR_APPROACHING_DIRECT,
        POLAR_RECEDING_DIRECT
    }

    private data class ShoulderThresholds(
        val directLockMinutes: Double,
        val coreLockMinutes: Double,
        val approachingSummerWeight: Double
    )

    private data class ShoulderCalibration(
        val severityReferenceMinutes: Double,
        val approachingDirectLockReferenceMinutes: Double,
        val approachingShoulderWidthReferenceMinutes: Double,
        val recedingDirectLockReferenceMinutes: Double,
        val recedingShoulderWidthReferenceMinutes: Double,
        val directLockSeveritySlope: Double,
        val shoulderWidthSeveritySlope: Double,
        val seasonTrendScaleMinutesPerDay: Double,
        val missingDirectLockInterceptMinutes: Double,
        val missingDirectLockPerDayMinutes: Double,
        val missingShoulderWidthInterceptMinutes: Double,
        val missingShoulderWidthPerDayMinutes: Double
    )

    private enum class PolarState { NORMAL, POLAR_DAY, POLAR_NIGHT }

    private class BoundedCache<K, V>(private val maxSize: Int) {
        private val values = object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                size > maxSize
        }

        @Synchronized
        fun getOrPut(key: K, producer: () -> V): V {
            values[key]?.let { return it }
            return producer().also { values[key] = it }
        }
    }

    companion object {
        const val BASELINE_V14_VERSION = "diyanet_reconstruction_v14_fajr_transition_regimes"
        const val CANDIDATE_VERSION = "diyanet_reconstruction_v14_1_asymmetric_fajr_shoulder"
        const val MIN_ABS_LATITUDE = 45.0

        private const val FAJR_ANGLE = 18.0
        private const val ISHA_ANGLE = 16.0
        private const val NIGHT_ARC_DENOMINATOR = 90.0
        private const val MIN_PRAYER_DAY_MINUTES = 300.0
        private const val MAX_PRAYER_DAY_MINUTES = 1140.0
        private const val SHORT_DAY_HALF_MINUTES = 150L
        private const val LONG_DAY_HALF_MINUTES = 570L
        private const val TREND_RADIUS_DAYS = 4

        private const val RECOVERY_CONFIRMATION_DAYS = 3
        private const val SAME_DATE_ROOT_WINDOW_MINUTES = 30.0
        private const val SAME_DATE_MINIMUM_DEPTH_DEGREES = 0.53
        private const val EARLY_PRAYER_NOON_CUTOFF_MINUTES = 715.0
        private const val EXTREME_HOLD_MIN_MISSING_DAYS = 130
        private const val EXTREME_HOLD_MIN_ABS_LATITUDE = 60.0
        private val STABLE_MORNING_ROOT_TIME: LocalTime = LocalTime.of(4, 0)
        private const val REAPPEARANCE_TEMKIN_MINUTES = 7L
        private const val ROOT_DATE_UNCERTAINTY_MINUTES = 20L

        private const val SUMMER_ANCHOR_CORE_ANGLE = 16.0
        private const val SUMMER_ANCHOR_ZERO_ALTITUDE = -17.5
        private const val SUMMER_ANCHOR_BLEND_SPAN_DEGREES = 6.25
        private const val SUMMER_ANCHOR_ACTIVATION_SPAN_DEGREES = 4.5
        private const val SUMMER_ANCHOR_MIN_ALTITUDE = -22.5
        private const val SUMMER_ANCHOR_MAX_ALTITUDE = -17.5
        private const val SUMMER_ANCHOR_MAX_MISSING_DAYS = 15

        // Calibrated on the trusted 45-49° northern 2026 city distribution.
        // The bounded delta protects continuity and limits extrapolation risk.
        private const val SUMMER_ASYMMETRY_TREND_SCALE_MINUTES_PER_DAY = 2.5
        private const val SUMMER_ASYMMETRY_TREND_FADE_MINUTES_PER_DAY = 0.5
        private const val SUMMER_ASYMMETRY_REFERENCE_ALTITUDE_DEGREES = -19.0646
        private const val SUMMER_ASYMMETRY_ALTITUDE_SLOPE_MINUTES_PER_DEGREE = -1.8
        private const val SUMMER_ASYMMETRY_MAX_CHANGE_MINUTES = 3.0

        private const val LONG_MISSING_RANGE_MIN_DAYS = 130
        private const val LONG_MISSING_REFERENCE_DAYS = 150.0
        private const val LONG_MISSING_NORMALIZATION_DAYS = 20.0

        private const val PRE_RECOVERY_STANDARD_ANGLE = 14.0
        private const val PRE_RECOVERY_EXTREME_ANGLE = 15.0

        private val APPROACHING_FAJR_DISTANCE_SHOULDER = FajrDistanceShoulderParameters(
            centerDays = 14.114465,
            centerMissingSlope = -0.525604,
            logScale = 1.212298,
            logScaleMissingSlope = -0.013726
        )
        private val RECEDING_FAJR_DISTANCE_SHOULDER = FajrDistanceShoulderParameters(
            centerDays = 17.229858,
            centerMissingSlope = -0.801951,
            logScale = 1.346216,
            logScaleMissingSlope = -0.054385
        )

        private const val MIN_DIRECT_LOCK_MINUTES = 3.0
        private const val MAX_DIRECT_LOCK_MINUTES = 24.0
        private const val MIN_SHOULDER_WIDTH_MINUTES = 8.0
        private const val MAX_ANNUAL_SHOULDER_WIDTH_MINUTES = 180.0
        private const val MIN_MISSING_DIRECT_LOCK_MINUTES = 0.0
        private const val MAX_MISSING_DIRECT_LOCK_MINUTES = 20.0
        private const val MIN_MISSING_SHOULDER_WIDTH_MINUTES = 20.0
        private const val MAX_MISSING_SHOULDER_WIDTH_MINUTES = 160.0

        // Annual-direct band where the -18° root remains available but shallow.
        private const val ANNUAL_SHALLOW_18_ALTITUDE_LOW = -18.25
        private const val ANNUAL_SHALLOW_18_ALTITUDE_HIGH = -17.75
        private const val ANNUAL_SUNSET_LIMIT_GAP_MINUTES = 73.0
        private const val ANNUAL_SUNSET_LIMIT_NIGHT_CENTER_DEGREES = -22.19667348
        private const val ANNUAL_SUNSET_LIMIT_NIGHT_SCALE_DEGREES = 1.01357681
        private const val ANNUAL_SUNSET_LIMIT_APPROACHING_FRACTION = 0.37883547
        private const val ANNUAL_SUNSET_LIMIT_RECEDING_FRACTION = 0.59566050
        private const val ANNUAL_SUNSET_LIMIT_TREND_SCALE_MINUTES_PER_DAY = 1.0
        private const val ANNUAL_SUNSET_LIMIT_MAX_SHIFT_MINUTES = 13.79413752

        // Annual-direct locations whose summer minimum only barely clears -16°.
        private const val ANNUAL_NEAR_LOSS_ALTITUDE_LOW = -16.5
        private const val ANNUAL_NEAR_LOSS_ALTITUDE_HIGH = -16.0
        private const val ANNUAL_NEAR_LOSS_DEPTH_CENTER_DEGREES = 7.31605039
        private const val ANNUAL_NEAR_LOSS_DEPTH_SCALE_DEGREES = 0.90991664
        private const val ANNUAL_NEAR_LOSS_DIRECT_OFFSET_MINUTES = 1.49944167
        private const val ANNUAL_NEAR_LOSS_MAX_SHIFT_MINUTES = 8.39971280
        private const val ANNUAL_NEAR_LOSS_TREND_CENTER = 0.5
        private const val ANNUAL_NEAR_LOSS_TREND_SCALE = 0.8

        private val APPROACHING_ISHA_SHOULDER = IshaShoulderParameters(
            depthCenterDegrees = 9.44239327,
            depthScaleDegrees = 0.5,
            directOffsetMinutes = -7.84255409,
            maxLateCorrectionMinutes = 22.0
        )
        private val RECEDING_ISHA_SHOULDER = IshaShoulderParameters(
            depthCenterDegrees = 8.25519245,
            depthScaleDegrees = 0.71803878,
            directOffsetMinutes = 1.29241671,
            maxLateCorrectionMinutes = 22.0
        )
        private val POLAR_APPROACHING_ISHA_SHOULDER = IshaShoulderParameters(
            depthCenterDegrees = 8.41286042,
            depthScaleDegrees = 0.85182720,
            directOffsetMinutes = -2.66792840,
            maxLateCorrectionMinutes = 57.04175509
        )
        private val POLAR_RECEDING_ISHA_SHOULDER = IshaShoulderParameters(
            depthCenterDegrees = 7.58598088,
            depthScaleDegrees = 1.12795089,
            directOffsetMinutes = 1.53116621,
            maxLateCorrectionMinutes = 59.99748484
        )

        val HIGH_LATITUDE_PROFILE = DiyanetCriteriaProfile(
            profileId = "diyanet_high_latitude_18_16_v14",
            fajrAngle = FAJR_ANGLE,
            ishaAngle = ISHA_ANGLE
        )

        private val FAJR_CALIBRATION = ShoulderCalibration(
            severityReferenceMinutes = 75.0,
            approachingDirectLockReferenceMinutes = 14.681,
            approachingShoulderWidthReferenceMinutes = 161.461,
            recedingDirectLockReferenceMinutes = 23.758,
            recedingShoulderWidthReferenceMinutes = 165.833,
            directLockSeveritySlope = 0.311,
            shoulderWidthSeveritySlope = 0.0,
            seasonTrendScaleMinutesPerDay = 0.644,
            missingDirectLockInterceptMinutes = 6.119,
            missingDirectLockPerDayMinutes = 0.081,
            missingShoulderWidthInterceptMinutes = 20.0,
            missingShoulderWidthPerDayMinutes = 0.910
        )

        private val ISHA_CALIBRATION = ShoulderCalibration(
            severityReferenceMinutes = 55.0,
            approachingDirectLockReferenceMinutes = 16.863,
            approachingShoulderWidthReferenceMinutes = 146.339,
            recedingDirectLockReferenceMinutes = 9.620,
            recedingShoulderWidthReferenceMinutes = 138.784,
            directLockSeveritySlope = 0.492,
            shoulderWidthSeveritySlope = 1.089,
            seasonTrendScaleMinutesPerDay = 1.056,
            missingDirectLockInterceptMinutes = 9.153,
            missingDirectLockPerDayMinutes = 0.052,
            missingShoulderWidthInterceptMinutes = 40.752,
            missingShoulderWidthPerDayMinutes = 0.358
        )
    }

    private val annualCache = BoundedCache<AnnualKey, AnnualTwilightProfile>(64)
}

data class DiyanetV14Day(
    val date: LocalDate,
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime,
    val polarNight: Boolean,
    val confidence: DiyanetV14Confidence,
    val diagnostics: DiyanetV14Diagnostics
)

enum class DiyanetV14Confidence {
    CALIBRATED_NORTH_2026,
    EXPERIMENTAL_SOUTH
}

enum class DiyanetV14TwilightState {
    DIRECT,
    TRANSITION_TO_ESTIMATED,
    ESTIMATED,
    TRANSITION_TO_DIRECT,
    PREVIOUS_DATE_HOLD,
    DATE_OWNERSHIP_GUARD,
    PRE_REAPPEARANCE_BRIDGE
}

enum class DiyanetV14FajrRecoveryMode {
    NONE,
    DIRECT_REAPPEARANCE,
    PREVIOUS_DATE_HOLD
}

data class DiyanetV14Diagnostics(
    val candidateVersion: String,
    val astronomyKernelVersion: String,
    val profileId: String,
    val axisMode: String,
    val coreNightBasis: String,
    val fajrState: DiyanetV14TwilightState,
    val ishaState: DiyanetV14TwilightState,
    val recoveryMode: DiyanetV14FajrRecoveryMode,
    val dayLengthTrendMinutesPerDay: Double,
    val fajrExistsAllYear: Boolean,
    val ishaExistsAllYear: Boolean,
    val fajrMissingDays: Int,
    val ishaMissingDays: Int,
    val fajrMissingRange: DiyanetDateRange?,
    val fajrReappearanceDate: LocalDate?,
    val fajrHoldEndExclusive: LocalDate?,
    val fajrSyntheticBackdate: LocalDate?,
    val fajrRoot: ZonedDateTime?,
    val fajrRootCivilDate: LocalDate?,
    val fajrRootBelongsToPreviousCivilDate: Boolean,
    val fajrRootMinutesAfterPreviousMaghrib: Double?,
    val fajrNightMinimumSolarAltitudeDegrees: Double,
    val fajrDirectGapMinutes: Double?,
    val fajrCoreGapMinutes: Double,
    val fajrBaseGapMinutes: Double,
    val fajrOutputGapMinutes: Double,
    val fajrCorrectionMinutes: Double,
    val fajrLegacyV14GapMinutes: Double?,
    val fajrAsymmetricAdjustmentMinutes: Double,
    val fajrShoulderMode: String,
    val fajrAnnualSummerMinimumSolarAltitudeDegrees: Double,
    val fajrPreRecoveryBridgeDate: LocalDate?,
    val fajrPreRecoveryBridgeAngleDegrees: Double?,
    val ishaDirectGapMinutes: Double?,
    val ishaCoreGapMinutes: Double,
    val ishaBaseGapMinutes: Double,
    val ishaOutputGapMinutes: Double,
    val ishaCorrectionMinutes: Double,
    val ishaShoulderMode: String,
    val ishaNightMinimumSolarAltitudeDegrees: Double,
    val ishaAnnualSummerMinimumSolarAltitudeDegrees: Double,
    val ishaMissingRange: DiyanetDateRange?,
    val fajrDirectLockMinutes: Double,
    val fajrCoreLockMinutes: Double,
    val ishaDirectLockMinutes: Double,
    val ishaCoreLockMinutes: Double,
    val polarState: String
)
