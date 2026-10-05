package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.HijriUtils
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.dialTimeStyle
import com.ybugmobile.waktiva.ui.home.composables.gear.dayAngle
import com.ybugmobile.waktiva.ui.home.composables.gear.pointOn
import com.ybugmobile.waktiva.ui.home.composables.gear.toDegrees
import com.ybugmobile.waktiva.ui.home.composables.timeShade
import com.ybugmobile.waktiva.ui.theme.getGradientColorsForTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Today on the dial of the day, the sky's own clock: midnight at the bottom and noon at the top,
 * as on the home screen's circle, with the hours round its rim and the sun walking them.
 *
 * Inside, the horizon runs from sunrise to sunset across the dial: over it the sky of the hour in
 * the home screen's colours, under it the deep sky. Each prayer is its own star on the ring (see
 * [prayerStar]), lit with a tap. The ring between two prayers is a dotted path in their colours
 * until both are prayed, then lit; with all five prayed the night's stretch lights too and the
 * ring closes. The prayer whose time is on fills its stretch in lilac as its time goes by. At the
 * centre, the moon: the Hijri date and the moon of its day, the Gregorian date with a tap, and the
 * day's tally under it.
 *
 * [times] are today's prayer times, [now] the time to the minute.
 */
@Composable
internal fun TodayDial(
    today: PrayerLogDay,
    times: PrayerDay?,
    now: LocalTime,
    onToggle: (PrayerLogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val minutes = remember(today, times) { dialMinutes(today, times) }
    val nowMin = now.hour * 60f + now.minute
    val sky = remember(now, times) { dialSky(nowMin, minutes, getGradientColorsForTime(now, times)) }
    val current = remember(nowMin, minutes) { currentPrayer(nowMin, minutes) }
    val prayed = today.entries.filter { it.status == PrayerLogStatus.PRAYED }.mapTo(mutableSetOf()) { it.type }
    val full = prayed.size == LoggedPrayers.size

    val pulse = rememberInfiniteTransition(label = "dialPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "dialPulse"
    )
    val twinkle = rememberInfiniteTransition(label = "dialTwinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "dialTwinkle"
    )
    val starTwinkle = rememberInfiniteTransition(label = "dialStarTwinkle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "dialStarTwinkle"
    )
    // Each stretch of the ring lights as its two prayers are both prayed; the night's, as all are.
    val litStretches = DialStretches.mapIndexed { i, (from, to) ->
        val lit = if (i == DialStretches.lastIndex) full else from in prayed && to in prayed
        animateFloatAsState(if (lit) 1f else 0f, tween(700), label = "stretch$i")
    }
    val closed by animateFloatAsState(if (full) 1f else 0f, tween(900), label = "ringClosed")

    val cosmos = ImageBitmap.imageResource(R.drawable.cetele_cosmos)
    val field = remember { dialField() }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember { dialTimeStyle(SkyInk, isLandscape = false) }
    val sunriseStyle = remember { labelStyle.copy(color = SkyInk.copy(alpha = 0.62f)) }

    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val u = with(density) { side.toPx() } / 400f

        Spacer(
            Modifier
                .size(side)
                .drawWithCache {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val ring = 172f * u
                    val disc = 163f * u
                    fun angle(m: Float) = dayAngle(m, rtl)
                    val dir = if (rtl) -1f else 1f
                    val sunrise = minutes.getValue(PrayerType.SUNRISE)
                    val sunset = minutes.getValue(PrayerType.MAGHRIB)
                    val dayLength = (sunset - sunrise).mod(1440f)
                    val discRect = Rect(c, disc)
                    val dome = Path().apply {
                        arcTo(discRect, angle(sunrise).toDegrees(), dir * dayLength / 4f, true)
                        close()
                    }
                    val night = Path().apply {
                        arcTo(discRect, angle(sunset).toDegrees(), dir * (1440f - dayLength) / 4f, true)
                        close()
                    }
                    val discPath = Path().apply { addOval(discRect) }
                    val rise = pointOn(c, disc, angle(sunrise))
                    val set = pointOn(c, disc, angle(sunset))
                    val horizon = Offset((rise.x + set.x) / 2f, (rise.y + set.y) / 2f)
                    val tilt = atan2(set.y - rise.y, set.x - rise.x).toDegrees()
                    val domeBrush = Brush.verticalGradient(sky.colors, startY = c.y - disc, endY = horizon.y)

                    // The deep sky's still, cropped square to cover the disc.
                    val crop = minOf(cosmos.width, cosmos.height)
                    val srcOffset = IntOffset((cosmos.width - crop) / 2, (cosmos.height - crop) / 2)
                    val dstSize = (2 * disc).roundToInt()
                    val dstOffset = IntOffset((c.x - disc).roundToInt(), (c.y - disc).roundToInt())

                    val ticks = Path().apply {
                        for (h in 0 until 24) {
                            if (h % 6 == 0) continue
                            moveTo(pointOn(c, 183f * u, angle(h * 60f)))
                            lineTo(pointOn(c, 188f * u, angle(h * 60f)))
                        }
                    }
                    val majors = Path().apply {
                        for (h in 0 until 24 step 6) {
                            moveTo(pointOn(c, 179f * u, angle(h * 60f)))
                            lineTo(pointOn(c, 193f * u, angle(h * 60f)))
                        }
                    }

                    val stretches = DialStretches.map { (from, to) ->
                        val start = minutes.getValue(from) + StarGap
                        val span = (minutes.getValue(to) - minutes.getValue(from)).mod(1440f).let { if (it == 0f) 1440f else it } - 2 * StarGap
                        val path = Path().apply {
                            if (span > 0f) arcTo(Rect(c, ring), angle(start).toDegrees(), dir * span / 4f, true)
                        }
                        val a = pointOn(c, ring, angle(start))
                        val b = pointOn(c, ring, angle(start + span.coerceAtLeast(0f)))
                        Triple(
                            path,
                            Brush.linearGradient(listOf(from.accentColor, to.accentColor), a, b),
                            Brush.linearGradient(listOf(from.paleColor, to.paleColor), a, b)
                        )
                    }
                    val dots = Stroke(2.6f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 7f * u)))

                    // The stretch of the prayer whose time is on, filled up to now.
                    val progress = current?.let { type ->
                        val start = minutes.getValue(type) + StarGap
                        val span = (nowMin - start).mod(1440f)
                        if (nowMin - minutes.getValue(type) > StarGap + 6f && span < 720f) {
                            Path().apply { arcTo(Rect(c, ring), angle(start).toDegrees(), dir * span / 4f, true) }
                        } else null
                    }
                    val progressColor = if (current != null && current in prayed) StarWhite else NowLilac

                    val labels = (LoggedPrayers + PrayerType.SUNRISE).map { type ->
                        val text = textMeasurer.measure(formatMinutes(minutes.getValue(type)), if (type == PrayerType.SUNRISE) sunriseStyle else labelStyle)
                        text to pointOn(c, 137f * u, angle(minutes.getValue(type)))
                    }
                    val sunriseAt = pointOn(c, ring, angle(sunrise))
                    val handAngle = angle(nowMin)
                    val handRoot = pointOn(c, 62f * u, handAngle)
                    val handTip = pointOn(c, 178f * u, handAngle)
                    val hand = Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.75f)), handRoot, handTip)
                    val sunAt = pointOn(c, 186f * u, handAngle)
                    val isDayNow = (nowMin - sunrise).mod(1440f) < dayLength
                    val fieldStars = field.map { Offset(c.x + it.x * disc, c.y + it.y * disc) }

                    onDrawBehind {
                        // Lifted off the screen by a soft shade round it.
                        drawCircle(
                            Brush.radialGradient(0.9f to Color(0x6B02030E), 1f to Color.Transparent, center = c, radius = 182f * u),
                            182f * u,
                            c
                        )

                        val t = twinkle.value
                        fun stars(alpha: Float) {
                            if (alpha <= 0f) return
                            field.forEachIndexed { i, star ->
                                val shimmer = if (star.bright) 0.55f + 0.45f * sin(2f * PI.toFloat() * (t + star.phase)) else 1f
                                drawCircle(
                                    (if (star.bright) StarWhite else Color.White).copy(alpha = star.alpha * shimmer * alpha),
                                    radius = (if (star.bright) 1.05f else 0.55f) * u,
                                    center = fieldStars[i]
                                )
                            }
                        }

                        clipPath(night) {
                            drawRect(Color(0xFF05040F))
                            drawImage(
                                cosmos,
                                srcOffset = srcOffset,
                                srcSize = IntSize(crop, crop),
                                dstOffset = dstOffset,
                                dstSize = IntSize(dstSize, dstSize),
                                alpha = 0.9f,
                                filterQuality = FilterQuality.Medium
                            )
                            stars(1f)
                            rotate(tilt, horizon) {
                                drawRect(
                                    Brush.verticalGradient(listOf(sky.colors.last().copy(alpha = 0.32f), Color.Transparent), startY = horizon.y, endY = horizon.y + 56f * u),
                                    topLeft = Offset(horizon.x - 200f * u, horizon.y),
                                    size = Size(400f * u, 56f * u)
                                )
                            }
                        }
                        clipPath(dome) {
                            drawRect(domeBrush)
                            stars(sky.stars)
                        }
                        clipPath(discPath) {
                            withTransform({
                                rotate(tilt, horizon)
                                scale(1f, 44f / 190f, horizon)
                            }) {
                                drawCircle(Brush.radialGradient(listOf(sky.glow, Color.Transparent), horizon, 190f * u), 190f * u, horizon)
                            }
                            drawCircle(
                                Brush.radialGradient(0.9f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.42f), center = c, radius = disc),
                                disc,
                                c
                            )
                        }
                        drawLine(SkyInk.copy(alpha = if (sky.isDay) 0.55f else 0.26f), rise, set, 1.dp.toPx())

                        // The rim: the ring the prayers sit on, and the hours outside it.
                        drawCircle(SkyInk.copy(alpha = 0.13f), ring, c, style = Stroke(1.dp.toPx()))
                        drawCircle(SkyInk.copy(alpha = 0.10f), 190f * u, c, style = Stroke(0.8.dp.toPx()))
                        drawPath(ticks, SkyInk.copy(alpha = 0.34f), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(majors, SkyInk.copy(alpha = 0.72f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
                        drawCircle(Color(0xFFFFE082).copy(alpha = 0.55f), 7.5f * u, sunriseAt, style = Stroke(1.dp.toPx()))
                        drawCircle(Color(0xFFFFE082), 3.6f * u, sunriseAt)

                        stretches.forEachIndexed { i, (path, dotted, lit) ->
                            val on = litStretches[i].value
                            if (on < 1f) drawPath(path, dotted, alpha = 0.8f * (1f - on), style = dots)
                            if (on > 0f) {
                                drawPath(path, lit, alpha = 0.22f * on, style = Stroke(9f * u, cap = StrokeCap.Round))
                                drawPath(path, lit, alpha = on, style = Stroke(2.2f * u, cap = StrokeCap.Round))
                            }
                        }
                        if (closed > 0f) {
                            drawCircle(StarWhite.copy(alpha = 0.16f * closed), ring, c, style = Stroke(16f * u))
                            drawCircle(StarWhite.copy(alpha = 0.9f * closed), ring, c, style = Stroke(1.dp.toPx()))
                        }
                        progress?.let {
                            val breath = 0.55f + 0.45f * sin(2f * PI.toFloat() * pulse.value)
                            drawPath(it, progressColor, alpha = 0.22f * breath, style = Stroke(9f * u, cap = StrokeCap.Round))
                            drawPath(it, progressColor, alpha = breath, style = Stroke(2.4f * u, cap = StrokeCap.Round))
                        }

                        labels.forEach { (text, at) ->
                            timeShade(at, text.size.width.toFloat(), text.size.height.toFloat())
                            drawText(text, topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f))
                        }

                        // Now: a hand to the rim, where the sun walks the hours by day; by night, a lilac light.
                        drawLine(hand, handRoot, handTip, 1.4.dp.toPx(), StrokeCap.Round)
                        val nowColor = if (isDayNow) Color(0xFFFFE082) else NowLilac
                        drawCircle(Brush.radialGradient(listOf(nowColor.copy(alpha = 0.8f), Color.Transparent), sunAt, 20f * u), 20f * u, sunAt)
                        drawCircle(if (isDayNow) Color(0xFFFFF6D6) else NowLilac, (if (isDayNow) 5.5f else 4f) * u, sunAt)
                        drawCircle(Color.White.copy(alpha = 0.85f), (if (isDayNow) 5.5f else 4f) * u, sunAt, style = Stroke(0.8.dp.toPx()))
                    }
                }
        )

        // The name of the prayer whose time is on, over the moon.
        current?.let { type ->
            Text(
                type.prayerName.uppercase(LocalConfiguration.current.locales[0]),
                style = TextStyle(
                    fontFamily = LogFonts.names,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 3.sp,
                    shadow = TextShade
                ),
                color = type.accentColor,
                maxLines = 1,
                modifier = Modifier.offset(y = with(density) { (-82f * u).toDp() })
            )
        }

        val starBox = with(density) { (60f * u).toDp() }
        today.entries.forEachIndexed { i, entry ->
            key(entry.type) {
                val at = pointOn(Offset.Zero, 172f * u, dayAngle(minutes.getValue(entry.type), rtl))
                val inDome = (minutes.getValue(entry.type) - minutes.getValue(PrayerType.SUNRISE)).mod(1440f) <=
                    (minutes.getValue(PrayerType.MAGHRIB) - minutes.getValue(PrayerType.SUNRISE)).mod(1440f)
                DialStar(
                    entry = entry,
                    onLightSky = sky.isDay && inDome,
                    pulse = pulse,
                    twinkle = starTwinkle,
                    phase = i * 0.23f,
                    onClick = { onToggle(entry) },
                    modifier = Modifier
                        .size(starBox)
                        .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                )
            }
        }

        DialMoon(today, times, size = with(density) { (112f * u).toDp() }, u = u)
    }
}

