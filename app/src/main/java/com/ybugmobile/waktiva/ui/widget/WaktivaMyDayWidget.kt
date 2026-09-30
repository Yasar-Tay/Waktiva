package com.ybugmobile.waktiva.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The 4×4 "my day" widget: the place, the dates and the weather, the day circle with the
 * countdown at its heart and the prayers' weather round it, and the day's times as today's
 * prayer log, marked with a tap. Drawn by [DayWidgets] and refreshed with the other widgets by
 * [WaktivaWidget.updateAll].
 */
class WaktivaMyDayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                WaktivaWidget.renderDayWidgets(context, appWidgetManager, ringIds = IntArray(0), dayIds = appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        DayWidgets.stopTickingIfUnused(context)
    }
}
