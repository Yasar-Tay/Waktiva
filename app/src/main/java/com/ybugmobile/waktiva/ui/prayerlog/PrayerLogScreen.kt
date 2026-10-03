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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.theme.GlassSurface
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.ui.theme.darken
import com.ybugmobile.waktiva.ui.theme.liquidGlass
import kotlinx.coroutines.delay
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
    val haptics = LocalHapticFeedback.current
    val onToggle = remember(viewModel, haptics) {
        { date: LocalDate, entry: PrayerLogEntry ->
            val prayed = entry.status != PrayerLogStatus.PRAYED
            haptics.performHapticFeedback(if (prayed) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            viewModel.setPrayed(date, entry.type, prayed)
        }
    }

    // What changed since the last look, to cheer for: a day made full, a level, a badge. The first
    // look after the screen opens is only taken in.
    val unlocks = remember { mutableStateListOf<Unlock>() }
    var confetti by remember { mutableIntStateOf(0) }
    var seen by remember { mutableStateOf<PrayerLogViewState?>(null) }
    LaunchedEffect(state) {
        if (state.isLoading) return@LaunchedEffect
        val before = seen
        seen = state
        if (before == null) return@LaunchedEffect
        val progress = state.progress
        val news = buildList {
            if (progress.level > before.progress.level) add(Unlock.Level(progress.level))
            (progress.earned - before.progress.earned).forEach { add(Unlock.Badge(it)) }
        }
        unlocks += news
        val dayFilled = state.today?.isComplete == true && before.today?.isComplete == false &&
            state.today?.date == before.today?.date
        if (news.isNotEmpty() || dayFilled) {
            confetti++
            if (dayFilled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val unlock = unlocks.firstOrNull()
    LaunchedEffect(unlock) {
        if (unlock != null) {
            delay(UnlockToastMillis)
            unlocks.removeAt(0)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // Landscape is short of height: the navigation rail beside it names the screen.
            if (!isLandscape) CenterAlignedTopAppBar(
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
        val historyCard = @Composable { compact: Boolean ->
            HistoryCard(
                state = state,
                onShowMonth = viewModel::showMonth,
                onSelect = viewModel::select,
                onToggle = onToggle,
                compact = compact
            )
        }

        Box(Modifier.fillMaxSize()) {
            if (isLandscape) {
                // Side by side, clear of the navigation rail: today and the game on one side, the
                // calendar on the other, each scrolling on its own.
                // The system bars and the cut-out sit at either side in landscape: the navigation
                // rail is past those at the start, the system buttons may be at the end.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding())
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(start = 76.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp, bottom = bottomInset + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TodayCard(today, state, onToggle, compact = true)
                        BadgesCard(state.progress)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp, bottom = bottomInset + 16.dp)
                    ) {
                        historyCard(true)
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
                    TodayCard(today, state, onToggle, compact = false)
                    BadgesCard(state.progress)
                    historyCard(false)
                }
            }

            ConfettiBurst(confetti, Modifier.fillMaxSize())
            UnlockToast(
                unlock,
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding() + 8.dp)
            )
        }
    }
}

/**
 * Today, with the level: the ring beside the headline and the level line, the five prayers to
 * mark, the full day's bonus, and the last seven days with the streak and the next badge along
 * the bottom. [compact], for landscape's short height, makes it all a little smaller.
 */
@Composable
private fun TodayCard(
    today: PrayerLogDay,
    state: PrayerLogViewState,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit,
    compact: Boolean
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val gap = if (compact) 10.dp else 16.dp

    GlassSurface(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 12.dp else 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayRing(today, Modifier.size(if (compact) 76.dp else 88.dp))
                Spacer(Modifier.width(if (compact) 14.dp else 16.dp))
                Column(Modifier.weight(1f)) {
                    TodayHeadline(today, state)
                    Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                    LevelLine(state.progress, compact = true)
                }
            }

            Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
            PrayerRow(today, chipSize = if (compact) 40.dp else 46.dp, onToggle = onToggle)
            Spacer(Modifier.height(gap))
            FullDayBonus(today.isComplete, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(gap))
            HorizontalDivider(color = contentColor.copy(alpha = 0.1f))
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            WeekStrip(state)
        }
    }
}

/**
 * Today's date over what matters now: the prayer whose time is on, else how the day stands.
 * Until the first prayer is marked, how to mark one.
 */
@Composable
private fun TodayHeadline(today: PrayerLogDay, state: PrayerLogViewState, modifier: Modifier = Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val active = today.entries.firstOrNull { it.status == PrayerLogStatus.ACTIVE }

    Column(modifier) {
        Text(
            text = today.date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale)),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                today.isComplete -> stringResource(R.string.prayer_log_all_done)
                active != null -> stringResource(R.string.prayer_log_now, active.type.prayerName)
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

/**
 * The last seven days as the calendar draws them, today last, and under them the streak of full
 * days (with the best one) and the badge nearest to being earned.
 */
@Composable
private fun WeekStrip(state: PrayerLogViewState) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val today = state.today?.date

    Row(Modifier.fillMaxWidth()) {
        state.lastWeek.forEach { day ->
            val isToday = day.date == today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isToday) FontWeight.Black else FontWeight.Medium
                    ),
                    color = contentColor.copy(alpha = if (isToday) 0.9f else 0.5f),
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    DayCell(date = day.date, day = day, isToday = isToday, isSelected = isToday, onClick = null)
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            tint = if (state.streak > 0) goldInk() else contentColor.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = state.streak.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = IBMPlexArabic, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = contentColor
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.prayer_log_streak),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.6f),
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        state.progress.bestStreak.takeIf { it > 0 }?.let { best ->
            Text(
                text = stringResource(R.string.prayer_log_best_streak, best),
                style = goldText(MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)),
                maxLines = 1
            )
        }
    }

    state.progress.nextBadge?.let { next ->
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(next.badge.icon, contentDescription = null, tint = goldInk(), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.prayer_log_next_goal) + ": " + stringResource(next.badge.nameRes),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${next.current}/${next.target}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = IBMPlexArabic, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = contentColor.copy(alpha = 0.8f)
            )
        }
        Spacer(Modifier.height(6.dp))
        GoalBar(next.fraction, Modifier.fillMaxWidth().height(5.dp))
    }
}