/**
 * A prayer on the dial: its own star in its state, lit with a tap, flashing as it lights with its
 * XP rising from it. A prayer whose time hasn't come can't be tapped.
 */
@Composable
private fun DialStar(
    entry: PrayerLogEntry,
    onLightSky: Boolean,
    pulse: State<Float>,
    twinkle: State<Float>,
    phase: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = entry.status
    val lit = status == PrayerLogStatus.PRAYED
    val name = entry.type.prayerName
    val statusText = stringResource(status.labelRes)
    val pop by animateFloatAsState(
        if (lit) 1f else 0.6f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "dialStarPop"
    )
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
    val time = entry.time?.let { " ${it.format(TimeFormat)}" }.orEmpty()

    Box(
        modifier
            .clip(CircleShape)
            .then(if (status != PrayerLogStatus.UPCOMING) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = "$name$time, $statusText" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val shimmer = 0.5f + 0.5f * cos(2f * PI.toFloat() * (twinkle.value + phase))
            prayerStar(entry.type, status, center, pop * 0.8f, shimmer, pulse.value, ignite.value, isDay = onLightSky)
        }
        XpPop(marks, Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true).align(Alignment.TopCenter))
    }
}

/**
 * The moon at the dial's heart: the Hijri date with the moon of its day, or with a tap the
 * Gregorian date; and the day's five stars under it, lit as the prayers are.
 */
