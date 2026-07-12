package com.ybugmobile.waktiva.data.local.diyanet

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

interface DiyanetCandidateCalculator {
    fun calculate(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetCalculationResult
}

class AdaptiveDiyanetCalculator(
    private val astronomyKernel: DiyanetAstronomyKernel = DiyanetAstronomyKernel()
) : DiyanetCandidateCalculator {

    private val annualProfileCache = BoundedCache<AnnualProfileCacheKey, DiyanetAnnualProfile>(96)

    override fun calculate(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetCalculationResult {
        val rawEvents = astronomyKernel.rawEvents(date, location, profile)
        val annualProfile = inspectAnnualProfile(date, location, profile)
        if (
            !annualProfile.usesFiveHourBounds &&
            (rawEvents.astronomicalSunrise == null || rawEvents.astronomicalSunset == null)
        ) {
            return unsupportedResult(
                profile = profile,
                location = location,
                rawEvents = rawEvents,
                fallbackReason = "polar_unsupported"
            )
        }
        return when (annualProfile.regime) {
            DiyanetRegime.DIRECT_ANGLES -> directResult(profile, location, rawEvents)
            DiyanetRegime.SOLSTICE_ONE_THIRD_GRADUAL -> {
                solsticeOneThirdResult(date, profile, location, rawEvents, annualProfile)
            }
            DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR -> {
                missingFajrResult(date, profile, location, rawEvents, annualProfile)
            }
        }
    }

    fun inspectAnnualProfile(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetAnnualProfile {
        val anchor = resolveAnchorDate(date, location.latitude)
        if (!DiyanetRegimePolicy.isAdaptiveEligible(location.latitude)) {
            return DiyanetAnnualProfile(
                profileId = profile.profileId,
                anchor = anchor,
                regime = DiyanetRegime.DIRECT_ANGLES,
                usesFiveHourBounds = false
            )
        }

        val key = AnnualProfileCacheKey(
            candidateVersion = ADAPTIVE_DIYANET_CANDIDATE_VERSION,
            latitudeKey = normalizeCoordinate(location.latitude),
            longitudeKey = normalizeCoordinate(location.longitude),
            zoneId = location.zoneId.id,
            calculationElevationKey = normalizeCoordinate(location.calculationElevationMeters),
            profileId = profile.profileId,
            anchor = anchor,
            boundsYear = date.year
        )

        return annualProfileCache.getOrPut(key) {
            computeAnnualProfile(anchor, date.year, location, profile)
        }
    }

    private fun computeAnnualProfile(
        anchor: LocalDate,
        boundsYear: Int,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetAnnualProfile {
        val usesFiveHourBounds = astronomyKernel.usesFiveHourBounds(boundsYear, location, profile)
        val dominantMissingRun = findDominantMissingRun(anchor, location, profile, fajr = true)
        val dominantMissingIshaRun = findDominantMissingRun(anchor, location, profile, fajr = false)
        return if (dominantMissingRun != null && dominantMissingRun.lengthDays >= 10) {
            buildMissingFajrAnnualProfile(
                anchor = anchor,
                location = location,
                profile = profile,
                dominantMissingRun = dominantMissingRun,
                dominantMissingIshaRun = dominantMissingIshaRun,
                usesFiveHourBounds = usesFiveHourBounds
            )
        } else {
            buildSolsticeAnnualProfile(
                anchor = anchor,
                location = location,
                profile = profile,
                dominantMissingRun = dominantMissingRun,
                usesFiveHourBounds = usesFiveHourBounds
            )
        }
    }

    private fun buildSolsticeAnnualProfile(
        anchor: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        dominantMissingRun: DiyanetDateRange?,
        usesFiveHourBounds: Boolean
    ): DiyanetAnnualProfile {
        val anchorEvents = astronomyKernel.rawEvents(anchor, location, profile)
        val nextTrueFajr = astronomyKernel.rawEvents(anchor.plusDays(1), location, profile).directFajr
        val prayerSunrise = anchorEvents.prayerSunrise
        val prayerMaghrib = anchorEvents.prayerMaghrib

        if (prayerSunrise == null || prayerMaghrib == null || nextTrueFajr == null) {
            return DiyanetAnnualProfile(
                profileId = profile.profileId,
                anchor = anchor,
                regime = DiyanetRegime.DIRECT_ANGLES,
                usesFiveHourBounds = usesFiveHourBounds,
                dominantMissingRun = dominantMissingRun
            )
        }

        val shariNight = Duration.between(prayerMaghrib, nextTrueFajr)
        val oneThird = shariNight.dividedBy(3)
        val fajrDuration = minutesAsDuration(oneThird.toMinutesDouble() * profile.fajrAngle / profile.ishaAngle)
        val estimatedFajr = prayerSunrise.minus(fajrDuration)
        val estimatedIsha = prayerMaghrib.plus(oneThird)

        val springStart = findSpringTransitionStart(
            anchor = anchor,
            location = location,
            profile = profile,
            anchorEstimatedValue = estimatedFajr,
            fajr = true
        ) ?: anchor
        val autumnEnd = findAutumnTransitionEnd(
            start = anchor.plusDays(1),
            location = location,
            profile = profile,
            anchorEstimatedValue = estimatedFajr,
            fajr = true
        ) ?: anchor.plusDays(1)
        val springIshaStart = findSpringTransitionStart(
            anchor = anchor,
            location = location,
            profile = profile,
            anchorEstimatedValue = estimatedIsha,
            fajr = false
        ) ?: anchor
        val autumnIshaEnd = findAutumnTransitionEnd(
            start = anchor.plusDays(1),
            location = location,
            profile = profile,
            anchorEstimatedValue = estimatedIsha,
            fajr = false
        ) ?: anchor.plusDays(1)

        return DiyanetAnnualProfile(
            profileId = profile.profileId,
            anchor = anchor,
            regime = DiyanetRegime.SOLSTICE_ONE_THIRD_GRADUAL,
            usesFiveHourBounds = usesFiveHourBounds,
            dominantMissingRun = dominantMissingRun,
            adaptiveShoulderRegime = "solstice_margin_20",
            springFajrMarginMinutes = SOLSTICE_TRANSITION_MARGIN_MINUTES,
            springIshaMarginMinutes = SOLSTICE_TRANSITION_MARGIN_MINUTES,
            autumnFajrMarginMinutes = SOLSTICE_TRANSITION_MARGIN_MINUTES,
            autumnIshaMarginMinutes = SOLSTICE_TRANSITION_MARGIN_MINUTES,
            fajrTransitionStart = springStart,
            fajrTransitionEnd = autumnEnd,
            ishaTransitionStart = springIshaStart,
            ishaTransitionEnd = autumnIshaEnd,
            solsticeOneThird = oneThird,
            solsticeFajrDuration = fajrDuration,
            solsticeIshaDuration = oneThird,
            anchorPrayerSunrise = prayerSunrise,
            anchorPrayerMaghrib = prayerMaghrib,
            anchorNextTrueFajr = nextTrueFajr,
            anchorEstimatedFajr = estimatedFajr,
            anchorEstimatedIsha = estimatedIsha
        )
    }

    private fun buildMissingFajrAnnualProfile(
        anchor: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        dominantMissingRun: DiyanetDateRange,
        dominantMissingIshaRun: DiyanetDateRange?,
        usesFiveHourBounds: Boolean
    ): DiyanetAnnualProfile {
        val springReferenceDay = dominantMissingRun.start.minusDays(1)
        val previousPrayerMaghrib = astronomyKernel
            .rawEvents(springReferenceDay.minusDays(1), location, profile)
            .prayerMaghrib
        val currentPrayerSunrise = astronomyKernel
            .rawEvents(springReferenceDay, location, profile)
            .prayerSunrise
        val trueFajr = astronomyKernel
            .rawEvents(springReferenceDay, location, profile)
            .directFajr

        if (previousPrayerMaghrib == null || currentPrayerSunrise == null || trueFajr == null) {
            return DiyanetAnnualProfile(
                profileId = profile.profileId,
                anchor = anchor,
                regime = DiyanetRegime.DIRECT_ANGLES,
                usesFiveHourBounds = usesFiveHourBounds,
                dominantMissingRun = dominantMissingRun
            )
        }

        val shari = Duration.between(previousPrayerMaghrib, trueFajr).toMinutesDouble()
        val urfi = Duration.between(previousPrayerMaghrib, currentPrayerSunrise).toMinutesDouble()
        val summerRatio = (shari / 3.0) / urfi

        if (!summerRatio.isFinite() || summerRatio <= 0.0 || summerRatio >= 1.0) {
            return DiyanetAnnualProfile(
                profileId = profile.profileId,
                anchor = anchor,
                regime = DiyanetRegime.DIRECT_ANGLES,
                usesFiveHourBounds = usesFiveHourBounds,
                dominantMissingRun = dominantMissingRun
            )
        }

        val minimumNightMinutes = if (usesFiveHourBounds) 300 else 0
        val shoulderFamily = if (usesFiveHourBounds) {
            "five_hour_family"
        } else {
            "standard_family"
        }
        val margins = if (usesFiveHourBounds) {
            ShoulderMargins(
                springFajr = 20.0,
                springIsha = 14.0,
                autumnFajr = 14.0,
                autumnIsha = 20.0
            )
        } else {
            ShoulderMargins(
                springFajr = 16.0,
                springIsha = 10.0,
                autumnFajr = 12.0,
                autumnIsha = 18.0
            )
        }

        val fajrTransitionStart = findTransitionStartBeforeMissingRun(
            firstMissing = dominantMissingRun.start,
            location = location,
            profile = profile,
            usesFiveHourBounds = usesFiveHourBounds,
            minimumNightMinutes = minimumNightMinutes,
            summerRatio = summerRatio,
            marginMinutes = margins.springFajr,
            fajr = true
        ) ?: dominantMissingRun.start.minusDays(1)
        val resolvedIshaMissingRun = dominantMissingIshaRun ?: dominantMissingRun
        val delayedIshaAutumnTransition = resolvedIshaMissingRun.end.isAfter(dominantMissingRun.end)
        val ishaTransitionStart = findTransitionStartBeforeMissingRun(
            firstMissing = resolvedIshaMissingRun.start,
            location = location,
            profile = profile,
            usesFiveHourBounds = usesFiveHourBounds,
            minimumNightMinutes = minimumNightMinutes,
            summerRatio = summerRatio,
            marginMinutes = margins.springIsha,
            fajr = false
        ) ?: dominantMissingRun.start.minusDays(1)

        val firstDirect = dominantMissingRun.end.plusDays(1)
        val fajrTransitionEnd = findTransitionEndAfterMissingRun(
            firstDirect = firstDirect,
            location = location,
            profile = profile,
            usesFiveHourBounds = usesFiveHourBounds,
            minimumNightMinutes = minimumNightMinutes,
            summerRatio = summerRatio,
            marginMinutes = margins.autumnFajr,
            fajr = true
        ) ?: firstDirect
        val ishaTransitionEnd = findTransitionEndAfterMissingRun(
            firstDirect = resolvedIshaMissingRun.end.plusDays(1),
            location = location,
            profile = profile,
            usesFiveHourBounds = usesFiveHourBounds,
            minimumNightMinutes = minimumNightMinutes,
            summerRatio = summerRatio,
            marginMinutes = if (delayedIshaAutumnTransition) {
                ISHA_AUTUMN_CONVERGENCE_MARGIN_MINUTES
            } else {
                margins.autumnIsha
            },
            fajr = false
        ) ?: firstDirect

        return DiyanetAnnualProfile(
            profileId = profile.profileId,
            anchor = anchor,
            regime = DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR,
            usesFiveHourBounds = usesFiveHourBounds,
            dominantMissingRun = dominantMissingRun,
            dominantMissingIshaRun = resolvedIshaMissingRun,
            delayedIshaAutumnTransition = delayedIshaAutumnTransition,
            adaptiveShoulderRegime = shoulderFamily,
            springReferenceDay = springReferenceDay,
            summerRatio = summerRatio,
            ratioSource = "last_true_fajr_before_dominant_missing_run",
            minimumNightMinutes = minimumNightMinutes,
            springFajrMarginMinutes = margins.springFajr,
            springIshaMarginMinutes = margins.springIsha,
            autumnFajrMarginMinutes = margins.autumnFajr,
            autumnIshaMarginMinutes = margins.autumnIsha,
            fajrTransitionStart = fajrTransitionStart,
            fajrTransitionEnd = fajrTransitionEnd,
            ishaTransitionStart = ishaTransitionStart,
            ishaTransitionEnd = ishaTransitionEnd
        )
    }

    private fun directResult(
        profile: DiyanetCriteriaProfile,
        location: PrayerLocation,
        rawEvents: DiyanetRawEvents
    ): DiyanetCalculationResult {
        val diagnostics = DiyanetDiagnostics(
            profileId = profile.profileId,
            zoneId = location.zoneId.id,
            zoneSource = "zone_id_parameter",
            calculationElevationM = location.calculationElevationMeters,
            regime = DiyanetRegime.DIRECT_ANGLES,
            usesFiveHourBounds = false,
            phase = "direct",
            transitionCurve = "linear",
            directFajr = rawEvents.directFajr,
            directIsha = rawEvents.directIsha,
            fallbackReason = if (rawEvents.directFajr == null || rawEvents.directIsha == null) {
                "direct_root_missing"
            } else {
                null
            }
        )
        return DiyanetCalculationResult(
            fajr = rawEvents.directFajr,
            isha = rawEvents.directIsha,
            regime = DiyanetRegime.DIRECT_ANGLES,
            confidence = if (rawEvents.directFajr != null && rawEvents.directIsha != null) {
                DiyanetConfidence.HIGH
            } else {
                DiyanetConfidence.UNSUPPORTED
            },
            diagnostics = diagnostics
        )
    }

    private fun solsticeOneThirdResult(
        date: LocalDate,
        profile: DiyanetCriteriaProfile,
        location: PrayerLocation,
        rawEvents: DiyanetRawEvents,
        annualProfile: DiyanetAnnualProfile
    ): DiyanetCalculationResult {
        val estimated = estimateSolsticeTimes(date, location, profile, annualProfile)
        val (fajr, fajrPhase) = resolveSolsticeField(
            date = date,
            annualProfile = annualProfile,
            directValue = rawEvents.directFajr,
            anchorEstimatedValue = annualProfile.anchorEstimatedFajr,
            transitionStart = annualProfile.fajrTransitionStart,
            autumnEnd = annualProfile.fajrTransitionEnd,
            isFajr = true
        )
        val (isha, ishaPhase) = resolveSolsticeField(
            date = date,
            annualProfile = annualProfile,
            directValue = rawEvents.directIsha,
            anchorEstimatedValue = annualProfile.anchorEstimatedIsha,
            transitionStart = annualProfile.ishaTransitionStart,
            autumnEnd = annualProfile.ishaTransitionEnd,
            isFajr = false
        )

        val phase = combinePhases(fajrPhase, ishaPhase)
        val diagnostics = DiyanetDiagnostics(
            profileId = profile.profileId,
            zoneId = location.zoneId.id,
            zoneSource = "zone_id_parameter",
            calculationElevationM = location.calculationElevationMeters,
            anchor = annualProfile.anchor,
            fajrAngle = profile.fajrAngle,
            ishaAngle = profile.ishaAngle,
            anchorPrayerSunrise = annualProfile.anchorPrayerSunrise,
            anchorPrayerMaghrib = annualProfile.anchorPrayerMaghrib,
            anchorNextTrueFajr = annualProfile.anchorNextTrueFajr,
            anchorShariNightMinutes = annualProfile.solsticeOneThird?.toMinutesDouble()?.times(3.0),
            anchorOneThirdMinutes = annualProfile.solsticeOneThird?.toMinutesDouble(),
            anchorEstimatedFajr = annualProfile.anchorEstimatedFajr,
            anchorEstimatedIsha = annualProfile.anchorEstimatedIsha,
            regime = annualProfile.regime,
            adaptiveShoulderRegime = annualProfile.adaptiveShoulderRegime,
            usesFiveHourBounds = annualProfile.usesFiveHourBounds,
            springFajrMarginMinutes = annualProfile.springFajrMarginMinutes,
            springIshaMarginMinutes = annualProfile.springIshaMarginMinutes,
            autumnFajrMarginMinutes = annualProfile.autumnFajrMarginMinutes,
            autumnIshaMarginMinutes = annualProfile.autumnIshaMarginMinutes,
            fajrTransitionStart = annualProfile.fajrTransitionStart,
            fajrTransitionEnd = annualProfile.fajrTransitionEnd,
            ishaTransitionStart = annualProfile.ishaTransitionStart,
            ishaTransitionEnd = annualProfile.ishaTransitionEnd,
            phase = phase,
            transitionCurve = "linear",
            directFajr = rawEvents.directFajr,
            directIsha = rawEvents.directIsha,
            estimatedFajr = estimated.fajr,
            estimatedIsha = estimated.isha,
            fallbackReason = if (fajr == null || isha == null) "solstice_estimate_unavailable" else null
        )
        return DiyanetCalculationResult(
            fajr = fajr,
            isha = isha,
            regime = annualProfile.regime,
            confidence = if (fajr != null && isha != null) {
                DiyanetConfidence.MEDIUM
            } else {
                DiyanetConfidence.LOW
            },
            diagnostics = diagnostics
        )
    }

    private fun missingFajrResult(
        date: LocalDate,
        profile: DiyanetCriteriaProfile,
        location: PrayerLocation,
        rawEvents: DiyanetRawEvents,
        annualProfile: DiyanetAnnualProfile
    ): DiyanetCalculationResult {
        val estimated = estimateMissingFajrTimes(date, location, profile, annualProfile)
        val missingRun = annualProfile.dominantMissingRun
        val missingIshaRun = annualProfile.dominantMissingIshaRun ?: missingRun
        val (fajr, fajrPhase) = resolveMissingRunField(
            date = date,
            missingRun = missingRun,
            directValue = rawEvents.directFajr,
            estimatedValue = estimated.fajr,
            transitionStart = annualProfile.fajrTransitionStart,
            autumnEnd = annualProfile.fajrTransitionEnd,
            springMarginMinutes = annualProfile.springFajrMarginMinutes,
            autumnMarginMinutes = annualProfile.autumnFajrMarginMinutes,
            autumnCurveExponent = 1.0,
            isFajr = true
        )
        val (isha, ishaPhase) = resolveMissingRunField(
            date = date,
            missingRun = missingIshaRun,
            directValue = rawEvents.directIsha,
            estimatedValue = estimated.isha,
            transitionStart = annualProfile.ishaTransitionStart,
            autumnEnd = annualProfile.ishaTransitionEnd,
            springMarginMinutes = annualProfile.springIshaMarginMinutes,
            autumnMarginMinutes = annualProfile.autumnIshaMarginMinutes,
            autumnCurveExponent = if (annualProfile.delayedIshaAutumnTransition) {
                ISHA_AUTUMN_CURVE_EXPONENT
            } else {
                1.0
            },
            isFajr = false
        )

        val phase = combinePhases(fajrPhase, ishaPhase)
        val diagnostics = DiyanetDiagnostics(
            profileId = profile.profileId,
            zoneId = location.zoneId.id,
            zoneSource = "zone_id_parameter",
            calculationElevationM = location.calculationElevationMeters,
            anchor = annualProfile.anchor,
            regime = annualProfile.regime,
            adaptiveShoulderRegime = annualProfile.adaptiveShoulderRegime,
            usesFiveHourBounds = annualProfile.usesFiveHourBounds,
            firstMissing = missingRun?.start,
            lastMissing = missingRun?.end,
            ishaFirstMissing = missingIshaRun?.start,
            ishaLastMissing = missingIshaRun?.end,
            delayedIshaAutumnTransition = annualProfile.delayedIshaAutumnTransition,
            springReferenceDay = annualProfile.springReferenceDay,
            summerRatio = annualProfile.summerRatio,
            ratioSource = annualProfile.ratioSource,
            minimumNightMinutes = annualProfile.minimumNightMinutes,
            previousUrfiRawMinutes = estimated.previousUrfiRawMinutes,
            currentUrfiRawMinutes = estimated.currentUrfiRawMinutes,
            previousUrfiEffectiveMinutes = estimated.previousUrfiEffectiveMinutes,
            currentUrfiEffectiveMinutes = estimated.currentUrfiEffectiveMinutes,
            springFajrMarginMinutes = annualProfile.springFajrMarginMinutes,
            springIshaMarginMinutes = annualProfile.springIshaMarginMinutes,
            autumnFajrMarginMinutes = annualProfile.autumnFajrMarginMinutes,
            autumnIshaMarginMinutes = annualProfile.autumnIshaMarginMinutes,
            fajrTransitionStart = annualProfile.fajrTransitionStart,
            fajrTransitionEnd = annualProfile.fajrTransitionEnd,
            ishaTransitionStart = annualProfile.ishaTransitionStart,
            ishaTransitionEnd = annualProfile.ishaTransitionEnd,
            phase = phase,
            transitionCurve = if (annualProfile.delayedIshaAutumnTransition) {
                "linear_fajr_quadratic_delayed_isha_autumn"
            } else {
                "linear"
            },
            directFajr = rawEvents.directFajr,
            directIsha = rawEvents.directIsha,
            estimatedFajr = estimated.fajr,
            estimatedIsha = estimated.isha,
            previousBoundPhase = estimated.previousBoundPhase,
            currentBoundPhase = estimated.currentBoundPhase,
            nextBoundPhase = estimated.nextBoundPhase,
            durationClock = estimated.durationClock,
            fallbackReason = if (fajr == null || isha == null) "missing_run_estimate_unavailable" else null
        )
        return DiyanetCalculationResult(
            fajr = fajr,
            isha = isha,
            regime = annualProfile.regime,
            confidence = if (fajr != null && isha != null) {
                DiyanetConfidence.MEDIUM
            } else {
                DiyanetConfidence.LOW
            },
            diagnostics = diagnostics
        )
    }

    private fun estimateSolsticeTimes(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        annualProfile: DiyanetAnnualProfile
    ): DiyanetEstimatedTimes {
        val rawEvents = astronomyKernel.rawEvents(date, location, profile)
        val prayerSunrise = rawEvents.prayerSunrise
        val prayerMaghrib = rawEvents.prayerMaghrib
        val fajrDuration = annualProfile.solsticeFajrDuration
        val ishaDuration = annualProfile.solsticeIshaDuration

        if (prayerSunrise == null || prayerMaghrib == null || fajrDuration == null || ishaDuration == null) {
            return DiyanetEstimatedTimes(fajr = null, isha = null)
        }

        return DiyanetEstimatedTimes(
            fajr = prayerSunrise.minus(fajrDuration),
            isha = prayerMaghrib.plus(ishaDuration)
        )
    }

    private fun estimateMissingFajrTimes(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        annualProfile: DiyanetAnnualProfile
    ): DiyanetEstimatedTimes {
        val previousAxis = astronomyKernel.prayerAxis(
            date = date.minusDays(1),
            location = location,
            profile = profile,
            useFiveHourBounds = annualProfile.usesFiveHourBounds
        )
        val currentAxis = astronomyKernel.prayerAxis(
            date = date,
            location = location,
            profile = profile,
            useFiveHourBounds = annualProfile.usesFiveHourBounds
        )
        val nextAxis = astronomyKernel.prayerAxis(
            date = date.plusDays(1),
            location = location,
            profile = profile,
            useFiveHourBounds = annualProfile.usesFiveHourBounds
        )
        val previousPrayerMaghrib = previousAxis.prayerMaghrib
        val currentPrayerSunrise = currentAxis.prayerSunrise
        val currentPrayerMaghrib = currentAxis.prayerMaghrib
        val nextPrayerSunrise = nextAxis.prayerSunrise
        val summerRatio = annualProfile.summerRatio

        if (
            previousPrayerMaghrib == null ||
            currentPrayerSunrise == null ||
            currentPrayerMaghrib == null ||
            nextPrayerSunrise == null ||
            summerRatio == null
        ) {
            return DiyanetEstimatedTimes(fajr = null, isha = null)
        }

        val resolvedPreviousPrayerMaghrib = previousPrayerMaghrib!!
        val resolvedCurrentPrayerMaghrib = currentPrayerMaghrib!!
        var normalizedCurrentPrayerSunrise = currentPrayerSunrise!!
        while (!normalizedCurrentPrayerSunrise.isAfter(resolvedPreviousPrayerMaghrib)) {
            normalizedCurrentPrayerSunrise = normalizedCurrentPrayerSunrise.plusDays(1)
        }
        var normalizedNextPrayerSunrise = nextPrayerSunrise!!
        while (!normalizedNextPrayerSunrise.isAfter(resolvedCurrentPrayerMaghrib)) {
            normalizedNextPrayerSunrise = normalizedNextPrayerSunrise.plusDays(1)
        }

        val previousUrfiRaw = Duration.between(
            resolvedPreviousPrayerMaghrib.toInstant(),
            normalizedCurrentPrayerSunrise.toInstant()
        ).toMinutesDouble()
        val currentUrfiRaw = Duration.between(
            resolvedCurrentPrayerMaghrib.toInstant(),
            normalizedNextPrayerSunrise.toInstant()
        ).toMinutesDouble()
        val previousUrfiEffective = max(previousUrfiRaw, annualProfile.minimumNightMinutes.toDouble())
        val currentUrfiEffective = max(currentUrfiRaw, annualProfile.minimumNightMinutes.toDouble())
        val ishaDurationMinutes = currentUrfiEffective * summerRatio
        val fajrDurationMinutes = previousUrfiEffective * summerRatio * profile.fajrAngle / profile.ishaAngle

        return DiyanetEstimatedTimes(
            fajr = normalizedCurrentPrayerSunrise.minus(minutesAsDuration(fajrDurationMinutes)),
            isha = resolvedCurrentPrayerMaghrib.plus(minutesAsDuration(ishaDurationMinutes)),
            previousUrfiRawMinutes = previousUrfiRaw,
            currentUrfiRawMinutes = currentUrfiRaw,
            previousUrfiEffectiveMinutes = previousUrfiEffective,
            currentUrfiEffectiveMinutes = currentUrfiEffective,
            previousBoundPhase = previousAxis.phase,
            currentBoundPhase = currentAxis.phase,
            nextBoundPhase = nextAxis.phase,
            durationClock = "utc_elapsed"
        )
    }

    private fun resolveSolsticeField(
        date: LocalDate,
        annualProfile: DiyanetAnnualProfile,
        directValue: ZonedDateTime?,
        anchorEstimatedValue: ZonedDateTime?,
        transitionStart: LocalDate?,
        autumnEnd: LocalDate?,
        isFajr: Boolean
    ): Pair<ZonedDateTime?, String> {
        if (anchorEstimatedValue == null) {
            return directValue to "direct"
        }

        val anchor = annualProfile.anchor
        val candidateAndPhase = when {
            transitionStart != null &&
                date.isBefore(transitionStart) -> {
                null to "direct"
            }

            transitionStart != null &&
                !date.isBefore(transitionStart) &&
                !date.isAfter(anchor) -> {
                val t = normalizedProgress(transitionStart, anchor, date)
                solsticeCandidate(date, anchorEstimatedValue, isFajr, 1.0 - t) to "spring_transition"
            }

            autumnEnd != null &&
                date.isAfter(anchor) &&
                !date.isAfter(autumnEnd) -> {
                val t = normalizedProgress(anchor, autumnEnd, date)
                solsticeCandidate(date, anchorEstimatedValue, isFajr, t) to "autumn_transition"
            }

            else -> null to "direct"
        }

        val candidate = candidateAndPhase.first
        if (candidate == null) {
            return directValue to candidateAndPhase.second
        }
        if (directValue == null) {
            return candidate to "estimate_fallback"
        }
        return clampSolsticeWithDirect(directValue, candidate, isFajr) to candidateAndPhase.second
    }

    private fun solsticeCandidate(
        date: LocalDate,
        anchorEstimatedValue: ZonedDateTime,
        isFajr: Boolean,
        edgeProgress: Double
    ): ZonedDateTime {
        val edgeMinutes = SOLSTICE_TRANSITION_MARGIN_MINUTES * edgeProgress
        val anchorClockOnTarget = date.atTime(anchorEstimatedValue.toLocalTime()).atZone(anchorEstimatedValue.zone)
        return if (isFajr) {
            anchorClockOnTarget.plus(minutesAsDuration(edgeMinutes))
        } else {
            anchorClockOnTarget.minus(minutesAsDuration(edgeMinutes))
        }
    }

    private fun clampSolsticeWithDirect(
        directValue: ZonedDateTime,
        candidate: ZonedDateTime,
        isFajr: Boolean
    ): ZonedDateTime {
        return if (isFajr) {
            if (candidate.isBefore(directValue)) directValue else candidate
        } else {
            if (candidate.isAfter(directValue)) directValue else candidate
        }
    }

    private fun resolveMissingRunField(
        date: LocalDate,
        missingRun: DiyanetDateRange?,
        directValue: ZonedDateTime?,
        estimatedValue: ZonedDateTime?,
        transitionStart: LocalDate?,
        autumnEnd: LocalDate?,
        springMarginMinutes: Double?,
        autumnMarginMinutes: Double?,
        autumnCurveExponent: Double,
        isFajr: Boolean
    ): Pair<ZonedDateTime?, String> {
        if (missingRun != null && date in missingRun) {
            return estimatedValue to "estimated"
        }

        val firstMissing = missingRun?.start
        val lastMissing = missingRun?.end
        if (estimatedValue == null) {
            return directValue to "direct"
        }

        if (
            transitionStart != null &&
            firstMissing != null &&
            !date.isBefore(transitionStart) &&
            date.isBefore(firstMissing) &&
            springMarginMinutes != null
        ) {
            val t = normalizedProgress(transitionStart, firstMissing, date)
            val residual = springMarginMinutes * (1.0 - t)
            return resolveTransitionCandidate(
                directValue = directValue,
                estimatedValue = estimatedValue,
                residualMinutes = residual,
                isFajr = isFajr,
                phase = "spring_transition"
            )
        }

        val firstDirect = lastMissing?.plusDays(1)
        if (
            firstDirect != null &&
            autumnEnd != null &&
            !date.isBefore(firstDirect) &&
            !date.isAfter(autumnEnd) &&
            autumnMarginMinutes != null
        ) {
            val progress = normalizedProgress(firstDirect, autumnEnd, date)
            val t = progress.pow(autumnCurveExponent)
            val residual = autumnMarginMinutes * t
            return resolveTransitionCandidate(
                directValue = directValue,
                estimatedValue = estimatedValue,
                residualMinutes = residual,
                isFajr = isFajr,
                phase = "autumn_transition"
            )
        }

        return if (directValue == null) {
            estimatedValue to "estimate_fallback"
        } else {
            directValue to "direct"
        }
    }

    fun prayerAxis(
        date: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile
    ): DiyanetPrayerAxis {
        val annualProfile = inspectAnnualProfile(date, location, profile)
        return astronomyKernel.prayerAxis(
            date = date,
            location = location,
            profile = profile,
            useFiveHourBounds = annualProfile.usesFiveHourBounds
        )
    }

    private fun resolveTransitionCandidate(
        directValue: ZonedDateTime?,
        estimatedValue: ZonedDateTime,
        residualMinutes: Double,
        isFajr: Boolean,
        phase: String
    ): Pair<ZonedDateTime, String> {
        val candidate = transitionCandidate(estimatedValue, residualMinutes, isFajr)
        return if (directValue == null) {
            candidate to "${phase}_estimated_only"
        } else {
            clampWithDirect(directValue, candidate, isFajr) to phase
        }
    }

    private fun clampWithDirect(
        directValue: ZonedDateTime,
        candidate: ZonedDateTime,
        isFajr: Boolean
    ): ZonedDateTime {
        return if (isFajr) {
            if (candidate.isBefore(directValue)) directValue else candidate
        } else {
            if (candidate.isAfter(directValue)) directValue else candidate
        }
    }

    private fun transitionCandidate(
        estimatedValue: ZonedDateTime,
        residualMinutes: Double,
        isFajr: Boolean
    ): ZonedDateTime {
        return if (isFajr) {
            estimatedValue.minus(minutesAsDuration(residualMinutes))
        } else {
            estimatedValue.plus(minutesAsDuration(residualMinutes))
        }
    }

    private fun findDominantMissingRun(
        anchor: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        fajr: Boolean
    ): DiyanetDateRange? {
        var best: DiyanetDateRange? = null
        var currentStart: LocalDate? = null
        val start = anchor.minusDays(190)
        val end = anchor.plusDays(190)

        for (date in datesBetween(start, end)) {
            val directValue = astronomyKernel.rawEvents(date, location, profile).let {
                if (fajr) it.directFajr else it.directIsha
            }
            if (directValue == null) {
                if (currentStart == null) {
                    currentStart = date
                }
            } else if (currentStart != null) {
                val candidate = DiyanetDateRange(currentStart, date.minusDays(1))
                if (best == null || candidate.lengthDays > best.lengthDays) {
                    best = candidate
                }
                currentStart = null
            }
        }

        if (currentStart != null) {
            val candidate = DiyanetDateRange(currentStart, end)
            if (best == null || candidate.lengthDays > best.lengthDays) {
                best = candidate
            }
        }

        return best
    }

    private fun findSpringTransitionStart(
        anchor: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        anchorEstimatedValue: ZonedDateTime,
        fajr: Boolean
    ): LocalDate? {
        for (candidateDate in datesBetween(anchor.minusDays(190), anchor)) {
            val directValue = astronomyKernel.rawEvents(candidateDate, location, profile).let {
                if (fajr) it.directFajr else it.directIsha
            } ?: continue
            val edgeValue = solsticeCandidate(candidateDate, anchorEstimatedValue, fajr, 1.0)
            val reachedEdge = if (fajr) {
                !directValue.isAfter(edgeValue)
            } else {
                !directValue.isBefore(edgeValue)
            }
            if (reachedEdge) {
                return candidateDate
            }
        }

        return null
    }

    private fun findAutumnTransitionEnd(
        start: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        anchorEstimatedValue: ZonedDateTime,
        fajr: Boolean
    ): LocalDate? {
        for (candidateDate in datesBetween(start, start.plusDays(190))) {
            val directValue = astronomyKernel.rawEvents(candidateDate, location, profile).let {
                if (fajr) it.directFajr else it.directIsha
            } ?: continue
            val edgeValue = solsticeCandidate(candidateDate, anchorEstimatedValue, fajr, 1.0)
            val reachedEdge = if (fajr) {
                !directValue.isBefore(edgeValue)
            } else {
                !directValue.isAfter(edgeValue)
            }
            if (reachedEdge) {
                return candidateDate
            }
        }

        return null
    }

    private fun findTransitionStartBeforeMissingRun(
        firstMissing: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        usesFiveHourBounds: Boolean,
        minimumNightMinutes: Int,
        summerRatio: Double,
        marginMinutes: Double,
        fajr: Boolean
    ): LocalDate? {
        val scanStart = firstMissing.minusDays(90)
        for (candidateDate in datesBetween(scanStart, firstMissing.minusDays(1))) {
            val directValue = astronomyKernel.rawEvents(candidateDate, location, profile).let {
                if (fajr) it.directFajr else it.directIsha
            } ?: continue
            val estimatedValue = estimateMissingFajrTimes(
                candidateDate,
                location,
                profile,
                DiyanetAnnualProfile(
                    profileId = profile.profileId,
                    anchor = resolveAnchorDate(candidateDate, location.latitude),
                    regime = DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR,
                    usesFiveHourBounds = usesFiveHourBounds,
                    summerRatio = summerRatio,
                    minimumNightMinutes = minimumNightMinutes
                )
            ).let { if (fajr) it.fajr else it.isha } ?: continue

            val diff = if (fajr) {
                Duration.between(directValue, estimatedValue).toMinutesDouble()
            } else {
                Duration.between(estimatedValue, directValue).toMinutesDouble()
            }
            if (diff >= marginMinutes) {
                return candidateDate
            }
        }
        return null
    }

    private fun findTransitionEndAfterMissingRun(
        firstDirect: LocalDate,
        location: PrayerLocation,
        profile: DiyanetCriteriaProfile,
        usesFiveHourBounds: Boolean,
        minimumNightMinutes: Int,
        summerRatio: Double,
        marginMinutes: Double,
        fajr: Boolean
    ): LocalDate? {
        var seenAbove = false
        for (candidateDate in datesBetween(firstDirect, firstDirect.plusDays(90))) {
            val directValue = astronomyKernel.rawEvents(candidateDate, location, profile).let {
                if (fajr) it.directFajr else it.directIsha
            } ?: continue
            val estimatedValue = estimateMissingFajrTimes(
                candidateDate,
                location,
                profile,
                DiyanetAnnualProfile(
                    profileId = profile.profileId,
                    anchor = resolveAnchorDate(candidateDate, location.latitude),
                    regime = DiyanetRegime.ROBUST_MISSING_FAJR_FULL_YEAR,
                    usesFiveHourBounds = usesFiveHourBounds,
                    summerRatio = summerRatio,
                    minimumNightMinutes = minimumNightMinutes
                )
            ).let { if (fajr) it.fajr else it.isha } ?: continue

            val diff = if (fajr) {
                Duration.between(directValue, estimatedValue).toMinutesDouble()
            } else {
                Duration.between(estimatedValue, directValue).toMinutesDouble()
            }
            if (diff > marginMinutes) {
                seenAbove = true
            }
            val offset = ChronoUnit.DAYS.between(firstDirect, candidateDate) + 1
            if (seenAbove && diff <= marginMinutes) {
                return candidateDate
            }
            if (!seenAbove && offset <= 3 && diff <= marginMinutes) {
                return candidateDate
            }
        }
        return null
    }

    private fun resolveAnchorDate(date: LocalDate, latitude: Double): LocalDate {
        return if (latitude >= 0.0) {
            LocalDate.of(date.year, 6, 21)
        } else if (date.monthValue <= 6) {
            LocalDate.of(date.year - 1, 12, 21)
        } else {
            LocalDate.of(date.year, 12, 21)
        }
    }

    private fun unsupportedResult(
        profile: DiyanetCriteriaProfile,
        location: PrayerLocation,
        rawEvents: DiyanetRawEvents,
        fallbackReason: String
    ): DiyanetCalculationResult {
        return DiyanetCalculationResult(
            fajr = null,
            isha = null,
            regime = DiyanetRegime.DIRECT_ANGLES,
            confidence = DiyanetConfidence.UNSUPPORTED,
            diagnostics = DiyanetDiagnostics(
                profileId = profile.profileId,
                zoneId = location.zoneId.id,
                zoneSource = "zone_id_parameter",
                calculationElevationM = location.calculationElevationMeters,
                regime = DiyanetRegime.DIRECT_ANGLES,
                phase = "unsupported",
                transitionCurve = "linear",
                directFajr = rawEvents.directFajr,
                directIsha = rawEvents.directIsha,
                fallbackReason = fallbackReason
            )
        )
    }

    private fun combinePhases(fajrPhase: String, ishaPhase: String): String {
        return if (fajrPhase == ishaPhase) {
            fajrPhase
        } else {
            "$fajrPhase|$ishaPhase"
        }
    }

    private fun normalizedProgress(start: LocalDate, end: LocalDate, current: LocalDate): Double {
        val totalDays = max(1L, ChronoUnit.DAYS.between(start, end))
        val progressed = ChronoUnit.DAYS.between(start, current)
        return (progressed.toDouble() / totalDays.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun datesBetween(start: LocalDate, end: LocalDate): Sequence<LocalDate> {
        return generateSequence(start) { current ->
            if (current.isBefore(end)) current.plusDays(1) else null
        }
    }

    private fun normalizeCoordinate(value: Double): Long {
        return (value * 1_000_000.0).roundToLong()
    }

    private data class ShoulderMargins(
        val springFajr: Double,
        val springIsha: Double,
        val autumnFajr: Double,
        val autumnIsha: Double
    )

    private data class AnnualProfileCacheKey(
        val candidateVersion: String,
        val latitudeKey: Long,
        val longitudeKey: Long,
        val zoneId: String,
        val calculationElevationKey: Long,
        val profileId: String,
        val anchor: LocalDate,
        val boundsYear: Int
    )

    private class BoundedCache<K, V>(
        private val maxSize: Int
    ) {
        private val delegate = object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }

        @Synchronized
        fun getOrPut(key: K, producer: () -> V): V {
            val existing = delegate[key]
            if (existing != null) {
                return existing
            }

            val computed = producer()
            delegate[key] = computed
            return computed
        }
    }

    private companion object {
        const val SOLSTICE_TRANSITION_MARGIN_MINUTES: Double = 20.0
        const val ISHA_AUTUMN_CONVERGENCE_MARGIN_MINUTES: Double = 5.0
        const val ISHA_AUTUMN_CURVE_EXPONENT: Double = 2.0
    }
}