/**
 * The month calendar, each day a small ring of its five prayers, and the picked day's prayers to
 * mark. Arrows or a swipe move between months. In portrait the day's prayers are a row under the
 * month; [compact], for landscape's short height, puts them in a column beside a flatter month
 * when there's the width for it, else under it.
 */
@Composable
private fun HistoryCard(
    state: PrayerLogViewState,
    onShowMonth: (Long) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit,
    compact: Boolean = false
) {
    GlassSurface(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        if (!compact) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                MonthHeader(state, onShowMonth)
                Spacer(Modifier.height(8.dp))
                MonthPager(state, onShowMonth, onSelect, cellAspect = 1f)
                state.selected?.let { SelectedDayRow(it, onToggle) }
            }
            return@GlassSurface
        }
        BoxWithConstraints {
            if (maxWidth >= SideBySideMinWidth) {
                Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        MonthHeader(state, onShowMonth)
                        MonthPager(state, onShowMonth, onSelect, cellAspect = 1.2f)
                    }
                    state.selected?.let { day ->
                        Spacer(Modifier.width(10.dp))
                        SelectedDayColumn(day, onToggle, Modifier.width(SelectedDayWidth))
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    MonthHeader(state, onShowMonth)
                    MonthPager(state, onShowMonth, onSelect, cellAspect = 1.5f)
                    state.selected?.let { SelectedDayRow(it, onToggle) }
                }
            }
        }
    }
}

