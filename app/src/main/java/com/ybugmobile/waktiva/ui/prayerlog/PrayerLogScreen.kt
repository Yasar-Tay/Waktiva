package com.ybugmobile.waktiva.ui.prayerlog

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.ui.home.composables.PrayedGold
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.theme.GlassSurface
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.abs

/**
 * The prayer log (çetele) as a character sheet, over the app's sky. At the top the player: the
 * level on a crest, its title and the XP bar, and under it the streak and the last week's and
 * month's share of prayers prayed. Then the day's five prayers as the day's quests, each worth
 * its XP, with the full day's chest under them; the badges; and the journey log, a month calendar
 * where each day is a tile, in gold once full; picking a day opens its prayers under the calendar
 * to mark or unmark. It opens on yesterday, the day most often left to fill in, and swipes
 * between months.
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
                title = { SheetTitle(stringResource(R.string.prayer_log_title)) },
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
                // Side by side, clear of the navigation rail: the player and the day on one side,
                // the calendar on the other, each scrolling on its own.
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
                        CharacterCard(state.progress, compact = true)
                        QuestsCard(today, state, onToggle, compact = true)
                        Attributes(state)
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
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + 100.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CharacterCard(state.progress)
                    Attributes(state)
                    QuestsCard(today, state, onToggle)
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

/** The screen's name in spaced capitals between gold rules and diamonds. */
@Composable
private fun SheetTitle(title: String) {
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(36.dp).height(1.dp).background(PrayedGold.copy(alpha = 0.5f)))
        Spacer(Modifier.width(10.dp))
        Diamond(6.dp, PrayedGold)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title.uppercase(locale),
            style = eyebrowStyle(16.sp).copy(letterSpacing = 4.sp),
            color = Color.White
        )
        Spacer(Modifier.width(12.dp))
        Diamond(6.dp, PrayedGold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(36.dp).height(1.dp).background(PrayedGold.copy(alpha = 0.5f)))
    }
}

/**
 * The player's figures as three plaques: the streak with the best one under it, and the last
 * week's and month's share of prayers prayed.
 */
