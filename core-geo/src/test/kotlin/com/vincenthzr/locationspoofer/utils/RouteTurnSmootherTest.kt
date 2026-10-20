package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTurnSmootherTest {

    private val start = LatLng(30.0, 120.0)

    @Test
    fun `sharp corner is rounded without moving endpoints`() {
        val corner = GeoMath.destination(start, 90.0, 50.0)
        val end = GeoMath.destination(corner, 0.0, 50.0)
        val smoothed = RouteTurnSmoother.smooth(listOf(start, corner, end))

        assertTrue(smoothed.size > 5)
        assertEquals(start.lat, smoothed.first().lat, 1e-9)
        assertEquals(start.lng, smoothed.first().lng, 1e-9)
        assertEquals(end.lat, smoothed.last().lat, 1e-9)
        assertEquals(end.lng, smoothed.last().lng, 1e-9)

        val nearest = smoothed.minOf { GeoMath.distance(it, corner) }
        assertTrue("corner should be cut by a few metres, nearest=$nearest", nearest in 2.0..8.0)
    }

    @Test
    fun `straight line is left untouched`() {
        val mid = GeoMath.destination(start, 90.0, 50.0)
        val end = GeoMath.destination(mid, 90.0, 50.0)
        val smoothed = RouteTurnSmoother.smooth(listOf(start, mid, end))
        assertEquals(3, smoothed.size)
        assertEquals(start, smoothed.first())
        assertEquals(end, smoothed.last())
    }

    @Test
    fun `gentle bend below threshold is left untouched`() {
        val mid = GeoMath.destination(start, 90.0, 50.0)
        val end = GeoMath.destination(mid, 100.0, 50.0)
        val smoothed = RouteTurnSmoother.smooth(listOf(start, mid, end))
        assertEquals(3, smoothed.size)
    }

    @Test
    fun `dense road polyline does not duplicate points`() {
        val corner = GeoMath.destination(start, 90.0, 30.0)
        val end = GeoMath.destination(corner, 0.0, 30.0)
        val smoothed = RouteTurnSmoother.smooth(listOf(start, corner, end), radiusMeters = 4.0, sampleSpacingMeters = 1.0)
        assertTrue(smoothed.size > 5)
        for (i in 1 until smoothed.size) {
            assertTrue(GeoMath.distance(smoothed[i - 1], smoothed[i]) > 0.2)
        }
    }
}
