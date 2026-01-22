package com.ybugmobile.vaktiva.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AdhanNotificationTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @Before
    fun setUp() {
        device.pressHome()
        // Ensure service is stopped before each test
        context.stopService(Intent(context, AdhanService::class.java))
        
        // Clear all notifications to start clean
        device.openNotification()
        val clearAll = device.wait(Until.findObject(By.text("Clear all")), 1000)
        clearAll?.click()
        device.pressHome()
    }

    @Test
    fun testNotificationStopButton() {
        // Use a real audio resource to prevent the service from stopping due to playback error
        val audioPath = "android.resource://${context.packageName}/raw/ezan"
        val intent = Intent(context, AdhanService::class.java).apply {
            putExtra("PRAYER_NAME", "Fajr")
            putExtra("AUDIO_PATH", audioPath)
        }
        
        // Start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        // Open notifications
        device.openNotification()

        // Wait for the notification with the title containing "Fajr"
        // Use By.text because NotificationCompat.Builder.setContentTitle sets the text
        val notificationFound = device.wait(Until.hasObject(By.textContains("Fajr")), 10000)
        
        if (!notificationFound) {
            // Debugging: help identify what's on screen if it fails
            val allTexts = device.findObjects(By.pkg("com.android.systemui")).map { it.text }
            println("System UI texts: $allTexts")
        }

        assert(notificationFound) { "Adhan notification containing 'Fajr' not found" }

        // Find and click the STOP button
        val stopButton = device.wait(Until.findObject(By.text("STOP")), 5000)
        assert(stopButton != null) { "STOP button not found in notification" }
        stopButton.click()

        // Verify notification disappears
        val notificationDismissed = device.wait(Until.gone(By.textContains("Fajr")), 5000)
        assert(notificationDismissed) { "Notification did not disappear after clicking STOP" }
    }
}
