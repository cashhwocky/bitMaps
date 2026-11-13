package com.bitdroid.maps.nav

import android.location.Location
import com.bitdroid.maps.data.Geo
import com.bitdroid.maps.data.LatLon
import com.bitdroid.maps.data.Maneuver
import com.bitdroid.maps.data.Route
import com.bitdroid.maps.data.Units

/** Where you are on the route right now. */
data class Progress(
    /** The turn you're heading to. */
    val maneuver: Maneuver,
    /** The turn after that. */
    val next: Maneuver?,
    val toManeuverM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val speedMps: Float,
    val arrived: Boolean,
)

data class Step(val progress: Progress, val speech: String?, val offRoute: Boolean)

/**
 * Follows GPS fixes along a route: which turn is next, how far it is, what to say and when,
 * and whether you've left the route (so the app can reroute).
 */
class Navigator(val route: Route) {
    private var segment = 0
    private var target = 1
    /** 0 = nothing said for this turn yet, 1 = early heads-up said, 2 = "turn now" said. */
    private var stage = 0
    private var offCount = 0
    private var started = false
    private var arrived = false

    /** Your position snapped onto the route (or the raw GPS fix when you're off it). */
    var snapped: Location? = null
        private set

    fun update(loc: Location): Step {
        val p = LatLon(loc.latitude, loc.longitude)
        val pts = route.points
        val maneuvers = route.maneuvers

        // Which way you're actually driving (only trust it when moving).
        val heading: Double? = if (loc.hasBearing() && loc.hasSpeed() && loc.speed > 3f) loc.bearing.toDouble() else null

        fun search(from: Int, to: Int, needDirection: Boolean): Triple<Int, Double, Double> {
            var bi = -1; var bt = 0.0; var bd = Double.MAX_VALUE
            for (i in from..to) {
                if (i < 0 || i + 1 >= pts.size) continue
                if (needDirection && heading != null) {
                    // Skip parts of the route going the other way (opposite side of a highway, a loop back).
                    if (Geo.angleDiff(Geo.bearing(pts[i], pts[i + 1]), heading) > 60) continue
                }
                val (t, d) = Geo.project(p, pts[i], pts[i + 1])
                if (d < bd) { bd = d; bi = i; bt = t }
            }
            return Triple(bi, bt, bd)
        }

        // 1) Near where we were: a little behind, well ahead. This keeps you on the right road
        //    even when another part of the route (or an overpass) is close by.
        var (best, bestT, bestD) = search((segment - 3).coerceAtLeast(0), (segment + 120).coerceAtMost(pts.size - 2), needDirection = true)
        if (best < 0) {
            val r = search((segment - 3).coerceAtLeast(0), (segment + 120).coerceAtMost(pts.size - 2), needDirection = false)
            best = r.first; bestT = r.second; bestD = r.third
        }
        // 2) Lost? Look over the rest of the route ahead, but only parts heading your way.
        if (offCount > 0 && pts.size > 1) {
            val r = search(segment, pts.size - 2, needDirection = true)
            if (r.first >= 0 && r.third < bestD) { best = r.first; bestT = r.second; bestD = r.third }
        }
        if (best < 0 || pts.size < 2) { best = 0; bestT = 0.0; bestD = 0.0 }
        segment = best
        val along = if (pts.size < 2) 0.0 else
            route.cumulative[best] + bestT * (route.cumulative[best + 1] - route.cumulative[best])

        // Off route: clearly away from the line for a few fixes in a row (wider on fast roads).
        val limit = maxOf(if (loc.speed > 20f) 60.0 else 45.0, loc.accuracy * 1.8)
        offCount = if (bestD > limit) offCount + 1 else 0
        val offRoute = offCount >= 3

        // Where the arrow should be drawn: right on the route line, pointing along the road.
        snapped = if (!offRoute && bestD <= limit && pts.size >= 2) {
            val a = pts[best]; val b = pts[best + 1]
            Location(loc).apply {
                latitude = a.lat + bestT * (b.lat - a.lat)
                longitude = a.lon + bestT * (b.lon - a.lon)
                bearing = Geo.bearing(a, b).toFloat()
            }
        } else loc

        // The next turn is the first maneuver that starts ahead of us.
        var t = maneuvers.indexOfFirst { m -> m.beginIndex < route.cumulative.size && route.cumulative[m.beginIndex] > along + 3 }
        if (t < 0) t = maneuvers.lastIndex
        if (t != target) { target = t; stage = 0 }
        val m = maneuvers[t]
        val toTurn = (route.cumulative[m.beginIndex.coerceAtMost(route.cumulative.lastIndex)] - along).coerceAtLeast(0.0)
        val remaining = (route.length - along).coerceAtLeast(0.0)
        val remainingS = if (route.length > 0) route.durationS * remaining / route.length else 0.0

        var speech: String? = null
        if (!started) {
            started = true
            val first = maneuvers.firstOrNull()
            speech = listOfNotNull(first?.pre ?: first?.instruction, first?.post).joinToString(" ")
            if (toTurn < 60) stage = 2
        } else if (!offRoute) {
            val v = maxOf(loc.speed.toDouble(), 5.0)
            val near = (v * 7).coerceIn(35.0, 250.0)
            val far = (v * 35).coerceIn(250.0, 1600.0)
            if (stage < 2 && toTurn <= near) {
                stage = 2
                speech = m.pre ?: m.instruction
            } else if (stage < 1 && toTurn <= far && toTurn > near * 1.8) {
                stage = 1
                speech = "In ${Units.spoken(toTurn)}, ${(m.alert ?: m.instruction).replaceFirstChar { it.lowercase() }}"
            }
        }

        if (!arrived && (remaining < 20 || (m.isArrival && toTurn < 15 && t == maneuvers.lastIndex))) {
            arrived = true
            if (stage < 2) speech = m.pre ?: "You have arrived."
            stage = 2
        }

        return Step(
            Progress(
                maneuver = m,
                next = maneuvers.getOrNull(t + 1),
                toManeuverM = toTurn,
                remainingM = remaining,
                remainingS = remainingS,
                speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                arrived = arrived,
            ),
            speech,
            offRoute,
        )
    }
}
