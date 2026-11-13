package com.bitdroid.maps.nav

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * GPS through Android's own LocationManager, so it works without Google Play Services.
 * GPS fixes win; network/fused fixes fill in while GPS warms up or indoors.
 */
class LocationTracker(private val context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location
    private var running = false
    private var lastGpsAt = 0L

    private val listener = LocationListener { loc -> offer(loc) }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !hasPermission()) return
        running = true
        val providers = lm.getProviders(true)
        // Seed with the freshest last-known fix so the map can center right away.
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { offer(it) }
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (p in providers) {
                runCatching { lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper()) }
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        lm.removeUpdates(listener)
    }

    private var lastBearing: Float? = null

    private fun offer(raw: Location) {
        val now = System.currentTimeMillis()
        if (raw.provider == LocationManager.GPS_PROVIDER) lastGpsAt = now
        else if (now - lastGpsAt <= 5000) return
        _location.value = steady(raw)
    }

    /**
     * GPS wobbles a few meters and guesses a new heading every second when you stand still.
     * When you're stopped: keep the dot where it was (unless it clearly moved) and keep the last
     * real heading, so the arrow doesn't spin or jitter.
     */
    private fun steady(raw: Location): Location {
        val loc = Location(raw)
        val moving = raw.hasSpeed() && raw.speed >= 1.5f
        if (moving && raw.hasBearing()) lastBearing = raw.bearing
        val bearing = lastBearing
        if (!moving) {
            if (bearing != null) loc.bearing = bearing else loc.removeBearing()
            val prev = _location.value
            if (prev != null) {
                val jump = prev.distanceTo(raw)
                val noise = maxOf(raw.accuracy, 8f)
                if (jump < noise) {
                    // Same place, fresher time: keep the old position.
                    loc.latitude = prev.latitude
                    loc.longitude = prev.longitude
                }
            }
        }
        return loc
    }
}