@Composable
private fun DialMoon(today: PrayerLogDay, times: PrayerDay?, size: androidx.compose.ui.unit.Dp, u: Float) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    var gregorian by rememberSaveable { mutableStateOf(false) }
    val hijri = remember(today.date, times) { times?.hijriDate ?: HijriUtils.calculateFallbackHijri(today.date) }
    val hijriMonth = hijri?.let {
        val res = context.resources.getIdentifier("hijri_month_${it.monthNumber}", "string", context.packageName)
        if (res != 0) context.getString(res) else it.monthEn
    }.orEmpty()
    val showHijri = !gregorian && hijri != null
    val fs = { px: Float -> with(density) { px.toSp() } }
    val small = TextStyle(fontFamily = LogFonts.text, fontWeight = FontWeight.SemiBold, fontSize = fs(max(8.5f * u, with(density) { 7.sp.toPx() })), letterSpacing = 1.4.sp)
    val big = TextStyle(fontFamily = LogFonts.numbers, fontWeight = FontWeight.SemiBold, fontSize = fs(30f * u), lineHeight = fs(32f * u))
    val description = if (showHijri) "${hijri!!.day} $hijriMonth ${hijri.year}" else today.date.format(DateTimeFormatter.ofPattern("d MMMM EEEE", locale))

    Column(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xC72C305C), Color(0xDB060816))))
            .border(1.dp, SkyInk.copy(alpha = 0.34f), CircleShape)
            .clickable(role = Role.Button) { gregorian = !gregorian }
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showHijri) {
            val phase = ((hijri!!.day - 1) / 29.53f).coerceIn(0f, 1f)
            Canvas(Modifier.size(with(density) { (15f * u).toDp() })) { drawMoonPhase(phase) }
            Text(hijri.day.toString(), style = big, color = SkyInk)
            Text(hijriMonth.uppercase(locale), style = small, color = skyFaint(0.74f), maxLines = 1)
        } else {
            Text(today.date.format(DateTimeFormatter.ofPattern("EEEE", locale)).uppercase(locale), style = small, color = skyFaint(0.74f), maxLines = 1)
            Text(today.date.dayOfMonth.toString(), style = big, color = SkyInk)
            Text(today.date.format(DateTimeFormatter.ofPattern("MMMM", locale)).uppercase(locale), style = small, color = skyFaint(0.74f), maxLines = 1)
        }
        Row(
            Modifier.padding(top = with(density) { (4f * u).toDp() }),
            horizontalArrangement = Arrangement.spacedBy(with(density) { (3f * u).toDp() })
        ) {
            today.entries.forEach { entry ->
                val on = entry.status == PrayerLogStatus.PRAYED
                Canvas(Modifier.size(with(density) { max(8f * u, 6.dp.toPx()).toDp() })) {
                    val star = sparkle(center, this.size.minDimension / 2f)
                    if (on) drawPath(star, entry.type.paleColor)
                    else drawPath(star, SkyInk.copy(alpha = 0.5f), style = Stroke(0.9.dp.toPx()))
                }
            }
        }
    }
}