@Composable
private fun Attributes(state: PrayerLogViewState) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Plaque(
            value = state.streak.toString(),
            label = stringResource(R.string.prayer_log_streak),
            icon = Icons.Rounded.LocalFireDepartment,
            iconTint = if (state.streak > 0) Ember else contentColor.copy(alpha = 0.3f),
            note = state.progress.bestStreak.takeIf { it > 0 }
                ?.let { stringResource(R.string.prayer_log_best_streak, it) },
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        Plaque(
            value = state.last7Days.rate?.let { percent.format(it) } ?: "—",
            label = stringResource(R.string.prayer_log_week),
            icon = Icons.Rounded.BarChart,
            iconTint = PrayedGold,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        Plaque(
            value = state.last30Days.rate?.let { percent.format(it) } ?: "—",
            label = stringResource(R.string.prayer_log_month),
            icon = Icons.Rounded.CalendarMonth,
            iconTint = PrayedGold,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun Plaque(
    value: String,
    label: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    note: String? = null
) {
    val contentColor = LocalGlassTheme.current.contentColor
    GlassSurface(shape = CardShape, modifier = modifier) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Text(text = value, style = figureStyle(24.sp), color = contentColor, maxLines = 1)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.onSky(),
                color = faded(0.6f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold).onSky(),
                    color = Ember,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The day's quests: today's date over its five prayers, each a row to mark with its XP, and the
 * full day's chest under them. Until the first prayer is marked, how to mark one.
 */
@Composable
private fun QuestsCard(
    today: PrayerLogDay,
    state: PrayerLogViewState,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit,
    compact: Boolean = false
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]

    GlassSurface(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 12.dp else 16.dp)) {
            SectionHeader(
                icon = Icons.Rounded.TaskAlt,
                title = stringResource(R.string.prayer_log_quests),
                count = "${today.prayed}/${today.entries.size}",
                countColor = PrayedGold
            )
            Text(
                text = today.date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale)),
                style = MaterialTheme.typography.labelMedium.onSky(),
                color = faded(0.6f),
                modifier = Modifier.padding(start = 28.dp, top = 2.dp)
            )
            if (state.startDate == null) {
                Text(
                    text = stringResource(R.string.prayer_log_hint),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp).onSky(),
                    color = contentColor.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 28.dp, top = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                today.entries.forEach { entry ->
                    // Keyed by day, so a new day starts its rows afresh rather than seeing a
                    // change of mark in each.
                    key(today.date, entry.type) {
                        QuestRow(today.date, entry, onToggle, compact)
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            FullDayChest(today.entries.map { it.status == PrayerLogStatus.PRAYED })
        }
    }
}

/**
 * A prayer as a quest: its emblem, its name over where it stands (until when it's open, when it
 * opens, that its time has gone), the XP it's worth and a seal, gold and ticked once prayed.
 * Tapping the row marks or unmarks it, except before its time.
 */
@Composable
private fun QuestRow(
    date: LocalDate,
    entry: PrayerLogEntry,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit,
    compact: Boolean
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val status = entry.status
    val shape = RoundedCornerShape(8.dp)
    val background by animateColorAsState(
        when (status) {
            PrayerLogStatus.PRAYED -> PrayedGold.copy(alpha = 0.12f)
            PrayerLogStatus.ACTIVE -> QuestGreenDeep.copy(alpha = 0.5f)
            PrayerLogStatus.MISSED -> QuestRedDeep.copy(alpha = 0.5f)
            else -> Color.Black.copy(alpha = 0.06f)
        },
        label = "questFill"
    )
    val edge by animateColorAsState(
        when (status) {
            PrayerLogStatus.PRAYED -> PrayedGold.copy(alpha = 0.5f)
            PrayerLogStatus.ACTIVE -> QuestGreen.copy(alpha = 0.75f)
            PrayerLogStatus.MISSED -> QuestRed.copy(alpha = 0.6f)
            else -> contentColor.copy(alpha = 0.1f)
        },
        label = "questEdge"
    )
    val statusText = when (status) {
        PrayerLogStatus.PRAYED -> stringResource(R.string.prayer_log_status_prayed)
        PrayerLogStatus.ACTIVE -> entry.end?.let { stringResource(R.string.prayer_log_quest_until, it.format(ClockFormat)) }
            ?: stringResource(R.string.prayer_log_status_active)
        PrayerLogStatus.MISSED -> stringResource(R.string.prayer_log_quest_missed)
        PrayerLogStatus.UPCOMING -> entry.time?.let { stringResource(R.string.prayer_log_quest_opens, it.format(ClockFormat)) }
            ?: stringResource(R.string.prayer_log_status_upcoming)
        PrayerLogStatus.UNTRACKED -> stringResource(R.string.prayer_log_status_untracked)
    }
    val isUpcoming = status == PrayerLogStatus.UPCOMING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 52.dp else 62.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, edge, shape)
            .toggleable(
                value = status == PrayerLogStatus.PRAYED,
                enabled = !isUpcoming,
                role = Role.Checkbox,
                onValueChange = { onToggle(date, entry) }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrayerEmblem(entry, size = if (compact) 36.dp else 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.type.prayerName,
                style = headingStyle(if (compact) 15.sp else 17.sp),
                color = if (isUpcoming) faded(0.5f) else contentColor,
                maxLines = 1
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium.onSky(),
                color = when (status) {
                    PrayerLogStatus.PRAYED -> PrayedGold
                    PrayerLogStatus.ACTIVE -> QuestGreen
                    PrayerLogStatus.MISSED -> QuestRedText
                    else -> faded(0.55f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.prayer_log_xp_gain, PrayerLogProgress.XP_PER_PRAYER),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black).onSky(),
            color = when (status) {
                PrayerLogStatus.PRAYED -> PrayedGold
                PrayerLogStatus.UPCOMING, PrayerLogStatus.UNTRACKED -> faded(0.3f)
                else -> contentColor.copy(alpha = 0.8f)
            }
        )
        Spacer(Modifier.width(10.dp))
        QuestSeal(status)
    }
}

/** A quest's seal: gold and ticked once prayed, ringed green or red while open or gone, locked before its time. */
@Composable
private fun QuestSeal(status: PrayerLogStatus) {
    val contentColor = LocalGlassTheme.current.contentColor
    val fill by animateColorAsState(if (status == PrayerLogStatus.PRAYED) PrayedGold else Color.Transparent, label = "sealFill")
    val ring = when (status) {
        PrayerLogStatus.PRAYED -> GoldLight
        PrayerLogStatus.ACTIVE -> QuestGreen
        PrayerLogStatus.MISSED -> QuestRed
        else -> contentColor.copy(alpha = 0.2f)
    }
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            PrayerLogStatus.PRAYED -> Icon(Icons.Rounded.Check, contentDescription = null, tint = PrayedInk, modifier = Modifier.size(17.dp))
            PrayerLogStatus.UPCOMING -> Icon(Icons.Rounded.Lock, contentDescription = null, tint = faded(0.4f), modifier = Modifier.size(13.dp))
            PrayerLogStatus.UNTRACKED -> Icon(Icons.Rounded.Remove, contentDescription = null, tint = contentColor.copy(alpha = 0.35f), modifier = Modifier.size(14.dp))
            else -> Unit
        }
    }
}

