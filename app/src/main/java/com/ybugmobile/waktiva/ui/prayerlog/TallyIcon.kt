package com.ybugmobile.waktiva.ui.prayerlog

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The prayer log's icon: a tally of five, four strokes crossed by the fifth, as notches were cut
 * into a çetele stick. Drawn in strokes with round ends, like the rounded Material icons beside it
 * in the navigation bar; tinting colours it like any icon.
 */
val TallyIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Tally",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // The four strokes lean a little, as if cut by hand, and differ slightly in height.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6.2f, 5.5f); lineTo(5.6f, 18.5f)
            moveTo(10.2f, 5f); lineTo(9.6f, 19f)
            moveTo(14.2f, 5.5f); lineTo(13.6f, 18.5f)
            moveTo(18.2f, 5f); lineTo(17.6f, 19f)
        }
        // The fifth, across them.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(3f, 15.8f); lineTo(21f, 8.2f)
        }
    }.build()
}
