package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.ui.theme.CormorantGaramond
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Milky Way: the last five weeks as a band of stars, a column a day and a lane a prayer, the
 * day's order top to bottom. Prayers marked are stars, and the more of a day's are, the wider and
 * brighter the galaxy there, its core lit through full days; a prayer gone unmarked is a dark hole
 * ringed in coral. The whole band is one wide touch target: a finger on it picks the nearest day,
 * glides along to the next with a tick, and a loupe above shows the day under it. The picked day's
 * prayers are under the band to mark, with arrows to step a day at a time.
 */
@Composable
internal fun Galaxy(
    state: PrayerLogViewState,
    onShowGalaxy: (Int) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val start = state.galaxyStart ?: return
    var gaps by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SkySectionTitle(
                title = stringResource(R.string.prayer_log_galaxy),
                hint = stringResource(R.string.prayer_log_galaxy_hint),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            MissedSwitch(gaps, state.galaxyMissed, onChange = { gaps = it })
        }
        Spacer(Modifier.height(18.dp))
        GalaxyBand(
            days = state.galaxy,
            start = start,
            selected = state.selected?.date,
            gaps = gaps,
            onPick = onSelect
        )
        RangeRow(state, start, onShowGalaxy, Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(8.dp))
        state.selected?.let { day ->
            DayPanel(
                day = day,
                isToday = day.date == state.today?.date,
                canGoBack = state.canShowEarlier || day.date.isAfter(start),
                onStep = { onSelect(day.date.plusDays(it)) },
                onToggle = onToggle,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

private val BandHeight = 176.dp
private val Gutter = 30.dp
private val BandEnd = 12.dp

/** Each lane's star: the prayer's colour paled almost to white. */
private val LaneStars = listOf(Color(0xFFD6F0FF), Color(0xFFFFFCE0), Color(0xFFFFEBD1), Color(0xFFF6E0FA), Color(0xFFE6E9FF))

@Composable
private fun GalaxyBand(
    days: List<PrayerLogDay?>,
    start: LocalDate,
    selected: LocalDate?,
    gaps: Boolean,
    onPick: (LocalDate) -> Unit
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val locale = LocalConfiguration.current.locales[0]
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val pick by rememberUpdatedState(onPick)
    var dragging by remember { mutableStateOf(false) }
    var scrub by remember { mutableIntStateOf(-1) }
    val gapsT by animateFloatAsState(if (gaps) 1f else 0f, tween(300), label = "gaps")
    val breath = rememberInfiniteTransition(label = "galaxyBreath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "galaxyBreath"
    )

    val selectedIndex = selected?.let { ChronoUnit.DAYS.between(start, it).toInt() }?.takeIf { it in days.indices } ?: -1
    val shown = if (dragging && scrub >= 0) scrub else selectedIndex
    val lastKnown = days.indexOfLast { it != null }
    val shorts = LoggedPrayers.map { it.prayerName.take(2) }
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLL", locale) }
    val dayMonth = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    // A breath of jitter for each star, the same each time this galaxy is drawn.
    val jitter = remember(start, days.size) {
        val random = Random(start.toEpochDay())
        FloatArray(days.size * 10) { random.nextFloat() - 0.5f }
    }
    val bandLabel = stringResource(R.string.prayer_log_galaxy_hint)

    BoxWithConstraints(Modifier.fillMaxWidth().height(BandHeight)) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val gutter = with(density) { Gutter.toPx() }
        val column = (widthPx - gutter - with(density) { BandEnd.toPx() }) / days.size
        fun x(i: Int): Float = (gutter + column * (i + 0.5f)).let { if (rtl) widthPx - it else it }
        fun indexAt(px: Float): Int {
            val along = if (rtl) widthPx - px else px
            return ((along - gutter) / column).toInt().coerceIn(0, lastKnown.coerceAtLeast(0))
        }
        fun pickAt(px: Float) {
            val i = indexAt(px)
            if (i != scrub) {
                scrub = i
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                days[i]?.let { pick(it.date) }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(widthPx, rtl, days.size, lastKnown) {
                    detectTapGestures { scrub = -1; pickAt(it.x) }
                }
                .pointerInput(widthPx, rtl, days.size, lastKnown) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; scrub = -1; pickAt(it.x) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, _ ->
                        change.consume()
                        pickAt(change.position.x)
                    }
                }
                .semantics { contentDescription = bandLabel }
        ) {
            val lane = { b: Int -> (40 + 24 * b).dp.toPx() }
            val mid = lane(2)
            val counts = days.map { day -> day?.prayed ?: 0 }
            val smooth = counts.indices.map { i ->
                (counts[(i - 1).coerceAtLeast(0)] + 2 * counts[i] + counts[(i + 1).coerceAtMost(counts.lastIndex)]) / 4f
            }

            // The galaxy: a soft band as wide as the prayers marked, its core lit through full days.
            val bandAlpha = 1f - 0.65f * gapsT
            drawBand(smooth.mapIndexed { i, c -> x(i) to (10 + c * 10).dp.toPx() }, mid, rtl,
                Brush.verticalGradient(
                    0f to Color(0xFF7C6BFF).copy(alpha = 0f),
                    0.5f to Color(0xFFB7AEFF).copy(alpha = 0.34f * bandAlpha),
                    1f to Color(0xFF7C6BFF).copy(alpha = 0f),
                    startY = mid - 70.dp.toPx(),
                    endY = mid + 70.dp.toPx()
                )
            )
            drawBand(days.mapIndexed { i, day -> x(i) to (if (day?.isComplete == true) 20f else 3f + 2f * smooth[i]).dp.toPx() }, mid, rtl,
                Brush.verticalGradient(
                    0f to Color(0xFFFFE9D6).copy(alpha = 0f),
                    0.5f to Color(0xFFFFF4E8).copy(alpha = 0.42f * bandAlpha),
                    1f to Color(0xFFFFE9D6).copy(alpha = 0f),
                    startY = mid - 24.dp.toPx(),
                    endY = mid + 24.dp.toPx()
                )
            )

            // Faint lanes, and the weeks parted by dotted lines.
            val laneFrom = minOf(x(0), x(days.lastIndex)) - 6.dp.toPx()
            val laneTo = maxOf(x(0), x(days.lastIndex)) + 6.dp.toPx()
            for (b in 0 until 5) {
                drawLine(Color.White.copy(alpha = 0.05f), Offset(laneFrom, lane(b)), Offset(laneTo, lane(b)), 1.dp.toPx())
            }
            for (i in 7 until days.size step 7) {
                val sx = (x(i - 1) + x(i)) / 2f
                drawLine(
                    Color.White.copy(alpha = 0.12f), Offset(sx, 22.dp.toPx()), Offset(sx, 150.dp.toPx()), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                )
            }

            // The picked day's beam.
            if (shown >= 0) {
                val w = maxOf(column * 1.1f, 10.dp.toPx())
                val top = 22.dp.toPx()
                val h = 130.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0f), 0.2f to Color.White.copy(alpha = 0.16f),
                        0.8f to Color.White.copy(alpha = 0.16f), 1f to Color.White.copy(alpha = 0f),
                        startY = top, endY = top + h
                    ),
                    topLeft = Offset(x(shown) - w / 2f, top), size = Size(w, h), cornerRadius = CornerRadius(w / 2f)
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.28f), topLeft = Offset(x(shown) - w / 2f, top), size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2f), style = Stroke(0.8.dp.toPx())
                )
            }

            // The stars, the holes, and the prayer whose time is on, breathing.
            val starAlpha = 1f - 0.7f * gapsT
            days.forEachIndexed { i, day ->
                day ?: return@forEachIndexed
                day.entries.forEachIndexed { b, entry ->
                    val cx = x(i) + jitter[i * 10 + b * 2] * 1.6.dp.toPx()
                    val cy = lane(b) + jitter[i * 10 + b * 2 + 1] * 2.4.dp.toPx()
                    when (entry.status) {
                        PrayerLogStatus.PRAYED -> {
                            val r = (if (day.isComplete) 2.3f else 1.9f).dp.toPx()
                            drawCircle(
                                Brush.radialGradient(listOf(entry.type.accentColor.copy(alpha = 0.45f * starAlpha), Color.Transparent), Offset(cx, cy), r * 3f),
                                r * 3f, Offset(cx, cy)
                            )
                            drawCircle(LaneStars[b].copy(alpha = starAlpha), r, Offset(cx, cy))
                        }
                        PrayerLogStatus.MISSED -> {
                            val c = Offset(x(i), lane(b))
                            if (gapsT > 0f) {
                                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7878).copy(alpha = 0.6f * gapsT), Color.Transparent), c, 8.dp.toPx()), 8.dp.toPx(), c)
                            }
                            drawCircle(Color(0xFF03050D).copy(alpha = 0.9f), 2.4.dp.toPx(), c)
                            drawCircle(
                                lerpColor(MissedCoral.copy(alpha = 0.5f), Color(0xFFFF9B9B), gapsT), 2.4.dp.toPx(), c,
                                style = Stroke((0.9f + 0.4f * gapsT).dp.toPx())
                            )
                        }
                        PrayerLogStatus.ACTIVE -> {
                            val a = 0.55f + 0.45f * sin(2f * PI.toFloat() * breath.value)
                            drawCircle(NowLilac.copy(alpha = a), 3.2.dp.toPx(), Offset(x(i), lane(b)), style = Stroke(1.dp.toPx()))
                        }
                        PrayerLogStatus.UPCOMING -> drawCircle(Color.White.copy(alpha = 0.12f), 0.9.dp.toPx(), Offset(x(i), lane(b)))
                        PrayerLogStatus.UNTRACKED -> drawCircle(Color.White.copy(alpha = 0.16f), 1.dp.toPx(), Offset(x(i), lane(b)))
                    }
                }
            }

            // Names: the lanes in the gutter, the months over the band, the weeks under it.
            val small = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = skyFaint(0.55f))
            shorts.forEachIndexed { b, short ->
                val text = measurer.measure(short, small)
                val dotX = if (rtl) widthPx - 6.dp.toPx() else 6.dp.toPx()
                drawCircle(LoggedPrayers[b].accentColor, 2.dp.toPx(), Offset(dotX, lane(b)))
                val tx = if (rtl) dotX - 5.dp.toPx() - text.size.width else dotX + 5.dp.toPx()
                drawText(text, topLeft = Offset(tx, lane(b) - text.size.height / 2f))
            }
            val monthStyle = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = skyFaint(0.5f))
            days.indices.forEach { i ->
                val date = start.plusDays(i.toLong())
                if (i == 0 || date.dayOfMonth == 1) {
                    if (i != 0 && i < 4 && start.dayOfMonth != 1) return@forEach
                    val text = measurer.measure(date.format(monthFormat).uppercase(locale), monthStyle)
                    val left = x(i) - column / 2f
                    drawText(text, topLeft = Offset(if (rtl) left - text.size.width + column else left, 2.dp.toPx()))
                }
            }
            val weekStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = skyFaint(0.42f))
            for (i in days.indices step 7) {
                val text = measurer.measure(start.plusDays(i.toLong()).format(dayMonth), weekStyle)
                val cx = (x(i) + x((i + 6).coerceAtMost(days.lastIndex))) / 2f
                drawText(text, topLeft = Offset(cx - text.size.width / 2f, 156.dp.toPx()))
            }
        }

        // The loupe, while a finger glides along.
        if (dragging && shown >= 0) {
            val day = days[shown]
            if (day != null) {
                val at = with(density) { x(shown).toDp() }
                Loupe(
                    day = day,
                    label = day.date.format(dayMonth),
                    modifier = Modifier.offset(x = (at - 38.dp).coerceIn(4.dp, maxWidth - 80.dp), y = (-64).dp)
                )
            }
        }
    }
}