/**
 * A prayer's emblem: its icon on a diamond, the diamond gold once prayed, red once missed, in the
 * prayer's own colour with a ring widening from it while its time is on, faint before its time.
 * Each time it's marked, its XP rises from it.
 */
@Composable
private fun PrayerEmblem(entry: PrayerLogEntry, size: Dp, modifier: Modifier = Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val accent = entry.type.accentColor
    val status = entry.status
    val isPrayed = status == PrayerLogStatus.PRAYED
    val fill by animateColorAsState(
        when (status) {
            PrayerLogStatus.PRAYED -> PrayedGold
            PrayerLogStatus.MISSED -> QuestRed.copy(alpha = 0.16f)
            PrayerLogStatus.ACTIVE -> accent.copy(alpha = 0.2f)
            else -> Color.Black.copy(alpha = 0.12f)
        },
        label = "emblemFill"
    )
    val edge by animateColorAsState(
        when (status) {
            PrayerLogStatus.PRAYED -> GoldLight
            PrayerLogStatus.MISSED -> QuestRed.copy(alpha = 0.85f)
            PrayerLogStatus.ACTIVE -> accent
            PrayerLogStatus.UPCOMING -> contentColor.copy(alpha = 0.22f)
            PrayerLogStatus.UNTRACKED -> contentColor.copy(alpha = 0.14f)
        },
        label = "emblemEdge"
    )
    val glow by animateFloatAsState(if (isPrayed) 1f else 0f, label = "emblemGlow")
    val pop by animateFloatAsState(
        if (isPrayed) 1f else 0.94f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "emblemPop"
    )
    val pulse = if (status == PrayerLogStatus.ACTIVE) {
        rememberInfiniteTransition(label = "emblemPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
            label = "emblemPulse"
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
    val diamond = size * 0.7f
    val corner = RoundedCornerShape(size * 0.1f)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(diamond)
                .scale(if (status == PrayerLogStatus.UPCOMING || status == PrayerLogStatus.UNTRACKED) 1f else pop)
                .rotate(45f)
                .drawBehind {
                    if (glow > 0f) {
                        drawCircle(
                            Brush.radialGradient(
                                0.5f to PrayedGold.copy(alpha = 0.5f * glow),
                                1f to Color.Transparent,
                                center = center,
                                radius = this.size.minDimension
                            ),
                            radius = this.size.minDimension
                        )
                    }
                    // A diamond widening out and fading, over and over: this one's time is on.
                    pulse?.value?.let { t ->
                        val grow = 1f + 0.45f * t
                        val side = this.size.width * grow
                        drawRect(
                            color = accent.copy(alpha = 0.6f * (1f - t)),
                            topLeft = center - Offset(side / 2f, side / 2f),
                            size = Size(side, side),
                            style = Stroke(1.5.dp.toPx())
                        )
                    }
                }
                .clip(corner)
                .background(fill)
                .border(1.5.dp, edge, corner)
        )
        Icon(
            ImageVector.vectorResource(entry.type.iconRes),
            contentDescription = null,
            tint = when (status) {
                PrayerLogStatus.PRAYED -> PrayedInk
                PrayerLogStatus.MISSED -> QuestRed
                PrayerLogStatus.ACTIVE -> accent
                else -> faded(0.35f)
            },
            modifier = Modifier.size(size * 0.45f)
        )
        XpPop(marks, Modifier.align(Alignment.TopCenter).wrapContentSize(Alignment.TopCenter, unbounded = true))
    }
}

