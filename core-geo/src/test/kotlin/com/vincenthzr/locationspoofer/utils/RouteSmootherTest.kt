package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSmootherTest {

    private val start = LatLng(30.0, 120.0)

    @Test
    fun `curve keeps endpoints and passes every waypoint`() {
        val corner = GeoMath.destination(start, 90.0, 800.0)
        val end = GeoMath.destination(corner, 0.0, 800.0)
        val smoothed = RouteSmoother.smooth(listOf(start, corner, end), spacingMeters = 5.0)

        assertTrue("expected a densified curve, got ${smoothed.size}", smoothed.size > 20)
        assertEquals(start.lat, smoothed.first().lat, 1e-9)
        assertEquals(start.lng, smoothed.first().lng, 1e-9)
        assertEquals(end.lat, smoothed.last().lat, 1e-9)
        assertEquals(end.lng, smoothed.last().lng, 1e-9)

        val nearestCorner = smoothed.minOf { GeoMath.distance(it, corner) }
        assertTrue("curve should pass through the selected corner, nearest=$nearestCorner", nearestCorner < 0.2)
    }

    @Test
    fun `curve actually bends instead of following two straight legs`() {
        val corner = GeoMath.destination(start, 90.0, 800.0)
        val end = GeoMath.destination(corner, 0.0, 800.0)
        val smoothed = RouteSmoother.smooth(listOf(start, corner, end), spacingMeters = 5.0)

        // 曲线上至少要有一些点偏离两条直线腿构成的折线，否则只是在原折线上加密。
        val maxDeviation = smoothed.maxOf { point ->
            minOf(
                PolylineCoverage.distanceMeters(point, listOf(start, corner)),
                PolylineCoverage.distanceMeters(point, listOf(corner, end))
            )
        }
        assertTrue("expected the curve to bow away from the two straight legs, max deviation=$maxDeviation", maxDeviation > 0.5)
    }

    @Test
    fun `two points remain a straight segment because curvature is undefined`() {
        val end = GeoMath.destination(start, 45.0, 300.0)
        val smoothed = RouteSmoother.smooth(listOf(start, end))
        assertEquals(2, smoothed.size)
        assertEquals(start, smoothed.first())
        assertEquals(end, smoothed.last())
    }

    @Test
    fun `closed loop is returned closed`() {
        val a = start
        val b = GeoMath.destination(a, 90.0, 500.0)
        val c = GeoMath.destination(b, 0.0, 500.0)
        val d = GeoMath.destination(c, 270.0, 500.0)
        val smoothed = RouteSmoother.smooth(listOf(a, b, c, d, a), spacingMeters = 5.0)

        assertTrue(smoothed.size > 20)
        assertEquals(smoothed.first().lat, smoothed.last().lat, 1e-9)
        assertEquals(smoothed.first().lng, smoothed.last().lng, 1e-9)
        assertTrue(RoutePath(smoothed).isClosedLoop)
    }

    @Test
    fun `point budget is respected on long routes`() {
        val end = GeoMath.destination(start, 0.0, 500_000.0)
        val mid = GeoMath.destination(start, 45.0, 250_000.0)
        val smoothed = RouteSmoother.smooth(listOf(start, mid, end), spacingMeters = 1.0, maxPoints = 300)
        assertTrue(smoothed.size <= 310)
        assertFalse(smoothed.isEmpty())
    }
}