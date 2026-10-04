package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.BadgeProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogBadge
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── The star atlas: the badges ──────────────────────────────────────────────

/**
 * The badges as a star atlas, each one a constellation: lit and joined once earned, a faint dotted
 * outline until then. Tapping one shows what it asks for under the atlas; it opens on the next one
 * to earn.
 */
@Composable
internal fun StarAtlas(progress: PrayerLogProgress, modifier: Modifier = Modifier) {
    var picked by remember { mutableStateOf<PrayerLogBadge?>(null) }
    val shown = progress.badges.firstOrNull { it.badge == picked }
        ?: progress.nextBadge
        ?: progress.badges.last()

    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            SkySectionTitle(
                title = stringResource(R.string.prayer_log_atlas),
                hint = stringResource(R.string.prayer_log_atlas_hint),
                modifier = Modifier.weight(1f)
            )
            Text(
                "${progress.earned.size}/${progress.badges.size}",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = skyFaint(0.7f)
            )
        }
        Spacer(Modifier.height(14.dp))
        progress.badges.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { badge ->
                    AtlasTile(
                        progress = badge,
                        isSelected = badge.badge == shown.badge,
                        onClick = { picked = badge.badge },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = shown,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            contentKey = { it.badge to it.isEarned },
            label = "atlasDetail"
        ) { badge -> AtlasDetail(badge, isNext = picked == null && !badge.isEarned) }
    }
}

@Composable
private fun AtlasTile(progress: BadgeProgress, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val earned = progress.isEarned
    val name = stringResource(progress.badge.nameRes)
    val sky = stringResource(progress.badge.skyRes)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .height(112.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (isSelected) 0.12f else if (earned) 0.06f else 0.025f))
            .border(1.dp, Color.White.copy(alpha = if (isSelected) 0.3f else if (earned) 0.12f else 0.06f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$name, $sky, ${progress.current}/${progress.target}" }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ConstellationArt(progress.badge.constellation, earned, Modifier.size(56.dp, 50.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp),
            color = if (earned) SkyInk else skyFaint(0.55f),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Text(
            if (earned) sky else "${progress.current}/${progress.target}",
            style = TextStyle(fontSize = 9.sp, fontFeatureSettings = "tnum"),
            color = skyFaint(0.45f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** A constellation drawn small: its lines, then its stars, lit or a faint outline. */
@Composable
private fun ConstellationArt(constellation: Constellation, lit: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 60f
        val sy = size.height / 54f
        fun at(i: Int) = constellation.points[i].let { (x, y) -> Offset(x * sx, y * sy) }
        val line = if (lit) Color(0xFFFFFAE6).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.22f)
        val dash = if (lit) null else PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
        constellation.edges.forEach { (a, b) ->
            drawLine(line, at(a), at(b), 1.2.dp.toPx(), StrokeCap.Round, dash)
        }
        constellation.points.indices.forEach { i ->
            val p = at(i)
            val bright = i == constellation.bright
            when {
                constellation.planet -> {
                    val r = 5.dp.toPx()
                    if (lit) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4D6).copy(alpha = 0.7f), Color.Transparent), p, r * 3f), r * 3f, p)
                    drawCircle(if (lit) StarWhite else Color.White.copy(alpha = 0.3f), r, p)
                }
                bright -> {
                    if (lit) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), p, 10.dp.toPx()), 10.dp.toPx(), p)
                    drawPath(sparkle(p, 6.dp.toPx()), if (lit) StarWhite else Color.White.copy(alpha = 0.35f))
                }
                else -> {
                    val r = (if (lit) 2.4f else 1.8f).dp.toPx()
                    if (lit) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), p, r * 3f), r * 3f, p)
                    drawCircle(if (lit) StarWhite else Color(0xFFD6DAFF).copy(alpha = 0.35f), r, p)
                }
            }
        }
    }
}

@Composable
private fun AtlasDetail(progress: BadgeProgress, isNext: Boolean) {
    SkyTile(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (isNext) {
                    Text(
                        stringResource(R.string.prayer_log_next_goal).uppercase(),
                        style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp),
                        color = NowLilac
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    "${stringResource(progress.badge.nameRes)} · ${stringResource(progress.badge.skyRes)}",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = SkyInk
                )
                Text(
                    stringResource(progress.badge.descRes),
                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                    color = skyFaint(0.65f)
                )
            }
            Spacer(Modifier.width(12.dp))
            if (progress.isEarned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = StarWhite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.prayer_log_badge_earned),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = SkyInk
                    )
                }
            } else {
                Text(
                    "${progress.current}/${progress.target}",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    color = skyFaint(0.8f)
                )
            }
        }
        if (!progress.isEarned) {
            val fraction by animateFloatAsState(progress.fraction, tween(600), label = "atlasFraction")
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
        }
    }
}

/**
 * A constellation in a box 60 wide and 54 tall: its stars, the lines between them, the one drawn
 * as a bright four-pointed star if any, or a single round planet.
 */
internal class Constellation(
    val points: List<Pair<Float, Float>>,
    val edges: List<Pair<Int, Int>> = points.indices.zipWithNext(),
    val bright: Int = -1,
    val planet: Boolean = false
)

