package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.ui.theme.CormorantGaramond
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.theme.getGradientColorsForTime
import kotlin.math.PI
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ── The night the log is drawn on ───────────────────────────────────────────

/** Words on the night: a moonlit white, and the same stepping back. */
internal val SkyInk = Color(0xFFF4F1FF)
internal fun skyFaint(alpha: Float) = Color(0xFFE4E2FF).copy(alpha = alpha)

/** A lit star's white, the lilac of a prayer whose time is on, the coral of one gone unmarked. */
internal val StarWhite = Color(0xFFFFFDF4)
internal val NowLilac = Color(0xFFC5CBF0)
internal val MissedCoral = Color(0xFFFFB4B4)

/** The night's colours, top to bottom. */
private val NightColors = listOf(Color(0xFF03050D), Color(0xFF070B22), Color(0xFF141142), Color(0xFF1E1A52))

/**
 * The night the prayer log is drawn on, under the sky of the hour (see [SkyDome]): deep blue into
 * violet, a glow low down, and stars twinkling in it, more of them high up where it's darkest.
 */
@Composable
internal fun NightSky(modifier: Modifier = Modifier) {
    val twinkle = rememberInfiniteTransition(label = "twinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "twinkle"
    )
    val stars = remember {
        val random = Random(7)
        List(90) {
            NightStar(
                x = random.nextFloat(),
                y = random.nextFloat().let { it * it },
                radius = if (random.nextFloat() < 0.85f) 0.7f else 1.2f,
                alpha = 0.25f + random.nextFloat() * 0.55f,
                phase = random.nextFloat()
            )
        }
    }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(NightColors))
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF9FA8DA).copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width / 2f, size.height * 1.05f),
                radius = size.width * 1.1f
            )
        )
        val t = twinkle.value
        stars.forEach { star ->
            val shimmer = 0.55f + 0.45f * sin(2f * PI.toFloat() * (t + star.phase))
            drawCircle(
                Color.White.copy(alpha = star.alpha * shimmer),
                radius = star.radius.dp.toPx(),
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }
    }
}

private class NightStar(val x: Float, val y: Float, val radius: Float, val alpha: Float, val phase: Float)

// ── The sky by the hour ─────────────────────────────────────────────────────

/**
 * How the sky over today's stars looks at an hour: the home screen's colours for it ([colors], top
 * to horizon), how much of the night's stars show through them, the moon's light while it's up,
 * the glow low on the horizon, and whether the sky is light enough that words and stars need a
 * shadow on it.
 */
@Immutable
internal class SkyHour(
    val colors: List<Color>,
    val stars: Float,
    val moon: Float,
    val glow: Color,
    val isDay: Boolean
)

/**
 * The sky at [now] on [day]: the night until Fajr and after Isha, the dawn's violet till sunrise,
 * the sun coming up, the day, the sunset's last three quarters of an hour, and the dusk.
 */
internal fun skyHour(now: LocalTime, day: PrayerDay?): SkyHour {
    val colors = getGradientColorsForTime(now, day)
    val timings = day?.timings
    val fajr = timings?.get(PrayerType.FAJR)
    val sunrise = timings?.get(PrayerType.SUNRISE)
    val maghrib = timings?.get(PrayerType.MAGHRIB)
    val isha = timings?.get(PrayerType.ISHA)
    if (fajr == null || sunrise == null || maghrib == null || isha == null) {
        return SkyHour(colors, stars = 1f, moon = 1f, glow = Color(0x529FA8DA), isDay = false)
    }
    return when {
        now.isBefore(fajr) || !now.isBefore(isha) -> SkyHour(colors, stars = 1f, moon = 1f, glow = Color(0x529FA8DA), isDay = false)
        now.isBefore(sunrise) -> SkyHour(colors, stars = 0.85f, moon = 0.7f, glow = Color(0x73AA8CE6), isDay = false)
        now.isBefore(sunrise.plusMinutes(45)) -> SkyHour(colors, stars = 0.04f, moon = 0f, glow = Color(0xB3FFBE78), isDay = true)
        now.isBefore(maghrib.minusMinutes(45)) -> SkyHour(colors, stars = 0f, moon = 0f, glow = Color(0x80F0FAFF), isDay = true)
        now.isBefore(maghrib) -> SkyHour(colors, stars = 0.2f, moon = 0f, glow = Color(0xB3FF8046), isDay = false)
        else -> SkyHour(colors, stars = 0.6f, moon = 0.9f, glow = Color(0x66C8506E), isDay = false)
    }
}

