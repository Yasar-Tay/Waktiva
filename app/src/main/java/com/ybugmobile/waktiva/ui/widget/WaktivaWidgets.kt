package com.ybugmobile.waktiva.ui.widget

import android.content.Context

/** Single refresh entry point for every Waktiva home-screen widget. */
object WaktivaWidgets {

    suspend fun updateAll(context: Context) {
        WaktivaWidget.updateAll(context)
        WaktivaIosWidget.updateAll(context)
    }
}
