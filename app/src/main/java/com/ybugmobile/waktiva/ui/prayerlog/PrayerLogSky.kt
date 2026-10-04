package com.ybugmobile.waktiva.ui.prayerlog

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
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import kotlin.math.PI
import kotlin.math.cos
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
 * The night the prayer log is drawn on, whatever the hour: deep blue into violet, a glow low down,
 * and stars twinkling in it, more of them high up where the sky is darkest.
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
 * day. A prayer marked is a star lit, and the lit ones are joined, line by line, into the day's own
 * constellation; the one whose time is on pulses, waiting; one gone unmarked is a dark hole ringed
 * in coral; one to come is a faint outline. Tapping a star marks it or takes the mark back.
 *
 * With [cassiopeia], the day just made full, the five glide off the sun's path into Cassiopeia's W,
 * five prayers as the queen's five stars, and its lines are drawn in.
 */
@Composable
internal fun DayStars(
    today: PrayerLogDay,
    cassiopeia: Boolean,
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
            drawOval(
                Brush.radialGradient(
                    listOf(Color(0xFF9FA8DA).copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(size.width / 2f, horizon),
                    radius = size.width * 0.6f
                ),
                topLeft = Offset(size.width / 2f - size.width * 0.6f, horizon - 60.dp.toPx()),
                size = Size(size.width * 1.2f, 120.dp.toPx())
            )
            drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
            drawMoon(Offset(if (rtl) size.width - 70.dp.toPx() else 70.dp.toPx(), 60.dp.toPx()), 22.dp.toPx())

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
                    Color.White.copy(alpha = 0.16f * arcAlpha),
                    style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())))
                )
                // The day's constellation: the lit stars joined in their order.
                val lit = today.entries.indices.filter { today.entries[it].status == PrayerLogStatus.PRAYED }
                if (lit.size > 1) {
                    val line = Path().apply {
                        lit.forEachIndexed { n, i ->
                            val p = onArc[i].toPx(this@Canvas)
                            if (n == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                        }
                    }
                    val brush = Brush.horizontalGradient(listOf(Color(0xFF81D4FA), Color(0xFFFFF8D6), Color(0xFF9FA8DA)))
                    drawPath(line, brush, alpha = 0.18f * arcAlpha, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(line, brush, alpha = arcAlpha, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // A dotted line on to the prayer whose time is on, breathing.
                val active = today.entries.indexOfFirst { it.status == PrayerLogStatus.ACTIVE }
                val before = lit.lastOrNull { it < active }
                if (active >= 0 && before != null) {
                    val breath = 0.55f + 0.45f * sin(2f * PI.toFloat() * pulse.value)
                    drawLine(
                        NowLilac.copy(alpha = breath * arcAlpha),
                        onArc[before].toPx(this),
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
                    onClick = { onToggle(entry) },
                    modifier = Modifier.offset(x - 32.dp, y - 32.dp)
                )
            }
        }

        // How many are lit, in the middle; the queen's name in its place while she shows.
        val lit = today.prayed
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
                    brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFC9C6F2)))
                )
            )
            Text(
                text = stringResource(R.string.prayer_log_sky_lit).uppercase(),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp),
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
                style = TextStyle(fontFamily = CormorantGaramond, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 36.sp),
                color = SkyInk
            )
            Text(
                text = stringResource(R.string.prayer_log_cassiopeia_line).uppercase(),
                style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
                color = skyFaint(0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * One prayer in the sky: its star in its state, with its name and time under it. The whole of it,
 * 64 by 84, is the tap target, except for a prayer whose time hasn't come.
 */
@Composable
private fun SkyStar(
    entry: PrayerLogEntry,
    brightness: Float,
    showLabel: Boolean,
    pulse: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = entry.status
    val accent = entry.type.accentColor
    val name = entry.type.prayerName
    val statusText = stringResource(status.labelRes)
    val lit = status == PrayerLogStatus.PRAYED
    val pop by animateFloatAsState(
        if (lit) brightness else 0.6f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "starPop"
    )
    val labelAlpha by animateFloatAsState(if (showLabel) 1f else 0f, tween(600), label = "starLabel")
    // Each time it's lit, its XP rises from it.
    var marks by remember { mutableIntStateOf(0) }
    var lastStatus by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (lit && lastStatus != PrayerLogStatus.PRAYED) marks++
        lastStatus = status
    }

    Column(
        modifier
            .size(64.dp, 84.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(if (status != PrayerLogStatus.UPCOMING) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = "$name, $statusText" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                when (status) {
                    PrayerLogStatus.PRAYED -> {
                        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), c, size.minDimension * 0.62f))
                        drawPath(sparkle(c, 17.dp.toPx() * pop), StarWhite)
                    }
                    PrayerLogStatus.ACTIVE -> {
                        val t = pulse.value
                        drawCircle(NowLilac.copy(alpha = 0.9f * (1f - t)), radius = 18.dp.toPx() * (0.7f + 1.2f * t), style = Stroke(1.5.dp.toPx()))
                        drawCircle(Color(0xFF9FA8DA).copy(alpha = 0.18f), radius = 14.dp.toPx())
                        drawCircle(NowLilac.copy(alpha = 0.7f), radius = 14.dp.toPx(), style = Stroke(1.dp.toPx()))
                        drawPath(sparkle(c, 10.dp.toPx()), Color(0xFFDDE1FF), style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
                    }
                    PrayerLogStatus.MISSED -> {
                        drawCircle(Color(0xFF03050D).copy(alpha = 0.9f), radius = 8.dp.toPx())
                        drawCircle(MissedCoral.copy(alpha = 0.7f), radius = 8.dp.toPx(), style = Stroke(1.2.dp.toPx()))
                    }
                    PrayerLogStatus.UPCOMING, PrayerLogStatus.UNTRACKED -> {
                        drawPath(sparkle(c, 9.dp.toPx()), Color.White.copy(alpha = 0.32f), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
                    }
                }
            }
            XpPop(marks, Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true).align(Alignment.TopCenter))
        }
        Column(Modifier.offset(y = (-10).dp).alpha(labelAlpha), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = name,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                color = if (status == PrayerLogStatus.MISSED) MissedCoral else if (status == PrayerLogStatus.UPCOMING) skyFaint(0.55f) else SkyInk,
                maxLines = 1,
                softWrap = false
            )
            entry.time?.let {
                Text(
                    text = it.format(TimeFormat),
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                    color = skyFaint(0.5f),
                    maxLines = 1
                )
            }
        }
    }
}

private val TimeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

/** A new moon, lit on its outer edge. */
private fun DrawScope.drawMoon(center: Offset, r: Float) {
    val moon = Path().apply {
        addOval(Rect(center, r))
    }
    val bite = Path().apply {
        addOval(Rect(Offset(center.x + r * 0.42f, center.y - r * 0.18f), r * 0.86f))
    }
    val crescent = Path().apply { op(moon, bite, androidx.compose.ui.graphics.PathOperation.Difference) }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF6F1DC).copy(alpha = 0.25f), Color.Transparent), center, r * 2f), r * 2f, center)
    drawPath(crescent, Color(0xFFF6F1DC), style = Fill)
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
