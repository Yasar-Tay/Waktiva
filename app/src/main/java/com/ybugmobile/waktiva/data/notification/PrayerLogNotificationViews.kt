package com.ybugmobile.waktiva.data.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.home.composables.iconRes
import kotlin.math.roundToInt

/**
 * The prayer log's notifications drawn as its character sheet: their views, under the header
 * the system draws, and the medal a cheer shows.
 */
internal object PrayerLogNotificationViews {

    /** The sheet's gold, its light, the ink on it, its green and its ember. */
    const val GOLD = 0xFFE6BE5A.toInt()
    private const val GOLD_INK = 0xFF2A1D05.toInt()
    private const val GREEN = 0xFF6FCF8E.toInt()
    private const val EMBER = 0xFFE8743B.toInt()
    private const val MUTED = 0xFFC9C2B0.toInt()

    private val SmallDiamonds = listOf(
        R.id.cetele_small_d1, R.id.cetele_small_d2, R.id.cetele_small_d3, R.id.cetele_small_d4, R.id.cetele_small_d5
    )
    private val DayDiamonds = listOf(
        R.id.cetele_day_d1, R.id.cetele_day_d2, R.id.cetele_day_d3, R.id.cetele_day_d4, R.id.cetele_day_d5
    )
    private val DayLabels = listOf(
        R.id.cetele_day_l1, R.id.cetele_day_l2, R.id.cetele_day_l3, R.id.cetele_day_l4, R.id.cetele_day_l5
    )

    /**
     * The nudge, expanded: [type]'s emblem beside [eyebrow], its [name] and [text], and, while
     * [millisLeft] of its time remain, how much of it ([leftFraction]) is left, counting down.
     */
    fun nudge(
        context: Context,
        type: PrayerType,
        eyebrow: String,
        name: String,
        text: String,
        millisLeft: Long?,
        leftFraction: Float?
    ) = RemoteViews(context.packageName, R.layout.notification_cetele_nudge).apply {
        setTextViewText(R.id.cetele_eyebrow, eyebrow)
        setTextViewText(R.id.cetele_title, name)
        setTextViewText(R.id.cetele_text, text)
        setImageViewResource(R.id.cetele_emblem_icon, type.iconRes)
        setInt(R.id.cetele_emblem_icon, "setColorFilter", GREEN)
        if (millisLeft != null) {
            setProgressBar(R.id.cetele_time_left, 1000, ((leftFraction ?: 1f) * 1000).roundToInt(), false)
            countDown(R.id.cetele_countdown, millisLeft)
        } else {
            setViewVisibility(R.id.cetele_time_row, View.GONE)
        }
    }

    /**
     * The reminder after Isha, expanded, with the game on: [title] and [text] beside a flame, then
     * the day's five prayers, those [unmarked] in ember dashes.
     */
    fun reminder(context: Context, title: String, text: String, unmarked: List<PrayerType>) =
        RemoteViews(context.packageName, R.layout.notification_cetele_reminder).apply {
            setTextViewText(R.id.cetele_title, title)
            setTextViewText(R.id.cetele_text, text)
            LoggedPrayers.forEachIndexed { i, type ->
                val left = type in unmarked
                setImageViewResource(DayDiamonds[i], if (left) R.drawable.cetele_diamond_open else R.drawable.cetele_diamond_gold)
                setTextViewText(DayLabels[i], type.getPrayerName(context))
                setTextColor(DayLabels[i], if (left) EMBER else MUTED)
            }
        }

    /** A cheer, expanded: the [medal] beside [eyebrow], [title], [text] and [chip], if any. */
    fun celebration(
        context: Context,
        medal: Bitmap,
        eyebrow: String,
        title: String,
        text: String?,
        chip: String?
    ) = RemoteViews(context.packageName, R.layout.notification_cetele_celebration).apply {
        setImageViewBitmap(R.id.cetele_medal, medal)
        setTextViewText(R.id.cetele_eyebrow, eyebrow)
        setTextViewText(R.id.cetele_title, title)
        if (text != null) setTextViewText(R.id.cetele_text, text) else setViewVisibility(R.id.cetele_text, View.GONE)
        if (chip != null) setTextViewText(R.id.cetele_chip, chip) else setViewVisibility(R.id.cetele_chip, View.GONE)
    }

