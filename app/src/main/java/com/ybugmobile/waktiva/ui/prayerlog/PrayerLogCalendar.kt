package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle

/**
 * The calendar: a month of days, each a small ring of its five prayers, prayed ones in their
 * colours and missed ones in coral, so the days and the prayers left undone stand out. Over it,
 * how many times each prayer was missed in the month, and a switch to see only the days with one
 * missed. A day tapped is picked, its five prayers under the month to mark or unmark; arrows step
 * a month back or on, within the last year.
 */
@Composable
internal fun LogCalendar(
    state: PrayerLogViewState,
    onShowMonth: (Int) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToggle: (LocalDate, PrayerLogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val month = state.month ?: return
    val locale = LocalConfiguration.current.locales[0]
    var onlyMissed by rememberSaveable { mutableStateOf(false) }
    val monthMissed = state.monthMissed.values.sum()

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkySectionTitle(
                title = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            MissedSwitch(onlyMissed, monthMissed, onChange = { onlyMissed = it })
        }
        Spacer(Modifier.height(14.dp))

        // How many times each prayer was missed this month.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LoggedPrayers.forEach { type ->
                val count = state.monthMissed[type] ?: 0
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF080A1E).copy(alpha = 0.5f))
                        .border(1.dp, if (count > 0) MissedCoral.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(type.accentColor))
                        Spacer(Modifier.width(5.dp))
                        Text(type.prayerName, style = TextStyle(fontFamily = LogFonts.text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold), color = skyFaint(0.8f), maxLines = 1)
                    }
                    Text(
                        count.toString(),
                        style = TextStyle(fontFamily = LogFonts.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                        color = if (count > 0) MissedCoral else skyFaint(0.45f)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShowMonth(-1) }, enabled = state.canShowEarlier) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.prayer_log_prev_month),
                    tint = skyFaint(if (state.canShowEarlier) 0.8f else 0.2f)
                )
            }
            // The month's prayers prayed, of those whose time has come and gone or is on.
            val days = state.calendar.filterNotNull()
            val prayed = days.sumOf { it.prayed }
            val due = prayed + days.sumOf { it.missed }
            Text(
                if (due > 0) stringResource(R.string.prayer_log_today_count, prayed, due) else "",
                style = TextStyle(fontFamily = LogFonts.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = skyFaint(0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onShowMonth(1) }, enabled = state.canShowLater) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.prayer_log_next_month),
                    tint = skyFaint(if (state.canShowLater) 0.8f else 0.2f)
                )
            }
        }

        // The weekdays, from the first of the week.
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                Text(
                    state.firstDayOfWeek.plus(i.toLong()).getDisplayName(DateTextStyle.SHORT, locale),
                    style = TextStyle(fontFamily = LogFonts.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                    color = skyFaint(0.55f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        val lead = (month.atDay(1).dayOfWeek.value - state.firstDayOfWeek.value + 7) % 7
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        key(date) {
                            val day = state.calendar.getOrNull(date.dayOfMonth - 1)
                            DayCell(
                                date = date,
                                day = day,
                                isSelected = date == state.selected?.date,
                                isToday = date == state.today?.date,
                                dimmed = onlyMissed && (day?.missed ?: 0) == 0,
                                onClick = { onSelect(date) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(10.dp))
        state.selected?.let { day ->
            DayPanel(day, isToday = day.date == state.today?.date, onToggle = onToggle)
        }
    }
}

/**
 * A day of the month: its date over a ring of its five prayers, Fajr at the top and on round the
 * day; a full day's ring glows. A day still to come can't be picked.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    day: PrayerLogDay?,
    isSelected: Boolean,
    isToday: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fade by animateFloatAsState(if (dimmed) 0.25f else 1f, tween(250), label = "dayDim")
    val shape = RoundedCornerShape(14.dp)
    val missed = day?.missed ?: 0
    val full = day?.isComplete == true
    val count = day?.let { stringResource(R.string.prayer_log_today_count, it.prayed, it.entries.size) }
    val missedNames = day?.entries?.filter { it.status == PrayerLogStatus.MISSED }?.map { it.type.prayerName }.orEmpty()
    val description = listOfNotNull(
        date.dayOfMonth.toString(),
        count,
        missedNames.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.prayer_log_month_missed, it.size) + ": " + it.joinToString() }
    ).joinToString(", ")

    Box(
        modifier
            .height(52.dp)
            .alpha(fade)
            .clip(shape)
            .background(
                when {
                    isSelected -> NowLilac.copy(alpha = 0.16f)
                    missed > 0 -> MissedCoral.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .border(
                1.dp,
                when {
                    isSelected -> NowLilac.copy(alpha = 0.8f)
                    isToday -> NowLilac.copy(alpha = 0.35f)
                    else -> Color.Transparent
                },
                shape
            )
            .then(if (day != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics {
                contentDescription = description
                selected = isSelected
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(40.dp)) {
            val r = 14.dp.toPx()
            val box = Rect(center, r)
            if (full) {
                drawCircle(StarWhite.copy(alpha = 0.14f), r + 3.dp.toPx(), center)
            }
            LoggedPrayers.forEachIndexed { i, type ->
                val status = day?.entries?.getOrNull(i)?.status
                val color = when (status) {
                    PrayerLogStatus.PRAYED -> if (full) type.paleColor else type.accentColor
                    PrayerLogStatus.MISSED -> MissedCoral
                    PrayerLogStatus.ACTIVE -> NowLilac
                    else -> Color.White.copy(alpha = 0.16f)
                }
                val slice = Path().apply { arcTo(box, -90f + i * 72f + 6f, 60f, true) }
                drawPath(slice, color, style = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Text(
            date.dayOfMonth.toString(),
            style = TextStyle(fontFamily = LogFonts.text, fontSize = 13.sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium, fontFeatureSettings = "tnum"),
            color = when {
                day == null || day.isTracked.not() -> skyFaint(0.35f)
                isToday -> NowLilac
                else -> SkyInk
            }
        )
    }
}

/**
 * The picked day: its date and how many of its prayers are prayed, and its five prayers, each with
 * its star in its state and what it stands at, to mark as prayed or unmark. A prayer whose time
 * hasn't come can't be marked yet.
 */
@Composable
private fun DayPanel(day: PrayerLogDay, isToday: Boolean, onToggle: (LocalDate, PrayerLogEntry) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val title = day.date.format(DateTimeFormatter.ofPattern("d MMMM EEEE", locale)) +
        if (isToday) " · ${stringResource(R.string.prayer_log_today)}" else ""

    SkyTile(modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = TextStyle(fontFamily = LogFonts.text, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = SkyInk,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${day.prayed}/${day.entries.size}",
                style = TextStyle(fontFamily = LogFonts.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = if (day.isComplete) StarWhite else skyFaint(0.8f)
            )
        }
        Spacer(Modifier.height(6.dp))
        day.entries.forEach { entry ->
            key(day.date, entry.type) {
                PrayerRow(entry) { onToggle(day.date, entry) }
            }
        }
    }
}

@Composable
private fun PrayerRow(entry: PrayerLogEntry, onToggle: () -> Unit) {
    val status = entry.status
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            PrayerLogStatus.PRAYED -> StarGlyph(20.dp, glow = entry.type.accentColor)
            PrayerLogStatus.ACTIVE -> StarGlyph(20.dp, fill = null, outline = Color(0xFFDDE1FF))
            PrayerLogStatus.MISSED -> StarGlyph(20.dp, fill = null, outline = MissedCoral)
            else -> StarGlyph(20.dp, fill = null, outline = Color.White.copy(alpha = 0.35f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.type.prayerName, style = TextStyle(fontFamily = LogFonts.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = SkyInk, maxLines = 1)
            Text(
                stringResource(status.labelRes),
                style = TextStyle(fontFamily = LogFonts.text, fontSize = 12.sp),
                color = when (status) {
                    PrayerLogStatus.MISSED -> MissedCoral
                    PrayerLogStatus.ACTIVE -> NowLilac
                    else -> skyFaint(0.6f)
                },
                maxLines = 1
            )
        }
        when (status) {
            PrayerLogStatus.UPCOMING -> Unit
            PrayerLogStatus.PRAYED -> RowButton(stringResource(R.string.prayer_log_unmark), filled = false, onClick = onToggle)
            else -> RowButton(stringResource(R.string.prayer_log_mark), filled = true, onClick = onToggle)
        }
    }
}

@Composable
private fun RowButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (filled) StarWhite else Color.Transparent)
            .border(1.dp, if (filled) Color.Transparent else Color.White.copy(alpha = 0.26f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = LogFonts.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (filled) Color(0xFF0B0F2A) else skyFaint(0.85f),
            maxLines = 1
        )
    }
}

/** "N missed" this month, and a switch that dims the days with none missed. */
@Composable
private fun MissedSwitch(on: Boolean, missed: Int, onChange: (Boolean) -> Unit) {
    val knob by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "switchKnob")
    val label = stringResource(R.string.prayer_log_show_missed)
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
            stringResource(R.string.prayer_log_month_missed, missed),
            style = TextStyle(fontFamily = LogFonts.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = SkyInk
        )
    }
}
