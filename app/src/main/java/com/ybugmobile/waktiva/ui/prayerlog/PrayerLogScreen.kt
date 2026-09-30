package com.ybugmobile.waktiva.ui.prayerlog

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.ui.home.composables.InTimeGreen
import com.ybugmobile.waktiva.ui.home.composables.MissedRed
import com.ybugmobile.waktiva.ui.home.composables.PrayedGold
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.theme.GlassSurface
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.abs

/**
 * The prayer log (çetele), in two cards. Today: the five prayers around a ring, to mark with a
 * tap, and under them the streak and the last week's and month's share of prayers prayed. History:
 * a month calendar where each day is a small ring of its five prayers; picking a day opens its
 * prayers under the calendar to mark or unmark. It opens on yesterday, the day most often left
 * to fill in, and swipes between months.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerLogScreen(viewModel: PrayerLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contentColor = LocalGlassTheme.current.contentColor
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val onToggle = remember(viewModel) {
        { date: LocalDate, entry: PrayerLogEntry ->
            viewModel.setPrayed(date, entry.type, entry.status != PrayerLogStatus.PRAYED)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.prayer_log_title).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        val today = state.today
        if (state.isLoading || today == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = contentColor)
            }
            return@Scaffold
        }

        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val todayCard = @Composable { TodayCard(today, state, onToggle) }
        val historyCard = @Composable {
            HistoryCard(
                state = state,
                onShowMonth = viewModel::showMonth,
                onSelect = viewModel::select,
                onToggle = onToggle
            )
        }

        if (isLandscape) {
            // Side by side, clear of the navigation rail.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .displayCutoutPadding()
                    .padding(start = 92.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(todayCard, historyCard).forEach { card ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp, bottom = bottomInset + 24.dp)
                    ) { card() }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomInset + 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                todayCard()
                historyCard()
            }
        }
    }
}

/**
 * Today: the ring, a line saying what's next, the five prayers to mark, and the streak and
 * week's and month's share of prayers prayed along the bottom.
 */
@Composable
private fun TodayCard(
    today: PrayerLogDay,
    state: PrayerLogViewState,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val active = today.entries.firstOrNull { it.status == PrayerLogStatus.ACTIVE }

    GlassSurface(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayRing(today, Modifier.size(92.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = today.date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale)),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))
                    // What matters now: the prayer whose time is on, else how the day stands. Until
                    // the first prayer is marked, how to mark one.
                    Text(
                        text = when {
                            today.isComplete -> stringResource(R.string.prayer_log_all_done)
                            active != null -> stringResource(R.string.prayer_log_now, active.type.displayName)
                            state.startDate == null -> stringResource(R.string.prayer_log_hint)
                            else -> stringResource(R.string.prayer_log_today_count, today.prayed, today.entries.size)
                        },
                        style = if (state.startDate == null && active == null && !today.isComplete) {
                            MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp)
                        } else {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        },
                        color = contentColor
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PrayerRow(today, chipSize = 46.dp, onToggle = onToggle)
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = contentColor.copy(alpha = 0.1f))
            Spacer(Modifier.height(14.dp))
            Stats(state)
        }
    }
}

/** The streak and the last week's and month's share of prayers prayed, as one row of figures. */
@Composable
private fun Stats(state: PrayerLogViewState) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Stat(
            value = state.streak.toString(),
            label = stringResource(R.string.prayer_log_streak),
            icon = Icons.Rounded.LocalFireDepartment,
            iconTint = if (state.streak > 0) PrayedGold else contentColor.copy(alpha = 0.3f),
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        Stat(
            value = state.last7Days.rate?.let { percent.format(it) } ?: "—",
            label = stringResource(R.string.prayer_log_week),
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        Stat(
            value = state.last30Days.rate?.let { percent.format(it) } ?: "—",
            label = stringResource(R.string.prayer_log_month),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified
) {
    val contentColor = LocalGlassTheme.current.contentColor
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum"
                ),
                color = contentColor,
                maxLines = 1
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .width(1.dp)
            .fillMaxHeight()
            .background(LocalGlassTheme.current.contentColor.copy(alpha = 0.1f))
    )
}

/**
 * The month calendar, each day a small ring of its five prayers, and under it the picked day's
 * prayers to mark. Arrows or a swipe move between months.
 */