/** A band through the points (x, half its height there) about [mid], filled with [brush]. */
private fun DrawScope.drawBand(points: List<Pair<Float, Float>>, mid: Float, rtl: Boolean, brush: Brush) {
    if (points.isEmpty()) return
    val path = Path()
    val edge = points.first().first + if (rtl) 10.dp.toPx() else -10.dp.toPx()
    path.moveTo(edge, mid)
    points.forEach { (x, h) -> path.lineTo(x, mid - h) }
    points.asReversed().forEach { (x, h) -> path.lineTo(x, mid + h) }
    path.close()
    drawPath(path, brush)
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** The day under the finger, enlarged: its date, its five prayers, how many are marked. */
@Composable
private fun Loupe(day: PrayerLogDay, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .size(76.dp, 82.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF14123A).copy(alpha = 0.94f))
            .border(1.dp, Color(0xFFD6DAFF).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = SkyInk, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            day.entries.forEach { entry ->
                val (fill, edge) = when (entry.status) {
                    PrayerLogStatus.PRAYED -> StarWhite to entry.type.accentColor
                    PrayerLogStatus.MISSED -> Color(0xFF03050D) to Color(0xFFFF9B9B)
                    PrayerLogStatus.ACTIVE -> Color(0xFF03050D) to NowLilac
                    else -> Color.Transparent to Color.White.copy(alpha = 0.25f)
                }
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(1.2.dp, edge, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${day.prayed}/${day.entries.size}",
            style = TextStyle(fontFamily = CormorantGaramond, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            color = SkyInk
        )
    }
}

/** The galaxy's weeks, between the arrows to the ones before and after, and the month's share prayed. */
@Composable
private fun RangeRow(state: PrayerLogViewState, start: LocalDate, onShowGalaxy: (Int) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val end = start.plusWeeks(GALAXY_WEEKS.toLong()).minusDays(1)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShowGalaxy(-1) }, enabled = state.canShowEarlier) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.prayer_log_galaxy_earlier),
                tint = skyFaint(if (state.canShowEarlier) 0.8f else 0.2f)
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${start.format(format)} – ${end.format(format)}",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = skyFaint(0.8f)
            )
            state.last30Days.rate?.let {
                Text(
                    "${stringResource(R.string.prayer_log_month)} ${percent.format(it)}",
                    style = TextStyle(fontSize = 10.5.sp),
                    color = skyFaint(0.5f)
                )
            }
        }
        IconButton(onClick = { onShowGalaxy(1) }, enabled = state.canShowLater) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.prayer_log_galaxy_later),
                tint = skyFaint(if (state.canShowLater) 0.8f else 0.2f)
            )
        }
    }
}