/** The moon [phase] of the way through its month (0 new, 0.5 full), lit as seen from the north. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoonPhase(phase: Float) {
    val r = size.minDimension / 2f
    val c = center
    drawCircle(MoonLight.copy(alpha = 0.16f), r, c)
    val k = cos(2f * PI.toFloat() * phase)
    val waxing = phase < 0.5f
    val lit = Path().apply {
        // The lit half, then back along the terminator.
        arcTo(Rect(c, r), -90f, if (waxing) 180f else -180f, true)
        val rx = r * abs(k)
        val bulgesOut = k < 0f
        arcTo(Rect(c.x - rx, c.y - r, c.x + rx, c.y + r), 90f, if (waxing == bulgesOut) 180f else -180f, false)
        close()
    }
    drawPath(lit, MoonLight)
}

/** The stretches of the ring, prayer to prayer; the last, the night's, from Isha round to Fajr. */
private val DialStretches = listOf(
    PrayerType.FAJR to PrayerType.DHUHR,
    PrayerType.DHUHR to PrayerType.ASR,
    PrayerType.ASR to PrayerType.MAGHRIB,
    PrayerType.MAGHRIB to PrayerType.ISHA,
    PrayerType.ISHA to PrayerType.FAJR
)

/** How far, in minutes of the dial, a stretch keeps clear of the stars at its ends. */
private const val StarGap = 22f