/**
 * The sky over today's stars in [hour]'s colours, from [extendTop] above its content (to the top of
 * the screen) down to the horizon under the stars, the night's stars showing through as much as the
 * hour lets, a glow on the horizon and its light on the ground below. Under the horizon the screen
 * stays in the night: the galaxy lives there.
 */
@Composable
internal fun SkyDome(hour: SkyHour, extendTop: Dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val twinkle = rememberInfiniteTransition(label = "domeTwinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "domeTwinkle"
    )
    val stars = remember {
        val random = Random(11)
        List(50) {
            NightStar(
                x = random.nextFloat(),
                y = random.nextFloat().pow(1.3f),
                radius = if (random.nextFloat() < 0.85f) 0.7f else 1.2f,
                alpha = 0.25f + random.nextFloat() * 0.6f,
                phase = random.nextFloat()
            )
        }
    }
    Column(
        modifier.drawBehind {
            val top = -extendTop.toPx()
            val horizon = size.height - (SkyHeight - HorizonY).toPx()
            drawRect(
                Brush.verticalGradient(hour.colors, startY = top, endY = horizon),
                topLeft = Offset(0f, top),
                size = Size(size.width, horizon - top)
            )
            val ground = 70.dp.toPx()
            drawRect(
                Brush.verticalGradient(listOf(hour.colors.last().copy(alpha = 0.28f), Color.Transparent), startY = horizon, endY = horizon + ground),
                topLeft = Offset(0f, horizon),
                size = Size(size.width, ground)
            )
            if (hour.stars > 0f) {
                val t = twinkle.value
                val height = horizon - 4.dp.toPx() - top
                stars.forEach { star ->
                    val shimmer = 0.55f + 0.45f * sin(2f * PI.toFloat() * (t + star.phase))
                    drawCircle(
                        Color.White.copy(alpha = star.alpha * shimmer * hour.stars),
                        radius = star.radius.dp.toPx(),
                        center = Offset(star.x * size.width, top + star.y * height)
                    )
                }
            }
            val glowAt = Offset(size.width / 2f, horizon)
            val reach = 280.dp.toPx()
            scale(1f, 80f / 280f, glowAt) {
                drawCircle(Brush.radialGradient(listOf(hour.glow, Color.Transparent), glowAt, reach), reach, glowAt)
            }
        },
        content = content
    )
}

// ── Stars ───────────────────────────────────────────────────────────────────

/** A four-pointed star of radius [r] at [center], its arms drawn in with soft curves. */
internal fun sparkle(center: Offset, r: Float): Path = Path().apply {
    val a = 0.137f * r
    val b = 0.158f * r
    val (x, y) = center
    moveTo(x, y - r)
    cubicTo(x + a, y - b, x + b, y - a, x + r, y)
    cubicTo(x + b, y + a, x + a, y + b, x, y + r)
    cubicTo(x - a, y + b, x - b, y + a, x - r, y)
    cubicTo(x - b, y - a, x - a, y - b, x, y - r)
    close()
}

/** A star as a little picture: filled and glowing, or only its outline in [outline]. */
@Composable
internal fun StarGlyph(size: Dp, modifier: Modifier = Modifier, fill: Color? = StarWhite, outline: Color? = null, glow: Color? = null) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        if (glow != null) {
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.6f), Color.Transparent), center, r * 1.6f), r * 1.6f)
        }
        val path = sparkle(center, r)
        if (fill != null) drawPath(path, fill)
        if (outline != null) drawPath(path, outline, style = Stroke(r * 0.16f, join = StrokeJoin.Round))
    }
}

// ── The stars of the day ────────────────────────────────────────────────────

/** Where each of Cassiopeia's five stars sits on a sky 390 wide, and how bright it is. */
private val CassiopeiaStars = listOf(
    Triple(70f, 129.5f, 1f),     // Caph
    Triple(140f, 218.6f, 1.05f), // Schedar
    Triple(195f, 139.4f, 1.12f), // Navi, the brightest
    Triple(250f, 205.4f, 0.9f),  // Ruchbah
    Triple(320f, 119.6f, 0.78f)  // Segin, the faintest
)

private val SkyHeight = 320.dp
private val HorizonY = 300.dp

