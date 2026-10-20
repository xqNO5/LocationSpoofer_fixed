package com.vincenthzr.locationspoofer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryCacheTest {

    private val a = RoutePoint(30.1234567, 120.7654321)
    private val b = RoutePoint(30.2234567, 120.8654321)

    @Test
    fun `cache key is stable and ignores sub-decimeter float noise`() {
        val first = RouteGeometryCacheKey.of(listOf(a, b), "WALKING")
        val second = RouteGeometryCacheKey.of(
            listOf(a.copy(lat = a.lat + 1e-8), b.copy(lng = b.lng - 1e-8)),
            "WALKING"
        )
        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun `cache key separates profiles and waypoint order`() {
        assertNotEquals(
            RouteGeometryCacheKey.of(listOf(a, b), "WALKING"),
            RouteGeometryCacheKey.of(listOf(a, b), "DRIVING")
        )
        assertNotEquals(
            RouteGeometryCacheKey.of(listOf(a, b), "WALKING"),
            RouteGeometryCacheKey.of(listOf(b, a), "WALKING")
        )
    }

    @Test
    fun `route point codec keeps traffic light waits`() {
        val encoded = RoutePointCodec.encode(listOf(a.copy(waitSec = 15.0), b))
        val decoded = RoutePointCodec.decode(encoded)
        assertEquals(2, decoded.size)
        assertEquals(15.0, decoded[0].waitSec, 0.0)
        assertEquals(0.0, decoded[1].waitSec, 0.0)
        assertEquals(a.lat, decoded[0].lat, 1e-9)
    }

    @Test
    fun `route point codec rejects malformed input without throwing`() {
        assertTrue(RoutePointCodec.decode("not json").isEmpty())
        assertTrue(RoutePointCodec.decode("[]").isEmpty())
        assertTrue(RoutePointCodec.decode("[{\"lat\":999,\"lng\":0}]").isEmpty())
    }
}
