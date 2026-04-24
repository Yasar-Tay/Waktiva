package com.ybugmobile.waktiva.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

import android.location.LocationManager
// ...
@Singleton
class LocationWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Retrieves the current location.
     * @param highAccuracy If true, uses PRIORITY_HIGH_ACCURACY (for Qibla).
     *                     If false, uses PRIORITY_BALANCED_POWER_ACCURACY (for Prayer Times).
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(highAccuracy: Boolean = false): Location? {
        return try {
            val priority = if (highAccuracy) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }
            
            // 1. Try Fused Location Provider (Fresh)
            val currentLocation = withContext(Dispatchers.IO) {
                try {
                    fusedLocationClient.getCurrentLocation(priority, null).await()
                } catch (e: Exception) { null }
            }

            if (currentLocation != null) return currentLocation

            // 2. Try Fused Location Provider (Last Known)
            val lastLocation = withContext(Dispatchers.IO) {
                try {
                    fusedLocationClient.lastLocation.await()
                } catch (e: Exception) { null }
            }

            if (lastLocation != null) return lastLocation

            // 3. Last Fallback: Legacy Location Manager (Common in some emulators)
            withContext(Dispatchers.IO) {
                try {
                    val provider = if (highAccuracy) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                    locationManager.getLastKnownLocation(provider)
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            null
        }
    }
// ...

    suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val city = address.locality ?: address.subAdminArea ?: address.adminArea
                    val country = address.countryName
                    if (city != null && country != null) "$city, $country" else city ?: country
                } else null
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val city = address.locality ?: address.subAdminArea ?: address.adminArea
                    val country = address.countryName
                    if (city != null && country != null) "$city, $country" else city ?: country
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