/**
 * The journey log: the month calendar, each day a tile, and the picked day's prayers to mark.
 * Arrows or a swipe move between months. In portrait the day's prayers are a row under the
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
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
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

/**
 * The journey log's name over the month shown, between the arrows to the months around it, with
 * the prayers missed in all.
 */
@Composable
private fun MonthHeader(state: PrayerLogViewState, onShowMonth: (Long) -> Unit) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
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
                text = stringResource(R.string.prayer_log_journey).uppercase(locale),
                style = eyebrowStyle(10.sp),
                color = PrayedGold
            )
            Text(
                text = state.shownMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                    .replaceFirstChar { it.titlecase(locale) },
                style = headingStyle(18.sp),
                color = contentColor
            )
            if (state.missedSinceStart > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.prayer_log_missed_total),
                        style = MaterialTheme.typography.labelSmall.onSky(),
                        color = faded(0.6f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = NumberFormat.getIntegerInstance(locale).format(state.missedSinceStart),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black).onSky(),
                        color = QuestRedText
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
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale)),
            style = headingStyle(15.sp),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        DayCount(day)
    }
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        day.entries.forEach { entry ->
            // Keyed by day, so picking another day starts its marks afresh rather than seeing a
            // change of mark in each.
            key(day.date, entry.type) {
                DayPrayerMark(day.date, entry, onToggle)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

/** A prayer of the picked day: its emblem over its name, tapped to mark or unmark it. */
@Composable
private fun DayPrayerMark(date: LocalDate, entry: PrayerLogEntry, onToggle: (LocalDate, PrayerLogEntry) -> Unit) {
    val contentColor = LocalGlassTheme.current.contentColor
    val name = entry.type.prayerName
    val statusText = stringResource(entry.status.labelRes)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .toggleable(
                value = entry.status == PrayerLogStatus.PRAYED,
                enabled = entry.status != PrayerLogStatus.UPCOMING,
                role = Role.Checkbox,
                onValueChange = { onToggle(date, entry) }
            )
            .semantics { contentDescription = "$name, $statusText" }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrayerEmblem(entry, size = 40.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold).onSky(),
            color = when (entry.status) {
                PrayerLogStatus.PRAYED -> PrayedGold
                PrayerLogStatus.UPCOMING -> faded(0.45f)
                else -> contentColor.copy(alpha = 0.85f)
            },
            maxLines = 1
        )
    }
}

