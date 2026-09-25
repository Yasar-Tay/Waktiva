package com.ybugmobile.waktiva.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The countdown widget: a full-width 4×1 bar showing the time left to the next prayer at the
 * bar's full height, beside the main [WaktivaWidget] rather than in place of it.
 *
 * It shares the main widget's data and refresh path: [WaktivaWidget.updateAll] renders both at
 * every prayer boundary, and this provider only answers the launcher's own update requests.
 */
class WaktivaCountdownWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                WaktivaWidget.renderCountdown(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }
}
