package com.bitdroid.maps.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val R = 6371008.8

    fun distance(a: LatLon, b: LatLon): Double = distance(a.lat, a.lon, b.lat, b.lon)

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Projection of [p] onto segment a→b: fraction along it (0..1) and distance off it, in meters. */
    fun project(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        // Local flat approximation around a: fine for segments a few km long.
        val k = cos(Math.toRadians(a.lat))
        val ax = 0.0; val ay = 0.0
        val bx = (b.lon - a.lon) * k; val by = b.lat - a.lat
        val px = (p.lon - a.lon) * k; val py = p.lat - a.lat
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * bx + (py - ay) * by) / len2).coerceIn(0.0, 1.0)
        val q = LatLon(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
        return t to distance(p, q)
    }

    /** Decodes a Valhalla polyline (6 digits of precision). */
    fun decodePolyline6(encoded: String): List<LatLon> {
        val out = ArrayList<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            result = 0L
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lon += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            out.add(LatLon(lat / 1e6, lon / 1e6))
        }
        return out
    }
}
