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

    fun update(loc: Location): Step {
        val p = LatLon(loc.latitude, loc.longitude)
        val pts = route.points
        val maneuvers = route.maneuvers

        // Snap to the closest segment, looking a little behind and well ahead of where we were.
        val from = if (offCount > 0) 0 else (segment - 3).coerceAtLeast(0)
        val to = if (offCount > 0) pts.size - 2 else (segment + 80).coerceAtMost(pts.size - 2)
        var best = segment
        var bestT = 0.0
        var bestD = Double.MAX_VALUE
        for (i in from..to) {
            if (i < 0 || i + 1 >= pts.size) continue
            val (t, d) = Geo.project(p, pts[i], pts[i + 1])
            if (d < bestD) { bestD = d; best = i; bestT = t }
        }
        if (pts.size < 2) { bestD = 0.0; best = 0 }
        segment = best
        val along = if (pts.size < 2) 0.0 else
            route.cumulative[best] + bestT * (route.cumulative[best + 1] - route.cumulative[best])

        // Off route: clearly away from the line for a few fixes in a row.
        val limit = maxOf(40.0, loc.accuracy * 1.5)
        offCount = if (bestD > limit) offCount + 1 else 0
        val offRoute = offCount >= 3

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
