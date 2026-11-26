package com.bitdroid.maps.data

import java.util.Locale

data class LatLon(val lat: Double, val lon: Double)

/** Something you can search for and navigate to. */
data class Place(
    val id: String,
    val name: String,
    /** Second line: street, city, state. */
    val detail: String,
    /** Human readable kind of place ("Cafe", "Address", "City"). */
    val category: String,
    /** Raw OpenStreetMap value (cafe, fuel, city…) used to pick an icon. */
    val kind: String,
    val lat: Double,
    val lon: Double,
) {
    val pos get() = LatLon(lat, lon)
}

enum class Mode(val label: String, val costing: String, val emoji: String) {
    DRIVE("Drive", "auto", "🚗"),
    BIKE("Bike", "bicycle", "🚲"),
    WALK("Walk", "pedestrian", "🚶"),
}

/** One turn instruction from Valhalla. */
data class Maneuver(
    val type: Int,
    val instruction: String,
    /** "Turn right onto Harbor Street." said well before the turn. */
    val alert: String?,
    /** Said right before the turn. */
    val pre: String?,
    /** Said right after the turn ("Continue for 2 miles."). */
    val post: String?,
    val street: String?,
    val beginIndex: Int,
    val lengthM: Double,
    val timeS: Double,
) {
    val isArrival get() = type == 4 || type == 5 || type == 6
}

class Route(
    val points: List<LatLon>,
    val maneuvers: List<Maneuver>,
    val distanceM: Double,
    val durationS: Double,
    val via: String?,
    /** Extra time from live traffic (TomTom), or null when the route has no traffic data. */
    val trafficDelayS: Double? = null,
    /** Slow stretches (point index ranges into [points]); level 1 = slow, 2 = queuing, 3 = jammed/closed. */
    val traffic: List<Span> = emptyList(),
    /** Speed limits along the route (value = km/h). */
    val limits: List<Span> = emptyList(),
) {
    /** Same route with another shape/turns, keeping times and traffic info mapped onto the new shape. */
    fun withShape(pts: List<LatLon>, man: List<Maneuver>, viaName: String?): Route =
        Route(pts, man, distanceM, durationS, viaName ?: via, trafficDelayS, Span.remap(traffic, points, pts), Span.remap(limits, points, pts))

    /** Speed limit (km/h) at a point index, or null. */
    fun limitAt(i: Int): Int? = limits.firstOrNull { i >= it.from && i <= it.to }?.value

    /** Distance along the route at each shape point, in meters. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distance(points[i - 1], points[i])
    }
    val length get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()
}

/** Miles/feet for the US, UK, Liberia and Myanmar; kilometers everywhere else. */
object Units {
    var imperial: Boolean = Locale.getDefault().country.uppercase() in setOf("US", "GB", "LR", "MM")

    /** "250 ft", "0.4 mi", "300 m", "1.2 km" */
    fun short(meters: Double): String = if (imperial) {
        val miles = meters / 1609.344
        if (miles < 0.1) "${roundTo(meters * 3.28084, 10)} ft"
        else if (miles < 10) String.format(Locale.US, "%.1f mi", miles)
        else "${miles.toInt()} mi"
    } else {
        if (meters < 1000) "${roundTo(meters, 10)} m"
        else if (meters < 10000) String.format(Locale.US, "%.1f km", meters / 1000)
        else "${(meters / 1000).toInt()} km"
    }

    /** For the voice: "500 feet", "half a mile", "2 miles", "300 meters". */
    fun spoken(meters: Double): String = if (imperial) {
        val feet = meters * 3.28084
        val miles = meters / 1609.344
        when {
            feet < 1000 -> "${roundTo(feet, 100).coerceAtLeast(100)} feet"
            miles < 0.4 -> "a quarter mile"
            miles < 0.75 -> "half a mile"
            miles < 1.25 -> "1 mile"
            miles < 10 -> {
                val half = Math.round(miles * 2) / 2.0
                if (half % 1.0 == 0.0) "${half.toInt()} miles" else "$half miles"
            }
            else -> "${miles.toInt()} miles"
        }
    } else {
        if (meters < 1000) "${roundTo(meters, 50).coerceAtLeast(50)} meters"
        else String.format(Locale.US, "%.1f kilometers", meters / 1000)
    }

    fun speed(mps: Float): String = if (imperial) "${(mps * 2.23694f).toInt()}" else "${(mps * 3.6f).toInt()}"
    val speedUnit get() = if (imperial) "mph" else "km/h"

    fun duration(seconds: Double): String {
        val min = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
        return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
    }

    private fun roundTo(v: Double, step: Int): Int = (Math.round(v / step) * step).toInt()
}


/** A stretch of a route between two shape-point indexes, with a value (traffic level or speed limit). */
data class Span(val from: Int, val to: Int, val value: Int) {
    companion object {
        /** Moves spans from one route shape onto another shape of the same route (nearest points, in order). */
        fun remap(spans: List<Span>, old: List<LatLon>, new: List<LatLon>): List<Span> {
            if (spans.isEmpty() || old.isEmpty() || new.size < 2) return emptyList()
            var cursor = 0
            fun nearest(p: LatLon): Int {
                var best = cursor
                var bd = Double.MAX_VALUE
                // Search forward from the last match so loops and overlaps don't jump backwards.
                val end = minOf(new.size - 1, cursor + 4000)
                for (i in cursor..end) {
                    val d = Geo.distance(p, new[i])
                    if (d < bd) { bd = d; best = i }
                }
                return best
            }
            return spans.sortedBy { it.from }.mapNotNull { s ->
                val a = nearest(old[s.from.coerceIn(0, old.size - 1)])
                cursor = a
                val b = nearest(old[s.to.coerceIn(0, old.size - 1)])
                cursor = b
                if (b > a) Span(a, b, s.value) else null
            }
        }
    }
}
