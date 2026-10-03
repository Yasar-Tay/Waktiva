package com.ybugmobile.waktiva.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The 4×2 prayer log widget: the level, XP, streak and badges, and today's five prayers to mark
 * with a tap. Drawn by [PrayerLogWidget] and refreshed with the other widgets by
 * [WaktivaWidget.updateAll].
 */
class WaktivaPrayerLogWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                WaktivaWidget.renderPrayerLog(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }
}