/** The month shown, between the arrows to the months around it, with the prayers missed in all. */
@Composable
private fun MonthHeader(state: PrayerLogViewState, onShowMonth: (Long) -> Unit) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.prayer_log_prev_month),
            enabled = state.canShowPreviousMonth,
            onClick = { onShowMonth(-1) }
        )
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
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = IBMPlexArabic, fontWeight = FontWeight.Bold),
                        color = LogColors.Rose
                    )
                }
            }
        }
        GlassIconButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(R.string.prayer_log_next_month),
            enabled = state.canShowNextMonth,
            onClick = { onShowMonth(1) }
        )
    }
}

/** The shown month's days; a swipe turns the month, towards the start of the line for the next one. */
@Composable
private fun MonthPager(
    state: PrayerLogViewState,
    onShowMonth: (Long) -> Unit,
    onSelect: (LocalDate) -> Unit,
    cellAspect: Float
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
            onSelect = onSelect,
            cellAspect = cellAspect
        )
    }
}

/** The picked day under the month: its date and count, then its five prayers in a row to mark. */
@Composable
private fun SelectedDayRow(day: PrayerLogDay, onToggle: (LocalDate, PrayerLogEntry) -> Unit) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
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
        DayCount(day)
    }
    Spacer(Modifier.height(14.dp))
    Box(Modifier.padding(horizontal = 8.dp)) {
        PrayerRow(day, chipSize = 40.dp, onToggle = onToggle)
    }
    Spacer(Modifier.height(6.dp))
}

/**
 * The picked day beside the month, for landscape: its date and count over its five prayers in a
 * column, each a row to tap with its mark and its name.
 */