    /**
     * Any of the three, collapsed, on the shade's own ground: [label] in gold before [title], the
     * day's five prayers under them when [day] is given (gold once prayed, green for [active],
     * else outlined), [reward] at the end, and a countdown while [millisLeft] remain.
     */
    fun collapsed(
        context: Context,
        label: String?,
        title: String,
        reward: String?,
        day: Set<PrayerType>? = null,
        active: PrayerType? = null,
        millisLeft: Long? = null
    ) = RemoteViews(context.packageName, R.layout.notification_cetele_small).apply {
        if (label != null) setTextViewText(R.id.cetele_small_label, label) else setViewVisibility(R.id.cetele_small_label, View.GONE)
        setTextViewText(R.id.cetele_small_title, title)
        if (reward != null) setTextViewText(R.id.cetele_small_reward, reward) else setViewVisibility(R.id.cetele_small_reward, View.GONE)
        if (day != null) {
            LoggedPrayers.forEachIndexed { i, type ->
                setImageViewResource(
                    SmallDiamonds[i],
                    when (type) {
                        in day -> R.drawable.cetele_diamond_gold
                        active -> R.drawable.cetele_diamond_active
                        else -> R.drawable.cetele_diamond_empty
                    }
                )
            }
        } else {
            setViewVisibility(R.id.cetele_small_diamonds, View.GONE)
        }
        if (millisLeft != null) {
            setViewVisibility(R.id.cetele_small_countdown, View.VISIBLE)
            countDown(R.id.cetele_small_countdown, millisLeft)
        }
    }

    /**
     * A medal for a cheer, [sizeDp] across: [icon] in ink on a gold disc with a fine light ring,
     * rays around it.
     */
    fun medal(context: Context, icon: ImageVector, sizeDp: Int = 64): Bitmap {
        val size = (sizeDp * context.resources.displayMetrics.density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Drawn on a 64 by 64 grid.
        val s = size / 64f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * s
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = GOLD
        paint.alpha = 153
        Rays.forEach { (x1, y1, x2, y2) -> canvas.drawLine(x1 * s, y1 * s, x2 * s, y2 * s, paint) }

        paint.style = Paint.Style.FILL
        paint.color = GOLD
        canvas.drawCircle(32f * s, 32f * s, 18f * s, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * s
        paint.color = 0x66FFFFFF
        canvas.drawCircle(32f * s, 32f * s, 15f * s, paint)

        canvas.drawVector(icon, left = 21f * s, top = 21f * s, size = 22f * s, color = GOLD_INK)
        return bitmap
    }

    /** The medal's rays, as x1, y1, x2, y2 on its 64 by 64 grid. */
    private val Rays = listOf(
        listOf(32f, 2f, 32f, 10f), listOf(32f, 54f, 32f, 62f),
        listOf(2f, 32f, 10f, 32f), listOf(54f, 32f, 62f, 32f),
        listOf(10.8f, 10.8f, 16.5f, 16.5f), listOf(47.5f, 47.5f, 53.2f, 53.2f),
        listOf(10.8f, 53.2f, 16.5f, 47.5f), listOf(47.5f, 16.5f, 53.2f, 10.8f)
    )

    /** [icon] filled in [color], [size] square at [left], [top]. */
    private fun Canvas.drawVector(icon: ImageVector, left: Float, top: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        save()
        translate(left, top)
        scale(size / icon.viewportWidth, size / icon.viewportHeight)
        fun draw(group: VectorGroup) {
            group.forEach { node ->
                when (node) {
                    is VectorGroup -> draw(node)
                    is VectorPath -> {
                        val path = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath().apply {
                            fillType = if (node.pathFillType == PathFillType.EvenOdd) {
                                android.graphics.Path.FillType.EVEN_ODD
                            } else {
                                android.graphics.Path.FillType.WINDING
                            }
                        }
                        paint.color = color
                        paint.alpha = (255 * node.fillAlpha).roundToInt()
                        drawPath(path, paint)
                    }
                }
            }
        }
        draw(icon.root)
        restore()
    }

    /** Sets [viewId], a chronometer, counting down the [millisLeft] from now. */
    private fun RemoteViews.countDown(viewId: Int, millisLeft: Long) {
        setChronometer(viewId, SystemClock.elapsedRealtime() + millisLeft, null, true)
        setChronometerCountDown(viewId, true)
    }
}
