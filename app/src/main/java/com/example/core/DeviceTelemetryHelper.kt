package com.example.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.DeviceTelemetry
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "DeviceTelemetry"

class DeviceTelemetryHelper(private val context: Context) {

    fun currentDeviceLabel(): String {
        val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val raw = if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model"
        return raw.take(75).ifBlank { "Android Phone" }
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun snapshotMemoryAndBattery(locationLabel: String): DeviceTelemetry {
        val runtime = Runtime.getRuntime()
        val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)).toInt()
            .coerceAtLeast(1)
        val maxMb = (runtime.maxMemory() / (1024L * 1024L)).toInt().coerceAtLeast(64)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return DeviceTelemetry(
            appHeapMb = usedMb,
            maxHeapMb = maxMb,
            batteryPercent = pct.coerceIn(0, 100),
            charging = charging,
            locationLabel = locationLabel,
            deviceLabel = currentDeviceLabel(),
            networkOnline = isNetworkAvailable()
        )
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun resolveCoarseLocationLabel(): String? {
        if (!hasLocationPermission()) return null
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val loc = suspendCancellableCoroutine<android.location.Location?> { cont ->
                val tokenSource = CancellationTokenSource()
                cont.invokeOnCancellation { tokenSource.cancel() }
                try {
                    client.getCurrentLocation(Priority.PRIORITY_LOW_POWER, tokenSource.token)
                        .addOnSuccessListener { result ->
                            if (cont.isActive) cont.resume(result)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            } ?: return null

            withContext(Dispatchers.IO) {
                if (!Geocoder.isPresent()) {
                    return@withContext String.format(
                        Locale.US,
                        "%.2f, %.2f",
                        loc.latitude,
                        loc.longitude
                    )
                }
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                val first = addresses?.firstOrNull()
                val city = first?.locality ?: first?.subAdminArea
                val region = first?.adminArea ?: first?.countryName
                listOfNotNull(city, region)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank {
                        String.format(Locale.US, "%.2f, %.2f", loc.latitude, loc.longitude)
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Coarse location lookup skipped: ${e.message}")
            null
        }
    }
}
