package com.ybugmobile.waktiva.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The 4×2 day ring widget: the day circle with the weather in its middle, the countdown to the
 * next prayer, the day's weather and today's prayer log, marked with a tap. Drawn by
 * [DayWidgets] and refreshed with the other widgets by [WaktivaWidget.updateAll].
 */
class WaktivaDayRingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetManager, appWidgetIds)
    }

    /** Below Android 12 the short and tall layouts depend on the size, so re-render on resize. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDisabled(context: Context) {
        DayWidgets.stopTickingIfUnused(context)
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                WaktivaWidget.renderDayWidgets(context, manager, ringIds = ids, dayIds = IntArray(0))
            } finally {
                pending.finish()
            }
        }
    }
}