/**
 * The picked day beside the month, for landscape: its date and count over its five prayers in a
 * column, each a row to tap with its emblem and its name.
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
                    style = headingStyle(14.sp),
                    color = contentColor,
                    maxLines = 1
                )
                Text(
                    text = day.date.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                    style = MaterialTheme.typography.labelSmall.onSky(),
                    color = faded(0.6f),
                    maxLines = 1
                )
            }
            DayCount(day)
        }
        Spacer(Modifier.height(8.dp))
        day.entries.forEach { entry ->
            key(day.date, entry.type) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .toggleable(
                            value = entry.status == PrayerLogStatus.PRAYED,
                            enabled = entry.status != PrayerLogStatus.UPCOMING,
                            role = Role.Checkbox,
                            onValueChange = { onToggle(day.date, entry) }
                        )
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrayerEmblem(entry, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = entry.type.prayerName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold).onSky(),
                        color = when (entry.status) {
                            PrayerLogStatus.PRAYED -> PrayedGold
                            PrayerLogStatus.UPCOMING -> faded(0.45f)
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
        style = figureStyle(15.sp),
        color = if (day.isComplete) PrayedGold else LocalGlassTheme.current.contentColor.copy(alpha = 0.75f)
    )
}

/**
 * A month of days in weeks, starting on the locale's first day of the week. Each day's cell is
 * [cellAspect] times as wide as it's tall.
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
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold).onSky(),
                    color = faded(0.45f),
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
 * A day on the journey log: a tile with its number, all gold once its five prayers are prayed,
 * else with a pip for each prayer in how it stands; today's is edged in green, the picked one
 * framed in gold. Days still to come are only a faint number.
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
    val showPips = day != null && !isComplete && (day.isTracked || day.prayed > 0)
    val description = day?.let { "${date.dayOfMonth}: ${it.prayed}/${it.entries.size}" } ?: date.dayOfMonth.toString()
    val tile = RoundedCornerShape(5.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .then(
                if (isSelected) Modifier.border(2.dp, GoldLight, RoundedCornerShape(7.dp)).padding(3.dp)
                else Modifier.padding(1.dp)
            )
            .clip(tile)
            .then(
                when {
                    isComplete -> Modifier.background(Brush.linearGradient(listOf(GoldLight, PrayedGold, GoldDeep)))
                    day != null -> Modifier.background(Color.Black.copy(alpha = 0.14f))
                    else -> Modifier
                }
            )
            .then(
                when {
                    isToday -> Modifier.border(1.5.dp, QuestGreen, tile)
                    day != null && !isComplete -> Modifier.border(1.dp, contentColor.copy(alpha = 0.1f), tile)
                    else -> Modifier
                }
            )
            .then(if (day != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isToday || isComplete) FontWeight.Black else FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                    // The ink on a gold tile needs no shade.
                    shadow = if (isComplete) null else daySkyShadow()
                ),
                color = when {
                    isComplete -> PrayedInk
                    day == null -> faded(0.25f)
                    !day.isTracked && day.prayed == 0 -> faded(0.45f)
                    else -> contentColor
                }
            )
            if (showPips) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    day!!.entries.forEach { entry ->
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(pipColor(entry.status, contentColor))
                        )
                    }
                }
            }
        }
    }
}

/** A prayer's pip on the calendar: gold once prayed, red once missed, green while open, faint otherwise. */
private fun pipColor(status: PrayerLogStatus, contentColor: Color): Color = when (status) {
    PrayerLogStatus.PRAYED -> PrayedGold
    PrayerLogStatus.MISSED -> QuestRed
    PrayerLogStatus.ACTIVE -> QuestGreen
    else -> contentColor.copy(alpha = 0.25f)
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
internal val PrayedInk = Color(0xFF3B2A00)

/** The ember of a streak. */
internal val Ember = Color(0xFFFF8A4C)

/**
 * The sheet's green for a prayer whose time is on and red for one whose time has gone: deeper and
 * warmer than the home screen's, to sit with the gold. The deep ones fill a quest's row, dark
 * enough to hold the brighter one in text over any sky; the red's text is lifted a little to read.
 */
internal val QuestGreen = Color(0xFF3CC47C)
internal val QuestGreenDeep = Color(0xFF0E3B26)
internal val QuestRed = Color(0xFFE5484D)
internal val QuestRedDeep = Color(0xFF45121A)
internal val QuestRedText = Color(0xFFF2675F)

/** The sheet's cards: squarer than the app's others, with gold brackets that follow [CardCorner]. */
internal val CardCorner = 12.dp
internal val CardShape = RoundedCornerShape(CardCorner)

/** Prayer times as the rest of the app shows them. */
private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** The history card's least width for the picked day beside the month, and that day's column's width. */
private val SideBySideMinWidth = 400.dp
private val SelectedDayWidth = 136.dp
