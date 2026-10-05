package com.ybugmobile.waktiva.ui.prayerlog

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The prayer log (çetele) in the deep sky: clouds of gas and pillars of dust (see [CosmicSky]).
 *
 * At the top, today on the dial of the day (see [TodayDial]): the five prayers as stars on the
 * day's ring, lit with a tap, the ring lit between two prayed in a row and closing when the day is
 * full. Under it, the button for the prayer whose time is on, and the streak and the level.
 *
 * Then the calendar: a month of days, the days and the prayers missed standing out, a day picked
 * to look at and mark (see [LogCalendar]); and the star atlas, the badges each a constellation.
 */
@Composable
fun PrayerLogScreen(viewModel: PrayerLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val haptics = LocalHapticFeedback.current
    val onToggle = remember(viewModel, haptics) {
        { date: LocalDate, entry: PrayerLogEntry ->
            val prayed = entry.status != PrayerLogStatus.PRAYED
            haptics.performHapticFeedback(if (prayed) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            viewModel.setPrayed(date, entry.type, prayed)
        }
    }

    // What changed since the last look, to cheer for: the day made full, a level or a badge
    // (stardust, and a word for the last two). The first look after the screen opens is only taken in.
    val unlocks = remember { mutableStateListOf<Unlock>() }
    var stardust by remember { mutableIntStateOf(0) }
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
        if (dayFilled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (news.isNotEmpty() || dayFilled) stardust++
    }
    val unlock = unlocks.firstOrNull()
    LaunchedEffect(unlock) {
        if (unlock != null) {
            delay(UnlockToastMillis)
            unlocks.removeAt(0)
        }
    }

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        CosmicSky(Modifier.fillMaxSize(), scroll = { if (isLandscape) 0 else scroll.value })

        val today = state.today
        if (state.isLoading || today == null) {
            CircularProgressIndicator(color = SkyInk, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val onMark = { entry: PrayerLogEntry -> onToggle(today.date, entry) }
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val sky = @Composable {
            SkyHeader(today, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp), contentAlignment = Alignment.TopCenter) {
                TodayDial(
                    today = today,
                    times = state.todayTimes,
                    now = state.now,
                    onToggle = onMark,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
            SkyCall(today, isFirstUse = state.startDate == null, onMark = onMark, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(24.dp))
            SkyNumbers(state, Modifier.padding(horizontal = 20.dp))
        }
        val history = @Composable {
            LogCalendar(state, viewModel::showMonth, viewModel::select, onToggle, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(48.dp))
            StarAtlas(state.progress, Modifier.padding(horizontal = 20.dp))
        }

        if (isLandscape) {
            // Side by side, clear of the navigation rail: the day on one side, the galaxy and the
            // atlas on the other, each scrolling on its own.
            Row(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                    .padding(start = 76.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    Modifier
                        .weight(0.9f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = bottomInset + 16.dp)
                ) { sky() }
                Column(
                    Modifier
                        .weight(1.1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 20.dp, bottom = bottomInset + 16.dp)
                ) { history() }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 12.dp, bottom = bottomInset + 100.dp)
            ) {
                sky()
                Spacer(Modifier.height(56.dp))
                history()
            }
        }

        ConfettiBurst(stardust, Modifier.fillMaxSize())
        UnlockToast(
            unlock,
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp)
        )
    }
}

/**
 * The screen's name and today's date. The prayer whose time is on isn't named here: its star calls
 * in the sky below and the button under it names it.
 */
@Composable
private fun SkyHeader(today: PrayerLogDay, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.prayer_log_title).uppercase(locale),
            style = TextStyle(fontFamily = LogFonts.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, shadow = TextShade),
            color = skyFaint(0.72f)
        )
        Text(
            today.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)).replaceFirstChar { it.titlecase(locale) },
            style = TextStyle(fontFamily = LogFonts.names, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, shadow = TextShade),
            color = SkyInk
        )
    }
}
