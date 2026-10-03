package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.BadgeProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogBadge
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.theme.GlassSurface
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.ui.theme.darken
import com.ybugmobile.waktiva.ui.theme.liquidGlass
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The level in a line: "Level 5" with all the XP gathered beside it, the XP bar, and what the
 * next level takes. [compact] makes the bar thinner, for the landscape's today card.
 */
@Composable
internal fun LevelLine(progress: PrayerLogProgress, modifier: Modifier = Modifier, compact: Boolean = false) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val integer = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val fraction by animateLevelFraction(progress)
    val xp by animateIntAsState(progress.xp, tween(700), label = "xp")

    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.prayer_log_level, progress.level),
                style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                    .copy(fontWeight = FontWeight.ExtraBold),
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.prayer_log_xp, integer.format(xp)),
                style = accentText(
                    MaterialTheme.typography.labelLarge.copy(
                        fontFamily = IBMPlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    )
                )
            )
        }
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        GrooveBar(fraction, Modifier.fillMaxWidth().height(if (compact) 7.dp else 9.dp))
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            text = stringResource(
                R.string.prayer_log_xp_to_next,
                progress.levelSpan - progress.levelXp,
                progress.level + 1
            ),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.55f),
            maxLines = 1
        )
    }
}

@Composable
private fun animateLevelFraction(progress: PrayerLogProgress) = animateFloatAsState(
    progress.levelXp.toFloat() / progress.levelSpan,
    tween(700, easing = FastOutSlowInEasing),
    label = "levelFraction"
)

/** A bar filled in brass to [fraction]: the XP towards the next level, or a badge's progress. */
@Composable
internal fun GoalBar(fraction: Float, modifier: Modifier = Modifier) = GrooveBar(fraction, modifier)

/**
 * The badges: earned ones in gold, the rest faint with how far along they are drawn around them.
 * Tapping one shows what it asks for under the grid; it opens on the next one to earn.
 */
@Composable
internal fun BadgesCard(progress: PrayerLogProgress) {
    val contentColor = LocalGlassTheme.current.contentColor
    var picked by remember { mutableStateOf<PrayerLogBadge?>(null) }
    val shown = progress.badges.firstOrNull { it.badge == picked }
        ?: progress.nextBadge
        ?: progress.badges.last()

    GlassSurface(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = accentInk(),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.prayer_log_badges).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
                    color = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${progress.earned.size}/${progress.badges.size}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = IBMPlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(14.dp))
            progress.badges.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { badge ->
                        BadgeCell(
                            progress = badge,
                            isSelected = badge.badge == shown.badge,
                            onClick = { picked = badge.badge },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            HorizontalDivider(
                color = contentColor.copy(alpha = 0.1f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = shown,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                contentKey = { it.badge to it.isEarned },
                label = "badgeDetail"
            ) { badge -> BadgeDetail(badge, isNext = picked == null && !badge.isEarned) }
        }
    }
}