/** "N missed", and a switch that makes the galaxy step back and its holes glow. */
@Composable
private fun MissedSwitch(on: Boolean, missed: Int, onChange: (Boolean) -> Unit) {
    val knob by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "switchKnob")
    val label = stringResource(R.string.prayer_log_galaxy_show_missed)
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) Color(0xFFF87171).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (on) Color(0xFFFFAAAA).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label }
            .padding(start = 5.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp, 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (on) Color(0xFFF87171) else Color.White.copy(alpha = 0.18f))
        ) {
            Box(
                Modifier
                    .padding(start = (3 + 10 * knob).dp, top = 3.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.prayer_log_galaxy_missed, missed),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = SkyInk
        )
    }
}

/**
 * The picked day: its date between arrows that step a day back or on, what it lacks, and its five
 * prayers to mark or unmark.
 */
@Composable
private fun DayPanel(
    day: PrayerLogDay,
    isToday: Boolean,
    canGoBack: Boolean,
    onStep: (Long) -> Unit,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val title = day.date.format(DateTimeFormatter.ofPattern("d MMMM EEEE", locale)) +
        if (isToday) " · ${stringResource(R.string.prayer_log_today)}" else ""
    val missed = day.entries.filter { it.status == PrayerLogStatus.MISSED }.map { it.type.prayerName }
    val active = day.entries.firstOrNull { it.status == PrayerLogStatus.ACTIVE }
    val note = when {
        missed.isNotEmpty() -> stringResource(R.string.prayer_log_day_missed, missed.joinToString(", "))
        day.isComplete -> stringResource(R.string.prayer_log_day_full)
        active != null -> stringResource(R.string.prayer_log_now, active.type.prayerName)
        else -> "${day.prayed}/${day.entries.size}"
    }

    SkyTile(modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.prayer_log_prev_day), canGoBack) { onStep(-1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = SkyInk, textAlign = TextAlign.Center, maxLines = 1)
                Text(
                    note,
                    style = TextStyle(fontSize = 12.sp),
                    color = if (missed.isNotEmpty()) MissedCoral else skyFaint(0.6f),
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
            DayArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.prayer_log_next_day), !isToday) { onStep(1) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            day.entries.forEach { entry ->
                key(day.date, entry.type) {
                    DayChip(entry, Modifier.weight(1f)) { onToggle(day.date, entry) }
                }
            }
        }
    }
}

