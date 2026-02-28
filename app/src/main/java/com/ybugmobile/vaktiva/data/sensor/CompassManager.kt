package com.ybugmobile.vaktiva.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

data class CompassData(
    val azimuth: Float,
    val accuracy: Int
)

@Singleton
class CompassManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val declination = MutableStateFlow(0f)

    fun setDeclination(value: Float) {
        declination.value = value
    }

    private val rawCompassFlow: Flow<CompassData> = callbackFlow {
        var lastAzimuth = -1f
        val alpha = 0.22f 
        var lastEventTime = System.currentTimeMillis()

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val adjustedRotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)
            
            private val lastAccel = FloatArray(3)
            private val lastMag = FloatArray(3)
            private var hasAccel = false
            private var hasMag = false

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                lastEventTime = System.currentTimeMillis()

                var azimuthFound = false
                var currentAccuracy = event.accuracy

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        if (event.values.size >= 4) {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            azimuthFound = true
                            if (event.values.size > 4) {
                                val accuracyDegrees = Math.toDegrees(event.values[4].toDouble())
                                currentAccuracy = when {
                                    accuracyDegrees < 10 -> SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                                    accuracyDegrees < 25 -> SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                                    else -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                                }
                            }
                        }
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, lastAccel, 0, 3)
                        hasAccel = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, lastMag, 0, 3)
                        hasMag = true
                    }
                }

                if (!azimuthFound && hasAccel && hasMag) {
                    azimuthFound = SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag)
                }

                if (azimuthFound) {
                    val rotation = try {
                        displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
                    } catch (e: Exception) {
                        Surface.ROTATION_0
                    }

                    when (rotation) {
                        Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, adjustedRotationMatrix)
                        Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, adjustedRotationMatrix)
                        Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, adjustedRotationMatrix)
                        else -> System.arraycopy(rotationMatrix, 0, adjustedRotationMatrix, 0, 9)
                    }

                    SensorManager.getOrientation(adjustedRotationMatrix, orientation)
                    var currentAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (currentAzimuth.isNaN()) return
                    
                    currentAzimuth = (currentAzimuth + 360) % 360

                    if (lastAzimuth == -1f) {
                        lastAzimuth = currentAzimuth
                    } else {
                        var diff = currentAzimuth - lastAzimuth
                        if (diff > 180) diff -= 360 else if (diff < -180) diff += 360
                        lastAzimuth = (lastAzimuth + alpha * diff + 360) % 360
                    }

                    trySend(CompassData(lastAzimuth, currentAccuracy))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val handler = Handler(Looper.getMainLooper())
        
        val registerSensors = {
            if (rotationSensor != null) {
                sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI, handler)
            }
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI, handler)
            sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI, handler)
        }

        registerSensors()

        // Watchdog to recover from sensor freezes (especially after lock screen)
        val watchdogJob = launch {
            while (isActive) {
                delay(4000)
                if (System.currentTimeMillis() - lastEventTime > 4000) {
                    sensorManager.unregisterListener(listener)
                    registerSensors()
                }
            }
        }

        awaitClose { 
            watchdogJob.cancel()
            sensorManager.unregisterListener(listener) 
        }
    }.conflate()

    val compassFlow: Flow<CompassData> = combine(rawCompassFlow, declination) { data, dec ->
        val trueAzimuth = (data.azimuth + dec + 360) % 360
        data.copy(azimuth = trueAzimuth)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CompassData(0f, SensorManager.SENSOR_STATUS_UNRELIABLE)
    )
}