@Composable
private fun HistoryCard(
    state: PrayerLogViewState,
    onShowMonth: (Long) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    GlassSurface(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShowMonth(-1) }, enabled = state.canShowPreviousMonth) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.prayer_log_prev_month),
                        tint = contentColor.copy(alpha = if (state.canShowPreviousMonth) 0.8f else 0.2f)
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.shownMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                            .replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor
                    )
                    if (state.missedSinceStart > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.prayer_log_missed_total),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.55f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = NumberFormat.getIntegerInstance(locale).format(state.missedSinceStart),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = MissedRed
                            )
                        }
                    }
                }
                IconButton(onClick = { onShowMonth(1) }, enabled = state.canShowNextMonth) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.prayer_log_next_month),
                        tint = contentColor.copy(alpha = if (state.canShowNextMonth) 0.8f else 0.2f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // A swipe turns the month: towards the start of the line for the next one.
            var drag by remember { mutableFloatStateOf(0f) }
            AnimatedContent(
                targetState = state.shownMonth,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "calendarMonth",
                modifier = Modifier.pointerInput(state.canShowPreviousMonth, state.canShowNextMonth, isRtl) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            val forward = if (isRtl) drag > 0f else drag < 0f
                            if (abs(drag) > 60.dp.toPx()) {
                                if (forward && state.canShowNextMonth) onShowMonth(1)
                                if (!forward && state.canShowPreviousMonth) onShowMonth(-1)
                            }
                        }
                    ) { _, amount -> drag += amount }
                }
            ) { month ->
                MonthGrid(
                    month = month,
                    today = state.today?.date,
                    days = state.calendar,
                    selected = state.selected?.date,
                    onSelect = onSelect
                )
            }

            state.selected?.let { day ->
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(
                    color = contentColor.copy(alpha = 0.1f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = day.date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale)),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${day.prayed}/${day.entries.size}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (day.isComplete) PrayedGold else contentColor.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.padding(horizontal = 8.dp)) {
                    PrayerRow(day, chipSize = 40.dp, onToggle = onToggle)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** A month of days in weeks, starting on the locale's first day of the week. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate?,
    days: Map<LocalDate, PrayerLogDay>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekdays = remember(firstDay) { (0L until 7L).map { firstDay.plus(it) } }
    val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = contentColor.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val date = week.getOrNull(i)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                day = days[date],
                                isToday = date == today,
                                isSelected = date == selected,
                                onClick = { onSelect(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A day on the calendar: its number inside a small ring of its five prayers, filled in gold once
 * all five are prayed. Days still to come are only a faint number.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    day: PrayerLogDay?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val isComplete = day?.isComplete == true
    val colors = day?.entries?.map { statusColor(it.status, PrayedGold, contentColor) }
    val description = day?.let { "${date.dayOfMonth}: ${it.prayed}/${it.entries.size}" } ?: date.dayOfMonth.toString()

    Box(
        modifier = Modifier
            .padding(3.dp)
            .fillMaxSize()
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(contentColor.copy(alpha = 0.16f)) else Modifier)
            .then(if (day != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (colors != null) {
            Canvas(Modifier.fillMaxSize()) {
                if (isComplete) {
                    drawCircle(PrayedGold, radius = size.minDimension / 2f - 3.dp.toPx())
                } else {
                    drawStatusRing(colors, stroke = 2.5.dp.toPx(), inset = 3.dp.toPx(), gap = 14f)
                }
            }
        }
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isToday || isComplete) FontWeight.Black else FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = when {
                isComplete -> PrayedInk
                day == null -> contentColor.copy(alpha = 0.25f)
                isToday -> PrayedGold
                !day.isTracked && day.prayed == 0 -> contentColor.copy(alpha = 0.45f)
                else -> contentColor
            }
        )
    }
}

/** Today's ring: a segment per prayer in its own colour once prayed, with the count inside. */
@Composable
private fun DayRing(day: PrayerLogDay, modifier: Modifier = Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val colors = day.entries.map { entry ->
        val target = statusColor(entry.status, lerp(entry.type.accentColor, PrayedGold, 0.35f), contentColor)
        animateColorAsState(target, label = "ringArc").value
    }
    val glow by animateFloatAsState(if (day.isComplete) 1f else 0f, label = "ringGlow")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (glow > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(PrayedGold.copy(alpha = 0.45f * glow), Color.Transparent),
                        center = center,
                        radius = size.minDimension / 2f
                    )
                )
            }
            drawStatusRing(colors, stroke = 8.dp.toPx(), inset = 8.dp.toPx(), gap = 8f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${day.prayed}/${day.entries.size}",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum"
                ),
                color = contentColor
            )
            Text(
                text = stringResource(R.string.prayer_log_today).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                color = contentColor.copy(alpha = 0.5f)
            )
        }
    }
}

