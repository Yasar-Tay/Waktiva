package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.BadgeProgress
import com.ybugmobile.waktiva.domain.model.PrayerLogBadge
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.PrayedGold
import com.ybugmobile.waktiva.ui.home.composables.accentColor
import com.ybugmobile.waktiva.ui.theme.GlassSurface
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The player as on a character sheet: a gold crest with the level in it, beside it the level, the
 * title it earns and all the XP gathered, and under them the XP bar with what the next level
 * takes. Gold brackets hold its corners. [compact] makes it shorter, for landscape.
 */
@Composable
internal fun CharacterCard(progress: PrayerLogProgress, modifier: Modifier = Modifier, compact: Boolean = false) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    val integer = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val fraction by animateFloatAsState(
        progress.levelXp.toFloat() / progress.levelSpan,
        tween(700, easing = FastOutSlowInEasing),
        label = "levelFraction"
    )
    val xp by animateIntAsState(progress.xp, tween(700), label = "xp")

    GlassSurface(
        shape = CardShape,
        accent = PrayedGold.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .cornerBrackets(PrayedGold)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelCrest(
                    progress.level,
                    if (compact) Modifier.size(width = 60.dp, height = 70.dp) else Modifier.size(width = 84.dp, height = 98.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.prayer_log_level, progress.level).uppercase(locale),
                        style = eyebrowStyle(11.sp),
                        color = PrayedGold
                    )
                    Text(
                        text = stringResource(progress.level.rankRes),
                        style = headingStyle(if (compact) 18.sp else 21.sp),
                        color = contentColor
                    )
                    Text(
                        text = stringResource(R.string.prayer_log_xp_total, integer.format(xp)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(
                        R.string.prayer_log_xp,
                        "${integer.format(progress.levelXp)} / ${integer.format(progress.levelSpan)}"
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.prayer_log_xp_to_next,
                        progress.levelSpan - progress.levelXp,
                        progress.level + 1
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
            XpBar(fraction, Modifier.fillMaxWidth().height(if (compact) 10.dp else 14.dp), ticks = true)
        }
    }
}

/** The level on a gold shield, which bounces each time the level goes up. */
@Composable
private fun LevelCrest(level: Int, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(1f) }
    var shownLevel by remember { mutableIntStateOf(level) }
    LaunchedEffect(level) {
        if (level > shownLevel) {
            pop.snapTo(1.25f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        shownLevel = level
    }

    BoxWithConstraints(modifier.scale(pop.value), contentAlignment = Alignment.Center) {
        val height = maxHeight
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val gold = Brush.linearGradient(
                        listOf(GoldLight, PrayedGold, GoldDeep),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                    val sx = size.width / CrestWidth
                    val sy = size.height / CrestHeight
                    onDrawBehind {
                        scale(sx, sy, pivot = Offset.Zero) {
                            drawPath(CrestShield, gold)
                            drawPath(CrestInner, PrayedInk.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx() / sx))
                        }
                    }
                }
        )
        Text(
            text = level.toString(),
            style = figureStyle((height.value * 0.38f).sp),
            color = PrayedInk,
            modifier = Modifier.padding(bottom = height * 0.12f)
        )
    }
}

/** The title [this] level earns. */
private val Int.rankRes: Int
    get() = when {
        this < 5 -> R.string.prayer_log_rank_traveler
        this < 10 -> R.string.prayer_log_rank_resolute
        this < 20 -> R.string.prayer_log_rank_steadfast
        else -> R.string.prayer_log_rank_devoted
    }

/** A bar filled in gold to [fraction]; with [ticks], notched every tenth like a gauge. */
@Composable
internal fun XpBar(fraction: Float, modifier: Modifier = Modifier, ticks: Boolean = false) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, PrayedGold.copy(alpha = if (ticks) 0.4f else 0f), shape)
            .drawWithContent {
                drawContent()
                if (ticks) {
                    for (i in 1 until 10) {
                        val x = size.width * i / 10f
                        drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Brush.horizontalGradient(listOf(GoldDeep, PrayedGold, GoldLight)))
        )
    }
}