/**
 * Today's five prayers as stars on the sun's path across the sky, each where its time falls in the
 * day and each its own star (see [prayerStar]). A prayer marked is a star lit, and two in a row both
 * lit are joined by a line, the day's own constellation; one gone unmarked breaks it. The one whose
 * time is on pulses, a dotted line reaching on to it from the star before. Tapping a star marks it
 * or takes the mark back. [hour] is the sky they're on.
 *
 * With [cassiopeia], the day just made full, the five glide off the sun's path into Cassiopeia's W,
 * five prayers as the queen's five stars, and its lines are drawn in.
 */
@Composable
internal fun DayStars(
    today: PrayerLogDay,
    cassiopeia: Boolean,
    hour: SkyHour,
    onToggle: (PrayerLogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pulse = rememberInfiniteTransition(label = "skyPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "skyPulse"
    )
    val twinkle = rememberInfiniteTransition(label = "starTwinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "starTwinkle"
    )
    val arcAlpha by animateFloatAsState(if (cassiopeia) 0f else 1f, tween(900), label = "arcAlpha")
    val wDrawn by animateFloatAsState(
        if (cassiopeia) 1f else 0f,
        if (cassiopeia) tween(1400, delayMillis = 900) else tween(500),
        label = "wDrawn"
    )

    BoxWithConstraints(modifier.fillMaxWidth().height(SkyHeight)) {
        val width = maxWidth
        val cx = width / 2
        val rx = minOf(width * 0.41f, 170.dp)
        val ry = 210.dp
        // Minutes into the day, a time past midnight (Isha, far north) counted on from the day before.
        val minutes = today.entries.map { it.time?.let { t -> t.hour * 60 + t.minute } }.let { raw ->
            var last = -1
            raw.map { m -> m?.let { (if (it < last) it + 24 * 60 else it).also { v -> last = v } } }
        }
        val known = minutes.filterNotNull()
        val first = known.minOrNull() ?: 0
        val span = ((known.maxOrNull() ?: 1) - first).coerceAtLeast(1)

        // Each star's place on the sun's path, by its time; evenly spread without times.
        val onArc = today.entries.mapIndexed { i, _ ->
            val share = minutes[i]?.let { (it - first).toFloat() / span } ?: (i / 4f)
            val angle = PI.toFloat() * (0.05f + 0.88f * share)
            val dx = rx * cos(angle)
            SkyPoint(if (rtl) cx + dx else cx - dx, HorizonY - ry * sin(angle))
        }
        val inW = CassiopeiaStars.map { (x, y, _) -> SkyPoint(cx + (width / 390f) * (x - 195f), y.dp) }

        Canvas(Modifier.fillMaxSize()) {
            val horizon = HorizonY.toPx()
            drawLine(Color.White.copy(alpha = if (hour.isDay) 0.5f else 0.22f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
            if (hour.moon > 0f) {
                drawMoon(Offset(if (rtl) size.width - 70.dp.toPx() else 70.dp.toPx(), 60.dp.toPx()), 22.dp.toPx(), hour.moon)
            }
            // On a light sky, a dome of shade under the count, as the home dial has.
            if (hour.isDay) {
                val under = Offset(size.width / 2f, 205.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(Color(0x57050816), Color.Transparent), under, 150.dp.toPx()), 150.dp.toPx(), under)
            }

            if (arcAlpha > 0f) {
                // The sun's path, dotted.
                val arc = Path().apply {
                    addArc(
                        Rect(
                            Offset(cx.toPx() - rx.toPx(), horizon - ry.toPx()),
                            Size(rx.toPx() * 2f, ry.toPx() * 2f)
                        ),
                        180f, 180f
                    )
                }
                drawPath(
                    arc,
                    Color.White.copy(alpha = (if (hour.isDay) 0.45f else 0.16f) * arcAlpha),
                    style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())))
                )
                // The day's constellation: two prayers in a row both lit, joined.
                val entries = today.entries
                val brush = Brush.horizontalGradient(entries.map { it.type.paleColor })
                for (i in 0 until entries.size - 1) {
                    if (entries[i].status != PrayerLogStatus.PRAYED || entries[i + 1].status != PrayerLogStatus.PRAYED) continue
                    val from = onArc[i].toPx(this)
                    val to = onArc[i + 1].toPx(this)
                    drawLine(brush, from, to, 6.dp.toPx(), cap = StrokeCap.Round, alpha = 0.18f * arcAlpha)
                    drawLine(brush, from, to, 1.5.dp.toPx(), cap = StrokeCap.Round, alpha = arcAlpha)
                }
                // A dotted line on to the prayer whose time is on, breathing, from the star before it lit.
                val active = entries.indexOfFirst { it.status == PrayerLogStatus.ACTIVE }
                if (active > 0 && entries[active - 1].status == PrayerLogStatus.PRAYED) {
                    val breath = 0.55f + 0.45f * sin(2f * PI.toFloat() * pulse.value)
                    drawLine(
                        NowLilac.copy(alpha = breath * arcAlpha),
                        onArc[active - 1].toPx(this),
                        onArc[active].toPx(this),
                        1.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
                    )
                }
            }
            if (wDrawn > 0f) {
                val w = Path().apply {
                    inW.forEachIndexed { i, p -> val q = p.toPx(this@Canvas); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
                }
                val measure = PathMeasure().apply { setPath(w, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * wDrawn, drawn, true)
                drawPath(drawn, Color.White.copy(alpha = 0.35f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(drawn, StarWhite, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // The halo behind the queen.
        val queenGlow by animateFloatAsState(if (cassiopeia) 1f else 0f, tween(900), label = "queenGlow")
        if (queenGlow > 0f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .size(340.dp, 300.dp)
                    .alpha(queenGlow)
                    .background(Brush.radialGradient(listOf(NowLilac.copy(alpha = 0.35f), Color(0xFF9FA8DA).copy(alpha = 0.1f), Color.Transparent)))
            )
        }

        today.entries.forEachIndexed { i, entry ->
            key(entry.type) {
                val target = if (cassiopeia) inW[i] else onArc[i]
                val delay = (if (cassiopeia) i else 4 - i) * 80
                val x by animateDpAsState(target.x, tween(1600, delay, FastOutSlowInEasing), label = "starX")
                val y by animateDpAsState(target.y, tween(1600, delay, FastOutSlowInEasing), label = "starY")
                SkyStar(
                    entry = entry,
                    brightness = if (cassiopeia) CassiopeiaStars[i].third else 1f,
                    showLabel = !cassiopeia,
                    pulse = pulse,
                    twinkle = twinkle,
                    phase = i * 0.2f,
                    isDay = hour.isDay,
                    onClick = { onToggle(entry) },
                    modifier = Modifier.offset(x - 32.dp, y - 32.dp)
                )
            }
        }

        // How many are lit, in the middle; the queen's name in its place while she shows.
        val lit = today.prayed
        val dayShadow = if (hour.isDay) Shadow(Color(0x8C040A28), Offset(0f, 2f), 14f) else null
        val countAlpha by animateFloatAsState(if (cassiopeia) 0f else 1f, tween(900), label = "countAlpha")
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 150.dp)
                .alpha(countAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = buildAnnotatedString {
                    append(lit.toString())
                    withStyle(SpanStyle(fontSize = 52.sp, color = Color(0xFFC9C6F2).copy(alpha = 0.55f))) {
                        append("/${today.entries.size}")
                    }
                },
                style = TextStyle(
                    fontFamily = CormorantGaramond,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 88.sp,
                    lineHeight = 88.sp,
                    brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFC9C6F2))),
                    shadow = dayShadow
                )
            )
            Text(
                text = stringResource(R.string.prayer_log_sky_lit).uppercase(),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp, shadow = dayShadow),
                color = skyFaint(0.62f)
            )
        }
        val titleAlpha by animateFloatAsState(if (cassiopeia) 1f else 0f, tween(900, delayMillis = if (cassiopeia) 1200 else 0), label = "queenTitle")
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 244.dp)
                .graphicsLayer { alpha = titleAlpha; translationY = (1f - titleAlpha) * 10.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.prayer_log_cassiopeia),
                style = TextStyle(fontFamily = CormorantGaramond, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, shadow = dayShadow),
                color = SkyInk
            )
            Text(
                text = stringResource(R.string.prayer_log_cassiopeia_line).uppercase(),
                style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, shadow = dayShadow),
                color = skyFaint(0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * One prayer in the sky: its own star in its state (see [prayerStar]), with its name and time under
 * it. The whole of it, 64 by 84, is the tap target, except for a prayer whose time hasn't come; the
 * star's light reaches out past it. [phase] sets it apart in the twinkle; on a light sky ([isDay])
 * its words take a shadow.
 */
@Composable
private fun SkyStar(
    entry: PrayerLogEntry,
    brightness: Float,
    showLabel: Boolean,
    pulse: State<Float>,
    twinkle: State<Float>,
    phase: Float,
    isDay: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = entry.status
    val name = entry.type.prayerName
    val statusText = stringResource(status.labelRes)
    val lit = status == PrayerLogStatus.PRAYED
    val pop by animateFloatAsState(
        if (lit) brightness else 0.6f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "starPop"
    )
    val labelAlpha by animateFloatAsState(if (showLabel) 1f else 0f, tween(600), label = "starLabel")
    // Each time it's lit, it flashes and its XP rises from it.
    var marks by remember { mutableIntStateOf(0) }
    var lastStatus by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (lit && lastStatus != PrayerLogStatus.PRAYED) marks++
        lastStatus = status
    }
    val ignite = remember { Animatable(1f) }
    LaunchedEffect(marks) {
        if (marks > 0) {
            ignite.snapTo(0f)
            ignite.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        }
    }
    val shadow = if (isDay) Shadow(Color(0x8C040A28), Offset(0f, 1f), 8f) else null

    Box(modifier.size(64.dp, 84.dp)) {
        Canvas(Modifier.size(64.dp)) {
            val shimmer = 0.5f + 0.5f * cos(2f * PI.toFloat() * (twinkle.value + phase))
            prayerStar(entry.type, status, center, pop, shimmer, pulse.value, ignite.value, isDay)
        }
        XpPop(marks, Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true).align(Alignment.TopCenter))
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 54.dp)
                .alpha(labelAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, shadow = shadow),
                color = when (status) {
                    PrayerLogStatus.MISSED -> MissedCoral
                    PrayerLogStatus.UPCOMING -> if (isDay) Color.White.copy(alpha = 0.85f) else skyFaint(0.55f)
                    else -> SkyInk
                },
                maxLines = 1,
                softWrap = false
            )
            entry.time?.let {
                Text(
                    text = it.format(TimeFormat),
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", shadow = shadow),
                    color = if (isDay) Color.White.copy(alpha = 0.78f) else skyFaint(0.5f),
                    maxLines = 1
                )
            }
        }
        // The tap target over it all, its ripple kept to the star's own rounded square.
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .then(if (status != PrayerLogStatus.UPCOMING) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .semantics { contentDescription = "$name, $statusText" }
        )
    }
}

