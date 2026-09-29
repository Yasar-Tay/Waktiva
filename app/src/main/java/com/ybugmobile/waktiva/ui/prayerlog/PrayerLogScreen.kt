package com.ybugmobile.waktiva.ui.prayerlog

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TouchApp
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.ui.home.composables.MissedRed
import com.ybugmobile.waktiva.ui.home.composables.PrayedGold
import com.ybugmobile.waktiva.ui.home.composables.PrayedSeal
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import com.ybugmobile.waktiva.ui.theme.GlassSurface
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The prayer log (çetele): today's five prayers around a ring, the streak of complete days and how
 * the last week and month went, then a timeline of days, newest first. Any prayer whose time has
 * come can be marked or unmarked here with a tap, today's or an earlier day's.
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
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = contentColor)
            }
            return@Scaffold
        }

        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .then(
                    // Clear of the navigation rail in landscape.
                    if (isLandscape) Modifier.displayCutoutPadding().padding(start = 72.dp) else Modifier
                ),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = bottomInset + if (isLandscape) 24.dp else 100.dp
            )
        ) {
            state.today?.let { today ->
                item(key = "today") {
                    TodayCard(today, state.streak, onToggle)
                }
            }
            if (state.startDate == null) {
                item(key = "hint") { HintCard() }
            }
            item(key = "stats") {
                StatsRow(state)
            }
            item(key = "timeline-title") {
                TimelineHeader()
            }
            itemsIndexed(state.days, key = { _, day -> day.date.toString() }) { index, day ->
                TimelineRow(
                    day = day,
                    isFirst = index == 0,
                    isLast = index == state.days.lastIndex,
                    onToggle = onToggle
                )
            }
            if (state.canShowEarlier) {
                item(key = "earlier") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = viewModel::showEarlierDays,
                            colors = ButtonDefaults.textButtonColors(contentColor = contentColor.copy(alpha = 0.8f)),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(stringResource(R.string.prayer_log_earlier), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Today: the five prayers around a ring, the streak, and the prayers as buttons to mark. */
@Composable
private fun TodayCard(
    today: PrayerLogDay,
    streak: Int,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val active = today.entries.firstOrNull { it.status == PrayerLogStatus.ACTIVE }

    GlassSurface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayRing(today, Modifier.size(108.dp))
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = today.date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale)),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            today.isComplete -> stringResource(R.string.prayer_log_all_done)
                            active != null -> stringResource(R.string.prayer_log_now, active.type.displayName)
                            else -> stringResource(R.string.prayer_log_today_count, today.prayed, today.entries.size)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = if (streak > 0) PrayedGold else contentColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = streak.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = contentColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.prayer_log_streak),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                today.entries.forEach { entry ->
                    PrayerChip(
                        entry = entry,
                        size = 46.dp,
                        showLabel = true,
                        onClick = { onToggle(today.date, entry) }
                    )
                }
            }
        }
    }
}

/** A ring of five arcs, one per prayer, lit in the prayer's colour once it's prayed. */
@Composable
private fun DayRing(day: PrayerLogDay, modifier: Modifier = Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val colors = day.entries.map { entry ->
        val target = when (entry.status) {
            PrayerLogStatus.PRAYED -> lerp(entry.type.accentColor, PrayedGold, 0.35f)
            PrayerLogStatus.MISSED -> MissedRed.copy(alpha = 0.75f)
            PrayerLogStatus.ACTIVE -> entry.type.accentColor.copy(alpha = 0.45f)
            else -> contentColor.copy(alpha = 0.12f)
        }
        animateColorAsState(target, label = "ringArc").value
    }
    val glow by animateFloatAsState(if (day.isComplete) 1f else 0f, label = "ringGlow")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            if (glow > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(PrayedGold.copy(alpha = 0.45f * glow), Color.Transparent),
                        center = center,
                        radius = size.minDimension / 2f
                    )
                )
            }
            val sweep = 360f / colors.size
            val gap = 8f
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

/** Shown until the first prayer is marked: how to mark one. */
@Composable
private fun HintCard() {
    val contentColor = LocalGlassTheme.current.contentColor
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        tint = PrayedGold,
        accent = PrayedGold.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = PrayedGold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.prayer_log_hint),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = contentColor.copy(alpha = 0.85f)
            )
        }
    }
}