/** Gold brackets over a card's four corners, following their rounding, as on a framed sheet. */
internal fun Modifier.cornerBrackets(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = 2.dp.toPx()
    val radius = CardCorner.toPx() - stroke / 2f
    val reach = radius + 12.dp.toPx()
    val inset = stroke / 2f
    val bracket = Path().apply {
        moveTo(inset, reach)
        lineTo(inset, inset + radius)
        arcTo(Rect(inset, inset, inset + radius * 2f, inset + radius * 2f), 180f, 90f, false)
        lineTo(reach, inset)
    }
    val style = Stroke(stroke, cap = StrokeCap.Round)
    drawPath(bracket, color, style = style)
    withTransform({ scale(-1f, 1f, pivot = center) }) { drawPath(bracket, color, style = style) }
    withTransform({ scale(1f, -1f, pivot = center) }) { drawPath(bracket, color, style = style) }
    withTransform({ scale(-1f, -1f, pivot = center) }) { drawPath(bracket, color, style = style) }
}

/**
 * A card's heading: a gold [icon], its [title] in spaced capitals, and at the end [count], if
 * any, in [countColor].
 */
@Composable
internal fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    countColor: Color = LocalGlassTheme.current.contentColor.copy(alpha = 0.7f)
) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PrayedGold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = title.uppercase(locale),
            style = eyebrowStyle(13.sp),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Text(text = count, style = figureStyle(16.sp), color = countColor)
        }
    }
}

/** A small gold diamond, the sheet's ornament. */
@Composable
internal fun Diamond(size: Dp, color: Color, modifier: Modifier = Modifier, filled: Boolean = true) {
    Box(
        modifier
            .size(size)
            .rotate(45f)
            .then(
                if (filled) Modifier.background(color) else Modifier.border(1.dp, color)
            )
    )
}

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
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            SectionHeader(
                icon = Icons.Rounded.EmojiEvents,
                title = stringResource(R.string.prayer_log_badges),
                count = "${progress.earned.size}/${progress.badges.size}",
                modifier = Modifier.padding(horizontal = 4.dp)
            )
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
    val contentColor = LocalGlassTheme.current.contentColor
    val name = stringResource(progress.badge.nameRes)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .then(if (isSelected) Modifier.background(contentColor.copy(alpha = 0.08f)) else Modifier)
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