private val TimeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

/** A new moon, lit on its outer edge, as bright as [alpha]. */
private fun DrawScope.drawMoon(center: Offset, r: Float, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(MoonLight.copy(alpha = 0.25f), Color.Transparent), center, r * 2f), r * 2f, center, alpha = alpha)
    drawPath(crescent(center, r), MoonLight, alpha = alpha, style = Fill)
}

/** A point in dp, turned to pixels in [scope]. */
private data class SkyPoint(val x: Dp, val y: Dp) {
    fun toPx(scope: DrawScope) = with(scope) { Offset(x.toPx(), y.toPx()) }
}

// ── The call under the sky ──────────────────────────────────────────────────

/**
 * Under the sky: a line on how the day stands, and the button that lights the star of the prayer
 * whose time is on, with what it brings: its XP, and the full day's bonus when it's the last one.
 * Once the day is full, its quiet acknowledgment in place of the button.
 */
@Composable
internal fun SkyCall(today: PrayerLogDay, isFirstUse: Boolean, onMark: (PrayerLogEntry) -> Unit, modifier: Modifier = Modifier) {
    val active = today.entries.firstOrNull { it.status == PrayerLogStatus.ACTIVE }
    val left = today.entries.count { it.status != PrayerLogStatus.PRAYED }
    val anyMissed = today.entries.any { it.status == PrayerLogStatus.MISSED }
    val line = when {
        today.isComplete -> stringResource(R.string.prayer_log_sky_done)
        active == null && anyMissed -> stringResource(R.string.prayer_log_sky_mark_missed)
        else -> stringResource(R.string.prayer_log_sky_left, left)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = line,
            style = TextStyle(fontFamily = CormorantGaramond, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 25.sp),
            color = Color(0xFFECEAFF),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (isFirstUse && today.prayed == 0) {
            Text(
                text = stringResource(R.string.prayer_log_hint),
                style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                color = skyFaint(0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        when {
            active != null -> {
                val completes = left == 1
                val xp = PrayerLogProgress.XP_PER_PRAYER + if (completes) PrayerLogProgress.FULL_DAY_BONUS else 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .drawBehind {
                            drawRoundRect(
                                Brush.radialGradient(listOf(Color(0xFF6E64DC).copy(alpha = 0.35f), Color.Transparent), center, size.width * 0.6f),
                                topLeft = Offset(0f, 8.dp.toPx()),
                                size = Size(size.width, size.height + 6.dp.toPx())
                            )
                        }
                        .clip(RoundedCornerShape(29.dp))
                        .background(Color(0xFF9FA8DA).copy(alpha = 0.22f))
                        .border(1.dp, Color(0xFFD6DAFF).copy(alpha = 0.55f), RoundedCornerShape(29.dp))
                        .clickable(role = Role.Button) { onMark(active) }
                        .padding(start = 22.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StarGlyph(16.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.prayer_log_mark),
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = SkyInk
                    )
                    Text(
                        text = "  ·  ${active.type.prayerName}",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        color = skyFaint(0.75f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Box(
                        Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color(0xFF080A1E).copy(alpha = 0.45f))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.prayer_log_xp_gain, xp),
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                            color = Color(0xFFE3E6FF)
                        )
                    }
                }
            }
            today.isComplete -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = StarWhite, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.prayer_log_all_done),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = SkyInk
                    )
                }
            }
        }
    }
}