/** A ring of equal segments in [colors], clockwise from the top, [inset] from the edge. */
private fun DrawScope.drawStatusRing(colors: List<Color>, stroke: Float, inset: Float, gap: Float) {
    val sweep = 360f / colors.size
    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
    colors.forEachIndexed { i, color ->
        drawArc(
            color = color,
            startAngle = -90f + i * sweep + gap / 2f,
            sweepAngle = sweep - gap,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

/** A prayer's colour in the rings: [prayed] once prayed, red once missed, faint otherwise. */
private fun statusColor(status: PrayerLogStatus, prayed: Color, contentColor: Color): Color = when (status) {
    PrayerLogStatus.PRAYED -> prayed
    PrayerLogStatus.MISSED -> MissedRed.copy(alpha = 0.75f)
    PrayerLogStatus.ACTIVE -> InTimeGreen.copy(alpha = 0.55f)
    else -> contentColor.copy(alpha = 0.14f)
}

/** A day's five prayers as marks to tap, with their names. */
@Composable
private fun PrayerRow(day: PrayerLogDay, chipSize: Dp, onToggle: (LocalDate, PrayerLogEntry) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        day.entries.forEach { entry ->
            PrayerChip(entry = entry, size = chipSize, onClick = { onToggle(day.date, entry) })
        }
    }
}

/**
 * A prayer as a round mark: filled in its colour and ringed in gold with a tick once prayed, red
 * with a cross once missed, ringed in its colour and gently pulsing while its time is on (the one
 * to mark next), faint before its time. Tapping marks or unmarks it, except before its time.
 */
@Composable
private fun PrayerChip(entry: PrayerLogEntry, size: Dp, onClick: () -> Unit) {
    val contentColor = LocalGlassTheme.current.contentColor
    val accent = entry.type.accentColor
    val status = entry.status
    val isPrayed = status == PrayerLogStatus.PRAYED
    val name = entry.type.displayName
    val statusText = stringResource(status.labelRes)

    val fill by animateColorAsState(
        when (status) {
            PrayerLogStatus.PRAYED -> accent
            PrayerLogStatus.MISSED -> MissedRed.copy(alpha = 0.14f)
            PrayerLogStatus.ACTIVE -> accent.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        label = "chipFill"
    )
    val ring by animateColorAsState(
        when (status) {
            PrayerLogStatus.PRAYED -> PrayedGold
            PrayerLogStatus.MISSED -> MissedRed.copy(alpha = 0.8f)
            PrayerLogStatus.ACTIVE -> accent
            PrayerLogStatus.UPCOMING -> contentColor.copy(alpha = 0.18f)
            PrayerLogStatus.UNTRACKED -> contentColor.copy(alpha = 0.12f)
        },
        label = "chipRing"
    )
    val glow by animateFloatAsState(if (isPrayed) 1f else 0f, label = "chipGlow")
    val pop by animateFloatAsState(
        if (isPrayed) 1f else 0.94f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "chipPop"
    )
    val pulse = if (status == PrayerLogStatus.ACTIVE) {
        rememberInfiniteTransition(label = "chipPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
            label = "chipPulse"
        )
    } else {
        null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(if (status == PrayerLogStatus.UPCOMING || status == PrayerLogStatus.UNTRACKED) 1f else pop)
                .drawBehind {
                    if (glow > 0f) {
                        val reach = this.size.minDimension * 0.85f
                        drawCircle(
                            Brush.radialGradient(
                                0.55f to PrayedGold.copy(alpha = 0.5f * glow),
                                1f to Color.Transparent,
                                center = center,
                                radius = reach
                            ),
                            radius = reach
                        )
                    }
                    // A ring widening out and fading, over and over: this one's time is on.
                    pulse?.value?.let { t ->
                        drawCircle(
                            color = accent.copy(alpha = 0.6f * (1f - t)),
                            radius = this.size.minDimension / 2f * (1f + 0.35f * t),
                            style = Stroke(1.5.dp.toPx())
                        )
                    }
                }
                .clip(CircleShape)
                .background(fill)
                .border(1.5.dp, ring, CircleShape)
                .semantics { contentDescription = "$name, $statusText" }
                .then(
                    if (status != PrayerLogStatus.UPCOMING) Modifier.clickable(onClick = onClick) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            val iconSize = size * 0.5f
            Crossfade(targetState = status, label = "chipIcon") { shown ->
                when (shown) {
                    PrayerLogStatus.PRAYED -> Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (accent.luminance() > 0.5f) Color.Black.copy(alpha = 0.75f) else Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                    PrayerLogStatus.MISSED -> Icon(
                        Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MissedRed,
                        modifier = Modifier.size(iconSize * 0.9f)
                    )
                    PrayerLogStatus.UNTRACKED -> Icon(
                        Icons.Rounded.Remove,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(iconSize * 0.8f)
                    )
                    else -> Icon(
                        ImageVector.vectorResource(entry.type.iconRes),
                        contentDescription = null,
                        tint = if (shown == PrayerLogStatus.ACTIVE) accent else contentColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = contentColor.copy(alpha = if (status == PrayerLogStatus.UPCOMING) 0.45f else 0.8f),
            maxLines = 1
        )
    }
}

private val PrayerLogStatus.labelRes: Int
    get() = when (this) {
        PrayerLogStatus.PRAYED -> R.string.prayer_log_status_prayed
        PrayerLogStatus.MISSED -> R.string.prayer_log_status_missed
        PrayerLogStatus.ACTIVE -> R.string.prayer_log_status_active
        PrayerLogStatus.UPCOMING -> R.string.prayer_log_status_upcoming
        PrayerLogStatus.UNTRACKED -> R.string.prayer_log_status_untracked
    }

/** Ink on a day filled in gold. */
private val PrayedInk = Color(0xFF3B2A00)

private val CardShape = RoundedCornerShape(28.dp)