/** A badge as a medal: solid gold once earned, else faint, with its progress drawn around it. */
@Composable
private fun BadgeMedal(progress: BadgeProgress, modifier: Modifier = Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val earned = progress.isEarned
    val fraction by animateFloatAsState(progress.fraction, tween(600), label = "badgeFraction")
    val pop by animateFloatAsState(
        if (earned) 1f else 0.9f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "badgePop"
    )

    Box(modifier.scale(pop), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            if (earned) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(PrayedGold.copy(alpha = 0.45f), Color.Transparent),
                        center = center,
                        radius = size.minDimension / 2f
                    )
                )
                drawCircle(
                    Brush.linearGradient(
                        listOf(GoldLight, PrayedGold, GoldDeep),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    ),
                    radius = size.minDimension / 2f - stroke
                )
                drawCircle(
                    Color.White.copy(alpha = 0.35f),
                    radius = size.minDimension / 2f - stroke * 2f,
                    style = Stroke(1.dp.toPx())
                )
            } else {
                drawCircle(Color.Black.copy(alpha = 0.18f), radius = size.minDimension / 2f - stroke)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = contentColor.copy(alpha = 0.12f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = arcSize,
                    style = Stroke(stroke)
                )
                if (fraction > 0f) {
                    drawArc(
                        color = PrayedGold.copy(alpha = 0.9f),
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
        Icon(
            progress.badge.icon,
            contentDescription = null,
            tint = if (earned) PrayedInk else contentColor.copy(alpha = 0.4f),
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun BadgeDetail(progress: BadgeProgress, isNext: Boolean) {
    val contentColor = LocalGlassTheme.current.contentColor
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        if (isNext) {
            Text(
                text = stringResource(R.string.prayer_log_next_goal).uppercase(locale),
                style = eyebrowStyle(10.sp),
                color = PrayedGold
            )
            Spacer(Modifier.height(2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(progress.badge.nameRes),
                    style = headingStyle(16.sp),
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
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = PrayedGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.prayer_log_badge_earned),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = PrayedGold
                    )
                }
            } else {
                Text(
                    text = "${progress.current}/${progress.target}",
                    style = figureStyle(15.sp),
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
        if (!progress.isEarned) {
            Spacer(Modifier.height(8.dp))
            XpBar(progress.fraction, Modifier.fillMaxWidth().height(6.dp))
        }
    }
}

/**
 * The full day's chest under the day's prayers: shut, with a pip lit for each prayer prayed,
 * until all five are, then open in gold with its bonus won.
 */
@Composable
internal fun FullDayChest(prayed: List<Boolean>, modifier: Modifier = Modifier) {
    val contentColor = LocalGlassTheme.current.contentColor
    val open = prayed.all { it }
    val tone by animateColorAsState(if (open) GoldLight else PrayedGold.copy(alpha = 0.7f), label = "chestTone")
    val pop by animateFloatAsState(
        if (open) 1.03f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "chestPop"
    )
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(pop)
            .clip(shape)
            .background(if (open) PrayedGold.copy(alpha = 0.14f) else Color.Transparent)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = PrayedGold.copy(alpha = if (open) 0.9f else 0.45f),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                )
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(targetState = open, label = "chest") { isOpen ->
            Canvas(Modifier.size(38.dp)) {
                val s = size.minDimension / ChestBox
                scale(s, s, pivot = Offset.Zero) {
                    drawPath(
                        if (isOpen) ChestOpen else ChestShut,
                        tone,
                        style = Stroke(2.dp.toPx() / s, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.prayer_log_chest),
                style = headingStyle(15.sp),
                color = if (open) GoldLight else contentColor
            )
            Text(
                text = stringResource(if (open) R.string.prayer_log_chest_open else R.string.prayer_log_chest_hint),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                prayed.forEach { lit ->
                    Diamond(
                        size = 7.dp,
                        color = if (lit) PrayedGold else PrayedGold.copy(alpha = 0.5f),
                        filled = lit
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.prayer_log_xp_gain, PrayerLogProgress.FULL_DAY_BONUS),
            style = figureStyle(16.sp),
            color = tone
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
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            color = PrayedGold,
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

/** A gold banner dropping in from the top to name what was just reached. */
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
        val locale = LocalConfiguration.current.locales[0]
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
        val shape = RoundedCornerShape(CardCorner)
        Surface(
            shape = shape,
            color = Color(0xFF2A2110),
            shadowElevation = 12.dp,
            modifier = Modifier
                .border(1.5.dp, PrayedGold, shape)
                .cornerBrackets(GoldLight)
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GoldLight, PrayedGold, GoldDeep))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = PrayedInk, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = label.uppercase(locale),
                        style = eyebrowStyle(10.sp),
                        color = PrayedGold
                    )
                    Text(
                        text = title,
                        style = headingStyle(16.sp),
                        color = Color.White
                    )
                }
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

/** The section names of the sheet: the headline face, bold, in wide-spaced capitals. */
@Composable
internal fun eyebrowStyle(size: TextUnit): TextStyle = MaterialTheme.typography.headlineSmall.copy(
    fontSize = size,
    lineHeight = size * 1.4f,
    fontWeight = FontWeight.Bold,
    letterSpacing = 2.sp
)

/** Names and titles: the headline face, bold. */
@Composable
internal fun headingStyle(size: TextUnit): TextStyle = MaterialTheme.typography.headlineSmall.copy(
    fontSize = size,
    lineHeight = size * 1.25f,
    fontWeight = FontWeight.Bold
)

/** Figures: the display face, bold, with digits of one width. */
@Composable
internal fun figureStyle(size: TextUnit): TextStyle = MaterialTheme.typography.displaySmall.copy(
    fontSize = size,
    lineHeight = size * 1.15f,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum"
)

internal val GoldLight = Color(0xFFFFECB3)
internal val GoldDeep = Color(0xFFE0A800)

/** The crest's shield, and the line just inside it, on a [CrestWidth] by [CrestHeight] grid. */
private const val CrestWidth = 96f
private const val CrestHeight = 112f
private val CrestShield: Path by lazy {
    PathParser().parsePathString("M48 3L90 17V54C90 81 71 99 48 109C25 99 6 81 6 54V17Z").toPath()
}
private val CrestInner: Path by lazy {
    PathParser().parsePathString("M48 11L82 22.5V54C82 76 67 91 48 100C29 91 14 76 14 54V22.5Z").toPath()
}

/** The chest, shut and open, on a [ChestBox] square grid. */
private const val ChestBox = 40f
private val ChestShut: Path by lazy {
    PathParser().parsePathString("M6 18H34V34H6ZM6 18C6 12 10 9 20 9S34 12 34 18M17 18V24H23V18").toPath()
}
private val ChestOpen: Path by lazy {
    PathParser().parsePathString("M6 20H34V34H6ZM6 20L9 11H31L34 20M17 20V25H23V20M12 6L14 9M20 3V7M28 6L26 9").toPath()
}

private const val ConfettiMillis = 1900

private val ConfettiColors = listOf(PrayedGold, GoldLight, Color.White) +
    PrayerType.entries.map { it.accentColor }

private val PrayerLogBadge.icon: ImageVector
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