/** Each prayer's time and sunrise in minutes of the day; a missing one at a time it usually has. */
private fun dialMinutes(today: PrayerLogDay, times: PrayerDay?): Map<PrayerType, Float> {
    val defaults = mapOf(
        PrayerType.FAJR to 330f, PrayerType.SUNRISE to 410f, PrayerType.DHUHR to 780f,
        PrayerType.ASR to 990f, PrayerType.MAGHRIB to 1110f, PrayerType.ISHA to 1200f
    )
    return PrayerType.entries.associateWith { type ->
        val time = times?.timings?.get(type) ?: today.entries.firstOrNull { it.type == type }?.time
        time?.let { it.hour * 60f + it.minute } ?: defaults.getValue(type)
    }
}

/** The prayer whose time is on at [now], by the clock, or null between Fajr's end and Dhuhr, or before Fajr. */
private fun currentPrayer(now: Float, minutes: Map<PrayerType, Float>): PrayerType? {
    val type = LoggedPrayers.lastOrNull { minutes.getValue(it) <= now } ?: return null
    val end = when (type) {
        PrayerType.FAJR -> minutes.getValue(PrayerType.SUNRISE)
        PrayerType.DHUHR -> minutes.getValue(PrayerType.ASR)
        PrayerType.ASR -> minutes.getValue(PrayerType.MAGHRIB)
        PrayerType.MAGHRIB -> minutes.getValue(PrayerType.ISHA)
        else -> 1440f
    }
    return type.takeIf { now < end }
}