// ── Three numbers ───────────────────────────────────────────────────────────

/** The streak, the level with what the next one takes, and the badges earned: three tiles. */
@Composable
internal fun SkyNumbers(state: PrayerLogViewState, modifier: Modifier = Modifier) {
    val progress = state.progress
    val fraction by animateFloatAsState(progress.levelXp.toFloat() / progress.levelSpan, tween(700), label = "levelBar")
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkyTile(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (state.streak > 0) Color(0xFFFFCC80) else skyFaint(0.3f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(state.streak.toString(), style = NumberStyle, color = SkyInk)
            }
            Text(stringResource(R.string.prayer_log_streak), style = CaptionStyle, color = skyFaint(0.62f), maxLines = 2)
        }
        SkyTile(Modifier.weight(1f)) {
            Text(stringResource(R.string.prayer_log_level, progress.level).uppercase(), style = LabelStyle, color = skyFaint(0.6f), maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF9FA8DA), Color.White)))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.prayer_log_xp_to_next, progress.levelSpan - progress.levelXp, progress.level + 1),
                style = CaptionStyle,
                color = skyFaint(0.7f),
                maxLines = 2
            )
        }
        SkyTile(Modifier.weight(1f)) {
            Text(stringResource(R.string.prayer_log_badges).uppercase(), style = LabelStyle, color = skyFaint(0.6f), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(
                text = buildAnnotatedString {
                    append(progress.earned.size.toString())
                    withStyle(SpanStyle(fontSize = 14.sp, color = skyFaint(0.55f))) { append("/${progress.badges.size}") }
                },
                style = NumberStyle,
                color = SkyInk
            )
        }
    }
}

/** A pane of the night's glass, faint white with a faint white edge. */
@Composable
internal fun SkyTile(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(12.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(padding),
        content = content
    )
}

private val NumberStyle = TextStyle(fontFamily = CormorantGaramond, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp)
private val LabelStyle = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
private val CaptionStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)

/** The screen's section titles: a serif name over a quiet line. */
@Composable
internal fun SkySectionTitle(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = TextStyle(fontFamily = CormorantGaramond, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 26.sp), color = SkyInk)
        Text(hint, style = TextStyle(fontSize = 11.sp), color = skyFaint(0.55f), maxLines = 1)
    }
}

internal val PrayerLogStatus.labelRes: Int
    get() = when (this) {
        PrayerLogStatus.PRAYED -> R.string.prayer_log_status_prayed
        PrayerLogStatus.MISSED -> R.string.prayer_log_status_missed
        PrayerLogStatus.ACTIVE -> R.string.prayer_log_status_active
        PrayerLogStatus.UPCOMING -> R.string.prayer_log_status_upcoming
        PrayerLogStatus.UNTRACKED -> R.string.prayer_log_status_untracked
    }