@Composable
private fun DayArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFE4E2FF))
    }
}

/** A prayer of the picked day: its star in its state, its name and its state in words. */
@Composable
private fun DayChip(entry: PrayerLogEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val status = entry.status
    val name = entry.type.prayerName
    val stateText = stringResource(status.labelRes)
    val (bg, edge, words) = when (status) {
        PrayerLogStatus.PRAYED -> Triple(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.1f), skyFaint(0.55f))
        PrayerLogStatus.ACTIVE -> Triple(Color(0xFF9FA8DA).copy(alpha = 0.2f), Color(0xFFD6DAFF).copy(alpha = 0.45f), NowLilac)
        PrayerLogStatus.MISSED -> Triple(Color(0xFFF87171).copy(alpha = 0.12f), Color(0xFFFFAAAA).copy(alpha = 0.45f), MissedCoral)
        else -> Triple(Color.White.copy(alpha = 0.03f), Color.White.copy(alpha = 0.06f), skyFaint(0.4f))
    }
    Column(
        modifier
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, edge, RoundedCornerShape(16.dp))
            .then(if (status != PrayerLogStatus.UPCOMING) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = "$name, $stateText" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (status) {
            PrayerLogStatus.PRAYED -> StarGlyph(13.dp, glow = entry.type.accentColor)
            PrayerLogStatus.ACTIVE -> StarGlyph(13.dp, fill = null, outline = Color(0xFFDDE1FF))
            PrayerLogStatus.MISSED -> StarGlyph(13.dp, fill = null, outline = MissedCoral)
            else -> StarGlyph(13.dp, fill = null, outline = Color.White.copy(alpha = 0.3f))
        }
        Spacer(Modifier.height(3.dp))
        Text(name, style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold), color = SkyInk, maxLines = 1)
        Text(stateText, style = TextStyle(fontSize = 9.5.sp), color = words, maxLines = 1)
    }
}
