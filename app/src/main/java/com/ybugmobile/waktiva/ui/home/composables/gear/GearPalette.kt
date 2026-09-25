package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.theme.darken
import com.ybugmobile.waktiva.ui.theme.desaturate

/** Desaturates and darkens colours for overcast or stormy weather, like the rest of the home screen. */
internal class WeatherTone private constructor(private val desaturation: Float, private val darkening: Float) {
    operator fun invoke(color: Color): Color =
        if (this === None) color else color.desaturate(desaturation).darken(darkening)

    companion object {
        val None = WeatherTone(0f, 0f)

        /** Same rules and amounts as the home background gradient, for the dial's metals and plates. */
        fun forScenery(weather: WeatherCondition): WeatherTone = when {
            weather in SceneryStorms -> WeatherTone(0.3f, 0.2f)
            weather.isCloudy -> WeatherTone(0.2f, 0.1f)
            else -> None
        }

        /** Same rules and amounts as the classic circle's prayer markers. */
        fun forPrayers(weather: WeatherCondition): WeatherTone = when {
            weather in MarkerStorms -> WeatherTone(0.35f, 0.2f)
            weather.isCloudy -> WeatherTone(0.2f, 0.1f)
            else -> None
        }

        private val WeatherCondition.isCloudy get() = this != WeatherCondition.CLEAR && this != WeatherCondition.UNKNOWN

        // Mirrors Gradient.kt's severe weather.
        private val SceneryStorms = setOf(
            WeatherCondition.RAINY,
            WeatherCondition.HEAVY_RAIN,
            WeatherCondition.RAIN_SHOWERS,
            WeatherCondition.FREEZING_RAIN,
            WeatherCondition.THUNDERSTORM,
            WeatherCondition.THUNDERSTORM_HAIL,
            WeatherCondition.SNOWY,
            WeatherCondition.HEAVY_SNOW
        )

        // Mirrors PrayerCircleVisualization's severe weather.
        private val MarkerStorms = setOf(WeatherCondition.RAINY, WeatherCondition.THUNDERSTORM, WeatherCondition.SNOWY)
    }
}

/**
 * Every material colour of the gear dials, the prayer plaque and the special-day bridge,
 * toned once for the current weather. Build one per weather change and share it.
 */
internal class GearPalette(private val weather: WeatherTone) {
    fun tone(color: Color): Color = weather(color)

    val brass = toned(
        0f to Color(0xFFF6E1A2),
        0.35f to Color(0xFFD4AB5A),
        0.7f to Color(0xFFA77C34),
        1f to Color(0xFF6F5020)
    )

    val steel = toned(
        0f to Color(0xFFE3E9F3),
        0.4f to Color(0xFFA6B1C4),
        0.75f to Color(0xFF66728A),
        1f to Color(0xFF394257)
    )

    /** Reflections of turned brass around a round part; see [MetalSheen]. */
    val brassSheen = sheen(
        0f to 0xFFCBA45A, 0.06f to 0xFFDAB869, 0.125f to 0xFFF8E4AA, 0.19f to 0xFFC49D55,
        0.25f to 0xFFA88643, 0.375f to 0xFF8E6C34, 0.5f to 0xFFC49E57, 0.58f to 0xFFF1D895,
        0.625f to 0xFFFFFAEA, 0.67f to 0xFFF1D895, 0.75f to 0xFFC7A05A, 0.875f to 0xFF987540,
        1f to 0xFFCBA45A
    )

    /** Reflections of polished steel around a round part; see [MetalSheen]. */
    val steelSheen = sheen(
        0f to 0xFFA3ACBC, 0.06f to 0xFFBCC4D2, 0.125f to 0xFFEEF2F8, 0.19f to 0xFFAEB6C5,
        0.25f to 0xFF838D9F, 0.375f to 0xFF626B7C, 0.5f to 0xFFA4ADBD, 0.58f to 0xFFE3E8F0,
        0.625f to 0xFFFFFFFF, 0.67f to 0xFFE3E8F0, 0.75f to 0xFFABB4C3, 0.875f to 0xFF6A7485,
        1f to 0xFFA3ACBC
    )

    /** Light of the halo ring behind the brass and skeleton dials. */
    val warmHalo = tone(Color(0xFFFFE7B0))

    /** Light of the halo ring behind the steel dial. */
    val coolHalo = tone(Color(0xFFE2EEFF))

    /** Hairline gold used by the skeleton dial. */
    val gold = tone(Color(0xFFDEBE78))

    val jewelSetting = tone(Color(0xFFD9B96A))
    val ruby = arrayOf(0f to tone(Color(0xFFFF8A96)), 0.5f to tone(Color(0xFFC2173A)), 1f to tone(Color(0xFF5C0718)))

    private val brassFinish by lazy {
        PlateFinish(
            metal = brass,
            edge = Color(0xBF3C280A),
            inset = Color.Black.copy(alpha = 0.28f),
            highlight = Color.White.copy(alpha = 0.25f),
            stripe = Color.White.copy(alpha = 0.13f),
            ink = Color(0xFF3F2A0A),
            strongInk = Color(0xFF2A1C06),
            screw = tone(Color(0xFF7A5A26))
        )
    }

    private val steelFinish by lazy {
        PlateFinish(
            metal = steel,
            edge = Color(0xB30A0E16),
            inset = Color.Black.copy(alpha = 0.28f),
            highlight = Color.White.copy(alpha = 0.25f),
            stripe = Color.White.copy(alpha = 0.13f),
            ink = Color(0xFF1A2130),
            strongInk = Color(0xFF111722),
            screw = tone(Color(0xFF4A5468))
        )
    }

    private val skeletonFinish by lazy {
        PlateFinish(
            metal = null,
            edge = gold.copy(alpha = 0.65f),
            inset = gold.copy(alpha = 0.28f),
            highlight = null,
            stripe = gold.copy(alpha = 0.07f),
            ink = tone(Color(0xFFE9C983)),
            strongInk = Color.White,
            screw = null
        )
    }

    /** Surface of the plaque and bridge for [style]. */
    fun finish(style: DayCircleStyle): PlateFinish = when (style) {
        DayCircleStyle.STEEL -> steelFinish
        DayCircleStyle.SKELETON -> skeletonFinish
        else -> brassFinish
    }

    private fun toned(vararg stops: Pair<Float, Color>) = stops.map { (at, color) -> at to tone(color) }.toTypedArray()

    private fun sheen(vararg stops: Pair<Float, Long>) = MetalSheen(stops.map { (at, argb) -> at to tone(Color(argb)) })
}

/**
 * Surface shared by the prayer plaque and the special-day bridge, so both match their dial:
 * engraved brass or steel, or a dark plate with gold hairlines for the skeleton.
 */
internal class PlateFinish(
    private val metal: Array<Pair<Float, Color>>?,
    val edge: Color,
    val inset: Color,
    /** Light line under the engraved inset; null on the skeleton plate. */
    val highlight: Color?,
    val stripe: Color,
    val ink: Color,
    val strongInk: Color,
    /** Screw head colour; null draws no screw. */
    val screw: Color?
) {
    val isMetal get() = metal != null

    fun fill(from: Offset, to: Offset): Brush =
        metal?.let { Brush.linearGradient(*it, start = from, end = to) } ?: SolidColor(Color(0xE60C0F18))
}
