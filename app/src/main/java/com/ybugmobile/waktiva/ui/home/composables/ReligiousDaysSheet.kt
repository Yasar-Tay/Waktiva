package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.ReligiousDay
import com.ybugmobile.waktiva.domain.provider.ReligiousDaysProvider
import com.ybugmobile.waktiva.ui.theme.LocalBackgroundGradient
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.ui.theme.liquidGlass
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * The religious days of [year], in a sheet cut from the home screen's own sky: the current
 * background gradient under one pane of liquid glass, so it follows the time of day and the
 * weather.
 *
 * The days are a plain list under month headings. Consecutive days of one occasion, such as the
 * days of Eid, share a row; days already past are dimmed and the next one carries a lit dot.
 * The list opens at the next occasion's month. The year in the corner switches between this year and
 * the next: by a swipe, or a tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReligiousDaysSheet(
    year: Int,
    onDismiss: () -> Unit
) {
    val glass = LocalGlassTheme.current
    val sky = LocalBackgroundGradient.current
    val contentColor = glass.contentColor
    val locale = Locale.getDefault()
    val today = LocalDate.now()

    val years = remember(today.year) { listOf(today.year, today.year + 1) }
    var shownYear by rememberSaveable { mutableIntStateOf(year.coerceIn(years.first(), years.last())) }

    val occasions = remember(shownYear) { ReligiousDaysProvider.getReligiousDays(shownYear).toOccasions() }
    val next = occasions.firstOrNull { !it.end.isBefore(today) }
    val entries = remember(occasions) { occasions.withMonthHeadings() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        contentColor = contentColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = null,
        shape = SheetShape,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SheetShape)
                .background(sky)
                // Deepens the sky a little so the white text reads on any hour's colours.
                .background(Color.Black.copy(alpha = 0.18f))
                .liquidGlass(SheetShape, glass)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 22.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(contentColor.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mosque, contentDescription = null, tint = contentColor, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.religious_days_title),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                YearSwitcher(
                    year = shownYear,
                    years = years,
                    contentColor = contentColor,
                    onYearChange = { shownYear = it }
                )
            }

            // Keyed on the year so each year's list opens afresh at its own next occasion.
            key(shownYear) {
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = entries.indexOfFirst { it is Entry.Month && it.month == next?.start?.monthValue }
                        .coerceAtLeast(0)
                )
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.religious_days_empty, shownYear),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 20.dp, bottom = 40.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
                    ) {
                        items(entries, key = { it.key }) { entry ->
                            when (entry) {
                                is Entry.Month -> MonthHeading(entry.month, locale, contentColor)
                                is Entry.Day -> OccasionRow(
                                    occasion = entry.occasion,
                                    locale = locale,
                                    contentColor = contentColor,
                                    isPast = entry.occasion.end.isBefore(today),
                                    isNext = entry.occasion == next
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The shown year, swiped sideways to the year before or after it in [years]; a tap moves on to
 * the next, round to the first, as the arrow beside it hints.
 */
@Composable
private fun YearSwitcher(year: Int, years: List<Int>, contentColor: Color, onYearChange: (Int) -> Unit) {
    val index = years.indexOf(year)
    val yearStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontFeatureSettings = "tnum")

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // The year leans after the finger, a little, and springs back on release.
    val lean by animateFloatAsState(
        targetValue = drag.coerceIn(-threshold, threshold) * 0.5f,
        animationSpec = if (dragging) snap() else spring(),
        label = "religiousYearLean"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(years, index, isRtl) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        // Swiping towards the start, as on a pager, brings the next year.
                        val forward = if (isRtl) drag > 0 else drag < 0
                        val target = if (forward) index + 1 else index - 1
                        if (abs(drag) > threshold && target in years.indices) onYearChange(years[target])
                        drag = 0f
                        dragging = false
                    },
                    onDragCancel = {
                        drag = 0f
                        dragging = false
                    }
                ) { change, dx ->
                    change.consume()
                    drag += dx
                }
            }
            .clickable { onYearChange(years[(index + 1) % years.size]) }
            .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
    ) {
        AnimatedContent(
            targetState = year,
            transitionSpec = {
                val towardsStart = (targetState > initialState) != isRtl
                (slideInHorizontally { if (towardsStart) it else -it } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (towardsStart) -it else it } + fadeOut())
            },
            label = "religiousYear",
            modifier = Modifier.graphicsLayer { translationX = lean }
        ) { shown ->
            Text(text = shown.toString(), style = yearStyle, color = contentColor.copy(alpha = 0.85f))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MonthHeading(month: Int, locale: Locale, contentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
    ) {
        Text(
            text = Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            ),
            color = contentColor.copy(alpha = 0.55f)
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(contentColor.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun OccasionRow(
    occasion: Occasion,
    locale: Locale,
    contentColor: Color,
    isPast: Boolean,
    isNext: Boolean
) {
    val accent = accentFor(occasion.nameResId) ?: contentColor

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast) 0.45f else 1f)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = occasion.dayText(),
            color = accent,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1,
            modifier = Modifier.width(68.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(occasion.nameResId),
                color = contentColor,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = occasion.weekdayText(locale),
                color = contentColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (isNext) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .background(accent, CircleShape)
            )
        }
    }
}

/** One occasion, which spans several days when consecutive days share a name. */
private data class Occasion(val start: LocalDate, val end: LocalDate, val nameResId: Int) {
    fun dayText(): String =
        if (start == end) start.dayOfMonth.toString() else "${start.dayOfMonth}–${end.dayOfMonth}"

    fun weekdayText(locale: Locale): String {
        val weekday = DateTimeFormatter.ofPattern("EEEE", locale)
        return when {
            start == end -> start.format(weekday)
            start.month == end.month -> "${start.format(weekday)} – ${end.format(weekday)}"
            // A span into the next month names both dates, as the day column shows only numbers.
            else -> "${start.format(DateTimeFormatter.ofPattern("d MMMM", locale))} – " +
                end.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        }
    }
}

/** A row of the list: a month heading, or an occasion under it. */
private sealed class Entry(val key: String) {
    class Month(val month: Int) : Entry("month-$month")
    class Day(val occasion: Occasion) : Entry("day-${occasion.start}")
}

private fun List<Occasion>.withMonthHeadings(): List<Entry> {
    val entries = mutableListOf<Entry>()
    for (occasion in this) {
        val month = occasion.start.monthValue
        if ((entries.lastOrNull() as? Entry.Day)?.occasion?.start?.monthValue != month) {
            entries += Entry.Month(month)
        }
        entries += Entry.Day(occasion)
    }
    return entries
}

private fun List<ReligiousDay>.toOccasions(): List<Occasion> {
    val occasions = mutableListOf<Occasion>()
    for (day in this) {
        val last = occasions.lastOrNull()
        if (last != null && last.nameResId == day.nameResId && last.end.plusDays(1) == day.date) {
            occasions[occasions.lastIndex] = last.copy(end = day.date)
        } else {
            occasions += Occasion(day.date, day.date, day.nameResId)
        }
    }
    return occasions
}

/** The calendar strip's Eid gold and Ramadan green; null for the other days, which use the content colour. */
private fun accentFor(nameResId: Int): Color? = when (nameResId) {
    R.string.rel_day_ramadan_eid, R.string.rel_day_sacrifice_eid, R.string.rel_day_eid_eve -> Color(0xFFFBBF24)
    R.string.rel_day_first_tarawih, R.string.rel_day_ramadan_start, R.string.rel_day_kadir -> Color(0xFF4ADE80)
    else -> null
}

private val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
