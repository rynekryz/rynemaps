package com.rynekryz.rynemaps.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    fun updates(): Flow<Location> = callbackFlow {
        val listener = LocationListener { location -> trySend(location) }
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { manager.isProviderEnabled(it) }
            providers.forEach { provider ->
                manager.getLastKnownLocation(provider)?.let { trySend(it) }
                manager.requestLocationUpdates(provider, 1000L, 1f, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            close()
        }
        awaitClose { manager.removeUpdates(listener) }
    }
}
