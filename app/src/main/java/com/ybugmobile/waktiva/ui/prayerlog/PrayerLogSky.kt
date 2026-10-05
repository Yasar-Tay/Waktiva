package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogStatus

// ── The night the log is drawn on ───────────────────────────────────────────

/**
 * The home screen's faces, for the çetele too, following the language as the home screen's do: its
 * display face for numbers, its headline face for names and titles, its body face for the rest.
 */
internal object LogFonts {
    val numbers: FontFamily? @Composable get() = MaterialTheme.typography.displayLarge.fontFamily
    val names: FontFamily? @Composable get() = MaterialTheme.typography.headlineSmall.fontFamily
    val text: FontFamily? @Composable get() = MaterialTheme.typography.bodyMedium.fontFamily
}

/** Words on the night: a moonlit white, and the same stepping back. */
internal val SkyInk = Color(0xFFF4F1FF)
internal fun skyFaint(alpha: Float) = Color(0xFFE4E2FF).copy(alpha = alpha)

/** A lit star's white, the lilac of a prayer whose time is on, the coral of one gone unmarked. */
internal val StarWhite = Color(0xFFFFFDF4)
internal val NowLilac = Color(0xFFC5CBF0)
internal val MissedCoral = Color(0xFFFFB4B4)

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

/** Words on the deep sky: a soft dark shade under them, so they hold over its brightest clouds. */
internal val TextShade = Shadow(Color(0xA6020108), Offset(0f, 2f), 18f)

// ── The call under the sky ──────────────────────────────────────────────────

/**
 * Under the sky: the button that lights the star of the prayer whose time is on, with what it
 * brings: its XP, and the full day's bonus when it's the last one. A line only when there's no
 * button to say it: the day full, or a prayer gone unmarked to make up. (How many are lit is the
 * count above; it isn't said twice.) Once the day is full, its quiet acknowledgment in place of
 * the button.
 */
@Composable
internal fun SkyCall(today: PrayerLogDay, isFirstUse: Boolean, onMark: (PrayerLogEntry) -> Unit, modifier: Modifier = Modifier) {
    val active = today.entries.firstOrNull { it.status == PrayerLogStatus.ACTIVE }
    val left = today.entries.count { it.status != PrayerLogStatus.PRAYED }
    val anyMissed = today.entries.any { it.status == PrayerLogStatus.MISSED }
    val line = when {
        today.isComplete -> stringResource(R.string.prayer_log_sky_done)
        active == null && anyMissed -> stringResource(R.string.prayer_log_sky_mark_missed)
        else -> null
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        line?.let {
            Text(
                text = it,
                style = TextStyle(fontFamily = LogFonts.names, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp, shadow = TextShade),
                color = Color(0xFFECEAFF),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (isFirstUse && today.prayed == 0) {
            Text(
                text = stringResource(R.string.prayer_log_hint),
                style = TextStyle(fontFamily = LogFonts.text, fontSize = 12.sp, lineHeight = 17.sp),
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
                        style = TextStyle(fontFamily = LogFonts.text, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = SkyInk
                    )
                    Text(
                        text = "  ·  ${active.type.prayerName}",
                        style = TextStyle(fontFamily = LogFonts.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
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
                            style = TextStyle(fontFamily = LogFonts.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
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
                        style = TextStyle(fontFamily = LogFonts.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = SkyInk
                    )
                }
            }
        }
    }
}

// ── Two numbers ─────────────────────────────────────────────────────────────

/**
 * The streak, and the level with what the next one takes: two tiles. The badges earned are counted
 * by the atlas below.
 */
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
    }
}

/** A pane of dark glass over the deep sky, a lit white edge round it. */
@Composable
internal fun SkyTile(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(12.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1440).copy(alpha = 0.72f), Color(0xFF0A0820).copy(alpha = 0.78f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(22.dp))
            .padding(padding),
        content = content
    )
}

private val NumberStyle @Composable get() = TextStyle(fontFamily = LogFonts.numbers, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp)
private val LabelStyle @Composable get() = TextStyle(fontFamily = LogFonts.text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
private val CaptionStyle @Composable get() = TextStyle(fontFamily = LogFonts.text, fontSize = 12.5.sp, lineHeight = 16.sp)

/** The screen's section titles: a serif name, over a quiet line when there's a [hint]. */
@Composable
internal fun SkySectionTitle(title: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(modifier) {
        Text(title, style = TextStyle(fontFamily = LogFonts.names, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, shadow = TextShade), color = SkyInk)
        hint?.let { Text(it, style = TextStyle(fontFamily = LogFonts.text, fontSize = 12.5.sp, shadow = TextShade), color = skyFaint(0.7f), maxLines = 1) }
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
