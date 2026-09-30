package com.ybugmobile.waktiva.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ybugmobile.waktiva.domain.model.PrayerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * What the day circle widgets ask of the app: a tap on a prayer's token marks it in the prayer
 * log or takes the mark back ([DayWidgets.ACTION_TOGGLE_PRAYER]), and the half-hourly tick moves
 * the circle's hand and sky along ([DayWidgets.ACTION_TICK]). Either way every widget is redrawn.
 */
class DayWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != DayWidgets.ACTION_TOGGLE_PRAYER && action != DayWidgets.ACTION_TICK) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (action == DayWidgets.ACTION_TOGGLE_PRAYER) {
                    val date = intent.getStringExtra(DayWidgets.EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val type = intent.getStringExtra(DayWidgets.EXTRA_PRAYER)?.let { PrayerType.fromString(it) }
                    if (date != null && type != null) DayWidgets.togglePrayer(context, date, type)
                }
                WaktivaWidget.updateAll(context)
            } catch (e: Exception) {
                Log.e("DayWidgetActionReceiver", "Could not handle $action", e)
            } finally {
                pending.finish()
            }
        }
    }
}