@Composable
private fun BadgeCell(progress: BadgeProgress, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlassTheme.current
    val contentColor = glass.contentColor
    val name = stringResource(progress.badge.nameRes)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .then(if (isSelected) Modifier.liquidGlass(shape, glass, emphasis = 0.5f) else Modifier)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .semantics { contentDescription = "$name, ${progress.current}/${progress.target}" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BadgeMedal(progress, Modifier.size(52.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 13.sp),
            color = contentColor.copy(alpha = if (progress.isEarned) 0.9f else 0.5f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2
        )
    }
}

/**
 * A badge as a medal: a sphere of the game's coral once earned, with its sign in ink; before, a
 * sphere of deep glass in a groove filling with its progress.
 */
@Composable
private fun BadgeMedal(progress: BadgeProgress, modifier: Modifier = Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val earned = progress.isEarned
    val fraction by animateFloatAsState(progress.fraction, tween(600), label = "badgeFraction")
    val pop by animateFloatAsState(
        if (earned) 1f else 0.92f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "badgePop"
    )

    Box(modifier.scale(pop), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            if (earned) {
                glow(center, r * 1.15f, LogColors.Accent, 0.35f)
                colorOrb(center, r - 3.dp.toPx(), LogColors.Accent)
            } else {
                val stroke = 3.dp.toPx()
                ringGroove(stroke, stroke / 2f + 0.8.dp.toPx())
                if (fraction > 0f) glazedArc(LogColors.Accent, -90f, 360f * fraction, stroke, stroke / 2f + 0.8.dp.toPx())
                glassOrb(center, r - stroke - 3.dp.toPx(), rim = Color.White.copy(alpha = 0.3f), fill = LogColors.Night.copy(alpha = 0.55f), edge = null, shadow = false)
            }
        }
        Icon(
            progress.badge.icon,
            contentDescription = null,
            tint = if (earned) LogColors.AccentInk else contentColor.copy(alpha = 0.4f),
            modifier = Modifier.size(if (earned) 24.dp else 20.dp)
        )
    }
}

@Composable
private fun BadgeDetail(progress: BadgeProgress, isNext: Boolean) {
    val contentColor = LocalGlassTheme.current.contentColor
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        if (isNext) {
            Text(
                text = stringResource(R.string.prayer_log_next_goal).uppercase(),
                style = accentText(MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp))
            )
            Spacer(Modifier.height(2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(progress.badge.nameRes),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = contentColor
                )
                Text(
                    text = stringResource(progress.badge.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.65f)
                )
            }
            Spacer(Modifier.width(12.dp))
            if (progress.isEarned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = accentInk(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.prayer_log_badge_earned),
                        style = accentText(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    )
                }
            } else {
                Text(
                    text = "${progress.current}/${progress.target}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
        }
        if (!progress.isEarned) {
            Spacer(Modifier.height(8.dp))
            GrooveBar(progress.fraction, Modifier.fillMaxWidth().height(6.dp))
        }
    }
}

/** Today's bonus for a full day: a capsule of glass until all five are prayed, then of the game's coral. */
@Composable
internal fun FullDayBonus(isComplete: Boolean, modifier: Modifier = Modifier) {
    val glass = LocalGlassTheme.current
    val shape = RoundedCornerShape(50)
    val pop by animateFloatAsState(
        if (isComplete) 1.05f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bonusPop"
    )
    val ink = if (isComplete) LogColors.AccentInk else glass.contentColor.copy(alpha = 0.75f)
    Row(
        modifier = modifier
            .scale(pop)
            .liquidGlass(shape, glass, emphasis = if (isComplete) 1f else 0.2f)
            .then(
                if (isComplete) {
                    Modifier.background(
                        Brush.linearGradient(listOf(LogColors.Accent.lighten(0.25f), LogColors.Accent, LogColors.Accent.darken(0.1f))),
                        shape
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isComplete) Icons.Rounded.Check else Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = if (isComplete) LogColors.AccentInk else accentInk(),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.prayer_log_full_day_bonus),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ink
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.prayer_log_xp_gain, PrayerLogProgress.FULL_DAY_BONUS),
            style = if (isComplete) {
                MaterialTheme.typography.labelMedium.copy(fontFamily = IBMPlexArabic, fontWeight = FontWeight.Bold, color = LogColors.AccentInk)
            } else {
                accentText(MaterialTheme.typography.labelMedium.copy(fontFamily = IBMPlexArabic, fontWeight = FontWeight.Bold))
            }
        )
    }
}

/**
 * "+10 XP" rising from a prayer just marked and fading out. [trigger] counts the marks: each
 * new value sends one up; the first value shown sends none.
 */
