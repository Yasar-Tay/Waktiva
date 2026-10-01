package com.ybugmobile.waktiva.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The 4×4 "my day" widget: the same moment as the 4×2 with the weather as a note, the day circle
 * with the current prayer's stretch lit, and the day's five times. Drawn by [DayWidgets] and
 * refreshed with the other widgets by [WaktivaWidget.updateAll].
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