/** The last week's and month's share of prayers prayed, and all missed since the log began. */
@Composable
private fun StatsRow(state: PrayerLogViewState) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile(
            value = state.week.rate?.let { percent.format(it) } ?: "—",
            label = stringResource(R.string.prayer_log_week),
            modifier = Modifier.weight(1f)
        )
        StatTile(
            value = state.month.rate?.let { percent.format(it) } ?: "—",
            label = stringResource(R.string.prayer_log_month),
            modifier = Modifier.weight(1f)
        )
        StatTile(
            value = NumberFormat.getIntegerInstance(locale).format(state.missedSinceStart),
            label = stringResource(R.string.prayer_log_missed_total),
            accent = if (state.missedSinceStart > 0) MissedRed else null,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Color? = null) {
    val contentColor = LocalGlassTheme.current.contentColor
    GlassSurface(shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum"
                ),
                color = accent ?: contentColor,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/** The timeline's title, a legend of the marks, and the prayer names over the columns. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineHeader() {
    val contentColor = LocalGlassTheme.current.contentColor
    Column(Modifier.padding(top = 28.dp, bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.prayer_log_timeline).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
            color = contentColor.copy(alpha = 0.4f),
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 8.dp, bottom = 14.dp)
        ) {
            LegendItem(PrayedGold, filled = true, stringResource(R.string.prayer_log_status_prayed))
            LegendItem(MissedRed, filled = false, stringResource(R.string.prayer_log_status_missed))
            LegendItem(PrayedSeal, filled = false, stringResource(R.string.prayer_log_status_active))
            LegendItem(contentColor.copy(alpha = 0.3f), filled = false, stringResource(R.string.prayer_log_status_upcoming))
        }
        // The prayer names, over the columns of the timeline's cards.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(DateColumnWidth + RailWidth))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = CardPadding),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LoggedPrayers.forEach { type ->
                    Box(Modifier.width(TimelineChip), contentAlignment = Alignment.Center) {
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = contentColor.copy(alpha = 0.55f),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, filled: Boolean, label: String) {
    val contentColor = LocalGlassTheme.current.contentColor
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .then(
                    if (filled) Modifier.background(color, CircleShape)
                    else Modifier.border(1.5.dp, color, CircleShape)
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
    }
}

/**
 * A day on the timeline: its date, a node on the rail (gold once the day is complete, a partial
 * ring otherwise) and a card with its five prayers.
 */
@Composable
private fun TimelineRow(
    day: PrayerLogDay,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val dim = if (day.isTracked || day.prayed > 0) 1f else 0.55f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(DateColumnWidth),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isFirst) {
                    stringResource(R.string.prayer_log_today).uppercase(locale)
                } else {
                    day.date.format(DateTimeFormatter.ofPattern("EEE", locale)).uppercase(locale)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                color = (if (isFirst) PrayedGold else contentColor).copy(alpha = 0.7f * dim),
                maxLines = 1
            )
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = contentColor.copy(alpha = dim)
            )
            Text(
                text = day.date.format(DateTimeFormatter.ofPattern("MMM", locale)),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = contentColor.copy(alpha = 0.5f * dim),
                maxLines = 1
            )
        }

        TimelineRail(day, isFirst, isLast, Modifier.width(RailWidth).fillMaxHeight())

        GlassSurface(
            shape = RoundedCornerShape(20.dp),
            emphasis = if (isFirst) 0.4f else 0f,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CardPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                day.entries.forEach { entry ->
                    PrayerChip(
                        entry = entry,
                        size = TimelineChip,
                        showLabel = false,
                        onClick = { onToggle(day.date, entry) }
                    )
                }
            }
        }
    }
}

/** The rail through the timeline, with the day's node. */
@Composable
private fun TimelineRail(day: PrayerLogDay, isFirst: Boolean, isLast: Boolean, modifier: Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val progress by animateFloatAsState(day.prayed / day.entries.size.toFloat(), label = "railNode")
    Canvas(modifier) {
        val x = size.width / 2f
        val y = size.height / 2f
        val line = contentColor.copy(alpha = 0.15f)
        val width = 1.5.dp.toPx()
        if (!isFirst) drawLine(line, Offset(x, 0f), Offset(x, y), width)
        if (!isLast) drawLine(line, Offset(x, y), Offset(x, size.height), width)

        val r = 6.dp.toPx()
        if (day.isComplete) {
            drawCircle(
                Brush.radialGradient(listOf(PrayedGold.copy(alpha = 0.55f), Color.Transparent), center = Offset(x, y), radius = r * 2.6f),
                radius = r * 2.6f,
                center = Offset(x, y)
            )
            drawCircle(PrayedGold, r, Offset(x, y))
        } else {
            drawCircle(Color.Black.copy(alpha = 0.25f), r, Offset(x, y))
            drawCircle(contentColor.copy(alpha = 0.25f), r, Offset(x, y), style = Stroke(1.5.dp.toPx()))
            if (progress > 0f) {
                drawArc(
                    color = PrayedGold,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(x - r, y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * A prayer as a round mark: filled in its colour and ringed in gold with a tick once prayed, red
 * with a cross once missed, ringed in its colour while its time is on, faint before its time.
 * Tapping marks or unmarks it, except before its time.
 */
@Composable
private fun PrayerChip(
    entry: PrayerLogEntry,
    size: Dp,
    showLabel: Boolean,
    onClick: () -> Unit
) {
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
            PrayerLogStatus.UNTRACKED -> contentColor.copy(alpha = 0.1f)
        },
        label = "chipRing"
    )
    val glow by animateFloatAsState(if (isPrayed) 1f else 0f, label = "chipGlow")
    val pop by animateFloatAsState(
        if (isPrayed) 1f else 0.94f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "chipPop"
    )

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
                        tint = contentColor.copy(alpha = 0.25f),
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
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = contentColor.copy(alpha = if (status == PrayerLogStatus.UPCOMING) 0.45f else 0.8f),
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

private val DateColumnWidth = 44.dp
private val RailWidth = 26.dp
private val CardPadding = 12.dp
private val TimelineChip = 32.dp