private val PrayerLogBadge.constellation: Constellation
    get() = when (this) {
        // The Little Bear's tail, ending in Polaris.
        PrayerLogBadge.FIRST_PRAYER -> Constellation(
            listOf(10f to 44f, 18f to 38f, 27f to 36f, 34f to 29f, 42f to 31f, 46f to 21f, 50f to 9f),
            bright = 6
        )
        // The five prayers, a pentagon.
        PrayerLogBadge.FIRST_FULL_DAY -> Constellation(
            listOf(30f to 7f, 51f to 22f, 43f to 47f, 17f to 47f, 9f to 22f),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0)
        )
        PrayerLogBadge.STREAK_3 -> Constellation(
            listOf(14f to 42f, 30f to 10f, 48f to 38f),
            edges = listOf(0 to 1, 1 to 2, 2 to 0)
        )
        // The Swan: its long neck, crossed by its wings.
        PrayerLogBadge.STREAK_7 -> Constellation(
            listOf(30f to 5f, 30f to 24f, 30f to 48f, 10f to 21f, 50f to 27f),
            edges = listOf(0 to 1, 1 to 2, 3 to 1, 1 to 4)
        )
        // Venus, the morning star: a planet, not a constellation.
        PrayerLogBadge.FAJR_7 -> Constellation(listOf(30f to 27f), edges = emptyList(), planet = true)
        PrayerLogBadge.PRAYERS_100 -> Constellation(
            listOf(8f to 18f, 13f to 30f, 22f to 39f, 33f to 41f, 43f to 36f, 50f to 26f, 52f to 15f)
        )
        // The Big Dipper: its handle and its bowl.
        PrayerLogBadge.STREAK_40 -> Constellation(
            listOf(5f to 22f, 15f to 18f, 25f to 20f, 33f to 26f, 35f to 40f, 54f to 38f, 52f to 22f),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 3)
        )
        // Orion: shoulders, belt and feet.
        PrayerLogBadge.PRAYERS_500 -> Constellation(
            listOf(16f to 6f, 44f to 10f, 24f to 26f, 30f to 27f, 36f to 28f, 14f to 48f, 46f to 46f),
            edges = listOf(0 to 2, 1 to 4, 2 to 3, 3 to 4, 2 to 5, 4 to 6)
        )
    }

/** The sky a badge is drawn as. */
internal val PrayerLogBadge.skyRes: Int
    get() = when (this) {
        PrayerLogBadge.FIRST_PRAYER -> R.string.prayer_log_star_first_prayer
        PrayerLogBadge.FIRST_FULL_DAY -> R.string.prayer_log_star_first_full_day
        PrayerLogBadge.STREAK_3 -> R.string.prayer_log_star_streak_3
        PrayerLogBadge.STREAK_7 -> R.string.prayer_log_star_streak_7
        PrayerLogBadge.FAJR_7 -> R.string.prayer_log_star_fajr_7
        PrayerLogBadge.PRAYERS_100 -> R.string.prayer_log_star_prayers_100
        PrayerLogBadge.STREAK_40 -> R.string.prayer_log_star_streak_40
        PrayerLogBadge.PRAYERS_500 -> R.string.prayer_log_star_prayers_500
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

// ── Cheers ──────────────────────────────────────────────────────────────────

/**
 * "+10 XP" rising from a star just lit and fading out. [trigger] counts the marks: each new value
 * sends one up; the first value shown sends none.
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
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black),
            color = StarWhite,
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

/** A pill of the night's glass dropping in from the top to name what was just reached. */
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
        val (label, title) = when (shown) {
            is Unlock.Level -> stringResource(R.string.prayer_log_level_up) to stringResource(R.string.prayer_log_level, shown.level)
            is Unlock.Badge -> stringResource(R.string.prayer_log_badge_unlocked) to
                "${stringResource(shown.badge.nameRes)} · ${stringResource(shown.badge.skyRes)}"
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF14123A),
            shadowElevation = 12.dp,
            modifier = Modifier.border(1.dp, NowLilac.copy(alpha = 0.6f), RoundedCornerShape(50))
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF3B3880), Color(0xFF14123A)))),
                    contentAlignment = Alignment.Center
                ) {
                    StarGlyph(20.dp, glow = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        label.uppercase(),
                        style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                        color = NowLilac
                    )
                    Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}

/** How long an [UnlockToast] stays up. */
internal const val UnlockToastMillis = 2600L

/**
 * A burst of stardust from [origin] (a fraction of the size), once for each new value of
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
                size = random.nextFloat() * 4f + 3f,
                star = random.nextInt(3) == 0
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
            val s = piece.size * density
            if (piece.star) {
                rotate(piece.spin * seconds, pivot = Offset(x, y)) {
                    drawPath(sparkle(Offset(x, y), s * 1.4f), piece.color.copy(alpha = fade))
                }
            } else {
                drawCircle(piece.color.copy(alpha = fade), radius = s * 0.5f, center = Offset(x, y))
            }
        }
    }
}

private class Piece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val color: Color,
    val size: Float,
    val star: Boolean
)

private const val ConfettiMillis = 1900

private val ConfettiColors = listOf(Color.White, StarWhite, NowLilac) +
    PrayerType.entries.map { it.accentColor }