@Composable
private fun SelectedDayColumn(day: PrayerLogDay, onToggle: (LocalDate, PrayerLogEntry) -> Unit, modifier: Modifier = Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = day.date.format(DateTimeFormatter.ofPattern("d MMMM", locale)),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                    maxLines = 1
                )
                Text(
                    text = day.date.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.55f),
                    maxLines = 1
                )
            }
            DayCount(day)
        }
        Spacer(Modifier.height(8.dp))
        day.entries.forEach { entry ->
            key(day.date, entry.type) {
                val onClick = { onToggle(day.date, entry) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (entry.status != PrayerLogStatus.UPCOMING) Modifier.clickable(onClick = onClick) else Modifier)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrayerChip(entry = entry, size = 32.dp, onClick = onClick, showName = false)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = entry.type.prayerName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = when (entry.status) {
                            PrayerLogStatus.PRAYED -> goldInk()
                            PrayerLogStatus.UPCOMING -> contentColor.copy(alpha = 0.45f)
                            else -> contentColor.copy(alpha = 0.85f)
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** How many of [day]'s prayers are prayed, in gold once all are. */
@Composable
private fun DayCount(day: PrayerLogDay) {
    Text(
        text = "${day.prayed}/${day.entries.size}",
        style = MaterialTheme.typography.titleSmall.copy(
            fontFamily = IBMPlexArabic,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum"
        ),
        color = if (day.isComplete) goldInk() else LocalGlassTheme.current.contentColor.copy(alpha = 0.7f)
    )
}

/**
 * A month of days in weeks, starting on the locale's first day of the week. Each day's cell is
 * [cellAspect] times as wide as it's tall; its ring stays round.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate?,
    days: Map<LocalDate, PrayerLogDay>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    cellAspect: Float = 1f
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
                            .aspectRatio(cellAspect),
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
    onClick: (() -> Unit)?
) {
    val glass = LocalGlassTheme.current
    val contentColor = glass.contentColor
    val isComplete = day?.isComplete == true
    val colors = day?.entries?.map { statusColor(it.status) }
    val description = day?.let { "${date.dayOfMonth}: ${it.prayed}/${it.entries.size}" } ?: date.dayOfMonth.toString()

    Box(
        modifier = Modifier
            .padding(3.dp)
            .fillMaxHeight()
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .then(if (isSelected) Modifier.liquidGlass(CircleShape, glass, emphasis = 0.6f) else Modifier)
            .clip(CircleShape)
            .then(if (day != null && onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (colors != null) {
            Canvas(Modifier.fillMaxSize()) {
                if (isComplete) {
                    brassOrb(center, size.minDimension / 2f - 3.dp.toPx(), shadow = false)
                } else {
                    drawStatusRing(colors, stroke = 2.4.dp.toPx(), inset = 3.5.dp.toPx(), gap = 16f)
                }
            }
        }
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = IBMPlexArabic,
                fontWeight = if (isToday || isComplete) FontWeight.Bold else FontWeight.Medium,
                fontFeatureSettings = "tnum"
            ),
            color = when {
                isComplete -> LogColors.Ink
                day == null -> contentColor.copy(alpha = 0.25f)
                isToday -> goldInk()
                !day.isTracked && day.prayed == 0 -> contentColor.copy(alpha = 0.45f)
                else -> contentColor.copy(alpha = 0.9f)
            }
        )
    }
}

/**
 * Today's ring: a groove with an arc of gold for each prayer prayed, rose for each missed, the
 * prayer's own colour while its time is on, and the count inside. A full day glows.
 */
@Composable
private fun DayRing(day: PrayerLogDay, modifier: Modifier = Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val colors = day.entries.map { entry ->
        animateColorAsState(statusColor(entry.status, entry.type.accentColor) ?: Color.Transparent, label = "ringArc").value
    }
    val glow by animateFloatAsState(if (day.isComplete) 1f else 0f, label = "ringGlow")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            glow(center, size.minDimension / 2f, LogColors.Gold, 0.4f * glow)
            drawStatusRing(colors, stroke = 7.dp.toPx(), inset = 8.dp.toPx(), gap = 9f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${day.prayed}/${day.entries.size}",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = IBMPlexArabic,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = if (day.isComplete) goldInk() else contentColor
            )
            Text(
                text = stringResource(R.string.prayer_log_today).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
                color = contentColor.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * A ring of equal segments, clockwise from the top, [inset] from the edge, lying in a groove:
 * [colors] for each segment, none where it's transparent or null.
 */
private fun DrawScope.drawStatusRing(colors: List<Color?>, stroke: Float, inset: Float, gap: Float) {
    ringGroove(stroke, inset)
    val sweep = 360f / colors.size
    colors.forEachIndexed { i, color ->
        if (color == null || color.alpha == 0f) return@forEachIndexed
        glazedArc(color, -90f + i * sweep + gap / 2f, sweep - gap, stroke, inset)
    }
}

/**
 * A prayer's colour in the rings: gold once prayed, rose once missed, [active] (its own colour)
 * while its time is on, none otherwise.
 */
private fun statusColor(status: PrayerLogStatus, active: Color = LogColors.Gold): Color? = when (status) {
    PrayerLogStatus.PRAYED -> LogColors.Gold
    PrayerLogStatus.MISSED -> LogColors.Rose.copy(alpha = 0.8f)
    PrayerLogStatus.ACTIVE -> active.copy(alpha = 0.85f)
    else -> null
}

/** A day's five prayers as marks to tap, with their names. */
@Composable
private fun PrayerRow(day: PrayerLogDay, chipSize: Dp, onToggle: (LocalDate, PrayerLogEntry) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        day.entries.forEach { entry ->
            // Keyed by day, so picking another day starts its chips afresh rather than seeing a
            // change of mark in each.
            key(day.date, entry.type) {
                PrayerChip(entry = entry, size = chipSize, onClick = { onToggle(day.date, entry) })
            }
        }
    }
}

/**
 * A prayer as a glass sphere, as the home dial draws its prayers: deep glass ringed in its own
 * colour with its sign while its time is on (gently pulsing: the one to mark next), brass with a
 * tick once prayed, dimmed with a rose rim and a cross once missed, faint before its time.
 * Tapping marks or unmarks it, except before its time.
 */
@Composable
private fun PrayerChip(entry: PrayerLogEntry, size: Dp, onClick: () -> Unit, showName: Boolean = true) {
    val contentColor = LocalGlassTheme.current.contentColor
    val accent = entry.type.accentColor
    val status = entry.status
    val isPrayed = status == PrayerLogStatus.PRAYED
    val name = entry.type.prayerName
    val statusText = stringResource(status.labelRes)

    val rim by animateColorAsState(
        when (status) {
            PrayerLogStatus.PRAYED -> LogColors.Brass
            PrayerLogStatus.MISSED -> LogColors.Rose.darken(0.15f)
            PrayerLogStatus.ACTIVE -> accent
            else -> Color.White.copy(alpha = 0.28f)
        },
        label = "chipRim"
    )
    val fill by animateColorAsState(
        when (status) {
            PrayerLogStatus.PRAYED -> LogColors.Gold
            PrayerLogStatus.MISSED -> lerp(LogColors.Night, LogColors.Rose, 0.2f)
            PrayerLogStatus.ACTIVE -> lerp(LogColors.Night, accent, 0.35f)
            else -> LogColors.Night.copy(alpha = 0.45f)
        },
        label = "chipFill"
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
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
            label = "chipPulse"
        )
    } else {
        null
    }
    // Each time it's marked, its XP rises from it.
    var marks by remember { mutableIntStateOf(0) }
    var lastStatus by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (isPrayed && lastStatus != PrayerLogStatus.PRAYED) marks++
        lastStatus = status
    }
    val faint = status == PrayerLogStatus.UPCOMING || status == PrayerLogStatus.UNTRACKED

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(if (faint) 1f else pop)
                    .drawBehind {
                        val r = this.size.minDimension / 2f
                        glow(center, r * 1.45f, LogColors.Gold, 0.4f * glow)
                        // A ring widening out and fading, over and over: this one's time is on.
                        pulse?.value?.let { t ->
                            drawCircle(
                                color = accent.copy(alpha = 0.55f * (1f - t)),
                                radius = r * (1f + 0.4f * t),
                                style = Stroke(1.2.dp.toPx())
                            )
                        }
                        glassOrb(
                            center,
                            r,
                            rim = rim,
                            fill = fill,
                            edge = if (faint) null else LogColors.Gold.copy(alpha = 0.55f),
                            shadow = !faint
                        )
                    }
                    .clip(CircleShape)
                    .semantics { contentDescription = "$name, $statusText" }
                    .then(
                        if (status != PrayerLogStatus.UPCOMING) Modifier.clickable(onClick = onClick) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                val iconSize = size * 0.48f
                Crossfade(targetState = status, label = "chipIcon") { shown ->
                    when (shown) {
                        PrayerLogStatus.PRAYED -> Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = LogColors.Ink,
                            modifier = Modifier.size(iconSize)
                        )
                        PrayerLogStatus.MISSED -> Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = LogColors.Rose.lighten(0.25f),
                            modifier = Modifier.size(iconSize * 0.85f)
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
                            tint = if (shown == PrayerLogStatus.ACTIVE) Color.White.copy(alpha = 0.96f) else contentColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }
            XpPop(marks, Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true))
        }
        if (showName) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = when (status) {
                    PrayerLogStatus.PRAYED -> goldInk()
                    PrayerLogStatus.UPCOMING -> contentColor.copy(alpha = 0.45f)
                    else -> contentColor.copy(alpha = 0.8f)
                },
                maxLines = 1
            )
        }
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
internal val PrayedInk = LogColors.Ink

internal val CardShape = RoundedCornerShape(28.dp)

/** The history card's least width for the picked day beside the month, and that day's column's width. */
private val SideBySideMinWidth = 400.dp
private val SelectedDayWidth = 136.dp
