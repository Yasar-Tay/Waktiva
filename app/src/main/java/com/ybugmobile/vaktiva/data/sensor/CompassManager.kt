package com.ybugmobile.vaktiva.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val declination = MutableStateFlow(0f)

    /**
     * Set the magnetic declination for the current location to convert Magnetic North to True North.
     */
    fun setDeclination(value: Float) {
        declination.value = value
    }

    private val rawCompassFlow: Flow<CompassData> = callbackFlow {
        var lastAzimuth = 0f
        val alpha = 0.25f // Low-pass filter smoothing factor

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                    val rotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

                    val adjustedRotationMatrix = FloatArray(9)
                    when (rotation) {
                        Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                            rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, adjustedRotationMatrix
                        )
                        Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                            rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, adjustedRotationMatrix
                        )
                        Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                            rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, adjustedRotationMatrix
                        )
                        else -> System.arraycopy(rotationMatrix, 0, adjustedRotationMatrix, 0, 9)
                    }

                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(adjustedRotationMatrix, orientation)
                    
                    var currentAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    
                    // Normalize azimuth to 0..360
                    currentAzimuth = (currentAzimuth + 360) % 360

                    // Smooth out the rotation using a low-pass filter
                    var diff = currentAzimuth - lastAzimuth
                    if (diff > 180) diff -= 360
                    else if (diff < -180) diff += 360
                    
                    val smoothedAzimuth = (lastAzimuth + alpha * diff + 360) % 360
                    lastAzimuth = smoothedAzimuth

                    val headingAccuracy = if (event.values.size > 4 && event.values[4] >= 0) {
                        val accuracyInDegrees = Math.toDegrees(event.values[4].toDouble()).toFloat()
                        when {
                            accuracyInDegrees < 5f -> SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                            accuracyInDegrees < 15f -> SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                            else -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                        }
                    } else {
                        event.accuracy
                    }

                    trySend(CompassData(smoothedAzimuth, headingAccuracy))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }.onStart {
        // Emit a default value to prevent blocking combine() flows
        emit(CompassData(0f, SensorManager.SENSOR_STATUS_UNRELIABLE))
    }

    val compassFlow: Flow<CompassData> = combine(rawCompassFlow, declination) { data, dec ->
        // Adjust Magnetic North to True North
        val trueAzimuth = (data.azimuth + dec + 360) % 360
        data.copy(azimuth = trueAzimuth)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CompassData(0f, SensorManager.SENSOR_STATUS_UNRELIABLE)
    )
}
