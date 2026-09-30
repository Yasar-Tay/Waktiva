package com.ybugmobile.waktiva.ui.home.composables.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import kotlin.math.max

/**
 * Shown in place of a tapped prayer marker: a plate in the dial's material with the prayer's
 * gear as a medallion, the prayer name and a larger time, and when known the weather in the
 * prayer's hour ([weather]: its name and temperature) set off by an engraved line. On the classic
 * circle the plate is dark with gold hairlines and the medallion is the prayer as a gemstone.
 */
@Composable
internal fun GearPlaque(
    prayer: GearPrayer,
    name: String,
    style: DayCircleStyle,
    palette: GearPalette,
    phase: State<Float>,
    compact: Boolean,
    modifier: Modifier = Modifier,
    weather: Pair<String, String?>? = null
) {
    val finish = palette.finish(style)
    val height = if (compact) 36.dp else 42.dp
    val endPadding = height * 0.34f
    // Engraved lettering: a light line just below the ink, only on metal.
    val engraving = if (finish.isMetal) Shadow(Color.White.copy(alpha = 0.35f), Offset(0f, 1f), 0f) else null

    Row(
        modifier = modifier
            .height(height)
            .drawBehind { drawPlate(finish, endPadding.toPx()) }
            .padding(start = height * 0.16f, end = endPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            Modifier
                .size(height * 0.72f)
                .drawWithCache {
                    val radius = size.minDimension / 2f
                    // Metal plates show the prayer's gear; the dark plate shows it as a set stone.
                    val outline = planetOutline(radius * 0.92f)
                    val stone = GemCut(radius * 0.92f)
                    val sheen = if (style == DayCircleStyle.STEEL) palette.steelSheen else palette.brassSheen
                    onDrawBehind {
                        if (finish.isMetal) {
                            planet(prayer, center, radius * 0.92f, phase.value, outline, sheen, GearLight.Default, density, isCurrent = false)
                        } else {
                            gemStone(prayer, center, stone, palette.brassSheen, GearLight.Default, palette.gold, density)
                        }
                    }
                }
        )
        Spacer(Modifier.width(height * 0.24f))
        Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
            Text(
                text = name,
                style = TextStyle(
                    color = finish.ink,
                    fontSize = if (compact) 8.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    shadow = engraving
                ),
                maxLines = 1
            )
                Text(
                text = prayer.label,
                style = TextStyle(
                    color = finish.strongInk,
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontFamily = IBMPlexArabic,
                    fontWeight = FontWeight.SemiBold,
                    shadow = engraving
                ),
                maxLines = 1
            )
        }
        weather?.let { (name, temperature) ->
            // An engraved line, then the weather set like the name and the time.
            Spacer(Modifier.width(height * 0.24f))
            Spacer(
                Modifier
                    .width(1.dp)
                    .height(height * 0.55f)
                    .drawBehind {
                        drawRect(finish.ink.copy(alpha = 0.35f))
                        // Cut into metal, the line catches the light along one side.
                        if (finish.isMetal) drawRect(Color.White.copy(alpha = 0.3f), topLeft = Offset(size.width, 0f), size = size)
                    }
            )
            Spacer(Modifier.width(height * 0.24f))
            Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
                Text(
                    text = name,
                    style = TextStyle(
                        color = finish.ink,
                        fontSize = if (compact) 8.sp else 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        shadow = engraving
                    ),
                    maxLines = 1
                )
                Text(
                    text = temperature.orEmpty(),
                    style = TextStyle(
                        color = finish.strongInk,
                        fontSize = if (compact) 14.sp else 16.sp,
                        fontFamily = IBMPlexArabic,
                        fontWeight = FontWeight.SemiBold,
                        shadow = engraving
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

private fun DrawScope.drawPlate(finish: PlateFinish, endPadding: Float) {
    val corner = CornerRadius(size.height * 0.28f)
    val inset = 3.5.dp.toPx()
    val innerCorner = CornerRadius(max(2.dp.toPx(), corner.x - inset))
    val innerSize = Size(size.width - 2 * inset, size.height - 2 * inset)

    translate(0f, 2.dp.toPx()) { drawRoundRect(Color.Black.copy(alpha = 0.35f), cornerRadius = corner) }
    drawRoundRect(brush = finish.fill(Offset.Zero, Offset(size.width, size.height)), cornerRadius = corner)
    drawRoundRect(finish.edge, cornerRadius = corner, style = Stroke(1.dp.toPx()))
    drawRoundRect(finish.inset, Offset(inset, inset), innerSize, innerCorner, Stroke(0.8.dp.toPx()))
    finish.highlight?.let {
        val shift = 0.8.dp.toPx()
        drawRoundRect(it, Offset(inset + shift, inset + shift), innerSize, innerCorner, Stroke(0.6.dp.toPx()))
    }
    finish.screw?.let { screw ->
        // The screw sits at the plate's end, which is on the left in RTL.
        val x = if (layoutDirection == LayoutDirection.Rtl) endPadding * 0.5f else size.width - endPadding * 0.5f
        val y = size.height / 2f
        val r = max(1.8.dp.toPx(), size.height * 0.055f)
        drawCircle(screw, r, Offset(x, y))
        drawLine(Color.White.copy(alpha = 0.35f), Offset(x - r * 0.7f, y + r * 0.7f), Offset(x + r * 0.7f, y - r * 0.7f), 0.6.dp.toPx())
    }
}