@Composable
internal fun XpPop(trigger: Int, modifier: Modifier = Modifier) {
    val rise = remember { Animatable(1f) }
    var seen by remember { mutableIntStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger != seen) {
            seen = trigger
            rise.snapTo(0f)
            rise.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    if (rise.value < 1f) {
        Text(
            text = stringResource(R.string.prayer_log_xp_gain, PrayerLogProgress.XP_PER_PRAYER),
            style = accentText(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black)),
            maxLines = 1,
            softWrap = false,
            modifier = modifier.graphicsLayer {
                translationY = -rise.value * 30.dp.toPx()
                alpha = 1f - rise.value * rise.value
                val grow = 0.8f + 0.4f * minOf(rise.value * 4f, 1f)
                scaleX = grow
                scaleY = grow
            }
        )
    }
}

/** Something newly reached, to cheer for. */
internal sealed interface Unlock {
    data class Level(val level: Int) : Unlock
    data class Badge(val badge: PrayerLogBadge) : Unlock
}

/** A gold pill dropping in from the top to name what was just reached. */
@Composable
internal fun UnlockToast(unlock: Unlock?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(unlock) }
    if (unlock != null) last = unlock
    AnimatedVisibility(
        visible = unlock != null,
        enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.8f),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val shown = last ?: return@AnimatedVisibility
        val (icon, label, title) = when (shown) {
            is Unlock.Level -> Triple(
                Icons.Rounded.KeyboardDoubleArrowUp,
                stringResource(R.string.prayer_log_level_up),
                stringResource(R.string.prayer_log_level, shown.level)
            )
            is Unlock.Badge -> Triple(
                shown.badge.icon,
                stringResource(R.string.prayer_log_badge_unlocked),
                stringResource(shown.badge.nameRes)
            )
        }
        val glass = LocalGlassTheme.current
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier
                .liquidGlass(shape, glass, emphasis = 1f, accent = LogColors.Accent.copy(alpha = 0.8f))
                .background(Color.Black.copy(alpha = 0.25f), shape)
                .padding(start = 10.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { colorOrb(center, size.minDimension / 2f - 1.dp.toPx(), LogColors.Accent) }
                Icon(icon, contentDescription = null, tint = LogColors.AccentInk, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = label.uppercase(),
                    style = accentText(MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.5.sp))
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

/** How long an [UnlockToast] stays up. */
internal const val UnlockToastMillis = 2600L

/**
 * A burst of confetti from [origin] (a fraction of the size), once for each new value of
 * [trigger]; the first value shown fires none. It doesn't take touches.
 */
@Composable
internal fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier, origin: Offset = Offset(0.5f, 0.3f)) {
    val time = remember { Animatable(1f) }
    var seen by remember { mutableIntStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger != seen) {
            seen = trigger
            time.snapTo(0f)
            time.animateTo(1f, tween(ConfettiMillis, easing = LinearEasing))
        }
    }
    val pieces = remember(trigger) {
        val random = Random(trigger)
        List(56) {
            Piece(
                angle = Math.toRadians(-90.0 + random.nextDouble(-75.0, 75.0)).toFloat(),
                speed = random.nextFloat() * 0.7f + 0.55f,
                spin = random.nextFloat() * 720f - 360f,
                color = ConfettiColors[random.nextInt(ConfettiColors.size)],
                width = random.nextFloat() * 5f + 5f,
                height = random.nextFloat() * 6f + 8f,
                round = random.nextInt(4) == 0
            )
        }
    }
    val t = time.value
    if (t >= 1f) return

    Canvas(modifier) {
        val seconds = t * ConfettiMillis / 1000f
        val reach = size.minDimension * 1.1f
        val gravity = size.height * 0.9f
        val start = Offset(size.width * origin.x, size.height * origin.y)
        val fade = (1f - t).let { it * it }.coerceIn(0f, 1f)
        pieces.forEach { piece ->
            // Thrown out and up, slowed by the air, pulled down.
            val drag = (1f - kotlin.math.exp(-2.2f * seconds)) / 2.2f
            val x = start.x + cos(piece.angle) * piece.speed * reach * drag
            val y = start.y + sin(piece.angle) * piece.speed * reach * drag + 0.5f * gravity * seconds * seconds * 0.6f
            val w = piece.width * density
            val h = piece.height * density
            rotate(piece.spin * seconds, pivot = Offset(x, y)) {
                if (piece.round) {
                    drawCircle(piece.color.copy(alpha = fade), radius = w * 0.6f, center = Offset(x, y))
                } else {
                    // A turning strip: its height swells and shrinks as it tumbles.
                    val tumble = kotlin.math.abs(cos(seconds * 6f + piece.spin))
                    drawRect(
                        piece.color.copy(alpha = fade),
                        topLeft = Offset(x - w / 2f, y - h * tumble / 2f),
                        size = Size(w, h * tumble.coerceAtLeast(0.15f))
                    )
                }
            }
        }
    }
}

