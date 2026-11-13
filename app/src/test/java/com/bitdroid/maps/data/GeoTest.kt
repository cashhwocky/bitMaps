package com.bitdroid.maps.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun decodesPolyline6() {
        // Two points: (38.5, -120.2) and (40.7, -120.95), encoded at 6 digits of precision.
        val pts = Geo.decodePolyline6("_izlhA~rlgdF_{geC~ywl@")
        assertEquals(2, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-6)
        assertEquals(-120.2, pts[0].lon, 1e-6)
        assertEquals(40.7, pts[1].lat, 1e-6)
        assertEquals(-120.95, pts[1].lon, 1e-6)
    }

    @Test
    fun distanceIsSane() {
        // One degree of latitude is about 111 km.
        val d = Geo.distance(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, d, 200.0)
    }
}