/**
 * The dial's sky at [now]: the home screen's [colors] for the hour, deepened a little so the dial
 * reads as a window, how much of the stars show through by day, the glow on the horizon, and
 * whether it's light enough that the stars need a shade under them.
 */
private class DialSky(val colors: List<Color>, val stars: Float, val glow: Color, val isDay: Boolean)

private fun dialSky(now: Float, minutes: Map<PrayerType, Float>, colors: List<Color>): DialSky {
    val deep = colors.map { lerp(it, Color(0xFF03030A), 0.18f) }
    val fajr = minutes.getValue(PrayerType.FAJR)
    val sunrise = minutes.getValue(PrayerType.SUNRISE)
    val maghrib = minutes.getValue(PrayerType.MAGHRIB)
    val isha = minutes.getValue(PrayerType.ISHA)
    return when {
        now < fajr || now >= isha -> DialSky(deep, 1f, Color(0x529FA8DA), false)
        now < sunrise -> DialSky(deep, 0.85f, Color(0x73AA8CE6), false)
        now < sunrise + 45f -> DialSky(deep, 0.04f, Color(0xB3FFBE78), true)
        now < maghrib - 45f -> DialSky(deep, 0f, Color(0x80F0FAFF), true)
        now < maghrib -> DialSky(deep, 0.2f, Color(0xB3FF8046), false)
        else -> DialSky(deep, 0.6f, Color(0x66C8506E), false)
    }
}

/** A star of the dial's sky, its place on a disc of radius 1. */
private class FieldStar(val x: Float, val y: Float, val alpha: Float, val phase: Float, val bright: Boolean)

private fun dialField(): List<FieldStar> {
    val random = Random(7)
    return List(160) {
        val r = sqrt(random.nextFloat())
        val a = 2f * PI.toFloat() * random.nextFloat()
        FieldStar(
            x = r * cos(a),
            y = r * sin(a),
            alpha = 0.35f + random.nextFloat() * 0.55f,
            phase = random.nextFloat(),
            bright = random.nextFloat() >= 0.8f
        )
    }
}

private fun formatMinutes(m: Float): String {
    val total = m.roundToInt().mod(1440)
    return "%02d:%02d".format(total / 60, total % 60)
}

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Path.moveTo(p: Offset) = moveTo(p.x, p.y)
private fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)