private class Piece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val color: Color,
    val width: Float,
    val height: Float,
    val round: Boolean
)

private const val ConfettiMillis = 1900

private val ConfettiColors = listOf(LogColors.Accent, LogColors.AccentText, Color.White) +
    PrayerType.entries.map { it.logColor }

internal val PrayerLogBadge.icon: ImageVector
    get() = when (this) {
        PrayerLogBadge.FIRST_PRAYER -> Icons.Rounded.Flag
        PrayerLogBadge.FIRST_FULL_DAY -> Icons.Rounded.Verified
        PrayerLogBadge.STREAK_3 -> Icons.Rounded.Whatshot
        PrayerLogBadge.STREAK_7 -> Icons.Rounded.LocalFireDepartment
        PrayerLogBadge.FAJR_7 -> Icons.Rounded.WbTwilight
        PrayerLogBadge.PRAYERS_100 -> Icons.Rounded.MilitaryTech
        PrayerLogBadge.STREAK_40 -> Icons.Rounded.EmojiEvents
        PrayerLogBadge.PRAYERS_500 -> Icons.Rounded.WorkspacePremium
    }

internal val PrayerLogBadge.nameRes: Int
    get() = when (this) {
        PrayerLogBadge.FIRST_PRAYER -> R.string.prayer_log_badge_first_prayer
        PrayerLogBadge.FIRST_FULL_DAY -> R.string.prayer_log_badge_first_full_day
        PrayerLogBadge.STREAK_3 -> R.string.prayer_log_badge_streak_3
        PrayerLogBadge.STREAK_7 -> R.string.prayer_log_badge_streak_7
        PrayerLogBadge.FAJR_7 -> R.string.prayer_log_badge_fajr_7
        PrayerLogBadge.PRAYERS_100 -> R.string.prayer_log_badge_prayers_100
        PrayerLogBadge.STREAK_40 -> R.string.prayer_log_badge_streak_40
        PrayerLogBadge.PRAYERS_500 -> R.string.prayer_log_badge_prayers_500
    }

internal val PrayerLogBadge.descRes: Int
    get() = when (this) {
        PrayerLogBadge.FIRST_PRAYER -> R.string.prayer_log_badge_first_prayer_desc
        PrayerLogBadge.FIRST_FULL_DAY -> R.string.prayer_log_badge_first_full_day_desc
        PrayerLogBadge.STREAK_3 -> R.string.prayer_log_badge_streak_3_desc
        PrayerLogBadge.STREAK_7 -> R.string.prayer_log_badge_streak_7_desc
        PrayerLogBadge.FAJR_7 -> R.string.prayer_log_badge_fajr_7_desc
        PrayerLogBadge.PRAYERS_100 -> R.string.prayer_log_badge_prayers_100_desc
        PrayerLogBadge.STREAK_40 -> R.string.prayer_log_badge_streak_40_desc
        PrayerLogBadge.PRAYERS_500 -> R.string.prayer_log_badge_prayers_500_desc
    }

